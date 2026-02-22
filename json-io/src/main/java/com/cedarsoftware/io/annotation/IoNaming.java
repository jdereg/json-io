package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a class-level naming strategy that transforms all Java field names
 * to a different convention during serialization and deserialization.
 * <p>
 * Fields with an explicit {@link IoProperty} annotation are not affected —
 * {@code @IoProperty} always takes precedence over {@code @IoNaming}.
 *
 * <pre>{@code
 * @IoNaming(IoNaming.Strategy.SNAKE_CASE)
 * public class UserProfile {
 *     private String firstName;    // serialized as "first_name"
 *     private String lastName;     // serialized as "last_name"
 *
 *     @IoProperty("uid")
 *     private String userId;       // serialized as "uid" (@IoProperty wins)
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
public @interface IoNaming {
    /**
     * The naming strategy to apply to all fields in this class.
     */
    Strategy value();

    /**
     * Available naming strategies for field name transformation.
     */
    enum Strategy {
        /** Convert camelCase to snake_case: {@code firstName → first_name} */
        SNAKE_CASE,
        /** Convert camelCase to kebab-case: {@code firstName → first-name} */
        KEBAB_CASE,
        /** Convert camelCase to UpperCamelCase: {@code firstName → FirstName} */
        UPPER_CAMEL_CASE,
        /** Convert camelCase to lower.dot.case: {@code firstName → first.name} */
        LOWER_DOT_CASE
    }
}
