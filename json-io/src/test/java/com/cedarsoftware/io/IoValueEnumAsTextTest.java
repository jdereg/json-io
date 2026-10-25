package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enum written by an {@code @IoValue} method that returns a boolean or a number reads back from that value as text:
 * a map key in TOON ({@code false: x}, {@code "9": h}), a key stringified in JSON, text in a field. Looked up by the
 * value itself, {@code "false"} missed {@code false} and was read as a name ("No enum constant Flag.false"). No
 * constant can be named {@code false} or {@code 9}, so the text form takes no name's place. Read by that value itself,
 * such an enum reads back everywhere, which this pins.
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
class IoValueEnumAsTextTest {
    public enum Flag {
        ON(true), OFF(false);

        private final boolean b;

        Flag(boolean b) {
            this.b = b;
        }

        @IoValue
        public boolean b() {
            return b;
        }
    }

    public enum Level {
        LOW(1), HIGH(9);

        private final int n;

        Level(int n) {
            this.n = n;
        }

        @IoValue
        public int n() {
            return n;
        }
    }

    public enum Ratio {
        HALF(0.5), TWO(2.0);

        private final double r;

        Ratio(double r) {
            this.r = r;
        }

        @IoValue
        public double r() {
            return r;
        }
    }

    public static class Keyed {
        public Map<Flag, String> byFlag = new LinkedHashMap<>();
        public Map<Level, String> byLevel = new LinkedHashMap<>();
        public Map<Ratio, String> byRatio = new LinkedHashMap<>();

        Keyed() {
            byFlag.put(Flag.OFF, "x");
            byFlag.put(Flag.ON, "y");
            byLevel.put(Level.HIGH, "h");
            byRatio.put(Ratio.HALF, "a");
            byRatio.put(Ratio.TWO, "b");
        }
    }

    public static class Held {
        public Flag flag = Flag.OFF;
        public Level level = Level.HIGH;
        public Ratio ratio = Ratio.HALF;
        public List<Flag> flags = new ArrayList<>(Arrays.asList(Flag.ON, Flag.OFF));
        public Level[] levels = {Level.LOW};
        public EnumSet<Flag> set = EnumSet.of(Flag.ON);
    }

    private static final WriteOptions STRINGIFY = new WriteOptionsBuilder().stringifyMapKeys(true).build();

    @Test
    void mapKeys() {
        Keyed keyed = new Keyed();
        String toon = JsonIo.toToon(keyed, null);
        assertTrue(toon.contains("false: x"), toon);
        assertEquals(keyed.byFlag, JsonIo.fromToon(toon, null).asClass(Keyed.class).byFlag);
        assertEquals(keyed.byLevel, JsonIo.fromToon(toon, null).asClass(Keyed.class).byLevel);
        assertEquals(keyed.byRatio, JsonIo.fromToon(toon, null).asClass(Keyed.class).byRatio);

        // (JSON stringifies an enum key as its name, read back as before)
        Keyed back = JsonIo.toJava(JsonIo.toJson(keyed, STRINGIFY), null).asClass(Keyed.class);
        assertEquals(keyed.byFlag, back.byFlag);
        assertEquals(keyed.byLevel, back.byLevel);
        assertEquals(keyed.byRatio, back.byRatio);

        // Hand-written, a key as the value's text
        String type = Keyed.class.getName();
        back = JsonIo.toJava("{\"@type\":\"" + type + "\",\"byFlag\":{\"false\":\"x\"},\"byLevel\":{\"9\":\"h\"},"
                + "\"byRatio\":{\"0.5\":\"a\"}}", null).asClass(Keyed.class);
        assertEquals("x", back.byFlag.get(Flag.OFF));
        assertEquals("h", back.byLevel.get(Level.HIGH));
        assertEquals("a", back.byRatio.get(Ratio.HALF));
    }

    /** Text in a field: the value's text, or the constant's name, as before. */
    @Test
    void textInAField() {
        String type = Held.class.getName();
        Held held = JsonIo.toJava("{\"@type\":\"" + type + "\",\"flag\":\"true\",\"level\":\"9\",\"ratio\":\"2.0\"}",
                null).asClass(Held.class);
        assertEquals(Flag.ON, held.flag);
        assertEquals(Level.HIGH, held.level);
        assertEquals(Ratio.TWO, held.ratio);
        held = JsonIo.toJava("{\"@type\":\"" + type + "\",\"flag\":\"OFF\",\"level\":\"LOW\",\"ratio\":\"5e-1\"}",
                null).asClass(Held.class);
        assertEquals(Flag.OFF, held.flag);
        assertEquals(Level.LOW, held.level);
        assertEquals(Ratio.HALF, held.ratio);
    }

    @Test
    void byTheValueEverywhere() {
        Held held = new Held();
        for (Held back : new Held[] {JsonIo.toJava(JsonIo.toJson(held, null), null).asClass(Held.class),
                JsonIo.fromToon(JsonIo.toToon(held, null), null).asClass(Held.class)}) {
            assertEquals(held.flag, back.flag);
            assertEquals(held.level, back.level);
            assertEquals(held.ratio, back.ratio);
            assertEquals(held.flags, back.flags);
            assertEquals(Arrays.asList(held.levels), Arrays.asList(back.levels));
            assertEquals(held.set, back.set);
        }
        assertEquals(Flag.ON, JsonIo.toJava("true", null).asClass(Flag.class));
        assertEquals(Level.HIGH, JsonIo.toJava("9", null).asClass(Level.class));
    }
}
