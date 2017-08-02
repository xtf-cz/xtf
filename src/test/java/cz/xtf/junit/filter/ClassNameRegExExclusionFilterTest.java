package cz.xtf.junit.filter;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassNameRegExExclusionFilterTest {

	private ClassNameRegExExclusionFilter filter;

	@Test
	public void classShouldNotBeRejectedIfNoRegexIsProvided() {
		createFilterWithPropertyValues(null, null);
		assertThat(filter.exclude(TestClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldBeRejectedIfIncludeRegexDoesNotMatch() {
		createFilterWithPropertyValues("invalid", null);
		assertThat(filter.exclude(TestClass.class.getName())).isTrue();
	}

	@Test
	public void classShouldNotBeRejectedIfIncludeRegexMatches() {
		createFilterWithPropertyValues(".*TestClass$", null);
		assertThat(filter.exclude(TestClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldBeRejectedIfExcludeRegexMatches() {
		createFilterWithPropertyValues(null, ".*");
		assertThat(filter.exclude(TestClass.class.getName())).isTrue();
	}

	@Test
	public void classShouldNotBeRejectedIfExcludeRegexDoesNotMatch() {
		createFilterWithPropertyValues(null, "invalid");
		assertThat(filter.exclude(TestClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldNotBeRejectedIfSelectedByIncludeButExcludeRegexDoesNotMatch() {
		createFilterWithPropertyValues(".*", "invalid");
		assertThat(filter.exclude(TestClass.class.getName())).isFalse();
	}

	@Test
	public void classShouldBeRejectedIfSelectedByIncludeAndExcludeRegexMatches() {
		createFilterWithPropertyValues(".*", ".*");
		assertThat(filter.exclude(TestClass.class.getName())).isTrue();
	}

	private void createFilterWithPropertyValues(final String includeRegex, final String excludeRegex) {
		if (includeRegex == null) {
			System.getProperties().remove(ClassNameRegExExclusionFilter.SYSTEM_PROPERTY_INCLUDE);
		} else {
			System.setProperty(ClassNameRegExExclusionFilter.SYSTEM_PROPERTY_INCLUDE, includeRegex);
		}
		if (excludeRegex == null) {
			System.getProperties().remove(ClassNameRegExExclusionFilter.SYSTEM_PROPERTY_EXCLUDE);
		} else {
			System.setProperty(ClassNameRegExExclusionFilter.SYSTEM_PROPERTY_EXCLUDE, excludeRegex);
		}
		filter = new ClassNameRegExExclusionFilter();
	}

	private static class TestClass {}
}
