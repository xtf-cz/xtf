package cz.xtf.openshift.messaging;

import cz.xtf.openshift.builder.pod.PersistentVolumeClaim;
import org.apache.commons.lang3.StringUtils;

import cz.xtf.docker.DockerContainer;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.openshift.storage.DefaultStatefulAuxiliary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;

public class JBossAMQ extends DefaultStatefulAuxiliary implements MessageBroker {

	static final String SYMBOLIC_NAME = "jbamq";
	private static final String USERNAME = "mqUser";
	private static final String PASSWORD = "mqPassword";
	private static final String ADMIN_USERNAME = "adminUser";
	private static final String ADMIN_PASSWORD = "adminPassword";
	private static final int OPENWIRE_PORT = 61616;

	private final List<String> queues = new ArrayList<>();
	private final List<String> topics = new ArrayList<>();
	private Boolean tracking = null;
	private String jndiName;

	public JBossAMQ() {
		super(SYMBOLIC_NAME, "/opt/amq/data");
	}

	public JBossAMQ(final PersistentVolumeClaim pvc) {
		super(SYMBOLIC_NAME, "/opt/amq/data", pvc);
	}

	public Map<String, String> getImageVariables() {
		final Map<String, String> vars = new HashMap<>();
		vars.put("AMQ_USER", USERNAME);
		vars.put("AMQ_PASSWORD", PASSWORD);
		vars.put("AMQ_ADMIN_USERNAME", ADMIN_USERNAME);
		vars.put("AMQ_ADMIN_PASSWORD", ADMIN_PASSWORD);
		vars.put("AMQ_PROTOCOLS", "tcp");
		vars.put("AMQ_QUEUES", getQueueList());
		vars.put("AMQ_TOPIC", getTopicList());
		return vars;
	}

	@Override
	public DeploymentConfigBuilder configureDeployment(ApplicationBuilder appBuilder, final boolean synchronous) {
		final DeploymentConfigBuilder builder = appBuilder.deploymentConfig(
				SYMBOLIC_NAME, SYMBOLIC_NAME, false);
		builder.onConfigurationChange().podTemplate().container()
				.fromImage(ImageRegistry.get().amq())
				.envVars(getImageVariables())
				.port(OPENWIRE_PORT, "tcp")
				.port(8778, "jolokia");
		if (synchronous) {
			builder.onConfigurationChange();
			builder.synchronousDeployment();
		}
		if (isStateful) {
			storagePartition.configureApplicationDeployment(builder);
		}
		if(this.persistentVolClaim != null){
			builder.podTemplate().addPersistenVolumeClaim(
					this.persistentVolClaim.getName(),
					this.persistentVolClaim.getClaimName());
			builder.podTemplate().container().addVolumeMount(this.persistentVolClaim.getName(), dataDir, false);
		}

		appBuilder
				.service(SYMBOLIC_NAME + "-amq-tcp").setPort(OPENWIRE_PORT)
				.setContainerPort(OPENWIRE_PORT)
				.addContainerSelector("name", SYMBOLIC_NAME);

		return builder;
	}

	@Override
	public void configureApplicationDeployment(final DeploymentConfigBuilder dcBuilder) {
		String mqServiceMapping = dcBuilder.podTemplate().container().getEnvVars()
				.getOrDefault("MQ_SERVICE_PREFIX_MAPPING", "");
		if (mqServiceMapping.length() != 0) {
			mqServiceMapping = mqServiceMapping.concat(",");
		}
		dcBuilder.podTemplate().container()
				.envVar("MQ_SERVICE_PREFIX_MAPPING",
						mqServiceMapping.concat(String.format("%s-amq=%S",
								SYMBOLIC_NAME, SYMBOLIC_NAME)))
				.envVar(getEnvVarName("USERNAME"), USERNAME)
				.envVar(getEnvVarName("PASSWORD"), PASSWORD)
				.envVar(getEnvVarName("QUEUES"), getQueueList())
				.envVar(getEnvVarName("TOPICS"), getTopicList())
				.envVar(getEnvVarName("PROTOCOL"), "tcp");

		if (tracking != null) {
			dcBuilder.podTemplate().container()
					.envVar(getEnvVarName("TRACKING"), tracking.booleanValue() ? "true" : "false");
		}

		if (StringUtils.isNotBlank(jndiName)) {
			dcBuilder.podTemplate().container().envVar(getEnvVarName("JNDI"), jndiName);
		}
	}

	private String getTopicList() {
		return topics.stream().collect(Collectors.joining(","));
	}

	private String getQueueList() {
		return queues.stream().collect(Collectors.joining(","));
	}

	@Override
	public Pod getPod() {
		return OpenshiftUtil.getInstance().findNamedPod(SYMBOLIC_NAME);
	}

	@Override
	public DockerContainer getContainer() {
		return DockerContainer.createForPod(getPod(), SYMBOLIC_NAME);
	}

	@Override
	public JBossAMQ withQueues(final String... queues) {
		Collections.addAll(this.queues, queues);
		return this;
	}

	@Override
	public JBossAMQ withTopics(final String... topics) {
		Collections.addAll(this.topics, topics);
		return this;
	}

	public JBossAMQ withJndiName(final String jndiName) {
		this.jndiName = jndiName;
		return this;
	}

	public JBossAMQ withTracking(final boolean tracking) {
		this.tracking = tracking;
		return this;
	}

	public String getEnvVarName(final String name) {
		return String.format("%S_%S", SYMBOLIC_NAME, name);
	}

}
