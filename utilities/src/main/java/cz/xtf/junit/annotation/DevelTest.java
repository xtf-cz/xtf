package cz.xtf.junit.annotation;

import cz.xtf.junit.filter.DevelTestFilter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for {@link DevelTestFilter}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.TYPE })
public @interface DevelTest {
}
