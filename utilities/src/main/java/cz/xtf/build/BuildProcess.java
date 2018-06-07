package cz.xtf.build;

import cz.xtf.TestConfiguration;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.BuildConfigBuilder;
import cz.xtf.openshift.builder.ImageStreamBuilder;
import cz.xtf.openshift.builder.buildconfig.SourceBuildStrategy;
import cz.xtf.wait.WaitUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.Build;

/**
 * Class representing build process of shared build.
 */
@Slf4j
public abstract class BuildProcess {
	protected final OpenshiftUtil openshift;
	protected final BuildDefinition definition;
	protected final String buildNamespace;

	@Getter
	protected final String buildName;

	/**
	 * Takes instance of BuildDefinition and initializes instance of BuildProcess specific for this one build.
	 * 
	 * @param definition instance of BuildDefinition
	 */
	public BuildProcess(BuildDefinition definition) {
		openshift = OpenshiftUtil.getInstance();
		buildNamespace = TestConfiguration.buildNamespace();

		this.definition = definition;
		this.buildName = definition.getName();
	}
	
	protected BuildConfig getBuildConfig() {
		return openshift.getBuildConfig(buildName, buildNamespace);
	}

	/**
	 * Takes necessary steps to deploy all resources to Openshift and start build.
	 */
	public abstract void deployBuild();	

	protected void deployBuildFromGit(String gitRepo) {
		deployBuildFromGit(gitRepo, null, null);
	}

	protected void deployBuildFromGit(String gitRepo, String branch, String contextDir) {
		BuildConfigBuilder bcb = getPreconfiguredBuildConfig();
		BuildConfig bc = bcb.gitSource(gitRepo).gitRef(branch).gitContextDir(contextDir).build();

		deployResources(bc);
		startBuild(bc);
	}

	protected void deployResources(BuildConfig bc) {
		ImageStream is = new ImageStreamBuilder(buildName).build();

		openshift.createImageStream(is, buildNamespace);
		openshift.createBuildConfig(bc, buildNamespace);
	}

	protected void startBuild(BuildConfig bc) {
		openshift.startBuild(bc, buildNamespace);
	}

	/**
	 * @Return preconfigured buildConfigBuilder with preconfigured:</br>
	 * Output to image stream with buildName</br>
	 * Sti source strategy</br>
	 * From docker image</br>
	 * Force pull</br>
	 * Env properties</br>
	 */
	protected BuildConfigBuilder getPreconfiguredBuildConfig() {
		BuildConfigBuilder bcb = new BuildConfigBuilder(buildName);
		SourceBuildStrategy sbs = bcb.setOutput(buildName).sti().fromDockerImage(definition.getBuilderImage()).forcePull(definition.isForcePull());

		if (definition.getEnvProperties() != null) {
			definition.getEnvProperties().forEach((k, v) -> sbs.addEnvVariable(k, v));
		}

		if (TestConfiguration.mavenProxyEnabled()) {
			sbs.addEnvVariable("MAVEN_MIRROR_URL", TestConfiguration.mavenProxyURL());
		}

		return bcb;
	}

	/**
	 * Deletes all resources associated with buildProcess.
	 */
	public abstract void deleteBuild();
	
	/**
	 * Deletes buildconfig, imagestream and builds from Openshift associated with this build process.
	 */
	protected void deleteOpenshiftResources() {
		BuildConfig bc = openshift.getBuildConfig(buildName, buildNamespace);
		ImageStream is = openshift.getImageStream(buildName, buildNamespace);
		List<Build> builds = openshift
				.getBuilds(buildNamespace)
				.stream()
				.filter(b -> b.getMetadata().getLabels()
						.containsValue(buildName)).collect(Collectors.toList());

		if (bc != null) {
			log.debug("Deleting bc {}", bc.getMetadata().getName());
			openshift.deleteBuildConfig(bc, buildNamespace);
		}
		if (is != null) {
			log.debug("Deleting is {}", is.getMetadata().getName());
			openshift.deleteImageStream(is, buildNamespace);
		}
		if (builds != null) {
			builds.forEach(b -> log.debug("Deleting build {}", b.getMetadata().getName()));
			builds.forEach(b -> openshift.deleteBuild(b, buildNamespace));
		}

		if (bc != null || is != null || builds != null) {
			try {
				// give OpenShift a few seconds to delete resources before continuing
				Thread.sleep(2_000L);
			} catch (InterruptedException ex) {
				// ok
			}
		}
	}

	public abstract void updateBuild();
	
	/**
	 * Will wait for completion of build in shared namespace for 10 minutes.
	 * 
	 * @throws IllegalStateException in case of build timeout or build fail
	 */
	public void waitForCompletion() {
		waitForCompletion(20);
	}

	/**
	 * Will wait for completion of build in shared namespace.
	 * 
	 * @param timeout timeout for build in minutes
	 * 
	 * @throws IllegalStateException in case of build timeout or build fail
	 */
	public void waitForCompletion(long timeout) {
		boolean success = false;
		try {
			success = WaitUtil.waitFor(() -> getBuildStatus(), x -> x == BuildStatus.READY, x -> x == BuildStatus.FAILED, 5_000, TimeUnit.MINUTES.toMillis(timeout));
		} catch (InterruptedException | TimeoutException e) {
			throw new IllegalStateException("Build " + buildName + " run out of time");
		}
		if (!success) {
			throw new IllegalStateException("Build " + buildName + " failed");
		}
	}

	/**
	 * @return status of last build in openshift with extended statuses catching old images and outdated source code
	 */
	public abstract BuildStatus getBuildStatus();
	
	/**
	 * @param bc
	 * @return build status of Openshift build or OLD_IMAGE
	 */
	protected BuildStatus getCommonStatus(BuildConfig bc) {
		if (!isBuildConfigPresent()) {
			return BuildStatus.NOT_DEPLOYED;
		}
		if (!isImageUpToDate(bc)) {
			return BuildStatus.OLD_IMAGE;
		}
		return getLastBuildOpenshiftStatus(bc);
	}

	/**
	 * @param bc
	 * @return true if bc with expected name is found
	 */
	protected boolean isBuildConfigPresent() {
		return getBuildConfig() != null;
	}
	
	protected boolean isImageUpToDate(BuildConfig bc) {
		return bc.getSpec().getStrategy().getSourceStrategy().getFrom().getName().equals(definition.getBuilderImage());
	}

	protected BuildStatus getLastBuildOpenshiftStatus(BuildConfig bc) {
		long version = bc.getStatus().getLastVersion();
		Build build = openshift.getBuild(buildName + "-" + version, buildNamespace);

		if (build != null) {
			String phase = build.getStatus().getPhase();
			if (phase != null) {
				switch (build.getStatus().getPhase()) {
					case "Failed":
						return BuildStatus.FAILED;
					case "Complete":
						return BuildStatus.READY;
					case "Running":
						return BuildStatus.RUNNING;
					case "Pending":
						return BuildStatus.PENDING;
					case "New":
						return BuildStatus.NEW;
					default:
						return BuildStatus.DEPLOYED;
				}
			}
		}

		return BuildStatus.NOT_DEPLOYED;
	}

	/**
	 * Enum states symbolizing status of build process.
	 * <p>
	 * NOT_DEPLOYED - no such build is present in shared namespace</br>
	 * NEW - build is in new state - waits for pod creation (eg. if limit on pods quota is reached)</br>
	 * RUNNING - build is in running state</br>
	 * PENDING - build is in pending state</br>
	 * DEPLOYED - build is deployed in actual run, but still not ready</br>
	 * READY - build from actual jvm run is in ready state</br>
	 * FAILED - build from any run is in failed state even after rebuild</br>
	 * <p>
	 * OLD_IMAGE - build present on openshift is from non-actual image</br>
	 * SOURCE_CHANGE - build present on openshift has different source code</br>
	 * GIT_REPO_GONE - repository referenced by buildconfig no longer exist</br>
	 * ERROR - Error arrisen during source code comparision
	 */
	public static enum BuildStatus {
		NOT_DEPLOYED, NEW, RUNNING, PENDING, DEPLOYED, READY, FAILED, OLD_IMAGE, SOURCE_CHANGE, GIT_REPO_GONE, ERROR;
	}
}
