package cz.xtf.openshift.imagestream;

import cz.xtf.junit.annotation.ImageStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ImageStreamAnnotationConverterTest {

	private static final String IMAGE = "image";
	private static final String NAME = "name";

	@Mock
	private ImageStream is;

	@Before
	public void setUp() {
		when(is.name()).thenReturn(NAME);
		when(is.image()).thenReturn(IMAGE);
		when(is.tags()).thenReturn(new String[] {});
	}

	@Test
	public void testConversionWithoutTags() {
		final ImageStreamRequest isr = ImageStreamAnnotationConverter.convert(is);
		assertNameAndImage(isr);
		assertThat(isr.getTags()).isNotNull().isEmpty();
	}

	@Test
	public void testConversionWithTags() {
		final String[] tags = {"tag#1", "tag#2"};
		when(is.tags()).thenReturn(tags);

		final ImageStreamRequest isr = ImageStreamAnnotationConverter.convert(is);
		assertNameAndImage(isr);
		assertThat(isr.getTags()).containsExactly(tags);
	}

	private void assertNameAndImage(ImageStreamRequest isr) {
		assertThat(isr.getName()).isEqualTo(NAME);
		assertThat(isr.getImageName()).isEqualTo(IMAGE);
	}
}
