package cz.xtf.junit5.annotations;

import cz.xtf.junit5.extensions.CleanBeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(CleanBeforeAllCallback.class)
public @interface CleanBeforeAll {
}
