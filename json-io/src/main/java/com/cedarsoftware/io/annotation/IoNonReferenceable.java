package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as non-referenceable. Instances of this class will never emit {@code @id}/{@code @ref}
 * pairs during serialization. Each occurrence is written as a full value, even if the same instance
 * appears multiple times in the object graph.
 * <p>
 * Use this for immutable, value-like classes where instance sharing is not important and
 * readability of the JSON output is preferred over memory optimization during deserialization.
 * <p>
 * This is the annotation equivalent of the {@code config/nonRefs.txt} configuration file
 * and the {@code addNonReferenceableClass()} programmatic API.
 *
 * <pre>{@code
 * @IoNonReferenceable
 * public class Money {
 *     private final BigDecimal amount;
 *     private final String currency;
 *     // ...
 * }
 * // Instances are always written in full — no @id/@ref
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
public @interface IoNonReferenceable {
}
