package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Map read into a non-Map type -- {"duration": ...} into a Duration -- converts its values by their own types. A
 * value json-io wrote with its type, {"@type":"BigDecimal","value":"123"}, reached the Converter as a Map whose value
 * was the text "123", so its type was lost: 123 seconds as a BigDecimal became 123 milliseconds as integer text.
 */
class NestedTypedValueTest {

    public static class Holder {
        public Duration duration;
        public Instant instant;
    }

    private static final String BIG_123 = "{\"@type\":\"BigDecimal\",\"value\":\"123\"}";

    @Test
    void aTypedValueKeepsItsTypeAtTheRoot() {
        assertEquals(Duration.ofSeconds(123),
                JsonIo.toJava("{\"duration\":" + BIG_123 + "}", null).asClass(Duration.class));
        assertEquals(Instant.ofEpochSecond(123),
                JsonIo.toJava("{\"instant\":" + BIG_123 + "}", null).asClass(Instant.class));
        // a BigInteger is nanoseconds to a Duration
        assertEquals(Duration.ofNanos(123), JsonIo.toJava(
                "{\"duration\":{\"@type\":\"BigInteger\",\"value\":\"123\"}}", null).asClass(Duration.class));
    }

    @Test
    void aTypedValueKeepsItsTypeInAField() {
        Holder holder = JsonIo.toJava("{\"duration\":{\"duration\":" + BIG_123 + "},\"instant\":{\"instant\":"
                + BIG_123 + "}}", null).asClass(Holder.class);
        assertEquals(Duration.ofSeconds(123), holder.duration);
        assertEquals(Instant.ofEpochSecond(123), holder.instant);
    }

    /** A Java Map holding a BigDecimal, written by json-io and read back as a Duration. */
    @Test
    void aMapRoundTripsIntoTheTypeItDescribes() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("duration", new BigDecimal("123"));
        assertEquals(Duration.ofSeconds(123), JsonIo.toJava(JsonIo.toJson(map, null), null).asClass(Duration.class));
        map.put("duration", new BigDecimal(Long.toString(Long.MIN_VALUE)));
        assertEquals(Duration.ofSeconds(Long.MIN_VALUE),
                JsonIo.toJava(JsonIo.toJson(map, null), null).asClass(Duration.class));
        map.put("duration", BigInteger.valueOf(1500));
        assertEquals(Duration.ofNanos(1500), JsonIo.toJava(JsonIo.toJson(map, null), null).asClass(Duration.class));
    }

    /** Values that were already right stay right: text, numbers and ISO strings. */
    @Test
    void untypedValuesReadAsBefore() {
        assertEquals(Duration.ofMillis(1500), JsonIo.toJava("{\"duration\":1500}", null).asClass(Duration.class));
        assertEquals(Duration.ofMinutes(2), JsonIo.toJava("{\"duration\":\"PT2M\"}", null).asClass(Duration.class));
        assertEquals(Duration.ofMillis(1500), JsonIo.toJava("{\"duration\":\"1.5\"}", null).asClass(Duration.class));
    }
}
