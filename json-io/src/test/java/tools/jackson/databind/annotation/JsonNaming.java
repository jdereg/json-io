package tools.jackson.databind.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-only stand-in for Jackson 3.x's {@code tools.jackson.databind.annotation.JsonNaming}.
 * <p>
 * json-io's {@code AnnotationResolver} detects the Jackson databind annotations purely by
 * fully-qualified class name (via {@code ClassUtilities.forName}) and maps naming strategies by
 * {@code Class.getSimpleName()} — both of which are identical between Jackson 2.x and 3.x. This
 * hand-rolled annotation, declared at the exact 3.x coordinates, therefore exercises json-io's
 * Jackson-3 detection path faithfully <em>without</em> adding the real Jackson 3 dependency (which
 * would force the whole json-io test suite onto Java 17). Only present on the test classpath.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface JsonNaming {
    Class<?> value();
}
