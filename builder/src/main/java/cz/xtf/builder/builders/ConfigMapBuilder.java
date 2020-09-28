package cz.xtf.builder.builders;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;

public class ConfigMapBuilder extends AbstractBuilder<ConfigMap, ConfigMapBuilder> implements EnvironmentConfiguration {
    private final Map<String, String> config = new HashMap<>();
    private Function<String, String> nameTransformationFunction = Function.identity();

    public ConfigMapBuilder(String name) {
        super(null, name);
    }

    @Override
    public ConfigMap build() {
        return new io.fabric8.kubernetes.api.model.ConfigMapBuilder()
                .withMetadata(metadataBuilder().build())
                .withData(config.entrySet().stream()
                        .collect(Collectors.toMap(x -> nameTransformationFunction.apply(x.getKey()), Map.Entry::getValue)))
                .build();
    }

    @Override
    protected ConfigMapBuilder getThis() {
        return this;
    }

    @Override
    public ConfigMapBuilder configEntry(final String key, final String value) {
        config.put(nameTransformationFunction.apply(key), value);
        return this;
    }

    @Override
    public Map<String, String> getConfigEntries() {
        return config;
    }

    public ConfigMapBuilder transformNames(Function<String, String> nameTransformation) {
        nameTransformationFunction = nameTransformation;
        return this;
    }

    public static Function<String, String> toEnvVarFormat() {
        return x -> x.toUpperCase().replace('-', '_');
    }

    public static Function<String, String> fromEnvVarFormat() {
        return x -> x.toLowerCase().replace('_', '-');
    }
}
