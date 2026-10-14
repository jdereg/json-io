package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@link JsonObject} is a {@code Map<Object, Object>}, and its keys are written as an object's field names only
 * when every one is a String. One with another key is written as the map it is: {@code @keys}/{@code @items}, or
 * {@code {"1":...}} under {@code stringifyMapKeys}, as a {@code LinkedHashMap} with the same entries is (4.115.0).
 * <p>
 * Through 4.114.0 such a JsonObject was written as an object, and the write threw a ClassCastException at its first
 * key that was not a String ("Field name must not be null" for a null key). It took no hand-built object: the root
 * of a typeless {@code @keys}/{@code @items} document read with {@code toMaps()} is one, so json-io could not write
 * back what it had just read.
 */
class JsonObjectWithOtherKeysTest {

    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();
    private static final WriteOptions DEFAULT = new WriteOptionsBuilder().build();

    private static JsonObject longKeys() {
        JsonObject jObj = new JsonObject();
        jObj.put(1L, "one");
        jObj.put(2L, "two");
        return jObj;
    }

    private static Map<Object, Object> sameEntries(JsonObject jObj) {
        return new LinkedHashMap<>(jObj);
    }

    @Test
    void aJsonObjectWithLongKeysIsWrittenAsAMap() {
        JsonObject jObj = longKeys();
        assertThat(JsonIo.toJson(jObj, NEVER)).isEqualTo("{\"@keys\":[1,2],\"@items\":[\"one\",\"two\"]}");
        assertThat(JsonIo.toJson(jObj, DEFAULT)).isEqualTo("{\"@keys\":[1,2],\"@items\":[\"one\",\"two\"]}");
        assertThat(JsonIo.toJson(jObj, NEVER)).isEqualTo(JsonIo.toJson(sameEntries(jObj), NEVER));

        WriteOptions stringify = new WriteOptionsBuilder().showTypeInfoNever().stringifyMapKeys(true).build();
        assertThat(JsonIo.toJson(jObj, stringify)).isEqualTo("{\"1\":\"one\",\"2\":\"two\"}");
        WriteOptions twoLists = new WriteOptionsBuilder().showTypeInfoNever().stringifyMapKeys(true)
                .forceMapOutputAsTwoArrays(true).build();
        assertThat(JsonIo.toJson(jObj, twoLists)).isEqualTo("{\"@keys\":[1,2],\"@items\":[\"one\",\"two\"]}");
        WriteOptions pretty = new WriteOptionsBuilder().showTypeInfoNever().prettyPrint(true).build();
        assertThat(JsonIo.toJson(jObj, pretty)).isEqualTo(JsonIo.toJson(sameEntries(jObj), pretty));

        Map<?, ?> back = JsonIo.toJava(JsonIo.toJson(jObj, DEFAULT), null).asClass(Map.class);
        assertThat(back).isEqualTo(sameEntries(jObj));
    }

    @Test
    void aTypelessKeysAndItemsDocumentReadAsMapsIsWrittenBackAsItWas() {
        String json = "{\"@keys\":[1,2],\"@items\":[\"a\",\"b\"]}";
        Object maps = JsonIo.toMaps(json, null).asClass(null);
        assertThat(JsonIo.toJson(maps, NEVER)).isEqualTo(json);
        assertThat(JsonIo.toJson(maps, DEFAULT)).isEqualTo(json);

        String keyed = "{\"@keys\":[[1,2],{\"x\":3}],\"@items\":[\"by list\",\"by map\"]}";
        assertThat(JsonIo.toJson(JsonIo.toMaps(keyed, null).asClass(null), NEVER)).isEqualTo(keyed);
    }

    @Test
    void aKeyThatIsNotAStringAddedAfterReading() {
        JsonObject read = JsonIo.toMaps("{\"a\":1}", null).asClass(JsonObject.class);
        read.put(5L, "five");
        String json = JsonIo.toJson(read, NEVER);
        assertThat(json).isEqualTo("{\"@keys\":[\"a\",5],\"@items\":[1,\"five\"]}");

        Map<?, ?> back = JsonIo.toJava(json, null).asClass(Map.class);
        assertThat(back.get("a")).isEqualTo(1L);
        assertThat(back.get(5L)).isEqualTo("five");
    }

    @Test
    void aNullKey() {
        JsonObject jObj = new JsonObject();
        jObj.put("a", 1L);
        jObj.put(null, "under the null key");
        assertThat(JsonIo.toJson(jObj, NEVER)).isEqualTo("{\"@keys\":[\"a\",null],\"@items\":[1,\"under the null key\"]}");
        WriteOptions stringify = new WriteOptionsBuilder().showTypeInfoNever().stringifyMapKeys(true).build();
        assertThat(JsonIo.toJson(jObj, stringify))      // a null key is never stringified, as for any map
                .isEqualTo("{\"@keys\":[\"a\",null],\"@items\":[1,\"under the null key\"]}");

        // The key that is not a String comes first, the Strings after it
        JsonObject first = new JsonObject();
        first.put(null, "under the null key");
        first.put("a", 1L);
        first.put("b", 2L);
        assertThat(JsonIo.toJson(first, NEVER))
                .isEqualTo("{\"@keys\":[null,\"a\",\"b\"],\"@items\":[\"under the null key\",1,2]}");
    }

    @Test
    void insideAnotherDocumentAndReferencedTwice() {
        JsonObject jObj = longKeys();
        List<Object> list = new ArrayList<>();
        list.add(jObj);
        list.add(jObj);
        assertThat(JsonIo.toJson(list, NEVER))
                .isEqualTo("[{\"@id\":1,\"@keys\":[1,2],\"@items\":[\"one\",\"two\"]},{\"@ref\":1}]");

        JsonObject outer = new JsonObject();
        outer.put("inner", longKeys());
        assertThat(JsonIo.toJson(outer, NEVER)).isEqualTo("{\"inner\":{\"@keys\":[1,2],\"@items\":[\"one\",\"two\"]}}");
    }

    @Test
    void aJsonObjectWithStringKeysIsWrittenAsBefore() {
        JsonObject jObj = new JsonObject();
        jObj.put("a", 1L);
        jObj.put("b", "x");
        assertThat(JsonIo.toJson(jObj, NEVER)).isEqualTo("{\"a\":1,\"b\":\"x\"}");
        assertThat(JsonIo.toJson(new JsonObject(), NEVER)).isEqualTo("{}");

        // A document with an @type, read in Maps mode and written back
        String typed = "{\"@type\":\"com.cedarsoftware.io.JsonObjectWithOtherKeysTest$Point\",\"x\":1,\"y\":2}";
        assertThat(JsonIo.toJson(JsonIo.toMaps(typed, null).asClass(null), DEFAULT)).isEqualTo(typed);
    }

    public static class Point {
        public long x;
        public long y;
    }
}
