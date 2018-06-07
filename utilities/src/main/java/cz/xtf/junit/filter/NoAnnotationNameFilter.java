package cz.xtf.junit.filter;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * A type of {@link DefaultExclusionTestFilter} filtering test classes by an annotation presence.
 *
 * <p>
 * Test classes are filtered by the specified comma-separated {@code annotationNames}. If a test class has not annotation which
 * {@link Class#getSimpleName()} is equal to one of the name specified in the system property, the test class is excluded.
 * </p>
 *
 * <p>
 * If the provided {@code annotationName} is blank / empty, nothing is excluded.
 * </p>
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class NoAnnotationNameFilter implements ExclusionTestClassFilter {

	/**
	 * System property name with the filtered annotation name.
	 */
	public static final String SYSTEM_PROPERTY_NAME = "test.filter.notannotation";

	@Getter private final List<String> annotationNames;

	/**
	 * Gets the {@code annotationName} value from the {@link #SYSTEM_PROPERTY_NAME} system property.
	 *
	 * <p>
	 * If the value of the system property is blank / empty, nothing is excluded.
	 * </p>
	 */
	public NoAnnotationNameFilter() {
		final String annotationNames = System.getProperty(SYSTEM_PROPERTY_NAME);
		this.annotationNames = StringUtils.isNotBlank(annotationNames) ? Arrays.asList(annotationNames.split(",")) : null;
	}

	public NoAnnotationNameFilter(String annotationName) {
		this.annotationNames = Collections.singletonList(annotationName);
	}

	@Override
	public boolean exclude(Class<?> testClass) {

		if (annotationNames == null) {
			return false;
		}

		boolean exclude = true;
		for (String annotationName : annotationNames) {
			if (Arrays.stream(testClass.getAnnotations())
					.anyMatch(a -> a.annotationType().getSimpleName().equals(annotationName))) {
				exclude = false;
			}
		}

		return exclude;
	}
}
