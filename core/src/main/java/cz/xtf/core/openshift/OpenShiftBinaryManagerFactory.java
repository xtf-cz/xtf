package cz.xtf.core.openshift;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

enum OpenShiftBinaryManagerFactory {
    INSTANCE;

    private volatile OpenShiftBinaryManager openShiftBinaryManager;

    OpenShiftBinaryManager getOpenShiftBinaryManager() {
        OpenShiftBinaryManager localRef = openShiftBinaryManager;
        if (localRef == null) {
            synchronized (OpenShiftBinaryManagerFactory.class) {
                localRef = openShiftBinaryManager;
                if (localRef == null) {
                    for (OpenShiftBinaryPathResolver resolver : resolverList()) {
                        String path = resolver.resolve();
                        if (path != null) {
                            openShiftBinaryManager = localRef = new OpenShiftBinaryManager(path);
                            break;
                        }
                    }
                }
            }
        }
        return localRef;
    }

    private List<OpenShiftBinaryPathResolver> resolverList() {
        return Stream.of(
                new ConfiguredPathOpenShiftBinaryResolver(),
                new ClusterVersionOpenShiftBinaryPathResolver()).collect(Collectors.toList());
    }
}
