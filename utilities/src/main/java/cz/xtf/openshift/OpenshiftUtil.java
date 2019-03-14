package cz.xtf.openshift;

import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.Service;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.entity.ContentType;

import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.TestConfiguration;
import cz.xtf.TestParent;
import cz.xtf.docker.OpenShiftNode;
import cz.xtf.http.HttpClient;
import cz.xtf.http.HttpUtil;
import cz.xtf.time.TimeUtil;
import cz.xtf.tuple.Tuple;
import cz.xtf.wait.WaitUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.KubernetesListMixedOperation;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.api.model.BuildTriggerPolicy;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.OpenshiftRole;
import io.fabric8.openshift.api.model.OpenshiftRoleBinding;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.openshift.client.ParameterValue;
import rx.Observable;
import rx.observables.StringObservable;

/**
 * This util is deprecated and will be deleted in one of future releases.
 */
@Deprecated
public class OpenshiftUtil implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(OpenshiftUtil.class);
	private static final String ANNOTATION_BUILD_POD = "openshift.io/build.pod-name";

	private static OpenshiftUtil INSTANCE;
	private final String server;
	private NamespacedOpenShiftClient defaultClient;
	private NamespacedOpenShiftClient adminClient;
	private OpenShiftContext context;

	public OpenshiftUtil(String server, OpenShiftContext context) throws MalformedURLException {
		// validate the server URL
		new URL(server);

		this.server = server;
		this.context = context;
	}

	public static OpenshiftUtil getInstance() {
		if (INSTANCE == null) {
			try {
				INSTANCE = new OpenshiftUtil(TestConfiguration.masterUrl(), OpenShiftContext.getContext());
			} catch (MalformedURLException ex) {
				throw new IllegalArgumentException("OpenShift Master URL is malformed", ex);
			}
		}

		return INSTANCE;
	}

	public String getServer() {
		return server;
	}

	public OpenShiftContext getContext() {
		return context;
	}

	public void setOpenShiftContext(OpenShiftContext context) {
		if (context != null) {
			this.context = context;

			// close the current client
			if (defaultClient != null) {
				defaultClient.close();
			}
			defaultClient = null;
		}
	}

	public String getAdminToken() {
		return getToken(TestConfiguration.adminUsername(), TestConfiguration.adminPassword());
	}

	public String getDefaultToken() {
		return getToken(TestConfiguration.masterUsername(), TestConfiguration.masterPassword());
	}

	public String getToken(String username, String password) {
		try {
			String url = TestConfiguration.masterUrl() + "/oauth/authorize?response_type=token&client_id=openshift-challenging-client";
			List<Header> headers = HttpClient.get(url)
					.basicAuth(username, password)
					.preemptiveAuth()
					.disableRedirect()
					.responseHeaders();
			Optional<Header> location = headers.stream().filter(h -> "Location".equals(h.getName())).findFirst();
			if (location.isPresent()) {
				LOGGER.debug("Location: {}", location.get().getValue());
				String token = StringUtils.substringBetween(location.get().getValue(), "#access_token=", "&");
				LOGGER.info("Oauth token: {}", token);
				return token;
			}
			LOGGER.info("Location header with token not found");
			return null;
		} catch (IOException e) {
			LOGGER.error("Error getting token from master", e);
			return null;
		}
	}

	public <R> R withDefaultUser(Function<NamespacedOpenShiftClient, R> f) {
		if (defaultClient == null) {

			final OpenShiftConfigBuilder openShiftConfigBuilder = new OpenShiftConfigBuilder()
					.withMasterUrl(TestConfiguration.masterUrl())
					.withTrustCerts(true)
					.withRequestTimeout(120_000)
					.withNamespace(context.getNamespace());
			if (TestConfiguration.openshiftOnline()) {
				openShiftConfigBuilder.withOauthToken(context.getToken());
			} else {
				openShiftConfigBuilder
						.withPassword(context.getPassword())
						.withUsername(context.getUsername())
				;
			}

			defaultClient = new DefaultOpenShiftClient(openShiftConfigBuilder.build());
		}

		return f.apply(defaultClient);
	}

	public <R> R withAdminUser(Function<NamespacedOpenShiftClient, R> f) {
		if (TestConfiguration.openshiftOnline()) {
			throw new IllegalArgumentException("Openshift online does not support admin users.");
		}
		if (adminClient == null) {
			OpenShiftConfig config = new OpenShiftConfigBuilder()
					.withMasterUrl(TestConfiguration.masterUrl())
					.withTrustCerts(true)
					.withUsername(TestConfiguration.adminUsername())
					.withPassword(TestConfiguration.adminPassword()).build();
			adminClient = new DefaultOpenShiftClient(config);
		}

		return f.apply(adminClient);
	}

	// general purpose methods
	public Collection<HasMetadata> createResources(HasMetadata... resources) {
		return createResources(Arrays.asList(resources));
	}

	public Collection<HasMetadata> createResources(List<HasMetadata> resources) {
		KubernetesList list = new KubernetesListBuilder().withItems(resources)
				.build();
		List<HasMetadata> created = createResources(list).getItems();
		created.stream().filter(x -> x.getMetadata().getLabels() != null && x.getMetadata().getLabels().containsKey(DeploymentConfigBuilder.SYNCHRONOUS_LABEL)).forEach(x -> {
			final String deploymentId = x.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL);
			try {
				LOGGER.info("Waiting for a startup of pod with syncId '{}'", deploymentId);
				WaitUtil.waitFor(WaitUtil.isAPodReady(DeploymentConfigBuilder.SYNCHRONOUS_LABEL, deploymentId));
			} catch (Exception e) {
				throw new IllegalStateException("Timeout while waiting for deployment of " + x.getMetadata().getName());
			}
		});
		return created;
	}

	public KubernetesList createResources(KubernetesList list) {
		return withDefaultUser(client -> client.lists().create(list));
	}

	// project
	public Project getProject(String name) {
		return withAdminUser(client -> {
			Optional<Project> opt = client.projects().list().getItems()
					.stream()
					.filter(proj -> proj.getMetadata().getName().equals(name))
					.findFirst();
			if (opt.isPresent()) {
				return opt.get();
			} else {
				return null;
			}
		});
	}

	public void deleteProject(String name) {
		deleteProject(getProject(name));
	}

	public void deleteProject(Project project) {
		withAdminUser(client -> client.projects().delete(project));
		String name = project.getMetadata().getName();

		try {
			WaitUtil.waitFor(() -> getProject(name) == null);
		} catch (TimeoutException | InterruptedException e) {
			throw new IllegalStateException("Unable to delete project " + name);
		}
	}

	public Project createProject(String name, boolean recreateIfExists) {
		Project existing = getProject(name);
		if (existing != null) {
			if (recreateIfExists) {
				deleteProject(existing);
			} else {
				return existing;
			}
		}

		try {
			withAdminUser(client -> client.projectrequests().createNew()
					.withNewMetadata()
					.withName(name)
					.endMetadata()
					.done()
			);
		} catch (KubernetesClientException e) {
			if (e.getMessage() != null && e.getMessage().contains("reason=AlreadyExists") && getProject(name) != null) {
				LOGGER.error("Already existing namespace due to multiple concurrent invocations are OK", e);
			} else {
				throw e;
			}
		}

		addRoleToUser(name, "admin", TestConfiguration.masterUsername());

		return getProject(name);
	}

	public void addRoleToUser(String namespace, String role, String name) {
		OpenshiftRoleBinding roleBinding = getOrCreateRoleBinding(namespace, role);

		addSubjectToRoleBinding(roleBinding, "User", name);
		addUserNameToRoleBinding(roleBinding, name);

		updateRoleBinding(roleBinding);
	}

	public void addRoleToUser(String role, String name) {
		addRoleToUser(getContext().getNamespace(), role, name);
	}

	// image streams
	public ImageStream createImageStream(ImageStream imageStream) {
		return createImageStream(imageStream, context.getNamespace());
	}

	public ImageStream createImageStream(ImageStream imageStream,
			String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace)
				.imageStreams().create(imageStream));
	}

	public ImageStream getImageStream(String name) {
		return getImageStream(name, context.getNamespace());
	}

	public ImageStream getImageStream(String name, String namespace) {
		return withDefaultUser(client -> {
			Optional<ImageStream> opt = client.inNamespace(namespace)
					.imageStreams().list().getItems().stream()
					.filter(is -> is.getMetadata().getName().equals(name))
					.findFirst();
			if (opt.isPresent()) {
				return opt.get();
			} else {
				return null;
			}
		});
	}

	public Collection<ImageStream> getImageStreams() {
		return getImageStreams(context.getNamespace());
	}

	public Collection<ImageStream> getImageStreams(String namespace) {
		LOGGER.debug("Getting image streams for namespace {}", namespace);
		return withDefaultUser(client -> client.imageStreams().list()
				.getItems());
	}

	public boolean deleteImageStream(String name) {
		return deleteImageStream(getImageStream(name));
	}

	public boolean deleteImageStream(ImageStream imageStream) {
		return deleteImageStream(imageStream, context.getNamespace());
	}

	public boolean deleteImageStream(ImageStream imageStream, String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).imageStreams().delete(
				imageStream));
	}

	// pods
	public Pod createPod(Pod pod) {
		return withDefaultUser(client -> client.pods().create(pod));
	}

	public Collection<Pod> getPods() {
		return getPods(context.getNamespace());
	}

	public Collection<Pod> getPods(String namespace) {
		LOGGER.debug("Getting pods for namespace {}", namespace);
		return withDefaultUser(client -> client.inNamespace(namespace).pods()
				.list().getItems());
	}

	public Pod getPod(String podName) {
		return getPod(podName, context.getNamespace());
	}

	public Pod getPod(String podName, String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).pods()
				.withName(podName).get());
	}

	public List<Pod> findPods(Map<String, String> labels) {
		return findPods(labels, context.getNamespace());
	}

	private List<Pod> findPods(Map<String, String> labels, String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).pods()
				.withLabels(labels).list().getItems());
	}
	
	public List<Pod> findNamedPods(String name) {
		return findNamedPods("name", name);
	}

	public List<Pod> findNamedPods(String keyName, String valueName) {
		return findPods(Collections.singletonMap(keyName, valueName));
	}

	public Pod findNamedPod(String name) {
		return findNamedPod("name", name);
	}
	public Pod findNamedPod(String keyName, String valueName) {
		Collection<Pod> pods = this.findNamedPods(keyName, valueName);
		if (pods.size() != 1) {
			throw new IllegalStateException("Expected one named pod with '" + keyName + "' = '" + valueName + "' but got " + pods.size());
		} else {
			return (Pod)pods.iterator().next();
		}
	}

	public Pod findRandomlyChosenNamedPod(String name) {
		List<Pod> pods = findNamedPods(name);
		return pods.get((new Random().nextInt(pods.size())));
	}

	public void deletePod(Pod pod) {
		withDefaultUser(client -> client.pods().delete(pod));
	}

	public void deletePod(Pod pod, long gracePeriod) {
		withDefaultUserPod(pod)
				.withGracePeriod(gracePeriod).delete();
	}

	public void deletePod(Map<String, String> labels) {
		deletePod(findPods(labels).get(0));
	}

	// secrets
	public Secret createSecret(Secret secret) {
		return withDefaultUser(client -> client.secrets().create(secret));
	}

	public Collection<Secret> getSecrets() {
		return getSecrets(context.getNamespace());
	}

	public Collection<Secret> getSecrets(String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace)
				.secrets().list().getItems());
	}

	public void deleteSecret(Secret secret) {
		withDefaultUser(client -> client.secrets().delete(secret));
	}

	// services
	public Collection<Service> getServices() {
		return withDefaultUser(client -> client.services().list().getItems());
	}

	public Collection<Service> getServices(String namespace) {
		return withDefaultUser(client -> client.services().inNamespace(namespace).list().getItems());
	}

	public Service getService(String name) {
		return withDefaultUser(client -> client.services().withName(name).get());
	}

	public Service createService(Service service) {
		return withDefaultUser(client -> client.services().create(service));
	}

	public void deleteService(Service service) {
		withDefaultUser(client -> client.services().delete(service));
	}

	public void deleteService(String name) {
		withDefaultUser(client -> client.services().withName(name).delete());
	}

	// endpoints
	public Collection<Endpoints> getEndpoints() {
		return withDefaultUser(client -> client.endpoints().list().getItems());
	}

	public Endpoints getEndpoint(final String name) {
		return withDefaultUser(client -> client.endpoints().withName(name).get());
	}

	public Endpoints createEndpoint(final Endpoints endpoint) {
		return withDefaultUser(client -> client.endpoints().create(endpoint));
	}

	public void deleteEndpoint(final Endpoints endpoint) {
		withDefaultUser(client -> client.endpoints().delete(endpoint));
	}

	// routes
	public Route createRoute(Route route) {
		return withDefaultUser(client -> client.routes().create(route));
	}

	public Collection<Route> getRoutes() {
		return getRoutes(context.getNamespace());
	}

	public Stream<Route> getRoutesNamedWithPrefix(final String prefix) {
		return getRoutes().stream().filter(x -> x.getMetadata().getName().startsWith(prefix));
	}

	public Route getRoute(final String name) {
		List<Route> candidates = getRoutesNamedWithPrefix(name).collect(Collectors.toList());
		if (candidates.size() == 1) {
			return candidates.get(0);
		}
		throw new IllegalArgumentException("Cannot find unique route, there is " + candidates.size() + "candidate(s)");
	}

	public Route getExactRoute(String name) {
		return withDefaultUser(client -> client.routes().withName(name).get());
	}

	public Collection<Route> getRoutes(String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).routes()
				.list().getItems());
	}

	public void deleteRoute(Route route) {
		withDefaultUser(client -> client.routes().delete(route));
	}

	// roles
	public OpenshiftRole createRole(OpenshiftRole role) {
		return withDefaultUser(client -> client.roles().create(role));
	}

	public Collection<OpenshiftRole> getRoles() {
		return getRoles(context.getNamespace());
	}

	public Collection<OpenshiftRole> getRoles(String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).roles()
				.list().getItems());
	}

	public Stream<OpenshiftRole> getRolesNamedWithPrefix(final String prefix) {
		return getRoles().stream().filter(x -> x.getMetadata().getName().startsWith(prefix));
	}

	public void deleteRole(OpenshiftRole role) {
		withDefaultUser(client -> client.roles().delete(role));
	}

	// role bindings
	public OpenshiftRoleBinding createRoleBinding(OpenshiftRoleBinding roleBinding) {
		return withDefaultUser(client -> client.roleBindings().create(roleBinding));
	}

	public Collection<OpenshiftRoleBinding> getRoleBindings() {
		return getRoleBindings(context.getNamespace());
	}

	public Collection<OpenshiftRoleBinding> getRoleBindings(String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).roleBindings()
				.list().getItems());
	}

	public Stream<OpenshiftRoleBinding> getRoleBindingsNamedWithPrefix(final String prefix) {
		return getRoleBindings().stream().filter(x -> x.getMetadata().getName().startsWith(prefix));
	}

	public void deleteRoleBinding(OpenshiftRoleBinding roleBinding) {
		withDefaultUser(client -> client.roleBindings().delete(roleBinding));
	}

	// replicationControllers
	public Collection<ReplicationController> getReplicationControllers() {
		return withDefaultUser(client -> client.replicationControllers().list()
				.getItems());
	}

	public Collection<ReplicationController> getReplicationControllers(String namespace) {
		return withDefaultUser(client -> client.replicationControllers().inNamespace(namespace).list()
				.getItems());
	}

	public ReplicationController scaleReplicationController(
			ReplicationController rc, int replicas) {
		return withDefaultUser(client -> client.replicationControllers()
				.withName(rc.getMetadata().getName()).scale(replicas));
	}

	public void deleteReplicationController(ReplicationController rc) {
		withDefaultUser(client -> client.replicationControllers().withName(rc.getMetadata().getName()).delete());
	}

	public void deleteReplicationController(boolean cascading, ReplicationController rc) {
		withDefaultUser(client -> client.replicationControllers().withName(rc.getMetadata().getName()).cascading(cascading).delete());
	}

	/**
	 * Returns a replication controller by given name.
	 *
	 * @param name name of replication controller
	 * @throws IllegalStateException if replication controller is not found
	 */
	public ReplicationController namedReplicationController(final String name)
			throws IllegalStateException {
		ReplicationController result = withDefaultUser(client -> client
				.replicationControllers().withName(name).get());

		if (result == null) {
			throw new IllegalStateException("Named replication not found ("
					+ name + ")");
		}
		return result;
	}

	/**
	 * Returns deployment config with given name.
	 *
	 * @param name name of deployment config
	 * @throws IllegalStateException if deployment config is not found
	 */
	public DeploymentConfig namedDeploymentConfig(final String name)
			throws IllegalStateException {
		DeploymentConfig result = withDefaultUser(client -> client.deploymentConfigs().withName(name).get());

		if (result == null) {
			throw new IllegalStateException("Named replication not found ("
					+ name + ")");
		}
		return result;
	}

	// deployment configs
	public DeploymentConfig createDeploymentConfig(
			DeploymentConfig deploymentConfig) {
		return withDefaultUser(client -> client.deploymentConfigs().create(
				deploymentConfig));
	}

	public Collection<DeploymentConfig> getDeployments() {
		return withDefaultUser(client -> client.deploymentConfigs().list()
				.getItems());
	}

	public Collection<DeploymentConfig> getDeployments(final String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).deploymentConfigs().list()
				.getItems());
	}

	public void deleteDeploymentConfig(DeploymentConfig deploymentConfig) {
		withDefaultUser(client -> client.deploymentConfigs()
				.withName(deploymentConfig.getMetadata().getName())
				.cascading(true)
				.delete());
	}

	public void deleteDeploymentConfig(boolean cascading, DeploymentConfig deploymentConfig) {
		withDefaultUser(client -> client.deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).cascading(cascading).delete());
	}

	// TODO generic update method
	public DeploymentConfig updateDeploymentconfig(DeploymentConfig dc) {
		return withDefaultUser(client -> client.deploymentConfigs()
				.withName(dc.getMetadata().getName())
				.edit()
				.withMetadata(dc.getMetadata())
				.withSpec(dc.getSpec())
				.done());
	}

	public Map<String, String> getDeploymentEnvVars(final String name) {
		return namedDeploymentConfig(name).getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
	}

	public void updateDeploymentEnvVars(final String name, final Map<String, String> vars) {
		final DeploymentConfig dc = namedDeploymentConfig(name);
		dc.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(vars.entrySet().stream()
				.map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build())
				.collect(Collectors.toList()));
		updateDeploymentconfig(dc);
	}

	
	// builds
	public Collection<Build> getBuilds() {
		return getBuilds(context.getNamespace());
	}

	public Collection<Build> getBuilds(String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).builds().list().getItems());
	}

	public Build getBuild(String buildName) {
		return getBuild(buildName, context.getNamespace());
	}

	public Build getBuild(String buildName, String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace).builds().withName(buildName)
				.get());
	}

	public Build startBuild(BuildConfig buildConfig) {
		return startBuild(buildConfig, context.getNamespace());
	}

	public synchronized Build startBuild(BuildConfig buildConfig, String namespace) {
		BuildRequest request = new BuildRequestBuilder().withNewMetadata().withName(buildConfig.getMetadata().getName()).endMetadata().build();
		return withDefaultUser(client -> client.inNamespace(namespace).buildConfigs().withName(buildConfig.getMetadata().getName()).instantiate(request));
	}

	public void deleteBuild(Build build) {
		deleteBuild(build, context.getNamespace());
	}

	public void deleteBuild(Build build, String namespace) {
		withDefaultUser(client -> client.inNamespace(namespace).builds().delete(build));
	}

	// buildconfigs
	public BuildConfig createBuildConfig(BuildConfig buildConfig) {
		return createBuildConfig(buildConfig, context.getNamespace());
	}

	public BuildConfig createBuildConfig(BuildConfig buildConfig,
			String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace)
				.buildConfigs().create(buildConfig));
	}

	public BuildConfig getBuildConfig(String name, String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace)
				.buildConfigs().withName(name).get());
	}

	public BuildConfig getBuildConfig(String name) {
		return withDefaultUser(client -> client.buildConfigs().withName(name).get());
	}

	public Collection<BuildConfig> getBuildConfigs() {
		return withDefaultUser(client -> client.buildConfigs().list()
				.getItems());
	}

	public Collection<BuildConfig> getBuildConfigs(String namespace) {
		return withDefaultUser(client -> client.buildConfigs().inNamespace(namespace).list()
				.getItems());
	}

	public Map<String, String> getSourceBuildConfigEnvVars(final String name) {
		return getBuildConfig(name).getSpec().getStrategy().getSourceStrategy().getEnv().stream().collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
	}

	public void updateSourceBuildConfigEnvVars(final String name, final Map<String, String> vars) {
		updateSourceBuildConfigEnvVars(name, context.getNamespace(), vars);
	}

	public void updateSourceBuildConfigEnvVars(final String name, final String namespace, final Map<String, String> vars) {
		final BuildConfig bc = getBuildConfig(name, namespace);
		bc.getSpec().getStrategy().getSourceStrategy().setEnv(vars.entrySet().stream()
				.map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build())
				.collect(Collectors.toList()));
		updateBuildConfig(bc);
	}

	public BuildConfig updateBuildConfig(final BuildConfig bc) {
		return withDefaultUser(client -> client.buildConfigs()
				.inNamespace(bc.getMetadata().getNamespace())
				.withName(bc.getMetadata().getName())
				.edit()
				.withMetadata(bc.getMetadata())
				.withSpec(bc.getSpec())
				.done());
	}

	public void scaleDeploymentConfig(final String deploymentConfig,
			final int replicas) {
		OpenShiftNode.master()
				.executeCommand(
						String.format(
								"sudo oc scale -n %s --replicas=%s dc %s",
								getContext().getNamespace(), replicas,
								deploymentConfig));
	}

	public void addAnyUIDToServiceAccount(final String serviceAccount) {
		OpenShiftNode.master()
				.executeCommand(
						String.format(
								"sudo oadm policy add-scc-to-user %s system:serviceaccount:%s:%s",
								"anyuid",
								getContext().getNamespace(),
								serviceAccount
							)
						);
	}

	public void deleteBuildConfig(BuildConfig buildConfig) {
		deleteBuildConfig(buildConfig, context.getNamespace());
	}

	public void deleteBuildConfig(BuildConfig buildConfig, String namespace) {
		withDefaultUser(client -> client.inNamespace(namespace).buildConfigs().delete(buildConfig));
	}

	// serviceAccounts
	public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
		return withDefaultUser(client -> client.serviceAccounts().create(
				serviceAccount));
	}

	public Collection<ServiceAccount> getServiceAccounts() {
		return withDefaultUser(client -> client.serviceAccounts().list()
				.getItems());
	}

	public void deleteServiceAccount(ServiceAccount serviceAccount) {
		withDefaultUser(client -> client.serviceAccounts().delete(
				serviceAccount));
	}

	// roleBindings
	public void addRoleToServiceAccount(String role, String serviceAccountName) {
		addRoleToServiceAccount(getContext().getNamespace(), role, serviceAccountName);
	}

	public void addRoleToServiceAccount(String namespace, String role, String serviceAccountName) {
		OpenshiftRoleBinding roleBinding = getOrCreateRoleBinding(namespace, role);

		addSubjectToRoleBinding(roleBinding, "ServiceAccount", serviceAccountName);
		addUserNameToRoleBinding(roleBinding, String.format("system:serviceaccount:%s:%s", namespace, serviceAccountName));

		updateRoleBinding(roleBinding);
	}

	public void addRoleToGroup(String role, String groupName) {
		addRoleToGroup(getContext().getNamespace(), role, groupName);
	}

	public void addRoleToGroup(String namespace, String role, String groupName) {
		OpenshiftRoleBinding roleBinding = getOrCreateRoleBinding(namespace, role);

		addSubjectToRoleBinding(roleBinding, "SystemGroup", groupName);
		addGroupNameToRoleBinding(roleBinding, groupName);

		updateRoleBinding(roleBinding);
	}

	private OpenshiftRoleBinding getOrCreateRoleBinding(String namespace, String role) {
		OpenshiftRoleBinding roleBinding = withAdminUser(client -> client.inNamespace(namespace).roleBindings().withName(role).get());

		if(roleBinding == null) {
			return withAdminUser(client -> client.inNamespace(namespace).roleBindings().createNew()
					.withNewMetadata().withName(role).endMetadata()
					.withNewRoleRef().withName(role).endRoleRef()
					.done());
		}
		return roleBinding;
	}

	public OpenshiftRoleBinding updateRoleBinding(OpenshiftRoleBinding roleBinding) {
		return withAdminUser(client -> client.inNamespace(roleBinding.getMetadata().getNamespace())
				.roleBindings().withName(roleBinding.getMetadata().getName())
				.replace(roleBinding));
	}

	private void addSubjectToRoleBinding(OpenshiftRoleBinding roleBinding, String entityKind, String entityName) {
		ObjectReference subject = new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).build();

		if(!roleBinding.getSubjects().stream().anyMatch(x -> x.getName().equals(subject.getName()) && x.getKind().equals(subject.getKind()))) {
			roleBinding.getSubjects().add(subject);
		}
	}

	private void addUserNameToRoleBinding(OpenshiftRoleBinding roleBinding, String userName) {
		if( roleBinding.getUserNames() == null) {
			roleBinding.setUserNames(new ArrayList<>());
		}
		if( !roleBinding.getUserNames().contains(userName)) {
			roleBinding.getUserNames().add(userName);
		}
	}

	private void addGroupNameToRoleBinding(OpenshiftRoleBinding roleBinding, String groupName) {
		if( roleBinding.getGroupNames() == null) {
			roleBinding.setGroupNames(new ArrayList<>());
		}
		if(!roleBinding.getGroupNames().contains(groupName)) {
			roleBinding.getGroupNames().add(groupName);
		}
	}

	public void removeRoleFromServiceAccount(String role, String serviceAccountName) {
		removeRoleFromServiceAccount(getContext().getNamespace(), role, serviceAccountName);
	}

	public void removeRoleFromServiceAccount(String namespace, String role, String serviceAccountName) {
		removeRoleFromEntity(namespace, role, "ServiceAccount", serviceAccountName, String.format("system:serviceaccount:%s:%s", getContext().getNamespace(), serviceAccountName));
	}

	private void removeRoleFromEntity(String namespace, String role, String entityKind, String entityName, String userName) {
		withAdminUser(client -> {
			OpenshiftRoleBinding roleBinding = client.inNamespace(namespace).roleBindings().withName(role).get();

			if (roleBinding != null) {
				roleBinding.getSubjects().remove(new ObjectReferenceBuilder().withKind(entityKind).withName(entityName).withNamespace(namespace).build());
				roleBinding.getUserNames().remove(userName);

				updateRoleBinding(roleBinding);
			} else {
				// prevent fails.. just log error
				LOGGER.warn("No role '{}' in namespace '{}'.", role, namespace);
			}
			return null;
		});
	}

	// resource quotas
	public ResourceQuota createHardResourceQuota(String namespace, String quotaName, String resourceName, String value) {
		ResourceQuota quota = withAdminUser(client -> client.inNamespace(namespace).resourceQuotas().withName(quotaName).get());
		if(quota != null) {
			return withAdminUser(client -> client.inNamespace(namespace).resourceQuotas().withName(quotaName).edit()
					.editSpec().addToHard(resourceName, new Quantity(value)).endSpec()
					.done());
		} else {
			return withAdminUser(client -> client.inNamespace(namespace).resourceQuotas().createNew()
					.withNewMetadata().withName(quotaName).endMetadata()
					.withNewSpec().addToHard("pods", new Quantity(value)).endSpec()
					.done());
		}
	}

	// persistent volume claims
	public PersistentVolumeClaim createPersistentVolumeClaim(
			PersistentVolumeClaim pvc) {
		return withDefaultUser(client -> client.persistentVolumeClaims()
				.create(pvc));
	}

	public Collection<PersistentVolumeClaim> getPersistentVolumeClaims() {
		return withDefaultUser(client -> client.persistentVolumeClaims().list()
				.getItems());
	}

	public PersistentVolumeClaim getPersistentVolumeClaim(String name) {
		return withDefaultUser(client -> client.persistentVolumeClaims().withName(name).get());
	}

	public void deletePersistentVolumeClaim(PersistentVolumeClaim pvc) {
		withDefaultUser(client -> client.persistentVolumeClaims().delete(pvc));
	}

	public KubernetesListMixedOperation getLists() {
		return withDefaultUser(client -> client.lists());
	}

	// Horizontal pod autoscalers
	public HorizontalPodAutoscaler createHorizontalPodAutoscaler(
			final HorizontalPodAutoscaler hpa) {
		return withDefaultUser(client -> client.autoscaling().horizontalPodAutoscalers()
				.create(hpa));
	}

	public Collection<HorizontalPodAutoscaler> getHorizontalPodAutoscalers() {
		return withDefaultUser(client -> client.autoscaling().horizontalPodAutoscalers().list().getItems());
	}

	public HorizontalPodAutoscaler getHorizontalPodAutoscaler(final String name) {
		return withDefaultUser(client -> client.autoscaling().horizontalPodAutoscalers().withName(name).get());
	}

	public void deleteHorizontalPodAutoscaler(final HorizontalPodAutoscaler hpa) {
		withDefaultUser(client -> client.autoscaling().horizontalPodAutoscalers().delete(hpa));
	}

	// ConfigMaps
	public Collection<ConfigMap> getConfigMaps() {
		return withDefaultUser(client -> client.configMaps().list().getItems());
	}

	public ConfigMap getConfigMap(final String name) {
		return withDefaultUser(client -> client.configMaps().withName(name).get());
	}

	public ConfigMap createConfigMap(final ConfigMap configMap) {
		return withDefaultUser(client -> client.configMaps().create(configMap));
	}

	public void deleteConfigMap(final ConfigMap configMap) {
		withDefaultUser(client -> client.configMaps().delete(configMap));
	}

	/**
	 * Change enviroment variable in deployment config to new value
	 *
	 * @param deploymentConfig deployment config
	 * @param variableName     name of the enviroment variable to be changed
	 * @param variableValue    new/added value to the enviroment variable (depends on the
	 *                         last flag)
	 * @param overwrite        distinct if value is appended to current value or overwritten
	 */
	public void updateEnviromentVariable(
			final DeploymentConfig deploymentConfig, final String variableName,
			final String variableValue, boolean overwrite) {
		updateEnviromentVariable("dc/" + deploymentConfig.getMetadata().getName(), variableName, variableValue, overwrite);
	}

	/**
	 * Change enviroment variable in replication controller to new value
	 *
	 * @param replicationController replication controller
	 * @param variableName          name of the enviroment variable to be changed
	 * @param variableValue         new/added value to the enviroment variable (depends on the
	 *                              last flag)
	 * @param overwrite             distinct if value is appended to current value or overwritten
	 */
	public void updateEnviromentVariable(
			final ReplicationController replicationController, final String variableName,
			final String variableValue, boolean overwrite) {
		updateEnviromentVariable("rc/" + replicationController.getMetadata().getName(), variableName, variableValue, overwrite);
	}

	private void updateEnviromentVariable(final String resource, final String variableName,
			final String variableValue, boolean overwrite) {
		String overWriteString = overwrite ? "--overwrite" : "";
		String cmd = String.format("sudo oc set env -n %s %s %s=%s %s",
				getContext().getNamespace(), resource,
				variableName, variableValue, overWriteString);
		LOGGER.info(String.format("Executing command '%s'", cmd));
		OpenShiftNode.master().executeCommand(cmd);
	}

	public void startRedeployment(String deploymentName) {
		OpenShiftNode.master().executeCommand(
				String.format("sudo oc rollout latest %s -n %s",
						deploymentName, getContext().getNamespace()));
	}

	// TODO refactor to use same mechanism as manual build trigger
	public Build startBuildViaGenericWebHook(BuildConfig buildConfig) {
		BuildConfig refreshed = withDefaultUser(client -> client.buildConfigs()
				.withName(buildConfig.getMetadata().getName()).get());
		Collection<String> originalBuilds = getBuilds().stream()
				.map(build -> build.getMetadata().getName())
				.collect(Collectors.toList());

		// refresh the information
		BuildTriggerPolicy trigger = refreshed.getSpec().getTriggers().stream()
				.filter(t -> "Generic".equals(t.getType())).findFirst().get();

		String baseUrl = withDefaultUser(client -> client.getOpenshiftUrl()
				.toString());
		String webhookUrl = String.format(
				"%snamespaces/%s/buildconfigs/%s/webhooks/%s/%s", baseUrl,
				refreshed.getMetadata().getNamespace(), refreshed.getMetadata()
						.getName(), trigger.getGeneric().getSecret(), trigger
						.getType().toLowerCase());

		try {
			LOGGER.info("Invoking web hook via url {}", webhookUrl);
			String result = HttpUtil.httpPost(webhookUrl, null,
					ContentType.APPLICATION_JSON);
			LOGGER.debug("Web hook invoked with return value '{}'", result);
		} catch (Exception ex) {
			throw new IllegalStateException(
					"Could not initialize HTTP connection", ex);
		}

		final String buildConfigName = refreshed.getMetadata().getName();
		List<Build> builds = getBuilds()
				.stream()
				.filter(build -> !originalBuilds.contains(build.getMetadata()
						.getName())
						&& build.getMetadata().getName()
						.startsWith(buildConfigName))
				.collect(Collectors.toList());
		if (builds.size() != 1) {
			throw new IllegalStateException("Expected one build but got "
					+ builds.size());
		} else {
			LOGGER.info("Started build {}", builds.get(0).getMetadata()
					.getName());
			return builds.get(0);
		}
	}

	public Build waitForBuildCompletion(Build build, int timeoutInSeconds,
			boolean exceptionOnFail) {
		return waitForBuildCompletion(build, timeoutInSeconds,
				TimeUnit.SECONDS, exceptionOnFail);
	}

	public Build waitForBuildCompletion(Build build, long timeout,
			TimeUnit timeUnit, boolean exceptionOnFail) {
		LOGGER.info("Waiting for completion of build {}", build.getMetadata()
				.getName());
		long startTime = System.currentTimeMillis();
		do {
			try {
				Thread.sleep(5_000L);
			} catch (InterruptedException e) {
				throw new IllegalStateException(
						"Interrupted while waiting for build completion");
			}
			build = getBuild(build.getMetadata().getName(), build.getMetadata().getNamespace());
		} while (System.currentTimeMillis() - startTime < timeUnit
				.toMillis(timeout)
				&& !"Complete".equals(build.getStatus().getPhase())
				&& !"Failed".equals(build.getStatus().getPhase()));

		String buildName = build.getMetadata().getName();
		Optional<Pod> buildPod = getPods().stream()
				.filter(p -> p.getMetadata().getName().startsWith(buildName))
				.findFirst();
		if (buildPod.isPresent()) {
			LOGGER.info("Build '{}' ran on node '{}'", build.getMetadata()
					.getName(), buildPod.get().getStatus().getHostIP());
		} else {
			LOGGER.debug("Could not find build pod.");
		}

		switch (build.getStatus().getPhase()) {
			case "Complete":
				try {
					long start = TimeUtil.parseOpenShiftTimestamp(build.getStatus()
							.getStartTimestamp());
					long complete = TimeUtil.parseOpenShiftTimestamp(build.getStatus()
							.getCompletionTimestamp());

					LOGGER.info("Finished build {} in {}", build.getMetadata()
							.getName(), TimeUtil.millisToString(complete - start));
				} catch (ParseException x) {
					LOGGER.info("Finished build {}", build.getMetadata().getName());
					LOGGER.error("Failed to parse time!", x);
				}
				break;
			case "Failed":
				if (exceptionOnFail) {
					Path logPath = saveBuildLog(build);
					LOGGER.error("Build {} has failed, build log is saved at {}",
							build.getMetadata().getName(), logPath);
					LOGGER.debug("Failed build: {}", build);
					Assertions.fail("Build '" + build.getMetadata().getName() + "' has failed");
				}
				break;
			default:
				Assertions.fail("Timed out while waiting for build completion");
		}

		return build;
	}

	public String getBuildLog(final Build build) {
		return getBuildLog(build, this.context.getNamespace());
	}

	public String getBuildLog(final Build build, final String namespace) {
		return withDefaultUser(client -> client.inNamespace(namespace)
				.pods()
				.withName(
						client
							.inNamespace(namespace)
							.builds()
							.withName(build.getMetadata().getName())
							.get()
						.getMetadata()
						.getAnnotations()
						.get(ANNOTATION_BUILD_POD)
				)
				.getLog());
	}

	private PodResource<Pod, DoneablePod> withDefaultUserPod(Pod pod) {
		if (pod.getMetadata().getNamespace() != null) {
			return withDefaultUser(c -> c.pods().inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName()));
		}
		else {
			return withDefaultUser(c -> c.pods().withName(pod.getMetadata().getName()));
		}
	}

	public String getRuntimeLog(final Pod pod) {
		return withDefaultUserPod(pod).getLog();
	}

	public Observable<String> observeRuntimeLog(final Pod pod) {
		final LogWatch watcher = withDefaultUserPod(pod).watchLog();
		return StringObservable.byLine(StringObservable
				.from(new InputStreamReader(watcher.getOutput())));
	}

	// templates

	/**
	 * This method will delete the existing template and replace it with a new
	 * one (all in current namespace) and process it afterwards.
	 */
	public KubernetesList processTemplate(Template template, Map<String, String> parameters) {
		ParameterValue[] values = processParameters(parameters);

		return withDefaultUser(client -> {
			// delete existing
			client.templates().withName(template.getMetadata().getName()).delete();
			// create new
			client.templates().create(template);
			// return processed template
			return client.templates().withName(template.getMetadata().getName()).process(values);
		});
	}

	public KubernetesList processTemplate(String name,
			Map<String, String> parameters) {
		return processTemplate(context.getNamespace(), name, parameters);
	}

	public KubernetesList processTemplate(String namespace, String name,
			Map<String, String> parameters) {
		ParameterValue[] values = processParameters(parameters);

		return withDefaultUser(client -> client.templates()
				.inNamespace(namespace).withName(name).process(values));
	}

	private ParameterValue[] processParameters(Map<String, String> parameters) {
		return parameters
				.entrySet()
				.stream()
				.map(entry -> new ParameterValue(entry.getKey(), entry
						.getValue())).collect(Collectors.toList())
				.toArray(new ParameterValue[parameters.size()]);
	}

	public void cleanProject() {
		cleanProject(false);
	}

	public void cleanProject(boolean conditional) {
		if (!conditional || TestConfiguration.cleanNamespace()) {
			savePodList();
			savePodLogs();
			cleanProject(2);
		}
	}

	public void savePodLogs() {
		getPods().forEach(this::savePodLog);
	}

	public void savePodList() {
		OpenshiftUtil.getProjectLogsDir().toFile().mkdirs();
		try (final FileWriter writer = new FileWriter(OpenshiftUtil.getProjectLogsDir().resolve(TestParent.getCurrentTestClass() + "-pods.log").toFile())) {
			for (final Pod p : getPods()) {
				writer.append(p.toString());
			}
		} catch (IOException e) {
			LOGGER.error("Could not write pod list for project", e);
		}
	}

	public void savePodLogs(final String phase) {
		getPods().forEach(x -> savePodLog(x, phase));
	}

	private void cleanProject(int count) {
		if (context.getNamespace().matches("\\s*|\\s*default\\s*")) {
			throw new IllegalStateException(
					"Attempt to clean default namespace!");
		}

		// keep the order for deletion to prevent K8s creating resources again
		getDeployments().forEach(x -> deleteDeploymentConfig(false, x));
		getBuildConfigs().forEach(this::deleteBuildConfig);
		getReplicationControllers().forEach(x -> deleteReplicationController(false, x));
		getImageStreams().forEach(this::deleteImageStream);
		getEndpoints().forEach(this::deleteEndpoint);
		getServices().forEach(this::deleteService);
		getBuilds().forEach(this::deleteBuild);
		getRoutes().forEach(this::deleteRoute);
		getPods().forEach(pod -> deletePod(pod, 0L));
		getPersistentVolumeClaims().forEach(this::deletePersistentVolumeClaim);
		getHorizontalPodAutoscalers().forEach(this::deleteHorizontalPodAutoscaler);
		getConfigMaps().forEach(this::deleteConfigMap);
		// Remove only user secrets
		getSecrets().stream()
				.filter(s -> !s.getType().startsWith("kubernetes.io/"))
				.forEach(this::deleteSecret);

		getRoleBindings().stream()
			.filter(rb -> !rb.getRoleRef().getName().startsWith("admin"))
			.filter(rb -> !rb.getRoleRef().getName().startsWith("system"))
			.filter(rb -> rb.getSubjects().stream().allMatch(s -> s.getNamespace() == null || s.getNamespace().equals(TestConfiguration.masterNamespace())))
			.forEach(this::deleteRoleBinding);
		getRoles().forEach(this::deleteRole);

		// Remove only users service accounts
		getServiceAccounts().stream()
				.filter(sa -> !sa.getMetadata().getName().equals("builder"))
				.filter(sa -> !sa.getMetadata().getName().equals("default"))
				.filter(sa -> !sa.getMetadata().getName().equals("deployer"))
				.forEach(this::deleteServiceAccount);

		if (count > 0) {
			try {
				Thread.sleep(1_000L);
			} catch (InterruptedException e) {
				LOGGER.warn("Interrupted while cleaning project.", e);
			}
			cleanProject(count - 1);
		}
	}

	public void cleanImages(boolean conditional) {
		if (!conditional || TestConfiguration.cleanNamespace()) {
			OpenShiftNode
					.master()
					.executeCommand(
							String.format(
									"oc login %s --insecure-skip-tls-verify -u %s -p %s",
									TestConfiguration.masterUrl(),
									TestConfiguration.adminUsername(),
									TestConfiguration.adminPassword()));
			OpenShiftNode
					.master()
					.executeCommand(
							"oadm prune images --keep-tag-revisions=1 --confirm");
		}
	}

	public Path saveBuildLog(Build build) {
		int suffix = 0;
		Path logFile = null;
		do {
			try {
				getBuildLogsDir().toFile().mkdirs();
				logFile = Files.createFile(getBuildLogsDir().resolve(
						String.format("%s_%s.log", build.getMetadata()
								.getName(), suffix)));
			} catch (FileAlreadyExistsException ex) {
				suffix++;
			} catch (IOException ex) {
				LOGGER.debug("Unable to create log file", ex);
				return null;
			}
		} while (logFile == null);

		try {
			List<String> buildLog = Arrays.asList(getBuildLog(build)
					.split("\n"));
			Files.write(logFile, buildLog, Charset.defaultCharset());

			return logFile;
		} catch (Throwable t) {
			LOGGER.warn("Unable to save the build log", t);
			return null;
		}
	}

	public Path savePodLog(final Pod pod) {
		return savePodLog(pod, null);
	}

	public Path savePodLog(final Pod pod, final String phase) {
		Path logFile = null;
		try {
			final Path logDir = getPodLogsDir().resolve(
					TestParent.getCurrentTestClass());
			logDir.toFile().mkdirs();
			logFile = (phase == null) ? Files.createFile(logDir.resolve(String
					.format("%s.log", pod.getMetadata().getName()))) : Files
					.createFile(logDir.resolve(String.format("%s-%s.log",
							phase, pod.getMetadata().getName())));
		} catch (IOException | KubernetesClientException ex) {
			LOGGER.debug("Unable to create log file", ex);
			return null;
		}
		try {
			List<String> podLog = Arrays.asList(getRuntimeLog(pod).split("\n"));
			Files.write(logFile, podLog, Charset.defaultCharset());
			return logFile;
		} catch (Throwable t) {
			LOGGER.warn("Unable to save logs for pod: {}", pod.getMetadata().getName());
			LOGGER.debug("Stacktrace: ", t);
			return null;
		}
	}

	public Path savePodLogWithObserver(final Pod pod, final String phase) {
		Path logFile = null;
		try {
			final Path logDir = getPodLogsDir().resolve(
					TestParent.getCurrentTestClass());
			logDir.toFile().mkdirs();
			logFile = (phase == null) ? Files.createFile(logDir.resolve(String
					.format("%s.log", pod.getMetadata().getName()))) : Files
					.createFile(logDir.resolve(String.format("%s-%s.log",
							phase, pod.getMetadata().getName())));
		} catch (IOException | KubernetesClientException ex) {
			LOGGER.debug("Unable to create log file", ex);
			return null;
		}
		try {
			List<String> podLog = new ArrayList<>();
			observeRuntimeLog(pod).forEach(podLog::add);
			Files.write(logFile, podLog, Charset.defaultCharset());
			return logFile;
		} catch (Throwable t) {
			LOGGER.warn("Unable to save logs for pod: {}", pod.getMetadata().getName());
			LOGGER.debug("Stacktrace: ", t);
			return null;
		}
	}

	public static final Path getPodLogsDir() {
		return Paths.get("log", "pods");
	}

	public static final Path getBuildLogsDir() {
		return Paths.get("log", "builds");
	}

	public static final Path getProjectLogsDir() {
		return Paths.get("log", "project");
	}

	public List<OpenShiftNode> getNodes() {
		return withAdminUser(client -> client.nodes().list().getItems())
				.stream()
				.map(node -> new OpenShiftNode(node.getMetadata().getName(),
						OpenShiftNode.Status.parseString(node.getStatus().getConditions()),
						node.getMetadata().getLabels()))
				.collect(Collectors.toList());
	}

	@SafeVarargs
	public final List<OpenShiftNode> getNodes(
			Tuple.Pair<String, String>... labels) {
		return getNodes()
				.stream()
				.filter(node -> Arrays.stream(labels).allMatch(
						label -> label.getSecond().equals(
								node.getLabels().get(label.getFirst()))))
				.collect(Collectors.toList());
	}

	public Map<String, OpenShiftNode> getNodesAsMap() {
		return getNodes().stream().collect(
				Collectors.toMap(OpenShiftNode::getHostname,
						Function.identity()));
	}

	@Override
	public void close() throws Exception {
		if (defaultClient != null) {
			defaultClient.close();
		}
		if (adminClient != null) {
			adminClient.close();
		}
	}

	public List<Event> getEvents(String namespace) {
		return withDefaultUser(client -> client.events().inNamespace(namespace).list()).getItems();
	}
}
