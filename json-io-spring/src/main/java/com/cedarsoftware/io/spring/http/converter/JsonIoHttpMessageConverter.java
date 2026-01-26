package com.cedarsoftware.io.spring.http.converter;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.springframework.lang.Nullable;

/**
 * HttpMessageConverter for standard JSON format using json-io.
 * <p>
 * Handles {@code application/json} media type.
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
public class JsonIoHttpMessageConverter extends AbstractJsonIoHttpMessageConverter {

    /**
     * Create a new converter with default options.
     */
    public JsonIoHttpMessageConverter() {
        super(JsonIoMediaTypes.APPLICATION_JSON);
    }

    /**
     * Create a new converter with custom options.
     *
     * @param readOptions  read options (null for defaults)
     * @param writeOptions write options (null for defaults)
     */
    public JsonIoHttpMessageConverter(@Nullable ReadOptions readOptions, @Nullable WriteOptions writeOptions) {
        super(readOptions, writeOptions, JsonIoMediaTypes.APPLICATION_JSON);
    }
}
