package cz.xtf.junit;

import cz.xtf.build.BuildDefinition;
import cz.xtf.build.BuildManagerV2;
import cz.xtf.build.XTFBuild;
import cz.xtf.junit.annotation.UsesBuild;
import cz.xtf.junit.annotation.PrepareProject;
import cz.xtf.junit.annotation.*;
import cz.xtf.manipulation.ImageStreamProcessor;
import cz.xtf.manipulation.LogCleaner;
import cz.xtf.manipulation.ProjectHandler;
import cz.xtf.manipulation.Recorder;
import cz.xtf.openshift.imagestream.ImageStreamRequest;

import cz.xtf.junit.filter.*;
import cz.xtf.wait.Waiters;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A JUnit test runner which runs all test classes in the maven module.
 *
 * <p>
 * Test classes to run are (can be) filtered by couple of filters:
 * </p>
 * <ul>
 *     <li>
 *         {@link ClassNameSuffixInclusionFilter} - you can specify the suffix by
 *         {@link TestSuffix} or the {@link #DEFAULT_TEST_SUFFIX} will be used.
 *     </li>
 *     <li>{@link ClassNameRegExExclusionFilter}</li>
 *     <li>{@link AbstractClassFilter}</li>
 *     <li>{@link ManualTestFilter}</li>
 *     <li>{@link AnnotationNameFilter}</li>
 *     <li>{@link SuiteClassFilter}</li>
 *     <li>
 *         All custom filter defined in the test suite class in methods annotated by
 *         {@link TestFilterMethod}
 *     </li>
 * </ul>
 *
 * <p>
 * First, all {@link org.junit.BeforeClass} and {@link org.junit.AfterClass} methods are invoked in the test suite
 * class, then all test classes are executed.
 * </p>
 */
@Slf4j
public class XTFTestSuite extends ParentRunner<Runner> {

	/**
	 * Default test class name suffix used for the {@link ClassNameSuffixInclusionFilter}
	 * if the test suite class does not have the {@link TestSuffix} annotation.
	 */
	public static final String DEFAULT_TEST_SUFFIX = "Test";

	// This cache is needed as surefire call the test with Suite runner. This causes that the AllTestsSuite runner is
	// initialized twice and the loading process of test classes is performed twice too.
	// We cannot prevent the AllTestSuite runner to be called twice, but we can cache the test classes loading process.
	private static final Map<Class<?>, List<Runner>> RUNNERS_CACHE = new HashMap<>();
	private static final Map<Class<?>, List<Class<?>>> TEST_CLASSES_CACHE = new HashMap<>();

	private static Class<?> suiteClass;
	private static ProjectHandler projectHandler;

	private final List<Runner> testRunners;

	public XTFTestSuite(final Class<?> suiteClass, final RunnerBuilder builder) throws InitializationError {
		super(suiteClass);
		XTFTestSuite.suiteClass = suiteClass;
		testRunners = getRunners(suiteClass, builder);
	}

	@Synchronized
	private static List<Runner> getRunners(@NonNull Class<?> suiteClass, @NonNull RunnerBuilder builder)
			throws InitializationError {

		List<Runner> runners = RUNNERS_CACHE.get(suiteClass);
		if (runners == null) {
			final List<Class<?>> testClasses = resolveTestClasses(suiteClass);
			runners = Collections.unmodifiableList(builder.runners(suiteClass, testClasses));
			RUNNERS_CACHE.put(suiteClass, runners);
			TEST_CLASSES_CACHE.put(suiteClass, testClasses);
		}
		return runners;
	}

	private static List<Class<?>> resolveTestClasses(Class<?> suiteClass) throws InitializationError {
		try {
			log.info("action=resolving-test-classes status=START suite={}", suiteClass);
			final List<Class<?>> classes = new XTFTestSuiteHelper(suiteClass).resolveTestClasses();
			log.info("action=resolving-test-classes status=FINISH suite={} testClasses={}", suiteClass, classes.size());
			log.debug("Test classes: ", classes.size());
			classes.forEach(c -> log.debug(" - {}", c.getName()));
			return classes;
		} catch (URISyntaxException | IOException e) {
			throw new InitializationError(e);
		}
	}

	/**
	 * Prepares the suite, executed before all {@link BeforeClass} methods.
	 *
	 * <ul>
	 *     <li>Records environment version</li>
	 *     <li>Cleans log directories</li>
	 *     <li>Prepares project</li>
	 *     <li>Records used images</li>
	 * </ul>
	 *
	 * @throws Exception in case of any error
	 */
	public static void beforeSuite() throws Exception {
		Recorder.recordEnvironmentVersions();
		LogCleaner.cleanAllLogDirectories();
		prepareProject();
		recordUsedImages();
		createImageStreams();
		deployBuilds();
	}

	/**
	 * Cleanups the suite, executed after all {@link AfterClass} methods.
	 *
	 * <ul>
	 *     <li>Cleans projects</li>
	 *     <li>Stores test configuration</li>
	 * </ul>
	 *
	 * @throws Exception in case of any error
	 */
	public static void afterSuite() throws Exception {
		cleanProject();
		Recorder.storeTestConfiguration();
	}

	// TestClass#getAnnotatedMethdos returns unmodifiable list
	private static List<FrameworkMethod> join(
			@NonNull FrameworkMethod fm,
			@NonNull List<FrameworkMethod> methods,
			boolean asFirst) {

		final List<FrameworkMethod> joined = new ArrayList<>(methods.size() + 1);
		if (asFirst) {
			joined.add(fm);
			joined.addAll(methods);
		} else {
			joined.addAll(methods);
			joined.add(fm);
		}
		return joined;
	}

	private static void prepareProject() {
		final PrepareProject prepareProject = suiteClass.getAnnotation(PrepareProject.class);
		if (prepareProject != null) {
			projectHandler = new ProjectHandler(prepareProject.value());
			projectHandler.prepare();
		}
	}

	private static void recordUsedImages() {
		final RecordImageUsage recordImageUsage = suiteClass.getAnnotation(RecordImageUsage.class);
		if (recordImageUsage != null) {
			Recorder.recordUsedImages(recordImageUsage.value());
		}
	}

	private static void createImageStreams() {
		final List<Class<?>> tcs = TEST_CLASSES_CACHE.get(suiteClass);
		final Set<ImageStreamRequest> requests = SuiteUtils.getImageStreamRequests(suiteClass, tcs);
		if (requests.size() > 0) {
			requests.forEach(ImageStreamProcessor::createImageStream);
			Waiters.sleep(1_000L,"Waiting for ImageStreams creation.");
		}
	}

	private static void deployBuilds() {
		Set<BuildDefinition> buildsToBeBuild = new HashSet<>();

		TEST_CLASSES_CACHE.forEach((k, v) -> {
			v.forEach(c -> {
				Arrays.stream(c.getAnnotations()).filter(a -> a.annotationType().getAnnotation(UsesBuild.class) != null).forEach(a -> {
					try {
						Object result = a.annotationType().getMethod("value").invoke(a);
						if (result instanceof XTFBuild) {
							buildsToBeBuild.add(((XTFBuild) result).getBuildDefinition());
						}
						else if (result instanceof XTFBuild[]) {
							Stream.of((XTFBuild[])result).forEach(x -> buildsToBeBuild.add(x.getBuildDefinition()));
						} else {
							log.error("Value present in {} is not instance of {}, not able to get BuildDefinition to be built");
						}
					} catch (Exception e) {
						log.error("Failed to invoke value() on annotation " + a.annotationType().getName(), e);
					}
				});
			});
		});

		BuildManagerV2.get().deployBuilds(buildsToBeBuild);
	}

	private static void cleanProject() {
		if (projectHandler != null) {
			projectHandler.saveEventsLog();
			projectHandler.cleanup();
		}
	}

	@Override
	protected List<Runner> getChildren() {
		return testRunners;
	}

	@Override
	protected Description describeChild(final Runner child) {
		return child.getDescription();
	}

	@Override
	protected void runChild(final Runner runner, final RunNotifier notifier) {
		runner.run(notifier);
	}

	@Override
	@SneakyThrows(NoSuchMethodException.class)
	protected Statement withBeforeClasses(Statement statement) {
		final FrameworkMethod fm = new FrameworkMethod(XTFTestSuite.class.getDeclaredMethod("beforeSuite"));
		return new RunBefores(statement, join(fm, getTestClass().getAnnotatedMethods(BeforeClass.class), true), null);
	}

	@Override
	@SneakyThrows(NoSuchMethodException.class)
	protected Statement withAfterClasses(Statement statement) {
		final FrameworkMethod fm = new FrameworkMethod(XTFTestSuite.class.getDeclaredMethod("afterSuite"));
		return new RunAfters(statement, join(fm, getTestClass().getAnnotatedMethods(AfterClass.class), false), null);
	}
}
