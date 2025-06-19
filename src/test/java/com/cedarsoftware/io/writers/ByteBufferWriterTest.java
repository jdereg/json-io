package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ByteBufferWriterTest {

    @Test
    void writesArrayBackedBuffer() throws IOException {
        byte[] data = {1, 2, 3, 4, 5};
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(1);
        buffer.limit(4);
        int originalPosition = buffer.position();

        StringWriter out = new StringWriter();
        new ByteBufferWriter().write(buffer, false, out, null);

        String expected = "\"value\":\"" + Base64.getEncoder().encodeToString(new byte[]{2, 3, 4}) + "\"";
        assertEquals(expected, out.toString());
        assertEquals(originalPosition, buffer.position());
    }

    @Test
    void writesDirectBufferAndRestoresPosition() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(5);
        buffer.put(new byte[]{10, 20, 30, 40, 50});
        buffer.position(1);
        buffer.limit(4);
        int originalPosition = buffer.position();

        StringWriter out = new StringWriter();
        new ByteBufferWriter().write(buffer, true, out, null);

        String expected = "\"value\":\"" + Base64.getEncoder().encodeToString(new byte[]{20, 30, 40}) + "\"";
        assertEquals(expected, out.toString());
        assertEquals(originalPosition, buffer.position());
    }
}
