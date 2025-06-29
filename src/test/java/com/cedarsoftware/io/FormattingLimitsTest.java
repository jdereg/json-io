package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import com.cedarsoftware.io.prettyprint.JsonPrettyPrinter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that formatting limits can be configured via WriteOptions and work correctly.
 * 
 * @author Claude4.0s
 */
public class FormattingLimitsTest {

    private WriteOptions originalDefaults;
    private int originalIndentationSize;
    private double originalBufferSizeMultiplier;
    private int originalIndentationThreshold;

    @BeforeEach
    public void setUp() {
        // Store original defaults to restore later
        originalDefaults = WriteOptionsBuilder.getDefaultWriteOptions();
        originalIndentationSize = originalDefaults.getIndentationSize();
        originalBufferSizeMultiplier = originalDefaults.getBufferSizeMultiplier();
        originalIndentationThreshold = originalDefaults.getIndentationThreshold();
    }

    @AfterEach
    public void tearDown() {
        // Restore original permanent settings
        WriteOptionsBuilder.addPermanentIndentationSize(originalIndentationSize);
        WriteOptionsBuilder.addPermanentBufferSizeMultiplier(originalBufferSizeMultiplier);
        WriteOptionsBuilder.addPermanentIndentationThreshold(originalIndentationThreshold);
    }

    @Test
    public void testDefaultFormattingLimits_ShouldUseBackwardCompatibleDefaults() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        
        // Formatting limits should have backward compatible defaults
        assertEquals(2, writeOptions.getIndentationSize());           // 2 spaces per level
        assertEquals(1.3, writeOptions.getBufferSizeMultiplier(), 0.001); // 1.3x buffer multiplier
        assertEquals(10, writeOptions.getIndentationThreshold());     // 10 threshold for strategy switching
    }

    @Test
    public void testConfigurableFormattingLimits_ShouldSetCorrectValues() {
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .indentationSize(4)                     // 4 spaces per level
                .bufferSizeMultiplier(2.0)              // 2.0x buffer multiplier  
                .indentationThreshold(5)                // 5 threshold for strategy switching
                .build();
        
        assertEquals(4, writeOptions.getIndentationSize());
        assertEquals(2.0, writeOptions.getBufferSizeMultiplier(), 0.001);
        assertEquals(5, writeOptions.getIndentationThreshold());
    }

    @Test
    public void testCopyingWriteOptions_ShouldCopyFormattingLimits() {
        WriteOptions original = new WriteOptionsBuilder()
                .indentationSize(8)                     // 8 spaces per level
                .bufferSizeMultiplier(1.5)              // 1.5x buffer multiplier
                .indentationThreshold(20)               // 20 threshold for strategy switching
                .build();
        
        WriteOptions copied = new WriteOptionsBuilder(original).build();
        
        assertEquals(8, copied.getIndentationSize());
        assertEquals(1.5, copied.getBufferSizeMultiplier(), 0.001);
        assertEquals(20, copied.getIndentationThreshold());
    }

    @Test
    public void testPermanentFormattingLimits_InheritedByNewInstances() {
        // Store original values to restore later
        WriteOptions originalDefaults = new WriteOptionsBuilder().build();
        int originalIndentSize = originalDefaults.getIndentationSize();
        double originalBufferMultiplier = originalDefaults.getBufferSizeMultiplier();
        int originalIndentThreshold = originalDefaults.getIndentationThreshold();
        
        try {
            // Set permanent formatting limits
            WriteOptionsBuilder.addPermanentIndentationSize(6);
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(2.5);
            WriteOptionsBuilder.addPermanentIndentationThreshold(15);
            
            // New WriteOptions instances should inherit the permanent limits
            WriteOptions writeOptions1 = new WriteOptionsBuilder().build();
            assertEquals(6, writeOptions1.getIndentationSize());
            assertEquals(2.5, writeOptions1.getBufferSizeMultiplier(), 0.001);
            assertEquals(15, writeOptions1.getIndentationThreshold());
            
            // Another new instance should also inherit them
            WriteOptions writeOptions2 = new WriteOptionsBuilder().build();
            assertEquals(6, writeOptions2.getIndentationSize());
            assertEquals(2.5, writeOptions2.getBufferSizeMultiplier(), 0.001);
            assertEquals(15, writeOptions2.getIndentationThreshold());
            
        } finally {
            // Restore original permanent settings
            WriteOptionsBuilder.addPermanentIndentationSize(originalIndentSize);
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(originalBufferMultiplier);
            WriteOptionsBuilder.addPermanentIndentationThreshold(originalIndentThreshold);
        }
    }

    @Test
    public void testPermanentFormattingLimits_CanBeOverriddenLocally() {
        // Store original values to restore later
        WriteOptions originalDefaults = new WriteOptionsBuilder().build();
        int originalIndentSize = originalDefaults.getIndentationSize();
        double originalBufferMultiplier = originalDefaults.getBufferSizeMultiplier();
        int originalIndentThreshold = originalDefaults.getIndentationThreshold();
        
        try {
            // Set permanent formatting limits
            WriteOptionsBuilder.addPermanentIndentationSize(3);
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(1.8);
            WriteOptionsBuilder.addPermanentIndentationThreshold(12);
            
            // Local settings should override permanent settings
            WriteOptions writeOptions = new WriteOptionsBuilder()
                    .indentationSize(7)                 // Override permanent 3
                    .bufferSizeMultiplier(3.0)          // Override permanent 1.8
                    .indentationThreshold(25)           // Override permanent 12
                    .build();
            
            assertEquals(7, writeOptions.getIndentationSize());
            assertEquals(3.0, writeOptions.getBufferSizeMultiplier(), 0.001);
            assertEquals(25, writeOptions.getIndentationThreshold());
            
        } finally {
            // Restore original permanent settings
            WriteOptionsBuilder.addPermanentIndentationSize(originalIndentSize);
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(originalBufferMultiplier);
            WriteOptionsBuilder.addPermanentIndentationThreshold(originalIndentThreshold);
        }
    }

    @Test
    public void testFormattingLimitValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().indentationSize(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().indentationSize(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().bufferSizeMultiplier(0.5).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().bufferSizeMultiplier(-1.0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().indentationThreshold(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new WriteOptionsBuilder().indentationThreshold(-1).build();
        });
    }

    @Test
    public void testPermanentFormattingLimitValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentIndentationSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentIndentationSize(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(0.5);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentBufferSizeMultiplier(-1.0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentIndentationThreshold(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentIndentationThreshold(-1);
        });
    }

    @Test
    public void testBackwardCompatibility_DefaultFormattingWorksWithModerateUsage() {
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        
        // Should work with default formatting limits
        assertEquals(2, writeOptions.getIndentationSize());
        assertEquals(1.3, writeOptions.getBufferSizeMultiplier(), 0.001);
        assertEquals(10, writeOptions.getIndentationThreshold());
        
        // Test that normal JSON processing works with default formatting
        String jsonInput = "{\"key1\":\"value1\", \"key2\":\"value2\", \"key3\":\"value3\"}";
        
        assertDoesNotThrow(() -> {
            String prettyJson = JsonPrettyPrinter.prettyPrint(jsonInput, writeOptions);
            assertNotNull(prettyJson);
            assertTrue(prettyJson.contains("\"key1\""));
        });
    }

    @Test
    public void testFormattingLimitRange_ShouldAcceptValidValues() {
        // Test various valid formatting values
        
        // Test indentation sizes
        int[] validIndentSizes = {1, 2, 4, 8, 16, 32};
        for (int size : validIndentSizes) {
            assertDoesNotThrow(() -> {
                WriteOptions writeOptions = new WriteOptionsBuilder()
                        .indentationSize(size)
                        .build();
                assertEquals(size, writeOptions.getIndentationSize());
            }, "Indent size " + size + " should be valid");
        }
        
        // Test buffer multipliers
        double[] validMultipliers = {1.0, 1.1, 1.3, 1.5, 2.0, 3.0, 5.0};
        for (double multiplier : validMultipliers) {
            assertDoesNotThrow(() -> {
                WriteOptions writeOptions = new WriteOptionsBuilder()
                        .bufferSizeMultiplier(multiplier)
                        .build();
                assertEquals(multiplier, writeOptions.getBufferSizeMultiplier(), 0.001);
            }, "Buffer multiplier " + multiplier + " should be valid");
        }
        
        // Test indentation thresholds
        int[] validThresholds = {1, 5, 10, 20, 50, 100, 1000};
        for (int threshold : validThresholds) {
            assertDoesNotThrow(() -> {
                WriteOptions writeOptions = new WriteOptionsBuilder()
                        .indentationThreshold(threshold)
                        .build();
                assertEquals(threshold, writeOptions.getIndentationThreshold());
            }, "Indentation threshold " + threshold + " should be valid");
        }
    }

    @Test
    public void testJsonPrettyPrinterIntegration_UsesConfigurableIndentation() {
        // Test that JsonPrettyPrinter uses the configurable indentation size
        String jsonInput = "{\"a\":{\"b\":\"value\"}}";
        
        // Test with 4-space indentation
        WriteOptions fourSpaceOptions = new WriteOptionsBuilder()
                .indentationSize(4)
                .build();
        
        String prettyJson4 = JsonPrettyPrinter.prettyPrint(jsonInput, fourSpaceOptions);
        
        // Should contain 4 spaces for the first level of indentation
        assertTrue(prettyJson4.contains("    \"a\""), "Should use 4-space indentation");
        
        // Test with 8-space indentation
        WriteOptions eightSpaceOptions = new WriteOptionsBuilder()
                .indentationSize(8)
                .build();
        
        String prettyJson8 = JsonPrettyPrinter.prettyPrint(jsonInput, eightSpaceOptions);
        
        // Should contain 8 spaces for the first level of indentation
        assertTrue(prettyJson8.contains("        \"a\""), "Should use 8-space indentation");
    }

    @Test
    public void testJsonPrettyPrinterIntegration_UsesConfigurableBufferSize() {
        // Test that JsonPrettyPrinter uses the configurable buffer size multiplier
        String jsonInput = "{\"key\":\"value\"}";
        
        // Test with different buffer multipliers (this is mainly about ensuring no exceptions)
        WriteOptions smallBufferOptions = new WriteOptionsBuilder()
                .bufferSizeMultiplier(1.0)  // Minimal buffer
                .build();
        
        WriteOptions largeBufferOptions = new WriteOptionsBuilder()
                .bufferSizeMultiplier(5.0)  // Large buffer
                .build();
        
        assertDoesNotThrow(() -> {
            String prettyJson1 = JsonPrettyPrinter.prettyPrint(jsonInput, smallBufferOptions);
            assertNotNull(prettyJson1);
            assertTrue(prettyJson1.contains("\"key\""));
        });
        
        assertDoesNotThrow(() -> {
            String prettyJson2 = JsonPrettyPrinter.prettyPrint(jsonInput, largeBufferOptions);
            assertNotNull(prettyJson2);
            assertTrue(prettyJson2.contains("\"key\""));
        });
    }

    @Test
    public void testJsonWriterIntegration_UsesConfigurableIndentationThreshold() {
        // Test that JsonWriter uses the configurable indentation threshold
        // This is more of a conceptual test since the threshold affects internal performance optimization
        
        // Test with very low threshold (forces character array building sooner)
        WriteOptions lowThresholdOptions = new WriteOptionsBuilder()
                .indentationThreshold(1)
                .prettyPrint(true)
                .build();
        
        assertEquals(1, lowThresholdOptions.getIndentationThreshold());
        
        // Test with high threshold (uses simple repeated writes longer)
        WriteOptions highThresholdOptions = new WriteOptionsBuilder()
                .indentationThreshold(100)
                .prettyPrint(true)
                .build();
        
        assertEquals(100, highThresholdOptions.getIndentationThreshold());
        
        // Both configurations should allow normal JSON writing
        Object testObject = new java.util.HashMap<String, String>() {{
            put("test", "value");
        }};
        
        assertDoesNotThrow(() -> {
            String json1 = JsonIo.toJson(testObject, lowThresholdOptions);
            assertNotNull(json1);
            assertTrue(json1.contains("\"test\""));
        });
        
        assertDoesNotThrow(() -> {
            String json2 = JsonIo.toJson(testObject, highThresholdOptions);
            assertNotNull(json2);
            assertTrue(json2.contains("\"test\""));
        });
    }

    @Test
    public void testExtremeFormattingValues_ShouldWorkCorrectly() {
        // Test with minimum valid values
        WriteOptions minOptions = new WriteOptionsBuilder()
                .indentationSize(1)
                .bufferSizeMultiplier(1.0)
                .indentationThreshold(1)
                .build();
        
        assertEquals(1, minOptions.getIndentationSize());
        assertEquals(1.0, minOptions.getBufferSizeMultiplier(), 0.001);
        assertEquals(1, minOptions.getIndentationThreshold());
        
        // Test with large values
        WriteOptions maxOptions = new WriteOptionsBuilder()
                .indentationSize(50)
                .bufferSizeMultiplier(10.0)
                .indentationThreshold(Integer.MAX_VALUE)
                .build();
        
        assertEquals(50, maxOptions.getIndentationSize());
        assertEquals(10.0, maxOptions.getBufferSizeMultiplier(), 0.001);
        assertEquals(Integer.MAX_VALUE, maxOptions.getIndentationThreshold());
        
        // Both extreme configurations should work for basic JSON processing
        String testJson = "{\"test\":\"value\"}";
        
        assertDoesNotThrow(() -> {
            String prettyJson1 = JsonPrettyPrinter.prettyPrint(testJson, minOptions);
            assertNotNull(prettyJson1);
        });
        
        assertDoesNotThrow(() -> {
            String prettyJson2 = JsonPrettyPrinter.prettyPrint(testJson, maxOptions);
            assertNotNull(prettyJson2);
        });
    }

    @Test
    public void testFormattingConfiguration_IndependentOfSecurityLimits() {
        // Test that formatting limits are independent of security limits
        WriteOptions options = new WriteOptionsBuilder()
                .indentationSize(6)
                .bufferSizeMultiplier(2.5)
                .indentationThreshold(25)
                .maxIndentationDepth(50)     // Security limit
                .maxObjectCount(10000)       // Security limit
                .build();
        
        // Formatting limits should be set correctly
        assertEquals(6, options.getIndentationSize());
        assertEquals(2.5, options.getBufferSizeMultiplier(), 0.001);
        assertEquals(25, options.getIndentationThreshold());
        
        // Security limits should also be set correctly
        assertEquals(50, options.getMaxIndentationDepth());
        assertEquals(10000, options.getMaxObjectCount());
    }

    @Test
    public void testFormattingLimitsInteraction_WithPrettyPrintSetting() {
        // Test that formatting limits work correctly with prettyPrint setting
        
        // Pretty print enabled with custom formatting
        WriteOptions prettyOptions = new WriteOptionsBuilder()
                .prettyPrint(true)
                .indentationSize(3)
                .build();
        
        assertTrue(prettyOptions.isPrettyPrint());
        assertEquals(3, prettyOptions.getIndentationSize());
        
        // Pretty print disabled (formatting limits still available for JsonPrettyPrinter)
        WriteOptions compactOptions = new WriteOptionsBuilder()
                .prettyPrint(false)
                .indentationSize(6)
                .build();
        
        assertFalse(compactOptions.isPrettyPrint());
        assertEquals(6, compactOptions.getIndentationSize());
        
        // Both should work for JSON processing
        Object testObject = java.util.Collections.singletonMap("key", "value");
        
        assertDoesNotThrow(() -> {
            String prettyJson = JsonIo.toJson(testObject, prettyOptions);
            assertNotNull(prettyJson);
        });
        
        assertDoesNotThrow(() -> {
            String compactJson = JsonIo.toJson(testObject, compactOptions);
            assertNotNull(compactJson);
        });
    }
}