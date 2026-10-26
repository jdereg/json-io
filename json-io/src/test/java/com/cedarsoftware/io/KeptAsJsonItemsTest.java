package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode an object holding @items that is kept as the JSON it is -- its class not on the classpath, or a class
 * of fields -- has its items read as an array's are: a {@code @ref} among them is the object it names, a value json-io
 * wrote with its type is that value, and an object or array among them is read in turn. They stayed as parsed: a
 * {@code @ref} was an empty object, so the tree was written back with {} in its place and the object it named lost
 * its {@code @id}, the link gone; a BigDecimal stayed {@code {"@type":"BigDecimal","value":..}}.
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
class KeptAsJsonItemsTest {
    public static class Line {
        public int qty;
    }

    private static final String[] TYPES = {"com.nowhere.Bag", Line.class.getName()};

    private static String document(String type) {
        return "{\"a\":{\"@id\":1,\"x\":1},\"b\":{\"@type\":\"" + type + "\",\"@items\":[{\"@ref\":1},"
                + "{\"@type\":\"BigDecimal\",\"value\":\"1.50\"},{\"n\":{\"@ref\":1}},[{\"@ref\":1}]]}}";
    }

    @Test
    void itsItemsAreRead() {
        for (String type : TYPES) {
            JsonObject root = JsonIo.toMaps(document(type), null).asClass(JsonObject.class);
            Object a = root.get("a");
            Object[] items = ((JsonObject) root.get("b")).getItems();
            assertSame(a, items[0], type);
            assertEquals(new BigDecimal("1.50"), items[1], type);
            assertSame(a, ((Map<?, ?>) items[2]).get("n"), type);
            assertSame(a, ((Object[]) items[3])[0], type);
        }
    }

    /** Written back, the links hold: read again, each item that named the object is that object. */
    @Test
    void writtenBackTheLinksHold() {
        for (String type : TYPES) {
            JsonObject root = JsonIo.toMaps(document(type), null).asClass(JsonObject.class);
            String json = JsonIo.toJson(root, null);
            assertTrue(json.contains("\"a\":{\"@id\":1,\"x\":1}"), json);
            assertTrue(json.contains("\"@items\":[{\"@ref\":1},"), json);
            JsonObject again = JsonIo.toMaps(json, null).asClass(JsonObject.class);
            Object a = again.get("a");
            Object[] items = ((JsonObject) again.get("b")).getItems();
            assertSame(a, items[0], json);
            assertSame(a, ((Map<?, ?>) items[2]).get("n"), json);
            assertSame(a, ((Object[]) items[3])[0], json);
            assertEquals(type, ((JsonObject) again.get("b")).getTypeString(), json);
        }
    }
}
