package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class to suppress custom reader usage during deserialization.
 * <p>
 * When a class is annotated with {@code @IoNotCustomRead}, json-io will use standard
 * field-by-field deserialization instead of any custom reader that might otherwise apply
 * (e.g., inherited from a parent class). This is the annotation equivalent of adding
 * a class to the {@code config/notCustomRead.txt} configuration file or calling
 * {@code ReadOptionsBuilder.addNotCustomReaderClass(Class)}.
 * </p>
 * <p>
 * All three sources (annotation, config file, programmatic API) are additive — any one
 * of them is sufficient to suppress custom reading for the class.
 * </p>
 *
 * <pre>{@code
 * @IoNotCustomRead
 * public class MySpecialSet extends HashSet<String> {
 *     // Will NOT use HashSet's custom reader — uses standard field-by-field deserialization
 * }
 * }</pre>
 *
 * @see IoNotCustomWrite
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoNotCustomRead {
}
