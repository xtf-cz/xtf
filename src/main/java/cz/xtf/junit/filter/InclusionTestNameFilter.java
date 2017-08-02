package cz.xtf.junit.filter;

/**
 * Test filter deciding which test classes can be included to the test suite by the name of test class.
 */
public interface InclusionTestNameFilter {

	/**
	 * @param className the name of test class
	 * @return true if the class can be included to the test suite.
	 */
	boolean include(String className);
}
