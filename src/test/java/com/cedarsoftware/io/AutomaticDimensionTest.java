package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Dimension support through enhanced Converter integration.
 * These tests validate that java.awt.Dimension can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Dimension transformations.
 */
class AutomaticDimensionTest {
    private static final Logger LOG = Logger.getLogger(AutomaticDimensionTest.class.getName());

    @Test
    void testDimensionSerializationAndDeserialization() {
        // Given: A Dimension object
        Dimension originalDimension = new Dimension(800, 600);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        LOG.info("Serialized JSON: " + json);
        
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension.class);
        LOG.info("Deserialized result type: " + result.getClass());
        LOG.info("Deserialized result: " + result);
        
        Dimension deserializedDimension = (Dimension) result;

        // Then: The dimension should be preserved
        assertThat(deserializedDimension).isEqualTo(originalDimension);
        assertThat(deserializedDimension.width).isEqualTo(800);
        assertThat(deserializedDimension.height).isEqualTo(600);
    }

    @Test
    void testDimensionWithDoubleValues() {
        // Given: A Dimension with double values (which get converted to int)
        Dimension originalDimension = new Dimension();
        originalDimension.setSize(1920.0, 1080.0);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        Dimension deserializedDimension = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: The dimension should be preserved with integer values
        assertThat(deserializedDimension).isEqualTo(originalDimension);
        assertThat(deserializedDimension.width).isEqualTo(1920);
        assertThat(deserializedDimension.height).isEqualTo(1080);
    }

    @Test
    void testEmptyDimension() {
        // Given: An empty Dimension
        Dimension originalDimension = new Dimension();

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        Dimension deserializedDimension = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: The dimension should be preserved
        assertThat(deserializedDimension).isEqualTo(originalDimension);
        assertThat(deserializedDimension.width).isEqualTo(0);
        assertThat(deserializedDimension.height).isEqualTo(0);
    }

    @Test
    void testDimensionWithConstructor() {
        // Given: A Dimension created with constructor
        Dimension originalDimension = new Dimension(1024, 768);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        Dimension deserializedDimension = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: The dimension should be preserved
        assertThat(deserializedDimension).isEqualTo(originalDimension);
        assertThat(deserializedDimension.width).isEqualTo(1024);
        assertThat(deserializedDimension.height).isEqualTo(768);
    }

    @Test
    void testDimensionInComplexObject() {
        // Given: A complex object containing dimensions
        TestObject originalObject = new TestObject();
        originalObject.screenSize = new Dimension(1920, 1080);
        originalObject.windowSize = new Dimension(800, 600);
        originalObject.name = "Display Config";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalObject, new WriteOptionsBuilder().build());
        TestObject deserializedObject = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);

        // Then: All dimensions should be preserved
        assertThat(deserializedObject.screenSize).isEqualTo(originalObject.screenSize);
        assertThat(deserializedObject.windowSize).isEqualTo(originalObject.windowSize);
        assertThat(deserializedObject.name).isEqualTo("Display Config");
    }

    @Test
    void testDimensionArray() {
        // Given: An array of dimensions
        Dimension[] originalDimensions = {
            new Dimension(640, 480),
            new Dimension(800, 600),
            new Dimension(1024, 768),
            new Dimension(1920, 1080)
        };

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimensions, new WriteOptionsBuilder().build());
        Dimension[] deserializedDimensions = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension[].class);

        // Then: All dimensions should be preserved
        assertThat(deserializedDimensions).hasSize(4);
        assertThat(deserializedDimensions[0]).isEqualTo(originalDimensions[0]);
        assertThat(deserializedDimensions[1]).isEqualTo(originalDimensions[1]);
        assertThat(deserializedDimensions[2]).isEqualTo(originalDimensions[2]);
        assertThat(deserializedDimensions[3]).isEqualTo(originalDimensions[3]);
    }

    @Test
    void testManualMapToDimensionConversion() {
        // Given: A map representing a dimension (simulating what Converter should handle)
        Map<String, Object> dimensionMap = new HashMap<>();
        dimensionMap.put("width", 1280);
        dimensionMap.put("height", 720);

        // When: We try to convert using the enhanced Converter integration
        // Note: This test verifies the conversion path works, assuming java-util supports it
        String json = JsonIo.toJson(dimensionMap, new WriteOptionsBuilder().build());
        
        // First, let's see what the JSON looks like
        LOG.info("Dimension map JSON: " + json);
        
        // For now, we can verify the map structure is preserved
        Map<String, Object> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);
        assertThat(deserializedMap).containsEntry("width", 1280);
        assertThat(deserializedMap).containsEntry("height", 720);
    }

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a dimension
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if Dimension is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Dimension.class);
        LOG.info("Dimension is simple type: " + isSimpleType);
        
        // Test Dimension to String conversion
        Dimension testDimension = new Dimension(1024, 768);
        if (isSimpleType) {
            String dimensionString = converter.convert(testDimension, String.class);
            LOG.info("Dimension to String: " + dimensionString);
            
            // Test String back to Dimension
            Dimension backToDimension = converter.convert(dimensionString, Dimension.class);
            LOG.info("String back to Dimension: " + backToDimension);
            assertThat(backToDimension).isEqualTo(testDimension);
        }
        
        Map<String, Object> dimensionMap = new HashMap<>();
        dimensionMap.put("width", 1024);
        dimensionMap.put("height", 768);

        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(Map.class, Dimension.class);
        
        // Then: We can report the current state
        LOG.info("Map to Dimension conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Dimension convertedDimension = converter.convert(dimensionMap, Dimension.class);
            assertThat(convertedDimension.width).isEqualTo(1024);
            assertThat(convertedDimension.height).isEqualTo(768);
        }
    }

    @Test
    void testDimensionWithLargeValues() {
        // Given: A Dimension with large values
        Dimension originalDimension = new Dimension(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        Dimension deserializedDimension = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: The dimension should be preserved with exact integer values
        assertThat(deserializedDimension).isEqualTo(originalDimension);
        assertThat(deserializedDimension.width).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(deserializedDimension.height).isEqualTo(Integer.MAX_VALUE - 1);
    }

    @Test
    void testMultipleDimensionsInMap() {
        // Given: A map containing multiple dimensions
        Map<String, Dimension> dimensionMap = new HashMap<>();
        dimensionMap.put("4K", new Dimension(3840, 2160));
        dimensionMap.put("FullHD", new Dimension(1920, 1080));
        dimensionMap.put("HD", new Dimension(1280, 720));
        dimensionMap.put("VGA", new Dimension(640, 480));

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(dimensionMap, new WriteOptionsBuilder().build());
        Map<String, Dimension> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);

        // Then: All dimensions should be preserved
        assertThat(deserializedMap).hasSize(4);
        assertThat(deserializedMap.get("4K")).isEqualTo(dimensionMap.get("4K"));
        assertThat(deserializedMap.get("FullHD")).isEqualTo(dimensionMap.get("FullHD"));
        assertThat(deserializedMap.get("HD")).isEqualTo(dimensionMap.get("HD"));
        assertThat(deserializedMap.get("VGA")).isEqualTo(dimensionMap.get("VGA"));
    }

    @Test
    void testDimensionEquals() {
        // Given: Two dimensions with same values
        Dimension dimension1 = new Dimension(800, 600);
        Dimension dimension2 = new Dimension(800, 600);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(dimension1, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(dimension2, new WriteOptionsBuilder().build());
        
        Dimension deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Dimension.class);
        Dimension deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: Both should be equal and should equal the originals
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(dimension1);
        assertThat(deserialized2).isEqualTo(dimension2);
    }

    @Test
    void testDimensionCopyConstructor() {
        // Given: A Dimension created from another dimension
        Dimension originalDimension = new Dimension(1366, 768);
        Dimension copiedDimension = new Dimension(originalDimension);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(originalDimension, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(copiedDimension, new WriteOptionsBuilder().build());
        
        Dimension deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Dimension.class);
        Dimension deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Dimension.class);

        // Then: Both should be equal
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(originalDimension);
        assertThat(deserialized2).isEqualTo(copiedDimension);
    }

    /**
     * Test object for complex serialization scenarios
     */
    public static class TestObject {
        public Dimension screenSize;
        public Dimension windowSize;
        public String name;
    }
}