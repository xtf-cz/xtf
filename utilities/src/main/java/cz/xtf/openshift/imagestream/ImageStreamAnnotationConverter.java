package cz.xtf.openshift.imagestream;

import cz.xtf.junit.annotation.ImageStream;
import cz.xtf.openshift.imagestream.ImageStreamRequest.ImageStreamRequestBuilder;
import lombok.NonNull;

import java.util.Arrays;

/**
 * Handler for {@link ImageStream} responsible for converting the annotation to regular {@link ImageStreamRequest}.
 */
public class ImageStreamAnnotationConverter {

	/**
	 * @param is ImageStream annotation, cannot be null
	 * @return new Image Steam request created from value in the provided annotation
	 */
	public static ImageStreamRequest convert(@NonNull ImageStream is) {
		final ImageStreamRequestBuilder builder = ImageStreamRequest.builder().name(is.name()).imageName(is.image());
		Arrays.stream(is.tags()).forEach(t -> builder.tag(t));
		return builder.build();
	}
}
