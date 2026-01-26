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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Debug test to investigate skipped conversions in ConverterEverythingTest
 */
public class SkippedConversionsDebugTest {

    @Test
    void testAtomicIntegerToMap() {
        AtomicInteger source = new AtomicInteger(42);
        System.out.println("=== AtomicInteger → Map ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        Object readBack = JsonIo.toJava(json, null).asClass(Object.class);
        System.out.println("Read back type: " + readBack.getClass().getName());
        System.out.println("Read back value: " + readBack);
    }

    @Test
    void testByteToYear() {
        Byte source = (byte) 24;
        System.out.println("\n=== Byte → Year ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Year readBack = JsonIo.toJava(json, null).asClass(Year.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
    }

    @Test
    void testMapToMap() {
        // Test with simple string values (matching ConverterEverythingTest)
        Map<String, String> source = new LinkedHashMap<>();
        source.put("message", "in a bottle");
        System.out.println("\n=== Map → Map (simple strings) ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        Object readBack = JsonIo.toJava(json, null).asClass(Map.class);
        System.out.println("Read back type: " + readBack.getClass().getName());
        System.out.println("Read back value: " + readBack);
        System.out.println("Equal? " + source.equals(readBack));

        // Test with mixed types
        Map<String, Object> source2 = new LinkedHashMap<>();
        source2.put("key1", "value1");
        source2.put("key2", 42);
        System.out.println("\n=== Map → Map (mixed types) ===");
        String json2 = JsonIo.toJson(source2, null);
        System.out.println("JSON: " + json2);
        Object readBack2 = JsonIo.toJava(json2, null).asClass(Map.class);
        System.out.println("Read back type: " + readBack2.getClass().getName());
        System.out.println("Read back value: " + readBack2);
        System.out.println("Equal? " + source2.equals(readBack2));
    }

    @Test
    void testMapToThrowable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("message", "test error");
        source.put("detailMessage", "test error");
        System.out.println("\n=== Map → Throwable ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Throwable readBack = JsonIo.toJava(json, null).asClass(Throwable.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack.getMessage());
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testBitSet() {
        BitSet source = new BitSet();
        source.set(1);
        source.set(5);
        source.set(10);
        System.out.println("\n=== BitSet (non-empty) ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Object readBack = JsonIo.toJava(json, null).asClass(BitSet.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
            System.out.println("Equal? " + source.equals(readBack));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        BitSet empty = new BitSet();
        System.out.println("\n=== BitSet (empty) ===");
        String jsonEmpty = JsonIo.toJson(empty, null);
        System.out.println("JSON: " + jsonEmpty);
        try {
            Object readBackEmpty = JsonIo.toJava(jsonEmpty, null).asClass(BitSet.class);
            System.out.println("Read back type: " + readBackEmpty.getClass().getName());
            System.out.println("Read back value: " + readBackEmpty);
            System.out.println("Equal? " + empty.equals(readBackEmpty));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testAtomicIntegerArray() {
        AtomicIntegerArray source = new AtomicIntegerArray(new int[]{1, 2, 3, 4, 5});
        System.out.println("\n=== AtomicIntegerArray ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Object readBack = JsonIo.toJava(json, null).asClass(AtomicIntegerArray.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testAtomicLongArray() {
        AtomicLongArray source = new AtomicLongArray(new long[]{1L, 2L, 3L});
        System.out.println("\n=== AtomicLongArray ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Object readBack = JsonIo.toJava(json, null).asClass(AtomicLongArray.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testStringBufferToCharSequence() {
        StringBuffer source = new StringBuffer("hello world");
        System.out.println("\n=== StringBuffer → CharSequence ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        Object readBack = JsonIo.toJava(json, null).asClass(CharSequence.class);
        System.out.println("Read back type: " + readBack.getClass().getName());
        System.out.println("Read back value: " + readBack);
    }

    @Test
    void testFile() {
        File source = new File("/tmp/test.txt");
        System.out.println("\n=== File ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            File readBack = JsonIo.toJava(json, null).asClass(File.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
            System.out.println("Equal? " + source.equals(readBack));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testPath() {
        Path source = Paths.get("/tmp/test.txt");
        System.out.println("\n=== Path ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            Path readBack = JsonIo.toJava(json, null).asClass(Path.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
            System.out.println("Equal? " + source.equals(readBack));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testURI() {
        URI source = URI.create("https://example.com/path?query=value");
        System.out.println("\n=== URI ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            URI readBack = JsonIo.toJava(json, null).asClass(URI.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
            System.out.println("Equal? " + source.equals(readBack));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Test
    void testURL() throws Exception {
        URL source = new URL("https://example.com/path?query=value");
        System.out.println("\n=== URL ===");
        String json = JsonIo.toJson(source, null);
        System.out.println("JSON: " + json);
        try {
            URL readBack = JsonIo.toJava(json, null).asClass(URL.class);
            System.out.println("Read back type: " + readBack.getClass().getName());
            System.out.println("Read back value: " + readBack);
            System.out.println("Equal? " + source.equals(readBack));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
