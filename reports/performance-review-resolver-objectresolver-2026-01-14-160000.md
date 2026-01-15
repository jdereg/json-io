# Performance Review Plan
Generated: 2026-01-14T16:00:00
Scope: Resolver.java and ObjectResolver.java (json-io deserialization core)

## Executive Summary

The `Resolver.java` and `ObjectResolver.java` files form the core deserialization engine of json-io, responsible for converting parsed JSON (Map-of-Maps representation) into Java object graphs. Overall, the code demonstrates **strong performance optimization practices** with several existing optimizations already in place:

**Existing Optimizations Found:**
- ClassValueMap caching for DEFAULT_INSTANTIATORS (line 862, Resolver.java)
- Hoisted ReadOptions constants to avoid repeated method calls (lines 92-93, Resolver.java)
- Pre-sized collections via ensureCollectionCapacity helper
- Fast path checks using isDirectlyAddableJsonValue() for native JSON types
- Type classification caching via JsonObject.getJsonType()
- Optimized array element setting with isPrimitive check hoisted out of loops

**Areas Requiring Attention:**
- Some residual opportunities for micro-optimizations
- Collection iteration patterns could be improved in hot paths
- HashSet creation in reference resolution could be optimized

## Performance Bottlenecks

### Critical Issues (Severe Impact)
- [ ] **None identified** - The codebase shows mature performance optimization

### High Priority Issues

- [ ] **HashSet allocation in reference chain resolution** (Resolver.java:1459)
  - Location: `DefaultReferenceTracker.get(Long id)`
  - Issue: Creates new HashSet for every reference lookup for circular detection
  - Impact: Memory churn in hot path during reference resolution
  - Recommendation: Consider ThreadLocal or pooled Set, or use visited flag on JsonObject

- [ ] **Repeated Type resolution in markUntypedObjects** (ObjectResolver.java:507-540)
  - Location: `markUntypedObjects()` traversal loop
  - Issue: Creates new AbstractMap.SimpleEntry objects for every stack item
  - Impact: Object allocation pressure in deep object graphs
  - Recommendation: Use a simple two-slot holder class or parallel stacks

### Medium Priority Issues

- [ ] **String.split() avoided but similar pattern exists** (Resolver.java:902-908)
  - Location: CompactMap instantiator slash counting
  - Issue: Already optimized with char-by-char counting - good
  - Status: **Already optimized** - manual loop with early exit

- [ ] **Iterator creation in entrySet loops** (ObjectResolver.java:93, 667)
  - Location: `traverseFields()` and `handleObjectFieldsMarking()`
  - Issue: Enhanced for-loop creates iterator; for EntrySet this is generally efficient
  - Status: Comment indicates this was considered - acceptable

- [ ] **Converter.isConversionSupportedFor called before convert** (ObjectResolver.java:937, 1005)
  - Location: `extractArrayElementValue()` and `convertToComponentType()`
  - Issue: Two-phase check (isSupported + convert) may do redundant work
  - Impact: Minor - converter likely caches internally
  - Recommendation: Consider try-convert pattern with exception handling

- [ ] **ReadOptions.isNonReferenceableClass called multiple times** (ObjectResolver.java:175, 218, 984)
  - Location: Various places checking non-referenceable status
  - Issue: Method is called repeatedly for same class
  - Impact: Low - ReadOptions likely caches internally
  - Status: Acceptable if ReadOptions caches; verify caching exists

### Low Priority Issues

- [ ] **JsonObject.clear() at end of traverseArray** (ObjectResolver.java:414)
  - Issue: Clears the JsonObject map after processing
  - Impact: Minimal - happens once per array
  - Recommendation: Verify this is necessary for GC or if items can just be nulled

- [ ] **Arrays.asList() in createAndPopulateArray** (ObjectResolver.java:818)
  - Location: Recursive call for nested arrays
  - Issue: Creates intermediate List wrapper
  - Impact: Only for multi-dimensional arrays
  - Recommendation: Could pass Object[] directly and change signature

- [ ] **Multiple instanceof checks in hot paths** (ObjectResolver.java:290-333)
  - Location: `traverseCollection()` element processing
  - Issue: Chain of instanceof checks for each element
  - Impact: JVM optimizes instanceof well; modern JVMs use type profiling
  - Status: Acceptable - pattern is common and JVM handles it well

## Performance Metrics

### Current State (Estimated based on code analysis)
- **Resolver operations**: Efficient with ClassValueMap caching
- **Object instantiation**: Optimized via DEFAULT_INSTANTIATORS map
- **Reference resolution**: O(1) with HashMap, but HashSet allocation per call
- **Type inference**: Stack-based iteration avoids recursion overhead
- **Collection capacity**: Pre-sized when possible

### Target State
- Eliminate per-call HashSet allocations in reference resolution
- Reduce object allocation in markUntypedObjects traversal
- Maintain current sub-millisecond performance for typical objects

## Optimization Plan

### Quick Wins (1-2 hours)

1. **Optimize DefaultReferenceTracker.get() HashSet creation** (Resolver.java:1459)
   - Replace `new HashSet<>()` with ThreadLocal or reusable Set
   - Alternatively, track visited state on JsonObject itself
   - Expected impact: Reduced GC pressure during deserialization

2. **Review AbstractMap.SimpleEntry usage** (ObjectResolver.java:516-520)
   - Consider lightweight holder class or parallel ArrayDeques
   - Expected impact: Reduced allocation in deep object graphs

### Major Optimizations (1-2 days)

1. **Consolidate type checking patterns**
   - Create helper that combines isNonReferenceableClass + push logic
   - Reduce repeated patterns across traverseCollection, traverseArray, etc.

2. **Profile and optimize hot path branches**
   - Use JMH to identify actual hot spots
   - Reorder conditionals based on frequency data

### Architectural Improvements (Future consideration)

1. **Consider object pooling for JsonObject**
   - During parsing, JsonObject instances are created and discarded
   - Pool could reduce allocation pressure for large documents

2. **Investigate bulk array operations**
   - System.arraycopy for primitive arrays where applicable
   - Batch processing for homogeneous collections

## Implementation Strategy

### Phase 1: Measurement
- Add JMH benchmarks for key deserialization paths
- Establish baseline metrics for:
  - Simple object deserialization
  - Deep nested structures
  - Large collections
  - Reference-heavy graphs

### Phase 2: Low-Risk Optimizations
- HashSet allocation optimization in DefaultReferenceTracker
- Review and potentially simplify markUntypedObjects allocations
- These changes are isolated and low-risk

### Phase 3: Validation
- Run full test suite (2000+ tests)
- Compare JMH benchmarks against baseline
- Verify no regression in correctness

## Performance Testing Plan

1. **Benchmark current performance**
   ```bash
   mvn test -Dtest=zzLastTest  # After running full suite
   ```
   Monitor "Read JSON" metric in output

2. **Create targeted microbenchmarks**
   - Reference resolution with many @ref/@id
   - Deep nested object graphs
   - Large homogeneous collections

3. **Regression testing**
   - Compare against baseline after each optimization
   - Document performance improvements

## Risk Assessment

### Safe Optimizations
- Replacing HashSet with ThreadLocal or pooled Set
- Simplifying holder objects for stack traversal
- Adding pre-sizing hints for collections

### Moderate Risk
- Changing iteration patterns (verify correctness)
- Modifying type inference logic

### High Risk
- Object pooling (complex lifecycle management)
- Changing reference resolution algorithm

## Recommendations

1. **Prioritize HashSet optimization in DefaultReferenceTracker**
   - Highest impact-to-effort ratio
   - Isolated change with clear testing path

2. **Profile before optimizing further**
   - The code already shows mature optimization
   - Use JMH to find actual bottlenecks, not theoretical ones

3. **Maintain existing optimizations**
   - ClassValueMap usage is excellent
   - Hoisted constants pattern should be preserved
   - isDirectlyAddableJsonValue fast path is valuable

4. **Consider lazy initialization patterns**
   - Some collections (unresolvedRefs, mapsToRehash) are created upfront
   - Could be lazily initialized if often empty

## Estimated Impact

- **Quick wins**: 5-10% reduction in allocation pressure
- **Major optimizations**: 10-20% improvement in throughput for reference-heavy graphs
- **Full implementation**: Up to 25% improvement for worst-case scenarios

## Effort Estimate

- Quick wins: 2-4 hours
- Major optimizations: 1-2 days
- Complete implementation with testing: 3-5 days

## Code Quality Notes

The Resolver and ObjectResolver code demonstrates **excellent software engineering practices**:

1. **Clear separation of concerns** - Base class handles common logic, subclass handles Java object specifics
2. **Well-documented** - Extensive Javadoc explains complex resolution logic
3. **Defensive programming** - Null checks, security limits, proper exception handling
4. **Performance awareness** - Comments explicitly note performance considerations (lines 91-93, 901-908)
5. **Extensibility** - Hook methods (adjustTypeBeforeResolve, reconcileResult) allow customization

The code is production-ready with only minor optimization opportunities remaining.
