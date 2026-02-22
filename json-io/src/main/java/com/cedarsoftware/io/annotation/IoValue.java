package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a no-arg instance method whose return value becomes the serialized form of the object.
 * When present, json-io calls this method instead of performing field-by-field serialization.
 * <p>
 * On the read side, pair with {@link IoCreator} on a constructor or static factory that accepts
 * the value type returned by this method.
 *
 * <pre>{@code
 * public class EmailAddress {
 *     private final String address;
 *
 *     @IoCreator
 *     public EmailAddress(@IoProperty("address") String address) {
 *         this.address = address;
 *     }
 *
 *     @IoValue
 *     public String toValue() {
 *         return address;
 *     }
 * }
 * // Serializes as: "user@example.com" instead of {"address":"user@example.com"}
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
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoValue {
}
