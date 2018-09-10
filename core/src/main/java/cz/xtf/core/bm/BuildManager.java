package cz.xtf.core.bm;

import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.Waiter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuildManager {
	private final OpenShift openShift;

	public BuildManager(OpenShift openShift) {
		this.openShift = openShift;

		if (openShift.getProject(openShift.getNamespace()) == null) {
			openShift.createProjectRequest();
		}

		openShift.addRoleToGroup("system:image-puller", "system:authenticated");
	}

	public ManagedBuildReference deploy(ManagedBuild managedBuild) {
		if (BuildManagerConfig.forceRebuild()) {
			log.info("Force rebuilding is enabled... Building '{}' ...", managedBuild.getId());
			managedBuild.delete(openShift);
			managedBuild.build(openShift);
		} else if (!managedBuild.isPresent(openShift)) {
			log.info("Managed build '{}' is not present... Building...", managedBuild.getId());
			managedBuild.build(openShift);
		} else if (managedBuild.needsUpdate(openShift)) {
			log.info("Managed build '{}' is not up to date... Building...", managedBuild.getId());
			managedBuild.update(openShift);
		}

		return getBuildReference(managedBuild);
	}

	public Waiter hasBuildCompleted(ManagedBuild managedBuild) {
		return managedBuild.hasCompleted(openShift);
	}

	public ManagedBuildReference getBuildReference(ManagedBuild managedBuild) {
		return new ManagedBuildReference(managedBuild.getId(), "latest", openShift.getNamespace());
	}
}
