package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@code byte[]} across json-io's read and write paths plus
 * the new wrapped form emitted by {@link JsonGenerator#writeBinary(byte[])}.
 *
 * <p>Together with the format-detection tests in java-util's
 * {@code ConverterEverythingTest}, these verify that:
 * <ul>
 *   <li>{@code JsonGenerator.writeBinary(byte[])} emits the wrapped form
 *       {@code {"@type":"byte[]","value":"<base64>"}} and json-io's reader
 *       round-trips it back to the original byte array.</li>
 *   <li>The existing {@code [1, 2, 3]} number-array form (the default tree-writer
 *       output for {@code byte[]}) continues to round-trip.</li>
 *   <li>Foreign JSON inputs containing bare Base64 / hex / spaced-hex strings
 *       in {@code byte[]} fields are correctly decoded via java-util's smart
 *       {@code String → byte[]} detection in 4.103.0.</li>
 * </ul>
 */
class ByteArrayRoundTripTest {

    public static class BlobHolder {
        public String name;
        public byte[] data;
    }

    // -------------------------------------------------------------------
    // JsonGenerator.writeBinary → JsonIo.toJava round-trip
    // -------------------------------------------------------------------

    @Test
    void writeBinary_roundTripsViaWrappedForm() throws IOException {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeStringField("name", "alice")
                .writeFieldName("data");
            g.writeBinary(payload);
            g.writeEndObject();
        }
        String json = sw.toString();

        // Sanity: the wrapped form is present in the output
        assertTrue(json.contains("\"@type\":\"byte[]\""),
                "expected @type tag, got: " + json);
        assertTrue(json.contains("\"value\":\""),
                "expected base64 value field, got: " + json);

        // Round-trip: parse back into BlobHolder, verify byte[] matches
        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertEquals("alice", round.name);
        assertNotNull(round.data);
        assertArrayEquals(payload, round.data);
    }

    @Test
    void writeBinary_nullEmitsJsonNull() throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeFieldName("data");
            g.writeBinary((byte[]) null);
            g.writeEndObject();
        }
        String json = sw.toString();
        assertEquals("{\"data\":null}", json);

        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertNull(round.data);
    }

    @Test
    void writeBinary_smallBlob_threeBytes() throws IOException {
        // 3 bytes → 4-char unpadded Base64 → the "AQL9" round-trip case that motivated
        // the try-base64-first fix in java-util's MapConversions.toByteBuffer/toByteArray.
        byte[] payload = new byte[]{1, 2, -3};
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeStringField("name", "x")
                .writeFieldName("data");
            g.writeBinary(payload);
            g.writeEndObject();
        }
        BlobHolder round = JsonIo.toJava(sw.toString(), null).asClass(BlobHolder.class);
        assertArrayEquals(payload, round.data);
    }

    @Test
    void writeBinary_largeBlob() throws IOException {
        byte[] payload = new byte[1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeStringField("name", "kb")
                .writeFieldName("data");
            g.writeBinary(payload);
            g.writeEndObject();
        }
        BlobHolder round = JsonIo.toJava(sw.toString(), null).asClass(BlobHolder.class);
        assertArrayEquals(payload, round.data);
    }

    @Test
    void writeBinary_sliceOverload() throws IOException {
        byte[] full = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        byte[] expected = Arrays.copyOfRange(full, 7, 12); // "World"
        StringWriter sw = new StringWriter();
        try (JsonGenerator g = JsonIo.createGenerator(sw)) {
            g.writeStartObject()
                .writeStringField("name", "slice")
                .writeFieldName("data");
            g.writeBinary(full, 7, 5);
            g.writeEndObject();
        }
        BlobHolder round = JsonIo.toJava(sw.toString(), null).asClass(BlobHolder.class);
        assertArrayEquals(expected, round.data);
    }

    @Test
    void writeBinary_outputStreamFactory_utf8Encoded() throws IOException {
        byte[] payload = new byte[]{0x42, 0x43, 0x44, 0x45};
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JsonGenerator g = JsonIo.createGenerator(bos)) {
            g.writeBinary(payload);
        }
        String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        // Top-level binary value emits the wrapped Map at root
        assertTrue(json.startsWith("{") && json.contains("@type"),
                "expected wrapped form, got: " + json);
    }

    // -------------------------------------------------------------------
    // Existing number-array form ([1, 2, 3]) still round-trips
    // -------------------------------------------------------------------

    @Test
    void treeWriter_numberArrayForm_stillRoundTrips() {
        BlobHolder h = new BlobHolder();
        h.name = "tree";
        h.data = new byte[]{10, 20, 30};

        WriteOptions wOpts = new WriteOptionsBuilder().standardJson().build();
        String json = JsonIo.toJson(h, wOpts);
        // Tree writer emits byte[] as JSON number array
        assertTrue(json.contains("\"data\":[10,20,30]") || json.contains("\"data\": [10, 20, 30]"),
                "expected number-array form for byte[], got: " + json);

        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertArrayEquals(h.data, round.data);
    }

    // -------------------------------------------------------------------
    // Foreign-format inputs (Jackson-style bare base64, hex, spaced-hex) decode
    // via java-util smart String → byte[] in 4.103.0
    // -------------------------------------------------------------------

    @Test
    void foreignInput_bareBase64String_decodesIntoByteArrayField() {
        // Jackson's writeBinary emits a bare base64 string. With java-util 4.103.0's smart
        // String → byte[], json-io's resolver routes through Converter and decodes correctly.
        // Use padded form so the tight rule fires.
        String json = "{\"name\":\"jackson-emitter\",\"data\":\"SGVsbG8sIFdvcmxkIQ==\"}";
        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertEquals("jackson-emitter", round.name);
        assertArrayEquals("Hello, World!".getBytes(StandardCharsets.UTF_8), round.data);
    }

    @Test
    void foreignInput_unspacedHexMagicNumber_decodes() {
        // Hex string of length 8 — passes the hex-tight rule.
        String json = "{\"name\":\"magic\",\"data\":\"CAFEBABE\"}";
        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertArrayEquals(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}, round.data);
    }

    @Test
    void foreignInput_spacedHexBytes_decode() {
        String json = "{\"name\":\"hexdump\",\"data\":\"00 01 02 FF\"}";
        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 0xFF}, round.data);
    }

    @Test
    void foreignInput_shortTextString_fallsBackToCharset() {
        // "DATA" fails all tight rules → charset fallback → UTF-8 bytes of "DATA".
        // Preserves the historical behavior for short text on byte[] fields.
        String json = "{\"name\":\"short\",\"data\":\"DATA\"}";
        BlobHolder round = JsonIo.toJava(json, null).asClass(BlobHolder.class);
        assertArrayEquals("DATA".getBytes(StandardCharsets.UTF_8), round.data);
    }

    // -------------------------------------------------------------------
    // ByteBuffer continues to round-trip via its existing wrapped-form writer
    // -------------------------------------------------------------------

    public static class BufferHolder {
        public String name;
        public ByteBuffer buffer;
    }

    // -------------------------------------------------------------------
    // Shared-reference preservation across the wrapped form
    // -------------------------------------------------------------------

    public static class TwoBlobs {
        public byte[] one;
        public byte[] two;
    }

    @Test
    void sharedByteArray_handWrittenWrappedFormWithIdAndRef_preservesIdentity() {
        // Hand-written JSON: two fields point to the same byte[], expressed via
        // {@id:N,@type:byte[],value:<base64>} on the first and {@ref:N} on the second.
        // The Converter dispatch in ObjectResolver decodes the wrapped form AND registers
        // the result as the JsonObject's target, so the subsequent @ref resolves to the
        // same instance — identity preserved across the wrapped-form shape.
        String json = "{\"@type\":\"com.cedarsoftware.io.ByteArrayRoundTripTest$TwoBlobs\","
                + "\"one\":{\"@id\":1,\"@type\":\"byte[]\",\"value\":\"AQIDBA==\"},"
                + "\"two\":{\"@ref\":1}}";
        TwoBlobs round = JsonIo.toJava(json, null).asClass(TwoBlobs.class);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, round.one);
        assertTrue(round.one == round.two, "@id/@ref should preserve identity across the wrapped form");
    }

    @Test
    void byteBufferField_roundTripsViaTreeWriter() {
        BufferHolder h = new BufferHolder();
        h.name = "bb";
        h.buffer = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});
        String json = JsonIo.toJson(h, new WriteOptionsBuilder().standardJson().build());
        BufferHolder round = JsonIo.toJava(json, null).asClass(BufferHolder.class);
        assertNotNull(round.buffer);
        byte[] originalBytes = new byte[h.buffer.remaining()];
        h.buffer.duplicate().get(originalBytes);
        byte[] roundBytes = new byte[round.buffer.remaining()];
        round.buffer.duplicate().get(roundBytes);
        assertArrayEquals(originalBytes, roundBytes);
    }
}
