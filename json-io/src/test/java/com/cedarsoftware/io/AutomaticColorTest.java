package com.cedarsoftware.io;

import com.cedarsoftware.util.geom.Color;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Color support through enhanced Converter integration.
 * These tests validate that Cedar DTO Color can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Color transformations.
 */
class AutomaticColorTest {

    @Test
    void testColorSerializationAndDeserialization() {
        // Given: A Color object
        Color originalColor = new Color(255, 128, 64, 192);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalColor, new WriteOptionsBuilder().build());
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Color.class);
        Color deserializedColor = (Color) result;

        // Then: The color should be preserved
        assertThat(deserializedColor).isEqualTo(originalColor);
        assertThat(deserializedColor.getRed()).isEqualTo(255);
        assertThat(deserializedColor.getGreen()).isEqualTo(128);
        assertThat(deserializedColor.getBlue()).isEqualTo(64);
        assertThat(deserializedColor.getAlpha()).isEqualTo(192);
    }

    @Test
    void testColorWithoutAlpha() {
        // Given: A Color object without alpha
        Color originalColor = new Color(100, 150, 200);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalColor, new WriteOptionsBuilder().build());
        Color deserializedColor = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Color.class);

        // Then: The color should be preserved with full alpha
        assertThat(deserializedColor).isEqualTo(originalColor);
        assertThat(deserializedColor.getRed()).isEqualTo(100);
        assertThat(deserializedColor.getGreen()).isEqualTo(150);
        assertThat(deserializedColor.getBlue()).isEqualTo(200);
        assertThat(deserializedColor.getAlpha()).isEqualTo(255);
    }

    @Test
    void testColorInComplexObject() {
        // Given: A complex object containing colors
        TestObject originalObject = new TestObject();
        originalObject.backgroundColor = Color.WHITE;
        originalObject.foregroundColor = new Color(50, 100, 150, 200);
        originalObject.name = "Test Object";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalObject, new WriteOptionsBuilder().build());
        TestObject deserializedObject = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);

        // Then: All colors should be preserved
        assertThat(deserializedObject.backgroundColor).isEqualTo(Color.WHITE);
        assertThat(deserializedObject.foregroundColor).isEqualTo(originalObject.foregroundColor);
        assertThat(deserializedObject.name).isEqualTo("Test Object");
    }

    @Test
    void testColorArray() {
        // Given: An array of colors
        Color[] originalColors = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            new Color(255, 255, 0, 128)
        };

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalColors, new WriteOptionsBuilder().build());
        Color[] deserializedColors = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Color[].class);

        // Then: All colors should be preserved
        assertThat(deserializedColors).hasSize(4);
        assertThat(deserializedColors[0]).isEqualTo(Color.RED);
        assertThat(deserializedColors[1]).isEqualTo(Color.GREEN);
        assertThat(deserializedColors[2]).isEqualTo(Color.BLUE);
        assertThat(deserializedColors[3]).isEqualTo(originalColors[3]);
    }

    @Test
    void testManualMapToColorConversion() {
        // Given: A map representing a color (simulating what Converter should handle)
        Map<String, Object> colorMap = new HashMap<>();
        colorMap.put("r", 255);
        colorMap.put("g", 128);
        colorMap.put("b", 64);
        colorMap.put("a", 192);

        // When: We try to convert using the enhanced Converter integration
        // Note: This test verifies the conversion path works, assuming java-util supports it
        String json = JsonIo.toJson(colorMap, new WriteOptionsBuilder().build());
        
        // For now, we can't fully test this until java-util has Color conversion support
        // But we can verify the map structure is preserved
        Map<String, Object> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);
        assertThat(deserializedMap).containsEntry("r", 255);
        assertThat(deserializedMap).containsEntry("g", 128);
        assertThat(deserializedMap).containsEntry("b", 64);
        assertThat(deserializedMap).containsEntry("a", 192);
    }

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a color map
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Color should be considered a "simple type"
        assertThat(Resolver.isPseudoPrimitive(Color.class)).isTrue();

        // Test Color to String round-trip conversion
        Color testColor = new Color(255, 128, 64, 192);
        String colorString = converter.convert(testColor, String.class);
        assertThat(colorString).isNotEmpty();

        Color backToColor = converter.convert(colorString, Color.class);
        assertThat(backToColor).isEqualTo(testColor);

        // Verify Map to Color conversion is supported
        assertThat(converter.isConversionSupportedFor(Map.class, Color.class)).isTrue();

        Map<String, Object> colorMap = new HashMap<>();
        colorMap.put("red", 255);
        colorMap.put("green", 128);
        colorMap.put("blue", 64);
        colorMap.put("alpha", 192);

        Color convertedColor = converter.convert(colorMap, Color.class);
        assertThat(convertedColor.getRed()).isEqualTo(255);
        assertThat(convertedColor.getGreen()).isEqualTo(128);
        assertThat(convertedColor.getBlue()).isEqualTo(64);
        assertThat(convertedColor.getAlpha()).isEqualTo(192);
    }

    /**
     * Test object for complex serialization scenarios
     */
    public static class TestObject {
        public Color backgroundColor;
        public Color foregroundColor;
        public String name;
    }
}
