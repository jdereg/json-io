package com.cedarsoftware.io;

import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for converting Map representations to {@link Timestamp}.
 */
class MapTimestampConversionTest {
    @Test
    void testIsoStringTimestampFails() {
        String json = "{\"@type\":\"java.sql.Timestamp\",\"timestamp\":\"2025-06-19T13:46:38.745Z\"}";
        assertThrows(IllegalArgumentException.class, () -> TestUtil.toObjects(json, Timestamp.class));
    }
}

