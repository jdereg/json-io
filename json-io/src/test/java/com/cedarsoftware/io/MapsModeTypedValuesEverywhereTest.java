package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * In Maps mode a value json-io writes with its type ({"@type":"BigDecimal","value":"1.50"}, a date, a UUID, an Integer
 * where Object is declared) is that value wherever it sits -- in a list, an array, a typed collection, a list in a
 * list, a map's keys and values -- as it is in a field. Everywhere but a field, the root and a root array it stayed
 * the object holding it. Written back, the tree gives the text it was read from: json-io writes the value with its
 * type again.
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
class MapsModeTypedValuesEverywhereTest {
    private static final Object[] VALUES = {5, (short) 3, 2.5f, 'c', new BigDecimal("1.50"), new UUID(1, 2),
            new Date(1700000000000L), ZonedDateTime.parse("2024-01-15T10:30:00-05:00[America/New_York]"), String.class};

    private static Map<String, Object> holder(Object value) {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("list", new ArrayList<>(Arrays.asList(value)));
        holder.put("array", new Object[] {value});
        holder.put("nested", new ArrayList<>(Arrays.asList(new ArrayList<>(Arrays.asList(value)))));
        holder.put("set", new LinkedHashSet<>(Arrays.asList(value)));
        Map<Object, Object> keys = new LinkedHashMap<>();
        keys.put(value, "k");
        holder.put("keys", keys);
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put(1L, value);
        holder.put("values", values);
        return holder;
    }

    private static Object first(Object container) {
        if (container instanceof Object[]) {
            return ((Object[]) container)[0];
        }
        if (container instanceof List) {
            return ((List<?>) container).get(0);
        }
        return ((JsonObject) container).getItems()[0];
    }

    private static void assertValue(Object expected, Object actual, String where) {
        assertSame(expected.getClass(), actual.getClass(), where);
        assertEquals(expected, actual, where);
    }

    private static void assertEveryPosition(Object value, Map<?, ?> read, String text) {
        assertValue(value, first(read.get("list")), "list " + text);
        assertValue(value, first(read.get("array")), "array " + text);
        assertValue(value, first(first(read.get("nested"))), "nested " + text);
        assertValue(value, first(read.get("set")), "set " + text);
        assertValue(value, ((Map<?, ?>) read.get("keys")).keySet().iterator().next(), "keys " + text);
        assertValue(value, ((Map<?, ?>) read.get("values")).values().iterator().next(), "values " + text);
    }

    @Test
    void eachValueWhereverItSits() {
        for (Object value : VALUES) {
            String json = JsonIo.toJson(holder(value), null);
            Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
            assertEveryPosition(value, read, json);
            assertEquals(json, JsonIo.toJson(read, null));
        }
    }

    @Test
    void inToon() {
        WriteOptions always = new WriteOptionsBuilder().showTypeInfoAlways().build();
        for (Object value : VALUES) {
            String toon = JsonIo.toToon(holder(value), always);
            Map<?, ?> read = (Map<?, ?>) JsonIo.fromToonToMaps(toon, null).asClass(null);
            assertValue(value, first(read.get("list")), "list " + toon);
            assertValue(value, first(read.get("array")), "array " + toon);
        }
    }

    /** A TOON list (no $type of its own) of typed values, as another writer or a hand may write it: and a $ref. */
    @Test
    void aToonListOfTypedValues() {
        String toon = "a:\n  $id: 1\n  $type: BigDecimal\n  value: \"1.5\"\nlist[2]:\n  - $type: Integer\n    value: 5\n  - $ref: 1\n";
        Map<?, ?> read = (Map<?, ?>) JsonIo.fromToonToMaps(toon, null).asClass(null);
        List<?> list = (List<?>) read.get("list");
        assertValue(5, list.get(0), toon);
        assertValue(new BigDecimal("1.5"), list.get(1), toon);
    }

    /** A @ref is the value it names, in a list and a typed collection, before or after the value. */
    @Test
    void aRefIsTheValueItNames() {
        String value = "{\"@id\":1,\"@type\":\"BigDecimal\",\"value\":\"1.5\"}";
        String[] texts = {"{\"a\":" + value + ",\"l\":[{\"@ref\":1}],\"t\":{\"@type\":\"ArrayList\",\"@items\":[{\"@ref\":1}]}}",
                "{\"l\":[{\"@ref\":1}],\"t\":{\"@type\":\"ArrayList\",\"@items\":[{\"@ref\":1}]},\"a\":" + value + "}"};
        for (String text : texts) {
            Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(text, null).asClass(null);
            assertValue(new BigDecimal("1.5"), read.get("a"), text);
            assertValue(new BigDecimal("1.5"), first(read.get("l")), text);
            assertValue(new BigDecimal("1.5"), first(read.get("t")), text);
        }
    }
}
