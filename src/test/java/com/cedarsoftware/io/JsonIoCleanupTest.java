package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonIoCleanup utility class to ensure ThreadLocal resource management works correctly.
 * 
 * @author Claude Code AI Assistant
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class JsonIoCleanupTest {
    
    @Test
    public void testClearThreadLocals() {
        // First trigger some JSON processing to populate ThreadLocal buffers
        String json = "{\"name\":\"test\",\"value\":42}";
        TestObject result = JsonIo.toJava(json, null).asClass(TestObject.class);
        assertNotNull(result);
        assertEquals("test", result.name);
        assertEquals(42, result.value);
        
        // Clear ThreadLocal resources - should not throw any exceptions
        assertDoesNotThrow(() -> JsonIoCleanup.clearThreadLocals());
        
        // Should be able to call multiple times safely
        assertDoesNotThrow(() -> JsonIoCleanup.clearThreadLocals());
    }
    
    @Test
    public void testCleanupAlias() {
        // Test the cleanup() alias method
        String json = "[1,2,3,4,5]";
        int[] result = JsonIo.toJava(json, null).asClass(int[].class);
        assertArrayEquals(new int[]{1,2,3,4,5}, result);
        
        // Cleanup should work the same as clearThreadLocals
        assertDoesNotThrow(() -> JsonIoCleanup.cleanup());
    }
    
    @Test
    public void testHasThreadLocalResources() {
        // This method currently returns false as noted in implementation
        // Test that it doesn't throw exceptions
        assertDoesNotThrow(() -> {
            boolean hasResources = JsonIoCleanup.hasThreadLocalResources();
            // Should return false as per current implementation
            assertFalse(hasResources);
        });
    }
    
    @Test
    public void testGetThreadLocalInfo() {
        String info = JsonIoCleanup.getThreadLocalInfo();
        assertNotNull(info);
        assertTrue(info.contains("JsonParser"), "Should mention JsonParser resources");
        assertTrue(info.contains("STRING_BUFFER"), "Should mention STRING_BUFFER");
        assertTrue(info.contains("LARGE_STRING_BUFFER"), "Should mention LARGE_STRING_BUFFER");
        assertTrue(info.contains("clearThreadLocals"), "Should mention cleanup method");
    }
    
    @Test
    public void testAutoCleanupWithTryWithResources() {
        // Test the try-with-resources automatic cleanup
        String json = "{\"items\":[\"a\",\"b\",\"c\"]}";
        
        TestContainer result;
        try (JsonIoCleanup.ThreadLocalCleaner cleaner = JsonIoCleanup.autoCleanup()) {
            result = JsonIo.toJava(json, null).asClass(TestContainer.class);
            assertNotNull(result);
            assertNotNull(result.items);
            assertEquals(3, result.items.length);
            assertEquals("a", result.items[0]);
            assertEquals("b", result.items[1]);
            assertEquals("c", result.items[2]);
        } // Automatic cleanup happens here
        
        // Result should still be valid after cleanup
        assertNotNull(result);
        assertEquals(3, result.items.length);
    }
    
    @Test
    public void testAutoCleanupExceptionHandling() {
        // Test that cleanup happens even if exception is thrown
        assertThrows(RuntimeException.class, () -> {
            try (JsonIoCleanup.ThreadLocalCleaner cleaner = JsonIoCleanup.autoCleanup()) {
                // Process some JSON first
                JsonIo.toJava("{\"test\":true}", null).asClass(Object.class);
                // Then throw an exception
                throw new RuntimeException("Test exception");
            } // Cleanup should still happen
        });
        
        // Subsequent processing should still work
        String json = "{\"name\":\"after exception\"}";
        TestObject result = JsonIo.toJava(json, null).asClass(TestObject.class);
        assertEquals("after exception", result.name);
    }
    
    @Test
    public void testConstructorThrowsException() {
        // Test that JsonIoCleanup cannot be instantiated
        java.lang.reflect.InvocationTargetException exception = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> {
            // Use reflection to try to create an instance
            java.lang.reflect.Constructor<?> constructor = JsonIoCleanup.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        });
        
        // Verify the cause is UnsupportedOperationException
        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        assertTrue(exception.getCause().getMessage().contains("utility class"));
    }
    
    @Test
    public void testConcurrentCleanup() throws InterruptedException {
        // Test that cleanup is thread-safe
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];
        
        // Create threads that each do JSON processing and cleanup
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    // Process JSON
                    String json = "{\"name\":\"thread" + threadIndex + "\",\"value\":" + threadIndex + "}";
                    TestObject result = JsonIo.toJava(json, null).asClass(TestObject.class);
                    assertEquals(threadIndex, result.value);
                    
                    // Cleanup ThreadLocals
                    JsonIoCleanup.clearThreadLocals();
                    
                    results[threadIndex] = true;
                } catch (Exception e) {
                    results[threadIndex] = false;
                }
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
        
        // Verify all threads completed successfully
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "Thread " + i + " failed");
        }
    }
    
    // Test classes for JSON parsing
    public static class TestObject {
        public String name;
        public int value;
    }
    
    public static class TestContainer {
        public String[] items;
    }
}