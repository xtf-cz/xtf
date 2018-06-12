package cz.xtf.junit.filter;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composite of {@link ExclusionTestClassFilter} excluding a test if at least one contained filters excludes the test.
 */
@Slf4j
@NoArgsConstructor
public class CompositeExclusionTestClassFilter
		extends CompositeFilterContainer<ExclusionTestClassFilter> implements ExclusionTestClassFilter {

	/**
	 * @param testClass the test class
	 * @return false if no contained effective filter excludes the test class
	 */
	@Override
	public boolean exclude(Class<?> testClass) {
		return filters.parallelStream().map(f -> f.exclude(testClass)).anyMatch(r -> r);
	}
}
