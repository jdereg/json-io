package com.cedarsoftware.io;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Charset's runtime class is a JDK-internal subclass (sun.nio.cs.UTF_8, ...). json-io wrote it as an
 * ordinary object -- {"@type":"sun.nio.cs.UTF_8","name":null,...} -- which JDK 16+ cannot instantiate,
 * so a Charset in an Object field, a collection, or at the top level could be written but not read back.
 */
class CharsetRoundTripTest {

    public static class Holder {
        public Object anyCharset;
        public Charset typedCharset;
    }

    @Test
    void charsetInObjectAndTypedFields() {
        Holder h = new Holder();
        h.anyCharset = StandardCharsets.UTF_8;
        h.typedCharset = StandardCharsets.ISO_8859_1;
        String json = JsonIo.toJson(h, null);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(StandardCharsets.UTF_8, back.anyCharset, json);
        assertEquals(StandardCharsets.ISO_8859_1, back.typedCharset, json);
    }

    @Test
    void charsetsInACollection() {
        List<Object> list = new ArrayList<>();
        list.add(StandardCharsets.US_ASCII);
        list.add(StandardCharsets.UTF_16);
        list.add(Charset.forName("windows-1252"));
        String json = JsonIo.toJson(list, null);
        List<?> back = JsonIo.toJava(json, null).asClass(List.class);
        assertEquals(list, back, json);
    }

    @Test
    void writesThePublicTypeAndCanonicalName() {
        String json = JsonIo.toJson(StandardCharsets.UTF_8, null);
        assertEquals("{\"@type\":\"Charset\",\"value\":\"UTF-8\"}", json);
    }

    @Test
    void charsetFieldThroughToon() {
        Holder h = new Holder();
        h.typedCharset = StandardCharsets.UTF_16;
        String toon = JsonIo.toToon(h, null);
        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
        assertEquals(StandardCharsets.UTF_16, back.typedCharset, toon);
    }

    @Test
    void topLevelCharset() {
        String json = JsonIo.toJson(StandardCharsets.UTF_8, null);
        assertEquals(StandardCharsets.UTF_8, JsonIo.toJava(json, null).asClass(Charset.class), json);
        assertEquals(StandardCharsets.UTF_8, JsonIo.toJava(json, null).asClass(Object.class), json);
    }
}
