package cz.xtf.junit5.extensions;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;

public class CleanBeforeEachCallback implements BeforeEachCallback {
    private static final OpenShift openShift = OpenShifts.master();

    @Override
    public void beforeEach(ExtensionContext context) {
        openShift.clean().waitFor();
    }
}
