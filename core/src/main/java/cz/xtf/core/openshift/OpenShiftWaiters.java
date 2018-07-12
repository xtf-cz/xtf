package cz.xtf.core.openshift;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.openshift.helpers.ResourceFunctions;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.SupplierWaiter;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.api.model.Build;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
public class OpenShiftWaiters {
	private OpenShift openShift;

	OpenShiftWaiters(OpenShift openShift) {
		this.openShift = openShift;
	}

	/**
	 * Creates waiter for build completion with preconfigured timeout 10 minutes,
	 * 5 seconds interval check and both logging points.
	 *
	 * @param buildName name of build to be waited upon.
	 * @return Waiter instance
	 */
	public Waiter hasBuildCompleted(String buildName) {
		Supplier<String> supplier = () -> openShift.getBuild(buildName).getStatus().getPhase();
		String reason = "Waiting for completion of build " + buildName;

		Runnable logBuildPodLog = () -> {
			Build build = openShift.getBuild(buildName);
			if (build != null) {
				log.debug("hasBuildCompleted build {} log follows", buildName);
				try (BufferedReader reader = new BufferedReader(openShift.getBuildLogReader(build))) {
					final Logger buildLogger = LoggerFactory.getLogger(OpenShiftWaiters.class.getName() + "." + build.getMetadata().getName());
					reader.lines().forEach(line ->
						buildLogger.debug(line.trim())
					);
				} catch (IOException e) {
					log.debug("Error reading " + buildName + " log", e);
				}
				log.debug("hasBuildCompleted build {} log ends", buildName);
			}
		};

		return new SupplierWaiter<>(supplier, "Complete"::equals, "Failed"::equals, TimeUnit.MINUTES, 10, reason).logPoint(Waiter.LogPoint.BOTH).interval(5_000)
				.onFailure(logBuildPodLog)
				.onTimeout(logBuildPodLog);
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

		return new SupplierWaiter<>(supplier, Objects::nonNull, TimeUnit.MILLISECONDS, WaitingConfig.timeout(), reason).logPoint(Waiter.LogPoint.BOTH).interval(5_000);
	}

	/**
	 * Creates waiter for clean project with 20 seconds timeout.
	 *
	 * @return Waiter instance
	 */
	public Waiter isProjectClean() {
		BooleanSupplier bs = () -> {
			List<Boolean> cleanedResources = new ArrayList<>();
			cleanedResources.add(openShift.extensions().deployments().list().getItems().isEmpty());
			cleanedResources.add(openShift.extensions().jobs().list().getItems().isEmpty());
			cleanedResources.add(openShift.getDeploymentConfigs().isEmpty());
			cleanedResources.add(openShift.apps().statefulSets().list().getItems().isEmpty());
			cleanedResources.add(openShift.replicationControllers().list().getItems().isEmpty());
			cleanedResources.add(openShift.getBuildConfigs().isEmpty());
			cleanedResources.add(openShift.getImageStreams().isEmpty());
			cleanedResources.add(openShift.getEndpoints().isEmpty());
			cleanedResources.add(openShift.getServices().isEmpty());
			cleanedResources.add(openShift.getBuilds().isEmpty());
			cleanedResources.add(openShift.getRoutes().isEmpty());
			cleanedResources.add(openShift.getPods().isEmpty());
			cleanedResources.add(openShift.getPersistentVolumeClaims().isEmpty());
			cleanedResources.add(openShift.getHorizontalPodAutoscalers().isEmpty());
			cleanedResources.add(openShift.getConfigMaps().isEmpty());
			cleanedResources.add(openShift.getUserSecrets().isEmpty());
			cleanedResources.add(openShift.getUserServiceAccounts().isEmpty());
			cleanedResources.add(openShift.getUserRoleBindings().isEmpty());
			cleanedResources.add(openShift.getRoles().isEmpty());

			return !cleanedResources.contains(false);
		};

		return new SimpleWaiter(bs, TimeUnit.SECONDS, 20, "Cleaning project.");
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

		Runnable logPodLogs = () -> {
			log.debug("isDeploymentReady pods log follows");
			ps.get().forEach(pod -> {
				final String podName = pod.getMetadata().getName();
				log.debug("isDeploymentReady pod {} logs follows", podName);
				try (BufferedReader reader = new BufferedReader(openShift.getPodLogReader(pod))) {
					final Logger buildLogger = LoggerFactory.getLogger(OpenShiftWaiters.class.getName() + "." + podName);
					reader.lines().forEach(line ->
							buildLogger.debug(line.trim())
					);
				} catch (IOException e) {
					log.debug("Error reading " + podName + " log", e);
				}
				log.debug("isDeploymentReady pod {} logs end", podName);
			});
			log.debug("isDeploymentReady pods log ends");
		};

		return new SupplierWaiter<>(ps, sc, fc, TimeUnit.MILLISECONDS, WaitingConfig.timeout())
				.onTimeout(logPodLogs)
				.onFailure(logPodLogs);
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
		return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsReady(n), TimeUnit.MILLISECONDS, WaitingConfig.timeout());
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
		return new SupplierWaiter<>(podSupplier, ResourceFunctions.areExactlyNPodsRunning(n), TimeUnit.MILLISECONDS, WaitingConfig.timeout());
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

	private static Waiter areNoPodsPresent(Supplier<List<Pod>> podSupplier) {
		return new SupplierWaiter<>(podSupplier, List::isEmpty, TimeUnit.MILLISECONDS, WaitingConfig.timeout());
	}
}
