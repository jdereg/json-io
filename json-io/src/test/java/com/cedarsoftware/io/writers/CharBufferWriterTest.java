package com.cedarsoftware.io.writers;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import com.cedarsoftware.io.JsonIo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CharBufferWriter using end-to-end JsonIo serialization.
 * These tests verify:
 * 1. JSON string escaping correctness (quotes, backslashes, control chars)
 * 2. CharBuffer position preservation (no side effects)
 * 3. Both array-backed and direct (non-array) buffers work correctly
 */
class CharBufferWriterTest {

    @Test
    void testArrayBackedBufferWithJsonEscaping() {
        // Setup: Create buffer with characters that need JSON escaping
        // X, a, ", \, backspace, formfeed, newline, carriage-return, tab, control-char(0x12), Y
        char[] data = new char[]{'X', 'a', '"', '\\', '\b', '\f', '\n', '\r', '\t', 0x12, 'Y'};
        CharBuffer buffer = CharBuffer.wrap(data);
        buffer.position(1);  // Skip 'X'
        buffer.limit(data.length - 1);  // Skip 'Y'
        int originalPosition = buffer.position();

        // Expected: Characters should be properly JSON-escaped
        // a, ", \, \b, \f, \n, \r, \t, \u0012
        String expectedEscaped = "a\\\"\\\\\\b\\f\\n\\r\\t\\u0012";

        // Execute: Serialize through JsonIo
        String json = JsonIo.toJson(buffer, null);

        // Verify: JSON contains properly escaped string
        assertTrue(json.contains("\"value\":\"" + expectedEscaped + "\""),
                   "JSON should contain properly escaped characters: " + json);

        // Verify: Buffer position was not modified (no side effects)
        assertEquals(originalPosition, buffer.position(),
                     "CharBuffer position should be preserved after serialization");
    }

    @Test
    void testDirectBufferRestoresPosition() {
        // Setup: Create direct (non-array-backed) CharBuffer
        ByteBuffer bytes = ByteBuffer.allocateDirect(16);
        CharBuffer buffer = bytes.asCharBuffer();
        buffer.put("abc");
        buffer.flip();  // Prepare for reading
        buffer.position(1);  // Skip 'a'
        int originalPosition = buffer.position();

        // Expected: Only "bc" should be serialized
        String json = JsonIo.toJson(buffer, null);

        // Verify: JSON contains correct substring
        assertTrue(json.contains("\"value\":\"bc\""),
                   "JSON should contain substring 'bc': " + json);

        // Verify: Buffer position was not modified (no side effects)
        assertEquals(originalPosition, buffer.position(),
                     "Direct CharBuffer position should be preserved after serialization");
    }

    @Test
    void testFullBufferSerialization() {
        // Test the common case where the entire buffer is used
        CharBuffer buffer = CharBuffer.wrap("Hello World");

        String json = JsonIo.toJson(buffer, null);

        assertTrue(json.contains("\"value\":\"Hello World\""),
                   "Full buffer should be serialized: " + json);
        assertEquals(0, buffer.position(),
                     "Position should still be at start");
    }

    @Test
    void testEmptyBufferSerialization() {
        // Test edge case: empty buffer
        CharBuffer buffer = CharBuffer.allocate(0);

        String json = JsonIo.toJson(buffer, null);

        assertTrue(json.contains("\"value\":\"\""),
                   "Empty buffer should produce empty string: " + json);
    }

    @Test
    void testSpecialCharactersEscaping() {
        // Test all special JSON escape sequences
        CharBuffer buffer = CharBuffer.wrap("\"\\\b\f\n\r\t");

        String json = JsonIo.toJson(buffer, null);

        // All special characters should be escaped
        assertTrue(json.contains("\\\""),  // quote
                   "Quote should be escaped");
        assertTrue(json.contains("\\\\"),  // backslash
                   "Backslash should be escaped");
        assertTrue(json.contains("\\b"),   // backspace
                   "Backspace should be escaped");
        assertTrue(json.contains("\\f"),   // formfeed
                   "Formfeed should be escaped");
        assertTrue(json.contains("\\n"),   // newline
                   "Newline should be escaped");
        assertTrue(json.contains("\\r"),   // carriage return
                   "Carriage return should be escaped");
        assertTrue(json.contains("\\t"),   // tab
                   "Tab should be escaped");
    }
}
