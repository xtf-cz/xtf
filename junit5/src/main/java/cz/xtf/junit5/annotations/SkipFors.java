package cz.xtf.junit5.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.extensions.SkipForCondition;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExtendWith(SkipForCondition.class)
public @interface SkipFors {
    SkipFor[] value();
}
