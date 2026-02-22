package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls the inclusion of a field during JSON serialization.
 * When set to {@link Include#NON_NULL}, the field is omitted from JSON output
 * if its value is {@code null}, regardless of the global {@code skipNullFields} setting.
 *
 * <pre>{@code
 * public class Response {
 *     @IoInclude(IoInclude.Include.NON_NULL)
 *     private String optionalMessage;
 *     private String status;
 * }
 * // If optionalMessage is null, it will not appear in JSON output.
 * // status will always appear, even if null (unless global skipNullFields is true).
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
public @interface IoInclude {
    /**
     * The inclusion rule for this field. Default is {@link Include#ALWAYS}.
     */
    Include value() default Include.ALWAYS;

    /**
     * Inclusion strategies for field serialization.
     */
    enum Include {
        /** Always include the field in JSON output, even if null. */
        ALWAYS,
        /** Exclude the field from JSON output if its value is null. */
        NON_NULL
    }
}
