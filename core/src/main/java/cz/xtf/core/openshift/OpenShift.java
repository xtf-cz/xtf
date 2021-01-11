package cz.xtf.core.openshift;

import static cz.xtf.core.config.OpenShiftConfig.routeDomain;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.event.EventList;
import cz.xtf.core.openshift.crd.CustomResourceDefinitionContextProvider;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.kubernetes.client.AppsAPIGroupClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildRequest;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectRequest;
import io.fabric8.openshift.api.model.ProjectRequestBuilder;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.RouteSpecBuilder;
import io.fabric8.openshift.api.model.Template;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import io.fabric8.openshift.client.ParameterValue;
import lombok.extern.slf4j.Slf4j;
import rx.Observable;
import rx.observables.StringObservable;

@Slf4j
public class OpenShift extends DefaultOpenShiftClient {
    private static ServiceLoader<CustomResourceDefinitionContextProvider> crdContextProviderLoader;
    private static volatile String routeSuffix;

    public static final String KEEP_LABEL = "xtf.cz/keep";
    /**
     * This label is supposed to be used for any resource created by the XTF to easily distinguish which resources have
     * been created by XTF automation.
     * NOTE: at the moment only place where this is used is for labeling namespaces. Other usages may be added in the future.
     */
    public static final String XTF_MANAGED_LABEL = "xtf.cz/managed";
    private final AppsAPIGroupClient appsAPIGroupClient;

    /**
     * Autoconfigures the client with the default fabric8 client rules
     * 
     * @return
     */
    public static OpenShift get(String namespace) {
        Config kubeconfig = Config.autoConfigure(null);

        OpenShiftConfig openShiftConfig = new OpenShiftConfig(kubeconfig);

        setupTimeouts(openShiftConfig);

        if (StringUtils.isNotEmpty(namespace)) {
            openShiftConfig.setNamespace(namespace);
        }

        return new OpenShift(openShiftConfig);
    }

    public static OpenShift get(Path kubeconfigPath, String namespace) {
        try {
            String kubeconfigContents = new String(Files.readAllBytes(kubeconfigPath), StandardCharsets.UTF_8);
            Config kubeconfig = Config.fromKubeconfig(null, kubeconfigContents, kubeconfigPath.toAbsolutePath().toString());
            OpenShiftConfig openShiftConfig = new OpenShiftConfig(kubeconfig);

            setupTimeouts(openShiftConfig);

            if (StringUtils.isNotEmpty(namespace)) {
                openShiftConfig.setNamespace(namespace);
            }

            return new OpenShift(openShiftConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static OpenShift get(String masterUrl, String namespace, String username, String password) {
        OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder()
                .withMasterUrl(masterUrl)
                .withTrustCerts(true)
                .withNamespace(namespace)
                .withUsername(username)
                .withPassword(password)
                .build();

        setupTimeouts(openShiftConfig);

        return new OpenShift(openShiftConfig);
    }

    public static OpenShift get(String masterUrl, String namespace, String token) {
        OpenShiftConfig openShiftConfig = new OpenShiftConfigBuilder()
                .withMasterUrl(masterUrl)
                .withTrustCerts(true)
                .withNamespace(namespace)
                .withOauthToken(token)
                .build();

        setupTimeouts(openShiftConfig);

        return new OpenShift(openShiftConfig);
    }

    private static void setupTimeouts(OpenShiftConfig config) {
        config.setBuildTimeout(10 * 60 * 1000);
        config.setRequestTimeout(120_000);
        config.setConnectionTimeout(120_000);
    }

    protected static synchronized ServiceLoader<CustomResourceDefinitionContextProvider> getCRDContextProviders() {
        if (crdContextProviderLoader == null) {
            crdContextProviderLoader = ServiceLoader.load(CustomResourceDefinitionContextProvider.class);
        }
        return crdContextProviderLoader;
    }

    private final OpenShiftWaiters waiters;

    public OpenShift(OpenShiftConfig openShiftConfig) {
        super(openShiftConfig);

        appsAPIGroupClient = new AppsAPIGroupClient(httpClient, openShiftConfig);

        this.waiters = new OpenShiftWaiters(this);
    }

    public void setupPullSecret(String secret) {
        setupPullSecret("xtf-pull-secret", secret);
    }

    /**
     * Convenient method to create pull secret for authenticated image registries.
     * The secret content must be provided in "dockerconfigjson" formar.
     *
     * E.g.: {"auths":{"registry.redhat.io":{"auth":"<REDACTED_TOKEN>"}}}
     *
     * Linking Secret to ServiceAccount is based on OpenShift documentation:
     * https://docs.openshift.com/container-platform/4.2/openshift_images/managing-images/using-image-pull-secrets.html
     *
     * @param name of the Secret to be created
     * @param secret content of Secret in json format
     */
    public void setupPullSecret(String name, String secret) {
        Secret pullSecret = new SecretBuilder()
                .withNewMetadata()
                .withNewName(name)
                .addToLabels(KEEP_LABEL, "true")
                .endMetadata()
                .withNewType("kubernetes.io/dockerconfigjson")
                .withData(Collections.singletonMap(".dockerconfigjson", Base64.getEncoder().encodeToString(secret.getBytes())))
                .build();
        secrets().createOrReplace(pullSecret);
        serviceAccounts().withName("default").edit()
                .addToImagePullSecrets(new LocalObjectReferenceBuilder().withName(pullSecret.getMetadata().getName()).build())
                .done();
        serviceAccounts().withName("builder").edit()
                .addToSecrets(new ObjectReferenceBuilder().withName(pullSecret.getMetadata().getName()).build()).done();
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
        return lists().create(resources);
    }

    public boolean deleteResources(KubernetesList resources) {
        return lists().delete(resources);
    }

    public void loadResource(InputStream is) {
        load(is).deletingExisting().createOrReplace();
    }

    // Projects
    public ProjectRequest createProjectRequest() {
        return createProjectRequest(
                new ProjectRequestBuilder().withNewMetadata().withName(getNamespace()).endMetadata().build());
    }

    public ProjectRequest createProjectRequest(String name) {
        return createProjectRequest(new ProjectRequestBuilder().withNewMetadata().withName(name).endMetadata().build());
    }

    public ProjectRequest createProjectRequest(ProjectRequest projectRequest) {
        return projectrequests().create(projectRequest);
    }

    /**
     * Calls recreateProject(namespace).
     *
     * @see OpenShift#recreateProject(String)
     */
    public ProjectRequest recreateProject() {
        return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(getNamespace()).endMetadata().build());
    }

    /**
     * Creates or recreates project specified by name.
     *
     * @param name name of a project to be created
     * @return ProjectRequest instance
     */
    public ProjectRequest recreateProject(String name) {
        return recreateProject(new ProjectRequestBuilder().withNewMetadata().withName(name).endMetadata().build());
    }

    /**
     * Creates or recreates project specified by projectRequest instance.
     *
     * @return ProjectRequest instance
     */
    public ProjectRequest recreateProject(ProjectRequest projectRequest) {
        boolean deleted = deleteProject(projectRequest.getMetadata().getName());
        if (deleted) {
            BooleanSupplier bs = () -> getProject(projectRequest.getMetadata().getName()) == null;
            new SimpleWaiter(bs, TimeUnit.MILLISECONDS, WaitingConfig.timeout(),
                    "Waiting for old project deletion before creating new one").waitFor();
        }
        return createProjectRequest(projectRequest);
    }

    /**
     * Tries to retrieve project with name 'name'. Swallows KubernetesClientException
     * if project doesn't exist or isn't accessible for user.
     *
     * @param name name of requested project.
     * @return Project instance if accessible otherwise null.
     */
    public Project getProject(String name) {
        try {
            return projects().withName(name).get();
        } catch (KubernetesClientException e) {
            return null;
        }
    }

    public Project getProject() {
        try {
            return projects().withName(getNamespace()).get();
        } catch (KubernetesClientException e) {
            return null;
        }
    }

    public boolean deleteProject() {
        return deleteProject(getNamespace());
    }

    public boolean deleteProject(String name) {
        return getProject(name) != null ? projects().withName(name).delete() : false;
    }

    // ImageStreams
    public ImageStream createImageStream(ImageStream imageStream) {
        return imageStreams().create(imageStream);
    }

    public ImageStream getImageStream(String name) {
        return imageStreams().withName(name).get();
    }

    public List<ImageStream> getImageStreams() {
        return imageStreams().list().getItems();
    }

    // StatefulSets
    public StatefulSet createStatefulSet(StatefulSet statefulSet) {
        return appsAPIGroupClient.statefulSets().create(statefulSet);
    }

    public StatefulSet getStatefulSet(String name) {
        return appsAPIGroupClient.statefulSets().withName(name).get();
    }

    public List<StatefulSet> getStatefulSets() {
        return appsAPIGroupClient.statefulSets().list().getItems();
    }

    public boolean deleteImageStream(ImageStream imageStream) {
        return imageStreams().delete(imageStream);
    }

    // ImageStreamsTags
    public ImageStreamTag createImageStreamTag(ImageStreamTag imageStreamTag) {
        return imageStreamTags().create(imageStreamTag);
    }

    public ImageStreamTag getImageStreamTag(String imageStreamName, String tag) {
        return imageStreamTags().withName(imageStreamName + ":" + tag).get();
    }

    public List<ImageStreamTag> getImageStreamTags() {
        return imageStreamTags().list().getItems();
    }

    public boolean deleteImageStreamTag(ImageStreamTag imageStreamTag) {
        return imageStreamTags().delete(imageStreamTag);
    }

    // Pods
    public Pod createPod(Pod pod) {
        return pods().create(pod);
    }

    public Pod getPod(String name) {
        return pods().withName(name).get();
    }

    public String getPodLog(String deploymentConfigName) {
        return getPodLog(this.getAnyPod(deploymentConfigName));
    }

    public String getPodLog(Pod pod) {
        return getPodLog(pod, getAnyContainer(pod));
    }

    public String getPodLog(Pod pod, String containerName) {
        return getPodLog(pod, getContainer(pod, containerName));
    }

    public String getPodLog(Pod pod, Container container) {
        if (Objects.nonNull(container)) {
            return pods().withName(pod.getMetadata().getName()).inContainer(container.getName()).getLog();
        } else {
            return pods().withName(pod.getMetadata().getName()).getLog();
        }
    }

    /**
     * Return logs of all containers from the pod
     *
     * @param pod Pod to retrieve from
     * @return Map of container name / logs
     */
    public Map<String, String> getAllContainerLogs(Pod pod) {
        return retrieveFromPodContainers(pod, container -> this.getPodLog(pod, container));
    }

    public Reader getPodLogReader(Pod pod) {
        return getPodLogReader(pod, getAnyContainer(pod));
    }

    public Reader getPodLogReader(Pod pod, String containerName) {
        return getPodLogReader(pod, getContainer(pod, containerName));
    }

    public Reader getPodLogReader(Pod pod, Container container) {
        if (Objects.nonNull(container)) {
            return pods().withName(pod.getMetadata().getName()).inContainer(container.getName()).getLogReader();
        } else {
            return pods().withName(pod.getMetadata().getName()).getLogReader();
        }
    }

    /**
     * Return readers on logs of all containers from the pod
     *
     * @param pod Pod to retrieve from
     * @return Map of container name / reader
     */
    public Map<String, Reader> getAllContainerLogReaders(Pod pod) {
        return retrieveFromPodContainers(pod, container -> this.getPodLogReader(pod, container));
    }

    public Observable<String> observePodLog(String dcName) {
        return observePodLog(getAnyPod(dcName));
    }

    public Observable<String> observePodLog(Pod pod) {
        return observePodLog(pod, getAnyContainer(pod));
    }

    public Observable<String> observePodLog(Pod pod, String containerName) {
        return observePodLog(pod, getContainer(pod, containerName));
    }

    public Observable<String> observePodLog(Pod pod, Container container) {
        LogWatch watcher;
        if (Objects.nonNull(container)) {
            watcher = pods().withName(pod.getMetadata().getName()).inContainer(container.getName()).watchLog();
        } else {
            watcher = pods().withName(pod.getMetadata().getName()).watchLog();
        }
        return StringObservable.byLine(StringObservable.from(new InputStreamReader(watcher.getOutput())));
    }

    /**
     * Return obervables on logs of all containers from the pod
     *
     * @param pod Pod to retrieve from
     * @return Map of container name / logs obervable
     */
    public Map<String, Observable<String>> observeAllContainerLogs(Pod pod) {
        return retrieveFromPodContainers(pod, container -> this.observePodLog(pod, container));
    }

    public List<Pod> getPods() {
        return pods().list().getItems();
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
        return pods().withLabels(labels).list().getItems();
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
        return pods().withName(pod.getMetadata().getName()).withGracePeriod(gracePeriod).delete();
    }

    /**
     * Deletes pods with specified label.
     *
     * @param key key of the label
     * @param value value of the label
     * @return True if any pod has been deleted
     */
    public boolean deletePods(String key, String value) {
        return pods().withLabel(key, value).delete();
    }

    public boolean deletePods(Map<String, String> labels) {
        return pods().withLabels(labels).delete();
    }

    /**
     * Retrieve pod containers
     *
     * @param pod pod to retrieve in
     * @return List of containers of empty list if none ...
     */
    public List<Container> getAllContainers(Pod pod) {
        return Optional.ofNullable(pod.getSpec()).map(PodSpec::getContainers).orElse(new ArrayList<>());
    }

    /**
     * Retrieve any container from the given pod
     *
     * @param pod Pod to retrieve from
     * @return One random container from the pod
     */
    public Container getAnyContainer(Pod pod) {
        List<Container> containers = getAllContainers(pod);
        return containers.get(new Random().nextInt(containers.size()));
    }

    public Container getContainer(Pod pod, String containerName) {
        return getAllContainers(pod).stream()
                .filter(c -> c.getName().equals(containerName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Cannot find container with name " + containerName + " in pod " + pod.getMetadata().getName()));
    }

    private <R> Map<String, R> retrieveFromPodContainers(Pod pod, Function<Container, R> containerRetriever) {
        return getAllContainers(pod).stream().collect(Collectors.toMap(Container::getName, containerRetriever));
    }

    // Secrets
    public Secret createSecret(Secret secret) {
        return secrets().create(secret);
    }

    public Secret getSecret(String name) {
        return secrets().withName(name).get();
    }

    public List<Secret> getSecrets() {
        return secrets().list().getItems();
    }

    /**
     * Retrieves secrets that aren't considered default. Secrets that are left out contain type starting with 'kubernetes.io/'.
     *
     * @return List of secrets that aren't considered default.
     */
    public List<Secret> getUserSecrets() {
        return secrets().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems().stream()
                .filter(s -> !s.getType().startsWith("kubernetes.io/"))
                .collect(Collectors.toList());
    }

    public boolean deleteSecret(Secret secret) {
        return secrets().delete(secret);
    }

    // Services
    public Service createService(Service service) {
        return services().create(service);
    }

    public Service getService(String name) {
        return services().withName(name).get();
    }

    public List<Service> getServices() {
        return services().list().getItems();
    }

    public boolean deleteService(Service service) {
        return services().delete(service);
    }

    // Endpoints
    public Endpoints createEndpoint(Endpoints endpoint) {
        return endpoints().create(endpoint);
    }

    public Endpoints getEndpoint(String name) {
        return endpoints().withName(name).get();
    }

    public List<Endpoints> getEndpoints() {
        return endpoints().list().getItems();
    }

    public boolean deleteEndpoint(Endpoints endpoint) {
        return endpoints().delete(endpoint);
    }

    // Routes
    public Route createRoute(Route route) {
        return routes().create(route);
    }

    public Route getRoute(String name) {
        return routes().withName(name).get();
    }

    public List<Route> getRoutes() {
        return routes().list().getItems();
    }

    public boolean deleteRoute(Route route) {
        return routes().delete(route);
    }

    /**
     * Generates hostname as is expected to be generated by OpenShift instance.
     *
     * @param routeName prefix for route hostname
     * @return Hostname as is expected to be generated by OpenShift
     */
    public String generateHostname(String routeName) {
        if (routeSuffix == null) {
            synchronized (OpenShift.class) {
                if (routeSuffix == null) {
                    if (StringUtils.isNotBlank(routeDomain())) {
                        routeSuffix = routeDomain();
                    } else {
                        routeSuffix = retrieveRouteSuffix();
                    }
                }
            }
        }
        return routeName + "-" + getNamespace() + "." + routeSuffix;
    }

    private String retrieveRouteSuffix() {
        Route route = new Route();
        route.setMetadata(new ObjectMetaBuilder().withName("probing-route").build());
        route.setSpec(new RouteSpecBuilder().withNewTo().withKind("Service").withName("imaginary-service").endTo().build());

        route = createRoute(route);
        deleteRoute(route);

        return route.getSpec().getHost().replaceAll("^[^\\.]*\\.(.*)", "$1");
    }

    // ReplicationControllers - Only for internal usage with clean
    private List<ReplicationController> getReplicationControllers() {
        return replicationControllers().list().getItems();
    }

    private boolean deleteReplicationController(ReplicationController replicationController) {
        return replicationControllers().withName(replicationController.getMetadata().getName()).cascading(false).delete();
    }

    // DeploymentConfigs
    public DeploymentConfig createDeploymentConfig(DeploymentConfig deploymentConfig) {
        return deploymentConfigs().create(deploymentConfig);
    }

    public DeploymentConfig getDeploymentConfig(String name) {
        return deploymentConfigs().withName(name).get();
    }

    public List<DeploymentConfig> getDeploymentConfigs() {
        return deploymentConfigs().list().getItems();
    }

    /**
     * Returns first container environment variables.
     *
     * @param name name of deploymentConfig
     * @return Map of environment variables
     */
    public Map<String, String> getDeploymentConfigEnvVars(String name) {
        Map<String, String> envVars = new HashMap<>();
        getDeploymentConfig(name).getSpec().getTemplate().getSpec().getContainers().get(0).getEnv()
                .forEach(envVar -> envVars.put(envVar.getName(), envVar.getValue()));
        return envVars;
    }

    public DeploymentConfig updateDeploymentconfig(DeploymentConfig deploymentConfig) {
        return deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).replace(deploymentConfig);
    }

    /**
     * Updates deployment config environment variables with envVars values.
     *
     * @param name name of deploymentConfig
     * @param envVars environment variables
     */
    public DeploymentConfig updateDeploymentConfigEnvVars(String name, Map<String, String> envVars) {
        DeploymentConfig dc = getDeploymentConfig(name);

        List<EnvVar> vars = envVars.entrySet().stream()
                .map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build())
                .collect(Collectors.toList());
        dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().removeIf(x -> envVars.containsKey(x.getName()));
        dc.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv().addAll(vars);

        return updateDeploymentconfig(dc);
    }

    public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig) {
        return deleteDeploymentConfig(deploymentConfig, false);
    }

    public boolean deleteDeploymentConfig(DeploymentConfig deploymentConfig, boolean cascading) {
        return deploymentConfigs().withName(deploymentConfig.getMetadata().getName()).cascading(cascading).delete();
    }

    /**
     * Scales deployment config to specified number of replicas.
     *
     * @param name name of deploymentConfig
     * @param replicas number of target replicas
     */
    public void scale(String name, int replicas) {
        deploymentConfigs().withName(name).scale(replicas);
    }

    /**
     * Redeploys deployment config to latest version.
     *
     * @param name name of deploymentConfig
     */
    public void deployLatest(String name) {
        deploymentConfigs().withName(name).deployLatest();
    }

    // Builds
    public Build getBuild(String name) {
        return inNamespace(getNamespace()).builds().withName(name).get();
    }

    public Build getLatestBuild(String buildConfigName) {
        long lastVersion = buildConfigs().withName(buildConfigName).get().getStatus().getLastVersion();
        return getBuild(buildConfigName + "-" + lastVersion);
    }

    public List<Build> getBuilds() {
        return builds().list().getItems();
    }

    public String getBuildLog(Build build) {
        return builds().withName(build.getMetadata().getName()).getLog();
    }

    public Reader getBuildLogReader(Build build) {
        return builds().withName(build.getMetadata().getName()).getLogReader();
    }

    public boolean deleteBuild(Build build) {
        return builds().delete(build);
    }

    public Build startBuild(String buildConfigName) {
        BuildRequest request = new BuildRequestBuilder().withNewMetadata().withName(buildConfigName).endMetadata().build();
        return buildConfigs().withName(buildConfigName).instantiate(request);
    }

    public Build startBinaryBuild(String buildConfigName, File file) {
        return buildConfigs().withName(buildConfigName).instantiateBinary().fromFile(file);
    }

    // BuildConfigs
    public BuildConfig createBuildConfig(BuildConfig buildConfig) {
        return buildConfigs().create(buildConfig);
    }

    public BuildConfig getBuildConfig(String name) {
        return buildConfigs().withName(name).get();
    }

    public List<BuildConfig> getBuildConfigs() {
        return buildConfigs().list().getItems();
    }

    /**
     * Returns environment variables of buildConfig specified under sourceStrategy.
     *
     * @param name name of buildConfig
     * @return environment variables
     */
    public Map<String, String> getBuildConfigEnvVars(String name) {
        return getBuildConfig(name).getSpec().getStrategy().getSourceStrategy().getEnv().stream()
                .collect(Collectors.toMap(EnvVar::getName, EnvVar::getValue));
    }

    public BuildConfig updateBuildConfig(BuildConfig buildConfig) {
        return buildConfigs().withName(buildConfig.getMetadata().getName()).replace(buildConfig);
    }

    /**
     * Updates build config with specified environment variables.
     *
     * @param name name of buildConfig
     * @param envVars environment variables
     */
    public BuildConfig updateBuildConfigEnvVars(String name, Map<String, String> envVars) {
        BuildConfig bc = getBuildConfig(name);

        List<EnvVar> vars = envVars.entrySet().stream()
                .map(x -> new EnvVarBuilder().withName(x.getKey()).withValue(x.getValue()).build())
                .collect(Collectors.toList());
        bc.getSpec().getStrategy().getSourceStrategy().getEnv().removeIf(x -> envVars.containsKey(x.getName()));
        bc.getSpec().getStrategy().getSourceStrategy().getEnv().addAll(vars);

        return updateBuildConfig(bc);
    }

    public boolean deleteBuildConfig(BuildConfig buildConfig) {
        return buildConfigs().delete(buildConfig);
    }

    // ServiceAccounts
    public ServiceAccount createServiceAccount(ServiceAccount serviceAccount) {
        return serviceAccounts().create(serviceAccount);
    }

    public ServiceAccount getServiceAccount(String name) {
        return serviceAccounts().withName(name).get();
    }

    public List<ServiceAccount> getServiceAccounts() {
        return serviceAccounts().list().getItems();
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
        return serviceAccounts().withLabelNotIn(KEEP_LABEL, "", "true").list().getItems().stream()
                .filter(sa -> !sa.getMetadata().getName().matches("builder|default|deployer"))
                .collect(Collectors.toList());
    }

    public boolean deleteServiceAccount(ServiceAccount serviceAccount) {
        return serviceAccounts().delete(serviceAccount);
    }

    // RoleBindings
    public RoleBinding createRoleBinding(RoleBinding roleBinding) {
        return rbac().roleBindings().create(roleBinding);
    }

    public RoleBinding getRoleBinding(String name) {
        return rbac().roleBindings().withName(name).get();
    }

    public List<RoleBinding> getRoleBindings() {
        return rbac().roleBindings().list().getItems();
    }

    public Role getRole(String name) {
        return rbac().roles().withName(name).get();
    }

    public List<Role> getRoles() {
        return rbac().roles().list().getItems();
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
        return rbac().roleBindings().withLabelNotIn(KEEP_LABEL, "", "true")
                .withLabelNotIn("olm.owner.kind", "ClusterServiceVersion").list().getItems().stream()
                .filter(rb -> !rb.getMetadata().getName()
                        .matches("admin|system:deployers|system:image-builders|system:image-pullers"))
                .collect(Collectors.toList());
    }

    public boolean deleteRoleBinding(RoleBinding roleBinding) {
        return rbac().roleBindings().delete(roleBinding);
    }

    public RoleBinding addRoleToUser(String roleName, String username) {
        return addRoleToUser(roleName, null, username);
    }

    public RoleBinding addRoleToUser(String roleName, String roleKind, String username) {
        RoleBinding roleBinding = getOrCreateRoleBinding(roleName);
        addSubjectToRoleBinding(roleBinding, "User", username, null);
        return updateRoleBinding(roleBinding);
    }

    public RoleBinding addRoleToServiceAccount(String roleName, String serviceAccountName) {
        return addRoleToServiceAccount(roleName, serviceAccountName, getNamespace());
    }

    public RoleBinding addRoleToServiceAccount(String roleName, String serviceAccountName, String namespace) {
        return addRoleToServiceAccount(roleName, null, serviceAccountName, namespace);
    }

    public RoleBinding addRoleToServiceAccount(String roleName, String roleKind, String serviceAccountName, String namespace) {
        RoleBinding roleBinding = getOrCreateRoleBinding(roleName, roleKind);
        addSubjectToRoleBinding(roleBinding, "ServiceAccount", serviceAccountName, namespace);
        return updateRoleBinding(roleBinding);
    }

    /**
     * Most of the groups are `system:*` wide therefore use `kind: ClusterRole`
     *
     * @Deprecated use method {@link #addRoleToGroup(String, String, String)}
     *
     */
    @Deprecated
    public RoleBinding addRoleToGroup(String roleName, String groupName) {
        RoleBinding roleBinding = getOrCreateRoleBinding(roleName);
        addSubjectToRoleBinding(roleBinding, "Group", groupName, null);
        return updateRoleBinding(roleBinding);
    }

    public RoleBinding addRoleToGroup(String roleName, String roleKind, String groupName) {
        RoleBinding roleBinding = getOrCreateRoleBinding(roleName, roleKind);
        addSubjectToRoleBinding(roleBinding, "Group", groupName, null);
        return updateRoleBinding(roleBinding);
    }

    private RoleBinding getOrCreateRoleBinding(String name) {
        return getOrCreateRoleBinding(name, null);
    }

    private RoleBinding getOrCreateRoleBinding(String name, String kind) {
        RoleBinding roleBinding = getRoleBinding(name);
        if (roleBinding == null) {
            // {admin, edit, view} are K8s ClusterRole that are considered user-facing
            if (kind == null) {
                kind = "Role";
                if (Arrays.asList("admin", "edit", "view").contains(name)) {
                    kind = "ClusterRole";
                }
            }
            roleBinding = new RoleBindingBuilder()
                    .withNewMetadata().withName(name).endMetadata()
                    .withNewRoleRef().withKind(kind).withName(name).endRoleRef()
                    .build();
            createRoleBinding(roleBinding);
        }
        return roleBinding;
    }

    public RoleBinding updateRoleBinding(RoleBinding roleBinding) {
        return rbac().roleBindings().withName(roleBinding.getMetadata().getName()).replace(roleBinding);
    }

    private void addSubjectToRoleBinding(RoleBinding roleBinding, String entityKind, String entityName) {
        addSubjectToRoleBinding(roleBinding, entityKind, entityName, null);
    }

    private void addSubjectToRoleBinding(RoleBinding roleBinding, String entityKind, String entityName,
            String entityNamespace) {
        SubjectBuilder subjectBuilder = new SubjectBuilder().withKind(entityKind).withName(entityName);
        if (entityNamespace != null) {
            subjectBuilder.withNamespace(entityNamespace);
        }
        Subject subject = subjectBuilder.build();
        if (roleBinding.getSubjects().stream()
                .noneMatch(x -> x.getName().equals(subject.getName()) && x.getKind().equals(subject.getKind()))) {
            roleBinding.getSubjects().add(subject);
        }
    }

    public RoleBinding removeRoleFromServiceAccount(String roleName, String serviceAccountName) {
        return removeRoleFromEntity(roleName, "ServiceAccount", serviceAccountName);
    }

    public RoleBinding removeRoleFromEntity(String roleName, String entityKind, String entityName) {
        RoleBinding roleBinding = this.rbac().roleBindings().withName(roleName).get();

        if (roleBinding != null) {
            roleBinding.getSubjects().removeIf(s -> s.getName().equals(entityName) && s.getKind().equals(entityKind));
            return updateRoleBinding(roleBinding);
        }
        return null;
    }

    // ResourceQuotas
    public ResourceQuota createResourceQuota(ResourceQuota resourceQuota) {
        return resourceQuotas().create(resourceQuota);
    }

    public ResourceQuota getResourceQuota(String name) {
        return resourceQuotas().withName(name).get();
    }

    public boolean deleteResourceQuota(ResourceQuota resourceQuota) {
        return resourceQuotas().delete(resourceQuota);
    }

    // Persistent volume claims
    public PersistentVolumeClaim createPersistentVolumeClaim(PersistentVolumeClaim pvc) {
        return persistentVolumeClaims().create(pvc);
    }

    public PersistentVolumeClaim getPersistentVolumeClaim(String name) {
        return persistentVolumeClaims().withName(name).get();
    }

    public List<PersistentVolumeClaim> getPersistentVolumeClaims() {
        return persistentVolumeClaims().list().getItems();
    }

    public boolean deletePersistentVolumeClaim(PersistentVolumeClaim pvc) {
        return persistentVolumeClaims().delete(pvc);
    }

    // HorizontalPodAutoscalers
    public HorizontalPodAutoscaler createHorizontalPodAutoscaler(HorizontalPodAutoscaler hpa) {
        return autoscaling().v1().horizontalPodAutoscalers().create(hpa);
    }

    public HorizontalPodAutoscaler getHorizontalPodAutoscaler(String name) {
        return autoscaling().v1().horizontalPodAutoscalers().withName(name).get();
    }

    public List<HorizontalPodAutoscaler> getHorizontalPodAutoscalers() {
        return autoscaling().v1().horizontalPodAutoscalers().list().getItems();
    }

    public boolean deleteHorizontalPodAutoscaler(HorizontalPodAutoscaler hpa) {
        return autoscaling().v1().horizontalPodAutoscalers().delete(hpa);
    }

    // ConfigMaps
    public ConfigMap createConfigMap(ConfigMap configMap) {
        return configMaps().create(configMap);
    }

    public ConfigMap getConfigMap(String name) {
        return configMaps().withName(name).get();
    }

    public List<ConfigMap> getConfigMaps() {
        return configMaps().list().getItems();
    }

    /**
     * Retrieves all configmaps but "kube-root-ca.crt" which is created out of the box.
     *
     * @return List of configmaps created by user
     */
    public List<ConfigMap> getUserConfigMaps() {
        return configMaps().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems().stream()
                .filter(cm -> !cm.getMetadata().getName().equals("kube-root-ca.crt"))
                .collect(Collectors.toList());
    }

    public boolean deleteConfigMap(ConfigMap configMap) {
        return configMaps().delete(configMap);
    }

    // Templates
    private void updateTemplateApiVersion(Template template) {
        if (OpenShifts.getVersion().startsWith("3")) {
            template.setApiVersion("template.openshift.io/v1");
        }
    }

    public Template createTemplate(Template template) {
        updateTemplateApiVersion(template);
        return templates().create(template);
    }

    public Template getTemplate(String name) {
        return templates().withName(name).get();
    }

    public List<Template> getTemplates() {
        return templates().list().getItems();
    }

    public boolean deleteTemplate(String name) {
        return templates().withName(name).delete();
    }

    public boolean deleteTemplate(Template template) {
        updateTemplateApiVersion(template);
        return templates().delete(template);
    }

    public Template loadAndCreateTemplate(InputStream is) {
        Template t = templates().load(is).get();
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
        return templates().withName(name).process(values);
    }

    public KubernetesList processAndDeployTemplate(String name, Map<String, String> parameters) {
        return createResources(processTemplate(name, parameters));
    }

    private ParameterValue[] processParameters(Map<String, String> parameters) {
        return parameters.entrySet().stream().map(entry -> new ParameterValue(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()).toArray(new ParameterValue[parameters.size()]);
    }

    // Nodes
    public Node getNode(String name) {
        return nodes().withName(name).get();
    }

    public List<Node> getNodes() {
        return nodes().list().getItems();
    }

    public List<Node> getNodes(Map<String, String> labels) {
        return nodes().withLabels(labels).list().getItems();
    }

    // Events
    public EventList getEventList() {
        return new EventList(events().list().getItems());
    }

    /**
     * Use {@link OpenShift#getEventList()} instead
     */
    @Deprecated
    public List<Event> getEvents() {
        return events().list().getItems();
    }

    // Port Forward
    public LocalPortForward portForward(String deploymentConfigName, int remotePort, int localPort) {
        return portForward(getAnyPod(deploymentConfigName), remotePort, localPort);
    }

    public LocalPortForward portForward(Pod pod, int remotePort, int localPort) {
        return pods().withName(pod.getMetadata().getName()).portForward(remotePort, localPort);
    }

    // PodShell
    public PodShell podShell(String dcName) {
        return podShell(getAnyPod(dcName));
    }

    public PodShell podShell(Pod pod) {
        return new PodShell(this, pod);
    }

    public PodShell podShell(Pod pod, String containerName) {
        return new PodShell(this, pod, containerName);
    }

    // Clean up function
    /**
     * Deletes all* resources in namespace. Doesn't wait till all are deleted. <br/>
     * <br/>
     * <p>
     * * Only user created configmaps, secrets, service accounts and role bindings are deleted. Default will remain.
     *
     * @see #getUserConfigMaps()
     * @see #getUserSecrets()
     * @see #getUserServiceAccounts()
     * @see #getUserRoleBindings()
     */
    public Waiter clean() {
        for (CustomResourceDefinitionContextProvider crdContextProvider : OpenShift.getCRDContextProviders()) {
            try {
                customResource(crdContextProvider.getContext()).delete(getNamespace());
                log.debug("DELETE :: " + crdContextProvider.getContext().getName() + " instances");
            } catch (KubernetesClientException kce) {
                log.debug(crdContextProvider.getContext().getName() + " might not be installed on the cluster.", kce);
            }
        }

        templates().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        apps().deployments().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        apps().replicaSets().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        apps().statefulSets().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        batch().jobs().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        deploymentConfigs().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        replicationControllers().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        buildConfigs().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        imageStreams().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        endpoints().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        services().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        builds().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        routes().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        pods().withLabelNotIn(KEEP_LABEL, "", "true").withGracePeriod(0).delete();
        persistentVolumeClaims().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        autoscaling().v1().horizontalPodAutoscalers().withLabelNotIn(KEEP_LABEL, "", "true").delete();
        getUserConfigMaps().forEach(this::deleteConfigMap);
        getUserSecrets().forEach(this::deleteSecret);
        getUserServiceAccounts().forEach(this::deleteServiceAccount);
        getUserRoleBindings().forEach(this::deleteRoleBinding);
        rbac().roles().withLabelNotIn(KEEP_LABEL, "", "true").withLabelNotIn("olm.owner.kind", "ClusterServiceVersion")
                .delete();

        for (HasMetadata hasMetadata : listRemovableResources()) {
            log.warn("DELETE LEFTOVER :: " + hasMetadata.getKind() + "/" + hasMetadata.getMetadata().getName());
            resource(hasMetadata).withGracePeriod(0).cascading(true).delete();
        }

        return waiters.isProjectClean();
    }

    List<HasMetadata> listRemovableResources() {
        // keep the order for deletion to prevent K8s creating resources again
        List<HasMetadata> removables = new ArrayList<>();
        removables.addAll(templates().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(apps().deployments().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(apps().replicaSets().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(batch().jobs().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(deploymentConfigs().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(apps().statefulSets().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(replicationControllers().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(buildConfigs().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(imageStreams().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(endpoints().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(services().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(builds().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(routes().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(pods().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(persistentVolumeClaims().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list().getItems());
        removables.addAll(autoscaling().v1().horizontalPodAutoscalers().withLabelNotIn(OpenShift.KEEP_LABEL, "", "true").list()
                .getItems());
        removables.addAll(getUserConfigMaps());
        removables.addAll(getUserSecrets());
        removables.addAll(getUserServiceAccounts());
        removables.addAll(getUserRoleBindings());
        removables.addAll(rbac().roles().withLabelNotIn(KEEP_LABEL, "", "true")
                .withLabelNotIn("olm.owner.kind", "ClusterServiceVersion").list().getItems());

        return removables;
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
        Path filePath = dirPath.resolve(fileName);

        Files.createDirectories(dirPath);
        Files.createFile(filePath);
        Files.write(filePath, log.getBytes());

        return filePath;
    }

    // Waiting

    /**
     * Use {@link OpenShiftWaiters#get(OpenShift, cz.xtf.core.waiting.failfast.FailFastCheck)} instead.
     */
    @Deprecated
    public OpenShiftWaiters waiters() {
        return waiters;
    }
}
