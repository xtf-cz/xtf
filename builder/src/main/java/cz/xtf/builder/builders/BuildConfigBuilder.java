package cz.xtf.builder.builders;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.builder.builders.buildconfig.BuildStrategy;
import cz.xtf.builder.builders.buildconfig.DockerBuildStrategy;
import cz.xtf.builder.builders.buildconfig.ImageSource;
import cz.xtf.builder.builders.buildconfig.SourceBuildStrategy;
import cz.xtf.builder.builders.limits.CPUResource;
import cz.xtf.builder.builders.limits.ComputingResource;
import cz.xtf.builder.builders.limits.MemoryResource;
import cz.xtf.builder.builders.limits.ResourceLimitBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.openshift.api.model.BinaryBuildSource;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigFluent.SpecNested;
import io.fabric8.openshift.api.model.BuildSourceBuilder;
import io.fabric8.openshift.api.model.BuildTriggerPolicy;
import io.fabric8.openshift.api.model.BuildTriggerPolicyBuilder;
import io.fabric8.openshift.api.model.GitBuildSourceBuilder;
import io.fabric8.openshift.api.model.ImageSourceBuilder;
import io.fabric8.openshift.api.model.ImageSourceFluent.FromNested;
import io.fabric8.openshift.api.model.SecretBuildSourceBuilder;

public class BuildConfigBuilder extends AbstractBuilder<BuildConfig, BuildConfigBuilder> implements ResourceLimitBuilder {
    public static final String DEFAULT_SECRET = "secret101";

    private String gitUrl;
    private String gitRef;
    private String gitContextDir;
    private String githubSecret = null;
    private String genericSecret = DEFAULT_SECRET;
    private String output;
    private String secret;
    private String secretDestinationDir;
    private boolean binaryBuild;
    private boolean configChangeTrigger;

    private BuildStrategy strategy;
    private ImageSource imageSource;

    private Map<String, ComputingResource> computingResources = new HashMap<>();

    public BuildConfigBuilder(String name) {
        this(null, name);
    }

    BuildConfigBuilder(ApplicationBuilder applicationBuilder, String name) {
        super(applicationBuilder, name);
    }

    public BuildConfigBuilder gitSource(String gitUrl) {
        this.gitUrl = gitUrl;
        return this;
    }

    public BuildConfigBuilder gitRef(String gitRef) {
        this.gitRef = gitRef;
        return this;
    }

    public BuildConfigBuilder gitContextDir(String gitContextDir) {
        this.gitContextDir = gitContextDir;
        return this;
    }

    public SourceBuildStrategy sti() {
        if (strategy == null) {
            strategy = new SourceBuildStrategy(this);
        }
        return (SourceBuildStrategy) strategy;
    }

    public DockerBuildStrategy docker() {
        if (strategy == null) {
            strategy = new DockerBuildStrategy(this);
        }
        return (DockerBuildStrategy) strategy;
    }

    public BuildConfigBuilder setOutput(String output) {
        this.output = output;

        // create image stream
        try {
            app().imageStream(output);
        } catch (IllegalStateException ex) {
            // builders was not set, never mind
        }

        return this;
    }

    public BuildConfigBuilder genericWebhook(String secret) {
        this.genericSecret = secret;
        return this;
    }

    public BuildConfigBuilder githubWebhook(String secret) {
        this.githubSecret = secret;
        return this;
    }

    public BuildConfigBuilder onConfigurationChange() {
        this.configChangeTrigger = true;
        return this;
    }

    public BuildConfigBuilder withBinaryBuild() {
        this.binaryBuild = true;
        return this;
    }

    public BuildConfigBuilder withSecret(String secretName) {
        this.secret = secretName;
        return this;
    }

    public BuildConfigBuilder withSecret(String secretName, String destinationDir) {
        this.secret = secretName;
        this.secretDestinationDir = destinationDir;

        return this;
    }

    public BuildConfig build() {
        // triggers
        List<BuildTriggerPolicy> triggers = new LinkedList<>();

        if (StringUtils.isNotBlank(genericSecret)) {
            triggers.add(new BuildTriggerPolicyBuilder()
                    .withType("Generic").withNewGeneric().withAllowEnv(true).withSecret(genericSecret).endGeneric().build());
        }

        if (StringUtils.isNotBlank(githubSecret)) {
            triggers.add(new BuildTriggerPolicyBuilder()
                    .withType("GitHub").withNewGithub().withAllowEnv(true).withSecret(githubSecret).endGithub().build());
        }

        if (configChangeTrigger) {
            triggers.add(new BuildTriggerPolicyBuilder()
                    .withType("ConfigChange").build());
        }

        BuildSourceBuilder sourceBuilder = new BuildSourceBuilder();
        if (binaryBuild) {
            sourceBuilder
                    .withType("Binary").withBinary(new BinaryBuildSource());
        } else {
            // source
            GitBuildSourceBuilder gitSourceBuilder = new GitBuildSourceBuilder()
                    .withUri(gitUrl);
            if (StringUtils.isNotBlank(gitRef)) {
                gitSourceBuilder.withRef(gitRef);
            }
            sourceBuilder.withType("Git")
                    .withGit(gitSourceBuilder.build());
        }
        if (StringUtils.isNotBlank(gitContextDir)) {
            sourceBuilder.withContextDir(gitContextDir);
        }

        if (StringUtils.isNoneBlank(secret)) {
            SecretBuildSourceBuilder sbsb = new SecretBuildSourceBuilder();
            sbsb.withNewSecret(secret);
            if (StringUtils.isNoneBlank(secretDestinationDir)) {
                sbsb.withDestinationDir(secretDestinationDir);
            }
            sourceBuilder.withSecrets(sbsb.build());
        }

        if (imageSource != null) {
            final ImageSourceBuilder isb = new ImageSourceBuilder();
            FromNested<ImageSourceBuilder> from = isb.withNewFrom()
                    .withName(imageSource.getName())
                    .withKind(imageSource.getKind());
            if (imageSource.getNamespace() != null) {
                from.withNamespace(imageSource.getNamespace());
            }
            from.endFrom();
            imageSource.getPaths().forEach(
                    x -> isb.addNewPath().withDestinationDir(x.getDestinationPath())
                            .withSourcePath(x.getSourcePath()).endPath());
            sourceBuilder.withImages(isb.build());
        }

        final io.fabric8.openshift.api.model.BuildConfigBuilder builder = new io.fabric8.openshift.api.model.BuildConfigBuilder()
                .withMetadata(metadataBuilder().build());

        // spec
        final SpecNested<io.fabric8.openshift.api.model.BuildConfigBuilder> spec = builder.withNewSpec();

        // limits
        final List<ComputingResource> requests = computingResources.values().stream().filter(x -> x.getRequests() != null)
                .collect(Collectors.toList());
        final List<ComputingResource> limits = computingResources.values().stream().filter(x -> x.getLimits() != null)
                .collect(Collectors.toList());
        if (!requests.isEmpty() || !limits.isEmpty()) {
            io.fabric8.openshift.api.model.BuildConfigSpecFluent.ResourcesNested<SpecNested<io.fabric8.openshift.api.model.BuildConfigBuilder>> resources = spec
                    .withNewResources();
            if (!requests.isEmpty()) {
                resources.withRequests(
                        requests.stream().collect(Collectors.toMap(
                                ComputingResource::resourceIdentifier, x -> new Quantity(x.getRequests()))));
            }
            if (!limits.isEmpty()) {
                resources.withLimits(
                        limits.stream().collect(Collectors.toMap(
                                ComputingResource::resourceIdentifier, x -> new Quantity(x.getLimits()))));
            }
            resources.endResources();
        }

        spec
                // triggers
                .withTriggers(triggers)
                // source
                .withSource(sourceBuilder.build())
                // strategy
                .withStrategy(strategy.build())
                // to
                .withNewOutput()
                .withNewTo()
                .withKind("ImageStreamTag").withName(output + ":latest")
                .endTo()
                .endOutput()

                .endSpec();
        return builder.build();
    }

    @Override
    protected BuildConfigBuilder getThis() {
        return this;
    }

    public BuildConfigBuilder imageSource(final ImageSource imageSource) {
        this.imageSource = imageSource;
        return this;
    }

    @Override
    public ComputingResource addCPUResource() {
        final ComputingResource r = new CPUResource();
        computingResources.put(r.resourceIdentifier(), r);
        return r;
    }

    @Override
    public ComputingResource addMemoryResource() {
        final ComputingResource r = new MemoryResource();
        computingResources.put(r.resourceIdentifier(), r);
        return r;
    }
}
