package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor or static factory method as the preferred deserialization creator.
 * When present, json-io uses the annotated constructor or method instead of its default
 * constructor discovery heuristics.
 * <p>
 * Parameters are matched by name from JSON keys. Use {@link IoProperty} on parameters
 * to match renamed JSON keys.
 *
 * <pre>{@code
 * public class Money {
 *     private final BigDecimal amount;
 *     private final String currency;
 *
 *     @IoCreator
 *     public Money(@IoProperty("amount") BigDecimal amt,
 *                  @IoProperty("ccy") String currency) {
 *         this.amount = amt;
 *         this.currency = currency;
 *     }
 * }
 * }</pre>
 *
 * <p>For static factory methods, the method must return the declaring class (or a subclass):
 *
 * <pre>{@code
 * public class Color {
 *     @IoCreator
 *     public static Color of(@IoProperty("r") int red,
 *                            @IoProperty("g") int green,
 *                            @IoProperty("b") int blue) {
 *         return new Color(red, green, blue);
 *     }
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
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoCreator {
}
