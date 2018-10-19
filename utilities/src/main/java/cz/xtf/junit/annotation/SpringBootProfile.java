package cz.xtf.junit.annotation;

import cz.xtf.junit.filter.MsaProfileFilter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for the {@link MsaProfileFilter}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.TYPE})
@Deprecated
public @interface SpringBootProfile {
}
