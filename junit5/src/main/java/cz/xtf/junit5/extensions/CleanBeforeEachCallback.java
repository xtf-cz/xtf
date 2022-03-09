package cz.xtf.junit5.extensions;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import cz.xtf.core.openshift.OpenShift;
import cz.xtf.core.openshift.OpenShifts;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanBeforeEachCallback implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        OpenShift openShift = OpenShifts.master();
        openShift.clean().reason("Cleaning namespace: " + openShift.getNamespace() + " before test.")
                .onTimeout(() -> log.info("Cleaning namespace: " + openShift.getNamespace() + " before test - timed out."))
                .onFailure(() -> log.info("Cleaning namespace: " + openShift.getNamespace() + " before test - failed."))
                .onSuccess(() -> log.info("Cleaning namespace: " + openShift.getNamespace() + " before test - finished."))
                .waitFor();
    }
}
