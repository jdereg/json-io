package com.cedarsoftware.io.spring.integration;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for edge cases including empty bodies, large payloads,
 * Unicode/special characters, date/time serialization, and boundary conditions.
 */
@WebMvcTest(EdgeCaseTest.TestController.class)
@ImportAutoConfiguration({JsonIoAutoConfiguration.class, JsonIoWebMvcAutoConfiguration.class})
@TestPropertySource(properties = "spring.json-io.write.show-type-info=NEVER")
class EdgeCaseTest {

    @Autowired
    private MockMvc mockMvc;

    // ============= Unicode and Special Characters =============

    @Test
    void unicodeCharactersInStringField() throws Exception {
        String json = "{\"text\":\"Hello \\u4e16\\u754c\"}"; // Hello 世界

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello 世界"));
    }

    @Test
    void emojiCharacters() throws Exception {
        String json = "{\"text\":\"Test emoji \\uD83D\\uDE00\"}"; // 😀

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void specialEscapeSequences() throws Exception {
        String json = "{\"text\":\"Line1\\nLine2\\tTabbed\\\"Quoted\\\"\"}";

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void emptyStringValue() throws Exception {
        String json = "{\"text\":\"\"}";

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(""));
    }

    // ============= Large Payloads =============

    @Test
    void moderatelyLargePayload() throws Exception {
        // Create a JSON string with a 10KB text field
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("0123456789");
        }
        String json = "{\"text\":\"" + sb.toString() + "\"}";

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ============= Numeric Edge Cases =============

    @Test
    void maxIntegerValue() throws Exception {
        String json = "{\"number\":" + Integer.MAX_VALUE + "}";

        mockMvc.perform(post("/edge/number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(Integer.MAX_VALUE));
    }

    @Test
    void minIntegerValue() throws Exception {
        String json = "{\"number\":" + Integer.MIN_VALUE + "}";

        mockMvc.perform(post("/edge/number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(Integer.MIN_VALUE));
    }

    @Test
    void zeroValue() throws Exception {
        String json = "{\"number\":0}";

        mockMvc.perform(post("/edge/number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void negativeNumber() throws Exception {
        String json = "{\"number\":-42}";

        mockMvc.perform(post("/edge/number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(-42));
    }

    @Test
    void decimalNumber() throws Exception {
        String json = "{\"decimal\":3.14159}";

        mockMvc.perform(post("/edge/decimal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decimal").value(3.14159));
    }

    @Test
    void serializeBigDecimal() throws Exception {
        mockMvc.perform(get("/edge/big-decimal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void serializeBigInteger() throws Exception {
        mockMvc.perform(get("/edge/big-integer")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ============= Date/Time Serialization =============

    @Test
    void serializeLocalDate() throws Exception {
        mockMvc.perform(get("/edge/local-date")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void serializeLocalTime() throws Exception {
        mockMvc.perform(get("/edge/local-time")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void serializeLocalDateTime() throws Exception {
        mockMvc.perform(get("/edge/local-datetime")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void serializeZonedDateTime() throws Exception {
        mockMvc.perform(get("/edge/zoned-datetime")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void serializeInstant() throws Exception {
        mockMvc.perform(get("/edge/instant")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ============= UUID and Special Types =============

    @Test
    void serializeUuid() throws Exception {
        mockMvc.perform(get("/edge/uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ============= Boolean Edge Cases =============

    @Test
    void trueBooleanValue() throws Exception {
        String json = "{\"flag\":true}";

        mockMvc.perform(post("/edge/boolean")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void falseBooleanValue() throws Exception {
        String json = "{\"flag\":false}";

        mockMvc.perform(post("/edge/boolean")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(false));
    }

    // ============= Null Values =============

    @Test
    void explicitNullValue() throws Exception {
        String json = "{\"text\":null}";

        mockMvc.perform(post("/edge/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ============= JSON5 Edge Cases =============

    @Test
    void json5WithMultipleTrailingCommas() throws Exception {
        String json5 = "{\"text\":\"test\",\"number\":42,}";

        mockMvc.perform(post("/edge/mixed")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("test"))
                .andExpect(jsonPath("$.number").value(42));
    }

    @Test
    void json5WithHexNumbers() throws Exception {
        // JSON5 supports hex numbers
        String json5 = "{\"number\":0xFF}";

        mockMvc.perform(post("/edge/number")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(255));
    }

    @Test
    void json5WithMultiLineComment() throws Exception {
        // JSON5 supports multi-line comments
        String json5 = "{\n/* This is a\nmulti-line comment */\n\"number\":42}";

        mockMvc.perform(post("/edge/number")
                        .contentType(JsonIoMediaTypes.APPLICATION_JSON5)
                        .content(json5)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(42));
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

        @PostMapping("/edge/text")
        public TextData postText(@RequestBody TextData data) {
            return data;
        }

        @PostMapping("/edge/number")
        public NumberData postNumber(@RequestBody NumberData data) {
            return data;
        }

        @PostMapping("/edge/decimal")
        public DecimalData postDecimal(@RequestBody DecimalData data) {
            return data;
        }

        @PostMapping("/edge/boolean")
        public BooleanData postBoolean(@RequestBody BooleanData data) {
            return data;
        }

        @PostMapping("/edge/mixed")
        public MixedData postMixed(@RequestBody MixedData data) {
            return data;
        }

        @GetMapping("/edge/big-decimal")
        public BigDecimal getBigDecimal() {
            return new BigDecimal("12345678901234567890.123456789");
        }

        @GetMapping("/edge/big-integer")
        public BigInteger getBigInteger() {
            return new BigInteger("12345678901234567890123456789");
        }

        @GetMapping("/edge/local-date")
        public LocalDate getLocalDate() {
            return LocalDate.of(2025, 1, 25);
        }

        @GetMapping("/edge/local-time")
        public LocalTime getLocalTime() {
            return LocalTime.of(14, 30, 45);
        }

        @GetMapping("/edge/local-datetime")
        public LocalDateTime getLocalDateTime() {
            return LocalDateTime.of(2025, 1, 25, 14, 30, 45);
        }

        @GetMapping("/edge/zoned-datetime")
        public ZonedDateTime getZonedDateTime() {
            return ZonedDateTime.now();
        }

        @GetMapping("/edge/instant")
        public Instant getInstant() {
            return Instant.now();
        }

        @GetMapping("/edge/uuid")
        public UUID getUuid() {
            return UUID.randomUUID();
        }
    }

    static class TextData {
        public String text;
    }

    static class NumberData {
        public int number;
    }

    static class DecimalData {
        public double decimal;
    }

    static class BooleanData {
        public boolean flag;
    }

    static class MixedData {
        public String text;
        public int number;
    }
}
