package com.cedarsoftware.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecurityAuditLogger to ensure security logging functionality works correctly.
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
public class SecurityAuditLoggerTest {
    
    private SecurityAuditLogger auditLogger;
    private TestLogHandler testHandler;
    
    @BeforeEach
    public void setUp() {
        auditLogger = SecurityAuditLogger.getInstance();
        auditLogger.resetCounters();
        auditLogger.setAuditEnabled(true);
        
        // Set up test log handler
        testHandler = new TestLogHandler();
        Logger logger = Logger.getLogger(SecurityAuditLogger.class.getName());
        logger.addHandler(testHandler);
        logger.setLevel(Level.ALL);
    }
    
    @Test
    public void testSingletonInstance() {
        SecurityAuditLogger instance1 = SecurityAuditLogger.getInstance();
        SecurityAuditLogger instance2 = SecurityAuditLogger.getInstance();
        assertSame(instance1, instance2, "SecurityAuditLogger should be singleton");
    }
    
    @Test
    public void testAuditConfiguration() {
        // Test enabling audit
        auditLogger.setAuditEnabled(true);
        assertTrue(testHandler.getLogCount() > 0, "Should log when audit is enabled");
        
        // Test setting performance threshold
        auditLogger.setPerformanceThreshold(500);
        assertTrue(testHandler.hasLogMessage("Performance threshold set to 500ms"), 
                  "Should log performance threshold change");
    }
    
    @Test
    public void testOperationLogging() {
        auditLogger.logOperation("toJson", 50, 1000, true, null);
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.totalOperations, "Should increment total operations");
        assertEquals(0, summary.securityViolations, "Successful operation should not be violation");
    }
    
    @Test
    public void testFailedOperationLogging() {
        auditLogger.logOperation("toJava", 100, 5000, false, "Parsing error");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.totalOperations, "Should increment total operations");
        assertEquals(1, summary.securityViolations, "Failed operation should be violation");
        assertTrue(testHandler.hasLogMessage("OPERATION_FAILED"), "Should log operation failure");
    }
    
    @Test
    public void testPerformanceAnomalyLogging() {
        auditLogger.setPerformanceThreshold(100);
        auditLogger.logOperation("slowOperation", 200, 1000, true, null);
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.performanceAnomalies, "Should detect performance anomaly");
        assertTrue(testHandler.hasLogMessage("PERFORMANCE_ANOMALY"), "Should log performance anomaly");
    }
    
    @Test
    public void testSecurityLimitViolationLogging() {
        auditLogger.logSecurityLimitViolation("memory", 2000000, 1000000, "Large collection");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.memoryLimitViolations, "Should increment memory violations");
        assertEquals(1, summary.securityViolations, "Should increment total violations");
        assertTrue(testHandler.hasLogMessage("SECURITY_LIMIT_VIOLATED"), "Should log security violation");
    }
    
    @Test
    public void testStackDepthViolationLogging() {
        auditLogger.logSecurityLimitViolation("depth", 15000, 10000, "Deep nesting");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.stackDepthViolations, "Should increment stack depth violations");
        assertTrue(testHandler.hasLogMessage("depth"), "Should log depth violation details");
    }
    
    @Test
    public void testSuspiciousPatternLogging() {
        auditLogger.logSuspiciousPattern("deep_nesting", "JSON parsing", "{\"a\":{\"b\":{\"c\":...}}}");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.suspiciousPatterns.size(), "Should track suspicious pattern");
        assertTrue(summary.suspiciousPatterns.containsKey("deep_nesting"), "Should track specific pattern");
        assertTrue(testHandler.hasLogMessage("SUSPICIOUS_PATTERN"), "Should log suspicious pattern");
    }
    
    @Test
    public void testClassLoadingSecurityLogging() {
        // Test blocked class loading
        auditLogger.logClassLoadingSecurity("java.lang.Runtime", false, "Dangerous class");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.classLoadingFailures, "Should increment class loading failures");
        assertEquals(1, summary.blockedClasses.size(), "Should track blocked class");
        assertTrue(testHandler.hasLogMessage("CLASS_LOADING_BLOCKED"), "Should log blocked class");
        
        // Test allowed class loading
        auditLogger.logClassLoadingSecurity("com.example.SafeClass", true, "Safe class");
        // Should not increment failures for allowed classes
        assertEquals(1, summary.classLoadingFailures, "Should not increment for allowed classes");
    }
    
    @Test
    public void testReflectionSecurityLogging() {
        auditLogger.logReflectionSecurity("setAccessible", "java.lang.String.value", false, "Security manager denied");
        
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(1, summary.securityViolations, "Should increment security violations");
        assertTrue(testHandler.hasLogMessage("REFLECTION_BLOCKED"), "Should log blocked reflection");
    }
    
    @Test
    public void testCounterReset() {
        // Generate some audit events
        auditLogger.logOperation("test", 100, 1000, false, "error");
        auditLogger.logSecurityLimitViolation("memory", 2000000, 1000000, "test");
        auditLogger.logSuspiciousPattern("test_pattern", "test", "test_input");
        
        // Verify counters are not zero
        SecurityAuditLogger.SecurityAuditSummary beforeReset = auditLogger.getAuditSummary();
        assertTrue(beforeReset.totalOperations > 0, "Should have operations before reset");
        assertTrue(beforeReset.securityViolations > 0, "Should have violations before reset");
        
        // Reset and verify
        auditLogger.resetCounters();
        SecurityAuditLogger.SecurityAuditSummary afterReset = auditLogger.getAuditSummary();
        assertEquals(0, afterReset.totalOperations, "Should reset total operations");
        assertEquals(0, afterReset.securityViolations, "Should reset security violations");
        assertEquals(0, afterReset.suspiciousPatterns.size(), "Should reset suspicious patterns");
        assertTrue(testHandler.hasLogMessage("COUNTERS_RESET"), "Should log counter reset");
    }
    
    @Test
    public void testAuditDisabled() {
        auditLogger.setAuditEnabled(false);
        testHandler.clearLogs();
        
        auditLogger.logOperation("test", 100, 1000, false, "error");
        auditLogger.logSecurityLimitViolation("memory", 2000000, 1000000, "test");
        
        // Should not log when audit is disabled
        assertEquals(0, testHandler.getLogCount(), "Should not log when audit disabled");
        
        // But counters should still not increment when disabled
        SecurityAuditLogger.SecurityAuditSummary summary = auditLogger.getAuditSummary();
        assertEquals(0, summary.totalOperations, "Should not count when disabled");
    }
    
    @Test
    public void testCriticalEventLogging() {
        auditLogger.logSecurityLimitViolation("memory", 5000000, 1000000, "Critical violation");
        
        // Should log both as INFO and SEVERE for critical events
        assertTrue(testHandler.hasLogLevel(Level.INFO), "Should log at INFO level");
        assertTrue(testHandler.hasLogLevel(Level.SEVERE), "Should log at SEVERE level for critical events");
        assertTrue(testHandler.hasLogMessage("CRITICAL SECURITY EVENT"), "Should mark as critical");
    }
    
    @Test
    public void testInputTruncation() {
        // Create long input (Java 8 compatible)
        StringBuilder longInputBuilder = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            longInputBuilder.append("very_long_input_");
        }
        String longInput = longInputBuilder.toString();
        
        auditLogger.logSuspiciousPattern("test", "context", longInput);
        
        assertTrue(testHandler.hasLogMessage("..."), "Should truncate long input");
        assertTrue(testHandler.hasLogMessage("Input:"), "Should include truncated input");
    }
    
    /**
     * Test log handler to capture log messages for verification
     */
    private static class TestLogHandler extends Handler {
        private final AtomicInteger logCount = new AtomicInteger(0);
        private final StringBuilder logMessages = new StringBuilder();
        
        @Override
        public void publish(LogRecord record) {
            logCount.incrementAndGet();
            logMessages.append(record.getLevel()).append(": ").append(record.getMessage()).append("\n");
        }
        
        @Override
        public void flush() {
            // No-op
        }
        
        @Override
        public void close() throws SecurityException {
            // No-op
        }
        
        public int getLogCount() {
            return logCount.get();
        }
        
        public boolean hasLogMessage(String message) {
            return logMessages.toString().contains(message);
        }
        
        public boolean hasLogLevel(Level level) {
            return logMessages.toString().contains(level.toString() + ":");
        }
        
        public void clearLogs() {
            logCount.set(0);
            logMessages.setLength(0);
        }
    }
}