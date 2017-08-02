package cz.xtf.junit.filter;

/**
 * Accept all classes.
 */
public class AllClassesInclusionFilter implements InclusionTestClassFilter {

	private AllClassesInclusionFilter() {
		// no instance fields, can be singleton
	}

	public static AllClassesInclusionFilter instance() {
		return FilterHolder.FILTER;
	}

	@Override
	public boolean include(Class<?> testClass) {
		return true;
	}

	private static class FilterHolder {
		public static final AllClassesInclusionFilter FILTER = new AllClassesInclusionFilter();
	}
}
