package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the "any getter" for extra fields during serialization.
 * The annotated method provides additional key-value pairs that are written as
 * JSON fields alongside the regular declared fields.
 *
 * <p>The annotated method must:</p>
 * <ul>
 *   <li>Be a non-static instance method</li>
 *   <li>Accept no parameters</li>
 *   <li>Return a {@code Map<String, Object>} (or any {@code Map} subtype)</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>
 * public class FlexibleConfig {
 *     private String name;
 *     private Map&lt;String, Object&gt; extras = new LinkedHashMap&lt;&gt;();
 *
 *     &#64;IoAnyGetter
 *     public Map&lt;String, Object&gt; getExtras() {
 *         return extras;
 *     }
 * }
 * </pre>
 *
 * <p>Extra fields from {@code @IoAnyGetter} are written after the regular declared fields.
 * Null values in the returned Map are subject to the {@code skipNullFields} setting.</p>
 *
 * <p>Equivalent to Jackson's {@code @JsonAnyGetter}. json-io reflectively honors
 * {@code @JsonAnyGetter} when Jackson annotations are on the classpath.</p>
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
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IoAnyGetter {
}
