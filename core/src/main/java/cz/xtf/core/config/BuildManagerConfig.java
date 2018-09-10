package cz.xtf.core.config;

public class BuildManagerConfig {
	public static final String BUILD_NAMESPACE = "xtf.bm.namespace";
	public static final String FORCE_REBUILD = "xtf.bm.force_rebuild";

	public static String namespace() {
		return XTFConfig.get(BUILD_NAMESPACE, "xtf-builds");
	}

	public static boolean forceRebuild() {
		return Boolean.valueOf(XTFConfig.get(FORCE_REBUILD, "false"));
	}
}
