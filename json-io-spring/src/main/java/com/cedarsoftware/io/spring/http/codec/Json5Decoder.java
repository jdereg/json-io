package com.cedarsoftware.io.spring.http.codec;

import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.springframework.lang.Nullable;

/**
 * Reactive Decoder for JSON5 format using json-io.
 * <p>
 * Handles {@code application/vnd.json5} media type for WebFlux and WebClient.
 * JSON5 extends JSON with comments, trailing commas, unquoted keys, and more.
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
public class Json5Decoder extends AbstractJsonIoDecoder {

    public Json5Decoder() {
        this(null);
    }

    public Json5Decoder(@Nullable ReadOptions readOptions) {
        super(readOptions, JsonIoMediaTypes.APPLICATION_JSON5);
    }

    @Override
    protected ReadOptions createDefaultReadOptions() {
        return new ReadOptionsBuilder()
                .closeStream(false)
                .allowNanAndInfinity(true)  // JSON5 supports Infinity and NaN
                .build();
    }
}
