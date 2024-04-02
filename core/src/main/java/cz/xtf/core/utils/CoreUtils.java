package cz.xtf.core.utils;

import org.apache.commons.lang3.SystemUtils;

public class CoreUtils {

    public static String getSystemTypeForOCP3() {
        String systemType = "linux";
        if (SystemUtils.IS_OS_MAC) {
            systemType = "macosx";
        } else if (isS390x()) {
            systemType += "-s390x";
        } else if (isPpc64le()) {
            systemType += "-ppc64le";
        }
        return systemType;
    }

    public static String getSystemArchForOCP4() {
        String systemType = "amd64";
        if (isS390x()) {
            systemType = "s390x";
        } else if (isPpc64le()) {
            systemType = "ppc64le";
        } else if (isArm64()) {
            systemType = "arm64";
        }
        return systemType;
    }

    private static boolean isS390x() {
        return SystemUtils.IS_OS_ZOS || "s390x".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("s390x");
    }

    private static boolean isPpc64le() {
        return "ppc64le".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("ppc64le");
    }

    private static boolean isArm64() {
        return "aarch64".equals(SystemUtils.OS_ARCH) || SystemUtils.OS_VERSION.contains("aarch64");
    }
}
