package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for JsonReader interface default methods.
 */
public class JsonReaderDefaultMethodsTest {

    static class Sample {}

    @Test
    void classFactoryDefaultNewInstanceReturnsObject() {
        JsonReader.ClassFactory factory = new JsonReader.ClassFactory() {};
        JsonReader reader = new JsonReader(ReadOptionsBuilder.getDefaultReadOptions());

        Object instance = factory.newInstance(Sample.class, null, reader.getResolver());

        assertTrue(instance instanceof Sample);
    }

    @Test
    void jsonClassReaderReadThrowsByDefault() {
        JsonReader.JsonClassReader classReader = new JsonReader.JsonClassReader() {};
        JsonReader reader = new JsonReader(ReadOptionsBuilder.getDefaultReadOptions());

        assertThrows(UnsupportedOperationException.class,
                () -> classReader.read(new JsonObject(), reader.getResolver()));
    }
}
