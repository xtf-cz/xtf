package cz.xtf.junit5.extensions;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class CleanBeforeEachCallback implements BeforeEachCallback {
	private static final OpenShift openShift = OpenShifts.master();

	@Override
	public void beforeEach(ExtensionContext context) {
		openShift.clean().waitFor();
	}
}
