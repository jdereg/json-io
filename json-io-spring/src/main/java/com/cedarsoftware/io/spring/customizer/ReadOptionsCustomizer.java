package com.cedarsoftware.io.spring.customizer;

import com.cedarsoftware.io.ReadOptionsBuilder;

/**
 * Callback interface for customizing json-io ReadOptions.
 * <p>
 * Implement this interface and register as a Spring bean to customize
 * how json-io reads/parses JSON:
 * </p>
 * <pre>
 * {@literal @}Bean
 * public ReadOptionsCustomizer myReadCustomizer() {
 *     return builder -&gt; builder
 *         .maxDepth(500)
 *         .addCustomReader(MyClass.class, new MyCustomReader());
 * }
 * </pre>
 * <p>
 * Multiple customizers are applied in order. Use {@literal @}Order annotation
 * to control ordering if needed.
 * </p>
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
@FunctionalInterface
public interface ReadOptionsCustomizer {

    /**
     * Customize the ReadOptionsBuilder.
     *
     * @param builder the builder to customize
     */
    void customize(ReadOptionsBuilder builder);
}
