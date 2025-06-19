package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link JsonIo#main(String[])} method.
 */
class JsonIoMainTest {
    @Test
    void verifyMainOutputsSupportedConversions() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            JsonIo.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }
        String output = baos.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("json-io supported conversions"));
        assertTrue(output.contains("java.lang.String"));
        assertTrue(output.contains("java.lang.Integer"));
    }
}
