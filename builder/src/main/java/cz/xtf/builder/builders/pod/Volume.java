package cz.xtf.builder.builders.pod;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.kubernetes.api.model.VolumeBuilder;

public abstract class Volume {
    private final String name;

    protected Volume(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name mus not be null nor empty");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public final io.fabric8.kubernetes.api.model.Volume build() {
        VolumeBuilder builder = new VolumeBuilder()
                .withName(name);

        addVolumeParameters(builder);

        return builder.build();
    }

    protected abstract void addVolumeParameters(VolumeBuilder builder);

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Volume))
            return false;

        Volume volume = (Volume) o;

        return name.equals(volume.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
