package cz.xtf.openshift;

import cz.xtf.wait.SimpleWaiter;
import cz.xtf.wait.SupplierWaiter;
import cz.xtf.wait.Waiter;
import io.fabric8.kubernetes.api.model.Pod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
		String reason = "Waiting for completion of build " + buildName;
		Supplier<String> supplier =  () -> openShiftUtil.getBuild(buildName).getStatus().getPhase();

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

			return !cleanedResources.contains(false);
		};

		return new SimpleWaiter(bs, TimeUnit.SECONDS,20,"Cleaning project.");
	}

	/**
	 * Creates a waiter that checks whether all deployment pods are ready.
	 * Defaults to 2 minutes timeout.
	 *
	 * @param dcName name of deploymentConfig
	 * @return Waiter instance
	 */
	public Waiter isDeploymentReady(String dcName) {
		int replicas = openShiftUtil.getDeploymentConfig(dcName).getSpec().getReplicas();
		return areExactlyNPodsReady(replicas,() -> openShiftUtil.getPods(dcName));
	}

	/**
	 * Creates a waiter that checks whether all deployment pods are ready with specific version.
	 * Defaults to 2 minutes timeout.
	 *
	 * @param dcName name of deploymentConfig
	 * @param version deployment version
	 * @return Waiter instance
	 */
	public Waiter isDeploymentReady(String dcName, int version) {
		int replicas = openShiftUtil.getDeploymentConfig(dcName).getSpec().getReplicas();
		return areExactlyNPodsReady(replicas, () -> openShiftUtil.getPods(dcName, version));
	}

	/**
	 * Creates a waiter that checks that exactly n pods is ready in project.
	 * Defaults to 2 minutes timeout.
	 *
	 * @param n number of expected pods to wait upon
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsReady(int n) {
		return areExactlyNPodsReady(n, openShiftUtil::getPods).reason("Waiting for exactly " + n + " pods to be ready.");
	}

	/**
	 * Creates a waiter that checks that exactly n pods is ready in project.
	 * Defaults to 2 minutes timeout.
	 *
	 * @param n number of expected pods to wait upon
	 * @param key label key for pod filtering
	 * @param value label value for pod filtering
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsReady(int n, String key, String value) {
		return areExactlyNPodsReady(n, () -> openShiftUtil.getLabeledPods(key, value)).reason("Waiting for exactly " + n + " pods with label " + key + "=" + value + " to be ready.");
	}

	/**
	 * Creates a waiter that checks that exactly n pods is ready in project
	 * specified by pod supplier. Defaults to 2 minutes timeout.
	 *
	 * @param n number of expected pods to wait upon
	 * @param podSupplier pod list generator to be checked
	 * @return Waiter instance
	 */
	public Waiter areExactlyNPodsReady(int n, Supplier<List<Pod>> podSupplier) {
		BooleanSupplier bs = () -> {
			List<Pod> pods = podSupplier.get();
			return pods.size() == n && pods.stream().allMatch(OpenShiftUtil::isPodReady);
		};
		return new SimpleWaiter(bs, TimeUnit.MINUTES, 2);
	}
}
