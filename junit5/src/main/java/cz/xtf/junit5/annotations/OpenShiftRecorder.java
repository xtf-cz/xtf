package cz.xtf.junit5.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.extensions.OpenShiftRecorderHandler;

/**
 * {@see OpenShiftRecorderHandler}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExtendWith(OpenShiftRecorderHandler.class)
public @interface OpenShiftRecorder {

    /**
     * Resource names for filtering out events, pods,...
     * Will be turned into regex {@code __value__.*}
     * <p>
     * If no value is given resources are filtered automatically by recording what resources are seen before test and so on.
     * {@see OpenShiftRecorderHandler}
     */
    String[] resourceNames() default "";
}
