package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that specifies a whitelist of fields to include in both
 * serialization and deserialization. Any field NOT listed will be excluded.
 * <p>
 * This is the inverse of {@link IoIgnoreProperties}, which specifies fields to exclude.
 * If both {@code @IoIncludeProperties} and {@code @IoIgnoreProperties} are present on the
 * same class, the whitelist is applied first, then the blacklist removes from that set.
 * <p>
 * Fields are matched by their Java field name, not the serialized name (i.e., before
 * any {@link IoProperty} rename is applied).
 *
 * <pre>{@code
 * @IoIncludeProperties({"name", "email"})
 * public class User {
 *     private String name;       // included
 *     private String email;      // included
 *     private String password;   // excluded (not in whitelist)
 *     private int age;           // excluded (not in whitelist)
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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoIncludeProperties {
    /**
     * Names of fields to include in serialization and deserialization.
     * All other fields will be excluded.
     */
    String[] value();
}
