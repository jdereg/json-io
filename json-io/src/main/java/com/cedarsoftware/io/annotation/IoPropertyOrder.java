package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that controls the order of fields during JSON serialization.
 * Fields listed in the annotation appear first in the specified order; any remaining
 * fields follow in their natural declaration order.
 *
 * <pre>{@code
 * @IoPropertyOrder({"id", "name", "email"})
 * public class User {
 *     private String email;
 *     private String name;
 *     private long id;
 *     private int age;
 * }
 * // Serializes as: {"id":1,"name":"Alice","email":"alice@example.com","age":30}
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
public @interface IoPropertyOrder {
    /**
     * Ordered list of field names. Fields not listed follow at the end in natural order.
     */
    String[] value();
}
