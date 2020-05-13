package cz.xtf.core.openshift.imagestreams;

import io.fabric8.openshift.api.model.ImageStream;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Defines the contract for implementing a registry which keeps track
 * of {@link ImageStream} instances.
 */
public interface ImageStreamRegistry {

	ImageStream register(ImageStream imageStream, UnaryOperator<ImageStream> imageStreamCreation);

	ImageStream unregister(String name, Consumer<ImageStream> imageStreamDeletion);
}
