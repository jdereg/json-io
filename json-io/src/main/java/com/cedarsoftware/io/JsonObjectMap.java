package com.cedarsoftware.io;

/**
 * Specialization of {@link JsonObject} for complex-keyed map-shaped JSON values
 * (those carried as the {@code @keys} + {@code @items} pair in the json-io intermediate form,
 * representing Java {@code Map} instances whose keys are not Strings).
 * <p>
 * Simple String-keyed maps remain represented by the lite {@link JsonObject} since their storage
 * shape matches the typical POJO/object case (parallel {@code keys[]} / {@code data[]}).
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
class JsonObjectMap extends JsonObject {
    JsonObjectMap() {
        super();
    }

    JsonObjectMap(int initialCapacity) {
        super(initialCapacity);
    }
}
