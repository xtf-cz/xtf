package cz.xtf.core.bm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;

public class BinaryBuildFromFileAsInputStream extends BinaryBuildFromFile {

    public BinaryBuildFromFileAsInputStream(String builderImage, Path path, Map<String, String> envProperties, String id) {
        super(builderImage, path, envProperties, id);
    }

    protected void configureBuildStrategy(BuildConfigSpecBuilder builder, String builderImage, List<EnvVar> env) {
        builder.withNewStrategy()
                .withType("Source")
                .withNewSourceStrategy()
                .withEnv(env)
                .withForcePull(true)
                .withNewFrom()
                .withKind("ImageStreamTag")
                .withName(builderImage)
                .endFrom()
                .endSourceStrategy()
                .endStrategy();
    }

}
