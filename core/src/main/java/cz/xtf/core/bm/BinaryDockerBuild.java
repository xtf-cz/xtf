package cz.xtf.core.bm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BinaryDockerBuild extends BinaryBuildFromSources {
    public BinaryDockerBuild(String builderImage, Path path, Map<String, String> envProperties, String id) {
        super(builderImage, path, envProperties, id);
    }

    @Override
    protected void configureBuildStrategy(BuildConfigSpecBuilder builder, String builderImage, List<EnvVar> env) {
        builder.withNewStrategy().withType("Docker").withNewDockerStrategy().withEnv(env).withForcePull(true).withNewFrom()
                .withKind("DockerImage").withName(builderImage).endFrom().endDockerStrategy().endStrategy();
    }

    @Override
    protected String getImage(BuildConfig bc) {
        return bc.getSpec().getStrategy().getDockerStrategy().getFrom().getName();
    }

    @Override
    protected List<EnvVar> getEnv(BuildConfig buildConfig) {
        return buildConfig.getSpec().getStrategy().getDockerStrategy().getEnv();
    }
}
