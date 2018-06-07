package cz.xtf.junit.annotation;

import cz.xtf.openshift.imagestream.ImageRegistry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation to request Image Stream creation for test automatically.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
@Repeatable(ImageStreams.class)
public @interface ImageStream {

	/**
	 * @return the Image Stream name
	 */
	String name();

	/**
	 * The Image Stream name. The name must <strong>either</strong> corresponds with the method name in the
	 * {@link ImageRegistry}; or be a full docker image name
	 * (e.g. registry.access.redhat.com/jboss-datagrid-7/datagrid71-openshift:1.0 )
	 *
	 * @return the Image Stream image (from which image is the IS created)
	 */
	String image();

	/**
	 * @return the list of custom tags for the Image Stream
	 */
	String[] tags() default {};

	/**
	 * @return name of public static method to be invoked for true / false result
	 */
	String condition() default "";
}
