package com.cedarsoftware.io;

/**
 * Implement this interface to add a custom JSON reader for a specific class.
 * <p>
 * Recommendation: Use {@link ClassFactory} instead when possible - it allows you to
 * instantiate and read the JSON object in one operation, which is simpler for most use cases.
 * <p>
 * Register your custom reader using {@link ReadOptionsBuilder#addCustomReaderClass(Class, JsonClassReader)}.
 *
 * @param <T> the type of object this reader produces
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
public interface JsonClassReader<T> {
    /**
     * Read a custom object from JSON. Only process the non-structural values for any given reader,
     * and push the structural elements (non-primitive fields) onto the resolver's stack to be processed.
     * This allows them to be processed by a standard processor (array, Map, Collection) or another
     * custom factory or reader that handles the "next level." You can handle sub-objects here if needed.
     *
     * @param jsonObj  Object being read. Could be a fundamental JSON type (String, long, boolean,
     *                 double, null) or a JsonObject containing the parsed JSON structure.
     * @param resolver Provides access to push non-primitive items onto the stack for further processing,
     *                 as well as access to ReadOptions, Converter, and reference tracking.
     * @return Java Object that you filled out with values from the passed in jsonObj.
     */
    default T read(Object jsonObj, Resolver resolver) {
        throw new UnsupportedOperationException("You must implement this method to read the JSON content from jsonObj and copy the values to the target object.");
    }
}
