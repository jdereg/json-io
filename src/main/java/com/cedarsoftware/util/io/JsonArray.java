package com.cedarsoftware.util.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * This class holds a JSON array in a ArrayList.  The objects stored within the list
 * are all JsonValue's.  They could be JSON objects {...}, arrays [...], or primitive values.
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
 *         limitations under the License.*
 */
public class JsonArray extends JsonValue implements List<JsonValue> {
    private final List<JsonValue> jsonStore = new ArrayList<>();

    public JsonArray() {
    }

    public String toString()
    {
        String jType = javaType == null ? "javaTypeNotSet" : javaType.getName();
        String targetInfo = target == null ? "not set (null)" : "set";
        return "JsonArray(id:" + id + ", type:" + jType + ", target:" + targetInfo +", line:" + line +", col:"+ col +", size:" + size() + ")";
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        JsonArray other = (JsonArray) obj;
        if (target == null) {
            return other.target == null;
        } else {
            return target.equals(other.target);
        }
    }

    public int hashCode() {
        return target != null ? target.hashCode() : 0;
    }

    public boolean isJsonArray() {
        return true;
    }

    public boolean isCollection() {
        return target instanceof Collection;
    }

    //
    // List implementation starts here.  Holds the raw JSON [...] data.
    //
    public JsonValue get(int index) {
        return jsonStore.get(index);
    }

    public JsonValue set(int index, JsonValue element) {
        return jsonStore.set(index, element);
    }

    public void add(int index, JsonValue element) {
        jsonStore.add(index, element);
    }

    public JsonValue remove(int index) {
        return jsonStore.remove(index);
    }

    public int indexOf(Object o) {
        return jsonStore.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return jsonStore.lastIndexOf(o);
    }

    public ListIterator<JsonValue> listIterator() {
        return jsonStore.listIterator();
    }

    public ListIterator<JsonValue> listIterator(int index) {
        return listIterator(index);
    }

    public List<JsonValue> subList(int fromIndex, int toIndex) {
        return subList(fromIndex, toIndex);
    }

    public int size() {
        return jsonStore.size();
    }

    public boolean isEmpty() {
        return jsonStore.isEmpty();
    }

    public boolean contains(Object o) {
        return jsonStore.contains(o);
    }

    public Iterator<JsonValue> iterator() {
        return jsonStore.iterator();
    }

    public Object[] toArray() {
        return jsonStore.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return jsonStore.toArray(a);
    }

    public boolean add(JsonValue o) {
        return jsonStore.add(o);
    }

    public boolean remove(Object o) {
        return jsonStore.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return jsonStore.containsAll(c);
    }

    public boolean addAll(Collection<? extends JsonValue> c) {
        return jsonStore.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends JsonValue> c) {
        return jsonStore.addAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        return jsonStore.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return jsonStore.retainAll(c);
    }

    public void clear() {
        jsonStore.clear();
    }
}
