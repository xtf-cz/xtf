package cz.xtf.core.config;

public class BuildManagerConfig {
    public static final String BUILD_NAMESPACE = "xtf.bm.namespace";
    public static final String FORCE_REBUILD = "xtf.bm.force_rebuild";
    public static final String MAX_RUNNING_BUILDS = "xtf.bm.max_running_builds";

    public static String namespace() {
        return XTFConfig.get(BUILD_NAMESPACE, "xtf-builds");
    }

    public static boolean forceRebuild() {
        return Boolean.valueOf(XTFConfig.get(FORCE_REBUILD, "false"));
    }

    public static int maxRunningBuilds() {
        return Integer.valueOf(XTFConfig.get(MAX_RUNNING_BUILDS, "5"));
    }
}
