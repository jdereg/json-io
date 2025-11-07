package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that JsonReader security limits can be configured via ReadOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class JsonReaderSecurityLimitsTest {

    @Test
    public void testDefaultJsonReaderSecurityLimits_ShouldUseBackwardCompatibleDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // JsonReader security limits should have backward compatible defaults
        assertEquals(10000000, readOptions.getMaxObjectReferences());       // 10M objects
        assertEquals(10000, readOptions.getMaxReferenceChainDepth());       // 10K chain depth
        assertEquals(256, readOptions.getMaxEnumNameLength());              // 256 chars
    }

    @Test
    public void testConfigurableJsonReaderSecurityLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxObjectReferences(5000000)      // 5M max object references
                .maxReferenceChainDepth(5000)      // 5K max reference chain depth
                .maxEnumNameLength(128)            // 128 chars max enum names
                .build();
        
        assertEquals(5000000, readOptions.getMaxObjectReferences());
        assertEquals(5000, readOptions.getMaxReferenceChainDepth());
        assertEquals(128, readOptions.getMaxEnumNameLength());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopyJsonReaderSecurityLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .maxObjectReferences(3000000)      // 3M max object references
                .maxReferenceChainDepth(3000)      // 3K max reference chain depth
                .maxEnumNameLength(64)             // 64 chars max enum names
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(3000000, copied.getMaxObjectReferences());
        assertEquals(3000, copied.getMaxReferenceChainDepth());
        assertEquals(64, copied.getMaxEnumNameLength());
    }

    @Test
    public void testEnumNameLengthLimit_ShouldRejectLongEnumNames() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxEnumNameLength(10)  // Very short limit for testing
                .build();

        // Create a string that will be converted to enum with a name longer than the limit
        // This should trigger the enum validation in JsonReader.convertIfNeeded()
        String longEnumName = "THIS_IS_A_VERY_LONG_ENUM_NAME_THAT_EXCEEDS_LIMIT";
        
        // Should throw JsonIoException due to enum name length limit
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            // Use JsonReader directly with a specific enum type to trigger convertIfNeeded
            Object result = JsonIo.toJava("\"" + longEnumName + "\"", readOptions).asClass(java.util.concurrent.TimeUnit.class);
        });
        
        assertTrue(exception.getMessage().contains("Security limit exceeded"));
        assertTrue(exception.getMessage().contains("Enum name too long"));
    }

    @Test
    public void testEnumNameLengthLimit_ShouldAllowShortEnumNames() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxEnumNameLength(50)  // Allow reasonable length
                .build();

        // Create enum JSON with a name within the limit
        String shortEnumJson = "{\"@type\":\"com.cedarsoftware.io.models.TestEnum\",\"name\":\"SHORT_NAME\"}";
        
        // Should not throw exception
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(shortEnumJson, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testObjectReferencesLimit_ShouldUseConfiguredLimit() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxObjectReferences(3)  // Very low limit for testing
                .build();

        // Create JSON with many @id objects to exceed the reference limit
        // Each @id object gets tracked in the DefaultReferenceTracker
        StringBuilder largeJson = new StringBuilder();
        largeJson.append("[");
        for (int i = 1; i <= 10; i++) {  // Start from 1, use <= to get 10 objects
            if (i > 1) largeJson.append(",");
            largeJson.append("{\"@id\":").append(i).append(",\"value\":\"item").append(i).append("\"}");
        }
        largeJson.append("]");

        // Should throw JsonIoException due to object reference limit
        // Note: Must call .asClass() to trigger parsing and reference tracking
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(largeJson.toString(), readOptions).asClass(Object[].class);
        });

        assertTrue(exception.getMessage().contains("Security limit exceeded"));
        assertTrue(exception.getMessage().contains("Maximum number of object references"));
    }

    @Test
    public void testReferenceChainDepthLimit_ShouldUseConfiguredLimit() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxReferenceChainDepth(2)  // Very low limit for testing
                .build();

        // Create JSON with chained @ref objects to create a deep reference chain
        // The key is that objects themselves are references (not just fields containing @ref)
        // This creates a chain: obj1 IS ref to obj2, obj2 IS ref to obj3, obj3 IS ref to obj4, obj4 is data
        // When accessing obj1, it must resolve through: 1->2 (depth 1), 2->3 (depth 2), 3->4 (depth 3, exceeds limit)
        // Note: We need 4 objects to create a chain depth of 3 (exceeding limit of 2)
        String deepReferenceJson = "["
                + "{\"@ref\":1},"               // Array element points to object 1
                + "{\"@id\":1,\"@ref\":2},"     // Object 1 IS a reference to object 2
                + "{\"@id\":2,\"@ref\":3},"     // Object 2 IS a reference to object 3
                + "{\"@id\":3,\"@ref\":4},"     // Object 3 IS a reference to object 4
                + "{\"@id\":4,\"value\":\"end\"}" // Object 4 is the actual data
                + "]";

        // Should throw JsonIoException due to reference chain depth limit
        // Note: Must call .asClass() to trigger parsing and reference resolution
        JsonIoException exception = assertThrows(JsonIoException.class, () -> {
            JsonIo.toJava(deepReferenceJson, readOptions).asClass(Object[].class);
        });

        assertTrue(exception.getMessage().contains("Security limit exceeded"));
        assertTrue(exception.getMessage().contains("Reference chain depth"));
    }

    @Test
    public void testBackwardCompatibility_UnlimitedReferencesWorkWithDefaults() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Create JSON with moderate complexity that should work with defaults
        StringBuilder moderateJson = new StringBuilder();
        moderateJson.append("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) moderateJson.append(",");
            moderateJson.append("{\"@id\":").append(i).append(",\"value\":\"item").append(i).append("\"}");
        }
        moderateJson.append("]");
        
        // Should not throw exception with default generous limits
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(moderateJson.toString(), readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testPermanentJsonReaderSecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxRefs = originalDefaults.getMaxObjectReferences();
        int originalMaxChain = originalDefaults.getMaxReferenceChainDepth();
        int originalMaxEnum = originalDefaults.getMaxEnumNameLength();
        
        try {
            // Set permanent JsonReader security limits
            ReadOptionsBuilder.addPermanentMaxObjectReferences(2000000);
            ReadOptionsBuilder.addPermanentMaxReferenceChainDepth(2000);
            ReadOptionsBuilder.addPermanentMaxEnumNameLength(64);
            
            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(2000000, readOptions1.getMaxObjectReferences());
            assertEquals(2000, readOptions1.getMaxReferenceChainDepth());
            assertEquals(64, readOptions1.getMaxEnumNameLength());
            
            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(2000000, readOptions2.getMaxObjectReferences());
            assertEquals(2000, readOptions2.getMaxReferenceChainDepth());
            assertEquals(64, readOptions2.getMaxEnumNameLength());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxObjectReferences(originalMaxRefs);
            ReadOptionsBuilder.addPermanentMaxReferenceChainDepth(originalMaxChain);
            ReadOptionsBuilder.addPermanentMaxEnumNameLength(originalMaxEnum);
        }
    }

    @Test
    public void testPermanentJsonReaderSecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxRefs = originalDefaults.getMaxObjectReferences();
        int originalMaxChain = originalDefaults.getMaxReferenceChainDepth();
        int originalMaxEnum = originalDefaults.getMaxEnumNameLength();
        
        try {
            // Set permanent JsonReader security limits
            ReadOptionsBuilder.addPermanentMaxObjectReferences(1000000);
            ReadOptionsBuilder.addPermanentMaxReferenceChainDepth(1000);
            ReadOptionsBuilder.addPermanentMaxEnumNameLength(32);
            
            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .maxObjectReferences(8000000)      // Override permanent
                    .maxReferenceChainDepth(8000)      // Override permanent
                    .maxEnumNameLength(512)            // Override permanent
                    .build();
            
            assertEquals(8000000, readOptions.getMaxObjectReferences());
            assertEquals(8000, readOptions.getMaxReferenceChainDepth());
            assertEquals(512, readOptions.getMaxEnumNameLength());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxObjectReferences(originalMaxRefs);
            ReadOptionsBuilder.addPermanentMaxReferenceChainDepth(originalMaxChain);
            ReadOptionsBuilder.addPermanentMaxEnumNameLength(originalMaxEnum);
        }
    }
}