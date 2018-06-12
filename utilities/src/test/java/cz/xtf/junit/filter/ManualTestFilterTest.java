package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.ManualTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ManualTestFilterTest {

	private static final ManualTestFilter FILTER = ManualTestFilter.instance();

	@Test
	public void classShouldNotBeRejectedWithoutAnnotation() {
		assertThat(FILTER.exclude(TestClass.class)).isFalse();
	}

	@Test
	public void classShouldBeRejectedWithAnnotation() {
		assertThat(FILTER.exclude(ManualTestClass.class)).isTrue();
	}

	private static class TestClass {}

	@ManualTest
	private static class ManualTestClass {}
}
