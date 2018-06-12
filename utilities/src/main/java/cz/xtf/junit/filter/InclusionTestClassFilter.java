package cz.xtf.junit.filter;

/**
 * Test filter deciding which test classes can be included to the test suite by the test class.
 */
public interface InclusionTestClassFilter {

	/**
	 * @param testClass the test class
	 * @return true if the class can be included to the test suite.
	 */
	boolean include(Class<?> testClass);
}
