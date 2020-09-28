package cz.xtf.core.bm;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cz.xtf.core.config.WaitingConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BinaryBuild implements ManagedBuild {
    static private final String CONTENT_HASH_LABEL_KEY = "xtf.bm/content-hash";

    @Getter
    private final String id;
    @Getter
    private final Path path;

    private final String builderImage;
    private final Map<String, String> envProperties;

    protected final ImageStream is;
    protected final BuildConfig bc;

    protected String contentHash = null;

    public BinaryBuild(String builderImage, Path path, Map<String, String> envProperties, String id) {
        this.builderImage = builderImage;
        this.path = path;
        this.envProperties = envProperties;
        this.id = id;

        this.is = this.createIsDefinition();
        this.bc = this.createBcDefinition();
    }

    @Override
    public void update(OpenShift openShift) {
        this.delete(openShift);
        this.build(openShift);
    }

    @Override
    public void delete(OpenShift openShift) {
        openShift.imageStreams().withName(is.getMetadata().getName()).withPropagationPolicy(DeletionPropagation.BACKGROUND)
                .delete();
        openShift.buildConfigs().withName(bc.getMetadata().getName()).withPropagationPolicy(DeletionPropagation.BACKGROUND)
                .delete();
        final String podName = bc.getMetadata().getName() + "-1-build";
        openShift.pods().withName(podName).withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();

        new SimpleWaiter(() -> openShift.getImageStream(is.getMetadata().getName()) == null, TimeUnit.MILLISECONDS,
                WaitingConfig.timeout(), "Waiting for old imageStreams deletion").waitFor();
        new SimpleWaiter(() -> openShift.getBuildConfig(bc.getMetadata().getName()) == null, TimeUnit.MILLISECONDS,
                WaitingConfig.timeout(), "Waiting for old buildConfigs deletion").waitFor();
        new SimpleWaiter(() -> openShift.getPods().stream().noneMatch(p -> podName.equals(p.getMetadata().getName())),
                TimeUnit.MILLISECONDS, WaitingConfig.timeout(), "Waiting for old pods deletion").waitFor();
    }

    @Override
    public boolean isPresent(OpenShift openShift) {
        boolean isPresence = openShift.imageStreams().withName(id).get() != null;
        boolean bcPresence = openShift.buildConfigs().withName(id).get() != null;

        return isPresence || bcPresence;
    }

    protected abstract List<EnvVar> getEnv(BuildConfig bc);

    protected abstract void configureBuildStrategy(BuildConfigSpecBuilder builder, String builderImage, List<EnvVar> envs);

    protected abstract String getImage(BuildConfig bc);

    protected abstract String getContentHash();

    protected boolean isCached() {
        return true;
    }

    @Override
    public boolean needsUpdate(OpenShift openShift) {
        BuildConfig activeBc = openShift.buildConfigs().withName(id).get();
        ImageStream activeIs = openShift.imageStreams().withName(id).get();

        // Check resources presence
        boolean needsUpdate = activeBc == null | activeIs == null;

        // Check image match
        if (!needsUpdate) {
            String activeBuilderImage = getImage(activeBc);
            needsUpdate = !builderImage.equals(activeBuilderImage);

            log.debug("Builder image differs? {} != {} ? {} ", builderImage, activeBuilderImage, needsUpdate);
        }

        // Check source match
        if (!needsUpdate) {
            String activeContentHash = activeBc.getMetadata().getLabels().get(CONTENT_HASH_LABEL_KEY);
            needsUpdate = !getContentHash().equals(activeContentHash);

            log.debug("Content hash differs? {}", needsUpdate);
        }

        // Check build strategy match
        if (!needsUpdate) {
            int thisCount = envProperties != null ? envProperties.size() : 0;
            List<EnvVar> activeBcEnv = getEnv(activeBc);
            int themCount = activeBcEnv != null ? activeBcEnv.size() : 0;
            needsUpdate = thisCount != themCount;

            log.debug("env count differs? {} != {} ? {}", thisCount, themCount, needsUpdate);

            if (thisCount == themCount && thisCount > 0) {
                for (EnvVar envVar : activeBcEnv) {
                    if (envVar.getValue() == null) {
                        if (envProperties.get(envVar.getName()) != null) {
                            needsUpdate = true;

                            log.debug("env {} null in BC, but not in envProperties", envVar.getValue());
                            break;
                        }
                    } else if (!envVar.getValue().equals(envProperties.get(envVar.getName()))) {
                        needsUpdate = true;

                        log.debug("env {}={} in BC, but {} in envProperties", envVar.getName(), envVar.getValue(),
                                envProperties.get(envVar.getName()));
                        break;
                    }
                }
            }

            log.debug("Build strategy differs? {}", needsUpdate);
        }

        // Check build status, update if failed
        if (!needsUpdate) {
            if (activeBc.getStatus() == null || activeBc.getStatus().getLastVersion() == null) {
                log.debug("No build last version");
                needsUpdate = true;
            } else {
                Build activeBuild = openShift.getBuild(id + "-" + activeBc.getStatus().getLastVersion());
                if (activeBuild == null || activeBuild.getStatus() == null
                        || "Failed".equals(activeBuild.getStatus().getPhase())) {
                    log.debug("Build failed");
                    needsUpdate = true;
                }
            }
        }

        return needsUpdate;
    }

    @Override
    public Waiter hasCompleted(OpenShift openShift) {
        return openShift.waiters().hasBuildCompleted(id);
    }

    private ImageStream createIsDefinition() {
        ObjectMeta metadata = new ObjectMetaBuilder().withName(id).build();
        return new ImageStreamBuilder().withMetadata(metadata).build();
    }

    private BuildConfig createBcDefinition() {
        List<EnvVar> envVarList = new LinkedList<>();

        if (envProperties != null) {
            for (Map.Entry<String, String> env : envProperties.entrySet()) {
                envVarList.add(new EnvVarBuilder().withName(env.getKey()).withValue(env.getValue()).build());
            }
        }

        ObjectMeta metadata = new ObjectMetaBuilder().withName(id)
                .withLabels(Collections.singletonMap(CONTENT_HASH_LABEL_KEY, getContentHash())).build();
        BuildConfigSpecBuilder bcBuilder = new BuildConfigSpecBuilder();
        bcBuilder
                .withNewOutput().withNewTo().withKind("ImageStreamTag").withName(id + ":latest").endTo().endOutput()
                .withNewSource().withType("Binary").endSource();

        configureBuildStrategy(bcBuilder, builderImage, envVarList);

        return new BuildConfigBuilder().withMetadata(metadata).withSpec(bcBuilder.build()).build();
    }
}
