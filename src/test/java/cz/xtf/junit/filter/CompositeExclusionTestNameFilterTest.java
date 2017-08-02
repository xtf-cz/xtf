package cz.xtf.junit.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;

public class CompositeExclusionTestNameFilterTest {

	private static CompositeExclusionTestNameFilter create(ExclusionTestNameFilter... filters) {
		final CompositeExclusionTestNameFilter filter = new CompositeExclusionTestNameFilter();
		filter.addFilters(Arrays.asList(filters));
		return filter;
	}

	@Test
	public void collectionShouldBeEmptyIfNoFilterAdded() {
		Assertions.assertThat(new CompositeExclusionTestNameFilter().getFilters()).isEmpty();
	}

	@Test
	public void collectionShouldContainsSingleFilter() {
		final ExclusionTestNameFilter filter = className -> true;
		Assertions.assertThat(create(filter).getFilters()).contains(filter);
	}

	@Test
	public void collectionShouldContainsAllProvidedFilters() {
		final ExclusionTestNameFilter fa = className -> true;
		final ExclusionTestNameFilter fb = className -> true;
		Assertions.assertThat(create(fa, fb).getFilters()).contains(fa, fb);
	}

	@Test
	public void onlyUniqueFiltersShouldBeContained() {
		final ExclusionTestNameFilter filter = className -> true;
		Assertions.assertThat(create(filter, filter).getFilters()).containsExactly(filter);
		Assertions.assertThat(create(filter, create(filter)).getFilters()).containsExactly(filter);

		final SuiteClassFilter specific = new SuiteClassFilter(this.getClass());
		Assertions.assertThat(create(specific, specific).getFilters()).containsExactly(specific);
	}

	@Test
	public void classShouldNotBeExcludedIfThereAreNoFilters() {
		assertThat(create().exclude(this.getClass().getName())).isFalse();
	}

	@Test
	public void classShouldBeExcludedIfAtLeastOneFilterExcludesIt() {
		assertThat(create(className -> true, className -> false).exclude(this.getClass().getName())).isTrue();
	}

	@Test
	public void classShouldNotBeExcludedIfNoFilterExcludesIt() {
		assertThat(create(className -> false, className -> false).exclude(this.getClass().getName())).isFalse();
	}
}
