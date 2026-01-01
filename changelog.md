### Revision History
#### 4.72.0 - 2025-12-31
* **DEPENDENCY**: Updated `java-util` to version 4.72.0
  * Includes fix for `ThreadedLRUCacheStrategy` scheduled task accumulation
  * Includes fix for Jackson dependencies incorrectly declared without `<scope>test</scope>`
* **PERFORMANCE**: `JsonWriter` - Eliminate redundant `@type` for Collection and Map elements
  * When a field is declared with generic type info (e.g., `List<Person>`), `@type` is now omitted on elements when the element class exactly matches the declared element type
  * Extends to Map keys/values when using `@keys`/`@items` format (e.g., `Map<Building, Person>`)
  * Produces shorter JSON output without loss of type information
  * Parser already handles type inference from context, so this is backward compatible
* **PERFORMANCE**: `MapResolver` - Optimized Maps mode from 9x slower to 4x slower than Jackson
  * Added `MAP_OPTIONS_CACHE` to avoid creating `ReadOptionsBuilder` on every `toMaps()` call
  * Added `fastPrimitiveCoercion()` for common JSON primitive to Java primitive conversions without Converter lookup
  * Added `isNonReferenceableClass` check before Converter lookup in `traverseArray` - user types are not nonRef and Converter cannot convert them
  * Added early exit for `Object.class` or matching types in `traverseFields`
  * Cache `readOptions` local variable in `traverseArray` hot loop
* **PERFORMANCE**: `Resolver` - Added early `isFinished` check in `push()` method
  * Skips pushing objects that are already fully resolved, reducing unnecessary work
* **PERFORMANCE**: Replaced `Array` reflection calls with faster `ArrayUtilities` methods
  * Uses optimized array operations from java-util for better performance
* **FIX**: `ArrayFactory` - Fixed converting subclass types unnecessarily
  * Added `isAssignableFrom` check before calling Converter
  * Only convert if value is NOT already assignable to the component type
  * Preserves subclass types in polymorphic arrays (e.g., `java.sql.Date` in `Date[]` arrays)
* **ADDED**: Aliases for new JDK factory types
  * `AbstractMap.SimpleEntry`, `AbstractMap.SimpleImmutableEntry`
  * `ReentrantLock`, `ReentrantReadWriteLock`
  * `Semaphore`, `CountDownLatch`

#### 4.71.0 - 2025-12-31
* **FIX**: Added factories and writers for JDK classes with inaccessible `private final` fields on Java 9+:
  * Java 9+ module system blocks reflection access to `private final` fields in `java.base` module
  * JDK classes are not compiled with `-parameters` flag, so constructor parameter names are synthetic (`arg0`, `arg1`), preventing named parameter matching
  * **New Factories** (extract state from JsonObject, invoke constructors directly):
    * `SimpleEntryFactory` - Handles `AbstractMap.SimpleEntry` and `AbstractMap.SimpleImmutableEntry`
    * `ReentrantLockFactory` - Creates `ReentrantLock` with correct fairness setting
    * `ReentrantReadWriteLockFactory` - Creates `ReentrantReadWriteLock` with correct fairness setting
    * `SemaphoreFactory` - Creates `Semaphore` with permits and fairness
    * `CountDownLatchFactory` - Creates `CountDownLatch` with initial count
    * `OptionalFactory` - Creates `Optional.empty()` or `Optional.of(value)`
  * **New Writers** (serialize meaningful state instead of internal `Sync` fields):
    * `ReentrantLockWriter` - Writes fairness setting
    * `ReentrantReadWriteLockWriter` - Writes fairness setting
    * `SemaphoreWriter` - Writes available permits and fairness
    * `CountDownLatchWriter` - Writes current count
    * `OptionalWriter` - Writes value or empty marker
  * **Impact**: These JDK types now round-trip correctly through JSON serialization on all Java versions
  * **Backward Compatible**: No API changes, automatic factory/writer registration via config files
* **FIX**: `ObjectResolver` - Fixed potential `NullPointerException` in `traverseArray()` when array elements are null
  * Added null check before calling `getClass()` on array elements created by `createInstance()`
* **SECURITY**: `ObjectResolver` - Fixed bypass of security limits for unresolved references and missing fields
  * Changed 4 locations from direct collection access (`unresolvedRefs.add()`, `missingFields.add()`) to security-aware methods (`addUnresolvedReference()`, `addMissingField()`)
  * These methods enforce `maxUnresolvedReferences` and `maxMissingFields` limits configured in `ReadOptions`
  * Prevents potential DoS attacks via unbounded memory consumption
* **FIX**: `EnumTests` - Fixed flaky test failures caused by cached constructor accessibility
  * Added `@BeforeEach` with `ClassUtilities.clearCaches()` to ensure test isolation
* **DEPENDENCY**: Updated `java-util` to version 4.71.0
  * Required for `ArrayUtilities.getLength()` optimization
  * FastReader line/column tracking removed for performance (use `getLastSnippet()` for error context)

#### 4.70.0 - 2025-11-18
* **DEPENDENCY**: Updated `java-util` to version 4.70.0 for FastReader performance improvements and coordinated release.
* **PERFORMANCE**: JsonReader/JsonIo - Eliminated unnecessary String encoding/decoding in String-based parsing:
  * **Added `JsonReader(Reader, ReadOptions)` constructor**: Allows direct character-based input without byte stream conversion
  * **Optimized `JavaStringBuilder.parseJson()`**: Now uses `StringReader` instead of `FastByteArrayInputStream`, eliminating String → bytes → chars roundtrip
  * **Before**: String → bytes (encode UTF-8) → FastByteArrayInputStream → InputStreamReader (decode UTF-8) → FastReader
  * **After**: String → StringReader → FastReader (direct character access, no encoding/decoding)
  * **Impact**: Removes ~1-2% overhead and eliminates unnecessary byte[] allocation for all `JsonIo.toJava(String)` and `JsonIo.toMaps(String)` calls
  * **Backward Compatible**: Zero API changes, all internal optimizations
* **PERFORMANCE**: JsonReader - Eliminated dummy stream creation in non-parsing constructors:
  * **Optimized `JsonReader(ReadOptions)` constructor**: No longer creates empty FastByteArrayInputStream/InputStreamReader/FastReader/JsonParser when only resolving JsonObject graphs
  * **Optimized `JsonReader(Resolver)` constructor**: Removed dummy ByteArrayInputStream allocation when constructor is used for object resolution (not stream parsing)
  * **Before**: Created full stream infrastructure (FastByteArrayInputStream → InputStreamReader → FastReader → JsonParser) that was never used
  * **After**: Sets `input` and `parser` to `null` when not needed for the use case
  * **Impact**: Reduced allocation overhead in `JavaObjectBuilder` path (used when converting existing JsonObject graphs to Java objects)
* **IMPROVED**: JavaStreamBuilder - Removed redundant InputStream.close():
  * **Before**: Closed both JsonReader and underlying InputStream separately (double close)
  * **After**: Only closes JsonReader, which automatically closes the underlying stream
  * **Impact**: Cleaner code, no functional change (double close was safe but unnecessary)
* **PERFORMANCE**: Optimized `JsonParser.readValue()` for faster JSON parsing (16% improvement in combined parsing):
  * **Optimization #1**: Consolidated digit cases ('0'-'9') in switch statement into single range check in default case
  * **Optimization #2**: Removed redundant `pushback('{')` and `skipWhitespaceRead()` cycle when parsing JSON objects
  * **Optimization #3**: Added fast-path if statements for '{' and '[' (most common JSON value types) before switch statement
  * **Optimization #4**: Added fast-path for regular fields (non-@ prefixed) in `readJsonObject()` to bypass switch statement overhead
  * **Result**: Combined parsing CPU reduced from 13.87% to 11.65% (-2.22% total, 16.0% faster)
  * **Impact**: `readValue()` 7.91% → 6.55% (-1.36%, 17.2% faster), `readJsonObject()` 5.96% → 5.10% (-0.86%, 14.4% faster)
  * **Backward Compatibility**: Zero API changes, all 2,112 tests passing
* **IMPROVED**: `ReadOptionsBuilder.returnAsJsonObjects()` now automatically sets `failOnUnknownType(false)`:
  * **Semantic Consistency**: Map-of-Maps mode is designed to work without requiring classes on classpath
  * **Usability**: Enables parsing any JSON structure (including unknown @type entries) without exceptions
  * **Use Cases**: HTTP middleware, log analysis, cross-JVM data transport - all work without domain classes
  * **Override**: If strict type validation is needed in Map mode, explicitly call `.failOnUnknownType(true)` after `.returnAsJsonObjects()`
  * **Backward Compatibility**: Minimal impact - only affects code using `returnAsJsonObjects()` with unknown types (previously failing)
  * **MapResolver Alignment**: Aligns API behavior with MapResolver's documented purpose of handling JSON without class dependencies
* **NEW API**: Added explicit `toMaps()` methods for class-independent JSON parsing:
  * **Methods**: `toMaps(String)`, `toMaps(String, ReadOptions)`, `toMaps(InputStream)`, `toMaps(InputStream, ReadOptions)`
  * **Returns**: Builder supporting `.asClass()` and `.asType()` for flexible type extraction (same as `toJava()`)
  * **Maximum Flexibility**: Handles all valid JSON types - objects (`Map`), arrays (`List`), primitives (`String`, `Integer`, etc.), and null
  * **Consistent API**: Same fluent builder pattern as `toJava()` - identical extraction methods
  * **Purpose**: Makes Map mode obvious from API - no hidden ReadOptions flags required
  * **Auto-Configuration**: Automatically sets `returnAsJsonObjects()` mode (which sets `failOnUnknownType(false)`)
  * **Use Cases**: HTTP middleware, log analysis, cross-JVM transport - all without domain classes on classpath
  * **Examples**:
    * `Map<String, Object> map = JsonIo.toMaps("{...}").asClass(Map.class)` - Parse JSON object to Map
    * `List<Object> list = JsonIo.toMaps("[1,2,3]").asClass(List.class)` - Parse JSON array to List
    * `String str = JsonIo.toMaps("\"hello\"").asClass(String.class)` - Parse JSON string to String
    * `Object result = JsonIo.toMaps(json).asClass(null)` - Parse any valid JSON type
    * `JsonObject obj = JsonIo.toMaps(json).asClass(JsonObject.class)` - Access preserved @type metadata
  * **Zero Duplication**: Delegates to existing `toJava()` infrastructure with pre-configured options
  * **API Clarity**: `JsonIo.toMaps(json).asClass(Map.class)` vs `JsonIo.toJava(json, opts).asClass(Person.class)` - intent is clear from method name
* **ENHANCED**: Updated `JsonIo` class-level JavaDoc with prominent "Two Modes for Reading JSON" section:
  * **Java Object Mode**: `toJava()` - Requires classes, returns typed objects, compile-time safety
  * **Map Mode**: `toMaps()` - No classes required, returns Map graph, works with any JSON
  * **Documentation**: Clear examples, use case guidance, and feature comparison
  * **Improved Discoverability**: Two modes now obvious from API documentation
* **DEPRECATED**: Marked `toObjects()` methods for removal in version 5.0.0:
  * `toObjects(String, ReadOptions, Class<T>)` → Use `toJava(String, ReadOptions).asClass(Class)`
  * `toObjects(InputStream, ReadOptions, Class<T>)` → Use `toJava(InputStream, ReadOptions).asClass(Class)`
  * `toObjects(JsonObject, ReadOptions, Class<T>)` → Use `toJava(JsonObject, ReadOptions).asClass(Class)`
  * **Reason**: Fluent builder API (`toJava().asClass()`) is more flexible and supports generic types via `.asType()`
  * **Migration**: Simple one-to-one replacement with improved API
* **API CLEANUP**: Removed ThreadLocal buffer size configuration from `ReadOptions` and `ReadOptionsBuilder`:
  * **Removed methods**: `getThreadLocalBufferSize()`, `getLargeThreadLocalBufferSize()`, `threadLocalBufferSize()`, `largeThreadLocalBufferSize()`, `addPermanentThreadLocalBufferSize()`, `addPermanentLargeThreadLocalBufferSize()`
  * **Reason**: `JsonParser.readString()` reverted to simple state machine using instance field `StringBuilder` instead of ThreadLocal `char[]` buffers
  * **Impact**: API simplification - removed 6 public methods and 2 constants that are no longer needed
  * **Tests removed**: Deleted `ThreadLocalBufferIntegrationTest.java` (12 tests) and updated `JsonParserSecurityLimitsTest.java` to remove ThreadLocal buffer assertions
  * **Migration**: No action required - these configuration options had no effect after `readString()` revert
* **IMPROVED**: `WriteOptionsBuilder` and `ReadOptionsBuilder` alias type name removal methods now use `RegexUtilities` for enhanced performance and security:
  * **Methods updated**: `removeAliasTypeNamesMatching()`, `removePermanentAliasTypeNamesMatching()` in both builders
  * **Pattern Caching**: Wildcard patterns (converted to regex) are now cached, eliminating redundant Pattern.compile() calls when same pattern is used multiple times
  * **ReDoS Protection**: Timeout-based regex execution (default 5000ms) prevents catastrophic backtracking from malicious wildcard patterns
  * **Invalid Pattern Handling**: Malformed patterns are handled gracefully with null checks instead of throwing exceptions
  * **Thread Safety**: All pattern operations are thread-safe with ConcurrentHashMap-based caching
  * **Performance**: Zero overhead - shared ExecutorService eliminates per-operation thread creation that previously caused 74% performance degradation
  * **Backward Compatibility**: All 2,124 existing tests pass with identical behavior for valid patterns
  * **Security**: Prevents regex-based DoS attacks when user-provided wildcard patterns are used for alias filtering
  * **Note**: These methods are called during configuration setup, not in JSON serialization/deserialization hot paths, so performance impact is minimal but security benefits are significant
* **CLEANUP**: Removed unused import `com.sun.tools.javac.util.StringUtils` from `Injector.java` that was preventing compilation on JDK 17+

#### 4.63.0 - 2025-11-07
* **DEPENDENCY**: Updated `java-util` to version 4.3.0 for new `DataGeneratorInputStream` utility and other enhancements.
* **PERFORMANCE**: Optimized `Injector.inject()` hot path for ~12% read performance improvement:
  * Removed redundant `declaringClass.isInstance(object)` check - Native call eliminated from every field injection
  * Cached system class check as boolean field (computed once at construction vs per-injection)
  * Cached field metadata (fieldType, fieldName, displayName) to avoid repeated method calls
  * **Impact**: Read performance improved from 365ms to 322ms on benchmark suite (~12% faster)
* **PERFORMANCE**: Optimized `EnumFieldFilter` to avoid unnecessary `field.getName()` call:
  * Moved field name computation after `isEnum()` check for early exit on non-enum fields (99% of fields)
  * Minor cold path optimization during class metadata setup
* **FIX**: Fixed bug in `ArrayTest.testEmptySubArray()` test:
  * Changed `JsonIo.toObjects()` to `JsonIo.toJava()` for correct API usage
  * Fixed variable references from `outer[]` array to `outerList.get()` for proper type handling
* **FIX**: Fixed 2 disabled security tests in `JsonReaderSecurityLimitsTest`:
  * `testObjectReferencesLimit_ShouldUseConfiguredLimit` - Added `.asClass()` to trigger parsing and reference tracking
  * `testReferenceChainDepthLimit_ShouldUseConfiguredLimit` - Created proper 4-level reference chain and added `.asClass()` to trigger resolution
  * Both tests now properly validate DoS attack prevention mechanisms
* **CLEANUP**: Removed 3 stale TODO comments:
  * Removed outdated TODO in `MultiKeyMapTest` about nested Set support (already implemented and tested)
  * Removed 2 misleading TODOs in `TestGraphComparator` and `TestGraphComparatorList` about IP address performance (no IP lookup exists, static init is fast)

#### 4.62.0 - 2025-11-02
* **PERFORMANCE**: Increased ArrayList initial capacity in `JsonParser.readArray()` from 16 to 64 - Reduces resize operations for typical JSON arrays. Each resize allocates new array (2x size), copies all elements, and triggers GC pressure. Initial capacity of 64 eliminates 1-2 resize operations for arrays with 16-64 elements while adding only ~200 bytes overhead per array. **Expected improvement**: 5-8% faster deserialization for JSON with arrays >16 elements (common in API responses, list endpoints, data exports).
* **PERFORMANCE**: Eliminated redundant `getClass()` calls in serialization hot paths - Cached `Class<?>` at call sites and passed as parameters to methods, avoiding repeated `getClass()` invocations. Fixed two critical paths:
  * `writeImpl()` → `writeArray()`: Cache objClass and pass to writeArray() instead of calling array.getClass() again
  * `traceReferences()` → `processArray()`: Pass cached clazz to processArray(), eliminating array.getClass().getComponentType() redundancy
  * **Expected improvement**: 8-12% faster serialization performance in array-heavy workloads (getClass() is expensive when called millions of times)
* **PERFORMANCE**: Optimized `MapResolver.traverseCollection()` to cache `ReferenceTracker` outside loop, eliminating redundant `getReferences()` calls during collection traversal.
* **PERFORMANCE**: Removed redundant injector map caches that duplicated `ReadOptions.getDeepInjectorMap()` caching:
  * Removed `MapResolver.classInjectorCache` instance field - Was caching results already cached by ReadOptions via ClassValueMap
  * Removed local `injectorCache` in `Resolver.patchUnresolvedReferences()` - Method-local cache was redundant
  * Combined with ObjectResolver type cache removal: Overall ~4% improvement in deserialization performance
* **PERFORMANCE**: Removed redundant type resolution cache from `ObjectResolver` - Type resolution is already cached by `TypeUtilities.resolveType()` in java-util, making the additional cache layer unnecessary. Simplifies code and eliminates duplicate string concatenation overhead.
* **FEATURE**: Added convenience read methods to `Resolver` for cleaner ClassFactory implementations:
  * **Primitive type methods**: `readString()`, `readInt()`, `readLong()`, `readFloat()`, `readDouble()`, `readBoolean()` - Automatic type conversion via Converter
  * **Complex type methods**: `readObject()`, `readArray()`, `readList()`, `readMap()` - Full deserialization with cycles and references support
  * **Benefits**: No manual Map casting, no instanceof checks, eliminates boilerplate, symmetric API with WriterContext
  * **Updated factories**: `MultiKeyMapFactory`, `CompactSetFactory`, `CompactMapFactory`, `EnumSetFactory` now use convenience methods
  * **Updated examples**: `CustomJsonTest`, `CustomJsonSubObjectTest`, `CustomJsonSubObjectsTest` demonstrate usage
  * **Comprehensive documentation**: Added "Writing Custom ClassFactory (Reader)" section to user-guide.md with complete examples
* **ENHANCEMENT**: Simplified `MultiKeyMapFactory` key resolution - Removed unnecessary Collections.unmodifiable*() wrapping of collection keys to avoid Sealable* serialization issues when MultiKeyMap is re-serialized. Keys are resolved directly via reader.toJava() for cleaner, simpler code. Also improved config parsing to use Converter methods instead of manual parsing.
* **REMOVED**: `MultiKeyMapFactory` old format handling - Removed support for legacy array-with-marker-strings format that used `internalizeMarkers()` and old `reconstructKey()`. All MultiKeyMap serialization now uses the native List/Set format. Requires java-util 4.2.0+ which consolidated to single `reconstructKey()` method.
* **FEATURE**: Added comprehensive semantic write API to `WriterContext` for easier custom writer implementation:
  * **Basic field/value methods**:
    * `writeFieldName(String name)` - Writes field name with colon (e.g., `"name":`)
    * `writeValue(String value)` - Writes string value with proper quote escaping
    * `writeValue(Object value)` - Writes any object value with full serialization
  * **Complete field methods** (with leading comma):
    * `writeStringField(String name, String value)` - Complete string field (e.g., `,"name":"value"`)
    * `writeObjectField(String name, Object value)` - Complete object field with automatic serialization
    * `writeNumberField(String name, Number value)` - Complete number field WITHOUT quotes (e.g., `,"count":42`)
    * `writeBooleanField(String name, boolean value)` - Complete boolean field WITHOUT quotes (e.g., `,"active":true`)
  * **Composite field methods** (combines comma + field name + opening bracket/brace):
    * `writeArrayFieldStart(String name)` - Comma + field name + `[` in one call (e.g., `,"items":[`)
    * `writeObjectFieldStart(String name)` - Comma + field name + `{` in one call (e.g., `,"config":{`)
  * **Structural methods**:
    * `writeStartObject()` / `writeEndObject()` - Object braces
    * `writeStartArray()` / `writeEndArray()` - Array brackets
  * **Benefits**: Eliminates manual quote escaping, reduces boilerplate, prevents malformed JSON, provides Jackson-like semantic API
  * **Updated `MultiKeyMapWriter`**: Refactored to use new semantic API (uses `writeArrayFieldStart()` for cleaner code)
  * **Comprehensive Javadoc**: All 13 methods include detailed documentation with usage examples and patterns
* **FEATURE**: Added complete serialization support for `MultiKeyMap` (from java-util):
  * **Added `MultiKeyMapWriter`**: Custom writer that serializes MultiKeyMap with shortened configuration format and externalized markers
  * **Added `MultiKeyMapFactory`**: Custom factory that deserializes MultiKeyMap, reconstructing original Set/List/Array key structures
  * **Marker externalization**: Internal markers (OPEN, CLOSE, SET_OPEN, SET_CLOSE) are converted to consistent tilde notation (`~OPEN~`, `~CLOSE~`, `~SET_OPEN~`, `~SET_CLOSE~`) for clean JSON output
  * **Collision handling**: User strings matching marker names are automatically escaped with `~~ESC~~` prefix to prevent data corruption
  * **Null key support**: NULL_SENTINEL objects properly converted to/from null during serialization
  * **Configuration preservation**: All MultiKeyMap settings (capacity, loadFactor, collectionKeyMode, flattenDimensions, simpleKeysMode, valueBasedEquality, caseSensitive) preserved during round-trip
  * **Comprehensive test coverage**: 25 tests covering null keys, Set/List keys, nested arrays, complex objects, enums, temporal types, BigDecimal/BigInteger, UUID, primitive arrays, empty collections, marker collision, and string edge cases
* **ENHANCEMENT**: Enhanced `WriterContext.writeValue()` with context-aware automatic comma management:
  * **Context tracking**: JsonWriter now maintains a context stack (ARRAY_EMPTY, ARRAY_ELEMENT, OBJECT_EMPTY, OBJECT_FIELD) to track the current JSON structure state
  * **Automatic comma insertion**: `writeValue(Object)` and `writeValue(String)` automatically insert commas based on context (e.g., between array elements, after object fields)
  * **Benefits for custom writers**: Eliminates manual "boolean first" comma tracking pattern - writers can simply call `writeValue()` in loops without any comma logic
  * **Design principle**: Unified API where one method (`writeValue()`) handles all value types (String, Number, Boolean, Object, null) with automatic type detection and comma management
* **ENHANCEMENT**: Modernized custom writers to use new semantic WriterContext API:
  * **Updated `ByteBufferWriter`**: Refactored to use `writeFieldName()` and `writeValue()` instead of manual output writing
  * **Updated `CharBufferWriter`**: Refactored to use semantic API, eliminating 60+ lines of manual JSON escaping code
  * **Updated `CompactMapWriter`**: Replaced reflection-based configuration access with public `getConfig()` API from java-util
  * **Updated `CompactSetWriter`**: Refactored to use context-aware `writeValue()`, eliminating manual comma handling (removed "boolean first" pattern)
  * **Updated `LongWriter`**: Added documentation explaining why primitive writers use direct output writing (comma handling already managed by framework)
  * **Benefits**: Cleaner code, automatic JSON escaping, automatic comma management, better maintainability, consistent with new WriterContext patterns
* **TEST**: Refactored buffer writer tests to use end-to-end JsonIo integration testing:
  * **`ByteBufferWriterTest`**: Converted from null-context unit tests to 4 comprehensive JsonIo integration tests
  * **`CharBufferWriterTest`**: Converted from null-context unit tests to 5 comprehensive JsonIo integration tests
  * **Removed dead code**: Eliminated null-check fallback paths that could never execute in production (WriterContext is never null)
  * **Enhanced coverage**: Added tests for empty buffers, full buffers, and comprehensive JSON escaping validation
  * **Better testing**: Tests now verify writers work correctly through real JsonIo pipeline with actual WriterContext
* **FIX**: Fixed critical bug in java-util's `ConcurrentList.toArray()` methods where null elements were incorrectly treated as "vanished due to concurrent removal", causing early loop termination and data loss. Added comprehensive test coverage (`testNullElementsInToArray()`) with 4 test scenarios covering nulls in various positions, array-backed and typed arrays, and edge cases.

#### 4.61.0
* **FEATURE**: Added `useUnsafe` option to `ReadOptions` to control unsafe object instantiation. When enabled, json-io can deserialize package-private classes, inner classes, and classes without accessible constructors. This feature is opt-in for security reasons and uses thread-local settings to prevent interference between concurrent deserializations.
* **TEST**: Fixed `DistanceBetweenClassesTest.testPrimitives` to accommodate changes in java-util 4.1.0's `ClassUtilities.computeInheritanceDistance()` method. The method now correctly recognizes primitive widening conversions (e.g., byte to int returns distance 2, short to int returns distance 1).
* **FIX**: PR #426 - Windows compatibility fixes for json-io:
  * Fixed `JsonObject.CacheState` to implement `Serializable` for proper Java serialization support
  * Fixed `EnumFieldFilter` to correctly handle both `$VALUES` (standard JVM) and `ENUM$VALUES` (Windows JVM) synthetic fields
  * Fixed tests to handle Windows file path separators (backslash vs forward slash)
  * Changed `SecurityTest` to use cross-platform `java --version` command instead of `ipconfig`
  * Fixed `JsonObjectTest` serialization test typos
  * Fixed `EnumFieldFilterTests` to properly validate enum field filtering behavior
* **PERFORMANCE**: Parser and Resolver optimizations
  * Skip injector resolution when there's no type context (null or Object.class), avoiding expensive reflection calls
  * Use Map.getOrDefault() for field substitutes to avoid double lookup
  * Pre-size ArrayList for arrays (16 elements) to reduce resizing operations
  * Hoisted ReadOptions.getMaxIdValue() to avoid repeated method calls
  * Removed duplicate array check in loadItems() method - signature already ensures Object[]
  * Optimized ObjectResolver.traverseArray() to use items array length directly instead of jsonObj.size()
  * Pre-size ArrayList collections in traverseCollection() to avoid resizing
  * Optimized traverseCollection() branch order - check common primitives first for fast path
  * Hoisted ReadOptions constants (maxUnresolvedRefs, maxMapsToRehash, maxMissingFields) to avoid repeated method calls
  * Replaced string concatenation in ObjectResolver type resolution cache with dedicated TypeResolutionKey class
  * Pre-size ArrayList collections in MapResolver.traverseCollection() to avoid resizing
#### 4.60.0
* **FIX**: Issue #424 - Fixed `maxObjectGraphDepth` incorrectly counting objects instead of actual depth. The depth limit was being triggered by the number of objects at the same level (e.g., a list with 12 elements at depth 2) rather than the actual nesting depth. The fix properly tracks depth for each object during traversal.
* **DOCUMENTATION**: Issue #423 - Updated documentation to correctly reflect that the default unknown type is `JsonObject` (not `LinkedHashMap`). When `unknownTypeClass` is null and an unknown type is encountered, json-io creates a `JsonObject` which implements `Map`. Users can explicitly set `unknownTypeClass` to `LinkedHashMap.class` or any other Map implementation if desired.
* **VERIFIED**: Issue #425 - Added comprehensive tests for nested JSON with `unknownTypeClass(LinkedHashMap.class)`. The reported issue of inner object values being duplicated to the outer level could not be reproduced in the current version. Tests confirm correct behavior with various nesting levels and configurations.
#### 4.59.0
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.8.0` to `3.9.0.`
#### 4.58.0 (no release)
#### 4.57.0
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.6.0` to `3.7.0.`
* **ENHANCEMENT**: Replace System.out.println/System.err.println with proper Java logging - all console output now uses java.util.logging with appropriate levels (LOG.info() for user-visible results, LOG.fine() for debug information, LOG.warning() for errors) for better build output control and maintainability
* **ENHANCEMENT**: Add comprehensive test suite for automatic Insets support - java.awt.Insets objects now have complete test coverage with 12 test scenarios including serialization/deserialization, arrays, complex objects, converter integration, and edge cases, complementing the existing Enhanced Converter Integration
* **ENHANCEMENT**: Add comprehensive test suites for automatic Point, File, and Path support - java.awt.Point objects now have complete test coverage with 12 scenarios, java.io.File and java.nio.file.Path have test coverage for Enhanced Converter Integration capabilities, validating string ↔ object conversions and type support detection
* **TESTS**: Add AutomaticDimensionTest for comprehensive java.awt.Dimension testing - validates Enhanced Converter Integration with 6 test scenarios covering string serialization, array handling, and type detection
* **TESTS**: Add AutomaticRectangleTest for comprehensive java.awt.Rectangle testing - validates Enhanced Converter Integration with 6 test scenarios covering string serialization, array handling, and type detection
* **ENHANCEMENT**: Add aliases for AWT classes to improve JSON readability - java.awt.Point = Point, java.awt.Color = Color, java.awt.Rectangle = Rectangle, java.awt.Dimension = Dimension, java.awt.Insets = Insets aliases added to aliases.txt for cleaner JSON output without package prefixes
#### 4.56.0
* **ARCHITECTURAL**: Enhanced Converter support to/from: Point, Rectangle, Insets, Dimensions, Color, File, and Path to many other types.s  
* **ARCHITECTURAL**: Enhanced Converter integration in Resolver.createInstance() - prioritizes java-util Converter over custom factories for specific DTO types like java.awt.Color, java.awt.Rectangle, and java.awt.Dimension, enabling automatic string ↔ Object conversions for designated types only
* **ARCHITECTURAL**: Add sophisticated source type detection for enhanced DTO conversion - analyzes JsonObject structure to identify common patterns (Color: r/g/b/a, Point: x/y, Rectangle/Dimension: width/height) for intelligent Converter-based transformations
* **ARCHITECTURAL**: Preserve Throwable factory handling while enabling enhanced Converter integration - ensures exception type preservation continues to work through ThrowableFactory while allowing other types to benefit from automatic Converter support
* **ENHANCEMENT**: Modernize public API generics with enhanced type safety - adds sophisticated generic bounds to JsonClassReader<T>, JsonClassWriter<T>, and collection handling methods following PECS principle for maximum flexibility while maintaining 100% backward compatibility
* **ENHANCEMENT**: Improve collection method signatures with bounded wildcards - enhances WriteOptionsBuilder and ReadOptionsBuilder methods with sophisticated generic bounds like `Collection<? extends String>` and `Map<? extends Class<?>, ? extends Collection<? extends String>>` for better type safety and API flexibility
* **ENHANCEMENT**: Add automatic Rectangle support - java.awt.Rectangle objects now serialize/deserialize automatically without custom factories, supporting both string format "(x,y,width,height)" and Map format with x/y/width/height properties
* **ENHANCEMENT**: Add automatic Dimension support - java.awt.Dimension objects now serialize/deserialize automatically without custom factories, supporting both string format "widthxheight" and Map format with width/height properties
* **FIX**: Resolve exception cause type preservation during JSON deserialization - fixes ThrowableFactory to properly maintain specific exception types (e.g., ExceptionWithStringConstructor) instead of falling back to generic Throwable, ensuring accurate exception reconstruction
* **SECURITY**: Fix critical type safety vulnerability in `ArgumentHelper.getNumberWithDefault()` - prevents ClassCastException attacks
* **SECURITY**: Fix unsafe type casting in `JsonObject.rehashMaps()` - adds instanceof validation before casting 
* **SECURITY**: Fix unsafe type casting in `JsonParser.loadItems()` - validates array types before casting
* **SECURITY**: Fix infinite loop vulnerability in `JsonReader.DefaultReferenceTracker` - adds circular reference detection
* **SECURITY**: Fix critical memory leak in `JsonValue` type cache - implements bounded cache with eviction to prevent OutOfMemoryError
* **SECURITY**: Fix null pointer vulnerabilities in `JsonValue` - adds comprehensive null safety checks
* **SECURITY**: Fix race condition in `JsonValue` type cache - uses atomic operations for thread safety
* **SECURITY**: Fix memory leak in `JsonWriter.traceReferences()` - implements object count and depth limits to prevent unbounded memory growth
* **SECURITY**: Fix unsafe reflection access in `JsonWriter.getValueByReflect()` - adds comprehensive null and security checks with graceful failure
* **SECURITY**: Fix input validation in `JsonWriter.writeJsonUtf8String()` - adds null safety and string length limits to prevent memory issues  
* **SECURITY**: Add bounds checking in `JsonWriter` primitive array writers - prevents buffer overflow attacks in byte and int array processing
* **PERFORMANCE**: Updated `JsonParser.readString()` to March 3, 2025 version with full Unicode surrogate pair support - removed ThreadLocal buffers from later versions, added lookup tables (ESCAPE_CHAR_MAP, HEX_VALUE_MAP) and fast path optimization for regular characters. Correctly handles Unicode characters beyond Basic Multilingual Plane (emoji, mathematical symbols, ancient scripts) using surrogate pairs. Performance: 0.9% faster than Feb 11 simple state machine while providing superior Unicode correctness.
* **SECURITY**: Fix null validation in `JsonParser.loadId()` and `loadRef()` - adds comprehensive null checks and ID range validation to prevent attacks
* **SECURITY**: Add bounds checking for escape processing in `JsonParser.processEscape()` - prevents buffer overflow in Unicode escape sequence handling
* Fix resource leak in `JsonIo.JavaStreamBuilder.asType()` - ensures proper cleanup of JsonReader and InputStream resources with enhanced error handling
* Simplify exception handling in `JsonIo.toJson()` methods - improves maintainability while preserving original JsonIoException types
* Simplify cache management system in `JsonObject` - consolidates multiple cache variables into unified CacheState class for improved maintainability
* **SECURITY**: Fix unbounded object reference tracking in `JsonReader.DefaultReferenceTracker` - adds configurable limits (10M objects, 10K chain depth) to prevent DoS attacks
* **SECURITY**: Improve circular reference detection in `JsonReader.DefaultReferenceTracker` - enhanced tracking with depth limits and better error reporting
* **SECURITY**: Add input validation for enum type coercion in `JsonReader` - validates enum string length and content to prevent malicious input attacks
* **SECURITY**: Fix unbounded memory consumption in `Resolver` collections - adds configurable limits (1M objects, 10K stack depth) to prevent DoS attacks
* **SECURITY**: Add bounds checking in `Resolver.setArrayElement()` method - validates array indices to prevent buffer overflow attacks
* **SECURITY**: Implement depth limits for traversal operations in `Resolver` - prevents stack overflow via malicious deeply nested JSON structures
* **SECURITY**: Fix unbounded collection processing in `MapResolver` - adds configurable limits (1M objects) to prevent memory exhaustion attacks
* **SECURITY**: Improve type safety in `MapResolver` casting operations - adds validation before type conversions to prevent unsafe casting vulnerabilities
* **SECURITY**: Add string length validation in `MapResolver` - prevents memory exhaustion via extremely large string fields (64KB limit)
* **SECURITY**: Add reference ID validation in `MapResolver` - prevents malicious negative reference ID attacks
* **SECURITY**: Fix directory traversal vulnerability in `MetaUtils.loadMapDefinition()` and `loadSetDefinition()` - adds resource path validation to prevent unauthorized file access
* **SECURITY**: Add input validation in `MetaUtils` resource loading methods - prevents memory exhaustion via file size (1MB), line count (10K), and line length (8KB) limits
* **SECURITY**: Improve type safety in `MetaUtils.getValueWithDefault()` methods - adds graceful handling of ClassCastException with detailed error messages
* **SECURITY**: Add bounds checking in `MetaUtils.getJsonStringToMaxLength()` - prevents memory issues with 64KB string length limit and exception handling
* **SECURITY**: Secure reflection operations in `Injector` class - adds comprehensive security manager validation for field access, setAccessible() calls, and final modifier removal
* **SECURITY**: Enhance VarHandle security in `Injector` - adds permission checks and graceful fallback to Field.set() when VarHandle creation fails due to security restrictions
* **SECURITY**: Add object type validation in `Injector.inject()` - prevents injection into incorrect object types and adds extra protection for system classes
* **SECURITY**: Improve MethodHandle security in `Injector` - adds validation for method access permissions and proper error handling for access violations
* **SECURITY**: Secure reflection operations in `Accessor` class - adds comprehensive security manager validation for field access, setAccessible() calls, and MethodHandle operations
* **SECURITY**: Enhance field access security in `Accessor` - adds permission checks with graceful fallback to Field.get() when MethodHandle creation fails due to security restrictions
* **SECURITY**: Add object type validation in `Accessor.retrieve()` - prevents field access on incorrect object types and adds extra protection for system classes
* **SECURITY**: Improve error handling in `Accessor` - graceful fallback for JDK internal classes while maintaining security for user classes
* Fix null pointer exception in `JsonReader.isRootArray()` - adds null guard
* Optimize `JsonReader.getErrorMessage()` performance with StringBuilder to reduce GC pressure  
* Optimize `JsonParser.skipWhitespaceRead()` with lookup table for whitespace characters
* Add fast path for single-digit integer parsing in `JsonParser.readNumber()`
* Use switch statement instead of if-else chain in `JsonParser.readValue()` for better performance
* Optimize `JsonParser.readToken()` with fast ASCII case conversion for common tokens
* Optimize `JsonParser` string buffer management with size-based strategy
* Optimize `Resolver.patchUnresolvedReferences()` with injector caching and reduced object lookups
* Optimize `ObjectResolver.traverseCollection()` with early null exits and reduced getClass() calls
* **SECURITY**: Add comprehensive security audit logging system via `SecurityAuditLogger` - provides detailed security event tracking, performance monitoring, and incident response capabilities for production deployment
* **SECURITY**: Add comprehensive fuzz testing framework via `SecurityFuzzTest` - validates protection against malicious JSON inputs including deeply nested structures, large collections, massive strings, circular references, unicode injection, numeric overflow, and reference manipulation attacks  
* **SECURITY**: Add advanced attack simulation testing via `SecurityAttackSimulationTest` - models real-world attack scenarios including billion laughs attacks, zip bombs, hash collision attacks, regex DoS attacks, prototype pollution attacks, timing attacks, memory disclosure attacks, and concurrent attacks
* **SECURITY**: Add comprehensive performance benchmarking via `PerformanceBenchmarkTest` - validates that security improvements maintain excellent performance with detailed metrics for serialization, deserialization, large collections, reflection caching, and memory efficiency
* **SECURITY**: Add production security documentation via `SECURITY.md` - comprehensive security guide covering security features, configuration, attack protection, best practices, incident response, and compliance for secure deployment
* Optimize `MapResolver` methods with early null exits and cached method lookups
* Optimize `JsonReader` methods by caching getClass() calls and using final variables
* Optimize `Injector.inject()` exception handling with cached field information
#### 4.56.0
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.4.0` to `3.5.0.`
* Jar now built with the `-parameters` flag enabling constructor parameter name matching
* Improved object instantiation logic to use parameter names when available
* Expanded `ThrowableFactory` to support parameter name aliases
* Throwable instantiation now delegates to `Converter` for faster construction
* Fixed exception message selection when using `ThrowableFactory`
* Preserve null `cause` when constructing exceptions via `ThrowableFactory`
* Treat empty `cause` objects as `null` during exception instantiation
* Ensure message field is written first to preserve constructor argument order
* Minor fixes and test updates
* Reflection usage in `ReadOptionsBuilder` and `Injector` now leverages `ReflectionUtils` caching
* Optimized `JsonObject.get()` method with binary search for sorted String keys (>8 elements)
* Cache `keySet()` and `values()` collections in `JsonObject` to avoid repeated creation
* Optimize `containsKey()` and `containsValue()` with early exit strategies and type filtering
* Optimize `entrySet()` with custom iterator to reduce object allocations for array-based data
* Optimize `hashCode()` calculation by avoiding IdentityHashMap overhead for simple arrays
* Add fast path for `isEmpty()` check to avoid size() calculation overhead
* Optimize `putAll()` with bulk operations and single hash invalidation
* Cache sorted state in `JsonObject` to eliminate O(n) scans on every `get()` operation for large arrays
* Cache array lengths in `JsonObject` to eliminate expensive `Array.getLength()` JNI calls
#### 4.55.0
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.3.2` to `3.4.0.`
* Added class-level Javadoc for `ByteArrayWriter` describing Base64 encoding
* Fixed deserialization of Java records
* Tests now create record classes via reflection for JDK 8 compile compatibility.
* InjectorPrivateConstructorsTest now uses `ReflectionUtils` for reflective calls.
* Replaced `System.out.println` debug output with Java logging via `LoggingConfig`
* SealableNavigableMap now wraps returned entries to enforce immutability
* Preserve comparator when constructing `SealableNavigableSet` from a `SortedSet`
* Documentation expanded for CompactMap usage and builder() caveats
* JsonObject exposes `getTypeString()` with the raw `@type` value
* Fixed TestUtil.serializeDeserialize to retain Enum type information
* Pinned core Maven plugin versions to prevent Maven 4 warnings
* Fixed SealableNavigableSet.retainAll to correctly return modification status
* Fixed SealableNavigableSet.addAll to report modifications
* Corrected assertion for `retainAll` result in `SealableNavigableSetAdditionalTest`
* Tests avoid `ByteArrayOutputStream.toString(Charset)` for JDK 8 compatibility
* Documentation updated with guidance for parsing JSON that references unknown classes
* Enum alias/coercion tests now force type info to be written so enums deserialize correctly
* Updated EnumBasicCreationTest to expect enum values serialized as simple strings
* RecordFactory now uses java-util `ReflectionUtils`
* Adjusted TreeSet substitution test to check assignability of Set type
* Root-level enums with fields now include type info for reliable deserialization
* Added String-to-enum fallback conversion for root objects
* Fixed `SealableNavigableSet.tailSet(E)` to include the starting element
* Expanded `SingletonList` tests for branch coverage
* Fixed primitive array conversion handling in `Resolver.valueToTarget`
* Fixed EnumSet traversal when enum element lacks a name
* Fixed VarHandle reflection to allow private-constructor injector
* Added unit tests for JsonObject map-view methods
* Fixed VarHandle injection using a MethodHandle
* Fixed VarHandle injection invocation for reflection-based Injector
* Fixed Injector method-based creation to correctly locate void setters
* Injector.create now supports invoking package-private and private setter methods
* RecordFactory now checks the Java version before using records
* SealableSet(Collection) now copies the supplied collection instead of wrapping it
* Added unit test for Unicode surrogate pair escapes
* Added APIs to remove permanent method filters and accessor factories
* Added unit test for removePermanentAccessorFactory
* Fixed failing test for non-simple root type handling
* Fixed enum round-trip test to specify target class
* Added tests covering Pattern and Currency serialization
* Added Javadoc for ZoneIdWriter describing its behavior
* Updated JsonReaderHandleObjectRootTest to expect JsonIoException on return type mismatch
* Added regression test for ISO Timestamp Map conversion
#### 4.54.0 Updated to use java-util 3.3.1
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.3.1` to `3.3.2.`
#### 4.53.0 Updated to use java-util 3.3.1
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.3.0` to `3.3.1.`
* Removed unused ClassFactories.
* Added `ReadOptions/WriteOptions` ability to specify `notCustmoRead/Written` classes in `notCustomRead.txt` and `notCustomWritten.txt`
#### 4.52.0 CompactMap and CompactSet Enhancements
* `CompactMap` and `CompactSet` JSON formats improved - match historical formats, except when using builder pattern.
* `ByteBuffer and CharBuffer` converstions to/from `Map` added.
* Performance improvements in JSON serialization and deserialization.
* Code simplification related to instance creation for common types.
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.2.0` to `3.3.0.`
#### 4.51.0 CompactMap and CompactSet Enhancements
* **JSON Serialization Improvements**
  * Implemented specialized JSON serialization for `CompactMap` and `CompactSet` with optimized format
  ```json
  [{"@type":"com.cedarsoftware.util.CompactMap","config":"java.util.HashMap/CS/S70/id/Unord","data":[]},
  {"@type":"com.cedarsoftware.util.CompactSet","config":"CI/S30/Ord","data":[]}]
  ```
  * Added tests to ensure reference identity maintained across JSON round-trips
  * Preserved configuration details during serialization/deserialization
* **Collection API Consistency**
  * Aligned `CompactSet` API with `CompactMap` for better usability
* **Improved JsonObject Implementation**
  * Consistent `Map` Interface: Enhanced `JsonObject` to implement the `Map` interface more consistently:
  * Fixed the internal storage mechanism to properly handle different storage states
  * Ensured `Map` methods work correctly regardless of internal storage approach
* **Documentation and Testing**
  * Added comprehensive test suite for JSON serialization and deserialization
  * Added tests for complex reference scenarios and edge cases
  * Improved JavaDoc comments for new public methods
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.1.1` to `3.2.0.`
#### 4.50 Major Improvements to Type Resolution and API Usability
* **Enhanced Type System Support**
  * `json-io` now leverages `java-util`'s sophisticated `TypeUtilities` framework to provide comprehensive resolution of complex Java generics:
      * Full support for `ParameterizedTypes` (e.g., `Map<String, List<Person>>`)
      * Proper handling of `GenericArrayTypes` for arrays with generic components
      * Resolution of `TypeVariables` in generic class hierarchies
      * Support for `WildcardTypes` (`? extends`, `? super`) in generic declarations
      * These improvements enable more accurate field type inferencing during Java object resolution, particularly for complex nested generic structures.
  * Performance improvements in parsing and resolving. 
* **New Type-Aware API Capabilities**
  * Added advanced type specification via the new `TypeHolder` class:
      * Enables capturing and preserving full generic type information at runtime
      * Allows specifying complex parameterized types as deserialization targets
      * Example: `new TypeHolder<Map<String, List<Person>>>() {}`
  * The `JsonIo` class now accepts `TypeHolder` instances, allowing precise type targeting beyond what's possible with raw `Class` references alone.
* **Fluent Builder API for Improved Developer Experience**
  * Introduced a new fluent builder pattern through the `JsonIo.toJava()` API family:
      * More intuitive, chainable API that clearly separates the input source from the target type
      * Provides dedicated methods for different input types (String, InputStream, JsonObject)
      * Offers type-specific terminal operations:
          * `asClass(Class<T>)` for simple class types
          * `asType(TypeHolder<T>)` for complex generic types
      * Examples:
        ```java
        // Simple class types
        Person person = JsonIo.toJava(jsonString, options).asClass(Person.class);
      
        // Complex generic types
        List<Person> people = JsonIo.toJava(jsonString, options)
                                  .asType(new TypeHolder<List<Person>>(){});
        ```
      * Eliminates ambiguity in method signatures while providing a more expressive API
      * Facilitates better IDE code completion and suggestion
* Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.0.3` to `3.1.0.`
#### 4.40.0
  * All Time and Date classes compressed to a single String representation in JSON, uses ISO formats when possible.
  * Java's `Pattern` and `Currency` support added
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `3.0.2` to `3.0.3.`
#### 4.33.0
  * New custom `ClassFactory` classes are easier to write:
    * See [examples](user-guide.md#classfactory-and-customwriter-examples)
  * `ByteBuffer` and `CharBuffer` now natively supported.
  * `CompactMap` supported added via `CompactMapFactory` and `CompactMapWriter`.
  * `Sealable*` tests moved from `java-util` to `json-io` (and so have the `Sealable*` classes).
#### 4.32.0
  * EnumSet can now be written with @type or @enum, controlled by a WriteOption (writeEnumSetOldWay).  Currently, the default is `true,` write the old way for backward compatibility. This will change in a future release.
  * JsonObject simplified, with `@keys` and `@items` now as explicit fields.
  * JsonObject simplified, enumType has been removed, as it is now stored in JavaType field.
  * Root object types like `Person[].class` or `String[].class` supported for casting, as opposed to only `Object[].class.`
#### 4.31.0
  * scrub release.
#### 4.30.0
  * Root object type's like `Person[].class,` `String[].class,` can now be specified as the `rootType` and the return value will be `Person[],` `String[],` or a `ClassCastException` if the JSON data does not match the type.
  * `JsonIo.formatJson()` three parameter version removed. Use the one (1) parameter API that takes the JSON to format. It runs much faster, as it no longer deserializes/serializes, but walks the JSON `String` directly.
#### 4.29.0
  * Consumed `java-util's` `ClassUtilities.getClassLoader(),` which obtains the classLoader in a more robust way and works in OSGi and JPMS environment or non-framework environment
  * Removed `slf4j` and `logback-classic` from `test` dependencies
  * Merged in PR #297 by DaniellaHubble: Fix test that fails unexpectedly in `testEnumWithPrivateMembersAsField_withPrivatesOn()`
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.15.0` to `2.17.0.`
#### 4.28.0
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.14.0` to `2.15.0.`
#### 4.27.0
  * `ReadOptionsBuilder.addInjectorFactory()` added to allow additional `InjectorFactory's` to be added.
  * `ReadOptionsBuilder.addFieldFilter()` added to allow additional `FieldFilters` to be added.
  * LRU size control added to `ReadOptionsBuild` and `WriteOptionsBuilder`. These control the LRU size of the cache that maps `Classes` to `Fields`, `Classes` to `Injectors`, and `Classes` to `Accessors.`
  * Adds `bigint,` `BigInt,` `bigdec,` `BigDec,` `String,` `Date,` and `Class` to aliases to java-util's `ClassUtilities.forName()` support
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.13.0` to `2.14.0.`
#### 4.26.0
  * Performance improvement for `JsonIo`: When using `null` for default `ReadOptions` or `WriteOptions,` the same static instance is used.  
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.10.0` to `2.13.0.`
#### 4.25.0
  * `JsonParser` now uses an instance-based cache for common values, not a static one.  This will allow for more speed during concurrent parsing.
  * Within `aliases.txt,` `java.time.zone.ZoneRules = ZoneRules` is now correctly specified (it had `java.time.ZoneRules` before).
  * When `null` passed in for `ReadOptions` or `WriteOptions` to `JsonIo` APIs, an already created default instance of `ReadOptions` or `WriteOptions` is returned to improve performance (no need to reconstruct the default instance).
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.9.0` to `2.10.0.`
#### 4.24.0
  * All aliases have been moved to [aliases.txt](/src/main/resources/config/aliases.txt) in the resources folder. It is a very complete list of class names to alias names. If you want less aliases (or more) substituted on writing JSON, use the `addPermanentAlias()` APIs on `ReadOptionsBuilder` and `WriteOptionsBuilder.` If you do not want a particular alias output, use `WriteOptionsBuilder.removeAliasedClassName(wildcardPattern)`. The API is available for all Read/WriteOptions, the "permanent" APIs on the builder, or for a specific Read/WriteOptions instance.
  * The "extendedAliases" option has been removed from Read/Write options builders. By default, as many aliases are enabled as possible, and you can use the removeAliasXXX APIs to reduce them, or place your own version of [aliases.txt](/src/main/resources/config/aliases.txt) in the classpath ahead of the one in `json-io.jar.`
  * `WriterContext.getObjsReferenced()` added, which has all objects id to `Object` to allow custom writers to write `@id, @ref` if desired.
#### 4.23.0
  * `Collections.unmodifiableXXX()` instances when serialized, restore back to unmodifiable instances.
  * `ImmutableList` and `ImmutableSet` restore back unmodifiable instances.
  * `ReadOptionsBuilder` now include all extended aliases by default (`.withExtendedAliases()`). You can take advantage of this on the sending side by using the `WriteOptionsBuilder().withExtendAliases().` We will default this on the `WriteOptionsBuilder` in the future as the new default makes it "out there."  Remember: You can read them even if they are not sent, but you can't write them if the reader is not ready for them. 
#### 4.22.0
  * Many more `@type` aliases added to keep the JSON succinct and more human-readable.
  * Broader conversion support for rootTypes: `JsonIo.toObjects(..., rootType)` Includes all the `java-utils` `Converter.convert()` pairings (680+)
  * Removed `stack` argument from CustomReader. When creating a CustomReader, use the passed in `resolver.push(node)` to push objects onto the stack for later processing (custom or not). See example in UserGuide (coming shortly).  
#### 4.21.0
  * Empty Lists, Sets, and Maps enforce 'emptiness' on reconstruction
  * Singleton Lists, Sets, and Maps enforce 'singleton-ness' on reconstruction
  * Synchronized Lists, Sets, and Maps enforce 'synchronized-ness' on reconstruction
  * Fixed NPE on null writeOptions for `JsonIo.toJson().` The `writeOptions` are now created with defaults for you if null is passed in.
  * Added `Resolver` as the last argument to the `JsonClassReader.read()` method. The author is not required to use the `Resolver` in their implementation, but it does it come in handy as it has the Map of IDs to JsonObjects, as well as the `ReadOptions,` and the `Converter.`
  * Deprecated the APIs on `JsonIo` that exist to show one how to convert the old style `Map` options to the new "builder" format. 
#### 4.20.0
  * `MethodFilter` can be applied to remove the use of a method accessor, useful when the method accessor is causing problems (additional unwanted side-effects) during the serialization (outputting of JSON). `MethodFilter's` are added to `WriteOptions` via the `WriteOptionsBuilder.` `MethodFilter's` can be added to a single `WriteOptions` instance or added permanently (jvm lifecyle) so that all created `WriteOptions` include it automatically (see `WriteOptionsBuilder.addPermanent*` APIs).
  * Significant updates made to User Guide documentation.
  * pom.xml file updated to support both OSGi Bundle and JPMS (Modules).
  * module-info.class resides in the root of the .jar but it is not referenced.
#### 4.19.13
  * `ReadOptionsBuilder` did not have the `withExtendedAliases()` option. This adds in all the `config/extendedAliases.txt` aliases, dramatically shrinking the size of common Java class names in the JSON `@type` field.
  * Both `ReadOptionsBuilder` and `WriteOptionsBuilder` can take an existing `ReadOptions` or `WriteOptions` as a starting point, allowing you to copy from an exist options, and then tweak it from there.
#### 4.19.12
  * Added `JsonIo.getReadOptionsBuilder(Map options)` and `JsonIo.getWriteOptionsBuilder(Map options)` to facilitate porting over code that users older Map-based options.
  * Removed classes that were packaged in the adapter layer `com.cedarsoftware.util.io.*` All classes now start at `com.cedarsoftware.io.*`  You will have to adjust your imports.
  * Bug fix: Arrays that did not have a type specified, but the array type could be inferred, the component types was incorrectly being set as the array type, not the component type.
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.4.6` to `2.4.8`.
#### 4.19.11
  * Removed references to JsonObject from transition classes com.cedarsoftware.util.io.JsonReader.
#### 4.19.10
  * Updated transition class `com.cedarsoftware.util.io.JsonReader`. This API is for temporary transition to JsonIo class static APIs.
  * Added transition class `com.cedarsoftware.util.io.JsonWriter`.  This API is for temporary transition to JsonIo class static APIs.
  * The entire packaging of JsonIo has been moved from com.cedarsoftware.util.io to com.cedarsoftware.io, except for the transition APIs.
  * Added the ability to put custom options (key/value pairs) on `WriteOptions` and `ReadOptions`.
#### 4.19.9
  * **NOTE**: Repackaged resources into config/resources
#### 4.19.8
  * **NOTE**: Repackaged com.cedarsoftware.util.io to com.cedarsoftware.io
  * **NOTE**: Repackaged com.cedarsoftware.util.reflect to com.cedarsoftware.io.reflect
  * You will need to adjust the import statements for any consuming classes.
#### 4.19.7
  * Handle NoSuchMethodError() quietly, for shaded accessFactories that were added by containing platform, that no logner exist.
#### 4.19.6
  * Added additional build properties to manifest.mf
  * Renamed method on AccessorFactory that changed signature, due to conflicts running inside container that also uses this library. 
#### 4.19.5
  * Updated `JsonReader` for backwards compatibility by adding `jsonToJava(), jsonToMaps(), jsonObjectsToJava()` static APIs. 
#### 4.19.4
  * In `ReadOptionsBuilder` and `WriteOptionsBuilder`, when loading dynamic items (class names, aliases, etc.) from resources, output warnings as opposed to throwing exceptions.
#### 4.19.3
  * Remove `ReflectionUtils` from json-io as it is part of java-util.
  * Updated [java-util](https://github.com/jdereg/java-util/blob/master/changelog.md) from `2.4.4` to `2.4.5`.
#### 4.19.2
  * Moved more settings/properties from source code to resource files. 
    > Example changes required due to this update:
    <br><b>Before</b>
    > ```
    > A. Employee e = (Employee) JsonReader.jsonObjectsToJava(JsonObject employee, readOptions)
    > ```
    > <b>After</b>
    > ```
    > A. Employee e = JsonIo.toObjects(JsonObject, readOptions, Employee.class)
    > ```

#### 4.19.1
  * The old `Map` options method has been superceded by passing instead a `WriteOptions` or `ReadOptions` instance.
    All the prior features are still supported, plus new features have been added.  Use the methods on
    `WriteOptionsBuilder` and `ReadOptionsBuilder` to set them.   
    > Example changes required due to this update:
    <br><b>Before</b>
    > ```
    > // Using [key: value] to indicate a Map 
    > A. String json = JsonWriter.objectToJson(srcObj, [JsonWriter.TYPE: false])
    > B. JsonWriter.objectToJson(srcObj)
    > C. String json = JsonWriter.toJson(srcObj, null)
    > D. String json = JsonWriter.formatJson(json)
    > E. Map axisConverted = (Map) JsonReader.jsonToJava(json, [JsonReader.USE_MAPS:true])
    > F. JsonWriter.writeJsonUtf8String(value, writer)
    > ```
    > <b>After</b>
    > ```
    > A. String json = JsonIo.toJson(srcObj, new WriteOptionsBuilder().showTypeInfoNever().build());
    > B. JsonIo.toJson(srcObj)
    > C. JsonIo.toJson(srcObj, null) // 2nd arg is WriteOptions instance (can be null for defaults)
    > D. return JsonIo.formatJson(json)
    > E. ReadOptionsBuilder builder = new ReadOptionsBuilder().returnAsMaps().build() 
    >    Map axisConverted = JsonIo.toObjects(json, builder.build(), null)  // 3rd param can be root class
    > F. JsonWriter.writeJsonUtf8String(writer, value)
    > ```
#### 4.19.0
  * User Guide documentation on how to specify "options" to `JsonReader/JsonWriter` the new, easier way.  The old Map options method has been superceded by the `WriteOptions` and `ReadOptions` approach.  All the prior options are still supported, plus new features have been added.
#### 4.18.0
  * Bug fix: When Enums were sent the "old way," (JSON object form) there was a bug in outputting additional fields defined on an Enum.
  * Enhancement: Improvements on Object construction for difficult to instantiate classes. 
#### 4.17.0
  * Java class instantiation has been improved and the related code has been refactored to a much better state.
  * More built-in types are moving to use the ClassFactory and JsonClassWriter, simplifying the code base further.
  * Continuing to refine the JsonReader/JsonWriter API, Deprecated older redundant methods.  
  * There will be more releases of the 4.x branch, including support for specifying a root class to load from, removing the @type output for root fields (Issue #122, #150), support for field names without quotes and JSON comments, to name a few.
  * Upcoming version 5.0.0 will drop the dedicated methods.  
  * Upcoming version 6.0.0 will move to JDK 11 syntax.  
#### 4.16.0
  * `JsonReader/JsonWriter` Options are now specified using `ReadOptionsBuilder.build()` and `WriteOptionsBuilder.build().`
  * For improved security, key JDK classes like `ClassLoader,` `Process` (and derived classes), `Method`, `Field`, `Constructor` and others are not serialized.
  * Fixed Issue #185 Serializing/deserializing `SingletonMap/List/Set.`
  * Performance improvement: Reads and Writes are much faster due to improved low-level stream handling.  More to come on read performance improvements.
  * The public API on `JsonReader` and `JsonWriter` has been simplified to fewer options and many of the prior APIs have been deprecated.  The 5.0.0+ release will remove these deprecated APIs.
#### 4.15.0
  * Supports `JDK1.8, JDK11, 17, 21.` Tested with these versions, and compiled in class file version 52 (`JDK1.8 `) format.
  * `ClassFactory` added `isFinalObject() { return true/false }` to prevent additional processing from happening if the `ClassFactory` creates the object AND assigns all values.
  * Fixed an issue with classes that used custom reader/writers being loaded when inside an array or collection. If there were circular references, they were not resolved correctly.
  * This version writes `Enums` in a more compact way with the field name associated to a JSON String name of the enum.  However, the prior versions of `json-io` wrote `Enums` out as JSON objects.  The JSON reader will read `Enums` either way.  If you want the output to continue to write `Enums` as a JSON Object, use the `.writeEnumsAsObjects()` on the `WriteOptionsBuilder`, and it will output enums as it used to.
  * Minor change: `JsonObject` was `JsonObject<K, V>` and is now `JsonObject` (no generics).  If you used `JsonObject` in your code, make sure to remove the generics.
  * Minor change: `JsonReader.ClassFactory::newInstance(Class c, Object)` has been changed to `JsonReader.ClassFactory::newInstance(Class<?>, JsonObject)`.  If you have written a `CustomClassFactory,` update the method signature to `newInstance(Class<?>, JsonObject).` 
#### 4.14.2
  * `Enum/EnumSet` support fully added @kpartlow
  * `WARN` This version inadvertently slipped to `JDK11+` (which has been corrected in `4.15.0`).  Version `5.x.x` will be `JDK11 or JDK17`.
#### 4.14.1
  * JDK 1.8 is target class file format. @laurgarn
  * JDK 11 is source file format. @laurgarn
  * Bug fix: `EnumSet` support fixed. @laurgarn
  * Bug fix: Null boxed primitives are preserved round-trip. @laurgarn
  * Enhancement: Filter Blacklisted Fields Before Trying to Access them to prevent exceptions thrown by Proxies (improve hibernate support) @kpartlow
  * Bug fix: Stack overflow error caused by json-io parsing of untrusted JSON String @PoppingSnack
  * Enhancement: Create gradle-publish.yml @devlynnx
  * Enhancement: Added record deserialization, which implies java 16 codebase @reuschling
  * Bug fix: Fixed TestJavaScript @h143570
  * Enhancement: Bump gson from 2.6.2 to 2.8.9 @dependabot
  * Enhancement: support deserialization of Collections.EmptyList on JDK17 @ozhelezniak-talend
#### 4.14.0
  * Bug fix: `Enum` serialization error with Java 17 #155.  According to @wweng-talend, if you set : "--illegal-access=deny" on jvm parameters, it works the same between jdk11 and jdk17. 
  * Bug fix: java.lang primitives serialization - JDK-8256358 - JDK 17 support #154. Fix by @wwang-talend.
  * Bug fix: failed to deserialize `EnumSet` with json without type #120.  Fix by @sgandon and @wwang-talend
#### 4.13.0
   * Enhancement: Clear unresolved references after all have been processed, as opposed to removing each one after it was processed.  
#### 4.12.0
  * Bug fix: Enhancement #137 introduced bug for negative numbers on simple values when tolerant/lenient parsing of +/- infinity was turned on.
#### 4.11.1
  * Enhancement (#140): New option flag added `FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS:true|false` to allow forcing JSON output format to always write `Map` as `@keys/@items` in the JSON (example: `{"@keys":["a", "b"], "@values":[1, 2]}`, rather than its default behavior of recognizing all `String` keys and writing the `Map` as a JSON object, example: `{"a":1, "b":2}.`  The default value for this flag is `false`.  
#### 4.11.0
  * Enhancement (#137): Allow tolerant/lenient parser of +/- infinity and NaN.  New API added, `JsonReader.setAllowNanAndInfinity(boolean)` and `JsonWriter.setAllowNanAndInfinity(boolean)`.  The default is `false` to match the JSON standard.
  * Enhancement (#129): `JsonReader.jsonToJava("")` or `JsonReader.jsonToJava(null)` now returns a `null`, rather than throwing an exception.
  * Bug fix (#123): Removed vulnerability by disallowing `ProcessBuilder` to be serialized.
  * Bug fix (#124): Illegal Reflective Access warning when using json-io in Java 9 or newer.  This was do to call `isAccessible()` on Java's `Field` class.  This has been removed.
  * Bug fix (#132, #133): There was instance when @i was written when it should have been @e, indicating items, when using SHORT_META_KEYS flag. 
  * Bug fix (#135): When reading `{ "@type": "char", "value": "\"" }`, the value was read in as `\u0000`.  It now reads in correctly as a double quote character.
#### 4.10.1
  * Enhancement: Made `FastPushbackBufferedReader` constructor public so that this stream reader can be used anywhere.
#### 4.10.0
  * Bug fix: When reading into `Maps`, logical primitives that are not `long`, `double`, `boolean`, or `null`, were being kept in `JsonObjects` instead of being converted into their respective types (`int`, `float`, `Date`, etc.) 
#### 4.9.12
  * Bug fix: Line number was incorrectly being reported as column number in error output. 
#### 4.9.11
  * Enhancement: Added nice JSON-style argument format method, typically used for logging method calls.  See `MetaUtils.getLogMessage()`. 
#### 4.9.10
  * Bug fix: When system property file.encoding was not set to UTF-8, json-io was not correctly handling characters outside the ASCII space.  @rednoah
#### 4.9.9
  * Enhancement: Missing field handler improvements. Submitted by @sgandon
#### 4.9.8
  * Enhancement: Missing field handler improvements. Submitted by @sgandon
#### 4.9.7
  * Enhancement: Added `JsonReader.addReaderPermanent()` and `JsonWriter.addWriterPermanent()` to allow for a static (lifecycle of JVM) reader / writer to be added.  Now, custom readers and writers can be added that only exist per-instance of `JsonReader` / `JsonWriter` or permanently, so they do not have to be added each instantiation (through args or call `.addReader()` or `.addWriter()`).
#### 4.9.6
  * Enhancement: Improved `enum` handling. Updated how enums are detected so that subclasses of enums are detected.  `ordinal` and `internal` fields no longer output.
#### 4.9.5
  * Bug fix: The new FastPushBackBytesReader was incorrectly reading a String byte-by-byte ignoring the code point boundaries.  Because of this, it would blow up during parsing Strings with characters outside the ascii range.  New test case added that causes the failure.  For time being, the FastPushBackBytesReader has been removed.
  * Javadoc updates.
#### 4.9.4
  * Optimization: The coercedTypes Map in the Resolver is built one time now.
  * Added test case illustrating gson cannot handle writing then reading back Maps correctly when the keys are not Strings.
#### 4.9.3
  * Enhancement: Double.INF and NAN are output as null.
#### 4.9.2
  * Optimization: When parsing from String, a different (faster) byte[] based pushback reader is used.
  * Optimization: Built-in Readers and Writers are only instantiated once for all instances of JsonReader / JsonWriter and then re-used.
  * Enhancement: Inner 'view' classes generated from `.keySet()` and `.values()` are coerced to standard mutable collection classes. 
  * Optimization: Identical code consolidated to one function.
#### 4.9.1
  * Enhancement: Make it possible to assign instantiator for package private classes, for example com.google.common.collect.RegularImmutableMap.  Contributed by @mhmx (Richard Kovacs)
#### 4.9.0
  * Enhancement: AtomicInteger, AtomicLong, and AtomicBoolean are now supported.
#### 4.8.0
  * Enhancement: Added support for specifying the ClassLoader to be used when mapping JSON to Objects. Useful within OSGI and other frameworks where multiple ClassLoaders are involved. @lightcycle
  * JavaDoc has been significantly updated / improved.
#### 4.7.0
  * Bug fix: failing to set a double field when the JSON from the client contained a whole number (e.g. 300) instead of a decimal (e.g. 300.0). @lordvlad
  * Enhancement: when instantiating classes, json-io iterates through constructors until it can find one that works.  The order of constructors was non-deterministic.  Now the order is public constructors first, then protected, then private.
#### 4.6.0
  * Bug fix: custom write serializers were being cleared in the `write()` method, not the `close()` method after full serialization completed.  @darmbrust
  * Enhancement: Access increased to public for the pretty-print support apis, `tabIn()`, `tabOut()`, and `newLine()`. @darmbrust
#### 4.5.0
  * Improved read speed.
  * Black-list support for excluding fields.  Submitted by @sgandon
  * Pretty-print with support for options.  Submitted by @dtracers
  * Ability to use `writeObject()` API to write the 'body only'.  Submitted by @francisu
  * Bug fix: Unclear error sometimes when a class could not be loaded.  Submitted by @francisu
  * Enhancement: Provide optional notification of missing field. Submitted by @francisu
#### 4.4.0
  * `JsonReader.jsonToMaps()` API is no longer recommended (not yet deprecated).  These can easily be turned into `JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true])`.  The one difference is the return value will match the return value type of the JSON (not always be a Map).
#### 4.3.1
  * Enhancement: Skip null fields.  When this flag is set on the `JsonWriter` optional arguments, fields which have a null value are not written in the JSON output.
#### 4.3.0
  * Double / Float Nan and inifinity are now written as null, per RFC 4627
  * JsonReader.jsonToJava() can now be used to read input into Maps only (as opposed to attempting to create specific Java objects.
    Using this API allows the return value to support an array [], object, string, double, long, null as opposed to the JsonReader.jsonToMaps()
    API which forces the return value to be a Map.  May deprecate JsonReader.jsonToMaps() in the future.
#### 4.2.1
  * Bug fix: The error message showing any parsing errors put the first character of the message at the end of the message (off by one error on a ring buffer).
  * Parsing exceptions always include the line number and column number (there were a couple of places in the code that did not do this).
#### 4.2.0
  * Enhancement: In Map of Maps mode, all fields are kept, even if they start with @.  In the past fields starting with @ were skipped.
  * Ehancement: No longer throws ClassNotFound exception when the class associated to the @type is not found.  Instead it returns a LinkedHashMap, which works well in Map of Maps mode.  In Object mode, it*may* work if the field can have the Map set into it, otherwise an error will be thrown indicating that a Map cannot be set into field of type 'x'.
  * Bug fix: In Map of Maps mode, Object[] were being added with an @items field.  The object[] is now stored directly in the field holding it.  If an Object[] is 'pointed to' (re-used), then it will be written as an object { } with an @id identifying the object, and an @items field containing the array's elements.
#### 4.1.10
  * Enhancement: Java's EnumSet support added (submitted by @francisu) without need for using custom instantiator.
  * Enhancement: Added support for additional instantiator, ClassFactory2 that takes the Class (c) and the JsonObject which the instance will be filled from.  Useful for custom readers.
#### 4.1.9
  * Bug fix: When writing a Map that has all String keys, the keys were not being escaped for quotes (UTF-8 characters in general).
#### 4.1.8
  * Bug fix: 4.1.7 skipped ALL transient fields.  If a transient field is listed in the field specifiers map, then it must be traced. 
#### 4.1.7
  * Bug fix: Transient fields are skipped during reference tracing. (fix submitted by Francis Upton, @francisu).  Some transient fields could cause an exception to be thrown when being trace for references, stopping serialization.   
#### 4.1.6
  * Better support for primitive output when 'never show type' is set. (submitted by @KaiHufenbach)
#### 4.1.5
  * Tests updated to use Groovy 2.4.4
  * Deserialization updated to handle objects where the referencing class uses an Object pointer and writes the value out as single primitive value, using the 'value' key. (submitted by @KaiHufenbach)
  * pom filed updated to use a maven bundle plugin (Apache Felix) to generate OSGI headers (submitted by @KaiHufenbach)
#### 4.1.4
  * Bug fix: Custom readers will now always have the .target field set if a `JsonObject` is passed to them.  The custom reader's `read()` method was being called before the `.target` field was set on the `JsonObject`.
#### 4.1.3
  * Made `JsonReader / JsonWriter getObjectsReferenced()` API `public` (allows custom reader / writers access to these)
  * `Resolver.createJavaObjectInstance()`, used to create the correct Java object for a `JsonObject` peer, no longer calls the .read() API for objects's with custom readers.
#### 4.1.2
  * All objects in the graph are 'traced' (JsonWriter.traceReferences) except references.  The code used to not trace fields on objects that were handled by custom writers.
#### 4.1.1
  * JDK 1.6 support - Use of `ReflectiveOperationException` changed to `InvocationTargetException`.
#### 4.1.0
  * JDK 1.6 support restored. Keeping 1.6 support for Android developers.  Submitted by @kkalisz
#### 4.0.1
  * To prevent @type from being written, set the optional argument `JsonWriter.TYPE = false`. This is generally not recommended, as the output JSON may not be able to be re-read into Java objects.  However, if the JSON is destined for a non-Java system, this can be useful.
#### 4.0.0
  * Custom readers / writers are set now per-instance of `JsonReader` / `JsonWriter`, not static.  This allows using different customization for cloning, for example, than for serialization to client.
  * `JsonReader.jsonToJava()` and `JsonReader.jsonToMaps()` now allow an `InputStream` to be used.
  * Custom readers / writers can now be set all-at-once through the optional 'args' `Map`.
  * 'notCustom' readers / writers can now be set all-at-once through the optional 'args' `Map`.
  * The `removeReader()`, `removeWriter()`, `removeNotCustomReader()`, and `removeNotCustomWriter()` APIs have been removed since customizers are set per-instance. 
#### 3.3.2
  * Added new `JsonObject.isReference()` API which will return 'true' if the `JsonObject` is currently representing a reference `@ref`
  * Added new `JsonReader.getRefTarget(jsonObject)` API which will follow the `@ref` links until it resolves to the referenced (target) instance.
  * Added new `JsonReader()` constructor that only takes the args (`Map`).  It is expected that you will call `JsonReader.jsonObjectsToJava(rootJsonObject)` which will parse the passed in JsonObject graph.
  * Added new `JsonReader.removeReader()` API to remove a custom reader association to a given class.
  * Added new `JsonWriter.removeWriter()` API to remove a custom writer association to a given class.
  * Added new `JsonReader.removeNotCustomReader()` API to remove a `not custom` reader - if a `notCustom()` reader has been added (preventing inherited object from using custom reader), the association can be eliminated using this API.
  * Added new `JsonWriter.removeNotCustomWriter()` API to remove a `not custom` writer - if a `notCustom()` writer has been added (preventing inherited object from using custom writer), the association can be eliminated using this API.
#### 3.3.1
  * Re-entrancy issue fixed.  If a CustomReader (or CustomWriter) instantiated another copy of JsonReader or JsonWriter (indirectly, through recursion, for example), the 2nd instance of JsonReader or JsonWriter would clobber the ThreadLocal values inside JsonReader / JsonWriter.  Those ThreadLocal values have been removed and converted to per-instance member variables.
#### 3.3.0
  * Consolidate all 3.2.x changes
  * Last snippet read no longer shows 'boxes' for unused internal buffer characters.
  * `JsonWriter` - moved reference check 'up' to `writeImpl()` so that each specific 'write' routine did not have to test / call `writeOptionalReference()`.
  * If you have a custom reader that does not bother to resolve references from 'deeper' internal `JsonObject` maps, an exception will no longer be thrown.  It is OK for a custom reader not to 'care' about internal deeper fields if it wants to ignore them.
#### 3.2.3
  * Cache Map's for custom reader's updated to be `ConcurrentMap` instead of `Map`.
#### 3.2.2
  * `JsonCustomReaderEx` added, which passes the 'args' `Map` through to the custom reader.
  * Both `JsonCustomReaderEx` and `JsonCustomWriterEx` have a `Map` as the last argument in their single method that is implemented by the custom reader / writer.  This `Map` is the same as the 'args' `Ma` passed into to the `JsonReader` / `JsonWriter`, with the added `JSON_READER` or `JSON_WRITER` key and associated value of the calling `JsonReader` / `JsonWriter` instance.
#### 3.2.1
  * Made `Support.getWriter()` method `public static` so that CustomWriters can easily use it
  * Changed `JsonCustomWriterEx` to no longer inherit from `JsonCustomWriter` and instead added a common parent (`JsonCustomWriterBase`).  This allows only one method to be overridden to create a `JsonCustomWriterEx`.
#### 3.2.0
  * New `JsonCustomWriterEx` interface which adds the `JsonWriter` access to the implementing class so that it can call back and use `jsonWriter.writeImpl()` API.
  * Change `JsonWriter.writeImpl()` from protected to public
#### 3.1.3
  * Performance improvement: No longer using .classForName() inside JsonObject to determine isMap() or isCollection().  Reading JSON into Map of Maps mode significantly faster.
#### 3.1.2
  * Bug fix: Version 3.1.1 introduced a bug where it would always run as though it was in JSON to Java mode always (as opposed to supporting JSON to Maps).  This has been fixed.
#### 3.1.1 
  * `JsonReader.UNKNOWN_OBJECT` added as an option to indicate what to do when an unknown object is encountered in the JSON.  Default is a `Map` will be created.  However, you can set this argument to a `String` class name to instantiate, or set it to false to force an exception to be thrown.
#### 3.1.0
  ***New Feature**: Short class names to reduce the size of the output JSON. This allows you to, for example, substitute `java.util.HashMap` with `hmap` so that it will appear in the JSON as `"@type":"hmap"`.  Pass the substitution map to the `JsonWriter` (or reader) as an entry in the args `Map` with the key of `JsonWriter.TYPE_NAME_MAP` and the value as a `Map` instance with String class names as the keys and short-names as the values. The same map can be passed to the `JsonReader` and it will properly read the substituted types.
  ***New Feature**: Short meta-key names to reduce the size of the output JSON.  The `@type` key name will be shortened to `@t`, `@id` => `@i`, `@ref` => `@r`, `@keys` => `@k`, `@items` => `@e`.  Put a key in the `args` `Map` as `JsonWriter.SHORT_META_KEYS` with the value `true`.   
#### 3.0.2
  * Bug fix: Using a CustomReader in a Collection with at least two identical elements causes an exception (submitted by @KaiHufenbach).    
#### 3.0.1
  * Added new flag `JsonWriter.WRITE_LONGS_AS_STRINGS` which forces long/Long's to be written as Strings.  When sending JSON data to a Javascript, longs can lose precision because Javascript only maintains 53-bits of info (Javascript uses IEEE 754 `double` for numbers).  The precision is lost due to some of the bits used for maintaining an exponent.  With this flag set, longs will be sent as Strings, however, on return back to a Java server, json-io allows Strings to be set right back into long (fields, array elements, collections, etc.)
#### 3.0.0
  * Performance improvement: caching the custom readers and writes associated to given classes.
  * Ease of use: `json-io` throws a `JsonIoException` (unchecked) instead of checked exception `IOException`.  This allows more flexibility in terms of error handling for the user.
  * Code cleanup: Moved reflection related code from `JsonReader` into separate `MetaUtils` class.
  * Code cleanup: Moved `FastPushbackReader` from `JsonReader` into separate class.
  * Code cleanup: Moved JSON parsing code from `JsonReader` into separate `JsonParser` class.
  * Code cleanup: Moved built-in readers from `JsonReader` to separate `Readers` class.
  * Code cleanup: Moved resolver code (marshals map of maps to Java instances) into separate `Resolver` classes.
#### 2.9.4
  * `JsonReader.newInstance()` API made public
  * Bumped version of junit from 4.11 to 4.12
  * Added additional tests to ensure that null and "" can be properly assigned to primitive values (matching behavior of java-util's `Converter.convert()` API).
#### 2.9.3
  * Bug fix: When writing a `Map` with JSON primitive keys (`String`, `Long`, `Double`, or `Boolean`), a `ClassCastException` was being thrown if the type was `Long`, `Double`, or `Boolean`.  This has been fixed with test added.
#### 2.9.2
  * Android: Rearranged `[:.]` to `[.:]` in regular expressions for Android compatibility.  Technically, it should not matter, but `[:.]` was causing `java.util.regex.PatternSyntaxException: Syntax error U_ILLEGAL_ARGUMENT_ERROR` on Android JVM.
  * Bug fix: When using the `JsonWriter` arguments `Map` with `FIELD_SPECIFIERS`, if you specified a field that was transient, it was not serialized.  This has been corrected.  When you specify the field list for a given class, the `Map` can contain any non-static fields in the class, including transient fields.
  * All JUnit tests converted to Groovy.
#### 2.9.1
  * Bug fix: Parameterized types are only internally stamped onto generic Maps (Maps read with no `@type`) if the field that points to the `Map` is a template variable or it has template arguments.
  * Performance optimization: tracing references specially handles `Collection` and `Map`.  By avoiding internal structures, the reference trace is much faster.
#### 2.9.0
  * Unmodifiable `Collections` and `Maps` can now be serialized.
  * Added tests to ensure that `JsonReader.jsonToMaps()` coerces the RHS values when logical primitives, to the optional associated `@type's` fields.
  * More tests and improved code-coverage.
#### 2.8.1
  * Bug fix: `JsonReader.jsonToMaps()` API was incorrectly attempting to instantiate peer objects (specified by "@type" field in the JSON) when in 'maps' mode.  This made `JsonReader.jsonToMaps()` fail if all referenced class names did not exist in the JVM.  This has been fixed.
  * Minor Javadoc cleanup (Daniel Darabos @darabos)
  * Began migration of tests from one monolithic Java class (`TestJsonReaderWriter`) to individual Groovy test classes.
#### 2.8.0
  * Additional attempt to instantiate classes via `sun.misc.Unsafe` added (optional must be turned on by calling `JsonReader.setUseUnsafe(true)`). json-io already tries all constructors (private or public) with varying arguments, etc.  If this fails and unsafe is true, it will try `sun.misc.Unsafe.allocateInstance()` which effectively does a C-style `malloc()`.  This is OK, because the rest of `JsonReader` fills in the member variables from the serialized content.  (Submitted by @KaiHufenbach).
#### 2.7.6
  * Performance optimizations.  Use of switch statement instead of if-else chains.
  * JDK 1.7 for source code and target JVM.
#### 2.7.5
  * Bug fix: ArrayIndexOutOfBounds could still occur when serializing a class with multiple Templated fields.  The exception has been fixed.
#### 2.7.4
  * Bug fix: ArrayIndexOutOfBounds exception occurring when serializing non-static inner class with nested template parameters.  JsonReader was incorrectly passing on the 'this$0' field for further template argument processing when it should not have.
#### 2.7.3
  * `JsonReader` executes faster (more efficiently manages internal 'snippet' buffer and last line and column read.)
  * Improved date parsing: day of week support (long or short name), days with suffix (3rd, 25th, etc.), Java's default `.toString()` output for `Date` now parses, full time zone support, extra whitespace allowed within the date string.
  * Added ability to have custom JSON writers for interfaces (submitted by @KaiHufenbach).
#### 2.7.2
  * When writing JSON, less memory is used to manage referenced objects.  `JsonWriter` requires a smaller memory foot print during writing.
  * New option available to JsonWriter that allows you to force enums to not write private variables.  First you can make them transient.  However, if you do not own the code or cannot change it, you can set the `JsonWriter.getArgs().put(ENUM_PUBLIC_ONLY, true)`, and then only public fields on enums will be emitted.
#### 2.7.1
  * `BigDecimal` and `BigInteger` are now always written as a primitive (immutable, non-referenced) value.  This uniformizes their output.
#### 2.7.0
  * Updated to support JSON root of `String`, `Integer`, Floating point, and `Boolean`, per the updated JSON RFP.  Example, the `String` "football" is considered valid JSON.  The `JsonReader.readObject()` API and `JsonReader.jsonToJava()` will return a `String` in this case.  The `JsonReader.jsonToMaps()` API will still return a `Map (JsonObject)`, and the `@items` key will contain an `Object[]` with the single value (`String, Integer, Double, Boolean`) in it.
  * When a Java `Map` has only `String` keys in it, json-io will use the JSON object keys directly and associate the values to the keys as expected.  For example, the `Map` ['Football':true] would be written `{"Football":true}`.  However, if the keys are non-Strings, then Maps will be written as a JSON object with `{"@keys":[...], "@items":[...]}`, where `@keys` is an array [] of all the keys, and the `@items` is an array [] of all the values.  Entry 0 of `@keys` matches with Entry 0 in the `@items` array, and so on.  Thanks for Christian Reuschling for making the request and then supplying the implementation.
  * Change some APIs from `private` to `protected` to allow for subclasses to more easily override the default behavior.
#### 2.6.1
  * Bug fix: An internal `Map` that kept meta-information about a Java Class, changed to `ConcurrentHashMap` from `HashMap`.
#### 2.6.0
  * Added support for specifying which fields on a class will be serialized.  Use the `JsonWriter.FIELD_SPECIFIERS` key and assign the value to a `Map<Class, List<String>>`, where the keys of the `Map` are classes (e.g. Bingo.class) and the values are `List<String>`, which indicates the fields to serialize for the class.  This provides a way to reduce the number of fields written for a given class.  For example, you may encounter a 3rd Party class which fails to serialize because it has an oddball field like a `ClassLoader` reference as a non-static, non-transient field. You may not have access to the source code to mark the field as `transient`. In this case, add the appropriate entries in the `FIELD_SPECIFIERS` map. Voila, problem solved. Use the `JsonWriter` API that takes `optionalArgs Map`.  The key for this `Map` is `JsonWriter.FIELD_SPECIFIER` and the value is `Map<Class, List<String>>`.
#### 2.5.2
  * `java.net.URL` can now be used as a constructor argument.  The reader was throwing an exception instantiating a constructor with a `URL` parameter.
  * `java.lang.Object` parameters in constructor arguments are now tried with both null and `new Object()` now.
#### 2.5.1
  * Fixed a bug (introduced in 2.5.0) in the processing of a `Map` that has a `Collection` as a key.
#### 2.5.0
  * New 'Pretty-Print' option available.  If the 'args' Map passed to `JsonWriter.objectToJson(o, args)` contains the key `JsonWriter.PRETTY_PRINT` and the value 'true' (`boolean` or `String`), the `JsonWriter` output will be formatted in a nice human readable format.
  * Convert a JSON String to Pretty-Print format using `JsonWriter.formatJson(String json)`.  A `String` will be returned with the JSON formatted in a nice, human readable format.
  * If a Field contains Parameterized types (e.g., `Map<String, Set<Long>>`, and so on), `JsonReader` will use those fields to process objects deep within Maps, Collections, etc. and still create the proper Java class.
#### 2.4.5
  * Allow "" to be set into `Date` field, setting the `Date` field (or `Date` array element) as null.
#### 2.4.4
  * Allow "" to be set into `BigInteger` or `BigDecimal` when return value is `Map` (`JsonObject`). "" to non-String fields will be null, except for primitives and primitive wrappers, that will result in JVM default value.
#### 2.4.2
  * Allow "" to be set into non-String fields, when doing so, null is set on Object type fields; on primitive fields, the JVM default value is set. This is for when converting JSON to Java objects directly.
#### 2.4.1
  * Added support to allow primitives and `String` to be assigned to abstract / interface / base type field on an object (`Serializable`, `Comparable`, `Object`, etc.). Primitives can now be 'set' into these fields, without any additional type information.
#### 2.4.0
  * Primitives can be set from `Strings`
  * `Strings` can be set from primitives
  * `BigDecimal` and `BigInteger` can be set from primitives, `Strings`, `BigDecimal`, or `BigInteger`
#### 2.3.0
  * `Maps` and `Collections` (`Lists`, `Set`, etc.) can be read in, even when there are no `@keys` or `@items` as would come from a Javascript client.
  ***json-io** will now use the generic info on a `Map<Foo, Bar>` or `Collection<Foo>` object's field when the `@type` information is not included.**json-io** will then know to create `Foo` instances, `Bar` instances, etc. within the `Collection` or `Map`.
  * All parsing error messages now output the last 100 characters read, making it easier to locate the problem in JSON text. Furthermore, line and column number are now included (before it was a single position number). This allows you to immediately find the offending location.
  * You can now force `@type` to be written (not recommended) by putting the `JsonWriter.TYPE` key in the `JsonWriter` args map, and assigning the associated value to `true`.
#### 2.2.32
  * Date/Time format can be customized when writing JSON output. New optional `Map args` parameter added to main API of `JsonWriter` that specifies additional parameters for `JsonWriter`. Set the key to `JsonWriter.DATE_FORMAT` and the value to a `SimpleDateFormat` string.  Two ISO formats are available for convenience as constants on `JsonWriter`, `JsonWriter.ISO_DATE_FORMAT` and `JsonWriter.ISO_DATE_TIME_FORMAT`.
  * `JsonReader` updated to read many different date/time formats.
  * When `JsonReader` encounters a class that cannot be constructed, you can associate a `ClassFactory` to the class, so that then the un-instantiable class is encountered, your factory class will be called to create the class. New API: `JsonReader.assignInstantiator(Class c, ClassFactory factory)`
#### 2.2.31
  * Adds ability to instantiate a wider range of constructors. This was done by attempting construction with both null and non-null values for many common class types (`Collections`, `String`, `Date`, `Timezone`, etc.)
#### 2.2.30
  * `java.sql.Date` when read in, was instantiated as a `java.util.Date`. This has been corrected.
#### 2.2.29
  * First official release through Maven Central
