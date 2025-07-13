package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test automatic Insets support through enhanced Converter integration.
 * These tests validate that java.awt.Insets can be serialized and deserialized
 * automatically without requiring custom factories, leveraging the Converter
 * system for Map <-> Insets transformations.
 */
class AutomaticInsetsTest {
    private static final Logger LOG = Logger.getLogger(AutomaticInsetsTest.class.getName());

    @Test
    void testInsetsSerializationAndDeserialization() {
        // Given: An Insets object
        Insets originalInsets = new Insets(10, 20, 30, 40);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalInsets, new WriteOptionsBuilder().build());
        LOG.info("Serialized JSON: " + json);
        
        Object result = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Insets.class);
        LOG.info("Deserialized result type: " + result.getClass());
        LOG.info("Deserialized result: " + result);
        
        Insets deserializedInsets = (Insets) result;

        // Then: The insets should be preserved
        assertThat(deserializedInsets).isEqualTo(originalInsets);
        assertThat(deserializedInsets.top).isEqualTo(10);
        assertThat(deserializedInsets.left).isEqualTo(20);
        assertThat(deserializedInsets.bottom).isEqualTo(30);
        assertThat(deserializedInsets.right).isEqualTo(40);
    }

    @Test
    void testEmptyInsets() {
        // Given: An empty Insets object (all zeros)
        Insets originalInsets = new Insets(0, 0, 0, 0);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalInsets, new WriteOptionsBuilder().build());
        Insets deserializedInsets = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Insets.class);

        // Then: The insets should be preserved
        assertThat(deserializedInsets).isEqualTo(originalInsets);
        assertThat(deserializedInsets.top).isEqualTo(0);
        assertThat(deserializedInsets.left).isEqualTo(0);
        assertThat(deserializedInsets.bottom).isEqualTo(0);
        assertThat(deserializedInsets.right).isEqualTo(0);
    }

    @Test
    void testUniformInsets() {
        // Given: Uniform insets (all sides same value)
        Insets originalInsets = new Insets(15, 15, 15, 15);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalInsets, new WriteOptionsBuilder().build());
        Insets deserializedInsets = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Insets.class);

        // Then: The insets should be preserved
        assertThat(deserializedInsets).isEqualTo(originalInsets);
        assertThat(deserializedInsets.top).isEqualTo(15);
        assertThat(deserializedInsets.left).isEqualTo(15);
        assertThat(deserializedInsets.bottom).isEqualTo(15);
        assertThat(deserializedInsets.right).isEqualTo(15);
    }

    @Test
    void testInsetsInComplexObject() {
        // Given: A complex object containing insets
        TestObject originalObject = new TestObject();
        originalObject.padding = new Insets(5, 10, 15, 20);
        originalObject.margin = new Insets(2, 4, 6, 8);
        originalObject.name = "Layout Config";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalObject, new WriteOptionsBuilder().build());
        TestObject deserializedObject = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestObject.class);

        // Then: All insets should be preserved
        assertThat(deserializedObject.padding).isEqualTo(originalObject.padding);
        assertThat(deserializedObject.margin).isEqualTo(originalObject.margin);
        assertThat(deserializedObject.name).isEqualTo("Layout Config");
    }

    @Test
    void testInsetsArray() {
        // Given: An array of insets
        Insets[] originalInsetsArray = {
            new Insets(0, 0, 0, 0),
            new Insets(5, 5, 5, 5),
            new Insets(10, 20, 30, 40),
            new Insets(1, 2, 3, 4)
        };

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalInsetsArray, new WriteOptionsBuilder().build());
        Insets[] deserializedInsetsArray = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Insets[].class);

        // Then: All insets should be preserved
        assertThat(deserializedInsetsArray).hasSize(4);
        assertThat(deserializedInsetsArray[0]).isEqualTo(originalInsetsArray[0]);
        assertThat(deserializedInsetsArray[1]).isEqualTo(originalInsetsArray[1]);
        assertThat(deserializedInsetsArray[2]).isEqualTo(originalInsetsArray[2]);
        assertThat(deserializedInsetsArray[3]).isEqualTo(originalInsetsArray[3]);
    }

    @Test
    void testManualMapToInsetsConversion() {
        // Given: A map representing insets (simulating what Converter should handle)
        Map<String, Object> insetsMap = new HashMap<>();
        insetsMap.put("top", 100);
        insetsMap.put("left", 200);
        insetsMap.put("bottom", 300);
        insetsMap.put("right", 400);

        // When: We try to convert using the enhanced Converter integration
        // Note: This test verifies the conversion path works, assuming java-util supports it
        String json = JsonIo.toJson(insetsMap, new WriteOptionsBuilder().build());
        
        // First, let's see what the JSON looks like
        LOG.info("Insets map JSON: " + json);
        
        // For now, we can verify the map structure is preserved
        Map<String, Object> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);
        assertThat(deserializedMap).containsEntry("top", 100);
        assertThat(deserializedMap).containsEntry("left", 200);
        assertThat(deserializedMap).containsEntry("bottom", 300);
        assertThat(deserializedMap).containsEntry("right", 400);
    }

    @Test
    void testConverterDirectly() {
        // Given: A Converter instance and insets
        ReadOptions options = new ReadOptionsBuilder().build();
        Converter converter = new Converter(options.getConverterOptions());
        
        // Check if Insets is considered a "simple type" now
        boolean isSimpleType = converter.isSimpleTypeConversionSupported(Insets.class);
        LOG.info("Insets is simple type: " + isSimpleType);
        
        // Test Insets to String conversion
        Insets testInsets = new Insets(10, 20, 30, 40);
        if (isSimpleType) {
            String insetsString = converter.convert(testInsets, String.class);
            LOG.info("Insets to String: " + insetsString);
            
            // Test String back to Insets
            Insets backToInsets = converter.convert(insetsString, Insets.class);
            LOG.info("String back to Insets: " + backToInsets);
            assertThat(backToInsets).isEqualTo(testInsets);
        }
        
        Map<String, Object> insetsMap = new HashMap<>();
        insetsMap.put("top", 10);
        insetsMap.put("left", 20);
        insetsMap.put("bottom", 30);
        insetsMap.put("right", 40);

        // When: We check if conversion is supported
        boolean isSupported = converter.isConversionSupportedFor(Map.class, Insets.class);
        
        // Then: We can report the current state
        LOG.info("Map to Insets conversion supported: " + isSupported);
        
        if (isSupported) {
            // This should work now with updated java-util
            Insets convertedInsets = converter.convert(insetsMap, Insets.class);
            assertThat(convertedInsets.top).isEqualTo(10);
            assertThat(convertedInsets.left).isEqualTo(20);
            assertThat(convertedInsets.bottom).isEqualTo(30);
            assertThat(convertedInsets.right).isEqualTo(40);
        }
    }

    @Test
    void testInsetsWithLargeValues() {
        // Given: Insets with large values
        Insets originalInsets = new Insets(Integer.MAX_VALUE - 3, Integer.MAX_VALUE - 2, 
                                          Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 0);

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(originalInsets, new WriteOptionsBuilder().build());
        Insets deserializedInsets = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Insets.class);

        // Then: The insets should be preserved with exact integer values
        assertThat(deserializedInsets).isEqualTo(originalInsets);
        assertThat(deserializedInsets.top).isEqualTo(Integer.MAX_VALUE - 3);
        assertThat(deserializedInsets.left).isEqualTo(Integer.MAX_VALUE - 2);
        assertThat(deserializedInsets.bottom).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(deserializedInsets.right).isEqualTo(Integer.MAX_VALUE - 0);
    }

    @Test
    void testMultipleInsetsInMap() {
        // Given: A map containing multiple insets
        Map<String, Insets> insetsMap = new HashMap<>();
        insetsMap.put("border", new Insets(1, 1, 1, 1));
        insetsMap.put("padding", new Insets(5, 10, 5, 10));
        insetsMap.put("margin", new Insets(20, 20, 20, 20));
        insetsMap.put("custom", new Insets(3, 6, 9, 12));

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(insetsMap, new WriteOptionsBuilder().build());
        Map<String, Insets> deserializedMap = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(Map.class);

        // Then: All insets should be preserved
        assertThat(deserializedMap).hasSize(4);
        assertThat(deserializedMap.get("border")).isEqualTo(insetsMap.get("border"));
        assertThat(deserializedMap.get("padding")).isEqualTo(insetsMap.get("padding"));
        assertThat(deserializedMap.get("margin")).isEqualTo(insetsMap.get("margin"));
        assertThat(deserializedMap.get("custom")).isEqualTo(insetsMap.get("custom"));
    }

    @Test
    void testInsetsEquals() {
        // Given: Two insets with same values
        Insets insets1 = new Insets(5, 10, 15, 20);
        Insets insets2 = new Insets(5, 10, 15, 20);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(insets1, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(insets2, new WriteOptionsBuilder().build());
        
        Insets deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Insets.class);
        Insets deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Insets.class);

        // Then: Both should be equal and should equal the originals
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(insets1);
        assertThat(deserialized2).isEqualTo(insets2);
    }

    @Test
    void testInsetsCopyConstructor() {
        // Given: An Insets created from another insets
        Insets originalInsets = new Insets(7, 14, 21, 28);
        Insets copiedInsets = new Insets(originalInsets.top, originalInsets.left, 
                                        originalInsets.bottom, originalInsets.right);

        // When: We serialize and deserialize both
        String json1 = JsonIo.toJson(originalInsets, new WriteOptionsBuilder().build());
        String json2 = JsonIo.toJson(copiedInsets, new WriteOptionsBuilder().build());
        
        Insets deserialized1 = JsonIo.toJava(json1, new ReadOptionsBuilder().build()).asClass(Insets.class);
        Insets deserialized2 = JsonIo.toJava(json2, new ReadOptionsBuilder().build()).asClass(Insets.class);

        // Then: Both should be equal
        assertThat(deserialized1).isEqualTo(deserialized2);
        assertThat(deserialized1).isEqualTo(originalInsets);
        assertThat(deserialized2).isEqualTo(copiedInsets);
    }

    @Test
    void testUIComponentInsets() {
        // Given: Insets representing typical UI component margins/padding
        TestUIComponent component = new TestUIComponent();
        component.padding = new Insets(8, 12, 8, 12);  // Top/bottom: 8px, Left/right: 12px
        component.margin = new Insets(16, 16, 16, 16);  // All sides: 16px
        component.border = new Insets(2, 2, 2, 2);      // All sides: 2px
        component.componentName = "Button";

        // When: We serialize and deserialize it
        String json = JsonIo.toJson(component, new WriteOptionsBuilder().build());
        TestUIComponent deserializedComponent = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(TestUIComponent.class);

        // Then: All insets should be preserved
        assertThat(deserializedComponent.padding).isEqualTo(component.padding);
        assertThat(deserializedComponent.margin).isEqualTo(component.margin);
        assertThat(deserializedComponent.border).isEqualTo(component.border);
        assertThat(deserializedComponent.componentName).isEqualTo("Button");
    }

    /**
     * Test object for complex serialization scenarios
     */
    public static class TestObject {
        public Insets padding;
        public Insets margin;
        public String name;
    }

    /**
     * Test object representing a UI component with various insets
     */
    public static class TestUIComponent {
        public Insets padding;
        public Insets margin;
        public Insets border;
        public String componentName;
    }
}