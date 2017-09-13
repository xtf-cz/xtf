package cz.xtf.junit.filter;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.regex.Pattern;

/**
 * Filters class names based on regular expression pattern.
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ClassNameRegExExclusionFilter implements ExclusionTestNameFilter {

	/**
	 * System property name with the regular expression for class inclusion.
	 */
	public static final String SYSTEM_PROPERTY_INCLUDE = "xtf.test.regex";

	/**
	 * System property name with the regular expression for class exclusion.
	 */
	public static final String SYSTEM_PROPERTY_EXCLUDE = "xtf.test.regex.exclude";

	@Getter private final Pattern includePattern;
	@Getter private final Pattern excludePattern;

	/**
	 * Sets the pattern via the {@link #SYSTEM_PROPERTY_INCLUDE} system property.
	 */
	public ClassNameRegExExclusionFilter() {
		final String includeString = System.getProperty(SYSTEM_PROPERTY_INCLUDE);
		this.includePattern = includeString == null ? null : Pattern.compile(includeString);
		final String excludeString = System.getProperty(SYSTEM_PROPERTY_EXCLUDE);
		this.excludePattern = excludeString == null ? null : Pattern.compile(excludeString);
	}

	@Override
	public boolean exclude(String className) {
		boolean excluded = (includePattern != null ? !includePattern.matcher(className).matches() : false);
		if (excluded) {
			return true;
		}
		return (excludePattern != null ? excludePattern.matcher(className).matches() : false);
	}
}
