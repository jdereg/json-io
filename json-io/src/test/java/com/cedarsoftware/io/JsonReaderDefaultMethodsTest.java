package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
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
        ClassFactory factory = new ClassFactory() {};
        ReadOptions options = ReadOptionsBuilder.getDefaultReadOptions();
        ReferenceTracker references = new Resolver.DefaultReferenceTracker(options);
        Converter converter = new Converter(options.getConverterOptions());
        Resolver resolver = new ObjectResolver(options, references, converter);

        Object instance = factory.newInstance(Sample.class, null, resolver);

        assertTrue(instance instanceof Sample);
    }

    @Test
    void jsonClassReaderReadThrowsByDefault() {
        JsonClassReader classReader = new JsonClassReader() {};
        ReadOptions options = ReadOptionsBuilder.getDefaultReadOptions();
        ReferenceTracker references = new Resolver.DefaultReferenceTracker(options);
        Converter converter = new Converter(options.getConverterOptions());
        Resolver resolver = new ObjectResolver(options, references, converter);

        assertThrows(UnsupportedOperationException.class,
                () -> classReader.read(new JsonObject(), resolver));
    }
}
