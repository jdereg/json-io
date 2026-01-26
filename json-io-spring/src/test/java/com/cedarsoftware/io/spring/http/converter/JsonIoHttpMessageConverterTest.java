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
 * Tests for {@link JsonIoHttpMessageConverter}.
 */
class JsonIoHttpMessageConverterTest {

    private JsonIoHttpMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JsonIoHttpMessageConverter();
    }

    @Test
    void supportsApplicationJson() {
        assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isTrue();
        assertThat(converter.canWrite(Object.class, MediaType.APPLICATION_JSON)).isTrue();
    }

    @Test
    void doesNotSupportTextPlain() {
        assertThat(converter.canRead(Object.class, MediaType.TEXT_PLAIN)).isFalse();
        assertThat(converter.canWrite(Object.class, MediaType.TEXT_PLAIN)).isFalse();
    }

    @Test
    void readSimpleObject() throws Exception {
        String json = "{\"name\":\"John\",\"age\":30}";
        HttpInputMessage inputMessage = createInputMessage(json);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("John");
        assertThat(person.age).isEqualTo(30);
    }

    @Test
    void writeSimpleObject() throws Exception {
        TestPerson person = new TestPerson();
        person.name = "Jane";
        person.age = 25;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpOutputMessage outputMessage = createOutputMessage(outputStream);

        converter.write(person, MediaType.APPLICATION_JSON, outputMessage);

        String json = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"name\":");
        assertThat(json).contains("Jane");
        assertThat(json).contains("\"age\":");
        assertThat(json).contains("25");
    }

    private HttpInputMessage createInputMessage(String content) throws Exception {
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        when(inputMessage.getHeaders()).thenReturn(headers);
        when(inputMessage.getBody()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return inputMessage;
    }

    private HttpOutputMessage createOutputMessage(ByteArrayOutputStream outputStream) throws Exception {
        HttpOutputMessage outputMessage = mock(HttpOutputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        when(outputMessage.getHeaders()).thenReturn(headers);
        when(outputMessage.getBody()).thenReturn(outputStream);
        return outputMessage;
    }

    static class TestPerson {
        public String name;
        public int age;
    }
}
