package com.cedarsoftware.io;

import java.util.Collection;

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
    // JSON intermediate values. The parent JsonObject still has its own itemsRef
    // field at this stage (commit 2c removes it); during this transitional state
    // both fields receive the value via super.setItems(), but only this one is
    // canonical (parent's getItems is overridden below to read from here).
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
        // Delegate to parent for storageMode bookkeeping, hash invalidation, and
        // jsonTypeCache reset. Parent stores the array in its own itemsRef as well
        // during this transitional commit; we then store the canonical reference here.
        super.setItems(array);
        this.itemsRef = array;
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
}
