package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verify that json-io can read standard JSON with String keys (e.g., {"100": "value"})
 * and convert those keys to their declared Map key type (e.g., Map&lt;Long, String&gt;).
 *
 * This exercises the read-side pipeline:
 *   traverseMap() → processMapEntries() → traverseArray() → readWithFactoryIfExists()
 *   → Converter.convert("100", Long.class) → 100L
 *
 * These tests hand-craft JSON with @type pointing to a holder class whose field
 * declares a specific Map generic type, so the reader has the type info it needs.
 */
class StringKeyConversionTest {

    // ========== Holder classes — each declares a Map with a specific non-String key type ==========

    public static class LongKeyHolder {
        public Map<Long, String> map;
    }

    public static class IntegerKeyHolder {
        public Map<Integer, String> map;
    }

    public static class DoubleKeyHolder {
        public Map<Double, String> map;
    }

    public static class BooleanKeyHolder {
        public Map<Boolean, String> map;
    }

    public static class BigDecimalKeyHolder {
        public Map<BigDecimal, String> map;
    }

    public static class BigIntegerKeyHolder {
        public Map<BigInteger, String> map;
    }

    public static class UUIDKeyHolder {
        public Map<UUID, String> map;
    }

    public static class DateKeyHolder {
        public Map<Date, String> map;
    }

    public static class ZonedDateTimeKeyHolder {
        public Map<ZonedDateTime, String> map;
    }

    public static class AtomicLongKeyHolder {
        public Map<AtomicLong, String> map;
    }

    public static class CharacterKeyHolder {
        public Map<Character, String> map;
    }

    // ========== Tests ==========

    @Test
    void testLongKeysFromStringJson() {
        String json = "{\"@type\":\"" + LongKeyHolder.class.getName() + "\",\"map\":{\"100\":\"alpha\",\"200\":\"beta\"}}";

        LongKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LongKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(100L)).isEqualTo("alpha");
        assertThat(holder.map.get(200L)).isEqualTo("beta");
        // Verify keys are actually Long, not String
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Long.class);
        }
    }

    @Test
    void testIntegerKeysFromStringJson() {
        String json = "{\"@type\":\"" + IntegerKeyHolder.class.getName() + "\",\"map\":{\"42\":\"answer\",\"7\":\"lucky\"}}";

        IntegerKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(IntegerKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(42)).isEqualTo("answer");
        assertThat(holder.map.get(7)).isEqualTo("lucky");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Integer.class);
        }
    }

    @Test
    void testDoubleKeysFromStringJson() {
        String json = "{\"@type\":\"" + DoubleKeyHolder.class.getName() + "\",\"map\":{\"3.14\":\"pi\",\"2.718\":\"e\"}}";

        DoubleKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(DoubleKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(3.14)).isEqualTo("pi");
        assertThat(holder.map.get(2.718)).isEqualTo("e");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Double.class);
        }
    }

    @Test
    void testBooleanKeysFromStringJson() {
        String json = "{\"@type\":\"" + BooleanKeyHolder.class.getName() + "\",\"map\":{\"true\":\"yes\",\"false\":\"no\"}}";

        BooleanKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(BooleanKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(true)).isEqualTo("yes");
        assertThat(holder.map.get(false)).isEqualTo("no");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Boolean.class);
        }
    }

    @Test
    void testBigDecimalKeysFromStringJson() {
        String json = "{\"@type\":\"" + BigDecimalKeyHolder.class.getName() + "\",\"map\":{\"123.456\":\"a\",\"789.012\":\"b\"}}";

        BigDecimalKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(BigDecimalKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(new BigDecimal("123.456"))).isEqualTo("a");
        assertThat(holder.map.get(new BigDecimal("789.012"))).isEqualTo("b");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(BigDecimal.class);
        }
    }

    @Test
    void testBigIntegerKeysFromStringJson() {
        String json = "{\"@type\":\"" + BigIntegerKeyHolder.class.getName() + "\",\"map\":{\"99999999999999999999\":\"big\",\"42\":\"small\"}}";

        BigIntegerKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(BigIntegerKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(new BigInteger("99999999999999999999"))).isEqualTo("big");
        assertThat(holder.map.get(new BigInteger("42"))).isEqualTo("small");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(BigInteger.class);
        }
    }

    @Test
    void testUUIDKeysFromStringJson() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        String json = "{\"@type\":\"" + UUIDKeyHolder.class.getName() + "\",\"map\":{\"" + uuid1 + "\":\"first\",\"" + uuid2 + "\":\"second\"}}";

        UUIDKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(UUIDKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(uuid1)).isEqualTo("first");
        assertThat(holder.map.get(uuid2)).isEqualTo("second");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(UUID.class);
        }
    }

    @Test
    void testDateKeysFromStringJson() {
        // Converter's Date→String produces a specific format; use epoch millis as a simpler string form
        Date d1 = new Date(1700000000000L);
        Date d2 = new Date(1600000000000L);

        // Convert dates to their Converter string form for the JSON
        String d1Str = com.cedarsoftware.util.Converter.convert(d1, String.class);
        String d2Str = com.cedarsoftware.util.Converter.convert(d2, String.class);

        String json = "{\"@type\":\"" + DateKeyHolder.class.getName() + "\",\"map\":{\"" + d1Str + "\":\"recent\",\"" + d2Str + "\":\"older\"}}";

        DateKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(DateKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(d1)).isEqualTo("recent");
        assertThat(holder.map.get(d2)).isEqualTo("older");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Date.class);
        }
    }

    @Test
    void testZonedDateTimeKeysFromStringJson() {
        ZonedDateTime zdt1 = ZonedDateTime.parse("2025-06-15T10:30:00Z");
        ZonedDateTime zdt2 = ZonedDateTime.parse("2024-01-01T00:00:00Z");

        String zdt1Str = com.cedarsoftware.util.Converter.convert(zdt1, String.class);
        String zdt2Str = com.cedarsoftware.util.Converter.convert(zdt2, String.class);

        String json = "{\"@type\":\"" + ZonedDateTimeKeyHolder.class.getName() + "\",\"map\":{\"" + zdt1Str + "\":\"summer\",\"" + zdt2Str + "\":\"newyear\"}}";

        ZonedDateTimeKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(ZonedDateTimeKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(zdt1)).isEqualTo("summer");
        assertThat(holder.map.get(zdt2)).isEqualTo("newyear");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(ZonedDateTime.class);
        }
    }

    @Test
    void testAtomicLongKeysFromStringJson() {
        String json = "{\"@type\":\"" + AtomicLongKeyHolder.class.getName() + "\",\"map\":{\"1000\":\"kilo\",\"1000000\":\"mega\"}}";

        AtomicLongKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(AtomicLongKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        // AtomicLong doesn't override equals, so look up by iterating
        Map<Long, String> asLongMap = new LinkedHashMap<>();
        for (Map.Entry<AtomicLong, String> entry : holder.map.entrySet()) {
            assertThat(entry.getKey()).isInstanceOf(AtomicLong.class);
            asLongMap.put(entry.getKey().get(), entry.getValue());
        }
        assertThat(asLongMap.get(1000L)).isEqualTo("kilo");
        assertThat(asLongMap.get(1000000L)).isEqualTo("mega");
    }

    @Test
    void testCharacterKeysFromStringJson() {
        String json = "{\"@type\":\"" + CharacterKeyHolder.class.getName() + "\",\"map\":{\"A\":\"alpha\",\"Z\":\"zulu\"}}";

        CharacterKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(CharacterKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get('A')).isEqualTo("alpha");
        assertThat(holder.map.get('Z')).isEqualTo("zulu");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Character.class);
        }
    }

    // ========== Edge case: keys alongside @keys/@items backward compat ==========

    @Test
    void testOldFormatKeysItemsStillWorks() {
        // Verify the old @keys/@items format still reads correctly (backward compat)
        String json = "{\"@type\":\"" + LongKeyHolder.class.getName() + "\",\"map\":{\"@type\":\"java.util.LinkedHashMap\",\"@keys\":[100,200],\"@items\":[\"alpha\",\"beta\"]}}";

        LongKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LongKeyHolder.class);

        assertThat(holder.map).hasSize(2);
        assertThat(holder.map.get(100L)).isEqualTo("alpha");
        assertThat(holder.map.get(200L)).isEqualTo("beta");
        for (Object key : holder.map.keySet()) {
            assertThat(key).isInstanceOf(Long.class);
        }
    }

    @Test
    void testEmptyMapWithTypedKeys() {
        String json = "{\"@type\":\"" + LongKeyHolder.class.getName() + "\",\"map\":{}}";

        LongKeyHolder holder = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LongKeyHolder.class);

        assertThat(holder.map).isNotNull();
        assertThat(holder.map).isEmpty();
    }

    // ========== WRITE-SIDE TESTS: stringifyMapKeys option ==========

    private static final WriteOptions STRINGIFY_ON = new WriteOptionsBuilder()
            .showTypeInfoNever()
            .stringifyMapKeys(true)
            .build();

    private static final WriteOptions STRINGIFY_OFF = new WriteOptionsBuilder()
            .showTypeInfoNever()
            .stringifyMapKeys(false)
            .build();

    @Test
    void testWriteLongKeysStringified() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(100L, "alpha");
        map.put(200L, "beta");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        // Should be standard JSON object, not @keys/@items
        assertThat(json).contains("\"100\"");
        assertThat(json).contains("\"200\"");
        assertThat(json).doesNotContain("@keys");
        assertThat(json).doesNotContain("@items");
    }

    @Test
    void testWriteLongKeysDefaultOff() {
        Map<Long, String> map = new LinkedHashMap<>();
        map.put(100L, "alpha");
        map.put(200L, "beta");

        String json = JsonIo.toJson(map, STRINGIFY_OFF);

        // Default off — should use @keys/@items
        assertThat(json).contains("@keys");
        assertThat(json).contains("@items");
    }

    @Test
    void testWriteIntegerKeysStringified() {
        Map<Integer, String> map = new LinkedHashMap<>();
        map.put(42, "answer");
        map.put(7, "lucky");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).contains("\"42\"");
        assertThat(json).contains("\"7\"");
        assertThat(json).doesNotContain("@keys");
    }

    @Test
    void testWriteUUIDKeysStringified() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Map<UUID, String> map = new LinkedHashMap<>();
        map.put(uuid1, "first");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).contains("\"550e8400-e29b-41d4-a716-446655440000\"");
        assertThat(json).doesNotContain("@keys");
    }

    @Test
    void testWriteBooleanKeysStringified() {
        Map<Boolean, String> map = new LinkedHashMap<>();
        map.put(true, "yes");
        map.put(false, "no");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).contains("\"true\"");
        assertThat(json).contains("\"false\"");
        assertThat(json).doesNotContain("@keys");
    }

    @Test
    void testWriteBigDecimalKeysStringified() {
        Map<BigDecimal, String> map = new LinkedHashMap<>();
        map.put(new BigDecimal("123.456"), "a");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).contains("\"123.456\"");
        assertThat(json).doesNotContain("@keys");
    }

    @Test
    void testWriteCharacterKeysStringified() {
        Map<Character, String> map = new LinkedHashMap<>();
        map.put('A', "alpha");
        map.put('Z', "zulu");

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).contains("\"A\"");
        assertThat(json).contains("\"Z\"");
        assertThat(json).doesNotContain("@keys");
    }

    @Test
    void testWriteStringKeysUnaffected() {
        // String-keyed maps should work the same regardless of stringifyMapKeys setting
        Map<String, String> map = new LinkedHashMap<>();
        map.put("hello", "world");

        String jsonOn = JsonIo.toJson(map, STRINGIFY_ON);
        String jsonOff = JsonIo.toJson(map, STRINGIFY_OFF);

        assertThat(jsonOn).contains("\"hello\"");
        assertThat(jsonOff).contains("\"hello\"");
        assertThat(jsonOn).doesNotContain("@keys");
        assertThat(jsonOff).doesNotContain("@keys");
    }

    @Test
    void testForceMapOutputOverridesStringify() {
        // forceMapOutputAsTwoArrays should override stringifyMapKeys
        WriteOptions forceArrays = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .stringifyMapKeys(true)
                .forceMapOutputAsTwoArrays(true)
                .build();

        Map<Long, String> map = new LinkedHashMap<>();
        map.put(100L, "alpha");

        String json = JsonIo.toJson(map, forceArrays);

        assertThat(json).contains("@keys");
        assertThat(json).contains("@items");
    }

    @Test
    void testJson5DefaultsStringifyOn() {
        // JSON5 mode should default stringifyMapKeys to true
        WriteOptions json5Options = new WriteOptionsBuilder()
                .json5()
                .build();

        Map<Long, String> map = new LinkedHashMap<>();
        map.put(100L, "alpha");
        map.put(200L, "beta");

        String json = JsonIo.toJson(map, json5Options);

        assertThat(json).doesNotContain("@keys");
        assertThat(json).doesNotContain("$keys");
    }

    @Test
    void testRoundTripLongKeysStringified() {
        // Full round-trip: write with stringify → read back → verify typed keys
        LongKeyHolder original = new LongKeyHolder();
        original.map = new LinkedHashMap<>();
        original.map.put(100L, "alpha");
        original.map.put(200L, "beta");
        original.map.put(-50L, "negative");

        WriteOptions writeOpts = new WriteOptionsBuilder()
                .stringifyMapKeys(true)
                .build();
        String json = JsonIo.toJson(original, writeOpts);

        // Verify it's standard JSON format (no @keys/@items)
        assertThat(json).doesNotContain("@keys");

        LongKeyHolder restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(LongKeyHolder.class);

        assertThat(restored.map).hasSize(3);
        assertThat(restored.map.get(100L)).isEqualTo("alpha");
        assertThat(restored.map.get(200L)).isEqualTo("beta");
        assertThat(restored.map.get(-50L)).isEqualTo("negative");
        for (Object key : restored.map.keySet()) {
            assertThat(key).isInstanceOf(Long.class);
        }
    }

    @Test
    void testRoundTripUUIDKeysStringified() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

        UUIDKeyHolder original = new UUIDKeyHolder();
        original.map = new LinkedHashMap<>();
        original.map.put(uuid1, "first");
        original.map.put(uuid2, "second");

        WriteOptions writeOpts = new WriteOptionsBuilder()
                .stringifyMapKeys(true)
                .build();
        String json = JsonIo.toJson(original, writeOpts);
        assertThat(json).doesNotContain("@keys");

        UUIDKeyHolder restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(UUIDKeyHolder.class);

        assertThat(restored.map).hasSize(2);
        assertThat(restored.map.get(uuid1)).isEqualTo("first");
        assertThat(restored.map.get(uuid2)).isEqualTo("second");
    }

    @Test
    void testRoundTripIntegerKeysStringified() {
        IntegerKeyHolder original = new IntegerKeyHolder();
        original.map = new LinkedHashMap<>();
        original.map.put(42, "answer");
        original.map.put(0, "zero");
        original.map.put(-1, "minus");

        WriteOptions writeOpts = new WriteOptionsBuilder()
                .stringifyMapKeys(true)
                .build();
        String json = JsonIo.toJson(original, writeOpts);
        assertThat(json).doesNotContain("@keys");

        IntegerKeyHolder restored = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(IntegerKeyHolder.class);

        assertThat(restored.map).hasSize(3);
        assertThat(restored.map.get(42)).isEqualTo("answer");
        assertThat(restored.map.get(0)).isEqualTo("zero");
        assertThat(restored.map.get(-1)).isEqualTo("minus");
        for (Object key : restored.map.keySet()) {
            assertThat(key).isInstanceOf(Integer.class);
        }
    }

    @Test
    void testEmptyMapStringified() {
        Map<Long, String> map = new LinkedHashMap<>();

        String json = JsonIo.toJson(map, STRINGIFY_ON);

        assertThat(json).doesNotContain("@keys");
        assertThat(json).contains("{}");
    }
}
