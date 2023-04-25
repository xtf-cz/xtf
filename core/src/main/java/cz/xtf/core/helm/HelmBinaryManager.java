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
        return getBinary(OpenShiftConfig.adminToken(), OpenShiftConfig.namespace());
    }

    public HelmBinary masterBinary() {
        return getBinary(OpenShiftConfig.masterToken(), OpenShiftConfig.namespace());
    }

    private static HelmBinary getBinary(String token, String namespace) {
        String helmBinaryPath = HelmBinaryManagerFactory.INSTANCE.getHelmBinaryManager().getHelmBinaryPath();
        return new HelmBinary(helmBinaryPath, token, namespace);

    }
}
