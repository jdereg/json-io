package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boundary coverage for the per-instance cache-compare scratch (4.104.0) that replaced
 * the CharBufScratch ThreadLocal in CharStreamTokenizer / ToonReader. The scratch is
 * sized to MAX_CACHED_STRING_LENGTH (64); strings at, below, and above that boundary —
 * including repeated occurrences that exercise the cache-hit compare path — must
 * round-trip identically.
 */
class TokenizerStringCacheScratchTest {

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    void cacheBoundaryStringsRoundTrip() {
        String len63 = repeat('a', 63);
        String len64 = repeat('b', 64);   // exactly MAX_CACHED_STRING_LENGTH
        String len65 = repeat('c', 65);   // bypasses the cache entirely

        Map<String, Object> map = new LinkedHashMap<>();
        // Repeat each value several times so the cache-verify (equalsRange against the
        // scratch) path actually fires on the later occurrences.
        for (int i = 0; i < 4; i++) {
            map.put("k63_" + i, len63);
            map.put("k64_" + i, len64);
            map.put("k65_" + i, len65);
        }

        String json = JsonIo.toJson(map, null);
        Map<String, Object> back = JsonIo.toJava(json, null)
                .asType(new TypeHolder<Map<String, Object>>() {});
        assertEquals(map, back);

        // TOON path exercises ToonReader.cacheSubstringFromBuf's scratch the same way.
        String toon = JsonIo.toToon(map, null);
        Map<String, Object> toonBack = JsonIo.fromToon(toon, null)
                .asType(new TypeHolder<Map<String, Object>>() {});
        assertEquals(map, toonBack);
    }

    @Test
    void nearMissCacheCollisionsCompareCorrectly() {
        // Same length + same first/middle/last chars (the cacheHash inputs) but
        // different interior content — forces slot collisions where only the
        // scratch-based equalsRange distinguishes the strings.
        String s1 = "aXXXXXXXXXXXXXXXmXXXXXXXXXXXXXXXz";
        String s2 = "aYYYYYYYYYYYYYYYmYYYYYYYYYYYYYYYz";

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("first", s1);
        map.put("second", s2);
        map.put("third", s1);
        map.put("fourth", s2);

        String json = JsonIo.toJson(map, null);
        Map<String, Object> back = JsonIo.toJava(json, null)
                .asType(new TypeHolder<Map<String, Object>>() {});
        assertEquals(map, back);
    }
}
