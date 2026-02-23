package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.cedarsoftware.io.JsonClassWriter;

/**
 * Marks a class to use a specific custom writer during serialization.
 * <p>
 * When a class is annotated with {@code @IoCustomWriter}, json-io will use the specified
 * {@link JsonClassWriter} implementation to serialize instances of this class instead of
 * standard field-by-field serialization. This is the annotation equivalent of calling
 * {@code WriteOptionsBuilder.addCustomWrittenClass(Class, JsonClassWriter)} or adding
 * an entry to the {@code config/customWriters.txt} configuration file.
 * </p>
 * <p>
 * The writer class must have a public no-arg constructor. Instances are cached and shared
 * across all serialization operations.
 * </p>
 * <p>
 * Programmatic API ({@code addCustomWrittenClass()}) takes priority over this annotation.
 * </p>
 *
 * <pre>{@code
 * @IoCustomWriter(MoneyWriter.class)
 * public class Money {
 *     private BigDecimal amount;
 *     private Currency currency;
 * }
 * }</pre>
 *
 * @see JsonClassWriter
 * @see IoCustomReader
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoCustomWriter {
    /**
     * The {@link JsonClassWriter} implementation class to use for serialization.
     */
    Class<? extends JsonClassWriter> value();
}
