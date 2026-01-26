package com.cedarsoftware.io.spring.http.codec;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.MimeType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Abstract base class for json-io reactive Decoders.
 * <p>
 * Provides common functionality for JSON, JSON5, and TOON decoders.
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
public abstract class AbstractJsonIoDecoder extends AbstractDataBufferDecoder<Object> {

    protected final ReadOptions readOptions;
    private final List<MimeType> supportedMimeTypes;

    protected AbstractJsonIoDecoder(ReadOptions readOptions, MimeType... mimeTypes) {
        super(mimeTypes);
        this.readOptions = readOptions != null ? readOptions : createDefaultReadOptions();
        this.supportedMimeTypes = List.of(mimeTypes);
    }

    protected ReadOptions createDefaultReadOptions() {
        return new ReadOptionsBuilder()
                .closeStream(false)
                .build();
    }

    @Override
    public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
        return super.canDecode(elementType, mimeType) && !CharSequence.class.isAssignableFrom(elementType.toClass());
    }

    @Override
    public Flux<Object> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType,
                                @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
        // For streaming, we decode each buffer as a separate object
        return Flux.from(inputStream)
                .map(buffer -> decodeDataBuffer(buffer, elementType));
    }

    @Override
    public Mono<Object> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType,
                                      @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
        return DataBufferUtils.join(inputStream)
                .map(buffer -> decodeDataBuffer(buffer, elementType));
    }

    @Override
    public Object decode(DataBuffer buffer, ResolvableType targetType,
                         @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
        return decodeDataBuffer(buffer, targetType);
    }

    protected Object decodeDataBuffer(DataBuffer buffer, ResolvableType targetType) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);

            Class<?> clazz = targetType.toClass();
            return decodeBytes(bytes, clazz);
        } catch (Exception e) {
            throw new DecodingException("Could not decode: " + e.getMessage(), e);
        }
    }

    /**
     * Decode bytes to an object. Subclasses can override for format-specific decoding.
     */
    protected Object decodeBytes(byte[] bytes, Class<?> clazz) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        return JsonIo.toJava(inputStream, readOptions).asClass(clazz);
    }

    @Override
    public List<MimeType> getDecodableMimeTypes() {
        return supportedMimeTypes;
    }
}
