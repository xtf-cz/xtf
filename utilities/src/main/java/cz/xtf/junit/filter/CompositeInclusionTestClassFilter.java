package cz.xtf.junit.filter;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composite of {@link InclusionTestClassFilter} excluding a test if at least one contained filters includes the test.
 */
@Slf4j
@NoArgsConstructor
public class CompositeInclusionTestClassFilter
		extends CompositeFilterContainer<InclusionTestClassFilter> implements InclusionTestClassFilter {

	/**
	 * @param testClass the test class
	 * @return true if at least one contained filter includes the test class
	 */
	@Override
	public boolean include(Class<?> testClass) {
		return filters.parallelStream().map(f -> f.include(testClass)).anyMatch(r -> r);
	}
}
