package cz.xtf.core.service.logs.streaming;

import org.junit.jupiter.api.Assertions;

/**
 * A simple helper class that performs repetitive tasks on behalf of test methods in the context of the
 * Service Logs Streaming feature testing.
 */
public class ServiceLogsConfigurationTestHelper {
    public static void verifyPerClassConfigurationSearchDoesNotExist(ServiceLogsSettings configuration,
            Class<?> testClazz) {
        Assertions.assertNull(configuration,
                String.format("The per-class SLS configuration for \"%s\" exists", testClazz.getSimpleName()));
    }

    public static void verifyPerClassConfigurationSearchExists(ServiceLogsSettings configuration, Class<?> testClazz) {
        Assertions.assertNotNull(configuration,
                String.format("The per-class SLS configuration for \"%s\" is null", testClazz.getSimpleName()));
    }
}
