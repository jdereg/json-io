package com.cedarsoftware.io;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Comprehensive security audit logging for json-io operations.
 * Tracks security events, suspicious patterns, and performance metrics
 * for security monitoring and incident response.
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
public class SecurityAuditLogger {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityAuditLogger.class.getName());
    private static final SecurityAuditLogger INSTANCE = new SecurityAuditLogger();
    
    // Security event counters
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong securityViolations = new AtomicLong(0);
    private final AtomicLong performanceAnomalies = new AtomicLong(0);
    private final AtomicLong classLoadingFailures = new AtomicLong(0);
    private final AtomicLong memoryLimitViolations = new AtomicLong(0);
    private final AtomicLong stackDepthViolations = new AtomicLong(0);
    
    // Suspicious pattern tracking
    private final ConcurrentHashMap<String, AtomicLong> suspiciousPatterns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> blockedClasses = new ConcurrentHashMap<>();
    
    // Configuration
    private volatile boolean auditEnabled = Boolean.parseBoolean(
        System.getProperty("json-io.security.audit.enabled", "false"));
    private volatile long performanceThresholdMs = Long.parseLong(
        System.getProperty("json-io.security.performance.threshold", "1000"));
    
    private SecurityAuditLogger() {
        // Private constructor for singleton
    }
    
    public static SecurityAuditLogger getInstance() {
        return INSTANCE;
    }
    
    /**
     * Enable or disable security audit logging
     */
    public void setAuditEnabled(boolean enabled) {
        this.auditEnabled = enabled;
        if (enabled) {
            logSecurityEvent(SecurityEvent.AUDIT_ENABLED, "Security audit logging enabled", null);
        }
    }
    
    /**
     * Set performance anomaly threshold in milliseconds
     */
    public void setPerformanceThreshold(long thresholdMs) {
        this.performanceThresholdMs = thresholdMs;
        logSecurityEvent(SecurityEvent.CONFIG_CHANGE, 
            "Performance threshold set to " + thresholdMs + "ms", null);
    }
    
    /**
     * Log a JSON operation with timing and security context
     */
    public void logOperation(String operation, long durationMs, int inputSize, boolean success, String error) {
        if (!auditEnabled) return;
        
        totalOperations.incrementAndGet();
        
        if (!success) {
            securityViolations.incrementAndGet();
            logSecurityEvent(SecurityEvent.OPERATION_FAILED, 
                String.format("Operation: %s, Duration: %dms, Size: %d, Error: %s", 
                    operation, durationMs, inputSize, error), error);
        }
        
        if (durationMs > performanceThresholdMs) {
            performanceAnomalies.incrementAndGet();
            logSecurityEvent(SecurityEvent.PERFORMANCE_ANOMALY,
                String.format("Slow operation: %s, Duration: %dms, Size: %d", 
                    operation, durationMs, inputSize), null);
        }
        
        // Log successful operations at fine level
        if (success && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("JSON operation successful: %s, Duration: %dms, Size: %d", 
                operation, durationMs, inputSize));
        }
    }
    
    /**
     * Log security limit violations
     */
    public void logSecurityLimitViolation(String limitType, long attemptedValue, long maxValue, String context) {
        if (!auditEnabled) return;
        
        if ("memory".equals(limitType) || "collection".equals(limitType)) {
            memoryLimitViolations.incrementAndGet();
        } else if ("depth".equals(limitType) || "stack".equals(limitType)) {
            stackDepthViolations.incrementAndGet();
        }
        
        securityViolations.incrementAndGet();
        logSecurityEvent(SecurityEvent.SECURITY_LIMIT_VIOLATED,
            String.format("Security limit exceeded - Type: %s, Attempted: %d, Max: %d, Context: %s",
                limitType, attemptedValue, maxValue, context), null);
    }
    
    /**
     * Log suspicious patterns detected in JSON input
     */
    public void logSuspiciousPattern(String pattern, String context, String input) {
        if (!auditEnabled) return;
        
        suspiciousPatterns.computeIfAbsent(pattern, k -> new AtomicLong(0)).incrementAndGet();
        
        // Truncate input for logging
        String truncatedInput = input != null && input.length() > 200 ? 
            input.substring(0, 200) + "..." : input;
            
        logSecurityEvent(SecurityEvent.SUSPICIOUS_PATTERN,
            String.format("Suspicious pattern detected - Pattern: %s, Context: %s, Input: %s",
                pattern, context, truncatedInput), null);
    }
    
    /**
     * Log class loading security events
     */
    public void logClassLoadingSecurity(String className, boolean allowed, String reason) {
        if (!auditEnabled) return;
        
        if (!allowed) {
            classLoadingFailures.incrementAndGet();
            blockedClasses.computeIfAbsent(className, k -> new AtomicLong(0)).incrementAndGet();
            
            logSecurityEvent(SecurityEvent.CLASS_LOADING_BLOCKED,
                String.format("Class loading blocked - Class: %s, Reason: %s", className, reason), null);
        } else if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Class loading allowed: %s", className));
        }
    }
    
    /**
     * Log reflection security events
     */
    public void logReflectionSecurity(String operation, String target, boolean allowed, String reason) {
        if (!auditEnabled) return;
        
        if (!allowed) {
            securityViolations.incrementAndGet();
            logSecurityEvent(SecurityEvent.REFLECTION_BLOCKED,
                String.format("Reflection blocked - Operation: %s, Target: %s, Reason: %s", 
                    operation, target, reason), null);
        } else if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Reflection allowed - Operation: %s, Target: %s", operation, target));
        }
    }
    
    /**
     * Get security audit summary
     */
    public SecurityAuditSummary getAuditSummary() {
        return new SecurityAuditSummary(
            totalOperations.get(),
            securityViolations.get(),
            performanceAnomalies.get(),
            classLoadingFailures.get(),
            memoryLimitViolations.get(),
            stackDepthViolations.get(),
            new ConcurrentHashMap<>(suspiciousPatterns),
            new ConcurrentHashMap<>(blockedClasses)
        );
    }
    
    /**
     * Reset all security counters
     */
    public void resetCounters() {
        totalOperations.set(0);
        securityViolations.set(0);
        performanceAnomalies.set(0);
        classLoadingFailures.set(0);
        memoryLimitViolations.set(0);
        stackDepthViolations.set(0);
        suspiciousPatterns.clear();
        blockedClasses.clear();
        
        if (auditEnabled) {
            logSecurityEvent(SecurityEvent.COUNTERS_RESET, "Security audit counters reset", null);
        }
    }
    
    /**
     * Core security event logging method
     */
    private void logSecurityEvent(SecurityEvent event, String message, String error) {
        if (!auditEnabled) return;
        
        String logMessage = String.format("[SECURITY] %s: %s", event.name(), message);
        
        if (error != null) {
            LOGGER.warning(logMessage + " | Error: " + error);
        } else {
            LOGGER.info(logMessage);
        }
        
        // For critical events, also log at severe level
        if (event.isCritical()) {
            LOGGER.severe("CRITICAL SECURITY EVENT: " + logMessage);
        }
    }
    
    /**
     * Security event types
     */
    public enum SecurityEvent {
        AUDIT_ENABLED(false),
        CONFIG_CHANGE(false),
        OPERATION_FAILED(true),
        PERFORMANCE_ANOMALY(false),
        SECURITY_LIMIT_VIOLATED(true),
        SUSPICIOUS_PATTERN(true),
        CLASS_LOADING_BLOCKED(true),
        REFLECTION_BLOCKED(true),
        COUNTERS_RESET(false);
        
        private final boolean critical;
        
        SecurityEvent(boolean critical) {
            this.critical = critical;
        }
        
        public boolean isCritical() {
            return critical;
        }
    }
    
    /**
     * Security audit summary data
     */
    public static class SecurityAuditSummary {
        public final long totalOperations;
        public final long securityViolations;
        public final long performanceAnomalies;
        public final long classLoadingFailures;
        public final long memoryLimitViolations;
        public final long stackDepthViolations;
        public final ConcurrentHashMap<String, AtomicLong> suspiciousPatterns;
        public final ConcurrentHashMap<String, AtomicLong> blockedClasses;
        
        SecurityAuditSummary(long totalOperations, long securityViolations, long performanceAnomalies,
                           long classLoadingFailures, long memoryLimitViolations, long stackDepthViolations,
                           ConcurrentHashMap<String, AtomicLong> suspiciousPatterns,
                           ConcurrentHashMap<String, AtomicLong> blockedClasses) {
            this.totalOperations = totalOperations;
            this.securityViolations = securityViolations;
            this.performanceAnomalies = performanceAnomalies;
            this.classLoadingFailures = classLoadingFailures;
            this.memoryLimitViolations = memoryLimitViolations;
            this.stackDepthViolations = stackDepthViolations;
            this.suspiciousPatterns = suspiciousPatterns;
            this.blockedClasses = blockedClasses;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("SecurityAuditSummary{\n");
            sb.append("  totalOperations=").append(totalOperations).append("\n");
            sb.append("  securityViolations=").append(securityViolations).append("\n");
            sb.append("  performanceAnomalies=").append(performanceAnomalies).append("\n");
            sb.append("  classLoadingFailures=").append(classLoadingFailures).append("\n");
            sb.append("  memoryLimitViolations=").append(memoryLimitViolations).append("\n");
            sb.append("  stackDepthViolations=").append(stackDepthViolations).append("\n");
            sb.append("  suspiciousPatterns=").append(suspiciousPatterns.size()).append(" types\n");
            sb.append("  blockedClasses=").append(blockedClasses.size()).append(" types\n");
            sb.append("}");
            return sb.toString();
        }
    }
}