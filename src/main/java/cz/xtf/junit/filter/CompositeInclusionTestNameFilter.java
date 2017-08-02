package cz.xtf.junit.filter;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composite of {@link InclusionTestNameFilter} excluding a test if at least one contained filters includes the test.
 */
@Slf4j
@NoArgsConstructor
public class CompositeInclusionTestNameFilter
		extends CompositeFilterContainer<InclusionTestNameFilter> implements InclusionTestNameFilter {

	/**
	 * @param className the name of test class
	 * @return true if at least one contained filter includes the test class with the provided name
	 */
	@Override
	public boolean include(String className) {
		return filters.parallelStream().map(f -> f.include(className)).anyMatch(r -> r);
	}
}
