package cz.xtf.junit5.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import cz.xtf.junit5.extensions.CleanBeforeEachCallback;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(CleanBeforeEachCallback.class)
public @interface CleanBeforeEach {
}
