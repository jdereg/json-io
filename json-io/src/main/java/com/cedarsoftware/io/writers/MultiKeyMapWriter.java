package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.util.MultiKeyMap;

/**
 * Writer for MultiKeyMap instances that produces a shortened configuration format.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The {@code "config"} field is emitted via the generator;
 * the {@code "entries"} array (each element a {@code {"keys":...,"value":...}}
 * sub-object containing arbitrary objects) is emitted through the
 * {@link WriterContext} semantic API so the tree-walker's
 * {@link com.cedarsoftware.io.JsonWriter} contextStack tracks structural
 * state during the recursive {@code writeImpl} / {@code writeObjectField}
 * calls. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so user subclasses written against the old API can chain via
 * {@code super.write(...)} unchanged.
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
public class MultiKeyMapWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        MultiKeyMap<?> map = (MultiKeyMap<?>) obj;

        int capacity = map.getCapacity();
        float loadFactor = map.getLoadFactor();
        MultiKeyMap.CollectionKeyMode collectionKeyMode = map.getCollectionKeyMode();
        boolean flattenDimensions = map.getFlattenDimensions();
        boolean simpleKeysMode = map.getSimpleKeysMode();
        boolean valueBasedEquality = map.getValueBasedEquality();
        boolean caseSensitive = map.getCaseSensitive();

        StringBuilder config = new StringBuilder();
        config.append(capacity).append('/');
        config.append(loadFactor).append('/');

        String modeCode;
        switch (collectionKeyMode) {
            case COLLECTIONS_EXPANDED:
                modeCode = "EXP";
                break;
            case COLLECTIONS_NOT_EXPANDED:
                modeCode = "NOEXP";
                break;
            default:
                modeCode = "EXP";
        }
        config.append(modeCode).append('/');
        config.append(flattenDimensions ? "T" : "F").append('/');
        config.append(simpleKeysMode ? "T" : "F").append('/');
        config.append(valueBasedEquality ? "T" : "F").append('/');
        config.append(caseSensitive ? "T" : "F");

        // "config" field via the JsonGenerator
        gen.writeFieldName("config");
        gen.writeString(config.toString());

        // "entries" array via the tree-walker's semantic API — each element is a
        // {"keys":...,"value":...} sub-object containing arbitrary objects.
        // JsonWriter's contextStack handles all structural commas in the loop.
        context.writeArrayFieldStart("entries");
        for (java.util.Map.Entry<Object, ?> entry : map.entrySet()) {
            context.writeStartObject();
            context.writeFieldName("keys");
            context.writeImpl(entry.getKey(), showType);
            context.writeObjectField("value", entry.getValue());
            context.writeEndObject();
        }
        context.writeEndArray();
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }

    @Override
    public String getTypeName(Object o) {
        return "com.cedarsoftware.util.MultiKeyMap";
    }
}
