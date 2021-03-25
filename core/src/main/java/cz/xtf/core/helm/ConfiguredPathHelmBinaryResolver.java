package cz.xtf.core.helm;

import cz.xtf.core.config.HelmConfig;

class ConfiguredPathHelmBinaryResolver implements HelmBinaryPathResolver {
    @Override
    public String resolve() {
        return HelmConfig.binaryPath();
    }
}
