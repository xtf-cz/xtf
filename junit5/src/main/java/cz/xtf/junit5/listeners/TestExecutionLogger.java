package cz.xtf.junit5.listeners;

import java.util.HashMap;
import java.util.Map;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestExecutionLogger implements TestExecutionListener {
    private Map<String, Long> executionTimes = new HashMap<>();

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        String displayName = getTestDisplayName(testIdentifier);

        if (testIdentifier.isTest()) {
            log.info("*** {} is being skipped ***", displayName);
            log.info("*** {}: {} ***", displayName, reason);
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            String displayName = getTestDisplayName(testIdentifier);
            executionTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
            log.info("*** {} is starting ***", displayName);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            String displayName = getTestDisplayName(testIdentifier);
            long executionTime = System.currentTimeMillis() - executionTimes.remove(testIdentifier.getUniqueId());
            String status = resolveStatusString(testExecutionResult);
            log.info("*** {} {} after {}. ***", displayName, status, millisToString(executionTime));
        }
    }

    private String resolveStatusString(TestExecutionResult testExecutionResult) {
        switch (testExecutionResult.getStatus()) {
            case SUCCESSFUL:
                return "succeeded";
            case FAILED:
                return "failed";
            case ABORTED:
                return "has been aborted";
            default:
                return "is unknown";
        }
    }

    /**
     * Parses testIdentifier parent id in format:
     * '[engine:test-engine]/[class:full.class.name.MyTest]/...' (May or may not continue. Eg. parameterized tests).
     *
     * @param testIdentifier testIdentifier (isTest condition should be met, otherwise behaviour is undefined)
     * @return Customized test display name
     */
    private String getTestDisplayName(TestIdentifier testIdentifier) {
        String className = testIdentifier.getParentId().get()
                .replaceAll(".*class:", "") // cut of everything before class: string
                .replaceAll("].*", "") // cut of everything after full class name
                .replaceAll(".*\\.", ""); // cut of everyting before dot (output: class name)
        return String.format("%s#%s", className, testIdentifier.getDisplayName());
    }

    private String millisToString(long millis) {
        long hours = millis / 3600000L;
        long minutes = millis % 3600000L / 60000L;
        long seconds = millis % 60000L / 1000L;
        long milis = millis % 1000L;
        return String.format("%s:%02d:%02d.%03d", hours, minutes, seconds, milis);
    }
}
