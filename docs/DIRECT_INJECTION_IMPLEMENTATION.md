# Direct Field Injection Implementation Guide

## Overview

This document captures the ongoing performance optimization work for json-io's `JsonParser`. The goal is to bypass the intermediate `JsonObject` representation when parsing JSON into Java objects, injecting field values directly into target instances during parsing.

**Status**: Active development - Parser:Resolver ratio now 4.4:1 (Dec 1, 2025).

## Performance Context

### Current Performance Baseline (Nov 28, 2025)
- All 2120 tests passing
- Baseline commit: `58df5fff` - "Performance: Inline processFieldValue common case in processObjectFrame"

### JFR Profiling Development Workflow

**This is the standard workflow after each small change.** When the user says "I generated profile data, please jfr print, analyze, and report", follow this process:

#### Step 1: User Generates Profile Data
1. User runs `JsonPerformanceTest` main() in IntelliJ with the **Profiler** attached
   - Note: This is a `main()` method, NOT a JUnit test (so it doesn't run during builds)
   - Location: `src/test/java/com/cedarsoftware/io/JsonPerformanceTest.java`
2. User copies the generated `.jfr` file to `/tmp/` with a descriptive name

#### Step 2: Claude Extracts and Analyzes JFR Data

When asked to analyze, run:
```bash
/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/jfr print --events jdk.ExecutionSample /tmp/<newest_file>.jfr > /tmp/<output_name>.txt
```

Then analyze cedarsoftware-specific samples:
```bash
grep "com.cedarsoftware" /tmp/<output>.txt | sort | uniq -c | sort -rn | head -40
```

#### Step 3: Three-Way Comparison Report

Compare THREE profiles side-by-side:
1. **Baseline** - The oldest/original profile in `/tmp/` (before optimization work began)
2. **Previous** - The second-newest profile (state before this change)
3. **Current** - The newest profile (state after this change)

**Report Format:**
```
| Method/Location                        | Baseline | Previous | Current | Δ from Prev |
|----------------------------------------|----------|----------|---------|-------------|
| JsonParser.processObjectFrame          |   1200   |    900   |   850   |    -50 ✓    |
| JsonParser.readValue                   |    800   |    750   |   780   |    +30 ⚠    |
| FastReader.readChar                    |    600   |    500   |   490   |    -10 ✓    |
```

**Legend:**
- ✓ = Improvement (lower sample count)
- ⚠ = Regression (higher sample count) - INVESTIGATE!
- ≈ = No significant change (±5%)

#### Step 4: Commentary and Recommendation

After the table, provide:
1. **Summary**: Overall trend (faster/slower/same)
2. **Hot spots**: Top 3 methods consuming time
3. **Regressions**: Any methods that got worse (and why if known)
4. **Recommendation**:
   - "Lock in gains - commit now" (if improvement)
   - "Investigate regression before committing" (if worse)
   - "No significant change - continue" (if neutral)

#### Example Analysis Output

```
## JFR Profile Analysis - Nov 28, 2025

### Three-Way Comparison (cedarsoftware samples)

| Method                                 | Baseline | Previous | Current | Δ      |
|----------------------------------------|----------|----------|---------|--------|
| JsonParser.processObjectFrame:892      |   1523   |   1102   |   987   | -115 ✓ |
| Resolver.traverseFields:445            |    834   |    834   |   812   |  -22 ✓ |
| FastReader.readChar:156                |    612   |    590   |   595   |   +5 ≈ |

### Summary
- **Overall**: 8% improvement in parsing hot path
- **Hot spots**: processObjectFrame still dominates (expected)
- **Regressions**: None
- **Recommendation**: ✅ Lock in gains - commit this change

### Observations
The inlining of field name handling reduced samples in processObjectFrame.
No unexpected side effects in other methods.
```

#### File Naming Convention
Use descriptive names for JFR output files:
- `/tmp/jfr_baseline_nov28.txt` - Original baseline
- `/tmp/jfr_after_inline_fieldname.txt` - After specific change
- `/tmp/jfr_current.txt` - Most recent (gets overwritten)

## Direct Injection Design

### The Problem
Current parsing flow:
```
JSON text → JsonParser → JsonObject (intermediate) → Resolver → Java Object
```

Every field goes through:
1. Parse field name/value
2. `JsonObject.put(field, value)` - HashMap overhead
3. Later: Resolver traverses JsonObject, creates instance, injects fields

### The Solution
When we know the target type, skip JsonObject:
```
JSON text → JsonParser → Direct injection into Java Object
```

### JsonWriter Field Ordering: @id and @type Come First

**Critical for Direct Injection**: JsonWriter ALWAYS writes meta-fields in this order:

1. **`@id`** (if referenced) - Written first
2. **`@type`** (if needed for disambiguation) - Written second
3. **Regular fields** - Written after meta-fields

This is guaranteed by `JsonWriter.writeObject()` (lines 1652-1679):
```java
if (!bodyOnly) {
    out.write('{');
    if (referenced) {
        writeId(getId(obj));  // @id FIRST
    }
    if (showType) {
        writeType(obj.getClass().getName(), out);  // @type SECOND
    }
}
// Then regular fields are written in the loop
```

**Why This Matters**: When parsing, we will ALWAYS see `@id` and `@type` BEFORE any regular fields. This means:
- We can register the object's ID immediately when we see `@id`
- We can create the correct instance type when we see `@type` (before any fields need to be injected)
- Direct injection can proceed confidently for all regular fields

### @type vs suggestedType: Understanding the Type System

**Key Principle**: `@type` from JSON is always the **concrete type** to instantiate. `suggestedType` provides **generic type parameters** that `@type` cannot carry.

#### @type is the Truth for Instance Creation

When `@type` is present in JSON, it's always the correct concrete class to create:
- JsonWriter writes `@type` as the actual `obj.getClass().getName()`
- This is the exact class that was serialized
- Always use `@type` for `Class.forName()` / instance creation

#### suggestedType Carries Generic Information

`suggestedType` comes from the parent's field declaration and carries generic parameters:
```java
class Company {
    List<Building> locations;  // suggestedType is List<Building>
}
```

When parsing `locations`:
- `@type` might be `java.util.ArrayList` (concrete class to instantiate)
- `suggestedType` is `List<Building>` (tells us elements should be `Building`)

**The generic type parameter (`Building`) must propagate to child parsing**, because:
- `ArrayList.class` doesn't know its element type at runtime (type erasure)
- Only `suggestedType` carries this information
- Each level extracts its component type and passes it down

#### Type Propagation Down the DAG

For nested generics like `List<Building<String>>`:
```
Level 0: Field type = List<Building<String>>
         → Create ArrayList (from @type or default)
         → Pass Building<String> as suggestedType to elements

Level 1: suggestedType = Building<String>
         → Create Building instance
         → Pass String as suggestedType to Building's generic fields

Level 2: suggestedType = String
         → Create/convert String values
```

The `TypeUtilities.extractArrayComponentType()` and similar methods handle this extraction.

### JsonWriter's Minimal @type Emission Strategy

**Critical Context**: JsonWriter intelligently minimizes `@type` output to keep JSON clean. Understanding this strategy tells us when the parser will have type information available WITHOUT an explicit `@type` in the JSON.

#### When @type is NOT written (type is inferred):

1. **Field type matches instance type**:
   ```java
   class Person { Employee manager; }  // manager field is type Employee
   ```
   If `manager` holds an `Employee` instance (exact match), no `@type` is written. The reader uses the field's declared type.

2. **Array component type matches element types**:
   ```java
   Date[] dates;  // Array of Date
   ```
   If all elements are `Date` instances, no `@type` needed. Even if JSON contains `"2024-01-15"` strings, `Converter.convert()` handles String→Date.

3. **Generic type parameter matches instance types**:
   ```java
   List<Employee> staff;        // All employees - no @type needed per element
   Map<String, Building> sites; // Values are Buildings - no @type on values
   ```

4. **Root type specified via `.asClass()` or `.asType()`**:
   ```java
   JsonIo.toJava(json, readOptions).asClass(Person.class)
   ```
   The root object won't have `@type` because caller specified it.

#### When @type IS written (disambiguation required):

1. **Field type doesn't match instance type**:
   ```java
   class Company { Object data; }  // Field is Object
   ```
   If `data` holds a `Building`, JSON will include `{"@type":"Building", ...}`.

2. **Array of Object containing typed instances**:
   ```java
   Object[] items;  // Contains Vehicle instances
   ```
   Each element: `{"@type":"Vehicle", "vin":"123", ...}`

3. **Polymorphic field (interface or abstract class)**:
   ```java
   class Zoo { Animal resident; }  // Animal is interface/abstract
   ```
   Actual instance type must be written: `{"@type":"Lion", ...}`

#### Implications for Direct Injection

When parsing, we know the type from:
1. **`suggestedType` parameter** - Passed from parent frame's field type or generic parameter
2. **Explicit `@type` in JSON** - When JsonWriter had to disambiguate

**Key insight**: For the common case (90%+), `suggestedType` tells us the type BEFORE we read any fields. Direct injection can begin immediately. Only when `suggestedType` is `Object.class` or an interface do we need to wait for `@type` field.

### Key Insight: Only `@ref` Prevents Direct Injection

Most `@` meta-fields are compatible with direct injection:
- `@type` - Tells us which class to instantiate (we use this!)
- `@id` - Assigns a reference ID to this object (we track it, but still create the object)
- Regular fields - Inject directly into the target instance

**The one exception is `@ref`**: This indicates the object is a reference to something defined elsewhere in the JSON.
- If the referenced object was already created → return it directly (common case)
- If it's a forward reference (rare) → must use JsonObject path for later resolution

### Meta Fields Reference

**Note**: `@id` and `@type` are ALWAYS written first by JsonWriter (before any regular fields).

| Field | Purpose | Direct Injection Compatible? | Notes |
|-------|---------|------------------------------|-------|
| `@type` / `@t` | Concrete type to instantiate | Yes - use for instance creation | Always the truth; appears before regular fields |
| `@id` / `@i` | Reference ID for this object | Yes - register in references | Appears first; doesn't affect type |
| `@ref` / `@r` | Reference to another object | No - needs resolution | May be forward ref (object not yet created) |
| `@items` / `@e` | Array items | No - arrays need size first | Used for typed arrays |
| `@keys` / `@k` | Map keys | No - Maps use different path | Map entry handling |
| `@enum` | Legacy EnumSet support | No - special handling needed | Deprecated format |

## Implementation Challenges (Lessons Learned)

This section documents challenges discovered during an initial implementation attempt. These must be addressed incrementally when re-implementing direct injection.

### Challenge 1: Unknown Fields Must Be Preserved

**Problem**: When direct injection is active and a field has no Injector (unknown field), the field value was silently discarded.

**Impact**:
- `MissingFieldHandler` never gets called because fields aren't stored in JsonObject
- Tests like `MissingFieldHandlerTest.testMissingHandler` fail because the handler expects to receive the unknown field

**Solution**: Even in direct injection mode, unknown fields must be stored in JsonObject so Resolver can invoke MissingFieldHandler later.

### Challenge 2: CustomReaders Require Full JsonObject

**Problem**: Custom readers (registered via `ReadOptionsBuilder.addCustomReader()`) expect JsonObject to contain all parsed field values. Direct injection bypasses `JsonObject.put()`, leaving the JsonObject empty.

**Impact**:
- `CustomReaderMapTest.testMapWithCustom` fails with NPE because `jObj.get("x")` returns null
- Any type with a custom reader will break if direct injection is applied

**Solution**: Types with custom readers must NOT use direct injection. Check `readOptions.getCustomReader(rawClass) != null` in eligibility check, OR always store fields in JsonObject even when injecting directly (belt and suspenders).

### Challenge 3: Collection Type Confusion

**Problem**: Parser was returning arrays when Collections were expected in some cases.

**Impact**:
- `CollectionTests.testCollectionWithParameterizedTypes` throws ClassCastException: `Point[]` cannot be cast to `Set`
- Collections not being populated correctly (size 0 instead of 3)

**Solution**: Ensure `resolver.resolveArray()` properly handles Collection types, not just arrays. The type system must flow correctly from suggestedType through to array resolution.

### Challenge 4: JsonWriter Over-Emitting @type

**Problem**: After round-tripping through direct injection, JsonWriter emits more `@type` annotations than expected.

**Impact**:
- Reconstitute tests fail (ArrayTest, ConstructorTest, FieldsTest)
- Expected: `"_booleans_a":[true,false,true]`
- Actual: `"_booleans_a":{"@type":"boolean[]","@items":[true,false,true]}`

**Analysis**: This may be a writer-side issue rather than parser-side. The parser may be losing type information that JsonWriter uses to decide when `@type` is needed. Investigate whether the JsonObject's type field is being set correctly during direct injection.

### Challenge 5: Types Requiring Map->Type Conversion

**Problem**: Some types like `ZonedDateTime`, `UUID`, etc. use `Converter.convert(Map, TargetType)` to create instances from the parsed JSON fields. These types need the full JsonObject to be available.

**Solution**: Check `converter.isConversionSupportedFor(Map.class, rawClass)` in eligibility check and disable direct injection for these types.

### Challenge 6: Circular References in Maps

**Problem**: `MapsTest.testMapWithNestedCircularReferenceInValues` fails.

**Analysis**: Complex reference graphs with circular references in Map values may not work correctly with direct injection because the reference tracking depends on JsonObject's `@id`/`@ref` handling.

**Solution**: Ensure that even with direct injection, JsonObject is still properly registered with references when `@id` is present, and that `@ref` resolution works correctly.

### Challenge 7: Arrays Containing Nested Objects (SOLVED - Nov 29, 2025)

**Problem**: Arrays like `Object[] { String[], String[] }` were failing with ClassCastException because JsonObjects inside arrays were being directly injected without resolution.

**Root Cause**: `tryDirectFieldInjection()` was checking `value instanceof Object[]` and seeing that `Object[].class.isAssignableFrom(Object[].class)` is true, so it injected the array directly. But the array contained JsonObjects that needed Resolver processing.

**Solution** (in `JsonParser.tryDirectFieldInjection()`):
```java
// Arrays containing JsonObjects need Resolver - can't inject directly
if (value instanceof Object[]) {
    for (Object element : (Object[]) value) {
        if (element instanceof JsonObject || element instanceof Object[]) {
            return false;  // Contains nested objects/arrays that need resolution
        }
    }
}
```

### Challenge 8: Nested Objects Not Traversed After Direct Injection (SOLVED - Nov 29, 2025)

**Problem**: When a parent object used direct injection but a nested object's field fell back to JsonObject storage (because it couldn't be directly injected), that nested object was never traversed by the Resolver to populate its fields.

**Example**: Pet class with `nickNames` field. First Pet worked fine, but second Pet's nickNames was null.

**Root Cause**: In `extractValue()`, when a JsonObject had a target set, we returned the target immediately without pushing the JsonObject for traversal. If the object wasn't marked as finished (because some fields fell back to JsonObject), those fields were never processed.

**Solution** (in `ObjectResolver.extractValue()`):
```java
if (jObj.getTarget() != null) {
    if (!jObj.isFinished) {
        push(jObj);  // Queue for field resolution - CRITICAL!
    }
    return jObj.getTarget();
}
```

This ensures that any object with unprocessed fields gets traversed by the Resolver.

### Challenge 9: char[] Array Handling (SOLVED - Nov 29, 2025)

**Problem**: `char[]` arrays were failing with "Unable to parse 'a\t' as char" because they're serialized as a single String in JSON, not as an array of characters.

**Solution** (in `ObjectResolver.createAndPopulateArray()`):
```java
// Special handling for char[] - stored as a single String in JSON
if (componentType == char.class) {
    if (list.isEmpty()) return new char[0];
    Object first = list.get(0);
    if (first instanceof String) return ((String) first).toCharArray();
}
```

### Challenge 10: Enum Array Handling (SOLVED - Nov 29, 2025)

**Problem**: Enum arrays were failing with "array element type mismatch" because String values couldn't be set into enum array slots.

**Solution** (in `ObjectResolver.createAndPopulateArray()`):
```java
if (element != null && !componentType.isAssignableFrom(element.getClass())) {
    // Enum handling
    if (componentType.isEnum() && element instanceof String) {
        element = Enum.valueOf((Class<Enum>) componentType, (String) element);
    } else if (getConverter().isConversionSupportedFor(element.getClass(), componentType)) {
        element = getConverter().convert(element, componentType);
    }
}
```

## Recommended Incremental Approach

1. **Start conservative**: Only enable direct injection for "leaf" objects (simple POJOs with primitive/String fields, no nested objects)
2. **Add one capability at a time**: Each PR adds support for one more case (e.g., nested objects, then enums, then @id handling)
3. **Test after each increment**: Full test suite must pass before moving to next capability
4. **Measure performance**: Use JFR profiling to verify each change actually improves performance
5. **Preserve fallback**: Always have a clean fallback to JsonObject path when any edge case is detected

## Important Classes and Methods

### JsonParser (src/main/java/com/cedarsoftware/io/JsonParser.java)
- `readValue(Type)` - Main entry point, heap-based parsing loop
- `processObjectFrame(ParseFrame)` - Process one iteration of object parsing
- `pushObjectFrame(Type)` - Create and push new object frame

### Resolver (src/main/java/com/cedarsoftware/io/Resolver.java)
- `createInstance(JsonObject)` - Creates Java instance from JsonObject
- `getConverter()` - Returns the Converter for type conversions

### JsonObject (src/main/java/com/cedarsoftware/io/JsonObject.java)
- `setTarget(Object)` - Link to Java instance
- `getTarget()` - Get linked Java instance
- `setFinishedTarget(Object, boolean)` - Mark as finished
- `isFinished()` - Check if already processed

### Injector (src/main/java/com/cedarsoftware/io/reflect/Injector.java)
- `inject(Object target, Object value)` - Inject value into field
- `getType()` / `getGenericType()` - Get field type for conversion

### Converter (com.cedarsoftware.util.convert.Converter)
- `convert(Object source, Class<?> targetType)` - Convert between types

### ObjectResolver Array Handling Methods (Added Nov 29, 2025)

**`assignArrayToField(Object target, Injector injector, Type fieldType, Object[] elements)`**
- Entry point for assigning arrays to object fields
- Checks if all elements can be resolved immediately via `canResolveAllElements()`
- If resolvable: uses `resolveArray()` directly for immediate injection
- If not resolvable: wraps in JsonObject and pushes for Resolver traversal

**`canResolveAllElements(Object[] elements)`**
- Recursively checks if all array elements can be resolved without Resolver traversal
- Returns false if any element is:
  - A JsonObject with unresolved reference (`@ref` to non-existent object)
  - A JsonObject without a target (needs instance creation)
  - A nested array that itself contains unresolvable elements
- Returns true if all elements are primitives, resolved references, or already-created targets

**`createAndPopulateArray(Class<?> arrayType, List<Object> list)`**
- Creates a Java array from a list of elements with proper type handling
- Special cases:
  - `char[]`: Converts from single String (JSON serialization format)
  - Enum arrays: Converts String elements to enum values
  - Nested arrays: Recursively creates typed arrays
- Uses Converter for type coercion when element types don't match

**`extractValue(Object element)`**
- Extracts the actual Java value from an element (which may be a JsonObject wrapper)
- Handles:
  - `@ref` references: Returns the referenced target if resolved
  - JsonObjects with targets: Returns target, pushes for traversal if not finished
  - JsonObjects without targets: Creates instance and pushes for traversal
- **Critical**: Pushes unfinished objects for Resolver traversal to ensure all fields get processed

## java-util Utilities Reference

json-io leverages powerful utilities from java-util. Understanding these is essential for the direct injection implementation.

### Converter - The Power of Type Conversion

**CRITICAL**: Always use the `converter` instance from `resolver.getConverter()`, NOT static Converter methods. The instance holds custom conversions registered by the user.

The Converter supports **thousands** of built-in conversions including:
- Primitives ↔ Wrappers (int ↔ Integer, etc.)
- Numbers ↔ Numbers (Long → int, BigDecimal → double, etc.)
- Strings ↔ Everything (String → Date, UUID → String, etc.)
- Date/Time types (Date, Calendar, LocalDateTime, ZonedDateTime, Instant, etc.)
- Collections and Arrays (with deep conversion support)
- Map → Object (used for types like UUID, ZonedDateTime that serialize to Maps)

**Key Methods**:
```java
// Primary conversion API
T convert(Object from, Class<T> toType)

// Check if conversion is supported (important for eligibility checks!)
boolean isConversionSupportedFor(Class<?> source, Class<?> target)

// Check if it's a "simple" type conversion (not collection/array)
boolean isSimpleTypeConversionSupported(Class<?> source, Class<?> target)
```

**Important for Direct Injection**: Some types like `ZonedDateTime`, `UUID`, etc. use `Map → TargetType` conversion. Check `converter.isConversionSupportedFor(Map.class, targetClass)` - if true, these types need full JsonObject, NOT direct injection.

### ArrayConversions & CollectionConversions

These classes handle deep, unlimited nesting conversions:
- `arrayToArray(Object sourceArray, Class<?> targetArrayType, Converter)` - Deep array conversion with cycle detection
- `collectionToArray(Collection<?>, Class<?> arrayType, Converter)` - Collection → Array with cycles
- `arrayToCollection(Object array, Class<T> targetType)` - Array → Collection preserving characteristics
- `collectionToCollection(Collection<?>, Class<?> targetType)` - Collection → Collection with cycles

**Key Features**:
- Uses `IdentityHashMap` for cycle detection (handles circular references!)
- Heap-based iteration (no stack overflow on deep nesting)
- Preserves collection characteristics (Unmodifiable, Synchronized, etc.)
- Supports jagged multi-dimensional arrays

### ReflectionUtils

Provides cached reflection operations with security controls:
- `getMethod(Class, String, Class<?>...)` - Cached method lookup
- `getField(Class, String)` - Cached field lookup
- `getConstructor(Class, Class<?>...)` - Cached constructor lookup
- All operations use LRU caches (configurable size via `reflection.utils.cache.size`)

**Caching**: Uses `LRUCache` with thread-safe wrappers. Default cache size: 1500 entries.

### ClassUtilities

Provides class manipulation utilities with extensive caching:
- `forName(String, ClassLoader)` - Cached class loading with WeakReference to prevent leaks
- `isPrimitive(Class)` - Check if primitive or wrapper
- `toPrimitiveWrapperClass(Class)` - Convert primitive to wrapper (int.class → Integer.class)
- `computeInheritanceDistance(Class, Class)` - Calculate inheritance steps between classes
- `newInstance(Class, Object...)` - Create instance with varargs support
- `trySetAccessible(AccessibleObject)` - Safe accessibility setting for modules

**Important Caches**:
- `PRIMITIVE_TO_WRAPPER` / `WRAPPER_TO_PRIMITIVE` - Fast primitive/wrapper mapping
- `NAME_CACHE` - ClassLoader-scoped class name cache with WeakReferences
- `DIRECT_CLASS_MAPPING` - Direct instantiation for common types (Date, StringBuilder, etc.)

### Design Principle: Don't Reinvent

**Never write helper functions** that java-util already provides:
- Primitive handling → `ClassUtilities.isPrimitive()`, `toPrimitiveWrapperClass()`
- Type conversion → `Converter.convert()`
- Reflection → `ReflectionUtils.getField()`, `getMethod()`
- Class loading → `ClassUtilities.forName()`
- Array/Collection conversion → `ArrayConversions`, `CollectionConversions`

## Architecture Strategy

### The Performance Problem

Current flow (json-io is ~5x slower than Jackson):
```
JSON text → JsonParser → JsonObject graph → Resolver → Java Object graph
                              ↑
                      HashMap.put() for every field
                      HashMap.get() for every field read
                      Double memory allocation
```

### The Target Flow

When in `returnAsJavaObjects` mode:
```
JSON text → JsonParser → Java Object graph (directly)
                              ↑
                      field.set() / injector.inject() directly
                      Single memory allocation
                      No intermediate HashMap operations
```

### How Jackson Does It (Reference Architecture)

Jackson creates Java objects **immediately** during parsing - no intermediate tree:

1. **Direct Instance Creation**: `_valueInstantiator.createUsingDefault(ctxt)` creates the Java object as soon as the type is known
2. **Immediate Field Population**: `prop.deserializeAndSet(p, ctxt, bean)` - deserialize and inject in one step
3. **Lightweight Reference Tracking**: Uses `ReadableObjectId` with just 4 fields:
   - `Object _item` - the Java object
   - `IdKey _key` - the ID
   - `LinkedList<Referring> _referringProperties` - lazily created only when forward refs exist
   - `ObjectIdResolver _resolver`
4. **Forward Reference Resolution**: `Referring` objects hold minimal info to patch later

**Key insight**: Jackson does NOT build a parallel Map graph. The Java object IS the container.

### Existing json-io Infrastructure (Already Built!)

json-io already has the forward reference resolution pattern - it just uses JsonObject as the reference holder instead of the Java object directly.

#### UnresolvedReference (Resolver.java:101-118)
```java
static final class UnresolvedReference {
    private final JsonObject referencingObj;  // Could be: Object targetJavaObj
    private String field;                      // Field name to set
    private final long refId;                  // ID we're waiting for
    private int index = -1;                    // For array/collection refs
}
```

This is nearly identical to Jackson's `Referring` class! The only change needed: store the Java object directly instead of JsonObject.

#### patchUnresolvedReferences() (Resolver.java:904-939)
Already handles forward reference resolution at the end of parsing:
- Field references: Uses `injector.inject(objToFix, referencedTarget)`
- Array references: `((Object[]) objToFix)[ref.index] = referencedTarget`
- List references: `((List) objToFix).set(ref.index, referencedTarget)`
- Collection references: `((Collection) objToFix).add(referencedTarget)`

**This code already works with Java objects**, it just gets them via `ref.referencingObj.getTarget()`.

#### ReferenceTracker (ReferenceTracker.java)
```java
public interface ReferenceTracker {
    JsonObject put(Long l, JsonObject o);
    JsonObject get(Long id);
    JsonObject getOrThrow(Long id);
}
```

**Opportunity**: For direct injection mode, this could map `Long id → Object` (Java object) instead of `Long id → JsonObject`.

### Why This Is Hard

1. **JsonObject serves multiple purposes**:
   - Holds `@id` for reference tracking
   - Holds `@type` for polymorphic handling
   - Holds `@ref` for forward reference resolution
   - Holds fields until instance can be created
   - Acts as Map when in "maps mode"

2. **Some types need all fields before creation**:
   - `BigDecimal` - needs "value" field
   - Types with `Map → Type` converter support
   - Types with custom ClassFactory

3. **References create ordering dependencies**:
   - `@ref` may point to object not yet created (forward reference)
   - Cycles require careful handling

### The Mental Model

Imagine weaving a fabric of Java objects into a graph:
1. **Create Java objects immediately** when type is known
2. **Inject fields directly** as they are parsed
3. **Track minimal metadata** separately (ID, forward refs) - NOT in JsonObjects
4. **Patch forward references** at the end using existing `patchUnresolvedReferences()`

The Java object IS the holder. We don't need a parallel JsonObject graph.

### Proposed Lightweight Tracking (Instead of JsonObject)

```java
// Map ID → Java object (not JsonObject)
Map<Long, Object> idToJavaObject = new HashMap<>();

// Only for forward references (rare case)
class PendingRef {
    Object targetJavaObject;  // Object that needs a field set
    Injector injector;        // How to inject (or int index for arrays)
    long refId;               // What ID we're waiting for
}
List<PendingRef> pendingRefs = new ArrayList<>();  // Usually empty!
```

For 95%+ of JSON, `pendingRefs` will be empty because forward references are rare.

### Instrumentation Strategy

Add lightweight counters to track progress:
```java
// In JsonParser
static AtomicLong directInjectionAttempts = new AtomicLong();
static AtomicLong directInjectionSuccess = new AtomicLong();
static AtomicLong jsonObjectFallback = new AtomicLong();
```

This lets us see: "Of 1000 objects parsed, 800 used direct injection, 200 fell back to JsonObject."

## Proposed Approach: Refactor First, Then Implement

### Phase 1: Clean Up JsonParser (No Behavior Change) ✅ COMPLETED

JsonParser has been refactored with named behavior methods (commit `3d3c8554`):

**Object Frame Processing** (`processObjectFrame`):
- `handleChildResultForObject()` - Process nested value from child frame
- `readAndResolveFieldName()` - Read field name and resolve @t → @type aliases
- `resolveFieldType()` - Get generic type for field from injectors
- `readFieldValueInlined()` - Read value with inlined primitive dispatch (performance)
- `storeFieldValue()` - Store in JsonObject with meta-field handling
- `checkObjectContinuation()` - Check for ',' or '}' after field

**Array Frame Processing** (`processArrayFrame`):
- `handleChildResultForArray()` - Process nested value from child frame
- `handleEmptyArrayMarker()` - Handle empty array marker
- `handlePrimitiveArrayElement()` - Add primitive and check continuation
- `pushNestedContainerFrame()` - Push child frame for nested container
- `completeArray()` - Finalize array via resolver

**Meta-Field Processing** (`processFieldValue`):
- Handles @type, @id, @ref, @items, @keys, @enum directly in switch (no wrapper methods)
- Fast path for regular fields (95%+ don't start with '@')

**Performance**: No regression - processObjectFrame samples reduced 22% from baseline.

### Phase 2: Add Direct Injection (Incremental)

With clean code structure, add direct injection incrementally:

1. **Step 1: Leaf POJOs only**
   - Simple objects with primitive/String fields
   - No nested objects, no @id/@ref
   - Add instrumentation to count success rate

2. **Step 2: Add nested object support**
   - Child objects also use direct injection when eligible
   - Parent waits for child to complete before injecting

3. **Step 3: Add @id support**
   - Register object in references after creation
   - Still use direct injection for fields

4. **Step 4: Handle edge cases**
   - Unknown fields → store in JsonObject for MissingFieldHandler
   - Custom readers → disable direct injection for those types
   - Map→Type converters → disable direct injection

### Phase 3: Optimize

Once correct, optimize:
- Reduce allocations
- Inline hot paths
- Profile with JFR to find remaining bottlenecks

## Next Steps

1. ~~**Refactor JsonParser** to use named behaviors (dispatcher pattern)~~ ✅ DONE
2. Add ParseFrame fields for direct injection (`target`, `directInjection`)
3. Add eligibility check method `canDirectInject(Type)` with conservative rules:
   - Has a known type (from suggestedType or @type)
   - NOT a custom reader type
   - NOT a Map→Type converter type
   - NOT an interface/abstract class without @type
4. Modify `storeFieldValue()` to inject directly when `frame.directInjection == true`
5. Handle @id: Register Java object in `idToJavaObject` map
6. Handle @ref: Check `idToJavaObject` first, fall back to `UnresolvedReference` for forward refs
7. Adapt `ReferenceTracker` or create parallel tracking for Java objects
8. Run full test suite, measure performance with JFR
9. Incrementally add support for more cases (nested objects, collections, etc.)

## Unified Approach: Maps Mode and Java Mode

### The Goal: Same Process, Different Target Objects

Both modes should follow the **same construction process** during parsing:

| Aspect | Maps Mode | Java Mode |
|--------|-----------|-----------|
| Target object | JsonObject | Java POJO |
| Field storage | `jsonObject.put(field, value)` | `injector.inject(target, value)` |
| ID tracking | `idMap.put(id, jsonObject)` | `idMap.put(id, javaObject)` |
| Reference resolution | Same `@ref` lookup | Same `@ref` lookup |
| Forward refs | Same `UnresolvedReference` | Same `UnresolvedReference` |

### Current Problem: Re-walking the Graph

Currently, both MapResolver and ObjectResolver start at the root and re-walk the entire graph:
```
JsonParser creates JsonObject graph
         ↓
MapResolver/ObjectResolver walks from root
         ↓
Visits every node again to "resolve" it
```

This is wasteful - we already visited every node during parsing!

### Target: Finish During Parsing

The parser should **finish** each object as it completes parsing:

```
JsonParser reads '{'
    → Creates target (JsonObject or Java object)
    → Registers ID if @id present
    → For each field: store/inject value directly
    → Resolves @ref immediately if target exists, else queue UnresolvedReference
    → When '}' reached: object is FINISHED
         ↓
At end of parse: patch any forward references
         ↓
Return root object (already fully resolved)
```

### MapResolver Should Become a No-Op

If the parser finishes JsonObjects during parsing:
- `@type` is already set on JsonObject
- `@id` is already registered in reference map
- `@ref` is already resolved (or queued for patching)
- All fields are already populated
- Nested objects are already finished

MapResolver would only need to:
1. Patch forward references (rare case)
2. Return the root

### Code Similarity Goal

The processing logic should be nearly identical:

```java
// Unified approach - pseudocode
void processField(Frame frame, String field, Object value) {
    if (field.equals("@id")) {
        idMap.put(value, frame.target);  // target is JsonObject OR Java object
        return;
    }

    if (field.equals("@ref")) {
        Object referenced = idMap.get(value);
        if (referenced != null) {
            // Immediate resolution
            return referenced;
        } else {
            // Forward reference - queue for later
            unresolvedRefs.add(new UnresolvedReference(frame.target, field, refId));
            return UNRESOLVED_MARKER;
        }
    }

    // Regular field - store in target
    if (frame.target instanceof JsonObject) {
        ((JsonObject) frame.target).put(field, value);
    } else {
        Injector injector = getInjector(frame.target.getClass(), field);
        if (injector != null) {
            injector.inject(frame.target, value);
        }
    }
}
```

### Benefits of Unified Approach

1. **Single code path** - Less code to maintain, fewer bugs
2. **No graph re-walking** - Everything resolved during parsing
3. **Consistent behavior** - Maps and Java modes behave identically
4. **Simpler Resolvers** - MapResolver/ObjectResolver become thin wrappers
5. **Better performance** - Single pass through the data

### Implementation Strategy

1. **First**: Get Java mode working with direct injection
2. **Then**: Apply same pattern to Maps mode (finish JsonObjects during parsing)
3. **Finally**: Unify the code paths where possible

The key insight: **The parser knows everything it needs to finish objects**. The Resolver re-walk is redundant work that can be eliminated for both modes.

## Current State (Nov 30, 2025)

### Latest Progress (Nov 30, 2025)

**@ref Resolution in Parser**:
- Parser now resolves @ref immediately using ReferenceTracker
- Back-references (99% of cases) resolve during parsing - no Resolver needed
- Only forward and circular references need post-parse patching
- `tryResolveReference(Long refId)` handles immediate resolution
- All @ref JsonObjects properly marked as `finished` when resolved

**Collection @items Type Propagation Fix**:
- Fixed: `resolveFieldType()` now extracts element type from Collections for @items field
- Before: `List<NestedData>` @items → null element type → JsonWriter needed @type on elements
- After: `List<NestedData>` @items → `NestedData` element type propagated
- Increased successful type resolution from 38.9% to 39.3%

**Understanding Where Null Types Come From** (Important!):
The remaining "null/Object type" cases are NOT bugs - they're legitimate:

| Source | % | Why |
|--------|---|-----|
| From array element | 78% | `Map<String, Object>` values - declared as `Object`, needs @type |
| From field (Object) | 22% | Fields declared as `Object` - can't know type without @type |

**Key insight**: When Java declares `Object`, we CANNOT know the type at parse time without @type in JSON. JsonWriter correctly emits @type, Parser correctly defers to Resolver. This is working as designed.

### JsonWriter Field Ordering (Critical for Direct Injection)

**Guaranteed order**: @id → @type → regular fields

This is essential because:
1. @id must be registered FIRST (so other objects can reference it)
2. @type must come BEFORE fields (so we know what instance to create)
3. Regular fields can then be injected into the correct instance



### What's Been Implemented

**Slice 1-3: Deferred Instance Creation, @id Support, and Array Handling**

The parser now has infrastructure for direct injection with proper type handling:

1. **Two-phase activation** in `ParseFrame`:
   - `directInjectionPending` - eligible but waiting to see @type
   - `directInjection` - active, instance created
   - `target` - the Java object being populated
   - `allFieldsInjected` - tracks if object can be marked finished

2. **Type resolution before instance creation**:
   - `pushObjectFrame()` marks eligibility but does NOT create instance
   - `storeFieldValue()` determines final type when first field is seen:
     - If `@type` present → use @type (it's always the concrete truth)
     - If regular field seen (no @type) → use suggestedType
     - If `@id` seen → continue pending (doesn't affect type)
   - `activateDirectInjection()` creates instance with correct type

3. **Array handling improvements** (Nov 29, 2025):
   - `assignArrayToField()` checks if elements can be resolved immediately via `canResolveAllElements()`
   - If all elements resolvable → use `resolveArray()` directly
   - If any element is unresolved JsonObject → fall back to Resolver traversal
   - `createAndPopulateArray()` handles special cases:
     - `char[]` arrays (serialized as single String in JSON)
     - Enum arrays (String → Enum conversion)
     - Nested typed arrays (recursive handling)
   - `extractValue()` now pushes unfinished objects for traversal when they have targets

4. **Direct injection safety** in `tryDirectFieldInjection()`:
   - Prevents injection of arrays containing JsonObjects (need Resolver)
   - Prevents injection of arrays containing nested arrays with JsonObjects
   - Simple primitive arrays are injected directly

5. **Collection handling in Parser** (Nov 29, 2025 evening):
   - `tryCreateCollection()` creates ArrayList, HashSet, LinkedHashSet, etc. directly
   - Uses `InstanceCreator` for interface → concrete type resolution (List → ArrayList)
   - Handles element type conversion via Converter
   - Falls back to Resolver for ClassFactory types

6. **Map handling in Parser** (Nov 29, 2025 evening):
   - `tryCreateMapDirectly()` creates LinkedHashMap, TreeMap, etc. for JSON objects with string keys
   - Checks for `@keys`/`@items`, `@type`, `@id`, `@ref` to fall back appropriately
   - Handles key and value type conversion
   - Nested finished JsonObjects extracted to targets before populating map
   - `extractMapKeyType()` helper extracts key type from `Map<K,V>` generic signature

7. **Cascading completion** (Nov 29, 2025):
   - `checkObjectContinuation()` returns Java object directly when all fields injected
   - Parent frames receive Java objects instead of JsonObjects for finished children
   - `completeArray()` tries direct handling before falling back to Resolver
   - Enables elimination of Resolver traversal for complete object subgraphs

### Performance Profile (Nov 29, 2025 21:15)

Latest JFR analysis (500k iterations, simple POJOs with nested objects, List, Map):

| Component | Samples | Notes |
|-----------|---------|-------|
| JsonParser | 128 | Dominant (as expected) |
| Resolver | 52 | Still used for @ref resolution |
| JsonReader | 18 | Initialization overhead |
| Converter | 37 | Type conversion checks |

**Top methods in parser:**
- `processObjectFrame()` - 19 samples
- `readValue()` - 15 samples
- `pushObjectFrame()` - 10 samples
- `resolveFieldType()` - 9 samples

**Key observations:**
- Parser dominates as expected
- Resolver samples are for @ref handling (references in test data)
- Collection/Map creation adds minimal overhead
- With simple data (no @ref), throughput: ~125k ops/sec

**Comparison with earlier profiles:**
- Resolver relative usage dropped from ~13% to ~14% of cedarsoftware samples
- Direct injection, Collection, and Map handling allow Parser to bypass Resolver for most objects

### Why Most Objects Still Use Resolver Path

The current `canDirectInject()` rejects objects when:
- **Null/Object type (~95%)**: Map entries with `Object` value type - can't know what to instantiate
- **Interface/abstract (~3.5%)**: Types like `List`, `Map` that need concrete class resolution
- **Custom reader/factory**: Types with special instantiation logic

**Key insight**: The Resolver's `createInstance()` method handles all these cases. It knows:
- `List` → `ArrayList`
- `Map` → `LinkedHashMap`
- ClassFactory types → delegate to factory
- Converter types → use Converter

## Target Architecture: Parser-Side Instance Creation

### The Vision

```
CURRENT (Resolver creates instances):
JSON text → JsonParser → JsonObject graph → Resolver.createInstance() → Java Objects
                                                      ↓
                                          Resolver.traverseFields() injects values

TARGET (Parser creates instances):
JSON text → JsonParser → Java Objects (directly)
                ↓
    createInstance() called during parsing
    Fields injected as they're parsed
    Only forward refs need post-parse patching
```

### Two Modes, Same Process

The parser should support both modes with the same basic flow:

| Aspect | Java Mode | Maps Mode |
|--------|-----------|-----------|
| Instance creation | `createJavaInstance(type, suggestedType)` | `createMapInstance()` → JsonObject |
| Field storage | `injector.inject(target, value)` | `jsonObject.put(field, value)` |
| ID tracking | `idMap.put(id, javaObject)` | `idMap.put(id, jsonObject)` |
| Reference resolution | Same @ref lookup | Same @ref lookup |
| Forward refs | Same `UnresolvedReference` patching | Same patching |

### New Instance Creation API (To Be Built)

Instead of Resolver's `createInstance(JsonObject)`, we need parser-friendly methods:

```java
/**
 * Create a Java instance during parsing.
 *
 * @param concreteType The @type from JSON, or null if not present
 * @param suggestedType The type from parent's field declaration (carries generics)
 * @param readOptions For ClassFactory lookup, coercion rules, etc.
 * @return The created instance, or null if creation not possible
 */
Object createJavaInstance(Class<?> concreteType, Type suggestedType, ReadOptions readOptions);

/**
 * Create a JsonObject for Maps mode.
 * Sets up the type information for later use.
 */
JsonObject createMapInstance(Class<?> concreteType, Type suggestedType);
```

**Key differences from Resolver.createInstance()**:
- No JsonObject as parameter - we're creating it, not reading from it
- Separate `concreteType` (from @type) and `suggestedType` (from parent field)
- Returns the instance; caller handles linking/tracking

### What createJavaInstance() Must Handle

Extracted from Resolver.createInstance() analysis:

1. **Simple type conversion** (String → Date, Number → BigDecimal, etc.)
   - `converter.isSimpleTypeConversionSupported(targetType)`
   - These are "finished" immediately, no field injection needed

2. **Interface/abstract → concrete class**
   - `List` → `ArrayList`
   - `Map` → `LinkedHashMap`
   - `Set` → `LinkedHashSet`
   - Uses `DEFAULT_INSTANTIATORS` map or coercion rules

3. **ClassFactory types**
   - `readOptions.getClassFactory(type)` returns factory
   - Factory may fully populate the object (`isObjectFinal()`)
   - Examples: EnumSet, certain collection types

4. **Enum handling**
   - `ClassUtilities.getClassIfEnum(type)`
   - May need coercion to different enum class

5. **Array creation**
   - `Array.newInstance(componentType, size)`
   - Size known from @items or must be determined

6. **Regular classes**
   - `ClassUtilities.newInstance(converter, targetClass, null)`
   - Default constructor or Unsafe instantiation

### Post-Parse Steps (What Remains)

After parsing completes, only these steps should remain:

1. **Patch forward references** (`patchUnresolvedReferences()`)
   - For @ref that pointed to not-yet-created objects
   - Uses `UnresolvedReference` list built during parsing
   - Already works with Java objects, not just JsonObjects

2. **Rehash Maps** (if needed)
   - Maps with mutable keys may need rehashing
   - After all objects are fully constructed

3. **Nothing else** - no graph walking, no instance creation, no field injection

## Implementation Path: Option B

### Phase 1: Build createJavaInstance() ✅ NEXT

Build a clean instance creation API in JsonParser:

1. **Start simple**: Handle concrete classes with default constructors
2. **Add interface resolution**: List→ArrayList, Map→LinkedHashMap, etc.
3. **Add ClassFactory support**: Delegate to factories when registered
4. **Add Converter support**: Simple type conversions
5. **Add array support**: Create arrays when size is known

Key design principles:
- No JsonObject parameter - create from type info only
- Clear separation of concreteType vs suggestedType
- Return null if can't create (caller falls back to Resolver path)

### Phase 2: Integrate with Parsing Loop

Replace `activateDirectInjection()` logic:
```java
private void activateDirectInjection(ParseFrame frame, Class<?> typeFromJson) {
    Class<?> concreteType = typeFromJson;  // from @type, or null
    Type suggestedType = frame.suggestedType;  // from parent field

    Object target = createJavaInstance(concreteType, suggestedType, readOptions);
    if (target != null) {
        frame.target = target;
        frame.directInjection = true;
        // ... rest of activation
    }
}
```

### Phase 3: Field Injection During Parsing

Once instance creation works for most cases:
1. Inject primitive fields directly (String, int, boolean, etc.)
2. Inject nested objects as they complete
3. Handle @ref (immediate if target exists, else queue UnresolvedReference)

### Phase 4: Maps Mode Unification

Apply same pattern to Maps mode:
1. `createMapInstance()` creates JsonObject with type info
2. Fields stored via `jsonObject.put()`
3. Same @id/@ref handling
4. Result: MapResolver becomes nearly empty

## Historical Session Notes (Nov 29, 2025)

> **NOTE**: See "Session Restart Checklist (Updated Nov 30, 2025)" at the end of this document for the most current checklist.

### Implementation Progress (Nov 29)

**Nested object direct return** - Key breakthrough for Resolver bypass:
- `checkObjectContinuation()` now returns `frame.target` (Java object) when `allFieldsInjected`
- `handleChildResultForObject()` delegates to `checkObjectContinuation()` (removed duplicate logic)
- `tryDirectFieldInjection()` already handles non-JsonObject values via type compatibility check

**Cascading completion**: When child is finished, parent can inject it directly
- Leaf objects (primitives only) → marked finished, return target
- Parent objects with finished children → inject children, marked finished
- This cascades up the tree - entire subtrees bypass Resolver!

**Key findings from type propagation investigation**:
- Type propagation IS working correctly for typed generics
- Primitives (JSON basic types) work perfectly
- The "ineligible null type" cases are legitimate (Object-typed fields)
- ClassFactory types (ArrayList, HashMap) cause "ineligible" counts - expected

**What bypasses Resolver** (as of Nov 29):
- Simple POJOs with only primitive/String fields ✓
- Nested POJOs where all children are finished ✓
- Objects with type info from suggestedType (field declarations) ✓
- Typed collections/arrays with primitive elements ✓

9. **JsonWriter @type elimination optimization** (Nov 30, 2025):

   **Opportunity Identified**:
   JsonWriter was emitting `@type` on Collection/Map elements even when the element type is known
   from the field's generic declaration. For example, `List<NestedData>` would emit:
   ```json
   "nestedList": { "@type":"ArrayList", "@items":[
     {"@type":"com.example.NestedData", "name":"foo", "value":1},  // @type was redundant!
     {"@type":"com.example.NestedData", "name":"bar", "value":2}
   ]}
   ```
   Since the field declares `List<NestedData>`, the `@type` on elements is redundant.

   **Implementation (ENABLED)**:
   - Added `declaredElementType` field to track element type from field generics
   - Modified `writeField()` to extract element type using `TypeUtilities.inferElementType()`
   - Added `shouldShowTypeForElement()` method for the optimization logic
   - Modified `writeCollectionElement()` to use `shouldShowTypeForElement()`

   **Key Design Decisions**:
   1. **Exact match only** (per user guidance): Element type comparison uses `==`:
      ```java
      return elementClass != declaredElementType;  // Exact match only!
      ```
      This ensures JsonReader instantiates the exact concrete type, not a subclass.

   2. **Enums always show @type**: Enums are serialized as Strings, need @type to convert back.

   3. **Throwables excluded**: Fields on Throwable subclasses always include @type because
      `ThrowableFactory` uses `ClassFactory` which relies on `JsonObject.getRawType()` to
      determine conversion types. Without @type, ThrowableFactory can't convert elements properly.

   **Parser Already Supports This**:
   Initially thought Parser didn't propagate element types, but investigation proved it does:
   - `pushArrayFrame(elementType)` at line 848 receives element type from field's generic
   - `pushNestedContainerFrame()` passes this to `pushObjectFrame(suggestedType)` for nested objects
   - Test confirms: JSON without @type on elements correctly creates typed instances

   **Result**:
   - JSON output is shorter (fewer redundant @type declarations)
   - Round-trip serialization still works correctly
   - All 2120 tests pass

10. **Map key/value @type elimination** (Nov 30, 2025):

    **Extension of Collection optimization**:
    Extended the @type elimination to Maps with non-String keys. Maps with non-String keys are
    written as `@keys`/`@items` arrays. When the field declares the Map's key and value types
    (e.g., `Map<Building, Person>`), both arrays can omit @type on their elements.

    **Example**:
    ```java
    class Container {
        Map<KeyType, ValueType> complexMap;  // Both key and value types are known!
    }
    ```
    Before:
    ```json
    "complexMap": {
      "@type":"LinkedHashMap",
      "@keys":[{"@type":"com.example.KeyType", "id":1}],     // @type was redundant
      "@items":[{"@type":"com.example.ValueType", "name":"foo"}]  // @type was redundant
    }
    ```
    After:
    ```json
    "complexMap": {
      "@type":"LinkedHashMap",
      "@keys":[{"id":1}],        // No @type - key type known from field generic
      "@items":[{"name":"foo"}]  // No @type - value type known from field generic
    }
    ```

    **Implementation**:
    - Added `declaredKeyType` field to track Map key type from field generics
    - Added `extractMapKeyType()` method to extract key type from ParameterizedType (index 0)
    - Modified `writeField()` to set both `declaredKeyType` and `declaredElementType` for Maps
    - Modified `writeMapToEnd()` to context-switch: set `declaredElementType=declaredKeyType`
      when writing @keys, then restore to value type when writing @items
    - Map values with String keys (using `writeMapBody()`) already use `writeCollectionElement()`
      which respects `declaredElementType`, so values were already optimized

    **Result**:
    - Maps with non-String keys now have shorter JSON output
    - Both @keys and @items arrays benefit from type elimination
    - All 2120 tests pass

11. **ClassFactory Invocation in Parser - Analysis** (Nov 30, 2025):

    **Goal**: Invoke ClassFactory during parsing to create instances immediately, bypassing Resolver.

    **Challenge Discovered**:
    ClassFactories have different contracts regarding nested elements:

    **Safe for Parser invocation** (only read primitive/String values from JsonObject):
    - `EnumClassFactory` - reads `jObj.get("name")` or `jObj.getValue()` (String)
    - `StackTraceElementFactory` - reads primitive string/int fields

    **Unsafe for Parser invocation** (process nested JsonObjects that may be unresolved):
    - `ArrayFactory` - iterates `jObj.getItems()` which may contain JsonObjects
    - `CollectionFactory` - same issue with @items
    - `MapFactory` - same issue with @keys/@items
    - `RecordFactory` - reads fields that may be JsonObjects needing resolution
    - `ThrowableFactory` - has stacktrace/cause which may be JsonObjects

    **The Problem**:
    When Parser invokes a factory like `ArrayFactory`:
    1. Parser calls `arrayFactory.newInstance(int[].class, jsonObj, resolver)`
    2. ArrayFactory gets `jsonObj.getItems()` = `[JsonObject, JsonObject, ...]`
    3. ArrayFactory tries `converter.convert(item, int.class)`
    4. **Fails**: The items are still JsonObjects, not resolved Java values!

    **Solution Options**:

    A. **Interface Enhancement** (recommended for future):
       Add `boolean canInvokeAtParseTime()` to `ClassFactory` interface:
       ```java
       default boolean canInvokeAtParseTime() {
           return false;  // Default: requires full Resolver context
       }
       ```
       Factories that only read primitive values can return `true`.

    B. **Whitelist Specific Factories** (quick win):
       In Parser, check if factory is one of the known-safe types:
       ```java
       if (classFactory instanceof EnumClassFactory ||
           classFactory instanceof StackTraceElementFactory) {
           // Safe to invoke at parse time
       }
       ```

    C. **Check JsonObject Contents** (complex, fragile):
       Before invoking factory, check if all nested values are primitives:
       ```java
       if (jsonObj.allValuesArePrimitivesOrStrings()) {
           // Safe to invoke factory
       }
       ```
       This is fragile because different factories read different fields.

    **Current Decision**:
    Defer ClassFactory-at-parse-time optimization. The existing optimizations (Map creation,
    @ref resolution, @type elimination) provide significant gains without the complexity.
    ClassFactory invocation can be added later with proper interface changes.

    **Alternative Already Working**:
    Maps are already created directly in Parser via `tryCreateMapDirectly()`, which handles
    the common case where JSON represents a Map type. This doesn't need ClassFactory at all.

12. **Simple Value Conversion in Parser** (Nov 30, 2025):

    **Optimization Added**:
    Added `trySimpleValueConversion()` to convert value-based objects directly in Parser.

    **Pattern Handled**:
    Types serialized as `{"@type":"...", "value": ...}`:
    - Integer, Long, Double, Float, BigDecimal, BigInteger
    - Date, UUID, LocalDateTime, ZonedDateTime
    - And other types where Converter supports String/Number → TargetType

    **Example**:
    ```json
    {"@type":"java.lang.Integer", "value":42}
    {"@type":"java.util.Date", "value":1699123456789}
    {"@type":"java.util.UUID", "value":"550e8400-e29b-41d4-a716-446655440000"}
    ```

    **Constraints**:
    - Only applies when `readOptions.isReturningJavaObjects()` = true
    - Requires `jsonObj.hasValue()` = true (exactly one field named "value")
    - Uses `converter.isSimpleTypeConversionSupported(sourceType, targetType)`
    - Excludes `Class` type (needs Resolver's specific error handling)

    **Integration Point**:
    Called in `checkObjectContinuation()` after Map creation attempt:
    ```java
    Object converted = trySimpleValueConversion(frame.jsonObject);
    if (converted != null) {
        return converted;  // Return converted value directly!
    }
    ```

    **Result**:
    Simple value types converted directly in Parser, bypassing Resolver traversal.
    All 2120 tests pass.

13. **DEFAULT_INSTANTIATORS Removal from Resolver** (Nov 30, 2025):

    **Major Milestone**:
    Successfully removed the `DEFAULT_INSTANTIATORS` block from `Resolver.createInstance()`.
    This was a ~20 line block that handled simple instantiation of common Map and Collection types.

    **What Was Changed**:

    1. **CompactMapFactory.java** - Handle simple CompactMap (no builder config):
       ```java
       if (configStr == null) {
           // Simple CompactMap without builder pattern config - create default instance
           CompactMap<Object, Object> cmap = new CompactMap<>();
           jObj.setTarget(cmap);
           return cmap;
       }
       ```
       Before: Threw `JsonIoException("CompactMap requires a config string")`
       After: Returns `new CompactMap<>()` for simple usage

    2. **CompactSetFactory.java** - Same pattern for CompactSet

    3. **InstanceCreator.java** - Added static methods for Resolver access:
       ```java
       public static boolean isSimpleInstantiable(Class<?> clazz)
       public static Object createSimpleInstanceStatic(Class<?> clazz)
       ```
       These allow Resolver to use the `SIMPLE_INSTANTIABLE` map directly.

    4. **Resolver.createInstance()** - Added simple instantiation check:
       ```java
       // Handle common Map and Collection types with simple instantiation.
       // This avoids falling through to createInstanceUsingType() which calls
       // ClassUtilities.newInstance() with the JsonObject, which can fail for
       // Maps with non-String keys (e.g., @keys: [1, 2, 3]).
       if (InstanceCreator.isSimpleInstantiable(targetType)) {
           Object instance = InstanceCreator.createSimpleInstanceStatic(targetType);
           if (instance != null) {
               return jsonObj.setTarget(instance);
           }
       }
       ```

    **Key Discovery - Why createInstanceUsingType() Was Failing**:
    The `createInstanceUsingType()` method calls `ClassUtilities.newInstance(converter, targetClass, jsonObj)`.
    This `ClassUtilities.newInstance()` examines the JsonObject's `@keys` array and expects String keys.
    When Maps have non-String keys (Long, Boolean, etc.), it throws ClassCastException:
    ```
    java.lang.Long cannot be cast to class java.lang.String
    ```

    **The Fix**: Add a check for `SIMPLE_INSTANTIABLE` types BEFORE falling through to
    `createInstanceUsingType()`. Simple instantiation just calls `new LinkedHashMap<>()` etc.,
    bypassing the problematic `ClassUtilities.newInstance(jsonObj)` path entirely.

    **Types in SIMPLE_INSTANTIABLE**:
    - Collections: ArrayList, LinkedList, HashSet, LinkedHashSet, TreeSet, ArrayDeque, etc.
    - Maps: HashMap, LinkedHashMap, TreeMap, ConcurrentHashMap, etc.
    - Cedar types: CompactMap, CompactSet, CompactLinkedMap, CompactLinkedSet, etc.

    **Result**:
    - All 2120 tests pass
    - Resolver.createInstance() is now smaller and cleaner
    - CompactMap/CompactSet work correctly in both simple and builder-pattern modes

14. **JFR Profile Analysis - Parser:Resolver Ratio Milestone** (Nov 30, 2025):

    **Latest Profile Results** (`/tmp/jfr_with_refs.jfr`):

    | Metric | Value |
    |--------|-------|
    | Total cedarsoftware Samples | 3,740 |
    | Parser Samples | 1,290 |
    | Resolver Samples | 290 |
    | JsonWriter Samples | 384 |
    | **Parser:Resolver Ratio** | **4.4:1** |

    **Historical Progression**:
    - Initial: ~1.0:1 (Parser and Resolver equal)
    - After early optimizations: ~1.3:1
    - After Map/Collection handling: ~1.6:1
    - **Current (Nov 30)**: **4.4:1** ← Major improvement!

    **Resolver Methods Breakdown** (290 total samples):
    | Samples | Method |
    |---------|--------|
    | 56 | `ObjectResolver.readWithFactoryIfExists` |
    | 47 | `ObjectResolver.processArrayElement` |
    | 27 | `Resolver.traverseSpecificType` |
    | 24 | `ObjectResolver.traverseArray` |
    | 23 | `Resolver.createInstance` |
    | 15 | `ObjectResolver.processRegularObjectInCollection` |
    | 12 | `Resolver.rehashMaps` |

    **Key Insight**: `createInstance` is now only 23 samples (8% of Resolver samples).
    Most Resolver work is now array/collection traversal and factory handling, not instance creation.

    **Parser Top Methods** (1,290 total samples):
    | Samples | Method |
    |---------|--------|
    | 254 | `readValue` |
    | 249 | `processObjectFrame` |
    | 87 | `readFieldValueInlined` |
    | 84 | `readString` |
    | 79 | `storeFieldValue` |
    | 68 | `readFieldName` |

    **Observations**:
    - Parser is doing the heavy lifting as intended
    - String parsing (`readString`, `FastReader.extractStringCached`) is significant
    - Field name handling is efficient (~6% of Parser samples)
    - The work distribution is now much closer to optimal

15. **Map→Type Conversion: Parser vs Resolver Handling** (Dec 1, 2025):

    **Problem**: Types like YearMonth, ZonedDateTime, UUID are serialized as Maps:
    ```json
    {"yearMonth":"1970-06"}           // YearMonth
    {"mostSigBits":..., "leastSigBits":...}  // UUID
    {"zoned":"2024-01-01T..."}        // ZonedDateTime
    ```

    When these appear as nested objects WITHOUT `@type`, the Parser needs to convert them.

    **Two Conversion APIs in java-util Converter**:

    | API | Use Case | Example |
    |-----|----------|---------|
    | `isSimpleTypeConversionSupported(Class)` | Checks if Type→Type exists | YearMonth→YearMonth = true |
    | `isConversionSupportedFor(Map.class, Class)` | Checks if Map→Type exists | Map→YearMonth = true |

    **How MapConversions Works** (java-util):

    Each converter (toYearMonth, toUUID, etc.) extracts keys from the Map:
    ```java
    // For YearMonth, looks for keys: "yearMonth", "value", "_v"
    private static final String[] YEAR_MONTH_KEYS = {YEAR_MONTH, VALUE, V};
    static YearMonth toYearMonth(Object from, Converter converter) {
        return dispatch(from, converter, YearMonth.class, YEAR_MONTH_KEYS);
    }
    ```

    The `dispatch()` method extracts the value and recursively converts it (String→YearMonth).

    **Current Architecture**:

    1. **Parser's `tryDirectObjectConversion()`** (JsonParser.java:1439-1495):
       - Checks: `converter.isConversionSupportedFor(Map.class, targetClass)`
       - Works when type is known (from `@type` or `suggestedType`)
       - Handles ~41.4% of objects (those with "good type" in Parser stats)
       - Excludes: Class, Throwable, arrays, collections, maps, java.sql.* types

    2. **Resolver's fallback** (Resolver.java:686-694):
       - Checks: `converter.isSimpleTypeConversionSupported(targetType)`
       - Works when Resolver resolves the type (from context or JsonObject inspection)
       - Handles objects where Parser didn't have type info

    **Why Parser Doesn't Always Handle It**:

    Parser stats show: 42.9% null type + 15.7% Object.class = 58.6% need Resolver type resolution.

    Reasons for null/Object type:
    - Field declared as `Object` (75.6% of null-type cases)
    - Array element where array has no generic info (24.4%)
    - Root object with no suggestedType

    **Result**: All 2120 tests pass. Both paths work correctly:
    - Parser handles conversions when it has type info
    - Resolver handles conversions when Parser lacks type info

    **Key Insight**: The Resolver's `isSimpleTypeConversionSupported` check is necessary
    as a fallback because Parser can't always know the target type. However, as type
    propagation improves, more conversions will shift to Parser.

## Session Restart Checklist (Updated Dec 1, 2025)

When starting a new session on this work:

1. **Read these docs first**:
   - `/docs/DIRECT_INJECTION_IMPLEMENTATION.md` (this file) - especially "Current State" section
   - `/docs/json-io-architecture.md`
   - `/.claude/claude.md`

2. **Current state** (Dec 1, 2025):
   - All 2120 tests passing
   - Parser:Resolver ratio is 4.4:1 (major milestone!)
   - DEFAULT_INSTANTIATORS removed from Resolver
   - CompactMap/CompactSet factories handle simple case (no config)
   - InstanceCreator has static methods for simple instantiation
   - Map→Type conversion works in both Parser (`isConversionSupportedFor`) and Resolver (`isSimpleTypeConversionSupported`)

3. **Recent commits** (Nov 30 - Dec 1, 2025):
   - `a302f682` - Remove DEFAULT_INSTANTIATORS from Resolver, handle in factories
   - `0adc3856` - Parser: Handle simple enum conversion (value-based format)
   - `2d7c26d4` - Fix tryDirectObjectConversion: Exclude arrays, collections, and maps
   - (Uncommitted) Parser now uses `isConversionSupportedFor(Map.class, targetClass)` for Map→Type conversion

4. **Key architectural decisions made**:
   - `ClassUtilities.newInstance(jsonObj)` expects String keys - avoid for Maps with non-String keys
   - Simple types (HashMap, ArrayList, etc.) use direct `new XXX()` via `SIMPLE_INSTANTIABLE`
   - CompactMap/CompactSet factories check for `config` field to distinguish builder vs simple usage
   - InstanceCreator provides both instance and static APIs for flexibility

5. **What still requires Resolver traversal**:
   - Forward references (`@ref` appearing before `@id`)
   - Custom ClassFactory types
   - Custom readers
   - Object-typed fields with nested JSON objects
   - Arrays containing unfinished JsonObjects
   - Unknown fields (for MissingFieldHandler)

6. **Next optimization targets** (from JFR analysis):
   - `ObjectResolver.readWithFactoryIfExists` (56 samples) - factory lookup overhead
   - `ObjectResolver.processArrayElement` (47 samples) - array element processing
   - String parsing methods collectively take ~15% of Parser time

7. **Profiling workflow**:
   - Run JsonPerformanceTest with IntelliJ Profiler
   - Copy .jfr file to /tmp/
   - Use `jfr print --events jdk.ExecutionSample` to extract data
   - Analyze cedarsoftware samples distribution
   - Compare Parser:Resolver ratio against 4.4:1 baseline

---

## Strategic Plan: Moving Instance Creation from Resolver to Parser (Dec 2025)

### Goal

Eliminate ObjectResolver's graph traversal entirely. After this work:
- **Parser** creates ALL instances and populates them during parsing
- **Resolver** only does: forward ref patching, map rehashing, post-parse factory calls

### Current State Analysis

`Resolver.createInstance()` has 7 distinct paths:

| # | Path | Can Parser Do It? |
|---|------|-------------------|
| 1 | Already has target | Yes - Parser sets this |
| 2 | Simple type conversion (`converter.isSimpleTypeConversionSupported`) | Yes |
| 3 | Enhanced Converter (DTO types like Color, Point) | Yes |
| 4 | ClassFactory | **Sometimes** - see below |
| 5 | Array creation (`Array.newInstance`) | Yes |
| 6 | Simple Instantiable (HashMap, ArrayList via `InstanceCreator`) | Yes - already done |
| 7 | Fallback: `ClassUtilities.newInstance(jsonObj)` | **Problematic** - needs JsonObject |

### Why ClassFactory Is The Core Obstacle

The ClassFactory API has a fundamental timing problem:

```java
// Current API - expects ALL data upfront
Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
```

But Parser works incrementally:
- Sees `{` → wants to create instance NOW
- Parses fields one by one
- Sees `}` → instance should be fully populated

The factory expects ALL data upfront, but Parser sees data incrementally.

#### Types That CANNOT Be Created Empty

Some types genuinely cannot exist as empty shells:

| Type | Why Empty Shell Impossible |
|------|---------------------------|
| `Enum` | Singletons - need the name to pick which one |
| `Record` | Immutable - all fields set in constructor |
| `StackTraceElement` | Immutable - 4-arg constructor only |
| `Throwable` | Constructor takes message/cause |
| `CompactMap (builder)` | Builder pattern needs config string |

These have **construction-time dependencies on field values**.

#### The Solution: Two-Phase Factory Model

Split the single `newInstance()` into two phases:

```java
public interface ClassFactory {
    /**
     * Phase 1: Create empty instance (called when Parser sees '{').
     * Return null if this type needs field data for construction.
     */
    default Object createEmpty(Class<?> c, Type suggestedType, int estimatedSize) {
        return null;  // Default: can't create empty
    }

    /**
     * Phase 2: Populate/create from data (called after parsing completes).
     * Only called if createEmpty() returned null.
     */
    default Object createFromData(Class<?> c, JsonObject jObj, Resolver resolver) {
        return newInstance(c, jObj, resolver);  // Legacy fallback
    }

    // Legacy method - kept for compatibility
    default Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        return ClassUtilities.newInstance(resolver.getConverter(), c, jObj);
    }
}
```

### Factory Categories Under New Model

| Factory | createEmpty() | createFromData() | Notes |
|---------|---------------|------------------|-------|
| `ArrayFactory` | `Array.newInstance(type, size)` | Not needed | Parser fills elements |
| `CollectionFactory` | `new ArrayList()` etc. | Not needed | Parser adds elements |
| `MapFactory` | `new LinkedHashMap()` | Not needed | Parser puts entries |
| `EnumClassFactory` | `null` | Reads "name", returns enum | **Needs data** |
| `RecordFactory` | `null` | Creates immutable record | **Needs all fields** |
| `ThrowableFactory` | `null` | Creates exception | **Needs message/cause** |
| `StackTraceElementFactory` | `null` | Creates STE | **Needs all fields** |

### The Consolidation Opportunity

Arrays, Collections, and Maps are all the same pattern:

```java
// ALL are: "Create empty container, add elements during parsing"
Object[] array = new Object[size];    // Then: array[i] = value
List list = new ArrayList<>();         // Then: list.add(value)
Map map = new LinkedHashMap<>();        // Then: map.put(key, value)
```

They can be unified in `InstanceCreator`:

```java
Object createEmptyContainer(Type containerType, int estimatedSize) {
    Class<?> raw = getRawClass(containerType);

    if (raw.isArray()) {
        return Array.newInstance(raw.getComponentType(), estimatedSize);
    } else if (Collection.class.isAssignableFrom(raw)) {
        return createSimpleInstance(resolveConcreteClass(raw));
    } else if (Map.class.isAssignableFrom(raw)) {
        return createSimpleInstance(resolveConcreteClass(raw));
    }
    return null;
}
```

### Implementation Roadmap

#### Step 1: Expand InstanceCreator
- Add `createEmptyInstance(Type, int estimatedSize)`
- Handle: Arrays, Collections, Maps, POJOs with default constructors
- Returns `null` for factory types → Parser falls back to JsonObject

#### Step 2: Parser Uses InstanceCreator Consistently
- Call `instanceCreator.createEmptyInstance()` in `pushObjectFrame()`
- If successful, set `frame.target` and enable direct injection
- If null, use existing JsonObject path

#### Step 3: Add Two-Phase Factory API
- Add `createEmpty()` and `createFromData()` to ClassFactory
- Migrate factories that CAN split (Array, Collection, Map)
- Keep legacy factories working via default implementation

#### Step 4: Slim Down Resolver.createInstance()
- Remove all cases now handled by Parser
- Keep only: factory invocation for data-dependent types

#### Step 5: Eliminate ObjectResolver Traversal
- Parser populates instances directly during parsing
- Resolver only does: post-parse factories, forward refs, rehash

### Target Architecture After Implementation

```
CURRENT:
JSON text → JsonParser → JsonObject graph → Resolver walks graph → Java Objects
                                               ↓
                              createInstance() for every object
                              traverseFields() for every object

TARGET:
JSON text → JsonParser → Java Objects (directly)
                ↓
    createEmptyInstance() when we see '{'
    inject fields as we parse them
    add elements to containers as we parse
                ↓
           Resolver only:
    - Invoke factories for Enum/Record/Throwable
    - Patch forward references
    - Rehash maps
```

### Expected Outcome

- **ObjectResolver**: From ~1100 lines to ~200 lines
- **Graph traversal**: Eliminated entirely
- **Performance**: Parser does everything in single pass
- **Resolver**: Becomes thin post-processing layer

---

## Refined Design: Create on `{` vs Create on `}` (Dec 2025)

### Core Insight

The Parser already has two natural hook points:
- **`{`** → `pushObjectFrame()` - we know the type (from field or @type)
- **`}`** → `completeObjectFrame()` - all fields have been parsed

We can create instances at **either** point depending on the type's requirements.

### Two Creation Paths

| Path | When | How | Examples |
|------|------|-----|----------|
| **Early Create** | On `{` | Create empty shell, inject fields as parsed | POJOs, ArrayList, HashMap, arrays |
| **Deferred Create** | On `}` | Buffer into JsonObject, call factory when complete | Enum, Record, Throwable, StackTraceElement |

### Early Create Flow (on `{`)

```
JSON: { "name": "John", "age": 30 }

pushObjectFrame(Person.class)
  → instance = new Person()
  → frame.target = instance
  → frame.directInjection = true

parse "name": "John"
  → injector.inject(person, "name", "John")

parse "age": 30
  → injector.inject(person, "age", 30)

completeObjectFrame()
  → return person (already populated)
```

### Deferred Create Flow (on `}`)

```
JSON: { "@type": "MyEnum", "name": "VALUE_A" }

pushObjectFrame(MyEnum.class)
  → canCreateEarly(MyEnum.class) = false (it's an enum)
  → frame.jsonObject = new JsonObject()
  → frame.target = null
  → frame.directInjection = false

parse "@type": "MyEnum"
  → frame.jsonObject.setType(MyEnum.class)

parse "name": "VALUE_A"
  → frame.jsonObject.put("name", "VALUE_A")

completeObjectFrame()
  → factory = getClassFactory(MyEnum.class)
  → return factory.newInstance(MyEnum.class, frame.jsonObject, resolver)
```

### Decision Logic: Which Path?

```java
boolean canCreateEarly(Class<?> clazz, Type suggestedType) {
    // These MUST defer - need constructor data
    if (clazz.isEnum()) return false;
    if (isRecord(clazz)) return false;
    if (Throwable.class.isAssignableFrom(clazz)) return false;
    if (StackTraceElement.class == clazz) return false;

    // Check ClassFactory preference
    ClassFactory factory = readOptions.getClassFactory(clazz);
    if (factory != null && factory.requiresCompleteData()) {
        return false;
    }

    // Check if Converter handles Map → this type (immutable DTOs)
    if (converter.isConversionSupportedFor(Map.class, clazz)) {
        return false;
    }

    // Default: create early if we can instantiate
    return canInstantiate(clazz);
}
```

### Nested Objects Inside Deferred Containers

Even when a parent is deferred (using JsonObject), nested objects still get evaluated independently:

```java
// Parsing nested object inside a deferred container
void parseNestedObject(Type elementType, JsonObject parentJObj, String fieldName) {
    if (canCreateEarly(elementType)) {
        // Create real instance, inject fields directly
        Object nested = createInstance(elementType);
        // ... parse fields, inject directly ...
        parentJObj.put(fieldName, nested);  // Store real object!
    } else {
        // Create nested JsonObject
        JsonObject nestedJObj = new JsonObject();
        // ... parse fields into nestedJObj ...
        parentJObj.put(fieldName, nestedJObj);
    }
}
```

This means a deferred container can hold a mix of:
- Real Java instances (for types that created early)
- JsonObjects (for types that also deferred)

### Type Propagation: Eliminating `markUntypedObjects()`

**Current approach**: Resolver calls `markUntypedObjects()` to stamp inferred types onto nested JsonObjects.

**New approach**: Parser naturally propagates types through the frame stack.

```java
// When parsing a field with generic type info
void parseField(String fieldName, Object parent) {
    Type fieldType = getFieldType(parent.getClass(), fieldName);
    // e.g., List<Employee> → fieldType carries Employee info

    Type elementType = inferElementType(fieldType);
    // e.g., Employee.class

    // Push frame with element type - it flows down naturally
    pushFrame(elementType);
}
```

**Type info sources (in priority order):**
1. `@type` in JSON (explicit, always wins)
2. `suggestedType` from parent's field declaration (generic info)
3. Fall back to Object.class

**Result**: `markUntypedObjects()` is eliminated. Types flow naturally through parsing.

### What Remains for Resolver?

After this refactoring, Resolver only handles:

| Task | Why Still Needed |
|------|------------------|
| **Forward reference patching** | `@ref` appears before `@id` in JSON stream |
| **Map rehashing** | Maps with non-String keys need rehash after keys are resolved |
| **MissingFieldHandler** | Callback for fields not present on target class |

Everything else moves to Parser:
- ❌ `traverseFields()` - eliminated (Parser injects directly)
- ❌ `traverseArray()` - eliminated (Parser fills arrays)
- ❌ `traverseCollection()` - eliminated (Parser adds to collections)
- ❌ `traverseMap()` - eliminated (Parser puts into maps)
- ❌ `markUntypedObjects()` - eliminated (types propagate naturally)
- ❌ `createInstance()` - moved to Parser (on `{` or `}`)

### Implementation Approach

**Phase 1: Add `canCreateEarly()` logic**
- Identify types that must defer
- Add `requiresCompleteData()` to ClassFactory interface (default: false)

**Phase 2: Implement deferred creation in Parser**
- Store `suggestedType` on ParseFrame
- At `}`, call factory with complete JsonObject for deferred types

**Phase 3: Migrate factory calls from Resolver to Parser**
- ArrayFactory, CollectionFactory, MapFactory → early create (no factory needed)
- EnumClassFactory, RecordFactory, etc. → called at `}` by Parser

**Phase 4: Remove Resolver traversal methods**
- Delete `traverseFields()`, `traverseArray()`, `traverseCollection()`
- Delete `markUntypedObjects()`
- Keep only: forward refs, rehash, missing field handler

### ClassFactory Interface Update

```java
public interface ClassFactory {
    /**
     * Does this factory need the complete JsonObject to create an instance?
     * If true, Parser will buffer fields into JsonObject and call newInstance() at '}'.
     * If false (default), Parser may create an empty shell at '{' and inject fields directly.
     */
    default boolean requiresCompleteData() {
        return false;  // Default: can create empty, populate later
    }

    // Existing method - unchanged
    Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver);

    // Existing method - unchanged
    default boolean isObjectFinal() {
        return false;
    }
}
```

### Factories by Category

| Factory | `requiresCompleteData()` | Reason |
|---------|--------------------------|--------|
| `CollectionFactory` | `false` | Can create empty ArrayList, add elements |
| `MapFactory` | `false` | Can create empty HashMap, put entries |
| `ArrayFactory` | `false`* | Can create array if size known |
| `EnumClassFactory` | `true` | Needs "name" field to pick enum constant |
| `RecordFactory` | `true` | Needs all fields for immutable constructor |
| `ThrowableFactory` | `true` | Needs message/cause for constructor |
| `StackTraceElementFactory` | `true` | Needs all 4 constructor args |
| `CompactMapFactory` | `true`* | Needs config data for builder pattern |

*ArrayFactory: For primitive arrays like `int[]`, we need size from @items. Could defer, or Parser could handle arrays specially.

### Target Architecture

```
BEFORE (Current):
JSON → Parser → JsonObject graph → Resolver.traverse*() → Java Objects
                                      ↓
                      createInstance() + field injection for every object

AFTER (New):
JSON → Parser → Java Objects (directly)
         ↓
   On '{': canCreateEarly()?
         ├── YES: Create shell, inject fields as parsed
         └── NO: Buffer to JsonObject, call factory on '}'
         ↓
   Resolver only:
   - Patch forward @ref → @id
   - Rehash non-String-key maps
   - MissingFieldHandler callbacks
```

### Benefits

1. **Single-pass parsing**: No separate traversal phase
2. **Memory efficiency**: Fewer JsonObject intermediaries
3. **Simpler code**: ObjectResolver shrinks from ~1100 to ~200 lines
4. **Natural type flow**: No explicit type projection needed
5. **Unified model**: Both early and deferred creation handled cleanly in Parser
