package cz.xtf.openshift;

import cz.xtf.wait.SimpleWaiter;
import cz.xtf.wait.SupplierWaiter;
import cz.xtf.wait.Waiter;
import io.fabric8.kubernetes.api.model.Pod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class OpenShiftWaiters {
	private OpenShiftUtil openShiftUtil;

	OpenShiftWaiters(OpenShiftUtil openShiftUtil) {
		this.openShiftUtil = openShiftUtil;

	}

	/**
	 * Creates waiter for build completion with preconfigured timeout 10 minutes,
	 * 5 seconds interval check and both logging points.
	 *
	 * @param buildName name of build to be waited upon.
	 * @return Waiter instance
	 */
	public Waiter hasBuildCompleted(String buildName) {
		Supplier<String> supplier =  () -> openShiftUtil.getBuild(buildName).getStatus().getPhase();
		String reason = "Waiting for completion of build " + buildName;

		return new SupplierWaiter<>(supplier, "Complete"::equals, "Failed"::equals, TimeUnit.MINUTES, 10, reason).logPoint(Waiter.LogPoint.BOTH).interval(5_000);
	}

	/**
	 * Creates waiter for clean project with 20 seconds timeout.
	 *
	 * @return Waiter instance
	 */
	public Waiter isProjectClean() {
		BooleanSupplier bs = () -> {
			List<Boolean> cleanedResources = new ArrayList<>();
			cleanedResources.add(openShiftUtil.client().extensions().deployments().list().getItems().isEmpty());
			cleanedResources.add(openShiftUtil.client().extensions().jobs().list().getItems().isEmpty());
			cleanedResources.add(openShiftUtil.getDeploymentConfigs().isEmpty());
			cleanedResources.add(openShiftUtil.client().replicationControllers().list().getItems().isEmpty());
			cleanedResources.add(openShiftUtil.getBuildConfigs().isEmpty());
			cleanedResources.add(openShiftUtil.getImageStreams().isEmpty());
			cleanedResources.add(openShiftUtil.getEndpoints().isEmpty());
			cleanedResources.add(openShiftUtil.getServices().isEmpty());
			cleanedResources.add(openShiftUtil.getBuilds().isEmpty());
			cleanedResources.add(openShiftUtil.getRoutes().isEmpty());
			cleanedResources.add(openShiftUtil.getPods().isEmpty());
			cleanedResources.add(openShiftUtil.getPersistentVolumeClaims().isEmpty());
			cleanedResources.add(openShiftUtil.getHorizontalPodAutoscalers().isEmpty());
			cleanedResources.add(openShiftUtil.getConfigMaps().isEmpty());
			cleanedResources.add(openShiftUtil.getUserSecrets().isEmpty());
			cleanedResources.add(openShiftUtil.getUserServiceAccounts().isEmpty());
			cleanedResources.add(openShiftUtil.getUserRoleBindings().isEmpty());

			return !cleanedResources.contains(false);
		};

		return new SimpleWaiter(bs, TimeUnit.SECONDS, 20, "Cleaning project.");
	}

	/**
	 * Creates a waiter object waiting till all pods created by deployment config with name {@code dcName} are ready.
	 * Tolerates any container restarts. Defaults to 3 minutes timeout.
	 *
	 * @param dcName name of deploymentConfig
	 * @return Waiter instance
	 */
	public Waiter isDcReady(String dcName) {
		return isDcReady(dcName, Integer.MAX_VALUE);
	}

	/**
	 * Creates a waiter object that waits till all pods created by deployment config with name {@code dcName} are ready.
	 * Tolerates {@code restartTolerace} container restarts. Defaults to 3 minutes timeout.
	 *
	 * @param dcName name of deploymentConfig
	 * @param restartTolerance number of container rest
	 * @return Waiter instance
	 */
	public Waiter isDcReady(String dcName, int restartTolerance) {
		Supplier<List<Pod>> ps = () -> openShiftUtil.getPods(dcName);
		String reason = "Waiting till all pods created by " + dcName + " deployment config are ready";

		return isDeploymentReady(dcName, ps, restartTolerance).reason(reason);
	}

	/**
	 * Creates a waiter object that waits till all pods created by deployment config with name {@code dcName} are ready.
	 * Tolerates any container restarts. Defaults to 3 minutes timeout.
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
	 * Tolerates any container restarts. Defaults to 3 minutes timeout.
	 *
	 * @param dcName name of deploymentConfig
	 * @param version deployment version
	 * @param restartTolerance number of container rest
	 * @return Waiter instance
	 */
	public Waiter isDeploymentReady(String dcName, int version, int restartTolerance) {
		Supplier<List<Pod>> ps = () -> openShiftUtil.getPods(dcName, version);
		String reason = "Waiting till all pods created by " + version + ". of " + dcName + " deployment config are ready";

		return isDeploymentReady(dcName, ps, restartTolerance).reason(reason);
	}

	private Waiter isDeploymentReady(String dcName, Supplier<List<Pod>> ps, int restartTolerance) {
		int replicas = openShiftUtil.getDeploymentConfig(dcName).getSpec().getReplicas();

		Function<List<Pod>, Boolean> sc = ResourceFunctions.areExactlyNPodsReady(replicas);
		Function<List<Pod>, Boolean> fc = ResourceFunctions.haveAnyPodRestartedAtLeastNTimes(restartTolerance);

		return new SupplierWaiter<>(ps, sc, fc, TimeUnit.MINUTES, 3);
	}

	/**
	 * Creates a waiter that checks that exactly n pods is ready in project.
	 * Defaults to 3 minutes timeout. Tolerates any container restarts.
	 *
	 * @param n number of expected pods to wait upon
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsReady(int n) {
		return areExactlyNPodsReady(n, openShiftUtil::getPods).reason("Waiting for exactly " + n + " pods to be ready.");
	}

	/**
	 * Creates a waiter that checks that exactly n pods is ready in project.
	 * Defaults to 3 minutes timeout. Tolerates any container restarts.
	 *
	 * @param n number of expected pods to wait upon
	 * @param key label key for pod filtering
	 * @param value label value for pod filtering
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsReady(int n, String key, String value) {
		Supplier<List<Pod>> ps = () -> openShiftUtil.getLabeledPods(key, value);
		String reason = "Waiting for exactly " + n + " pods with label " + key + "=" + value + " to be ready.";

		return areExactlyNPodsReady(n, ps).reason(reason);
	}

	private Waiter areExactlyNPodsReady(int n, Supplier<List<Pod>> podSupplier) {
		return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsReady(n), TimeUnit.MINUTES, 3);
	}

	/**
	 * Creates a waiter that checks that exactly n pods is running in project.
	 * Defaults to 3 minutes timeout. Tolerates any container restarts.
	 *
	 * @param n number of expected pods to wait upon
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsRunning(int n) {
		return areExactlyNPodsRunning(n, openShiftUtil::getPods).reason("Waiting for exactly " + n + " pods to be running.");
	}

	/**
	 * Creates a waiter that checks that exactly n pods is running in project.
	 * Defaults to 3 minutes timeout. Tolerates any container restarts.
	 *
	 * @param n number of expected pods to wait upon
	 * @param key label key for pod filtering
	 * @param value label value for pod filtering
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsRunning(int n, String key, String value) {
		Supplier<List<Pod>> ps = () -> openShiftUtil.getLabeledPods(key, value);
		String reason = "Waiting for exactly " + n + " pods with label " + key + "=" + value + " to be running.";

		return areExactlyNPodsRunning(n, ps).reason(reason);
	}

	private Waiter areExactlyNPodsRunning(int n, Supplier<List<Pod>> podSupplier) {
		return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsRunning(n), TimeUnit.MINUTES, 3);
	}
}
