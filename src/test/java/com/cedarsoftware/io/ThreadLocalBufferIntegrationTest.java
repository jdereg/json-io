package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify ThreadLocal buffer configuration works correctly
 * during actual JSON parsing operations.
 * 
 * @author Claude4.0s
 */
public class ThreadLocalBufferIntegrationTest {

    private ReadOptions originalDefaults;
    private int originalThreadLocalBufferSize;
    private int originalLargeThreadLocalBufferSize;

    @BeforeEach
    public void setUp() {
        // Store original defaults to restore later
        originalDefaults = ReadOptionsBuilder.getDefaultReadOptions();
        originalThreadLocalBufferSize = originalDefaults.getThreadLocalBufferSize();
        originalLargeThreadLocalBufferSize = originalDefaults.getLargeThreadLocalBufferSize();
    }

    @AfterEach
    public void tearDown() {
        // Restore original permanent settings
        ReadOptionsBuilder.addPermanentThreadLocalBufferSize(originalThreadLocalBufferSize);
        ReadOptionsBuilder.addPermanentLargeThreadLocalBufferSize(originalLargeThreadLocalBufferSize);
        
        // Clean up ThreadLocal buffers
        JsonParser.clearThreadLocalBuffers();
    }

    @Test
    public void testDefaultThreadLocalBufferConfiguration_ShouldWorkWithModerateJsonParsing() {
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        
        // Should work with default ThreadLocal buffer sizes
        assertEquals(1024, readOptions.getThreadLocalBufferSize());
        assertEquals(8192, readOptions.getLargeThreadLocalBufferSize());
        
        // Test that normal JSON processing works with default buffer sizes
        String jsonInput = "{\"key1\":\"value1\", \"key2\":\"value2\", \"array\":[1,2,3,4,5]}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonInput, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testConfigurableThreadLocalBuffers_ShouldWorkWithCustomSizes() {
        // Test with smaller buffer sizes
        ReadOptions smallBufferOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(64)           // Very small buffer
                .largeThreadLocalBufferSize(256)     // Small large buffer
                .build();
        
        assertEquals(64, smallBufferOptions.getThreadLocalBufferSize());
        assertEquals(256, smallBufferOptions.getLargeThreadLocalBufferSize());
        
        // Should still work with smaller buffers
        String jsonInput = "{\"test\":\"small buffer test\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonInput, smallBufferOptions);
            assertNotNull(result);
        });
        
        // Test with larger buffer sizes
        ReadOptions largeBufferOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(4096)         // Large buffer
                .largeThreadLocalBufferSize(16384)   // Very large buffer
                .build();
        
        assertEquals(4096, largeBufferOptions.getThreadLocalBufferSize());
        assertEquals(16384, largeBufferOptions.getLargeThreadLocalBufferSize());
        
        // Should work with larger buffers
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonInput, largeBufferOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testThreadLocalBufferValidation_ShouldRejectInvalidValues() {
        // Test that builder methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().threadLocalBufferSize(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().threadLocalBufferSize(-1).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().largeThreadLocalBufferSize(0).build();
        });
        
        assertThrows(JsonIoException.class, () -> {
            new ReadOptionsBuilder().largeThreadLocalBufferSize(-1).build();
        });
    }

    @Test
    public void testPermanentThreadLocalBufferValidation_ShouldRejectInvalidValues() {
        // Test that permanent methods validate input parameters
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentThreadLocalBufferSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentThreadLocalBufferSize(-1);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLargeThreadLocalBufferSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLargeThreadLocalBufferSize(-1);
        });
    }

    @Test
    public void testThreadLocalBufferProcessing_WithSmallStrings() {
        // Test processing JSON with strings that should fit in regular ThreadLocal buffer
        ReadOptions readOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(512)          // Small buffer size
                .largeThreadLocalBufferSize(2048)    // Larger fallback
                .build();
        
        // JSON with short strings (should use regular buffer)
        String jsonWithShortStrings = "{\"short\":\"test\", \"value\":\"123\", \"name\":\"John\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonWithShortStrings, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testThreadLocalBufferProcessing_WithLargeStrings() {
        // Test processing JSON with large strings that might require large buffer
        ReadOptions readOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(128)          // Very small regular buffer
                .largeThreadLocalBufferSize(4096)    // Large buffer for big strings
                .build();
        
        // Create JSON with a large string value
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            largeValue.append("This is a large string value that will likely exceed the regular ThreadLocal buffer size. ");
        }
        
        String jsonWithLargeString = "{\"largeValue\":\"" + largeValue.toString() + "\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonWithLargeString, readOptions);
            assertNotNull(result);
        });
    }

    @Test
    public void testThreadLocalBufferRange_ShouldAcceptValidValues() {
        // Test various valid buffer sizes
        int[] validBufferSizes = {1, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384};
        
        for (int bufferSize : validBufferSizes) {
            assertDoesNotThrow(() -> {
                ReadOptions readOptions = new ReadOptionsBuilder()
                        .threadLocalBufferSize(bufferSize)
                        .largeThreadLocalBufferSize(bufferSize * 2)
                        .build();
                        
                assertEquals(bufferSize, readOptions.getThreadLocalBufferSize());
                assertEquals(bufferSize * 2, readOptions.getLargeThreadLocalBufferSize());
                
                // Test that JSON processing works with this buffer size
                String testJson = "{\"test\":\"value\"}";
                Object result = JsonIo.toJava(testJson, readOptions);
                assertNotNull(result);
            }, "Buffer size " + bufferSize + " should be valid");
        }
    }

    @Test
    public void testThreadLocalBufferCleanup_ShouldNotCauseErrors() {
        ReadOptions readOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(512)
                .largeThreadLocalBufferSize(2048)
                .build();
        
        // Process some JSON to initialize ThreadLocal buffers
        String jsonInput = "{\"test\":\"buffer cleanup test\"}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(jsonInput, readOptions);
            assertNotNull(result);
            
            // Clean up ThreadLocal buffers
            JsonParser.clearThreadLocalBuffers();
            
            // Should still work after cleanup (will create new buffers)
            Object result2 = JsonIo.toJava(jsonInput, readOptions);
            assertNotNull(result2);
        });
    }

    @Test
    public void testThreadLocalBufferConfiguration_IndependentOfOtherLimits() {
        // Test that ThreadLocal buffer limits are independent of other security limits
        ReadOptions options = new ReadOptionsBuilder()
                .threadLocalBufferSize(256)
                .largeThreadLocalBufferSize(1024)
                .maxDepth(50)                       // Security limit
                .maxUnresolvedReferences(10000)     // Security limit
                .build();
        
        // ThreadLocal buffer limits should be set correctly
        assertEquals(256, options.getThreadLocalBufferSize());
        assertEquals(1024, options.getLargeThreadLocalBufferSize());
        
        // Other security limits should also be set correctly
        assertEquals(50, options.getMaxDepth());
        assertEquals(10000, options.getMaxUnresolvedReferences());
        
        // All should work together
        String testJson = "{\"nested\":{\"value\":\"test\"}}";
        
        assertDoesNotThrow(() -> {
            Object result = JsonIo.toJava(testJson, options);
            assertNotNull(result);
        });
    }

    @Test
    public void testThreadLocalBufferPerformance_DifferentSizes() {
        // Test that different buffer sizes don't cause failures (performance test concept)
        String mediumJson = createJsonWithRepeatedData(50);
        
        // Test with small buffers
        ReadOptions smallBuffers = new ReadOptionsBuilder()
                .threadLocalBufferSize(64)
                .largeThreadLocalBufferSize(256)
                .build();
        
        // Test with large buffers  
        ReadOptions largeBuffers = new ReadOptionsBuilder()
                .threadLocalBufferSize(2048)
                .largeThreadLocalBufferSize(8192)
                .build();
        
        // Both should work without errors
        assertDoesNotThrow(() -> {
            Object result1 = JsonIo.toJava(mediumJson, smallBuffers);
            assertNotNull(result1);
        });
        
        assertDoesNotThrow(() -> {
            Object result2 = JsonIo.toJava(mediumJson, largeBuffers);
            assertNotNull(result2);
        });
    }

    @Test
    public void testExtremeThreadLocalBufferValues_ShouldWorkCorrectly() {
        // Test with minimum valid values
        ReadOptions minOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(1)            // Minimum size
                .largeThreadLocalBufferSize(1)       // Minimum size
                .build();
        
        assertEquals(1, minOptions.getThreadLocalBufferSize());
        assertEquals(1, minOptions.getLargeThreadLocalBufferSize());
        
        // Test with very large values
        ReadOptions maxOptions = new ReadOptionsBuilder()
                .threadLocalBufferSize(65536)        // 64KB
                .largeThreadLocalBufferSize(131072)  // 128KB
                .build();
        
        assertEquals(65536, maxOptions.getThreadLocalBufferSize());
        assertEquals(131072, maxOptions.getLargeThreadLocalBufferSize());
        
        // Both extreme configurations should work for basic JSON processing
        String testJson = "{\"test\":\"extreme values\"}";
        
        assertDoesNotThrow(() -> {
            Object result1 = JsonIo.toJava(testJson, minOptions);
            assertNotNull(result1);
        });
        
        assertDoesNotThrow(() -> {
            Object result2 = JsonIo.toJava(testJson, maxOptions);
            assertNotNull(result2);
        });
    }

    @Test 
    public void testThreadLocalBufferCopyConstructor_PreservesConfiguration() {
        ReadOptions original = new ReadOptionsBuilder()
                .threadLocalBufferSize(777)          // Custom size
                .largeThreadLocalBufferSize(1555)    // Custom size
                .build();
        
        ReadOptions copied = new ReadOptionsBuilder(original).build();
        
        assertEquals(777, copied.getThreadLocalBufferSize());
        assertEquals(1555, copied.getLargeThreadLocalBufferSize());
        
        // Both should work identically
        String testJson = "{\"copy\":\"test\"}";
        
        assertDoesNotThrow(() -> {
            Object result1 = JsonIo.toJava(testJson, original);
            Object result2 = JsonIo.toJava(testJson, copied);
            assertNotNull(result1);
            assertNotNull(result2);
        });
    }

    /**
     * Helper method to create JSON with repeated data for testing
     */
    private String createJsonWithRepeatedData(int repetitions) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < repetitions; i++) {
            if (i > 0) json.append(",");
            json.append("\"key").append(i).append("\":\"value").append(i).append("\"");
        }
        json.append("}");
        return json.toString();
    }
}