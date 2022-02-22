package cz.xtf.junit5.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.extensions.SkipForCondition;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@Repeatable(SkipFors.class)
@ExtendWith(SkipForCondition.class)
public @interface SkipFor {

    /**
     * Name or regexp pattern matching name of the image. For example "eap73-openjdk11-openshift-rhel8" or "eap73-openjdk11-.*"
     * <p>
     * Only one of {@code name}, {@code imageMetadataLabelName}, {@code imageMetadataLabelArchitecture} and
     * {@code subId} can be presented.
     */
    String name() default "";

    /**
     * Name or regexp pattern matching name in {@code Docker Labels} in image metadata
     * For example "jboss-eap-7/eap73-openjdk11-openshift-rhel8" or "eap73-openjdk11-.*".
     * <p>
     * Only one of {@code name}, {@code imageMetadataLabelName}, {@code imageMetadataLabelArchitecture} and
     * {@code subId} can be presented.
     */
    String imageMetadataLabelName() default "";

    /**
     * Architecture or regexp pattern matching architecture in {@code Docker Labels} in image metadata
     * For example "x86_64" or "x86_.*".
     * <p>
     * Only one of {@code name}, {@code imageMetadataLabelName}, {@code imageMetadataLabelArchitecture} and
     * {@code subId} can be presented.
     */
    String imageMetadataLabelArchitecture() default "";

    /**
     * Name or regexp pattern matching the value of the {@code xtf.<product id>>.subid} XTF property
     * For example, ".*74.*" will match and skip the annotated test when xtf.eap.subid is set to "74-openjdk11",
     * "74-openj9-11" or "74".
     * <p>
     * Only one of {@code imageMetadataLabelName}, {@code name}, {@code imageMetadataLabelArchitecture} and
     * {@code subId} can be presented.
     */
    String subId() default "";

    String image();

    String reason() default "";
}
