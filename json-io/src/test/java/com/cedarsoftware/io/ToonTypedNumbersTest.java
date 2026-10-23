package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON with type info on (showTypeInfoMinimal, MinimalPlus, Always) and a Double or Float where nothing declares its
 * type: the root, an Object[] or raw list element, a map value, a field declared Object or Number, a table row, a key
 * declared Number, an Optional's content. TOON writes a number with no exponent and no ".0" (spec section 2), so a
 * whole-number Double (1.0, 1e23, Double.MAX_VALUE) is written as an integer, and up to 4.117.0 it read back as a Long
 * or BigInteger: the writer left $type off every Double, as it reads back as itself in JSON. -0.0 lost its sign. Now
 * such a value carries its type, and reads back as the same Double or Float, sign of zero included. With type info off
 * TOON carries no types, as its spec has it, and that is unchanged.
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
class ToonTypedNumbersTest {
    private static final double[] WHOLE = {1.0, 100.0, 1e23, Double.MAX_VALUE, -Double.MAX_VALUE, 0x1p63, -0.0, 0.0};

    public static class Holder {
        public Object any;
        public Number number;
        public double primitive;
        public Double boxed;
    }

    public static class Row {
        public String name;
        public Object amount;
    }

    /** Element and key types declared, so the writer may use its table and keyed forms, and a key declared Number. */
    public static class Tables {
        public List<Map<String, Object>> rows = new ArrayList<>();
        public Map<String, Map<String, Object>> keyed = new LinkedHashMap<>();
        public Map<Number, String> byNumber = new LinkedHashMap<>();
        public List<Optional<Object>> optionals = new ArrayList<>();
    }

    private static List<WriteOptions> typeInfoOn() {
        return Arrays.asList(new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build());
    }

    @Test
    void aWholeNumberDoubleInAnArrayReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            Object[] values = new Object[WHOLE.length + 2];
            for (int i = 0; i < WHOLE.length; i++) {
                values[i] = WHOLE[i];
            }
            values[WHOLE.length] = 1.5;
            values[WHOLE.length + 1] = "text";
            String toon = JsonIo.toToon(values, options);
            Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
            for (int i = 0; i < WHOLE.length; i++) {
                assertSameDouble(WHOLE[i], back[i], toon);
            }
            assertSameDouble(1.5, back[WHOLE.length], toon);
        }
    }

    @Test
    void aWholeNumberDoubleAtTheRootReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            for (double d : WHOLE) {
                String toon = JsonIo.toToon(d, options);
                assertSameDouble(d, JsonIo.fromToon(toon, null).asClass(Object.class), toon);
            }
        }
    }

    @Test
    void aWholeNumberFloatReadsBackAsTheSameFloat() {
        for (WriteOptions options : typeInfoOn()) {
            Object[] values = {3.0f, -0.0f, 1.5e10f, 2.5f};
            String toon = JsonIo.toToon(values, options);
            Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
            for (int i = 0; i < 3; i++) {
                assertInstanceOf(Float.class, back[i], toon);
                assertEquals(Float.floatToRawIntBits((Float) values[i]), Float.floatToRawIntBits((Float) back[i]), toon);
            }
            assertEquals(2.5, ((Number) back[3]).doubleValue(), toon);   // MinimalPlus reads 2.5f back as a double, as JSON does
        }
    }

    @Test
    void aWholeNumberDoubleInAListAMapAndFieldsReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            List<Object> list = new ArrayList<>(Arrays.asList(1.0, 1e23, 2.0));
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", 1.0);
            map.put("b", 1e23);
            map.put("c", -0.0);
            Holder holder = new Holder();
            holder.any = 42.0;
            holder.number = 1e23;
            holder.primitive = 7.0;
            holder.boxed = 8.0;
            Object[] root = {list, map, holder};

            String toon = JsonIo.toToon(root, options);
            Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
            List<?> listBack = (List<?>) back[0];
            assertSameDouble(1.0, listBack.get(0), toon);
            assertSameDouble(1e23, listBack.get(1), toon);
            assertSameDouble(2.0, listBack.get(2), toon);
            Map<?, ?> mapBack = (Map<?, ?>) back[1];
            assertSameDouble(1.0, mapBack.get("a"), toon);
            assertSameDouble(1e23, mapBack.get("b"), toon);
            assertSameDouble(-0.0, mapBack.get("c"), toon);
            Holder holderBack = (Holder) back[2];
            assertSameDouble(42.0, holderBack.any, toon);
            assertSameDouble(1e23, holderBack.number, toon);
            assertEquals(7.0, holderBack.primitive, toon);
            assertSameDouble(8.0, holderBack.boxed, toon);
        }
    }

    @Test
    void aWholeNumberDoubleInATableRowReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            List<Map<String, Object>> maps = new ArrayList<>();
            List<Row> rows = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", "n" + i);
                m.put("amount", (double) i);
                maps.add(m);
                Row r = new Row();
                r.name = "r" + i;
                r.amount = i * 10.0;
                rows.add(r);
            }
            String toon = JsonIo.toToon(new Object[] {maps, rows}, options);
            Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
            List<?> mapsBack = (List<?>) back[0];
            List<?> rowsBack = (List<?>) back[1];
            for (int i = 0; i < 3; i++) {
                assertSameDouble(i, ((Map<?, ?>) mapsBack.get(i)).get("amount"), toon);
                assertSameDouble(i * 10.0, ((Row) rowsBack.get(i)).amount, toon);
            }
        }
    }

    /**
     * Where the element or key type is declared the writer can use a table (rows of maps), a keyed table (a map of
     * maps), plain keys, and in-line Optionals; a whole-number Double there would be written bare.
     */
    @Test
    void aWholeNumberDoubleInDeclaredTablesKeysAndOptionalsReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            Tables tables = new Tables();
            for (int i = 0; i < 3; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", "n" + i);
                row.put("amount", (double) i);
                tables.rows.add(row);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("qty", i * 2.0);
                entry.put("note", "x");
                tables.keyed.put("k" + i, entry);
            }
            tables.byNumber.put(1.0, "one");
            tables.byNumber.put(1e23, "big");
            tables.optionals.add(Optional.of(1.0));
            tables.optionals.add(Optional.of(1e23));

            String toon = JsonIo.toToon(tables, options);
            Tables back = JsonIo.fromToon(toon, null).asClass(Tables.class);
            for (int i = 0; i < 3; i++) {
                assertSameDouble(i, back.rows.get(i).get("amount"), toon);
                assertSameDouble(i * 2.0, back.keyed.get("k" + i).get("qty"), toon);
            }
            assertEquals("one", back.byNumber.get(1.0), toon);
            assertEquals("big", back.byNumber.get(1e23), toon);
            assertSameDouble(1.0, back.optionals.get(0).get(), toon);
            assertSameDouble(1e23, back.optionals.get(1).get(), toon);
        }
    }

    @Test
    void aWholeNumberDoubleAsAKeyDeclaredNumberReadsBackAsTheSameDouble() {
        for (WriteOptions options : typeInfoOn()) {
            Map<Object, String> map = new LinkedHashMap<>();
            map.put(1.0, "one");
            map.put(1e23, "big");
            String toon = JsonIo.toToon(map, options);
            Map<?, ?> back = JsonIo.fromToon(toon, null).asClass(Map.class);
            assertEquals("one", back.get(1.0), toon);
            assertEquals("big", back.get(1e23), toon);
        }
    }

    @Test
    void anOptionalWholeNumberDoubleReadsBackAsTheSameDouble() {
        WriteOptions omitRoot = new WriteOptionsBuilder().showTypeInfoMinimal().omitRootTypeInfo().build();
        String toon = JsonIo.toToon(Optional.of(1.0), omitRoot);
        Optional<?> back = JsonIo.fromToon(toon, null).asClass(Optional.class);
        assertSameDouble(1.0, back.get(), toon);
        for (WriteOptions options : typeInfoOn()) {
            toon = JsonIo.toToon(new Object[] {Optional.of(1e23)}, options);
            Object[] array = JsonIo.fromToon(toon, null).asClass(Object[].class);
            assertSameDouble(1e23, ((Optional<?>) array[0]).get(), toon);
        }
    }

    @Test
    void aDeclaredDoubleNeedsNoType() {
        for (WriteOptions options : typeInfoOn()) {
            Holder holder = new Holder();
            holder.primitive = 7.0;
            holder.boxed = 8.0;
            String toon = JsonIo.toToon(holder, options);
            assertFalse(toon.contains("Double"), toon);
            assertTrue(toon.contains("primitive: 7"), toon);
            assertTrue(toon.contains("boxed: 8"), toon);
        }
    }

    @Test
    void withTypeInfoOffTheNumberIsWhatTheTextSays() {
        String toon = JsonIo.toToon(new Object[] {1.0, -0.0, 1.5}, null);
        assertEquals("[3]: 1,0,1.5", toon);
        Object[] back = JsonIo.fromToon(toon, null).asClass(Object[].class);
        assertEquals(1L, back[0]);
        assertEquals(0L, back[1]);
        assertEquals(1.5, back[2]);
    }

    private static void assertSameDouble(double expected, Object actual, String toon) {
        assertInstanceOf(Double.class, actual, toon);
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits((Double) actual),
                expected + " read back as " + actual + " from\n" + toon);
    }
}
