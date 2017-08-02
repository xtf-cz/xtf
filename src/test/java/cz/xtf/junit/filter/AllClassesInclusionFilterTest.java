package cz.xtf.junit.filter;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(DataProviderRunner.class)
public class AllClassesInclusionFilterTest {

	@DataProvider
	public static Object[][] classes() {
		return new Object[][]{
				{AllClassesInclusionFilter.class},
				{AllClassesInclusionFilterTest.class},
				{Object.class},
				{String.class},
				{Integer.class}
		};
	}

	@UseDataProvider("classes")
	@Test
	public void allClassesShouldBeIncluded(Class<?> testClass) {
		assertThat(AllClassesInclusionFilter.instance().include(testClass)).isTrue();
	}
}
