package com.cedarsoftware.util.io;

import java.io.InputStream;
import java.io.OutputStream;

import com.cedarsoftware.util.ReturnType;

/**
 * This is the main API for json-io.  Use these methods to convert:<br/>
 * <ul>
 * <li><b>1. Input</b>: Java objects | JsonValue <b>Output</b>: JSON<pre>String json = JsonIo.toJson(JavaObject | JsonValue root, writeOptions)</pre></li>
 * <li><b>2. Input</b>: Java objects | JsonValue, <b>Output</b>: JSON<pre>String json = JsonIo.toJson(OutputStream, JavaObject | JsonValue root, writeOptions)</pre></li>
 * <li><b>3. Input</b>: JSON, <b>Output</b>: Java objects<pre>BillingInfo billInfo = JsonIo.toObjects(String | InputStream, readOptions, BillingInfo.class)</pre></li>
 * <li><b>4. Input</b>: JSON, <b>Output</b>: JsonValues (simple, generic content):<pre>JsonIo.toObjects(String json, readOptions)</pre></li></ul>
 * Often, the goal is to get JSON to Java objects, and from Java objects to JSON.  That is #1 and #3 above.  However
 * sometimes, you may just want the raw JSON data in memory, not tying it back to a set of "DTO" Java objects. <br/>
 * <br/>
 * For example, you may have an extreme amount of data, and you want to process it as fast as possible, and in a
 * streaming mode.  JSON has great primitives to work with (In Java classes that would be boolean, null, long or
 * BigInteger, and double or BigDecimal), and String.<br/>
 * <br/>
 * In this JSON-mode, your root return value is a JsonValue.  That can represent a JSON object {...}, a JSON
 * array [...], or a JSON primitive (boolean, null, long, double, String).  JsonObject is a subclass of JsonValue
 * and represents any JSON object {...}. JsonArray is a subclass of JsonValue and represents an array [...]. And
 * JsonPrimitive also a subclass of JsonValue, represents one of the 5 JSON primitive value types. If you set the
 * ".returnType(ReturnType,JSON_VALUES) on ReadOptions, you will receive a JsonValue graph as a return.
 * You can write processing code against this data structure directly if you would like.  Or, you can set the root
 * class and ".returnType(ReturnType.JAVA_VALUES)" and JsonIo will convert the parsed in JSON into Java objects, or
 * if it was passed a Java instance, convert the "DTO" graph into JSON directly.<br/>
 * <br/>
 * <b>Note</b> the JsonIo.toObjects() API will output from JSON to Java objects or JsonValues depending on the returnType
 * set into the ReadOptions.returnType(ReturnType.JAVA_VALUES | ReturnType.JSON_VALUES).<br/>
 * <br/>
 * <b>Note</b> the JsonIo.toJson() API will use the root object type (instance of JsonValue or not) to know which type of
 * Object Graph it is serializing to JSON.<br/>
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
     * Convert the passed in Java source object to JSON.
     * @param srcObject Java instance to convert to JSON format.
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @return String of JSON that represents the srcObject in JSON format.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static String toJson(Object srcObject, WriteOptions writeOptions) {
        return JsonWriter.toJson(srcObject, writeOptions);
    }

    /**
     * Convert the passed in Java source object to JSON.
     * @param out OutputStream destination for the JSON output.
     * @param source Root Java object to begin creating the JSON.
     * @param writeOptions Feature options settings to control the JSON output.  Can be null,
     *                     in which case, default settings will be used.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static void toJson(OutputStream out, Object source, WriteOptions writeOptions) {
         JsonWriter.toJson(out, source, writeOptions);
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
        return JsonReader.toObjects(json, readOptions, rootType);
    }

    /**
     * Convert the passed in JSON to Java Objects.
     * @param in InputStream bringing JSON content.
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @param rootType Class of the root type of object that will be returned. Can be null, in which
     *                 case a best-guess will be made for the Class type of the return object.  If it
     *                 has an @type meta-property that will be used, otherwise the JSON types { ... }
     *                 will return a Map, [...] will return Object[] or Collection, and the primtive
     *                 types will be returned (String, long, double, boolean, or null).
     * @return rootType Java instance that represents the Java equivalent of the JSON input.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static <T> T toObjects(InputStream in, ReadOptions readOptions, Class<T> rootType) {
        return JsonReader.toObjects(in, readOptions, rootType);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param readOptions ReadOptions to control the feature options. Can be null to take the defaults.
     * @param writeOptions WriteOptions to control the feature options. Can be null to take the defaults.
     * @param json String JSON content.
     * @return String JSON formatted in human readable, standard multi-line, indented format.
     */
    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        if (writeOptions == null) {
            writeOptions = new WriteOptions();
        }
        else if (writeOptions.isBuilt()) {
            writeOptions = new WriteOptions(writeOptions);
        }
        writeOptions.prettyPrint(true);

        if (readOptions == null) {
            readOptions = new ReadOptions();
        } else if (readOptions.isBuilt()) {
            readOptions = new ReadOptions(readOptions);
        }
        readOptions.returnType(ReturnType.JSON_VALUES);
        
        Object object = JsonReader.toObjects(json, readOptions, null);
        return JsonWriter.toJson(object, writeOptions);
    }

    /**
     * Format the passed in JSON into multi-line, indented format, commonly used in JSON online editors.
     * @param json String JSON content.
     * @return String JSON formatted in human readable, standard multi-line, indented format.
     */
    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptions().returnType(ReturnType.JSON_VALUES),
                new WriteOptions().prettyPrint(true));
    }

    /**
     * Copy an object graph using JSON.
     * @param source Object root object to copy
     * @param readOptions ReadOptions feature settings. Can be null for default ReadOptions.
     * @param writeOptions WriteOptions feature settings. Can be null for default WriteOptions.
     * @return A new, duplicate instance of the original.
     */
    public static <T> T deepCopy(Object source, ReadOptions readOptions, WriteOptions writeOptions) {
        if (source == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }
        String json = JsonWriter.toJson(source, writeOptions);
        return (T) JsonReader.toObjects(json, readOptions, source.getClass());
    }
}
