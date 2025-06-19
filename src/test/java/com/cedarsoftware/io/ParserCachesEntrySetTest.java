package com.cedarsoftware.io;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserCachesEntrySetTest {

    @Test
    void testParserStringCacheEntrySet() throws Exception {
        Map<String, String> staticCache = new HashMap<>();
        staticCache.put("a", "A");
        staticCache.put("b", "B");

        Class<?> cls = Class.forName("com.cedarsoftware.io.JsonParser$ParserStringCache");
        Constructor<?> ctor = cls.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) ctor.newInstance(staticCache);

        cache.put("c", "C");
        Set<Map.Entry<String, String>> entries = cache.entrySet();

        assertEquals(3, entries.size());
        assertTrue(entries.stream().anyMatch(e -> "a".equals(e.getKey()) && "A".equals(e.getValue())));
        assertTrue(entries.stream().anyMatch(e -> "b".equals(e.getKey()) && "B".equals(e.getValue())));
        assertTrue(entries.stream().anyMatch(e -> "c".equals(e.getKey()) && "C".equals(e.getValue())));
    }

    @Test
    void testParserNumberCacheEntrySet() throws Exception {
        Map<Number, Number> staticCache = new HashMap<>();
        staticCache.put(1, 1);
        staticCache.put(2, 2);

        Class<?> cls = Class.forName("com.cedarsoftware.io.JsonParser$ParserNumberCache");
        Constructor<?> ctor = cls.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Number, Number> cache = (Map<Number, Number>) ctor.newInstance(staticCache);

        cache.put(3, 3);
        Set<Map.Entry<Number, Number>> entries = cache.entrySet();

        assertEquals(3, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.getKey().equals(1) && e.getValue().equals(1)));
        assertTrue(entries.stream().anyMatch(e -> e.getKey().equals(2) && e.getValue().equals(2)));
        assertTrue(entries.stream().anyMatch(e -> e.getKey().equals(3) && e.getValue().equals(3)));
    }
}
