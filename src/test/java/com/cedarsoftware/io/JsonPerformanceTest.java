package com.cedarsoftware.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonPerformanceTest {
    private static final Logger LOG = Logger.getLogger(JsonPerformanceTest.class.getName());
    private static final int WARMUP_ITERATIONS = 10000;
    private static final int TEST_ITERATIONS = 100000;

    // A sample POJO with a variety of fields to simulate complex JSON
    public static class TestData {
        public int id;
        public String name;
        public int[] values;
        public NestedData nested;
        public List<String> list;
        public Map<String, String> map;
        public List<NestedData> nestedList;  // More complex nesting
        public Map<String, NestedData> nestedMap;  // Map with object values

        // Additional integer-heavy fields to test readNumber fast-path
        public int age;
        public int count;
        public int score;
        public long timestamp;
        public List<Integer> integerList;  // List of integers
        public int[] largeIntArray;  // Larger array of integers
        public Map<String, Integer> counterMap;  // Map with integer values
    }

    public static class NestedData {
        public double metric;
        public boolean active;
        public String description;
        public String[] tags;  // Additional array field
        public Map<String, Object> metadata;  // Additional map field

        // Additional integer fields
        public int itemCount;
        public int priority;
        public long createdAt;
    }

    public static void main(String[] args) throws IOException {
        String mode = args.length > 0 ? args[0].toLowerCase() : "both";

        switch (mode) {
            case "java":
                testFullJavaResolution();
                break;
            case "maps":
                testMapsOnly();
                break;
            case "both":
            default:
                testFullJavaResolution();
                LOG.info("");
                LOG.info("========================================");
                LOG.info("");
                testMapsOnly();
                break;
        }
    }

    /**
     * Test full Java object resolution (parsing + resolution to POJOs)
     */
    public static void testFullJavaResolution() throws IOException {
        LOG.info("=== TEST: Full Java Resolution (toJava) ===");

        TestData testData = createTestData();
        ObjectMapper jacksonMapper = new ObjectMapper();
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        // Warm-up
        LOG.info("Starting warmup with " + WARMUP_ITERATIONS + " iterations...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String json = JsonIo.toJson(testData, writeOptions);
            TestData obj = JsonIo.toJava(json, readOptions).asClass(TestData.class);
            String jJson = jacksonMapper.writeValueAsString(testData);
            TestData jObj = jacksonMapper.readValue(jJson, TestData.class);
        }
        LOG.info("Warmup complete.");

        // Test Write
        LOG.info("Testing JsonIo Write with " + TEST_ITERATIONS + " iterations...");
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;
        LOG.info("JsonIo Write complete.");

        LOG.info("Testing Jackson Write with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = jacksonMapper.writeValueAsString(testData);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jacksonWriteTime = System.nanoTime() - start;
        LOG.info("Jackson Write complete.");

        // Prepare JSON strings for reading tests
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);

        // Test Read (full Java resolution)
        LOG.info("Testing JsonIo Read (toJava) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        TestData result = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            result = JsonIo.toJava(jsonIoJson, readOptions).asClass(TestData.class);
        }
        long jsonIoReadTime = System.nanoTime() - start;
        LOG.info("JsonIo Read complete.");

        LOG.info("Testing Jackson Read with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            result = jacksonMapper.readValue(jacksonJson, TestData.class);
        }
        long jacksonReadTime = System.nanoTime() - start;
        LOG.info("Jackson Read complete.");

        // Output results
        LOG.info("--- Full Java Resolution Results ---");
        LOG.info("Iterations: " + TEST_ITERATIONS);
        LOG.info("JsonIo Write Time: " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        LOG.info("Write Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
    }

    /**
     * Test Maps-only parsing (parsing to Map/List graph, no Java object resolution)
     */
    public static void testMapsOnly() throws IOException {
        LOG.info("=== TEST: Maps Only (toMaps) ===");

        TestData testData = createTestData();
        ObjectMapper jacksonMapper = new ObjectMapper();
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        // Warm-up
        LOG.info("Starting warmup with " + WARMUP_ITERATIONS + " iterations...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String json = JsonIo.toJson(testData, writeOptions);
            Map map = JsonIo.toMaps(json, readOptions).asClass(Map.class);
            String jJson = jacksonMapper.writeValueAsString(testData);
            Map<String, Object> jMap = jacksonMapper.readValue(jJson, Map.class);
        }
        LOG.info("Warmup complete.");

        // Test Write (same as full resolution)
        LOG.info("Testing JsonIo Write with " + TEST_ITERATIONS + " iterations...");
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;
        LOG.info("JsonIo Write complete.");

        LOG.info("Testing Jackson Write with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = jacksonMapper.writeValueAsString(testData);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jacksonWriteTime = System.nanoTime() - start;
        LOG.info("Jackson Write complete.");

        // Prepare JSON strings for reading tests
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);

        // Test Read (Maps only - no Java resolution)
        LOG.info("Testing JsonIo Read (toMaps) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        Map mapResult = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            mapResult = JsonIo.toMaps(jsonIoJson, readOptions).asClass(Map.class);
        }
        long jsonIoReadTime = System.nanoTime() - start;
        LOG.info("JsonIo Read complete.");

        LOG.info("Testing Jackson Read (to Map) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            mapResult = jacksonMapper.readValue(jacksonJson, Map.class);
        }
        long jacksonReadTime = System.nanoTime() - start;
        LOG.info("Jackson Read complete.");

        // Output results
        LOG.info("--- Maps Only Results ---");
        LOG.info("Iterations: " + TEST_ITERATIONS);
        LOG.info("JsonIo Write Time: " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        LOG.info("Write Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
    }

    private static TestData createTestData() {
        TestData data = new TestData();
        data.id = 1;
        data.name = "Test Object";
        data.values = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        // Populate new integer-heavy fields
        data.age = 25;
        data.count = 12345;
        data.score = 98765;
        data.timestamp = 1700000000000L;

        // Large integer array (50 integers)
        data.largeIntArray = new int[50];
        for (int i = 0; i < 50; i++) {
            data.largeIntArray[i] = i * 100;
        }

        // List of integers (100 integers)
        data.integerList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            data.integerList.add(i * 10);
        }

        // Map with integer values (30 entries)
        data.counterMap = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            data.counterMap.put("counter" + i, i * 50);
        }

        NestedData nested = new NestedData();
        nested.metric = 123.456;
        nested.active = true;
        nested.description = "A nested object used to stress test serialization performance.";
        nested.tags = new String[]{"tag1", "tag2", "tag3", "performance", "testing"};
        nested.metadata = new HashMap<>();
        nested.metadata.put("version", "1.0");
        nested.metadata.put("count", 42);
        nested.metadata.put("enabled", true);

        // Populate new integer fields in nested
        nested.itemCount = 250;
        nested.priority = 5;
        nested.createdAt = 1700000000L;
        data.nested = nested;

        data.list = new ArrayList<>();
        for (int i = 0; i < 50; i++) {  // Increased size
            data.list.add("List item " + i);
        }

        data.map = new HashMap<>();
        for (int i = 0; i < 50; i++) {  // Increased size
            data.map.put("key" + i, "value" + i);
        }

        // Populate nestedList with multiple NestedData objects
        data.nestedList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            NestedData nd = new NestedData();
            nd.metric = 100.0 + i;
            nd.active = i % 2 == 0;
            nd.description = "Nested description " + i;
            nd.tags = new String[]{"nested", "item" + i, "test"};
            nd.metadata = new HashMap<>();
            nd.metadata.put("index", i);
            nd.metadata.put("type", "nested");
            data.nestedList.add(nd);
        }

        // Populate nestedMap with NestedData objects
        data.nestedMap = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            NestedData nd = new NestedData();
            nd.metric = 200.0 + i;
            nd.active = i % 3 == 0;
            nd.description = "Map nested description " + i;
            nd.tags = new String[]{"map", "nested", "key" + i};
            nd.metadata = new HashMap<>();
            nd.metadata.put("mapKey", "key" + i);
            nd.metadata.put("priority", i);
            data.nestedMap.put("nested" + i, nd);
        }

        return data;
    }
}
