package cz.xtf.core.config;

public class BuildManagerConfig {
    public static final String BUILD_NAMESPACE = "xtf.bm.namespace";
    public static final String FORCE_REBUILD = "xtf.bm.force_rebuild";
    public static final String SKIP_REBUILD = "xtf.bm.skip_rebuild";
    public static final String MAX_RUNNING_BUILDS = "xtf.bm.max_running_builds";
    public static final String MEMORY_REQUEST = "xtf.bm.memory.request";
    public static final String MEMORY_LIMIT = "xtf.bm.memory.limit";

    public static String namespace() {
        return XTFConfig.get(BUILD_NAMESPACE, "xtf-builds");
    }

    public static boolean forceRebuild() {
        return Boolean.valueOf(XTFConfig.get(FORCE_REBUILD, "false"));
    }

    public static boolean skipRebuild() {
        return Boolean.valueOf(XTFConfig.get(SKIP_REBUILD, "false"));
    }

    public static int maxRunningBuilds() {
        return Integer.valueOf(XTFConfig.get(MAX_RUNNING_BUILDS, "5"));
    }

    /**
     * Memory request for builds (e.g., "2Gi", "512Mi")
     *
     * @return memory request string or null if not configured
     */
    public static String memoryRequest() {
        return XTFConfig.get(MEMORY_REQUEST);
    }

    /**
     * Memory limit for builds (e.g., "4Gi", "1Gi")
     *
     * @return memory limit string or null if not configured
     */
    public static String memoryLimit() {
        return XTFConfig.get(MEMORY_LIMIT);
    }
}
