package cz.xtf.junit.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import cz.xtf.junit.filter.MsaProfileFilter;

/**
 * Marker annotation for the {@link MsaProfileFilter}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.TYPE})
public @interface VertxProfile {
}
