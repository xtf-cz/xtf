package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.BypassAuthUser;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(DataProviderRunner.class)
public class BypassAuthUserFilterTest {

	private BypassAuthUserFilter filter;

	@DataProvider
	public static Object[][] negativeDefault() {
		return new Object[][] {
				{null},
				{false}
		};
	}

	@Test
	@UseDataProvider("negativeDefault")
	public void classShouldNotBeRejectedIfNoAnnotationPresentsAndDefaultIsFalse(Boolean value) {
		createFilterWithPropertyValue(value);
		assertThat(filter.exclude(TestClass.class)).isFalse();
	}

	@Test
	public void classShouldBeRejectedIfNoAnnotationPresentsAndDefaultIsTrue() {
		createFilterWithPropertyValue(true);
		assertThat(filter.exclude(TestClass.class)).isTrue();
	}

	@Test
	@UseDataProvider("negativeDefault")
	public void classShouldBeRejectedIfAnnotationPresentsAndDefaultIsFalse(Boolean value) {
		createFilterWithPropertyValue(value);
		assertThat(filter.exclude(BypassTestClass.class)).isTrue();
	}

	@Test
	public void classShouldNotBeRejectedIfAnnotationPresentsAndDefaultIsTrue() {
		createFilterWithPropertyValue(true);
		assertThat(filter.exclude(BypassTestClass.class)).isFalse();
	}

	private void createFilterWithPropertyValue(Boolean value) {
		if (value == null) {
			System.getProperties().remove(BypassAuthUserFilter.SYSTEM_PROPERTY_NAME);
		} else {
			System.setProperty(BypassAuthUserFilter.SYSTEM_PROPERTY_NAME, value.toString());
		}
		filter = new BypassAuthUserFilter();
	}

	private static class TestClass {}

	@BypassAuthUser
	private static class BypassTestClass {}
}
