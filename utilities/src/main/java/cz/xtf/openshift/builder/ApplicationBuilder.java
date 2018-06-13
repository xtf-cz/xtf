package cz.xtf.openshift.builder;

import cz.xtf.openshift.OpenShiftAuxiliary;
import cz.xtf.openshift.OpenshiftApplication;
import cz.xtf.openshift.builder.buildconfig.SourceBuildStrategy;
import cz.xtf.openshift.builder.pod.ContainerBuilder;
import cz.xtf.openshift.external.ExternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.build.XTFBuild;
import cz.xtf.build.BuildDefinition;
import cz.xtf.build.BuildManagerV2;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscalerBuilder;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Role;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.Route;

public class ApplicationBuilder {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationBuilder.class);

	private final String applicationName;

	private boolean sslEnabled = false;

	private final Set<RouteBuilder> routes = new HashSet<>();
	private final Set<ServiceBuilder> services = new HashSet<>();
	private final Set<ImageStreamBuilder> images = new HashSet<>();
	private final Set<DeploymentConfigBuilder> deployments = new HashSet<>();
	private final Set<BuildConfigBuilder> builds = new HashSet<>();
	private final Map<String, SecretBuilder> secrets = new HashMap<>();
	private List<ExternalService> externalServices = new ArrayList<>();
	private final Set<ConfigMapWithPropertyFilesBuilder> configMaps = new HashSet<>(); 
	private final Set<RoleBuilder> roles = new HashSet<>(); 
	private final Set<RoleBindingBuilder> roleBindings = new HashSet<>(); 

	private SourceBuildStrategy s2iStrategy;
	private Optional<AutoScaleConfig> autoScale = Optional.empty();
	
	/**
	 * Creates empty Application with only name set.
	 *
	 * @param name
	 * 		name of the application
	 */
	public ApplicationBuilder(String name) {
		this.applicationName = name;
	}

	/**
	 * Creates complete Application (image repository, build config, deployment
	 * config, service, route).
	 *
	 * @param name
	 * 		name of the application
	 * @param gitRepo
	 * 		repository to use for STI
	 * @param baseImage
	 * 		image to use as a base for STI
	 * @param useMavenProxy
	 *	  an option to overwrite system-wide setting for Maven proxy usage
	 */
	public ApplicationBuilder(String name, String gitRepo, String baseImage, boolean useMavenProxy) {
		this(name);

		// add default resources
		imageStream().addLabel("name", applicationName);
		s2iStrategy = buildConfig()
				.gitSource(gitRepo)
				.setOutput(applicationName + "-image")
				.sti()
					.forcePull(true)
					.fromDockerImage(baseImage);
		if (useMavenProxy) {
			s2iStrategy.addEnvVariable("MAVEN_MIRROR_URL", TestConfiguration.mavenProxyURL());
		}
		deploymentConfig();
		service().setContainerPort(8080);
		route();
	}

	/**
	 * Creates complete Application (image repository, build config, deployment
	 * config, service, route).
	 *
	 * @param name
	 * 		name of the application
	 * @param gitRepo
	 * 		repository to use for STI
	 * @param baseImage
	 * 		image to use as a base for STI
	 */
	public ApplicationBuilder(String name, String gitRepo, String baseImage) {
		this(name, gitRepo, baseImage, TestConfiguration.mavenProxyEnabled());
	}
	
	public ApplicationBuilder(String name, XTFBuild build) {
		this(name, build.getBuildDefinition());
	}
	
	public ApplicationBuilder(String name, BuildDefinition build) {
		this(name);
		
		BuildManagerV2.get().deployBuild(build);
		deploymentConfig(applicationName).onConfigurationChange()
				.podTemplate().container()
				.fromImage(TestConfiguration.buildNamespace(), build.getName())
				.port(8080);
		service().setContainerPort(8080);
		route();
	}
	
	public String getName() {
		return applicationName;
	}

	// image stream
	public ImageStreamBuilder imageStream() {
		return imageStream(applicationName + "-image");
	}

	public ImageStreamBuilder imageStream(String name) {
		ImageStreamBuilder builder;
		Optional<ImageStreamBuilder> orig = images.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create image stream that already exists.");
			builder = orig.get();
		} else {
			builder = new ImageStreamBuilder(this, name);
			images.add(builder);
		}

		return builder;
	}

	// build config
	public BuildConfigBuilder buildConfig() {
		return buildConfig(applicationName + "-build");
	}

	public BuildConfigBuilder buildConfig(String name) {
		BuildConfigBuilder builder;
		Optional<BuildConfigBuilder> orig = builds.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create deployment config that already exists.");
			builder = orig.get();
		} else {
			builder = new BuildConfigBuilder(this, name);
			builds.add(builder);
		}

		return builder;
	}

	// deployment config

	/**
	 * Gets default deploymentConfig (i.e. named
	 * '{applicationName}'). If default deployment config does not
	 * exists, it is initiated with default values.
	 *
	 * @return Default deployment config.
	 */
	public DeploymentConfigBuilder deploymentConfig() {
		return deploymentConfig(applicationName);
	}

	/**
	 * Gets deployment config with a given name. If no such deployment config
	 * exists, it is initiated with default values.
	 *
	 * @param name
	 * 		name of the deployment config
	 * @return Deployment config with given name.
	 */
	public DeploymentConfigBuilder deploymentConfig(String name) {
		return deploymentConfig(name, applicationName, true);
	}

	public DeploymentConfigBuilder deploymentConfig(String name, String podName, boolean preConfigurePod) {
		DeploymentConfigBuilder builder;
		Optional<DeploymentConfigBuilder> orig = deployments.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create deployment config that already exists.");
			builder = orig.get();
		} else {
			builder = new DeploymentConfigBuilder(this, name, podName);
			if (preConfigurePod) {
				builder.onConfigurationChange();
				ContainerBuilder container = builder.onImageChange().podTemplate().container();
				container.fromImage(imageStream().getName()).port(8080);
	
				if (sslEnabled) {
					container.port(8443);
				}
			}
			deployments.add(builder);
		}

		return builder;
	}

	// services

	/**
	 * Gets default service (i.e. named '{applicationName}-service'). If default
	 * service does not exists, it is initiated with default values.
	 *
	 * @return Default service.
	 */
	public ServiceBuilder service() {
		return service(applicationName + "-service");
	}

	/**
	 * Gets the service with given name. If no such service exists, it is
	 * initiated with default values.
	 *
	 * @param serviceName
	 * 		name of the service
	 * @return Service with a given name.
	 */
	public ServiceBuilder service(String serviceName) {
		return addService(serviceName, false);
	}

	public ServiceBuilder secureService() {
		return secureService(applicationName + "-secure-service");
	}

	public ServiceBuilder secureService(String serviceName) {
		sslEnabled = true;
		return addService(serviceName, true);
	}

	// routes

	/**
	 * Gets default route (i.e. named '{applicationName}'). If default
	 * route does not exists, it is initiated with default values.
	 *
	 * @return Default route.
	 */
	public RouteBuilder route() {
		return route(applicationName);
	}

	/**
	 * Gets the route with given name. If no such route exists, it is initiated
	 * with default values.
	 *
	 * @param routeName
	 * 		name of the route
	 * @return Route with given name.
	 */
	public RouteBuilder route(String routeName) {
		RouteBuilder result;
		Optional<RouteBuilder> orig = routes.stream().filter(r -> r.getName().startsWith(routeName)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create route that already exists.");
			result = orig.get();
		} else {
			result = new RouteBuilder(this, routeName);
			result.forService(applicationName + "-service");

			routes.add(result);
		}

		return result;
	}

	// roles

	/**
	 * Gets default role (i.e. named '{applicationName}'). If default
	 * role does not exists, it is initiated with default values.
	*/
	public RoleBuilder role() {
	   return role(applicationName);
	}

	/**
	 * Gets the role with given name. If no such role exists, it is initiated
	 * with default values.
	 */
	public RoleBuilder role(String roleName) {
		RoleBuilder result;
		Optional<RoleBuilder> orig = roles.stream().filter(r -> r.getName().startsWith(roleName)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create role {} that already exists.", roleName);
			result = orig.get();
		} else {
			result = new RoleBuilder(this, roleName);
			roles.add(result);
		}

		return result;
	}

	// role bindings

	/**
	 * Gets default role binding (i.e. named '{applicationName}'). If default
	 * role binding does not exists, it is initiated with default values.
	 */
	public RoleBindingBuilder roleBinding() {
		return roleBinding(applicationName);
	}

	/**
	 * Gets the role binding with given name. If no such role binding exists, it is initiated
	 * with default values.
	 */
	public RoleBindingBuilder roleBinding(String roleBindingName) {
		RoleBindingBuilder result;
		Optional<RoleBindingBuilder> orig = roleBindings.stream().filter(r -> r.getName().startsWith(roleBindingName)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create role {} that already exists.", roleBindingName);
			result = orig.get();
		} else {
			result = new RoleBindingBuilder(this, roleBindingName);
			roleBindings.add(result);
		}

		return result;
	}

	// config maps

	/**
	 * Gets default ConfigMap (i.e. named '{applicationName}'). If default
	 * ConfigMap does not exist, it is created.
	 *
	 * @return Default ConfigMap.
	 */
	public ConfigMapWithPropertyFilesBuilder configMap() {
		return configMap(applicationName);
	}

	/**
	 * Gets a ConfigMap route with given name. If no such ConfigMap exists, it is created
	 *
	 * @param configMapName
	 * 		name of the ConfigMap
	 * @return ConfigMap with given name.
	 */
	public ConfigMapWithPropertyFilesBuilder configMap(final String configMapName) {
		ConfigMapWithPropertyFilesBuilder result;
		Optional<ConfigMapWithPropertyFilesBuilder> orig = configMaps.stream().filter(r -> r.getName().equals(configMapName)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create configMap that already exists.");
			result = orig.get();
		} else {
			result = new ConfigMapWithPropertyFilesBuilder(configMapName);
			configMaps.add(result);
		}
		return result;
	}

	public List<ImageStream> buildImageStreams() {
		return images.stream().map(ImageStreamBuilder::build).collect(Collectors.toList());
	}

	public List<BuildConfig> buildBuildConfigs() {
		return builds.stream().map(BuildConfigBuilder::build).collect(Collectors.toList());
	}

	public List<DeploymentConfig> buildDeploymentConfigs() {
		return deployments.stream().map(DeploymentConfigBuilder::build)
				.collect(Collectors.toList());
	}

	public List<Service> buildServices() {
		externalServices.stream().forEach(x -> x.configureService(this));
		return services.stream().map(ServiceBuilder::build).collect(Collectors.toList());
	}

	public List<Endpoints> buildEndpoints() {
		return externalServices.stream().map(x -> x.getEndpoints(this)).collect(Collectors.toList());
	}

	public List<Route> buildRoutes() {
		return routes.stream().map(RouteBuilder::build).collect(Collectors.toList());
	}

	public List<Role> buildRoles() {
		return roles.stream().map(RoleBuilder::build).collect(Collectors.toList());
	}

	public List<RoleBinding> buildRoleBindings() {
		return roleBindings.stream().map(RoleBindingBuilder::build).collect(Collectors.toList());
	}

	public List<ConfigMap> buildConfigMaps() {
		return configMaps.stream().map(ConfigMapWithPropertyFilesBuilder::build).collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	public List<HorizontalPodAutoscaler> buildAutoScalers() {
		return (List<HorizontalPodAutoscaler>) autoScale.map(x -> x == null ? Collections.EMPTY_LIST : Arrays.asList(x.build())).orElse(Collections.EMPTY_LIST);
	}

	public List<HasMetadata> build() {
		List<HasMetadata> result = new LinkedList<>();

		// build ImageRepository
		result.addAll(buildImageStreams());

		// build BuildConfig
		result.addAll(buildBuildConfigs());

		// build DeploymentConfig
		result.addAll(buildDeploymentConfigs());

		// build Services
		result.addAll(buildServices());

		// build Route
		result.addAll(buildRoutes());

		// build ConfigMap
		result.addAll(buildConfigMaps());

		// create HPA scale
		autoScale.ifPresent(x -> result.add(x.build()));
		return result;
	}

	public OpenshiftApplication buildApplication() {
		return new OpenshiftApplication(this);
	}

	private ServiceBuilder addService(String serviceName, boolean secure) {
		ServiceBuilder result;
		Optional<ServiceBuilder> orig = services.stream().filter(b -> b.getName().equals(serviceName)).findFirst();
		if (orig.isPresent()) {
			LOGGER.debug("Trying to create service that already exists.");
			result = orig.get();
		} else {
			result = new ServiceBuilder(this, serviceName);
			result.addContainerSelector("name", applicationName);
			if (secure) {
				result.setPort(8443);
			}
			services.add(result);
		}

		return result;
	}

	public ApplicationBuilder addDatabases(OpenShiftAuxiliary... databases) {
		Arrays.stream(databases).forEach(aux -> {
				aux.configureDeployment(this);
				aux.configureApplicationDeployment(deploymentConfig());
		});
		return this;
	}

	public ApplicationBuilder addExternalServices(final ExternalService... externalServices) {
		Collections.addAll(this.externalServices, externalServices);
		return this;
	}
	public List<Secret> getSecrets() {
		return secrets.values().stream().map(SecretBuilder::build)
				.collect(Collectors.toList());
	}

	public Secret getSingleSecret() {
		if (secrets.size() != 1) {
			throw new IllegalArgumentException("Only one secret expected");
		}
		return getSecrets().get(0);
	}

	private SecretBuilder findSecretBuilder(final String secretName) {
		SecretBuilder sb = secrets.get(secretName);
		if (sb == null) {
			sb = new SecretBuilder(secretName);
			secrets.put(secretName, sb);
		}
		return sb;
	}

	public ApplicationBuilder addSecret(final String secretName, final String name, final byte[] value) {
		findSecretBuilder(secretName).addData(name, value);
		return this;
	}

	public ApplicationBuilder addSecret(final String secretName, final String name, InputStream value) {
		findSecretBuilder(secretName).addData(name, value);
		return this;
	}

	public ApplicationBuilder addSecret(final String name, InputStream value) {
		return addSecret(name, name, value);
	}

	public SourceBuildStrategy getS2iStrategy() {
		return s2iStrategy;
	}

	public ApplicationBuilder cpuAutoscale(final int targetUtilization, final int minReplicas, final int maxReplicas) {
		autoScale = Optional.of(new AutoScaleConfig(targetUtilization, minReplicas, maxReplicas));
		return this;
	}

	private class AutoScaleConfig {
		private final int targetCPUUtilization;
		private final int minReplicas;
		private final int maxReplicas;
		
		public AutoScaleConfig(int targetCPUUtilization, int minReplicas, int maxReplicas) {
			super();
			this.targetCPUUtilization = targetCPUUtilization;
			this.minReplicas = minReplicas;
			this.maxReplicas = maxReplicas;
		}

		public HasMetadata build() {
			return new HorizontalPodAutoscalerBuilder()
					.withNewMetadata().withName(applicationName).endMetadata()
					.withNewSpec()
						.withNewScaleTargetRef()
							.withKind("DeploymentConfig")
							.withName(deploymentConfig().getName())
						.endScaleTargetRef()
						.withTargetCPUUtilizationPercentage(targetCPUUtilization)
						.withMinReplicas(minReplicas).
						withMaxReplicas(maxReplicas)
					.endSpec()
				.build();
		}
	}
}
