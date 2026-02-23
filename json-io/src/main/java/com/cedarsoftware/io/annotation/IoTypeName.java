package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns a short alias name to a class for use in the {@code @type} field during JSON serialization.
 * <p>
 * When a class is annotated with {@code @IoTypeName("ShortName")}, json-io will write
 * {@code "@type":"ShortName"} instead of the fully-qualified class name. On deserialization,
 * the alias is resolved back to the original class. This is the annotation equivalent of
 * calling {@code WriteOptionsBuilder.aliasTypeName(Class, String)} /
 * {@code ReadOptionsBuilder.aliasTypeName(Class, String)} or adding an entry to the
 * {@code config/aliases.txt} configuration file.
 * </p>
 * <p>
 * Programmatic API ({@code aliasTypeName()}) takes priority over this annotation.
 * </p>
 * <p>
 * Equivalent to Jackson's {@code @JsonTypeName("ShortName")}.
 * </p>
 *
 * <pre>{@code
 * @IoTypeName("Sensor")
 * public class SensorReading {
 *     private double value;
 *     private String unit;
 * }
 * // Serializes as: {"@type":"Sensor","value":23.5,"unit":"C"}
 * // Instead of:    {"@type":"com.example.SensorReading","value":23.5,"unit":"C"}
 * }</pre>
 *
 * @see com.cedarsoftware.io.WriteOptionsBuilder#aliasTypeName(Class, String)
 * @see com.cedarsoftware.io.ReadOptionsBuilder#aliasTypeName(Class, String)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoTypeName {
    /**
     * The short alias name to use in place of the fully-qualified class name in {@code @type}.
     */
    String value();
}
