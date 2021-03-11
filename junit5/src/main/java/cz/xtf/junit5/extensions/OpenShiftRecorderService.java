package cz.xtf.junit5.extensions;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import cz.xtf.junit5.annotations.OpenShiftRecorder;
import cz.xtf.junit5.config.JUnitConfig;
import cz.xtf.junit5.extensions.helpers.EventsFilterBuilder;
import cz.xtf.junit5.extensions.helpers.ResourcesFilterBuilder;
import cz.xtf.junit5.extensions.helpers.ResourcesPrinterHelper;
import cz.xtf.junit5.extensions.helpers.ResourcesTimestampHelper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
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
 * Provides an API and implementation for setting filtered resources and recording their state upon request.
 *
 * This service class can typically be used by classes that implement JUnit extension lifecycle interfaces,
 * e.g.: {@link org.junit.jupiter.api.extension.TestWatcher}, {@link org.junit.jupiter.api.extension.BeforeAllCallback}
 * etc.
 *
 * One example of such classes is {@link OpenShiftRecorderHandler} which initializes (and updates) filters or
 * records OCP state when handling different JUnit events.
 *
 * State recording is about downloading several resources logs from OCP.
 *
 * Resources are filtered by name provided via {@link OpenShiftRecorder} annotation. Names are turned into regexes
 * by adding {@code .*} as a suffix. If no name is provided, resources in namespaces (BM and master) are filtered
 * automatically by recording which resources are seen before test and so on.
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
 * <li>logs of builds</li>
 * <li>events</li>
 * </ul>
 * <p>
 * Use {@link JUnitConfig#recordDir()} ()} to set the directory of records.
 */
public class OpenShiftRecorderService {
    private static final Logger log = LoggerFactory.getLogger(OpenShiftRecorderService.class);

    private static final String FILTER_INITIALIZATION_DONE = "FILTERS_INITIALIZATION_DONE";
    private static final String POD_FILTER_MASTER = "POD_FILTER_MASTER";
    private static final String DC_FILTER_MASTER = "DC_FILTER_MASTER";
    private static final String BUILD_FILTER_MASTER = "BUILD_FILTER_MASTER";
    private static final String BC_FILTER_MASTER = "BC_FILTER_MASTER";
    private static final String IS_FILTER_MASTER = "IS_FILTER_MASTER";
    private static final String SS_FILTER_MASTER = "SS_FILTER_MASTER";
    private static final String ROUTE_FILTER_MASTER = "ROUTE_FILTER_MASTER";
    private static final String CONFIGMAP_FILTER_MASTER = "CONFIGMAP_FILTER_MASTER";
    private static final String SERVICE_FILTER_MASTER = "SERVICE_FILTER_MASTER";
    private static final String EVENT_FILTER_MASTER = "EVENT_FILTER_MASTER";
    private static final String POD_FILTER_BUILDS = "POD_FILTER_BUILDS";
    private static final String BUILD_FILTER_BUILDS = "BUILD_METHOD_FILTER_BUILDS";
    private static final String BC_FILTER_BUILDS = "BC_FILTER_BUILDS";
    private static final String IS_FILTER_BUILDS = "IS_FILTER_BUILDS";
    private static final String EVENT_FILTER_BUILDS = "EVENT_FILTER_BUILDS";

    /**
     * Initialize filters by collecting information OCP resources which are relevant for the current test execution
     * context (e.g.: called by a {@link org.junit.jupiter.api.extension.BeforeAllCallback#beforeAll(ExtensionContext)}
     * implementation
     *
     * @param context The test execution context
     */
    public void initFilters(ExtensionContext context) {
        ExtensionContext.Store classStore = getClassStore(context);
        OpenShift master = OpenShifts.master();
        OpenShift bm = BuildManagers.get().openShift();

        initClassFilter(context, POD_FILTER_MASTER, master, Pod.class);
        initClassFilter(context, DC_FILTER_MASTER, master, DeploymentConfig.class);
        initClassFilter(context, BUILD_FILTER_MASTER, master, Build.class);
        initClassFilter(context, BC_FILTER_MASTER, master, BuildConfig.class);
        initClassFilter(context, IS_FILTER_MASTER, master, ImageStream.class);
        initClassFilter(context, SS_FILTER_MASTER, master, StatefulSet.class);
        initClassFilter(context, ROUTE_FILTER_MASTER, master, Route.class);
        initClassFilter(context, CONFIGMAP_FILTER_MASTER, master, ConfigMap.class);
        initClassFilter(context, SERVICE_FILTER_MASTER, master, Service.class);
        classStore.put(EVENT_FILTER_MASTER,
                new EventsFilterBuilder().setExcludedUntil(ResourcesTimestampHelper.timeOfLastEvent(master)));

        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            initClassFilter(context, POD_FILTER_BUILDS, bm, Pod.class);
            initClassFilter(context, BUILD_FILTER_BUILDS, bm, Build.class);
            initClassFilter(context, BC_FILTER_BUILDS, bm, BuildConfig.class);
            initClassFilter(context, IS_FILTER_BUILDS, bm, ImageStream.class);
            classStore.put(EVENT_FILTER_BUILDS,
                    new EventsFilterBuilder().setExcludedUntil(ResourcesTimestampHelper.timeOfLastEvent(bm)));
        }

        setFiltersInitializationStatus(context, false);
    }

    /**
     * Update filters by adding information OCP resources which are relevant for the current test execution
     * context (e.g.: called by a {@link org.junit.jupiter.api.extension.BeforeEachCallback#beforeEach(ExtensionContext)}
     * implementation
     *
     * @param context The test execution context
     */
    public void updateFilters(ExtensionContext context) {
        ExtensionContext.Store classStore = getClassStore(context);
        OpenShift master = OpenShifts.master();
        OpenShift bm = BuildManagers.get().openShift();
        if (!isFilterInitializationComplete(context)) {
            // set filter in a way everything created between filters initialization and now will be captured - alwaysIncludedWindow
            // excluded until is set below

            //master
            updateClassFilter(context, POD_FILTER_MASTER, master, Pod.class);
            updateClassFilter(context, DC_FILTER_MASTER, master, DeploymentConfig.class);
            updateClassFilter(context, BUILD_FILTER_MASTER, master, Build.class);
            updateClassFilter(context, BC_FILTER_MASTER, master, BuildConfig.class);
            updateClassFilter(context, IS_FILTER_MASTER, master, ImageStream.class);
            updateClassFilter(context, SS_FILTER_MASTER, master, StatefulSet.class);
            updateClassFilter(context, ROUTE_FILTER_MASTER, master, Route.class);
            updateClassFilter(context, CONFIGMAP_FILTER_MASTER, master, ConfigMap.class);
            updateClassFilter(context, SERVICE_FILTER_MASTER, master, Service.class);
            updateClassFilter(context, EVENT_FILTER_MASTER, master, Event.class);

            // builds namespace (if not same)
            if (!isMasterAndBuildNamespaceSame()) {
                updateClassFilter(context, POD_FILTER_BUILDS, bm, Pod.class);
                updateClassFilter(context, BUILD_FILTER_BUILDS, bm, Build.class);
                updateClassFilter(context, BC_FILTER_BUILDS, bm, BuildConfig.class);
                updateClassFilter(context, IS_FILTER_BUILDS, bm, ImageStream.class);
                updateClassFilter(context, EVENT_FILTER_BUILDS, bm, Event.class);
            }

            setFiltersInitializationStatus(context, true);
        }

        // RESOURCE_FILTERs are set and now we are setting filter for specific tests
        // need to clone (shallow) it, since tests may run in a parallel way

        // master
        initMethodFilter(context, POD_FILTER_MASTER, master, Pod.class);
        initMethodFilter(context, DC_FILTER_MASTER, master, DeploymentConfig.class);
        initMethodFilter(context, BUILD_FILTER_MASTER, master, Build.class);
        initMethodFilter(context, BC_FILTER_MASTER, master, BuildConfig.class);
        initMethodFilter(context, IS_FILTER_MASTER, master, ImageStream.class);
        initMethodFilter(context, SS_FILTER_MASTER, master, StatefulSet.class);
        initMethodFilter(context, ROUTE_FILTER_MASTER, master, Route.class);
        initMethodFilter(context, CONFIGMAP_FILTER_MASTER, master, Route.class);
        initMethodFilter(context, SERVICE_FILTER_MASTER, master, Service.class);
        initMethodFilter(context, EVENT_FILTER_MASTER, master, Event.class);

        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            initMethodFilter(context, POD_FILTER_BUILDS, bm, Pod.class);
            initMethodFilter(context, BUILD_FILTER_BUILDS, bm, Build.class);
            initMethodFilter(context, BC_FILTER_BUILDS, bm, BuildConfig.class);
            initMethodFilter(context, IS_FILTER_BUILDS, bm, ImageStream.class);
            initMethodFilter(context, EVENT_FILTER_BUILDS, bm, Event.class);
        }
    }

    /**
     * Retrieves resources identified by filters
     */
    public void recordState(ExtensionContext context) throws IOException {
        savePods(context, getFilter(context, POD_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, POD_FILTER_BUILDS) : null);
        saveDCs(context, getFilter(context, DC_FILTER_MASTER));
        saveBuilds(context, getFilter(context, BUILD_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, BUILD_FILTER_BUILDS) : null);
        saveBCs(context, getFilter(context, BC_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, BC_FILTER_BUILDS) : null);
        saveISs(context, getFilter(context, IS_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, IS_FILTER_BUILDS) : null);
        saveStatefulSets(context, getFilter(context, SS_FILTER_MASTER));
        saveRoutes(context, getFilter(context, ROUTE_FILTER_MASTER));
        saveConfigMaps(context, getFilter(context, CONFIGMAP_FILTER_MASTER));
        saveServices(context, getFilter(context, SERVICE_FILTER_MASTER));
        saveSecrets(context);
        savePodLogs(context, getFilter(context, POD_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, POD_FILTER_BUILDS) : null);
        saveBuildLogs(context, getFilter(context, BUILD_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, BUILD_FILTER_MASTER) : null);
        saveEvents(context, getFilter(context, EVENT_FILTER_MASTER),
                !isMasterAndBuildNamespaceSame() ? getFilter(context, EVENT_FILTER_BUILDS) : null);
    }

    private boolean isFilterInitializationComplete(ExtensionContext context) {
        ExtensionContext.Store classStore = getClassStore(context);
        return classStore.get(FILTER_INITIALIZATION_DONE, AtomicBoolean.class).get();
    }

    private void setFiltersInitializationStatus(ExtensionContext context, boolean done) {
        ExtensionContext.Store classStore = getClassStore(context);
        classStore.put(FILTER_INITIALIZATION_DONE, new AtomicBoolean(done));
    }

    private void initClassFilter(ExtensionContext context, String key, OpenShift openShift,
            Class<? extends HasMetadata> resourceClass) {
        ExtensionContext.Store classStore = getClassStore(context);
        classStore.put(key, new ResourcesFilterBuilder()
                .setExcludedUntil(ResourcesTimestampHelper.timeOfLastResourceOf(openShift, resourceClass)));
    }

    private void updateClassFilter(ExtensionContext context, String key, OpenShift openShift,
            Class<? extends HasMetadata> resourceClass) {
        ExtensionContext.Store classStore = getClassStore(context);
        classStore.get(key, ResourcesFilterBuilder.class)
                .setIncludedAlwaysWindow(
                        classStore.get(key, ResourcesFilterBuilder.class).getExcludedUntil(),
                        ResourcesTimestampHelper.timeOfLastResourceOf(openShift, resourceClass))
                .setExcludedUntil(null);
    }

    private void initMethodFilter(ExtensionContext context, String key, OpenShift openShift,
            Class<? extends HasMetadata> resourceClass) {
        ExtensionContext.Store classStore = getClassStore(context);
        ExtensionContext.Store methodStore = getMethodStore(context);
        try {
            methodStore.put(key, classStore.get(key, ResourcesFilterBuilder.class)
                    .clone()
                    .setExcludedUntil(ResourcesTimestampHelper.timeOfLastResourceOf(openShift, resourceClass)));
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    private ExtensionContext.Store getClassStore(ExtensionContext extensionContext) {
        return extensionContext.getStore(ExtensionContext.Namespace.create(extensionContext.getRequiredTestClass()));
    }

    private ExtensionContext.Store getMethodStore(ExtensionContext extensionContext) {
        return extensionContext
                .getStore(ExtensionContext.Namespace.create(extensionContext.getRequiredTestClass(),
                        extensionContext.getTestClass()));
    }

    private <E extends HasMetadata> ResourcesFilterBuilder<E> getFilter(ExtensionContext context, String key) {
        OpenShiftRecorder classOpenShiftRecorder = AnnotationSupport
                .findAnnotation(context.getRequiredTestClass(), OpenShiftRecorder.class).orElse(null);
        OpenShiftRecorder methodOpenShiftRecorder = AnnotationSupport
                .findAnnotation(context.getElement(), OpenShiftRecorder.class).orElse(null);
        OpenShiftRecorder openShiftRecorder = methodOpenShiftRecorder != null
                ? methodOpenShiftRecorder
                : classOpenShiftRecorder;

        // annotation (openShiftRecorder is not null) or global include in META-INF.services - null
        String[] resourceNames = openShiftRecorder != null ? openShiftRecorder.resourceNames() : null;

        ResourcesFilterBuilder<E> filter = context.getTestMethod().isPresent()
                ? (ResourcesFilterBuilder<E>) getMethodStore(context).get(key, ResourcesFilterBuilder.class)
                : (ResourcesFilterBuilder<E>) getClassStore(context).get(key, ResourcesFilterBuilder.class);

        // OpenShiftRecorder (OpenShiftRecorderHandler) may be used in following ways
        // (1) A test is annotated by @OpenShiftRecorder - OpenShiftRecorder#resourcesNames is set to default value -
        //     an array of one empty string.
        // (2) A test is annotated by @OpenShiftRecorder(string [string]*) -
        //     OpenShiftRecorder#resourcesNames is an array of strings. null value is not allowed in an annotation.
        // (3) OpenShiftRecorderHandler is defined via SPI in resources/META-INF/services
        //     the annotation is not found for the test class and resourceNames is null
        if (resourceNames != null // exclude (3)
                && !(resourceNames.length == 1 && resourceNames[0].equals("")) // exclude (1)
        ) {
            // option (2) list of resource names is present - filter by names
            filter.filterByResourceNames();
            filter.setResourceNames(resourceNames);
        } else {
            filter.filterByLastSeenResources();
        }
        return filter;
    }

    protected void saveStatefulSets(ExtensionContext context, ResourcesFilterBuilder<StatefulSet> masterFilter)
            throws IOException {
        final Path StatefulSetsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "statefulSets.log");

        try (final ResourcesPrinterHelper<StatefulSet> printer = ResourcesPrinterHelper.forStatefulSet(StatefulSetsLogPath)) {
            OpenShifts.master().getStatefulSets().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
    }

    protected void saveISs(ExtensionContext context, ResourcesFilterBuilder<ImageStream> masterFilter,
            ResourcesFilterBuilder<ImageStream> buildsFilter) throws IOException {
        // master namespace
        final Path imageStreamsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "imageStreams-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<ImageStream> printer = ResourcesPrinterHelper.forISs(imageStreamsMasterLogPath)) {
            OpenShifts.master().getImageStreams().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            final Path imageStreamsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "imageStreams-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<ImageStream> printer = ResourcesPrinterHelper.forISs(imageStreamsBMLogPath)) {
                BuildManagers.get().openShift().getImageStreams().stream()
                        .filter(buildsFilter.build())
                        .forEach(printer::row);
            }
        }
    }

    protected void saveBCs(ExtensionContext context, ResourcesFilterBuilder<BuildConfig> masterFilter,
            ResourcesFilterBuilder<BuildConfig> buildsFilter) throws IOException {
        final Path bcMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "buildConfigs-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<BuildConfig> printer = ResourcesPrinterHelper.forBCs(bcMasterLogPath)) {
            OpenShifts.master().getBuildConfigs().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            final Path bcBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "buildConfigs-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<BuildConfig> printer = ResourcesPrinterHelper.forBCs(bcBMLogPath)) {
                BuildManagers.get().openShift().getBuildConfigs().stream()
                        .filter(buildsFilter.build())
                        .forEach(printer::row);
            }
        }
    }

    protected void saveBuilds(ExtensionContext context, ResourcesFilterBuilder<Build> masterFilter,
            ResourcesFilterBuilder<Build> buildsFilter) throws IOException {
        // master namespace
        final Path buildsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "builds-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Build> printer = ResourcesPrinterHelper.forBuilds(buildsMasterLogPath)) {
            OpenShifts.master().getBuilds().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            final Path buildsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "builds-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Build> printer = ResourcesPrinterHelper.forBuilds(buildsBMLogPath)) {
                BuildManagers.get().openShift().getBuilds().stream()
                        .filter(buildsFilter.build())
                        .forEach(printer::row);
            }
        }
    }

    protected void saveSecrets(ExtensionContext context) throws IOException {
        final Path secretsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "secrets.log");
        try (final ResourcesPrinterHelper<Secret> printer = ResourcesPrinterHelper.forSecrets(secretsLogPath)) {
            OpenShifts.master().getSecrets()
                    .forEach(printer::row);
        }
    }

    protected void saveServices(ExtensionContext context, ResourcesFilterBuilder<Service> masterFilter) throws IOException {
        final Path servicesLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "services.log");
        try (final ResourcesPrinterHelper<Service> printer = ResourcesPrinterHelper.forServices(servicesLogPath)) {
            OpenShifts.master().getServices().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
    }

    protected void saveRoutes(ExtensionContext context, ResourcesFilterBuilder<Route> masterFilter) throws IOException {
        final Path routesLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "routes.log");
        try (final ResourcesPrinterHelper<Route> printer = ResourcesPrinterHelper.forRoutes(routesLogPath)) {
            OpenShifts.master().getRoutes().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
    }

    protected void saveConfigMaps(ExtensionContext context, ResourcesFilterBuilder<ConfigMap> masterFilter) throws IOException {
        final Path configMapsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "configMaps.log");
        try (final ResourcesPrinterHelper<ConfigMap> printer = ResourcesPrinterHelper.forConfigMaps(configMapsLogPath)) {
            OpenShifts.master().getConfigMaps().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
    }

    protected void savePods(ExtensionContext context, ResourcesFilterBuilder<Pod> masterFilter,
            ResourcesFilterBuilder<Pod> buildsFilter) throws IOException {
        // master namespace
        final Path podsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "pods-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Pod> printer = ResourcesPrinterHelper.forPods(podsMasterLogPath)) {
            OpenShifts.master().getPods()
                    .stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            final Path podsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "pods-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Pod> printer = ResourcesPrinterHelper.forPods(podsBMLogPath)) {
                BuildManagers.get().openShift().getPods()
                        .stream()
                        .filter(buildsFilter.build())
                        .forEach(printer::row);
            }
        }
    }

    protected void saveDCs(ExtensionContext context, ResourcesFilterBuilder<DeploymentConfig> masterFilter) throws IOException {
        final Path dcsLogPath = Paths.get(attachmentsDir(), dirNameForTest(context), "deploymentConfigs.log");
        try (final ResourcesPrinterHelper<DeploymentConfig> printer = ResourcesPrinterHelper.forDCs(dcsLogPath)) {
            OpenShifts.master().getDeploymentConfigs().stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);

        }
    }

    protected void savePodLogs(ExtensionContext context, ResourcesFilterBuilder<Pod> masterFilter,
            ResourcesFilterBuilder<Pod> buildsFilter) {
        BiConsumer<OpenShift, ResourcesFilterBuilder<Pod>> podPrinter = (openShift, filter) -> openShift.getPods()
                .stream()
                .filter(filter.build())
                .filter(
                        // filter un-initialized pods out: those pods do not provide any log
                        pod -> pod.getStatus().getInitContainerStatuses().stream().filter(
                                containerStatus -> !(containerStatus.getState().getTerminated() != null
                                        &&
                                        "Completed".equalsIgnoreCase(
                                                containerStatus.getState().getTerminated().getReason())))
                                .count() == 0)
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

        podPrinter.accept(OpenShifts.master(), masterFilter);
        if (!isMasterAndBuildNamespaceSame()) {
            podPrinter.accept(BuildManagers.get().openShift(), buildsFilter);
        }
    }

    protected void saveEvents(ExtensionContext context, ResourcesFilterBuilder<Event> masterFilter,
            ResourcesFilterBuilder<Event> buildsFilter) throws IOException {
        // master namespace
        final Path eventsMasterLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                "events-" + OpenShifts.master().getNamespace() + ".log");
        try (final ResourcesPrinterHelper<Event> printer = ResourcesPrinterHelper.forEvents(eventsMasterLogPath)) {
            OpenShifts.master().getEvents()
                    .stream()
                    .filter(masterFilter.build())
                    .forEach(printer::row);
        }
        // builds namespace (if not same)
        if (!isMasterAndBuildNamespaceSame()) {
            final Path eventsBMLogPath = Paths.get(attachmentsDir(), dirNameForTest(context),
                    "events-" + BuildManagers.get().openShift().getNamespace() + ".log");
            try (final ResourcesPrinterHelper<Event> printer = ResourcesPrinterHelper.forEvents(eventsBMLogPath)) {
                BuildManagers.get().openShift().getEvents()
                        .stream()
                        .filter(buildsFilter.build())
                        .forEach(printer::row);
            }
        }
    }

    protected void saveBuildLogs(ExtensionContext context, ResourcesFilterBuilder<Build> masterFilter,
            ResourcesFilterBuilder<Build> buildsFilter) {
        BiConsumer<OpenShift, ResourcesFilterBuilder<Build>> buildPrinter = (openShift, filter) -> openShift.getBuilds()
                .stream()
                .filter(filter.build())
                .forEach(build -> {
                    try {
                        openShift.storeBuildLog(
                                build,
                                Paths.get(attachmentsDir(), dirNameForTest(context)),
                                build.getMetadata().getName() + ".log");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        buildPrinter.accept(OpenShifts.master(), masterFilter);
        if (!isMasterAndBuildNamespaceSame()) {
            buildPrinter.accept(BuildManagers.get().openShift(), buildsFilter);
        }
    }

    private String attachmentsDir() {
        return JUnitConfig.recordDir() != null ? JUnitConfig.recordDir() : System.getProperty("user.dir");
    }

    private boolean isMasterAndBuildNamespaceSame() {
        return OpenShifts.master().getNamespace().equals(BuildManagers.get().openShift().getNamespace());
    }

    private String dirNameForTest(ExtensionContext context) {
        // if is test
        if (context.getTestMethod().isPresent()) {
            return context.getTestClass().get().getName() + "." + context.getDisplayName();
        } else {
            return context.getTestClass().get().getName();
        }
    }
}
