package cz.xtf.manipulation;

import cz.xtf.TestConfiguration;
import cz.xtf.UsageRecorder;
import cz.xtf.openshift.OpenShiftUtil;
import cz.xtf.openshift.OpenShiftUtils;
import cz.xtf.openshift.builder.ImageStreamBuilder;
import cz.xtf.openshift.imagestream.ImageStreamRequest;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.ImageStream;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Util class responsible for ImageStream manipulation.
 */
@Slf4j
public final class ImageStreamProcessor {

	public static final String IS_NAMESPACE = TestConfiguration.imageStreamNamespace();

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

		final OpenShiftUtil openshift = TestConfiguration.openshiftOnline() ? OpenShiftUtils.master(IS_NAMESPACE) : OpenShiftUtils.admin(IS_NAMESPACE);
		final ImageStreamBuilder isBuilder = new ImageStreamBuilder(name).insecure();
		for (final String tag : customTags) {
			isBuilder.addTag(tag, fromImage, ImageStreamBuilder.TagReferencePolicyType.LOCAL);
		}
		final ImageStream is = isBuilder.build();

		log.info("action=create-image-stream status=START name={} image={} tags={}", name, fromImage, customTags);
		openshift.deleteImageStream(is);
		openshift.createImageStream(is);
		log.info("action=create-image-stream status=FINISH name={} image={} tags={}", name, fromImage, customTags);
	}
}
