package cz.xtf.junit5.extensions;

import java.io.IOException;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.xtf.junit5.config.JUnitConfig;

/**
 * Implements JUnit lifecycle events handling in order to record OCP state for different purposes (e.g.: debug).
 * The operational logic to perform the steps to get the status from OCP is delegated to
 * {@link OpenShiftRecorderService}.
 *
 * Based on the JUnit test life cycle event that is being handled, an instance of this class will either call
 * {@link OpenShiftRecorderService#initFilters(ExtensionContext)} - beforeAllCallback: used to get information on what
 * resources are on a cluster before a test class is processed
 * {@link OpenShiftRecorderService#updateFilters(ExtensionContext)} - beforeEachCallback: used to get information on
 * what resources are on a cluster before a test case is processed
 * {@link OpenShiftRecorderService#recordState(ExtensionContext)} - on various events: used to record state of
 * selected resources (by name) or make an estimation based on information collected when previous events like the two
 * described above took place
 *
 * <p>
 * OpenShift state is recorded in the following cases:
 * <p>
 * <ul>
 * <li>{@link TestExecutionExceptionHandler#handleTestExecutionException}</li>
 * <li>{@link LifecycleMethodExecutionExceptionHandler#handleBeforeAllMethodExecutionException(ExtensionContext, Throwable)}
 * and {@link LifecycleMethodExecutionExceptionHandler#handleBeforeEachMethodExecutionException(ExtensionContext, Throwable)}
 * in case {@code xtf.record.before} is set</li>
 * <li>{@link TestWatcher#testSuccessful(ExtensionContext)} in case {@code xtf.record.before} is set</li>
 */
public class OpenShiftRecorderHandler implements TestWatcher, TestExecutionExceptionHandler, BeforeAllCallback,
        BeforeEachCallback, LifecycleMethodExecutionExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(OpenShiftRecorderHandler.class);

    private final OpenShiftRecorderService openShiftRecorderService;

    public OpenShiftRecorderHandler() {
        openShiftRecorderService = new OpenShiftRecorderService();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        openShiftRecorderService.initFilters(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        openShiftRecorderService.updateFilters(context);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        try {
            openShiftRecorderService.recordState(context);
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void handleBeforeAllMethodExecutionException(final ExtensionContext context, final Throwable throwable)
            throws Throwable {
        try {
            if (JUnitConfig.recordBefore()) {
                openShiftRecorderService.recordState(context);
            }
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void handleBeforeEachMethodExecutionException(final ExtensionContext context, final Throwable throwable)
            throws Throwable {
        try {
            if (JUnitConfig.recordBefore()) {
                openShiftRecorderService.recordState(context);
            }
        } catch (Throwable t) {
            log.error("Throwable: ", t);
        } finally {
            throw throwable;
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        if (JUnitConfig.recordAlways()) {
            try {
                openShiftRecorderService.recordState(context);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
