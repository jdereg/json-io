package com.cedarsoftware.io;

/**
 * Specialization of {@link JsonObject} for array-shaped JSON values (those carried as
 * {@code @items} in the json-io intermediate form, including both Java arrays and
 * Collection-typed payloads).
 * <p>
 * Package-private by design. External callers continue to hold {@link JsonObject}
 * references; subclass identity is an internal dispatch and storage detail.
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
class JsonObjectArray extends JsonObject {
    JsonObjectArray() {
        super();
    }

    JsonObjectArray(int initialCapacity) {
        super(initialCapacity);
    }
}
