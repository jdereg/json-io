package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the JSON property name and/or directional access for a field
 * (or constructor/method parameter).
 *
 * <h3>Rename a field</h3>
 * <pre>{@code
 * public class Person {
 *     @IoProperty("full_name")
 *     private String name;
 *     private int age;
 * }
 * // Serializes as: {"full_name":"Alice","age":25}
 * }</pre>
 *
 * <h3>Restrict direction (read-only / write-only)</h3>
 * <p>
 * {@link #access()} matches Jackson's {@code @JsonProperty(access = ...)} semantics:
 * <ul>
 *   <li>{@link Access#READ_ONLY} — the value is <b>serialized</b> (written to JSON) but
 *       <b>not deserialized</b> (ignored on input). Useful for derived/computed fields surfaced
 *       to clients that should not be settable from JSON (e.g. {@code computedTotal}).</li>
 *   <li>{@link Access#WRITE_ONLY} — the value is <b>deserialized</b> (read from JSON) but
 *       <b>not serialized</b> (suppressed on output). Useful for secrets that may be set from
 *       JSON but must never appear in output (e.g. {@code passwordHash}).</li>
 *   <li>{@link Access#READ_WRITE} / {@link Access#AUTO} — no constraint (the default).</li>
 * </ul>
 * <pre>{@code
 * public class User {
 *     private String name;
 *
 *     @IoProperty(access = IoProperty.Access.WRITE_ONLY)
 *     private String passwordHash;     // accepted on input, never written
 *
 *     @IoProperty(access = IoProperty.Access.READ_ONLY)
 *     private long lastLoginEpoch;     // written on output, ignored on input
 * }
 * }</pre>
 * <p>
 * The {@link #value()} (rename) and {@link #access()} (direction) attributes are independent
 * and may be combined: {@code @IoProperty(value="pwd", access = Access.WRITE_ONLY)} renames
 * the field and makes it write-only.
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
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoProperty {
    /**
     * The JSON property name to use for this field. When empty (the default),
     * the field's Java name is used.
     */
    String value() default "";

    /**
     * Direction of access permitted for this field. Defaults to {@link Access#AUTO}
     * (no constraint — equivalent to {@link Access#READ_WRITE}).
     * <p>
     * Mirrors {@code com.fasterxml.jackson.annotation.JsonProperty.Access}; the
     * corresponding Jackson enum is consulted when {@code @IoProperty} is not present.
     */
    Access access() default Access.AUTO;

    /**
     * Direction of access permitted for a property.
     * <p>
     * <b>Naming convention:</b> these names describe the Java-bean perspective —
     * {@code READ_ONLY} means "the Java bean's getter is read; setter is not invoked."
     * Translated to the JSON direction: {@code READ_ONLY} fields appear in <i>output</i>
     * JSON but are ignored on <i>input</i>. {@code WRITE_ONLY} is the inverse.
     */
    enum Access {
        /** Default — no constraint. Equivalent to {@link #READ_WRITE} for json-io. */
        AUTO,
        /** Serialized only — included in JSON output, ignored on input. */
        READ_ONLY,
        /** Deserialized only — accepted from JSON input, suppressed on output. */
        WRITE_ONLY,
        /** Explicit "both directions" — same effect as {@link #AUTO}. */
        READ_WRITE
    }
}
