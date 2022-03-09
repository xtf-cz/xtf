package cz.xtf.junit5.extensions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.config.XTFConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.core.service.logs.streaming.AnnotationBasedServiceLogsConfigurations;
import cz.xtf.core.service.logs.streaming.ServiceLogs;
import cz.xtf.core.service.logs.streaming.ServiceLogsSettings;
import cz.xtf.core.service.logs.streaming.SystemPropertyBasedServiceLogsConfigurations;
import cz.xtf.core.service.logs.streaming.k8s.PodLogs;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements {@link BeforeAllCallback} in order to provide the logic to retrieve the Service Logs Streaming
 * configurations and activating concrete {@link ServiceLogs} instances.
 *
 * Workflow:<br>
 * <ul>
 * <li>Check whether the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_ENABLED}
 * is set and apply a configuration that will enable Service Logs Streaming for all the test classes in such a
 * case</li>
 * <li>If {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_ENABLED} is not set, then check
 * whether the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_CONFIG} is set</li>
 * <li>If the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_CONFIG} is set, create an
 * {@link SystemPropertyBasedServiceLogsConfigurations} instance for the property value and store it for the
 * current execution context</li>
 * <li>If the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_CONFIG} is not set as well, create a
 * {@link AnnotationBasedServiceLogsConfigurations} and store it for the current execution context</li>
 * <li>Extract a unique {@link ServiceLogsSettings} instance out of the relevant Service Logs Streaming
 * configurations, for it to be used by the current execution context test class</li>
 * <li>Create a {@link PodLogs} instance that implements the {@link ServiceLogs} interface for a k8s based
 * Cloud environment</li><
 * <li>Call {@link ServiceLogs#start()} to start the {@link PodLogs} for the current execution context test
 * class</li>
 * </ul>
 */
@Slf4j
public class ServiceLogsStreamingRunner implements BeforeAllCallback, AfterAllCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create("cz", "xtf", "junit",
            "extensions", "ServiceLogsRunner");

    private static final String SERVICE_LOGS_PROPERTY_BASED_CONFIGURATIONS = "SERVICE_LOGS:PROPERTY_BASED_CONFIGURATIONS";
    private static final String SERVICE_LOGS_ANNOTATION_BASED_CONFIGURATIONS = "SERVICE_LOGS:ANNOTATION_BASED_CONFIGURATIONS";
    private static final String SERVICE_LOGS_OUTPUT_STREAMS = "SERVICE_LOGS:OUTPUT_STREAMS";
    private static final String SERVICE_LOGS = "SERVICE_LOGS";

    // Service Logs Streaming (SLS)
    private static final String SERVICE_LOGS_STREAMING_PROPERTY_CONFIG = "xtf.log.streaming.config";
    private static final String SERVICE_LOGS_STREAMING_PROPERTY_ENABLED = "xtf.log.streaming.enabled";

    @Override
    public void beforeAll(ExtensionContext context) {
        // if the xtf.log.streaming.enabled property is set, then we'll enable SLS for all tests
        if (getServiceLogsStreamingEnabledPropertyValue()) {
            enableServiceLogsStreamingForAllTests(context);
        }
        startServiceLogsStreaming(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stopServiceLogsStreaming(context);
    }

    /**
     * Set the config for starting Service Logs Streaming for all tests
     *
     * @param extensionContext The current test execution context
     */
    private void enableServiceLogsStreamingForAllTests(ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String key = SERVICE_LOGS_PROPERTY_BASED_CONFIGURATIONS;
        SystemPropertyBasedServiceLogsConfigurations systemPropertyBasedServiceLogsConfigurations = store.get(key,
                SystemPropertyBasedServiceLogsConfigurations.class);
        if (systemPropertyBasedServiceLogsConfigurations != null) {
            store.remove(key);
        }
        store.put(key, new SystemPropertyBasedServiceLogsConfigurations("target=.*"));
    }

    /**
     * Access the execution context <i>system property based</i> Service Logs Streaming configurations
     *
     * @param extensionContext The current test execution context
     * @return An instance of {@link SystemPropertyBasedServiceLogsConfigurations}, representing all the
     *         <i>system property based</i> Service Logs Streaming configurations
     */
    private SystemPropertyBasedServiceLogsConfigurations systemPropertyBasedServiceLogsConfigurations(
            ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String key = SERVICE_LOGS_PROPERTY_BASED_CONFIGURATIONS;
        SystemPropertyBasedServiceLogsConfigurations systemPropertyBasedServiceLogsConfigurations = store.get(key,
                SystemPropertyBasedServiceLogsConfigurations.class);
        if (systemPropertyBasedServiceLogsConfigurations == null) {
            final String serviceLogsStreamingConfig = getServiceLogsStreamingConfigPropertyValue();
            // if the property is not set, then there will be no configurations at all
            if ((serviceLogsStreamingConfig != null)
                    && (!serviceLogsStreamingConfig.isEmpty())) {
                systemPropertyBasedServiceLogsConfigurations = new SystemPropertyBasedServiceLogsConfigurations(
                        serviceLogsStreamingConfig);
                store.put(key, systemPropertyBasedServiceLogsConfigurations);
            }
        }
        return systemPropertyBasedServiceLogsConfigurations;
    }

    /**
     * Access the execution context <i>annotation based</i> Service Logs Streaming configurations
     *
     * @param extensionContext The current test execution context
     * @return An instance of {@link AnnotationBasedServiceLogsConfigurations}, representing all the
     *         <i>annotation based</i> Service Logs Streaming configurations
     */
    private AnnotationBasedServiceLogsConfigurations annotationBasedServiceLogsConfigurations(
            ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String key = SERVICE_LOGS_ANNOTATION_BASED_CONFIGURATIONS;
        AnnotationBasedServiceLogsConfigurations annotationBasedServiceLogsConfigurations = store.get(key,
                AnnotationBasedServiceLogsConfigurations.class);
        if (annotationBasedServiceLogsConfigurations == null) {
            annotationBasedServiceLogsConfigurations = new AnnotationBasedServiceLogsConfigurations();
            store.put(key, annotationBasedServiceLogsConfigurations);
        }
        return annotationBasedServiceLogsConfigurations;
    }

    /**
     * Access the execution context test classes {@link ServiceLogs} related instances
     *
     * @param extensionContext The current test execution context
     * @return A {@link Map} containing test classes keys and related {@link ServiceLogs} instances
     */
    private Map<String, ServiceLogs> serviceLogs(ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String key = SERVICE_LOGS;
        Map<String, ServiceLogs> serviceLogsMap = store.get(key, Map.class);
        if (serviceLogsMap == null) {
            serviceLogsMap = new HashMap<>();
            store.put(key, serviceLogsMap);
        }
        return serviceLogsMap;
    }

    /**
     * Access the execution context {@link OutputStream} instances that represent the output of existing
     * {@link ServiceLogs} instances
     * 
     * @param extensionContext The current test execution context
     * @return A list of {@link OutputStream} instances that represent the output of existing {@link ServiceLogs}
     *         instances
     */
    private List<OutputStream> serviceLogsOutputStreams(ExtensionContext extensionContext) {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String key = SERVICE_LOGS_OUTPUT_STREAMS;
        List<OutputStream> serviceLogsOutputStreams = store.get(key, List.class);
        if (serviceLogsOutputStreams == null) {
            serviceLogsOutputStreams = new ArrayList<>();
            store.put(key, serviceLogsOutputStreams);
        }
        return serviceLogsOutputStreams;
    }

    /**
     * Read the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_CONFIG} property value
     *
     * @return A string representing the Service Logs Streaming configuration, i.e. a list of configuration
     *         items, each one storing attributes and their values
     */
    private String getServiceLogsStreamingConfigPropertyValue() {
        return XTFConfig.get(SERVICE_LOGS_STREAMING_PROPERTY_CONFIG, "");
    }

    /**
     * Read the {@link ServiceLogsStreamingRunner#SERVICE_LOGS_STREAMING_PROPERTY_ENABLED} property value
     *
     * @return True, if the property value equals "true" (case insensitive), false otherwise
     */
    private boolean getServiceLogsStreamingEnabledPropertyValue() {
        return Boolean.parseBoolean(XTFConfig.get(SERVICE_LOGS_STREAMING_PROPERTY_ENABLED, "false"));
    }

    /**
     * Start Service Logs Streaming after creating a concrete {@link ServiceLogs} instance based on current
     * configuration settings
     *
     * @param extensionContext The current test execution context
     */
    private void startServiceLogsStreaming(ExtensionContext extensionContext) {
        ServiceLogsSettings currentClazzServiceLogsSettings = retrieveServiceLogsSettings(extensionContext);
        if (currentClazzServiceLogsSettings != null) {
            // start the service logs, which is lazily initialized and added to the extension context store
            ServiceLogs serviceLogs = createServiceLogs(extensionContext, currentClazzServiceLogsSettings);
            serviceLogs(extensionContext).put(extensionContext.getRequiredTestClass().getName(), serviceLogs);
            serviceLogs.start();
        }
    }

    /**
     * Look for a valid system property based configuration for the given execution context, or search for an
     * annotation based one if it doesn't exist.
     *
     * @param extensionContext The current test execution context
     * @return A {@link ServiceLogsSettings} instance representing the actual configuration for a {@link ServiceLogs}
     *         instance to be created for a given execution context
     */
    private ServiceLogsSettings retrieveServiceLogsSettings(ExtensionContext extensionContext) {
        if (systemPropertyBasedServiceLogsConfigurations(extensionContext) != null) {
            ServiceLogsSettings currentClazzServiceLogsSettings = systemPropertyBasedServiceLogsConfigurations(extensionContext)
                    .forClass(
                            extensionContext.getRequiredTestClass());
            if (currentClazzServiceLogsSettings != null) {
                return currentClazzServiceLogsSettings;
            }
        }
        return annotationBasedServiceLogsConfigurations(extensionContext).forClass(
                extensionContext.getRequiredTestClass());
    }

    /**
     * Creates a concrete {@link ServiceLogs} instance to manage service logs streaming for the current environment
     *
     * @param extensionContext The current test execution context
     * @param testClazzServiceLogsSettings The settings to be used in order to create a concrete {@link ServiceLogs}
     *        instance for a given test class
     * @return A concrete {@link ServiceLogs} instance for a given test class
     */
    private ServiceLogs createServiceLogs(ExtensionContext extensionContext,
            ServiceLogsSettings testClazzServiceLogsSettings) {
        log.debug("createServiceLogs");
        ServiceLogs serviceLogs;

        // namespaces
        OpenShift openShift = OpenShifts.master();
        final String masterNamespace = openShift.getNamespace();
        final String buildsNamespace = BuildManagers.get().openShift().getNamespace();
        final List<String> uniqueNamespaces = new ArrayList<>();
        uniqueNamespaces.add(masterNamespace);
        if (!masterNamespace.equals(buildsNamespace)) {
            uniqueNamespaces.add(buildsNamespace);
        }
        // output defaults to System.out
        PrintStream out = System.out;
        if (!ServiceLogsSettings.UNASSIGNED.equals(testClazzServiceLogsSettings.getOutputPath())) {
            try {
                // in case there's output stream to be used, let's use it and keep track of it,
                // so that it can be closed eventually
                final File baseOutputPath = new File(testClazzServiceLogsSettings.getOutputPath());
                if (!baseOutputPath.exists() && !baseOutputPath.mkdirs()) {
                    throw new IllegalStateException(
                            "Cannot create SLS base output path: " + testClazzServiceLogsSettings.getOutputPath());
                }
                File outputFile = new File(testClazzServiceLogsSettings.getOutputPath(),
                        extensionContext.getRequiredTestClass().getName());
                out = new PrintStream(new FileOutputStream(outputFile));
                serviceLogsOutputStreams(extensionContext).add(out);
            } catch (FileNotFoundException e) {
                log.warn("Could not create file stream {} because of the following error: {}. Redirecting to System.out",
                        testClazzServiceLogsSettings.getOutputPath(), e.getMessage());
            }
        }
        serviceLogs = new PodLogs.Builder()
                .withClient(openShift)
                .inNamespaces(uniqueNamespaces)
                // The life cycle of the PrintStream instance that is being passed on to ServiceLogs
                // must be handled in the outer scope, which is not (usually) here.
                .outputTo(out)
                .filter(ServiceLogsSettings.UNASSIGNED.equals(testClazzServiceLogsSettings.getFilter()) ? null
                        : testClazzServiceLogsSettings.getFilter())
                .build();

        return serviceLogs;
    }

    private void stopServiceLogsStreaming(ExtensionContext extensionContext) {
        // stop all service logs
        for (ServiceLogs serviceLogs : serviceLogs(extensionContext).values()) {
            serviceLogs.stop();
        }
        // ... and any used output stream used by service logging
        for (OutputStream outputStream : serviceLogsOutputStreams(extensionContext)) {
            try {
                outputStream.close();
            } catch (IOException e) {
                log.warn("Couldn't close Print Stream due to IOException: {}", e.getMessage());
            }
        }
    }
}
