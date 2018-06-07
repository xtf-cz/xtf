package cz.xtf.openshift.storage;

import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.DeploymentConfigBuilder;
import cz.xtf.openshift.builder.PVCBuilder;

public class StoragePartition {
	private final String mountPoint;
	private final int claimNumber;

	public StoragePartition(final int claimNumber, final String mountPoint) {
		this.mountPoint = mountPoint;
		this.claimNumber = claimNumber;
	}

	public void configureApplicationDeployment(final DeploymentConfigBuilder builder) {
		final String volumeName = builder.app().getName() + "-" + claimNumber;
		OpenshiftUtil openshift = OpenshiftUtil.getInstance();
		final PVCBuilder pvc = new PVCBuilder(volumeName).accessRWX().storageSize("512m");
		openshift.createPersistentVolumeClaim(pvc.build());
		builder.podTemplate().addPersistenVolumeClaim(volumeName, volumeName);
		builder.podTemplate().container().addVolumeMount(volumeName, mountPoint, false);
	}
}
