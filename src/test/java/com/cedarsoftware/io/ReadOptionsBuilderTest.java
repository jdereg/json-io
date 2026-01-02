package com.cedarsoftware.io;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReadOptionsBuilder} configuration methods.
 */
class ReadOptionsBuilderTest {

    static class PermanentReadClass {}
    static class NonRefClass {}

    static class DummyReader implements JsonClassReader {
        @Override
        public Object read(Object jsonObj, Resolver resolver) {
            return new PermanentReadClass();
        }
    }

    static class DummyFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            return new PermanentReadClass();
        }
    }

    @Test
    void testAddPermanentReader() {
        ReadOptions optionsBefore = new ReadOptionsBuilder().build();
        assertFalse(optionsBefore.isCustomReaderClass(PermanentReadClass.class));

        JsonClassReader reader = new DummyReader();
        ReadOptionsBuilder.addPermanentReader(PermanentReadClass.class, reader);
        ReadOptions optionsAfter = new ReadOptionsBuilder().build();

        assertTrue(optionsAfter.isCustomReaderClass(PermanentReadClass.class));
        assertSame(reader, optionsAfter.getCustomReader(PermanentReadClass.class));
    }

    @Test
    void testAddNotCustomReaderClass() {
        ReadOptions options = new ReadOptionsBuilder()
                .addNotCustomReaderClass(NonRefClass.class)
                .build();
        assertTrue(options.isNotCustomReaderClass(NonRefClass.class));
    }

    @Test
    void testReplaceClassFactories() {
        ClassFactory factory = new DummyFactory();
        Map<Class<?>, ClassFactory> map = new HashMap<>();
        map.put(PermanentReadClass.class, factory);
        ReadOptions options = new ReadOptionsBuilder()
                .replaceClassFactories(map)
                .build();
        assertThat(options.getClassFactory(PermanentReadClass.class)).isSameAs(factory);
    }

    @Test
    void testFloatingPointAndMaxDepth() {
        ReadOptions options = new ReadOptionsBuilder()
                .floatPointBigDecimal()
                .floatPointDouble()
                .maxDepth(42)
                .build();
        assertTrue(options.isFloatingPointDouble());
        assertFalse(options.isFloatingPointBigDecimal());
        assertFalse(options.isFloatingPointBoth());
        assertThat(options.getMaxDepth()).isEqualTo(42);
    }

    @Test
    void testLocaleCharsetAndCharsAndCustomOptionAndNonReferenceableClass() {
        ReadOptions options = new ReadOptionsBuilder()
                .setLocale(Locale.FRANCE)
                .setCharset(StandardCharsets.ISO_8859_1)
                .setTrueCharacter('Y')
                .setFalseCharacter('N')
                .addCustomOption("foo", "bar")
                .addNonReferenceableClass(NonRefClass.class)
                .build();

        assertThat(options.getConverterOptions().getLocale()).isEqualTo(Locale.FRANCE);
        assertThat(options.getConverterOptions().getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(options.getConverterOptions().trueChar()).isEqualTo('Y');
        assertThat(options.getConverterOptions().falseChar()).isEqualTo('N');
        assertThat(options.getCustomOption("foo")).isEqualTo("bar");
        assertTrue(options.isNonReferenceableClass(NonRefClass.class));
    }

    @Test
    void testLruSize_defaultAndCustom() {
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        assertThat(defaultOptions.getLruSize()).isEqualTo(1000);

        ReadOptions customOptions = new ReadOptionsBuilder()
                .lruSize(42)
                .build();
        assertThat(customOptions.getLruSize()).isEqualTo(42);
    }

    static class SourceClass {}
    static class DestinationClass {}

    @Test
    void testIsClassCoerced() {
        ReadOptions options = new ReadOptionsBuilder()
                .coerceClass(SourceClass.class.getName(), DestinationClass.class)
                .build();

        assertFalse(new ReadOptionsBuilder().build().isClassCoerced(SourceClass.class));
        assertTrue(options.isClassCoerced(SourceClass.class));
        assertThat(options.getCoercedClass(SourceClass.class)).isSameAs(DestinationClass.class);
    }
}
