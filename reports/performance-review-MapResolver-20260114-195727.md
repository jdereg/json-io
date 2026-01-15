# Performance Review: MapResolver.java

**File:** `/Users/jderegnaucourt/workspace/json-io/src/main/java/com/cedarsoftware/io/MapResolver.java`
**Date:** 2026-01-14
**Reviewer:** Claude Opus 4.5 (Performance Analysis)

---

## Executive Summary

MapResolver is a critical component in json-io's JSON deserialization pipeline, responsible for converting raw JSON Maps to higher-quality Java Map representations. This analysis identifies several performance optimization opportunities across six key areas: method efficiency, object allocation, loop optimizations, caching opportunities, data structure choices, and hot path optimizations.

**Overall Assessment:** The codebase is well-optimized with evidence of prior performance work (fast primitive coercion methods, pre-computed type caches). However, several medium-priority opportunities remain for further optimization.

**Priority Ranking:**
- **High Priority:** 2 items (immediate performance impact)
- **Medium Priority:** 4 items (measurable improvement potential)
- **Low Priority:** 3 items (minor gains, consider if profiling confirms)

---

## 1. Method Efficiency and Algorithmic Complexity

### 1.1 `traverseFields()` Method (Lines 321-411) - **HIGH PRIORITY**

**Current State:**
```java
for (Map.Entry<Object, Object> e : jsonObj.entrySet()) {
    final String fieldName = (String) e.getKey();
    final Object rhs = e.getValue();
    // ... extensive processing per entry
}
```

**Analysis:**
- The method iterates over all entries in the JsonObject
- Each iteration performs multiple type checks, instanceof operations, and method calls
- The loop body is complex with nested conditionals (8+ branches)

**Complexity:** O(n * m) where n = number of fields, m = complexity of field processing

**Observations:**
1. **Redundant put() calls:** When `rhs == null`, the code does `jsonObj.put(fieldName, null)` (line 337), but the entry already exists with null value. This is a no-op that triggers hash invalidation in JsonObject.
2. **Multiple getReadOptions() calls:** Lines 327 and 330 both call `getReadOptions()` which is a simple getter, but could be hoisted.

**Recommendation:**
- Remove the redundant `jsonObj.put(fieldName, null)` call when rhs is already null
- Hoist `readOptions` retrieval outside the loop (already done at line 330, but injectorMap lookup at line 327 uses getReadOptions() instead of the local variable)

### 1.2 `verifyRootType()` Method (Lines 82-120) - **LOW PRIORITY**

**Current State:**
The method performs multiple type checks in sequence with potential early returns.

**Analysis:**
- Well-structured with early returns for common cases
- TypeUtilities.getRawClass() is called multiple times on the same or related types
- The method is called once per toJavaObjects() invocation, so not a hot path

**Recommendation:** No changes needed - the method is already optimized with early returns.

### 1.3 `getUltimateComponentType()` Method (Lines 125-139) - **LOW PRIORITY**

**Current State:**
```java
while (true) {
    if (type instanceof Class<?>) {
        Class<?> cls = (Class<?>) type;
        if (cls.isArray()) {
            type = cls.getComponentType();
            continue;
        }
    } else if (type instanceof GenericArrayType) {
        type = ((GenericArrayType) type).getGenericComponentType();
        continue;
    }
    return type;
}
```

**Analysis:**
- Clean iterative implementation avoiding recursion
- Minimal object allocation
- Only called from `verifyRootType()` which is not a hot path

**Recommendation:** No changes needed - efficient implementation.

---

## 2. Object Allocation Patterns

### 2.1 `traverseFields()` New JsonObject Creation (Line 347) - **MEDIUM PRIORITY**

**Current State:**
```java
if (rhsClass.isArray()) {
    JsonObject jsonArray = new JsonObject();
    jsonArray.setItems((Object[])rhs);
    push(jsonArray);
    jsonObj.put(fieldName, rhs);
}
```

**Analysis:**
- Creates a new JsonObject for every array field encountered
- JsonObject constructor allocates two Object arrays of size 16 (INITIAL_CAPACITY)
- If many fields are arrays, this creates significant allocation pressure

**Memory Cost per allocation:**
- JsonObject instance: ~48 bytes (header + fields)
- keys[16]: ~80 bytes (array header + 16 references)
- values[16]: ~80 bytes
- **Total: ~208 bytes per array field**

**Recommendation:**
Consider whether a lightweight wrapper or pooling mechanism could reduce allocations. However, the JsonObject is pushed to the stack for processing, so lifecycle management would need careful consideration.

### 2.2 `reconcileResult()` Method (Lines 241-268) - **LOW PRIORITY**

**Current State:**
```java
Class<?> basicType = getJsonSynonymType(javaClass);
return converter.convert(rootObj, basicType);
```

**Analysis:**
- Method creates minimal allocations
- `getJsonSynonymType()` returns existing Class objects (no allocation)
- Converter.convert() may allocate but that's unavoidable

**Recommendation:** No changes needed.

### 2.3 `fastPrimitiveCoercion()` Methods (Lines 668-707) - **POSITIVE OBSERVATION**

**Analysis:**
These static methods are well-optimized:
- No object allocations except for the autoboxed return values
- Direct casting avoids unnecessary wrapper creation
- Static methods avoid virtual dispatch overhead

**Recommendation:** Excellent implementation - no changes needed.

---

## 3. Loop Optimizations

### 3.1 `traverseArrayForRefs()` Method (Lines 213-228) - **MEDIUM PRIORITY**

**Current State:**
```java
private void traverseArrayForRefs(Object[] array) {
    final ReferenceTracker refTracker = references;
    for (int i = 0; i < array.length; i++) {
        Object element = array[i];
        if (element instanceof JsonObject) {
            JsonObject jObj = (JsonObject) element;
            if (jObj.isReference()) {
                long refId = jObj.getReferenceId();
                JsonObject refObject = refTracker.getOrThrow(refId);
                array[i] = refObject;
            } else {
                push(jObj);
            }
        }
    }
}
```

**Analysis:**
- Good: `references` is hoisted to local variable
- Good: Uses indexed loop allowing in-place array modification
- Potential: Array length is computed on each iteration (though JIT may optimize this)

**Recommendation:**
```java
private void traverseArrayForRefs(Object[] array) {
    final ReferenceTracker refTracker = references;
    final int len = array.length;  // Hoist length
    for (int i = 0; i < len; i++) {
        // ... rest unchanged
    }
}
```

### 3.2 `traverseArray()` Method (Lines 449-479) - **POSITIVE OBSERVATION**

**Current State:**
```java
final boolean isPrimitive = componentType.isPrimitive();
final Object[] refArray = isPrimitive ? null : (Object[]) target;
final int len = items.length;
for (int i = 0; i < len; i++) {
    // ...
}
```

**Analysis:**
- Excellent: Length hoisted to local variable
- Excellent: isPrimitive and refArray computed once outside loop
- Excellent: Uses indexed loop for efficient array access

**Recommendation:** No changes needed - already well-optimized.

### 3.3 `traverseCollection()` Method (Lines 569-607) - **MEDIUM PRIORITY**

**Current State:**
```java
for (Object element : items) {
    if (element == null) {
        col.add(null);
    } else if (isDirectlyAddableJsonValue(element)) {
        col.add(element);
    } else if (element.getClass().isArray()) {
        wrapArrayAndAddToCollection((Object[]) element, Object[].class, col);
    } else {
        processJsonObjectElement((JsonObject) element, jsonObj, col, idx, isList, isEnumSet);
    }
    idx++;
}
```

**Analysis:**
- Uses enhanced for-loop with separate index variable
- `element.getClass().isArray()` called for each non-null, non-JSON-value element
- Multiple branch conditions evaluated in sequence

**Optimization Opportunity:**
The `isDirectlyAddableJsonValue()` check (line 596) calls `instanceof` for 6 types. For arrays with homogeneous types (common case), the first element check could potentially enable branch prediction optimization.

**Recommendation:** The loop is reasonably efficient. Consider whether batch processing would help for large homogeneous collections, but this would require significant refactoring.

---

## 4. Caching Opportunities

### 4.1 `traverseFields()` ReadOptions Access - **MEDIUM PRIORITY**

**Current State:**
```java
public void traverseFields(final JsonObject jsonObj) {
    final Object target = jsonObj.getTarget();

    Map<String, Injector> injectorMap = null;
    if (target != null) {
        injectorMap = getReadOptions().getDeepInjectorMap(target.getClass());
    }

    final ReadOptions readOptions = getReadOptions();
    // ...
}
```

**Analysis:**
- `getReadOptions()` is called twice (lines 327 and 330)
- While `getReadOptions()` is a simple getter, consistency suggests using a single local variable
- The `ReadOptions.getDeepInjectorMap()` already has internal caching via ClassValueMap

**Recommendation:**
```java
public void traverseFields(final JsonObject jsonObj) {
    final Object target = jsonObj.getTarget();
    final ReadOptions readOptions = getReadOptions();  // Single call

    Map<String, Injector> injectorMap = null;
    if (target != null) {
        injectorMap = readOptions.getDeepInjectorMap(target.getClass());  // Use local var
    }
    // ...
}
```

### 4.2 `readOptions.isNonReferenceableClass()` Calls - **ALREADY OPTIMIZED**

**Analysis:**
Looking at ReadOptionsBuilder (line 1853-1857):
```java
public boolean isNonReferenceableClass(Class<?> clazz) {
    return nonRefClasses.contains(clazz) ||
            Number.class.isAssignableFrom(clazz) ||
            clazz.isEnum();
}
```

The method is lightweight with:
- HashSet lookup: O(1)
- Two class checks: constant time

**Recommendation:** No additional caching needed.

### 4.3 Converter Method Caching - **ALREADY OPTIMIZED**

**Analysis:**
The Converter class from java-util has excellent internal caching for:
- `isSimpleTypeConversionSupported()`
- `isConversionSupportedFor()`
- Actual conversion paths

**Recommendation:** No additional caching needed at MapResolver level.

---

## 5. Data Structure Choices

### 5.1 JsonObject as Primary Data Structure - **POSITIVE OBSERVATION**

**Analysis:**
JsonObject uses parallel arrays for storage which provides:
- Memory efficiency (no Entry object overhead)
- Cache-friendly sequential access
- Lazy HashMap index for O(1) lookup on large objects

**Recommendation:** Excellent design choice for the use case.

### 5.2 Stack vs Queue for Traversal - **ALREADY OPTIMAL**

**Current State:**
```java
protected final Deque<JsonObject> stack = new ArrayDeque<>();
```

**Analysis:**
- ArrayDeque is the optimal choice for stack operations
- No allocations during push/pop (until resize)
- Stack-based traversal (DFS) is appropriate for this use case

**Recommendation:** No changes needed.

### 5.3 `substituteSortedCollectionType()` Switch Consideration - **LOW PRIORITY**

**Current State:**
```java
private void substituteSortedCollectionType(JsonObject jsonObj) {
    Class<?> javaType = jsonObj.getRawType();
    if (javaType == null) {
        return;
    }
    if (SortedSet.class.isAssignableFrom(javaType)) {
        jsonObj.setType(LinkedHashSet.class);
    } else if (SortedMap.class.isAssignableFrom(javaType)) {
        jsonObj.setType(LinkedHashMap.class);
    }
}
```

**Analysis:**
- Uses `isAssignableFrom()` which traverses the class hierarchy
- Called multiple times for Maps mode (lines 148-159, 169-177, 188-189)
- However, the method guards with null check and returns early

**Recommendation:** The implementation is acceptable. A potential micro-optimization would be caching the sorted collection check result on the JsonObject, but the benefit would be minimal.

---

## 6. Hot Path Optimizations

### 6.1 `fastPrimitiveCoercion()` - Hot Path for Primitive Arrays - **HIGH PRIORITY OBSERVATION**

**Current State:**
```java
private static Object fastPrimitiveCoercion(Object value, Class<?> valueClass, Class<?> targetType) {
    if (valueClass == Long.class) {
        return coerceLong((Long) value, targetType);
    } else if (valueClass == Double.class) {
        return coerceDouble((Double) value, targetType);
    }
    return null;
}
```

**Analysis:**
This is a critical hot path optimization that:
- Avoids Converter lookup for common cases
- Uses direct casting instead of reflection
- Covers JSON's native numeric types (Long, Double)

**Enhancement Opportunity:**
Consider adding String coercion for common string-to-primitive conversions:
```java
else if (valueClass == String.class && targetType == String.class) {
    return value;  // No conversion needed
}
```

However, this would need profiling to determine if it's a common enough case.

### 6.2 `processArrayElement()` Method (Lines 497-518) - **POSITIVE OBSERVATION**

**Analysis:**
The method structure is well-optimized:
1. Fast path for primitive coercion checked first
2. Converter support checked second
3. JsonObject handling last (least common case for primitive arrays)

**Recommendation:** No changes needed - good branch ordering.

### 6.3 `traverseMap()` Override (Lines 185-208) - **MEDIUM PRIORITY**

**Current State:**
```java
@Override
protected void traverseMap(JsonObject jsonObj) {
    if (jsonObj.getTypeString() != null) {
        substituteSortedCollectionType(jsonObj);
    }

    Object[] complexKeys = jsonObj.getKeys();
    if (complexKeys != null) {
        Object[] items = jsonObj.getItems();
        if (items != null) {
            traverseArrayForRefs(complexKeys);
            traverseArrayForRefs(items);
        }
    } else {
        traverseFields(jsonObj);
    }

    addMapToRehash(jsonObj);
}
```

**Analysis:**
- `substituteSortedCollectionType()` is called here AND potentially again in `traverseFields()` (via the field loop)
- `addMapToRehash()` is always called regardless of whether rehashing is needed

**Recommendation:** The redundant sorted collection check is minor. The implementation is clean and readable.

---

## 7. Memory Usage Analysis

### 7.1 Field-Level Memory Observations

| Field/Variable | Size | Notes |
|----------------|------|-------|
| `references` (inherited) | 8 bytes (ref) | Points to shared ReferenceTracker |
| `stack` (inherited) | ~56 bytes | ArrayDeque with initial capacity |
| `readOptions` (inherited) | 8 bytes (ref) | Shared across resolution |
| `converter` (inherited) | 8 bytes (ref) | Shared instance |

### 7.2 Per-Operation Allocations

| Operation | Allocations | Notes |
|-----------|-------------|-------|
| `traverseFields()` per array field | ~208 bytes | New JsonObject for each array |
| `traverseArrayForRefs()` | 0 bytes | In-place array modification |
| `fastPrimitiveCoercion()` | 16-24 bytes | Boxed primitive return value |

---

## 8. Recommendations Summary

### High Priority (Implement First)

1. **Fix redundant readOptions access in traverseFields()** (Section 4.1)
   - Single call to `getReadOptions()` instead of two
   - Estimated improvement: Minor but enforces consistency

2. **Remove no-op null put in traverseFields()** (Section 1.1)
   - When `rhs == null`, remove the `jsonObj.put(fieldName, null)` call
   - Avoids unnecessary hash invalidation

### Medium Priority (Consider for Next Optimization Pass)

3. **Hoist array length in traverseArrayForRefs()** (Section 3.1)
   - Store `array.length` in local variable
   - Estimated improvement: Minor (JIT may already optimize)

4. **Evaluate JsonObject pooling for array field processing** (Section 2.1)
   - For workloads with many array fields
   - Requires profiling to validate benefit

5. **Consider batch processing for homogeneous collections** (Section 3.3)
   - Would require significant refactoring
   - Only if profiling shows this as a bottleneck

### Low Priority (Future Consideration)

6. **Add String identity check to fastPrimitiveCoercion()** (Section 6.1)
   - Only if profiling shows String->String conversions are common

7. **Cache sorted collection type check** (Section 5.3)
   - Minimal benefit for the added complexity

---

## 9. Code Quality Observations

### Positive Patterns Identified

1. **Static helper methods for coercion** - Avoids virtual dispatch, enables inlining
2. **Final local variables** - Enables JIT optimization, documents intent
3. **Early returns** - Reduces nesting, improves readability
4. **Existing fast paths** - `fastPrimitiveCoercion()` shows performance awareness
5. **Proper use of inheritance** - Resolver provides well-designed extension points

### Areas for Improvement

1. **Method length** - `traverseFields()` is 90 lines; consider extracting sub-methods
2. **Comment density** - Some complex logic blocks lack explanatory comments
3. **Defensive coding** - Some null checks are redundant based on code flow

---

## 10. Appendix: Method Call Frequency Analysis

Based on typical JSON deserialization workloads:

| Method | Expected Call Frequency | Hot Path? |
|--------|------------------------|-----------|
| `traverseFields()` | Once per JsonObject | Yes |
| `traverseArray()` | Once per array | Yes |
| `traverseMap()` | Once per Map | Yes |
| `traverseCollection()` | Once per Collection | Yes |
| `fastPrimitiveCoercion()` | Once per array element | **Critical** |
| `verifyRootType()` | Once per root object | No |
| `substituteSortedCollectionType()` | Once per sorted collection | No |
| `reconcileResult()` | Once per root | No |

---

## 11. Conclusion

MapResolver is a well-implemented component with evidence of prior performance optimization work. The `fastPrimitiveCoercion()` implementation demonstrates good performance awareness. The high-priority recommendations are minor consistency improvements rather than major architectural changes.

The most impactful optimization would be reducing object allocation in `traverseFields()` for array processing, but this requires careful analysis of the object lifecycle and would benefit from profiling data to validate the improvement.

**Overall Performance Grade: B+**
- Good algorithmic complexity
- Efficient data structures
- Some minor redundancies that could be cleaned up
- Well-suited for its purpose in the json-io pipeline

---

*Report generated by Claude Opus 4.5 Performance Analysis*
*File analyzed: MapResolver.java (708 lines)*
