package cz.xtf;

import org.apache.commons.io.FileUtils;
import org.apache.http.entity.ContentType;

import cz.xtf.git.GitLabUtil;
import cz.xtf.http.HttpClient;
import cz.xtf.junit.annotation.release.SinceVersion;
import cz.xtf.junit.annotation.release.SkipFor;
import cz.xtf.openshift.OpenshiftUtil;
import cz.xtf.openshift.PodService;
import cz.xtf.openshift.imagestream.ImageRegistry;
import cz.xtf.time.TimeUtil;

import javax.swing.JOptionPane;

import org.assertj.core.api.JUnitSoftAssertions;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Toolkit;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TestParent {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestParent.class);
	private static String currentTestClass = null;
	
	@Rule
	public TestName name = new TestName();

	@Rule
	public TestWatcher testWatcher = new LoggingTestWatcher();

	@Rule
	public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

	@Before
	public void setCurrentTestClass() {
		currentTestClass = this.getClass().getName();
	}

	public static String getCurrentTestClass() {
		return (currentTestClass == null) ? "UNSET" : currentTestClass;
	}

	/**
	 * Method to get the current image as an image property used in @SinceVersion(image='eap'... or @SkipFor(image='$getImage')
	 * @param image either "foo", for an ImageRegistry method name, or "$foo", for the current test class method name "foo"
	 * @return
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalAccessException
	 */
	private String getRegistryOrInstanceImage(String image) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		if (image.startsWith("$")) {
			// special case, we get the image by a method call
			return (String)this.getClass().getMethod(image.replace("$", "")).invoke(this);
		}
		else {
			return (String) ImageRegistry.class.getDeclaredMethod(image).invoke(ImageRegistry.get());
		}
	}

	@Before
	public void skipIfSinceVersionAnnotation() {
		for (final Method method : this.getClass().getMethods()) {
			if (method.getName().equals(name.getMethodName())) {
				final SinceVersion[] sinceVersions = method.getAnnotationsByType(SinceVersion.class);
				for (final SinceVersion sinceVersion : sinceVersions) {
					try {
						final String image = getRegistryOrInstanceImage(sinceVersion.image());

						if (image.contains(sinceVersion.name())) {
							final String sinceVersionVersion = sinceVersion.since().split("-")[0];
							if (!ImageRegistry.isVersionAtLeast(new BigDecimal(sinceVersionVersion), image)) {
								if (!sinceVersion.jira().isEmpty()) {
									Assume.assumeTrue(sinceVersion.jira() + ", Image " + image + " (" + sinceVersion.image() + ") stream containing " + sinceVersion.name() + " must be of version at least " + sinceVersionVersion, false);
								}
								else {
									Assume.assumeTrue("Image " + image + " (" + sinceVersion.image() + ") stream containing " + sinceVersion.name() + " must be of version at least " + sinceVersionVersion, false);
								}
							}
						}
					} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
						log.error("Error invoking during @SinceVersion processing", e);
					}
				}

				final SkipFor[] skipFors = method.getAnnotationsByType(SkipFor.class);
				for (final SkipFor skipFor : skipFors) {
					try {
						final String image = getRegistryOrInstanceImage(skipFor.image());

						if (image.contains(skipFor.name())) {
							if (!skipFor.reason().isEmpty()) {
								Assume.assumeTrue("Skipping: " + skipFor.reason() + " - image " + image + " (" + skipFor.image() + ") stream containing " + skipFor.name(), false);
							}
							else {
								Assume.assumeTrue("Skipping - image " + image + " (" + skipFor.image() + ") stream containing " + skipFor.name(), false);
							}
						}
					} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
						log.error("Error invoking during @SkipFor processing", e);
					}
				}
			}
		}
	}

	@AfterClass
	public static void cleanupNamespace() {
		OpenshiftUtil.getInstance().cleanProject(true);
//		OpenshiftUtil.getInstance().cleanImages(true);
	}

	@After
	public void gatherJacocoExecsFromPods() {
		if (TestConfiguration.jacoco()) {
			// go through all pods with the "jacoco" label
			final OpenshiftUtil openshift = OpenshiftUtil.getInstance();
			openshift.withDefaultUser(c -> c.pods().withLabel("jacoco").list().getItems()).stream().forEach(pod -> {
				// Try to read jolokia jacoco property

				String jolokiaGetSessionUrl = String.format("%s/api/v1/namespaces/%s/pods/https:%s:8778/proxy/jolokia/read/org.jacoco:type=Runtime/SessionId",
						TestConfiguration.masterUrl(), openshift.getContext().getNamespace(), pod.getMetadata().getName());

				String jolokiaPostUrl = String.format("%s/api/v1/namespaces/%s/pods/https:%s:8778/proxy/jolokia/",
						TestConfiguration.masterUrl(), openshift.getContext().getNamespace(), pod.getMetadata().getName());

				try {
					String sessionId = HttpClient.get(jolokiaGetSessionUrl).bearerAuth(openshift.getContext().getToken()).response();

					int responseCode = HttpClient.post(jolokiaPostUrl).data("{\"type\":\"exec\",\"mbean\":\"org.jacoco:type=Runtime\",\"operation\":\"dump(boolean)\",\"arguments\":[false]}", ContentType.APPLICATION_JSON).bearerAuth(openshift.getContext().getToken()).code();

					if (responseCode == 200) {
						PodService ps = new PodService(pod);

						Path testPodDir = Paths.get("tmp", "jacoco", getCurrentTestClass(), pod.getMetadata().getName());
						testPodDir.toFile().mkdirs();

						ps.execAndConsume(istream -> {
							FileUtils.copyInputStreamToFile(istream, testPodDir.resolve(name.getMethodName() + ".jacoco.exec").toFile());
						}, "cat", "/tmp/jacoco.exec");
					}
					else {
						log.error("Error calling jacoco dump on {}", pod.getMetadata().getName());
					}

				} catch (IOException e) {
					log.error("Cannot connect to jolokia on pod " + pod.getMetadata().getName() + " during " + name, e);
				}
			});
		}
	}

	/**
	 * This method is deprecated and will be removed, please switch to {@link cz.xtf.build.Project#findApplicationDirectory(String, String, String)}
	 */
	@Deprecated
	public static Path findApplicationDirectory(String moduleName, String appName, String subModuleName) {
		// Tests are started either from the parent directory or model directory
		Path path;
		path = FileSystems.getDefault().getPath("src/test/resources/apps", appName);

		/* We only return this path if the absolute path contains the moduleName,
		    e.g. if both  test-eap and test-common contain "foo", but we explicitly want
		    the test-common/src/test/resources/apps/foo
		  */
		if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
			return path;
		}
		LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		if (subModuleName != null) {
			path = FileSystems.getDefault().getPath("src/test/resources/apps/" + subModuleName, appName);
			if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
				return path;
			}
			LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		}
		path = FileSystems.getDefault().getPath(moduleName + "/src/test/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		if (subModuleName != null) {
			path = FileSystems.getDefault().getPath(moduleName + "/src/test/resources/apps/" + subModuleName, appName);
			if (Files.exists(path) && path.toAbsolutePath().toString().contains(moduleName)) {
				return path;
			}
			LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		}
		path = FileSystems.getDefault().getPath("../" + moduleName + "/src/main/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		path = FileSystems.getDefault().getPath("../" + moduleName + "/src/test/resources/apps", appName);
		if (Files.exists(path)) {
			return path;
		}
		LOGGER.info("Path {} does not exist", path.toAbsolutePath());
		throw new IllegalArgumentException("Cannot find directory with STI app sources");
	}

	@Deprecated
	public static Path findApplicationDirectory(String moduleName, String appName) {
		return findApplicationDirectory(moduleName, appName, null);
	}

	public static Path findModuleDirectory(String moduleName) {
		Path path;
		path = FileSystems.getDefault().getPath("..", moduleName);
		return path;
	}

	private static final class LoggingTestWatcher extends TestWatcher {
		private Map<String, Long> times = new HashMap<>();

		protected void starting(org.junit.runner.Description description) {
			LOGGER.info(" *** Test {} is starting *** ", getTestId(description));
			times.put(getTestId(description), System.currentTimeMillis());
		}

		protected void succeeded(org.junit.runner.Description description) {
			long testTime = System.currentTimeMillis() - times.get(getTestId(description));
			LOGGER.info(" *** Test {} succeeded after {} *** ", getTestId(description), TimeUtil.millisToString(testTime));
		}

		protected void failed(Throwable e, org.junit.runner.Description description) {
			long testTime = System.currentTimeMillis() - times.get(getTestId(description));
			LOGGER.warn(" *** Test {} failed after {} *** ", getTestId(description), TimeUtil.millisToString(testTime));
		}

		protected void skipped(org.junit.AssumptionViolatedException e, Description description) {
			long testTime = System.currentTimeMillis() - times.get(getTestId(description));
			LOGGER.warn(" *** Test {} skipped after {} *** ", getTestId(description), TimeUtil.millisToString(testTime));
		}

		private String getTestId(org.junit.runner.Description description) {
			return description.getClassName() + "." + description.getMethodName();
		}
	}

	@AfterClass
	public static void alert() {
		if (TestConfiguration.testAlert()) {
			Toolkit.getDefaultToolkit().beep();
			JOptionPane.showMessageDialog(null, "Test completed");
		}
	}

	private static GitLabUtil GITLAB;
	public static synchronized GitLabUtil gitlab() {
		if (GITLAB == null) {
			GITLAB = new GitLabUtil();
		}

		return GITLAB;
	}
}
