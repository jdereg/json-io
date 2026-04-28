package com.cedarsoftware.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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

        // Maps declared as CONCRETE types (HashMap, LinkedHashMap) - NO @type emitted in JSON
        // These exercise tryCreateMapDirectly() without early bail-out on @type
        // When field type matches instance type exactly, JsonWriter omits @type
        public HashMap<String, String> concreteHashMap;           // No @type - exercises direct Map creation
        public LinkedHashMap<String, Integer> concreteLinkedMap;  // No @type - exercises direct Map creation

        // === Expanded coverage to exercise more code paths ===

        // Floating point fields (exercises readFloatingPoint path, not just readInteger fast path)
        public double ratio;
        public double precision;
        public float factor;
        public double[] measurements;      // Array of doubles
        public List<Double> samples;       // List of doubles (boxed)

        // Negative numbers (exercises negative integer path)
        public int offset;
        public long signedBalance;
        public int[] deltas;               // Mix of positive and negative

        // Boolean fields / arrays
        public boolean enabled;
        public boolean verified;
        public boolean[] flags;
        public List<Boolean> switches;

        // Strings with escapes / non-ASCII / long content
        public String escaped;             // Contains backslash escapes
        public String unicode;             // Contains unicode escapes
        public String longText;            // > 256 chars (exercises readStringSlowPath)

        // Big numbers
        public BigInteger bigId;
        public BigDecimal price;
        public List<BigDecimal> prices;

        // Date/time types (exercise Converter paths)
        public Date created;
        public Instant instant;
        public LocalDate localDate;
        public LocalDateTime localDateTime;
        public ZonedDateTime zonedDateTime;

        // UUID (exercises custom type conversion)
        public UUID uuid;
        public List<UUID> uuids;

        // Pseudo-primitive temporal lists — realistic real-world shape (event logs, billing dates).
        // Mirror the existing List<UUID>/List<BigDecimal> shape for fair coverage of temporals.
        public List<Instant> eventTimestamps;
        public List<LocalDate> billingDates;

        // Enum names as plain strings (avoids cross-library enum serialization mismatches
        // while still exercising string paths that would be used for enum values in practice)
        public String statusName;
        public String priorityName;

        // TreeMap — exercises non-HashMap Map creation path
        public TreeMap<String, String> treeMap;

        // Nullable fields (exercises null handling paths)
        public String nullableName;
        public Integer nullableCount;
        public NestedData nullableNested;

        // Deeply nested object (5 levels) — exercises recursion depth
        public DeepNode deepNode;

        // Secondary complex POJO to vary class types during parsing
        public SecondaryData secondary;
        public List<SecondaryData> secondaryList;
    }

    public enum Status { ACTIVE, INACTIVE, PENDING, ARCHIVED, DELETED }
    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

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

    // Secondary POJO with different shape than NestedData to exercise varied class paths
    public static class SecondaryData {
        public String code;
        public int sequence;
        public double weight;
        public boolean active;
        public UUID refId;
        public List<Integer> buckets;
        public Map<String, Double> scoreMap;
    }

    // Deeply nested POJO (5 levels) — exercises parser/resolver recursion
    public static class DeepNode {
        public String label;
        public int depth;
        public DeepNode child;
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
        // Register JavaTimeModule so Jackson can handle Instant/LocalDate/LocalDateTime/ZonedDateTime
        jacksonMapper.registerModule(new JavaTimeModule());
        jacksonMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoMinimalPlus().build();
        // cycleSupport(false) skips traceReferences() pass - faster for acyclic data
        WriteOptions writeOptionsNoCycles = new WriteOptionsBuilder().showTypeInfoMinimalPlus().cycleSupport(false).build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        // Warm-up
        LOG.info("Starting warmup with " + WARMUP_ITERATIONS + " iterations...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String json = JsonIo.toJson(testData, writeOptions);
            TestData obj = JsonIo.toJava(json, readOptions).asClass(TestData.class);
            String json2 = JsonIo.toJson(testData, writeOptionsNoCycles);
            TestData obj2 = JsonIo.toJava(json2, readOptions).asClass(TestData.class);
            String toon = JsonIo.toToon(testData, writeOptions);
            TestData toonObj = JsonIo.fromToon(toon, readOptions).asClass(TestData.class);
            String toon2 = JsonIo.toToon(testData, writeOptionsNoCycles);
            TestData toonObj2 = JsonIo.fromToon(toon2, readOptions).asClass(TestData.class);
            String jJson = jacksonMapper.writeValueAsString(testData);
            TestData jObj = jacksonMapper.readValue(jJson, TestData.class);
        }
        LOG.info("Warmup complete.");

        // Test Write with cycle support (default)
        LOG.info("Testing JsonIo Write (cycleSupport=true) with " + TEST_ITERATIONS + " iterations...");
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;
        LOG.info("JsonIo Write (cycleSupport=true) complete.");

        // Test TOON Write with cycle support (default)
        LOG.info("Testing Toon Write (cycleSupport=true) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toToon(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long toonWriteTime = System.nanoTime() - start;
        LOG.info("Toon Write (cycleSupport=true) complete.");

        // Test Write without cycle support (faster for acyclic data)
        LOG.info("Testing JsonIo Write (cycleSupport=false) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptionsNoCycles);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTimeNoCycles = System.nanoTime() - start;
        LOG.info("JsonIo Write (cycleSupport=false) complete.");

        LOG.info("Testing Toon Write (cycleSupport=false) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toToon(testData, writeOptionsNoCycles);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long toonWriteTimeNoCycles = System.nanoTime() - start;
        LOG.info("Toon Write (cycleSupport=false) complete.");

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
        String toon = JsonIo.toToon(testData, writeOptions);
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

        LOG.info("Testing Toon Read (fromToon) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            result = JsonIo.fromToon(toon, readOptions).asClass(TestData.class);
        }
        long toonReadTime = System.nanoTime() - start;
        LOG.info("Toon Read complete.");

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
        LOG.info("JsonIo Write Time (cycleSupport=true):  " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=true):    " + (toonWriteTime / 1_000_000.0) + " ms");
        LOG.info("JsonIo Write Time (cycleSupport=false): " + (jsonIoWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=false):   " + (toonWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        LOG.info("Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) jsonIoWriteTime / jsonIoWriteTimeNoCycles));
        LOG.info("TOON Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) toonWriteTime / toonWriteTimeNoCycles));
        LOG.info("Write Ratio (JsonIo cycleSupport=true / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (JsonIo cycleSupport=false / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=true / Jackson): " + String.format("%.2fx", (double) toonWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=false / Jackson): " + String.format("%.2fx", (double) toonWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Toon Read Time: " + (toonReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
        LOG.info("Read Ratio (Toon/Jackson): " + String.format("%.2fx", (double) toonReadTime / jacksonReadTime));
    }

    /**
     * Test Maps-only parsing (parsing to Map/List graph, no Java object resolution)
     */
    public static void testMapsOnly() throws IOException {
        LOG.info("=== TEST: Maps Only (toMaps) ===");

        TestData testData = createTestData();
        ObjectMapper jacksonMapper = new ObjectMapper();
        // Register JavaTimeModule so Jackson can handle Instant/LocalDate/LocalDateTime/ZonedDateTime
        jacksonMapper.registerModule(new JavaTimeModule());
        jacksonMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoMinimalPlus().build();
        // cycleSupport(false) skips traceReferences() pass - faster for acyclic data
        WriteOptions writeOptionsNoCycles = new WriteOptionsBuilder().showTypeInfoMinimalPlus().cycleSupport(false).build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        // Warm-up
        LOG.info("Starting warmup with " + WARMUP_ITERATIONS + " iterations...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String json = JsonIo.toJson(testData, writeOptions);
            Map map = JsonIo.toMaps(json, readOptions).asClass(Map.class);
            String json2 = JsonIo.toJson(testData, writeOptionsNoCycles);
            Map map2 = JsonIo.toMaps(json2, readOptions).asClass(Map.class);
            String toon = JsonIo.toToon(testData, writeOptions);
            Map toonMap = JsonIo.fromToonToMaps(toon, readOptions).asClass(Map.class);
            String toon2 = JsonIo.toToon(testData, writeOptionsNoCycles);
            Map toonMap2 = JsonIo.fromToonToMaps(toon2, readOptions).asClass(Map.class);
            String jJson = jacksonMapper.writeValueAsString(testData);
            Map<String, Object> jMap = jacksonMapper.readValue(jJson, Map.class);
        }
        LOG.info("Warmup complete.");

        // Test Write with cycle support (default)
        LOG.info("Testing JsonIo Write (cycleSupport=true) with " + TEST_ITERATIONS + " iterations...");
        long start = System.nanoTime();
        String dummy = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTime = System.nanoTime() - start;
        LOG.info("JsonIo Write (cycleSupport=true) complete.");

        LOG.info("Testing Toon Write (cycleSupport=true) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toToon(testData, writeOptions);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long toonWriteTime = System.nanoTime() - start;
        LOG.info("Toon Write (cycleSupport=true) complete.");

        // Test Write without cycle support (faster for acyclic data)
        LOG.info("Testing JsonIo Write (cycleSupport=false) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toJson(testData, writeOptionsNoCycles);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long jsonIoWriteTimeNoCycles = System.nanoTime() - start;
        LOG.info("JsonIo Write (cycleSupport=false) complete.");

        LOG.info("Testing Toon Write (cycleSupport=false) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            dummy = JsonIo.toToon(testData, writeOptionsNoCycles);
            if (dummy.length() == 0) { /* no-op */ }
        }
        long toonWriteTimeNoCycles = System.nanoTime() - start;
        LOG.info("Toon Write (cycleSupport=false) complete.");

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
        String toon = JsonIo.toToon(testData, writeOptions);
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

        LOG.info("Testing Toon Read (fromToonToMaps) with " + TEST_ITERATIONS + " iterations...");
        start = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            mapResult = JsonIo.fromToonToMaps(toon, readOptions).asClass(Map.class);
        }
        long toonReadTime = System.nanoTime() - start;
        LOG.info("Toon Read complete.");

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
        LOG.info("JsonIo Write Time (cycleSupport=true):  " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=true):    " + (toonWriteTime / 1_000_000.0) + " ms");
        LOG.info("JsonIo Write Time (cycleSupport=false): " + (jsonIoWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=false):   " + (toonWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        LOG.info("Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) jsonIoWriteTime / jsonIoWriteTimeNoCycles));
        LOG.info("TOON Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) toonWriteTime / toonWriteTimeNoCycles));
        LOG.info("Write Ratio (JsonIo cycleSupport=true / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (JsonIo cycleSupport=false / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=true / Jackson): " + String.format("%.2fx", (double) toonWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=false / Jackson): " + String.format("%.2fx", (double) toonWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Toon Read Time: " + (toonReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
        LOG.info("Read Ratio (Toon/Jackson): " + String.format("%.2fx", (double) toonReadTime / jacksonReadTime));
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

        // Maps declared as CONCRETE types - these will NOT have @type in JSON output
        // This exercises the tryCreateMapDirectly() code path that doesn't bail on @type

        // HashMap<String, String> - no @type because field type == instance type
        data.concreteHashMap = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            data.concreteHashMap.put("hashKey" + i, "hashValue" + i);
        }

        // LinkedHashMap<String, Integer> - no @type because field type == instance type
        data.concreteLinkedMap = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            data.concreteLinkedMap.put("linkedKey" + i, i * 7);
        }

        // === Populate expanded-coverage fields ===

        // Floating-point fields
        data.ratio = 3.141592653589793;
        data.precision = 2.718281828459045;
        data.factor = 1.414213f;
        data.measurements = new double[]{0.1, 0.5, 1.25, 2.5, 5.0, 10.0, 20.0, 50.0, 100.0, 1000.0};
        data.samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.samples.add(i * 1.5);
        }

        // Negative numbers
        data.offset = -42;
        data.signedBalance = -1234567890L;
        data.deltas = new int[]{-5, -3, -1, 0, 1, 3, 5, -100, 100, -1000};

        // Boolean fields
        data.enabled = true;
        data.verified = false;
        data.flags = new boolean[]{true, false, true, true, false, false, true};
        data.switches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.switches.add(i % 2 == 0);
        }

        // Strings with escapes
        data.escaped = "Line 1\nLine 2\tTabbed\n\"Quoted\"\\Backslash";
        data.unicode = "Unicode: \u00e9\u00e8\u00ea \u00f1 \u00e7 \u4e2d\u6587 emoji-free";
        StringBuilder longBuilder = new StringBuilder(512);
        for (int i = 0; i < 20; i++) {
            longBuilder.append("This is a longer string segment to exceed 256 chars and exercise the slow path. ");
        }
        data.longText = longBuilder.toString();

        // Big numbers
        data.bigId = new BigInteger("12345678901234567890");
        data.price = new BigDecimal("99999.99");
        data.prices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.prices.add(new BigDecimal("1" + i + "." + (i * 11)));
        }

        // Date/time values (fixed for reproducibility)
        data.created = new Date(1700000000000L);
        data.instant = Instant.ofEpochSecond(1700000000L);
        data.localDate = LocalDate.of(2026, 4, 10);
        data.localDateTime = LocalDateTime.of(2026, 4, 10, 12, 30, 45);
        data.zonedDateTime = ZonedDateTime.parse("2026-04-10T12:30:45+00:00");

        // UUID values
        data.uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        data.uuids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.uuids.add(new UUID(i, i * 1000L));
        }

        // Pseudo-primitive temporal lists (10 each — same shape as uuids/prices)
        data.eventTimestamps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.eventTimestamps.add(Instant.ofEpochSecond(1700000000L + i * 3600L));
        }
        data.billingDates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.billingDates.add(LocalDate.of(2026, 1, 1).plusDays(i * 30L));
        }

        // Enum names (as strings)
        data.statusName = Status.ACTIVE.name();
        data.priorityName = Priority.HIGH.name();

        // TreeMap
        data.treeMap = new TreeMap<>();
        for (int i = 0; i < 15; i++) {
            data.treeMap.put("treeKey" + i, "treeValue" + i);
        }

        // Nullable fields (leave nullableName, nullableCount, nullableNested as null)

        // Deep nesting (5 levels)
        DeepNode leaf = new DeepNode();
        leaf.label = "leaf";
        leaf.depth = 5;
        DeepNode level4 = new DeepNode();
        level4.label = "level4";
        level4.depth = 4;
        level4.child = leaf;
        DeepNode level3 = new DeepNode();
        level3.label = "level3";
        level3.depth = 3;
        level3.child = level4;
        DeepNode level2 = new DeepNode();
        level2.label = "level2";
        level2.depth = 2;
        level2.child = level3;
        DeepNode level1 = new DeepNode();
        level1.label = "level1";
        level1.depth = 1;
        level1.child = level2;
        data.deepNode = level1;

        // SecondaryData (different class shape)
        data.secondary = createSecondary(0);
        data.secondaryList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            data.secondaryList.add(createSecondary(i + 1));
        }

        return data;
    }

    private static SecondaryData createSecondary(int seed) {
        SecondaryData s = new SecondaryData();
        s.code = "SEC-" + seed;
        s.sequence = seed * 1000;
        s.weight = 0.5 + seed * 0.25;
        s.active = seed % 2 == 0;
        s.refId = new UUID(seed, seed * 7L);
        s.buckets = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            s.buckets.add(seed * 10 + i);
        }
        s.scoreMap = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            s.scoreMap.put("score" + i, seed + i * 0.1);
        }
        return s;
    }
}
