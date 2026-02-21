package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Year;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for round-trip serialization of types that were previously skipped
 * in ConverterEverythingTest.
 */
public class SkippedConversionsDebugTest {

    @Test
    void testAtomicIntegerToMap() {
        AtomicInteger source = new AtomicInteger(42);
        String json = JsonIo.toJson(source, null);
        Object readBack = JsonIo.toJava(json, null).asClass(Object.class);
        assertInstanceOf(AtomicInteger.class, readBack);
        assertEquals(42, ((AtomicInteger) readBack).get());
    }

    @Test
    void testByteToYear() {
        Byte source = (byte) 24;
        String json = JsonIo.toJson(source, null);
        assertThrows(Exception.class, () -> JsonIo.toJava(json, null).asClass(Year.class),
                "Byte cannot be read back as Year");
    }

    @Test
    void testMapToMap() {
        // Simple string values
        Map<String, String> source = new LinkedHashMap<>();
        source.put("message", "in a bottle");
        String json = JsonIo.toJson(source, null);
        Object readBack = JsonIo.toJava(json, null).asClass(Map.class);
        assertInstanceOf(Map.class, readBack);
        assertEquals(source, readBack);

        // Mixed types
        Map<String, Object> source2 = new LinkedHashMap<>();
        source2.put("key1", "value1");
        source2.put("key2", 42);
        String json2 = JsonIo.toJson(source2, null);
        Object readBack2 = JsonIo.toJava(json2, null).asClass(Map.class);
        assertInstanceOf(Map.class, readBack2);
        assertEquals(source2, readBack2);
    }

    @Test
    void testMapToThrowable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("message", "test error");
        source.put("detailMessage", "test error");
        String json = JsonIo.toJson(source, null);
        Throwable readBack = JsonIo.toJava(json, null).asClass(Throwable.class);
        assertNotNull(readBack);
        assertEquals("test error", readBack.getMessage());
    }

    @Test
    void testBitSet() {
        BitSet source = new BitSet();
        source.set(1);
        source.set(5);
        source.set(10);
        String json = JsonIo.toJson(source, null);
        BitSet readBack = JsonIo.toJava(json, null).asClass(BitSet.class);
        assertEquals(source, readBack);

        // Empty BitSet
        BitSet empty = new BitSet();
        String jsonEmpty = JsonIo.toJson(empty, null);
        BitSet readBackEmpty = JsonIo.toJava(jsonEmpty, null).asClass(BitSet.class);
        assertEquals(empty, readBackEmpty);
    }

    @Test
    void testAtomicIntegerArray() {
        AtomicIntegerArray source = new AtomicIntegerArray(new int[]{1, 2, 3, 4, 5});
        String json = JsonIo.toJson(source, null);
        AtomicIntegerArray readBack = JsonIo.toJava(json, null).asClass(AtomicIntegerArray.class);
        assertNotNull(readBack);
        assertEquals(source.length(), readBack.length());
        for (int i = 0; i < source.length(); i++) {
            assertEquals(source.get(i), readBack.get(i));
        }
    }

    @Test
    void testAtomicLongArray() {
        AtomicLongArray source = new AtomicLongArray(new long[]{1L, 2L, 3L});
        String json = JsonIo.toJson(source, null);
        AtomicLongArray readBack = JsonIo.toJava(json, null).asClass(AtomicLongArray.class);
        assertNotNull(readBack);
        assertEquals(source.length(), readBack.length());
        for (int i = 0; i < source.length(); i++) {
            assertEquals(source.get(i), readBack.get(i));
        }
    }

    @Test
    void testStringBufferToCharSequence() {
        StringBuffer source = new StringBuffer("hello world");
        String json = JsonIo.toJson(source, null);
        Object readBack = JsonIo.toJava(json, null).asClass(CharSequence.class);
        assertInstanceOf(StringBuffer.class, readBack);
        assertEquals("hello world", readBack.toString());
    }

    @Test
    void testFile() {
        File source = new File("/tmp/test.txt");
        String json = JsonIo.toJson(source, null);
        File readBack = JsonIo.toJava(json, null).asClass(File.class);
        assertEquals(source, readBack);
    }

    @Test
    void testPath() {
        Path source = Paths.get("/tmp/test.txt");
        String json = JsonIo.toJson(source, null);
        Path readBack = JsonIo.toJava(json, null).asClass(Path.class);
        assertEquals(source, readBack);
    }

    @Test
    void testURI() {
        URI source = URI.create("https://example.com/path?query=value");
        String json = JsonIo.toJson(source, null);
        URI readBack = JsonIo.toJava(json, null).asClass(URI.class);
        assertEquals(source, readBack);
    }

    @Test
    void testURL() throws Exception {
        URL source = new URL("https://example.com/path?query=value");
        String json = JsonIo.toJson(source, null);
        URL readBack = JsonIo.toJava(json, null).asClass(URL.class);
        assertEquals(source, readBack);
    }
}
