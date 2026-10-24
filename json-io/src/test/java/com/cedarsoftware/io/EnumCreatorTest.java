package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An enum with a one-parameter static {@code @IoCreator} (or Jackson's {@code @JsonCreator}) factory is read through
 * it, as Jackson reads it: {@code Shade.from("Dark")} where the factory accepts a code or a name in any case. Then, if
 * the factory throws or returns null, by the value its {@code @IoValue} method writes, then by name, so what json-io
 * wrote through 4.117.0 (names) reads as before. Up to now json-io ignored the factory: only the value and the exact
 * name read. Writing does not change.
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
class EnumCreatorTest {
    /** Written as its code; the factory reads a code or a name in any case, and throws for anything else. */
    public enum Shade {
        LIGHT("l"), DARK("d");

        private final String code;

        Shade(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }

        @IoCreator
        public static Shade from(String text) {
            for (Shade s : values()) {
                if (s.code.equalsIgnoreCase(text) || s.name().equalsIgnoreCase(text)) {
                    return s;
                }
            }
            throw new IllegalArgumentException("no shade " + text);
        }
    }

    /** Written by name; Jackson's factory reads abbreviations and returns null for anything else. */
    public enum Size {
        SMALL, LARGE;

        @JsonCreator
        static Size parse(String text) {
            return "S".equalsIgnoreCase(text) ? SMALL : "L".equalsIgnoreCase(text) ? LARGE : null;
        }
    }

    /** A factory taking a number: a name, which it cannot take, reads by name. */
    public enum Level {
        LOW, HIGH;

        @IoCreator
        public static Level of(int n) {
            if (n == 1) {
                return LOW;
            }
            if (n == 2) {
                return HIGH;
            }
            throw new IllegalArgumentException("no level " + n);
        }
    }

    /** Written as its code; the factory reads codes only and throws for a name, which json-io wrote through 4.117.0. */
    public enum Color {
        RED("r"), GREEN("g");

        private final String code;

        Color(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }

        @IoCreator
        public static Color fromCode(String text) {
            for (Color c : values()) {
                if (c.code.equals(text)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("no color code " + text);
        }
    }

    /** Written as its code; the factory reads aliases only and returns null for the code, read by value. */
    public enum Tone {
        WARM("w"), COOL("c");

        private final String code;

        Tone(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }

        @JsonCreator
        public static Tone alias(String text) {
            return "hot".equals(text) ? WARM : "cold".equals(text) ? COOL : null;
        }
    }

    /** {@code @IoCreator} on an enum's constructor builds nothing: read by name. */
    public enum Odd {
        ONE;

        @IoCreator
        Odd() {
        }
    }

    public static class Holder {
        public Shade one;
        public Object any;
        public List<Shade> list;
        public Shade[] array;
        public Map<String, Shade> values;
        public Map<Shade, String> keys;
        public EnumSet<Shade> set;
        public Optional<Shade> opt;
        public Size size;
        public List<Size> sizes;
        public Level level;
        public List<Level> levels;
        public Odd odd;
    }

    private static String json(String shade, String size, String level) {
        return "{\"one\":\"" + shade + "\",\"any\":{\"@type\":\"" + Shade.class.getName() + "\",\"value\":\"" + shade
                + "\"},\"list\":[\"" + shade + "\"],\"array\":[\"" + shade + "\"],\"values\":{\"k\":\"" + shade
                + "\"},\"keys\":{\"" + shade + "\":\"v\"},\"set\":[\"" + shade + "\"],\"opt\":\"" + shade
                + "\",\"size\":\"" + size + "\",\"sizes\":[\"" + size + "\"],\"level\":" + level + ",\"levels\":["
                + level + "],\"odd\":\"ONE\"}";
    }

    private static void assertRead(Holder h, Shade shade, Size size, Level level) {
        assertEquals(shade, h.one);
        assertEquals(shade, h.any);
        assertEquals(Collections.singletonList(shade), h.list);
        assertArrayEquals(new Shade[] {shade}, h.array);
        assertEquals(shade, h.values.get("k"));
        assertEquals(Collections.singletonList(shade), new ArrayList<>(h.keys.keySet()));
        assertEquals(EnumSet.of(shade), h.set);
        assertEquals(Optional.of(shade), h.opt);
        assertEquals(size, h.size);
        assertEquals(Collections.singletonList(size), h.sizes);
        assertEquals(level, h.level);
        assertEquals(Collections.singletonList(level), h.levels);
        assertEquals(Odd.ONE, h.odd);
    }

    @Test
    void theFactoryReadsFirst() {
        for (String text : new String[] {"Dark", "dark", "D"}) {
            Holder h = JsonIo.toJava(json(text, "s", "2"), null).asClass(Holder.class);
            assertRead(h, Shade.DARK, Size.SMALL, Level.HIGH);
        }
    }

    /** The value and the name read as before: through the factory, or past one that does not know them. */
    @Test
    void valuesAndNamesReadAsBefore() {
        assertRead(JsonIo.toJava(json("d", "SMALL", "\"HIGH\""), null).asClass(Holder.class),
                Shade.DARK, Size.SMALL, Level.HIGH);
        assertRead(JsonIo.toJava(json("DARK", "LARGE", "\"LOW\""), null).asClass(Holder.class),
                Shade.DARK, Size.LARGE, Level.LOW);
    }

    /** A factory that does not know a value (it throws, or returns null) leaves it to the value, then the name. */
    @Test
    void pastAFactoryThatDoesNotKnowIt() {
        assertEquals(Color.GREEN, JsonIo.toJava("\"g\"", null).asClass(Color.class));
        assertEquals(Color.GREEN, JsonIo.toJava("\"GREEN\"", null).asClass(Color.class));
        assertEquals(Collections.singletonList(Color.RED), JsonIo.toJava("[\"RED\"]", null)
                .asType(new TypeHolder<List<Color>>() {}));
        assertEquals(Tone.WARM, JsonIo.toJava("\"hot\"", null).asClass(Tone.class));
        assertEquals(Tone.WARM, JsonIo.toJava("\"w\"", null).asClass(Tone.class));
        assertEquals(Tone.COOL, JsonIo.toJava("\"COOL\"", null).asClass(Tone.class));
        assertEquals(EnumSet.of(Tone.COOL), JsonIo.toJava("{\"@type\":\"java.util.RegularEnumSet\",\"@enum\":\""
                + Tone.class.getName() + "\",\"@items\":[\"c\"]}", null).asClass(EnumSet.class));
    }

    @Test
    void atTheRootAndInToon() {
        assertEquals(Shade.LIGHT, JsonIo.toJava("\"Light\"", null).asClass(Shade.class));
        assertEquals(Size.LARGE, JsonIo.toJava("\"l\"", null).asClass(Size.class));
        assertEquals(Level.LOW, JsonIo.toJava("1", null).asClass(Level.class));
        Holder h = JsonIo.fromToon("one: Dark\nlist[1]: dark\nsize: s\nsizes[1]: L\nlevel: 2\nlevels[1]: 1\n", null)
                .asClass(Holder.class);
        assertEquals(Shade.DARK, h.one);
        assertEquals(Collections.singletonList(Shade.DARK), h.list);
        assertEquals(Size.SMALL, h.size);
        assertEquals(Collections.singletonList(Size.LARGE), h.sizes);
        assertEquals(Level.HIGH, h.level);
        assertEquals(Collections.singletonList(Level.LOW), h.levels);
    }

    @Test
    void whatNoneReadsIsAnError() {
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"one\":\"x\"}", null).asClass(Holder.class));
        assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"sizes\":[\"medium\"]}", null).asClass(Holder.class));
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"level\":7}", null).asClass(Holder.class));
    }

    /** Writing does not change: the value, else the name; and it reads back. */
    @Test
    void writingIsUnchanged() {
        Holder h = new Holder();
        h.one = Shade.LIGHT;
        h.size = Size.LARGE;
        h.level = Level.HIGH;
        h.keys = new LinkedHashMap<>();
        h.keys.put(Shade.DARK, "v");
        String json = JsonIo.toJson(h, new WriteOptionsBuilder().showTypeInfoNever().build());
        assertEquals(true, json.contains("\"one\":\"l\""), json);
        assertEquals(true, json.contains("\"size\":\"LARGE\""), json);
        assertEquals(true, json.contains("\"level\":\"HIGH\""), json);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(Shade.LIGHT, back.one);
        assertEquals(Size.LARGE, back.size);
        assertEquals(Level.HIGH, back.level);
        assertEquals(Shade.DARK, back.keys.keySet().iterator().next());
    }
}
