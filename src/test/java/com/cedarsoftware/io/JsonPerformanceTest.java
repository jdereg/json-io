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
    }

    public static class NestedData {
        public double metric;
        public boolean active;
        public String description;
        public String[] tags;  // Additional array field
        public Map<String, Object> metadata;  // Additional map field
    }

    public static void main(String[] args) throws IOException {
        // Create a sample object
        TestData testData = createTestData();

        // Set up Jackson ObjectMapper (reuse for every call)
        ObjectMapper jacksonMapper = new ObjectMapper();

        // Create JsonIo options (reuse these for all tests)
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        // --- Warm-Up Loop ---
        // Run a number of iterations to let the JVM "warm up" (JIT compilation, etc.)
        int warmupIterations = 10000;  // Sufficient for JIT optimization, faster test runs
        LOG.info("Starting warmup with " + warmupIterations + " iterations...");
        for (int i = 0; i < warmupIterations; i++) {
            // JsonIo warm-up (serialization then deserialization)
            String json = JsonIo.toJson(testData, writeOptions);
            TestData obj = JsonIo.toJava(json, readOptions).asClass(TestData.class);

            // Jackson warm-up (serialization then deserialization)
            String jJson = jacksonMapper.writeValueAsString(testData);
            TestData jObj = jacksonMapper.readValue(jJson, TestData.class);
        }
        LOG.info("Warmup complete.");

        // --- Performance Test ---
        int iterations = 100000;  // Reduced for faster test runs while maintaining accuracy

        // Test JsonIo serialization ("writing")
        LOG.info("Testing JsonIo Write with " + iterations + " iterations...");
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < iterations; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            // Use dummy to prevent dead-code elimination (but do minimal work)
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;
        LOG.info("JsonIo Write complete.");

        // Test Jackson serialization ("writing")
        LOG.info("Testing Jackson Write with " + iterations + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            dummy = jacksonMapper.writeValueAsString(testData);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jacksonWriteTime = System.nanoTime() - start;
        LOG.info("Jackson Write complete.");

        // Prepare JSON strings for reading tests (to remove serialization overhead)
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);

        // Test JsonIo deserialization ("reading")
        LOG.info("Testing JsonIo Read with " + iterations + " iterations...");
        start = System.nanoTime();
        TestData result = null;
        for (int i = 0; i < iterations; i++) {
            result = JsonIo.toJava(jsonIoJson, readOptions).asClass(TestData.class);
        }
        long jsonIoReadTime = System.nanoTime() - start;
        LOG.info("JsonIo Read complete.");

        // Test Jackson deserialization ("reading")
        LOG.info("Testing Jackson Read with " + iterations + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            result = jacksonMapper.readValue(jacksonJson, TestData.class);
        }
        long jacksonReadTime = System.nanoTime() - start;
        LOG.info("Jackson Read complete.");

        // Output results (times in milliseconds)
        LOG.info("Iterations: " + iterations);
        LOG.info("JsonIo Write Time: " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
    }

    private static TestData createTestData() {
        TestData data = new TestData();
        data.id = 1;
        data.name = "Test Object";
        data.values = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        NestedData nested = new NestedData();
        nested.metric = 123.456;
        nested.active = true;
        nested.description = "A nested object used to stress test serialization performance.";
        nested.tags = new String[]{"tag1", "tag2", "tag3", "performance", "testing"};
        nested.metadata = new HashMap<>();
        nested.metadata.put("version", "1.0");
        nested.metadata.put("count", 42);
        nested.metadata.put("enabled", true);
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
