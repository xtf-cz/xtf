package cz.xtf.junit.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeInclusionTestClassFilterTest {

	private static CompositeInclusionTestClassFilter create(InclusionTestClassFilter... filters) {
		final CompositeInclusionTestClassFilter filter = new CompositeInclusionTestClassFilter();
		filter.addFilters(Arrays.asList(filters));
		return filter;
	}

	@Test
	public void collectionShouldBeEmptyIfNoFilterAdded() {
		assertThat(new CompositeInclusionTestClassFilter().getFilters()).isEmpty();
	}

	@Test
	public void collectionShouldContainsSingleFilter() {
		final InclusionTestClassFilter filter = testClass -> true;
		assertThat(create(filter).getFilters()).contains(filter);
	}

	@Test
	public void collectionShouldContainsAllProvidedFilters() {
		final InclusionTestClassFilter fa = testClass -> true;
		final InclusionTestClassFilter fb = testClass -> true;
		assertThat(create(fa, fb).getFilters()).contains(fa, fb);
	}

	@Test
	public void onlyUniqueFiltersShouldBeContained() {
		final InclusionTestClassFilter filter = testClass -> true;
		assertThat(create(filter, filter).getFilters()).containsExactly(filter);
		assertThat(create(filter, create(filter)).getFilters()).containsExactly(filter);
	}

	@Test
	public void classShouldNotBeIncludedIfThereAreNoFilters() {
		assertThat(create().include(this.getClass())).isFalse();
	}

	@Test
	public void classShouldBeIncludedIfAtLeastOneFilterIncludesIt() {
		assertThat(create(testClass -> true, testClass -> false).include(this.getClass())).isTrue();
	}

	@Test
	public void classShouldNotBeIncludedIfNoFilterIncludesIt() {
		assertThat(create(testClass -> false, testClass -> false).include(this.getClass())).isFalse();
	}
}
