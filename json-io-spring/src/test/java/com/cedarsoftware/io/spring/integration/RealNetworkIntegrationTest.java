package com.cedarsoftware.io.spring.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.spring.JsonIoMediaTypes;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoAutoConfiguration;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoWebMvcAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real network integration tests using an embedded server.
 * These tests make actual HTTP calls over the network to catch issues
 * that MockMvc simulations might miss (stream handling, encoding, etc.).
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        classes = RealNetworkIntegrationTest.TestApplication.class
)
class RealNetworkIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ============= Basic JSON Tests =============

    @Test
    void getJsonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"name\"");
        assertThat(response.getBody()).contains("John");
    }

    @Test
    void postJsonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String json = "{\"name\":\"Jane\",\"age\":25}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Jane");
        assertThat(response.getBody()).contains("25");
    }

    // ============= JSON5 Tests =============

    @Test
    void getJson5OverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(JsonIoMediaTypes.APPLICATION_JSON5));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void postJson5WithTrailingCommaOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_JSON5);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // JSON5 with trailing comma - would fail with standard JSON parser
        String json5 = "{\"name\":\"Bob\",\"age\":30,}";
        HttpEntity<String> request = new HttpEntity<>(json5, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Bob");
    }

    @Test
    void postJson5WithCommentsOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_JSON5);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String json5 = "{\n  \"name\": \"Alice\",\n  // This is a comment\n  \"age\": 28\n}";
        HttpEntity<String> request = new HttpEntity<>(json5, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Alice");
    }

    // ============= TOON Tests =============

    @Test
    void getToonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(JsonIoMediaTypes.APPLICATION_TOON));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // TOON format uses different syntax
        assertThat(response.getBody()).contains("name:");
    }

    @Test
    void postToonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_TOON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String toon = "name: Charlie\nage: 35";
        HttpEntity<String> request = new HttpEntity<>(toon, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Charlie");
    }

    // ============= Cross-Format Tests =============

    @Test
    void postJsonReceiveToonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(JsonIoMediaTypes.APPLICATION_TOON));

        String json = "{\"name\":\"CrossFormat\",\"age\":40}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/vnd.toon");
    }

    @Test
    void postToonReceiveJson5OverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JsonIoMediaTypes.APPLICATION_TOON);
        headers.setAccept(List.of(JsonIoMediaTypes.APPLICATION_JSON5));

        String toon = "name: CrossFormat2\nage: 45";
        HttpEntity<String> request = new HttpEntity<>(toon, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/vnd.json5");
    }

    // ============= Unicode/Encoding Tests =============

    @Test
    void unicodeOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // Unicode characters that could have encoding issues
        String json = "{\"name\":\"日本語テスト\",\"age\":99}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("日本語テスト");
    }

    @Test
    void emojiOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String json = "{\"name\":\"Test 😀🎉\",\"age\":1}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Emojis should survive the round trip
    }

    // ============= Complex Objects =============

    @Test
    void nestedObjectOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String json = "{\"name\":\"Parent\",\"age\":50,\"child\":{\"name\":\"Child\",\"age\":20}}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/nested",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Parent");
        assertThat(response.getBody()).contains("Child");
    }

    @Test
    void listOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/list",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Item1");
        assertThat(response.getBody()).contains("Item2");
    }

    @Test
    void mapOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/map",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("key1");
        assertThat(response.getBody()).contains("Value1");
    }

    // ============= Large Payload Test =============

    @Test
    void largePayloadOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // Create a 50KB payload
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append("0123456789");
        }
        String json = "{\"name\":\"" + sb.toString() + "\",\"age\":1}";
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Verify the large payload survived the round trip
        assertThat(response.getBody().length()).isGreaterThan(50000);
    }

    // ============= Error Handling =============

    @Test
    void malformedJsonOverRealNetwork() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String malformedJson = "{\"name\":\"test\""; // Missing closing brace
        HttpEntity<String> request = new HttpEntity<>(malformedJson, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/network/person",
                HttpMethod.POST,
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ============= Test Configuration =============

    @Configuration
    @EnableAutoConfiguration
    @Import({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
    static class TestApplication {

        @Bean
        public NetworkTestController networkTestController() {
            return new NetworkTestController();
        }
    }

    @RestController
    static class NetworkTestController {

        @GetMapping("/network/person")
        public Person getPerson() {
            Person person = new Person();
            person.name = "John";
            person.age = 30;
            return person;
        }

        @PostMapping("/network/person")
        public Person postPerson(@RequestBody Person person) {
            return person;
        }

        @PostMapping("/network/nested")
        public NestedPerson postNested(@RequestBody NestedPerson person) {
            return person;
        }

        @GetMapping("/network/list")
        public List<Person> getList() {
            List<Person> list = new ArrayList<>();
            list.add(createPerson("Item1", 1));
            list.add(createPerson("Item2", 2));
            list.add(createPerson("Item3", 3));
            return list;
        }

        @GetMapping("/network/map")
        public Map<String, Person> getMap() {
            Map<String, Person> map = new HashMap<>();
            map.put("key1", createPerson("Value1", 1));
            map.put("key2", createPerson("Value2", 2));
            return map;
        }

        private Person createPerson(String name, int age) {
            Person p = new Person();
            p.name = name;
            p.age = age;
            return p;
        }
    }

    static class Person {
        public String name;
        public int age;
    }

    static class NestedPerson {
        public String name;
        public int age;
        public Person child;
    }
}
