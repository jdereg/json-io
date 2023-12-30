package com.cedarsoftware.util.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * This is the main API for json-io.  Use these methods to convert:<br/>
 * <ul>
 * <li><b>1. Input</b>: Java root | JsonObject root <b>Output</b>: JSON<pre>String json = JsonIo.toJson(JavaObject | JsonObject root, writeOptions)</pre></li>
 * <li><b>2. Input</b>: Java root | JsonObject root, <b>Output</b>: JSON -> outputStream<pre>JsonIo.toJson(OutputStream, JavaObject | JsonObject root, writeOptions)</pre></li>
 * <li><b>3. Input</b>: JSON, <b>Output</b>: Java objects | JsonObject<pre>BillingInfo billInfo = JsonIo.toObjects(String | InputStream, readOptions, BillingInfo.class)</pre></li>
 * <li><b>4. Input</b>: JsonObject root, <b>Output</b>: Java objects<pre>BillingInfo billInfo = JsonIo.toObjects(JsonObject, readOptions, BillingInfo.class)</pre></li>
 * Often, the goal is to get JSON to Java objects and from Java objects to JSON.  That is #1 and #3 above. <br/>
 * <br/>
 * For approaches #1 and #2 above, json-io will check the root object type (regular Java class or JsonObject instance) to
 * know which type of Object Graph it is serializing to JSON.<br/>
 * <br/>
 * There are occasions where you may just want the raw JSON data, without anchoring it to a set of "DTO" Java objects.
 * For example, you may have an extreme amount of data, and you want to process it as fast as possible, and in
 * streaming mode.  The JSON specification has great primitives which are universally useful in many languages. In
 * Java that is boolean, null, long [or BigInteger], and double [or BigDecimal], and String.<br/>
 * <br/>
 * When JsonObject is returned [option #3 or #4 above with readOptions.returnType(ReturnType.JSON_VALUES)], your root
 * value will represent one of:
 * <ul>JSON object {...}<br/>
 * JSON array [...]<br/>
 * JSON primitive (boolean true/false, null, long, double, String).</ul>
 * <br/>
 * <b>{...} JsonObject</b> implements the Map interface and represents any JSON object {...}.  It will respond true to
 * isObject(), false to isArray(), and false to isPrimitive().<br/>
 * <br/>
 * <b>[...] JsonObject</b> implements the List interface and represents any JSON array [...].  It will respond true to
 * isArray(), false to isObject(), and false to is isPrimitive().<br/>
 * <br/>
 * <b>Primitive JsonObject</b> If the root of the JSON is a String, Number (Long or Double), Boolean, or null, not an
 * object { ... } nor an array { ... }, then the value can be obtained by calling .getValue() on the JsonObject. It will
 * respond false to isObject(), false to isArray(), and true to isPrimitive().<br/>
 * <br/>
 * If you have a return object graph of JsonObject and want to turn these into Java (DTO) objects, use #4.  To turn the
 * JsonObject graph back into JSON, use option #1 or #2.<br/>
 * <br/>
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
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
 *         limitations under the License.*
 */
public class JsonIo {

    /**
     * Statically accessed class.
     */
    private JsonIo() {
    }

    /**
     * Convert the passed in Java source object to JSON.
     * @param srcObject Java instance to convert to JSON format.  Can be a JsonObject that was loaded earlier
     *                  via .toObjects() with readOptions.returnType(ReturnType.JSON_OBJECTS).
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @return String of JSON that represents the srcObject in JSON format.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static String toJson(Object srcObject, WriteOptions writeOptions) {
        FastByteArrayOutputStream out = new FastByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(out, writeOptions)) {
            writer.write(srcObject);
            return out.toString();
        } catch (JsonIoException je) {
            throw je;
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object to JSON", e);
        }
    }

    /**
     * Convert the passed in Java source object to JSON.  If you want a copy of the JSON that was written to the
     * OutputStream, you can wrap the output stream before calling this method, like this:<br/>
     * <br/>
     * <code>ByteArrayOutputStream baos = new ByteArrayOutputStream(originalOutputStream);<br/>
     * JsonIo.toJson(baos, source, writeOptions);<br/>
     * baos.flush();<br/>
     * String json = new String(baos.toByteArray(), StandardCharsets.UTF_8);<br/>
     * </code><br/>
     * @param out OutputStream destination for the JSON output.  The OutputStream will be closed by default.  If
     *            you don't want this, set writeOptions.closeStream(false).  This is useful for creating NDJSON,
     *            where multiple JSON objects are written to the stream, separated by a newline.
     * @param source Java instance to convert to JSON format.  Can be a JsonObject that was loaded earlier
     *                  via .toObjects() with readOptions.returnType(ReturnType.JSON_OBJECTS).
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static void toJson(OutputStream out, Object source, WriteOptions writeOptions) {
        Convention.throwIfNull(out, "OutputStream cannot be null");
        JsonWriter writer = null;
        try {
            writer = new JsonWriter(out, writeOptions);
            writer.write(source);
        } catch (Exception e) {
            throw new JsonIoException("Unable to convert object and send in JSON format to OutputStream.", e);
        }
        finally {
            if (writeOptions.isCloseStream()) {
                if (writer != null) {
                    writer.close();
                }
            }
        }
    }

    /**
     * Convert the passed in JSON to Java Objects.
     * @param json String containing JSON content.
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @param rootType Class of the root type of object that will be returned. Can be null, in which
     *                 case a best-guess will be made for the Class type of the return object.  If it
     *                 has an @type meta-property that will be used, otherwise the JSON types { ... }
     *                 will return a Map, [...] will return Object[] or Collection, and the primtive
     *                 types will be returned (String, long, Double, boolean, or null).
     * @return rootType Java instance that represents the Java equivalent of the passed in JSON string.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static <T> T toObjects(String json, ReadOptions readOptions, Class<T> rootType) {
        if (json == null) {
            json = "";
        }
        return toObjects(new FastByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), readOptions, rootType);
    }

    /**
     * Convert the passed in JSON to Java Objects.
     * @param in InputStream bringing JSON content.  By default, it will be closed.  If you don't want
     *           it closed after reading, set readOptions.closeStream(false).
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @param rootType Class of the root type of object that will be returned. Can be null, in which
     *                 case a best-guess will be made for the Class type of the return object.  If it
     *                 has a @type meta-property that will be used, otherwise a JsonObject will be returned.
     * @return rootType Java instance that represents the Java equivalent of the JSON input.  If the returnType() on
     * ReadOptions is set to ReturnType.JSON_OBJECTS, then the root return value will be a JsonObject, which can
     * represent a JSON object {...}, a JSON array [...], or a JSON primitive.  JsonObject has .is*() methods on
     * it to determine the type of object represented.  If the type is a JSON primitive, use .getValue() on JSON
     * object to obtain the primitive value.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static <T> T toObjects(InputStream in, ReadOptions readOptions, Class<T> rootType) {
        Convention.throwIfNull(in, "InputStream cannot be null");

        JsonReader jr = null;
        try  {
            jr = new JsonReader(in, readOptions);
            T root = jr.readObject(rootType);
            return root;
        } catch (JsonIoException je) {
            throw je;
        } catch (Exception e) {
            throw new JsonIoException(e);
        }
        finally {
            if (readOptions != null && readOptions.isCloseStream()) {
                if (jr != null) {
                    jr.close();
                }
            }
        }
    }


    /**
     * Convert a root JsonObject that represents parsed JSON, into an actual Java object.
     * @param rootType The class that represents, in Java, the root of the underlying JSON from which the JsonObject
     *                 was loaded.
     * @return a typed Java instance object graph.
     */
    public static <T> T toObjects(JsonObject jsonObject, ReadOptions readOptions, Class<T> rootType) {
        ReadOptions localReadOptions = readOptions == null ? new ReadOptions() : new ReadOptions(readOptions);
        localReadOptions.returnType(ReturnType.JAVA_OBJECTS);
        JsonReader reader = new JsonReader(localReadOptions);
        return reader.convertJsonValueToJava(jsonObject, rootType);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param readOptions ReadOptions to control the feature options. Can be null to take the defaults.
     * @param writeOptions WriteOptions to control the feature options. Can be null to take the defaults.
     * @param json String JSON content.
     * @return String JSON formatted in human readable, standard multi-line, indented format.
     */
    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        WriteOptionsBuilder builder = writeOptions == null ? new WriteOptionsBuilder() : new WriteOptionsBuilder(writeOptions);
        WriteOptions newWriteOptions = builder.prettyPrint(true).build();

        if (readOptions == null) {
            readOptions = new ReadOptions();
        } else {
            readOptions = new ReadOptions(readOptions);
        }
        readOptions.returnType(ReturnType.JSON_OBJECTS);
        
        Object object = toObjects(json, readOptions, null);
        return toJson(object, newWriteOptions);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param json String JSON content.
     * @return String JSON formatted in human readable, standard multi-line, indented format.
     */
    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptions().returnType(ReturnType.JSON_OBJECTS),
                new WriteOptionsBuilder().prettyPrint(true).build());
    }

    /**
     * Copy an object graph using JSON.
     * @param source Object root object to copy
     * @param readOptions ReadOptions feature settings. Can be null for default ReadOptions.
     * @param writeOptions WriteOptions feature settings. Can be null for default WriteOptions.
     * @return A new, duplicate instance of the original.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(Object source, ReadOptions readOptions, WriteOptions writeOptions) {
        if (source == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }

        String json = toJson(source, writeOptions);
        return (T) toObjects(json, readOptions, source.getClass());
    }

    /**
     * Call this method to see all of the conversions offered.
     * @param args
     */
    public static void main(String[] args) {
        String json = toJson(Converter.getSupportedConversions(), new WriteOptionsBuilder().prettyPrint(true).showTypeInfoNever().build());
        System.out.println("json-io supported conversions between data types (targets by sources):");
        System.out.println(json);
    }
}
