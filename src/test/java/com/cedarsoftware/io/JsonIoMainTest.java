package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link JsonIo#main(String[])} method.
 */
class JsonIoMainTest {
    @Test
    void verifyMainOutputsSupportedConversions() {
        JsonIo.main(new String[0]);
    }
}
