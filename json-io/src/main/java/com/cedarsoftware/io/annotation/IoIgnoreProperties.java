package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level annotation that excludes named properties from JSON serialization and/or
 * deserialization. Mirrors Jackson's {@code @JsonIgnoreProperties}, including the
 * directional escapes {@link #allowGetters()} and {@link #allowSetters()}.
 *
 * <h3>Standard usage — exclude on both sides</h3>
 * <pre>{@code
 * @IoIgnoreProperties({"password", "secretKey"})
 * public class User {
 *     private String name;
 *     private String password;
 *     private String secretKey;
 * }
 * // "password" and "secretKey" never appear in JSON output, and are ignored on input.
 * }</pre>
 *
 * <h3>Directional escapes</h3>
 * <ul>
 *   <li>{@code allowGetters = true} — the named properties remain in <b>output</b> JSON
 *       (the getter is invoked); only <b>input</b> JSON is ignored. Useful for
 *       computed/derived properties surfaced to clients.</li>
 *   <li>{@code allowSetters = true} — the named properties may still be supplied by
 *       <b>input</b> JSON (the setter is invoked); only <b>output</b> JSON suppresses them.
 *       Useful for secrets that may arrive on input but must never appear in output.</li>
 *   <li>Both {@code true} — no exclusion (effectively a no-op for the listed fields).</li>
 * </ul>
 * <pre>{@code
 * @IoIgnoreProperties(value = {"passwordHash"}, allowSetters = true)
 * public class Credentials {
 *     private String username;
 *     private String passwordHash;  // accepted on input, suppressed on output
 * }
 *
 * @IoIgnoreProperties(value = {"computedTotal"}, allowGetters = true)
 * public class Cart {
 *     private List<Item> items;
 *     private BigDecimal computedTotal;  // written on output, ignored on input
 * }
 * }</pre>
 *
 * <p>Equivalent to using {@code @IoProperty(access = ...)} on each field individually,
 * but applied at the class level — useful when annotating a class you can't touch each
 * field of (e.g., a generated DTO).
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
     * Names of fields to exclude. By default, exclusion applies to both serialization
     * and deserialization; see {@link #allowGetters()} and {@link #allowSetters()} for
     * directional escapes.
     */
    String[] value();

    /**
     * When {@code true}, the listed properties are still written to JSON during
     * serialization (only input/deserialization is ignored). Default {@code false}
     * (full both-sides exclusion).
     */
    boolean allowGetters() default false;

    /**
     * When {@code true}, the listed properties are still read from JSON during
     * deserialization (only output/serialization is suppressed). Default {@code false}
     * (full both-sides exclusion).
     */
    boolean allowSetters() default false;
}
