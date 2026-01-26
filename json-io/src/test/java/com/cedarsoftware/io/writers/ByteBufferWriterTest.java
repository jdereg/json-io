package com.cedarsoftware.io.writers;

import java.nio.ByteBuffer;
import java.util.Base64;

import com.cedarsoftware.io.JsonIo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ByteBufferWriter using end-to-end JsonIo serialization.
 * These tests verify:
 * 1. Base64 encoding correctness
 * 2. ByteBuffer position/limit preservation (no side effects)
 * 3. Both array-backed and direct buffers work correctly
 */
class ByteBufferWriterTest {

    @Test
    void testArrayBackedBufferSerializationPreservesPosition() {
        // Setup: Create array-backed buffer with position/limit window
        byte[] data = {1, 2, 3, 4, 5};
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(1);  // Start at index 1
        buffer.limit(4);     // End at index 4 (exclusive)
        int originalPosition = buffer.position();

        // Expected: Only bytes [2, 3, 4] should be encoded (indices 1-3)
        String expectedBase64 = Base64.getEncoder().encodeToString(new byte[]{2, 3, 4});

        // Execute: Serialize through JsonIo
        String json = JsonIo.toJson(buffer, null);

        // Verify: JSON contains correct Base64 encoding
        assertTrue(json.contains("\"value\":\"" + expectedBase64 + "\""),
                   "JSON should contain Base64-encoded slice: " + json);

        // Verify: Buffer position was not modified (no side effects)
        assertEquals(originalPosition, buffer.position(),
                     "ByteBuffer position should be preserved after serialization");
    }

    @Test
    void testDirectBufferSerializationRestoresPosition() {
        // Setup: Create direct (non-heap) buffer with position/limit window
        ByteBuffer buffer = ByteBuffer.allocateDirect(5);
        buffer.put(new byte[]{10, 20, 30, 40, 50});
        buffer.position(1);  // Start at index 1
        buffer.limit(4);     // End at index 4 (exclusive)
        int originalPosition = buffer.position();

        // Expected: Only bytes [20, 30, 40] should be encoded (indices 1-3)
        String expectedBase64 = Base64.getEncoder().encodeToString(new byte[]{20, 30, 40});

        // Execute: Serialize through JsonIo
        String json = JsonIo.toJson(buffer, null);

        // Verify: JSON contains correct Base64 encoding
        assertTrue(json.contains("\"value\":\"" + expectedBase64 + "\""),
                   "JSON should contain Base64-encoded slice: " + json);

        // Verify: Buffer position was not modified (no side effects)
        assertEquals(originalPosition, buffer.position(),
                     "Direct ByteBuffer position should be preserved after serialization");
    }

    @Test
    void testFullBufferSerialization() {
        // Test the common case where the entire buffer is used
        byte[] data = {65, 66, 67};  // "ABC"
        ByteBuffer buffer = ByteBuffer.wrap(data);

        String expectedBase64 = Base64.getEncoder().encodeToString(data);
        String json = JsonIo.toJson(buffer, null);

        assertTrue(json.contains(expectedBase64),
                   "Full buffer should be Base64 encoded: " + json);
        assertEquals(0, buffer.position(),
                     "Position should still be at start");
    }

    @Test
    void testEmptyBufferSerialization() {
        // Test edge case: empty buffer
        ByteBuffer buffer = ByteBuffer.allocate(0);

        String json = JsonIo.toJson(buffer, null);

        // Empty buffer should encode to empty Base64 string
        assertTrue(json.contains("\"value\":\"\""),
                   "Empty buffer should produce empty Base64: " + json);
    }
}
