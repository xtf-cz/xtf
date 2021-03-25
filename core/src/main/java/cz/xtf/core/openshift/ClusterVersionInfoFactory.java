package cz.xtf.core.openshift;

public enum ClusterVersionInfoFactory {
    INSTANCE;

    private volatile ClusterVersionInfo clusterVersionInfo;

    public ClusterVersionInfo getClusterVersionInfo() {
        return getClusterVersionInfo(false);
    }

    //just for reloading version on tests
    public ClusterVersionInfo getClusterVersionInfo(boolean reload) {
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
