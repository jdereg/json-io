package com.cedarsoftware.io.spring.integration;

import com.cedarsoftware.io.spring.JsonIoMediaTypes;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoAutoConfiguration;
import com.cedarsoftware.io.spring.autoconfigure.JsonIoWebMvcAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests using MockMvc to test actual HTTP request/response flow.
 */
@WebMvcTest(MockMvcIntegrationTest.TestController.class)
@ImportAutoConfiguration({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
class MockMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getWithJsonAcceptHeader() throws Exception {
        mockMvc.perform(get("/test/person")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("John"))
                .andExpect(jsonPath("$.age").value(30));
    }

    @Test
    void getWithJson5AcceptHeader() throws Exception {
        mockMvc.perform(get("/test/person")
                        .accept(JsonIoMediaTypes.APPLICATION_JSON5))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_JSON5));
    }

    @Test
    void getWithToonAcceptHeader() throws Exception {
        mockMvc.perform(get("/test/person")
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_TOON));
    }

    @Test
    void postWithJsonContentType() throws Exception {
        String json = "{\"name\":\"Jane\",\"age\":25}";

        mockMvc.perform(post("/test/person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.age").value(25));
    }

    @Test
    void postWithJson5ContentType() throws Exception {
        // JSON5 with trailing comma
        String json5 = "{\"name\":\"Bob\",\"age\":35,}";

        mockMvc.perform(post("/test/person")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(JsonIoMediaTypes.APPLICATION_JSON5))
                .andExpect(status().isOk());
    }

    @Test
    void postWithToonContentType() throws Exception {
        String toon = "name: Alice\nage: 28";

        mockMvc.perform(post("/test/person")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(toon)
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk());
    }

    @Test
    void postJsonReceiveToon() throws Exception {
        String json = "{\"name\":\"Charlie\",\"age\":40}";

        mockMvc.perform(post("/test/person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_TOON));
    }

    @Test
    void postToonReceiveJson() throws Exception {
        String toon = "name: Diana\nage: 32";

        mockMvc.perform(post("/test/person")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(toon)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Configuration
    static class TestConfig {
        @Bean
        public TestController testController() {
            return new TestController();
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/test/person")
        public TestPerson getPerson() {
            TestPerson person = new TestPerson();
            person.name = "John";
            person.age = 30;
            return person;
        }

        @PostMapping("/test/person")
        public TestPerson postPerson(@RequestBody TestPerson person) {
            return person;
        }
    }

    static class TestPerson {
        public String name;
        public int age;
    }
}
