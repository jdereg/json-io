package com.cedarsoftware.io;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode a simple enum -- one that is only its constants: no fields of its own, no constant with a body -- on
 * the classpath is its constant wherever json-io wrote it with its type: where Object is declared, in a list, an
 * array, a typed collection, a map's keys, at the root. Any other enum stays JSON, as an object of a class does, unless
 * a type declares it (a field of the root's class, an array of that enum): it was its constant in a field and a root
 * array whatever its kind, and the object holding it in a list, a typed collection, a map key and at the root.
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
class MapsModeSimpleEnumsTest {
    public enum Level { LOW, HIGH }

    /** A field of its own. */
    public enum Coded {
        A("a"), B("b");

        private final String code;

        Coded(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    /** A constant with a body: json-io names its class Bodied$1. */
    public enum Bodied {
        X {
            @Override
            int weight() {
                return 1;
            }
        },
        Y;

        int weight() {
            return 0;
        }
    }

    private static Map<String, Object> holder(Object value) {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("field", value);
        holder.put("list", new ArrayList<>(Arrays.asList(value)));
        holder.put("array", new Object[] {value});
        holder.put("set", new LinkedHashSet<>(Arrays.asList(value)));
        Map<Object, Object> keys = new LinkedHashMap<>();
        keys.put(value, "k");
        holder.put("keys", keys);
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

    private static List<Object> positions(Object value) {
        String json = JsonIo.toJson(holder(value), null);
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
        assertEquals(json, JsonIo.toJson(read, null));      // written back unchanged
        // At the root json-io writes an enum as its name, JSON text; this is the typed form it writes everywhere else
        String typed = "{\"@type\":\"" + value.getClass().getName() + "\",\"name\":\"" + ((Enum<?>) value).name() + "\"}";
        Object root = JsonIo.toMaps(typed, null).asClass(null);
        return Arrays.asList(read.get("field"), first(read.get("list")), first(read.get("array")), first(read.get("set")),
                ((Map<?, ?>) read.get("keys")).keySet().iterator().next(), root);
    }

    @Test
    void aSimpleEnumIsItsConstantEverywhere() {
        for (Object constant : new Object[] {Level.HIGH, DayOfWeek.MONDAY}) {
            for (Object read : positions(constant)) {
                assertSame(constant, read);
            }
        }
    }

    @Test
    void anotherEnumStaysJsonEverywhere() {
        for (Object constant : new Object[] {Coded.A, Bodied.X, Bodied.Y, TimeUnit.SECONDS}) {
            for (Object read : positions(constant)) {
                assertInstanceOf(JsonObject.class, read, constant + ": " + read);
            }
        }
    }

    /** An array whose type declares the enum reads its elements as that enum, as a field of that type does. */
    @Test
    void anArrayOfAnEnumIsItsConstants() {
        Object[] read = ((JsonObject) ((Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(holder(new Coded[] {Coded.A, Coded.B}),
                new WriteOptionsBuilder().showTypeInfoAlways().build()), null).asClass(null)).get("field")).getItems();
        assertArrayEquals(new Object[] {Coded.A, Coded.B}, read);
    }

    @Test
    void whatIsSimple() {
        assertTrue(MapResolver.isSimpleEnum(Level.class));
        assertTrue(MapResolver.isSimpleEnum(DayOfWeek.class));
        assertFalse(MapResolver.isSimpleEnum(Coded.class));
        assertFalse(MapResolver.isSimpleEnum(Bodied.class));
        assertFalse(MapResolver.isSimpleEnum(TimeUnit.class));
    }
}
