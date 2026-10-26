package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JsonObject a read gives converts its typed values -- {@code get(key, type)}, {@code putAsJson(key, value)} -- with
 * that read's Converter: the conversions added to its ReadOptions, its zone and locale. Every object of the tree,
 * wherever it stands, read from JSON or from TOON. One made by hand converts with the default ReadOptions' Converter.
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
class JsonObjectReadConverterTest {
    /** Text as an Integer is its length: no default conversion gives that. */
    private static ReadOptions lengths() {
        return new ReadOptionsBuilder()
                .addConverterOverride(String.class, Integer.class, (from, converter) -> ((String) from).length())
                .addConverterOverride(LocalDate.class, String.class, (from, converter) -> "day " + from)
                .build();
    }

    @Test
    void theReadsConversionsApply() {
        JsonObject order = JsonIo.toMaps("{\"code\":\"abc\",\"shipped\":\"2026-01-15\"}", lengths())
                .asClass(JsonObject.class);
        assertEquals(3, order.get("code", Integer.class));
        assertEquals(3, order.get("code", int.class));
        assertEquals(3, order.get("absent", Integer.class, 3));
        assertEquals(3, order.get("code", new TypeHolder<Integer>() {}));
        order.putAsJson("next", LocalDate.of(2026, 2, 1));
        assertEquals("day 2026-02-01", order.get("next"));
        order.putAsJson("nested", mapOf("on", LocalDate.of(2026, 3, 1), "code", "abcd"));
        JsonObject nested = (JsonObject) order.get("nested");
        assertEquals("day 2026-03-01", nested.get("on"));
        assertEquals(4, nested.get("code", Integer.class));

        JsonObject byHand = new JsonObject();
        byHand.put("code", "7");
        byHand.putAsJson("next", LocalDate.of(2026, 2, 1));
        assertEquals(7, byHand.get("code", Integer.class));
        assertEquals("2026-02-01", byHand.get("next"));
    }

    @Test
    void theReadsZoneApplies() {
        ZoneId zone = ZoneId.of("Asia/Kathmandu");
        assertNotEquals(zone, ZoneId.systemDefault());
        ReadOptions options = new ReadOptionsBuilder().setZoneId(zone).build();
        JsonObject read = JsonIo.toMaps("{\"at\":\"2026-01-15T10:00:00\"}", options).asClass(JsonObject.class);
        assertEquals(ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, zone), read.get("at", ZonedDateTime.class));
    }

    /**
     * Each object in the tree -- root, nested, in an array, in an array in an array, by @ref, of a class not here, one
     * whose @type comes last, and one reshaped by @items arriving after a field.
     */
    @Test
    void everyObjectOfAJsonRead() {
        String json = "{\"code\":\"a\",\"inner\":{\"code\":\"ab\",\"deeper\":{\"code\":\"abc\"}},"
                + "\"list\":[{\"code\":\"abcd\"},[{\"code\":\"abcde\"}],{\"@id\":1,\"code\":\"abcdef\"}],"
                + "\"again\":{\"@ref\":1},\"odd\":{\"@type\":\"com.nowhere.Odd\",\"code\":\"abcdefg\"},"
                + "\"late\":{\"code\":\"abcdefgh\",\"@type\":\"com.nowhere.Late\"},"
                + "\"bag\":{\"@type\":\"com.nowhere.Bag\",\"n\":1,\"@items\":[2]}}";
        assertEachConvertsAsTheReadDoes(JsonIo.toMaps(json, lengths()).asClass(JsonObject.class), 8, 9);
    }

    /**
     * Each object of a TOON read: root, nested, a tabular row, a list item (an empty one too), the objects a dotted
     * key makes (one in place of a value too), a bare "key:", a keyed table and its entries, a row's field group, an
     * object of metadata only (in a list too), one holding $items, $keys or @enum first; and the empty document's map.
     * Each is made at a place of its own in the reader.
     */
    @Test
    void everyObjectOfAToonRead() {
        String toon = "code: a\n"
                + "inner:\n"
                + "  code: ab\n"
                + "  deeper:\n"
                + "    code: abc\n"
                + "rows[2]{code,n}:\n"
                + "  abcd,1\n"
                + "  abcde,2\n"
                + "items[3]:\n"
                + "  - code: abcdef\n"
                + "    n: 1\n"
                + "  - code: abcdefg\n"
                + "  -\n"
                + "paths.go.deep:\n"
                + "  code: abcdefgh\n"
                + "empty:\n"
                + "keyed[2:]{code,n}:\n"
                + "  k1: abcdefghi,1\n"
                + "  k2: abcdefghij,2\n"
                + "grouped[1]{g{code},n}:\n"
                + "  abcdefghijk,1\n"
                + "clash: 1\n"
                + "clash.inner.code: abcdefghijkl\n"
                + "meta:\n"
                + "  $id: 7\n"
                + "listMeta[1]:\n"
                + "  - $type: com.nowhere.Odd\n"
                + "bag:\n"
                + "  $type: com.nowhere.Bag\n"
                + "  $items[1]: 2\n"
                + "keyed2:\n"
                + "  $keys[1]: a\n"
                + "  $items[1]: b\n"
                + "colors:\n"
                + "  @enum: com.nowhere.Color\n"
                + "  $items[1]: RED\n";
        ReadOptions options = new ReadOptionsBuilder(lengths()).toonExpandPaths(true).build();
        assertEachConvertsAsTheReadDoes(JsonIo.fromToonToMaps(toon, options).asClass(JsonObject.class), 12, 24);
        assertEachConvertsAsTheReadDoes(JsonIo.fromToonToMaps("", options).asClass(JsonObject.class), 0, 1);
    }

    /** Java serialization: the Converter is not written; one read back converts with the default ReadOptions'. */
    @Test
    void serializedAndReadBack() throws Exception {
        JsonObject read = JsonIo.toMaps("{\"code\":\"12\"}", lengths()).asClass(JsonObject.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(read);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            JsonObject back = (JsonObject) in.readObject();
            assertEquals(12, back.get("code", Integer.class));
        }
    }

    /** An object holding @items hashes as an equal one does after its items change in place. */
    @Test
    void itemsChangedInPlace() {
        JsonObject a = JsonObject.newArrayInstance();
        a.setItems(new Object[] {1L, 2L});
        int before = a.hashCode();
        a.getItems()[0] = 5L;
        JsonObject b = JsonObject.newArrayInstance();
        b.setItems(new Object[] {5L, 2L});
        assertEquals(b, a);
        assertEquals(b.hashCode(), a.hashCode());
        assertNotEquals(before, a.hashCode());
    }

    /**
     * Each JsonObject reachable -- {@code objects} of them -- converts as the read does: a "code" it holds
     * ({@code codes} of them), and text put in it, as an Integer is the text's length.
     */
    private static void assertEachConvertsAsTheReadDoes(Object root, int codes, int objects) {
        List<JsonObject> found = new ArrayList<>();
        collect(root, found, new IdentityHashMap<>());
        int checked = 0;
        for (JsonObject object : found) {
            Object code = object.get("code");
            if (code instanceof String) {
                assertEquals(((String) code).length(), object.get("code", Integer.class), "code " + code);
                checked++;
            }
            object.put("probe", "abcd");
            assertEquals(4, object.get("probe", Integer.class), String.valueOf(object));
        }
        assertEquals(codes, checked);
        assertEquals(objects, found.size());
    }

    private static void collect(Object value, List<JsonObject> found, IdentityHashMap<Object, Object> seen) {
        if (value == null || seen.put(value, value) != null) {
            return;
        }
        if (value instanceof JsonObject) {
            found.add((JsonObject) value);
            collect(((JsonObject) value).getItems(), found, seen);
        }
        if (value instanceof Map) {
            for (Object nested : ((Map<?, ?>) value).values()) {
                collect(nested, found, seen);
            }
        } else if (value instanceof Iterable) {
            for (Object nested : (Iterable<?>) value) {
                collect(nested, found, seen);
            }
        } else if (value instanceof Object[]) {
            for (Object nested : (Object[]) value) {
                collect(nested, found, seen);
            }
        }
        assertTrue(found.size() < 100);
    }

    private static Map<String, Object> mapOf(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
