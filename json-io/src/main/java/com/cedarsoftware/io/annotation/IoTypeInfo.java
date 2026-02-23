package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level annotation that specifies the default concrete type to use during
 * deserialization when no {@code @type} metadata is present in the JSON for this field.
 * <p>
 * This is useful for polymorphic fields declared as interfaces or abstract classes
 * (e.g., {@code Object}, {@code List}, {@code Map}) where json-io cannot determine
 * the concrete type from the JSON alone.
 * <p>
 * If {@code @type} IS present in the JSON, it takes precedence over this annotation.
 * This annotation only serves as a default/fallback type hint.
 *
 * <pre>{@code
 * public class Container {
 *     @IoTypeInfo(ArrayList.class)
 *     private Object items;     // defaults to ArrayList when @type is absent
 *
 *     @IoTypeInfo(LinkedHashMap.class)
 *     private Map<String, Object> data;  // defaults to LinkedHashMap
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
public @interface IoTypeInfo {
    /**
     * The default concrete class to use when deserializing this field
     * and no {@code @type} information is present in the JSON.
     */
    Class<?> value();
}
