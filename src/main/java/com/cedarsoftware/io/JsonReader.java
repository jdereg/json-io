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
 * Read an object graph in JSON format and make it available in Java objects, or
 * in a "Map of Maps." (untyped representation).  This code handles cyclic references
 * and can deserialize any Object graph without requiring a class to be 'Serializable'
 * or have any specific methods on it.  It will handle classes with non-public constructors.
 * <br><br>
 * Usages:
 * <ul><li>
 * Call the static method: {@code JsonReader.jsonToJava(String json)}.  This will
 * return a typed Java object graph.</li>
 * <li>
 * Call the static method: {@code JsonReader.jsonToMaps(String json)}.  This will
 * return an untyped object representation of the JSON String as a Map of Maps, where
 * the fields are the Map keys, and the field values are the associated Map's values.  You can
 * call the JsonWriter.objectToJson() method with the returned Map, and it will serialize
 * the Graph into the equivalent JSON stream from which it was read.
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in)} and then call
 * {@code readObject()}.  Cast the return value of readObject() to the Java class that was the root of
 * the graph.
 * </li>
 * <li>
 * Instantiate the JsonReader with an InputStream: {@code JsonReader(InputStream in, true)} and then call
 * {@code readObject()}.  The return value will be a Map of Maps.
 * </li></ul><br>
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

    /**
     * Allow others to try potentially faster Readers.
     * @param inputStream InputStream that will be offering JSON.
     * @return FastReader wrapped around the passed in inputStream, translating from InputStream to InputStreamReader.
     */
    protected FastReader getReader(InputStream inputStream) {
        return new FastReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 65536, 16);
    }

    /**
     * Allow others to try potentially faster Readers.
     * @param reader Reader that will be offering JSON characters.
     * @return FastReader wrapped around the passed in reader.
     */
    protected FastReader getReader(Reader reader) {
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

    /**
     * Internal constructor for InputStream with custom ReferenceTracker.
     * Package-private as ReferenceTracker management is an internal concern.
     */
    JsonReader(InputStream inputStream, ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;   // When root is true, the resolver has .cleanup() called on it upon JsonReader finalization
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(inputStream);
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.parser = new JsonParser(this.input, this.resolver);
    }

    /**
     * Internal constructor for Reader with custom ReferenceTracker.
     * Package-private as ReferenceTracker management is an internal concern.
     */
    JsonReader(Reader reader, ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;   // When root is true, the resolver has .cleanup() called on it upon JsonReader finalization
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.input = getReader(reader);
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        this.parser = new JsonParser(this.input, this.resolver);
    }

    /**
     * Use this constructor if you already have a JsonObject graph and want to parse it into
     * Java objects by calling jsonReader.jsonObjectsToJava(rootJsonObject) after constructing
     * the JsonReader. This constructor only sets up the resolver and converter needed for
     * object resolution, without creating unnecessary stream infrastructure.
     *
     * @param readOptions Read Options to turn on/off various feature options, or supply additional ClassFactory data,
     *                    etc. If null, readOptions will use all defaults.
     */
    public JsonReader(ReadOptions readOptions) {
        this(readOptions, new Resolver.DefaultReferenceTracker(readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions));
    }

    /**
     * Internal constructor for creating a JsonReader that only needs resolver/converter (no stream parsing).
     * Used when converting JsonObject graphs to Java objects without reading from a stream.
     */
    JsonReader(ReadOptions readOptions, ReferenceTracker references) {
        this.isRoot = true;
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        Converter converter = new Converter(this.readOptions.getConverterOptions());
        this.resolver = this.readOptions.isReturningJsonObjects() ?
                new MapResolver(this.readOptions, references, converter) :
                new ObjectResolver(this.readOptions, references, converter);
        // No stream/parser needed for this use case - only resolving existing JsonObjects
        this.input = null;
        this.parser = null;
    }

    /**
     * Use this constructor to resolve JsonObjects into Java, for example, in a ClassFactory or custom reader.
     * Then use jsonReader.jsonObjectsToJava(rootJsonObject) to turn JsonObject graph (sub-graph) into Java.
     * This constructor only sets up the resolver - no stream/parser is created since the graph is already read in.
     *
     * @param resolver Resolver, obtained from ClassFactory.newInstance() or CustomReader.read().
     */
    public JsonReader(Resolver resolver) {
        this.isRoot = false;    // If not root, .cleanup() is not called when the JsonReader is finalized
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        // No stream/parser needed - graph is already read in when using this API
        this.input = null;
        this.parser = null;
    }

    /**
     * Reads and parses a JSON structure from the underlying {@code parser}, then returns
     * an object of type {@code T}. The parsing and return type resolution follow these steps:
     *
     * <ul>
     *   <li>Attempts to parse the top-level JSON element (e.g. object, array, or primitive) and
     *   produce an initial {@code returnValue} of type {@code T} (as inferred or declared).</li>
     *   <li>If the parsed value is {@code null}, simply returns {@code null}.</li>
     *   <li>If the value is recognized as an “array-like” structure (either an actual Java array or
     *   a {@link JsonObject} flagged as an array), it delegates to {@code handleArrayRoot()} to build
     *   the final result object.</li>
     *   <li>If the value is a {@link JsonObject} at the root, it delegates to
     *   {@code determineReturnValueWhenJsonObjectRoot()} for further resolution.</li>
     *   <li>Otherwise (if it’s a primitive or other type), it may convert the result
     *   to {@code rootType} if needed/possible.</li>
     * </ul>
     *
     * @param <T>       The expected return type.
     * @param rootType  The class token representing the desired return type. May be {@code null},
     *                  in which case the type is inferred from the JSON content or defaults to
     *                  the parser’s best guess.
     * @return          The fully resolved and (if applicable) type-converted object.
     *                  Could be {@code null} if the JSON explicitly represents a null value.
     * @throws JsonIoException if there is any error parsing or resolving the JSON content,
     *         or if type conversion fails (e.g., when the actual type does not match
     *         the requested {@code rootType} and no valid conversion is available).
     */
    public <T> T readObject(Type rootType) {
        final T returnValue;
        try {
            // Attempt to parse the JSON into an object
            returnValue = (T) parser.readValue(rootType);
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            // Wrap any other exception
            throw new JsonIoException(getErrorMessage("error parsing JSON value"), e);
        }

        return (T) toJava(rootType, returnValue);
    }

    /**
     * Only use from ClassFactory or CustomReader.
     * Delegates to the Resolver's resolveRoot method which handles routing
     * based on the type of parsed value (array, object, or primitive).
     * Includes lifecycle management (unsafe mode, cleanup) for proper resolution.
     */
    public Object toJava(Type rootType, Object root) {
        // Enable unsafe mode for the entire resolution if requested
        boolean shouldManageUnsafe = false;
        if (readOptions.isUseUnsafe()) {
            ClassUtilities.setUseUnsafe(true);
            // Only the root JsonReader should manage (disable) unsafe mode at the end
            shouldManageUnsafe = isRoot;
        }

        try {
            return resolver.resolveRoot(root, rootType);
        } catch (Exception e) {
            // Safely close the stream if the read options specify to do so
            if (readOptions.isCloseStream()) {
                ExceptionUtilities.safelyIgnoreException(this::close);
            }

            // Rethrow known JsonIoExceptions directly
            if (e instanceof JsonIoException) {
                throw (JsonIoException) e;
            }

            // Wrap other exceptions in a JsonIoException for consistency
            throw new JsonIoException(getErrorMessage(e.getMessage()), e);
        } finally {
            // Restore unsafe mode to its original state
            if (shouldManageUnsafe) {
                ClassUtilities.setUseUnsafe(false);
            }

            // Cleanup the resolver's state post-resolution (only if root)
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
     * Returns the current {@link Resolver} instance used for JSON deserialization.
     *
     * <p>The {@code Resolver} serves as the superclass for both {@link ObjectResolver} and {@link MapResolver},
     * each handling specific aspects of converting JSON structures into Java objects.</p>
     *
     * <ul>
     *     <li><strong>ObjectResolver:</strong>
     *         <p>
     *             Responsible for converting JSON maps (represented by {@link JsonObject}) into their corresponding
     *             Java object instances. It handles the instantiation of Java classes and populates their fields based on
     *             the JSON data.
     *         </p>
     *     </li>
     *     <li><strong>MapResolver:</strong>
     *         <p>
     *             Focuses on refining the value side within a map. It utilizes the class information associated with
     *             a {@link JsonObject} to coerce primitive fields in the map's values to their correct data types.
     *             For example, if a {@code Long} is serialized as a {@code String} in the JSON, the {@code MapResolver}
     *             will convert it back to a {@code Long} within the map by matching the JSON key to the corresponding
     *             field in the Java class and transforming the raw JSON primitive value to the field's type (e.g., {@code long}/{@code Long}).
     *         </p>
     *     </li>
     * </ul>
     *
     * <p>
     * This method is essential for scenarios where JSON data needs to be deserialized into Java objects,
     * ensuring that both object instantiation and type coercion are handled appropriately.
     * </p>
     *
     * @return the {@code Resolver} currently in use. This {@code Resolver} is the superclass for
     *         {@code ObjectResolver} and {@code MapResolver}, facilitating the conversion of JSON structures
     *         into Java objects and the coercion of map values to their correct data types.
     */
    public Resolver getResolver() {
        return resolver;
    }
    
    public void close() {
        IOUtilities.close(input);
    }

    private String getErrorMessage(String msg)
    {
        if (input == null) {
            return msg;
        }

        StringBuilder sb = new StringBuilder(msg.length() + 100); // Pre-size for efficiency
        sb.append(msg)
          .append("\nLast read: ").append(input.getLastSnippet())
          .append("\nline: ").append(input.getLine())
          .append(", col: ").append(input.getCol());
        return sb.toString();
    }
}
