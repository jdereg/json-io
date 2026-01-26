package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JsonParser security limits can be configured via ReadOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class JsonParserSecurityLimitsTest {

    @Test
    public void testDefaultJsonParserSecurityLimits_ShouldUseBackwardCompatibleDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        // JsonParser security limits should have backward compatible defaults
        assertEquals(1000000000L, readOptions.getMaxIdValue());              // 1B max ID value
        assertEquals(256, readOptions.getStringBufferSize());                // 256 chars initial capacity
    }

    @Test
    public void testConfigurableJsonParserSecurityLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxIdValue(500000000L)              // 500M max ID value
                .stringBufferSize(128)               // 128 chars initial capacity
                .build();

        assertEquals(500000000L, readOptions.getMaxIdValue());
        assertEquals(128, readOptions.getStringBufferSize());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopyJsonParserSecurityLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .maxIdValue(750000000L)              // 750M max ID value
                .stringBufferSize(64)                // 64 chars initial capacity
                .build();

        ReadOptions copied = new ReadOptionsBuilder(original).build();

        assertEquals(750000000L, copied.getMaxIdValue());
        assertEquals(64, copied.getStringBufferSize());
    }

    @Test
    public void testIdValueLimit_ShouldRejectLargePositiveIds() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxIdValue(1000)  // Very low limit for testing
                .build();

        // Debug: verify the limit is set correctly
        assertEquals(1000L, readOptions.getMaxIdValue());

        // Create JSON with @id and @ref to trigger reference resolution
        String largeIdJson = "[{\"@id\":2000,\"value\":\"test\"},{\"@ref\":2000}]";
        
        // Should throw JsonIoException due to ID value limit
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(largeIdJson, readOptions).asClass(Object[].class);
        });
        
        assertTrue(exception.getMessage().contains("ID value out of safe range"));
        assertTrue(exception.getMessage().contains("2000"));
    }

    @Test
    public void testIdValueLimit_ShouldRejectLargeNegativeIds() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxIdValue(1000)  // Very low limit for testing
                .build();

        // Create JSON with @id and @ref to trigger reference resolution (negative ID)
        String largeNegativeIdJson = "[{\"@id\":-2000,\"value\":\"test\"},{\"@ref\":-2000}]";
        
        // Should throw JsonIoException due to ID value limit
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(largeNegativeIdJson, readOptions).asClass(Object[].class);
        });
        
        assertTrue(exception.getMessage().contains("ID value out of safe range"));
        assertTrue(exception.getMessage().contains("-2000"));
    }

    @Test
    public void testRefIdValueLimit_ShouldRejectLargeRefIds() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxIdValue(1000)  // Very low limit for testing
                .build();

        // Create JSON with @ref value exceeding the limit
        String largeRefJson = "[{\"@id\":500,\"value\":\"target\"},{\"@ref\":2000}]";
        
        // Should throw JsonIoException due to reference ID value limit
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(largeRefJson, readOptions).asClass(Object[].class);
        });
        
        assertTrue(exception.getMessage().contains("Reference ID value out of safe range"));
        assertTrue(exception.getMessage().contains("2000"));
    }

    @Test
    public void testIdValueLimit_ShouldAllowIdsWithinLimit() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxIdValue(10000)  // Allow reasonable range
                .build();

        // Create JSON with @id value within the limit
        String validIdJson = "{\"@id\":5000,\"value\":\"test\"}";
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(validIdJson, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testStringBufferSize_AffectsInitialCapacity() {
        // This test verifies that the stringBufferSize configuration is applied
        // We can't directly test the StringBuilder capacity, but we can verify
        // the configuration is stored correctly
        ReadOptions readOptions = new ReadOptionsBuilder()
                .stringBufferSize(512)
                .build();
        
        assertEquals(512, readOptions.getStringBufferSize());
        
        // Test that parsing still works with different buffer size
        String testJson = "{\"message\":\"Hello World\"}";
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(testJson, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultLimitsWorkWithModerateJson() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Create JSON with moderate complexity that should work with defaults
        String moderateJson = "{\"@id\":999999999,\"data\":\"some reasonably long string content that fits within normal limits\"}";
        
        // Should not throw exception with default generous limits
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(moderateJson, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testPermanentJsonParserSecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        long originalMaxId = originalDefaults.getMaxIdValue();
        int originalStringBuffer = originalDefaults.getStringBufferSize();

        try {
            // Set permanent JsonParser security limits
            ReadOptionsBuilder.addPermanentMaxIdValue(2000000000L);
            ReadOptionsBuilder.addPermanentStringBufferSize(128);

            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(2000000000L, readOptions1.getMaxIdValue());
            assertEquals(128, readOptions1.getStringBufferSize());

            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(2000000000L, readOptions2.getMaxIdValue());
            assertEquals(128, readOptions2.getStringBufferSize());

        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxIdValue(originalMaxId);
            ReadOptionsBuilder.addPermanentStringBufferSize(originalStringBuffer);
        }
    }

    @Test
    public void testPermanentJsonParserSecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        long originalMaxId = originalDefaults.getMaxIdValue();
        int originalStringBuffer = originalDefaults.getStringBufferSize();

        try {
            // Set permanent JsonParser security limits
            ReadOptionsBuilder.addPermanentMaxIdValue(100000000L);
            ReadOptionsBuilder.addPermanentStringBufferSize(64);

            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .maxIdValue(3000000000L)               // Override permanent
                    .stringBufferSize(1024)                // Override permanent
                    .build();

            assertEquals(3000000000L, readOptions.getMaxIdValue());
            assertEquals(1024, readOptions.getStringBufferSize());

        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxIdValue(originalMaxId);
            ReadOptionsBuilder.addPermanentStringBufferSize(originalStringBuffer);
        }
    }
}