package cz.xtf.builder.builders.buildconfig;

import cz.xtf.builder.builders.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;
import io.fabric8.openshift.api.model.DockerBuildStrategyBuilder;

public class DockerBuildStrategy extends BuildStrategy {
    private boolean noCache = false;
    private Boolean forcePull = null;
    private String imageUrl;

    public DockerBuildStrategy(BuildConfigBuilder parent) {
        super(parent, "Docker");
    }

    public DockerBuildStrategy fromDockerImage(String imageUrl) {
        this.imageUrl = imageUrl;
        return this;
    }

    public DockerBuildStrategy setNoCache(boolean noCache) {
        this.noCache = noCache;
        return this;
    }

    public DockerBuildStrategy setForcePull(boolean forcePull) {
        this.forcePull = forcePull;
        return this;
    }

    @Override
    protected void buildStrategy(BuildStrategyBuilder builder) {
        DockerBuildStrategyBuilder strategyBuilder = new DockerBuildStrategyBuilder();
        strategyBuilder.withNewFrom()
                .withKind("DockerImage")
                .withName(imageUrl)
                .endFrom();
        if (noCache) {
            strategyBuilder.withNoCache(noCache);
        }
        if (forcePull != null) {
            strategyBuilder.withForcePull(forcePull);
        }

        builder.withDockerStrategy(strategyBuilder.build());
    }
}
