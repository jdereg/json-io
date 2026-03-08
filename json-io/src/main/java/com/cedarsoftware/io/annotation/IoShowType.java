package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation that forces {@code @type} metadata emission for this field's value,
 * regardless of the global {@code showTypeInfo} setting.
 * <p>
 * This is essential for polymorphic fields — where the runtime type differs from the declared type
 * and the reader needs type information to instantiate the correct concrete class.
 * <p>
 * Behavior by field type:
 * <ul>
 *   <li><b>Plain field:</b> emits {@code @type} on the field value itself</li>
 *   <li><b>Collection/List:</b> emits {@code @type} on each element</li>
 *   <li><b>Array:</b> emits {@code @type} on each element</li>
 *   <li><b>Map values:</b> emits {@code @type} on each value</li>
 *   <li><b>Map keys:</b> emits {@code @type} on each key (when keys are non-String objects)</li>
 * </ul>
 * <p>
 * Primitive and native JSON types (String, boolean, numbers) are never affected —
 * they don't need {@code @type} regardless of this annotation.
 *
 * <pre>{@code
 * public class Fleet {
 *     @IoShowType
 *     private List<Vehicle> vehicles;  // Car, Truck, Van get @type
 *
 *     @IoShowType
 *     private Vehicle primary;         // concrete subclass gets @type
 *
 *     private String name;             // unaffected — no @type needed
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
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoShowType {
}
