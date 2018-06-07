package cz.xtf.build;

import cz.xtf.TestConfiguration;
import cz.xtf.build.BuildProcess.BuildStatus;
import cz.xtf.openshift.OpenshiftUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Singleton class responsible for centralized shared builds management.
 * <p>
 * At initialization creates specified namespace if not present and configures necessary rolebinding.
 * <p>
 * Set xtf.config.build.namespace=buildNamespace for used namespace</br>
 * Set xtf.config.build.force.rebuild=true for forced rebuild once per jvm run.</br>
 * Set xtf.config.build.binary=true for running binary build
 */
@Slf4j
public class BuildManagerV2 {
	private final Map<BuildDefinition, BuildProcess> builds;

	public static BuildManagerV2 get() {
		return BuildManagerHolder.MANAGER;
	}

	private BuildManagerV2() {
		builds = new HashMap<>();

		OpenshiftUtil openshift = OpenshiftUtil.getInstance();
		openshift.createProject(TestConfiguration.buildNamespace(), false);
		openshift.addRoleToGroup(TestConfiguration.buildNamespace(), "system:image-puller", "system:authenticated");
		openshift.createHardResourceQuota(TestConfiguration.buildNamespace(), "max-running-builds", "pods", "5");
	}

	/**
	 * Will deploy undeployed build to shared namespace, waits on completion.
	 *
	 * @param definition build to be deployed
	 */
	public void deployBuild(BuildDefinition definition) {
		deployBuild(true, definition);
	}

	/**
	 * Will deploy undeployed build to shared namespace.
	 *
	 * @param waitForCompletion whether to wait or not on build completion
	 * @param definition 			build to be deployed
	 */
	public void deployBuild(boolean waitForCompletion, BuildDefinition definition) {
		BuildProcess process = builds.getOrDefault(definition, BuildProcessFactory.getProcess(definition));
		builds.putIfAbsent(definition, process);

		BuildStatus status = process.getBuildStatus();
		if (TestConfiguration.forceRebuild()
				|| status == BuildStatus.NOT_DEPLOYED
				|| status == BuildStatus.OLD_IMAGE
				|| status == BuildStatus.GIT_REPO_GONE
				|| status == BuildStatus.ERROR
				|| status == BuildStatus.FAILED) {
			process.deleteBuild();
			process.deployBuild();
			log.info("Building {}, reason: {}, force rebuild: {}", process.getBuildName(), status, TestConfiguration.forceRebuild());
		} else if (status == BuildStatus.SOURCE_CHANGE) {
			process.updateBuild();
			log.info("Building {}, reason: {}", process.getBuildName(), status, TestConfiguration.forceRebuild());
		} else {
			log.info("Build {} present, status: {}", process.getBuildName(), status, TestConfiguration.forceRebuild());
		}

		if (waitForCompletion) {
			process.waitForCompletion();
		}
	}

	/**
	 * Will deploy collection of builds on Openshift to shared namespace, doesn't wait on completion.
	 *
	 * @param definitions collection of builds to be deployed
	 */
	public void deployBuilds(Collection<? extends BuildDefinition> definitions) {
		definitions.forEach(buildDef -> deployBuild(false, buildDef));
	}

	/**
	 * Will wait for build completion.
	 *
	 * @param definition
	 */
	public void waitForBuildCompletion(BuildDefinition definition) {
		builds.get(definition).waitForCompletion();
	}

	/**
	 * Will wait for build completion.
	 *
	 * @param definition
	 * @param timeout timeout for build in minutes
	 */
	public void waitForBuildCompletion(BuildDefinition definition, long timeout) {
		builds.get(definition).waitForCompletion(timeout);
	}

	/**
	 * Delete build and all associated resources from build namespace.
	 *
	 * @param definition build to be deleted
	 */
	public void deleteBuild(BuildDefinition definition) {
		builds.get(definition).deleteBuild();
	}

	/**
	 * @param definition	definition of build
	 * @return			status of build
	 */
	public BuildStatus getBuildStatus(BuildDefinition definition) {
		return builds.get(definition).getBuildStatus();
	}

	private static class BuildManagerHolder {
		static final BuildManagerV2 MANAGER = new BuildManagerV2();
	}
}
