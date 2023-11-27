package com.cedarsoftware.util.io;

import java.io.InputStream;

import com.cedarsoftware.util.ReturnType;

/**
 * This is the API for json-io.  Use these methods to convert:<br/>
 * 1. JSON to Java objects
 * 2. JSON to JsonValues (simple non-typed, generic content)
 * 3. Convert Java objects to JSON
 * 4. JsonValues to JSON.
 * <br/><br/>
 * @author Kenny Partlow (kpartlow@gmail.com)
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
     * Convert the passed in JSON to JsonValue types (JsonObject, JsonArray, JsonPrimitive).  JsonValue is
     * the parent class for these classes.
     * @param json String containing JSON content.
     * @param readOptions Feature options settings to control the JSON processing.  Can be null,
     *                     in which case, default settings will be used.
     * @return JsonValue JsonValue derived class instances.  Output will be JsonObject, JsonArray, or JsonPrimitive
     * depending on that the root of the JSON is ({...} will return JsonObject, [...] will return JsonArray, and
     * JsonPrimitive will be returned if "string", long (12345), double (3.1415), boolean (true/false), or null.
     * NOTE: Currently, in the 4.x versions, only JsonObject is being returned.  This will change in 5.x and work
     * as documented.
     * @throws JsonIoException A runtime exception thrown if any errors happen during serialization
     */
    public static JsonValue toJsonValues(String json, ReadOptions readOptions) {
        return JsonReader.toMaps(json, readOptions);
    }
    
    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        if (writeOptions.isBuilt())
        {
            writeOptions = new WriteOptions(writeOptions);
        }
        writeOptions.prettyPrint(true);

        if (readOptions.isBuilt())
        {
            readOptions = new ReadOptions(readOptions);
        }
        readOptions.returnType(ReturnType.JSON_VALUES);
        
        Object object = JsonReader.toObjects(json, readOptions, null);
        return JsonWriter.toJson(object, writeOptions);
    }

    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptions().returnType(ReturnType.JSON_VALUES),
                new WriteOptions().prettyPrint(true));
    }

    public static <T> T deepCopy(Object o, Class<T> rootType) {
        return deepCopy(o, new ReadOptions(), new WriteOptions(), rootType);
    }

    public static <T> T deepCopy(Object o, ReadOptions readOptions, WriteOptions writeOptions, Class<T> rootType) {
        if (o == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }
        String json = JsonWriter.toJson(o, writeOptions);
        return JsonReader.toObjects(json, readOptions, rootType);
    }
}
