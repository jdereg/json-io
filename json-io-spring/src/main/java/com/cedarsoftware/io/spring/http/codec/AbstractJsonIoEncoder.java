package com.cedarsoftware.io.spring.http.codec;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstract base class for json-io reactive Encoders.
 * <p>
 * Provides common functionality for JSON, JSON5, and TOON encoders.
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
public abstract class AbstractJsonIoEncoder extends AbstractEncoder<Object> {

    protected final WriteOptions writeOptions;
    private final List<MimeType> supportedMimeTypes;

    protected AbstractJsonIoEncoder(WriteOptions writeOptions, MimeType... mimeTypes) {
        super(mimeTypes);
        this.writeOptions = writeOptions != null ? writeOptions : createDefaultWriteOptions();
        this.supportedMimeTypes = List.of(mimeTypes);
    }

    protected WriteOptions createDefaultWriteOptions() {
        return new WriteOptionsBuilder()
                .closeStream(false)
                .build();
    }

    @Override
    public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
        return super.canEncode(elementType, mimeType) && !CharSequence.class.isAssignableFrom(elementType.toClass());
    }

    @Override
    public Flux<DataBuffer> encode(Publisher<?> inputStream, DataBufferFactory bufferFactory,
                                    ResolvableType elementType, @Nullable MimeType mimeType,
                                    @Nullable Map<String, Object> hints) {
        return Flux.from(inputStream)
                .map(value -> encodeValue(value, bufferFactory));
    }

    @Override
    public DataBuffer encodeValue(Object value, DataBufferFactory bufferFactory,
                                   ResolvableType valueType, @Nullable MimeType mimeType,
                                   @Nullable Map<String, Object> hints) {
        return encodeValue(value, bufferFactory);
    }

    protected DataBuffer encodeValue(Object value, DataBufferFactory bufferFactory) {
        try {
            byte[] bytes = encodeToBytes(value);
            DataBuffer buffer = bufferFactory.allocateBuffer(bytes.length);
            buffer.write(bytes);
            return buffer;
        } catch (Exception e) {
            throw new EncodingException("Could not encode: " + e.getMessage(), e);
        }
    }

    /**
     * Encode an object to bytes. Subclasses can override for format-specific encoding.
     */
    protected byte[] encodeToBytes(Object value) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JsonIo.toJson(outputStream, value, writeOptions);
        return outputStream.toByteArray();
    }

    @Override
    public List<MimeType> getEncodableMimeTypes() {
        return supportedMimeTypes;
    }
}
