package cz.xtf.core.openshift;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.openshift.crd.CustomResourceDefinitionContextProvider;
import cz.xtf.core.openshift.helpers.ResourceFunctions;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.SupplierWaiter;
import cz.xtf.core.waiting.Waiter;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildStatus;

public class OpenShiftWaiters {
    private OpenShift openShift;
    private FailFastCheck failFast;

    OpenShiftWaiters(OpenShift openShift) {
        this(openShift, () -> false);
    }

    OpenShiftWaiters(OpenShift openShift, FailFastCheck failFast) {
        this.openShift = openShift;
        this.failFast = failFast;
    }

    /**
     * @param openShift openshift client
     * @param failFast {@link BooleanSupplier} that returns true if waiter should fail due to error state of i.e. OpenShift
     *
     * @return returns Openshift waiters
     */
    public static OpenShiftWaiters get(OpenShift openShift, FailFastCheck failFast) {
        return new OpenShiftWaiters(openShift, failFast);
    }

    /**
     * Creates waiter for latest build completion with a build timeout which is preconfigured to be up to 10 minutes
     * (but can also be customized by setting {@code xtf.waiting.build.timeout}), 5 seconds interval check and both
     * logging points.
     *
     * @param buildConfigName build name to wait upon
     * @return Waiter instance
     */
    public Waiter hasBuildCompleted(String buildConfigName) {
        Supplier<String> supplier = () -> Optional.ofNullable(openShift.getLatestBuild(buildConfigName))
                .map(Build::getMetadata).map(ObjectMeta::getName)
                .map(openShift::getBuild).map(Build::getStatus).map(BuildStatus::getPhase)
                .orElse(null);
        String reason = "Waiting for completion of latest build " + buildConfigName;

        return new SupplierWaiter<>(supplier, "Complete"::equals, "Failed"::equals, TimeUnit.MINUTES,
                WaitingConfig.buildTimeout(), reason)
                        .logPoint(Waiter.LogPoint.BOTH)
                        .failFast(failFast)
                        .interval(5_000);
    }

    /**
     * Creates waiter for build completion with a build timeout which is preconfigured to be up to 10 minutes (but can
     * also be customized by setting {@code xtf.waiting.build.timeout}), 5 seconds interval check and both logging
     * points.
     *
     * @param build to be waited upon.
     * @return Waiter instance
     */
    public Waiter hasBuildCompleted(Build build) {
        Supplier<String> supplier = () -> openShift.getBuild(build.getMetadata().getName()).getStatus().getPhase();
        String reason = "Waiting for completion of build " + build.getMetadata().getName();

        return new SupplierWaiter<>(supplier, "Complete"::equals, "Failed"::equals, TimeUnit.MINUTES,
                WaitingConfig.buildTimeout(), reason)
                        .logPoint(Waiter.LogPoint.BOTH)
                        .failFast(failFast)
                        .interval(5_000);
    }

    /**
     * Creates waiter for latest build presence with default timeout,
     * 5 seconds interval check and both logging points.
     *
     * @param buildConfigName name of buildConfig for which build to be waited upon
     * @return Waiter instance
     */
    public Waiter isLatestBuildPresent(String buildConfigName) {
        Supplier<Build> supplier = () -> openShift.getLatestBuild(buildConfigName);
        String reason = "Waiting for presence of latest build of buildconfig " + buildConfigName;

        return new SupplierWaiter<>(supplier, Objects::nonNull, reason).logPoint(Waiter.LogPoint.BOTH)
                .failFast(failFast)
                .interval(5_000);
    }

    /**
     * Create waiter for project to created with 20 seconds timeout.
     *
     * @return Waiter instance
     */
    public Waiter isProjectReady() {
        return new SimpleWaiter(() -> openShift.getProject() != null, TimeUnit.SECONDS, 20,
                "Waiting for the project to be created.")
                        .failFast(failFast);
    }

    /**
     * Creates waiter for clean project with 20 seconds timeout.
     *
     * @return Waiter instance
     */
    public Waiter isProjectClean() {
        return new SimpleWaiter(() -> {
            int crdInstances = 0;
            for (CustomResourceDefinitionContextProvider crdContextProvider : OpenShift.getCRDContextProviders()) {
                try {
                    crdInstances += ((List) (openShift.customResource(crdContextProvider.getContext())
                            .list(openShift.getNamespace()).get("items"))).size();
                } catch (KubernetesClientException kce) {
                    // CRD might not be installed on the cluster
                }
            }
            return crdInstances == 0 & openShift.listRemovableResources().isEmpty();
        }, TimeUnit.MILLISECONDS, WaitingConfig.timeoutCleanup(), "Cleaning project.")
                .failFast(failFast);
    }

    /**
     * Creates a waiter object waiting till all pods created by deployment config with name {@code dcName} are ready.
     * Tolerates any container restarts. Uses default timeout.
     *
     * @param dcName name of deploymentConfig
     * @return Waiter instance
     */
    public Waiter isDcReady(String dcName) {
        return isDcReady(dcName, Integer.MAX_VALUE);
    }

    /**
     * Creates a waiter object that waits till all pods created by deployment config with name {@code dcName} are ready.
     * Tolerates {@code restartTolerace} container restarts. Uses default timeout.
     *
     * @param dcName name of deploymentConfig
     * @param restartTolerance number of container rest
     * @return Waiter instance
     */
    public Waiter isDcReady(String dcName, int restartTolerance) {
        Supplier<List<Pod>> ps = () -> openShift.getPods(dcName);
        String reason = "Waiting till all pods created by " + dcName + " deployment config are ready";

        return isDeploymentReady(dcName, ps, restartTolerance).reason(reason);
    }

    /**
     * Creates a waiter object that waits till all pods created by deployment config with name {@code dcName} are ready.
     * Tolerates any container restarts. Uses default timeout.
     *
     * @param dcName name of deploymentConfig
     * @param version version of deploymentConfig to be waited upon
     * @return Waiter instance
     */
    public Waiter isDeploymentReady(String dcName, int version) {
        return isDeploymentReady(dcName, version, Integer.MAX_VALUE);
    }

    /**
     * Creates a waiter object that waits till all pods created by deployment config with name {@code dcName} are ready.
     * Tolerates any container restarts. Uses default timeout.
     *
     * @param dcName name of deploymentConfig
     * @param version deployment version
     * @param restartTolerance number of container rest
     * @return Waiter instance
     */
    public Waiter isDeploymentReady(String dcName, int version, int restartTolerance) {
        Supplier<List<Pod>> ps = () -> openShift.getPods(dcName, version);
        String reason = "Waiting till all pods created by " + version + ". of " + dcName + " deployment config are ready";

        return isDeploymentReady(dcName, ps, restartTolerance).reason(reason);
    }

    private Waiter isDeploymentReady(String dcName, Supplier<List<Pod>> ps, int restartTolerance) {
        int replicas = openShift.getDeploymentConfig(dcName).getSpec().getReplicas();

        Function<List<Pod>, Boolean> sc = ResourceFunctions.areExactlyNPodsReady(replicas);
        Function<List<Pod>, Boolean> fc = ResourceFunctions.haveAnyPodRestartedAtLeastNTimes(restartTolerance);

        return new SupplierWaiter<>(ps, sc, fc).failFast(failFast);
    }

    /**
     * Creates a waiter that checks that exactly n pods is ready in project.
     * Uses default timeout. Tolerates any container restarts.
     *
     * @param n number of expected pods to wait upon
     * @return Waiter instance
     */
    public Waiter areExactlyNPodsReady(int n) {
        return areExactlyNPodsReady(n, openShift::getPods).reason("Waiting for exactly " + n + " pods to be ready.");
    }

    public Waiter areExactlyNPodsReady(int n, String dcName) {
        return areExactlyNPodsReady(n, "deploymentconfig", dcName);
    }

    /**
     * Creates a waiter that checks that exactly n pods is ready in project.
     * Uses default timeout. Tolerates any container restarts.
     *
     * @param n number of expected pods to wait upon
     * @param key label key for pod filtering
     * @param value label value for pod filtering
     * @return Waiter instance
     */
    public Waiter areExactlyNPodsReady(int n, String key, String value) {
        Supplier<List<Pod>> ps = () -> openShift.getLabeledPods(key, value);
        String reason = "Waiting for exactly " + n + " pods with label " + key + "=" + value + " to be ready.";

        return areExactlyNPodsReady(n, ps).reason(reason);
    }

    private Waiter areExactlyNPodsReady(int n, Supplier<List<Pod>> podSupplier) {
        return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsReady(n)).failFast(failFast);
    }

    /**
     * Creates a waiter that checks that exactly n pods is running in project.
     * Uses default timeout. Tolerates any container restarts.
     *
     * @param n number of expected pods to wait upon
     * @return Waiter instance
     */
    public Waiter areExactlyNPodsRunning(int n) {
        return areExactlyNPodsRunning(n, openShift::getPods).reason("Waiting for exactly " + n + " pods to be running.");
    }

    public Waiter areExactlyNPodsRunning(int n, String dcName) {
        return areExactlyNPodsRunning(n, "deploymentconfig", dcName);
    }

    /**
     * Creates a waiter that checks that exactly n pods is running in project.
     * Uses default timeout. Tolerates any container restarts.
     *
     * @param n number of expected pods to wait upon
     * @param key label key for pod filtering
     * @param value label value for pod filtering
     * @return Waiter instance
     */
    public Waiter areExactlyNPodsRunning(int n, String key, String value) {
        Supplier<List<Pod>> ps = () -> openShift.getLabeledPods(key, value);
        String reason = "Waiting for exactly " + n + " pods with label " + key + "=" + value + " to be running.";

        return areExactlyNPodsRunning(n, ps).reason(reason);
    }

    private Waiter areExactlyNPodsRunning(int n, Supplier<List<Pod>> podSupplier) {
        return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsRunning(n)).failFast(failFast);
    }

    public Waiter areNoPodsPresent(String dcName) {
        return areNoPodsPresent("deploymentconfig", dcName);
    }

    /**
     * Creates a waiter that waits until there aren't any pods in project.
     * Uses default timeout.
     *
     * @param key label key for pod filtering
     * @param value label value for pod filtering
     * @return Waiter instance
     */
    public Waiter areNoPodsPresent(String key, String value) {
        Supplier<List<Pod>> ps = () -> openShift.getLabeledPods(key, value);
        String reason = "Waiting for no present pods with label " + key + "=" + value + ".";

        return areNoPodsPresent(ps).reason(reason);
    }

    private Waiter areNoPodsPresent(Supplier<List<Pod>> podSupplier) {
        return new SupplierWaiter<>(podSupplier, List::isEmpty).failFast(failFast);
    }

    public Waiter havePodsBeenRestarted(String dcName) {
        return havePodsBeenRestarted("deploymentconfig", dcName);
    }

    public Waiter havePodsBeenRestarted(String key, String value) {
        return havePodsBeenRestartedAtLeastNTimes(1, key, value);
    }

    public Waiter havePodsBeenRestartedAtLeastNTimes(int times, String dcName) {
        return havePodsBeenRestartedAtLeastNTimes(times, "deploymentconfig", dcName);
    }

    public Waiter havePodsBeenRestartedAtLeastNTimes(int times, String key, String value) {
        Supplier<List<Pod>> ps = () -> openShift.getLabeledPods(key, value);
        String reason = "Waiting for any pods with label " + key + "=" + value + " having a restart count >= " + times + ".";
        return havePodsBeenRestartedAtLeastNTimes(times, ps).reason(reason);
    }

    private Waiter havePodsBeenRestartedAtLeastNTimes(int times, Supplier<List<Pod>> podSupplier) {
        return new SupplierWaiter<>(podSupplier, ResourceFunctions.haveAnyPodRestartedAtLeastNTimes(times)).failFast(failFast);
    }
}
