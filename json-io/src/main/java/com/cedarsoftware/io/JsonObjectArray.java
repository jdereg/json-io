package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

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

    // Shape-specific storage. Holds the @items payload for array/collection-shaped
    // JSON intermediate values. Canonical storage lives here; parent's itemsRef field
    // is unused on JsonObjectArray instances and is removed in commit 4a-ii.
    private Object[] itemsRef;

    JsonObjectArray() {
        super();
    }

    JsonObjectArray(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Object[] getItems() {
        return itemsRef;
    }

    @Override
    public void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        this.itemsRef = array;
        // Invalidate cached state on parent (package-private access).
        this.hash = null;
        this.jsonTypeCache = 0;
    }

    @Override
    public void clear() {
        super.clear();
        this.itemsRef = null;
    }

    @Override
    public boolean isArray() {
        if (target != null) {
            return target.getClass().isArray();
        }
        if (type != null) {
            return getRawType().isArray();
        }
        return true;
    }

    @Override
    public boolean isCollection() {
        if (target instanceof Collection) {
            return true;
        }
        if (isMap()) {
            return false;
        }
        Class<?> rawType = getRawType();
        return rawType != null && !rawType.isArray();
    }

    // ========== Hash and Equals ==========
    // Items live on JsonObjectArray; equality must include the @items payload
    // in addition to any map-shape POJO data the parent already compares.

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JsonObjectArray)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        JsonObjectArray other = (JsonObjectArray) obj;
        return Objects.deepEquals(this.itemsRef, other.itemsRef);
    }

    @Override
    public int hashCode() {
        if (hash == null) {
            int result = super.hashCode();
            result = 31 * result + (itemsRef == null ? 0 : Arrays.deepHashCode(itemsRef));
            hash = result;
        }
        return hash;
    }
}
