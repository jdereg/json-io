package com.cedarsoftware.io;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JSON string read into a byte[] is Base64, as Jackson writes and reads it: json-io decodes it itself, wherever the
 * byte[] sits. A string that is not Base64 gives its own UTF-8 bytes.
 */
class ByteArrayBase64ReadTest {

    public static class Holder {
        public byte[] data;
        public List<byte[]> list;
        public byte[][] matrix;
        public Map<String, byte[]> map;
        public Object any;
    }

    /**
     * Under showTypeInfoNever(), JsonGenerator.writeBinary writes a bare Base64 string, and the read relied on
     * java-util guessing the string's format: the Base64 of 3 bytes is 4 letters with no padding, which it took for
     * text, and Base64 that happened to be all hex digits it took for hex. 112 of 2,050 random arrays came back
     * different. Every length from 0 to 40 bytes must now come back as written.
     */
    @Test
    void jacksonModeRoundTripsEveryLength() throws Exception {
        WriteOptions never = new WriteOptionsBuilder().showTypeInfoNever().build();
        Random random = new Random(7);
        List<String> wrong = new ArrayList<>();
        for (int length = 0; length <= 40; length++) {
            for (int k = 0; k < 25; k++) {
                byte[] payload = new byte[length];
                random.nextBytes(payload);
                StringWriter out = new StringWriter();
                try (JsonGenerator gen = JsonIo.createGenerator(out, never)) {
                    gen.writeStartObject().writeFieldName("data");
                    gen.writeBinary(payload);
                    gen.writeEndObject();
                }
                Holder back = JsonIo.toJava(out.toString(), null).asClass(Holder.class);
                if (!java.util.Arrays.equals(payload, back.data)) {
                    wrong.add(out + " -> " + java.util.Arrays.toString(back.data));
                }
            }
        }
        assertTrue(wrong.isEmpty(), wrong.size() + " wrong, e.g. " + wrong.subList(0, Math.min(3, wrong.size())));
    }

    /** "AQID" is the Base64 of {1, 2, 3}: at the root, in a field, a List, an array of arrays and a Map. */
    @Test
    void bareBase64ReadsInEveryPosition() {
        byte[] expected = {1, 2, 3};
        assertArrayEquals(expected, JsonIo.toJava("\"AQID\"", null).asClass(byte[].class));

        String json = "{\"data\":\"AQID\",\"list\":[\"AQID\",\"BAUG\"],\"matrix\":[\"AQID\"],\"map\":{\"k\":\"AQID\"}}";
        Holder holder = JsonIo.toJava(json, null).asClass(Holder.class);
        assertArrayEquals(expected, holder.data);
        assertEquals(2, holder.list.size());
        assertArrayEquals(expected, holder.list.get(0));
        assertArrayEquals(new byte[] {4, 5, 6}, holder.list.get(1));
        assertEquals(1, holder.matrix.length);
        assertArrayEquals(expected, holder.matrix[0]);
        assertArrayEquals(expected, holder.map.get("k"));
    }

    /** URL-safe Base64 (JWT segments) reads too: "-_8" is {0xFB, 0xFF}. */
    @Test
    void urlSafeBase64Reads() {
        Holder holder = JsonIo.toJava("{\"data\":\"-_8\"}", null).asClass(Holder.class);
        assertArrayEquals(new byte[] {(byte) 0xFB, (byte) 0xFF}, holder.data);
    }

    /** A string that is not Base64 (a space, or a length no Base64 has) gives its own UTF-8 bytes. */
    @Test
    void aStringThatIsNotBase64GivesItsBytes() {
        for (String text : new String[] {"hello world", "00 01 02 FF", "abcde", "naïve"}) {
            String json = "{\"data\":\"" + text + "\"}";
            assertArrayEquals(text.getBytes(StandardCharsets.UTF_8),
                    JsonIo.toJava(json, null).asClass(Holder.class).data, text);
        }
    }
}
