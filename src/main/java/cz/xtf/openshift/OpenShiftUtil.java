package cz.xtf.openshift;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.KubernetesListMixedOperation;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.*;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.observables.StringObservable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@Slf4j
public class OpenShiftUtil  implements AutoCloseable {
	private final NamespacedOpenShiftClient client;
	private final String namespace;

	public OpenShiftUtil(OpenShiftConfig openShiftConfig) {
		if(openShiftConfig.getNamespace() == null) {
			throw new IllegalArgumentException("Namespace in OpenShiftConfig must not be null!");
		}

		this.namespace = openShiftConfig.getNamespace();
		this.client = new DefaultOpenShiftClient(openShiftConfig);
	}

	public OpenShiftUtil(String masterUrl, String namespace, String username, String password) throws MalformedURLException {
		new URL(masterUrl);	// masterUrl validation

		OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder()
				.withMasterUrl(masterUrl)
				.withTrustCerts(true)
				.withRequestTimeout(120_000)
				.withNamespace(namespace)
				.withUsername(username)
				.withPassword(password)
				.build();

		this.namespace = namespace;
		this.client = new DefaultOpenShiftClient(openShiftConfig);
	}

	public OpenShiftUtil(String masterUrl, String namespace, String token) throws MalformedURLException {
		new URL(masterUrl); // masterUrl validation

		OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder()
				.withMasterUrl(masterUrl)
				.withTrustCerts(true)
				.withRequestTimeout(120_000)
				.withNamespace(namespace)
				.withOauthToken(token)
				.build();

		this.namespace = namespace;
		this.client = new DefaultOpenShiftClient(openShiftConfig);
	}

	// General functions
	public <R> R withUser(Function<NamespacedOpenShiftClient, R> f) {
		return f.apply(client);
	}

	public KubernetesList createResources(HasMetadata... resources) {
		return createResources(Arrays.asList(resources));
	}

	public KubernetesList createResources(List<HasMetadata> resources) {
		KubernetesList list = new KubernetesList();
		list.setItems(resources);
		return createResources(list);
	}

	public KubernetesList createResources(KubernetesList resources) {
		return client.lists().create(resources);
	}

	// Projects
	public ProjectRequest createProjectRequest() {
		return createProjectRequest(new ProjectRequestBuilder().withNewMetadata().withName(namespace).endMetadata().build());
	}

	public ProjectRequest createProjectRequest(String name) {
		return createProjectRequest(new ProjectRequestBuilder().withNewMetadata().withName(name).endMetadata().build());
	}

	public ProjectRequest createProjectRequest(ProjectRequest projectRequest) {
		return client.projectrequests().create(projectRequest);
	}

	/**
	 * Calls rectreateProject(namespace).
	 *
	 * @see OpenShiftUtil#recreateProject(String)
	 */
	public ProjectRequest recreateProject() throws TimeoutException, InterruptedException {
		return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(namespace).endMetadata().build());
	}

	/**
	 * Creates or recreates project specified by name.
	 *
	 * @param name name of a project to be created
	 * @return ProjectRequest instatnce
	 */
	public ProjectRequest recreateProject(String name) throws TimeoutException, InterruptedException {
		return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(name).endMetadata().build());
	}

	/**
	 * Creates or recreates project specified by projectRequest instance.
	 *
	 * @return ProjectRequest instatnce
	 */
	public ProjectRequest recreateProject(ProjectRequest projectRequest) throws TimeoutException, InterruptedException {
		boolean deleted = deleteProject(projectRequest.getMetadata().getName());
		if(deleted) {
			waitFor(() -> getProject(projectRequest.getMetadata().getName()), Objects::isNull , null, 1_000L, 120_000L);
		}
		return createProjectRequest(projectRequest);
	}

	/**
	 * Tries to retreive project with name 'name'. Swallows KubernetesClientException
	 * if project doesn't exist or isn't accessible for user.
	 *
	 * @param name name of requested project.
	 * @return Project instance if accessible otherwise null.
	 */
	public Project getProject(String name) {
		try {
			return client.projects().withName(name).get();
		} catch (KubernetesClientException e) {
			return null;
		}
	}

	public boolean deleteProject() {
		return deleteProject(namespace);
	}

	public boolean deleteProject(String name) {
		return getProject(name) != null ? client.projects().withName(name).delete() : false;
	}

	// ImageStreams
	public ImageStream createImageStream(ImageStream imageStream) {
		return createImageStream(imageStream, namespace);
	}

	public ImageStream createImageStream(ImageStream imageStream, String namespace) {
		return client.inNamespace(namespace).imageStreams().create(imageStream);
	}

	public ImageStream getImageStream(String name) {
		return getImageStream(name, namespace);
	}

	public ImageStream getImageStream(String name, String namespace) {
		return client.inNamespace(namespace).imageStreams().withName(name).get();
	}

	public List<ImageStream> getImageStreams() {
		return getImageStreams(namespace);
	}

	public List<ImageStream> getImageStreams(String namespace) {
		return client.inNamespace(namespace).imageStreams().list().getItems();
	}

	public boolean deleteImageStream(String name) {
		return deleteImageStream(name, namespace);
	}

	public boolean deleteImageStream(String name, String namespace) {
		return deleteImageStream(getImageStream(name, namespace));
	}

	public boolean deleteImageStream(ImageStream imageStream) {
		return client.inNamespace(imageStream.getMetadata().getNamespace()).imageStreams().delete(imageStream);
	}

	// Pods
	public Pod createPod(Pod pod) {
		return client.pods().create(pod);
	}

	public Pod getPod(String name) {
		return getPod(name, namespace);
	}

	public Pod getPod(String name, String namespace) {
		return client.inNamespace(namespace).pods().withName(name).get();
	}

	public String getPodLog(String name) {
		return client.pods().withName(name).getLog();
	}

	public Observable<String> observePodLog(String name) {
		LogWatch watcher = client.pods().withName(name).watchLog();
		return StringObservable.byLine(StringObservable.from(new InputStreamReader(watcher.getOutput())));
	}

	public List<Pod> getPods() {
		return getPods(namespace);
	}

	public List<Pod> getPods(String namespace) {
		return client.inNamespace(namespace).pods().list().getItems();
	}

	/**
	 * @param deploymentConfigName name of deploymentConfig
	 * @param version deployment version to be retrieved
	 * @return active pods created by deploymentConfig with specified version
	 */
	public List<Pod> getDeploymentPods(String deploymentConfigName, int version) {
		return getLabeledPods("deployment", deploymentConfigName + "-" + version);
	}

	/**
	 * @param deploymentConfigName name of deploymentConfig
	 * @return all active pods created by specified deploymentConfig
	 */
	public List<Pod> getDeploymentConfigPods(String deploymentConfigName) {
		return getLabeledPods("deploymentconfig", deploymentConfigName);
	}

	public List<Pod> getLabeledPods(String key, String value) {
		return getLabeledPods(Collections.singletonMap(key, value), namespace);
	}

	public List<Pod> getLabeledPods(Map<String, String> labels) {
		return getLabeledPods(labels, namespace);
	}

	public List<Pod> getLabeledPods(Map<String, String> labels, String namespace) {
		return client.inNamespace(namespace).pods().withLabels(labels).list().getItems();
	}

	public Pod getAnyPod(Map<String, String> labels) {
		List<Pod> pods = getLabeledPods(labels);
		return pods.get(new Random().nextInt(pods.size()));
	}

	public boolean deletePod(Pod pod) {
		return deletePod(pod, 0L);
	}

	public boolean deletePod(Pod pod, long gracePeriod) {
		return client.inNamespace(pod.getMetadata().getNamespace()).pods().withName(pod.getMetadata().getName()).withGracePeriod(gracePeriod).delete();
	}

	public boolean deletePods(Map<String, String> labels) {
		return client.pods().withLabels(labels).delete();
	}

	// Secrets
	public Secret createSecret(Secret secret) {
		return client.secrets().create(secret);
	}

	public List<Secret> getSecrets() {
		return getSecrets(namespace);
	}

	public List<Secret> getSecrets(String namespace) {
		return client.inNamespace(namespace).secrets().list().getItems();
	}

	public boolean deleteSecret(Secret secret) {
		return client.inNamespace(secret.getMetadata().getNamespace()).secrets().delete(secret);
	}

	// Services
	public Service createService(Service service) {
		return client.services().create(service);
	}

	public Service getService(String name) {
		return client.services().withName(name).get();
	}

	public List<Service> getServices() {
		return getServices(namespace);
	}

	public List<Service> getServices(String namespace) {
		return client.services().inNamespace(namespace).list().getItems();
	}

	public boolean deleteService(String name) {
		return deleteService(getService(name));
	}

	public boolean deleteService(Service service) {
		return client.inNamespace(service.getMetadata().getNamespace()).services().delete(service);
	}

	// Endpoints
	public Endpoints createEndpoint(Endpoints endpoint) {
		return client.endpoints().create(endpoint);
	}

	public Endpoints getEndpoint(String name) {
		return client.endpoints().withName(name).get();
	}

	public List<Endpoints> getEndpoints() {
		return client.endpoints().list().getItems();
	}

	public boolean deleteEndpoint(Endpoints endpoint) {
		return client.inNamespace(endpoint.getMetadata().getNamespace()).endpoints().delete(endpoint);
	}

	// Routes
	public Route createRoute(Route route) {
		return client.routes().create(route);
	}

	public Route getRoute(String name) {
		return client.routes().withName(name).get();
	}

	public List<Route> getRoutes() {
		return getRoutes(namespace);
	}

	public List<Route> getRoutes(String namespace) {
		return client.inNamespace(namespace).routes().list().getItems();
	}

	public boolean deleteRoute(Route route) {
		return client.inNamespace(route.getMetadata().getNamespace()).routes().delete(route);
	}

	// DeploymentConfigs
	public DeploymentConfig createDeploymentConfig(DeploymentConfig deploymentConfig) {
		return client.deploymentConfigs().create(deploymentConfig);
	}

	public DeploymentConfig getDeploymentConfig(String name) {
		return client.deploymentConfigs().withName(name).get();
	}

	public List<DeploymentConfig> getDeploymentConfigs() {
		return getDeploymentConfigs(namespace);
	}

	public List<DeploymentConfig> getDeploymentConfigs(String namespace) {
		return client.inNamespace(namespace).deploymentConfigs().list().getItems();
	}

	/**
	 * Returns first container environment variables.
	 *
	 * @param name name of deploymentConfig
	 * @return Map of environment variables
	 */
	public Map<String, String> getDeploymentConfigEnvVars(String name) {
		return getDeploymentConfig(name).getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
	}

	public DeploymentConfig updateDeploymentconfig(DeploymentConfig deploymentConfig) {
		return client.inNamespace(deploymentConfig.getMetadata().getNamespace()).deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).replace(deploymentConfig);
	}

	/**
	 * Updates deployment config environment variables with envVars values.
	 *
	 * @param name name of deploymentConfig
	 * @param envVars environment variables
	 */
	public DeploymentConfig updateDeploymentConfigEnvVars(String name, Map<String, String> envVars) {
		DeploymentConfig dc = getDeploymentConfig(name);

		List<EnvVar> vars = envVars.entrySet().stream().map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build()).collect(Collectors.toList());
		dc.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(vars);

		return updateDeploymentconfig(dc);
	}

	public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig) {
		return deleteDeploymentConfig(deploymentConfig, true);
	}

	public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig, boolean cascading) {
		return client.inNamespace(deploymentConfig.getMetadata().getNamespace()).deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).cascading(cascading).delete();
	}

	/**
	 * Scales deployment config to specified number of replicas.
	 *
	 * @param name name of deploymentConfig
	 * @param replicas number of target replicas
	 */
	public void scale(String name, int replicas) {
		client.deploymentConfigs().withName(name).scale(replicas);
	}

	/**
	 * Redeploys deployment config to latest version.
	 *
	 * @param name name of deploymentConfig
	 */
	public void deployLatest(String name) {
		client.deploymentConfigs().withName(name).deployLatest();
	}

	// Builds
	public Build getBuild(String name) {
		return getBuild(name, namespace);
	}

	public Build getBuild(String name, String namespace) {
		return client.inNamespace(namespace).builds().withName(name).get();
	}

	public Build getLatestBuild(String buildConfigName) {
		return getLatestBuild(buildConfigName, namespace);
	}

	public Build getLatestBuild(String buildConfigName, String namespace) {
		long lastVersion = client.inNamespace(namespace).buildConfigs().withName(buildConfigName).get().getStatus().getLastVersion();
		return getBuild(buildConfigName + "-" + lastVersion);
	}

	public List<Build> getBuilds() {
		return getBuilds(namespace);
	}

	public List<Build> getBuilds(String namespace) {
		return client.inNamespace(namespace).builds().list().getItems();
	}

	public String getBuildLog(Build build) {
		return  client.inNamespace(build.getMetadata().getNamespace()).builds().withName(build.getMetadata().getName()).getLog();
	}

	public boolean deleteBuild(Build build) {
		return client.inNamespace(build.getMetadata().getNamespace()).builds().delete(build);
	}

	public Build startBuild(String buildConfigName) {
		return startBuild(buildConfigName, namespace);
	}

	public Build startBuild(String buildConfigName, String namespace) {
		BuildRequest request = new BuildRequestBuilder().withNewMetadata().withName(buildConfigName).endMetadata().build();
		return client.inNamespace(namespace).buildConfigs().withName(buildConfigName).instantiate(request);
	}

	/**
	 * @see OpenShiftUtil#waitForBuildFinished(String, long, TimeUnit)
	 */
	public void waitForBuildFinished(String name, int timeoutInSeconds) throws TimeoutException, InterruptedException {
		waitForBuildFinished(name, timeoutInSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Waits till builds finishes, no matter whether it fails or not.
	 *
	 * @param buildName build to be waited upon
	 */
	public void waitForBuildFinished(String buildName, long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
		log.info("Waiting for completion of build {}", buildName);
		waitFor(() -> getBuild(buildName).getStatus().getPhase(), phase -> phase.matches("Complete|Failed"), null,2_000L, TimeUnit.MILLISECONDS.convert(timeout, timeUnit));
	}

	public void waitForBuildCompletion(String buildName, int timeoutInSeconds) throws TimeoutException, InterruptedException {
		waitForBuildCompletion(buildName, timeoutInSeconds, TimeUnit.SECONDS);
	}

	/**
	 * Waits till builds successfully completes.
	 *
	 * @param buildName build to be waited upon
	 * @throws IllegalStateException if build fails
	 */
	public void waitForBuildCompletion(String buildName, long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
		log.info("Waiting for completion of build {}", buildName);
		boolean success = waitFor(() -> getBuild(buildName).getStatus().getPhase(), "Complete"::equals, "Failed"::equals,2_000L, TimeUnit.MILLISECONDS.convert(timeout, timeUnit));

		if(!success) throw new IllegalStateException("Build " + buildName + " has failed");
	}

	// TODO use WaitUtil once it migrates away from obsolet OpenshiftUtil class
	private static <X> boolean waitFor(Supplier<X> supplier, Function<X, Boolean> trueCondition, Function<X, Boolean> failCondition, long interval, long timeout) throws InterruptedException, TimeoutException {
		timeout = System.currentTimeMillis() + timeout;

		while (System.currentTimeMillis() < timeout) {
			X x = supplier.get();
			if (failCondition != null && failCondition.apply(x)) {
				return false;
			}
			if (trueCondition.apply(x)) {
				return true;
			}
			Thread.sleep(interval);
		}
		throw new TimeoutException();
	}

	// BuildConfigs
	public BuildConfig createBuildConfig(BuildConfig buildConfig) {
		return createBuildConfig(buildConfig, namespace);
	}

	public BuildConfig createBuildConfig(BuildConfig buildConfig, String namespace) {
		return client.inNamespace(namespace).buildConfigs().create(buildConfig);
	}

	public BuildConfig getBuildConfig(String name) {
		return getBuildConfig(name, namespace);
	}

	public BuildConfig getBuildConfig(String name, String namespace) {
		return client.inNamespace(namespace).buildConfigs().withName(name).get();
	}

	public List<BuildConfig> getBuildConfigs() {
		return getBuildConfigs(namespace);
	}

	public List<BuildConfig> getBuildConfigs(String namespace) {
		return client.buildConfigs().inNamespace(namespace).list().getItems();
	}

	/**
	 * Returns environment variables of buildConfig specified under sourceStrategy.
	 *
	 * @param name name of buildConfig
	 * @return environment variables
	 */
	public Map<String, String> getBuildConfigEnvVars(String name) {
		return getBuildConfig(name).getSpec().getStrategy().getSourceStrategy().getEnv().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
	}

	public BuildConfig updateBuildConfig(BuildConfig buildConfig) {
		return client.buildConfigs().inNamespace(buildConfig.getMetadata().getNamespace()).withName(buildConfig.getMetadata().getName()).replace(buildConfig);
	}

	/**
	 * @see OpenShiftUtil#updateBuildConfigEnvVars(String, String, Map)
	 */
	public BuildConfig updateBuildConfigEnvVars(String name, Map<String, String> vars) {
		return updateBuildConfigEnvVars(name, namespace, vars);
	}

	/**
	 * Updates build config with specified environment variables.
	 *
	 * @param name name of buildConfig
	 * @param namespace namespace where to lookup for specified buildConfig
	 * @param envVars environment variables
	 */
	public BuildConfig updateBuildConfigEnvVars(String name, String namespace, Map<String, String> envVars) {
		List<EnvVar> vars = envVars.entrySet().stream().map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build()).collect(Collectors.toList());

		BuildConfig bc = getBuildConfig(name, namespace);
		bc.getSpec().getStrategy().getSourceStrategy().setEnv(vars);

		return updateBuildConfig(bc);
	}

	public boolean deleteBuildConfig(BuildConfig buildConfig) {
		return client.inNamespace(buildConfig.getMetadata().getNamespace()).buildConfigs().delete(buildConfig);
	}

	// ServiceAccounts
	public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
		return client.serviceAccounts().create(serviceAccount);
	}

	public List<ServiceAccount> getServiceAccounts() {
		return client.serviceAccounts().list().getItems();
	}

	public boolean deleteServiceAccount(ServiceAccount serviceAccount) {
		return client.inNamespace(serviceAccount.getMetadata().getNamespace()).serviceAccounts().delete(serviceAccount);
	}

	// RoleBindings
	public RoleBinding addRoleToUser(String role, String username) {
		return addRoleToUser(role, username, namespace);
	}

	public RoleBinding addRoleToUser(String role, String username, String namespace) {
		RoleBinding roleBinding = getOrCreateRoleBinding(role, namespace);

		addSubjectToRoleBinding(roleBinding, "User", username);
		addUserNameToRoleBinding(roleBinding, username);

		return updateRoleBinding(roleBinding);
	}

	public RoleBinding addRoleToServiceAccount(String role, String serviceAccountName) {
		return addRoleToServiceAccount(role, serviceAccountName, namespace);
	}

	public RoleBinding addRoleToServiceAccount(String role, String serviceAccountName, String namespace) {
		RoleBinding roleBinding = getOrCreateRoleBinding(role, namespace);

		addSubjectToRoleBinding(roleBinding, "ServiceAccount", serviceAccountName);
		addUserNameToRoleBinding(roleBinding, String.format("system:serviceaccount:%s:%s", namespace, serviceAccountName));

		return updateRoleBinding(roleBinding);
	}

	public RoleBinding addRoleToGroup(String role, String groupName) {
		return addRoleToGroup(role, groupName, namespace);
	}

	public RoleBinding addRoleToGroup(String role, String groupName, String namespace) {
		RoleBinding roleBinding = getOrCreateRoleBinding(role, namespace);

		addSubjectToRoleBinding(roleBinding, "SystemGroup", groupName);
		addGroupNameToRoleBinding(roleBinding, groupName);

		return updateRoleBinding(roleBinding);
	}

	private RoleBinding getOrCreateRoleBinding(String name, String namespace) {
		RoleBinding roleBinding = client.inNamespace(namespace).roleBindings().withName(name).get();

		if(roleBinding == null) {
			return client.inNamespace(namespace).roleBindings().createNew()
					.withNewMetadata().withName(name).endMetadata()
					.withNewRoleRef().withName(name).endRoleRef()
					.done();
		}
		return roleBinding;
	}

	public RoleBinding updateRoleBinding(RoleBinding roleBinding) {
		return client.inNamespace(roleBinding.getMetadata().getNamespace()).roleBindings().withName(roleBinding.getMetadata().getName()).replace(roleBinding);
	}

	private void addSubjectToRoleBinding(RoleBinding roleBinding, String entityKind, String entityName) {
		ObjectReference subject = new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).build();

		if(roleBinding.getSubjects().stream().noneMatch(x -> x.getName().equals(subject.getName()) && x.getKind().equals(subject.getKind()))) {
			roleBinding.getSubjects().add(subject);
		}
	}

	private void addUserNameToRoleBinding(RoleBinding roleBinding, String userName) {
		if( roleBinding.getUserNames() == null) {
			roleBinding.setUserNames(new ArrayList<>());
		}
		if( !roleBinding.getUserNames().contains(userName)) {
			roleBinding.getUserNames().add(userName);
		}
	}

	private void addGroupNameToRoleBinding(RoleBinding roleBinding, String groupName) {
		if( roleBinding.getGroupNames() == null) {
			roleBinding.setGroupNames(new ArrayList<>());
		}
		if(!roleBinding.getGroupNames().contains(groupName)) {
			roleBinding.getGroupNames().add(groupName);
		}
	}

	public RoleBinding removeRoleFromServiceAccount(String role, String serviceAccountName) {
		return removeRoleFromServiceAccount(role, serviceAccountName, namespace);
	}

	public RoleBinding removeRoleFromServiceAccount(String role, String serviceAccountName, String namespace) {
		return removeRoleFromEntity(role, "ServiceAccount", serviceAccountName, String.format("system:serviceaccount:%s:%s", namespace, serviceAccountName), namespace);
	}

	public RoleBinding removeRoleFromEntity(String role, String entityKind, String entityName, String userName, String namespace) {
		RoleBinding roleBinding = client.inNamespace(namespace).roleBindings().withName(role).get();

		if (roleBinding != null) {
			roleBinding.getSubjects().remove(new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).withNamespace(namespace).build());
			roleBinding.getUserNames().remove(userName);

			return updateRoleBinding(roleBinding);
		}
		return null;
	}

	// ResourceQuotas
	public ResourceQuota createResourceQuota(ResourceQuota resourceQuota) {
		return createResourceQuota(resourceQuota, namespace);
	}

	public ResourceQuota createResourceQuota(ResourceQuota resourceQuota, String namespace) {
		return client.inNamespace(namespace).resourceQuotas().create(resourceQuota);
	}

	public ResourceQuota getResourceQuota(String name) {
		return getResourceQuota(name, namespace);
	}

	public ResourceQuota getResourceQuota(String name, String namespace) {
		return client.inNamespace(namespace).resourceQuotas().withName(name).get();
	}

	public boolean deleteResourceQuota(ResourceQuota resourceQuota) {
		return client.inNamespace(resourceQuota.getMetadata().getNamespace()).resourceQuotas().delete(resourceQuota);
	}

	// Persistent volume claims
	public PersistentVolumeClaim createPersistentVolumeClaim(PersistentVolumeClaim pvc) {
		return client.persistentVolumeClaims().create(pvc);
	}

	public PersistentVolumeClaim getPersistentVolumeClaim(String name) {
		return client.persistentVolumeClaims().withName(name).get();
	}

	public List<PersistentVolumeClaim> getPersistentVolumeClaims() {
		return client.persistentVolumeClaims().list().getItems();
	}

	public boolean deletePersistentVolumeClaim(PersistentVolumeClaim pvc) {
		return client.inNamespace(pvc.getMetadata().getNamespace()).persistentVolumeClaims().delete(pvc);
	}

	// MixedOperations
	public KubernetesListMixedOperation getLists() {
		return client.lists();
	}

	// HorizontalPodAutoscalers
	public HorizontalPodAutoscaler createHorizontalPodAutoscaler(HorizontalPodAutoscaler hpa) {
		return client.autoscaling().horizontalPodAutoscalers().create(hpa);
	}

	public HorizontalPodAutoscaler getHorizontalPodAutoscaler(String name) {
		return client.autoscaling().horizontalPodAutoscalers().withName(name).get();
	}

	public List<HorizontalPodAutoscaler> getHorizontalPodAutoscalers() {
		return client.autoscaling().horizontalPodAutoscalers().list().getItems();
	}

	public boolean deleteHorizontalPodAutoscaler(HorizontalPodAutoscaler hpa) {
		return client.inNamespace(hpa.getMetadata().getNamespace()).autoscaling().horizontalPodAutoscalers().delete(hpa);
	}

	// ConfigMaps
	public ConfigMap createConfigMap(ConfigMap configMap) {
		return client.configMaps().create(configMap);
	}

	public ConfigMap getConfigMap(String name) {
		return client.configMaps().withName(name).get();
	}

	public List<ConfigMap> getConfigMaps() {
		return client.configMaps().list().getItems();
	}

	public boolean deleteConfigMap(ConfigMap configMap) {
		return client.inNamespace(configMap.getMetadata().getNamespace()).configMaps().delete(configMap);
	}

	// Templates
	public Template createTemplate(Template template) {
		return createTemplate(template, namespace);
	}

	public Template createTemplate(Template template, String namespace) {
		return client.templates().inNamespace(namespace).create(template);
	}

	public Template getTemplate(String name) {
		return getTemplate(name, namespace);
	}

	public Template getTemplate(String name, String namespace) {
		return client.templates().inNamespace(namespace).withName(name).get();
	}

	public boolean deleteTemplate(String name) {
		return deleteTemplate(name, namespace);
	}

	public boolean deleteTemplate(String name, String namespace) {
		return client.inNamespace(namespace).templates().withName(name).delete();
	}

	public boolean deleteTemplate(Template template) {
		return client.inNamespace(template.getMetadata().getNamespace()).templates().delete(template);
	}

	public KubernetesList recreateAndProcessTemplate(Template template, Map<String, String> parameters) {
		deleteTemplate(template.getMetadata().getName());
		createTemplate(template);

		return processTemplate(template.getMetadata().getName(), parameters);
	}

	public KubernetesList processTemplate(String name, Map<String, String> parameters) {
		return processTemplate(name, parameters, namespace);
	}

	public KubernetesList processTemplate(String name, Map<String, String> parameters, String namespace) {
		ParameterValue[] values = processParameters(parameters);
		return client.templates().inNamespace(namespace).withName(name).process(values);
	}

	private ParameterValue[] processParameters(Map<String, String> parameters) {
		return parameters.entrySet().stream().map(entry -> new ParameterValue(entry.getKey(), entry.getValue())).collect(Collectors.toList()).toArray(new ParameterValue[parameters.size()]);
	}

	// Nodes
	public List<Node> getNodes() {
		return client.nodes().list().getItems();
	}

	public List<Node> getNodes(Map<String, String> labels) {
		return client.nodes().withLabels(labels).list().getItems();
	}

	// Events
	public List<Event> getEvents() {
		return getEvents(namespace);
	}

	public List<Event> getEvents(String namespace) {
		return client.inNamespace(namespace).events().list().getItems();
	}


	// Clean up function
	public void cleanProject() {
		// keep the order for deletion to prevent K8s creating resources again
		getDeploymentConfigs().forEach(this::deleteDeploymentConfig);
		getBuildConfigs().forEach(this::deleteBuildConfig);
		getImageStreams().forEach(this::deleteImageStream);
		getEndpoints().forEach(this::deleteEndpoint);
		getServices().forEach(this::deleteService);
		getBuilds().forEach(this::deleteBuild);
		getRoutes().forEach(this::deleteRoute);
		getPods().forEach(this::deletePod);
		getPersistentVolumeClaims().forEach(this::deletePersistentVolumeClaim);
		getHorizontalPodAutoscalers().forEach(this::deleteHorizontalPodAutoscaler);
		getConfigMaps().forEach(this::deleteConfigMap);

		// Remove only user secrets
		getSecrets().stream().filter(s -> !s.getType().startsWith("kubernetes.io/")).forEach(this::deleteSecret);

		// Remove only users service accounts
		getServiceAccounts().stream().filter(sa -> !sa.getMetadata().getName().matches(".*(builder|default|deployer).*")).forEach(this::deleteServiceAccount);

		try{
			Thread.sleep(2_000L);
		} catch (InterruptedException e) {
			log.warn("Interrupted while giving openshift time to delete resources");
		}
	}

	@Override
	public void close() {
		client.close();
	}

	// Logs storing
	public Path storePodLog(Pod pod, Path dirPath, String fileName) throws IOException {
		String log = getPodLog(pod.getMetadata().getName());
		return storeLog(log, dirPath, fileName);
	}

	public Path storeBuildLog(Build build, Path dirPath, String fileName) throws IOException {
		String log = getBuildLog(build);
		return storeLog(log, dirPath, fileName);
	}

	private Path storeLog(String log, Path dirPath, String fileName) throws IOException {
		Path filePath = dirPath.resolve(fileName + ".log");

		Files.createDirectories(dirPath);
		Files.createFile(filePath);
		Files.write(filePath, log.getBytes());

		return filePath;
	}
}
