package cz.xtf.core.service.logs.streaming;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@link ServiceLogsSettings} in order to provide the concrete logic to handle SLS configuration based
 * on the {@link ServiceLogsStreaming} annotation.
 */
@Slf4j
public class AnnotationBasedServiceLogsConfigurations {

    private static final Map<String, ServiceLogsSettings> CONFIGURATIONS = new HashMap<>();

    private ServiceLogsSettings loadConfiguration(Class<?> testClazz) {
        ServiceLogsStreaming annotation = testClazz.getAnnotation(ServiceLogsStreaming.class);
        if (annotation != null) {
            return new ServiceLogsSettings.Builder()
                    .withTarget(testClazz.getName())
                    .withFilter(annotation.filter())
                    .withOutputPath(annotation.output())
                    .build();
        }
        return null;
    }

    public ServiceLogsSettings forClass(Class<?> testClazz) {
        ServiceLogsSettings serviceLogsSettings = CONFIGURATIONS.get(testClazz.getName());
        if (serviceLogsSettings == null) {
            serviceLogsSettings = loadConfiguration(testClazz);
            if (serviceLogsSettings != null) {
                CONFIGURATIONS.put(testClazz.getName(), serviceLogsSettings);
            }
        }
        return serviceLogsSettings;
    }
}
