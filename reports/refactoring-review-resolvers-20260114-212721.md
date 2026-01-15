# Refactoring Review Report: Resolver Classes

**Date**: 2026-01-14
**Files Analyzed**:
- `src/main/java/com/cedarsoftware/io/Resolver.java` (1491 lines)
- `src/main/java/com/cedarsoftware/io/ObjectResolver.java` (1057 lines)
- `src/main/java/com/cedarsoftware/io/MapResolver.java` (711 lines)

**Total Lines**: ~3259 lines

---

## Executive Summary

The Resolver hierarchy is a well-designed core component of json-io that handles JSON deserialization through two modes: Java object mode (`ObjectResolver`) and Maps mode (`MapResolver`). The abstract `Resolver` base class provides common functionality while subclasses specialize for their respective modes.

Overall, the code demonstrates **good architectural decisions** with proper use of template method pattern and hook methods. However, there are several opportunities for improvement in code clarity, duplication reduction, and maintainability.

**Quality Score**: 7.5/10

---

## 1. Code Duplication Analysis

### 1.1 HIGH PRIORITY - Array Element Processing Duplication

**Location**:
- `ObjectResolver.traverseArray()` (lines 346-415)
- `MapResolver.traverseArray()` (lines 452-482)

**Issue**: Both resolvers have similar array traversal logic with subtle differences. While `ObjectResolver` handles more complex type resolution, both share:
- Null element handling
- Nested array detection
- JsonObject reference resolution
- Primitive vs. reference array distinction

**Recommendation**: Consider extracting a protected template method `processArrayElement()` in the base `Resolver` class with hook methods for mode-specific behavior.

```java
// Potential base class method
protected void traverseArrayCommon(JsonObject jsonObj, ArrayElementProcessor processor) {
    Object[] items = jsonObj.getItems();
    if (ArrayUtilities.isEmpty(items)) return;

    Object target = jsonObj.getTarget();
    Class<?> componentType = getArrayComponentType(target);
    boolean isPrimitive = componentType.isPrimitive();
    Object[] refArray = isPrimitive ? null : (Object[]) target;

    for (int i = 0; i < items.length; i++) {
        processor.process(items[i], target, refArray, i, isPrimitive, componentType);
    }
    jsonObj.setFinished();
}
```

---

### 1.2 MEDIUM PRIORITY - Collection Traversal Duplication

**Location**:
- `ObjectResolver.traverseCollection()` (lines 269-336)
- `MapResolver.traverseCollection()` (lines 572-610)

**Issue**: Both methods share:
- Null element handling
- `isDirectlyAddableJsonValue()` check
- Array element wrapping pattern
- Reference resolution in collections

**Differences**: ObjectResolver handles type inference via `elementType`, while MapResolver uses simpler Object.class.

**Current Pattern** (duplicated in both):
```java
if (element == null) {
    col.add(null);
} else if (isDirectlyAddableJsonValue(element)) {
    col.add(element);
} else if (element.getClass().isArray()) {
    wrapArrayAndAddToCollection((Object[]) element, ...);
} else {
    // Mode-specific processing
}
```

**Recommendation**: Extract the common structure into a base class template method with a hook for mode-specific JsonObject processing.

---

### 1.3 MEDIUM PRIORITY - Reference Resolution Patterns

**Location**: Multiple locations across all three files

**Issue**: The pattern for resolving `@ref` references appears repeatedly:
```java
if (jObj.isReference()) {
    long refId = jObj.getReferenceId();
    JsonObject refObject = references.getOrThrow(refId);
    // ... use refObject
}
```

**Occurrences**:
- `ObjectResolver.assignField()` - line 162
- `ObjectResolver.traverseCollection()` - line 311
- `ObjectResolver.traverseArray()` - line 393
- `MapResolver.traverseArrayForRefs()` - line 224
- `MapResolver.traverseFields()` - line 366
- `MapResolver.processArrayReference()` - line 538

**Recommendation**: The base `Resolver` class already has `resolveReferenceInCollection()` helper (line 1363). Consider adding a more general `resolveReference(JsonObject refHolder)` that returns the resolved object or null.

---

### 1.4 LOW PRIORITY - Sorted Collection Substitution

**Location**:
- `MapResolver.substituteSortedCollectionType()` (lines 148-159)
- Called from multiple places in MapResolver

**Issue**: This method is called from 4 different locations:
- `adjustTypeBeforeResolve()` (line 175)
- `traverseMap()` (line 189)
- `traverseCollection()` (line 578)
- `resolveArray()` (line 652)

**Recommendation**: Consider consolidating calls or documenting why each call site is necessary.

---

## 2. Complex Methods Analysis

### 2.1 CRITICAL - `Resolver.toJava()` Method (117 lines)

**Location**: `Resolver.java` lines 318-434

**Cyclomatic Complexity**: HIGH (estimated 15+)

**Issues**:
1. Multiple nested conditionals
2. Handles 5+ distinct code paths
3. Comments indicate special cases that are hard to follow
4. Long method with multiple responsibilities

**Current Structure**:
```java
public Object toJava(Type type, Object value) {
    // 1. Null check
    // 2. Primitive handling
    // 3. Array wrapping in JsonObject
    // 4. Special case: Map->non-Map conversion
    // 5. Special case: Array->different-Array conversion
    // 6. Standard resolution via toJavaObjects()
    // 7. Result extraction
    // 8. Final type conversion
}
```

**Extract Method Candidates**:
1. `wrapValueInJsonObject(Object value)` - lines 328-350
2. `handleMapToNonMapConversion(JsonObject jsonObj, Type type)` - lines 352-375
3. `handleArrayCrossConversion(JsonObject jsonObj, Type type)` - lines 377-419

**Recommendation**: Refactor into smaller, focused methods with clear responsibilities.

---

### 2.2 HIGH PRIORITY - `ObjectResolver.markUntypedObjects()` Complex (250+ lines total)

**Location**: `ObjectResolver.java` lines 507-759

**Issue**: This method and its 7 helper methods form a complex subsystem for type marking:
- `markUntypedObjects()` (main method)
- `handleParameterizedTypeMarking()`
- `handleMapTypeMarking()`
- `handleCollectionTypeMarking()`
- `handleArrayInCollection()`
- `handleObjectFieldsMarking()`
- `shouldSkipTraversal()`
- `addItemsToStack()`
- `stampTypeOnJsonObject()`

**Observations**:
- Uses stack-based traversal (good for avoiding stack overflow)
- Has optimization checks (`shouldSkipTraversal`)
- Complex type resolution logic

**Recommendation**: Consider extracting these into a separate inner class `TypeMarker` or utility class to improve organization:
```java
private class TypeMarker {
    private final Deque<Map.Entry<Type, Object>> stack = new ArrayDeque<>();
    private final Set<JsonObject> visited;

    void mark(Type type, JsonObject rhs) { ... }
}
```

---

### 2.3 HIGH PRIORITY - `ObjectResolver.createAndPopulateArray()` (95 lines)

**Location**: `ObjectResolver.java` lines 785-879

**Issues**:
1. Multiple special cases (char[], nested arrays, collections)
2. Forward reference tracking logic interleaved with array population
3. Complex control flow with multiple `continue` statements

**Extract Method Candidates**:
1. `handleCharArray(List<Object> list)` - lines 787-795
2. `handleNestedArrayElement(Object element, Type elementType, Class<?> componentClass)` - lines 815-829
3. `trackUnresolvedReference(JsonObject jsonArray, List<UnresolvedArrayElement> elements, Object array)` - lines 864-876

---

### 2.4 MEDIUM PRIORITY - `MapResolver.traverseFields()` (86 lines)

**Location**: `MapResolver.java` lines 329-414

**Issues**:
1. Three main branches (array, JsonObject, other)
2. Nested conditionals within JsonObject branch
3. Comments explaining "upgrading" logic could be clearer

**Recommendation**: Extract the three main branches into separate methods:
```java
private void processArrayField(Object[] rhs);
private void processJsonObjectField(JsonObject jObj, String fieldName, Injector injector);
private void processOtherField(Object rhs, String fieldName, Injector injector);
```

---

## 3. SOLID Principle Analysis

### 3.1 Single Responsibility Principle (SRP)

**Violations**:

1. **Resolver.java** has multiple responsibilities:
   - JSON value conversion (`toJava()`, `convertToType()`)
   - Object graph traversal (`traverseJsonObject()`, `traverseSpecificType()`)
   - Instance creation (`createInstance()`, `createInstanceUsingClassFactory()`)
   - Reference management (`patchUnresolvedReferences()`, `rehashMaps()`)
   - Security limit checking (multiple methods)
   - Convenience methods for ClassFactory (`readString()`, `readInt()`, etc.)

   **Recommendation**: Consider extracting:
   - `InstanceFactory` class for creation logic
   - `ReferenceResolver` class for reference patching
   - `TypeConverter` class for type conversion logic

2. **ObjectResolver.traverseFields()** handles:
   - Field iteration
   - Injector lookup
   - Missing field handling
   - Type resolution
   - Value assignment

---

### 3.2 Open/Closed Principle (OCP)

**Good**: The template method pattern with hooks (`adjustTypeBeforeResolve()`, `reconcileResult()`) allows extension without modification.

**Concern**: The `DEFAULT_INSTANTIATORS` static map in `Resolver.java` (lines 862-934) is not extensible. Adding new types requires modifying the class.

**Recommendation**: Allow registration of additional instantiators via configuration.

---

### 3.3 Liskov Substitution Principle (LSP)

**Good**: Both `ObjectResolver` and `MapResolver` properly extend `Resolver` and can be used interchangeably where the base type is expected.

**Minor Issue**: `MapResolver.traverseMap()` has significantly different behavior than the base class method it overrides, which could surprise maintainers.

---

### 3.4 Interface Segregation Principle (ISP)

**Concern**: The `Resolver` class exposes many public methods that are primarily for internal use:
- `readString()`, `readInt()`, etc. (convenience methods)
- `push()`, `getSealedSupplier()` (internal mechanics)
- `valueToTarget()` (internal conversion)

**Recommendation**: Consider marking internal methods as `protected` or using package-private visibility.

---

### 3.5 Dependency Inversion Principle (DIP)

**Good**: Dependencies are injected via constructor (`ReadOptions`, `ReferenceTracker`, `Converter`).

**Concern**: Direct use of concrete classes like `ArrayList`, `HashMap` in various places instead of interfaces.

---

## 4. Extract Method Candidates

### 4.1 Priority 1 (High Impact)

| Current Location | Suggested Method | Lines |
|-----------------|------------------|-------|
| `Resolver.toJava()` | `wrapRawArrayInJsonObject()` | 328-350 |
| `Resolver.toJava()` | `attemptEarlyMapConversion()` | 352-375 |
| `Resolver.toJava()` | `attemptArrayCrossConversion()` | 377-419 |
| `Resolver.createInstance()` | `handleEnumCreation()` | 1012-1046 |
| `ObjectResolver.traverseArray()` | `processArrayElementByType()` | 372-414 |

### 4.2 Priority 2 (Medium Impact)

| Current Location | Suggested Method | Lines |
|-----------------|------------------|-------|
| `Resolver.convertToType()` | `attemptEnumConversion()` | 611-625 |
| `Resolver.convertToType()` | `checkCollectionCompatibility()` | 606-608 |
| `ObjectResolver.assignField()` | `handleJsonObjectField()` | 159-178 |
| `MapResolver.traverseFields()` | `upgradeFieldValue()` | 381-405 |

---

## 5. Long Parameter Lists

### 5.1 `ObjectResolver.handleParameterizedTypeMarking()` (5 parameters)

**Location**: Line 546

```java
private void handleParameterizedTypeMarking(
    final ParameterizedType pType,
    final Object instance,
    final Type parentType,
    final Deque<Map.Entry<Type, Object>> stack)
```

**Recommendation**: The stack parameter could be a class field if this were extracted to a `TypeMarker` inner class.

---

### 5.2 `MapResolver.processArrayElement()` (7 parameters)

**Location**: Line 500

```java
private void processArrayElement(
    Object element,
    Class<?> elementClass,
    Class<?> componentType,
    Object target,
    Object[] refArray,
    int index,
    boolean isPrimitive)
```

**Recommendation**: Create an `ArrayElementContext` class to encapsulate array traversal state:
```java
class ArrayElementContext {
    final Object target;
    final Object[] refArray;
    final Class<?> componentType;
    final boolean isPrimitive;
    int currentIndex;
}
```

---

### 5.3 `Resolver.setArrayElement()` (5 parameters)

**Location**: Line 1316

```java
protected static void setArrayElement(
    Object array,
    Object[] refArray,
    int index,
    Object value,
    boolean isPrimitive)
```

**Note**: This is a utility method where parameter count is acceptable.

---

## 6. Deeply Nested Code Analysis

### 6.1 `ObjectResolver.handleObjectFieldsMarking()` - 5 levels deep

**Location**: Lines 651-692

```java
private void handleObjectFieldsMarking(...) {
    if (!(instance instanceof JsonObject)) {  // Level 1
        return;
    }
    // ...
    for (Map.Entry<Object, Object> entry : jObj.entrySet()) {  // Level 2
        // ...
        if (injector != null) {  // Level 3
            // ...
            if (TypeUtilities.hasUnresolvedType(resolved)) {  // Level 4
                resolved = typeArgs[0];
            }
            if (!shouldSkipTraversal(resolved)) {  // Level 5
                stack.addFirst(...);
            }
        }
    }
}
```

**Recommendation**: Use early returns and guard clauses:
```java
if (!(instance instanceof JsonObject)) return;
// ...
for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
    final String fieldName = (String) entry.getKey();
    if (fieldName.startsWith("this$")) continue;  // Guard clause

    Injector injector = classFields.get(fieldName);
    if (injector == null) continue;  // Guard clause

    Type resolved = resolveFieldType(injector, containerType, typeArgs);
    if (shouldSkipTraversal(resolved)) continue;  // Guard clause

    stack.addFirst(new AbstractMap.SimpleEntry<>(resolved, entry.getValue()));
}
```

---

### 6.2 `MapResolver.traverseFields()` - 5 levels deep

**Location**: Lines 329-414

**Issue**: Multiple nested if-else branches within the loop.

**Recommendation**: Extract handling logic into separate methods as noted in Section 2.4.

---

## 7. Magic Numbers and Strings

### 7.1 Magic Strings

| Location | String | Recommendation |
|----------|--------|----------------|
| `Resolver.java:76` | `"_︿_ψ_☼"` | Already named `NO_FACTORY` - Good |
| `ObjectResolver.java:671` | `"this$"` | Extract to constant `OUTER_CLASS_PREFIX` |
| `MapResolver.java:625` | `"name"`, `"Enum.name"` | Extract to constants |

### 7.2 Magic Numbers

| Location | Number | Recommendation |
|----------|--------|----------------|
| `Resolver.java:862-934` | Multiple instantiator lambdas | Well-organized, acceptable |
| `JsonObject.java:46` | `16` (INITIAL_CAPACITY) | Already a named constant - Good |
| `JsonObject.java:47` | `16` (INDEX_THRESHOLD) | Already a named constant - Good |

---

## 8. Dead Code Analysis

### 8.1 Potentially Unused Code

1. **`Resolver.getLength()` deprecation** (JsonObject.java:208-211)
   - Marked `@Deprecated` but still present
   - Recommend: Add `@Deprecated(forRemoval = true)` and document removal timeline

2. **Commented code** in `Resolver.isCompatibleCollectionType()` (lines 582-584):
   ```java
   //        // Future proof - if we add SealableQueue - then make sure this code is added:
   //        return graph instanceof Queue && Queue.class.isAssignableFrom(rawType);
   ```
   - Recommendation: Remove commented code or convert to a TODO/FIXME

3. **`UnresolvedArrayElement` inner class** (ObjectResolver.java:889-897)
   - Used only in `createAndPopulateArray()`
   - Consider: Could be replaced with a simple array of indices if refHolder storage is rethought

---

## 9. Unclear Naming

### 9.1 Abbreviations and Cryptic Names

| Current Name | Location | Suggested Name |
|-------------|----------|----------------|
| `rhs` | Multiple locations | `rightHandSideValue` or `fieldValue` |
| `jObj` | Multiple locations | `jsonObject` (more descriptive) |
| `col` | Collection methods | `collection` |
| `compType` | Array methods | `componentType` |
| `c` | `createInstanceUsingClassFactory()` | `targetClass` |
| `jsRhs` | `ObjectResolver.assignField()` | `jsonRightHandSide` |

### 9.2 Method Naming Improvements

| Current Name | Location | Suggested Name |
|-------------|----------|----------------|
| `readWithFactoryIfExists()` | Both resolvers | `tryCreateWithFactory()` or `createUsingFactoryIfAvailable()` |
| `valueToTarget()` | Resolver | `tryConvertToTarget()` |
| `markUntypedObjects()` | ObjectResolver | `inferTypesForUntypedJsonObjects()` |
| `stampTypeOnJsonObject()` | ObjectResolver | `setTypeIfMissing()` |

### 9.3 Class Naming

| Current Name | Suggested Name | Reason |
|-------------|----------------|--------|
| `Missingfields` | `MissingField` | Standard Java naming (PascalCase, singular) |
| `UnresolvedArrayElement` | Could be eliminated | Too specific, consider simplifying |

---

## 10. Opportunities for Better Abstractions

### 10.1 Array Processing Strategy Pattern

**Current State**: Both resolvers have similar but different array processing logic spread across multiple methods.

**Proposed Abstraction**:
```java
interface ArrayElementProcessor {
    void processNull(Object target, int index);
    void processReference(JsonObject refHolder, Object target, int index);
    void processNestedArray(Object[] element, Object target, int index, Type componentType);
    void processJsonObject(JsonObject element, Object target, int index, Type componentType);
    void processValue(Object element, Object target, int index, Class<?> componentType);
}

abstract class AbstractArrayProcessor implements ArrayElementProcessor {
    // Common implementations
}

class ObjectResolverArrayProcessor extends AbstractArrayProcessor { ... }
class MapResolverArrayProcessor extends AbstractArrayProcessor { ... }
```

---

### 10.2 Type Inference Strategy

**Current State**: Type inference logic is scattered across multiple methods in ObjectResolver.

**Proposed Abstraction**:
```java
interface TypeInferenceStrategy {
    Type inferType(JsonObject jsonObj, Type parentType, Type fieldType);
    Type resolveCollectionElementType(Type containerType);
    Type resolveMapKeyValueTypes(Type mapType);
}
```

---

### 10.3 Value Conversion Chain

**Current State**: Value conversion logic in `convertToType()` handles multiple cases in sequence.

**Proposed Abstraction**:
```java
interface ValueConverter {
    boolean canConvert(Object value, Type targetType);
    Object convert(Object value, Type targetType);
}

class ValueConversionChain {
    private final List<ValueConverter> converters;

    Object convert(Object value, Type targetType) {
        for (ValueConverter converter : converters) {
            if (converter.canConvert(value, targetType)) {
                return converter.convert(value, targetType);
            }
        }
        throw new ConversionException(...);
    }
}
```

---

### 10.4 Reference Resolution Service

**Current State**: Reference resolution logic is embedded in multiple methods.

**Proposed Abstraction**:
```java
interface ReferenceResolver {
    Object resolveReference(JsonObject refHolder);
    void addUnresolvedReference(JsonObject parent, int index, long refId);
    void addUnresolvedReference(JsonObject parent, String fieldName, long refId);
    void patchAllUnresolved();
}
```

---

## 11. Additional Observations

### 11.1 Security Considerations

**Good Practices**:
- Security limits are configurable via ReadOptions
- DoS attack prevention through bounded collections
- Reference chain depth limiting

**Recommendation**: Consider adding metrics/logging for security limit hits in production monitoring scenarios.

---

### 11.2 Performance Considerations

**Good Practices**:
- Hoisted ReadOptions constants to avoid repeated method calls
- Pre-sized ArrayList capacity
- Optimized array access patterns
- `ClassValueMap` usage for caching

**Areas for Review**:
- `markUntypedObjects()` creates many `AbstractMap.SimpleEntry` objects
- Consider object pooling for high-frequency traversals

---

### 11.3 Documentation Quality

**Strong Areas**:
- Comprehensive Javadoc on public methods
- Good inline comments explaining complex logic
- Clear class-level documentation

**Areas for Improvement**:
- Some private methods lack documentation
- Complex algorithms could benefit from flowcharts or sequence diagrams
- Consider adding package-level documentation

---

## 12. Recommended Refactoring Priorities

### Phase 1: Quick Wins (Low Risk, High Value)
1. Extract magic strings to named constants
2. Rename cryptic variables (`rhs`, `jObj`, `col`)
3. Fix `Missingfields` class name to `MissingField`
4. Add guard clauses to reduce nesting depth

### Phase 2: Method Extraction (Medium Risk, High Value)
1. Break down `Resolver.toJava()` into smaller methods
2. Extract array element processing into helper methods
3. Create `ArrayElementContext` parameter object

### Phase 3: Architectural Improvements (Higher Risk, Very High Value)
1. Extract `TypeMarker` inner class from ObjectResolver
2. Implement Strategy pattern for array processing
3. Create `ReferenceResolver` abstraction
4. Consider separating instantiation logic into `InstanceFactory`

### Phase 4: Code Consolidation (Medium Risk, Medium Value)
1. Reduce duplication between ObjectResolver and MapResolver
2. Create common base methods with hooks for mode-specific behavior
3. Consolidate reference resolution patterns

---

## 13. Conclusion

The Resolver hierarchy is fundamentally well-designed with appropriate use of inheritance and template methods. The code has clearly evolved over time with performance optimizations and security enhancements.

The main opportunities for improvement are:
1. **Complexity Management**: Breaking down large methods into smaller, focused units
2. **Duplication Reduction**: Consolidating similar patterns between ObjectResolver and MapResolver
3. **Naming Clarity**: Using more descriptive names for variables and methods
4. **Abstraction Introduction**: Creating cleaner separation of concerns through interfaces and helper classes

These refactorings would improve maintainability and make the codebase more accessible to new contributors while preserving the existing functionality and performance characteristics.

---

**Report Generated**: 2026-01-14 21:27:21
**Analyzer**: Claude Code Refactoring Review
