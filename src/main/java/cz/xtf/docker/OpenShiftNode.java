package cz.xtf.docker;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import cz.xtf.TestConfiguration;
import cz.xtf.io.IOUtils;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.ssh.SshUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.NodeCondition;

public class OpenShiftNode {

	public static enum Status {
		Unknown, Ready, NotReady;

		public static Status parseString(final List<NodeCondition> conditions) {
			for (NodeCondition nc : conditions) {
				if (nc.getType().equals("Ready")) {
					if (nc.getStatus().equals("True")) {
						return Ready;
					} else {
						return NotReady;
					}
				}
			}
			return Unknown;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(OpenShiftNode.class);

	private static OpenShiftNode master = null;

	private final String hostname;
	private final String username;
	private Map<String, String> labels = null;
	private Status status = null;
	
	public OpenShiftNode(final String hostname) {
		this(hostname, (String)null);
	}

	public OpenShiftNode(final String hostname, final Status status, final Map<String, String> labels) {
		this(hostname, (String)null);
		this.labels = labels;
		this.status = status;
	}

	public OpenShiftNode(final String hostname, final String username) {
		this.hostname = hostname;
		this.username = (username == null) ? TestConfiguration.masterSshUsername() : username;
	}

	public static OpenShiftNode master() {
		if (master != null) {
			return master;
		}
		master = new OpenShiftNode(URI.create(OpenshiftUtil.getInstance().getServer()).getHost());
		return master;
	}

	public static OpenShiftNode router() {
//		TODO Disabled due to insufficient privileges
//		return OpenshiftUtil.getInstance().getNodes().stream().filter(OpenShiftNode::isRouter).findFirst().orElseThrow(() -> new IllegalStateException("No router found"));
		return OpenShiftNode.master();
	}

	public int executeCommand(String command, CommandResultConsumer consumer) {
		return executeCommand(false, command, consumer);
	}

	public int executeCommand(boolean setPty, String command, CommandResultConsumer consumer) {
		Session ssh = SshUtil.getInstance().createSshSession(username, hostname);
		int resultCode = 255;

		try {
			ssh.connect();
			LOGGER.debug("Connected to ssh console");
			ChannelExec channel = (ChannelExec) ssh.openChannel("exec");
			channel.setPty(setPty);
			channel.setCommand(command);
			channel.setInputStream(null);
			channel.setOutputStream(System.err);

			LOGGER.debug("Executing command: '{}'", command);
			channel.connect();
			try {
				if (consumer != null) {
					consumer.consume(channel.getInputStream());
				}
			} catch (IOException ex) {
				throw new RuntimeException("Unable to read console output", ex);
			} finally {
				channel.disconnect();
			}
			resultCode = channel.getExitStatus();
		} catch (JSchException x) {
			LOGGER.error("Error SSH to " + username + "@" + hostname, x);
		} finally {
			ssh.disconnect();
		}

		return resultCode;
	}

	public String executeCommand(String command) {
		final AtomicReference<String> aref = new AtomicReference<>();

		int result = executeCommand(true, command, stream -> aref.set(IOUtils.readInputStream(stream)));
		String ret = aref.get();

		if (result != 0) {
			LOGGER.warn("Command '{}' exited with status {}", command, result);
			if (ret == null) {
				ret = "[null]";
			}
			LOGGER.warn("Result: '{}'", StringUtils.abbreviate(ret, ret.length() - 100, 100));
		}

		if (ret != null) {
			if (ret.length() > 80) {
				LOGGER.debug("Result: '{}...' (length: {})", ret.substring(0, 80), ret.length());
			}
			else {
				LOGGER.debug("Result: '{}'", ret);
			}
		}
		return aref.get();
	}

	public void portForward(final String targetHost, final int targetPort, final Consumer<Integer> code) {
		Session ssh = SshUtil.getInstance().createSshSession(username, hostname);
		try {
			final int localPort = ssh.setPortForwardingL(0, targetHost, targetPort);
			ssh.connect();
			code.accept(localPort);
			LOGGER.debug("Doing port forward {}->{}", localPort, targetPort);
		} catch (JSchException x) {
			LOGGER.error("Error SSH to " + username + "@" + hostname, x);
			throw new IllegalStateException(x);
		} finally {
			ssh.disconnect();
		}
	}

	public void restart() {
		executeCommand("sudo reboot");
	}

	public void shutdown() {
		executeCommand("sudo shutdown -H now");
	}

	public boolean isSSHResponsive() {
		try {
			String testUser = executeCommand("whoami");
			return testUser != null && username.equals(testUser.trim());
		}
		catch (Exception e) {
			LOGGER.error("SSH ping exception", e);
			return false;
		}
	}
	
	public void ifDownAndUp(int seconds) {
		// TODO: don't assume it is "eth0"
		executeCommand("nohup sudo -b sh -c 'ifdown eth0; sleep " + seconds + "; ifup eth0'");
	}

	public String getHostname() {
		return hostname;
	}

	public String getIPAddress() {
		try {
			return InetAddress.getByName(getHostname()).getHostAddress();
		} catch (UnknownHostException e) {
			throw new IllegalStateException("Cannot resolve hostname to IP", e);
		}
	}

	public String getUsername() {
		return username;
	}

	public boolean isMaster() {
		return "master".equals(executeCommand(
						"ls /etc/sysconfig/atomic-openshift-master > /dev/null && echo \"master\"")
						.trim());
	}

	public boolean isDnsmasq() {
		return "dnsmasq".equals(executeCommand(
				"netstat -tlnp | grep ':53 .*dnsmasq' > /dev/null && echo dnsmasq")
				.trim());
	}

	public boolean isRouter() {
		return "router".equals(executeCommand(
						"netstat -tlnp | grep ':80 .*docker-proxy' > /dev/null && echo router")
						.trim());
	}

	public boolean isInfra() {
		return isMaster() || isDnsmasq() || isRouter();
	}

	@Override
	public String toString() {
		return "[Node:" + username + "@" + hostname + "], status = "
				+ ((status != null) ? status : "Not initialized") + ", labels = "
				+ ((labels != null) ? labels : "Not initialized");
	}

	@FunctionalInterface
	public interface CommandResultConsumer {
		void consume(InputStream istream) throws IOException;
	}

	public Map<String, String> getLabels() {
		return labels;
	}

	public Status getStatus() {
		return status;
	}
	
	public void waitTillNodeIsReady() {
		LOGGER.info("Waiting for node {} to go on-line", getHostname());
		while (!isSSHResponsive()) {
			LOGGER.debug("SSH not responsive");
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				LOGGER.error("Error in waiting", e);
				throw new IllegalStateException("Error in waiting", e);
			}
		}
		LOGGER.info("SSH responsive");
		for (;;) {
			OpenShiftNode n = OpenshiftUtil.getInstance().getNodesAsMap().get(getHostname());
			if (n.getStatus() == Status.Ready) {
				break;
			}
			LOGGER.debug("Node on-line but not ready");
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				LOGGER.error("Error in waiting", e);
				throw new IllegalStateException("Error in waiting", e);
			}
		}
		LOGGER.info("Node {} ready", getHostname());
	}
}
