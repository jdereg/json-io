package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JsonObject performance limits can be configured via ReadOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class JsonObjectPerformanceLimitsTest {

    private int originalThreshold;

    @BeforeEach
    public void setUp() {
        // Store original threshold to restore later
        originalThreshold = JsonObject.getLinearSearchThreshold();
    }

    @AfterEach
    public void tearDown() {
        // Restore original threshold
        JsonObject.setLinearSearchThreshold(originalThreshold);
    }

    @Test
    public void testDefaultJsonObjectPerformanceLimits_ShouldUseBackwardCompatibleDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // JsonObject performance limits should have backward compatible defaults
        assertEquals(8, readOptions.getLinearSearchThreshold());         // 8 linear search threshold
        assertEquals(8, JsonObject.getLinearSearchThreshold());          // Static configuration should match
    }

    @Test
    public void testConfigurableJsonObjectPerformanceLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(16)                // 16 linear search threshold
                .build();
        
        assertEquals(16, readOptions.getLinearSearchThreshold());
        assertEquals(16, JsonObject.getLinearSearchThreshold());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopyJsonObjectPerformanceLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .linearSearchThreshold(12)                // 12 linear search threshold
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(12, copied.getLinearSearchThreshold());
        assertEquals(12, JsonObject.getLinearSearchThreshold());
    }

    @Test
    public void testJsonObjectStaticMethods_ShouldWorkCorrectly() {
        // Test setting threshold directly
        JsonObject.setLinearSearchThreshold(20);
        assertEquals(20, JsonObject.getLinearSearchThreshold());
        
        // Test threshold validation
        assertThrows(JsonIoException.class, () -> {
            JsonObject.setLinearSearchThreshold(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            JsonObject.setLinearSearchThreshold(-1);
        });
    }

    @Test
    public void testLinearSearchThresholdConfiguration_AppliedWhenBuildingReadOptions() {
        // Test that the threshold is applied to JsonObject when ReadOptions are built
        
        // Set a specific threshold
        ReadOptions readOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(25)
                .build();
        
        // Verify it's applied to both the ReadOptions and the static configuration
        assertEquals(25, readOptions.getLinearSearchThreshold());
        assertEquals(25, JsonObject.getLinearSearchThreshold());
        
        // Build another ReadOptions with a different threshold
        ReadOptions readOptions2 = new ReadOptionsBuilder()
                .linearSearchThreshold(5)
                .build();
        
        // Verify the new threshold is applied
        assertEquals(5, readOptions2.getLinearSearchThreshold());
        assertEquals(5, JsonObject.getLinearSearchThreshold());
    }

    @Test
    public void testPermanentJsonObjectPerformanceLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalLinearThreshold = originalDefaults.getLinearSearchThreshold();
        
        try {
            // Set permanent JsonObject performance limits
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(30);
            
            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(30, readOptions1.getLinearSearchThreshold());
            assertEquals(30, JsonObject.getLinearSearchThreshold());
            
            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(30, readOptions2.getLinearSearchThreshold());
            assertEquals(30, JsonObject.getLinearSearchThreshold());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(originalLinearThreshold);
        }
    }

    @Test
    public void testPermanentJsonObjectPerformanceLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalLinearThreshold = originalDefaults.getLinearSearchThreshold();
        
        try {
            // Set permanent JsonObject performance limits
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(40);
            
            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .linearSearchThreshold(50)            // Override permanent
                    .build();
            
            assertEquals(50, readOptions.getLinearSearchThreshold());
            assertEquals(50, JsonObject.getLinearSearchThreshold());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(originalLinearThreshold);
        }
    }

    @Test
    public void testSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().linearSearchThreshold(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().linearSearchThreshold(-1).build();
        });
    }

    @Test
    public void testPermanentSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLinearSearchThreshold(-1);
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultThresholdWorksWithModerateUsage() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Should work with default threshold
        assertEquals(8, readOptions.getLinearSearchThreshold());
        
        // Test that normal JSON processing works with default threshold
        String json = "{\"key1\":\"value1\", \"key2\":\"value2\", \"key3\":\"value3\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(json, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testLinearSearchThresholdRange_ShouldAcceptValidValues() {
        // Test various valid threshold values
        int[] validThresholds = {1, 2, 4, 8, 16, 32, 64, 128, 1000};
        
        for (int threshold : validThresholds) {
            assertDoesNotThrow(() -> {
                ReadOptions readOptions = new ReadOptionsBuilder()
                        .linearSearchThreshold(threshold)
                        .build();
                assertEquals(threshold, readOptions.getLinearSearchThreshold());
                assertEquals(threshold, JsonObject.getLinearSearchThreshold());
            }, "Threshold " + threshold + " should be valid");
        }
    }

    @Test
    public void testThresholdAffectsPerformanceOptimization_ConceptualTest() {
        // This is more of a conceptual test since the actual performance optimization
        // happens internally in JsonObject and is hard to test directly without
        // complex setup. We test that the configuration is applied correctly.
        
        // Test with very low threshold
        ReadOptions lowThresholdOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(1)
                .build();
        
        assertEquals(1, lowThresholdOptions.getLinearSearchThreshold());
        assertEquals(1, JsonObject.getLinearSearchThreshold());
        
        // Test with high threshold
        ReadOptions highThresholdOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(100)
                .build();
        
        assertEquals(100, highThresholdOptions.getLinearSearchThreshold());
        assertEquals(100, JsonObject.getLinearSearchThreshold());
        
        // Both configurations should allow normal JSON processing
        String testJson = "{\"test\": \"value\"}";
        
        assertDoesNotThrow(() -> {
            Object result1 = JsonIo.toJava(testJson, lowThresholdOptions);
            assertNotNull(result1);
        });
        
        // Reset to high threshold and test again
        ReadOptions highThresholdOptions2 = new ReadOptionsBuilder()
                .linearSearchThreshold(100)
                .build();
        
        assertDoesNotThrow(() -> {
            Object result2 = JsonIo.toJava(testJson, highThresholdOptions2);
            assertNotNull(result2);
        });
    }

    @Test
    public void testExtremeThresholdValues_ShouldWorkCorrectly() {
        // Test with minimum valid value
        ReadOptions minOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(1)
                .build();
        
        assertEquals(1, minOptions.getLinearSearchThreshold());
        assertEquals(1, JsonObject.getLinearSearchThreshold());
        
        // Test with large value
        ReadOptions maxOptions = new ReadOptionsBuilder()
                .linearSearchThreshold(Integer.MAX_VALUE)
                .build();
        
        assertEquals(Integer.MAX_VALUE, maxOptions.getLinearSearchThreshold());
        assertEquals(Integer.MAX_VALUE, JsonObject.getLinearSearchThreshold());
    }
}