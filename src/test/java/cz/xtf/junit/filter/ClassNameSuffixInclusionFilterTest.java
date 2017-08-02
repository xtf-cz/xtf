package cz.xtf.junit.filter;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassNameSuffixInclusionFilterTest {

	@Test
	public void classShouldBeIncludedIfItsNameEndsWithTheSpecifiedSuffix() {
		assertThat(new ClassNameSuffixInclusionFilter("TestClass").include(TestClass.class.getName())).isTrue();
	}

	@Test
	public void classShouldNotBeIncludedIfItsNameNotEndsWithTheSpecifiedSuffix() {
		assertThat(new ClassNameSuffixInclusionFilter("invalid").include(TestClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldNotBeInjectedIfTheSuffixIsNull() {
		assertThat(new ClassNameSuffixInclusionFilter(null).include(TestClass.class.getName())).isFalse();
	}

	private static class TestClass {}
}
