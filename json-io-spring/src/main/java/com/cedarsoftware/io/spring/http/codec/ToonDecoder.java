package com.cedarsoftware.io.spring.http.codec;

import java.io.ByteArrayInputStream;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.springframework.lang.Nullable;

/**
 * Reactive Decoder for TOON (Token-Oriented Object Notation) format.
 * <p>
 * Handles {@code application/vnd.toon} media type for WebFlux and WebClient.
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
public class ToonDecoder extends AbstractJsonIoDecoder {

    public ToonDecoder() {
        this(null);
    }

    public ToonDecoder(@Nullable ReadOptions readOptions) {
        super(readOptions, JsonIoMediaTypes.APPLICATION_TOON, JsonIoMediaTypes.APPLICATION_TOON_JSON);
    }

    @Override
    protected Object decodeBytes(byte[] bytes, Class<?> clazz) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        return JsonIo.fromToon(inputStream, readOptions).asClass(clazz);
    }
}
