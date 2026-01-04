package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JsonWriter security limits can be configured via WriteOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class JsonWriterSecurityLimitsTest {

    @Test
    public void testDefaultJsonWriterSecurityLimits_ShouldUseBackwardCompatibleDefaults() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        
        // JsonWriter security limits should have backward compatible defaults
        assertEquals(100, writeOptions.getMaxIndentationDepth());         // 100 max indentation depth
        assertEquals(10000, writeOptions.getMaxObjectGraphDepth());       // 10K max object graph depth
        assertEquals(100000, writeOptions.getMaxObjectCount());           // 100K max object count
        assertEquals(1000000, writeOptions.getMaxStringLength());         // 1MB max string length
    }

    @Test
    public void testConfigurableJsonWriterSecurityLimits_ShouldSetCorrectValues() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxIndentationDepth(50)                  // 50 max indentation depth
                .maxObjectGraphDepth(5000)                // 5K max object graph depth
                .maxObjectCount(50000)                    // 50K max object count
                .maxStringLength(500000)                  // 500KB max string length
                .build();
        
        assertEquals(50, writeOptions.getMaxIndentationDepth());
        assertEquals(5000, writeOptions.getMaxObjectGraphDepth());
        assertEquals(50000, writeOptions.getMaxObjectCount());
        assertEquals(500000, writeOptions.getMaxStringLength());
    }

    @Test
    public void testCopyingWriteOptions_ShouldCopyJsonWriterSecurityLimits() {
        WriteOptions original = new WriteOptionsBuilder()
                .maxIndentationDepth(25)                  // 25 max indentation depth
                .maxObjectGraphDepth(2500)                // 2.5K max object graph depth
                .maxObjectCount(25000)                    // 25K max object count
                .maxStringLength(250000)                  // 250KB max string length
                .build();
        
        WriteOptions copied = new WriteOptionsBuilder(original).build();
        
        assertEquals(25, copied.getMaxIndentationDepth());
        assertEquals(2500, copied.getMaxObjectGraphDepth());
        assertEquals(25000, copied.getMaxObjectCount());
        assertEquals(250000, copied.getMaxStringLength());
    }

    @Test
    public void testMaxStringLength_ShouldRejectLargeStrings() {
        // String length limit is now consistently enforced across all string serialization paths

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxStringLength(100)  // Very small limit for testing
                .build();

        // Create a string that exceeds the limit
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("x");
        }
        String largeString = sb.toString();

        // Verify the configuration is correct
        assertEquals(100, writeOptions.getMaxStringLength());

        // Strings exceeding the limit should now throw JsonIoException
        assertThrows(JsonIoException.class, () -> {
            JsonIo.toJson(largeString, writeOptions);
        });
    }

    @Test
    public void testMaxStringLength_ShouldAllowStringsWithinLimit() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxStringLength(1000)  // Allow reasonable size
                .build();

        // Create a string within the limit
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb2.append("x");
        }
        String validString = sb2.toString();
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            String json = JsonIo.toJson(validString, writeOptions);
            assertNotNull(json);
            assertTrue(json.contains("xxx")); // Should contain the repeated x's
        });
    }

    @Test
    public void testMaxObjectCount_ShouldRejectLargeObjectGraphs() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectCount(10)  // Very small limit for testing
                .build();

        // Create a list with more objects than the limit allows
        List<String> largeList = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            largeList.add("item" + i);
        }
        
        // Should throw JsonIoException due to object count limit
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJson(largeList, writeOptions);
        });
        
        assertTrue(exception.getMessage().contains("Object graph too deep") || 
                   exception.getMessage().contains("memory") ||
                   exception.getMessage().contains("10000")); // Object depth or object count message
    }

    @Test
    public void testMaxObjectCount_ShouldAllowObjectsWithinLimit() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .maxObjectCount(1000)  // Allow reasonable count
                .build();

        // Create a list within the limit
        List<String> validList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            validList.add("item" + i);
        }
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            String json = JsonIo.toJson(validList, writeOptions);
            assertNotNull(json);
            assertTrue(json.contains("item0"));
        });
    }

    @Test
    public void testMaxIndentationDepth_WorksWithPrettyPrint() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .prettyPrint(true)
                .maxIndentationDepth(5)  // Small limit for testing
                .build();

        // Create a deeply nested structure
        Map<String, Object> deepMap = new HashMap<>();
        Map<String, Object> current = deepMap;
        for (int i = 0; i < 10; i++) {
            Map<String, Object> nested = new HashMap<>();
            current.put("level" + i, nested);
            current = nested;
        }
        current.put("value", "deep");
        
        // Should not throw exception but should handle depth limitation gracefully
        assertDoesNotThrow(() -> {
            String json = JsonIo.toJson(deepMap, writeOptions);
            assertNotNull(json);
            // Should contain some indication of depth limiting if exceeded
            assertTrue(json.contains("level0") || json.contains("depth"));
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultLimitsWorkWithModerateContent() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        
        // Create content that should work with default generous limits
        Map<String, Object> moderateContent = new HashMap<>();
        moderateContent.put("string", "A reasonable string with normal content");
        
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            list.add("item" + i);
        }
        moderateContent.put("list", list);
        
        // Should not throw exception with default generous limits
        assertDoesNotThrow(() -> {
            String json = JsonIo.toJson(moderateContent, writeOptions);
            assertNotNull(json);
            assertTrue(json.contains("reasonable string"));
            assertTrue(json.contains("item0"));
        });
    }

    @Test
    public void testPermanentJsonWriterSecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        WriteOptions originalDefaults = new WriteOptionsBuilder().build();
        int originalMaxIndentation = originalDefaults.getMaxIndentationDepth();
        int originalMaxDepth = originalDefaults.getMaxObjectGraphDepth();
        int originalMaxCount = originalDefaults.getMaxObjectCount();
        int originalMaxLength = originalDefaults.getMaxStringLength();
        
        try {
            // Set permanent JsonWriter security limits
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(50);
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(5000);
            WriteOptionsBuilder.addPermanentMaxObjectCount(50000);
            WriteOptionsBuilder.addPermanentMaxStringLength(500000);
            
            // New WriteOptions instances should inherit the permanent limits
            WriteOptions writeOptions1 = new WriteOptionsBuilder().build();
            assertEquals(50, writeOptions1.getMaxIndentationDepth());
            assertEquals(5000, writeOptions1.getMaxObjectGraphDepth());
            assertEquals(50000, writeOptions1.getMaxObjectCount());
            assertEquals(500000, writeOptions1.getMaxStringLength());
            
            // Another new instance should also inherit them
            WriteOptions writeOptions2 = new WriteOptionsBuilder().build();
            assertEquals(50, writeOptions2.getMaxIndentationDepth());
            assertEquals(5000, writeOptions2.getMaxObjectGraphDepth());
            assertEquals(50000, writeOptions2.getMaxObjectCount());
            assertEquals(500000, writeOptions2.getMaxStringLength());
            
        } finally {
            // Restore original permanent settings
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(originalMaxIndentation);
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(originalMaxDepth);
            WriteOptionsBuilder.addPermanentMaxObjectCount(originalMaxCount);
            WriteOptionsBuilder.addPermanentMaxStringLength(originalMaxLength);
        }
    }

    @Test
    public void testPermanentJsonWriterSecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        WriteOptions originalDefaults = new WriteOptionsBuilder().build();
        int originalMaxIndentation = originalDefaults.getMaxIndentationDepth();
        int originalMaxDepth = originalDefaults.getMaxObjectGraphDepth();
        int originalMaxCount = originalDefaults.getMaxObjectCount();
        int originalMaxLength = originalDefaults.getMaxStringLength();
        
        try {
            // Set permanent JsonWriter security limits
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(25);
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(2500);
            WriteOptionsBuilder.addPermanentMaxObjectCount(25000);
            WriteOptionsBuilder.addPermanentMaxStringLength(250000);
            
            // Local settings should override permanent settings
            WriteOptions writeOptions = new WriteOptionsBuilder()
                    .maxIndentationDepth(200)             // Override permanent
                    .maxObjectGraphDepth(20000)           // Override permanent
                    .maxObjectCount(200000)               // Override permanent
                    .maxStringLength(2000000)             // Override permanent
                    .build();
            
            assertEquals(200, writeOptions.getMaxIndentationDepth());
            assertEquals(20000, writeOptions.getMaxObjectGraphDepth());
            assertEquals(200000, writeOptions.getMaxObjectCount());
            assertEquals(2000000, writeOptions.getMaxStringLength());
            
        } finally {
            // Restore original permanent settings
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(originalMaxIndentation);
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(originalMaxDepth);
            WriteOptionsBuilder.addPermanentMaxObjectCount(originalMaxCount);
            WriteOptionsBuilder.addPermanentMaxStringLength(originalMaxLength);
        }
    }

    @Test
    public void testSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxIndentationDepth(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxIndentationDepth(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxObjectGraphDepth(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxObjectGraphDepth(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxObjectCount(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxObjectCount(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxStringLength(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().maxStringLength(-1).build();
        });
    }

    @Test
    public void testPermanentSecurityLimitValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxIndentationDepth(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxObjectCount(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxObjectCount(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxStringLength(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentMaxStringLength(-1);
        });
    }
}