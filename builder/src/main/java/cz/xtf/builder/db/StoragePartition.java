package cz.xtf.builder.db;

import cz.xtf.builder.builders.ApplicationBuilder;
import cz.xtf.builder.builders.DeploymentConfigBuilder;

public class StoragePartition {
    private final String mountPoint;
    private final int claimNumber;

    public StoragePartition(int claimNumber, String mountPoint) {
        this.mountPoint = mountPoint;
        this.claimNumber = claimNumber;
    }

    public void configureApplicationDeployment(ApplicationBuilder appBuilder, DeploymentConfigBuilder dcBuilder) {
        final String volumeName = dcBuilder.app().getName() + "-" + claimNumber;

        appBuilder.pvc(volumeName).accessRWX().storageSize("512m");
        dcBuilder.podTemplate().addPersistenVolumeClaim(volumeName, volumeName);
        dcBuilder.podTemplate().container().addVolumeMount(volumeName, mountPoint, false);
    }
}
