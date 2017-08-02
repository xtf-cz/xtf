package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.ManualTest;

import java.util.Arrays;

/**
 * Filter which rejects classes with {@link ManualTest} annotation.
 */
public class ManualTestFilter implements ExclusionTestClassFilter {

	private ManualTestFilter() {
		// no instance fields, can be singleton
	}

	public static ManualTestFilter instance() {
		return ManualTestFilter.FilterHolder.FILTER;
	}

	@Override
	public boolean exclude(Class<?> testClass) {
		return Arrays.stream(testClass.getAnnotations()).filter(a -> a instanceof ManualTest).findFirst().isPresent();
	}

	private static class FilterHolder {
		public static final ManualTestFilter FILTER = new ManualTestFilter();
	}
}
