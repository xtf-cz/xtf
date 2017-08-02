package cz.xtf.junit.filter;

import org.junit.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;

public class CompositeInclusionTestNameFilterTest {

	private static CompositeInclusionTestNameFilter create(InclusionTestNameFilter... filters) {
		final CompositeInclusionTestNameFilter filter = new CompositeInclusionTestNameFilter();
		filter.addFilters(Arrays.asList(filters));
		return filter;
	}

	@Test
	public void collectionShouldBeEmptyIfNoFilterAdded() {
		Assertions.assertThat(new CompositeInclusionTestNameFilter().getFilters()).isEmpty();
	}

	@Test
	public void collectionShouldContainsSingleFilter() {
		final InclusionTestNameFilter filter = className -> true;
		Assertions.assertThat(create(filter).getFilters()).contains(filter);
	}

	@Test
	public void collectionShouldContainsAllProvidedFilters() {
		final InclusionTestNameFilter fa = className -> true;
		final InclusionTestNameFilter fb = className -> true;
		Assertions.assertThat(create(fa, fb).getFilters()).contains(fa, fb);
	}

	@Test
	public void onlyUniqueFiltersShouldBeContained() {
		final InclusionTestNameFilter filter = className -> true;
		Assertions.assertThat(create(filter, filter).getFilters()).containsExactly(filter);
		Assertions.assertThat(create(filter, create(filter)).getFilters()).containsExactly(filter);

		final ClassNameSuffixInclusionFilter specific = new ClassNameSuffixInclusionFilter("Test");
		Assertions.assertThat(create(specific, specific).getFilters()).containsExactly(specific);
	}

	@Test
	public void classShouldNotBeIncludedIfThereAreNoFilters() {
		assertThat(create().include(this.getClass().getName())).isFalse();
	}

	@Test
	public void classShouldBeIncludedIfAtLeastOneFilterIncludesIt() {
		assertThat(create(className -> true, className -> false).include(this.getClass().getName())).isTrue();
	}

	@Test
	public void classShouldNotBeIncludedIfNoFilterIncludesIt() {
		assertThat(create(className -> false, className -> false).include(this.getClass().getName())).isFalse();
	}
}
