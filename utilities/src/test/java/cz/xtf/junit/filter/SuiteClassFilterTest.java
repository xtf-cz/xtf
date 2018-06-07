package cz.xtf.junit.filter;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SuiteClassFilterTest {

	@Test
	public void classShouldNotBeRejectedIfNotSuiteClass() {
		assertThat(new SuiteClassFilter(Object.class).exclude(TestSuiteClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldBeRejectedIfSuiteClass() {
		assertThat(new SuiteClassFilter(TestSuiteClass.class).exclude(TestSuiteClass.class.getName())).isTrue();
	}

	private static class TestSuiteClass {}
}
