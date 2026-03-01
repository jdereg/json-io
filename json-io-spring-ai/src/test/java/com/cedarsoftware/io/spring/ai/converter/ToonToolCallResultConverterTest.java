package com.cedarsoftware.io.spring.ai.converter;

import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ToonToolCallResultConverter}.
 */
class ToonToolCallResultConverterTest {

    private final ToonToolCallResultConverter converter = new ToonToolCallResultConverter();

    @Test
    void nullResultReturnsEmptyString() {
        String result = converter.convert(null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void stringResultPassedThrough() {
        String result = converter.convert("hello world", String.class);
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void emptyStringPassedThrough() {
        String result = converter.convert("", String.class);
        assertThat(result).isEmpty();
    }

    @Test
    void mapSerializedToToon() {
        Map<String, Object> map = Map.of("name", "Alice", "age", 30);
        String result = converter.convert(map, Map.class);

        assertThat(result).isNotBlank();
        // TOON uses "key: value" syntax
        assertThat(result).contains("name: Alice");
        assertThat(result).contains("age: 30");
        // TOON should not have JSON braces
        assertThat(result).doesNotContain("{");
        assertThat(result).doesNotContain("}");
    }

    @Test
    void listSerializedToToon() {
        List<String> list = List.of("one", "two", "three");
        String result = converter.convert(list, List.class);

        assertThat(result).isNotBlank();
        assertThat(result).contains("one");
        assertThat(result).contains("two");
        assertThat(result).contains("three");
    }

    @Test
    void nestedObjectSerializedToToon() {
        Map<String, Object> address = Map.of("city", "Springfield", "zip", "62704");
        Map<String, Object> person = Map.of("name", "Bob", "address", address);
        String result = converter.convert(person, Map.class);

        assertThat(result).isNotBlank();
        assertThat(result).contains("name: Bob");
        assertThat(result).contains("city: Springfield");
        // "62704" is a numeric-looking string, so TOON quotes it to preserve string type
        assertThat(result).contains("zip:");
    }

    @Test
    void integerResultSerializedToToon() {
        String result = converter.convert(42, Integer.class);
        assertThat(result).contains("42");
    }

    @Test
    void customWriteOptionsUsed() {
        WriteOptions custom = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .toonKeyFolding(false)
                .build();
        ToonToolCallResultConverter customConverter = new ToonToolCallResultConverter(custom);

        Map<String, Object> map = Map.of("key", "value");
        String result = customConverter.convert(map, Map.class);
        assertThat(result).contains("key: value");
    }

    @Test
    void returnTypeCanBeNull() {
        Map<String, Object> map = Map.of("x", 1);
        String result = converter.convert(map, null);
        assertThat(result).isNotBlank();
        assertThat(result).contains("x: 1");
    }
}
