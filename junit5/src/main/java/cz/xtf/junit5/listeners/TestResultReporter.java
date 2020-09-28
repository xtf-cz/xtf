package cz.xtf.junit5.listeners;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class TestResultReporter implements TestExecutionListener {
    private final TestResultWriter testResultWriter;

    public TestResultReporter() {
        this.testResultWriter = new TestResultWriter();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        testResultWriter.recordSuiteStart();
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        testResultWriter.recordFinishedSuite();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            testResultWriter.recordTestStart(testIdentifier.getUniqueId());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (testIdentifier.isTest()) {
            String testId = testIdentifier.getUniqueId();
            String methodName = testIdentifier.getDisplayName();
            String className = testIdentifier.getParentId().orElse("null").replaceAll(".*class:", "").replaceAll("].*", "");

            testResultWriter.recordSkippedTest(testId, className, methodName, reason);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            String testId = testIdentifier.getUniqueId();
            String methodName = testIdentifier.getDisplayName();
            String className = testIdentifier.getParentId().orElse("null").replaceAll(".*class:", "").replaceAll("].*", "");

            switch (testExecutionResult.getStatus()) {
                case SUCCESSFUL:
                    testResultWriter.recordSuccessfulTest(testId, className, methodName);
                    break;
                case FAILED:
                    testResultWriter.recordFailedTest(testId, className, methodName, testExecutionResult.getThrowable()
                            .orElseThrow(() -> new IllegalStateException("Failed test didn't throw anything!")));
                    break;
                case ABORTED:
                    testResultWriter.recordSkippedTest(testId, className, methodName,
                            testExecutionResult.getThrowable().orElse(new IllegalStateException()).getMessage());
                default:
                    break;
            }
        }
    }
}
