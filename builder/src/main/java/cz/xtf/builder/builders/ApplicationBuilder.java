package cz.xtf.builder.builders;

import cz.xtf.builder.OpenShiftApplication;
import cz.xtf.builder.db.OpenShiftAuxiliary;
import cz.xtf.core.bm.ManagedBuildReference;
import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ApplicationBuilder {

	public static ApplicationBuilder fromImage(String name, String imageUrl) {
		ApplicationBuilder appBuilder = new ApplicationBuilder(name);
		appBuilder.deploymentConfig().onConfigurationChange().podTemplate().container().fromImage(imageUrl);

		return appBuilder;
	}

	public static ApplicationBuilder fromManagedBuild(String name, ManagedBuildReference mbr) {
		ApplicationBuilder appBuilder = new ApplicationBuilder(name);
		appBuilder.deploymentConfig().onImageChange().onConfigurationChange().podTemplate().container().fromImage(mbr.getNamespace(), mbr.getStreamName());

		return appBuilder;
	}

	public static ApplicationBuilder fromS2IBuild(String name, String imageUrl, String gitRepo) {
		ApplicationBuilder appBuilder = new ApplicationBuilder(name);
		appBuilder.buildConfig().onConfigurationChange().gitSource(gitRepo).setOutput(name).sti().forcePull(true).fromDockerImage(imageUrl);
		appBuilder.imageStream();
		appBuilder.deploymentConfig().onImageChange().onConfigurationChange().podTemplate().container().fromImage(name);

		return appBuilder;
	}

	private final String applicationName;

	private final Set<RouteBuilder> routes = new HashSet<>();
	private final Set<ServiceBuilder> services = new HashSet<>();
	private final Set<ImageStreamBuilder> images = new HashSet<>();
	private final Set<DeploymentConfigBuilder> deployments = new HashSet<>();
	private final Set<BuildConfigBuilder> builds = new HashSet<>();
	private final Set<SecretBuilder> secrets = new HashSet<>();
	private final Set<ConfigMapWithPropertyFilesBuilder> configMaps = new HashSet<>();
	private final Set<RoleBuilder> roles = new HashSet<>();
	private final Set<RoleBindingBuilder> roleBindings = new HashSet<>();
	private final Set<PVCBuilder> persistentVolumeClaims = new HashSet<>();

	public ApplicationBuilder(String name) {
		this.applicationName = name;
	}

	public String getName() {
		return applicationName;
	}

	public ImageStreamBuilder imageStream() {
		return imageStream(applicationName);
	}

	public ImageStreamBuilder imageStream(String name) {
		ImageStreamBuilder builder;
		Optional<ImageStreamBuilder> orig = images.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			builder = orig.get();
		} else {
			builder = new ImageStreamBuilder(this, name);
			images.add(builder);
		}

		return builder;
	}

	public BuildConfigBuilder buildConfig() {
		return buildConfig(applicationName);
	}

	public BuildConfigBuilder buildConfig(String name) {
		BuildConfigBuilder builder;
		Optional<BuildConfigBuilder> orig = builds.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			builder = orig.get();
		} else {
			builder = new BuildConfigBuilder(this, name);
			builds.add(builder);
		}

		return builder;
	}

	public DeploymentConfigBuilder deploymentConfig() {
		return deploymentConfig(applicationName);
	}

	public DeploymentConfigBuilder deploymentConfig(String name) {
		DeploymentConfigBuilder builder;
		Optional<DeploymentConfigBuilder> orig = deployments.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			builder = orig.get();
		} else {
			builder = new DeploymentConfigBuilder(this, name);
			deployments.add(builder);
		}

		return builder;
	}

	public ServiceBuilder service() {
		return service(applicationName);
	}

	public ServiceBuilder service(String name) {
		ServiceBuilder result;
		Optional<ServiceBuilder> orig = services.stream().filter(b -> b.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new ServiceBuilder(this, name);
			result.addContainerSelector("deploymentconfig", applicationName);
			services.add(result);
		}

		return result;
	}

	public RouteBuilder route() {
		return route(applicationName);
	}

	public RouteBuilder route(String name) {
		RouteBuilder result;
		Optional<RouteBuilder> orig = routes.stream().filter(r -> r.getName().startsWith(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new RouteBuilder(this, name);
			result.forService(applicationName);
			routes.add(result);
		}

		return result;
	}

	public RoleBuilder role() {
		return role(applicationName);
	}

	public RoleBuilder role(String name) {
		RoleBuilder result;
		Optional<RoleBuilder> orig = roles.stream().filter(r -> r.getName().startsWith(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new RoleBuilder(this, name);
			roles.add(result);
		}

		return result;
	}

	public RoleBindingBuilder roleBinding() {
		return roleBinding(applicationName);
	}

	public RoleBindingBuilder roleBinding(String name) {
		RoleBindingBuilder result;
		Optional<RoleBindingBuilder> orig = roleBindings.stream().filter(r -> r.getName().startsWith(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new RoleBindingBuilder(this, name);
			roleBindings.add(result);
		}

		return result;
	}

	public ConfigMapWithPropertyFilesBuilder configMap() {
		return configMap(applicationName);
	}

	public ConfigMapWithPropertyFilesBuilder configMap(String name) {
		ConfigMapWithPropertyFilesBuilder result;
		Optional<ConfigMapWithPropertyFilesBuilder> orig = configMaps.stream().filter(r -> r.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new ConfigMapWithPropertyFilesBuilder(name);
			configMaps.add(result);
		}
		return result;
	}

	public SecretBuilder secret() {
		return secret(applicationName);
	}

	public SecretBuilder secret(String name) {
		SecretBuilder result;
		Optional<SecretBuilder> orig = secrets.stream().filter(r -> r.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new SecretBuilder(name);
			secrets.add(result);
		}
		return result;
	}

	public PVCBuilder pvc() {
		return pvc(applicationName);
	}

	public PVCBuilder pvc(String name) {
		PVCBuilder result;
		Optional<PVCBuilder> orig = persistentVolumeClaims.stream().filter(r -> r.getName().equals(name)).findFirst();
		if (orig.isPresent()) {
			result = orig.get();
		} else {
			result = new PVCBuilder(name);
			persistentVolumeClaims.add(result);
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
		return deployments.stream().map(DeploymentConfigBuilder::build).collect(Collectors.toList());
	}

	public List<Service> buildServices() {
		return services.stream().map(ServiceBuilder::build).collect(Collectors.toList());
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

	public List<Secret> buildSecrets() {
		return secrets.stream().map(SecretBuilder::build).collect(Collectors.toList());
	}

	public List<PersistentVolumeClaim> buildPVCs() {
		return persistentVolumeClaims.stream().map(PVCBuilder::build).collect(Collectors.toList());
	}

	public List<HasMetadata> build() {
		List<HasMetadata> result = new LinkedList<>();
		result.addAll(buildImageStreams());
		result.addAll(buildBuildConfigs());
		result.addAll(buildDeploymentConfigs());
		result.addAll(buildServices());
		result.addAll(buildRoutes());
		result.addAll(buildConfigMaps());
		result.addAll(buildSecrets());
		result.addAll(buildPVCs());

		return result;
	}

	/**
	 * @deprecated superseded by {@link #buildApplication(OpenShift)}
	 * Bring your own client is a preferred way to obtain OpenShiftApplication object
	 */
	@Deprecated
	public OpenShiftApplication buildApplication() {
		return new OpenShiftApplication(this);
	}

	public OpenShiftApplication buildApplication(OpenShift openShift) {
		return new OpenShiftApplication(this, openShift);
	}

	public ApplicationBuilder addDatabase(OpenShiftAuxiliary database) {
		database.configureDeployment(this);
		database.configureApplicationDeployment(deploymentConfig());
		return this;
	}
}
