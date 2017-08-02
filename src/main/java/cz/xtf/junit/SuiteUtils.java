package cz.xtf.junit;

import com.google.common.base.Predicate;
import cz.xtf.junit.annotation.ImageStream;
import cz.xtf.junit.annotation.ImageStreams;
import cz.xtf.openshift.imagestream.ImageStreamRequest;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.reflections.ReflectionUtils.getAllAnnotations;

import cz.xtf.openshift.imagestream.ImageStreamAnnotationConverter;

/**
 * Utils for the {@link XTFTestSuite}.
 */
final class SuiteUtils {

	private SuiteUtils() {
		// util class, do not initialize
	}

	/**
	 * @param suiteClass the test suite class
	 * @param testClasses the concrete test classes
	 * @return all Image Stream request defined on the classes by {@link ImageStream} and {@link ImageStreams}
	 */
	public static Set<ImageStreamRequest> getImageStreamRequests(Class<?> suiteClass, List<Class<?>> testClasses) {
		final Set<ImageStreamRequest> requests = new HashSet<>();
		requests.addAll(getImageStreamRequestsForClass(suiteClass));
		testClasses.forEach(c -> requests.addAll(getImageStreamRequestsForClass(c)));
		return requests;
	}

	private static List<ImageStreamRequest> getImageStreamRequestsForClass(Class<?> c) {
		final List<ImageStreamRequest> requests = new ArrayList<>();
		addIfApplicable(c, getAllAnnotations(c, isImageStreams()).stream().map(a -> (ImageStreams) a)
				.flatMap(iss -> Stream.of(iss.value())), requests);
		addIfApplicable(c, getAllAnnotations(c, isImageStream()).stream().map(a -> (ImageStream) a), requests);
		return requests;
	}

	private static Predicate<Annotation> isImageStreams() {
		return a -> a.annotationType() == ImageStreams.class;
	}

	private static Predicate<Annotation> isImageStream() {
		return a -> a.annotationType() == ImageStream.class;
	}

	private static void addIfApplicable(Class<?> c, Stream<ImageStream> stream, List<ImageStreamRequest> requests) {
		stream.flatMap(is -> isApplicable(c, is) ? Stream.of(is) : Stream.of()).map(is -> ImageStreamAnnotationConverter.convert(is)).forEach(r -> requests.add(r));
	}

	private static boolean isApplicable(Class<?> c, ImageStream is) {
		if (isNotBlank(is.condition())) {
			try {
				return (Boolean) c.getMethod(is.condition()).invoke(null);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(
						format("Cannot invoke conditional method '%s' in class '%s'", is.condition(), c), e
				);
			}
		}
		return true;
	}
}
