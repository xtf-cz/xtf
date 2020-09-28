package cz.xtf.junit5.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.api.model.NamedTagEventList;
import io.fabric8.openshift.api.model.TagEventCondition;

/**
 * Represents Docker image metadata by exposing convenience methods to access
 * them and provides a cache to optimize retrievals.
 */
public class DockerImageMetadata {
    private static final String METADATA_CONFIG = "Config";
    private static final String METADATA_CONFIG_LABELS = "Labels";
    private static final String METADATA_CONFIG_ENV = "Env";
    private static final String METADATA_CONFIG_CMD = "Cmd";
    private static final String METADATA_CONFIG_EXPOSED_PORTS = "ExposedPorts";

    private static final ConcurrentHashMap<String, DockerImageMetadata> CACHED_METADATA = new ConcurrentHashMap<>();

    private final Map<String, Object> metadata;

    private DockerImageMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    private static String forgeMetadataKey(OpenShift openshift, Image image) {
        return String.format("%s;%s", openshift.getNamespace(), image.getUrl());
    }

    /**
     * Get docker image metadata for image in openshift namespace. Metadata are cached.
     */
    public static DockerImageMetadata get(OpenShift openShift, String imageUrl) {
        return get(openShift, Image.from(imageUrl));
    }

    /**
     * Get docker image metadata for image in openshift namespace. Metadata are cached.
     */
    public static DockerImageMetadata get(OpenShift openShift, Image image) {
        return CACHED_METADATA.computeIfAbsent(forgeMetadataKey(openShift, image), s -> getMetadata(openShift, image));
    }

    /**
     * <ul>
     * <ol>
     * try to find existing image in tag list and return docker image metadata from it
     * </ol>
     * <ol>
     * create new image stream of unique name, gather metadata and delete the stream
     * </ol>
     * </ul>
     */
    private static DockerImageMetadata getMetadata(OpenShift openShift, Image image) {
        // try to find existing one
        ImageStreamTag imageStreamTag = openShift.getImageStreamTag(image.getRepo(), image.getMajorTag());
        DockerImageMetadata metadataFromTag = getMetadataFromTag(imageStreamTag);
        if (metadataFromTag != null) {
            return metadataFromTag;
        }

        // create new one of unique name
        String tempName = image.getRepo() + "-" + randomString();
        ImageStream imageStream = image.getImageStream(tempName);
        imageStream.getMetadata().setName(tempName);
        openShift.imageStreams().createOrReplace(imageStream);

        // wait till metadata are available
        Waiter metadataWaiter = new SimpleWaiter(
                () -> DockerImageMetadata.areMetadataForImageReady(openShift.getImageStreamTag(tempName, image.getMajorTag())),
                "Giving OpenShift instance time to download image metadata.")
                        .failFast(new ImageStreamFailFastCheck(openShift, tempName, image));
        boolean metadataOK = metadataWaiter.waitFor();

        // delete unique image stream and return metadata
        DockerImageMetadata metadata = metadataOK
                ? getMetadataFromTag(openShift.getImageStreamTag(tempName, image.getMajorTag()))
                : null;
        openShift.deleteImageStream(imageStream);
        return metadata;
    }

    private static DockerImageMetadata getMetadataFromTag(ImageStreamTag imageStreamTag) {
        return areMetadataForImageReady(imageStreamTag)
                ? new DockerImageMetadata(imageStreamTag.getImage().getDockerImageMetadata().getAdditionalProperties())
                : null;
    }

    private static boolean areMetadataForImageReady(ImageStreamTag tag) {
        return tag != null && tag.getImage() != null && tag.getImage().getDockerImageMetadata() != null
                && tag.getImage().getDockerImageMetadata().getAdditionalProperties() != null;
    }

    private static String randomString() {
        return new Random().ints('a', 'z' + 1)
                .limit(5)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private Map<String, Object> getConfig() {
        return (Map<String, Object>) metadata.get(METADATA_CONFIG);
    }

    /**
     * Returns image labels of Config/Labels path
     *
     * @return map of labels
     */
    public Map<String, String> labels() {
        return (Map<String, String>) getConfig().get(METADATA_CONFIG_LABELS);
    }

    /**
     * Returns image environments of Config/Env path
     *
     * @return map of environment variables
     */
    public Map<String, String> envs() {
        final Map<String, String> envMap = new HashMap<>();

        List<String> envList = (List<String>) getConfig().get(METADATA_CONFIG_ENV);
        envList.forEach(
                node -> {
                    String[] keyValue = node.split("=", 2);
                    envMap.put(keyValue[0], keyValue[1]);
                });
        return Collections.unmodifiableMap(envMap);
    }

    /**
     * Returns default container command on Config/Cmd path
     *
     * @return default command
     */
    public String command() {
        List<String> cmdList = (List<String>) getConfig().get(METADATA_CONFIG_CMD);
        return cmdList.get(0);
    }

    /**
     * Returns integer set of exposed ports by specified protocol (eg. tcp, udp) on Config/ExposedPorts path.
     *
     * @return port set
     */
    public Set<Integer> exposedPorts(String protocol) {
        final Set<Integer> result = new HashSet<>();
        final Map<String, Object> exposedPorts = (Map<String, Object>) getConfig().get(METADATA_CONFIG_EXPOSED_PORTS);
        exposedPorts.keySet().forEach(
                portDef -> {
                    final String[] split = portDef.split("/");
                    if (StringUtils.isBlank(protocol) || split[1].equalsIgnoreCase(protocol)) {
                        result.add(Integer.parseInt(split[0]));
                    }
                });

        return result;
    }

    private static final class ImageStreamFailFastCheck implements FailFastCheck {

        private final OpenShift openShift;
        private final String imageName;
        private final Image image;
        private String reason = "";

        ImageStreamFailFastCheck(OpenShift openShift, String imageName, Image image) {
            this.openShift = openShift;
            this.imageName = imageName;
            this.image = image;
        }

        @Override
        public boolean hasFailed() {
            ImageStream imageStream = openShift.getImageStream(imageName);
            if (imageStream == null) {
                return false;
            }
            for (NamedTagEventList tag : imageStream.getStatus().getTags()) {
                if (image.getTag().startsWith(tag.getTag())) {
                    for (TagEventCondition condition : tag.getConditions()) {
                        if (condition.getType().equals("ImportSuccess") && condition.getStatus().equals("False")) {
                            reason = condition.getMessage();
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public String reason() {
            return reason;
        }
    }
}
