package cz.xtf.core.waiting.failfast;


import cz.xtf.core.config.BuildManagerConfig;
import cz.xtf.core.config.OpenShiftConfig;
import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;

import java.util.ArrayList;
import java.util.List;

/**
 * The class provides support to create {@link FailFastCheck} {@see MultipleFailFastChecksHandler} that checks if a waiter
 * should fail due to an error state of cluster. The function is supposed to be called by a {@link cz.xtf.core.waiting.Waiter}.
 * If it returns true, the waiter should throw an exception.
 * <p>
 * Example of use:
 * <pre>
 *     FailFastBuilder
 *     		.ofOpenshiftAndBmNamespace()
 *     		.events()
 *     		.after(EventHelper.timeOfLastEventBMOrTestNamespaceOrEpoch())
 *     		.ofNames("my-app.*")
 *     		.ofMessages("Failed.*")
 *     		.atLeasOneExists()
 *     		.build()
 * </pre>
 * <p>
 * You can create more checks for events and every one of them will be checked. If one of them returns true, the whole
 * fail fast function returns true.
 */
public class FailFastBuilder {

	private final List<OpenShift> openShifts = new ArrayList<>();
	private final List<FailFastCheck> failFastChecks = new ArrayList<>();

	private FailFastBuilder(String... namespaces) {
		if (namespaces.length == 0) {
			openShifts.add(OpenShifts.master());
		} else {
			for (String namespace : namespaces) {
				openShifts.add(OpenShifts.master(namespace));
			}
		}
	}

	public static FailFastBuilder ofOpenshiftAndBmNamespace() {
		return ofNamespaces(OpenShiftConfig.namespace(), BuildManagerConfig.namespace());
	}

	public static FailFastBuilder ofNamespaces(String... namespaces) {
		return new FailFastBuilder(namespaces);
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
