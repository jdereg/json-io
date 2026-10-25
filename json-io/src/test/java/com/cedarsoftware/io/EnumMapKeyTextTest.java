package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enum map key is written as the enum is written as a value, in JSON (keys stringified) and TOON alike: the value
 * of its {@code @IoValue} (or {@code @JsonValue}) method, else its name -- as Jackson writes it. JSON with
 * {@code stringifyMapKeys} wrote the name where the value was written everywhere else (Converter's text), and TOON
 * wrote a plain enum's {@code toString()}, which did not read back when the enum overrides it ("No enum constant").
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
class EnumMapKeyTextTest {
    public enum Code {
        RED("r"), GREEN("g") {
            @Override
            public String toString() {
                return "Green!";
            }
        };

        private final String c;

        Code(String c) {
            this.c = c;
        }

        @JsonValue
        public String c() {
            return c;
        }
    }

    public enum Num {
        ONE(1), NINE(9);

        private final int n;

        Num(int n) {
            this.n = n;
        }

        @JsonValue
        public int n() {
            return n;
        }
    }

    public enum Flag {
        ON(true), OFF(false);

        private final boolean b;

        Flag(boolean b) {
            this.b = b;
        }

        @JsonValue
        public boolean b() {
            return b;
        }
    }

    /** json-io's own annotation: the same as Jackson's. */
    public enum Size {
        SMALL("s"), LARGE("l");

        private final String s;

        Size(String s) {
            this.s = s;
        }

        @IoValue
        public String s() {
            return s;
        }
    }

    public enum Plain { A, B }

    /** Its text is not its name: a constant with a body, and the enum's own. */
    public enum Pretty {
        ONE {
            @Override
            public String toString() {
                return "One!";
            }
        },
        TWO;

        @Override
        public String toString() {
            return "pretty-" + name();
        }
    }

    public static class Maps {
        public Map<Code, String> byCode = new LinkedHashMap<>();
        public EnumMap<Code, String> enumMap = new EnumMap<>(Code.class);
        public Map<Num, String> byNum = new LinkedHashMap<>();
        public Map<Flag, String> byFlag = new LinkedHashMap<>();
        public Map<Plain, String> byPlain = new LinkedHashMap<>();
        public Map<Pretty, String> byPretty = new LinkedHashMap<>();
        public EnumMap<Pretty, String> prettyMap = new EnumMap<>(Pretty.class);
        public Map<String, Map<Code, String>> nested = new LinkedHashMap<>();
        public List<Map<Pretty, String>> inList = new ArrayList<>();
        public Map<Size, String> bySize = new LinkedHashMap<>();
    }

    private static Maps maps() {
        Maps m = new Maps();
        m.byCode.put(Code.GREEN, "x");
        m.byCode.put(Code.RED, "y");
        m.enumMap.put(Code.GREEN, "e");
        m.byNum.put(Num.NINE, "n");
        m.byFlag.put(Flag.OFF, "f");
        m.byPlain.put(Plain.B, "p");
        m.byPretty.put(Pretty.ONE, "o");
        m.byPretty.put(Pretty.TWO, "t");
        m.prettyMap.put(Pretty.ONE, "po");
        Map<Code, String> inner = new LinkedHashMap<>();
        inner.put(Code.RED, "i");
        m.nested.put("k", inner);
        Map<Pretty, String> listed = new LinkedHashMap<>();
        listed.put(Pretty.TWO, "l");
        m.inList.add(listed);
        return m;
    }

    private static void assertSameMaps(Maps expected, Maps actual, String text) {
        assertEquals(expected.byCode, actual.byCode, text);
        assertEquals(expected.enumMap, actual.enumMap, text);
        assertEquals(expected.byNum, actual.byNum, text);
        assertEquals(expected.byFlag, actual.byFlag, text);
        assertEquals(expected.byPlain, actual.byPlain, text);
        assertEquals(expected.byPretty, actual.byPretty, text);
        assertEquals(expected.prettyMap, actual.prettyMap, text);
        assertEquals(expected.nested, actual.nested, text);
        assertEquals(expected.inList, actual.inList, text);
        assertEquals(expected.bySize, actual.bySize, text);
    }

    /** standardJson() writes these maps as Jackson does, byte for byte: each key as the enum is written. */
    @Test
    void jsonKeysAreWhatJacksonWrites() throws Exception {
        String jackson = new ObjectMapper().writeValueAsString(maps());
        String json = JsonIo.toJson(maps(), new WriteOptionsBuilder().standardJson().build());
        assertEquals(jackson, json);
        assertTrue(json.contains("\"byCode\":{\"g\":\"x\",\"r\":\"y\"}"), json);
        assertTrue(json.contains("\"byNum\":{\"9\":\"n\"}"), json);
        assertTrue(json.contains("\"byFlag\":{\"false\":\"f\"}"), json);
        assertTrue(json.contains("\"byPretty\":{\"ONE\":\"o\",\"TWO\":\"t\"}"), json);
    }

    @Test
    void jsonReadsTheKeysBack() {
        Maps sized = maps();
        sized.bySize.put(Size.SMALL, "s");
        assertTrue(JsonIo.toJson(sized, new WriteOptionsBuilder().standardJson().build())
                .contains("\"bySize\":{\"s\":\"s\"}"));
        WriteOptions[] all = {new WriteOptionsBuilder().stringifyMapKeys(true).build(),
                new WriteOptionsBuilder().standardJson().build(),
                new WriteOptionsBuilder().stringifyMapKeys(true).showTypeInfoAlways().build(),
                new WriteOptionsBuilder().stringifyMapKeys(true).cycleSupport(true).build(),
                new WriteOptionsBuilder().build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(maps(), options);
            assertSameMaps(maps(), JsonIo.toJava(json, null).asClass(Maps.class), json);
        }
    }

    @Test
    void toonKeysAreTheValueOrTheName() {
        String toon = JsonIo.toToon(maps(), null);
        assertTrue(toon.contains("byCode:\n  g: x\n  r: y\n"), toon);
        assertTrue(toon.contains("byNum:\n  \"9\": n\n"), toon);
        assertTrue(toon.contains("byPretty:\n  ONE: o\n  TWO: t\n"), toon);
        assertTrue(toon.contains("prettyMap:\n  ONE: po\n"), toon);
        assertTrue(!toon.contains("One!") && !toon.contains("pretty-"), toon);
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().cycleSupport(true).build()};
        for (WriteOptions options : all) {
            toon = JsonIo.toToon(maps(), options);
            assertSameMaps(maps(), JsonIo.fromToon(toon, null).asClass(Maps.class), toon);
        }
    }

    /** A map with untyped keys: the text the enum is written as (it reads back as that text: there is no type). */
    @Test
    void anUntypedKeyIsTheSameText() {
        Map<Object, String> map = new LinkedHashMap<>();
        map.put(Code.GREEN, "a");
        map.put(Pretty.ONE, "b");
        map.put(Num.ONE, "c");
        map.put(Size.LARGE, "d");
        String json = JsonIo.toJson(map, new WriteOptionsBuilder().standardJson().build());
        assertEquals("{\"g\":\"a\",\"ONE\":\"b\",\"1\":\"c\",\"l\":\"d\"}", json);
        assertEquals("g: a\nONE: b\n\"1\": c\nl: d", JsonIo.toToon(map, null).trim());
    }

    /** A custom writer registered for the enum comes first for a value; for a key, the name, in both formats. */
    @Test
    void underACustomWriterTheKeyIsTheName() {
        WriteOptions options = new WriteOptionsBuilder().stringifyMapKeys(true)
                .addCustomWrittenClass(Code.class, new Writers.EnumsAsStringWriter()).build();
        Maps m = new Maps();
        m.byCode.put(Code.GREEN, "x");
        String json = JsonIo.toJson(m, options);
        assertTrue(json.contains("\"byCode\":{\"GREEN\":\"x\"}"), json);
        assertEquals(m.byCode, JsonIo.toJava(json, null).asClass(Maps.class).byCode, json);
        String toon = JsonIo.toToon(m, options);
        assertTrue(toon.contains("byCode:\n  GREEN: x\n"), toon);
        assertEquals(m.byCode, JsonIo.fromToon(toon, null).asClass(Maps.class).byCode, toon);
    }
}
