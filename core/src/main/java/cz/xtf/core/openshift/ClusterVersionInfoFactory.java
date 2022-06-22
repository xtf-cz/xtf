package cz.xtf.core.openshift;

enum ClusterVersionInfoFactory {
    INSTANCE;

    private volatile ClusterVersionInfo clusterVersionInfo;

    public ClusterVersionInfo getClusterVersionInfo() {
        return getClusterVersionInfo(false);
    }

    //just for reloading version on tests
    ClusterVersionInfo getClusterVersionInfo(boolean reload) {
        if (reload) {
            clusterVersionInfo = null;
        }
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
