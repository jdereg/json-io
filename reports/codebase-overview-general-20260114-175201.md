# json-io Codebase Overview

**Generated**: 2026-01-14
**Version**: 4.56.0
**Package**: `com.cedarsoftware.io`

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Project Structure](#project-structure)
3. [Core Technologies](#core-technologies)
4. [Architecture Overview](#architecture-overview)
5. [Key Classes and Components](#key-classes-and-components)
6. [Serialization Flow (Java to JSON)](#serialization-flow-java-to-json)
7. [Deserialization Flow (JSON to Java)](#deserialization-flow-json-to-java)
8. [Configuration System](#configuration-system)
9. [Extension Points](#extension-points)
10. [Testing Approach](#testing-approach)
11. [Development Workflow](#development-workflow)
12. [Performance Considerations](#performance-considerations)
13. [Quick Reference](#quick-reference)

---

## Executive Summary

**json-io** is a powerful JSON serialization library for Java that goes beyond basic JSON parsing. Unlike simpler JSON libraries, json-io:

- **Handles cyclic references** - Objects that reference each other (A->B->C->A) are properly serialized using `@id` and `@ref` markers
- **Preserves polymorphic types** - Concrete types in collections are preserved, not lost to generic erasure
- **Requires no annotations** - Works with any Java class without modification
- **Supports complex object graphs** - Deep nesting, forward references, and intricate relationships

### Key Statistics
- **150+ test classes** with 2000+ individual tests
- **Zero external dependencies** (except java-util from Cedar Software)
- **JDK compatibility**: 1.8 through 24
- **Test execution time**: ~2.5 seconds for full suite

### When to Use json-io vs Other Libraries

| Use json-io when you need... | Use Gson/Jackson when you need... |
|------------------------------|-----------------------------------|
| Cyclic object graph support | Simple POJOs only |
| Polymorphic collection elements | Known types at compile time |
| No class modifications | Annotation-based customization |
| Full object graph fidelity | REST API compatibility |
| Deep copy via serialization | Schema validation |

---

## Project Structure

```
json-io/
├── src/
│   ├── main/
│   │   ├── java/com/cedarsoftware/io/
│   │   │   ├── JsonIo.java              # Main API entry point
│   │   │   ├── JsonParser.java          # JSON parsing engine
│   │   │   ├── JsonWriter.java          # JSON writing engine
│   │   │   ├── Resolver.java            # Object graph resolution base
│   │   │   ├── ObjectResolver.java      # Resolves to Java objects
│   │   │   ├── MapResolver.java         # Resolves to Maps
│   │   │   ├── ReadOptions.java         # Read configuration interface
│   │   │   ├── ReadOptionsBuilder.java  # Read configuration builder
│   │   │   ├── WriteOptions.java        # Write configuration interface
│   │   │   ├── WriteOptionsBuilder.java # Write configuration builder
│   │   │   ├── JsonObject.java          # Internal map representation
│   │   │   ├── factory/                 # Object instantiation factories
│   │   │   ├── reflect/                 # Reflection utilities
│   │   │   ├── util/                    # Utility classes
│   │   │   └── writers/                 # Custom type writers
│   │   └── resources/config/
│   │       ├── aliases.txt              # Type name aliases
│   │       ├── classFactory.txt         # Factory mappings
│   │       ├── customReaders.txt        # Custom reader mappings
│   │       ├── customWriters.txt        # Custom writer mappings
│   │       ├── nonRefs.txt              # Non-referenceable types
│   │       ├── fieldsNotExported.txt    # Fields to skip on write
│   │       └── fieldsNotImported.txt    # Fields to skip on read
│   └── test/
│       └── java/com/cedarsoftware/io/
│           ├── *Test.java               # 150+ test classes
│           └── models/                  # Test model classes
├── pom.xml                              # Maven build configuration
├── README.md                            # Project documentation
├── user-guide.md                        # Detailed usage guide
├── user-guide-readOptions.md            # ReadOptions reference
├── user-guide-writeOptions.md           # WriteOptions reference
├── changelog.md                         # Version history
└── ARCHITECTURAL_VISION.md              # Future architecture plans
```

---

## Core Technologies

### Build System
- **Maven 3.x** with standard directory layout
- Single module project
- Dependencies managed via `pom.xml`

### Runtime Dependencies
```xml
<dependency>
    <groupId>com.cedarsoftware</groupId>
    <artifactId>java-util</artifactId>
    <version>3.9.0</version>
</dependency>
```

java-util provides:
- `Converter` - Type conversion utilities
- `ClassUtilities` - Reflection helpers
- `FastReader`/`FastWriter` - Optimized I/O
- Collection utilities (`CompactMap`, `CompactSet`)

### Test Dependencies
- JUnit 5 (Jupiter)
- AssertJ for fluent assertions
- Gson and Jackson for comparison tests

### Java Compatibility
- Compiles with JDK 1.8+
- Module system support (JPMS) for JDK 9+
- OSGi bundle metadata included
- Tested through JDK 24

---

## Architecture Overview

### High-Level Data Flow

```
                        WRITE (Serialization)
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ Java Object │ ──► │ JsonWriter  │ ──► │ JSON String │
    └─────────────┘     └─────────────┘     └─────────────┘
                              │
                              ▼
                        WriteOptions
                        - Pretty print
                        - Type info
                        - Custom writers

                        READ (Deserialization)
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ JSON String │ ──► │ JsonParser  │ ──► │ JsonObject  │
    └─────────────┘     └─────────────┘     │  (Map tree) │
                                            └──────┬──────┘
                                                   │
                                                   ▼
                                            ┌─────────────┐
                                            │  Resolver   │
                                            ├─────────────┤
                                            │ObjectResolver│ ──► Java Objects
                                            │ MapResolver │ ──► Map/List tree
                                            └─────────────┘
```

### Design Principles

1. **Two-Phase Reading**: JSON is first parsed into a `JsonObject` graph (Maps), then resolved into target types
2. **Reference Tracking**: `@id` markers identify objects, `@ref` markers reference them
3. **Type Preservation**: `@type` markers preserve concrete class information
4. **Immutable Configuration**: Options objects are immutable after `build()`
5. **Extensibility**: Custom readers/writers/factories for any type

---

## Key Classes and Components

### Entry Point: JsonIo

**Location**: `/src/main/java/com/cedarsoftware/io/JsonIo.java`

The main API providing static methods for all JSON operations:

```java
// Convert Java object to JSON
String json = JsonIo.toJson(myObject, writeOptions);

// Convert JSON to Java object
MyClass obj = JsonIo.toJava(json, readOptions, MyClass.class);

// Deep copy via JSON
MyClass copy = JsonIo.deepCopy(original, readOptions, writeOptions);

// Pretty print JSON
String pretty = JsonIo.formatJson(json, writeOptions);
```

### Configuration: ReadOptions / WriteOptions

**Locations**:
- `/src/main/java/com/cedarsoftware/io/ReadOptions.java` (interface)
- `/src/main/java/com/cedarsoftware/io/ReadOptionsBuilder.java` (builder)
- `/src/main/java/com/cedarsoftware/io/WriteOptions.java` (interface)
- `/src/main/java/com/cedarsoftware/io/WriteOptionsBuilder.java` (builder)

Builder pattern for immutable configuration:

```java
// Read options
ReadOptions readOpts = new ReadOptionsBuilder()
    .failOnUnknownType(true)
    .maxDepth(100)
    .addClassFactory(MyClass.class, new MyFactory())
    .build();

// Write options
WriteOptions writeOpts = new WriteOptionsBuilder()
    .prettyPrint(true)
    .showTypeInfo(WriteOptions.ShowType.MINIMAL)
    .addCustomWriter(MyClass.class, new MyWriter())
    .build();
```

### Parser: JsonParser

**Location**: `/src/main/java/com/cedarsoftware/io/JsonParser.java`

Parses JSON text into a `JsonObject` tree (Maps and Lists). Key features:
- Fast character-by-character parsing
- JSON5 support (comments, trailing commas, unquoted keys)
- String deduplication via LRU cache
- Security limits (max depth, max ID values)

### Writer: JsonWriter

**Location**: `/src/main/java/com/cedarsoftware/io/JsonWriter.java`

Serializes Java objects to JSON. Key features:
- Reference tracking via `IdentityHashMap`
- Automatic `@id`/`@ref` generation for cyclic graphs
- Custom writer support
- Pretty printing with configurable indentation

### Resolvers: ObjectResolver / MapResolver

**Locations**:
- `/src/main/java/com/cedarsoftware/io/Resolver.java` (base class)
- `/src/main/java/com/cedarsoftware/io/ObjectResolver.java`
- `/src/main/java/com/cedarsoftware/io/MapResolver.java`

Transforms `JsonObject` graph into target types:
- **ObjectResolver**: Creates actual Java class instances
- **MapResolver**: Keeps data as Maps/Lists for flexibility

### Internal Representation: JsonObject

**Location**: `/src/main/java/com/cedarsoftware/io/JsonObject.java`

Extends `LinkedHashMap` to represent parsed JSON objects. Contains:
- Standard Map entries for JSON properties
- Special keys: `@type`, `@id`, `@ref`, `@items`, `@keys`
- Type resolution helpers

---

## Serialization Flow (Java to JSON)

### Step-by-Step Process

```
1. JsonIo.toJson(object, writeOptions)
   │
2. JsonWriter created with OutputStream
   │
3. writer.write(object)
   │
   ├── Reference tracing pass (if needed)
   │   └── Builds map of objects that are referenced multiple times
   │
   └── Writing pass
       │
       ├── Check for custom writer
       │   └── If found, delegate to custom writer
       │
       ├── Check object type
       │   ├── Primitive/Wrapper → write value directly
       │   ├── String → write quoted/escaped
       │   ├── Array → write as JSON array
       │   ├── Collection → write as JSON array
       │   ├── Map → write as JSON object or @keys/@items
       │   └── POJO → write fields as JSON object
       │
       ├── Write @type if needed
       │   └── Based on ShowType setting (ALWAYS/MINIMAL/NEVER)
       │
       └── Write @id if object is referenced elsewhere
```

### JSON Output Examples

**Simple Object**:
```java
class Person { String name; int age; }
```
```json
{"name":"John","age":30}
```

**With Type Info** (ShowType.ALWAYS):
```json
{"@type":"Person","name":"John","age":30}
```

**Cyclic Reference**:
```java
class Node { String id; Node next; }
// node1.next = node2; node2.next = node1;
```
```json
{
  "@id":1,
  "@type":"Node",
  "id":"first",
  "next":{
    "@id":2,
    "id":"second",
    "next":{"@ref":1}
  }
}
```

---

## Deserialization Flow (JSON to Java)

### Step-by-Step Process

```
1. JsonIo.toJava(json, readOptions, targetClass)
   │
2. JsonParser parses JSON text
   │
   └── Creates JsonObject tree (Maps)
       ├── Stores @id references in lookup map
       └── Preserves @ref markers for later resolution
   │
3. Resolver transforms JsonObject tree
   │
   ├── ObjectResolver (for Java objects)
   │   │
   │   ├── Determine target class
   │   │   ├── From @type in JSON
   │   │   ├── From target parameter
   │   │   └── From field type inference
   │   │
   │   ├── Create instance
   │   │   ├── Via ClassFactory if registered
   │   │   ├── Via Converter if supported
   │   │   └── Via reflection (constructor)
   │   │
   │   ├── Populate fields
   │   │   ├── Via Injectors (field/method injection)
   │   │   └── Resolve nested objects recursively
   │   │
   │   └── Resolve @ref references
   │       └── Replace with actual object instances
   │
   └── MapResolver (for Map/List result)
       ├── Converts primitives to Java types
       └── Patches @ref markers with actual Map references
```

### Type Resolution Priority

1. Explicit `@type` in JSON
2. Target class parameter passed to `toJava()`
3. Field type from parent object
4. Generic type parameter (if available)
5. Default to `LinkedHashMap` / `ArrayList`

---

## Configuration System

### Configuration Files

Located in `/src/main/resources/config/`:

| File | Purpose | Example Entry |
|------|---------|---------------|
| `aliases.txt` | Short type names | `java.util.ArrayList = ArrayList` |
| `classFactory.txt` | Custom instantiation | `java.util.EnumSet = ...EnumSetFactory` |
| `customReaders.txt` | Custom deserialization | `java.time.LocalDate = ...LocalDateReader` |
| `customWriters.txt` | Custom serialization | `java.time.LocalDate = ...LocalDateWriter` |
| `nonRefs.txt` | Skip reference tracking | `java.lang.String` |
| `fieldsNotExported.txt` | Skip on write | `java.lang.Throwable.stackTrace` |
| `fieldsNotImported.txt` | Skip on read | `java.lang.Thread.*` |

### ReadOptions Key Settings

```java
ReadOptionsBuilder builder = new ReadOptionsBuilder();

// Security limits
builder.maxDepth(1000);                    // Prevent stack overflow attacks
builder.maxUnresolvedReferences(100000);   // Prevent memory exhaustion

// Type handling
builder.failOnUnknownType(true);           // Throw on unknown @type
builder.unknownTypeClass(LinkedHashMap.class); // Default for unknown types

// Numeric precision
builder.floatingPoint(ReadOptions.Decimals.BIG_DECIMAL);
builder.integers(ReadOptions.Integers.BOTH); // Auto Long/BigInteger

// Custom handlers
builder.addClassFactory(MyClass.class, new MyFactory());
builder.aliasTypeName("MyAlias", MyClass.class);
```

### WriteOptions Key Settings

```java
WriteOptionsBuilder builder = new WriteOptionsBuilder();

// Output formatting
builder.prettyPrint(true);
builder.shortMetaKeys(true);               // @t instead of @type

// Type information
builder.showTypeInfo(ShowType.MINIMAL);    // Only when needed
builder.showingRootTypeInfo(false);        // Omit @type on root

// Field control
builder.skipNullFields(true);
builder.addNotExportedField(MyClass.class, "password");

// Custom handlers
builder.addCustomWriter(MyClass.class, new MyWriter());
builder.addNonReferenceableClass(MyValueClass.class);
```

---

## Extension Points

### Custom ClassFactory

For classes that need special instantiation:

```java
public class ImmutablePersonFactory implements ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        String name = (String) jObj.get("name");
        int age = ((Number) jObj.get("age")).intValue();
        return new ImmutablePerson(name, age);
    }

    @Override
    public boolean isObjectFinal() {
        return true; // No further field injection needed
    }
}

// Register
ReadOptions opts = new ReadOptionsBuilder()
    .addClassFactory(ImmutablePerson.class, new ImmutablePersonFactory())
    .build();
```

### Custom JsonClassWriter

For custom JSON output format:

```java
public class MoneyWriter implements JsonClassWriter<Money> {
    @Override
    public void write(Money m, boolean showType, Writer output,
                      WriterContext context) throws IOException {
        if (showType) {
            output.write("{\"@type\":\"Money\",");
        } else {
            output.write("{");
        }
        output.write("\"amount\":" + m.getAmount());
        output.write(",\"currency\":\"" + m.getCurrency() + "\"}");
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        return true;
    }

    @Override
    public void writePrimitiveForm(Object o, Writer output,
                                   WriterContext context) throws IOException {
        Money m = (Money) o;
        output.write("\"" + m.getAmount() + " " + m.getCurrency() + "\"");
    }
}
```

### Built-in Factories

| Factory | Purpose | Location |
|---------|---------|----------|
| `ArrayFactory` | Primitive/Object arrays | `factory/ArrayFactory.java` |
| `CollectionFactory` | List, Set, Queue types | `factory/CollectionFactory.java` |
| `MapFactory` | Map implementations | `factory/MapFactory.java` |
| `EnumSetFactory` | EnumSet instances | `factory/EnumSetFactory.java` |
| `ThrowableFactory` | Exception types | `factory/ThrowableFactory.java` |
| `SingletonFactory` | Collections.singleton* | `factory/SingletonFactory.java` |
| `UnmodifiableFactory` | Collections.unmodifiable* | `factory/UnmodifiableFactory.java` |

---

## Testing Approach

### Test Organization

```
src/test/java/com/cedarsoftware/io/
├── Type-specific tests
│   ├── EnumTests.java, EnumSetFormatTest.java
│   ├── LocalDateTests.java, InstantTests.java
│   ├── BigDecimalTest.java, AtomicLongTest.java
│   └── ...
├── Feature tests
│   ├── ForwardReferencesTest.java
│   ├── GenericTypesTest.java
│   ├── CustomClassHandlerTest.java
│   └── ...
├── Comparison tests
│   ├── GsonNotHandleCycleButJsonIoCanTest.java
│   ├── GsonNotHandleHeteroCollectionsTest.java
│   └── ...
├── Security tests
│   ├── SecurityTest.java
│   ├── CollectionFactorySecurityLimitsTest.java
│   └── ...
└── Performance tests
    └── zzLastTest.java (statistics collector)
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=EnumSetFormatTest

# Run tests matching pattern
mvn test -Dtest="*Enum*"

# IMPORTANT: Never run zzLastTest directly!
# It collects statistics from other tests
# Always run the full test suite
```

### Test Patterns

**Round-trip Testing** (most common):
```java
@Test
void testRoundTrip() {
    MyObject original = new MyObject("test", 42);
    String json = JsonIo.toJson(original);
    MyObject restored = JsonIo.toJava(json, MyObject.class);
    assertEquals(original, restored);
}
```

**Specific JSON Format Testing**:
```java
@Test
void testSpecificFormat() {
    String json = "{\"@type\":\"MyClass\",\"value\":42}";
    MyClass obj = JsonIo.toJava(json, MyClass.class);
    assertEquals(42, obj.getValue());
}
```

---

## Development Workflow

### Before Any Code Change

1. Read and understand the existing code
2. Run full test suite to establish baseline: `mvn test`
3. Create focused unit tests for the change

### Making Changes

1. Implement the change
2. During development: `mvn test -Dtest=SpecificTest`
3. When complete: `mvn clean test` (full suite - mandatory!)
4. All 2000+ tests must pass
5. Update `changelog.md` with the change

### Commit Guidelines

- Use descriptive commit messages
- Include test coverage for new code
- Format: `[Type]: Brief description`
- Types: Security, Performance, Feature, Fix, Refactor

### Performance Monitoring

The test suite includes performance comparisons:
- json-io vs Gson vs Jackson
- Tolerance: +/- 5ms for standard tests
- Baseline: ~270ms for Read JSON operations

---

## Performance Considerations

### Optimization Techniques Used

1. **String Deduplication**: LRU cache in JsonParser reduces memory for repeated strings
2. **Lookup Tables**: Static arrays for escape character detection
3. **ClassValue Cache**: Caches type dispatch decisions in JsonWriter
4. **Hoisted Constants**: ReadOptions values cached as instance fields

### Memory Management

- `IdentityHashMap` for reference tracking (avoids equals() calls)
- Pre-sized collections where possible
- String interning for common values

### Security Limits

Configurable via ReadOptions:
- `maxDepth` - Maximum JSON nesting (default: 1000)
- `maxUnresolvedReferences` - Forward reference limit
- `maxObjectReferences` - Total object count limit
- `maxIdValue` - Maximum @id/@ref value (default: 1,000,000,000)

---

## Quick Reference

### Most Common Operations

```java
// Object to JSON
String json = JsonIo.toJson(myObject);

// JSON to Object (type inferred from JSON)
Object obj = JsonIo.toJava(json);

// JSON to specific type
MyClass obj = JsonIo.toJava(json, MyClass.class);

// JSON to Maps (no type conversion)
Map<String, Object> map = JsonIo.toMaps(json);

// Pretty print
String pretty = JsonIo.formatJson(json);

// Deep copy
MyClass copy = JsonIo.deepCopy(original);
```

### JSON Meta Keys

| Key | Short | Purpose |
|-----|-------|---------|
| `@type` | `@t` | Class name for instantiation |
| `@id` | `@i` | Unique object identifier |
| `@ref` | `@r` | Reference to @id |
| `@items` | `@e` | Array/collection elements |
| `@keys` | `@k` | Map keys (when non-String) |

### Common Configuration Patterns

```java
// Maximum compatibility (verbose JSON)
WriteOptions verbose = new WriteOptionsBuilder()
    .showTypeInfo(ShowType.ALWAYS)
    .build();

// Minimal JSON (for known types)
WriteOptions minimal = new WriteOptionsBuilder()
    .showTypeInfo(ShowType.NEVER)
    .skipNullFields(true)
    .build();

// Human-readable
WriteOptions readable = new WriteOptionsBuilder()
    .prettyPrint(true)
    .showTypeInfo(ShowType.MINIMAL)
    .build();

// Strict parsing
ReadOptions strict = new ReadOptionsBuilder()
    .failOnUnknownType(true)
    .maxDepth(100)
    .build();
```

---

## Further Reading

- **User Guide**: `/user-guide.md` - Comprehensive usage documentation
- **ReadOptions Guide**: `/user-guide-readOptions.md` - All read configuration options
- **WriteOptions Guide**: `/user-guide-writeOptions.md` - All write configuration options
- **Changelog**: `/changelog.md` - Version history and release notes
- **Architecture Vision**: `/ARCHITECTURAL_VISION.md` - Future development plans

---

*This overview was generated to help developers understand and contribute to the json-io project. For the most current information, refer to the source code and official documentation.*
