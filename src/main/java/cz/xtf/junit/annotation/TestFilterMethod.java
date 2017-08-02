package cz.xtf.junit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cz.xtf.junit.filter.ExclusionTestNameFilter;
import cz.xtf.junit.filter.InclusionTestClassFilter;

/**
 * Method annotation to mark methods returning test filters.
 *
 * <p>
 * All the methods annotated with this annotation must be public, static and return
 * {@link InclusionTestClassFilter} or
 * {@link ExclusionTestNameFilter} instance.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestFilterMethod {
}
