package cz.xtf.builder.builders;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;

public abstract class AbstractBuilder<T, R extends AbstractBuilder> {
    private final String name;
    private final ApplicationBuilder applicationBuilder;
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> annotations = new HashMap<>();

    protected AbstractBuilder(ApplicationBuilder applicationBuilder, String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name can't be blank.");
        }
        this.applicationBuilder = applicationBuilder;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ApplicationBuilder app() {
        if (applicationBuilder == null) {
            throw new IllegalStateException("ApplicationBuilder was not set in constructor");
        }
        return applicationBuilder;
    }

    public R addLabel(String key, String value) {
        labels.put(key, value);
        return getThis();
    }

    public R addLabels(Map<String, String> labels) {
        labels.forEach((key, value) -> this.labels.put(key, value));
        return getThis();
    }

    public R addAnnotation(String key, String value) {
        annotations.put(key, value);
        return getThis();
    }

    protected ObjectMetaBuilder metadataBuilder() {
        return new ObjectMetaBuilder()
                .withName(name)
                .withAnnotations(annotations)
                .withLabels(labels);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o.getClass() == getClass()))
            return false;

        AbstractBuilder<?, ?> that = (AbstractBuilder<?, ?>) o;

        return name.equals(that.name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public abstract T build();

    protected abstract R getThis();
}
