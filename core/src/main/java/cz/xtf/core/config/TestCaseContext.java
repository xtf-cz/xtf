package cz.xtf.core.config;

import static cz.xtf.core.config.OpenShiftConfig.OPENSHIFT_NAMESPACE;

import java.util.HashMap;
import java.util.Map;

public class TestCaseContext {

    /**
     * Used only in case when test cases are executed in different namespaces when:
     * -Dxtf.openshift.namespace.per.testcase=true
     *
     * This allows to track currently running test case for correct namespace mapping. This is used to automatically find
     * namespace for running test case when
     * creating {@link cz.xtf.core.openshift.OpenShift} instances.
     */
    private static String TEST_CASE_NAME;

    private static final Map<String, String> testcaseToNamespaceMap = new HashMap<String, String>() {
        {
            // put default namespace into map to guarantee that it will be deleted with other namespaces
            put(XTFConfig.get(OPENSHIFT_NAMESPACE), XTFConfig.get(OPENSHIFT_NAMESPACE));
        }
    };

    /**
     * @return test case name associated with current thread or null if not such mapping exists
     */
    public static String getTestCaseForCurrentThread() {
        return TEST_CASE_NAME;
    }

    public static void setRunningTestCase(String currentlyRunningTestCaseName) {
        TEST_CASE_NAME = currentlyRunningTestCaseName;
    }

    /**
     * Used only in case when test cases are executed in parallel in different namespaces when:
     * -Dxtf.openshift.namespace.per.testcase=true
     *
     * Maps testcase name -> namespace
     */
    public static Map<String, String> getRunningTestcase() {
        return testcaseToNamespaceMap;
    }
}
