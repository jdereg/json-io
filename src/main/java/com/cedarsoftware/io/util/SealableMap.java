package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * NOTE: Please do not reformat this code. This is a very lambda-like implementation
 * and it makes it easy to see over all structure.  The individual methods are trivial
 * because this is about APIs and delegating.
 * <br><br>
 * SealableMap provides a Map that can be 'sealed' and 'unsealed'. When sealed,
 * the Map is read-only, otherwise it is mutable. The iterator, keySet, entrySet, and
 * values() return views that honor the original Map's sealed state.
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
public class SealableMap<K, V> implements Map<K, V> {
    private final Map<K, V> map;
    private final Supplier<Boolean> sealedSupplier;

    public SealableMap(Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.map = new LinkedHashMap<>();
    }
    public SealableMap(Map<K, V> items, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.map = items;
    }
    
    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This map has been sealed and is now immutable");
        }
    }

    // Immutable
    public boolean equals(Object obj) { return map.equals(obj); }
    public int hashCode() { return map.hashCode(); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public boolean containsKey(Object key) { return map.containsKey(key); }
    public boolean containsValue(Object value) { return map.containsValue(value); }
    public V get(Object key) { return map.get(key); }
    public Set<K> keySet() { return new SealableSet<>(map.keySet(), sealedSupplier); }
    public Collection<V> values() { return new SealableList<>(new ArrayList<>(map.values()), sealedSupplier); }
    public Set<Map.Entry<K, V>> entrySet() { return new SealableSet<>(map.entrySet(), sealedSupplier); }

    // Mutable
    public V put(K key, V value) { throwIfSealed(); return map.put(key, value); }
    public V remove(Object key) { throwIfSealed(); return map.remove(key); }
    public void putAll(Map<? extends K, ? extends V> m) { throwIfSealed(); map.putAll(m); }
    public void clear() { throwIfSealed(); map.clear(); }
}