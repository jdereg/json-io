package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies alternate JSON property names that are accepted during deserialization.
 * This is a read-side only annotation — the primary field name (or {@link IoProperty} name)
 * is always used for serialization.
 *
 * <pre>{@code
 * public class Person {
 *     @IoAlias({"firstName", "first_name", "fname"})
 *     private String name;
 * }
 * // Any of {"name":"Alice"}, {"firstName":"Alice"}, {"first_name":"Alice"}, {"fname":"Alice"}
 * // will deserialize correctly into the 'name' field.
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
public @interface IoAlias {
    /**
     * Alternate JSON property names accepted during deserialization.
     */
    String[] value();
}
