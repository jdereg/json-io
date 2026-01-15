# Performance Review: JsonParser.java

**File:** `/Users/jderegnaucourt/workspace/json-io/src/main/java/com/cedarsoftware/io/JsonParser.java`
**Review Date:** 2026-01-10
**Reviewer:** Claude Opus 4.5 (Performance Optimization Expert)
**Lines Analyzed:** 1303

---

## Executive Summary

The `JsonParser` class is a well-optimized JSON parser that demonstrates thoughtful performance engineering. The codebase shows evidence of prior optimization work including:
- Static lookup tables for escape characters and hex values
- Two-tier caching (static + instance) for strings and numbers
- Hoisted ReadOptions constants to avoid method call overhead
- Pre-allocated StringBuilder buffers
- Optimized hot paths for common cases

**Overall Assessment:** The parser is already highly optimized for common use cases. The remaining optimization opportunities are primarily in edge cases and architectural patterns. The estimated combined impact of all recommendations is **5-15% throughput improvement** for typical JSON payloads.

### Performance Profile Summary
| Category | Status | Impact Potential |
|----------|--------|------------------|
| Algorithmic Complexity | Good | Low |
| Memory Allocation | Good | Medium |
| String Handling | Excellent | Low |
| Data Structures | Good | Low |
| Caching | Excellent | Low |
| I/O Efficiency | Good | Low |
| Hot Path Optimization | Very Good | Medium |
| Concurrency | Good | N/A (single-threaded by design) |

---

## Critical Priority Issues

### CRIT-1: Unbounded Instance Cache Growth (Lines 268-269)
**Severity:** Critical (Memory)
**Location:** Constructor, lines 268-269

**Issue:**
```java
this.stringCache = new ParserStringCache(STATIC_STRING_CACHE);
this.numberCache = new ParserNumberCache(STATIC_NUMBER_CACHE);
```

The instance caches (`ParserStringCache.instanceCache` and `ParserNumberCache.instanceCache`) are unbounded `HashMap` instances. When parsing JSON with many unique strings/numbers, these can grow without limit within a single parse operation.

**Impact:**
- Memory exhaustion for large JSON payloads with high string/number cardinality
- For a 100MB JSON with 1M unique strings, cache could consume 50-100MB additional memory
- GC pressure from large transient HashMaps

**Recommendation:**
Consider implementing bounded instance caches (LRU with configurable size) or avoiding instance-level caching for unique values. Given the parser is short-lived (single parse operation), the instance cache ROI is questionable for non-repeated values.

```java
// Option 1: Bounded cache using LRU
private static final int MAX_INSTANCE_CACHE_SIZE = 1000;

// Option 2: Skip caching for strings longer than threshold
if (length < 33 && instanceCache.size() < MAX_INSTANCE_CACHE_SIZE) {
    stringCache.put(s, s);
}
```

**Estimated Impact:** Prevents OOM for pathological inputs; negligible performance change for typical inputs.

---

## High Priority Issues

### HIGH-1: ArrayList Pre-sizing May Over-allocate (Line 481)
**Severity:** High (Memory)
**Location:** `readArray()`, line 481

**Issue:**
```java
final List<Object> list = new ArrayList<>(64);
```

Pre-sizing to 64 elements for ALL arrays, including empty arrays and single-element arrays, wastes ~256 bytes per small array.

**Impact:**
- For JSON with many small arrays (common in nested structures), this multiplies memory waste
- Example: 10,000 single-element arrays wastes ~2.5MB

**Recommendation:**
```java
// Use a smaller default, let ArrayList grow if needed
final List<Object> list = new ArrayList<>(8);
```

Or better, use the default capacity (10 in Java 8+) which is optimized for typical use:
```java
final List<Object> list = new ArrayList<>();
```

**Estimated Impact:** 5-10% memory reduction for nested JSON structures with many small arrays.

---

### HIGH-2: StringBuilder.toString() Allocation in Hot Path (Lines 589, 749, 796)
**Severity:** High (Allocation)
**Location:** Multiple methods

**Issue:**
The parser calls `StringBuilder.toString()` in hot paths, which allocates a new String object even when the result might be cached or discarded:

```java
// Line 589 - readUnquotedIdentifier
return strBuf.toString();

// Line 749 - readFloatingPoint
val = readFloatingPoint(number.toString());

// Line 796 - readInteger overflow case
String numStr = number.toString();
```

**Impact:**
- Each `toString()` creates a new char[] and String object
- In tight parsing loops, this adds GC pressure

**Recommendation:**
For the string cache path (Line 1039), the pattern is already optimized. However, for the integer path (Line 751), consider direct parsing for the common case:

```java
// Instead of creating String for cache lookup:
if (isFloat) {
    val = readFloatingPoint(number.toString());  // Unavoidable
} else {
    val = readInteger(number);  // Already optimized - no toString() for common case
}
```

The current implementation at line 751-752 is already well-optimized. No change needed.

**Estimated Impact:** Already optimized in critical path.

---

### HIGH-3: Duplicate entrySet() Allocations in Cache Wrappers (Lines 214-220, 252-258)
**Severity:** High (Allocation)
**Location:** `ParserStringCache.entrySet()` and `ParserNumberCache.entrySet()`

**Issue:**
```java
@Override
public Set<Entry<String, String>> entrySet() {
    Set<Entry<String, String>> entries = new HashSet<>();
    entries.addAll(staticCache.entrySet());
    entries.addAll(instanceCache.entrySet());
    return entries;
}
```

Every call to `entrySet()` allocates a new HashSet and copies all entries from both caches. While `entrySet()` may not be called during parsing, this is still a design concern.

**Impact:**
- O(n) memory and time for each entrySet() call
- Potential issue if debugging or profiling tools iterate the cache

**Recommendation:**
If `entrySet()` is not used during parsing, throw UnsupportedOperationException. If needed, implement lazy view:

```java
@Override
public Set<Entry<String, String>> entrySet() {
    throw new UnsupportedOperationException("entrySet() not supported for parser caches");
}
```

**Estimated Impact:** Negligible for parsing; prevents accidental O(n) operations.

---

### HIGH-4: Potential Integer Overflow in Unicode Surrogate Handling (Lines 978)
**Severity:** High (Correctness/Performance)
**Location:** `readString()`, line 978

**Issue:**
```java
int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
```

While the arithmetic is correct, there's no explicit overflow check. For malformed input, this could produce unexpected values.

**Impact:**
- Potential incorrect character decoding for malformed surrogate pairs
- No security risk (appendCodePoint validates), but adds defensive overhead

**Recommendation:**
The current implementation is correct and relies on the preceding validation. No change needed, but consider adding a comment:

```java
// This arithmetic is safe: max value is 0x10000 + (0x3FF << 10) + 0x3FF = 0x10FFFF
int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
```

**Estimated Impact:** None (documentation only).

---

## Medium Priority Issues

### MED-1: Repeated Type Resolution Calls (Lines 307-308, 372-373)
**Severity:** Medium (CPU)
**Location:** `readValue()` and `readJsonObject()`

**Issue:**
```java
// Line 307-308
Type elementType = TypeUtilities.extractArrayComponentType(suggestedType);
return readArray(elementType);

// Line 372-373
Type resolvedSuggestedType = TypeUtilities.resolveType(suggestedType, suggestedType);
jObj.setType(resolvedSuggestedType);
```

Type resolution methods may be called repeatedly for the same types during deep parsing.

**Impact:**
- For deeply nested JSON with uniform types, same resolution computed multiple times
- TypeUtilities methods have internal caching (verify), but call overhead remains

**Recommendation:**
Verify that `TypeUtilities.extractArrayComponentType()` and `TypeUtilities.resolveType()` have internal caching. If not, consider memoizing at the parser level for uniform structures.

**Estimated Impact:** 1-3% for deeply nested uniform structures.

---

### MED-2: Switch Statement Not Optimized for Common Cases (Lines 312-358)
**Severity:** Medium (CPU)
**Location:** `readValue()`, lines 312-358

**Issue:**
The switch statement handles values in the order: `"`, `'`, `]`, `f/F`, `n`, `N`, `t/T`, `-`, `I`, `.`, `+`, default (digits).

**Current hot path:**
- Objects `{` and arrays `[` are handled first (good)
- Strings `"` is first in switch (good)
- Numbers (0-9) fall to default case

**Analysis:**
For typical JSON payloads, the distribution is roughly:
1. Strings (40-60%)
2. Numbers (20-30%)
3. Objects/Arrays (20-30%)
4. Booleans/null (5-10%)

The current ordering is already optimized with `{` and `[` handled before the switch, and `"` first in switch.

**Recommendation:**
No change needed. The current ordering is appropriate.

**Estimated Impact:** Already optimized.

---

### MED-3: Injector Map Lookup Could Be Avoided (Lines 387-394)
**Severity:** Medium (CPU)
**Location:** `readJsonObject()`, lines 387-394

**Issue:**
```java
Class<?> rawClass = TypeUtilities.getRawClass(suggestedType);
Map<String, Injector> injectors;
if (suggestedType == null || rawClass == Object.class || rawClass == null) {
    injectors = java.util.Collections.emptyMap();
} else {
    injectors = readOptions.getDeepInjectorMap(rawClass);
}
```

For every JSON object, this performs a type check and potentially a map lookup.

**Impact:**
- `getDeepInjectorMap()` may involve reflection and caching
- For simple JSON (no type hints), this work is unnecessary

**Recommendation:**
Already well-optimized with the early return for Object.class. The `getDeepInjectorMap()` uses ClassValueMap internally which has O(1) cached lookup. No change needed.

**Estimated Impact:** Already optimized.

---

### MED-4: numBuf StringBuilder Could Be Thread-Local for Reuse (Line 82)
**Severity:** Medium (Memory/Design)
**Location:** Line 82

**Issue:**
```java
private final StringBuilder numBuf = new StringBuilder();
```

Each parser instance creates a new StringBuilder for numbers. Since parsers are short-lived and often created in rapid succession, this creates allocation churn.

**Impact:**
- Minor: StringBuilder is small and short-lived
- Parsers are single-threaded, so thread-local wouldn't add complexity

**Recommendation:**
Keep as-is. The StringBuilder is reused within a parse operation via `setLength(0)`, and the instance overhead is minimal. Thread-local would add complexity without significant benefit.

**Estimated Impact:** Negligible.

---

### MED-5: Static Constant Maps Could Use Primitive Collections (Lines 103-105)
**Severity:** Medium (Memory)
**Location:** Lines 103-105

**Issue:**
```java
private static final Map<String, String> STATIC_STRING_CACHE = new ConcurrentHashMap<>(64);
private static final Map<Number, Number> STATIC_NUMBER_CACHE = new ConcurrentHashMap<>(16);
```

`ConcurrentHashMap` has higher memory overhead than specialized collections.

**Impact:**
- Minor: These are static and loaded once
- ConcurrentHashMap adds ~40 bytes overhead per entry

**Recommendation:**
Keep as-is. The concurrency safety is worth the minor overhead, and these are read-only after static initialization. The maps are small (fewer than 100 entries combined).

**Estimated Impact:** Negligible.

---

### MED-6: getOrDefault Pattern (Line 399)
**Severity:** Medium (CPU)
**Location:** `readJsonObject()`, line 399

**Issue:**
```java
field = substitutes.getOrDefault(field, field);
```

**Analysis:**
This is already optimized. `getOrDefault()` avoids the double-lookup pattern of `get()` followed by null check and default.

**Recommendation:**
No change needed. This is a good pattern.

**Estimated Impact:** Already optimized.

---

## Low Priority Issues

### LOW-1: Character.toLowerCase() in Token Reading (Line 626)
**Severity:** Low (CPU)
**Location:** `readToken()`, line 626

**Issue:**
```java
c = Character.toLowerCase((char) c);
```

The fallback path for longer tokens uses `Character.toLowerCase()` which is slower than the inlined ASCII conversion used for short tokens.

**Impact:**
- Only affects tokens longer than 5 characters (rare: "infinity")
- Called infrequently

**Recommendation:**
Could use the same inline conversion for consistency:
```java
if (c >= 'A' && c <= 'Z') {
    c += 32;
}
```

**Estimated Impact:** Negligible (rare path).

---

### LOW-2: Exception Object Creation in Error Paths (Lines 1292-1301)
**Severity:** Low (Allocation)
**Location:** `error()` methods

**Issue:**
```java
private Object error(String msg) {
    throw new JsonIoException(getMessage(msg));
}
```

Exception creation includes stack trace capture which is expensive.

**Impact:**
- Only relevant for error paths
- Not a hot path concern

**Recommendation:**
No change needed. Error paths should prioritize clarity over performance.

**Estimated Impact:** None for normal parsing.

---

### LOW-3: Conditional Strictness Checks (Multiple Locations)
**Severity:** Low (CPU)
**Location:** Lines 317-319, 343-345, 349-352, etc.

**Issue:**
```java
if (strictJson) {
    error("Single-quoted strings not allowed in strict JSON mode");
}
```

The `strictJson` boolean is checked at multiple points during parsing.

**Impact:**
- Boolean checks are extremely fast (~1 CPU cycle)
- The field is hoisted from ReadOptions (already optimized)

**Recommendation:**
No change needed. This is already optimal.

**Estimated Impact:** Negligible.

---

### LOW-4: EMPTY_ARRAY Sentinel Usage (Lines 79, 487)
**Severity:** Low (Design)
**Location:** Lines 79, 487

**Issue:**
```java
private static final JsonObject EMPTY_ARRAY = new JsonObject();
// ...
if (value != EMPTY_ARRAY) {
```

Using object identity (`!=`) for sentinel comparison is correct and efficient.

**Recommendation:**
No change needed. This is a good pattern.

**Estimated Impact:** Already optimized.

---

## Caching Analysis

### Existing Caches (Excellent Implementation)

| Cache | Type | Scope | Bounded | Thread-Safe |
|-------|------|-------|---------|-------------|
| ESCAPE_CHAR_MAP | char[128] | Static | Yes (fixed) | Yes (immutable) |
| HEX_VALUE_MAP | int[128] | Static | Yes (fixed) | Yes (immutable) |
| STATIC_STRING_CACHE | ConcurrentHashMap | Static | Unbounded | Yes |
| STATIC_NUMBER_CACHE | ConcurrentHashMap | Static | Unbounded | Yes |
| SUBSTITUTES | HashMap | Static | Yes (fixed) | Yes (immutable) |
| stringCache | ParserStringCache | Instance | Unbounded | No (not needed) |
| numberCache | ParserNumberCache | Instance | Unbounded | No (not needed) |

### Cache Effectiveness

The two-tier caching strategy (static + instance) is well-designed:
1. Static caches handle common values efficiently across all parser instances
2. Instance caches capture repeated values within a single parse operation
3. The 33-character threshold for string caching balances memory vs. lookup cost

### Recommendations

1. **Bound instance caches** (CRIT-1) to prevent memory issues with high-cardinality inputs
2. **Consider eviction strategy** for static caches in long-running applications (already addressed in STATIC_STRING_CACHE via put-time clearing, but this could be improved)

---

## I/O Efficiency Analysis

### Current Implementation (Good)

The parser uses `FastReader` which provides:
- Non-synchronized buffered reading (faster than BufferedReader)
- Efficient pushback buffer (16 chars default)
- 8KB read buffer (appropriate for most use cases)

### Observations

1. **Read Buffer Size (8KB):** Appropriate for typical JSON payloads. Larger buffers show diminishing returns and waste memory for small inputs.

2. **Pushback Buffer (16 chars):** Sufficient for all current use cases (longest pushback is ~6 chars for surrogate pair handling).

3. **Single-character reads:** The parser predominantly uses `input.read()` for single characters. While `read(char[], int, int)` would be more efficient for bulk reads, the parsing logic requires character-by-character processing.

### Recommendation

No changes needed. The I/O layer is well-optimized for the parsing use case.

---

## Hot Path Analysis

### Identified Hot Paths

1. **`readString()`** (Lines 879-1027)
   - Most frequently called for typical JSON
   - Well-optimized with lookup tables
   - StringBuilder reuse minimizes allocation

2. **`readValue()`** (Lines 294-359)
   - Entry point for all value parsing
   - Object/array check before switch is optimal
   - Depth check is O(1)

3. **`skipWhitespaceRead()`** (Lines 1060-1106)
   - Called before every value
   - Inline whitespace checks avoid array lookup
   - Comment handling (JSON5) adds minimal overhead when not present

4. **`readNumber()`** (Lines 646-764)
   - Direct StringBuilder parsing avoids String allocation for integers
   - Number caching reduces object creation

### Hot Path Optimization Status

| Path | Status | Notes |
|------|--------|-------|
| readString | Excellent | Lookup tables, buffer reuse |
| readValue | Very Good | Optimal branching |
| skipWhitespaceRead | Good | Could use lookup table for slight improvement |
| readNumber | Very Good | Direct parsing, caching |
| readJsonObject | Good | Type resolution could be optimized |

---

## Concurrency Considerations

### Current Design (Appropriate)

The `JsonParser` is designed for single-threaded use:
- One parser instance per parse operation
- No synchronization needed on instance fields
- Static caches use ConcurrentHashMap for thread-safety

### Thread-Safety Status

| Component | Thread-Safe | Notes |
|-----------|-------------|-------|
| Static lookup tables | Yes | Immutable arrays |
| Static caches | Yes | ConcurrentHashMap |
| Instance caches | No | Single-threaded use |
| Parser instance | No | Single-threaded use |

### Recommendation

No changes needed. The design correctly separates shared (static) and per-parse (instance) state.

---

## Memory Allocation Profile

### Per-Parse Allocations

| Object | Size (approx) | Count | Notes |
|--------|---------------|-------|-------|
| JsonParser | 200 bytes | 1 | Instance fields |
| ParserStringCache | 64 bytes + growth | 1 | HashMap wrapper |
| ParserNumberCache | 64 bytes + growth | 1 | HashMap wrapper |
| StringBuilder (strBuf) | 256+ bytes | 1 | Configurable |
| StringBuilder (numBuf) | 32+ bytes | 1 | Grows with large numbers |
| JsonObject | 128+ bytes | per object | Parallel arrays |
| ArrayList (arrays) | 64+ elements | per array | Pre-sized to 64 |

### Hot Path Allocations

During parsing, the primary allocations are:
1. **JsonObject instances** - unavoidable, represents parsed structure
2. **String instances** - from StringBuilder.toString(), cached when possible
3. **Number instances** - from parsing, cached when possible
4. **ArrayList for arrays** - pre-sized, may over-allocate for small arrays

### Recommendation

Focus on reducing ArrayList over-allocation (HIGH-1) and bounding instance cache growth (CRIT-1).

---

## Implementation Strategy

### Priority Matrix

| ID | Priority | Effort | Impact | Recommendation |
|----|----------|--------|--------|----------------|
| CRIT-1 | Critical | Medium | High | Bound instance caches |
| HIGH-1 | High | Low | Medium | Reduce ArrayList pre-size |
| HIGH-2 | High | N/A | N/A | Already optimized |
| HIGH-3 | High | Low | Low | Throw UnsupportedOperationException |
| HIGH-4 | High | Low | N/A | Add comment (documentation) |
| MED-1 | Medium | Medium | Low | Verify TypeUtilities caching |
| MED-2 | Medium | N/A | N/A | Already optimized |
| MED-3 | Medium | N/A | N/A | Already optimized |
| MED-4 | Medium | N/A | N/A | Keep as-is |
| MED-5 | Medium | N/A | N/A | Keep as-is |
| MED-6 | Medium | N/A | N/A | Already optimized |
| LOW-* | Low | Low | Negligible | Optional |

### Recommended Implementation Order

1. **Phase 1: Memory Safety** (1-2 hours)
   - CRIT-1: Bound instance caches
   - HIGH-1: Reduce ArrayList pre-size

2. **Phase 2: Cleanup** (30 minutes)
   - HIGH-3: Throw UnsupportedOperationException in entrySet()
   - HIGH-4: Add clarifying comment

3. **Phase 3: Verification** (1 hour)
   - MED-1: Verify TypeUtilities caching behavior
   - Run performance benchmarks to validate changes

---

## Benchmarking Recommendations

### Baseline Metrics to Capture

1. **Throughput:** JSON bytes parsed per second
2. **Latency:** p50, p95, p99 parse time for various payload sizes
3. **Memory:** Peak heap usage during parsing
4. **GC:** Minor/major GC frequency and duration

### Test Payloads

1. **Small JSON** (< 1KB): Simple object with few fields
2. **Medium JSON** (10-100KB): Nested structures, mixed types
3. **Large JSON** (1-10MB): Arrays of objects
4. **Pathological JSON**: High string/number cardinality
5. **Deep JSON**: 100+ nesting levels

### Recommended Tools

- JMH (Java Microbenchmark Harness) for micro-benchmarks
- async-profiler for CPU/allocation profiling
- Eclipse MAT for memory analysis

---

## Conclusion

The `JsonParser` class is a mature, well-optimized implementation. The identified issues are primarily edge cases (memory bounds) and minor optimizations that would provide marginal improvements for typical use cases.

**Key Findings:**
1. The parser demonstrates excellent hot path optimization
2. The two-tier caching strategy is effective
3. Memory allocation patterns are reasonable but could be tightened
4. I/O efficiency is appropriate for the use case
5. The code is single-threaded by design, avoiding synchronization overhead

**Recommended Actions:**
1. Implement bounded instance caches (CRIT-1) for production safety
2. Consider reducing ArrayList pre-sizing (HIGH-1) for memory efficiency
3. Run comprehensive benchmarks before and after any changes

---

*Report generated by Claude Opus 4.5 Performance Optimization Expert*
*No code changes made - analysis only*
