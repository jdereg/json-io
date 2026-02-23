package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method as the getter for a specific field during serialization.
 * The method must be a no-arg instance method with a non-void return type.
 * <p>
 * When present, json-io calls this method to retrieve the field value instead of using
 * the standard {@code getXxx()} convention or direct field access.
 *
 * <pre>{@code
 * public class Sensor {
 *     private double temperature;
 *
 *     @IoGetter("temperature")
 *     public double readTemperature() {
 *         return temperature;
 *     }
 * }
 * }</pre>
 *
 * <b>Priority:</b> Programmatic API ({@code addNonStandardGetter()}) &gt;
 * {@code @IoGetter} &gt; {@code @JsonGetter} &gt; standard {@code getXxx()} convention.
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
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoGetter {
    /**
     * The Java field name this method provides a getter for.
     */
    String value();
}
