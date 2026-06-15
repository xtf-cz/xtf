package cz.xtf.builder.builders.pod;

import java.util.Map;

import io.fabric8.kubernetes.api.model.KeyToPathBuilder;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class SecretVolume extends Volume {
    private final String secretName;
    private final Map<String, String> items;

    public SecretVolume(String name, String secretName) {
        super(name);
        this.secretName = secretName;
        this.items = null;
    }

    public SecretVolume(String name, String secretName, Map<String, String> items) {
        super(name);
        this.secretName = secretName;
        this.items = items;
    }

    public String getSecretName() {
        return secretName;
    }

    @Override
    protected void addVolumeParameters(VolumeBuilder builder) {
        final SecretVolumeSourceBuilder svb = new SecretVolumeSourceBuilder()
                .withSecretName(getSecretName());
        if (items != null) {
            items.forEach((key, value) -> svb.addToItems(
                    new KeyToPathBuilder().withKey(key).withPath(value).build()));
        }
        builder.withSecret(svb.build());
    }
}
