package com.cedarsoftware.io;

import java.util.Map;

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

    // Shape-specific storage. For complex-keyed maps, the parser produces a (@keys, @items)
    // pair; keysRef holds the keys side, valuesRef holds the values side. The parent
    // JsonObject still has its own keys[] and itemsRef fields at this stage (commit 4a
    // removes the redundancy); during this transitional state both sides are populated via
    // super.setKeys/super.setItems, but only these are canonical (parent's getKeys/getItems
    // are overridden below to read from here).
    private Object[] keysRef;
    private Object[] valuesRef;
    private java.lang.reflect.Type mapKeyType;

    JsonObjectMap() {
        super();
    }

    JsonObjectMap(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Object[] getKeys() {
        return keysRef;
    }

    @Override
    void setKeys(Object[] keyArray) {
        super.setKeys(keyArray);
        this.keysRef = keyArray;
    }

    @Override
    public Object[] getItems() {
        return valuesRef;
    }

    @Override
    public void setItems(Object[] array) {
        super.setItems(array);
        this.valuesRef = array;
    }

    @Override
    public java.lang.reflect.Type getMapKeyType() {
        return mapKeyType;
    }

    @Override
    public void setMapKeyType(java.lang.reflect.Type keyType) {
        this.mapKeyType = keyType;
    }

    @Override
    public void clear() {
        super.clear();
        this.keysRef = null;
        this.valuesRef = null;
        this.mapKeyType = null;
    }

    @Override
    public boolean isMap() {
        if (target != null) {
            return target instanceof Map;
        }
        if (type != null) {
            return Map.class.isAssignableFrom(getRawType());
        }
        return true;
    }
}
