<p align="center">
  <img src="infographic.jpeg" alt="json-io infographic - JSON5, TOON, and Core Capabilities" width="100%" />
</p>
<p align="right"><sub>Infographic by <a href="https://github.com/glaforge">Guillaume Laforge</a></sub></p>

<div align="center">
  <p>
    <a href="https://central.sonatype.com/search?q=json-io&namespace=com.cedarsoftware">
      <img src="https://badgen.net/maven/v/maven-central/com.cedarsoftware/json-io" alt="Maven Central" height="20" />
    </a>
    <a href="http://www.javadoc.io/doc/com.cedarsoftware/json-io">
      <img src="https://javadoc.io/badge/com.cedarsoftware/json-io.svg" alt="Javadoc" height="20" />
    </a>
    <a href="https://github.com/jdereg/json-io/blob/master/LICENSE">
      <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" height="20" />
    </a>
    <img src="https://img.shields.io/badge/JDK-8%20to%2024-orange" alt="JDK 8–24" height="20" />
    <a href="https://json5.org/">
      <img src="https://img.shields.io/badge/JSON5-Full%20Support-brightgreen" alt="JSON5" height="20" />
    </a>
    <a href="https://toonformat.dev/">
      <img src="https://img.shields.io/badge/TOON-Read%2FWrite-blueviolet" alt="TOON" height="20" />
    </a>
  </p>

  <p>
    <a href="https://github.com/jdereg/json-io">
      <img src="https://img.shields.io/github/stars/jdereg/json-io?style=social" alt="GitHub stars" />
    </a>
    <a href="https://github.com/jdereg/json-io/fork">
      <img src="https://img.shields.io/github/forks/jdereg/json-io?style=social" alt="GitHub forks" />
    </a>
  </p>
</div>

json-io is a powerful and lightweight Java library that simplifies **JSON5**, **JSON**, and **TOON** serialization and deserialization while handling complex object graphs with ease. Unlike basic JSON parsers, json-io preserves object references, handles polymorphic types, and maintains cyclic relationships in your data structures.

## Table of Contents

- [Key Features](#key-features)
- [Why json-io?](#why-json-io)
  - [vs Jackson/Gson](#vs-jacksongson-json)
  - [vs JToon](#vs-jtoon-toon)
  - [Annotation Support](#annotation-support)
- [Installation](#installation)
  - [Spring Boot Integration](#spring-boot-integration)
- [Quick Start](#quick-start)
- [TOON Format](#toon-format)
- [Supported Types](#supported-types-60-built-in)
- [Documentation](#documentation)
- [Release](#release-)
  - [Logging](#logging)

## Key Features

- Full **JSON5** support including single-line and multi-line comments, single-quoted strings, unquoted object keys, trailing commas, and more — while remaining fully backward compatible with standard JSON (RFC 8259)
- **TOON read/write** — [Token-Oriented Object Notation](https://toonformat.dev/) for LLM-optimized serialization (~40-50% fewer tokens than JSON)
- Preserves object references and handles cyclic relationships (use `cycleSupport(false)` for ~35-40% faster writes on acyclic data) - for `JSON/JSON5`.
- Supports polymorphic types and complex object graphs
- Zero external dependencies (other than java-util)
- Fully compatible with both JPMS and OSGi environments
- Lightweight (`json-io.jar` is ~330K, `java-util` is ~700K)
- Compatible with JDK 1.8 through JDK 24
- The library is built with the `-parameters` compiler flag. Parameter names are now retained for tasks such as constructor discovery.
- Optional unsafe mode for deserializing package-private classes, inner classes, and classes without accessible constructors (opt-in for security)
- **Annotation support** — json-io's own `@Io*` annotations plus automatic recognition of Jackson annotations (no compile-time dependency)
- Extensive configuration options via `ReadOptionsBuilder` and `WriteOptionsBuilder`
- Two modes: typed Java objects (`toJava()`) or class-independent Maps (`toMaps()`)
- Parse JSON with unknown class references into a Map-of-Maps representation without requiring classes on classpath
- Featured on [json.org](http://json.org)

## Why json-io?

### vs Jackson/Gson (JSON)

| Capability | json-io | Jackson/Gson |
|------------|---------|--------------|
| Object graph cycles | Full support (`@id`/`@ref`) | None |
| Polymorphic types | Automatic (`@type` when needed) | Requires annotations |
| Configuration | Zero-config default; optional annotations | Annotation-heavy |
| Dependencies | java-util (~1MB total) | Multiple JARs (~2.5MB+) |

**Trade-off**: json-io prioritizes **correctness over speed**. It preserves graph shape and Java type semantics—handling cycles, references, and polymorphism that break other serializers. Jackson/Gson are faster for simple DTOs, but json-io handles what they cannot.

**Performance tip**: Use `cycleSupport(false)` for ~35-40% faster writes when you know your object graph is acyclic.

### vs JToon (TOON)

| Capability | json-io | JToon |
|---|---|---|
| Built-in types | 60+ | ~15 |
| Map key types | Any serializable type | Strings only |
| EnumSet support | Yes | No |
| Full Java serialization | Yes — any object graph | Limited to supported types |
| Cycle support (`$id`/`$ref`) | Yes (opt-in) | No |
| Annotation support | `@Io*` + Jackson (reflective) | None |
| Dependencies | java-util only | Jackson |
| Status | Stable, production-ready | Beta (v1.x.x) |

json-io's TOON implementation offers comprehensive Java type coverage while JToon focuses on basic types with Jackson integration.

### Annotation Support

json-io provides 25 annotations in the `com.cedarsoftware.io.annotation` package for controlling serialization and deserialization:

| Annotation | Target | Purpose |
|---|---|---|
| `@IoProperty("name")` | Field | Rename field in JSON |
| `@IoIgnore` | Field | Exclude field |
| `@IoIgnoreProperties({"a","b"})` | Class | Exclude fields by name |
| `@IoAlias({"alt1","alt2"})` | Field | Accept alternate names on read |
| `@IoPropertyOrder({"x","y"})` | Class | Control field order on write |
| `@IoInclude(Include.NON_NULL)` | Field | Skip null on write |
| `@IoCreator` | Constructor/Method | Custom deserialization constructor or static factory |
| `@IoValue` | Method | Single-value serialization |
| `@IoNaming(Strategy.SNAKE_CASE)` | Class | Naming strategy for all fields |
| `@IoIncludeProperties({"a","b"})` | Class | Whitelist of included fields |
| `@IoIgnoreType` | Class | Exclude all fields of this type everywhere |
| `@IoTypeInfo(LinkedList.class)` | Field | Default concrete type when `@type` absent; also eliminates `@type` on write when runtime type matches |
| `@IoDeserialize(as=LinkedList.class)` | Field/Class | Force type override during deserialization; also eliminates `@type` on write when runtime type matches |
| `@IoClassFactory(MyFactory.class)` | Class | Specify a ClassFactory for deserialization |
| `@IoGetter("fieldName")` | Method | Custom getter method for serialization |
| `@IoSetter("fieldName")` | Method | Custom setter method for deserialization |
| `@IoNonReferenceable` | Class | Suppress `@id`/`@ref` for instances of this type |
| `@IoNotCustomReader` | Class | Suppress custom reader (use standard deserialization) |
| `@IoNotCustomWritten` | Class | Suppress custom writer (use standard serialization) |
| `@IoCustomWriter(MyWriter.class)` | Class | Specify custom `JsonClassWriter` for serialization |
| `@IoCustomReader(MyReader.class)` | Class | Specify custom `JsonClassReader` for deserialization |
| `@IoTypeName("ShortName")` | Class | Alias for `@type` in JSON (replaces FQCN) |
| `@IoAnySetter` | Method | Receive unrecognized JSON fields during deserialization |
| `@IoAnyGetter` | Method | Provide extra fields during serialization |
| `@IoFormat("pattern")` | Field | Per-field format pattern (`String.format`, `DecimalFormat`, `DateTimeFormatter`, or `SimpleDateFormat`) |

Additionally, json-io **reflectively honors Jackson annotations** when they are on the classpath — with zero compile-time dependency on Jackson. Supported: `@JsonProperty`, `@JsonIgnore`, `@JsonIgnoreProperties`, `@JsonAlias`, `@JsonPropertyOrder`, `@JsonInclude`, `@JsonCreator`, `@JsonValue`, `@JsonIgnoreType`, `@JsonTypeInfo`, `@JsonIncludeProperties`, `@JsonNaming`, `@JsonDeserialize`, `@JsonGetter`, `@JsonSetter`, `@JsonTypeName`, `@JsonFormat`, `@JsonAnySetter`, `@JsonAnyGetter`.

**Precedence:** Programmatic API > json-io annotations > Jackson annotations.

See the [Annotations section of the User Guide](/user-guide.md#annotations) for full details and examples.

## Installation
To include in your project:
> Replace `LATEST_VERSION` with the version shown here:
[![Maven Central](https://img.shields.io/maven-central/v/com.cedarsoftware/json-io)](https://central.sonatype.com/artifact/com.cedarsoftware/json-io)

**Gradle**
```groovy
implementation 'com.cedarsoftware:json-io:LATEST_VERSION'
```

**Maven**
```xml
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

### Spring Boot Integration

json-io provides a Spring Boot starter for seamless integration with Spring MVC and WebFlux applications.

**Add the dependency:**

```xml
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io-spring-boot-starter</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

Your REST controllers now support JSON, JSON5, and TOON formats via content negotiation:

```java
@RestController
public class ApiController {
    @GetMapping("/data")
    public MyData getData() {
        return myData;  // Returns JSON, JSON5, or TOON based on Accept header
    }
}
```

## Quick Start

```java
// JSON
String json = JsonIo.toJson(myObject);
MyClass obj = JsonIo.toJava(json).asClass(MyClass.class);

// TOON (~40% fewer tokens than JSON)
String toon = JsonIo.toToon(myObject, writeOptions);
MyClass obj = JsonIo.fromToon(toon, readOptions).asClass(MyClass.class);
```

Request TOON format for LLM applications: `Accept: application/vnd.toon`

**Also supports WebFlux and WebClient** for reactive applications.

See the [Spring Integration Guide](/user-guide-spring.md) for configuration options, WebFlux usage, customizers, and Jackson coexistence modes.

## TOON Format

[TOON](https://toonformat.dev/) (Token-Oriented Object Notation) is an indentation-based format that produces **~40-50% fewer tokens** than JSON — ideal for LLM applications where token count directly impacts cost and context window usage.

- **No braces, brackets, or commas** — structure is expressed through indentation
- **No quoting** for most keys and values — quotes only when the value contains special characters
- **Compact arrays** — inline `[N]: a,b,c` or list format with `- ` prefixed elements
- **Tabular format** — arrays of uniform objects as CSV-like rows with column headers
- **Key folding** — nested keys like `address.city: Denver` flatten one level of nesting
- **Full fidelity** — most data requires no extra metadata at all. When type hints are needed for correct deserialization (e.g., polymorphic fields), you can use json-io's `@Io*` annotations, Jackson annotations, or explicit `$type` markers. For object graphs with cycles, enable `cycleSupport(true)` to emit `$id`/`$ref` pairs

**JSON:**
```json
{"team":"Rockets","players":[{"name":"John","age":30,"position":"guard"},{"name":"Sue","age":27,"position":"forward"},{"name":"Mike","age":32,"position":"center"}]}
```

**TOON (same data, ~45% fewer tokens):**
```yaml
team: Rockets
players:
  name, age, position
  John, 30, guard
  Sue, 27, forward
  Mike, 32, center
```

When an array contains uniform objects, TOON automatically uses **tabular format** — a compact CSV-like layout with column headers. Mixed or nested objects use indented format instead.

**Write / Read:**
```java
// Write TOON
String toon = JsonIo.toToon(myObject);

// Read TOON back to typed Java object
Person p = JsonIo.fromToon(toon).asClass(Person.class);

// Read TOON to Maps (no class needed)
Map map = JsonIo.fromToon(toon).asMap();
```

## Supported Types (60+ built-in)

json-io handles your **business objects, DTOs, and Records** automatically—no annotations required. It also provides optimized handling for these built-in types:

| Category | Types |
|----------|-------|
| Primitives | `byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char` + wrappers |
| Numbers | `BigInteger`, `BigDecimal`, `AtomicInteger`, `AtomicLong`, `AtomicBoolean` |
| Date/Time | `Date`, `Calendar`, `Instant`, `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Duration`, `Period`, `Year`, `YearMonth`, `MonthDay`, `TimeZone`, `ZoneId`, `ZoneOffset`, `java.sql.Date`, `Timestamp` |
| Strings | `String`, `StringBuffer`, `StringBuilder`, `char[]`, `CharBuffer` |
| Binary | `byte[]`, `ByteBuffer`, `BitSet` |
| IDs | `UUID`, `URI`, `URL`, `Class`, `Locale`, `Currency`, `Pattern`, `File`, `Path` |
| Geometric | `Color`, `Dimension`, `Point`, `Rectangle`, `Insets` |
| Other | `Enum` (any), `Throwable`, all `Collection`, `Map`, `EnumSet`, and array types |

See the [complete type comparison](/user-guide.md#toon-supported-types) showing json-io's comprehensive support vs other TOON implementations.

## Documentation

- [User Guide](/user-guide.md)
- [WriteOptions Reference](/user-guide-writeOptions.md)
- [ReadOptions Reference](/user-guide-readOptions.md)
- [Spring Integration Guide](/user-guide-spring.md)
- [Revision History](/changelog.md)

## Release [![Maven Central](https://img.shields.io/maven-central/v/com.cedarsoftware/json-io)](https://central.sonatype.com/artifact/com.cedarsoftware/json-io)

| | |
|---|---|
| **Bundling** | JPMS & OSGi |
| **Java** | JDK 1.8+ (multi-release JAR with module-info.class) |
| **Package** | com.cedarsoftware.io |

**API** — Static methods on [JsonIo](/json-io/src/main/java/com/cedarsoftware/io/JsonIo.java): `toJson()`, `toJava()`, `toMaps()`, `toToon()`, `fromToon()`, `formatJson()`, `deepCopy()`

Configure via [ReadOptionsBuilder](/user-guide-readOptions.md) and [WriteOptionsBuilder](/user-guide-writeOptions.md). Use [ClassFactory](/json-io/src/main/java/com/cedarsoftware/io/ClassFactory.java) for difficult-to-instantiate classes.

### Logging

json-io uses `java.util.logging` to minimize dependencies. See the [user guide](/user-guide.md#redirecting-javautillogging) to route logs to SLF4J or Log4j 2.

---

For useful Java utilities, check out [java-util](http://github.com/jdereg/java-util)
