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

	@Test
	public void imageParsingTest() {

		final String full = "foo:8888/bar/car:1.0-5";
		final String noport = "foo/bar/car:1.0-5";
		final String noregistry = "bar/car:1.0-5";

		final String registrynotag = "foo:8888/bar/car";
		final String noregistrynotag = "bar/car";
		final String noportnotag = "foo/bar/car";

		assertThat(ImageRegistry.toImage(full).getImageName()).isEqualTo("foo:8888/bar/car");
		assertThat(ImageRegistry.toImage(full).getImageTag()).isEqualTo("1.0-5");
		assertThat(ImageRegistry.toImage(full).getImageRegistry()).isEqualTo("foo:8888");
		assertThat(ImageRegistry.toImage(full).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(full).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(full).toString()).isEqualTo(full);

		assertThat(ImageRegistry.toImage(noport).getImageName()).isEqualTo("foo/bar/car");
		assertThat(ImageRegistry.toImage(noport).getImageTag()).isEqualTo("1.0-5");
		assertThat(ImageRegistry.toImage(noport).getImageRegistry()).isEqualTo("foo");
		assertThat(ImageRegistry.toImage(noport).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(noport).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(noport).toString()).isEqualTo(noport);

		assertThat(ImageRegistry.toImage(noregistry).getImageName()).isEqualTo("bar/car");
		assertThat(ImageRegistry.toImage(noregistry).getImageTag()).isEqualTo("1.0-5");
		assertThat(ImageRegistry.toImage(noregistry).getImageRegistry()).isEqualTo("");
		assertThat(ImageRegistry.toImage(noregistry).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(noregistry).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(noregistry).toString()).isEqualTo(noregistry);

		assertThat(ImageRegistry.toImage(registrynotag).getImageName()).isEqualTo("foo:8888/bar/car");
		assertThat(ImageRegistry.toImage(registrynotag).getImageTag()).isEqualTo("");
		assertThat(ImageRegistry.toImage(registrynotag).getImageRegistry()).isEqualTo("foo:8888");
		assertThat(ImageRegistry.toImage(registrynotag).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(registrynotag).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(registrynotag).toString()).isEqualTo(registrynotag);

		assertThat(ImageRegistry.toImage(noregistrynotag).getImageName()).isEqualTo("bar/car");
		assertThat(ImageRegistry.toImage(noregistrynotag).getImageTag()).isEqualTo("");
		assertThat(ImageRegistry.toImage(noregistrynotag).getImageRegistry()).isEqualTo("");
		assertThat(ImageRegistry.toImage(noregistrynotag).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(noregistrynotag).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(noregistrynotag).toString()).isEqualTo(noregistrynotag);

		assertThat(ImageRegistry.toImage(noportnotag).getImageName()).isEqualTo("foo/bar/car");
		assertThat(ImageRegistry.toImage(noportnotag).getImageTag()).isEqualTo("");
		assertThat(ImageRegistry.toImage(noportnotag).getImageRegistry()).isEqualTo("foo");
		assertThat(ImageRegistry.toImage(noportnotag).getImageUser()).isEqualTo("bar");
		assertThat(ImageRegistry.toImage(noportnotag).getImageRepo()).isEqualTo("car");
		assertThat(ImageRegistry.toImage(noportnotag).toString()).isEqualTo(noportnotag);
	}
}
