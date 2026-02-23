package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an instance method as the setter for a specific field during deserialization.
 * The method must be a single-argument instance method.
 * <p>
 * When present, json-io calls this method to inject the field value instead of using
 * the standard {@code setXxx()} convention or direct field access.
 *
 * <pre>{@code
 * public class Sensor {
 *     private double temperature;
 *
 *     @IoSetter("temperature")
 *     public void calibrateTemperature(double temp) {
 *         this.temperature = temp;
 *     }
 * }
 * }</pre>
 *
 * <b>Priority:</b> Programmatic API (config files / {@code addNonStandardSetter()}) &gt;
 * {@code @IoSetter} &gt; {@code @JsonSetter} &gt; standard {@code setXxx()} convention.
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
public @interface IoSetter {
    /**
     * The Java field name this method provides a setter for.
     */
    String value();
}
