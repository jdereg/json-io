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
 * Tests for {@link ToonHttpMessageConverter}.
 */
class ToonHttpMessageConverterTest {

    private ToonHttpMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ToonHttpMessageConverter();
    }

    @Test
    void supportsApplicationToon() {
        assertThat(converter.canRead(Object.class, JsonIoMediaTypes.APPLICATION_TOON)).isTrue();
        assertThat(converter.canWrite(Object.class, JsonIoMediaTypes.APPLICATION_TOON)).isTrue();
    }

    @Test
    void supportsApplicationToonJson() {
        assertThat(converter.canRead(Object.class, JsonIoMediaTypes.APPLICATION_TOON_JSON)).isTrue();
        assertThat(converter.canWrite(Object.class, JsonIoMediaTypes.APPLICATION_TOON_JSON)).isTrue();
    }

    @Test
    void doesNotSupportApplicationJson() {
        assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isFalse();
        assertThat(converter.canWrite(Object.class, MediaType.APPLICATION_JSON)).isFalse();
    }

    @Test
    void readSimpleObject() throws Exception {
        // TOON format uses key: value syntax
        String toon = "name: John\nage: 30";
        HttpInputMessage inputMessage = createInputMessage(toon);

        Object result = converter.read(TestPerson.class, null, inputMessage);

        assertThat(result).isInstanceOf(TestPerson.class);
        TestPerson person = (TestPerson) result;
        assertThat(person.name).isEqualTo("John");
        assertThat(person.age).isEqualTo(30);
    }

    @Test
    void writeProducesToonFormat() throws Exception {
        TestPerson person = new TestPerson();
        person.name = "Jane";
        person.age = 25;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        HttpOutputMessage outputMessage = createOutputMessage(outputStream);

        converter.write(person, JsonIoMediaTypes.APPLICATION_TOON, outputMessage);

        String output = outputStream.toString(StandardCharsets.UTF_8);
        // TOON format uses unquoted keys and is more compact
        assertThat(output).contains("name");
        assertThat(output).contains("Jane");
        assertThat(output).contains("age");
        assertThat(output).contains("25");
    }

    private HttpInputMessage createInputMessage(String content) throws Exception {
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_TOON);
        when(inputMessage.getHeaders()).thenReturn(headers);
        when(inputMessage.getBody()).thenReturn(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return inputMessage;
    }

    private HttpOutputMessage createOutputMessage(ByteArrayOutputStream outputStream) throws Exception {
        HttpOutputMessage outputMessage = mock(HttpOutputMessage.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_TOON);
        when(outputMessage.getHeaders()).thenReturn(headers);
        when(outputMessage.getBody()).thenReturn(outputStream);
        return outputMessage;
    }

    static class TestPerson {
        public String name;
        public int age;
    }
}
