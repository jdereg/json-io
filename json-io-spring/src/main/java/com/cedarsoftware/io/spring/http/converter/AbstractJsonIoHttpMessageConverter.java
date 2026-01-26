package com.cedarsoftware.io.spring.http.converter;

import java.io.IOException;
import java.lang.reflect.Type;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.ReadOptions;
import com.cedarsoftware.io.ReadOptionsBuilder;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

/**
 * Abstract base class for json-io HttpMessageConverters.
 * <p>
 * Provides common functionality for JSON, JSON5, and TOON converters.
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
public abstract class AbstractJsonIoHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    protected final ReadOptions readOptions;
    protected final WriteOptions writeOptions;

    /**
     * Create a new converter with default options.
     *
     * @param supportedMediaTypes the media types supported by this converter
     */
    protected AbstractJsonIoHttpMessageConverter(MediaType... supportedMediaTypes) {
        this(null, null, supportedMediaTypes);
    }

    /**
     * Create a new converter with custom options.
     *
     * @param readOptions         read options (null for defaults)
     * @param writeOptions        write options (null for defaults)
     * @param supportedMediaTypes the media types supported by this converter
     */
    protected AbstractJsonIoHttpMessageConverter(@Nullable ReadOptions readOptions,
                                                  @Nullable WriteOptions writeOptions,
                                                  MediaType... supportedMediaTypes) {
        super(supportedMediaTypes);
        this.readOptions = readOptions != null ? readOptions : createDefaultReadOptions();
        this.writeOptions = writeOptions != null ? writeOptions : createDefaultWriteOptions();
    }

    /**
     * Create default ReadOptions for this converter.
     * Subclasses can override to provide format-specific defaults.
     */
    protected ReadOptions createDefaultReadOptions() {
        return new ReadOptionsBuilder()
                .closeStream(false)  // Spring manages stream lifecycle
                .build();
    }

    /**
     * Create default WriteOptions for this converter.
     * Subclasses can override to provide format-specific defaults.
     */
    protected WriteOptions createDefaultWriteOptions() {
        return new WriteOptionsBuilder()
                .closeStream(false)  // Spring manages stream lifecycle
                .build();
    }

    @Override
    public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
        return supports(clazz) && canRead(mediaType);
    }

    @Override
    public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
        Class<?> clazz = resolveClass(type);
        return supports(clazz) && canRead(mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
        return supports(clazz) && canWrite(mediaType);
    }

    @Override
    public boolean canWrite(@Nullable Type type, Class<?> clazz, @Nullable MediaType mediaType) {
        return supports(clazz) && canWrite(mediaType);
    }

    /**
     * Check if this converter supports the given class.
     * Excludes primitive types and String which should be handled by other converters.
     */
    protected boolean supports(Class<?> clazz) {
        // Don't handle primitive types, their wrappers, String, or byte arrays
        // These have dedicated converters in Spring
        if (clazz == null) {
            return false;
        }
        if (clazz.isPrimitive()) {
            return false;
        }
        if (clazz == String.class ||
            clazz == byte[].class ||
            clazz == Byte[].class ||
            clazz == Character.class ||
            clazz == Boolean.class ||
            clazz == Integer.class ||
            clazz == Long.class ||
            clazz == Short.class ||
            clazz == Float.class ||
            clazz == Double.class) {
            return false;
        }
        return true;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        return readFromStream(inputMessage, clazz);
    }

    @Override
    public Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        Class<?> clazz = resolveClass(type);
        return readFromStream(inputMessage, clazz);
    }

    @Override
    protected void writeInternal(Object object, @Nullable Type type, HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        writeToStream(object, outputMessage);
    }

    /**
     * Read an object from the input stream.
     */
    protected Object readFromStream(HttpInputMessage inputMessage, Class<?> clazz) throws IOException {
        try {
            return JsonIo.toJava(inputMessage.getBody(), readOptions).asClass(clazz);
        } catch (Exception e) {
            throw new HttpMessageNotReadableException("Could not read JSON: " + e.getMessage(), e, inputMessage);
        }
    }

    /**
     * Write an object to the output stream.
     */
    protected void writeToStream(Object object, HttpOutputMessage outputMessage) throws IOException {
        try {
            JsonIo.toJson(outputMessage.getBody(), object, writeOptions);
        } catch (Exception e) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve a Type to a Class.
     */
    protected Class<?> resolveClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof java.lang.reflect.ParameterizedType) {
            Type rawType = ((java.lang.reflect.ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }
        return Object.class;
    }
}
