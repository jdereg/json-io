package com.cedarsoftware.io;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cedarsoftware.io.annotation.IoValue;
import com.fasterxml.jackson.annotation.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enum with an {@code @IoValue} method (or Jackson's {@code @JsonValue}) is written as the value that method returns,
 * {@code "g"} rather than {@code "GREEN"}, wherever it appears: a field, an {@code Object} field, a list, an array, an
 * {@code EnumSet}, an {@code EnumMap}'s and a map's keys, a map's values, an {@code Optional}, the root; as JSON and as
 * TOON, as Jackson writes it. It reads back from that value, and from its name, so what json-io wrote before (and
 * the names anyone wrote) still reads; where a value is also another constant's name, the value wins. Up to now
 * json-io ignored the method on an enum: it wrote the name, and Jackson's {@code "g"} did not read.
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
class IoValueEnumTest {
    public enum Color {
        RED("r"), GREEN("g"), BLUE("b");

        private final String code;

        Color(String code) {
            this.code = code;
        }

        @JsonValue
        public String code() {
            return code;
        }
    }

    /** Constants with bodies: each is a subclass of the enum. */
    public enum Shade {
        LIGHT("l") {
            int weight() {
                return 1;
            }
        },
        DARK("d") {
            int weight() {
                return 2;
            }
        };

        private final String code;

        Shade(String code) {
            this.code = code;
        }

        abstract int weight();

        @IoValue
        public String code() {
            return code;
        }
    }

    /** A number for a value. */
    public enum Level {
        LOW(10), HIGH(20);

        private final int n;

        Level(int n) {
            this.n = n;
        }

        @IoValue
        public int n() {
            return n;
        }
    }

    /** Each value is the other constant's name. */
    public enum Swap {
        A("B"), B("A");

        private final String code;

        Swap(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }
    }

    /** Two constants with one value: the first is the one it reads as. */
    public enum Twice {
        FIRST("same"), SECOND("same");

        private final String code;

        Twice(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }
    }

    public enum Plain { X, Y }

    public static class Bean {
        public Color color;
        public Object any;
        public List<Color> list;
        public Color[] array;
        public EnumSet<Color> set;
        public EnumMap<Color, String> enumMap;
        public Map<Color, String> keyed;
        public Map<String, Color> values;
        public Optional<Color> opt;
        public Level level;
        public List<Level> levels;
        public Shade shade;
        public Object anyShade;
        public Plain plain;
    }

    private static Bean bean() {
        Bean b = new Bean();
        b.color = Color.GREEN;
        b.any = Color.BLUE;
        b.list = new ArrayList<>(Arrays.asList(Color.RED, Color.GREEN));
        b.array = new Color[] {Color.BLUE};
        b.set = EnumSet.of(Color.RED, Color.BLUE);
        b.enumMap = new EnumMap<>(Color.class);
        b.enumMap.put(Color.GREEN, "eg");
        b.keyed = new LinkedHashMap<>();
        b.keyed.put(Color.BLUE, "kb");
        b.values = new LinkedHashMap<>();
        b.values.put("v", Color.RED);
        b.opt = Optional.of(Color.GREEN);
        b.level = Level.HIGH;
        b.levels = new ArrayList<>(Arrays.asList(Level.LOW, Level.HIGH));
        b.shade = Shade.DARK;
        b.anyShade = Shade.LIGHT;
        b.plain = Plain.Y;
        return b;
    }

    private static void assertSameBean(Bean expected, Bean actual, boolean typed, String text) {
        assertSame(expected.color, actual.color, text);
        if (typed) {
            assertSame(expected.any, actual.any, text);
            assertSame(expected.anyShade, actual.anyShade, text);
        }
        assertEquals(expected.list, actual.list, text);
        assertArrayEquals(expected.array, actual.array, text);
        assertEquals(expected.set, actual.set, text);
        assertEquals(expected.enumMap, actual.enumMap, text);
        assertEquals(expected.keyed, actual.keyed, text);
        assertEquals(expected.values, actual.values, text);
        assertEquals(expected.opt, actual.opt, text);
        assertSame(expected.level, actual.level, text);
        assertEquals(expected.levels, actual.levels, text);
        assertSame(expected.shade, actual.shade, text);
        assertSame(expected.plain, actual.plain, text);
    }

    @Test
    void jsonWritesTheValueWhereverTheEnumIs() {
        String json = JsonIo.toJson(bean(), null);
        assertTrue(json.contains("\"color\":\"g\""), json);
        assertTrue(json.contains("\"level\":20"), json);
        assertTrue(json.contains("\"shade\":\"d\""), json);
        assertTrue(json.contains("\"plain\":\"Y\""), json);         // an enum without the method: its name, as before
        for (String name : new String[] {"RED", "GREEN", "BLUE", "LOW", "HIGH", "LIGHT", "DARK"}) {
            assertFalse(json.contains("\"" + name + "\""), name + " in " + json);
        }
    }

    @Test
    void jsonReadsTheValueBack() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoNever().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().writeEnumAsJsonObject(true).build()};
        for (WriteOptions options : all) {
            Bean b = bean();
            String json = JsonIo.toJson(b, options);
            assertSameBean(b, JsonIo.toJava(json, null).asClass(Bean.class), options != all[1], json);
        }
    }

    @Test
    void toonWritesAndReadsTheValue() {
        String toon = JsonIo.toToon(bean(), null);
        assertTrue(toon.contains("color: g"), toon);
        assertTrue(toon.contains("level: 20"), toon);
        assertFalse(toon.contains("GREEN"), toon);
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            Bean b = bean();
            toon = JsonIo.toToon(b, options);
            assertSameBean(b, JsonIo.fromToon(toon, null).asClass(Bean.class), options != null, toon);
        }
    }

    /** What json-io wrote through 4.117.0, the names, still reads. */
    @Test
    void theNamesStillRead() {
        String color = Color.class.getName();
        String json = "{\"color\":\"GREEN\",\"any\":{\"@type\":\"" + color + "\",\"name\":\"BLUE\"},"
                + "\"list\":[{\"@type\":\"" + color + "\",\"name\":\"RED\"},{\"@type\":\"" + color + "\",\"name\":\"GREEN\"}],"
                + "\"array\":[\"BLUE\"],\"set\":{\"@enum\":\"" + color + "\",\"@items\":[\"RED\",\"BLUE\"]},"
                + "\"enumMap\":{\"@keys\":[{\"@type\":\"" + color + "\",\"name\":\"GREEN\"}],\"@items\":[\"eg\"]},"
                + "\"keyed\":{\"@keys\":[{\"@type\":\"" + color + "\",\"name\":\"BLUE\"}],\"@items\":[\"kb\"]},"
                + "\"values\":{\"v\":{\"@type\":\"" + color + "\",\"name\":\"RED\"}},\"opt\":{\"@type\":\"" + color
                + "\",\"name\":\"GREEN\"},\"level\":\"HIGH\",\"levels\":[\"LOW\",\"HIGH\"],\"shade\":\"DARK\","
                + "\"anyShade\":{\"@type\":\"" + Shade.class.getName() + "$1\",\"name\":\"LIGHT\"},\"plain\":\"Y\"}";
        assertSameBean(bean(), JsonIo.toJava(json, null).asClass(Bean.class), true, json);

        String toon = "color: GREEN\nlist[2]: RED,GREEN\narray[1]: BLUE\nset[2]: RED,BLUE\nenumMap:\n  GREEN: eg\n"
                + "keyed:\n  BLUE: kb\nvalues:\n  v: RED\nopt: GREEN\nlevel: HIGH\nlevels[2]: LOW,HIGH\nshade: DARK\nplain: Y\n";
        Bean back = JsonIo.fromToon(toon, null).asClass(Bean.class);
        assertSameBean(bean(), back, false, toon);
    }

    /** Jackson's output for the same class reads (an Object field it writes untyped, so it reads as text). */
    @Test
    void jacksonsOutputReads() {
        String jackson = "{\"color\":\"g\",\"list\":[\"r\",\"g\"],\"array\":[\"b\"],\"set\":[\"r\",\"b\"],"
                + "\"enumMap\":{\"g\":\"eg\"},\"keyed\":{\"b\":\"kb\"},\"values\":{\"v\":\"r\"},\"opt\":\"g\","
                + "\"level\":20,\"levels\":[10,20],\"shade\":\"d\",\"plain\":\"Y\"}";
        assertSameBean(bean(), JsonIo.toJava(jackson, null).asClass(Bean.class), false, jackson);
    }

    @Test
    void theRootReadsFromTheValueAndTheName() {
        assertSame(Color.GREEN, JsonIo.toJava("\"g\"", null).asClass(Color.class));
        assertSame(Color.GREEN, JsonIo.toJava("\"GREEN\"", null).asClass(Color.class));
        assertSame(Level.HIGH, JsonIo.toJava("20", null).asClass(Level.class));
        assertSame(Color.BLUE, JsonIo.toJava(JsonIo.toJson(Color.BLUE, null), null).asClass(null));
        assertSame(Shade.LIGHT, JsonIo.toJava(JsonIo.toJson(Shade.LIGHT, null), null).asClass(null));
        assertSame(Color.RED, JsonIo.fromToon("r", null).asClass(Color.class));
    }

    /** A value that is also a constant's name is the value: what json-io and Jackson write. */
    @Test
    void theValueWinsOverAName() {
        assertSame(Swap.A, JsonIo.toJava("\"B\"", null).asClass(Swap.class));
        assertSame(Swap.B, JsonIo.toJava("\"A\"", null).asClass(Swap.class));
        assertSame(Swap.A, JsonIo.toJava(JsonIo.toJson(Swap.A, null), null).asClass(Swap.class));
        assertSame(Twice.FIRST, JsonIo.toJava("\"same\"", null).asClass(Twice.class));
    }

    /** A custom writer registered for the enum still wins, as it does for any class. */
    @Test
    void aCustomWriterStillWins() {
        WriteOptions options = new WriteOptionsBuilder().addCustomWrittenClass(Color.class, new Writers.EnumsAsStringWriter()).build();
        Bean b = new Bean();
        b.color = Color.GREEN;
        String json = JsonIo.toJson(b, options);
        assertTrue(json.contains("\"color\":\"GREEN\""), json);
        assertSame(Color.GREEN, JsonIo.toJava(json, null).asClass(Bean.class).color);
        String toon = JsonIo.toToon(b, options);
        assertTrue(toon.contains("color: GREEN"), toon);
        assertSame(Color.GREEN, JsonIo.fromToon(toon, null).asClass(Bean.class).color);
    }

    /** Through WriteOptions other than json-io's own: the same, the value or, under a custom writer, the name. */
    @Test
    void anotherWriteOptionsImplementationWritesTheSame() {
        Bean b = new Bean();
        b.color = Color.GREEN;
        WriteOptions[] reals = {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().addCustomWrittenClass(Color.class, new Writers.EnumsAsStringWriter()).build()};
        String[] expected = {"color: g", "color: GREEN"};
        for (int i = 0; i < reals.length; i++) {
            WriteOptions real = reals[i];
            WriteOptions other = (WriteOptions) Proxy.newProxyInstance(WriteOptions.class.getClassLoader(),
                    new Class<?>[] {WriteOptions.class}, (proxy, method, args) -> {
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
            String toon = JsonIo.toToon(b, other);
            assertTrue(toon.contains(expected[i]), toon);
            assertSame(Color.GREEN, JsonIo.fromToon(toon, null).asClass(Bean.class).color);
        }
    }
}
