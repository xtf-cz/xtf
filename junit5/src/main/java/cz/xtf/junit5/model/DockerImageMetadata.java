package cz.xtf.junit5.model;

import cz.xtf.core.image.Image;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.SimpleWaiter;
import cz.xtf.core.waiting.Waiter;
import io.fabric8.openshift.api.model.ImageStreamTag;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Represents Docker image metadata by exposing convenience methods to access 
 * them and provides a cache to optimize retrievals.
 */
public class DockerImageMetadata {
	private static final String METADATA_CONFIG = "Config";
	private static final String METADATA_CONFIG_LABELS = "Labels";
	private static final String METADATA_CONFIG_LABEL_NAME = "name";
	private static final ConcurrentHashMap<String, DockerImageMetadata> CACHED_METADATA = new ConcurrentHashMap<>();
	
	private final Map<String, Object> metadata;

	private DockerImageMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}
	
	private static String forgeMetadataKey(OpenShift openshift, Image image) {
		return String.format("%s;%s", openshift.getNamespace(), image.getUrl());
	}

	public static DockerImageMetadata prepare(OpenShift openShift, String imageUrl) {
		return DockerImageMetadata.prepare(openShift, Image.from(imageUrl));
	}

	public static DockerImageMetadata prepare(OpenShift openShift, Image image) {
		openShift.createImageStream(image.getImageStream());

		Supplier<ImageStreamTag> imageStreamTagSupplier = () -> openShift.imageStreamTags().withName(image.getRepo() + ":" + image.getMajorTag()).get();
		Waiter metadataWaiter = new SimpleWaiter(() -> {
			ImageStreamTag isTag = imageStreamTagSupplier.get();
			if (isTag != null && isTag.getImage() != null && isTag.getImage().getDockerImageMetadata() != null && isTag.getImage().getDockerImageMetadata().getAdditionalProperties() != null) {
				return true;
			}
			return false;
		}, "Giving OpenShift instance time to download image metadata.");

		metadataWaiter.waitFor();
		
		return CACHED_METADATA.computeIfAbsent(forgeMetadataKey(openShift, image), m -> new DockerImageMetadata(imageStreamTagSupplier.get().getImage().getDockerImageMetadata().getAdditionalProperties()));
	}
	
	private Map<String, Object> getConfig() {
		return (Map<String, Object>)metadata.get(METADATA_CONFIG);
	}
	
	private Map<String, Object> getLabels() {
		return (Map<String, Object>)getConfig().get(METADATA_CONFIG_LABELS);
	}
	
	private String getLabelValue(String label) {
		return (String)getLabels().get(label);
	}
	
	public String getNameLabelValue() {
		return getLabelValue(METADATA_CONFIG_LABEL_NAME);
	}
	
	//  ... add more of them as needed...
}
