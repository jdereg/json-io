package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Type-level annotation that marks a class for exclusion from serialization and deserialization.
 * When a class is annotated with {@code @IoIgnoreType}, any field whose declared type is that
 * class will be automatically ignored across ALL classes that reference it.
 *
 * <pre>{@code
 * @IoIgnoreType
 * public class InternalMetadata {
 *     private String traceId;
 *     private long timestamp;
 * }
 *
 * public class Order {
 *     private String orderId;
 *     private InternalMetadata meta;  // auto-excluded from JSON
 * }
 * // JSON: {"orderId": "123"}  — "meta" field is absent
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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoIgnoreType { }
