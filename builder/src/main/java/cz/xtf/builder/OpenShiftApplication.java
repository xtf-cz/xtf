package cz.xtf.builder;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenShiftApplication {
    private final OpenShift openShift;
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

    /**
     * @deprecated superseded by {@link #OpenShiftApplication(ApplicationBuilder, OpenShift)}
     *             Bring your own client is a preferred way to obtain OpenShiftApplication object
     */
    @Deprecated
    public OpenShiftApplication(ApplicationBuilder appBuilder) {
        this(appBuilder, OpenShifts.master());
    }

    public OpenShiftApplication(ApplicationBuilder appBuilder, OpenShift openShift) {
        this.openShift = openShift;
        this.name = appBuilder.getName();

        secrets.addAll(appBuilder.buildSecrets());
        imageStreams.addAll(appBuilder.buildImageStreams());
        buildConfigs.addAll(appBuilder.buildBuildConfigs());
        persistentVolumeClaims.addAll(appBuilder.buildPVCs());
        deploymentConfigs.addAll(appBuilder.buildDeploymentConfigs());
        services.addAll(appBuilder.buildServices());
        routes.addAll(appBuilder.buildRoutes());
        configMaps.addAll(appBuilder.buildConfigMaps());
        roles.addAll(appBuilder.buildRoles());
        roleBindings.addAll(appBuilder.buildRoleBindings());
    }

    public void deploy() {
        createResources();
        // TODO return Waiter
    }

    private void createResources() {
        log.debug("Deploying application {}", name);

        // keep the order of deployment
        secrets = secrets.stream().map(openShift::createSecret).collect(Collectors.toList());
        serviceAccounts = serviceAccounts.stream().map(openShift::createServiceAccount).collect(Collectors.toList());
        imageStreams = imageStreams.stream().map(openShift::createImageStream).collect(Collectors.toList());
        buildConfigs = buildConfigs.stream().map(openShift::createBuildConfig).collect(Collectors.toList());
        persistentVolumeClaims = persistentVolumeClaims.stream().map(openShift::createPersistentVolumeClaim)
                .collect(Collectors.toList());
        services = services.stream().map(openShift::createService).collect(Collectors.toList());
        final List<DeploymentConfig> syncDeployments = deploymentConfigs.stream()
                .filter(x -> x.getMetadata().getLabels().containsKey(DeploymentConfigBuilder.SYNCHRONOUS_LABEL))
                .sorted((dc1, dc2) -> {
                    final int labelDc1 = Integer
                            .parseInt(dc1.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL));
                    final int labelDc2 = Integer
                            .parseInt(dc2.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL));
                    return labelDc1 - labelDc2;
                }).map(x -> {
                    final String syncId = x.getMetadata().getLabels().get(DeploymentConfigBuilder.SYNCHRONOUS_LABEL);
                    final DeploymentConfig dc = openShift.createDeploymentConfig(x);

                    if (dc.getSpec().getReplicas() > 0) {
                        try {
                            log.info("Waiting for a startup of pod with deploymentconfig '{}' ({} {})",
                                    dc.getMetadata().getName(), DeploymentConfigBuilder.SYNCHRONOUS_LABEL, syncId);
                            openShift.waiters().areExactlyNPodsReady(dc.getSpec().getReplicas(), dc.getMetadata().getName())
                                    .waitFor();
                        } catch (Exception e) {
                            throw new IllegalStateException(
                                    "Timeout while waiting for deployment of " + dc.getMetadata().getName(), e);
                        }
                    }
                    return dc;
                }).collect(Collectors.toList());
        deploymentConfigs = deploymentConfigs.stream()
                .filter(x -> !x.getMetadata().getLabels().containsKey(DeploymentConfigBuilder.SYNCHRONOUS_LABEL))
                .map(openShift::createDeploymentConfig).collect(Collectors.toList());
        deploymentConfigs.addAll(syncDeployments);
        endpoints = endpoints.stream().map(openShift::createEndpoint).collect(Collectors.toList());
        routes = routes.stream().map(openShift::createRoute).collect(Collectors.toList());
        configMaps = configMaps.stream().map(openShift::createConfigMap).collect(Collectors.toList());
        autoScalers = autoScalers.stream().map(openShift::createHorizontalPodAutoscaler).collect(Collectors.toList());
        roles = roles.stream().map(r -> openShift.rbac().roles().create(r)).collect(Collectors.toList());
        roleBindings = roleBindings.stream().map(openShift::createRoleBinding).collect(Collectors.toList());
    }
}
