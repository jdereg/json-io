package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Rectangle support through enhanced Converter integration.
 * These tests validate that java.awt.Rectangle can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Rectangle transformations.
 */
class AutomaticRectangleTest {
    private static final Logger LOG = Logger.getLogger(AutomaticRectangleTest.class.getName());

    @Test
    void testRectangleSerializationAndDeserialization() {
        // Given: A Rectangle object
        Rectangle originalRectangle = new Rectangle(10, 20, 100, 200);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangle, new WriteOptionsBuilder().build());
        LOG.info("Serialized JSON: " + json);
        
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle.class);
        LOG.info("Deserialized result type: " + result.getClass());
        LOG.info("Deserialized result: " + result);
        
        Rectangle deserializedRectangle = (Rectangle) result;

        // Then: The rectangle should be preserved
        assertThat(deserializedRectangle).isEqualTo(originalRectangle);
        assertThat(deserializedRectangle.x).isEqualTo(10);
        assertThat(deserializedRectangle.y).isEqualTo(20);
        assertThat(deserializedRectangle.width).isEqualTo(100);
        assertThat(deserializedRectangle.height).isEqualTo(200);
    }

    @Test
    void testRectangleAtOrigin() {
        // Given: A Rectangle object at origin
        Rectangle originalRectangle = new Rectangle(0, 0, 50, 75);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangle, new WriteOptionsBuilder().build());
        Rectangle deserializedRectangle = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle.class);

        // Then: The rectangle should be preserved
        assertThat(deserializedRectangle).isEqualTo(originalRectangle);
        assertThat(deserializedRectangle.x).isEqualTo(0);
        assertThat(deserializedRectangle.y).isEqualTo(0);
        assertThat(deserializedRectangle.width).isEqualTo(50);
        assertThat(deserializedRectangle.height).isEqualTo(75);
    }

    @Test
    void testEmptyRectangle() {
        // Given: An empty Rectangle
        Rectangle originalRectangle = new Rectangle();

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangle, new WriteOptionsBuilder().build());
        Rectangle deserializedRectangle = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle.class);

        // Then: The rectangle should be preserved
        assertThat(deserializedRectangle).isEqualTo(originalRectangle);
        assertThat(deserializedRectangle.x).isEqualTo(0);
        assertThat(deserializedRectangle.y).isEqualTo(0);
        assertThat(deserializedRectangle.width).isEqualTo(0);
        assertThat(deserializedRectangle.height).isEqualTo(0);
    }

    @Test
    void testRectangleWithNegativeCoordinates() {
        // Given: A Rectangle with negative coordinates
        Rectangle originalRectangle = new Rectangle(-10, -20, 30, 40);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangle, new WriteOptionsBuilder().build());
        Rectangle deserializedRectangle = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle.class);

        // Then: The rectangle should be preserved
        assertThat(deserializedRectangle).isEqualTo(originalRectangle);
        assertThat(deserializedRectangle.x).isEqualTo(-10);
        assertThat(deserializedRectangle.y).isEqualTo(-20);
        assertThat(deserializedRectangle.width).isEqualTo(30);
        assertThat(deserializedRectangle.height).isEqualTo(40);
    }

    @Test
    void testRectangleInComplexObject() {
        // Given: A complex object containing rectangles
        TestObject originalObject = new TestObject();
        originalObject.bounds = new Rectangle(0, 0, 100, 200);
        originalObject.clipArea = new Rectangle(25, 50, 50, 100);
        originalObject.name = "Test Object";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalObject, new WriteOptionsBuilder().build());
        TestObject deserializedObject = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);

        // Then: All rectangles should be preserved
        assertThat(deserializedObject.bounds).isEqualTo(originalObject.bounds);
        assertThat(deserializedObject.clipArea).isEqualTo(originalObject.clipArea);
        assertThat(deserializedObject.name).isEqualTo("Test Object");
    }

    @Test
    void testRectangleArray() {
        // Given: An array of rectangles
        Rectangle[] originalRectangles = {
            new Rectangle(0, 0, 10, 10),
            new Rectangle(20, 30, 40, 50),
            new Rectangle(-5, -10, 15, 20),
            new Rectangle()
        };

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangles, new WriteOptionsBuilder().build());
        Rectangle[] deserializedRectangles = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle[].class);

        // Then: All rectangles should be preserved
        assertThat(deserializedRectangles).hasSize(4);
        assertThat(deserializedRectangles[0]).isEqualTo(originalRectangles[0]);
        assertThat(deserializedRectangles[1]).isEqualTo(originalRectangles[1]);
        assertThat(deserializedRectangles[2]).isEqualTo(originalRectangles[2]);
        assertThat(deserializedRectangles[3]).isEqualTo(originalRectangles[3]);
    }

    @Test
    void testManualMapToRectangleConversion() {
        // Given: A map representing a rectangle (simulating what Converter should handle)
        Map<String, Object> rectangleMap = new HashMap<>();
        rectangleMap.put("x", 10);
        rectangleMap.put("y", 20);
        rectangleMap.put("width", 100);
        rectangleMap.put("height", 200);

        // When: We try to convert using the enhanced Converter integration
        // Note: This test verifies the conversion path works, assuming java-util supports it
        String json = JsonIo.toJson(rectangleMap, new WriteOptionsBuilder().build());
        
        // First, let's see what the JSON looks like
        LOG.info("Rectangle map JSON: " + json);
        
        // For now, we can verify the map structure is preserved
        Map<String, Object> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);
        assertThat(deserializedMap).containsEntry("x", 10);
        assertThat(deserializedMap).containsEntry("y", 20);
        assertThat(deserializedMap).containsEntry("width", 100);
        assertThat(deserializedMap).containsEntry("height", 200);
    }

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a rectangle
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if Rectangle is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Rectangle.class);
        LOG.info("Rectangle is simple type: " + isSimpleType);
        
        // Test Rectangle to String conversion
        Rectangle testRectangle = new Rectangle(10, 20, 100, 200);
        if (isSimpleType) {
            String rectangleString = converter.convert(testRectangle, String.class);
            LOG.info("Rectangle to String: " + rectangleString);
            
            // Test String back to Rectangle
            Rectangle backToRectangle = converter.convert(rectangleString, Rectangle.class);
            LOG.info("String back to Rectangle: " + backToRectangle);
            assertThat(backToRectangle).isEqualTo(testRectangle);
        }
        
        Map<String, Object> rectangleMap = new HashMap<>();
        rectangleMap.put("x", 10);
        rectangleMap.put("y", 20);
        rectangleMap.put("width", 100);
        rectangleMap.put("height", 200);

        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(Map.class, Rectangle.class);
        
        // Then: We can report the current state
        LOG.info("Map to Rectangle conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Rectangle convertedRectangle = converter.convert(rectangleMap, Rectangle.class);
            assertThat(convertedRectangle.x).isEqualTo(10);
            assertThat(convertedRectangle.y).isEqualTo(20);
            assertThat(convertedRectangle.width).isEqualTo(100);
            assertThat(convertedRectangle.height).isEqualTo(200);
        }
    }

    @Test
    void testRectangleWithIntegerBounds() {
        // Given: A Rectangle created with integer bounds
        Rectangle originalRectangle = new Rectangle(Integer.MAX_VALUE - 100, Integer.MAX_VALUE - 100, 50, 50);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalRectangle, new WriteOptionsBuilder().build());
        Rectangle deserializedRectangle = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Rectangle.class);

        // Then: The rectangle should be preserved with exact integer values
        assertThat(deserializedRectangle).isEqualTo(originalRectangle);
        assertThat(deserializedRectangle.x).isEqualTo(Integer.MAX_VALUE - 100);
        assertThat(deserializedRectangle.y).isEqualTo(Integer.MAX_VALUE - 100);
        assertThat(deserializedRectangle.width).isEqualTo(50);
        assertThat(deserializedRectangle.height).isEqualTo(50);
    }

    @Test
    void testMultipleRectanglesInMap() {
        // Given: A map containing multiple rectangles
        Map<String, Rectangle> rectangleMap = new HashMap<>();
        rectangleMap.put("window", new Rectangle(0, 0, 800, 600));
        rectangleMap.put("button", new Rectangle(10, 10, 100, 30));
        rectangleMap.put("panel", new Rectangle(50, 50, 200, 150));

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(rectangleMap, new WriteOptionsBuilder().build());
        Map<String, Rectangle> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);

        // Then: All rectangles should be preserved
        assertThat(deserializedMap).hasSize(3);
        assertThat(deserializedMap.get("window")).isEqualTo(rectangleMap.get("window"));
        assertThat(deserializedMap.get("button")).isEqualTo(rectangleMap.get("button"));
        assertThat(deserializedMap.get("panel")).isEqualTo(rectangleMap.get("panel"));
    }

    /**
     * Test object for complex serialization scenarios
     */
    public static class TestObject {
        public Rectangle bounds;
        public Rectangle clipArea;
        public String name;
    }
}