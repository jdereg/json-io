package com.cedarsoftware.io;

/**
 * Implement this interface to react to fields missing when reading an object.
 * This handler will be called after all deserialization has occurred to allow
 * all references to be resolved.
 * <p>
 * Register your handler using {@link ReadOptionsBuilder#missingFieldHandler(MissingFieldHandler)}.
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
public interface MissingFieldHandler {
    /**
     * Notify that a field is missing from the target object during deserialization.
     * <p>
     * Warning: not every type can be deserialized upon missing fields. Arrays and Object
     * types that do not have serialized @type definition will be ignored.
     *
     * @param object    the object that contains the missing field
     * @param fieldName name of the field that was in the JSON but not found in the object
     * @param value     the value from JSON that could not be assigned to a field
     */
    void fieldMissing(Object object, String fieldName, Object value);
}
