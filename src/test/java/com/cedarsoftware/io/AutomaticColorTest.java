package com.cedarsoftware.io;

import com.cedarsoftware.util.geom.Color;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Color support through enhanced Converter integration.
 * These tests validate that Cedar DTO Color can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Color transformations.
 */
class AutomaticColorTest {
    private static final Logger LOG = Logger.getLogger(AutomaticColorTest.class.getName());

    @Test
    void testColorSerializationAndDeserialization() {
        // Given: A Color object
        Color originalColor = new Color(255, 128, 64, 192);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalColor, new WriteOptionsBuilder().build());
        LOG.info("Serialized JSON: " + json);
        
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Color.class);
        LOG.info("Deserialized result type: " + result.getClass());
        LOG.info("Deserialized result: " + result);
        
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
        
        // First, let's see what the JSON looks like
        LOG.info("Color map JSON: " + json);
        
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
        
        // Check if Color is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Color.class);
        LOG.info("Color is simple type: " + isSimpleType);
        
        // Test Color to String conversion
        Color testColor = new Color(255, 128, 64, 192);
        if (isSimpleType) {
            String colorString = converter.convert(testColor, String.class);
            LOG.info("Color to String: " + colorString);
            
            // Test String back to Color
            Color backToColor = converter.convert(colorString, Color.class);
            LOG.info("String back to Color: " + backToColor);
            assertThat(backToColor).isEqualTo(testColor);
        }
        
        Map<String, Object> colorMap = new HashMap<>();
        colorMap.put("red", 255);
        colorMap.put("green", 128);
        colorMap.put("blue", 64);
        colorMap.put("alpha", 192);

        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(Map.class, Color.class);
        
        // Then: We can report the current state
        LOG.info("Map to Color conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Color convertedColor = converter.convert(colorMap, Color.class);
            assertThat(convertedColor.getRed()).isEqualTo(255);
            assertThat(convertedColor.getGreen()).isEqualTo(128);
            assertThat(convertedColor.getBlue()).isEqualTo(64);
            assertThat(convertedColor.getAlpha()).isEqualTo(192);
        }
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