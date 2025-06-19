package com.cedarsoftware.io.writers;

import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharBufferWriterTest {

    @Test
    void testWriteArrayBackedBufferEscaping() throws Exception {
        char[] data = new char[]{'X', 'a', '"', '\\', '\b', '\f', '\n', '\r', '\t', 0x12, 'Y'};
        CharBuffer buffer = CharBuffer.wrap(data);
        buffer.position(1);
        buffer.limit(data.length - 1);
        StringWriter out = new StringWriter();

        new CharBufferWriter().write(buffer, false, out, null);

        assertEquals("\"value\":\"a\\\"\\\\\\b\\f\\n\\r\\t\\u0012\"", out.toString());
        assertEquals(1, buffer.position());
    }

    @Test
    void testWriteNonArrayBackedBufferRestoresPosition() throws Exception {
        ByteBuffer bytes = ByteBuffer.allocateDirect(8);
        CharBuffer buffer = bytes.asCharBuffer();
        buffer.put("abc");
        buffer.flip();
        buffer.position(1);
        StringWriter out = new StringWriter();

        new CharBufferWriter().write(buffer, false, out, null);

        assertEquals("\"value\":\"bc\"", out.toString());
        assertEquals(1, buffer.position());
    }
}
