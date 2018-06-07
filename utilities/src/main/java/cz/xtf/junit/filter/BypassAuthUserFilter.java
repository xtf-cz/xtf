package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.BypassAuthUser;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static java.util.Arrays.stream;

/**
 * Filter classes by {@link BypassAuthUser} annotation and the provided value of {@code bypassAuthUser}.
 *
 * <p>
 * Rejects:
 * </p>
 * <ul>
 *     <li>
 *         All classes without the {@link BypassAuthUser} annotation if the {@code bypassAuthUser} is se to {@code true}
 *     </li>
 *     <li>
 *         All classes with the {@link BypassAuthUser} annotation if the {@code bypassAuthUser} is se to {@code false}
 *     </li>
 * </ul>
 */
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class BypassAuthUserFilter implements ExclusionTestClassFilter {

	/**
	 * System property name with the filtered annotation name.
	 */
	public static final String SYSTEM_PROPERTY_NAME = "org.kie.server.bypass.auth.user";

	private final boolean bypassAuthUser;

	/**
	 * Gets the {@code bypassAuthUser} value from the {@link #SYSTEM_PROPERTY_NAME} system property.
	 *
	 * <p>
	 * If the system property is not provided, {@code false} is used.
	 * </p>
	 */
	public BypassAuthUserFilter() {
		bypassAuthUser = Boolean.valueOf(System.getProperty(SYSTEM_PROPERTY_NAME, "false"));
	}

	@Override
	public boolean exclude(final Class<?> testClass) {
		if (stream(testClass.getAnnotations()).filter(a -> a instanceof BypassAuthUser).findFirst().isPresent()) {
			return !bypassAuthUser;
		}
		return bypassAuthUser;
	}
}
