package cz.xtf.core.helm;

import cz.xtf.core.config.OpenShiftConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class HelmBinaryManager {
    private final String helmBinaryPath;

    HelmBinaryManager(String helmBinaryPath) {
        this.helmBinaryPath = helmBinaryPath;
    }

    String getHelmBinaryPath() {
        return helmBinaryPath;
    }

    public HelmBinary adminBinary() {
        String adminToken = validateToken(
                OpenShiftConfig.adminToken(),
                OpenShiftConfig.OPENSHIFT_ADMIN_TOKEN);
        return getBinary(adminToken, OpenShiftConfig.namespace());
    }

    public HelmBinary masterBinary() {
        String masterToken = validateToken(
                OpenShiftConfig.masterToken(),
                OpenShiftConfig.OPENSHIFT_MASTER_TOKEN);
        return getBinary(masterToken, OpenShiftConfig.namespace());
    }

    private String validateToken(String token, String propertyName) {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException(
                    "Token is not configured. Please set '" + propertyName + "' in your properties file.");
        }
        return token;
    }

    private static HelmBinary getBinary(String token, String namespace) {
        String helmBinaryPath = HelmBinaryManagerFactory.INSTANCE.getHelmBinaryManager().getHelmBinaryPath();
        return new HelmBinary(helmBinaryPath, token, namespace);

    }
}
