package cz.xtf.junit.annotation.release;

import cz.xtf.junit.filter.SinceVersionFilter;

import java.lang.annotation.*;

/**
 * This annotation could be used for test exclusions. Value of "since" parameter is compared to corresponding image tag version.
 * If the image version is less than given "since" version then the test is excluded. See {@link SinceVersionFilter}
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SinceVersions.class)
public @interface SinceVersion {

	String name();

	String image();

	String since();

	String jira() default "";

}
