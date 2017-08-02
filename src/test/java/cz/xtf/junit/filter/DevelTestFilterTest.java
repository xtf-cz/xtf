package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.DevelTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DevelTestFilterTest {

	private static final DevelTestFilter FILTER = DevelTestFilter.instance();

	@Test
	public void classShouldNotBeRejectedWithAnnotation() {
		assertThat(FILTER.exclude(DevelTestClass.class)).isFalse();
	}

	@Test
	public void classShouldBeRejectedWithoutAnnotation() {
		assertThat(FILTER.exclude(TestClass.class)).isTrue();
	}

	private static class TestClass {}

	@DevelTest
	private static class DevelTestClass {}
}
