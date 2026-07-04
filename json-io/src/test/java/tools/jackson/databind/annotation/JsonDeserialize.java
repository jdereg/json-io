package tools.jackson.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-only stand-in for Jackson 3.x's {@code tools.jackson.databind.annotation.JsonDeserialize}.
 * <p>
 * See {@link JsonNaming} for why a hand-rolled stand-in at the 3.x coordinates is used instead of
 * the real Jackson 3 dependency. Only the {@code as} attribute (the piece json-io honors) is
 * modeled here. Only present on the test classpath.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER})
public @interface JsonDeserialize {
    Class<?> as() default Void.class;
}
