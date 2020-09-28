package cz.xtf.junit5.extensions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.event.helpers.EventHelper;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.OpenShiftRecorder;
import cz.xtf.junit5.config.JUnitConfig;
import cz.xtf.junit5.extensions.helpers.ResourcesPrinterHelper;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.Route;

/**
 * Record OpenShift isolated state relative to a test.
 * Resources are filtered by name provided via {@link OpenShiftRecorder} annotation. Names are turned into regexes
 * by adding {@code .*} as a suffix. If no name is provided, all resources in namespaces (BM and normal) are considered;
 * regex is {@code .*}.
 * <p>
 * Recorded resources:
 * <ul>
 * <li>pods states</li>
 * <li>deployment configs states</li>
 * <li>builds states</li>
 * <li>build configs states</li>
 * <li>image streams states</li>
 * <li>stateful sets states</li>
 * <li>routes states</li>
 * <li>services states</li>
 * <li>secrets states</li>
 * <li>logs of pods</li>
 * <li>events</li>
 * </ul>
 * <p>
 * OpenShift state is recorded when a test throws an exception. If {@link JUnitConfig#RECORD_ALWAYS} is true, state is
 * recorded also when a test passes.
 * <p>
 * Use {@link JUnitConfig#RECORD_DIR} to set the direction of records.
 */
public class OpenShiftRecorderHandler
        implements TestWatcher, TestExecutionExceptionHandler, BeforeAllCallback, LifecycleMethodExecutionExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(OpenShiftRecorderHandler.class);

    private static final String TRACK_FROM = "TRACK_FROM";

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        getClassStore(extensionContext).put(TRACK_FROM, EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch());
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        try {
            recordState(context);
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void handleBeforeAllMethodExecutionException(final ExtensionContext context, final Throwable throwable)
            throws Throwable {
        try {
            if (JUnitConfig.recordBefore()) {
                recordState(context);
            }
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void handleBeforeEachMethodExecutionException(final ExtensionContext context, final Throwable throwable)
            throws Throwable {
        try {
            if (JUnitConfig.recordBefore()) {
                recordState(context);
            }
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        if (JUnitConfig.recordAlways()) {
            try {
                recordState(context);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void recordState(ExtensionContext context) throws IOException {
        OpenShiftRecorder classOpenShiftRecorder = AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), OpenShiftRecorder.class).orElse(null);
        OpenShiftRecorder methodOpenShiftRecorder = AnnotationSupport
                .findAnnotation(context.getElement(), OpenShiftRecorder.class).orElse(null);
        final OpenShiftRecorder openShiftRecorder = methodOpenShiftRecorder != null ? methodOpenShiftRecorder
                : classOpenShiftRecorder;
        // annotation or global include in META-INF.services (empty string will be turned into .* and no resource shall be filtered out)
        String[] objNames = openShiftRecorder != null ? openShiftRecorder.resourceNames() : new String[] { "" };

        savePods(context, objNames);
        saveDCs(context, objNames);
        saveBuilds(context, objNames);
        saveBCs(context, objNames);
        saveISs(context, objNames);
        saveStatefulsets(context, objNames);
        saveRoutes(context, objNames);
        saveServices(context, objNames);
        saveSecrets(context, objNames);
        savePodLogs(context, objNames);
        saveEvents(context, objNames);

    }

    private void saveStatefulsets(ExtensionContext context, String[] objNames) throws IOException {
        final Path StatefulSetsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "statefulSets.log");
        try (final ResourcesPrinterHelper<StatefulSet> printer = ResourcesPrinterHelper.forStatefulSet(StatefulSetsLogPath)) {
            OpenShifts.master().getStatefulSets().stream()
                    .filter(build -> resourceNameMatches(build.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
    }

    private void saveISs(ExtensionContext context, String[] objNames) throws IOException {
        // master namespace
        final Path imageStreamsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "imageStreams-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<ImageStream> printer = ResourcesPrinterHelper.forISs(imageStreamsMasterLogPath)) {
            OpenShifts.master().getImageStreams().stream()
                    .filter(build -> resourceNameMatches(build.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace())) {
            final Path imageStreamsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "imageStreams-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<ImageStream> printer = ResourcesPrinterHelper.forISs(imageStreamsBMLogPath)) {
                BuildManagers.get().openShift().getImageStreams().stream()
                        .filter(build -> resourceNameMatches(build.getMetadata().getName(), objNames))
                        .forEach(printer::row);
            }
        }
    }

    private void saveBCs(ExtensionContext context, String[] objNames) throws IOException {
        final Path buildsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "buildConfigs.log");
        try (final ResourcesPrinterHelper<BuildConfig> printer = ResourcesPrinterHelper.forBCs(buildsLogPath)) {
            BuildManagers.get().openShift().getBuildConfigs().stream()
                    .filter(bc -> resourceNameMatches(bc.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
    }

    private void saveBuilds(ExtensionContext context, String[] objNames) throws IOException {
        // master namespace
        final Path buildsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "builds-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Build> printer = ResourcesPrinterHelper.forBuilds(buildsMasterLogPath)) {
            OpenShifts.master().getBuilds().stream()
                    .filter(build -> resourceNameMatches(build.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace())) {
            final Path buildsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "builds-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Build> printer = ResourcesPrinterHelper.forBuilds(buildsBMLogPath)) {
                BuildManagers.get().openShift().getBuilds().stream()
                        .filter(build -> resourceNameMatches(build.getMetadata().getName(), objNames))
                        .forEach(printer::row);
            }
        }
    }

    private void saveSecrets(ExtensionContext context, String[] objNames) throws IOException {
        final Path secretsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "secrets.log");
        try (final ResourcesPrinterHelper<Secret> printer = ResourcesPrinterHelper.forSecrets(secretsLogPath)) {
            OpenShifts.master().getSecrets()
                    .forEach(printer::row);
        }
    }

    private void saveServices(ExtensionContext context, String[] objNames) throws IOException {
        final Path servicesLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "services.log");
        try (final ResourcesPrinterHelper<Service> printer = ResourcesPrinterHelper.forServices(servicesLogPath)) {
            OpenShifts.master().getServices().stream()
                    .filter(service -> resourceNameMatches(service.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
    }

    private void saveRoutes(ExtensionContext context, String[] objNames) throws IOException {
        final Path routesLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "routes.log");
        try (final ResourcesPrinterHelper<Route> printer = ResourcesPrinterHelper.forRoutes(routesLogPath)) {
            OpenShifts.master().getRoutes().stream()
                    .filter(route -> resourceNameMatches(route.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
    }

    private void savePods(ExtensionContext context, String[] objNames) throws IOException {
        // master namespace
        final Path podsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "pods-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Pod> printer = ResourcesPrinterHelper.forPods(podsMasterLogPath)) {
            OpenShifts.master().getPods()
                    .stream()
                    .filter(pod -> resourceNameMatches(pod.getMetadata().getName(), objNames))
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace())) {
            final Path eventsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "pods-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Pod> printer = ResourcesPrinterHelper.forPods(eventsBMLogPath)) {
                BuildManagers.get().openShift().getPods()
                        .stream()
                        .filter(pod -> resourceNameMatches(pod.getMetadata().getName(), objNames))
                        .forEach(printer::row);
            }
        }
    }

    private void saveDCs(ExtensionContext context, String[] objNames) throws IOException {
        final Path dcsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "deploymentConfigs.log");
        try (final ResourcesPrinterHelper<DeploymentConfig> printer = ResourcesPrinterHelper.forDCs(dcsLogPath)) {
            OpenShifts.master().getDeploymentConfigs().stream()
                    .filter(dc -> resourceNameMatches(dc.getMetadata().getName(), objNames))
                    .forEach(printer::row);

        }
    }

    private Store getClassStore(ExtensionContext extensionContext) {
        return extensionContext.getStore(Namespace.create(extensionContext.getRequiredTestClass()));
    }

    private void savePodLogs(ExtensionContext context, String[] resourceNames) {
        Consumer<OpenShift> podPrinter = openShift -> openShift.getPods()
                .stream()
                .filter(pod -> resourceNameMatches(pod.getMetadata().getName(), resourceNames))
                .forEach(pod -> {
                    try {
                        openShift.storePodLog(
                                pod,
                                Paths.get(attachmentsDir(), dirNameForTest(context)),
                                pod.getMetadata().getName() + ".log");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        podPrinter.accept(OpenShifts.master());
        if (!OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace())) {
            podPrinter.accept(BuildManagers.get().openShift());
        }
    }

    private String dirNameForTest(ExtensionContext context) {
        // if is test
        if (context.getTestMethod().isPresent()) {
            return context.getTestClass().get().getName() + "." + context.getTestMethod().get().getName();
        } else {
            return context.getTestClass().get().getName();
        }
    }

    private void saveEvents(ExtensionContext context, String[] resourceNames) throws IOException {
        Store classStore = getClassStore(context);
        BiConsumer<OpenShift, ResourcesPrinterHelper<Event>> eventPrinter = (openShift, printer) -> openShift.getEventList()
                .filter()
                .ofObjNames(getRegexResourceNames(resourceNames))
                .after(classStore.get(TRACK_FROM, ZonedDateTime.class))
                .getStream()
                .forEach(printer::row);

        // master namespace
        final Path eventsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "events-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Event> printer = ResourcesPrinterHelper.forEvents(eventsMasterLogPath)) {
            eventPrinter.accept(OpenShifts.master(), printer);
        }
        // builds namespace (if not same)
        if (!OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace())) {
            final Path eventsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "events-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Event> printer = ResourcesPrinterHelper.forEvents(eventsBMLogPath)) {
                eventPrinter.accept(BuildManagers.get().openShift(), printer);
            }
        }
    }

    private boolean resourceNameMatches(String resourceName, String[] resourceNamesLookup) {
        for (String resourceNameLookup : getRegexResourceNames(resourceNamesLookup)) {
            if (resourceName.matches(resourceNameLookup)) {
                return true;
            }
        }
        return false;
    }

    private String[] getRegexResourceNames(String[] resourceNames) {
        String[] result = new String[resourceNames.length];
        for (int i = 0; i < resourceNames.length; i++) {
            result[i] = resourceNames[i] + ".*";
        }
        return result;
    }

    private String attachmentsDir() {
        return JUnitConfig.recordDir() != null ? JUnitConfig.recordDir() : System.getProperty("user.dir");
    }
}
