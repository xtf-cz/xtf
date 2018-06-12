package cz.xtf.junit.filter;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Filters class names based on suffix pattern.
 *
 * <p>
 * For example filter for suffix "Test" will accept classes {@code SomeTest}, {@code AnotherTest} and exclude
 * {@code SomeTest2}.
 * </p>
 *
 * <p>
 * It uses {@link String#endsWith(String)} method.
 * </p>
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ClassNameSuffixInclusionFilter implements InclusionTestNameFilter {

	@Getter private final String suffix;

	@Override
	public boolean include(String className) {
		return suffix == null ? false : className.endsWith(suffix);
	}
}
