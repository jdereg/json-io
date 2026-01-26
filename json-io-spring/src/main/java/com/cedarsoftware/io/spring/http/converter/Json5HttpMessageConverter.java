package com.cedarsoftware.io.spring.http.converter;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.springframework.lang.Nullable;

/**
 * HttpMessageConverter for JSON5 format using json-io.
 * <p>
 * Handles {@code application/vnd.json5} media type.
 * JSON5 extends JSON with features like:
 * </p>
 * <ul>
 *   <li>Comments (single-line and multi-line)</li>
 *   <li>Trailing commas in arrays and objects</li>
 *   <li>Unquoted object keys (when valid identifiers)</li>
 *   <li>Single-quoted strings</li>
 *   <li>Multi-line strings</li>
 *   <li>Additional number formats (hex, +Infinity, -Infinity, NaN)</li>
 * </ul>
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
public class Json5HttpMessageConverter extends AbstractJsonIoHttpMessageConverter {

    /**
     * Create a new converter with default options.
     */
    public Json5HttpMessageConverter() {
        super(JsonIoMediaTypes.APPLICATION_JSON5);
    }

    /**
     * Create a new converter with custom options.
     *
     * @param readOptions  read options (null for defaults)
     * @param writeOptions write options (null for defaults)
     */
    public Json5HttpMessageConverter(@Nullable ReadOptions readOptions, @Nullable WriteOptions writeOptions) {
        super(readOptions, writeOptions, JsonIoMediaTypes.APPLICATION_JSON5);
    }

    @Override
    protected ReadOptions createDefaultReadOptions() {
        // JSON5 reader is configured via the parser automatically
        // when json-io detects JSON5 syntax
        return new ReadOptionsBuilder()
                .closeStream(false)
                .allowNanAndInfinity(true)  // JSON5 supports Infinity and NaN
                .build();
    }
}
