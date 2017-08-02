package cz.xtf.openshift.imagestream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.math.BigDecimal;

public class ImageTest {
	@Test
	public void tagVersionTest() {
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar")).isTrue();
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar:latest")).isTrue();
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar:1.3")).isTrue();
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar:1.3-42")).isTrue();
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar:1.2")).isFalse();
		assertThat(ImageRegistry.isVersionAtLeast(new BigDecimal("1.3"), "reg/foo/bar:1.2-42")).isFalse();
	}
}
