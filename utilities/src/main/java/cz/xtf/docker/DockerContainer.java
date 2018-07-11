package cz.xtf.docker;

import cz.xtf.TestConfiguration;
import cz.xtf.openshift.OpenshiftUtil;
import io.fabric8.kubernetes.api.model.NodeAddress;
import io.fabric8.kubernetes.api.model.Pod;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cz.xtf.openshift.OpenShiftUtils.admin;

public class DockerContainer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainer.class);
	private OpenShiftNode node;
	private String containerId;

	private DockerContainer(final String host, final String containerId) {
		this.node = new OpenShiftNode(
				host.equals(TestConfiguration.CDK_INTERNAL_HOSTNAME) ? TestConfiguration.CDK_IP
						: host);
		this.containerId = containerId;
	}

	/**
	 * Get the host and containerId of the router, expecting a single router
	 * instance
	 * <p>
	 * It tries to ssh to the openshift master and run "osc" commands to get the
	 * default namespace pods information (where the router should be)
	 */
	public static DockerContainer createForRouter() {
		Collection<Pod> pods = OpenshiftUtil.getInstance().withAdminUser(client -> client.inNamespace("default").pods().list().getItems());

		for (Pod pod : pods) {
			if (pod.getMetadata().getName().startsWith("router-")) {
				return createForPod(pod, "router");
			}
		}

		return null;
	}

	/**
	 * Get the container with the same name as the "name" label of the pod
	 */
	public static DockerContainer createForPod(Pod pod) {
		return createForPod(pod, pod.getMetadata().getLabels().get("name"));
	}


	public static DockerContainer createForPod(Pod pod, String containerLabel) {
		String host = pod.getSpec().getNodeName();
		return createForPod(pod, containerLabel, host);
	}

	public static DockerContainer createForPod(Pod pod, String containerLabel, String host) {
		try {
			// attempt to treat the node name as a hostname
			InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			// try the node external address if exists
			Optional<NodeAddress> nodeAddress = admin().client().nodes().withName(host).get().getStatus().getAddresses().stream().filter(addr -> "ExternalIP".equals(addr.getType())).findFirst();
			if (nodeAddress.isPresent()) {
				host = nodeAddress.get().getAddress();
			}
		}

		String containerId = URI.create(pod.getStatus().getContainerStatuses().stream()
				.filter(containerStats -> containerStats.getName().equals(containerLabel))
				.findFirst().get().getContainerID()
		).getHost();

		return new DockerContainer(host, containerId);
	}

	public String dockerCmd(Function<String, String> cmdFunc) {
		LOGGER.debug("dockerCmd host = " + node + ", containerId = " + containerId);
		return node.executeCommand("sudo " + cmdFunc.apply(containerId)).trim();
	}

	public void dockerCmd(Function<String, String> cmdFunc, OpenShiftNode.CommandResultConsumer resultConsumer) {
		LOGGER.debug("dockerCmd host = " + node + ", containerId = " + containerId);
		node.executeCommand(true, "sudo " + cmdFunc.apply(containerId), resultConsumer);
	}

	private boolean dockerCmdReturningContainerIdOnSuccess(
			Function<String, String> cmdFunc) {
		final AtomicReference<String> expectedResult = new AtomicReference<>(
				null);
		String result = dockerCmd(containerId -> {
			expectedResult.set(containerId);
			return cmdFunc.apply(containerId);
		}).trim();
		return result.equals(expectedResult.get());
	}

	public void dockerPause() {
		boolean ret = dockerCmdReturningContainerIdOnSuccess(containerId -> "docker pause " + containerId);
		Assertions.assertThat(ret).isEqualTo(true).as("Error executing docker pause");
	}

	public void dockerUnPause() {
		boolean ret = dockerCmdReturningContainerIdOnSuccess(containerId -> "docker unpause " + containerId);
		Assertions.assertThat(ret).isEqualTo(true).as("Error executing docker unpause");
	}

	public boolean isRunning() {
		return Boolean.parseBoolean(dockerCmd(containerId -> " docker inspect -f '{{.State.Running}}'  " + containerId));
	}

	public void dockerJvmKill() {
		String javaPid = dockerCmd(
				containerId -> "docker exec "
						+ containerId
						+ " /bin/sh -c "
						+ "'(for i in `ls /proc/*/exe`; do j=`readlink $i`; echo $i $j; done) | grep java | cut -d'/' -f3'")
				.trim().replace('\n', ' ');

		LOGGER.debug("dockerJvmKill java pid: " + javaPid);

		String ret = dockerCmd(
				containerId -> "docker exec " + containerId + " kill -9 "
						+ javaPid).trim();

		LOGGER.debug("dockerJvmKill kill output:" + ret);
	}

	public void dockerKill(String signal) {
		boolean ret = dockerCmdReturningContainerIdOnSuccess(containerId -> "docker kill --signal=" + signal + " " + containerId);
		Assertions.assertThat(ret).isEqualTo(true).as("Error executing docker kill");
	}

	public void dockerKill() {
		dockerKill("KILL");
	}

	public void dockerStop() {
		boolean ret = dockerCmdReturningContainerIdOnSuccess(containerId -> "docker stop " + containerId);
		Assertions.assertThat(ret).isEqualTo(true).as("Error executing docker stop");
	}

	@Deprecated // use dockerLogs(OpenShiftNode.CommandResultConsumer resultConsumer) to not copy logs in memory in Strings
	public String dockerLogs() {
		return dockerCmd(containerId -> "docker logs " + containerId);
	}

	public void dockerLogs(OpenShiftNode.CommandResultConsumer resultConsumer) {
		dockerCmd(containerId -> "docker logs " + containerId, resultConsumer);
	}

	public String findLineInLog(final String regex) {
		LOGGER.info("Parse logs");
		final AtomicReference<String> soughtLine = new AtomicReference<>();
		final Pattern pattern = Pattern.compile(".*" + regex + ".*", Pattern.CASE_INSENSITIVE);
		dockerLogs(stream -> IOUtils.lineIterator(stream, StandardCharsets.UTF_8.toString())
				.forEachRemaining(line -> {
					Matcher m = pattern.matcher(line);
					if (m.matches()) {
						soughtLine.set(line);
					}
				}));
		return soughtLine.get();
	}
	
	public OpenShiftNode getOpenShiftNode() {
		return node;
	}

	public String getContainerId() {
		return containerId;
	}

	@Override
	public String toString() {
		return "DockerContainer [node=" + node + ", containerId=" + containerId
				+ "]";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DockerContainer that = (DockerContainer) o;

		if (node != null ? !node.getHostname().equals(that.node.getHostname()) : that.node != null) return false;
		return !(containerId != null ? !containerId.equals(that.containerId) : that.containerId != null);

	}

	@Override
	public int hashCode() {
		int result = node != null ? node.getHostname().hashCode() : 0;
		result = 31 * result + (containerId != null ? containerId.hashCode() : 0);
		return result;
	}
}
