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

LLM applications waste 40-50% of their token budget on JSON syntax overhead — braces, brackets, quotes, and commas that carry zero semantic information. **json-io** is a Java serialization library that reads and writes **JSON**, **JSON5**, and **[TOON](https://toonformat.dev/)** (Token-Oriented Object Notation) — a format that strips that overhead while preserving full data fidelity. It also handles what Jackson/Gson cannot: cyclic object graphs, automatic polymorphic types, and zero-config serialization of 60+ built-in Java types.

Featured on [json.org](http://json.org) and [Baeldung](https://www.baeldung.com/java-json-toon-format-libraries). See the [interactive JSON vs TOON comparison](https://jdereg.github.io/json-io/compare/).

## Quick Start

```java
// JSON
String json = JsonIo.toJson(myObject);
MyClass obj = JsonIo.toJava(json).asClass(MyClass.class);

// JSON5
String json5 = JsonIo.toJson(myObject, new WriteOptionsBuilder().json5().build());

// TOON (~40-50% fewer tokens than JSON)
String toon = JsonIo.toToon(myObject);
MyClass obj = JsonIo.fromToon(toon).asClass(MyClass.class);

// Standard JSON (Jackson-compatible output — no @type, no @id/@ref, stringified map keys)
String json = JsonIo.toJson(myObject, new WriteOptionsBuilder().standardJson().build());
```

## Installation

**Gradle**
```groovy
implementation 'com.cedarsoftware:json-io:4.101.0'
```

**Maven**
```xml
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io</artifactId>
  <version>4.101.0</version>
</dependency>
```

Latest version: [![Maven Central](https://img.shields.io/maven-central/v/com.cedarsoftware/json-io)](https://central.sonatype.com/artifact/com.cedarsoftware/json-io)

---

<details>
<summary><strong style="font-size:1.4em">JSON</strong></summary>

### Usage

```java
// Write JSON
String json = JsonIo.toJson(myObject);
JsonIo.toJson(outputStream, myObject, writeOptions);

// Read JSON to typed Java objects
Employee emp = JsonIo.toJava(json).asClass(Employee.class);
Employee emp = JsonIo.toJava(inputStream, readOptions).asClass(Employee.class);

// Read JSON to Maps (no classes needed)
Map map = JsonIo.toMaps(json).asMap();

// Generic types
List<Employee> list = JsonIo.toJava(json).asType(new TypeHolder<List<Employee>>(){});
```

### Standard JSON Mode

Use `standardJson()` to produce output identical to Jackson — no proprietary metadata:

```java
WriteOptions opts = new WriteOptionsBuilder().standardJson().build();
String json = JsonIo.toJson(myObject, opts);
```

This sets: `showTypeInfoNever`, `cycleSupport(false)`, `stringifyMapKeys(true)`, and `useMetaPrefixDollar()`. These will become the defaults in json-io 5.0.0.

### json-io vs Jackson vs Gson

**Object graph handling**

| Capability | json-io | Jackson | Gson |
|------------|---------|---------|------|
| Cyclic object graphs | Automatic (`$id`/`$ref`) | Requires `@JsonIdentityInfo` per class | `StackOverflowError` |
| Shared object references | Preserved automatically | Requires `@JsonIdentityInfo` per class | Duplicated (no identity) |
| Polymorphic types | Automatic with `showTypeInfoMinimal()` — no annotations needed; also supports `@IoTypeInfo` / `@JsonTypeInfo` annotations | Requires `@JsonTypeInfo` per hierarchy | Requires custom `TypeAdapter` |
| Unknown `$type` values | Graceful fallback to `Map` | Exception | Exception |

**Map handling**

| Capability | json-io | Jackson | Gson |
|------------|---------|---------|------|
| `Map<String, V>` | Standard JSON object | Same | Same |
| `Map<Long, V>`, `Map<UUID, V>`, etc. | Stringified keys (`stringifyMapKeys`) or `$keys`/`$items` fallback | `toString()` on keys (broken for POJOs without override) | `enableComplexMapKeySerialization()` |
| `Map<POJO, V>` (complex keys) | Full object preservation via `$keys`/`$items` | `toString()` (lossy) | JSON array of key-value pairs |

**Format & configuration**

| Capability | json-io | Jackson | Gson |
|------------|---------|---------|------|
| Standard JSON output | `.standardJson()` — identical to Jackson | Default | Default |
| JSON5 support | Full read/write | None | None (Lenient mode is partial) |
| TOON support | Full read/write (~40-50% fewer tokens) | None | None |
| Configuration | Zero-config; optional `@Io*` annotations | Annotation-heavy (`@Json*` required for advanced features) | Moderate (annotations + builders) |
| Jackson annotations | Recognized reflectively (zero compile-time dependency) | Native | Not supported |
| Two parse modes | `toJava()` (typed) + `toMaps()` (schema-free) | Typed only (or manual `JsonNode` tree) | Typed only (or manual `JsonElement` tree) |

**Runtime**

| Capability | json-io | Jackson | Gson |
|------------|---------|---------|------|
| Performance (simple DTOs) | 1.5-2x slower than Jackson | Fastest | ~1.3x slower than Jackson |
| Dependencies | java-util only (~1MB total) | Multiple JARs (~2.5MB+) | Single JAR (~300KB) |
| Java version | JDK 8+ | JDK 8+ | JDK 8+ |

**On performance:** Jackson is faster for simple DTOs. In real-world applications, serialization is typically <1% of total request time — the rest is network I/O, database queries, and business logic. json-io's additional capabilities (cycles, polymorphism, zero-config, JSON5, TOON) often matter more than raw serialization throughput.

**Performance tip:** Use `cycleSupport(false)` for ~35-40% faster writes when your data is acyclic (DTOs, POJOs, tree-shaped data).

### Key Features

- Two modes: typed Java objects (`toJava()`) or class-independent Maps (`toMaps()`)
- Preserves object references and handles cyclic relationships via `$id`/`$ref`
- Supports polymorphic types and complex object graphs
- Zero external dependencies (other than java-util)
- Stringify-able map keys: `Map<Long, V>` writes `{"100": value}` with `stringifyMapKeys(true)`
- Extensive configuration via `ReadOptionsBuilder` and `WriteOptionsBuilder`
- Parse JSON with unknown `$type` references into a Map-of-Maps without requiring classes on classpath

</details>

<details>
<summary><strong style="font-size:1.4em">JSON5</strong></summary>

[JSON5](https://json5.org/) is an extension to JSON that makes it more human-friendly. json-io provides **complete JSON5 support** for both reading and writing — the only major Java JSON library to do so natively.

### Reading JSON5

json-io accepts all JSON5 extensions by default — no configuration needed:

```java
String json5 = "{ name: 'John', age: 30, /* comment */ }";
Person p = JsonIo.toJava(json5).asClass(Person.class);
```

Supported: single-line (`//`) and multi-line (`/* */`) comments, single-quoted strings, unquoted keys, trailing commas, hex integers, `Infinity`/`NaN`/`-Infinity`, and more.

### Writing JSON5

```java
WriteOptions opts = new WriteOptionsBuilder().json5().build();
String json5 = JsonIo.toJson(myObject, opts);
```

The `json5()` umbrella enables:
- **Unquoted keys** — object keys that are valid identifiers are written without quotes
- **Smart quotes** — strings containing `"` (but not `'`) use single quotes
- **Infinity/NaN literals** — special float/double values as literals instead of `null`
- **Stringify map keys** — `Map<Long, V>` writes `{100: value}` instead of `$keys`/`$items`
- **No type info** — `showTypeInfoNever()` + `cycleSupport(false)` for clean output

Individual features can be enabled separately. See the [User Guide](/user-guide.md#json5-support) for details.

</details>

<details>
<summary><strong style="font-size:1.4em">TOON</strong></summary>

[TOON](https://toonformat.dev/) (Token-Oriented Object Notation) is an indentation-based format that produces **~40-50% fewer tokens** than JSON — ideal for LLM applications where token count directly impacts cost and context window usage.

- **No braces, brackets, or commas** — structure is expressed through indentation
- **No quoting** for most keys and values — quotes only when needed
- **Compact arrays** — inline `[N]: a,b,c` or list format with `- ` prefixed elements
- **Tabular format** — arrays of uniform objects as CSV-like rows with column headers
- **Key folding** — nested keys like `address.city: Denver` flatten one level of nesting
- **Full fidelity** — most data requires no extra metadata at all

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

### Usage

```java
// Write TOON
String toon = JsonIo.toToon(myObject);

// Read TOON back to typed Java object
Person p = JsonIo.fromToon(toon).asClass(Person.class);

// Read TOON to Maps (no class needed)
Map map = JsonIo.fromToon(toon).asMap();
```

Request TOON format for LLM applications: `Accept: application/vnd.toon`

### vs JToon

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

</details>

<details>
<summary><strong style="font-size:1.4em">Spring Boot Integration</strong></summary>

json-io provides a Spring Boot starter for seamless integration with Spring MVC and WebFlux applications.

```xml
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io-spring-boot-starter</artifactId>
  <version>4.101.0</version>
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

### Spring AI Integration

json-io provides a Spring AI module that reduces LLM token usage by ~40-50% using TOON format for tool call results and structured output parsing.

```xml
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io-spring-ai-toon</artifactId>
  <version>4.101.0</version>
</dependency>
```

Auto-configured: tool call results are serialized to TOON automatically. For structured output, use `ToonBeanOutputConverter<T>`:

```java
ToonBeanOutputConverter<Person> converter = new ToonBeanOutputConverter<>(Person.class);
Person person = chatClient.prompt()
    .user("Get info about John")
    .call()
    .entity(converter);
```

**Also supports WebFlux and WebClient** for reactive applications.

See the [Spring Integration Guide](/user-guide-spring.md) for configuration options, WebFlux usage, customizers, and Jackson coexistence modes.

</details>

<details>
<summary><strong style="font-size:1.4em">Annotations, Types &amp; Configuration</strong></summary>

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
| `@IoTypeInfo(LinkedList.class)` | Field | Default concrete type when `$type` absent; also eliminates `$type` on write when runtime type matches |
| `@IoDeserialize(as=LinkedList.class)` | Field/Class | Force type override during deserialization; also eliminates `$type` on write when runtime type matches |
| `@IoClassFactory(MyFactory.class)` | Class | Specify a ClassFactory for deserialization |
| `@IoGetter("fieldName")` | Method | Custom getter method for serialization |
| `@IoSetter("fieldName")` | Method | Custom setter method for deserialization |
| `@IoNonReferenceable` | Class | Suppress `$id`/`$ref` for instances of this type |
| `@IoNotCustomReader` | Class | Suppress custom reader (use standard deserialization) |
| `@IoNotCustomWritten` | Class | Suppress custom writer (use standard serialization) |
| `@IoCustomWriter(MyWriter.class)` | Class | Specify custom `JsonClassWriter` for serialization |
| `@IoCustomReader(MyReader.class)` | Class | Specify custom `JsonClassReader` for deserialization |
| `@IoTypeName("ShortName")` | Class | Alias for `$type` in JSON (replaces FQCN) |
| `@IoAnySetter` | Method | Receive unrecognized JSON fields during deserialization |
| `@IoAnyGetter` | Method | Provide extra fields during serialization |
| `@IoFormat("pattern")` | Field | Per-field format pattern (`String.format`, `DecimalFormat`, `DateTimeFormatter`, or `SimpleDateFormat`) |

Additionally, json-io **reflectively honors Jackson annotations** when they are on the classpath — with zero compile-time dependency on Jackson. Supported: `@JsonProperty`, `@JsonIgnore`, `@JsonIgnoreProperties`, `@JsonAlias`, `@JsonPropertyOrder`, `@JsonInclude`, `@JsonCreator`, `@JsonValue`, `@JsonIgnoreType`, `@JsonTypeInfo`, `@JsonIncludeProperties`, `@JsonNaming`, `@JsonDeserialize`, `@JsonGetter`, `@JsonSetter`, `@JsonTypeName`, `@JsonFormat`, `@JsonAnySetter`, `@JsonAnyGetter`.

**Precedence:** Programmatic API > json-io annotations > Jackson annotations.

See the [Annotations section of the User Guide](/user-guide.md#annotations) for full details and examples.

### Supported Types (60+ built-in)

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

### Key Features

- Fully compatible with both JPMS and OSGi environments
- Zero external dependencies (other than java-util)
- Lightweight (`json-io.jar` is ~330K, `java-util` is ~700K)
- Compatible with JDK 1.8 through JDK 24
- The library is built with the `-parameters` compiler flag. Parameter names are now retained for tasks such as constructor discovery.
- Optional unsafe mode for deserializing package-private classes, inner classes, and classes without accessible constructors (opt-in for security)
- Extensive configuration options via `ReadOptionsBuilder` and `WriteOptionsBuilder`

</details>

## Documentation

- [User Guide](/user-guide.md)
- [WriteOptions Reference](/user-guide-writeOptions.md)
- [ReadOptions Reference](/user-guide-readOptions.md)
- [Spring Integration Guide](/user-guide-spring.md)
- [Revision History](/changelog.md)

**Articles & Tutorials**
- [JSON, TOON, and Java Format Libraries](https://www.baeldung.com/java-json-toon-format-libraries) — Baeldung tutorial covering JSON, JSON5, and TOON serialization with json-io

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
