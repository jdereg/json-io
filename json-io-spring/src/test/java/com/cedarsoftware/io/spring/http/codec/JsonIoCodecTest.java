package com.cedarsoftware.io.spring.http.codec;

import java.nio.charset.StandardCharsets;

import com.cedarsoftware.io.spring.JsonIoMediaTypes;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for json-io reactive codecs.
 */
class JsonIoCodecTest {

    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Test
    void jsonEncoderSupportsApplicationJson() {
        JsonIoEncoder encoder = new JsonIoEncoder();
        assertThat(encoder.canEncode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_JSON)).isTrue();
    }

    @Test
    void jsonDecoderSupportsApplicationJson() {
        JsonIoDecoder decoder = new JsonIoDecoder();
        assertThat(decoder.canDecode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_JSON)).isTrue();
    }

    @Test
    void toonEncoderSupportsApplicationToon() {
        ToonEncoder encoder = new ToonEncoder();
        assertThat(encoder.canEncode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_TOON)).isTrue();
    }

    @Test
    void toonDecoderSupportsApplicationToon() {
        ToonDecoder decoder = new ToonDecoder();
        assertThat(decoder.canDecode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_TOON)).isTrue();
    }

    @Test
    void json5EncoderSupportsApplicationJson5() {
        Json5Encoder encoder = new Json5Encoder();
        assertThat(encoder.canEncode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_JSON5)).isTrue();
    }

    @Test
    void json5DecoderSupportsApplicationJson5() {
        Json5Decoder decoder = new Json5Decoder();
        assertThat(decoder.canDecode(ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_JSON5)).isTrue();
    }

    @Test
    void jsonEncoderEncodesObject() {
        JsonIoEncoder encoder = new JsonIoEncoder();
        TestPerson person = new TestPerson();
        person.name = "John";
        person.age = 30;

        DataBuffer buffer = encoder.encodeValue(person, bufferFactory,
                ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_JSON, null);

        String json = buffer.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"name\":");
        assertThat(json).contains("John");
        assertThat(json).contains("\"age\":");
    }

    @Test
    void jsonDecoderDecodesObject() {
        JsonIoDecoder decoder = new JsonIoDecoder();
        String json = "{\"name\":\"Jane\",\"age\":25}";
        DataBuffer buffer = bufferFactory.wrap(json.getBytes(StandardCharsets.UTF_8));

        Mono<Object> result = decoder.decodeToMono(
                Mono.just(buffer),
                ResolvableType.forClass(TestPerson.class),
                JsonIoMediaTypes.APPLICATION_JSON,
                null);

        StepVerifier.create(result)
                .assertNext(obj -> {
                    assertThat(obj).isInstanceOf(TestPerson.class);
                    TestPerson person = (TestPerson) obj;
                    assertThat(person.name).isEqualTo("Jane");
                    assertThat(person.age).isEqualTo(25);
                })
                .verifyComplete();
    }

    @Test
    void toonEncoderProducesToonFormat() {
        ToonEncoder encoder = new ToonEncoder();
        TestPerson person = new TestPerson();
        person.name = "Alice";
        person.age = 28;

        DataBuffer buffer = encoder.encodeValue(person, bufferFactory,
                ResolvableType.forClass(TestPerson.class), JsonIoMediaTypes.APPLICATION_TOON, null);

        String toon = buffer.toString(StandardCharsets.UTF_8);
        // TOON format has key: value syntax
        assertThat(toon).contains("name");
        assertThat(toon).contains("Alice");
        assertThat(toon).contains("age");
    }

    @Test
    void toonDecoderDecodesToonFormat() {
        ToonDecoder decoder = new ToonDecoder();
        String toon = "name: Bob\nage: 35";
        DataBuffer buffer = bufferFactory.wrap(toon.getBytes(StandardCharsets.UTF_8));

        Mono<Object> result = decoder.decodeToMono(
                Mono.just(buffer),
                ResolvableType.forClass(TestPerson.class),
                JsonIoMediaTypes.APPLICATION_TOON,
                null);

        StepVerifier.create(result)
                .assertNext(obj -> {
                    assertThat(obj).isInstanceOf(TestPerson.class);
                    TestPerson person = (TestPerson) obj;
                    assertThat(person.name).isEqualTo("Bob");
                    assertThat(person.age).isEqualTo(35);
                })
                .verifyComplete();
    }

    static class TestPerson {
        public String name;
        public int age;
    }
}
