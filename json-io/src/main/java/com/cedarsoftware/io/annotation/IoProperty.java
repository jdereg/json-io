package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the JSON property name to use for serialization and deserialization of a field.
 * When present, the field will be written to JSON using the specified name instead of the
 * Java field name, and the specified name will be recognized during deserialization.
 *
 * <pre>{@code
 * public class Person {
 *     @IoProperty("full_name")
 *     private String name;
 *     private int age;
 * }
 * // Serializes as: {"full_name":"Alice","age":25}
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
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoProperty {
    /**
     * The JSON property name to use for this field.
     */
    String value();
}
