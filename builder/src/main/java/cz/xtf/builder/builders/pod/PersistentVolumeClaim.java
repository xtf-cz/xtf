package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class PersistentVolumeClaim extends Volume {

    private String claimName;

    public PersistentVolumeClaim(String name, String pvcName) {
        super(name);
        this.claimName = pvcName;
    }

    public String getClaimName() {
        return claimName;
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        builder.withNewPersistentVolumeClaim()
                .withClaimName(getClaimName())
                .endPersistentVolumeClaim();
    }

}
