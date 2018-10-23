package cz.xtf.openshift.messaging;

import org.apache.commons.lang3.StringUtils;

import org.assertj.core.api.Assertions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import cz.xtf.TestConfiguration;
import cz.xtf.git.GitProject;
import cz.xtf.openshift.ActiveMQTransport;
import cz.xtf.openshift.OpenshiftApplication;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.PVCBuilder;
import cz.xtf.openshift.builder.PortBuilder;
import cz.xtf.openshift.builder.RouteBuilder;
import cz.xtf.openshift.builder.ServiceBuilder;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.wait.WaitUtil;

/**
 * @author David Simansky | dsimansk@redhat.com
 */
public class AmqStandaloneBuilder {
	private static final String AMQ_IMAGE = ImageRegistry.get().amq();
	private ApplicationBuilder appBuilder;
	private String appName;
	private String podName;
	private Set<ActiveMQTransport> transports;
	private List<Consumer<ContainerBuilder>> containerBuilderFuncs;
	private int replicas = 1;
	private String suffix = "";
	private boolean enableNodePort = false;
	private boolean enableDrainer = false;
	private boolean customBuild = false;
	private String claimName;
	private long deployTimeout = 5 * 60 * 1000L;
	private String meshDiscoveryType;
	private String meshServiceName;
	private String meshServiceNamespace;

	public AmqStandaloneBuilder(String appName) {
		this(appName, null);
	}

	public AmqStandaloneBuilder(String appName, String podName) {
		this.podName = podName;
		this.appName = appName;
		if (StringUtils.isBlank(podName)) {
			this.podName = appName + "-pod";
		}
		this.meshDiscoveryType = "kube";
		this.meshServiceName = "amq-mesh";
		this.meshServiceNamespace = TestConfiguration.masterNamespace();
		this.appBuilder = new ApplicationBuilder(this.appName);
		// init desired DC, reference by this.appName later in the code
		this.appBuilder.deploymentConfig(this.appName, this.podName, false);

		transports = new HashSet<>();
		containerBuilderFuncs = new LinkedList<>();
	}

	public AmqStandaloneBuilder transport(ActiveMQTransport... transports) {
		Arrays.asList(transports).stream().forEach(this.transports::add);
		return this;
	}

	public AmqStandaloneBuilder container(Consumer<ContainerBuilder> func) {
		containerBuilderFuncs.add(func);
		return this;
	}

	public AmqStandaloneBuilder nfs(String volumeName, String nfsServer, String serverPath, String mountPath, boolean readOnly) {
		container(cb -> cb.addVolumeMount(volumeName, mountPath, readOnly).pod().addNFSVolume(volumeName, nfsServer, serverPath));
		return this;
	}

	public AmqStandaloneBuilder nfs(String volumeName, String serverPath, String mountPath, boolean readOnly) {
		nfs(volumeName, TestConfiguration.nfsServer(), serverPath, mountPath, readOnly);
		return this;
	}

	//TODO: refactor to generic addVolume method
	public AmqStandaloneBuilder pvc(String volumeName, String claimName, String mountPath, boolean readOnly) {
		container(cb -> cb.addVolumeMount(volumeName, mountPath, readOnly).pod().addPersistenVolumeClaim(volumeName, claimName));
		return this;
	}

	public AmqStandaloneBuilder withDefaultPVC() {
		String claimName = "amq-claim-" + Integer.toString(Math.abs(new Random().nextInt()), 36);
		this.claimName = claimName;
		OpenshiftUtil.getInstance().createPersistentVolumeClaim(new PVCBuilder(claimName).accessRWX().storageSize("1Gi").build());
		pvc("store", claimName, "/opt/amq/data", false);
		return this;
	}

	public AmqStandaloneBuilder withMeshConf(String discoveryType, String serviceName, String serviceNamespace) {
		this.meshDiscoveryType = discoveryType;
		this.meshServiceName = serviceName;
		this.meshServiceNamespace = serviceNamespace;
		container(cb ->
				cb.envVar("AMQ_MESH_DISCOVERY_TYPE", discoveryType)
						.envVar("AMQ_MESH_SERVICE_NAME", serviceName)
						.envVar("AMQ_MESH_SERVICE_NAMESPACE", serviceNamespace)
						.pod().addLabel("topology", "mesh"));
        	 return this;
        }

	public AmqStandaloneBuilder withDefaultMeshConf() {
		return withMeshConf("kube", "amq-mesh", OpenshiftUtil.getInstance().getContext().getNamespace());
	}

	public AmqStandaloneBuilder withResourcesSuffix(String suffix) {
		this.suffix = suffix;
		return this;
	}

	public AmqStandaloneBuilder withReplicas(int replicas) {
		this.replicas = replicas;
		appBuilder.deploymentConfig(this.appName).setReplicas(this.replicas);
		return this;
	}

	public AmqStandaloneBuilder withNodePort() {
		this.enableNodePort = true;
		return this;
	}

	public AmqStandaloneBuilder withDrainerPod() {
		this.enableDrainer = true;
		return this;
	}

	public AmqStandaloneBuilder withDeployTimeout(long timeout){
		this.deployTimeout = timeout;
		return this;
	}

	public AmqStandaloneBuilder withCustomBuild(boolean useSti, GitProject gitProject, String appName, String imageUrl) {
		this.customBuild = true;
		this.appBuilder.imageStream().addLabel("name", appName);
		if (useSti) {
			appBuilder.buildConfig().sti().fromDockerImage(imageUrl);
		} else {
			appBuilder.buildConfig().docker().fromDockerImage(imageUrl);
		}
		this.appBuilder.buildConfig().gitSource(gitProject.getHttpUrl());
		this.appBuilder.buildConfig().setOutput(appName + "-image");

		this.appBuilder.deploymentConfig(this.appName)
				.onImageChange().podTemplate().container(this.appName).fromImage(appName + "-image");

		return this;
	}

	public AmqStandaloneBuilder deploy() {
		//build services&routes
		if (transports.isEmpty()) {
			//add all transports by default
			transport(ActiveMQTransport.OPENWIRE, ActiveMQTransport.AMQP, ActiveMQTransport.MQTT, ActiveMQTransport.STOMP);
		}
		ServiceBuilder service = appBuilder.service("amq-service" + suffix).addContainerSelector("name", podName);
		transports.stream().forEach(t -> service.ports(new PortBuilder(t.toString()).port(t.getPort()).targetPort(t.getPort()).build()));

		ServiceBuilder secureService = appBuilder.service("amq-sec-service" + suffix).addContainerSelector("name", podName);
		transports.stream().forEach(t -> secureService.ports(new PortBuilder(t + "-ssl").port(t.getSslPort()).targetPort(t.getSslPort()).build()));
		transports.stream().forEach(t -> appBuilder.route(t + "-sec-route" + suffix)
				.passthrough()
				.forService("amq-sec-service" + suffix)
				.targetPort(t.getSslPort())
				.exposedAsHost(RouteBuilder.createHostName("amq-" + t + suffix)));

		if (enableNodePort) {
			service.nodePort();
			secureService.nodePort();
		}

		//enable default ssl config in the A-MQ image
		//enable Jolokia by default
		//enable default readiness probe
		appBuilder.deploymentConfig(this.appName).podTemplate().container(this.appName).envVar("AMQ_KEYSTORE_TRUSTSTORE_DIR", "/opt/amq/conf")
				.envVar("AMQ_KEYSTORE", "broker.ks")
				.envVar("AMQ_TRUSTSTORE", "broker.ts")
				.envVar("AMQ_KEYSTORE_PASSWORD", "password")
				.envVar("AMQ_TRUSTSTORE_PASSWORD", "password")
				.port(8778, "jolokia")
				.addReadinessProbe().createExecProbe("/bin/bash", "-c", "/opt/amq/bin/readinessProbe.sh");

		for (Consumer<ContainerBuilder> cbf : containerBuilderFuncs) {
			cbf.accept(appBuilder.deploymentConfig(this.appName).podTemplate().container(this.appName));
		}

		if (customBuild) {
			// custom build: appName + "-image"
			OpenshiftApplication application = new OpenshiftApplication(appBuilder);
			application.deploy();
		} else {
			// by default set AMQ_IMAGE
			appBuilder.deploymentConfig(this.appName)
					.onConfigurationChange()
					.podTemplate()
					.container(this.appName)
					.fromImage(AMQ_IMAGE);
			this.appBuilder.buildApplication().deployWithoutBuild();
		}
		if (enableDrainer) {
			String drainerName = this.appName + "-drainer";
			ApplicationBuilder drainerBuilder = new ApplicationBuilder(drainerName);
			drainerBuilder.deploymentConfig(drainerName, drainerName, false)
					.onConfigurationChange()
					.podTemplate()
					.container(drainerName)
					.addCommand("/opt/amq/bin/drain.sh")
					.fromImage(AMQ_IMAGE);

			drainerBuilder.deploymentConfig(drainerName).podTemplate().container(drainerName)
					.envVar("AMQ_MESH_DISCOVERY_TYPE", meshDiscoveryType)
					.envVar("AMQ_MESH_SERVICE_NAME", meshServiceName)
					.envVar("AMQ_MESH_SERVICE_NAMESPACE", meshServiceNamespace);

			drainerBuilder.deploymentConfig(drainerName).podTemplate().container(drainerName)
					.addVolumeMount("store", "/opt/amq/data", false)
					.pod()
					.addPersistenVolumeClaim("store", this.claimName);

			drainerBuilder.buildApplication().deployWithoutBuild();
		}

		try {
			if (!WaitUtil.waitFor(WaitUtil.areNPodsReady(podName, replicas), WaitUtil.hasPodRestarted(podName), 1000L, deployTimeout)) {
				Assertions.fail("Pod " + podName + " has restarted, presumably failing.");
			}
		} catch (InterruptedException x) {
			throw new RuntimeException("Interrupted during wait for deployment");
		} catch (TimeoutException x) {
			throw new RuntimeException("Timeout waiting for " + podName + " application deployment");
		}

		return this;
	}

	/**
	 * Method to create service with custom properties
	 */
	public AmqStandaloneBuilder service(String name, int port, String selector) {
		appBuilder.service(name)
				.ports(new PortBuilder(name).port(port).targetPort(port).build())
				.addContainerSelector("name", selector);
		return this;
	}
}
