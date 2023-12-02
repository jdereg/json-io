package com.cedarsoftware.util.io;

import java.util.Deque;

public interface ReaderContext {

    ReferenceTracker getReferences();

    /**
     * This method converts a rootObj Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The rootObj map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     *
     * @param root JsonObject instance that was the rootObj object from the
     * @param hint When you know the type you will be returning.  Can be null (effectively Map.class)
     *             JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    <T> T reentrantConvertJsonValueToJava(JsonObject root, Class<T> hint);

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     *
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    void traverseFields(Deque<JsonObject> stack, JsonObject jsonObj);

    ReadOptions getReadOptions();
}
