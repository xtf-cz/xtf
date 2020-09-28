package cz.xtf.builder.builders.buildconfig;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.builder.builders.BuildConfigBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.openshift.api.model.BuildStrategyBuilder;
import io.fabric8.openshift.api.model.SourceBuildStrategyBuilder;

public class SourceBuildStrategy extends BuildStrategy {
    private final Map<String, String> env = new HashMap<>();
    private String imageStreamNamespace;
    private String imageStreamName;
    private String imageStreamTag;
    private String dockerImageUrl;
    private String scriptsLocation;
    private boolean incremental = false;
    private boolean forcePull = false;

    public SourceBuildStrategy(BuildConfigBuilder parent) {
        super(parent, "Source");
    }

    public SourceBuildStrategy fromImageStream(String namespace, String imageRepoName, String tag) {
        this.imageStreamNamespace = namespace;
        this.imageStreamName = imageRepoName;
        this.imageStreamTag = tag;
        return this;
    }

    public SourceBuildStrategy fromDockerImage(String imageUrl) {
        this.dockerImageUrl = imageUrl;
        return this;
    }

    public SourceBuildStrategy addEnvVariable(String name, String value) {
        env.put(name, value);
        return this;
    }

    public SourceBuildStrategy scriptsLocation(String scriptsLocation) {
        this.scriptsLocation = scriptsLocation;
        return this;
    }

    public SourceBuildStrategy incremental() {
        incremental = true;
        return this;
    }

    public SourceBuildStrategy forcePull() {
        this.forcePull = true;
        return this;
    }

    public SourceBuildStrategy forcePull(boolean forcePull) {
        this.forcePull = forcePull;
        return this;
    }

    public SourceBuildStrategy clean() {
        incremental = false;
        return this;
    }

    @Override
    protected void buildStrategy(BuildStrategyBuilder builder) {
        SourceBuildStrategyBuilder strategyBuilder = new SourceBuildStrategyBuilder();

        // source image
        if (StringUtils.isNotBlank(imageStreamName)) {
            if (StringUtils.isNotBlank(imageStreamTag)) {
                strategyBuilder.withNewFrom()
                        .withKind("ImageStreamTag")
                        .withName(imageStreamName + ":" + imageStreamTag)
                        .endFrom();
            } else {
                strategyBuilder.withNewFrom()
                        .withKind("ImageStream")
                        .withName(imageStreamName)
                        .endFrom();
            }
            if (StringUtils.isNotBlank(imageStreamNamespace)) {
                strategyBuilder.getFrom().setNamespace(imageStreamNamespace);
            }
        } else if (StringUtils.isNotBlank(dockerImageUrl)) {
            strategyBuilder.withNewFrom()
                    .withKind("DockerImage")
                    .withName(dockerImageUrl)
                    .endFrom();
        }

        // forcePull STI image, OSE default is false
        strategyBuilder.withForcePull(forcePull);

        // scripts location
        if (scriptsLocation != null) {
            strategyBuilder.withScripts(scriptsLocation);
        }

        // incremental build
        strategyBuilder.withIncremental(incremental);

        // build environment
        strategyBuilder.withEnv(
                env.entrySet().stream().map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null))
                        .collect(Collectors.toList()));

        builder.withSourceStrategy(strategyBuilder.build());
    }
}
