package cz.xtf.manipulation;

import cz.xtf.TestConfiguration;
import cz.xtf.UsageRecorder;
import cz.xtf.http.HttpClient;
import cz.xtf.openshift.imagestream.ImageRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jboss.dmr.ModelNode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Environment info recorder.
 */
@Slf4j
public class Recorder {

	/**
	 * File to store used test properties.
	 */
	public static final String RUNTIME_PROPERTIES_FILE = "../used-test.properties";

	/**
	 * File to store used images for the test.
	 */
	public static final String RUNTIME_IMAGES_FILE = "../used-images.properties";

	private static final String VERSION_FALLBACK = "N/A";

	private Recorder() {
		// utility class, do not initialize
	}

	/**
	 * Records OpenShift and Kube version.
	 *
	 * @see TestConfiguration#storeConfiguration()
	 * @see #recordOpenShiftVersion()
	 * @see #recordKubeVersion()
	 */
	public static void recordEnvironmentVersions() {
		TestConfiguration.get().storeConfiguration();
		final String config = retrieveConfig();
		recordOpenShiftVersion(config);
		recordKubeVersion(config);
	}

	/**
	 * Records OpenShift version by {@link UsageRecorder#recordOpenShiftVersion(String)}.
	 */
	public static void recordOpenShiftVersion() {
		final String config = retrieveConfig();
		recordOpenShiftVersion(config);
	}

	/**
	 * Records Kube version by {@link UsageRecorder#recordKubernetesVersion(String)}.
	 */
	public static void recordKubeVersion() {
		final String config = retrieveConfig();
		recordKubeVersion(config);
	}

	/**
	 * Saves test configuration by {@link TestConfiguration#get()#storeTestConfiguration()} to
	 * {@link #RUNTIME_PROPERTIES_FILE}.
	 *
	 * @throws IOException if unable to store configuration
	 */
	public static void storeTestConfiguration() throws IOException {
		try (final FileWriter writer = new FileWriter(RUNTIME_PROPERTIES_FILE)) {
			TestConfiguration.get().storeConfigurationInFile(writer);
		}
	}

	/**
	 * Records used images in the test to {@link #RUNTIME_IMAGES_FILE} file.
	 *
	 * <p>
	 * Image is found by the specified {@code imageName} from {@link ImageRegistry}.
	 * </p>
	 *
	 * @param imageNames names of images to record
	 */
	public static void recordUsedImages(String... imageNames) {
		final Properties images = new Properties();
		log.info("action=record-images status=START imageNames={}", Arrays.toString(imageNames));
		try (final FileWriter writer = new FileWriter(RUNTIME_IMAGES_FILE)) {
			Stream.of(imageNames).forEach(name -> addImage(images, name));
			images.store(writer, "Images used in test");
			log.info("action=record-images status=FINISH imageNames={}", Arrays.toString(imageNames));
		} catch (Exception e) {
			log.info("action=record-images status=ERROR imageNames={}", Arrays.toString(imageNames), e);
		}
	}

	private static void addImage(final Properties images, final String imageName) {
		try {
			log.debug("action=record-images status=PROCESS image={}", imageName);
			final String image = (String) ImageRegistry.class.getDeclaredMethod(imageName).invoke(ImageRegistry.get());
			images.setProperty(imageName, image);
		} catch (ReflectiveOperationException e) {
			log.info("action=record-images status=ERROR image={}", imageName, e);
		}
	}

	private static String retrieveConfig() {
		log.info("action=retrieve-config.js status=START");
		try {
			final String config = HttpClient.get(TestConfiguration.masterUrl() + "/console/config.js").response();
			log.debug("Retrieved config.js: {}", config);
			log.info("action=retrieve-config.js status=FINISH");
			return config;
		} catch (IOException e) {
			log.info("action=retrieve-config.js status=ERROR", e);
		}
		return null;
	}

	private static void recordOpenShiftVersion(String config) {
		UsageRecorder.recordOpenShiftVersion(config != null ? getOpenShiftVersion(config) : VERSION_FALLBACK);
	}

	private static void recordKubeVersion(String config) {
		UsageRecorder.recordKubernetesVersion(config != null ? getKubeVersion(config) : VERSION_FALLBACK);
	}

	private static String getOpenShiftVersion(String config) {
		final Matcher oseMatcher = Pattern.compile("openshift: \"(v\\d+(.\\d+)+(-[a-zA-Z0-9]+)?)\"").matcher(config);
		return oseMatcher.find() ? oseMatcher.group(1) : VERSION_FALLBACK;
	}

	private static String getKubeVersion(String config) {
		Matcher kubeMatcher = Pattern.compile("kubernetes: \"(v\\d+(.\\d+)+(-[a-zA-Z0-9]+)?)\"").matcher(config);
		if (kubeMatcher.find()) {
			return kubeMatcher.group(1);
		}
		return getKubeDefaultVersion();
	}

	private static String getKubeDefaultVersion() {
		log.info("action=retrieve-kube-version status=START");
		try {
			final String version = HttpClient.get(TestConfiguration.masterUrl() + "/version").response();
			log.info("action=retrieve-kube-version status=FINISH");
			return ModelNode.fromJSONString(version).get("gitVersion").asString();
		} catch (IOException e) {
			log.info("action=retrieve-kube-version status=ERROR", e);
		}
		return VERSION_FALLBACK;
	}
}
