package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With {@code stringifyMapKeys(true)} -- on in {@code standardJson()} and {@code json5()} -- a map whose keys all
 * have a text form is written {@code {"7":...}}. A key that is itself a map has one only when it wraps one value;
 * any other map key sends its map to {@code @keys}/{@code @items}, as with the option off (4.115.0).
 * <p>
 * Through 4.114.0 the write threw, "To convert from Map to 'String' the map must include: [value] or [_v] as key".
 * Converter lists Map to String for every map, and reads a value only from a map that wraps one.
 */
class MapKeyThatIsAMapTest {

    private static final WriteOptions STRINGIFY =
            new WriteOptionsBuilder().showTypeInfoNever().stringifyMapKeys(true).build();
    private static final WriteOptions PLAIN = new WriteOptionsBuilder().showTypeInfoNever().build();

    public static class Holder {
        public Map<Map<String, Object>, String> byMap = new LinkedHashMap<>();
        public Map<Long, String> byLong = new LinkedHashMap<>();
    }

    /** A key type that is a plain class is the declared key type: here Map itself. */
    @SuppressWarnings("rawtypes")
    public static class RawMapKeys {
        public Map<Map, String> byMap = new LinkedHashMap<>();
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    void aMapWithAMapKeyIsWrittenAsKeysAndItems() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(mapOf("x", 1L), "a map as a key");
        map.put(7L, "seven");

        String json = JsonIo.toJson(map, STRINGIFY);
        assertThat(json).isEqualTo("{\"@keys\":[{\"x\":1},7],\"@items\":[\"a map as a key\",\"seven\"]}");
        assertThat(json).isEqualTo(JsonIo.toJson(map, PLAIN));

        // Every kind of map as a key, json-io's own JsonObject among them
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("x", 1L);
        Map<String, Object> sorted = new TreeMap<>(mapOf("x", 1L));
        for (Object key : new Object[]{jsonObject, sorted}) {
            Map<Object, Object> byKey = new LinkedHashMap<>();
            byKey.put(key, "v");
            assertThat(JsonIo.toJson(byKey, STRINGIFY)).as(key.getClass().getSimpleName())
                    .isEqualTo("{\"@keys\":[{\"x\":1}],\"@items\":[\"v\"]}");
        }
    }

    @Test
    void theMapKeyComesBack() {
        WriteOptions stringify = new WriteOptionsBuilder().stringifyMapKeys(true).build();
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(mapOf("x", 1L), "a map as a key");
        map.put(7L, "seven");

        Map<?, ?> back = JsonIo.toJava(JsonIo.toJson(map, stringify), null).asClass(Map.class);
        assertThat(back).isEqualTo(map);
        assertThat(back.keySet().iterator().next()).isEqualTo(mapOf("x", 1L));
    }

    @Test
    void aMapKeyThatWrapsOneValueIsStringifiedAsBefore() {
        // Read in Maps mode, a BigDecimal key {"@type":"BigDecimal","value":"81598.5"} is the BigDecimal, as a field's
        // value is (it was the JsonObject holding it): its text is the BigDecimal's, as it was through 4.114.0
        String json = "{\"@type\":\"LinkedHashMap\",\"@keys\":[{\"@type\":\"BigDecimal\",\"value\":\"81598.5\"},7],"
                + "\"@items\":[\"x\",\"seven\"]}";
        Object maps = JsonIo.toMaps(json, null).asClass(null);
        assertThat(((Map<?, ?>) maps).keySet().iterator().next()).isEqualTo(new java.math.BigDecimal("81598.5"));
        assertThat(JsonIo.toJson(maps, STRINGIFY)).isEqualTo("{\"81598.5\":\"x\",\"7\":\"seven\"}");

        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(mapOf("value", 5L), "five");
        assertThat(JsonIo.toJson(map, STRINGIFY)).isEqualTo("{\"5\":\"five\"}");
    }

    @Test
    void theDeclaredKeyTypeIsAMapOrTheKeyGotInThroughARawReference() {
        Holder holder = new Holder();
        holder.byMap.put(mapOf("x", 1L), "by map");
        holder.byLong.put(1L, "one");
        assertThat(JsonIo.toJson(holder, STRINGIFY))
                .isEqualTo("{\"byMap\":{\"@keys\":[{\"x\":1}],\"@items\":[\"by map\"]},\"byLong\":{\"1\":\"one\"}}");

        RawMapKeys rawMapKeys = new RawMapKeys();
        rawMapKeys.byMap.put(mapOf("x", 1L), "by map");
        assertThat(JsonIo.toJson(rawMapKeys, STRINGIFY))
                .isEqualTo("{\"byMap\":{\"@keys\":[{\"x\":1}],\"@items\":[\"by map\"]}}");

        // Map<Long, String> holding a map key that came in through a raw reference
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<Object, String> raw = (Map) holder.byLong;
        raw.put(mapOf("x", 2L), "smuggled");
        assertThat(JsonIo.toJson(holder, STRINGIFY)).endsWith(
                "\"byLong\":{\"@keys\":[1,{\"x\":2}],\"@items\":[\"one\",\"smuggled\"]}}");
    }

    @Test
    void thePresetsThatStringifyWriteIt() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(mapOf("x", 1L), "a map as a key");
        assertThat(JsonIo.toJson(map, new WriteOptionsBuilder().standardJson().build()))
                .isEqualTo("{\"$keys\":[{\"x\":1}],\"$items\":[\"a map as a key\"]}");
        assertThat(JsonIo.toJson(map, new WriteOptionsBuilder().json5().build()))
                .isEqualTo("{$keys:[{x:1}],$items:[\"a map as a key\"]}");
    }

    @Test
    void keysWithATextFormAreStringifiedAsBefore() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(1L, "one");
        map.put("two", 2L);
        assertThat(JsonIo.toJson(map, STRINGIFY)).isEqualTo("{\"1\":\"one\",\"two\":2}");

        // A list key has no conversion to String: @keys/@items, before and after
        Map<Object, Object> byList = new LinkedHashMap<>();
        byList.put(java.util.Arrays.asList(1L, 2L), "list");
        assertThat(JsonIo.toJson(byList, STRINGIFY)).isEqualTo("{\"@keys\":[[1,2]],\"@items\":[\"list\"]}");

        // A class is asked about once per run of keys of it: a key of another class after a run is asked about
        Map<Object, Object> run = new LinkedHashMap<>();
        run.put(1L, "one");
        run.put(2L, "two");
        run.put(java.util.Arrays.asList(3L), "list");
        assertThat(JsonIo.toJson(run, STRINGIFY))
                .isEqualTo("{\"@keys\":[1,2,[3]],\"@items\":[\"one\",\"two\",\"list\"]}");
    }

    @Test
    void everyMapKeyIsAskedAboutNotOnlyTheFirstOfItsClass() {
        // Both keys are LinkedHashMaps; the first wraps one value, the second does not
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put(mapOf("value", 5L), "five");
        map.put(mapOf("x", 1L), "a map as a key");
        assertThat(JsonIo.toJson(map, STRINGIFY))
                .isEqualTo("{\"@keys\":[{\"value\":5},{\"x\":1}],\"@items\":[\"five\",\"a map as a key\"]}");

        // The same under a declared key type: Map itself, and Long with the two map keys come in through a raw reference
        RawMapKeys rawMapKeys = new RawMapKeys();
        rawMapKeys.byMap.put(mapOf("value", 5L), "five");
        rawMapKeys.byMap.put(mapOf("x", 1L), "by map");
        assertThat(JsonIo.toJson(rawMapKeys, STRINGIFY))
                .isEqualTo("{\"byMap\":{\"@keys\":[{\"value\":5},{\"x\":1}],\"@items\":[\"five\",\"by map\"]}}");

        Holder holder = new Holder();
        holder.byLong.put(1L, "one");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<Object, String> raw = (Map) holder.byLong;
        raw.put(mapOf("value", 5L), "five");
        raw.put(mapOf("x", 2L), "smuggled");
        assertThat(JsonIo.toJson(holder, STRINGIFY)).endsWith(
                "\"byLong\":{\"@keys\":[1,{\"value\":5},{\"x\":2}],\"@items\":[\"one\",\"five\",\"smuggled\"]}}");
    }
}
