package com.cedarsoftware.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonPerformanceTest {

    // A sample POJO with a variety of fields to simulate complex JSON
    public static class TestData {
        public int id;
        public String name;
        public int[] values;
        public NestedData nested;
        public List<String> list;
        public Map<String, String> map;
    }

    public static class NestedData {
        public double metric;
        public boolean active;
        public String description;
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
        // Run a number of iterations to let the JVM “warm up” (JIT compilation, etc.)
        int warmupIterations = 10000;
        for (int i = 0; i < warmupIterations; i++) {
            // JsonIo warm-up (serialization then deserialization)
            String json = JsonIo.toJson(testData, writeOptions);
            TestData obj = JsonIo.toJava(json, readOptions).asClass(TestData.class);

            // Jackson warm-up (serialization then deserialization)
            String jJson = jacksonMapper.writeValueAsString(testData);
            TestData jObj = jacksonMapper.readValue(jJson, TestData.class);
        }

        // --- Performance Test ---
        int iterations = 100000;  // adjust as needed to stress the system

        // Test JsonIo serialization ("writing")
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < iterations; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            // Use dummy to prevent dead-code elimination (but do minimal work)
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;

        // Test Jackson serialization ("writing")
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            dummy = jacksonMapper.writeValueAsString(testData);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jacksonWriteTime = System.nanoTime() - start;

        // Prepare JSON strings for reading tests (to remove serialization overhead)
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);

        // Test JsonIo deserialization ("reading")
        start = System.nanoTime();
        TestData result = null;
        for (int i = 0; i < iterations; i++) {
            result = JsonIo.toJava(jsonIoJson, readOptions).asClass(TestData.class);
        }
        long jsonIoReadTime = System.nanoTime() - start;

        // Test Jackson deserialization ("reading")
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            result = jacksonMapper.readValue(jacksonJson, TestData.class);
        }
        long jacksonReadTime = System.nanoTime() - start;

        // Output results (times in milliseconds)
        System.out.println("Iterations: " + iterations);
        System.out.println("JsonIo Write Time: " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        System.out.println("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        System.out.println("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        System.out.println("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
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
        data.nested = nested;

        data.list = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.list.add("List item " + i);
        }

        data.map = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            data.map.put("key" + i, "value" + i);
        }
        return data;
    }
}
