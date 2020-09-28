package cz.xtf.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads properties stored in several different ways. Possible options, with top to down overriding are: </br>
 *
 * <ul>
 * <li><i>global-test.properties</i> on project root path</li>
 * <li><i>test.properties</i> on project root path - meant to be user specific, unshared</li>
 * <li><i>environment variables</i> in System.getEnv()</li>
 * <li><i>system properties</i> in System.getProperties()</li>
 * </ul>
 */
@Slf4j
public final class XTFConfig {
    // Replace with common method for finding once available in core
    private static final Path testPropertiesPath;
    private static final Path globalPropertiesPath;

    private static final Properties properties = new Properties();

    private static final String TEST_PROPERTIES_PATH = "xtf.test_properties.path";
    private static final String GLOBAL_TEST_PROPERTIES_PATH = "xtf.global_test_properties.path";

    // Pre-loading
    static {
        globalPropertiesPath = resolvePropertiesPath(System.getProperty(GLOBAL_TEST_PROPERTIES_PATH, "global-test.properties"));
        testPropertiesPath = resolvePropertiesPath(System.getProperty(TEST_PROPERTIES_PATH, "test.properties"));
        properties.putAll(XTFConfig.getPropertiesFromPath(globalPropertiesPath));
        properties.putAll(XTFConfig.getPropertiesFromPath(testPropertiesPath));
        properties.putAll(System.getenv().entrySet().stream()
                .collect(Collectors.toMap(e -> "xtf." + e.getKey().replaceAll("_", ".").toLowerCase(), Map.Entry::getValue)));
        properties.putAll(System.getProperties());

        // Set new values based on old properties if new are not set
        BackwardCompatibility.updateProperties();
    }

    public static String get(String property) {
        return properties.getProperty(property);
    }

    public static String get(String property, String fallbackValue) {
        return properties.getProperty(property, fallbackValue);
    }

    static void setProperty(String property, String value) {
        properties.setProperty(property, value);
    }

    private static Path getProjectRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir.getParent().resolve("pom.xml").toFile().exists())
            dir = dir.getParent();
        return dir;
    }

    private static Path resolvePropertiesPath(String path) {
        if (Paths.get(path).toFile().exists()) {
            return Paths.get(path);
        }
        return getProjectRoot().resolve(path);
    }

    private static Properties getPropertiesFromPath(Path path) {
        Properties properties = new Properties();

        if (Files.isReadable(path)) {
            try (InputStream is = Files.newInputStream(path)) {
                properties.load(is);
            } catch (final IOException ex) {
                log.warn("Unable to read properties from '{}'", path, ex);
            }
        }

        return properties;
    }
}
