package cz.xtf.junit5.extensions;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class CleanBeforeAllCallback implements BeforeAllCallback {
	private static final OpenShift openShift = OpenShifts.master();

	@Override
	public void beforeAll(ExtensionContext context) {
		openShift.clean().waitFor();
	}
}
