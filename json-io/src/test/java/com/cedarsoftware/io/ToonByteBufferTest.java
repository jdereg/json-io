package com.cedarsoftware.io;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON writes a ByteBuffer's bytes as Base64, as JSON does, and reads them back. It wrote them as text -- each byte a
 * character -- and read that text back as UTF-8, so every byte from 128 up came back as two (256 bytes as 512).
 * A JSON or TOON string read into a ByteBuffer is Base64 when padded correctly, as for a byte[] (Jackson writes a
 * ByteBuffer as Base64); any other string gives its own bytes.
 */
class ToonByteBufferTest {

    public static class Holder {
        public ByteBuffer buffer;
        public List<ByteBuffer> buffers;
    }

    private static final WriteOptions[] MODES = {
            new WriteOptionsBuilder().build(),
            new WriteOptionsBuilder().showTypeInfoMinimal().build(),
            new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
            new WriteOptionsBuilder().showTypeInfoAlways().build()};

    private static byte[] everyByte() {
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        return all;
    }

    private static byte[] bytesOf(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }

    @Test
    void everyByteValueRoundTripsInAFieldAndAList() {
        for (WriteOptions mode : MODES) {
            Holder holder = new Holder();
            holder.buffer = ByteBuffer.wrap(everyByte());
            holder.buffers = new ArrayList<>(Arrays.asList(ByteBuffer.wrap(new byte[] {(byte) 0xFF, 0, (byte) 0x80}),
                    ByteBuffer.wrap(new byte[0])));
            String toon = JsonIo.toToon(holder, mode);
            assertTrue(toon.contains(Base64.getEncoder().encodeToString(everyByte())), toon);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertArrayEquals(everyByte(), bytesOf(back.buffer));
            assertArrayEquals(new byte[] {(byte) 0xFF, 0, (byte) 0x80}, bytesOf(back.buffers.get(0)));
            assertEquals(0, back.buffers.get(1).remaining());
        }
    }

    @Test
    void aByteBufferAtTheRootRoundTrips() {
        String toon = JsonIo.toToon(ByteBuffer.wrap(everyByte()), new WriteOptionsBuilder().build());
        assertArrayEquals(everyByte(), bytesOf(JsonIo.fromToon(toon, null).asClass(ByteBuffer.class)));
    }

    /** Only the remaining bytes are written, from a heap or a direct buffer, and the buffer is not moved. */
    @Test
    void theRemainingBytesAreWrittenAndTheBufferIsNotMoved() {
        ByteBuffer heap = ByteBuffer.wrap(new byte[] {9, 1, 2, (byte) 200, 9});
        heap.position(1);
        heap.limit(4);
        ByteBuffer direct = ByteBuffer.allocateDirect(3);
        direct.put(new byte[] {1, 2, (byte) 200});
        direct.flip();
        for (ByteBuffer buffer : new ByteBuffer[] {heap, direct}) {
            Holder holder = new Holder();
            holder.buffer = buffer;
            int position = buffer.position();
            Holder back = JsonIo.fromToon(JsonIo.toToon(holder, new WriteOptionsBuilder().build()), null)
                    .asClass(Holder.class);
            assertArrayEquals(new byte[] {1, 2, (byte) 200}, bytesOf(back.buffer));
            assertEquals(position, buffer.position());
        }
    }

    /** A JSON string in a ByteBuffer field: Base64 when padded correctly, as Jackson writes it; else its bytes. */
    @Test
    void aJsonStringInAByteBufferFieldIsBase64WhenPaddedCorrectly() {
        Holder back = JsonIo.toJava("{\"buffer\":\"AQID\"}", null).asClass(Holder.class);
        assertArrayEquals(new byte[] {1, 2, 3}, bytesOf(back.buffer));
        back = JsonIo.toJava("{\"buffer\":\"AF\"}", null).asClass(Holder.class);
        assertArrayEquals("AF".getBytes(StandardCharsets.UTF_8), bytesOf(back.buffer));
        assertArrayEquals(new byte[] {1, 2, 3}, bytesOf(JsonIo.toJava("\"AQID\"", null).asClass(ByteBuffer.class)));
    }
}
