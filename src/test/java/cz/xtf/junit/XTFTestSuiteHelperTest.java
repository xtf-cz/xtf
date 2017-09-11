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
import cz.xtf.junit.filter.SinceVersionFilter;

import org.apache.commons.lang3.StringUtils;

import org.junit.Test;

import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;

import static cz.xtf.junit.XTFTestSuite.DEFAULT_TEST_SUFFIX;

import static org.assertj.core.api.Assertions.assertThat;

// TODO(vchalupa): Add test for resolveTestClasses method
public class XTFTestSuiteHelperTest {

	private static final String DEFAULT_SUITE_URL
			= "/cz/xtf/junit/XTFTestSuiteHelperTest$DefaultSuite.class";
	private static final String EXTRA_SUFFIX = "Extra";

	private static final InclusionTestNameFilter DYNAMIC_IN_NAME_FILTER = className -> true;
	private static final InclusionTestClassFilter DYNAMIC_IN_CLASS_FILTER = testClass -> true;
	private static final ExclusionTestNameFilter DYNAMIC_EX_NAME_FILTER = className -> true;
	private static final ExclusionTestClassFilter DYNAMIC_EX_CLASS_FILTER = testClass -> true;

	@Test
	public void suiteClassShouldByTheProvidedOne() throws Exception {
		assertThat(new XTFTestSuiteHelper(DefaultSuite.class).getSuiteClass()).isEqualTo(DefaultSuite.class);
	}

	@Test
	public void testRootPathResolving() throws Exception {
		final URL url = this.getClass().getResource("/" + DefaultSuite.class.getName().replace('.', '/') + ".class");
		final String root = StringUtils.substringBefore(url.toString(), DEFAULT_SUITE_URL);
		assertThat(new XTFTestSuiteHelper(DefaultSuite.class).getRootPackage()).isEqualTo(Paths.get(new URI(root)));
	}

	private static XTFTestSuiteHelper defaultSuiteHelper() throws Exception {
		return new XTFTestSuiteHelper(DefaultSuite.class);
	}

	private static XTFTestSuiteHelper extendedSuiteHelper() throws Exception {
		return new XTFTestSuiteHelper(ExtendedSuite.class);
	}

	@Test
	public void testInclusionTestNameFilterComposingForDefaultSuite() throws Exception {
		final CompositeInclusionTestNameFilter filters = defaultSuiteHelper().getInclusionTestNameFilter();
		assertThat(filters.getFilters()).containsOnly(new ClassNameSuffixInclusionFilter(DEFAULT_TEST_SUFFIX));
	}

	@Test
	public void testInclusionTestNameFilterComposingForExtendedSuiteDefinition() throws Exception {
		final CompositeInclusionTestNameFilter filters = extendedSuiteHelper().getInclusionTestNameFilter();

		assertThat(filters.getFilters()).containsOnly(
				new ClassNameSuffixInclusionFilter(EXTRA_SUFFIX),
				DYNAMIC_IN_NAME_FILTER
		);
	}

	@Test
	public void testInclusionTestClassFilterComposingForDefaultSuite() throws Exception {
		final CompositeInclusionTestClassFilter filters = defaultSuiteHelper().getInclusionTestClassFilter();
		assertThat(filters.getFilters()).containsOnly(AllClassesInclusionFilter.instance());
	}

	@Test
	public void testInclusionTestClassFilterComposingForExtendedSuiteDefinition() throws Exception {
		final CompositeInclusionTestClassFilter filters = extendedSuiteHelper().getInclusionTestClassFilter();
		assertThat(filters.getFilters()).containsOnly(AllClassesInclusionFilter.instance(), DYNAMIC_IN_CLASS_FILTER);
	}

	@Test
	public void testExclusionTestNameFilterComposingForDefaultSuite() throws Exception {
		final CompositeExclusionTestNameFilter filters = defaultSuiteHelper().getExclusionTestNameFilter();

		assertThat(filters.getFilters()).containsOnly(
				new ClassNameRegExExclusionFilter(),
				new SuiteClassFilter(DefaultSuite.class)
		);
	}

	@Test
	public void testExclusionTestNameFilterComposingForExtendedSuiteDefinition() throws Exception {
		final CompositeExclusionTestNameFilter filters = extendedSuiteHelper().getExclusionTestNameFilter();

		assertThat(filters.getFilters()).containsOnly(
				new ClassNameRegExExclusionFilter(),
				new SuiteClassFilter(ExtendedSuite.class),
				DYNAMIC_EX_NAME_FILTER
		);
	}

	@Test
	public void testExclusionTestClassFilterComposingForDefaultSuite() throws Exception {
		final CompositeExclusionTestClassFilter filters = defaultSuiteHelper().getExclusionTestClassFilter();

		assertThat(filters.getFilters()).containsOnly(
				AbstractClassFilter.instance(),
				ManualTestFilter.instance(),
				new JenkinsRerunFilter(),
				new AnnotationNameFilter(),
				new NoAnnotationNameFilter(),
				SinceVersionFilter.instance()
		);
	}

	@Test
	public void testExclusionTestClassFilterComposingForExtendedSuiteDefinition() throws Exception {
		final CompositeExclusionTestClassFilter filters = extendedSuiteHelper().getExclusionTestClassFilter();

		assertThat(filters.getFilters()).containsOnly(
				AbstractClassFilter.instance(),
				ManualTestFilter.instance(),
				new JenkinsRerunFilter(),
				new AnnotationNameFilter(),
				new NoAnnotationNameFilter(),
				SinceVersionFilter.instance(),
				DYNAMIC_EX_CLASS_FILTER
		);
	}

	private static class DefaultSuite {}

	@TestSuffix(EXTRA_SUFFIX)
	private static class ExtendedSuite {

		@TestFilterMethod
		public static InclusionTestNameFilter inclusionNameFilter() {
			return DYNAMIC_IN_NAME_FILTER;
		}

		@TestFilterMethod
		public static InclusionTestClassFilter inclusionClassFilter() {
			return DYNAMIC_IN_CLASS_FILTER;
		}

		@TestFilterMethod
		public static ExclusionTestNameFilter exclusionNameFilter() {
			return DYNAMIC_EX_NAME_FILTER;
		}

		@TestFilterMethod
		public static ExclusionTestClassFilter exclusionClassFilter() {
			return DYNAMIC_EX_CLASS_FILTER;
		}

		@TestFilterMethod
		public static Object invalid() {
			return null;
		}
	}
}
