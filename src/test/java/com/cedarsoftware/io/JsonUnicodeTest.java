package com.cedarsoftware.io;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonUnicodeTest {

    /**
     * Test class with a field containing Unicode characters
     */
    public static class UnicodeContainer {
        private String text;

        public UnicodeContainer() {
        }

        public UnicodeContainer(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UnicodeContainer that = (UnicodeContainer) o;
            return text != null ? text.equals(that.text) : that.text == null;
        }

        @Override
        public int hashCode() {
            return text != null ? text.hashCode() : 0;
        }
    }

    @Test
    public void testAsciiCharacters() throws IOException {
        // Simple ASCII string
        String ascii = "Hello, world! 123 !@#$%^&*()";
        UnicodeContainer container = new UnicodeContainer(ascii);

        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(container, result, "ASCII characters should be preserved");
        assertEquals(ascii, result.getText(), "ASCII characters should match exactly");
    }

    @Test
    public void testBasicMultilingualPlaneCharacters() throws IOException {
        // Characters from BMP (Basic Multilingual Plane, U+0000 to U+FFFF)
        // Includes Latin, Greek, Cyrillic, Hebrew, Arabic, etc.
        String bmpChars = "English: Hello\n" +
                "Greek: Γειά σου\n" +
                "Russian: Привет\n" +
                "Hebrew: שלום\n" +
                "Arabic: مرحبا\n" +
                "Chinese: 你好\n" +
                "Japanese: こんにちは\n" +
                "Korean: 안녕하세요\n" +
                "Mathematical: ∑∫∞≈≠≤≥";

        UnicodeContainer container = new UnicodeContainer(bmpChars);

        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(container, result, "BMP characters should be preserved");
        assertEquals(bmpChars, result.getText(), "BMP characters should match exactly");
    }

    @Test
    public void testSupplementaryPlaneCharacters() throws IOException {
        // Characters from supplementary planes (above U+FFFF)
        // These require surrogate pairs in JSON and in Java's UTF-16 encoding
        String supplementaryChars = "Emoji: 😀🍕🚀🌍🎉💻🔥🎸🏆\n" +
                "Ancient scripts: 𐀀𐀁𐀂𐀃\n" +  // Linear B syllables
                "Math: 𝔸𝕊ℂ𝕀𝕀\n" +            // Mathematical alphanumeric symbols
                "Games: 🎮🎲🎯🎪\n" +        // Game symbols
                "Music: 🎵🎶🎹🎺\n" +        // Music symbols
                "Food: 🍇🍈🍉🍊🍎🍗🍔\n" +   // Food symbols
                "Mixed characters: A𝄞😊б";   // Mix of ASCII, BMP, and supplementary

        UnicodeContainer container = new UnicodeContainer(supplementaryChars);

        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(container, result, "Supplementary characters should be preserved");
        assertEquals(supplementaryChars, result.getText(), "Supplementary characters should match exactly");

        // Specifically test surrogate pairs
        String surrogatePair = "🍺"; // Beer mug: U+1F37A (surrogate pair D83C DF7A)
        UnicodeContainer beerContainer = new UnicodeContainer(surrogatePair);

        String beerJson = JsonIo.toJson(beerContainer, null);
        UnicodeContainer beerResult = JsonIo.toJava(beerJson, null).asClass(UnicodeContainer.class);

        assertEquals(beerContainer, beerResult, "Surrogate pair should be preserved");
        assertEquals(surrogatePair, beerResult.getText(), "Surrogate pair should match exactly");
        assertEquals(0x1F37A, surrogatePair.codePointAt(0), "Code point should be correct");
    }

    @Test
    public void testEscapedUnicodeCharacters() throws IOException {
        // Create string with explicit unicode escape sequences
        StringBuilder builder = new StringBuilder();
        // Add some BMP characters with explicit escapes
        builder.append('\u0041'); // Latin A
        builder.append('\u03B1'); // Greek alpha
        builder.append('\u0414'); // Cyrillic DE

        // Create a surrogate pair programmatically
        // Beer mug emoji U+1F37A -> surrogate pair U+D83C U+DF7A
        builder.append('\uD83C');
        builder.append('\uDF7A');

        String escapedString = builder.toString();
        UnicodeContainer container = new UnicodeContainer(escapedString);

        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(container, result, "Escaped Unicode characters should be preserved");
        assertEquals(escapedString, result.getText(), "Escaped Unicode characters should match exactly");
        assertEquals("AαД🍺", escapedString, "String should be: A, alpha, DE, beer mug");
    }

    @Test
    public void testMixedUnicodeCharacters() throws IOException {
        // Create a really complex string with characters from all categories
        String complexString = "ASCII: abc123\n" +
                "BMP: Γειά안녕こんにちは你好\n" +
                "Emoji: 😀🍕🚀\n" +
                "Control characters: \t\r\n\b\f\\\"\n" +
                "Surrogate pairs: " + new String(Character.toChars(0x1F421)) + // cat face
                new String(Character.toChars(0x1F435)) + // monkey face
                new String(Character.toChars(0x1F437)) + // pig face
                "Private use area: \uE000\uF8FF\n" +
                "Mixed text: Hello 你好 αβγ -> 💻 ⌨️ 📱 → 😊";

        UnicodeContainer container = new UnicodeContainer(complexString);

        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(container, result, "Complex Unicode string should be preserved");
        assertEquals(complexString, result.getText(), "Complex Unicode string should match exactly");
    }

    @Test
    public void testCharacterCounts() throws IOException {
        // Test with a string containing a mix of surrogate pairs and regular chars
        String testString = "a💻b🎮c";

        UnicodeContainer container = new UnicodeContainer(testString);
        String json = JsonIo.toJson(container, null);
        UnicodeContainer result = JsonIo.toJava(json, null).asClass(UnicodeContainer.class);

        assertEquals(testString.length(), result.getText().length(), "Unicode string length should match");
        assertEquals(testString.codePointCount(0, testString.length()),
                result.getText().codePointCount(0, result.getText().length()),
                "Unicode code point count should match");

        // String is only 5 characters long but 7 Java char units due to surrogate pairs
        assertEquals(7, testString.length(), "String should have 7 char units");
        assertEquals(5, testString.codePointCount(0, testString.length()), "String should have 5 code points");
    }
}