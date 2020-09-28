package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class EmptyDirVolume extends Volume {

    public EmptyDirVolume(String name) {
        super(name);
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        builder.withNewEmptyDir().endEmptyDir();
    }
}
