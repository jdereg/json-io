package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.IOUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * Internal class that orchestrates JSON parsing and object resolution.
 * <p>
 * For most use cases, prefer using {@link JsonIo} which provides a cleaner API:
 * <pre>
 * // Parse JSON to Java objects
 * Person person = JsonIo.toJava(jsonString, readOptions).asClass(Person.class);
 *
 * // Parse JSON to Map-of-Maps
 * Map map = JsonIo.toMaps(jsonString, readOptions).asClass(Map.class);
 * </pre>
 * <p>
 * JsonReader is used internally and by {@link ClassFactory} implementations that need
 * to resolve nested JsonObjects during custom object construction.
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
public class JsonReader implements Closeable
{
    private final FastReader input;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final JsonParser parser;
    private final boolean isRoot;

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.ClassFactory} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface ClassFactory extends com.cedarsoftware.io.ClassFactory {
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.MissingFieldHandler} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface MissingFieldHandler extends com.cedarsoftware.io.MissingFieldHandler {
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.JsonClassReader} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface JsonClassReader<T> extends com.cedarsoftware.io.JsonClassReader<T> {
    }

    private FastReader getReader(InputStream inputStream) {
        return new FastReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 65536, 16);
    }

    private FastReader getReader(Reader reader) {
        return new FastReader(reader, 65536, 16);
    }

    /**
     * Creates a json reader using custom read options
     * @param input         InputStream of utf-encoded json
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(InputStream input, ReadOptions readOptions) {
        this(input, readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions,
             new Resolver.DefaultReferenceTracker(readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions));
    }

    /**
     * Creates a json reader from a Reader (character-based input) using custom read options.
     * This constructor is more efficient than the InputStream constructor when starting from a String,
     * as it avoids unnecessary encoding/decoding.
     * @param reader Reader providing JSON characters
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(Reader reader, ReadOptions readOptions) {
        this(reader, readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions,
             new Resolver.DefaultReferenceTracker(readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions));
    }

    // Package-private: creates parser with custom ReferenceTracker
    JsonReader(InputStream inputStream, ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(inputStream);
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.parser = new JsonParser(this.input, this.resolver);
    }

    // Package-private: creates parser with custom ReferenceTracker
    JsonReader(Reader reader, ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(reader);
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.parser = new JsonParser(this.input, this.resolver);
    }

    /**
     * Creates a JsonReader for resolving existing JsonObject graphs (no stream parsing).
     * After construction, call {@link #toJava(Type, Object)} to convert JsonObjects to Java.
     *
     * @param readOptions ReadOptions for configuration (null uses defaults)
     */
    public JsonReader(ReadOptions readOptions) {
        this(readOptions, new Resolver.DefaultReferenceTracker(readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions));
    }

    // Package-private: resolver-only (no stream parsing)
    JsonReader(ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.input = null;
        this.parser = null;
    }

    /**
     * Creates a JsonReader for nested resolution within ClassFactory or custom reader implementations.
     * Call {@link #toJava(Type, Object)} to resolve JsonObject sub-graphs into Java objects.
     *
     * @param resolver Resolver from the parent context (via ClassFactory or JsonClassReader)
     */
    public JsonReader(Resolver resolver) {
        this.isRoot = false;  // Nested reader - cleanup handled by root
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        this.input = null;
        this.parser = null;
    }

    /**
     * Parses JSON from the input stream and returns a Java object of the specified type.
     *
     * @param <T>      the expected return type
     * @param rootType the target type (null to infer from JSON @type or use Object)
     * @return the deserialized Java object, or null if JSON is null
     * @throws JsonIoException if parsing fails or type conversion is not possible
     */
    public <T> T readObject(Type rootType) {
        final T returnValue;
        try {
            returnValue = (T) parser.readValue(rootType);
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException(getErrorMessage("error parsing JSON value"), e);
        }
        return (T) toJava(rootType, returnValue);
    }

    /**
     * Resolves a parsed JSON value to a Java object. Handles lifecycle management.
     * Used by ClassFactory and JsonClassReader implementations for nested resolution.
     */
    public Object toJava(Type rootType, Object root) {
        boolean shouldManageUnsafe = readOptions.isUseUnsafe() && isRoot;
        if (readOptions.isUseUnsafe()) {
            ClassUtilities.setUseUnsafe(true);
        }

        try {
            return resolver.resolveRoot(root, rootType);
        } catch (Exception e) {
            if (readOptions.isCloseStream()) {
                ExceptionUtilities.safelyIgnoreException(this::close);
            }
            if (e instanceof JsonIoException) {
                throw (JsonIoException) e;
            }
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        } finally {
            if (shouldManageUnsafe) {
                ClassUtilities.setUseUnsafe(false);
            }
            if (isRoot) {
                resolver.cleanup();
            }
        }
    }

    /**
     * Deserializes a JSON object graph into a strongly-typed Java object instance.
     * <p>
     * This method converts a {@link JsonObject} (Map-of-Maps representation) into a fully
     * resolved Java object. It delegates to {@link #toJava(Type, Object)} which handles
     * lifecycle management including unsafe mode, forward reference patching, and cleanup.
     *
     * @param <T>      the expected type of the Java object to be returned
     * @param rootObj  the root {@link JsonObject} representing the serialized JSON object graph
     * @param rootType the desired Java type for the root object (may be null for type inference)
     * @return a Java object instance corresponding to the provided JSON graph
     * @throws JsonIoException if an error occurs during deserialization
     */
    @SuppressWarnings("unchecked")
    protected <T> T resolveObjects(JsonObject rootObj, Type rootType) {
        return (T) toJava(rootType, rootObj);
    }

    /**
     * Returns the Resolver instance used for JSON deserialization.
     * Used by {@link ClassFactory} implementations to resolve nested JsonObjects.
     *
     * @return the Resolver (either ObjectResolver or MapResolver depending on ReadOptions)
     */
    public Resolver getResolver() {
        return resolver;
    }
    
    public void close() {
        IOUtilities.close(input);
    }

    private String getErrorMessage(String msg) {
        if (input == null) {
            return msg;
        }
        return msg + "\nLast read: " + input.getLastSnippet() +
               "\nline: " + input.getLine() + ", col: " + input.getCol();
    }
}
