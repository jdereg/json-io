package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ReadOptionsBuilder permanent configuration methods.
 * These tests ensure that permanent settings are properly applied to all new ReadOptions instances
 * and validate the configuration management capabilities.
 * 
 * @author Claude4.0s (claude4.0s@ai.assistant)
 */
class ReadOptionsPermanentMethodsTest {

    // Store original values to restore after tests
    private int originalMaxDepth;
    private int originalLruSize;
    private boolean originalAllowNanAndInfinity;
    private boolean originalFailOnUnknownType;
    private boolean originalCloseStream;

    @BeforeEach
    void setUp() {
        // Capture original permanent settings
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        originalMaxDepth = defaultOptions.getMaxDepth();
        originalLruSize = defaultOptions.getLruSize();
        originalAllowNanAndInfinity = defaultOptions.isAllowNanAndInfinity();
        originalFailOnUnknownType = defaultOptions.isFailOnUnknownType();
        originalCloseStream = defaultOptions.isCloseStream();
    }

    @AfterEach
    void tearDown() {
        // Restore original permanent settings
        ReadOptionsBuilder.addPermanentMaxDepth(originalMaxDepth);
        ReadOptionsBuilder.addPermanentLruSize(originalLruSize);
        ReadOptionsBuilder.addPermanentAllowNanAndInfinity(originalAllowNanAndInfinity);
        ReadOptionsBuilder.addPermanentFailOnUnknownType(originalFailOnUnknownType);
        ReadOptionsBuilder.addPermanentCloseStream(originalCloseStream);
    }

    @Test
    void testAddPermanentMaxDepth_SetsGlobalDefault() {
        // Set a custom permanent max depth
        int customMaxDepth = 1500;
        ReadOptionsBuilder.addPermanentMaxDepth(customMaxDepth);
        
        // Verify new ReadOptions instances use the permanent setting
        ReadOptions options1 = new ReadOptionsBuilder().build();
        ReadOptions options2 = new ReadOptionsBuilder().build();
        
        assertEquals(customMaxDepth, options1.getMaxDepth());
        assertEquals(customMaxDepth, options2.getMaxDepth());
    }

    @Test
    void testAddPermanentMaxDepth_CanBeOverriddenLocally() {
        // Set a permanent max depth
        ReadOptionsBuilder.addPermanentMaxDepth(1500);
        
        // Override locally in specific instance
        int localMaxDepth = 2000;
        ReadOptions options = new ReadOptionsBuilder()
                .maxDepth(localMaxDepth)
                .build();
        
        assertEquals(localMaxDepth, options.getMaxDepth());
        
        // Verify permanent setting still applies to new instances
        ReadOptions newOptions = new ReadOptionsBuilder().build();
        assertEquals(1500, newOptions.getMaxDepth());
    }

    @Test
    void testAddPermanentMaxDepth_ValidatesInput() {
        // Test validation - should reject values less than 1
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMaxDepth(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentMaxDepth(-1);
        });
    }

    @Test
    void testAddPermanentLruSize_SetsGlobalDefault() {
        // Set a custom permanent LRU size
        int customLruSize = 750;
        ReadOptionsBuilder.addPermanentLruSize(customLruSize);
        
        // Verify new ReadOptions instances use the permanent setting
        ReadOptions options1 = new ReadOptionsBuilder().build();
        ReadOptions options2 = new ReadOptionsBuilder().build();
        
        assertEquals(customLruSize, options1.getLruSize());
        assertEquals(customLruSize, options2.getLruSize());
    }

    @Test
    void testAddPermanentLruSize_CanBeOverriddenLocally() {
        // Set a permanent LRU size
        ReadOptionsBuilder.addPermanentLruSize(750);
        
        // Override locally in specific instance
        int localLruSize = 1000;
        ReadOptions options = new ReadOptionsBuilder()
                .lruSize(localLruSize)
                .build();
        
        assertEquals(localLruSize, options.getLruSize());
        
        // Verify permanent setting still applies to new instances
        ReadOptions newOptions = new ReadOptionsBuilder().build();
        assertEquals(750, newOptions.getLruSize());
    }

    @Test
    void testAddPermanentLruSize_ValidatesInput() {
        // Test validation - should reject values less than 1
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLruSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            ReadOptionsBuilder.addPermanentLruSize(-1);
        });
    }

    @Test
    void testAddPermanentAllowNanAndInfinity_SetsGlobalDefault() {
        // Set a custom permanent allow NaN and Infinity setting
        boolean customAllowNanAndInfinity = false;
        ReadOptionsBuilder.addPermanentAllowNanAndInfinity(customAllowNanAndInfinity);
        
        // Verify new ReadOptions instances use the permanent setting
        ReadOptions options1 = new ReadOptionsBuilder().build();
        ReadOptions options2 = new ReadOptionsBuilder().build();
        
        assertEquals(customAllowNanAndInfinity, options1.isAllowNanAndInfinity());
        assertEquals(customAllowNanAndInfinity, options2.isAllowNanAndInfinity());
    }

    @Test
    void testAddPermanentAllowNanAndInfinity_CanBeOverriddenLocally() {
        // Set a permanent allow NaN and Infinity setting
        ReadOptionsBuilder.addPermanentAllowNanAndInfinity(false);
        
        // Override locally in specific instance
        boolean localAllowNanAndInfinity = true;
        ReadOptions options = new ReadOptionsBuilder()
                .allowNanAndInfinity(localAllowNanAndInfinity)
                .build();
        
        assertEquals(localAllowNanAndInfinity, options.isAllowNanAndInfinity());
        
        // Verify permanent setting still applies to new instances
        ReadOptions newOptions = new ReadOptionsBuilder().build();
        assertFalse(newOptions.isAllowNanAndInfinity());
    }

    @Test
    void testAddPermanentFailOnUnknownType_SetsGlobalDefault() {
        // Set a custom permanent fail on unknown type setting
        boolean customFailOnUnknownType = true;
        ReadOptionsBuilder.addPermanentFailOnUnknownType(customFailOnUnknownType);
        
        // Verify new ReadOptions instances use the permanent setting
        ReadOptions options1 = new ReadOptionsBuilder().build();
        ReadOptions options2 = new ReadOptionsBuilder().build();
        
        assertEquals(customFailOnUnknownType, options1.isFailOnUnknownType());
        assertEquals(customFailOnUnknownType, options2.isFailOnUnknownType());
    }

    @Test
    void testAddPermanentFailOnUnknownType_CanBeOverriddenLocally() {
        // Set a permanent fail on unknown type setting
        ReadOptionsBuilder.addPermanentFailOnUnknownType(true);
        
        // Override locally in specific instance
        boolean localFailOnUnknownType = false;
        ReadOptions options = new ReadOptionsBuilder()
                .failOnUnknownType(localFailOnUnknownType)
                .build();
        
        assertEquals(localFailOnUnknownType, options.isFailOnUnknownType());
        
        // Verify permanent setting still applies to new instances
        ReadOptions newOptions = new ReadOptionsBuilder().build();
        assertTrue(newOptions.isFailOnUnknownType());
    }

    @Test
    void testAddPermanentCloseStream_SetsGlobalDefault() {
        // Set a custom permanent close stream setting
        boolean customCloseStream = false;
        ReadOptionsBuilder.addPermanentCloseStream(customCloseStream);
        
        // Verify new ReadOptions instances use the permanent setting
        ReadOptions options1 = new ReadOptionsBuilder().build();
        ReadOptions options2 = new ReadOptionsBuilder().build();
        
        assertEquals(customCloseStream, options1.isCloseStream());
        assertEquals(customCloseStream, options2.isCloseStream());
    }

    @Test
    void testAddPermanentCloseStream_CanBeOverriddenLocally() {
        // Set a permanent close stream setting
        ReadOptionsBuilder.addPermanentCloseStream(false);
        
        // Override locally in specific instance
        boolean localCloseStream = true;
        ReadOptions options = new ReadOptionsBuilder()
                .closeStream(localCloseStream)
                .build();
        
        assertEquals(localCloseStream, options.isCloseStream());
        
        // Verify permanent setting still applies to new instances
        ReadOptions newOptions = new ReadOptionsBuilder().build();
        assertFalse(newOptions.isCloseStream());
    }

    @Test
    void testPermanentSettings_CopiedToNewInstancesFromOtherOptions() {
        // Set custom permanent settings
        ReadOptionsBuilder.addPermanentMaxDepth(1200);
        ReadOptionsBuilder.addPermanentLruSize(600);
        ReadOptionsBuilder.addPermanentAllowNanAndInfinity(false);
        ReadOptionsBuilder.addPermanentFailOnUnknownType(true);
        ReadOptionsBuilder.addPermanentCloseStream(false);
        
        // Create an options instance with local overrides
        ReadOptions sourceOptions = new ReadOptionsBuilder()
                .maxDepth(1500)  // Local override
                .lruSize(800)    // Local override
                .build();
        
        // Create new instance using copy constructor
        ReadOptions copiedOptions = new ReadOptionsBuilder(sourceOptions).build();
        
        // Verify the copied instance has the source's values (not the permanent ones)
        assertEquals(1500, copiedOptions.getMaxDepth());
        assertEquals(800, copiedOptions.getLruSize());
        assertEquals(false, copiedOptions.isAllowNanAndInfinity());
        assertEquals(true, copiedOptions.isFailOnUnknownType());
        assertEquals(false, copiedOptions.isCloseStream());
    }

    @Test
    void testAllPermanentCoreSettings_WorkTogether() {
        // Set all permanent core settings to non-default values
        int customMaxDepth = 1800;
        int customLruSize = 650;
        boolean customAllowNanAndInfinity = false;
        boolean customFailOnUnknownType = true;
        boolean customCloseStream = false;
        
        ReadOptionsBuilder.addPermanentMaxDepth(customMaxDepth);
        ReadOptionsBuilder.addPermanentLruSize(customLruSize);
        ReadOptionsBuilder.addPermanentAllowNanAndInfinity(customAllowNanAndInfinity);
        ReadOptionsBuilder.addPermanentFailOnUnknownType(customFailOnUnknownType);
        ReadOptionsBuilder.addPermanentCloseStream(customCloseStream);
        
        // Create multiple new instances and verify all settings
        for (int i = 0; i < 3; i++) {
            ReadOptions options = new ReadOptionsBuilder().build();
            
            assertEquals(customMaxDepth, options.getMaxDepth());
            assertEquals(customLruSize, options.getLruSize());
            assertEquals(customAllowNanAndInfinity, options.isAllowNanAndInfinity());
            assertEquals(customFailOnUnknownType, options.isFailOnUnknownType());
            assertEquals(customCloseStream, options.isCloseStream());
        }
    }

    @Test
    void testPermanentSettings_ThreadSafety() throws InterruptedException {
        // Test thread safety of permanent settings
        final int numThreads = 10;
        final int maxDepth = 2000;
        
        // Set permanent setting from main thread
        ReadOptionsBuilder.addPermanentMaxDepth(maxDepth);
        
        Thread[] threads = new Thread[numThreads];
        final boolean[] results = new boolean[numThreads];
        
        // Create multiple threads that read the permanent setting
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                ReadOptions options = new ReadOptionsBuilder().build();
                results[threadIndex] = (options.getMaxDepth() == maxDepth);
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all threads saw the correct permanent setting
        for (boolean result : results) {
            assertTrue(result);
        }
    }
}