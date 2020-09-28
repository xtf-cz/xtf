package cz.xtf.core.bm;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.waiting.Waiter;

public interface ManagedBuild {

    String getId();

    void build(OpenShift openShift);

    void update(OpenShift openShift);

    void delete(OpenShift openShift);

    boolean isPresent(OpenShift openShift);

    boolean needsUpdate(OpenShift openShift);

    Waiter hasCompleted(OpenShift openShift);
}
