
package com.cedarsoftware.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
@SuppressWarnings({"unchecked", "rawtypes"})

public class JsonPerformanceTest {
    private static final Logger LOG = Logger.getLogger(JsonPerformanceTest.class.getName());
    private static final int WARMUP_ITERATIONS = 10000;
    private static final int TEST_ITERATIONS = 100000;

    /**
     * Opt-in flag: when set via {@code --with-gson} on the command line (or the
     * {@code gson} mode shortcut), the test additionally measures Gson write/read
     * throughput alongside the existing JsonIo/TOON/Jackson trio. Adds roughly
     * 20-30 seconds to the full run; left off by default so the typical
     * developer feedback loop stays snappy.
     */
    private static boolean runGson = false;

    /**
     * Cached Gson instance — built once, used across all iterations of all
     * phases. Matches the same discipline Jackson uses (single {@code ObjectMapper}
     * per test method). The custom type adapters serialize {@code java.time}
     * values as ISO-8601 strings to align with Jackson's
     * {@code JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false} output, so all
     * three libraries serialize comparable JSON.
     */
    private static final Gson GSON = buildGson();

    private static Gson buildGson() {
        GsonBuilder b = new GsonBuilder();
        // ISO-8601 string round-trip for java.time types — matches the Jackson
        // configuration above (registerModule(new JavaTimeModule()) +
        // disable(WRITE_DATES_AS_TIMESTAMPS)).
        b.registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (src, type, ctx) -> new JsonPrimitive(src.toString()));
        b.registerTypeAdapter(Instant.class,
                (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()));
        b.registerTypeAdapter(LocalDate.class,
                (JsonSerializer<LocalDate>) (src, type, ctx) -> new JsonPrimitive(src.toString()));
        b.registerTypeAdapter(LocalDate.class,
                (JsonDeserializer<LocalDate>) (json, type, ctx) -> LocalDate.parse(json.getAsString()));
        b.registerTypeAdapter(LocalDateTime.class,
                (JsonSerializer<LocalDateTime>) (src, type, ctx) -> new JsonPrimitive(src.toString()));
        b.registerTypeAdapter(LocalDateTime.class,
                (JsonDeserializer<LocalDateTime>) (json, type, ctx) -> LocalDateTime.parse(json.getAsString()));
        b.registerTypeAdapter(ZonedDateTime.class,
                (JsonSerializer<ZonedDateTime>) (src, type, ctx) -> new JsonPrimitive(src.toString()));
        b.registerTypeAdapter(ZonedDateTime.class,
                (JsonDeserializer<ZonedDateTime>) (json, type, ctx) -> ZonedDateTime.parse(json.getAsString()));
        return b.create();
    }

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

    public static void main(String[] args) throws Exception {
        // Parse positional args + optional --with-gson flag. Flag can appear anywhere; the first
        // non-flag arg is the mode (defaults to "both"); remaining positionals feed modes that take
        // parameters (e.g. "profile <lib> <threads> <sec>"). The `gson` shortcut == `both --with-gson`.
        List<String> pos = new ArrayList<>();
        for (String arg : args) {
            if (arg == null) continue;
            if ("--with-gson".equalsIgnoreCase(arg)) {
                runGson = true;
            } else {
                pos.add(arg);
            }
        }
        String mode = pos.isEmpty() ? "both" : pos.get(0).toLowerCase();
        if ("gson".equals(mode)) {
            mode = "both";
            runGson = true;
        }
        if (runGson) {
            LOG.info("Gson comparison enabled (--with-gson).");
        }

        switch (mode) {
            case "java":
                testFullJavaResolution();
                break;
            case "maps":
                testMapsOnly();
                break;
            case "nometa":
            case "no-meta":
                testNoMetadataResolution();
                break;
            case "databind":
            case "strings":
                testStringHeavyDatabindRead();
                break;
            case "threads":
                testThreadedDatabind();
                break;
            case "profile": {
                // profile <lib> <threads> <measureSec>  (run under -XX:StartFlightRecording)
                String lib = pos.size() > 1 ? pos.get(1).toLowerCase() : "jsonio";
                int threads = pos.size() > 2 ? Integer.parseInt(pos.get(2)) : 4;
                int seconds = pos.size() > 3 ? Integer.parseInt(pos.get(3)) : 20;
                profileThreaded(lib, threads, seconds);
                break;
            }
            case "both":
            default:
                testFullJavaResolution();
                LOG.info("");
                LOG.info("========================================");
                LOG.info("");
                testMapsOnly();
                LOG.info("");
                LOG.info("========================================");
                LOG.info("");
                testStringHeavyDatabindRead();
                break;
        }

        // Diagnostic: dump per-class Resolver-traversal stats when the
        // jsonio.instrumentResolver system property is true. Zero cost when
        // the flag is off (ENABLED is a static final boolean — JIT folds the
        // branch to a no-op).
        if (ResolverInstrumentation.ENABLED) {
            ResolverInstrumentation.dump();
        }
    }

    /**
     * Read benchmark variant that exercises the metadata-free workload pattern
     * (no @type, no @id) — the shape of typical Jackson-style POJO JSON. Same
     * TestData graph as {@link #testFullJavaResolution} but serialized with
     * {@code showTypeInfoNever() + cycleSupport(false)} so the JSON contains
     * no json-io metadata. The reader infers types from the caller-supplied
     * target class; this is the optimization path eager construction was
     * designed for.
     */
    public static void testNoMetadataResolution() throws IOException {
        LOG.info("=== TEST: No-metadata Resolution (toJava, no @type, no @id) ===");

        TestData testData = createTestData();
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .cycleSupport(false)
                .build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        String json = JsonIo.toJson(testData, writeOptions);
        LOG.info("Generated JSON size: " + json.length() + " chars");

        // Warmup
        LOG.info("Starting warmup with " + WARMUP_ITERATIONS + " iterations...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            TestData warm = JsonIo.toJava(json, readOptions).asClass(TestData.class);
            if (warm == null) throw new IllegalStateException("warmup result null");
        }
        LOG.info("Warmup complete.");

        LOG.info("Testing JsonIo Read (no-metadata) with " + TEST_ITERATIONS + " iterations...");
        long start = System.nanoTime();
        TestData result = null;
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            result = JsonIo.toJava(json, readOptions).asClass(TestData.class);
        }
        long elapsed = System.nanoTime() - start;
        LOG.info("JsonIo Read complete. Final result hash: " + (result == null ? "null" : Integer.toHexString(System.identityHashCode(result))));
        LOG.info("--- No-metadata Results ---");
        LOG.info("Iterations: " + TEST_ITERATIONS);
        LOG.info("JsonIo Read Time (no-metadata): " + (elapsed / 1_000_000.0) + " ms");
    }

    /**
     * Read benchmark mirroring fabienrenaud/java-json-benchmark's "users" workload — the case where
     * json-io's read gap vs Jackson is largest. Unlike the other read tests (which parse
     * json-io-authored JSON of a type-diverse {@link TestData} — json-io's strength), this parses a
     * FLAT, STRING-HEAVY POJO list from FOREIGN, metadata-free JSON (no {@code @type}/{@code @id},
     * byte-equivalent to Jackson output) into typed objects. A large list of simple string-field
     * objects maximally exposes json-io's two-phase read (tokenize &rarr; JsonObject maps &rarr;
     * Resolver &rarr; injectors) against Jackson's streaming token&rarr;setter. Expect ~10-15x.
     * <p>
     * ~20 persons yields ~10-12 KB of JSON (the benchmark's "10 KB users" tier); at 100k iterations
     * this adds only ~3s to the suite because per-parse the model is cheaper than {@link TestData}.
     */
    public static void testStringHeavyDatabindRead() throws IOException {
        LOG.info("=== TEST: String-heavy Databind Read (foreign metadata-free JSON -> typed POJOs) ===");
        // One ~10 KB tier (the java-json-benchmark "users" shape) as a regression guard for the databind
        // read path — exposes json-io's read overhead vs Jackson without inflating the suite (~+3s). The
        // gap is roughly flat with payload size, so a single representative size is sufficient; pass a
        // larger count to databindReadAtSize() ad hoc if you want to re-characterize the curve.
        databindReadAtSize(20, TEST_ITERATIONS);         // ~10 KB
    }

    /**
     * One databind-read measurement at a given payload size: parse a foreign, metadata-free JSON
     * document ({@code showTypeInfoNever}) of {@code personCount} string-heavy beans into typed POJOs.
     * Jackson reads UTF-8 bytes (its fast path), json-io reads the String (its natural path) — each its
     * idiomatic input for the same logical payload, as java-json-benchmark does.
     */
    private static void databindReadAtSize(int personCount, int iterations) throws IOException {
        People people = createPeople(personCount);
        ObjectMapper jacksonMapper = new ObjectMapper();   // no JavaTimeModule: this model has no java.time fields
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();
        ReadOptions readOptions = ReadOptionsBuilder.getDefaultReadOptions();

        String json = JsonIo.toJson(people, writeOptions);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        int warmup = Math.max(200, iterations / 10);
        for (int i = 0; i < warmup; i++) {
            People w1 = JsonIo.toJava(json, readOptions).asClass(People.class);
            People w2 = jacksonMapper.readValue(jsonBytes, People.class);
            if (w1 == null || w2 == null) throw new IllegalStateException("warmup result null");
        }

        People result = null;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            result = JsonIo.toJava(json, readOptions).asClass(People.class);
        }
        long jsonIoTime = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            result = jacksonMapper.readValue(jsonBytes, People.class);
        }
        long jacksonTime = System.nanoTime() - start;

        long gsonTime = -1;
        if (runGson) {
            start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                result = GSON.fromJson(json, People.class);
            }
            gsonTime = System.nanoTime() - start;
        }

        LOG.info(String.format(
                "  %5d persons (%7d chars, %6d iters): JsonIo %8.1f ms | Jackson %8.1f ms | Read Ratio (JsonIo/Jackson) %5.2fx%s  [result=%s]",
                personCount, json.length(), iterations, jsonIoTime / 1e6, jacksonTime / 1e6,
                (double) jsonIoTime / jacksonTime,
                runGson ? String.format(" | Gson %5.2fx", (double) gsonTime / jacksonTime) : "",
                result == null ? "null" : "ok"));
    }

    // ===================================================================================================
    // Multi-threaded databind read: aggregate throughput + a profiling hook.
    //
    // Real services deserialize concurrently, and single-threaded numbers hide lock/allocation scaling.
    // These drive N worker threads all reading the SAME ~10 KB foreign-JSON payload (java-json-benchmark
    // "users" shape) and report AGGREGATE ops/sec, so json-io's thread-scaling can be compared to Jackson.
    //
    //   main() "threads"                         -> sweep {1,4,8,12} threads for json-io + Jackson, table
    //   main() "profile <lib> <threads> <sec>"   -> hammer ONE lib at ONE thread count for <sec> seconds;
    //                                               run under -XX:StartFlightRecording to capture hotspots.
    // ===================================================================================================
    private static final int THREADED_PERSONS = 20;   // ~10 KB payload

    public static void testThreadedDatabind() throws Exception {
        Payload p = buildPayload(THREADED_PERSONS);
        LOG.info("=== TEST: Multi-threaded Databind Read (aggregate ops/sec, " + p.json.length() + "-char payload) ===");
        LOG.info(String.format("  %-7s | %14s | %14s | %8s | %9s | %9s",
                "threads", "JsonIo ops/s", "Jackson ops/s", "Jak/Jio", "Jio scale", "Jak scale"));
        double jioBase = 0, jakBase = 0;
        for (int t : new int[]{1, 4, 8, 12}) {
            double jio = threadedOps("jsonio", t, p);
            double jak = threadedOps("jackson", t, p);
            if (t == 1) { jioBase = jio; jakBase = jak; }
            LOG.info(String.format("  %-7d | %14.0f | %14.0f | %7.2fx | %8.2fx | %8.2fx",
                    t, jio, jak, jak / jio, jio / jioBase, jak / jakBase));
        }
    }

    public static void profileThreaded(String lib, int threads, int measureSec) throws Exception {
        Payload p = buildPayload(THREADED_PERSONS);
        LOG.info(String.format("=== PROFILE: %s, %d threads, %ds, %d-char payload ===", lib, threads, measureSec, p.json.length()));
        threadedRun(lib, threads, 3000, p);                        // warmup (discarded)
        double ops = threadedRun(lib, threads, measureSec * 1000L, p);
        LOG.info(String.format("  %s: %d threads -> %,.0f ops/s aggregate", lib, threads, ops));
    }

    private static double threadedOps(String lib, int threads, Payload p) throws Exception {
        threadedRun(lib, threads, 2000, p);        // warmup (discarded)
        return threadedRun(lib, threads, 3000, p); // measure
    }

    /** Runs {@code lib} on {@code threads} workers for {@code measureMs}; returns aggregate ops/sec. */
    private static double threadedRun(String lib, int threads, long measureMs, Payload p) throws Exception {
        Supplier<Object> action = readAction(lib, p);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong total = new AtomicLong();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ws = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ws[i] = new Thread(() -> {
                long c = 0;
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { return; }
                while (running.get()) {
                    if (action.get() == null) throw new IllegalStateException("null read result");
                    c++;
                }
                total.addAndGet(c);
            }, lib + "-worker-" + i);
            ws[i].start();
        }
        ready.await();
        long t0 = System.nanoTime();
        go.countDown();
        Thread.sleep(measureMs);
        running.set(false);
        for (Thread w : ws) w.join();
        return total.get() / ((System.nanoTime() - t0) / 1e9);
    }

    private static Supplier<Object> readAction(String lib, Payload p) {
        switch (lib) {
            case "jsonio":
                return () -> JsonIo.toJava(p.json, p.readOptions).asClass(People.class);
            case "jackson":
                return () -> { try { return p.jackson.readValue(p.bytes, People.class); } catch (Exception e) { throw new RuntimeException(e); } };
            case "gson":
                return () -> GSON.fromJson(p.json, People.class);
            default:
                throw new IllegalArgumentException("unknown lib (jsonio|jackson|gson): " + lib);
        }
    }

    /** Shared, read-only payload for the threaded runs (json String for json-io/gson, UTF-8 bytes for Jackson). */
    private static final class Payload {
        final String json; final byte[] bytes; final ObjectMapper jackson; final ReadOptions readOptions;
        Payload(String json, byte[] bytes, ObjectMapper jackson, ReadOptions ro) {
            this.json = json; this.bytes = bytes; this.jackson = jackson; this.readOptions = ro;
        }
    }

    private static Payload buildPayload(int persons) {
        People people = createPeople(persons);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();
        String json = JsonIo.toJson(people, wo);
        return new Payload(json, json.getBytes(StandardCharsets.UTF_8), new ObjectMapper(), ReadOptionsBuilder.getDefaultReadOptions());
    }

    // ---- String-heavy model mirroring java-json-benchmark's Users/User: a JavaBean (private fields +
    //      getters/setters) so Jackson uses its optimized method-based deserializer, exactly as the
    //      benchmark's model does. json-io reads via field injection regardless. The generator sets
    //      fields directly (legal — it is in the enclosing class), so only the read-side setters matter. ----

    public static class People {
        private List<Person> people;
        public List<Person> getPeople() { return people; }
        public void setPeople(List<Person> people) { this.people = people; }
    }

    public static class Person {
        private String id;
        private int index;
        private String guid;
        private boolean active;
        private String balance;
        private String picture;
        private int age;
        private String eyeColor;
        private String name;
        private String gender;
        private String company;
        private String email;
        private String phone;
        private String address;
        private String about;
        private String registered;
        private double latitude;
        private double longitude;
        private List<String> tags;
        private List<Friend> friends;
        private String greeting;
        private String favoriteFruit;

        public String getId() { return id; } public void setId(String v) { id = v; }
        public int getIndex() { return index; } public void setIndex(int v) { index = v; }
        public String getGuid() { return guid; } public void setGuid(String v) { guid = v; }
        public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
        public String getBalance() { return balance; } public void setBalance(String v) { balance = v; }
        public String getPicture() { return picture; } public void setPicture(String v) { picture = v; }
        public int getAge() { return age; } public void setAge(int v) { age = v; }
        public String getEyeColor() { return eyeColor; } public void setEyeColor(String v) { eyeColor = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
        public String getGender() { return gender; } public void setGender(String v) { gender = v; }
        public String getCompany() { return company; } public void setCompany(String v) { company = v; }
        public String getEmail() { return email; } public void setEmail(String v) { email = v; }
        public String getPhone() { return phone; } public void setPhone(String v) { phone = v; }
        public String getAddress() { return address; } public void setAddress(String v) { address = v; }
        public String getAbout() { return about; } public void setAbout(String v) { about = v; }
        public String getRegistered() { return registered; } public void setRegistered(String v) { registered = v; }
        public double getLatitude() { return latitude; } public void setLatitude(double v) { latitude = v; }
        public double getLongitude() { return longitude; } public void setLongitude(double v) { longitude = v; }
        public List<String> getTags() { return tags; } public void setTags(List<String> v) { tags = v; }
        public List<Friend> getFriends() { return friends; } public void setFriends(List<Friend> v) { friends = v; }
        public String getGreeting() { return greeting; } public void setGreeting(String v) { greeting = v; }
        public String getFavoriteFruit() { return favoriteFruit; } public void setFavoriteFruit(String v) { favoriteFruit = v; }
    }

    public static class Friend {
        private String id;
        private String name;
        public String getId() { return id; } public void setId(String v) { id = v; }
        public String getName() { return name; } public void setName(String v) { name = v; }
    }

    /**
     * Deterministic generator for the string-heavy {@link People} graph. Values vary per index so a
     * reader's string cache/interning can't unrealistically favor either side. ~20 persons yields
     * ~10-12 KB of JSON (the java-json-benchmark "10 KB users" tier).
     */
    public static People createPeople(int count) {
        String[] colors = {"brown", "blue", "green", "hazel", "gray"};
        String[] fruits = {"apple", "banana", "strawberry", "mango", "kiwi"};
        String[] genders = {"male", "female"};
        People root = new People();
        root.people = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Person p = new Person();
            p.id = "5f8d0d55b547644" + i;
            p.index = i;
            p.guid = "eab0324c-75ef-49a1-9c49-" + i;
            p.active = (i % 2 == 0);
            p.balance = "$" + (1000 + i * 37) + ".55";
            p.picture = "http://placehold.it/32x32?u=" + i;
            p.age = 20 + (i % 40);
            p.eyeColor = colors[i % colors.length];
            p.name = "Person " + i + " Longname";
            p.gender = genders[i % genders.length];
            p.company = "MEGADYNE-" + i;
            p.email = "person" + i + "@megadyne.example.com";
            p.phone = "+1 (800) 555-" + String.format("%04d", i);
            p.address = (100 + i) + " Enterprise Ave, Springfield IL 6" + String.format("%04d", i);
            p.about = "Person " + i + " enjoys velit aliqua nisi cupidatat consequat.";
            p.registered = "201" + (i % 9) + "-0" + (1 + i % 8) + "-1" + (i % 9) + "T00:30:00 -00:00";
            p.latitude = -80.0 + i * 1.37;
            p.longitude = -170.0 + i * 2.11;
            p.tags = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                p.tags.add("tag" + i + "_" + t);
            }
            p.friends = new ArrayList<>();
            for (int f = 0; f < 2; f++) {
                Friend fr = new Friend();
                fr.id = i + "-" + f;
                fr.name = "Friend " + f + " of " + i;
                p.friends.add(fr);
            }
            p.greeting = "Hello, Person " + i + "!";
            p.favoriteFruit = fruits[i % fruits.length];
            root.people.add(p);
        }
        return root;
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
            if (runGson) {
                String gJson = GSON.toJson(testData);
                TestData gObj = GSON.fromJson(gJson, TestData.class);
            }
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

        // Optional Gson Write (opt-in via --with-gson). Same single-cached-Gson
        // discipline as the Jackson path above; type adapters are pre-registered.
        long gsonWriteTime = -1;
        if (runGson) {
            LOG.info("Testing Gson Write with " + TEST_ITERATIONS + " iterations...");
            start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                dummy = GSON.toJson(testData);
                if (dummy.length() == 0) { /* no-op */ }
            }
            gsonWriteTime = System.nanoTime() - start;
            LOG.info("Gson Write complete.");
        }

        // Prepare JSON strings for reading tests
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String toon = JsonIo.toToon(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);
        String gsonJson = runGson ? GSON.toJson(testData) : null;

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

        long gsonReadTime = -1;
        if (runGson) {
            LOG.info("Testing Gson Read with " + TEST_ITERATIONS + " iterations...");
            start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                result = GSON.fromJson(gsonJson, TestData.class);
            }
            gsonReadTime = System.nanoTime() - start;
            LOG.info("Gson Read complete.");
        }

        // Output results
        LOG.info("--- Full Java Resolution Results ---");
        LOG.info("Iterations: " + TEST_ITERATIONS);
        LOG.info("JsonIo Write Time (cycleSupport=true):  " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=true):    " + (toonWriteTime / 1_000_000.0) + " ms");
        LOG.info("JsonIo Write Time (cycleSupport=false): " + (jsonIoWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=false):   " + (toonWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        if (runGson) {
            LOG.info("Gson Write Time:    " + (gsonWriteTime / 1_000_000.0) + " ms");
        }
        LOG.info("Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) jsonIoWriteTime / jsonIoWriteTimeNoCycles));
        LOG.info("TOON Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) toonWriteTime / toonWriteTimeNoCycles));
        LOG.info("Write Ratio (JsonIo cycleSupport=true / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (JsonIo cycleSupport=false / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=true / Jackson): " + String.format("%.2fx", (double) toonWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=false / Jackson): " + String.format("%.2fx", (double) toonWriteTimeNoCycles / jacksonWriteTime));
        if (runGson) {
            LOG.info("Write Ratio (Gson / Jackson): " + String.format("%.2fx", (double) gsonWriteTime / jacksonWriteTime));
        }
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Toon Read Time: " + (toonReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        if (runGson) {
            LOG.info("Gson Read Time:    " + (gsonReadTime / 1_000_000.0) + " ms");
        }
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
        LOG.info("Read Ratio (Toon/Jackson): " + String.format("%.2fx", (double) toonReadTime / jacksonReadTime));
        if (runGson) {
            LOG.info("Read Ratio (Gson / Jackson): " + String.format("%.2fx", (double) gsonReadTime / jacksonReadTime));
        }
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
            if (runGson) {
                String gJson = GSON.toJson(testData);
                Map gMap = GSON.fromJson(gJson, Map.class);
            }
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

        // Optional Gson Write (opt-in via --with-gson). Same single-cached-Gson
        // discipline as the Jackson path above.
        long gsonWriteTime = -1;
        if (runGson) {
            LOG.info("Testing Gson Write with " + TEST_ITERATIONS + " iterations...");
            start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                dummy = GSON.toJson(testData);
                if (dummy.length() == 0) { /* no-op */ }
            }
            gsonWriteTime = System.nanoTime() - start;
            LOG.info("Gson Write complete.");
        }

        // Prepare JSON strings for reading tests
        String jsonIoJson = JsonIo.toJson(testData, writeOptions);
        String toon = JsonIo.toToon(testData, writeOptions);
        String jacksonJson = jacksonMapper.writeValueAsString(testData);
        String gsonJson = runGson ? GSON.toJson(testData) : null;

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

        long gsonReadTime = -1;
        if (runGson) {
            LOG.info("Testing Gson Read (to Map) with " + TEST_ITERATIONS + " iterations...");
            start = System.nanoTime();
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                mapResult = GSON.fromJson(gsonJson, Map.class);
            }
            gsonReadTime = System.nanoTime() - start;
            LOG.info("Gson Read complete.");
        }

        // Output results
        LOG.info("--- Maps Only Results ---");
        LOG.info("Iterations: " + TEST_ITERATIONS);
        LOG.info("JsonIo Write Time (cycleSupport=true):  " + (jsonIoWriteTime / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=true):    " + (toonWriteTime / 1_000_000.0) + " ms");
        LOG.info("JsonIo Write Time (cycleSupport=false): " + (jsonIoWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Toon Write Time (cycleSupport=false):   " + (toonWriteTimeNoCycles / 1_000_000.0) + " ms");
        LOG.info("Jackson Write Time: " + (jacksonWriteTime / 1_000_000.0) + " ms");
        if (runGson) {
            LOG.info("Gson Write Time:    " + (gsonWriteTime / 1_000_000.0) + " ms");
        }
        LOG.info("Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) jsonIoWriteTime / jsonIoWriteTimeNoCycles));
        LOG.info("TOON Write Speedup (cycleSupport=false vs true): " + String.format("%.2fx", (double) toonWriteTime / toonWriteTimeNoCycles));
        LOG.info("Write Ratio (JsonIo cycleSupport=true / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (JsonIo cycleSupport=false / Jackson): " + String.format("%.2fx", (double) jsonIoWriteTimeNoCycles / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=true / Jackson): " + String.format("%.2fx", (double) toonWriteTime / jacksonWriteTime));
        LOG.info("Write Ratio (Toon cycleSupport=false / Jackson): " + String.format("%.2fx", (double) toonWriteTimeNoCycles / jacksonWriteTime));
        if (runGson) {
            LOG.info("Write Ratio (Gson / Jackson): " + String.format("%.2fx", (double) gsonWriteTime / jacksonWriteTime));
        }
        LOG.info("JsonIo Read Time: " + (jsonIoReadTime / 1_000_000.0) + " ms");
        LOG.info("Toon Read Time: " + (toonReadTime / 1_000_000.0) + " ms");
        LOG.info("Jackson Read Time: " + (jacksonReadTime / 1_000_000.0) + " ms");
        if (runGson) {
            LOG.info("Gson Read Time:    " + (gsonReadTime / 1_000_000.0) + " ms");
        }
        LOG.info("Read Ratio (JsonIo/Jackson): " + String.format("%.2fx", (double) jsonIoReadTime / jacksonReadTime));
        LOG.info("Read Ratio (Toon/Jackson): " + String.format("%.2fx", (double) toonReadTime / jacksonReadTime));
        if (runGson) {
            LOG.info("Read Ratio (Gson / Jackson): " + String.format("%.2fx", (double) gsonReadTime / jacksonReadTime));
        }
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
