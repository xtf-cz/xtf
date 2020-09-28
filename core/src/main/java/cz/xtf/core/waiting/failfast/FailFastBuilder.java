package cz.xtf.core.waiting.failfast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.xtf.core.bm.BuildManagers;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;

/**
 * The class provides support to create {@link FailFastCheck} {@see MultipleFailFastChecksHandler} that checks if a waiter
 * should fail due to an error state of cluster. The function is supposed to be called by a {@link cz.xtf.core.waiting.Waiter}.
 * If it returns true, the waiter should throw an exception.
 * <p>
 * Example of use:
 * 
 * <pre>
 * FailFastBuilder
 *         .ofTestAndBuildNamespace()
 *         .events()
 *         .after(EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch())
 *         .ofNames("my-app.*")
 *         .ofMessages("Failed.*")
 *         .atLeasOneExists()
 *         .build()
 * </pre>
 * <p>
 * You can create more checks for events and every one of them will be checked. If one of them returns true, the whole
 * fail fast function returns true.
 */
public class FailFastBuilder {

    private final List<OpenShift> openShifts = new ArrayList<>();
    private final List<FailFastCheck> failFastChecks = new ArrayList<>();

    private FailFastBuilder(OpenShift... openShifts) {
        if (openShifts.length == 0) {
            this.openShifts.add(OpenShifts.master());
        } else {
            this.openShifts.addAll(Arrays.asList(openShifts));
        }
    }

    public static FailFastBuilder ofTestAndBuildNamespace() {
        return ofOpenShifts(OpenShifts.master(), BuildManagers.get().openShift());
    }

    public static FailFastBuilder ofOpenShifts(OpenShift... openShifts) {
        return new FailFastBuilder(openShifts);
    }

    public EventFailFastCheckBuilder events() {
        return new EventFailFastCheckBuilder(this);
    }

    void addFailFastCheck(FailFastCheck check) {
        this.failFastChecks.add(check);
    }

    List<OpenShift> getOpenshifts() {
        return openShifts;
    }

    public FailFastCheck build() {
        return new MultipleFailFastChecksHandler(failFastChecks);
    }

}
