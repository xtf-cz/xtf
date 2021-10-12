package cz.xtf.core.openshift;

interface OpenShiftBinaryPathResolver {

    /**
     * @return resolved path or null
     */
    String resolve();
}
