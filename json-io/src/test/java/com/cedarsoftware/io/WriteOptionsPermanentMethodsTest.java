package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for WriteOptionsBuilder permanent configuration methods.
 * These tests ensure that permanent settings are properly applied to all new WriteOptions instances
 * and validate the configuration management capabilities.
 * 
 * @author Claude4.0s (claude4.0s@ai.assistant)
 */
class WriteOptionsPermanentMethodsTest {

    // Store original values to restore after tests
    private ClassLoader originalClassLoader;
    private boolean originalShortMetaKeys;
    private WriteOptions.ShowType originalShowTypeInfo;
    private boolean originalPrettyPrint;
    private int originalLruSize;
    private boolean originalWriteLongsAsStrings;
    private boolean originalSkipNullFields;
    private boolean originalForceMapOutputAsTwoArrays;
    private boolean originalAllowNanAndInfinity;
    private boolean originalEnumPublicFieldsOnly;
    private boolean originalEnumSetWrittenOldWay;
    private boolean originalCloseStream;

    @BeforeEach
    void setUp() {
        // Capture original permanent settings
        WriteOptions defaultOptions = new WriteOptionsBuilder().build();
        originalClassLoader = defaultOptions.getClassLoader();
        originalShortMetaKeys = defaultOptions.isShortMetaKeys();
        originalShowTypeInfo = defaultOptions.isAlwaysShowingType() ? WriteOptions.ShowType.ALWAYS :
                               defaultOptions.isNeverShowingType() ? WriteOptions.ShowType.NEVER :
                               WriteOptions.ShowType.MINIMAL;
        originalPrettyPrint = defaultOptions.isPrettyPrint();
        originalLruSize = defaultOptions.getLruSize();
        originalWriteLongsAsStrings = defaultOptions.isWriteLongsAsStrings();
        originalSkipNullFields = defaultOptions.isSkipNullFields();
        originalForceMapOutputAsTwoArrays = defaultOptions.isForceMapOutputAsTwoArrays();
        originalAllowNanAndInfinity = defaultOptions.isAllowNanAndInfinity();
        originalEnumPublicFieldsOnly = defaultOptions.isEnumPublicFieldsOnly();
        originalEnumSetWrittenOldWay = defaultOptions.isEnumSetWrittenOldWay();
        originalCloseStream = defaultOptions.isCloseStream();
    }

    @AfterEach
    void tearDown() {
        // Restore original permanent settings
        WriteOptionsBuilder.addPermanentClassLoader(originalClassLoader);
        WriteOptionsBuilder.addPermanentShortMetaKeys(originalShortMetaKeys);
        if (originalShowTypeInfo == WriteOptions.ShowType.ALWAYS) {
            WriteOptionsBuilder.addPermanentShowTypeInfoAlways();
        } else if (originalShowTypeInfo == WriteOptions.ShowType.NEVER) {
            WriteOptionsBuilder.addPermanentShowTypeInfoNever();
        } else {
            WriteOptionsBuilder.addPermanentShowTypeInfoMinimal();
        }
        WriteOptionsBuilder.addPermanentPrettyPrint(originalPrettyPrint);
        WriteOptionsBuilder.addPermanentLruSize(originalLruSize);
        WriteOptionsBuilder.addPermanentWriteLongsAsStrings(originalWriteLongsAsStrings);
        WriteOptionsBuilder.addPermanentSkipNullFields(originalSkipNullFields);
        WriteOptionsBuilder.addPermanentForceMapOutputAsTwoArrays(originalForceMapOutputAsTwoArrays);
        WriteOptionsBuilder.addPermanentAllowNanAndInfinity(originalAllowNanAndInfinity);
        WriteOptionsBuilder.addPermanentEnumPublicFieldsOnly(originalEnumPublicFieldsOnly);
        WriteOptionsBuilder.addPermanentEnumSetWrittenOldWay(originalEnumSetWrittenOldWay);
        WriteOptionsBuilder.addPermanentCloseStream(originalCloseStream);
    }

    @Test
    void testAddPermanentClassLoader_SetsGlobalDefault() {
        // Set a custom permanent class loader
        ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
        WriteOptionsBuilder.addPermanentClassLoader(customClassLoader);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customClassLoader, options1.getClassLoader());
        assertEquals(customClassLoader, options2.getClassLoader());
    }

    @Test
    void testAddPermanentClassLoader_CanBeOverriddenLocally() {
        // Set a permanent class loader
        ClassLoader permanentClassLoader = Thread.currentThread().getContextClassLoader();
        WriteOptionsBuilder.addPermanentClassLoader(permanentClassLoader);
        
        // Override locally in specific instance
        ClassLoader localClassLoader = ClassLoader.getSystemClassLoader();
        WriteOptions options = new WriteOptionsBuilder()
                .classLoader(localClassLoader)
                .build();
        
        assertEquals(localClassLoader, options.getClassLoader());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertEquals(permanentClassLoader, newOptions.getClassLoader());
    }

    @Test
    void testAddPermanentClassLoader_ValidatesInput() {
        // Test validation - should reject null values
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentClassLoader(null);
        });
    }

    @Test
    void testAddPermanentShortMetaKeys_SetsGlobalDefault() {
        // Set a custom permanent short meta keys setting
        boolean customShortMetaKeys = true;
        WriteOptionsBuilder.addPermanentShortMetaKeys(customShortMetaKeys);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customShortMetaKeys, options1.isShortMetaKeys());
        assertEquals(customShortMetaKeys, options2.isShortMetaKeys());
    }

    @Test
    void testAddPermanentShortMetaKeys_CanBeOverriddenLocally() {
        // Set a permanent short meta keys setting
        WriteOptionsBuilder.addPermanentShortMetaKeys(true);
        
        // Override locally in specific instance
        boolean localShortMetaKeys = false;
        WriteOptions options = new WriteOptionsBuilder()
                .shortMetaKeys(localShortMetaKeys)
                .build();
        
        assertEquals(localShortMetaKeys, options.isShortMetaKeys());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isShortMetaKeys());
    }

    @Test
    void testAddPermanentShowTypeInfo_SetsGlobalDefaults() {
        // Test ALWAYS setting
        WriteOptionsBuilder.addPermanentShowTypeInfoAlways();
        WriteOptions options1 = new WriteOptionsBuilder().build();
        assertTrue(options1.isAlwaysShowingType());
        assertFalse(options1.isNeverShowingType());
        assertFalse(options1.isMinimalShowingType());
        
        // Test NEVER setting
        WriteOptionsBuilder.addPermanentShowTypeInfoNever();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        assertFalse(options2.isAlwaysShowingType());
        assertTrue(options2.isNeverShowingType());
        assertFalse(options2.isMinimalShowingType());
        
        // Test MINIMAL setting
        WriteOptionsBuilder.addPermanentShowTypeInfoMinimal();
        WriteOptions options3 = new WriteOptionsBuilder().build();
        assertFalse(options3.isAlwaysShowingType());
        assertFalse(options3.isNeverShowingType());
        assertTrue(options3.isMinimalShowingType());
    }

    @Test
    void testAddPermanentShowTypeInfo_CanBeOverriddenLocally() {
        // Set a permanent show type info setting
        WriteOptionsBuilder.addPermanentShowTypeInfoAlways();
        
        // Override locally in specific instance
        WriteOptions options = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();
        
        assertTrue(options.isNeverShowingType());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isAlwaysShowingType());
    }

    @Test
    void testAddPermanentPrettyPrint_SetsGlobalDefault() {
        // Set a custom permanent pretty print setting
        boolean customPrettyPrint = true;
        WriteOptionsBuilder.addPermanentPrettyPrint(customPrettyPrint);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customPrettyPrint, options1.isPrettyPrint());
        assertEquals(customPrettyPrint, options2.isPrettyPrint());
    }

    @Test
    void testAddPermanentPrettyPrint_CanBeOverriddenLocally() {
        // Set a permanent pretty print setting
        WriteOptionsBuilder.addPermanentPrettyPrint(true);
        
        // Override locally in specific instance
        boolean localPrettyPrint = false;
        WriteOptions options = new WriteOptionsBuilder()
                .prettyPrint(localPrettyPrint)
                .build();
        
        assertEquals(localPrettyPrint, options.isPrettyPrint());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isPrettyPrint());
    }

    @Test
    void testAddPermanentLruSize_SetsGlobalDefault() {
        // Set a custom permanent LRU size
        int customLruSize = 750;
        WriteOptionsBuilder.addPermanentLruSize(customLruSize);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customLruSize, options1.getLruSize());
        assertEquals(customLruSize, options2.getLruSize());
    }

    @Test
    void testAddPermanentLruSize_CanBeOverriddenLocally() {
        // Set a permanent LRU size
        WriteOptionsBuilder.addPermanentLruSize(750);
        
        // Override locally in specific instance
        int localLruSize = 1000;
        WriteOptions options = new WriteOptionsBuilder()
                .lruSize(localLruSize)
                .build();
        
        assertEquals(localLruSize, options.getLruSize());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertEquals(750, newOptions.getLruSize());
    }

    @Test
    void testAddPermanentLruSize_ValidatesInput() {
        // Test validation - should reject values less than 1
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentLruSize(0);
        });
        
        assertThrows(JsonIoException.class, () -> {
            WriteOptionsBuilder.addPermanentLruSize(-1);
        });
    }

    @Test
    void testAddPermanentWriteLongsAsStrings_SetsGlobalDefault() {
        // Set a custom permanent write longs as strings setting
        boolean customWriteLongsAsStrings = true;
        WriteOptionsBuilder.addPermanentWriteLongsAsStrings(customWriteLongsAsStrings);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customWriteLongsAsStrings, options1.isWriteLongsAsStrings());
        assertEquals(customWriteLongsAsStrings, options2.isWriteLongsAsStrings());
    }

    @Test
    void testAddPermanentWriteLongsAsStrings_CanBeOverriddenLocally() {
        // Set a permanent write longs as strings setting
        WriteOptionsBuilder.addPermanentWriteLongsAsStrings(true);
        
        // Override locally in specific instance
        boolean localWriteLongsAsStrings = false;
        WriteOptions options = new WriteOptionsBuilder()
                .writeLongsAsStrings(localWriteLongsAsStrings)
                .build();
        
        assertEquals(localWriteLongsAsStrings, options.isWriteLongsAsStrings());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isWriteLongsAsStrings());
    }

    @Test
    void testAddPermanentSkipNullFields_SetsGlobalDefault() {
        // Set a custom permanent skip null fields setting
        boolean customSkipNullFields = true;
        WriteOptionsBuilder.addPermanentSkipNullFields(customSkipNullFields);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customSkipNullFields, options1.isSkipNullFields());
        assertEquals(customSkipNullFields, options2.isSkipNullFields());
    }

    @Test
    void testAddPermanentSkipNullFields_CanBeOverriddenLocally() {
        // Set a permanent skip null fields setting
        WriteOptionsBuilder.addPermanentSkipNullFields(true);
        
        // Override locally in specific instance
        boolean localSkipNullFields = false;
        WriteOptions options = new WriteOptionsBuilder()
                .skipNullFields(localSkipNullFields)
                .build();
        
        assertEquals(localSkipNullFields, options.isSkipNullFields());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isSkipNullFields());
    }

    @Test
    void testAddPermanentForceMapOutputAsTwoArrays_SetsGlobalDefault() {
        // Set a custom permanent force map output as two arrays setting
        boolean customForceMapOutputAsTwoArrays = true;
        WriteOptionsBuilder.addPermanentForceMapOutputAsTwoArrays(customForceMapOutputAsTwoArrays);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customForceMapOutputAsTwoArrays, options1.isForceMapOutputAsTwoArrays());
        assertEquals(customForceMapOutputAsTwoArrays, options2.isForceMapOutputAsTwoArrays());
    }

    @Test
    void testAddPermanentForceMapOutputAsTwoArrays_CanBeOverriddenLocally() {
        // Set a permanent force map output as two arrays setting
        WriteOptionsBuilder.addPermanentForceMapOutputAsTwoArrays(true);
        
        // Override locally in specific instance
        boolean localForceMapOutputAsTwoArrays = false;
        WriteOptions options = new WriteOptionsBuilder()
                .forceMapOutputAsTwoArrays(localForceMapOutputAsTwoArrays)
                .build();
        
        assertEquals(localForceMapOutputAsTwoArrays, options.isForceMapOutputAsTwoArrays());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isForceMapOutputAsTwoArrays());
    }

    @Test
    void testAddPermanentAllowNanAndInfinity_SetsGlobalDefault() {
        // Set a custom permanent allow NaN and Infinity setting
        boolean customAllowNanAndInfinity = true;
        WriteOptionsBuilder.addPermanentAllowNanAndInfinity(customAllowNanAndInfinity);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customAllowNanAndInfinity, options1.isAllowNanAndInfinity());
        assertEquals(customAllowNanAndInfinity, options2.isAllowNanAndInfinity());
    }

    @Test
    void testAddPermanentAllowNanAndInfinity_CanBeOverriddenLocally() {
        // Set a permanent allow NaN and Infinity setting
        WriteOptionsBuilder.addPermanentAllowNanAndInfinity(true);
        
        // Override locally in specific instance
        boolean localAllowNanAndInfinity = false;
        WriteOptions options = new WriteOptionsBuilder()
                .allowNanAndInfinity(localAllowNanAndInfinity)
                .build();
        
        assertEquals(localAllowNanAndInfinity, options.isAllowNanAndInfinity());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isAllowNanAndInfinity());
    }

    @Test
    void testAddPermanentEnumPublicFieldsOnly_SetsGlobalDefault() {
        // Set a custom permanent enum public fields only setting
        boolean customEnumPublicFieldsOnly = true;
        WriteOptionsBuilder.addPermanentEnumPublicFieldsOnly(customEnumPublicFieldsOnly);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customEnumPublicFieldsOnly, options1.isEnumPublicFieldsOnly());
        assertEquals(customEnumPublicFieldsOnly, options2.isEnumPublicFieldsOnly());
    }

    @Test
    void testAddPermanentEnumPublicFieldsOnly_CanBeOverriddenLocally() {
        // Set a permanent enum public fields only setting
        WriteOptionsBuilder.addPermanentEnumPublicFieldsOnly(true);
        
        // Override locally in specific instance
        boolean localEnumPublicFieldsOnly = false;
        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(localEnumPublicFieldsOnly)
                .build();
        
        assertEquals(localEnumPublicFieldsOnly, options.isEnumPublicFieldsOnly());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertTrue(newOptions.isEnumPublicFieldsOnly());
    }

    @Test
    void testAddPermanentEnumSetWrittenOldWay_SetsGlobalDefault() {
        // Set a custom permanent enum set written old way setting
        boolean customEnumSetWrittenOldWay = false;
        WriteOptionsBuilder.addPermanentEnumSetWrittenOldWay(customEnumSetWrittenOldWay);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customEnumSetWrittenOldWay, options1.isEnumSetWrittenOldWay());
        assertEquals(customEnumSetWrittenOldWay, options2.isEnumSetWrittenOldWay());
    }

    @Test
    void testAddPermanentEnumSetWrittenOldWay_CanBeOverriddenLocally() {
        // Set a permanent enum set written old way setting
        WriteOptionsBuilder.addPermanentEnumSetWrittenOldWay(false);
        
        // Override locally in specific instance
        boolean localEnumSetWrittenOldWay = true;
        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumSetOldWay(localEnumSetWrittenOldWay)
                .build();
        
        assertEquals(localEnumSetWrittenOldWay, options.isEnumSetWrittenOldWay());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertFalse(newOptions.isEnumSetWrittenOldWay());
    }

    @Test
    void testAddPermanentCloseStream_SetsGlobalDefault() {
        // Set a custom permanent close stream setting
        boolean customCloseStream = false;
        WriteOptionsBuilder.addPermanentCloseStream(customCloseStream);
        
        // Verify new WriteOptions instances use the permanent setting
        WriteOptions options1 = new WriteOptionsBuilder().build();
        WriteOptions options2 = new WriteOptionsBuilder().build();
        
        assertEquals(customCloseStream, options1.isCloseStream());
        assertEquals(customCloseStream, options2.isCloseStream());
    }

    @Test
    void testAddPermanentCloseStream_CanBeOverriddenLocally() {
        // Set a permanent close stream setting
        WriteOptionsBuilder.addPermanentCloseStream(false);
        
        // Override locally in specific instance
        boolean localCloseStream = true;
        WriteOptions options = new WriteOptionsBuilder()
                .closeStream(localCloseStream)
                .build();
        
        assertEquals(localCloseStream, options.isCloseStream());
        
        // Verify permanent setting still applies to new instances
        WriteOptions newOptions = new WriteOptionsBuilder().build();
        assertFalse(newOptions.isCloseStream());
    }

    @Test
    void testPermanentSettings_CopiedToNewInstancesFromOtherOptions() {
        // Set custom permanent settings
        WriteOptionsBuilder.addPermanentShortMetaKeys(true);
        WriteOptionsBuilder.addPermanentPrettyPrint(true);
        WriteOptionsBuilder.addPermanentLruSize(600);
        WriteOptionsBuilder.addPermanentWriteLongsAsStrings(true);
        WriteOptionsBuilder.addPermanentSkipNullFields(true);
        
        // Create an options instance with local overrides
        WriteOptions sourceOptions = new WriteOptionsBuilder()
                .shortMetaKeys(false)  // Local override
                .lruSize(800)    // Local override
                .build();
        
        // Create new instance using copy constructor
        WriteOptions copiedOptions = new WriteOptionsBuilder(sourceOptions).build();
        
        // Verify the copied instance has the source's values (not the permanent ones)
        assertFalse(copiedOptions.isShortMetaKeys());
        assertEquals(800, copiedOptions.getLruSize());
        assertTrue(copiedOptions.isPrettyPrint());
        assertTrue(copiedOptions.isWriteLongsAsStrings());
        assertTrue(copiedOptions.isSkipNullFields());
    }

    @Test
    void testAllPermanentCoreSettings_WorkTogether() {
        // Set all permanent core settings to non-default values
        ClassLoader customClassLoader = Thread.currentThread().getContextClassLoader();
        WriteOptionsBuilder.addPermanentClassLoader(customClassLoader);
        WriteOptionsBuilder.addPermanentShortMetaKeys(true);
        WriteOptionsBuilder.addPermanentShowTypeInfoAlways();
        WriteOptionsBuilder.addPermanentPrettyPrint(true);
        WriteOptionsBuilder.addPermanentLruSize(650);
        WriteOptionsBuilder.addPermanentWriteLongsAsStrings(true);
        WriteOptionsBuilder.addPermanentSkipNullFields(true);
        WriteOptionsBuilder.addPermanentForceMapOutputAsTwoArrays(true);
        WriteOptionsBuilder.addPermanentAllowNanAndInfinity(true);
        WriteOptionsBuilder.addPermanentEnumPublicFieldsOnly(true);
        WriteOptionsBuilder.addPermanentEnumSetWrittenOldWay(false);
        WriteOptionsBuilder.addPermanentCloseStream(false);
        
        // Create multiple new instances and verify all settings
        for (int i = 0; i < 3; i++) {
            WriteOptions options = new WriteOptionsBuilder().build();
            
            assertEquals(customClassLoader, options.getClassLoader());
            assertTrue(options.isShortMetaKeys());
            assertTrue(options.isAlwaysShowingType());
            assertTrue(options.isPrettyPrint());
            assertEquals(650, options.getLruSize());
            assertTrue(options.isWriteLongsAsStrings());
            assertTrue(options.isSkipNullFields());
            assertTrue(options.isForceMapOutputAsTwoArrays());
            assertTrue(options.isAllowNanAndInfinity());
            assertTrue(options.isEnumPublicFieldsOnly());
            assertFalse(options.isEnumSetWrittenOldWay());
            assertFalse(options.isCloseStream());
        }
    }

    @Test
    void testPermanentSettings_ThreadSafety() throws InterruptedException {
        // Test thread safety of permanent settings
        final int numThreads = 10;
        final int lruSize = 2000;
        
        // Set permanent setting from main thread
        WriteOptionsBuilder.addPermanentLruSize(lruSize);
        
        Thread[] threads = new Thread[numThreads];
        final boolean[] results = new boolean[numThreads];
        
        // Create multiple threads that read the permanent setting
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                WriteOptions options = new WriteOptionsBuilder().build();
                results[threadIndex] = (options.getLruSize() == lruSize);
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