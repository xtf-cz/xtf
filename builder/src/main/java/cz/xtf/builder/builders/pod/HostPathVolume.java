package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class HostPathVolume extends Volume {
    private final String sourceHostDirPath;

    public HostPathVolume(String name, String sourceHostDirPath) {
        super(name);
        this.sourceHostDirPath = sourceHostDirPath;
    }

    public String getSourceHostDirPath() {
        return sourceHostDirPath;
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        builder.withNewHostPath()
                .withPath(getSourceHostDirPath())
                .endHostPath();
    }
}
