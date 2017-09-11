package cz.xtf.junit.annotation;

import cz.xtf.junit.XTFTestSuite;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation specifying the suffix for JUnit tests. Based on the value the
 * {@link XTFTestSuite} will search for test classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TestSuffix {

	String value();
}
