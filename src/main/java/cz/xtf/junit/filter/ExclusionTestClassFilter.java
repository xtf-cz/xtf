package cz.xtf.junit.filter;

/**
 * Test filter deciding which test classes must be excluded to the test suite by the test class.
 */
public interface ExclusionTestClassFilter {

	/**
	 * @param testClass the test class
	 * @return true if the class should be excluded from the test suite
	 */
	boolean exclude(Class<?> testClass);
}
