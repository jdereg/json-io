package com.cedarsoftware.io.spring.ai.converter;

import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;

import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.lang.Nullable;

/**
 * A {@link ToolCallResultConverter} that serializes tool call results to TOON format
 * using json-io, producing ~40-50% fewer tokens than JSON for LLM consumption.
 *
 * <p>TOON (Token-Oriented Object Notation) is an indentation-based format optimized
 * for LLM token efficiency. This converter replaces the default Jackson-based
 * {@code DefaultToolCallResultConverter} with json-io's TOON serialization.</p>
 *
 * <p>Usage with {@code @Tool} annotation:</p>
 * <pre>{@code
 * @Tool(description = "Get customer", resultConverter = ToonToolCallResultConverter.class)
 * Customer getCustomer(Long id) { ... }
 * }</pre>
 *
 * <p>Usage with {@code FunctionToolCallback}:</p>
 * <pre>{@code
 * FunctionToolCallback.builder("myTool", myFunction)
 *     .toolCallResultConverter(new ToonToolCallResultConverter())
 *     .build();
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
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class ToonToolCallResultConverter implements ToolCallResultConverter {

    private final WriteOptions writeOptions;

    /**
     * Create a converter with default WriteOptions optimized for LLM consumption:
     * no type info and key folding enabled.
     */
    public ToonToolCallResultConverter() {
        this(null);
    }

    /**
     * Create a converter with custom WriteOptions.
     *
     * @param writeOptions custom options; if null, defaults optimized for LLM use are applied
     */
    public ToonToolCallResultConverter(@Nullable WriteOptions writeOptions) {
        if (writeOptions != null) {
            this.writeOptions = writeOptions;
        } else {
            this.writeOptions = new WriteOptionsBuilder()
                    .showTypeInfoNever()
                    .toonKeyFolding(true)
                    .build();
        }
    }

    @Override
    public String convert(@Nullable Object result, @Nullable Type returnType) {
        if (result == null) {
            return "";
        }
        if (result instanceof String s) {
            return s;
        }
        return JsonIo.toToon(result, writeOptions);
    }
}
