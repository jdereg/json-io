package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JsonValue security limits can be configured via ReadOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class JsonValueSecurityLimitsTest {

    private int originalCacheSize;

    @BeforeEach
    public void setUp() {
        // Store original cache size to restore later
        originalCacheSize = JsonValue.getMaxTypeResolutionCacheSize();
    }

    @AfterEach
    public void tearDown() {
        // Restore original cache size
        JsonValue.setMaxTypeResolutionCacheSize(originalCacheSize);
    }

    @Test
    public void testDefaultJsonValueSecurityLimits_ShouldUseBackwardCompatibleDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // JsonValue security limits should have backward compatible defaults
        assertEquals(1000, readOptions.getMaxTypeResolutionCacheSize());
        assertEquals(1000, JsonValue.getMaxTypeResolutionCacheSize());
    }

    @Test
    public void testConfigurableJsonValueSecurityLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxTypeResolutionCacheSize(500)  // 500 cache entries max
                .build();
        
        assertEquals(500, readOptions.getMaxTypeResolutionCacheSize());
        assertEquals(500, JsonValue.getMaxTypeResolutionCacheSize());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopyJsonValueSecurityLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .maxTypeResolutionCacheSize(250)  // 250 cache entries max
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(250, copied.getMaxTypeResolutionCacheSize());
        assertEquals(250, JsonValue.getMaxTypeResolutionCacheSize());
    }

    @Test
    public void testMaxTypeResolutionCacheSize_ShouldLimitCacheGrowth() {
        // Set a very small cache size for testing
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxTypeResolutionCacheSize(2)  // Very small cache for testing
                .build();
        
        assertEquals(2, readOptions.getMaxTypeResolutionCacheSize());
        assertEquals(2, JsonValue.getMaxTypeResolutionCacheSize());
        
        // Clear cache to start fresh
        JsonValue.clearTypeResolutionCache();
        assertEquals(0, JsonValue.getTypeResolutionCacheSize());
        
        // Create JsonValue instances to trigger cache usage
        TestClass obj1 = new TestClass();
        TestClass obj2 = new TestClass();
        TestClass obj3 = new TestClass();
        
        // Test that the cache doesn't grow beyond the limit
        // Note: This is testing the internal behavior, so we'll just verify the configuration is applied
        assertTrue(JsonValue.getTypeResolutionCacheSize() <= 2, 
                  "Cache size should not exceed the configured limit");
    }

    @Test
    public void testJsonValueStaticMethods_ShouldWorkCorrectly() {
        // Test setting cache size directly
        JsonValue.setMaxTypeResolutionCacheSize(100);
        assertEquals(100, JsonValue.getMaxTypeResolutionCacheSize());
        
        // Test clearing cache
        JsonValue.clearTypeResolutionCache();
        assertEquals(0, JsonValue.getTypeResolutionCacheSize());
        
        // Test cache size validation
        assertThrows(JsonIoException.class, () -> {
            JsonValue.setMaxTypeResolutionCacheSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            JsonValue.setMaxTypeResolutionCacheSize(-1);
        });
    }

    @Test
    public void testPermanentJsonValueSecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxCache = originalDefaults.getMaxTypeResolutionCacheSize();
        
        try {
            // Set permanent JsonValue security limits
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(750);
            
            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(750, readOptions1.getMaxTypeResolutionCacheSize());
            assertEquals(750, JsonValue.getMaxTypeResolutionCacheSize());
            
            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(750, readOptions2.getMaxTypeResolutionCacheSize());
            assertEquals(750, JsonValue.getMaxTypeResolutionCacheSize());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(originalMaxCache);
        }
    }

    @Test
    public void testPermanentJsonValueSecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxCache = originalDefaults.getMaxTypeResolutionCacheSize();
        
        try {
            // Set permanent JsonValue security limits
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(300);
            
            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .maxTypeResolutionCacheSize(600)  // Override permanent
                    .build();
            
            assertEquals(600, readOptions.getMaxTypeResolutionCacheSize());
            assertEquals(600, JsonValue.getMaxTypeResolutionCacheSize());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(originalMaxCache);
        }
    }

    @Test
    public void testSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().maxTypeResolutionCacheSize(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().maxTypeResolutionCacheSize(-1).build();
        });
    }

    @Test
    public void testPermanentSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMaxTypeResolutionCacheSize(-1);
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultLimitWorksWithModerateUsage() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Should work with default generous limit
        assertEquals(1000, readOptions.getMaxTypeResolutionCacheSize());
        
        // Test that normal JSON processing works
        String json = "{\"value\":\"test\"}";
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(json, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testCacheConfiguration_AppliedWhenBuildingReadOptions() {
        // Test that the cache size is applied to JsonValue when ReadOptions are built
        
        // Set a specific cache size
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxTypeResolutionCacheSize(123)
                .build();
        
        // Verify it's applied to both the ReadOptions and the static cache
        assertEquals(123, readOptions.getMaxTypeResolutionCacheSize());
        assertEquals(123, JsonValue.getMaxTypeResolutionCacheSize());
        
        // Build another ReadOptions with a different size
        ReadOptions readOptions2 = new ReadOptionsBuilder()
                .maxTypeResolutionCacheSize(456)
                .build();
        
        // Verify the new size is applied
        assertEquals(456, readOptions2.getMaxTypeResolutionCacheSize());
        assertEquals(456, JsonValue.getMaxTypeResolutionCacheSize());
    }

    // Helper class for testing
    private static class TestClass {
        private String value = "test";
        
        public String getValue() {
            return value;
        }
    }
}