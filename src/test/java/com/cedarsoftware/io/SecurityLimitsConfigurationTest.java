package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that security limits can be configured via ReadOptions and default to unlimited (backward compatible).
 * 
 * @author Claude4.0s
 */
public class SecurityLimitsConfigurationTest {

    @Test
    public void testDefaultSecurityLimits_ShouldBeUnlimited() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // All security limits should default to Integer.MAX_VALUE (unlimited) for backward compatibility
        assertEquals(Integer.MAX_VALUE, readOptions.getMaxUnresolvedReferences());
        assertEquals(Integer.MAX_VALUE, readOptions.getMaxStackDepth());
        assertEquals(Integer.MAX_VALUE, readOptions.getMaxMapsToRehash());
        assertEquals(Integer.MAX_VALUE, readOptions.getMaxMissingFields());
    }

    @Test
    public void testConfigurableSecurityLimits_ShouldSetCorrectValues() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .maxUnresolvedReferences(1000000)
                .maxStackDepth(10000)
                .maxMapsToRehash(1000000)
                .maxMissingFields(100000)
                .build();
        
        assertEquals(1000000, readOptions.getMaxUnresolvedReferences());
        assertEquals(10000, readOptions.getMaxStackDepth());
        assertEquals(1000000, readOptions.getMaxMapsToRehash());
        assertEquals(100000, readOptions.getMaxMissingFields());
    }

    @Test
    public void testCopyingReadOptions_ShouldCopySecurityLimits() {
        ReadOptions original = new ReadOptionsBuilder()
                .maxUnresolvedReferences(500000)
                .maxStackDepth(5000)
                .maxMapsToRehash(500000)
                .maxMissingFields(50000)
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(500000, copied.getMaxUnresolvedReferences());
        assertEquals(5000, copied.getMaxStackDepth());
        assertEquals(500000, copied.getMaxMapsToRehash());
        assertEquals(50000, copied.getMaxMissingFields());
    }

    @Test
    public void testSecurityLimitConfiguration_CanBeSet() {
        // Test that security limits can be configured with various values
        ReadOptions readOptions1 = new ReadOptionsBuilder()
                .maxStackDepth(100)
                .build();
        assertEquals(100, readOptions1.getMaxStackDepth());
        
        ReadOptions readOptions2 = new ReadOptionsBuilder()
                .maxUnresolvedReferences(50000)
                .build();
        assertEquals(50000, readOptions2.getMaxUnresolvedReferences());
        
        ReadOptions readOptions3 = new ReadOptionsBuilder()
                .maxMapsToRehash(75000)
                .build();
        assertEquals(75000, readOptions3.getMaxMapsToRehash());
        
        ReadOptions readOptions4 = new ReadOptionsBuilder()
                .maxMissingFields(25000)
                .build();
        assertEquals(25000, readOptions4.getMaxMissingFields());
    }

    @Test
    public void testBackwardCompatibility_UnlimitedByDefault() {
        // Test that the same deeply nested JSON works with default (unlimited) settings
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Create moderately nested JSON
        StringBuilder deepJson = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            deepJson.append("{\"level").append(i).append("\":");
        }
        deepJson.append("\"deep\"");
        for (int i = 0; i < 10; i++) {
            deepJson.append("}");
        }
        
        // Should not throw exception with unlimited limits
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(deepJson.toString(), readOptions);
            assertNotNull(result);
        });
    }
    
    @Test
    public void testPermanentSecurityLimits_InheritedByNewInstances() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxUnresolved = originalDefaults.getMaxUnresolvedReferences();
        int originalMaxStack = originalDefaults.getMaxStackDepth();
        int originalMaxMaps = originalDefaults.getMaxMapsToRehash();
        int originalMaxFields = originalDefaults.getMaxMissingFields();
        
        try {
            // Set permanent security limits
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(500000);
            ReadOptionsBuilder.addPermanentMaxStackDepth(5000);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(500000);
            ReadOptionsBuilder.addPermanentMaxMissingFields(50000);
            
            // New ReadOptions instances should inherit the permanent limits
            ReadOptions readOptions1 = new ReadOptionsBuilder().build();
            assertEquals(500000, readOptions1.getMaxUnresolvedReferences());
            assertEquals(5000, readOptions1.getMaxStackDepth());
            assertEquals(500000, readOptions1.getMaxMapsToRehash());
            assertEquals(50000, readOptions1.getMaxMissingFields());
            
            // Another new instance should also inherit them
            ReadOptions readOptions2 = new ReadOptionsBuilder().build();
            assertEquals(500000, readOptions2.getMaxUnresolvedReferences());
            assertEquals(5000, readOptions2.getMaxStackDepth());
            assertEquals(500000, readOptions2.getMaxMapsToRehash());
            assertEquals(50000, readOptions2.getMaxMissingFields());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(originalMaxUnresolved);
            ReadOptionsBuilder.addPermanentMaxStackDepth(originalMaxStack);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(originalMaxMaps);
            ReadOptionsBuilder.addPermanentMaxMissingFields(originalMaxFields);
        }
    }
    
    @Test
    public void testPermanentSecurityLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxUnresolved = originalDefaults.getMaxUnresolvedReferences();
        int originalMaxStack = originalDefaults.getMaxStackDepth();
        int originalMaxMaps = originalDefaults.getMaxMapsToRehash();
        int originalMaxFields = originalDefaults.getMaxMissingFields();
        
        try {
            // Set permanent security limits
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(500000);
            ReadOptionsBuilder.addPermanentMaxStackDepth(5000);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(500000);
            ReadOptionsBuilder.addPermanentMaxMissingFields(50000);
            
            // Local settings should override permanent settings
            ReadOptions readOptions = new ReadOptionsBuilder()
                    .maxUnresolvedReferences(1000000)  // Override permanent
                    .maxStackDepth(10000)              // Override permanent
                    .maxMapsToRehash(1000000)          // Override permanent
                    .maxMissingFields(100000)          // Override permanent
                    .build();
            
            assertEquals(1000000, readOptions.getMaxUnresolvedReferences());
            assertEquals(10000, readOptions.getMaxStackDepth());
            assertEquals(1000000, readOptions.getMaxMapsToRehash());
            assertEquals(100000, readOptions.getMaxMissingFields());
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(originalMaxUnresolved);
            ReadOptionsBuilder.addPermanentMaxStackDepth(originalMaxStack);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(originalMaxMaps);
            ReadOptionsBuilder.addPermanentMaxMissingFields(originalMaxFields);
        }
    }
    
    @Test
    public void testPermanentSecurityLimits_CopiedReadOptionsInheritsPermanent() {
        // Store original values to restore later
        ReadOptions originalDefaults = new ReadOptionsBuilder().build();
        int originalMaxUnresolved = originalDefaults.getMaxUnresolvedReferences();
        int originalMaxStack = originalDefaults.getMaxStackDepth();
        int originalMaxMaps = originalDefaults.getMaxMapsToRehash();
        int originalMaxFields = originalDefaults.getMaxMissingFields();
        
        try {
            // Set permanent security limits
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(400000);
            ReadOptionsBuilder.addPermanentMaxStackDepth(4000);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(400000);
            ReadOptionsBuilder.addPermanentMaxMissingFields(40000);
            
            // Create original ReadOptions with some local overrides
            ReadOptions original = new ReadOptionsBuilder()
                    .maxUnresolvedReferences(800000)  // Local override
                    .build();
            
            // Copy from original - should inherit current permanent values, not original's values
            ReadOptions copied = new ReadOptionsBuilder(original).build();
            
            // The copied instance should use permanent values as the base
            // (this tests that copying starts fresh with permanent settings)
            assertEquals(800000, copied.getMaxUnresolvedReferences()); // Should copy the local override
            assertEquals(4000, copied.getMaxStackDepth());              // Should use permanent
            assertEquals(400000, copied.getMaxMapsToRehash());         // Should use permanent  
            assertEquals(40000, copied.getMaxMissingFields());         // Should use permanent
            
        } finally {
            // Restore original permanent settings
            ReadOptionsBuilder.addPermanentMaxUnresolvedReferences(originalMaxUnresolved);
            ReadOptionsBuilder.addPermanentMaxStackDepth(originalMaxStack);
            ReadOptionsBuilder.addPermanentMaxMapsToRehash(originalMaxMaps);
            ReadOptionsBuilder.addPermanentMaxMissingFields(originalMaxFields);
        }
    }
}