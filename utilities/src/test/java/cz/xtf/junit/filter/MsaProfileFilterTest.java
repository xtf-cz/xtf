package cz.xtf.junit.filter;

import cz.xtf.junit.annotation.SpringBootProfile;
import cz.xtf.junit.annotation.VertxProfile;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * @author Radek Koubsky (rkoubsky@redhat.com)
 */
public class MsaProfileFilterTest {
	private MsaProfileFilter msaFilter;

	@Test
	public void rejectNonSpringBootClassFilterOnTest() {
		createFilterWithPropertyValue("springboot");
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.TestClass.class))
				.isTrue();
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.VertxClass.class))
				.isTrue();
	}

	@Test
	public void doNotRejectSpringBootClassFilterOnTest() {
		createFilterWithPropertyValue("springboot");
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.SpringBootClass.class))
				.isFalse();
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.SpringBootVertxClass.class))
				.isFalse();
	}

	@Test
	public void rejectNonVertxClassFilterOnTest() {
		createFilterWithPropertyValue("vertx");
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.TestClass.class))
				.isTrue();
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.SpringBootClass.class))
				.isTrue();
	}

	@Test
	public void doNotRejectVertxClassFilterOnTest() {
		createFilterWithPropertyValue("vertx");
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.VertxClass.class))
				.isFalse();
		Assertions.assertThat(this.msaFilter.exclude(MsaProfileFilterTest.SpringBootVertxClass.class))
				.isFalse();
	}

	private void createFilterWithPropertyValue(final String value) {
		System.setProperty(MsaProfileFilter.MSA_PROVIDER_PROPERTY, value);
		this.msaFilter = new MsaProfileFilter();
	}

	private static class TestClass {
	}

	@SpringBootProfile
	private static class SpringBootClass {
	}

	@VertxProfile
	private static class VertxClass {

	}

	@SpringBootProfile
	@VertxProfile
	private static class SpringBootVertxClass {

	}
}
