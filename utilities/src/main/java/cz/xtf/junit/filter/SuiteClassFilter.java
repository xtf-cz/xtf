package cz.xtf.junit.filter;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Filter rejecting test classes with the same name as the one given in constructor.
 *
 * <p>
 * Use it to exclude the suite class used to search for other classes. This prevents the initialization infinite loops.
 * </p>
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class SuiteClassFilter implements ExclusionTestNameFilter {

	@Getter private final Class<?> suiteClass;

	@Override
	public boolean exclude(String className) {
		return className.equals(suiteClass.getName());
	}
}
