package cz.xtf.openshift;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.openshift.builder.ApplicationBuilder;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.wait.WaitUtil;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Role;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.Route;

public class OpenshiftApplication {
	public static final long BUILD_TIMEOUT = 10L;
	public static final TimeUnit BUILD_TIMEOUT_UNIT = TimeUnit.MINUTES;
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenshiftApplication.class);
	private final OpenshiftUtil openshift;
	private final String name;

	private List<Secret> secrets = new LinkedList<>();
	private List<ServiceAccount> serviceAccounts = new LinkedList<>();
	private List<ImageStream> imageStreams = new LinkedList<>();
	private List<BuildConfig> buildConfigs = new LinkedList<>();
	private List<PersistentVolumeClaim> persistentVolumeClaims = new LinkedList<>();
	private List<DeploymentConfig> deploymentConfigs = new LinkedList<>();
	private List<Service> services = new LinkedList<>();
	private List<Endpoints> endpoints = new LinkedList<>();
	private List<Route> routes = new LinkedList<>();
	private List<ConfigMap> configMaps = new LinkedList<>();
	private List<HorizontalPodAutoscaler> autoScalers = new LinkedList<>();
	private List<Role> roles = new LinkedList<>();
	private List<RoleBinding> roleBindings = new LinkedList<>();

	private BuildConfig buildConfig;
	private DeploymentConfig mainDeployment;
	private Service mainService;
	private Route mainRoute;

	private Optional<String> serviceAccountName = Optional.empty();

	public OpenshiftApplication(ApplicationBuilder builder) {
		this.openshift = OpenshiftUtil.getInstance();
		this.name = builder.getName();

		// add secret
		secrets.addAll(builder.getSecrets());

		// add serviceaccount
		// TODO allow creating sa from ApplicationBuilder

		// add image config
		imageStreams.addAll(builder.buildImageStreams());

		// add build config
		buildConfigs.addAll(builder.buildBuildConfigs());
		buildConfig = findMainResource(buildConfigs, name + "-build");

		// add persistent volume claims
		// TODO allow creating pvc from ApplicationBuilder

		// add deployment config
		deploymentConfigs.addAll(builder.buildDeploymentConfigs());
		mainDeployment = findMainResource(deploymentConfigs, name);

		// add endpoints
		endpoints.addAll(builder.buildEndpoints());

		// add services
		services.addAll(builder.buildServices());
		mainService = findMainResource(services, name + "-service");

		// add routes
		routes.addAll(builder.buildRoutes());
		mainRoute = findMainResourceWithPrefix(routes, name);

		// add configMaps
		configMaps.addAll(builder.buildConfigMaps());

		// add autoscaler
		autoScalers.addAll(builder.buildAutoScalers());

		// add roles
		roles.addAll(builder.buildRoles());

		// add role bindings
		roleBindings.addAll(builder.buildRoleBindings());
	}

	public OpenshiftApplication createServiceAccountFromSecrets(final String accountName) {
		serviceAccountName = Optional.ofNullable(accountName);
		return this;
	}

	private void createServiceAccount() {
		serviceAccountName
				.ifPresent(accountName -> {
					LOGGER.debug("Creating service account {}", accountName);
					openshift.createServiceAccount(new ServiceAccountBuilder()
							.withNewMetadata()
							.withName(accountName)
							.endMetadata()
							.withSecrets(secrets.stream().map(secret -> new ObjectReferenceBuilder()
									.withKind(secret.getKind())
									.withName(secret.getMetadata().getName())
									.build()
							).collect(Collectors.toList())).build());
				});
	}

	public void deploy() {
		deploy(BUILD_TIMEOUT, BUILD_TIMEOUT_UNIT);
	}

	public void deploy(long timeout, TimeUnit timeUnit) {
		deployWithoutBuild();

		LOGGER.debug("Building application {} through manual build and waiting for results", name);
		openshift.waitForBuildCompletion(triggerManualBuild(), timeout, timeUnit, true);
	}

	public void deployWithoutBuild() {
		LOGGER.debug("Deploying application {}", name);

		// keep the order of deployment
		secrets = secrets.stream().map(openshift::createSecret).collect(Collectors.toList());
		serviceAccounts = serviceAccounts.stream().map(openshift::createServiceAccount).collect(Collectors.toList());
		imageStreams = imageStreams.stream().map(openshift::createImageStream).collect(Collectors.toList());
		buildConfigs = buildConfigs.stream().map(openshift::createBuildConfig).collect(Collectors.toList());
		persistentVolumeClaims = persistentVolumeClaims.stream().map(openshift::createPersistentVolumeClaim).collect(Collectors.toList());
		services = services.stream().map(openshift::createService).collect(Collectors.toList());
		final List<DeploymentConfig> syncDeployments = deploymentConfigs.stream().filter(x -> x.getMetadata().getLabels().containsKey(DeploymentConfigBuilder.SYNCHRONOUS_LABEL))
				.sorted((dc1, dc2) -> {
					final int labelDc1 = Integer.parseInt(dc1.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL));
					final int labelDc2 = Integer.parseInt(dc2.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL));
					return labelDc1 - labelDc2;
				}).map(x -> {
					final String deploymentId = x.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL);
					final DeploymentConfig dc = openshift.createDeploymentConfig(x);
					try {
						LOGGER.info("Waiting for a startup of pod with syncId '{}'", deploymentId);
						WaitUtil.waitFor(WaitUtil.isAPodReady(DeploymentConfigBuilder.SYNCHRONOUS_LABEL, deploymentId));
						// Let's wait few more seconds for pods that are ready but not fully ready
						TimeUnit.SECONDS.sleep(20);
					} catch (Exception e) {
						throw new IllegalStateException("Timeout while waiting for dpeloyment of " + dc.getMetadata().getName());
					}
					return dc;
				}).collect(Collectors.toList());
		deploymentConfigs = deploymentConfigs.stream().filter(x -> !x.getMetadata().getLabels().containsKey(DeploymentConfigBuilder.SYNCHRONOUS_LABEL)).map(openshift::createDeploymentConfig).collect(Collectors.toList());
		deploymentConfigs.addAll(syncDeployments);
		endpoints = endpoints.stream().map(openshift::createEndpoint).collect(Collectors.toList());
		routes = routes.stream().map(openshift::createRoute).collect(Collectors.toList());
		configMaps = configMaps.stream().map(openshift::createConfigMap).collect(Collectors.toList());
		autoScalers = autoScalers.stream().map(openshift::createHorizontalPodAutoscaler).collect(Collectors.toList());

		// find main resources
		if (buildConfig != null) {
			buildConfig = findMainResource(buildConfigs, buildConfig.getMetadata().getName());
		}
		if (mainDeployment != null) {
			mainDeployment = findMainResource(deploymentConfigs, mainDeployment.getMetadata().getName());
		}
		if (mainService != null) {
			mainService = findMainResource(services, mainService.getMetadata().getName());
		}
		if (mainRoute != null) {
			mainRoute = findMainResource(routes, mainRoute.getMetadata().getName());
		}

		createServiceAccount();

		roles = roles.stream().map(openshift::createRole).collect(Collectors.toList());
		roleBindings = roleBindings.stream().map(openshift::createRoleBinding).collect(Collectors.toList());
	}

	public Build triggerManualBuild() {
		if (buildConfig == null) {
			throw new IllegalStateException("Default build config was not found in application " + name);
		}

		return openshift.startBuild(buildConfig);
	}

	/**
	 * Change enviroment variable in deployment config to new value
	 *
	 * @param variableName  name of the enviroment variable to be changed
	 * @param variableValue new/added value to the enviroment variable (depends on the last flag )
	 * @param overwrite     distinct if value is appended to current value or overwritten
	 */
	public void updateEnviromentVariable(final String variableName, final String variableValue, boolean overwrite) {
		if (mainDeployment == null) {
			throw new IllegalStateException("Main Deployment Config was not found in application " + name);
		}

		openshift.updateEnviromentVariable(mainDeployment, variableName, variableValue, overwrite);
	}

	public String getHostName() {
		if (mainRoute == null) {
			throw new IllegalStateException("Default route was not found in application " + name);
		}

		return mainRoute.getSpec().getHost();
	}

	private <T extends HasMetadata> T findMainResource(Collection<T> resources, String defaultName) {
		if (resources.size() == 1) {
			return resources.iterator().next();
		} else {
			// try finding default resource
			Optional<T> match = resources.stream().filter(resource -> defaultName.equals(resource.getMetadata().getName())).findFirst();
			if (match.isPresent()) {
				return match.get();
			} else {
				LOGGER.debug("No default resource found '{}'", defaultName);
				return null;
			}
		}
	}

	private <T extends HasMetadata> T findMainResourceWithPrefix(Collection<T> resources, String defaultName) {
		if (resources.size() == 1) {
			return resources.iterator().next();
		} else {
			// try finding default resource
			Optional<T> match = resources.stream().filter(resource -> resource.getMetadata().getName().startsWith(defaultName)).findFirst();
			if (match.isPresent()) {
				return match.get();
			} else {
				LOGGER.debug("No default resource found '{}'", defaultName);
				return null;
			}
		}
	}
}
