package cz.xtf.junit;

import cz.xtf.junit.annotation.TestFilterMethod;
import cz.xtf.junit.annotation.TestSuffix;
import cz.xtf.junit.filter.AbstractClassFilter;
import cz.xtf.junit.filter.AllClassesInclusionFilter;
import cz.xtf.junit.filter.AnnotationNameFilter;
import cz.xtf.junit.filter.ClassNameRegExExclusionFilter;
import cz.xtf.junit.filter.ClassNameSuffixInclusionFilter;
import cz.xtf.junit.filter.CompositeExclusionTestClassFilter;
import cz.xtf.junit.filter.CompositeExclusionTestNameFilter;
import cz.xtf.junit.filter.CompositeInclusionTestClassFilter;
import cz.xtf.junit.filter.CompositeInclusionTestNameFilter;
import cz.xtf.junit.filter.ExclusionTestClassFilter;
import cz.xtf.junit.filter.ExclusionTestNameFilter;
import cz.xtf.junit.filter.InclusionTestClassFilter;
import cz.xtf.junit.filter.InclusionTestNameFilter;
import cz.xtf.junit.filter.JenkinsRerunFilter;
import cz.xtf.junit.filter.ManualTestFilter;
import cz.xtf.junit.filter.NoAnnotationNameFilter;
import cz.xtf.junit.filter.SuiteClassFilter;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reflections.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.reflections.ReflectionUtils.withAnnotation;
import static org.reflections.ReflectionUtils.withModifier;
import static org.reflections.ReflectionUtils.withParametersCount;
import static org.reflections.ReflectionUtils.withReturnTypeAssignableTo;

/**
 * Helper for {@link XTFTestSuite} resolving composing test filters and resolving test class for the suite.
 */
@Slf4j
class XTFTestSuiteHelper {

	private static final String PATH_SEPARATOR = System.getProperty("file.separator");

	/**
	 * The test suite class.
	 */
	@Getter private final Class<?> suiteClass;

	/**
	 * Path to root package of test classes.
	 *
	 * <p>
	 * Example: /home/user/workspace/project/target/test-classes
	 * </p>
	 */
	@Getter private final Path rootPackage;

	/**
	 * Collection of inclusion test name filters composed of:
	 *
	 * <ul>
	 *     <li>
	 *         {@link ClassNameSuffixInclusionFilter} - you can specify the suffix by {@link TestSuffix} or the
	 *         {@link XTFTestSuite#DEFAULT_TEST_SUFFIX} will be used.
	 *     </li>
	 *     <li>Filters from test suite {@link TestFilterMethod} methods returning {@link InclusionTestNameFilter}.</li>
	 * </ul>
	 */
	@Getter private final CompositeInclusionTestNameFilter inclusionTestNameFilter;

	/**
	 * Collection of inclusion test class filters composed of:
	 *
	 * <ul>
	 *     <li>{@link AllClassesInclusionFilter}</li>
	 *     <li>Filters from test suite {@link TestFilterMethod} methods returning {@link InclusionTestClassFilter}.</li>
	 * </ul>
	 */
	@Getter private final CompositeInclusionTestClassFilter inclusionTestClassFilter;

	/**
	 * Collection of exclusion test class filters composed of:
	 *
	 * <ul>
	 *     <li>{@link ClassNameRegExExclusionFilter}</li>
	 *     <li>{@link SuiteClassFilter}</li>
	 *     <li>Filters from test suite {@link TestFilterMethod} methods returning {@link ExclusionTestClassFilter}.</li>
	 * </ul>
	 */
	@Getter private final CompositeExclusionTestNameFilter exclusionTestNameFilter;

	/**
	 * Collection of exclusion test class filters composed of:
	 *
	 * <ul>
	 *     <li>{@link AbstractClassFilter}</li>
	 *     <li>{@link ManualTestFilter}</li>
	 *     <li>{@link AnnotationNameFilter}</li>
	 *     <li>Filters from test suite {@link TestFilterMethod} methods returning {@link ExclusionTestClassFilter}.</li>
	 * </ul>
	 */
	@Getter private final CompositeExclusionTestClassFilter exclusionTestClassFilter;

	/**
	 * Creates helper.
	 *
	 * @param suiteClass the test suite class
	 * @throws URISyntaxException in case the suite class {@link URL} cannot be converted to {@link java.net.URI}.
	 */
	XTFTestSuiteHelper(@NonNull final Class<?> suiteClass) throws URISyntaxException {
		this.suiteClass = suiteClass;
		rootPackage = getRootPackagePath(suiteClass);
		inclusionTestNameFilter = constructInclusionTestNameFilter(suiteClass);
		inclusionTestClassFilter = constructInclusionTestClassFilter(suiteClass);
		exclusionTestNameFilter = constructExclusionTestNameFilter(suiteClass);
		exclusionTestClassFilter = constructExclusionTestClassFilter(suiteClass);
	}

	private static boolean isNotAnonymousNorInnerClass(Path path) {
		return !path.toString().contains("$");
	}

	private static boolean isClassFile(Path path) {
		return path.toString().endsWith(".class");
	}

	private static String convertToClassName(Path rootPackagePath, Path path) {
		return rootPackagePath.relativize(path).toString().replace(PATH_SEPARATOR.charAt(0), '.').replace(".class", "");
	}

	private static Stream<Class<?>> convertToClass(String className) {
		try {
			return Stream.of(Class.forName(className));
		} catch (ClassNotFoundException | NoClassDefFoundError ex) {
			log.error("Could not find test class class={}", className, ex);
		}
		return Stream.of();
	}

	private static CompositeInclusionTestNameFilter constructInclusionTestNameFilter(final Class<?> suiteClass) {
		final CompositeInclusionTestNameFilter filters = new CompositeInclusionTestNameFilter();
		filters.addFilter(new ClassNameSuffixInclusionFilter(resolveTestSuffix(suiteClass)));
		filters.addFilters(getDeclaredFilters(suiteClass, InclusionTestNameFilter.class));
		return filters;
	}

	private static CompositeInclusionTestClassFilter constructInclusionTestClassFilter(final Class<?> suiteClass) {
		final CompositeInclusionTestClassFilter filters = new CompositeInclusionTestClassFilter();
		filters.addFilter(AllClassesInclusionFilter.instance());
		filters.addFilters(getDeclaredFilters(suiteClass, InclusionTestClassFilter.class));
		return filters;
	}

	private static CompositeExclusionTestNameFilter constructExclusionTestNameFilter(final Class<?> suiteClass) {
		final CompositeExclusionTestNameFilter filters = new CompositeExclusionTestNameFilter();
		filters.addFilter(new ClassNameRegExExclusionFilter());
		filters.addFilter(new SuiteClassFilter(suiteClass));
		filters.addFilters(getDeclaredFilters(suiteClass, ExclusionTestNameFilter.class));
		return filters;
	}

	private static CompositeExclusionTestClassFilter constructExclusionTestClassFilter(final Class<?> suiteClass) {
		final CompositeExclusionTestClassFilter filters = new CompositeExclusionTestClassFilter();
		filters.addFilter(AbstractClassFilter.instance());
		filters.addFilter(ManualTestFilter.instance());
		filters.addFilter(new AnnotationNameFilter());
		filters.addFilter(new NoAnnotationNameFilter());
		filters.addFilter(new JenkinsRerunFilter());
		filters.addFilters(getDeclaredFilters(suiteClass, ExclusionTestClassFilter.class));
		return filters;
	}

	private static String resolveTestSuffix(Class<?> suiteClass) {
		final TestSuffix suffix = suiteClass.getAnnotation(TestSuffix.class);
		return suffix != null ? suffix.value() : XTFTestSuite.DEFAULT_TEST_SUFFIX;
	}

	private static<T> List<T> getDeclaredFilters(Class<?> suiteClass, Class<T> returnType) {
		final Set<Method> methods = ReflectionUtils.getAllMethods(suiteClass,
				withAnnotation(TestFilterMethod.class),
				withModifier(Modifier.STATIC),
				withParametersCount(0),
				withReturnTypeAssignableTo(returnType));

		return methods.stream().flatMap(m -> {
			try {
				final T filter = (T) m.invoke(null);
				if (filter != null) {
					return Stream.of(filter);
				}
			} catch (final Exception ex) {
				log.warn("Unable to construct test filter ", ex);
			}
			return Stream.of();
		}).collect(Collectors.toList());
	}

	private static Path getRootPackagePath(final Class<?> suiteClass) throws URISyntaxException {
		final String rootPackage = StringUtils.substringBefore(suiteClass.getName(), ".");
		final URL url = XTFTestSuite.class.getResource("/" + suiteClass.getName().replace('.', '/') + ".class");

		Path rootPackagePath = Paths.get(url.toURI());

		// get the root package
		while (!rootPackage.equals(rootPackagePath.getFileName().toString())) {
			rootPackagePath = rootPackagePath.getParent();
		}

		// return test class location (parent of root package)
		return rootPackagePath.getParent();
	}

	/**
	 * @return list of test classes from the test suite root package filtered by all test filters
	 * @throws IOException in case there is an issue with reading files in the root package of test suite
	 *
	 * @see #getRootPackage()
	 * @see #getInclusionTestNameFilter()
	 * @see #getInclusionTestClassFilter()
	 * @see #getExclusionTestNameFilter()
	 * @see #getExclusionTestClassFilter()
	 */
	public List<Class<?>> resolveTestClasses() throws IOException {
		return Files.walk(rootPackage)
				.filter(path -> isClassFile(path))
				.filter(path -> isNotAnonymousNorInnerClass(path))
				.map(path -> convertToClassName(rootPackage, path))
				// filter class names
				.filter(inclusionTestNameFilter::include)
				.filter(className -> !exclusionTestNameFilter.exclude(className))
				// load classes
				.flatMap(className -> convertToClass(className))
				// filter classes
				.filter(inclusionTestClassFilter::include)
				.filter(testClass -> !exclusionTestClassFilter.exclude(testClass))
				// take only distinct classes, sort
				.distinct()
				.sorted((Class<?> c1, Class<?> c2) -> c1.getName().compareTo(c2.getName()))
				.collect(Collectors.toList());
	}
}
