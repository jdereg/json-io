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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
    }

    /**
     * Test that @items with valid array value works correctly.
     */
    @Test
    public void testItemsWithArrayValue_ShouldWork() {
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[1,2,3]}";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
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
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected quote before field name"));
    }

    // ========== Tests for readToken() error handling (lines 496, 505, 513, 519) ==========

    /**
     * Test that EOF during short token read throws JsonIoException.
     * This tests line 496 of JsonParser.readToken().
     * Short tokens are ≤5 characters: true, false, null
     */
    @Test
    public void testEofDuringTrueToken_ShouldThrowJsonIoException() {
        // Truncated "true" - EOF before token completes
        String json = "{\"field\": tru";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading token"));
    }

    /**
     * Test that EOF during "false" token read throws JsonIoException.
     * This tests line 496 of JsonParser.readToken().
     */
    @Test
    public void testEofDuringFalseToken_ShouldThrowJsonIoException() {
        // Truncated "false" - EOF before token completes
        String json = "{\"field\": fals";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading token"));
    }

    /**
     * Test that EOF during "null" token read throws JsonIoException.
     * This tests line 496 of JsonParser.readToken().
     */
    @Test
    public void testEofDuringNullToken_ShouldThrowJsonIoException() {
        // Truncated "null" - EOF before token completes
        String json = "{\"field\": nul";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading token"));
    }

    /**
     * Test that mismatched short token throws JsonIoException.
     * This tests line 505 of JsonParser.readToken().
     * Token starts with 't' but doesn't spell "true"
     */
    @Test
    public void testMismatchedTrueToken_ShouldThrowJsonIoException() {
        // "trux" instead of "true" - character mismatch
        String json = "{\"field\": trux}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected token:"));
    }

    /**
     * Test that mismatched "false" token throws JsonIoException.
     * This tests line 505 of JsonParser.readToken().
     */
    @Test
    public void testMismatchedFalseToken_ShouldThrowJsonIoException() {
        // "falsx" instead of "false" - character mismatch
        String json = "{\"field\": falsx}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected token:"));
    }

    /**
     * Test that mismatched "null" token throws JsonIoException.
     * This tests line 505 of JsonParser.readToken().
     */
    @Test
    public void testMismatchedNullToken_ShouldThrowJsonIoException() {
        // "nulx" instead of "null" - character mismatch
        String json = "{\"field\": nulx}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected token:"));
    }

    /**
     * Test that EOF during long token (Infinity) read throws JsonIoException.
     * This tests line 513 of JsonParser.readToken().
     * Long tokens are >5 characters: Infinity (8 chars)
     * Note: Must use allowNanAndInfinity(true) and array context to trigger readToken for Infinity.
     */
    @Test
    public void testEofDuringInfinityToken_ShouldThrowJsonIoException() {
        // Truncated "Infinity" in array context - EOF before token completes
        // Using array context to avoid JSON5 unquoted identifier parsing
        String json = "[Infini";
        ReadOptions opts = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading token"));
    }

    /**
     * Test that mismatched long token (Infinity) throws JsonIoException.
     * This tests line 519 of JsonParser.readToken().
     */
    @Test
    public void testMismatchedInfinityToken_ShouldThrowJsonIoException() {
        // "Infinitx" instead of "Infinity" - character mismatch in long token
        // Using array context to avoid JSON5 unquoted identifier parsing
        String json = "[Infinitx]";
        ReadOptions opts = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected token:"));
    }

    /**
     * Test that EOF during negative Infinity read throws JsonIoException.
     * Tests the long token path (line 513) with negative infinity.
     */
    @Test
    public void testEofDuringNegativeInfinityToken_ShouldThrowJsonIoException() {
        // Truncated "-Infinity" - EOF before token completes
        String json = "[-Infini";
        ReadOptions opts = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading token"));
    }

    /**
     * Test that mismatched negative Infinity throws JsonIoException.
     * Tests the long token path (line 519) with negative infinity.
     */
    @Test
    public void testMismatchedNegativeInfinityToken_ShouldThrowJsonIoException() {
        // "-Infinitx" instead of "-Infinity"
        String json = "[-Infinitx]";
        ReadOptions opts = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected token:"));
    }

    // ========== Tests for JSON5 positive numbers ==========

    /**
     * Test that valid JSON5 positive numbers work correctly.
     */
    @Test
    public void testValidPositiveNumber_ShouldWork() {
        // JSON5 allows explicit positive sign
        String json = "[+42, +3.14]";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(2, arr.length);
        assertEquals(42L, arr[0]);
        assertEquals(3.14, arr[1]);
    }

    // ========== Tests for readInteger() BigInteger mode (line 656) ==========

    /**
     * Test that integers return BigInteger when integerTypeBigInteger mode is enabled.
     * This tests line 656 of JsonParser.readInteger().
     * All integers should return as BigInteger regardless of size.
     */
    @Test
    public void testSmallInteger_WithIntegerTypeBigInteger_ReturnsBigInteger() {
        // Small integer that would normally be a Long
        String json = "[42]";
        ReadOptions opts = new ReadOptionsBuilder().integerTypeBigInteger().build();

        Object result = JsonIo.toJava(json, opts).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof java.math.BigInteger, "Expected BigInteger but got: " + arr[0].getClass().getName());
        assertEquals(new java.math.BigInteger("42"), arr[0]);
    }

    /**
     * Test that negative integers return BigInteger when integerTypeBigInteger mode is enabled.
     * This tests line 656 of JsonParser.readInteger().
     */
    @Test
    public void testNegativeInteger_WithIntegerTypeBigInteger_ReturnsBigInteger() {
        String json = "[-12345]";
        ReadOptions opts = new ReadOptionsBuilder().integerTypeBigInteger().build();

        Object result = JsonIo.toJava(json, opts).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof java.math.BigInteger);
        assertEquals(new java.math.BigInteger("-12345"), arr[0]);
    }

    // ========== Tests for readInteger() BigInteger overflow handling (lines 680-689) ==========

    /**
     * Test that integers larger than Long.MAX_VALUE return BigInteger when integerTypeBoth is enabled.
     * This tests lines 680-684 of JsonParser.readInteger().
     * Long.MAX_VALUE is 9223372036854775807 (19 digits).
     */
    @Test
    public void testIntegerOverflow_WithIntegerTypeBoth_ReturnsBigInteger() {
        // Number larger than Long.MAX_VALUE
        String json = "[9223372036854775808]";  // Long.MAX_VALUE + 1
        ReadOptions opts = new ReadOptionsBuilder().integerTypeBoth().build();

        Object result = JsonIo.toJava(json, opts).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof java.math.BigInteger, "Expected BigInteger but got: " + arr[0].getClass().getName());
        assertEquals(new java.math.BigInteger("9223372036854775808"), arr[0]);
    }

    /**
     * Test that very large integers return BigInteger when integerTypeBoth is enabled.
     * This tests lines 680-684 of JsonParser.readInteger().
     */
    @Test
    public void testVeryLargeInteger_WithIntegerTypeBoth_ReturnsBigInteger() {
        // 25-digit number - way beyond Long range
        String json = "[1234567890123456789012345]";
        ReadOptions opts = new ReadOptionsBuilder().integerTypeBoth().build();

        Object result = JsonIo.toJava(json, opts).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof java.math.BigInteger);
        assertEquals(new java.math.BigInteger("1234567890123456789012345"), arr[0]);
    }

    /**
     * Test that integers larger than Long.MAX_VALUE wrap around when integerTypeBoth is NOT enabled.
     * This tests lines 685-687 of JsonParser.readInteger().
     * The BigInteger.longValue() wraps around similar to casting.
     */
    @Test
    public void testIntegerOverflow_WithoutIntegerTypeBoth_ReturnsWrappedLong() {
        // Number larger than Long.MAX_VALUE - should wrap around
        String json = "[9223372036854775808]";  // Long.MAX_VALUE + 1
        // Default options - integerTypeBoth is false

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof Long, "Expected Long but got: " + arr[0].getClass().getName());
        // Long.MAX_VALUE + 1 wraps to Long.MIN_VALUE
        assertEquals(Long.MIN_VALUE, arr[0]);
    }

    /**
     * Test negative integers smaller than Long.MIN_VALUE return BigInteger when integerTypeBoth is enabled.
     * This tests lines 680-684 of JsonParser.readInteger().
     * Long.MIN_VALUE is -9223372036854775808 (19 digits + sign).
     */
    @Test
    public void testNegativeIntegerOverflow_WithIntegerTypeBoth_ReturnsBigInteger() {
        // Number smaller than Long.MIN_VALUE
        String json = "[-9223372036854775809]";  // Long.MIN_VALUE - 1
        ReadOptions opts = new ReadOptionsBuilder().integerTypeBoth().build();

        Object result = JsonIo.toJava(json, opts).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        assertTrue(arr[0] instanceof java.math.BigInteger);
        assertEquals(new java.math.BigInteger("-9223372036854775809"), arr[0]);
    }

    // ========== Tests for readString() error handling (lines 790, 818, 823) ==========

    /**
     * Test that content after a root-level string throws JsonIoException.
     * This tests line 790 of JsonParser.readString().
     * When curParseDepth == 0 (root level), nothing should follow the string.
     */
    @Test
    public void testContentAfterRootString_ShouldThrowJsonIoException() {
        // Root-level string followed by extra content
        String json = "\"hello\" extra";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF expected, content found after string"));
    }

    /**
     * Test that EOF immediately after backslash throws JsonIoException.
     * This tests line 799 of JsonParser.readString().
     */
    @Test
    public void testEofAfterBackslash_ShouldThrowJsonIoException() {
        // String with backslash followed by EOF (no escape character)
        String json = "\"hello\\";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading escape sequence"));
    }

    /**
     * Test that EOF during Unicode escape sequence throws JsonIoException.
     * This tests line 818 of JsonParser.readString().
     */
    @Test
    public void testEofDuringUnicodeEscape_ShouldThrowJsonIoException() {
        // Truncated Unicode escape - only 2 hex digits before EOF
        String json = "\"hello\\u00";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading Unicode escape sequence"));
    }

    /**
     * Test that invalid hex digit in Unicode escape throws JsonIoException.
     * This tests line 823 of JsonParser.readString().
     */
    @Test
    public void testInvalidHexInUnicodeEscape_ShouldThrowJsonIoException() {
        // Invalid hex digit 'G' in Unicode escape
        String json = "\"hello\\u00GG\"";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected hexadecimal digit, got:"));
    }

    /**
     * Test that valid Unicode escape sequences work correctly.
     */
    @Test
    public void testValidUnicodeEscape_ShouldWork() {
        // Valid Unicode escape for 'A' (U+0041)
        String json = "\"\\u0041\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertEquals("A", result);
    }

    // ========== Tests for surrogate pair handling (lines 847-877) ==========

    /**
     * Test that EOF during low surrogate Unicode escape throws JsonIoException.
     * This tests lines 847-848 of JsonParser.readString().
     * High surrogate (D800-DBFF) followed by backslash-u with truncated hex.
     */
    @Test
    public void testEofDuringLowSurrogateEscape_ShouldThrowJsonIoException() {
        // High surrogate followed by backslash-u with only 2 hex digits before EOF
        String json = "\"" + "\\u" + "D800" + "\\u" + "00";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("EOF reached while reading Unicode escape sequence"));
    }

    /**
     * Test that invalid hex in low surrogate escape throws JsonIoException.
     * This tests lines 852-853 of JsonParser.readString().
     */
    @Test
    public void testInvalidHexInLowSurrogateEscape_ShouldThrowJsonIoException() {
        // High surrogate followed by backslash-u with invalid hex 'GG'
        String json = "\"" + "\\u" + "D800" + "\\u" + "DCGG\"";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected hexadecimal digit, got:"));
    }

    /**
     * Test that invalid surrogate pair (low surrogate out of range) appends both chars.
     * This tests lines 864-866 of JsonParser.readString().
     * When high surrogate is followed by backslash-uXXXX where XXXX is not a valid low surrogate.
     */
    @Test
    public void testInvalidSurrogatePair_AppendsCharsSeparately() {
        // High surrogate followed by backslash-u0041 (not a low surrogate, just 'A')
        String json = "\"" + "\\u" + "D800" + "\\u" + "0041\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        // Should contain both characters: the orphan high surrogate and 'A'
        String str = (String) result;
        assertEquals(2, str.length());
        assertEquals(0xD800, str.charAt(0));  // Orphan high surrogate
        assertEquals('A', str.charAt(1));     // The 'A' from backslash-u0041
    }

    /**
     * Test that high surrogate followed by backslash-not-u pushes back correctly.
     * This tests lines 871-872 of JsonParser.readString().
     * High surrogate followed by backslash-n (not backslash-u).
     */
    @Test
    public void testHighSurrogateFollowedByBackslashNotU_AppendsOrphanSurrogate() {
        // High surrogate followed by backslash-n (newline escape)
        String json = "\"" + "\\u" + "D800" + "\\n\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        String str = (String) result;
        assertEquals(2, str.length());
        assertEquals(0xD800, str.charAt(0));  // Orphan high surrogate
        assertEquals('\n', str.charAt(1));    // Newline character
    }

    /**
     * Test that high surrogate followed by non-backslash pushes back correctly.
     * This tests lines 876-877 of JsonParser.readString().
     */
    @Test
    public void testHighSurrogateFollowedByRegularChar_AppendsOrphanSurrogate() {
        // High surrogate followed by regular character 'X'
        String json = "\"" + "\\u" + "D800X\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        String str = (String) result;
        assertEquals(2, str.length());
        assertEquals(0xD800, str.charAt(0));  // Orphan high surrogate
        assertEquals('X', str.charAt(1));     // Regular character
    }

    /**
     * Test that valid surrogate pairs work correctly.
     * This is the baseline test - a valid surrogate pair for emoji.
     */
    @Test
    public void testValidSurrogatePair_ShouldWork() {
        // Valid surrogate pair for U+1F600 (grinning face emoji)
        // High surrogate: D83D, Low surrogate: DE00
        String json = "\"" + "\\u" + "D83D" + "\\u" + "DE00\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        String str = (String) result;
        // Should be a single code point (2 UTF-16 chars)
        assertEquals(2, str.length());
        assertEquals(0x1F600, str.codePointAt(0));  // The emoji code point
    }

    // ========== Tests for multi-line strings in strict JSON mode (lines 888, 894) ==========

    /**
     * Test that backslash-newline in strict JSON mode throws JsonIoException.
     * This tests line 888 of JsonParser.readString().
     */
    @Test
    public void testMultiLineStringWithNewline_StrictMode_ShouldThrowJsonIoException() {
        // Backslash followed by actual newline character (JSON5 multi-line string)
        String json = "\"hello\\\nworld\"";
        ReadOptions opts = new ReadOptionsBuilder().strictJson().build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Multi-line strings not allowed in strict JSON mode"));
    }

    /**
     * Test that backslash-carriage-return in strict JSON mode throws JsonIoException.
     * This tests line 894 of JsonParser.readString().
     */
    @Test
    public void testMultiLineStringWithCarriageReturn_StrictMode_ShouldThrowJsonIoException() {
        // Backslash followed by carriage return (JSON5 multi-line string)
        String json = "\"hello\\\rworld\"";
        ReadOptions opts = new ReadOptionsBuilder().strictJson().build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Multi-line strings not allowed in strict JSON mode"));
    }

    /**
     * Test that multi-line strings work in permissive (JSON5) mode.
     * This is the baseline test.
     */
    @Test
    public void testMultiLineString_PermissiveMode_ShouldWork() {
        // Backslash followed by newline - allowed in JSON5
        String json = "\"hello\\\nworld\"";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertEquals("helloworld", result);  // Backslash-newline is removed
    }

    // ========== Tests for readHexNumber() error handling (line 740) ==========

    /**
     * Test that hexadecimal numbers with more than 16 digits throw JsonIoException.
     * This tests line 740 of JsonParser.readHexNumber().
     * 16 hex digits = 64 bits = max Long value.
     */
    @Test
    public void testHexNumberTooLarge_ShouldThrowJsonIoException() {
        // 17 hex digits - exceeds 64-bit Long capacity
        String json = "[0x12345678901234567]";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Hexadecimal number too large"));
    }

    /**
     * Test that 16-digit hexadecimal numbers work correctly (max valid size).
     * This is the boundary test - 16 digits should work.
     */
    @Test
    public void testHexNumber16Digits_ShouldWork() {
        // 16 hex digits = exactly 64 bits - should work
        String json = "[0xFFFFFFFFFFFFFFFF]";

        Object result = JsonIo.toJava(json, null).asClass(Object.class);
        assertNotNull(result);
        assertTrue(result instanceof Object[]);
        Object[] arr = (Object[]) result;
        assertEquals(1, arr.length);
        // 0xFFFFFFFFFFFFFFFF = -1 as signed long
        assertEquals(-1L, arr[0]);
    }

    // ========== Tests for readNumberGeneral() error handling (line 604) ==========

    /**
     * Test that EOF immediately after '+' sign throws JsonIoException.
     * This tests line 604 of JsonParser.readNumberGeneral().
     * The pushback bug at line 593 was fixed to properly handle EOF.
     */
    @Test
    public void testEofAfterPlusSign_ShouldThrowJsonIoException() {
        // '+' followed by EOF - no digit after positive sign
        String json = "[+";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Unexpected end of input after '+'"));
    }

    // ========== Tests for loadKeys() error handling ==========

    /**
     * Test that @keys with a non-array value throws JsonIoException.
     * This tests loadKeys() validation in JsonParser.
     */
    @Test
    public void testKeysWithNonArrayValue_ShouldThrowJsonIoException() {
        // @keys should be an array, but we provide a string
        String json = "{\"@type\":\"java.util.LinkedHashMap\",\"@keys\":\"notanarray\",\"@items\":[1,2,3]}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @keys to have an array []"));
    }

    /**
     * Test that @keys with a number value throws JsonIoException.
     */
    @Test
    public void testKeysWithNumberValue_ShouldThrowJsonIoException() {
        // @keys should be an array, but we provide a number
        String json = "{\"@type\":\"java.util.HashMap\",\"@keys\":12345,\"@items\":[1,2,3]}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected @keys to have an array []"));
    }

    // ========== Tests for loadType() error handling ==========

    /**
     * Test that @type with a non-String value throws JsonIoException.
     * This tests loadType() validation in JsonParser.
     */
    @Test
    public void testTypeWithNonStringValue_ShouldThrowJsonIoException() {
        // @type should be a String (class name), but we provide a number
        String json = "{\"@type\":12345}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected a String for @type"));
    }

    /**
     * Test that @type with array value throws JsonIoException.
     */
    @Test
    public void testTypeWithArrayValue_ShouldThrowJsonIoException() {
        // @type should be a String, but we provide an array
        String json = "{\"@type\":[1,2,3]}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected a String for @type"));
    }

    // ========== Tests for loadId() error handling (lines 1045, 1052) ==========

    /**
     * Test that @id with null value throws JsonIoException.
     * This tests line 1038-1039 of JsonParser.loadId().
     */
    @Test
    public void testIdWithNullValue_ShouldThrowJsonIoException() {
        // @id with null value
        String json = "{\"@id\":null,\"value\":42}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Null value provided for @id"));
    }

    /**
     * Test that @id with a non-Number value throws JsonIoException.
     * This tests line 1041 of JsonParser.loadId().
     */
    @Test
    public void testIdWithNonNumberValue_ShouldThrowJsonIoException() {
        // @id should be a number, but we provide a string
        String json = "{\"@id\":\"notanumber\",\"value\":42}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected a number for @id"));
    }

    /**
     * Test that @id with value out of safe range throws JsonIoException.
     * This tests line 1052 of JsonParser.loadId().
     * Default maxIdValue is Long.MAX_VALUE, so we need a custom lower limit.
     */
    @Test
    public void testIdOutOfRange_ShouldThrowJsonIoException() {
        // Use a custom maxIdValue that's smaller
        String json = "{\"@id\":1000,\"value\":42}";
        ReadOptions opts = new ReadOptionsBuilder().maxIdValue(100).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("ID value out of safe range"));
    }

    // ========== Tests for loadRef() error handling (lines 1068, 1074, 1081) ==========

    /**
     * Test that @ref with null value throws JsonIoException.
     * This tests line 1068 of JsonParser.loadRef().
     */
    @Test
    public void testRefWithNullValue_ShouldThrowJsonIoException() {
        // @ref with null value
        String json = "{\"@ref\":null}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Null value provided for @ref"));
    }

    /**
     * Test that @ref with a non-Number value throws JsonIoException.
     * This tests line 1074 of JsonParser.loadRef().
     */
    @Test
    public void testRefWithNonNumberValue_ShouldThrowJsonIoException() {
        // @ref should be a number, but we provide a string
        String json = "{\"@ref\":\"notanumber\"}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected a number for @ref"));
    }

    /**
     * Test that @ref with value out of safe range throws JsonIoException.
     * This tests line 1081 of JsonParser.loadRef().
     */
    @Test
    public void testRefOutOfRange_ShouldThrowJsonIoException() {
        // Use a custom maxIdValue that's smaller
        String json = "{\"@ref\":1000}";
        ReadOptions opts = new ReadOptionsBuilder().maxIdValue(100).build();

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, opts).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Reference ID value out of safe range"));
    }

    // ========== Tests for loadEnum() error handling (line 1094) ==========

    /**
     * Test that @enum with a non-String value throws JsonIoException.
     * This tests line 1094 of JsonParser.loadEnum().
     */
    @Test
    public void testEnumWithNonStringValue_ShouldThrowJsonIoException() {
        // @enum should be a String (class name), but we provide a number
        String json = "{\"@enum\":12345}";

        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(json, null).asClass(Object.class);
        });

        assertTrue(exception.getMessage().contains("Expected a String for @enum"));
    }

    // ========== Baseline tests ==========

    /**
     * Test that valid tokens work correctly - baseline for error tests.
     */
    @Test
    public void testValidTokens_ShouldWork() {
        // Valid true
        Object result = JsonIo.toJava("{\"a\": true}", null).asClass(Object.class);
        assertNotNull(result);

        // Valid false
        result = JsonIo.toJava("{\"b\": false}", null).asClass(Object.class);
        assertNotNull(result);

        // Valid null
        result = JsonIo.toJava("{\"c\": null}", null).asClass(Object.class);
        assertNotNull(result);
    }
}
