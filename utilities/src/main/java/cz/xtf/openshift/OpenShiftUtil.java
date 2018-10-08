package cz.xtf.openshift;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import cz.xtf.TestConfiguration;
import cz.xtf.openshift.builder.SecretBuilder;
import cz.xtf.openshift.builder.secret.SecretType;
import cz.xtf.wait.SimpleWaiter;
import cz.xtf.wait.Waiters;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectRequest;
import io.fabric8.openshift.api.model.ProjectRequestBuilder;
import io.fabric8.openshift.api.model.Role;
import io.fabric8.openshift.api.model.RoleBinding;
import io.fabric8.openshift.api.model.RoleBindingBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.openshift.client.ParameterValue;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.observables.StringObservable;

@Slf4j
public class OpenShiftUtil implements AutoCloseable {

	private final NamespacedOpenShiftClient client;
	private final OpenShiftWaiters waiters;
	private final String namespace;

	public static final String KEEP_LABEL = "xtf.cz/keep";

	public OpenShiftUtil(OpenShiftConfig openShiftConfig) {
		if (openShiftConfig.getNamespace() == null) {
			throw new IllegalArgumentException("Namespace in OpenShiftConfig must not be null!");
		}

		this.namespace = openShiftConfig.getNamespace();
		this.client = new DefaultOpenShiftClient(openShiftConfig);
		this.waiters = new OpenShiftWaiters(this);
	}

	public OpenShiftUtil(String masterUrl, String namespace, String username, String password) throws MalformedURLException {
		new URL(masterUrl);    // masterUrl validation

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
		this.waiters = new OpenShiftWaiters(this);
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
		this.waiters = new OpenShiftWaiters(this);
	}

	public String namespace() {
		return namespace;
	}

	public NamespacedOpenShiftClient client() {
		return client;
	}

	// General functions
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

	public boolean deleteResources(KubernetesList resources) {
		return client.lists().delete(resources);
	}

	public void loadResource(InputStream is) {
		client.load(is).deletingExisting().createOrReplace();
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
	public ProjectRequest recreateProject() throws TimeoutException {
		return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(namespace).endMetadata().build());
	}

	/**
	 * Creates or recreates project specified by name.
	 *
	 * @param name name of a project to be created
	 * @return ProjectRequest instatnce
	 */
	public ProjectRequest recreateProject(String name) throws TimeoutException {
		return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(name).endMetadata().build());
	}

	/**
	 * Creates or recreates project specified by projectRequest instance.
	 *
	 * @return ProjectRequest instatnce
	 */
	public ProjectRequest recreateProject(ProjectRequest projectRequest) throws TimeoutException {
		deleteProject(projectRequest.getMetadata().getName());
		Waiters.sleep(TimeUnit.SECONDS, 30);

		AtomicReference<ProjectRequest> pr = new AtomicReference<>();
		BooleanSupplier bs = () -> {
			try {
				ProjectRequest attempt = createProjectRequest(projectRequest);
				pr.set(attempt);
				return true;
			} catch (KubernetesClientException e) {
				log.warn("Failed to create project: {}", projectRequest.getMetadata().getName());
				return false;
			}
		};
		new SimpleWaiter(bs, TimeUnit.MINUTES, 3, "Waiting for successful project recreation").interval(TimeUnit.SECONDS, 10).execute();

		return pr.get();
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
		return client.imageStreams().create(imageStream);
	}

	public ImageStream getImageStream(String name) {
		return client.imageStreams().withName(name).get();
	}

	public List<ImageStream> getImageStreams() {
		return client.imageStreams().list().getItems();
	}

	public boolean deleteImageStream(ImageStream imageStream) {
		return client.imageStreams().delete(imageStream);
	}

	// Pods
	public Pod createPod(Pod pod) {
		return client.pods().create(pod);
	}

	public Pod getPod(String name) {
		return client.pods().withName(name).get();
	}

	public String getPodLog(Pod pod) {
		return client.pods().withName(pod.getMetadata().getName()).getLog();
	}

	public Reader getPodLogReader(Pod pod) {
		return client.pods().withName(pod.getMetadata().getName()).getLogReader();
	}

	public Observable<String> observePodLog(Pod pod) {
		LogWatch watcher = client.pods().withName(pod.getMetadata().getName()).watchLog();
		return StringObservable.byLine(StringObservable.from(new InputStreamReader(watcher.getOutput())));
	}

	public List<Pod> getPods() {
		return client.pods().list().getItems();
	}

	/**
	 * @param deploymentConfigName name of deploymentConfig
	 * @return all active pods created by specified deploymentConfig
	 */
	public List<Pod> getPods(String deploymentConfigName) {
		return getLabeledPods("deploymentconfig", deploymentConfigName);
	}

	/**
	 * @param deploymentConfigName name of deploymentConfig
	 * @param version deployment version to be retrieved
	 * @return active pods created by deploymentConfig with specified version
	 */
	public List<Pod> getPods(String deploymentConfigName, int version) {
		return getLabeledPods("deployment", deploymentConfigName + "-" + version);
	}

	public List<Pod> getLabeledPods(String key, String value) {
		return getLabeledPods(Collections.singletonMap(key, value));
	}

	public List<Pod> getLabeledPods(Map<String, String> labels) {
		return client.pods().withLabels(labels).list().getItems();
	}

	public Pod getAnyPod(String deploymentConfigName) {
		return getAnyPod("deploymentconfig", deploymentConfigName);
	}

	public Pod getAnyPod(String key, String value) {
		return getAnyPod(Collections.singletonMap(key, value));
	}

	public Pod getAnyPod(Map<String, String> labels) {
		List<Pod> pods = getLabeledPods(labels);
		return pods.get(new Random().nextInt(pods.size()));
	}

	public boolean deletePod(Pod pod) {
		return deletePod(pod, 0L);
	}

	public boolean deletePod(Pod pod, long gracePeriod) {
		return client.pods().withName(pod.getMetadata().getName()).withGracePeriod(gracePeriod).delete();
	}

	/**
	 * Deletes pods with specified label.
	 *
	 * @param key key of the label
	 * @param value value of the label
	 * @return True if any pod has been deleted
	 */
	public boolean deletePods(String key, String value) {
		return client.pods().withLabel(key, value).delete();
	}

	public boolean deletePods(Map<String, String> labels) {
		return client.pods().withLabels(labels).delete();
	}

	// Secrets
	public Secret createSecret(Secret secret) {
		return client.secrets().create(secret);
	}

	public Secret getSecret(String name) {
		return client.secrets().withName(name).get();
	}

	public List<Secret> getSecrets() {
		return client.secrets().list().getItems();
	}

	/**
	 * Retrieves secrets that aren't considered default. Secrets that are left out contain type starting with 'kubernetes.io/'.
	 *
	 * @return List of secrets that aren't considered default.
	 */
	public List<Secret> getUserSecrets() {
		return client.secrets().withoutLabel(KEEP_LABEL).list().getItems().stream()
				.filter(s -> !s.getType().startsWith("kubernetes.io/"))
				.collect(Collectors.toList());
	}

	public boolean deleteSecret(Secret secret) {
		return client.secrets().delete(secret);
	}

	/**
	 * Creates the secret needed for an authenticated external registry (e.g. registry.redhat.io ) and adds it to the default and builder service accounts
	 * Uses the TestConfiguration.oregRegistry and TestConfiguration.oregAuth properties
	 * @return
	 */
	public Secret createORegSecret() {
		if (TestConfiguration.oregRegistry() != null && !TestConfiguration.oregRegistry().trim().isEmpty()) {

			final String secretName = TestConfiguration.oregRegistry();

			Secret oregSecret = getSecret(secretName);
			if (oregSecret != null) {
				deleteSecret(oregSecret);
			}

			Secret dockerCfg = new SecretBuilder(secretName)
					.setType(SecretType.DOCKERCFG)
					.addData(".dockerconfigjson", ("{\"auths\":{\"" + TestConfiguration.oregRegistry() + "\":{\"auth\":\"" + TestConfiguration.oregAuth() + "\"}}}").getBytes())
					.build();
			dockerCfg.getMetadata().setLabels(Collections.singletonMap(KEEP_LABEL, "keep"));
			dockerCfg = createSecret(dockerCfg);

			client().serviceAccounts().withName("builder").edit().addToSecrets(new ObjectReferenceBuilder().withKind("Secret").withName(secretName).build()).done();
			client().serviceAccounts().withName("default").edit().addToSecrets(new ObjectReferenceBuilder().withKind("Secret").withName(secretName).build()).addToImagePullSecrets(new LocalObjectReference(secretName)).done();

			return dockerCfg;
		}

		return null;
	}

	// Services
	public Service createService(Service service) {
		return client.services().create(service);
	}

	public Service getService(String name) {
		return client.services().withName(name).get();
	}

	public List<Service> getServices() {
		return client.services().list().getItems();
	}

	public boolean deleteService(Service service) {
		return client.services().delete(service);
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
		return client.endpoints().delete(endpoint);
	}

	// Routes
	public Route createRoute(Route route) {
		return client.routes().create(route);
	}

	public Route getRoute(String name) {
		return client.routes().withName(name).get();
	}

	public List<Route> getRoutes() {
		return client.routes().list().getItems();
	}

	public boolean deleteRoute(Route route) {
		return client.routes().delete(route);
	}

	// ReplicationControllers - Only for internal usage with clean
	private List<ReplicationController> getReplicationControllers() {
		return client.replicationControllers().list().getItems();
	}

	private boolean deleteReplicationController(ReplicationController replicationController) {
		return client.replicationControllers().withName(replicationController.getMetadata().getName()).cascading(false).delete();
	}

	// DeploymentConfigs
	public DeploymentConfig createDeploymentConfig(DeploymentConfig deploymentConfig) {
		return client.deploymentConfigs().create(deploymentConfig);
	}

	public DeploymentConfig getDeploymentConfig(String name) {
		return client.deploymentConfigs().withName(name).get();
	}

	public List<DeploymentConfig> getDeploymentConfigs() {
		return client.deploymentConfigs().list().getItems();
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
		return client.deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).replace(deploymentConfig);
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
		dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().removeIf(x -> envVars.containsKey(x.getName()));
		dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().addAll(vars);

		return updateDeploymentconfig(dc);
	}

	public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig) {
		return deleteDeploymentConfig(deploymentConfig, false);
	}

	public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig, boolean cascading) {
		return client.deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).cascading(cascading).delete();
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
		return client.inNamespace(namespace).builds().withName(name).get();
	}

	public Build getLatestBuild(String buildConfigName) {
		long lastVersion = client.buildConfigs().withName(buildConfigName).get().getStatus().getLastVersion();
		return getBuild(buildConfigName + "-" + lastVersion);
	}

	public List<Build> getBuilds() {
		return client.builds().list().getItems();
	}

	public String getBuildLog(Build build) {
		return client.builds().withName(build.getMetadata().getName()).getLog();
	}

	public Reader getBuildLogReader(Build build) {
		return client.builds().withName(build.getMetadata().getName()).getLogReader();
	}

	public boolean deleteBuild(Build build) {
		return client.builds().delete(build);
	}

	public Build startBuild(String buildConfigName) {
		BuildRequest request = new BuildRequestBuilder().withNewMetadata().withName(buildConfigName).endMetadata().build();
		return client.buildConfigs().withName(buildConfigName).instantiate(request);
	}

	public Build startBinaryBuild(String buildConfigName, File file) {
		return client.buildConfigs().withName(buildConfigName).instantiateBinary().fromFile(file);
	}

	// BuildConfigs
	public BuildConfig createBuildConfig(BuildConfig buildConfig) {
		return client.buildConfigs().create(buildConfig);
	}

	public BuildConfig getBuildConfig(String name) {
		return client.buildConfigs().withName(name).get();
	}

	public List<BuildConfig> getBuildConfigs() {
		return client.buildConfigs().list().getItems();
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
		return client.buildConfigs().withName(buildConfig.getMetadata().getName()).replace(buildConfig);
	}

	/**
	 * Updates build config with specified environment variables.
	 *
	 * @param name name of buildConfig
	 * @param envVars environment variables
	 */
	public BuildConfig updateBuildConfigEnvVars(String name, Map<String, String> envVars) {
		BuildConfig bc = getBuildConfig(name);

		List<EnvVar> vars = envVars.entrySet().stream().map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build()).collect(Collectors.toList());
		bc.getSpec().getStrategy().getSourceStrategy().getEnv().removeIf(x -> envVars.containsKey(x.getName()));
		bc.getSpec().getStrategy().getSourceStrategy().getEnv().addAll(vars);

		return updateBuildConfig(bc);
	}

	public boolean deleteBuildConfig(BuildConfig buildConfig) {
		return client.buildConfigs().delete(buildConfig);
	}

	// ServiceAccounts
	public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
		return client.serviceAccounts().create(serviceAccount);
	}

	public ServiceAccount getServiceAccount(String name) {
		return client.serviceAccounts().withName(name).get();
	}

	public List<ServiceAccount> getServiceAccounts() {
		return client.serviceAccounts().list().getItems();
	}

	/**
	 * Retrieves service accounts that aren't considered default.
	 * Service accounts that are left out from list:
	 * <ul>
	 * <li>builder</li>
	 * <li>default</li>
	 * <li>deployer</li>
	 * </ul>
	 *
	 * @return List of service accounts that aren't considered default.
	 */
	public List<ServiceAccount> getUserServiceAccounts() {
		return client.serviceAccounts().withoutLabel(KEEP_LABEL).list().getItems().stream()
				.filter(sa -> !sa.getMetadata().getName().matches("builder|default|deployer"))
				.collect(Collectors.toList());
	}

	public boolean deleteServiceAccount(ServiceAccount serviceAccount) {
		return client.serviceAccounts().delete(serviceAccount);
	}

	// RoleBindings
	public RoleBinding createRoleBinding(RoleBinding roleBinding) {
		return client.roleBindings().create(roleBinding);
	}

	public RoleBinding getRoleBinding(String name) {
		return client.roleBindings().withName(name).get();
	}

	public List<RoleBinding> getRoleBindings() {
		return client.roleBindings().list().getItems();
	}

	public List<Role> getRoles() {
		return client.roles().list().getItems();
	}

	/**
	 * Retrieves role bindings that aren't considered default.
	 * Role bindings that are left out from list:
	 * <ul>
	 * <li>admin</li>
	 * <li>system:deployers</li>
	 * <li>system:image-builders</li>
	 * <li>system:image-pullers</li>
	 * </ul>
	 *
	 * @return List of role bindings that aren't considered default.
	 */
	public List<RoleBinding> getUserRoleBindings() {
		return client.roleBindings().withoutLabel(KEEP_LABEL).list().getItems().stream()
				.filter(rb -> !rb.getMetadata().getName().matches("admin|system:deployers|system:image-builders|system:image-pullers"))
				.collect(Collectors.toList());
	}

	public boolean deleteRoleBinding(RoleBinding roleBinding) {
		return client.roleBindings().delete(roleBinding);
	}

	public RoleBinding addRoleToUser(String roleName, String username) {
		RoleBinding roleBinding = getOrCreateRoleBinding(roleName);

		addSubjectToRoleBinding(roleBinding, "User", username);
		addUserNameToRoleBinding(roleBinding, username);

		return updateRoleBinding(roleBinding);
	}

	public RoleBinding addRoleToServiceAccount(String roleName, String serviceAccountName) {
		RoleBinding roleBinding = getOrCreateRoleBinding(roleName);

		addSubjectToRoleBinding(roleBinding, "ServiceAccount", serviceAccountName);
		addUserNameToRoleBinding(roleBinding, String.format("system:serviceaccount:%s:%s", namespace, serviceAccountName));

		return updateRoleBinding(roleBinding);
	}

	public RoleBinding addRoleToGroup(String roleName, String groupName) {
		RoleBinding roleBinding = getOrCreateRoleBinding(roleName);

		addSubjectToRoleBinding(roleBinding, "SystemGroup", groupName);
		addGroupNameToRoleBinding(roleBinding, groupName);

		return updateRoleBinding(roleBinding);
	}

	private RoleBinding getOrCreateRoleBinding(String name) {
		RoleBinding roleBinding = getRoleBinding(name);

		if (roleBinding == null) {
			roleBinding = new RoleBindingBuilder()
					.withNewMetadata().withName(name).endMetadata()
					.withNewRoleRef().withName(name).endRoleRef()
					.build();
			createRoleBinding(roleBinding);
		}
		return roleBinding;
	}

	public RoleBinding updateRoleBinding(RoleBinding roleBinding) {
		return client.roleBindings().withName(roleBinding.getMetadata().getName()).replace(roleBinding);
	}

	private void addSubjectToRoleBinding(RoleBinding roleBinding, String entityKind, String entityName) {
		ObjectReference subject = new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).build();

		if (roleBinding.getSubjects().stream().noneMatch(x -> x.getName().equals(subject.getName()) && x.getKind().equals(subject.getKind()))) {
			roleBinding.getSubjects().add(subject);
		}
	}

	private void addUserNameToRoleBinding(RoleBinding roleBinding, String userName) {
		if (roleBinding.getUserNames() == null) {
			roleBinding.setUserNames(new ArrayList<>());
		}
		if (!roleBinding.getUserNames().contains(userName)) {
			roleBinding.getUserNames().add(userName);
		}
	}

	private void addGroupNameToRoleBinding(RoleBinding roleBinding, String groupName) {
		if (roleBinding.getGroupNames() == null) {
			roleBinding.setGroupNames(new ArrayList<>());
		}
		if (!roleBinding.getGroupNames().contains(groupName)) {
			roleBinding.getGroupNames().add(groupName);
		}
	}

	public RoleBinding removeRoleFromServiceAccount(String roleName, String serviceAccountName) {
		return removeRoleFromEntity(roleName, "ServiceAccount", serviceAccountName, String.format("system:serviceaccount:%s:%s", namespace, serviceAccountName));
	}

	public RoleBinding removeRoleFromEntity(String roleName, String entityKind, String entityName, String userName) {
		RoleBinding roleBinding = client.roleBindings().withName(roleName).get();

		if (roleBinding != null) {
			roleBinding.getSubjects().remove(new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).withNamespace(namespace).build());
			roleBinding.getUserNames().remove(userName);

			return updateRoleBinding(roleBinding);
		}
		return null;
	}

	// ResourceQuotas
	public ResourceQuota createResourceQuota(ResourceQuota resourceQuota) {
		return client.resourceQuotas().create(resourceQuota);
	}

	public ResourceQuota getResourceQuota(String name) {
		return client.resourceQuotas().withName(name).get();
	}

	public boolean deleteResourceQuota(ResourceQuota resourceQuota) {
		return client.resourceQuotas().delete(resourceQuota);
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
		return client.persistentVolumeClaims().delete(pvc);
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
		return client.autoscaling().horizontalPodAutoscalers().delete(hpa);
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
		return client.configMaps().delete(configMap);
	}

	// Templates
	public Template createTemplate(Template template) {
		return client.templates().create(template);
	}

	public Template getTemplate(String name) {
		return client.templates().withName(name).get();
	}

	public List<Template> getTemplates() {
		return client.templates().list().getItems();
	}

	public boolean deleteTemplate(String name) {
		return client.templates().withName(name).delete();
	}

	public boolean deleteTemplate(Template template) {
		return client.templates().delete(template);
	}

	public Template loadAndCreateTemplate(InputStream is) {
		Template t = client().templates().load(is).get();
		deleteTemplate(t);

		return createTemplate(t);
	}

	public KubernetesList recreateAndProcessTemplate(Template template, Map<String, String> parameters) {
		deleteTemplate(template.getMetadata().getName());
		createTemplate(template);

		return processTemplate(template.getMetadata().getName(), parameters);
	}

	public KubernetesList recreateAndProcessAndDeployTemplate(Template template, Map<String, String> parameters) {
		return createResources(recreateAndProcessTemplate(template, parameters));
	}

	public KubernetesList processTemplate(String name, Map<String, String> parameters) {
		ParameterValue[] values = processParameters(parameters);
		return client.templates().withName(name).process(values);
	}

	public KubernetesList processAndDeployTemplate(String name, Map<String, String> parameters) {
		return createResources(processTemplate(name, parameters));
	}

	private ParameterValue[] processParameters(Map<String, String> parameters) {
		return parameters.entrySet().stream().map(entry -> new ParameterValue(entry.getKey(), entry.getValue())).collect(Collectors.toList()).toArray(new ParameterValue[parameters.size()]);
	}

	// Nodes
	public Node getNode(String name) {
		return client.nodes().withName(name).get();
	}

	public List<Node> getNodes() {
		return client.nodes().list().getItems();
	}

	public List<Node> getNodes(Map<String, String> labels) {
		return client.nodes().withLabels(labels).list().getItems();
	}

	// Events
	public List<Event> getEvents() {
		return client.events().list().getItems();
	}

	// Clean up function

	/**
	 * Deletes all* resources in namespace. Waits till all are deleted. <br/>
	 * <br/>
	 * <p>
	 * * Only user created secrets, service accounts and role bindings are deleted. Default will remain.
	 *
	 * @throws TimeoutException in case that some user resources will remain even after timeout.
	 * @see #getUserSecrets()
	 * @see #getUserServiceAccounts()
	 * @see #getUserRoleBindings()
	 */
	public void cleanAndWait() throws TimeoutException {
		clean();
		waiters.isProjectClean().execute();
	}

	/**
	 * Deletes all* resources in namespace. Waits till all are deleted. <br/>
	 * <br/>
	 * <p>
	 * * Only user created secrets, service accounts and role bindings are deleted. Default will remain.
	 *
	 * @throws AssertionError in case that some user resources will remain even after timeout.
	 * @see #getUserSecrets()
	 * @see #getUserServiceAccounts()
	 * @see #getUserRoleBindings()
	 */
	public void cleanAndAssert() {
		clean();
		waiters.isProjectClean().assertEventually();
	}

	/**
	 * Deletes all* resources in namespace. Doesn't wait till all are deleted. <br/>
	 * <br/>
	 * <p>
	 * * Only user created secrets, service accounts and role bindings are deleted. Default will remain.
	 *
	 * @see #getUserSecrets()
	 * @see #getUserServiceAccounts()
	 * @see #getUserRoleBindings()
	 */
	public void clean() {
		// keep the order for deletion to prevent K8s creating resources again
		client.templates().delete();
		client.extensions().deployments().delete();
		client.apps().statefulSets().delete();
		client.extensions().jobs().delete();
		getDeploymentConfigs().forEach(this::deleteDeploymentConfig);
		getReplicationControllers().forEach(this::deleteReplicationController);
		client.buildConfigs().delete();
		client.imageStreams().delete();
		client.endpoints().delete();
		client.services().delete();
		client.builds().delete();
		client.routes().delete();
		client.pods().withGracePeriod(0).delete();
		client.persistentVolumeClaims().delete();
		client.autoscaling().horizontalPodAutoscalers().delete();
		client.configMaps().delete();
		getUserSecrets().forEach(this::deleteSecret);
		getUserServiceAccounts().forEach(this::deleteServiceAccount);
		getUserRoleBindings().forEach(this::deleteRoleBinding);
		client.roles().delete();
	}

	@Override
	public void close() {
		client.close();
	}

	// Logs storing
	public Path storePodLog(Pod pod, Path dirPath, String fileName) throws IOException {
		String log = getPodLog(pod);
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

	// Waiting
	public OpenShiftWaiters waiters() {
		return waiters;
	}
}
