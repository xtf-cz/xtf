package cz.xtf.junit.filter;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composite of {@link ExclusionTestNameFilter} excluding a test if at least one contained filters excludes the test.
 */
@Slf4j
@NoArgsConstructor
public class CompositeExclusionTestNameFilter
		extends CompositeFilterContainer<ExclusionTestNameFilter> implements ExclusionTestNameFilter {

	/**
	 * @param className the name of test class
	 * @return false if no contained effective filter excludes the test class with the provided name
	 */
	@Override
	public boolean exclude(String className) {
		return filters.parallelStream().map(f -> f.exclude(className)).anyMatch(r -> r);
	}
}
