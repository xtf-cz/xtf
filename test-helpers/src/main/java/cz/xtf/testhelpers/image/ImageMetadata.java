package cz.xtf.testhelpers.image;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

import com.google.gson.Gson;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import cz.xtf.core.waiting.failfast.FailFastCheck;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.api.model.NamedTagEventList;
import io.fabric8.openshift.api.model.TagEventCondition;
import lombok.extern.slf4j.Slf4j;

/**
 * Use {@code DockerImageMetadata}
 */
@Slf4j
@Deprecated
public class ImageMetadata {

    /**
     * Creates ImageStream in provided OpenShift context and pulls back ImageStreamTag with imageUrl metadata.
     *
     * @param imageUrl image url to initialize new instance of this object
     * @param openShift context for creating ImageStream and retrieving image metadata
     * @return new instance
     */
    public static ImageMetadata prepare(OpenShift openShift, String imageUrl) {
        return ImageMetadata.prepare(openShift, Image.from(imageUrl));
    }

    public static ImageMetadata prepare(OpenShift openShift, Image image) {
        openShift.createImageStream(image.getImageStream());

        Supplier<ImageStreamTag> imageStreamTagSupplier = () -> openShift.imageStreamTags()
                .withName(image.getRepo() + ":" + image.getMajorTag()).get();
        Waiter metadataWaiter = new SimpleWaiter(() -> {
            ImageStreamTag isTag = imageStreamTagSupplier.get();
            if (isTag != null && isTag.getImage() != null && isTag.getImage().getDockerImageMetadata() != null
                    && isTag.getImage().getDockerImageMetadata().getAdditionalProperties() != null) {
                return true;
            }
            return false;
        }, "Giving OpenShift instance time to download image metadata.");

        metadataWaiter.failFast(new ImageStreamFailFastCheck(openShift, image.getRepo(), image)).waitFor();

        return new ImageMetadata(ModelNode.fromJSONString(
                new Gson().toJson(imageStreamTagSupplier.get().getImage().getDockerImageMetadata().getAdditionalProperties())));
    }

    private final ModelNode metadata;

    private ImageMetadata(ModelNode metadata) {
        this.metadata = metadata;
    }

    /**
     * Returns labels on Config:Labels path.
     *
     * @return map of labels
     */
    public Map<String, String> labels() {
        return metadata.get("Config", "Labels").asPropertyList().stream()
                .collect(Collectors.toMap(Property::getName, property -> property.getValue().asString()));
    }

    /**
     * Returns default container command on Config:Cmd path
     *
     * @return default command
     */
    public String command() {
        return metadata.get("Config", "Cmd").get(0).asString();
    }

    /**
     * Returns image environments of Config:Env path
     *
     * @return map of environments
     */
    public Map<String, String> envs() {
        final Map<String, String> env = new HashMap<>();

        metadata.get("Config", "Env").asList().forEach(
                node -> {
                    String[] keyValue = node.asString().split("=", 2);
                    env.put(keyValue[0], keyValue[1]);
                });

        return Collections.unmodifiableMap(env);
    }

    /**
     * Returns integer set of exposed ports by specified protocol (eg. tcp, udp).
     *
     * @return port set
     */
    public Set<Integer> exposedPorts(String protocol) {
        final Set<Integer> result = new HashSet<>();
        final ModelNode exposedPorts = metadata.get("Config", "ExposedPorts");

        if (exposedPorts.getType() != ModelType.UNDEFINED) {
            exposedPorts.keys().forEach(
                    portDef -> {
                        final String[] split = portDef.split("/");
                        if (StringUtils.isBlank(protocol) || split[1].equalsIgnoreCase(protocol)) {
                            result.add(Integer.parseInt(split[0]));
                        }
                    });
        }

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
