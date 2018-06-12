package cz.xtf.junit.filter;

import java.lang.reflect.Modifier;

/**
 * Filter which rejects abstracts classes and interfaces.
 */
public class AbstractClassFilter implements ExclusionTestClassFilter {

	private AbstractClassFilter() {
		// no instance fields, can be singleton
	}

	public static AbstractClassFilter instance() {
		return FilterHolder.FILTER;
	}

	@Override
	public boolean exclude(Class<?> testClass) {
		return Modifier.isAbstract(testClass.getModifiers()) || testClass.isInterface();
	}

	private static class FilterHolder {
		public static final AbstractClassFilter FILTER = new AbstractClassFilter();
	}
}
