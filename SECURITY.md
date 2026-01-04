# Security Guide for json-io

This document provides comprehensive guidance on using json-io securely in production environments, including security features, configuration options, and best practices.

## Table of Contents

- [Security Features](#security-features)
- [Security Configuration](#security-configuration)
- [Protection Against Common Attacks](#protection-against-common-attacks)
- [Security Best Practices](#security-best-practices)
- [Security Testing](#security-testing)
- [Incident Response](#incident-response)
- [Security Updates](#security-updates)

## Security Features

json-io includes comprehensive security protections implemented across all major components:

### Memory Exhaustion Protection

**DoS Attack Prevention via Bounded Processing:**
- Collection size limits (1M objects maximum)
- Stack depth limits (10K maximum depth)
- String length limits (64KB for individual strings)
- Resource file size limits (1MB maximum)

```java
// These limits are automatically enforced - no configuration needed
ReadOptions options = new ReadOptionsBuilder().build();
```

### Reflection Security Hardening

**Safe Reflection Operations:**
- SecurityManager validation for all reflection operations
- Safe setAccessible() usage with graceful fallbacks
- VarHandle/MethodHandle security with permission checks
- Protection against injection into system classes

```java
// Reflection security is automatically enabled
// For restricted environments, graceful fallbacks are used
JsonIo.toJava(jsonString, readOptions);
```

### Input Validation & Bounds Checking

**Comprehensive Input Validation:**
- Null safety checks throughout the codebase
- Array bounds validation in all array operations
- Reference ID validation against malicious attacks
- Directory traversal prevention in resource loading

### Resource Management

**Memory Leak Prevention:**
- Proper stream closure with try-with-resources
- Cache eviction strategies for bounded memory usage
- Enhanced error handling with resource cleanup

## Security Configuration

### Default Security Settings

json-io comes with secure defaults enabled out of the box:

```java
// Default configuration includes all security protections
ReadOptions secureOptions = new ReadOptionsBuilder().build();
WriteOptions secureWriteOptions = new WriteOptionsBuilder().build();
```

### Custom Security Limits

For high-security environments, you can implement additional restrictions:

```java
// Example: Custom validation for additional security
ReadOptions restrictedOptions = new ReadOptionsBuilder()
    .failOnUnknownType(true)  // Reject unknown types
    .allowNanAndInfinity(false)  // Reject NaN/Infinity
    .build();
```

### Production Environment Configuration

**Recommended settings for production:**

```java
ReadOptions productionOptions = new ReadOptionsBuilder()
    .failOnUnknownType(true)          // Security: Reject unknown types
    .allowNanAndInfinity(false)       // Security: Reject problematic numbers
    .build();

WriteOptions productionWriteOptions = new WriteOptionsBuilder()
    .writeEnumsAsJsonObjects(true)    // Consistency: Structured enum handling
    .alwaysShowType(false)            // Performance: Minimize type info
    .build();
```

## Protection Against Common Attacks

### Stack Overflow Attacks

**Protection:** Automatic depth limiting prevents stack overflow from deeply nested JSON.

```java
// Malicious deeply nested JSON is automatically rejected:
// {"nested":{"nested":{"nested":{...}}}} // 15000+ levels deep
// Throws JsonIoException with "stack depth" message
```

### Memory Exhaustion Attacks

**Protection:** Collection size limits prevent memory exhaustion.

```java
// Large collections are automatically limited:
// [item1, item2, ...] // 1M+ items
// Throws JsonIoException with "Security limit exceeded" message
```

### Injection Attacks

**Protection:** Class loading restrictions prevent malicious class instantiation.

```java
// Dangerous classes are automatically blocked:
// {"@type":"java.lang.Runtime", "value":"test"}
// {"@type":"java.lang.ProcessBuilder", "value":"test"}
// Returns null or throws security exception
```

### Reference Explosion Attacks

**Protection:** Reference tracking limits prevent exponential object creation.

```java
// Reference explosion patterns are detected and limited:
// Multiple objects referencing each other exponentially
// Throws JsonIoException when limits exceeded
```

### Unicode and Special Character Attacks

**Protection:** Safe string processing handles malicious unicode.

```java
// Malicious unicode patterns are safely processed:
// Control characters, normalization attacks, homographs
// Processed safely without security vulnerabilities
```

## Security Best Practices

### 1. Input Validation

Always validate JSON input from untrusted sources:

```java
public Object parseUntrustedJson(String jsonInput) {
    try {
        // Validate input size
        if (jsonInput.length() > MAX_JSON_SIZE) {
            throw new SecurityException("JSON input too large");
        }
        
        // Use secure options
        ReadOptions secureOptions = new ReadOptionsBuilder()
            .failOnUnknownType(true)
            .build();
            
        return JsonIo.toJava(jsonInput, secureOptions);
        
    } catch (JsonIoException e) {
        // Log security events
        logger.warn("JSON parsing failed: {}", e.getMessage());
        throw new SecurityException("Invalid JSON input", e);
    }
}
```

### 2. Error Handling

Implement secure error handling:

```java
public Object secureJsonParsing(String json) {
    try {
        return JsonIo.toJava(json, readOptions);
    } catch (JsonIoException e) {
        // Don't expose internal details in error messages
        logger.error("JSON parsing error", e);
        throw new SecurityException("JSON parsing failed");
    }
}
```

### 3. Resource Limits

Set appropriate resource limits for your environment:

```java
// Monitor memory usage
Runtime runtime = Runtime.getRuntime();
long maxMemory = runtime.maxMemory();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();

if (usedMemory > maxMemory * 0.8) {
    // Implement back-pressure or reject new requests
    throw new SecurityException("Memory usage too high");
}
```

### 4. Logging and Monitoring

Implement comprehensive security logging:

```java
public Object monitoredJsonParsing(String json) {
    long startTime = System.nanoTime();
    String source = getCurrentRequestSource();
    
    try {
        Object result = JsonIo.toJava(json, readOptions);
        
        // Log successful parsing with metrics
        long duration = System.nanoTime() - startTime;
        securityLogger.info("JSON parsed successfully - source: {}, duration: {}ms", 
                           source, duration / 1_000_000);
        
        return result;
        
    } catch (JsonIoException e) {
        // Log security events with context
        securityLogger.warn("JSON parsing failed - source: {}, error: {}, input_size: {}", 
                           source, e.getMessage(), json.length());
        throw e;
    }
}
```

### 5. Class Filtering

Implement additional class filtering for high-security environments:

```java
ReadOptions restrictiveOptions = new ReadOptionsBuilder()
    .failOnUnknownType(true)
    .addClassFactory(YourClass.class, new SecureClassFactory())
    .build();

// Custom class factory with security validation
public class SecureClassFactory implements ClassFactory {
    private static final Set<String> ALLOWED_PACKAGES = Set.of(
        "com.yourcompany.dto",
        "com.yourcompany.model"
    );
    
    @Override
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        String packageName = c.getPackage().getName();
        if (!ALLOWED_PACKAGES.contains(packageName)) {
            throw new SecurityException("Class not allowed: " + c.getName());
        }
        return ClassUtilities.newInstance(c);
    }
}
```

## Security Testing

### Automated Security Testing

Use the provided security fuzz tests:

```java
// Run comprehensive security tests
mvn test -Dtest=SecurityFuzzTest
```

### Manual Security Testing

Test common attack vectors:

```java
@Test
public void testSecurityLimits() {
    // Test deep nesting
    testDeeplyNestedInput();
    
    // Test large collections
    testLargeCollectionInput();
    
    // Test malicious references
    testMaliciousReferenceInput();
    
    // Test class injection
    testClassInjectionInput();
}
```

### Performance Security Testing

Validate that security controls don't impact performance:

```java
// Run performance benchmarks
mvn test -Dtest=PerformanceBenchmarkTest
```

## Incident Response

### Security Event Detection

Monitor for these security events:

1. **Excessive Memory Usage**: Monitor heap usage during JSON processing
2. **Processing Time Anomalies**: Detect unusually long processing times
3. **Security Exceptions**: Track JsonIoException with security messages
4. **Class Loading Failures**: Monitor failed class instantiation attempts

### Incident Response Steps

1. **Immediate Response**:
   - Isolate the affected system
   - Stop processing the malicious input
   - Preserve logs for analysis

2. **Analysis**:
   - Analyze the malicious JSON input
   - Identify the attack vector used
   - Assess potential data exposure

3. **Remediation**:
   - Apply additional input validation
   - Update security configurations
   - Implement additional monitoring

4. **Recovery**:
   - Restart affected services
   - Verify system integrity
   - Resume normal operations

## Security Updates

### Staying Current

1. **Monitor Releases**: Watch the json-io GitHub repository for security updates
2. **Update Dependencies**: Keep json-io and java-util updated to latest versions
3. **Security Advisories**: Subscribe to security notifications

### Version Information

Current security-hardened version: **4.57.0** (unreleased)

**Security improvements included:**
- Memory exhaustion protection
- Reflection security hardening  
- Input validation & bounds checking
- Resource management enhancements
- Thread safety improvements

### Reporting Security Issues

To report security vulnerabilities:

1. **Do not** create public GitHub issues for security problems
2. Email security reports to: [security contact - update as needed]
3. Include detailed reproduction steps
4. Allow reasonable time for response and patching

## Compliance

json-io's security features help meet various compliance requirements:

- **OWASP Top 10**: Protection against injection and security misconfiguration
- **NIST Cybersecurity Framework**: Input validation and secure development practices
- **ISO 27001**: Information security management controls
- **PCI DSS**: Secure coding practices for payment card environments

## Security Checklist

Use this checklist to ensure secure json-io deployment:

- [ ] Using latest security-hardened version
- [ ] Configured with secure ReadOptions/WriteOptions
- [ ] Implemented proper input validation
- [ ] Added comprehensive error handling
- [ ] Set up security logging and monitoring
- [ ] Tested with SecurityFuzzTest suite
- [ ] Validated performance with benchmarks
- [ ] Implemented incident response procedures
- [ ] Established security update process

---

**Remember**: Security is a process, not a product. Regularly review and update your security measures as threats evolve.