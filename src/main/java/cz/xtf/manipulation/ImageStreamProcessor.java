package cz.xtf.manipulation;

import cz.xtf.UsageRecorder;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.builder.ImageStreamBuilder;
import cz.xtf.openshift.imagestream.ImageStreamRequest;
import io.fabric8.openshift.api.model.ImageStream;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Util class responsible for ImageStream manipulation.
 */
@Slf4j
public final class ImageStreamProcessor {

	public static final String IS_NAMESPACE = "openshift";

	private ImageStreamProcessor() {
		// util class, do not initialize
	}

	/**
	 * @param request the request for Image Stream creation
	 * @see #createImageStream(String, String, List)
	 */
	public static void createImageStream(ImageStreamRequest request) {
		createImageStream(request.getName(), request.getImage(), request.getTags());
	}

	/**
	 * @param name the name of ImageStream to create
	 * @param fromImage the image from which will be the ImageStream created
	 * @param customTags list of custom tag to mark the image
	 * @see #createImageStream(String, String, String...)
	 */
	public static void createImageStream(String name, String fromImage, List<String> customTags) {
		createImageStream(name, fromImage, customTags.toArray(new String[customTags.size()]));
	}

	/**
	 * Creates (removes and recreates) new ImageStream on OpenShift in the {@link #IS_NAMESPACE} namespace.
	 *
	 * @param name the name of ImageStream to create
	 * @param fromImage the image from which will be the ImageStream created
	 * @param customTags list of custom tag to mark the image
	 */
	public static void createImageStream(String name, String fromImage, String... customTags) {
		UsageRecorder.recordImage(name, fromImage);

		final OpenshiftUtil openshift = OpenshiftUtil.getInstance();
		final ImageStreamBuilder isBuilder = new ImageStreamBuilder(name).insecure();
		for (final String tag : customTags) {
			isBuilder.addTag(tag, fromImage);
		}
		final ImageStream is = isBuilder.build();

		log.info("action=create-image-stream status=START name={} image={} tags={}", name, fromImage, customTags);
		openshift.withAdminUser(client -> client.inNamespace(IS_NAMESPACE).imageStreams().withName(name).delete());
		openshift.withAdminUser(client -> client.inNamespace(IS_NAMESPACE).imageStreams().create(is));
		log.info("action=create-image-stream status=FINISH name={} image={} tags={}", name, fromImage, customTags);
	}
}
