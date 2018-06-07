package cz.xtf.junit.filter;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractClassFilterTest {

	private static final AbstractClassFilter FILTER = AbstractClassFilter.instance();

	@Test
	public void interfacesAndAbstractClassesShouldBeRejected() {
		assertThat(FILTER.exclude(ConcreteTestClass.class)).isFalse();
		assertThat(FILTER.exclude(AbstractTestClass.class)).isTrue();
		assertThat(FILTER.exclude(TestInterface.class)).isTrue();
	}

	private static class ConcreteTestClass {}

	private static abstract class AbstractTestClass {}

	interface TestInterface {}
}
