package cz.xtf.openshift.imagestream;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;

import java.util.List;

import static java.lang.String.format;

/**
 * Request for new Image Stream creation comparable by {@code name}.
 */
@Value
@RequiredArgsConstructor
@Builder
@EqualsAndHashCode(of = "name")
public class ImageStreamRequest {

	/**
	 * @return the Image Stream name
	 */
	@NonNull String name;

	/**
	 * @return the name of image
	 */
	@NonNull String imageName;

	/**
	 * @return the list of custom tags for the Image Stream
	 */
	@Singular List<String> tags;

	/**
	 * Gets the image by the {@code imageName} from the {@link ImageRegistry}.
	 *
	 * @return the image by the {@code imageName}
	 */
	public String getImage() {
		if (imageName.contains("/")) {
			return imageName;
		}

		try {
			return (String) ImageRegistry.class.getDeclaredMethod(imageName).invoke(ImageRegistry.get());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(format("Cannot retrieve image '%s' from registry", imageName), e);
		}
	}
}
