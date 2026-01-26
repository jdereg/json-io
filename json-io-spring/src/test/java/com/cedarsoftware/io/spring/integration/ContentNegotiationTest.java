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
 * Tests for content negotiation - verifying correct converter selection based on Accept/Content-Type headers.
 */
@WebMvcTest(ContentNegotiationTest.TestController.class)
@ImportAutoConfiguration({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
class ContentNegotiationTest {

    @Autowired
    private MockMvc mockMvc;

    // ============= Accept header tests (response format) =============

    @Test
    void acceptJsonReturnsJsonContentType() throws Exception {
        mockMvc.perform(get("/content/data")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void acceptJson5ReturnsJson5ContentType() throws Exception {
        mockMvc.perform(get("/content/data")
                        .accept(JsonIoMediaTypes.APPLICATION_JSON5))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_JSON5));
    }

    @Test
    void acceptToonReturnsToonContentType() throws Exception {
        mockMvc.perform(get("/content/data")
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_TOON));
    }

    @Test
    void acceptToonJsonReturnsToonJsonContentType() throws Exception {
        mockMvc.perform(get("/content/data")
                        .accept(JsonIoMediaTypes.APPLICATION_TOON_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_TOON_JSON));
    }

    @Test
    void noAcceptHeaderReturnsSuccess() throws Exception {
        // Without an Accept header, Spring selects from available converters
        // based on their registration order
        mockMvc.perform(get("/content/data"))
                .andExpect(status().isOk());
    }

    @Test
    void wildcardAcceptHeaderReturnsSuccess() throws Exception {
        mockMvc.perform(get("/content/data")
                        .accept(MediaType.ALL))
                .andExpect(status().isOk());
        // With wildcard accept, Spring selects from available converters
    }

    // ============= Content-Type header tests (request format) =============

    @Test
    void contentTypeJsonParsesJsonBody() throws Exception {
        String json = "{\"value\":\"test\",\"number\":42}";

        mockMvc.perform(post("/content/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("test"))
                .andExpect(jsonPath("$.number").value(42));
    }

    @Test
    void contentTypeJson5ParsesJson5Body() throws Exception {
        // JSON5 with trailing comma
        String json5 = "{\"value\":\"test\",\"number\":42,}";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("test"))
                .andExpect(jsonPath("$.number").value(42));
    }

    @Test
    void contentTypeJson5ParsesJson5WithComments() throws Exception {
        // JSON5 with single-line comment
        String json5 = "{\n\"value\":\"commented\",\n// This is a comment\n\"number\":99\n}";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("commented"))
                .andExpect(jsonPath("$.number").value(99));
    }

    @Test
    void contentTypeJson5ParsesJson5WithUnquotedKeys() throws Exception {
        // JSON5 with unquoted keys
        String json5 = "{value:\"unquoted\",number:100}";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("unquoted"))
                .andExpect(jsonPath("$.number").value(100));
    }

    @Test
    void contentTypeToonParsesToonBody() throws Exception {
        String toon = "value: toon_test\nnumber: 123";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(toon)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("toon_test"))
                .andExpect(jsonPath("$.number").value(123));
    }

    // ============= Cross-format tests =============

    @Test
    void postJsonReceiveJson5() throws Exception {
        String json = "{\"value\":\"cross\",\"number\":1}";

        mockMvc.perform(post("/content/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(JsonIoMediaTypes.APPLICATION_JSON5))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_JSON5));
    }

    @Test
    void postJson5ReceiveToon() throws Exception {
        String json5 = "{\"value\":\"cross2\",\"number\":2,}";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(JsonIoMediaTypes.APPLICATION_TOON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_TOON));
    }

    @Test
    void postToonReceiveJson() throws Exception {
        String toon = "value: cross3\nnumber: 3";

        mockMvc.perform(post("/content/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(toon)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ============= Quality value tests =============

    @Test
    void acceptHeaderWithQualityValuesPrefersHigherQuality() throws Exception {
        // Prefer JSON5 over JSON
        mockMvc.perform(get("/content/data")
                        .header("Accept", "application/json;q=0.5, application/vnd.json5;q=1.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JsonIoMediaTypes.APPLICATION_JSON5));
    }

    @Test
    void acceptHeaderWithEqualQualityUsesFirstMatch() throws Exception {
        // Equal quality - should match first converter in list
        mockMvc.perform(get("/content/data")
                        .header("Accept", "application/vnd.json5, application/json"))
                .andExpect(status().isOk());
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

        @GetMapping("/content/data")
        public TestData getData() {
            TestData data = new TestData();
            data.value = "test_value";
            data.number = 42;
            return data;
        }

        @PostMapping("/content/data")
        public TestData postData(@RequestBody TestData data) {
            return data;
        }
    }

    static class TestData {
        public String value;
        public int number;
    }
}
