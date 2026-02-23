package com.cedarsoftware.io.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the "any setter" for unrecognized fields during deserialization.
 * When JSON contains fields that do not correspond to any declared field on the target class,
 * this method is invoked for each unrecognized field.
 *
 * <p>The annotated method must:</p>
 * <ul>
 *   <li>Be a non-static instance method</li>
 *   <li>Accept exactly two parameters: {@code (String fieldName, Object value)}</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>
 * public class FlexibleConfig {
 *     private String name;
 *     private Map&lt;String, Object&gt; extras = new LinkedHashMap&lt;&gt;();
 *
 *     &#64;IoAnySetter
 *     public void handleUnknown(String key, Object value) {
 *         extras.put(key, value);
 *     }
 * }
 * </pre>
 *
 * <p>{@code @IoAnySetter} takes priority over the global {@code MissingFieldHandler}
 * configured via {@code ReadOptionsBuilder}. If both are present, the annotation wins.</p>
 *
 * <p>Equivalent to Jackson's {@code @JsonAnySetter}. json-io reflectively honors
 * {@code @JsonAnySetter} when Jackson annotations are on the classpath.</p>
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
public @interface IoAnySetter {
}
