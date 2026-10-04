package com.cedarsoftware.io;

import java.util.Base64;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * json-io's wrapped form of a byte[], {"@type":"byte[]","value":"&lt;base64&gt;"}, as JsonGenerator.writeBinary writes
 * it, reads back wherever it sits: at the root, in a byte[] field and in an Object field.
 */
class ByteArrayWrappedFormReadTest {

    public static class Holder {
        public byte[] data;
        public Object any;
    }

    /**
     * json-io's wrapped form read back as null at the root and in an Object field; only a byte[] field read it. Its
     * Javadoc on JsonGenerator.writeBinary says it round-trips through JsonIo.toJava.
     */
    @Test
    void theWrappedFormReadsAtTheRootAndInAnObjectField() {
        String wrapped = "{\"@type\":\"byte[]\",\"value\":\"AQID\"}";
        assertArrayEquals(new byte[] {1, 2, 3}, JsonIo.toJava(wrapped, null).asClass(byte[].class));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) JsonIo.toJava(wrapped, null).asClass(null));

        Holder holder = JsonIo.toJava("{\"any\":" + wrapped + "}", null).asClass(Holder.class);
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) holder.any);
    }

    /** The Base64 json-io itself writes for byte[] (wrapped) still reads, padded or not. */
    @Test
    void theWrappedFormReadsEveryLength() {
        Random random = new Random(11);
        for (int length = 0; length <= 20; length++) {
            byte[] payload = new byte[length];
            random.nextBytes(payload);
            String json = "{\"data\":{\"@type\":\"byte[]\",\"value\":\""
                    + Base64.getEncoder().encodeToString(payload) + "\"}}";
            assertArrayEquals(payload, JsonIo.toJava(json, null).asClass(Holder.class).data, "length " + length);
        }
    }
}
