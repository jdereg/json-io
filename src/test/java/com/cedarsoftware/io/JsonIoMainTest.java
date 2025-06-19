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
        JsonIo.main(new String[0]);
    }
}
