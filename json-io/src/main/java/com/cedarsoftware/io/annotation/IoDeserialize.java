package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level or class-level annotation that overrides the declared type used during
 * deserialization. Matches Jackson's {@code @JsonDeserialize(as=...)} attribute format.
 * <p>
 * When placed on a <b>field</b>, json-io uses the specified class instead of the
 * declared field type during deserialization (field-level type coercion).
 * When placed on a <b>class</b>, acts as class-level coercion — equivalent to
 * {@code ReadOptionsBuilder.coerceClass()}.
 * <p>
 * If {@code @type} IS present in the JSON, it takes precedence over this annotation.
 * <p>
 * <b>Difference from {@link IoTypeInfo}:</b>
 * {@code @IoDeserialize(as=...)} always overrides the declared type (forced coercion).
 * {@code @IoTypeInfo} only provides a default when no type can be inferred. When both
 * are present on the same field, {@code @IoDeserialize} takes priority.
 *
 * <pre>{@code
 * public class Config {
 *     @IoDeserialize(as = LinkedList.class)
 *     private List<String> items;  // always deserialized as LinkedList
 *
 *     @IoDeserialize(as = LinkedHashMap.class)
 *     private Map<String, Object> data;  // always deserialized as LinkedHashMap
 * }
 * }</pre>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoDeserialize {
    /**
     * The concrete class to use instead of the declared field type
     * during deserialization. Defaults to {@code Void.class} (meaning not specified).
     */
    Class<?> as() default Void.class;
}
