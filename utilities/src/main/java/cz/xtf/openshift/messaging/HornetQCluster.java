package cz.xtf.openshift.messaging;

import cz.xtf.docker.DockerContainer;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.openshift.OpenShiftAuxiliary;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.VersionRegistry;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;

public class HornetQCluster implements OpenShiftAuxiliary, MessageBroker {

	private static final String POD_LABEL = "hq-cluster";
	private static final String DEFAULT_CLUSTER_PASSWORD = "MyCluster*123";
	private List<String> queues = new ArrayList<>();
	private List<String> topics = new ArrayList<>();
	private String name;

	public HornetQCluster(final String name) {
		this.name = name;
	}

	@Override
	public DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder, final boolean synchronous) {
		// NOP
		return null;
	}

	@Override
	public void configureApplicationDeployment(final DeploymentConfigBuilder dcBuilder) {
		final String mqPrefix = (VersionRegistry.get().eap().getMajorVersion().equals("6")) ? "HORNETQ" : "MQ";
		dcBuilder
				.podTemplate()
				.addLabel(POD_LABEL, name)
				.container()
				.envVar(mqPrefix + "_QUEUES", getQueueList())
				.envVar(mqPrefix + "_TOPICS", getTopicList())
				.envVar("HORNETQ_CLUSTER_PASSWORD", DEFAULT_CLUSTER_PASSWORD)
				.envVar("OPENSHIFT_KUBE_PING_LABELS", POD_LABEL + "=" + name)
				.envVar("OPENSHIFT_KUBE_PING_NAMESPACE",
						OpenshiftUtil.getInstance().getContext().getNamespace())
				.port(8888, "ping");
		OpenshiftUtil openshift = OpenshiftUtil.getInstance();
		OpenShiftNode.master().executeCommand(String.format("sudo oc policy add-role-to-user view system:serviceaccount:%s:default -n %s",
				openshift.getContext().getNamespace(),
				openshift.getContext().getNamespace()));
	}

	private String getTopicList() {
		return topics.stream().collect(Collectors.joining(","));
	}

	private String getQueueList() {
		return queues.stream().collect(Collectors.joining(","));
	}

	@Override
	public HornetQCluster withQueues(final String... queues) {
		Collections.addAll(this.queues, queues);
		return this;
	}

	@Override
	public HornetQCluster withTopics(final String... topics) {
		Collections.addAll(this.topics, topics);
		return this;
	}

	@Override
	public Pod getPod() {
		throw new UnsupportedOperationException();
	}

	@Override
	public DockerContainer getContainer() {
		throw new UnsupportedOperationException();
	}
}
