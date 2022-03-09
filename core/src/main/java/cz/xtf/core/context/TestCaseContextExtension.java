package cz.xtf.core.context;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Sets TestCaseContext to name of currently started test case in @BeforeAllCallback
 */
@Slf4j
public class TestCaseContextExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        TestCaseContext.setRunningTestCase(extensionContext.getTestClass().get().getName());
    }
}
