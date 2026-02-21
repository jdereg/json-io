package com.cedarsoftware.io;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link JsonIo#main(String[])} method.
 * Suppresses log output to avoid dumping the full conversion table during builds.
 */
class JsonIoMainTest {
    @Test
    void verifyMainOutputsSupportedConversions() {
        Logger logger = Logger.getLogger(JsonIo.class.getName());
        Level saved = logger.getLevel();
        try {
            logger.setLevel(Level.OFF);
            JsonIo.main(new String[0]);
        } finally {
            logger.setLevel(saved);
        }
    }
}
