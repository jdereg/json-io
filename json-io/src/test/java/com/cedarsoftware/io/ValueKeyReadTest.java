package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object read into a typed field reads as what it is when one of its keys is "value" (or "_v"). Since 4.103.0 any
 * such object, whose field type the Converter can build from a Map, was taken for json-io's wrapped form
 * {"@type":"byte[]","value":"..."} and handed to the Converter: a Map lost its class, and a map under "value" stayed a
 * JsonObject whose values were never read (a Date stayed a JsonObject, a List a JsonObjectArray); an exception with a
 * field named value lost its other fields; and an enum field TOON writes with type info always ($type, value) threw
 * "No enum constant ...value". 4.102.0 read the first two correctly.
 */
class ValueKeyReadTest {

    public static class Holder {
        public Map<String, Object> map;
        public Object any;
        public List<Map<String, Object>> rows;
        public Map<String, Map<String, Object>> nested;
        public TreeMap<String, Object> sorted;
    }

    public enum Plain { A, B }

    public enum Fancy {
        X { int f() { return 1; } },
        Y { int f() { return 2; } };

        abstract int f();
    }

    public static class Enums {
        public Plain plain;
        public Fancy fancy;
        public Object any;
        public List<Plain> list;
        public Plain[] array;
    }

    public static class CodedException extends RuntimeException {
        public int value;
        public String code;

        public CodedException() {
        }

        public CodedException(String message) {
            super(message);
        }
    }

    public static class Failure {
        public CodedException error;
    }

    private static Map<String, Object> withValueKey(String key) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("when", new Date(0));
        inner.put("list", new ArrayList<>(Arrays.asList(1, 2)));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, inner);
        map.put("x", 1L);
        return map;
    }

    private static void assertDeepEquals(Object expected, Object actual) {
        Map<String, Object> options = new HashMap<>();
        assertTrue(DeepEquals.deepEquals(expected, actual, options), () -> String.valueOf(options.get("diff")));
    }

    private static void assertReadWhole(Map<?, ?> read, String key) {
        Object inner = read.get(key);
        assertSame(LinkedHashMap.class, inner.getClass());
        assertSame(Date.class, ((Map<?, ?>) inner).get("when").getClass());
        assertSame(ArrayList.class, ((Map<?, ?>) inner).get("list").getClass());
        assertDeepEquals(withValueKey(key), read);
    }

    @Test
    void aMapUnderTheValueKeyReadsBack() {
        for (String key : new String[] {"value", "_v"}) {
            Holder holder = new Holder();
            holder.map = withValueKey(key);
            Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
            assertReadWhole(back.map, key);
        }
    }

    /** A field declared as a concrete Map class. */
    @Test
    void aTreeMapFieldWithAValueKeyReadsBack() {
        Holder holder = new Holder();
        holder.sorted = new TreeMap<>(withValueKey("value"));
        Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertSame(TreeMap.class, back.sorted.getClass());
        assertReadWhole(back.sorted, "value");
    }

    @Test
    void aMapWithAValueKeyKeepsItsClass() {
        for (String key : new String[] {"value", "_v"}) {
            Map<String, Object> tree = new TreeMap<>();
            tree.put(key, 1L);
            tree.put("c", 2L);
            Map<String, Object> hash = new HashMap<>();
            hash.put(key, 1.5);
            hash.put("b", "y");
            for (Map<String, Object> map : Arrays.asList(tree, hash)) {
                Holder holder = new Holder();
                holder.map = map;
                Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
                assertSame(map.getClass(), back.map.getClass(), key);
                assertEquals(map, back.map);
            }
        }
    }

    @Test
    void toonReadsThemBackToo() {
        WriteOptions typed = new WriteOptionsBuilder().showTypeInfoMinimal().build();
        Holder holder = new Holder();
        holder.map = withValueKey("value");
        Holder back = JsonIo.fromToon(JsonIo.toToon(holder, typed), null).asClass(Holder.class);
        assertReadWhole(back.map, "value");

        Map<String, Object> tree = new TreeMap<>();
        tree.put("value", 1L);
        tree.put("c", 2L);
        holder.map = tree;
        back = JsonIo.fromToon(JsonIo.toToon(holder, typed), null).asClass(Holder.class);
        assertSame(TreeMap.class, back.map.getClass());
        assertEquals(tree, back.map);
    }

    /** Maps with a "value" key as the elements of a List field and the values of a Map field. */
    @Test
    void mapsInsideAListOrMapFieldReadBack() {
        Holder holder = new Holder();
        holder.rows = new ArrayList<>(Arrays.asList(withValueKey("value"), withValueKey("value")));
        holder.nested = new LinkedHashMap<>();
        holder.nested.put("k", withValueKey("value"));
        Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertReadWhole(back.rows.get(0), "value");
        assertReadWhole(back.rows.get(1), "value");
        assertReadWhole(back.nested.get("k"), "value");
    }

    /** The same map held by a Map field and an Object field is one object after the read. */
    @Test
    void aSharedMapWithAValueKeyStaysOneObject() {
        Holder holder = new Holder();
        holder.map = withValueKey("value");
        holder.any = holder.map;
        Holder back = JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class);
        assertReadWhole(back.map, "value");
        assertSame(back.map, back.any);
    }

    @Test
    void anEnumFieldReadsFromToonWithTypeInfoAlways() {
        Enums enums = new Enums();
        enums.plain = Plain.B;
        enums.fancy = Fancy.Y;
        enums.any = Plain.A;
        enums.list = new ArrayList<>(Arrays.asList(Plain.B, Plain.A));
        enums.array = new Plain[] {Plain.A, Plain.B};
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(), new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            Enums back = JsonIo.fromToon(JsonIo.toToon(enums, options), null).asClass(Enums.class);
            assertSame(Plain.B, back.plain);
            assertSame(Fancy.Y, back.fancy);
            if (options != null) {
                assertSame(Plain.A, back.any);
            }
            assertEquals(enums.list, back.list);
            assertArrayEquals(enums.array, back.array);
        }
        Enums back = JsonIo.toJava("{\"plain\":{\"value\":\"B\"},\"fancy\":{\"@type\":\"" + Fancy.class.getName()
                + "\",\"value\":\"Y\"}}", null).asClass(Enums.class);
        assertSame(Plain.B, back.plain);
        assertSame(Fancy.Y, back.fancy);
    }

    @Test
    void anExceptionWithAFieldNamedValueKeepsItsFields() {
        CodedException error = new CodedException("boom");
        error.value = 7;
        error.code = "C";
        Failure failure = new Failure();
        failure.error = error;
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            for (boolean toon : new boolean[] {false, true}) {
                Failure back = toon ? JsonIo.fromToon(JsonIo.toToon(failure, options), null).asClass(Failure.class)
                        : JsonIo.toJava(JsonIo.toJson(failure, options), null).asClass(Failure.class);
                assertSame(CodedException.class, back.error.getClass());
                assertEquals("boom", back.error.getMessage());
                assertEquals(7, back.error.value);
                assertEquals("C", back.error.code);
            }
        }
    }
}
