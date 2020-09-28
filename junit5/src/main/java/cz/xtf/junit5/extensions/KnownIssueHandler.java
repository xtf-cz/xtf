package cz.xtf.junit5.extensions;

import org.assertj.core.api.SoftAssertionError;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.platform.commons.support.AnnotationSupport;
import org.opentest4j.TestAbortedException;

import cz.xtf.junit5.annotations.KnownIssue;

public class KnownIssueHandler implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        String knownIssue = AnnotationSupport.findAnnotation(context.getElement(), KnownIssue.class)
                .orElseThrow(() -> throwable).value();
        if (throwable instanceof SoftAssertionError
                && ((SoftAssertionError) throwable).getErrors().stream().allMatch(e -> e.contains(knownIssue))) {
            throw new TestAbortedException(knownIssue, throwable);
        } else if (throwable instanceof AssertionError && !(throwable instanceof SoftAssertionError)
                && throwable.getMessage().contains(knownIssue)) {
            throw new TestAbortedException(knownIssue, throwable);
        }
        throw throwable;
    }
}
