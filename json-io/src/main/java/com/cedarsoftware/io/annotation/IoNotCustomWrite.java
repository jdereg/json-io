package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class to suppress custom writer usage during serialization.
 * <p>
 * When a class is annotated with {@code @IoNotCustomWrite}, json-io will use standard
 * field-by-field serialization instead of any custom writer that might otherwise apply
 * (e.g., inherited from a parent class). This is the annotation equivalent of adding
 * a class to the {@code config/notCustomWritten.txt} configuration file or calling
 * {@code WriteOptionsBuilder.addNotCustomWrittenClass(Class)}.
 * </p>
 * <p>
 * All three sources (annotation, config file, programmatic API) are additive — any one
 * of them is sufficient to suppress custom writing for the class.
 * </p>
 *
 * <pre>{@code
 * @IoNotCustomWrite
 * public class MySpecialSet extends HashSet<String> {
 *     // Will NOT use HashSet's custom writer — uses standard field-by-field serialization
 * }
 * }</pre>
 *
 * @see IoNotCustomRead
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoNotCustomWrite {
}
