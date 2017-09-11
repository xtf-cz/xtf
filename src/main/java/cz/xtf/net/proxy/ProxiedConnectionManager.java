package cz.xtf.net.proxy;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import cz.xtf.TestConfiguration;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.ssh.SshUtil;
import cz.xtf.util.RandomUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxiedConnectionManager implements Closeable {
	private static final Logger LOGGER = LoggerFactory
			.getLogger(ProxiedConnectionManager.class);
	private final String sourceIPs;
	private final String targetHost;
	private final int targetPort;
	private final String containerName = RandomUtil.generateUniqueId("tcp-proxy");

	private Session ssh;

	private int listenPort;

	public ProxiedConnectionManager(final String targetHost, final int targetPort) {
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		sourceIPs = TestConfiguration.proxyHostsString();
		createTCPProxy();
	}

	public static ProxiedConnectionManager toHTTPRouter() {
		return new ProxiedConnectionManager(OpenShiftNode.router().getIPAddress(), 80);
	}

	public static ProxiedConnectionManager toTLSRouter() {
		return new ProxiedConnectionManager(OpenShiftNode.router().getIPAddress(), 443);
	}

	public ForwardedConnection connect() {
		return new ForwardedConnection(
				this,
				targetHost,
				targetPort);
	}

	private void createTCPProxy() {
		try {
			final String cleanCommand = String.format("sudo docker ps -a | grep 'hours ago' | awk '{print $1}' | xargs --no-run-if-empty sudo docker rm",
					containerName,
					this.targetHost,
					this.targetPort,
					sourceIPs,
					ImageRegistry.get().tcpProxy());
			LOGGER.info("Establishing SSH shell");
			ssh = SshUtil.getInstance()
					.createSshSession(TestConfiguration.proxyHostUsername(), getProxyHost());
			ssh.connect();
			LOGGER.debug("Connected to ssh console");

			final ChannelExec cleanChannel = (ChannelExec) ssh.openChannel("exec");
			cleanChannel.setPty(true);
			cleanChannel.setCommand(cleanCommand);
			cleanChannel.setInputStream(null);
			cleanChannel.setOutputStream(System.err);

			LOGGER.debug("Executing command: '{}'", cleanCommand);
			cleanChannel.connect();
			cleanChannel.disconnect();

			final String command = String.format("sudo docker run -it --name %s --net=host --rm -e AB_OFF=true -e TARGET_HOST=%s -e TARGET_PORT=%s -e TARGET_VIA=%s %s",
					containerName,
					this.targetHost,
					this.targetPort,
					sourceIPs,
					ImageRegistry.get().tcpProxy());
			LOGGER.info("Establishing SSH shell");
			ssh = SshUtil.getInstance()
					.createSshSession(TestConfiguration.proxyHostUsername(), getProxyHost());
			ssh.connect();
			LOGGER.debug("Connected to ssh console");

			final ChannelExec channel = (ChannelExec) ssh.openChannel("exec");
			channel.setPty(true);
			channel.setCommand(command);
			channel.setInputStream(null);
			channel.setOutputStream(System.err);

			LOGGER.debug("Executing command: '{}'", command);
			channel.connect();
			final LineIterator li = IOUtils.lineIterator(new InputStreamReader(channel.getInputStream()));
			final Pattern portLine = Pattern.compile(".*Listening on port ([0-9]*).*$");
			listenPort = 0;
			while (li.hasNext()) {
				final String line = li.next();
				LOGGER.trace("Shell line: {}", line);
				final Matcher m = portLine.matcher(line);
				if (m.matches()) {
					listenPort = Integer.parseInt(m.group(1));
					LOGGER.info("Connection listening on port {}", listenPort);
					break;
				}
			}
			channel.disconnect();
		} catch (final JSchException | IOException e) {
			LOGGER.debug("Error in creating SSH connection to proxy host", e);
			throw new IllegalStateException("Cannot open SSH connection", e);
		}
	}

	@Override
	public void close() {
		LOGGER.info("Closing TCP Proxy on port {}", listenPort);
		try {
			final String command = "sudo docker kill " + containerName;
			final ChannelExec channel = (ChannelExec) ssh.openChannel("exec");
			channel.setPty(true);
			channel.setCommand(command);
			channel.setInputStream(null);
			channel.setOutputStream(System.err);

			LOGGER.debug("Executing command: '{}'", command);
			channel.connect();
			Thread.sleep(3000);
		} catch (Exception e) {
			LOGGER.debug("Killing proxy", e);
		}
		try {
			ssh.disconnect();
		} catch (Exception e) {
			LOGGER.debug("Disconnecting SSH", e);
		}
	}

	String getProxyHost() {
		return TestConfiguration.proxyHost();
	}

	protected int getListenPort() {
		return listenPort;
	}

}
