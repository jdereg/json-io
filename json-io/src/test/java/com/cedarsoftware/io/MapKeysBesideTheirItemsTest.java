package com.cedarsoftware.io;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A map whose keys are not strings is written as two lists, {@code @keys} and {@code @items}, and the n-th key
 * belongs to the n-th item. Both lists come from one pass over the map's entries (4.115.0).
 * <p>
 * They used to come from two passes, {@code keySet()} and then {@code values()}. A map whose two views do not run in
 * the same order was written with each key beside another key's value, and a map that changed between the passes
 * with more keys than items or the reverse. Nothing said so; the document read back as a map with the wrong pairs.
 * TOON has written its {@code $keys} and {@code $items} from one pass since 4.114.0.
 */
class MapKeysBesideTheirItemsTest {

    private static final WriteOptions PLAIN = new WriteOptionsBuilder().showTypeInfoNever().build();

    /** values() runs in the reverse of keySet() and entrySet(). The Map contract allows it. */
    public static class ReversedValuesMap<K, V> extends AbstractMap<K, V> {
        private final LinkedHashMap<K, V> inner = new LinkedHashMap<>();

        @Override
        public V put(K key, V value) {
            return inner.put(key, value);
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return inner.entrySet();
        }

        @Override
        public Set<K> keySet() {
            return inner.keySet();
        }

        @Override
        public Collection<V> values() {
            List<V> reversed = new ArrayList<>(inner.values());
            Collections.reverse(reversed);
            return reversed;
        }
    }

    /** A key that takes another entry out of its map while it is written, as another thread might. */
    public static class Key {
        private final int id;
        private transient Map<Key, String> owner;
        private transient Key victim;

        Key(int id) {
            this.id = id;
        }

        public int getId() {
            if (victim != null) {
                owner.remove(victim);
            }
            return id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key && ((Key) other).id == id;
        }
    }

    @Test
    void aMapWhoseViewsRunInDifferentOrdersIsWrittenWithItsOwnPairs() {
        ReversedValuesMap<Long, String> map = new ReversedValuesMap<>();
        map.put(1L, "one");
        map.put(2L, "two");
        map.put(3L, "three");

        String json = JsonIo.toJson(map, PLAIN);
        assertEquals("{\"@keys\":[1,2,3],\"@items\":[\"one\",\"two\",\"three\"]}", json);   // was three,two,one

        Map<Long, String> expected = new LinkedHashMap<>();
        expected.put(1L, "one");
        expected.put(2L, "two");
        expected.put(3L, "three");
        assertEquals(expected, JsonIo.toJava(json, null).asClass(Map.class));

        // Keys that are objects, and the map inside another document
        ReversedValuesMap<List<Integer>, String> byList = new ReversedValuesMap<>();
        byList.put(Arrays.asList(1, 1), "one");
        byList.put(Arrays.asList(2, 2), "two");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("m", byList);
        assertEquals("{\"m\":{\"@keys\":[[1,1],[2,2]],\"@items\":[\"one\",\"two\"]}}", JsonIo.toJson(outer, PLAIN));
    }

    @Test
    void aMapThatLosesAnEntryWhileItIsWrittenKeepsAKeyForEveryItem() {
        // The last key written removes the first entry. Two passes then found three keys and two values.
        ConcurrentHashMap<Key, String> map = new ConcurrentHashMap<>();
        Key one = new Key(1);
        Key two = new Key(2);
        Key three = new Key(3);
        map.put(one, "one");
        map.put(two, "two");
        map.put(three, "three");
        three.owner = map;
        three.victim = one;

        WriteOptions getters = new WriteOptionsBuilder().showTypeInfoNever().useGetters(true).build();
        String json = JsonIo.toJson(map, getters);

        assertEquals("{\"@keys\":[{\"id\":1},{\"id\":2},{\"id\":3}],\"@items\":[\"one\",\"two\",\"three\"]}", json);
        assertEquals(2, map.size());
    }

    @Test
    void aMapThatGainsEntriesWhileItIsWrittenKeepsAnItemForEveryKey() {
        // The first key written puts six more entries into the map; the iterator of a ConcurrentHashMap shows
        // entries added behind its back. Whatever it shows, each key written has its own value beside it.
        ConcurrentHashMap<Key, String> map = new ConcurrentHashMap<>();
        Key one = new GrowingKey(1, map);
        map.put(one, "v1");
        map.put(new Key(2), "v2");
        map.put(new Key(3), "v3");

        WriteOptions getters = new WriteOptionsBuilder().showTypeInfoNever().useGetters(true).build();
        String json = JsonIo.toJson(map, getters);
        assertEquals(9, map.size());

        Map<?, ?> read = JsonIo.toMaps(json, null).asClass(Map.class);
        assertEquals(9, read.size(), json);     // more entries than the array made for three: it grew
        for (Map.Entry<?, ?> entry : read.entrySet()) {
            assertEquals("v" + ((Map<?, ?>) entry.getKey()).get("id"), entry.getValue(), json);
        }
    }

    /** A key that puts six more entries into its map while it is written. */
    public static class GrowingKey extends Key {
        private transient ConcurrentHashMap<Key, String> map;

        GrowingKey(int id, ConcurrentHashMap<Key, String> map) {
            super(id);
            this.map = map;
        }

        @Override
        public int getId() {
            for (int n = 4; n <= 9; n++) {
                map.put(new Key(n), "v" + n);
            }
            return super.getId();
        }
    }

    /** A map whose size() is out of date, as a concurrent map's can be: it says {@code size} whatever it holds. */
    public static class StaleSizeMap extends AbstractMap<Object, Object> {
        private final LinkedHashMap<Object, Object> inner = new LinkedHashMap<>();
        private final int size;

        StaleSizeMap(int size) {
            this.size = size;
        }

        @Override
        public Object put(Object key, Object value) {
            return inner.put(key, value);
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return inner.entrySet();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    @Test
    void aMapWhoseSizeIsOutOfDateIsWrittenWhole() {
        for (int staleSize : new int[]{0, 1, 2, 5, 40}) {
            StaleSizeMap map = new StaleSizeMap(staleSize);
            for (long n = 1; n <= 5; n++) {
                map.put(n, "v" + n);
            }
            assertEquals("{\"@keys\":[1,2,3,4,5],\"@items\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v5\"]}",
                    JsonIo.toJson(map, PLAIN), "size() says " + staleSize);
        }
        // It said it was not empty, and has nothing by the time it is written (this was a NoSuchElementException)
        WriteOptions twoLists = new WriteOptionsBuilder().showTypeInfoNever().forceMapOutputAsTwoArrays(true).build();
        assertEquals("{\"@keys\":[],\"@items\":[]}", JsonIo.toJson(new StaleSizeMap(3), twoLists));
    }

    @Test
    void theTwoListsArePrettyPrintedAsBefore() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1L, "one");
        map.put(2L, "two");
        map.put(3L, "three");
        WriteOptions pretty = new WriteOptionsBuilder().showTypeInfoNever().prettyPrint(true).build();
        assertEquals("{\n  \"@keys\": [\n    1,\n    2,\n    3\n  ],\n  \"@items\": [\n    \"one\",\n    \"two\",\n"
                + "    \"three\"\n  ]\n}", JsonIo.toJson(map, pretty));
    }

    @Test
    void ordinaryMapsAreWrittenAsBefore() {
        // What the two passes wrote for a map whose views agree, built from the two views: the oracle
        Random random = new Random(4115L);
        int written = 0;
        for (int round = 0; round < 300; round++) {
            Map<Object, Object> map = emptyMap(round % 8);
            int size = 1 + random.nextInt(12);
            for (int n = 0; n < size; n++) {
                map.put(key(round % 8, random.nextInt(10_000)), n % 3 == 0 ? "v" + n : (Object) (long) n);
            }
            if (!allKeysAreStrings(map)) {
                String expected = "{\"@keys\":" + JsonIo.toJson(new ArrayList<>(map.keySet()), PLAIN)
                        + ",\"@items\":" + JsonIo.toJson(new ArrayList<>(map.values()), PLAIN) + "}";
                assertEquals(expected, JsonIo.toJson(map, PLAIN), map.getClass().getSimpleName());
                written++;
            }
        }
        assertEquals(300, written);
    }

    @Test
    void anEmptyMapAndAMapOfOneAreWrittenAsBefore() {
        WriteOptions twoLists = new WriteOptionsBuilder().showTypeInfoNever().forceMapOutputAsTwoArrays(true).build();
        assertEquals("{}", JsonIo.toJson(new HashMap<>(), twoLists));
        Map<Object, Object> single = new HashMap<>();
        single.put(7L, null);
        assertEquals("{\"@keys\":[7],\"@items\":[null]}", JsonIo.toJson(single, twoLists));
        Map<Object, Object> nullKey = new HashMap<>();
        nullKey.put(null, "x");
        assertEquals("{\"@keys\":[null],\"@items\":[\"x\"]}", JsonIo.toJson(nullKey, PLAIN));
    }

    private static boolean allKeysAreStrings(Map<Object, Object> map) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static Map<Object, Object> emptyMap(int shape) {
        switch (shape) {
            case 0:
                return new HashMap<>();
            case 1:
                return new LinkedHashMap<>();
            case 2:
                return new TreeMap<>();
            case 3:
                return new ConcurrentHashMap<>();
            case 4:
                return new ConcurrentSkipListMap<>();
            case 5:
                return new Hashtable<>();
            case 6:
                return new IdentityHashMap<>();     // hands out one entry object for every entry
            default:
                return new PlainAbstractMap();
        }
    }

    /** An AbstractMap that leaves keySet() and values() to AbstractMap: they are views of entrySet(). */
    public static class PlainAbstractMap extends AbstractMap<Object, Object> {
        private final LinkedHashMap<Object, Object> inner = new LinkedHashMap<>();

        @Override
        public Object put(Object key, Object value) {
            return inner.put(key, value);
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return inner.entrySet();
        }
    }

    private static Object key(int shape, int n) {
        switch (shape) {
            case 2:
            case 4:
                return (long) n;                    // the sorted maps need keys that compare
            case 6:
                return (long) n + 1_000;            // not a cached Long: each key is its own object
            default:
                break;
        }
        switch (n % 4) {
            case 0:
                return (long) n;
            case 1:
                return new UUID(n, n);
            case 2:
                return LocalDate.of(2000 + n % 30, 1 + n % 12, 1 + n % 28);
            default:
                return Arrays.asList(n, n + 1);
        }
    }
}
