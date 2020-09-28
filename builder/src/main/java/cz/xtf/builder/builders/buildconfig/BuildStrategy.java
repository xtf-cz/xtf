package cz.xtf.builder.builders.buildconfig;

import cz.xtf.builder.builders.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;

public abstract class BuildStrategy {
    private final BuildConfigBuilder parent;

    private final BuildStrategyBuilder builder;

    protected BuildStrategy(BuildConfigBuilder parent, String type) {
        this.parent = parent;

        builder = new BuildStrategyBuilder();
        builder.withType(type);
    }

    public BuildConfigBuilder buildConfig() {
        return parent;
    }

    public final io.fabric8.openshift.api.model.BuildStrategy build() {
        buildStrategy(builder);
        return builder.build();
    }

    protected abstract void buildStrategy(BuildStrategyBuilder builder);
}
