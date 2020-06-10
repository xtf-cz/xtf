package cz.xtf.junit5.annotations;

import cz.xtf.junit5.extensions.OpenShiftRecorderHandler;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@see OpenShiftRecorderHandler}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtendWith(OpenShiftRecorderHandler.class)
public @interface OpenShiftRecorder {

	/**
	 * Resource names for filtering out events, pods,...
	 * Will be turned into regex {@code __value__.*}
	 *
	 * {@see OpenShiftRecorderHandler}
	 */
	String[] objNames() default "";
}
