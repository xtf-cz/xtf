package cz.xtf.core.context;

public class TestCaseContext {

    /**
     *
     * This allows to track currently running test case for correct namespace mapping. This is used to automatically find
     * namespace for running test case when
     * creating {@link cz.xtf.core.openshift.OpenShift} instances.
     */
    private static String runningTestCaseName;

    /**
     * @return test case name associated with current thread or null if not such mapping exists, for example for com.SmokeTest
     *         returns SmokeTest
     */
    public static String getRunningTestCaseName() {
        return runningTestCaseName;
    }

    public static void setRunningTestCase(String currentlyRunningTestCaseName) {
        runningTestCaseName = currentlyRunningTestCaseName;
    }
}
