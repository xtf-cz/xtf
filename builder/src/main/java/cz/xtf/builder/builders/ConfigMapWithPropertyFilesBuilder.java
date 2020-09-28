package cz.xtf.builder.builders;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;

public class ConfigMapWithPropertyFilesBuilder extends AbstractBuilder<ConfigMap, ConfigMapWithPropertyFilesBuilder>
        implements EnvironmentConfiguration {
    private final Map<String, Map<String, String>> fileMap = new HashMap<>();
    private Map<String, String> config = new HashMap<>();

    public ConfigMapWithPropertyFilesBuilder(final String name) {
        super(null, name);
    }

    @Override
    public ConfigMap build() {
        Map<String, String> map = new HashMap<>();
        map.putAll(config);
        map.putAll(fileMap.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                x -> x.getValue().entrySet().stream().map(y -> y.getKey() + "=" + y.getValue() + "\n")
                        .collect(Collectors.joining()))));
        return new io.fabric8.kubernetes.api.model.ConfigMapBuilder()
                .withMetadata(metadataBuilder().build())
                .withData(map)
                .build();
    }

    @Override
    protected ConfigMapWithPropertyFilesBuilder getThis() {
        return this;
    }

    @Override
    public ConfigMapWithPropertyFilesBuilder configEntry(final String key, final String value) {
        config.put(key, value);
        return this;
    }

    @Override
    public Map<String, String> getConfigEntries() {
        return (Map<String, String>) config;
    }

    public ConfigMapWithPropertyFilesBuilder setFilename(final String filename) {
        fileMap.put(filename, config);
        return this;
    }
}
