package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code Class} map key is written by its name in TOON, as it is written as a value, and as JSON and Jackson write
 * it as a key. TOON wrote its {@code toString()}, {@code "class java.lang.String"}, which did not read back ("Class
 * not found").
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
class ClassMapKeyTextTest {
    public static class ByClass {
        public Map<Class<?>, String> byClass = new LinkedHashMap<>();
        public Map<String, Class<?>> classes = new LinkedHashMap<>();
    }

    private static ByClass byClass() {
        ByClass b = new ByClass();
        b.byClass.put(String.class, "s");
        b.byClass.put(int[].class, "ia");
        b.byClass.put(Map.Entry.class, "e");       // an interface, nested
        b.byClass.put(long.class, "l");
        b.classes.put("v", Integer.class);
        return b;
    }

    @Test
    void toonWritesTheNameAndReadsItBack() {
        String toon = JsonIo.toToon(byClass(), null);
        assertTrue(toon.contains("byClass:\n  java.lang.String: s\n  \"[I\": ia\n  \"java.util.Map$Entry\": e\n"
                + "  long: l\n"), toon);
        assertFalse(toon.contains("class "), toon);
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().cycleSupport(true).build()};
        for (WriteOptions options : all) {
            toon = JsonIo.toToon(byClass(), options);
            ByClass back = JsonIo.fromToon(toon, null).asClass(ByClass.class);
            assertEquals(byClass().byClass, back.byClass, toon);
            assertEquals(byClass().classes, back.classes, toon);
        }
    }

    /** The key's text is JSON's and Jackson's. */
    @Test
    void theKeyIsTheTextJsonAndJacksonWrite() throws Exception {
        Map<Class<?>, String> map = byClass().byClass;
        String jackson = new ObjectMapper().writeValueAsString(map);
        String json = JsonIo.toJson(map, new WriteOptionsBuilder().standardJson().build());
        assertEquals(jackson, json);
        Map<?, ?> fromToon = JsonIo.fromToon(JsonIo.toToon(map, null), null).asClass(Map.class);
        Map<?, ?> fromJson = JsonIo.toJava(json, null).asClass(Map.class);
        assertEquals(fromJson, fromToon);
        assertEquals("java.lang.String", fromToon.keySet().iterator().next());
    }
}
