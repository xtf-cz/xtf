package cz.xtf.junit5.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.extensions.SinceVersionCondition;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Repeatable(SinceVersions.class)
@ExtendWith(SinceVersionCondition.class)
public @interface SinceVersion {

    /**
     * Name or regexp pattern matching name of the image. For example "eap73-openjdk11-openshift-rhel8" or "eap73-openjdk11-.*"
     * <p>
     * Only one of {@code imageMetadataLabelName} and {@code name} can be presented.
     */
    String name() default "";

    /**
     * Name or regexp pattern matching name in {@code Docker Labels} in image metadata
     * For example "eap73-openjdk11-openshift-rhel8" or "eap73-openjdk11-.*".
     * <p>
     * Only one of {@code imageMetadataLabelName} and {@code name} can be presented.
     */
    String imageMetadataLabelName() default "";

    String image();

    String since();

    String jira() default "";
}
