package com.cedarsoftware.io.spring.http.converter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link Json5HttpMessageConverter}.
 */
class Json5HttpMessageConverterTest {

    private Json5HttpMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new Json5HttpMessageConverter();
    }

    @Test
    void supportsApplicationJson5() {
        assertThat(converter.canRead(Object.class, JsonIoMediaTypes.APPLICATION_JSON5)).isTrue();
        assertThat(converter.canWrite(Object.class, JsonIoMediaTypes.APPLICATION_JSON5)).isTrue();
    }

    @Test
    void doesNotSupportApplicationJson() {
        assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isFalse();
        assertThat(converter.canWrite(Object.class, MediaType.APPLICATION_JSON)).isFalse();
    }

    @Test
    void doesNotSupportTextPlain() {
        assertThat(converter.canRead(Object.class, MediaType.TEXT_PLAIN)).isFalse();
        assertThat(converter.canWrite(Object.class, MediaType.TEXT_PLAIN)).isFalse();
    }

    @Test
    void readSimpleObject() throws Exception {
        String json5 = "{\"name\":\"John\",\"age\":30}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("John");
        assertThat(person.age).isEqualTo(30);
    }

    @Test
    void readJson5WithTrailingComma() throws Exception {
        // JSON5 allows trailing commas
        String json5 = "{\"name\":\"Jane\",\"age\":25,}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("Jane");
        assertThat(person.age).isEqualTo(25);
    }

    @Test
    void readJson5WithSingleLineComment() throws Exception {
        // JSON5 allows single-line comments
        String json5 = "{\n\"name\":\"Bob\",\n// this is a comment\n\"age\":35\n}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("Bob");
        assertThat(person.age).isEqualTo(35);
    }

    @Test
    void readJson5WithMultiLineComment() throws Exception {
        // JSON5 allows multi-line comments
        String json5 = "{\n\"name\":\"Alice\",\n/* multi\nline\ncomment */\n\"age\":28\n}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("Alice");
        assertThat(person.age).isEqualTo(28);
    }

    @Test
    void readJson5WithUnquotedKeys() throws Exception {
        // JSON5 allows unquoted keys (valid ECMAScript identifiers)
        String json5 = "{name:\"Charlie\",age:40}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("Charlie");
        assertThat(person.age).isEqualTo(40);
    }

    @Test
    void readJson5WithSingleQuotedStrings() throws Exception {
        // JSON5 allows single-quoted strings
        String json5 = "{\"name\":'Diana',\"age\":32}";
        HttpInputMessage inputMessage = createInputMessage(json5);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("Diana");
        assertThat(person.age).isEqualTo(32);
    }

    @Test
    void writeSimpleObject() throws Exception {
        TestPerson person = new TestPerson();
        person.name = "Eve";
        person.age = 29;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpOutputMessage outputMessage = createOutputMessage(outputStream);

        converter.write(person, JsonIoMediaTypes.APPLICATION_JSON5, outputMessage);

        String output = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("name");
        assertThat(output).contains("Eve");
        assertThat(output).contains("age");
        assertThat(output).contains("29");
    }

    private HttpInputMessage createInputMessage(String content) throws Exception {
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_JSON5);
        when(inputMessage.getHeaders()).thenReturn(headers);
        when(inputMessage.getBody()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return inputMessage;
    }

    private HttpOutputMessage createOutputMessage(ByteArrayOutputStream outputStream) throws Exception {
        HttpOutputMessage outputMessage = mock(HttpOutputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_JSON5);
        when(outputMessage.getHeaders()).thenReturn(headers);
        when(outputMessage.getBody()).thenReturn(outputStream);
        return outputMessage;
    }

    static class TestPerson {
        public String name;
        public int age;
    }
}
