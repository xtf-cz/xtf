package cz.xtf.junit.filter;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(DataProviderRunner.class)
public class AnnotationNameFilterTest {

	private static final String ANNOTATION_NAME = Filter.class.getSimpleName();

	private AnnotationNameFilter filter;

	@DataProvider
	public static Object[][] invalidFilterName() {
		return new Object[][] {
				{null},
				{""},
				{"  "},
				{"UnusedFilter"} // valid system property name but unused annotation name
		};
	}

	@Test
	public void classShouldBeExcludedWhenAnnotationPresent() {
		createFilterWithPropertyValue(ANNOTATION_NAME);
		assertThat(filter.exclude(TestClass.class)).isTrue();
	}

	@Test
	public void classShouldBeExcludedWhenAnnotationPresentOnParentClass() {
		createFilterWithPropertyValue(ANNOTATION_NAME);
		assertThat(filter.exclude(TestSubClass.class)).isTrue();
	}

	@Test
	@UseDataProvider("invalidFilterName")
	public void classShouldBeIncludedWhenFilterIsInvalid(String value) {
		createFilterWithPropertyValue(value);
		assertThat(filter.exclude(TestClass.class)).isFalse();
	}

	private void createFilterWithPropertyValue(String value) {
		if (value == null) {
			System.getProperties().remove(AnnotationNameFilter.SYSTEM_PROPERTY_NAME);
		} else {
			System.setProperty(AnnotationNameFilter.SYSTEM_PROPERTY_NAME, value);
		}
		filter = new AnnotationNameFilter();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	@Inherited
	public @interface Filter {}

	@Filter
	private static class TestClass {}

	private static class TestSubClass extends TestClass {}
}
