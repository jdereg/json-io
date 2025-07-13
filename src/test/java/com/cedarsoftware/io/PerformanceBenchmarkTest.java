package com.cedarsoftware.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive performance benchmarks to validate optimizations and ensure security 
 * improvements don't negatively impact performance.
 * 
 * @author Claude Code AI Assistant
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
public class PerformanceBenchmarkTest {
    private static final Logger LOG = Logger.getLogger(PerformanceBenchmarkTest.class.getName());

    private WriteOptions writeOptions;
    private ReadOptions readOptions;
    
    // Test data structures for benchmarking
    private Map<String, Object> complexMap;
    private List<Object> complexList;
    private TestObject[] objectArray;
    
    @BeforeEach
    public void setUp() {
        writeOptions = new WriteOptionsBuilder().build();
        readOptions = new ReadOptionsBuilder().build();
        
        // Create complex test data
        setupTestData();
    }
    
    private void setupTestData() {
        // Complex map with nested structures
        complexMap = new HashMap<>();
        complexMap.put("id", 12345L);
        complexMap.put("name", "Performance Test Object");
        complexMap.put("active", true);
        complexMap.put("metrics", Arrays.asList(1.1, 2.2, 3.3, 4.4, 5.5));
        
        Map<String, Object> nested = new HashMap<>();
        nested.put("level", 1);
        nested.put("data", "nested data");
        nested.put("timestamp", new Date());
        complexMap.put("nested", nested);
        
        // Complex list with mixed types
        complexList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("index", i);
            item.put("value", "item_" + i);
            item.put("score", Math.random() * 100);
            complexList.add(item);
        }
        
        // Object array
        objectArray = new TestObject[500];
        for (int i = 0; i < objectArray.length; i++) {
            objectArray[i] = new TestObject(i, "object_" + i, Math.random() > 0.5);
        }
    }
    
    /**
     * Benchmark basic serialization performance
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkBasicSerialization() {
        int iterations = 10000;
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            JsonIo.toJson(complexMap, writeOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            JsonIo.toJson(complexMap, writeOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Basic Serialization - Average time per operation: " + avgTimeMs + " ms");
        
        // Should complete in reasonable time (less than 1ms per operation for simple objects)
        assertTrue(avgTimeMs < 1.0, "Serialization performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark basic deserialization performance
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkBasicDeserialization() {
        String json = JsonIo.toJson(complexMap, writeOptions);
        int iterations = 10000;
        
        // Warm up
        for (int i = 0; i < 1000; i++) {
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Basic Deserialization - Average time per operation: " + avgTimeMs + " ms");
        
        // Should complete in reasonable time
        assertTrue(avgTimeMs < 2.0, "Deserialization performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark large collection processing
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void benchmarkLargeCollectionProcessing() {
        int iterations = 100;
        
        // Warm up
        for (int i = 0; i < 10; i++) {
            String json = JsonIo.toJson(complexList, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String json = JsonIo.toJson(complexList, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Large Collection Processing - Average time per operation: " + avgTimeMs + " ms");
        
        // Should handle large collections efficiently (less than 50ms for 1000 items)
        assertTrue(avgTimeMs < 50.0, "Large collection performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark array processing performance
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void benchmarkArrayProcessing() {
        int iterations = 1000;
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            String json = JsonIo.toJson(objectArray, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String json = JsonIo.toJson(objectArray, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Array Processing - Average time per operation: " + avgTimeMs + " ms");
        
        // Should handle arrays efficiently
        assertTrue(avgTimeMs < 20.0, "Array processing performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark string processing optimizations
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkStringProcessing() {
        Map<String, String> stringMap = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            stringMap.put("key_" + i, "This is a test string with some content " + i);
        }
        
        int iterations = 1000;
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            String json = JsonIo.toJson(stringMap, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String json = JsonIo.toJson(stringMap, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("String Processing - Average time per operation: " + avgTimeMs + " ms");
        
        // String processing should be efficient with our StringBuilder optimizations
        assertTrue(avgTimeMs < 15.0, "String processing performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark reflection and caching optimizations
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkReflectionCaching() {
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            objects.add(new TestObject(i, "cached_" + i, i % 2 == 0));
        }
        
        int iterations = 1000;
        
        // Warm up to populate caches
        for (int i = 0; i < 200; i++) {
            String json = JsonIo.toJson(objects, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String json = JsonIo.toJson(objects, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Reflection Caching - Average time per operation: " + avgTimeMs + " ms");
        
        // Caching should improve performance for repeated operations
        assertTrue(avgTimeMs < 25.0, "Reflection caching performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark circular reference handling performance
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkCircularReferenceHandling() {
        // Create objects with circular references
        CircularA objA = new CircularA();
        CircularB objB = new CircularB();
        objA.b = objB;
        objB.a = objA;
        objA.name = "Object A";
        objB.name = "Object B";
        
        int iterations = 1000;
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            String json = JsonIo.toJson(objA, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String json = JsonIo.toJson(objA, writeOptions);
            JsonIo.toJava(json, readOptions);
        }
        long endTime = System.nanoTime();
        
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        LOG.info("Circular Reference Handling - Average time per operation: " + avgTimeMs + " ms");
        
        // Circular reference handling should be efficient
        assertTrue(avgTimeMs < 5.0, "Circular reference performance degraded: " + avgTimeMs + " ms");
    }
    
    /**
     * Benchmark memory efficiency
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void benchmarkMemoryEfficiency() {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection and measure baseline
        System.gc();
        Thread.yield();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Perform many operations
        List<String> jsonStrings = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String json = JsonIo.toJson(complexMap, writeOptions);
            jsonStrings.add(json);
            JsonIo.toJava(json, readOptions);
        }
        
        // Measure memory after operations
        long afterOperationsMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = afterOperationsMemory - initialMemory;
        
        LOG.info("Memory used for 1000 operations: " + (memoryUsed / 1024 / 1024) + " MB");
        
        // Clear references and force GC
        jsonStrings.clear();
        System.gc();
        Thread.yield();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Memory should be released (allowing for some variance)
        long memoryLeaked = finalMemory - initialMemory;
        LOG.info("Potential memory leak: " + (memoryLeaked / 1024 / 1024) + " MB");
        
        // Should not leak significant memory (less than 10MB variance)
        assertTrue(memoryLeaked < 10 * 1024 * 1024, "Potential memory leak detected: " + (memoryLeaked / 1024 / 1024) + " MB");
    }
    
    /**
     * Test data class for benchmarking
     */
    public static class TestObject {
        public int id;
        public String name;
        public boolean active;
        public Date timestamp;
        public List<String> tags;
        
        public TestObject() {
            this.timestamp = new Date();
            this.tags = Arrays.asList("tag1", "tag2", "tag3");
        }
        
        public TestObject(int id, String name, boolean active) {
            this();
            this.id = id;
            this.name = name;
            this.active = active;
        }
    }
    
    /**
     * Circular reference test classes
     */
    public static class CircularA {
        public String name;
        public CircularB b;
    }
    
    public static class CircularB {
        public String name;
        public CircularA a;
    }
}