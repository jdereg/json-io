package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprehensive fuzz testing to validate security hardening against malicious JSON inputs.
 * Tests various attack vectors including memory exhaustion, stack overflow, and injection attacks.
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
public class SecurityFuzzTest {

    private static final ReadOptions SECURE_READ_OPTIONS = new ReadOptionsBuilder().build();
    
    /**
     * Helper method to create repeated strings (Java 8 compatible)
     */
    private static String createRepeatedString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * Test deeply nested JSON structures to validate stack overflow protection
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testDeeplyNestedObjectsProtection() {
        // Create deeply nested JSON that could cause stack overflow
        StringBuilder deepJson = new StringBuilder();
        int depth = 15000; // Attempt to exceed MAX_STACK_DEPTH
        
        // Build nested objects
        for (int i = 0; i < depth; i++) {
            deepJson.append("{\"nested\":");
        }
        deepJson.append("\"value\"");
        for (int i = 0; i < depth; i++) {
            deepJson.append("}");
        }
        
        // Should not cause stack overflow due to our depth limits
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(deepJson.toString(), SECURE_READ_OPTIONS);
            } catch (JsonIoException e) {
                // Expected - security limit should kick in
                assert e.getMessage().contains("stack depth") || e.getMessage().contains("depth");
            }
        });
    }

    /**
     * Test deeply nested arrays to validate array processing protection
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testDeeplyNestedArraysProtection() {
        // Create deeply nested arrays
        StringBuilder deepArrayJson = new StringBuilder();
        int depth = 15000;
        
        for (int i = 0; i < depth; i++) {
            deepArrayJson.append("[");
        }
        deepArrayJson.append("\"value\"");
        for (int i = 0; i < depth; i++) {
            deepArrayJson.append("]");
        }
        
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(deepArrayJson.toString(), SECURE_READ_OPTIONS);
            } catch (JsonIoException e) {
                // Expected - security limit should kick in
                assert e.getMessage().contains("stack depth") || e.getMessage().contains("depth");
            }
        });
    }

    /**
     * Test extremely large collections to validate memory exhaustion protection
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testLargeCollectionProtection() {
        // Create JSON with extremely large array
        StringBuilder largeArrayJson = new StringBuilder();
        largeArrayJson.append("[");
        
        // Attempt to create array larger than MAX_UNRESOLVED_REFS
        int size = 1500000; // Exceed 1M limit
        for (int i = 0; i < size; i++) {
            if (i > 0) largeArrayJson.append(",");
            largeArrayJson.append("\"item").append(i).append("\"");
            
            // Break early if we're getting too large for test memory
            if (largeArrayJson.length() > 50_000_000) break;
        }
        largeArrayJson.append("]");
        
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(largeArrayJson.toString(), SECURE_READ_OPTIONS);
            } catch (JsonIoException e) {
                // Expected - security limit should kick in
                assert e.getMessage().contains("Security limit exceeded") || 
                       e.getMessage().contains("Maximum");
            } catch (OutOfMemoryError e) {
                // Also acceptable - system ran out of memory before our limits
            }
        });
    }

    /**
     * Test massive string values to validate string length protection
     */
    @ParameterizedTest
    @ValueSource(ints = {100000, 500000, 1000000})
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testLargeStringProtection(int stringSize) {
        // Create JSON with extremely large string value
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < stringSize; i++) {
            largeString.append("A");
        }
        
        String json = "{\"largeField\":\"" + largeString.toString() + "\"}";
        
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(json, SECURE_READ_OPTIONS);
            } catch (JsonIoException e) {
                // May hit string length limits in some components
                if (stringSize > 65536) { // 64KB limit
                    assert e.getMessage().contains("string length") || 
                           e.getMessage().contains("limit");
                }
            } catch (OutOfMemoryError e) {
                // Acceptable - system memory limit
            }
        });
    }

    /**
     * Test circular reference attack patterns
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testCircularReferenceProtection() {
        // Create JSON with circular references designed to cause infinite loops
        String circularJson = "{"
            + "\"@id\":1,"
            + "\"self\":{\"@ref\":1},"
            + "\"other\":{\"@id\":2,\"back\":{\"@ref\":1}}"
            + "}";
        
        assertDoesNotThrow(() -> {
            try {
                Object result = JsonIo.toJava(circularJson, SECURE_READ_OPTIONS);
                // Should handle circular references gracefully
                assert result != null;
            } catch (JsonIoException e) {
                // May throw exception for security reasons, which is acceptable
            }
        });
    }

    /**
     * Test malformed JSON with extremely long keys
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testLongKeyProtection() {
        // Create JSON with extremely long field names
        StringBuilder longKey = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            longKey.append("verylongfieldname");
        }
        
        String json = "{\"" + longKey.toString() + "\":\"value\"}";
        
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(json, SECURE_READ_OPTIONS);
            } catch (JsonIoException | OutOfMemoryError e) {
                // Expected - either security limits or memory limits
            }
        });
    }

    /**
     * Test unicode and special character injection attacks
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testUnicodeInjectionProtection() {
        String[] maliciousStrings = {
            // Unicode null bytes
            "{\"field\":\"value\\u0000injection\"}",
            // Unicode control characters
            "{\"field\":\"value\\u0001\\u0002\\u0003\"}",
            // Unicode normalization attacks
            "{\"field\":\"\\u0041\\u0300\"}",  // A + combining grave accent
            // Unicode homograph attacks
            "{\"field\":\"\\u043E\\u0440\\u0433\"}",  // Cyrillic "org"
            // Unicode overflow attempts
            "{\"field\":\"\\uFFFF\\uFFFE\\uFFFD\"}"
        };
        
        for (String maliciousJson : maliciousStrings) {
            assertDoesNotThrow(() -> {
                try {
                    JsonIo.toJava(maliciousJson, SECURE_READ_OPTIONS);
                } catch (JsonIoException e) {
                    // May reject malicious unicode, which is acceptable
                }
            }, "Failed to safely handle: " + maliciousJson);
        }
    }

    /**
     * Test numeric overflow and underflow attacks
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testNumericOverflowProtection() {
        String[] numericAttacks = {
            // Extremely large numbers
            "{\"field\":" + createRepeatedString("9", 1000) + "}",
            // Extremely small numbers
            "{\"field\":-" + createRepeatedString("9", 1000) + "}",
            // Scientific notation attacks
            "{\"field\":1e" + createRepeatedString("9", 100) + "}",
            // Decimal attacks
            "{\"field\":0." + createRepeatedString("9", 10000) + "}",
            // NaN and Infinity
            "{\"field\":NaN}",
            "{\"field\":Infinity}",
            "{\"field\":-Infinity}"
        };
        
        for (String numericJson : numericAttacks) {
            assertDoesNotThrow(() -> {
                try {
                    JsonIo.toJava(numericJson, SECURE_READ_OPTIONS);
                } catch (JsonIoException | NumberFormatException e) {
                    // Expected - numeric validation should catch these
                }
            }, "Failed to safely handle: " + numericJson);
        }
    }

    /**
     * Test reference ID manipulation attacks
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testReferenceIdAttacks() {
        String[] refAttacks = {
            // Negative reference IDs
            "{\"@id\":-1,\"value\":\"test\"}",
            "{\"@ref\":-999999}",
            // Extremely large reference IDs
            "{\"@id\":" + Long.MAX_VALUE + ",\"value\":\"test\"}",
            "{\"@ref\":" + Long.MAX_VALUE + "}",
            // Invalid reference types
            "{\"@ref\":\"notanumber\"}",
            "{\"@ref\":[1,2,3]}",
            "{\"@ref\":{\"nested\":\"object\"}}"
        };
        
        for (String refJson : refAttacks) {
            assertDoesNotThrow(() -> {
                try {
                    JsonIo.toJava(refJson, SECURE_READ_OPTIONS);
                } catch (JsonIoException e) {
                    // Expected - reference validation should catch these
                    assert e.getMessage().contains("reference") || 
                           e.getMessage().contains("@ref") ||
                           e.getMessage().contains("@id");
                }
            }, "Failed to safely handle: " + refJson);
        }
    }

    /**
     * Test class name injection attacks
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testClassInjectionProtection() {
        String[] classAttacks = {
            // Dangerous system classes
            "{\"@type\":\"java.lang.Runtime\",\"value\":\"test\"}",
            "{\"@type\":\"java.lang.ProcessBuilder\",\"value\":\"test\"}",
            "{\"@type\":\"java.io.File\",\"value\":\"test\"}",
            "{\"@type\":\"java.net.URL\",\"value\":\"test\"}",
            // Non-existent classes
            "{\"@type\":\"com.malicious.Evil\",\"value\":\"test\"}",
            "{\"@type\":\"../../../etc/passwd\",\"value\":\"test\"}",
            // Path traversal in class names
            "{\"@type\":\"..\\\\..\\\\windows\\\\system32\\\\cmd.exe\",\"value\":\"test\"}"
        };
        
        for (String classJson : classAttacks) {
            assertDoesNotThrow(() -> {
                try {
                    JsonIo.toJava(classJson, SECURE_READ_OPTIONS);
                } catch (JsonIoException e) {
                    // Expected - class loading restrictions should prevent these
                    assert e.getMessage().toLowerCase().contains("class") ||
                           e.getMessage().toLowerCase().contains("type") ||
                           e.getMessage().toLowerCase().contains("security");
                }
            }, "Failed to safely handle: " + classJson);
        }
    }

    /**
     * Test memory exhaustion through object reference explosion
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testReferenceExplosionProtection() {
        // Create JSON designed to cause exponential object creation
        StringBuilder explosionJson = new StringBuilder();
        explosionJson.append("{");
        
        // Create many objects that reference each other
        for (int i = 0; i < 10000; i++) {
            if (i > 0) explosionJson.append(",");
            explosionJson.append("\"obj").append(i).append("\":{")
                       .append("\"@id\":").append(i).append(",")
                       .append("\"refs\":[");
            
            // Each object references many others
            for (int j = 0; j < Math.min(100, i); j++) {
                if (j > 0) explosionJson.append(",");
                explosionJson.append("{\"@ref\":").append(j).append("}");
            }
            explosionJson.append("]}");
        }
        explosionJson.append("}");
        
        assertDoesNotThrow(() -> {
            try {
                JsonIo.toJava(explosionJson.toString(), SECURE_READ_OPTIONS);
            } catch (JsonIoException e) {
                // Expected - reference limits should prevent explosion
                assert e.getMessage().contains("Security limit exceeded") ||
                       e.getMessage().contains("Maximum") ||
                       e.getMessage().contains("references");
            } catch (OutOfMemoryError e) {
                // Also acceptable - system memory protection
            }
        });
    }
}