package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.cedarsoftware.io.JsonClassReader;

/**
 * Marks a class to use a specific custom reader during deserialization.
 * <p>
 * When a class is annotated with {@code @IoCustomReader}, json-io will use the specified
 * {@link JsonClassReader} implementation to deserialize instances of this class instead of
 * standard field-by-field deserialization. This is the annotation equivalent of calling
 * {@code ReadOptionsBuilder.addCustomReaderClass(Class, JsonClassReader)} or adding
 * an entry to the {@code config/customReaders.txt} configuration file.
 * </p>
 * <p>
 * The reader class must have a public no-arg constructor. Instances are cached and shared
 * across all deserialization operations.
 * </p>
 * <p>
 * Programmatic API ({@code addCustomReaderClass()}) takes priority over this annotation.
 * </p>
 *
 * <pre>{@code
 * @IoCustomReader(MoneyReader.class)
 * public class Money {
 *     private BigDecimal amount;
 *     private Currency currency;
 * }
 * }</pre>
 *
 * @see JsonClassReader
 * @see IoCustomWriter
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoCustomReader {
    /**
     * The {@link JsonClassReader} implementation class to use for deserialization.
     */
    Class<? extends JsonClassReader> value();
}
