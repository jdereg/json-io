package com.cedarsoftware.io.spring.http.converter;

import java.io.IOException;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

/**
 * HttpMessageConverter for TOON (Terse Object Oriented Notation) format.
 * <p>
 * Handles {@code application/vnd.toon} and {@code application/vnd.toon+json} media types.
 * </p>
 * <p>
 * TOON is an optimized notation format that reduces token count by 40-50%,
 * making it ideal for LLM/AI applications where token efficiency matters.
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
public class ToonHttpMessageConverter extends AbstractJsonIoHttpMessageConverter {

    /**
     * Create a new converter with default options.
     */
    public ToonHttpMessageConverter() {
        super(JsonIoMediaTypes.APPLICATION_TOON, JsonIoMediaTypes.APPLICATION_TOON_JSON);
    }

    /**
     * Create a new converter with custom options.
     *
     * @param readOptions  read options (null for defaults)
     * @param writeOptions write options (null for defaults)
     */
    public ToonHttpMessageConverter(@Nullable ReadOptions readOptions, @Nullable WriteOptions writeOptions) {
        super(readOptions, writeOptions, JsonIoMediaTypes.APPLICATION_TOON, JsonIoMediaTypes.APPLICATION_TOON_JSON);
    }

    @Override
    protected Object readFromStream(HttpInputMessage inputMessage, Class<?> clazz) throws IOException {
        try {
            // TOON uses the same reader as JSON5 - json-io's parser handles both formats
            return JsonIo.fromToon(inputMessage.getBody(), readOptions).asClass(clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("Could not read TOON: " + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeToStream(Object object, HttpOutputMessage outputMessage) throws IOException {
        try {
            // Use TOON output format
            JsonIo.toToon(outputMessage.getBody(), object, writeOptions);
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("Could not write TOON: " + e.getMessage(), e);
        }
    }
}
