package com.cedarsoftware.io;

import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.annotation.IoProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the global {@link WriteOptionsBuilder#namingStrategy(IoNaming.Strategy)}
 * WriteOption — the Jackson-compatible equivalent of
 * {@code ObjectMapper.setPropertyNamingStrategy(...)}.
 */
class GlobalNamingStrategyTest {

    // ---- Plain POJOs with no naming annotations ----

    static class Profile {
        String firstName;
        String lastName;
        int loginCount;
        String parseXMLDocument;

        Profile() {}
        Profile(String first, String last, int count, String doc) {
            this.firstName = first;
            this.lastName = last;
            this.loginCount = count;
            this.parseXMLDocument = doc;
        }
    }

    // ---- POJO with per-field @IoProperty override ----

    static class ProfileWithIoProperty {
        String firstName;
        @IoProperty("uid")
        String userId;

        ProfileWithIoProperty() {}
        ProfileWithIoProperty(String first, String uid) {
            this.firstName = first;
            this.userId = uid;
        }
    }

    // ---- POJO with per-class @IoNaming override ----

    @IoNaming(IoNaming.Strategy.UPPER_CAMEL_CASE)
    static class ProfileWithIoNaming {
        String firstName;
        String lastName;

        ProfileWithIoNaming() {}
        ProfileWithIoNaming(String first, String last) {
            this.firstName = first;
            this.lastName = last;
        }
    }

    // ---- POJO with Jackson @JsonNaming (external annotation) override ----

    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    static class ProfileWithJsonNaming {
        String firstName;
        String lastName;

        ProfileWithJsonNaming() {}
        ProfileWithJsonNaming(String first, String last) {
            this.firstName = first;
            this.lastName = last;
        }
    }

    // ---- Tests ----

    @Test
    void defaultStrategyIsNull() {
        WriteOptions wo = new WriteOptionsBuilder().build();
        assertNull(wo.getNamingStrategy());

        Profile p = new Profile("Alice", "Smith", 3, "doc.xml");
        String json = JsonIo.toJson(p, new WriteOptionsBuilder().showTypeInfoNever().build());
        assertTrue(json.contains("firstName"), json);
        assertTrue(json.contains("lastName"), json);
        assertTrue(json.contains("loginCount"), json);
    }

    @Test
    void globalSnakeCaseTransformsUnannotatedFields() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .build();
        assertEquals(IoNaming.Strategy.SNAKE_CASE, wo.getNamingStrategy());

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 3, "doc.xml"), wo);
        assertTrue(json.contains("first_name"), json);
        assertTrue(json.contains("last_name"), json);
        assertTrue(json.contains("login_count"), json);
        assertTrue(json.contains("parse_xml_document"), json);
        assertFalse(json.contains("firstName"), json);
        assertFalse(json.contains("loginCount"), json);
    }

    @Test
    void globalUpperSnakeCaseWorks() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.UPPER_SNAKE_CASE)
                .build();

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 3, "doc.xml"), wo);
        assertTrue(json.contains("FIRST_NAME"), json);
        assertTrue(json.contains("LAST_NAME"), json);
        assertTrue(json.contains("LOGIN_COUNT"), json);
        assertTrue(json.contains("PARSE_XML_DOCUMENT"), json);
    }

    @Test
    void globalKebabCaseWorks() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.KEBAB_CASE)
                .build();

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 3, "doc.xml"), wo);
        assertTrue(json.contains("first-name"), json);
        assertTrue(json.contains("last-name"), json);
        assertTrue(json.contains("login-count"), json);
    }

    @Test
    void globalLowerCaseWorks() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.LOWER_CASE)
                .build();

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 3, "doc.xml"), wo);
        assertTrue(json.contains("firstname"), json);
        assertTrue(json.contains("lastname"), json);
        assertTrue(json.contains("logincount"), json);
        assertFalse(json.contains("firstName"), json);
    }

    @Test
    void globalLowerDotCaseWorks() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.LOWER_DOT_CASE)
                .build();

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 3, "doc.xml"), wo);
        assertTrue(json.contains("first.name"), json);
        assertTrue(json.contains("last.name"), json);
    }

    @Test
    void perFieldIoPropertyOverridesGlobal() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .build();

        String json = JsonIo.toJson(new ProfileWithIoProperty("Alice", "u-42"), wo);
        // firstName → first_name (global strategy applies)
        assertTrue(json.contains("first_name"), json);
        // userId → uid (per-field @IoProperty wins over global)
        assertTrue(json.contains("\"uid\""), json);
        assertFalse(json.contains("user_id"), json);
    }

    @Test
    void perClassIoNamingOverridesGlobal() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .build();

        String json = JsonIo.toJson(new ProfileWithIoNaming("Alice", "Smith"), wo);
        // Per-class @IoNaming(UPPER_CAMEL_CASE) wins over global SNAKE_CASE
        assertTrue(json.contains("FirstName"), json);
        assertTrue(json.contains("LastName"), json);
        assertFalse(json.contains("first_name"), json);
    }

    @Test
    void perClassJsonNamingOverridesGlobal() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .build();

        String json = JsonIo.toJson(new ProfileWithJsonNaming("Alice", "Smith"), wo);
        // Per-class @JsonNaming(UpperCamelCase) wins over global SNAKE_CASE
        assertTrue(json.contains("FirstName"), json);
        assertTrue(json.contains("LastName"), json);
        assertFalse(json.contains("first_name"), json);
    }

    @Test
    void clearingGlobalStrategyRestoresDefault() {
        WriteOptions wo = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .namingStrategy(null)
                .build();
        assertNull(wo.getNamingStrategy());

        String json = JsonIo.toJson(new Profile("Alice", "Smith", 1, "x"), wo);
        assertTrue(json.contains("firstName"), json);
    }

    @Test
    void copyConstructorCarriesNamingStrategy() {
        WriteOptions src = new WriteOptionsBuilder()
                .namingStrategy(IoNaming.Strategy.KEBAB_CASE)
                .build();
        WriteOptions copy = new WriteOptionsBuilder(src).build();
        assertEquals(IoNaming.Strategy.KEBAB_CASE, copy.getNamingStrategy());
    }

    @Test
    void permanentNamingStrategyAppliesToNewBuilders() {
        IoNaming.Strategy previous = WriteOptionsBuilder.getDefaultWriteOptions().getNamingStrategy();
        try {
            WriteOptionsBuilder.addPermanentNamingStrategy(IoNaming.Strategy.KEBAB_CASE);
            WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
            assertEquals(IoNaming.Strategy.KEBAB_CASE, wo.getNamingStrategy());

            String json = JsonIo.toJson(new Profile("Alice", "Smith", 1, "doc.xml"), wo);
            assertTrue(json.contains("first-name"), json);
        } finally {
            WriteOptionsBuilder.addPermanentNamingStrategy(previous);
        }
    }

    @Test
    void standardJsonDoesNotForceNamingStrategy() {
        // Jackson's default naming is LowerCamelCase (Java field names), so standardJson()
        // should not impose one. Verify: if user sets a global strategy then calls standardJson(),
        // the existing strategy is preserved (standardJson does not clobber it).
        WriteOptions wo = new WriteOptionsBuilder()
                .namingStrategy(IoNaming.Strategy.SNAKE_CASE)
                .standardJson()
                .build();
        assertEquals(IoNaming.Strategy.SNAKE_CASE, wo.getNamingStrategy());

        // And with no explicit set, standardJson() leaves it null (the default).
        WriteOptions defaultWo = new WriteOptionsBuilder().standardJson().build();
        assertNull(defaultWo.getNamingStrategy());
    }
}
