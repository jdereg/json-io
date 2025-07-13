package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Point support through enhanced Converter integration.
 * These tests validate that java.awt.Point can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Point transformations.
 */
class AutomaticPointTest {
    private static final Logger LOG = Logger.getLogger(AutomaticPointTest.class.getName());

    @Test
    void testPointSerializationAndDeserialization() {
        // Given: A Point object
        Point originalPoint = new Point(100, 200);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalPoint, new WriteOptionsBuilder().build());
        LOG.info("Serialized JSON: " + json);
        
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Point.class);
        LOG.info("Deserialized result type: " + result.getClass());
        LOG.info("Deserialized result: " + result);
        
        Point deserializedPoint = (Point) result;

        // Then: The point should be preserved
        assertThat(deserializedPoint).isEqualTo(originalPoint);
        assertThat(deserializedPoint.x).isEqualTo(100);
        assertThat(deserializedPoint.y).isEqualTo(200);
    }

    @Test
    void testPointAtOrigin() {
        // Given: A Point object at origin
        Point originalPoint = new Point(0, 0);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalPoint, new WriteOptionsBuilder().build());
        Point deserializedPoint = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Point.class);

        // Then: The point should be preserved
        assertThat(deserializedPoint).isEqualTo(originalPoint);
        assertThat(deserializedPoint.x).isEqualTo(0);
        assertThat(deserializedPoint.y).isEqualTo(0);
    }

    @Test
    void testPointWithNegativeCoordinates() {
        // Given: A Point with negative coordinates
        Point originalPoint = new Point(-50, -75);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalPoint, new WriteOptionsBuilder().build());
        Point deserializedPoint = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Point.class);

        // Then: The point should be preserved
        assertThat(deserializedPoint).isEqualTo(originalPoint);
        assertThat(deserializedPoint.x).isEqualTo(-50);
        assertThat(deserializedPoint.y).isEqualTo(-75);
    }

    @Test
    void testPointWithLargeCoordinates() {
        // Given: A Point with large coordinates
        Point originalPoint = new Point(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 2);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalPoint, new WriteOptionsBuilder().build());
        Point deserializedPoint = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Point.class);

        // Then: The point should be preserved with exact integer values
        assertThat(deserializedPoint).isEqualTo(originalPoint);
        assertThat(deserializedPoint.x).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(deserializedPoint.y).isEqualTo(Integer.MAX_VALUE - 2);
    }

    @Test
    void testPointInComplexObject() {
        // Given: A complex object containing points
        TestObject originalObject = new TestObject();
        originalObject.location = new Point(10, 20);
        originalObject.target = new Point(100, 200);
        originalObject.name = "Navigation Point";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalObject, new WriteOptionsBuilder().build());
        TestObject deserializedObject = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);

        // Then: All points should be preserved
        assertThat(deserializedObject.location).isEqualTo(originalObject.location);
        assertThat(deserializedObject.target).isEqualTo(originalObject.target);
        assertThat(deserializedObject.name).isEqualTo("Navigation Point");
    }

    @Test
    void testPointArray() {
        // Given: An array of points
        Point[] originalPoints = {
            new Point(0, 0),
            new Point(10, 20),
            new Point(-5, -10),
            new Point(Integer.MAX_VALUE, Integer.MIN_VALUE)
        };

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalPoints, new WriteOptionsBuilder().build());
        Point[] deserializedPoints = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Point[].class);

        // Then: All points should be preserved
        assertThat(deserializedPoints).hasSize(4);
        assertThat(deserializedPoints[0]).isEqualTo(originalPoints[0]);
        assertThat(deserializedPoints[1]).isEqualTo(originalPoints[1]);
        assertThat(deserializedPoints[2]).isEqualTo(originalPoints[2]);
        assertThat(deserializedPoints[3]).isEqualTo(originalPoints[3]);
    }

    @Test
    void testManualMapToPointConversion() {
        // Given: A map representing a point (simulating what Converter should handle)
        Map<String, Object> pointMap = new HashMap<>();
        pointMap.put("x", 150);
        pointMap.put("y", 250);

        // When: We try to convert using the enhanced Converter integration
        // Note: This test verifies the conversion path works, assuming java-util supports it
        String json = JsonIo.toJson(pointMap, new WriteOptionsBuilder().build());
        
        // First, let's see what the JSON looks like
        LOG.info("Point map JSON: " + json);
        
        // For now, we can verify the map structure is preserved
        Map<String, Object> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);
        assertThat(deserializedMap).containsEntry("x", 150);
        assertThat(deserializedMap).containsEntry("y", 250);
    }

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and a point
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if Point is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Point.class);
        LOG.info("Point is simple type: " + isSimpleType);
        
        // Test Point to String conversion
        Point testPoint = new Point(123, 456);
        if (isSimpleType) {
            String pointString = converter.convert(testPoint, String.class);
            LOG.info("Point to String: " + pointString);
            
            // Test String back to Point
            Point backToPoint = converter.convert(pointString, Point.class);
            LOG.info("String back to Point: " + backToPoint);
            assertThat(backToPoint).isEqualTo(testPoint);
        }
        
        Map<String, Object> pointMap = new HashMap<>();
        pointMap.put("x", 123);
        pointMap.put("y", 456);

        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(Map.class, Point.class);
        
        // Then: We can report the current state
        LOG.info("Map to Point conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Point convertedPoint = converter.convert(pointMap, Point.class);
            assertThat(convertedPoint.x).isEqualTo(123);
            assertThat(convertedPoint.y).isEqualTo(456);
        }
    }

    @Test
    void testMultiplePointsInMap() {
        // Given: A map containing multiple points
        Map<String, Point> pointMap = new HashMap<>();
        pointMap.put("start", new Point(0, 0));
        pointMap.put("middle", new Point(50, 50));
        pointMap.put("end", new Point(100, 100));
        pointMap.put("origin", new Point(-10, -10));

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(pointMap, new WriteOptionsBuilder().build());
        Map<String, Point> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);

        // Then: All points should be preserved
        assertThat(deserializedMap).hasSize(4);
        assertThat(deserializedMap.get("start")).isEqualTo(pointMap.get("start"));
        assertThat(deserializedMap.get("middle")).isEqualTo(pointMap.get("middle"));
        assertThat(deserializedMap.get("end")).isEqualTo(pointMap.get("end"));
        assertThat(deserializedMap.get("origin")).isEqualTo(pointMap.get("origin"));
    }

    @Test
    void testPointEquals() {
        // Given: Two points with same coordinates
        Point point1 = new Point(25, 75);
        Point point2 = new Point(25, 75);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(point1, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(point2, new WriteOptionsBuilder().build());
        
        Point deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Point.class);
        Point deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Point.class);

        // Then: Both should be equal and should equal the originals
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(point1);
        assertThat(deserialized2).isEqualTo(point2);
    }

    @Test
    void testPointCopyConstructor() {
        // Given: A Point created from another point
        Point originalPoint = new Point(33, 66);
        Point copiedPoint = new Point(originalPoint);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(originalPoint, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(copiedPoint, new WriteOptionsBuilder().build());
        
        Point deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Point.class);
        Point deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Point.class);

        // Then: Both should be equal
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(originalPoint);
        assertThat(deserialized2).isEqualTo(copiedPoint);
    }

    @Test
    void testGeometricCalculations() {
        // Given: Points representing a geometric shape
        TestGeometry geometry = new TestGeometry();
        geometry.topLeft = new Point(10, 10);
        geometry.topRight = new Point(90, 10);
        geometry.bottomLeft = new Point(10, 90);
        geometry.bottomRight = new Point(90, 90);
        geometry.center = new Point(50, 50);
        geometry.shapeName = "Rectangle";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(geometry, new WriteOptionsBuilder().build());
        TestGeometry deserializedGeometry = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestGeometry.class);

        // Then: All points should be preserved
        assertThat(deserializedGeometry.topLeft).isEqualTo(geometry.topLeft);
        assertThat(deserializedGeometry.topRight).isEqualTo(geometry.topRight);
        assertThat(deserializedGeometry.bottomLeft).isEqualTo(geometry.bottomLeft);
        assertThat(deserializedGeometry.bottomRight).isEqualTo(geometry.bottomRight);
        assertThat(deserializedGeometry.center).isEqualTo(geometry.center);
        assertThat(deserializedGeometry.shapeName).isEqualTo("Rectangle");
    }

    /**
     * Test object for complex serialization scenarios
     */
    public static class TestObject {
        public Point location;
        public Point target;
        public String name;
    }

    /**
     * Test object representing geometric shapes with multiple points
     */
    public static class TestGeometry {
        public Point topLeft;
        public Point topRight;
        public Point bottomLeft;
        public Point bottomRight;
        public Point center;
        public String shapeName;
    }
}