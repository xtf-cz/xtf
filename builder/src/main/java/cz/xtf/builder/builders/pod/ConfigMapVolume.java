package cz.xtf.builder.builders.pod;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class ConfigMapVolume extends Volume {
    private final String configMapName;
    private final String defaultMode;

    public ConfigMapVolume(String name, String configMapName) {
        super(name);
        this.configMapName = configMapName;
        this.defaultMode = null;
    }

    /**
     * @param defaultMode - permissions - something like '0755', must be between 0000 and 0777 (required by oc)
     */
    public ConfigMapVolume(String name, String configMapName, String defaultMode) {
        super(name);
        this.configMapName = configMapName;
        this.defaultMode = defaultMode;
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        final ConfigMapVolumeSourceBuilder cmb = new ConfigMapVolumeSourceBuilder()
                .withName(configMapName);
        if (defaultMode != null) {
            int defaultModeIntVal = 0;
            for (byte b : defaultMode.getBytes()) {
                int num = Character.getNumericValue(b);
                defaultModeIntVal = num | defaultModeIntVal << 3;
            }
            cmb.withDefaultMode(defaultModeIntVal);
        }
        builder.withConfigMap(cmb.build());
    }
}
