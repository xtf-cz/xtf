package cz.xtf.openshift.imagestream;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;

public class ImageStreamRequestTest {

	private static final String IMAGE = "image";
	private static final String NAME = "name";

	@Test(expected = NullPointerException.class)
	public void nameShouldBeRequired() {
		ImageStreamRequest.builder().imageName(IMAGE).build();
	}

	@Test(expected = NullPointerException.class)
	public void imageShouldBeRequired() {
		ImageStreamRequest.builder().name(NAME).build();
	}

	@Test
	public void tagsShouldBeOptional() {
		ImageStreamRequest.builder().name(NAME).imageName(IMAGE).build();
	}

	@Test
	public void testBuilder() {
		final String[] tags = {"tag#1", "tag#2"};
		final ImageStreamRequest isr = ImageStreamRequest.builder().name(NAME).imageName(IMAGE).tag(tags[0]).tag(tags[1]).build();
		assertThat(isr.getName()).isEqualTo(NAME);
		assertThat(isr.getImageName()).isEqualTo(IMAGE);
		assertThat(isr.getTags()).containsExactly(tags);
	}

	@Test
	public void noTagsShouldBeRepresentedAsEmptyCollection() {
		Assertions.assertThat(ImageStreamRequest.builder().name(NAME).imageName(IMAGE).build().getTags()).isNotNull().isEmpty();
	}

	@Test
	public void imageShouldByRetrievedByNameFromImageRegistry() {
		Assertions.assertThat(ImageStreamRequest.builder().name(NAME).imageName("eap7").build().getImage()).isEqualTo(ImageRegistry.get().eap7());
	}

	@Test(expected = IllegalStateException.class)
	public void illegalStateExpectedWhenImageIsNotFoundInImageRegistry() {
		ImageStreamRequest.builder().name(NAME).imageName("xyz").build().getImage();
	}
}
