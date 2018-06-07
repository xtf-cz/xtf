package cz.xtf.junit.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeExclusionClassFilterTest {

	private static CompositeExclusionTestClassFilter create(ExclusionTestClassFilter... filters) {
		final CompositeExclusionTestClassFilter filter = new CompositeExclusionTestClassFilter();
		filter.addFilters(Arrays.asList(filters));
		return filter;
	}

	@Test
	public void collectionShouldBeEmptyIfNoFilterAdded() {
		assertThat(new CompositeExclusionTestNameFilter().getFilters()).isEmpty();
	}

	@Test
	public void collectionShouldContainsSingleFilter() {
		final ExclusionTestClassFilter filter = testClass -> true;
		assertThat(create(filter).getFilters()).contains(filter);
	}

	@Test
	public void collectionShouldContainsAllProvidedFilters() {
		final ExclusionTestClassFilter fa = testClass -> true;
		final ExclusionTestClassFilter fb = testClass -> true;
		assertThat(create(fa, fb).getFilters()).contains(fa, fb);
	}

	@Test
	public void onlyUniqueFiltersShouldBeContained() {
		final ExclusionTestClassFilter filter = testClass -> true;
		assertThat(create(filter, filter).getFilters()).containsExactly(filter);
		assertThat(create(filter, create(filter)).getFilters()).containsExactly(filter);

		final ExclusionTestClassFilter specific = new AnnotationNameFilter("Test");
		assertThat(create(specific, specific).getFilters()).containsExactly(specific);
	}

	@Test
	public void classShouldNotBeExcludedIfThereAreNoFilters() {
		assertThat(create().exclude(this.getClass())).isFalse();
	}

	@Test
	public void classShouldBeExcludedIfAtLeastOneFilterExcludesIt() {
		assertThat(create(testClass -> true, testClass -> false).exclude(this.getClass())).isTrue();
	}

	@Test
	public void classShouldNotBeExcludedIfNoFilterExcludesIt() {
		assertThat(create(testClass -> false, testClass -> false).exclude(this.getClass())).isFalse();
	}
}
