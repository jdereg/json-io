package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cedarsoftware.util.LRUCache;
import com.cedarsoftware.util.TTLCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * java-util's caches, populated, written and read back -- in JSON and TOON, at the root, in a field, and in a slot
 * whose type is not declared -- and still working as caches. No json-io release could read either one back: every
 * one since 4.56.0 threw ("Capacity must be at least 1", "TTL must be at least 1 millisecond"), and plain JSON read
 * as one took its first value for the capacity or the TTL.
 * <ul>
 * <li>An {@link LRUCache} is written as the map of its entries, as before, and reads back with them, in a cache
 * with room for each (at least 16): its capacity is not written.</li>
 * <li>A {@link TTLCache} is written as its settings and no entries, as Guava and Caffeine serialize their caches,
 * and reads back empty, configured as it was: a reader cannot know how old an entry is. One written as its entries
 * -- before 4.113.0, by TOON without type info, or plain JSON -- keeps them, without expiry.</li>
 * </ul>
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
class CacheRoundTripTest {

    private static final WriteOptions TOON_TYPED = new WriteOptionsBuilder().showTypeInfoMinimal().build();

    public static class Holder {
        public LRUCache<String, Integer> lru;
        public TTLCache<String, String> ttl;
        public Map<String, Object> any;
    }

    private static LRUCache<String, Integer> lru() {
        LRUCache<String, Integer> lru = new LRUCache<>(10);
        lru.put("a", 1);
        lru.put("b", 2);
        lru.put("c", 3);
        return lru;
    }

    private static TTLCache<String, String> ttl() {
        TTLCache<String, String> ttl = new TTLCache<>(5000, 10, 250);
        ttl.put("a", "1");
        ttl.put("b", "2");
        return ttl;
    }

    /** Back as a working LRU cache holding what was written, with room for every entry (at least 16). */
    private static void assertWorkingLru(Map<?, ?> expected, Object back) {
        LRUCache<Object, Object> cache = assertCache(LRUCache.class, back);
        assertEquals(expected, new LinkedHashMap<>(cache));
        assertEquals(Math.max(expected.size(), 16), cache.getCapacity());
        for (int i = 0; i < 40; i++) {
            cache.put("more" + i, i);
        }
        assertEquals(cache.getCapacity(), cache.size());     // full, and evicting
        assertEquals(39, cache.get("more39"));
    }

    /** Back as an empty TTL cache built as the one written, and working. */
    private static void assertConfiguredTtl(Object back) {
        TTLCache<Object, Object> cache = assertCache(TTLCache.class, back);
        try {
            assertTrue(cache.isEmpty(), "entries are not written: " + cache);
            assertEquals(5000, cache.getTtlMillis());
            assertEquals(10, cache.getMaxSize());
            assertEquals(250, cache.getCleanupIntervalMillis());
            cache.put("x", "y");
            assertEquals("y", cache.get("x"));
        } finally {
            cache.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T assertCache(Class<?> type, Object back) {
        assertInstanceOf(type, back);
        return (T) back;
    }

    @Test
    void anLruCacheReadsBackWithItsEntries() {
        LRUCache<String, Integer> lru = lru();
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        expected.put("c", 3);

        assertWorkingLru(expected, JsonIo.toJava(JsonIo.toJson(lru, null), null).asClass(null));
        assertWorkingLru(expected, JsonIo.fromToon(JsonIo.toToon(lru, TOON_TYPED), null).asClass(null));
        // without type info, the type asked for (numbers then read as the JSON's Longs)
        Map<String, Object> asLongs = new LinkedHashMap<>();
        asLongs.put("a", 1L);
        asLongs.put("b", 2L);
        asLongs.put("c", 3L);
        assertWorkingLru(asLongs, JsonIo.fromToon(JsonIo.toToon(lru, null), null).asClass(LRUCache.class));
        assertWorkingLru(asLongs, JsonIo.toJava("{\"a\":1,\"b\":2,\"c\":3}", null).asClass(LRUCache.class));
    }

    @Test
    void anLruCacheHasRoomForEveryEntryItHeld() {
        LRUCache<String, Integer> big = new LRUCache<>(100);
        for (int i = 0; i < 40; i++) {
            big.put("k" + i, i);
        }
        LRUCache<?, ?> back = JsonIo.toJava(JsonIo.toJson(big, null), null).asClass(null);
        assertEquals(40, back.size());
        assertEquals(40, back.getCapacity());
    }

    @Test
    void aTtlCacheIsWrittenAsItsSettingsAndReadsBackEmptyConfiguredAsItWas() {
        TTLCache<String, String> ttl = ttl();
        try {
            String json = JsonIo.toJson(ttl, null);
            assertTrue(json.contains("\"config\":\"5000/10/250\""), json);
            assertFalse(json.contains("\"a\""), json);
            assertConfiguredTtl(JsonIo.toJava(json, null).asClass(null));

            String toon = JsonIo.toToon(ttl, TOON_TYPED);
            assertFalse(toon.contains("a:"), toon);
            assertConfiguredTtl(JsonIo.fromToon(toon, null).asClass(null));
        } finally {
            ttl.close();
        }
    }

    /** As json-io before 4.113.0 wrote one, as TOON without type info writes one, or plain JSON: its entries. */
    @Test
    void aTtlCacheWrittenAsItsEntriesKeepsThemWithoutExpiry() {
        String before = "{\"@type\":\"com.cedarsoftware.util.TTLCache\",\"a\":{\"@type\":\"Integer\",\"value\":1},"
                + "\"b\":{\"@type\":\"Integer\",\"value\":2}}";
        TTLCache<?, ?> old = JsonIo.toJava(before, null).asClass(null);
        try {
            assertEquals(1, old.get("a"));
            assertEquals(2, old.size());
            assertEquals(Long.MAX_VALUE / 2, old.getTtlMillis());      // no expiry
            assertEquals(16, old.getMaxSize());
        } finally {
            old.close();
        }

        TTLCache<String, String> ttl = ttl();
        try {
            TTLCache<?, ?> fromToon = JsonIo.fromToon(JsonIo.toToon(ttl, null), null).asClass(TTLCache.class);
            assertEquals("2", fromToon.get("b"));
            assertEquals(2, fromToon.size());
            fromToon.close();
        } finally {
            ttl.close();
        }

        // The first value was taken for the TTL in milliseconds: "a" lost, the rest expired within 1 ms
        TTLCache<?, ?> plain = JsonIo.toJava("{\"a\":1,\"b\":2,\"c\":3}", null).asClass(TTLCache.class);
        assertEquals(3, plain.size());
        assertEquals(1L, plain.get("a"));
        plain.close();
    }

    @Test
    void cachesInFieldsRoundTrip() {
        Holder holder = new Holder();
        holder.lru = lru();
        holder.ttl = ttl();
        holder.any = new LinkedHashMap<>();
        holder.any.put("cache", lru());
        Map<String, Object> expected = new LinkedHashMap<>(lru());
        try {
            for (Holder back : new Holder[] {
                    JsonIo.toJava(JsonIo.toJson(holder, null), null).asClass(Holder.class),
                    JsonIo.fromToon(JsonIo.toToon(holder, TOON_TYPED), null).asClass(Holder.class)}) {
                assertWorkingLru(expected, back.lru);
                assertConfiguredTtl(back.ttl);
                assertWorkingLru(expected, back.any.get("cache"));
            }
            // no type info: the declared types say what to build
            Holder never = JsonIo.toJava(JsonIo.toJson(holder, new WriteOptionsBuilder().showTypeInfoNever().build()),
                    null).asClass(Holder.class);
            assertEquals(3, never.lru.size());
            assertConfiguredTtl(never.ttl);
        } finally {
            holder.ttl.close();
        }
    }
}
