package cz.xtf.core.openshift;

enum ClusterVersionInfoFactory {
    INSTANCE;

    private volatile ClusterVersionInfo clusterVersionInfo;

    public ClusterVersionInfo getClusterVersionInfo() {
        ClusterVersionInfo localRef = clusterVersionInfo;
        if (localRef == null) {
            synchronized (ClusterVersionInfoFactory.class) {
                localRef = clusterVersionInfo;
                if (localRef == null) {
                    clusterVersionInfo = localRef = new ClusterVersionInfo();
                }
            }
        }
        return localRef;
    }
}
