package cz.xtf.junit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.RunnerBuilder;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(XTFTestSuite.class)
public class XTFTestSuiteTest {

	private XTFTestSuite suite;

	@Before
	public void setUp() throws Exception {
		spy(XTFTestSuite.class);
		doReturn(asList(SimpleTest.class)).when(XTFTestSuite.class, "resolveTestClasses", TestSuite.class);
		createSuite(TestSuite.class);
	}

	private void createSuite(Class<?> suiteClass) throws Exception {
		suite = new XTFTestSuite(suiteClass, new RunnerBuilder() {
			@Override
			public Runner runnerForClass(Class<?> testClass) throws Throwable {
				return new BlockJUnit4ClassRunner(testClass);
			}
		});
	}

	@Test
	public void testClassResolvingShouldBeCached() throws Exception {
		createSuite(TestSuite.class);
		verifyStatic(times(1));
	}

	@Test
	public void testClassResolvingShouldBeCachedByClass() throws Exception {
		createSuite(AnotherTestSuite.class);
		verifyStatic(times(2));
	}

	@Test
	public void numberOfChildrenShouldBeNumberOfResolvedClasses() throws Exception {
		createSuite(TestSuite.class);
		assertThat(suite.getChildren()).hasSize(1);
	}

	public static class TestSuite {}

	public static class AnotherTestSuite {}

	public static class SimpleTest {

		@Test
		public void test() {}
	}
}
