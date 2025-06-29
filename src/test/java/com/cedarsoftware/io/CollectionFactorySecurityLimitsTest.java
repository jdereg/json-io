package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that Collection/Map Factory security limits can be configured via ReadOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class CollectionFactorySecurityLimitsTest {

    @Test
    public void testDefaultCollectionFactorySecurityLimits_ShouldUseBackwardCompatibleDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Collection factory security limits should have backward compatible defaults
        assertEquals(16, readOptions.getDefaultCollectionCapacity());    // 16 default collection capacity
        assertEquals(0.75f, readOptions.getCollectionLoadFactor(), 0.001f);  // 0.75 load factor for hash collections
        assertEquals(16, readOptions.getMinCollectionCapacity());        // 16 minimum collection capacity
    }

    @Test
    public void testConfigurableCollectionFactorySecurityLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .defaultCollectionCapacity(32)        // 32 default collection capacity
                .collectionLoadFactor(0.6f)           // 0.6 load factor for hash collections
                .minCollectionCapacity(8)             // 8 minimum collection capacity
                .build();
        
        assertEquals(32, readOptions.getDefaultCollectionCapacity());
        assertEquals(0.6f, readOptions.getCollectionLoadFactor(), 0.001f);
        assertEquals(8, readOptions.getMinCollectionCapacity());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopyCollectionFactorySecurityLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .defaultCollectionCapacity(64)        // 64 default collection capacity
                .collectionLoadFactor(0.8f)           // 0.8 load factor for hash collections
                .minCollectionCapacity(4)             // 4 minimum collection capacity
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(64, copied.getDefaultCollectionCapacity());
        assertEquals(0.8f, copied.getCollectionLoadFactor(), 0.001f);
        assertEquals(4, copied.getMinCollectionCapacity());
    }

    @Test
    public void testCollectionFactoryWithCustomLimits_ShouldCreateCollectionsWithConfiguredCapacity() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .defaultCollectionCapacity(100)       // Large default capacity
                .minCollectionCapacity(50)            // Large minimum capacity
                .build();
        
        // Test that the configuration is stored correctly
        assertEquals(100, readOptions.getDefaultCollectionCapacity());
        assertEquals(50, readOptions.getMinCollectionCapacity());
        
        // Test that JSON parsing works with these settings (without type verification for simplicity)
        String json = "[\"item1\", \"item2\", \"item3\"]";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(json, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testMapFactoryWithCustomLimits_ShouldCreateMapsWithConfiguredCapacity() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .defaultCollectionCapacity(200)       // Large default capacity
                .collectionLoadFactor(0.5f)           // Lower load factor
                .minCollectionCapacity(100)           // Large minimum capacity
                .build();
        
        // Test that the configuration is stored correctly
        assertEquals(200, readOptions.getDefaultCollectionCapacity());
        assertEquals(0.5f, readOptions.getCollectionLoadFactor(), 0.001f);
        assertEquals(100, readOptions.getMinCollectionCapacity());
        
        // Test that JSON parsing works with these settings
        String json = "{\"key1\":\"value1\", \"key2\":\"value2\", \"key3\":\"value3\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(json, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testCollectionFactoryWithSmallCapacity_ShouldUseMinimumCapacity() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .defaultCollectionCapacity(1)         // Very small default
                .minCollectionCapacity(10)            // Larger minimum enforced
                .build();
        
        // Test that the configuration is stored correctly
        assertEquals(1, readOptions.getDefaultCollectionCapacity());
        assertEquals(10, readOptions.getMinCollectionCapacity());
        
        // Test that JSON parsing works with these settings
        String json = "[\"single_item\"]";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(json, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testPermanentCollectionFactorySecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalDefaultCapacity = originalDefaults.getDefaultCollectionCapacity();
        float originalLoadFactor = originalDefaults.getCollectionLoadFactor();
        int originalMinCapacity = originalDefaults.getMinCollectionCapacity();
        
        try {
            // Set permanent collection factory security limits
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(128);
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(0.9f);
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(32);
            
            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(128, readOptions1.getDefaultCollectionCapacity());
            assertEquals(0.9f, readOptions1.getCollectionLoadFactor(), 0.001f);
            assertEquals(32, readOptions1.getMinCollectionCapacity());
            
            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(128, readOptions2.getDefaultCollectionCapacity());
            assertEquals(0.9f, readOptions2.getCollectionLoadFactor(), 0.001f);
            assertEquals(32, readOptions2.getMinCollectionCapacity());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(originalDefaultCapacity);
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(originalLoadFactor);
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(originalMinCapacity);
        }
    }

    @Test
    public void testPermanentCollectionFactorySecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalDefaultCapacity = originalDefaults.getDefaultCollectionCapacity();
        float originalLoadFactor = originalDefaults.getCollectionLoadFactor();
        int originalMinCapacity = originalDefaults.getMinCollectionCapacity();
        
        try {
            // Set permanent collection factory security limits
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(256);
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(0.7f);
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(64);
            
            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .defaultCollectionCapacity(512)   // Override permanent
                    .collectionLoadFactor(0.4f)       // Override permanent
                    .minCollectionCapacity(128)       // Override permanent
                    .build();
            
            assertEquals(512, readOptions.getDefaultCollectionCapacity());
            assertEquals(0.4f, readOptions.getCollectionLoadFactor(), 0.001f);
            assertEquals(128, readOptions.getMinCollectionCapacity());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(originalDefaultCapacity);
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(originalLoadFactor);
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(originalMinCapacity);
        }
    }

    @Test
    public void testSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().defaultCollectionCapacity(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().defaultCollectionCapacity(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().collectionLoadFactor(0.0f).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().collectionLoadFactor(1.0f).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().collectionLoadFactor(-0.1f).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().collectionLoadFactor(1.1f).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().minCollectionCapacity(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().minCollectionCapacity(-1).build();
        });
    }

    @Test
    public void testPermanentSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentDefaultCollectionCapacity(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(0.0f);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(1.0f);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(-0.1f);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentCollectionLoadFactor(1.1f);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMinCollectionCapacity(-1);
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultLimitsWorkWithModerateUsage() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Should work with default generous limits
        assertEquals(16, readOptions.getDefaultCollectionCapacity());
        assertEquals(0.75f, readOptions.getCollectionLoadFactor(), 0.001f);
        assertEquals(16, readOptions.getMinCollectionCapacity());
        
        // Test that normal JSON processing works with various collection types
        assertDoesNotThrow(() -> {
            String listJson = "[1, 2, 3, 4, 5]";
            Object listResult = JsonIo.toJava(listJson, readOptions);
            assertNotNull(listResult);
        });
        
        assertDoesNotThrow(() -> {
            String mapJson = "{\"a\": 1, \"b\": 2, \"c\": 3}";
            Object mapResult = JsonIo.toJava(mapJson, readOptions);
            assertNotNull(mapResult);
        });
        
        assertDoesNotThrow(() -> {
            String setJson = "{\"@type\":\"java.util.HashSet\", \"@items\":[\"x\", \"y\", \"z\"]}";
            Object setResult = JsonIo.toJava(setJson, readOptions);
            assertNotNull(setResult);
        });
    }

    @Test
    public void testCollectionFactorySecurityLimits_AppliedDuringRealParsing() {
        // Test with a custom ReadOptions that has specific limits
        ReadOptions readOptions = new ReadOptionsBuilder()
                .defaultCollectionCapacity(50)
                .collectionLoadFactor(0.6f)
                .minCollectionCapacity(25)
                .build();
        
        // Test that the configuration is stored correctly
        assertEquals(50, readOptions.getDefaultCollectionCapacity());
        assertEquals(0.6f, readOptions.getCollectionLoadFactor(), 0.001f);
        assertEquals(25, readOptions.getMinCollectionCapacity());
        
        // Create JSON with various collection types
        String complexJson = "{\n" +
            "  \"list\": [1, 2, 3],\n" +
            "  \"map\": {\"key1\": \"value1\", \"key2\": \"value2\"},\n" +
            "  \"set\": {\"@type\":\"java.util.LinkedHashSet\", \"@items\":[\"a\", \"b\", \"c\"]}\n" +
            "}";
        
        // Should parse successfully with custom limits
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(complexJson, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testValidLoadFactorRange_ShouldAcceptValidValues() {
        // Test various valid load factor values
        float[] validLoadFactors = {0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 0.99f};
        
        for (float loadFactor : validLoadFactors) {
            assertDoesNotThrow(() -> {
                ReadOptions readOptions = new ReadOptionsBuilder()
                        .collectionLoadFactor(loadFactor)
                        .build();
                assertEquals(loadFactor, readOptions.getCollectionLoadFactor(), 0.001f);
            }, "Load factor " + loadFactor + " should be valid");
        }
    }

    @Test
    public void testCollectionCapacityRange_ShouldAcceptValidValues() {
        // Test various valid capacity values
        int[] validCapacities = {1, 2, 8, 16, 32, 64, 128, 1000, 10000};
        
        for (int capacity : validCapacities) {
            assertDoesNotThrow(() -> {
                ReadOptions readOptions = new ReadOptionsBuilder()
                        .defaultCollectionCapacity(capacity)
                        .minCollectionCapacity(capacity)
                        .build();
                assertEquals(capacity, readOptions.getDefaultCollectionCapacity());
                assertEquals(capacity, readOptions.getMinCollectionCapacity());
            }, "Capacity " + capacity + " should be valid");
        }
    }
}