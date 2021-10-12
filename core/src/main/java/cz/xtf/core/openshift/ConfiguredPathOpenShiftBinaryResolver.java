package cz.xtf.core.openshift;

import cz.xtf.core.config.OpenShiftConfig;

class ConfiguredPathOpenShiftBinaryResolver implements OpenShiftBinaryPathResolver {

    @Override
    public String resolve() {
        return OpenShiftConfig.binaryPath();
    }
}
