package cz.xtf.core.config;

import java.nio.file.Paths;

public class HelmConfig {

    public static final String HELM_BINARY_CACHE_PATH = "xtf.helm.binary.cache.path";
    public static final String HELM_BINARY_CACHE_ENABLED = "xtf.helm.binary.cache.enabled";
    public static final String HELM_BINARY_PATH = "xtf.helm.binary.path";
    public static final String HELM_CLIENT_VERSION = "xtf.helm.client.version";
    private static final String HELM_BINARY_CACHE_DEFAULT_FOLDER = "xtf-helm-cache";

    public static boolean isHelmBinaryCacheEnabled() {
        return Boolean.parseBoolean(XTFConfig.get(HELM_BINARY_CACHE_ENABLED, "true"));
    }

    public static String binaryCachePath() {
        return XTFConfig.get(HELM_BINARY_CACHE_PATH, Paths.get(System.getProperty("java.io.tmpdir"),
                HELM_BINARY_CACHE_DEFAULT_FOLDER).toAbsolutePath().normalize().toString());
    }

    public static String binaryPath() {
        return XTFConfig.get(HELM_BINARY_PATH);
    }

    public static String helmClientVersion() {
        return XTFConfig.get(HELM_CLIENT_VERSION, "latest");
    }

}
