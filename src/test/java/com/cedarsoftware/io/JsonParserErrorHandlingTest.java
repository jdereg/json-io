package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonParser error handling conditions.
 *
 * @author Claude 4.5o
 */
public class JsonParserErrorHandlingTest {

    /**
     * Test that @items with a non-array value (String) throws JsonIoException.
     * This tests line 323-324 of JsonParser.readJsonObject().
     */
    @Test
    public void testItemsWithStringValue_ShouldThrowJsonIoException() {
        // @items should contain an array, but we provide a String
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":\"not an array\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @items to have an array []"));
        assertTrue(exception.getMessage().contains("java.lang.String"));
    }

    /**
     * Test that @items with a numeric value throws JsonIoException.
     * This tests line 323-324 of JsonParser.readJsonObject().
     */
    @Test
    public void testItemsWithNumericValue_ShouldThrowJsonIoException() {
        // @items should contain an array, but we provide a number
        String json = "{\"@type\":\"java.util.HashSet\",\"@items\":12345}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @items to have an array []"));
        // Long is the default type for JSON numbers
        assertTrue(exception.getMessage().contains("Long"));
    }

    /**
     * Test that @items with an object value throws JsonIoException.
     * This tests line 323-324 of JsonParser.readJsonObject().
     */
    @Test
    public void testItemsWithObjectValue_ShouldThrowJsonIoException() {
        // @items should contain an array, but we provide an object
        String json = "{\"@type\":\"java.util.LinkedList\",\"@items\":{\"nested\":\"object\"}}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @items to have an array []"));
        assertTrue(exception.getMessage().contains("JsonObject"));
    }

    /**
     * Test that @items with a boolean value throws JsonIoException.
     * This tests line 323-324 of JsonParser.readJsonObject().
     */
    @Test
    public void testItemsWithBooleanValue_ShouldThrowJsonIoException() {
        // @items should contain an array, but we provide a boolean
        String json = "{\"@type\":\"java.util.TreeSet\",\"@items\":true}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @items to have an array []"));
        assertTrue(exception.getMessage().contains("Boolean"));
    }

    /**
     * Test that @items with null value does NOT throw (null is allowed).
     * Line 323 checks "value != null" before throwing.
     */
    @Test
    public void testItemsWithNullValue_ShouldNotThrow() {
        // @items with null is allowed (creates empty collection)
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":null}";

        // Should not throw - null @items is valid
        Object result = JsonIo.toObjects(json, null, Object.class);
        assertNotNull(result);
    }

    /**
     * Test that @items with valid array value works correctly.
     */
    @Test
    public void testItemsWithArrayValue_ShouldWork() {
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[1,2,3]}";

        Object result = JsonIo.toObjects(json, null, Object.class);
        assertNotNull(result);
        assertTrue(result instanceof java.util.ArrayList);
        assertEquals(3, ((java.util.ArrayList<?>) result).size());
    }

    // ========== Tests for readFieldName() error handling (lines 429-430) ==========

    /**
     * Test that a field name starting with a number throws JsonIoException.
     * This tests lines 429-430 of JsonParser.readFieldName().
     * Numbers are not valid identifier start characters and are not quotes.
     */
    @Test
    public void testFieldNameStartingWithNumber_ShouldThrowJsonIoException() {
        // Field name starts with a number - invalid in all JSON modes
        String json = "{123: \"value\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }

    /**
     * Test that a field name starting with exclamation mark throws JsonIoException.
     * This tests lines 429-430 of JsonParser.readFieldName().
     */
    @Test
    public void testFieldNameStartingWithExclamation_ShouldThrowJsonIoException() {
        // Field name starts with ! - not a valid identifier start or quote
        String json = "{!field: \"value\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }

    /**
     * Test that a field name starting with hash/pound throws JsonIoException.
     * This tests lines 429-430 of JsonParser.readFieldName().
     */
    @Test
    public void testFieldNameStartingWithHash_ShouldThrowJsonIoException() {
        // Field name starts with # - not a valid identifier start or quote
        String json = "{#field: \"value\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }

    /**
     * Test that a field name starting with percent throws JsonIoException.
     * This tests lines 429-430 of JsonParser.readFieldName().
     */
    @Test
    public void testFieldNameStartingWithPercent_ShouldThrowJsonIoException() {
        // Field name starts with % - not a valid identifier start or quote
        String json = "{%field: \"value\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }

    /**
     * Test that a field name starting with hyphen/minus throws JsonIoException.
     * This tests lines 429-430 of JsonParser.readFieldName().
     * Note: hyphen is NOT a valid identifier start in Java/JavaScript.
     */
    @Test
    public void testFieldNameStartingWithHyphen_ShouldThrowJsonIoException() {
        // Field name starts with - (hyphen) - not a valid identifier start
        String json = "{-field: \"value\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toObjects(json, null, Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }
}
