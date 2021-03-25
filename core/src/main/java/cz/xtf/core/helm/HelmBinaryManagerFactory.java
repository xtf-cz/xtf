package cz.xtf.core.helm;

import java.util.Arrays;

public enum HelmBinaryManagerFactory {

    INSTANCE;

    private volatile HelmBinaryManager helmBinaryManager;

    HelmBinaryManager getHelmBinaryManager() {
        HelmBinaryManager localHelmBinaryManagerRef = helmBinaryManager;
        if (localHelmBinaryManagerRef == null) {
            synchronized (HelmBinaryManagerFactory.class) {
                localHelmBinaryManagerRef = helmBinaryManager;
                if (localHelmBinaryManagerRef == null) {
                    for (HelmBinaryPathResolver resolver : Arrays.asList(new ConfiguredPathHelmBinaryResolver(),
                            new ConfiguredVersionHelmBinaryPathResolver())) {
                        String resolvedPath = resolver.resolve();
                        if (resolvedPath != null) {
                            helmBinaryManager = localHelmBinaryManagerRef = new HelmBinaryManager(resolvedPath);
                            break;
                        }
                    }
                }

            }
        }
        return localHelmBinaryManagerRef;

    }

}
