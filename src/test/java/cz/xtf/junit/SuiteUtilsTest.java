package cz.xtf.junit;

import cz.xtf.junit.annotation.ImageStream;
import cz.xtf.openshift.imagestream.ImageStreamRequest;
import org.junit.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SuiteUtilsTest {

	private static int flag;

	@Test
	public void retrievedRequestsShouldContainOnlyUniqueItems() {
		final Set<ImageStreamRequest> requests = SuiteUtils.getImageStreamRequests(
				TestSuite.class,
				Arrays.asList(TestClass.class, SingleISTestClass.class)
		);
		assertThat(requests).containsOnly(
				ImageStreamRequest.builder().name("A").imageName("").build(),
				ImageStreamRequest.builder().name("B").imageName("").build(),
				ImageStreamRequest.builder().name("C").imageName("").build(),
				ImageStreamRequest.builder().name("D").imageName("").build(),
				ImageStreamRequest.builder().name("E").imageName("").build(),
				ImageStreamRequest.builder().name("F").imageName("").build(),
				ImageStreamRequest.builder().name("G").imageName("").build()
		);
	}

	@Test
	public void conditionalMethodIs1ShouldBeTakenInConsideration() {
		flag = 1;
		final Set<ImageStreamRequest> requests = SuiteUtils.getImageStreamRequests(
				TestSuite.class,
				Arrays.asList(ConditionalTestClass.class)
		);
		assertThat(requests).containsOnly(
				ImageStreamRequest.builder().name("A").imageName("").build(),
				ImageStreamRequest.builder().name("B").imageName("").build(),
				ImageStreamRequest.builder().name("H").imageName("").build()
		);
	}

	@Test
	public void conditionalMethodIs2ShouldBeTakenInConsideration() {
		flag = 2;
		final Set<ImageStreamRequest> requests = SuiteUtils.getImageStreamRequests(
				TestSuite.class,
				Arrays.asList(ConditionalTestClass.class)
		);
		assertThat(requests).containsOnly(
				ImageStreamRequest.builder().name("A").imageName("").build(),
				ImageStreamRequest.builder().name("B").imageName("").build(),
				ImageStreamRequest.builder().name("I").imageName("").build()
				);
	}

	@Test(expected = IllegalStateException.class)
	public void illegalStateExpectedIfConditionalMethodCannotBeInvoked() {
		SuiteUtils.getImageStreamRequests(TestSuite.class, Arrays.asList(BadConditionalTestClass.class));
	}

	@ImageStream(name = "A", image = "")
	private static class TestSuite extends BaseTestSuite {}

	@ImageStream(name = "B", image = "")
	private static class BaseTestSuite {}

	@ImageStream(name = "A", image = "")
	@ImageStream(name = "B", image = "")
	@ImageStream(name = "C", image = "")
	@ImageStream(name = "D", image = "")
	private static class TestClass extends BaseTestClass {}

	@ImageStream(name = "A", image = "")
	@ImageStream(name = "E", image = "")
	private static class BaseTestClass {}

	@ImageStream(name = "F", image = "")
	private static class SingleISTestClass extends SingleIsBaseTestClass {}

	@ImageStream(name = "G", image = "")
	private static class SingleIsBaseTestClass {}

	@ImageStream(name = "H", image = "", condition = "is1")
	@ImageStream(name = "I", image = "", condition = "is2")
	private static class ConditionalTestClass {

		public static boolean is1() {
			return flag == 1;
		}

		public static boolean is2() {
			return flag == 2;
		}
	}

	@ImageStream(name = "J", image = "", condition = "xzy")
	private static class BadConditionalTestClass {}
}
