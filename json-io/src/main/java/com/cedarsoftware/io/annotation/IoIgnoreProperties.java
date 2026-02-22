package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that specifies fields to exclude from both serialization
 * and deserialization by name.
 *
 * <pre>{@code
 * @IoIgnoreProperties({"password", "secretKey"})
 * public class User {
 *     private String name;
 *     private String password;
 *     private String secretKey;
 * }
 * // "password" and "secretKey" will not appear in JSON output.
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
public @interface IoIgnoreProperties {
    /**
     * Names of fields to exclude from serialization and deserialization.
     */
    String[] value();
}
