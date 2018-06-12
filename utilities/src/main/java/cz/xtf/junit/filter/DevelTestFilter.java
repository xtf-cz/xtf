package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.DevelTest;

import java.util.Arrays;

/**
 * Test filter which accepts only classes with the {@link DevelTest} and rejects all classes without this annotation.
 */
public class DevelTestFilter implements ExclusionTestClassFilter {

	private DevelTestFilter() {
		// no instance fields, can be singleton
	}

	public static DevelTestFilter instance() {
		return DevelTestFilter.FilterHolder.FILTER;
	}

	@Override
	public boolean exclude(Class<?> testClass) {
		return !Arrays.stream(testClass.getAnnotations()).filter(a -> a instanceof DevelTest).findFirst().isPresent();
	}

	private static class FilterHolder {
		public static final DevelTestFilter FILTER = new DevelTestFilter();
	}
}
