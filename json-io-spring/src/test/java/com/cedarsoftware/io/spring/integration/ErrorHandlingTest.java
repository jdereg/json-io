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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for error handling scenarios including malformed input,
 * type mismatches, and invalid content.
 */
@WebMvcTest(ErrorHandlingTest.TestController.class)
@ImportAutoConfiguration({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
class ErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    // ============= Malformed JSON =============

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        String malformedJson = "{\"name\":\"test\""; // Missing closing brace

        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidJsonSyntaxReturnsBadRequest() throws Exception {
        String invalidJson = "{name: missing quotes}"; // Standard JSON requires quoted keys

        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ============= Malformed JSON5 =============

    @Test
    void malformedJson5ReturnsBadRequest() throws Exception {
        // Missing closing brace in JSON5
        String malformedJson5 = "{\"name\":\"test\",";

        mockMvc.perform(post("/error/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(malformedJson5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unclosedCommentInJson5ReturnsBadRequest() throws Exception {
        // Unclosed multi-line comment
        String badJson5 = "{\"name\":\"test\" /* unclosed comment";

        mockMvc.perform(post("/error/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(badJson5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ============= Malformed TOON =============

    @Test
    void malformedToonReturnsBadRequest() throws Exception {
        // Invalid TOON syntax
        String malformedToon = "this is not valid TOON {{{";

        mockMvc.perform(post("/error/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(malformedToon)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    // ============= Type Mismatch Tests =============

    @Test
    void stringForNumberFieldIsHandled() throws Exception {
        // json-io handles type coercion - may fail or succeed
        String json = "{\"name\":\"test\",\"count\":\"not-a-number\"}";

        // The request should not cause a 500 server error
        mockMvc.perform(post("/error/numeric")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Either 2xx success or 4xx client error is acceptable
                    // 5xx server error would be a bug
                    if (status >= 500) {
                        throw new AssertionError("Expected success or client error but got: " + status);
                    }
                });
    }

    @Test
    void wrongArrayTypeIsHandled() throws Exception {
        // Expected object but got array
        String json = "[1, 2, 3]";

        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Either 2xx success or 4xx client error is acceptable
                    // 5xx server error would be a bug
                    if (status >= 500) {
                        throw new AssertionError("Expected success or client error but got: " + status);
                    }
                });
    }

    // ============= Unsupported Media Type =============

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        String xml = "<data><name>test</name></data>";

        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unknownContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.valueOf("application/unknown"))
                        .content("{\"name\":\"test\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ============= Valid requests still work =============

    @Test
    void validJsonStillWorks() throws Exception {
        String validJson = "{\"name\":\"test\",\"value\":42}";

        mockMvc.perform(post("/error/data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void validJson5StillWorks() throws Exception {
        // JSON5 with trailing comma - should work
        String validJson5 = "{\"name\":\"test\",\"value\":42,}";

        mockMvc.perform(post("/error/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(validJson5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void validToonStillWorks() throws Exception {
        String validToon = "name: test\nvalue: 42";

        mockMvc.perform(post("/error/data")
                        .contentType(JsonIoMediaTypes.APPLICATION_TOON)
                        .content(validToon)
                        .accept(MediaType.APPLICATION_JSON))
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

        @PostMapping("/error/data")
        public TestData postData(@RequestBody TestData data) {
            return data;
        }

        @PostMapping("/error/numeric")
        public NumericData postNumeric(@RequestBody NumericData data) {
            return data;
        }
    }

    static class TestData {
        public String name;
        public int value;
    }

    static class NumericData {
        public String name;
        public int count;
    }
}
