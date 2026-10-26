## Usage
**json-io** supports two formats for both reading and writing:
- **JSON** — Standard JSON and JSON5 for universal interoperability
- **TOON** — [Token-Oriented Object Notation](https://toonformat.dev/) for LLM-optimized serialization (~40-50% fewer tokens)

Both formats can be used directly with Strings or Java's Streams.

> **Need a streaming cursor instead of databind?** Since 4.103.0, json-io also exposes a Jackson-shaped streaming API — `JsonIo.createGenerator(...)` and `JsonIo.createTokenizer(...)` — for hand-rolled write/read pipelines that bypass the tree-builder entirely. See [Streaming API](#streaming-api-cursor-style-readwrite) below for use, exception conventions, custom-reader/-writer migration notes, and binary payload handling.

### Typed Usage

_Example 1: Java object graph to JSON String_
```java
Employee emp;
// Emp fetched from database
String json = JsonIo.toJson(emp, writeOptions);
```
This example will convert the `Employee` instance to a JSON String, including nested sub-objects.  If
`JsonIo.toJava(json, readOptions).asClass(Employee.class)` were used on this JSON `String,` a new Java `Employee` instance would be returned.

**Performance tip:** If your data has no circular references (e.g., DTOs, simple POJOs), use `cycleSupport(false)` for ~35-40% faster serialization:
```java
WriteOptions fastOptions = new WriteOptionsBuilder().cycleSupport(false).build();
String json = JsonIo.toJson(emp, fastOptions);
```

See [WriteOptions reference](/user-guide-writeOptions.md) for list of all `WriteOptions` and instructions on how to use.

_Example 2: String to Java object_
```java
String json = // String JSON representing Employee instance
Employee employee = JsonIo.toJava(json, readOptions).asClass(Employee.class);
```
This will convert the JSON String back to a Java Object graph.

See [ReadOptions reference](/user-guide-readOptions.md) for a list of all `ReadOptions` and instructions on how to use.

_Example 3: Java Object to `OutputStream`_
```java
Employee emp;
// emp obtained from data store...
JsonIo.toJson(outputStream, emp, writeOptions);       
```
In this example, a Java object is written to an `OutputStream` in JSON format.  The stream is closed when finished.  If
you need to keep the `OutputStream` open (e.g. NDJSON), then set `writeOptions.closeStream(false).` Example:
```java
WriteOptions writeOptions = new WriteOptionsBuilder().closeStream(false).build();
JsonIo.toJson(outputStream, record1, writeOptions);    
JsonIo.toJson(outputStream, record2, writeOptions);
...
JsonIo.toJson(outputStream, recordn, writeOptions);
outputStream.close();
```

_Example 4: `InputStream` to Java object_
```java
List<Employee> list = JsonIo.toJava(stream, readOptions).asType(new TypeHolder<List<Employee>>(){});
```
In this example, an `InputStream` is supplying the JSON.

### Untyped Usage (Map Mode)
**json-io** provides two distinct modes for reading JSON. In addition to typed Java objects, you can use the **Map Mode**
which returns a generic `Map<String, Object>` graph without requiring Java classes on the classpath.

**Use `toMaps()` for class-independent JSON parsing:**

```java
// Parse JSON object to Map
Map<String, Object> root = JsonIo.toMaps(json).asClass(Map.class);

// Parse JSON array to List
List<Object> list = JsonIo.toMaps("[1,2,3]").asClass(List.class);

// Parse any JSON type (primitives, objects, arrays)
Object result = JsonIo.toMaps(json).asClass(null);

// Or from InputStream
Map<String, Object> root = JsonIo.toMaps(inputStream).asClass(Map.class);

// With custom options
ReadOptions readOptions = new ReadOptionsBuilder()
    .aliasTypeName("OldClass", "NewClass")
    .build();
Map<String, Object> root = JsonIo.toMaps(json, readOptions).asClass(Map.class);
```

The `toMaps()` API automatically configures the reader for Map mode and allows unknown `@type` values to be parsed
successfully. It returns a `JsonValue` builder that allows you to extract the result as any type (Map, List, primitives, etc.).
The parsed structure forms a graph consisting of `Map` instances (actually `JsonObject` with deterministic
LinkedHashMap ordering), arrays, and primitive types. `asClass(Map.class)` is the graph's own root object -- change it
and write it back -- while a concrete class (`asClass(LinkedHashMap.class)`, `asClass(TreeMap.class)`) is a copy into
that class. A `JsonObject` follows the `Map` contract: it equals any `Map` with the same entries, in any order.

### Asking Map Mode for a Type at the Root
`asClass()` and `asType()` shape the root; below it the tree stays JSON. A value below the root that does not convert
-- `{"@type":"BigDecimal","value":"abc"}`, a date that is no date, a constant its enum does not have, `"three"` where
the root's `@type` class declares an `int` -- stays the JSON that holds it, as written: the read goes on, and written
back the text is the same. (An EnumSet holding a name its enum does not have is an error, naming it.) The root is the
type asked for, or a `JsonIoException` naming why the JSON cannot be it, as in Java mode:
- **A value type or an array** (`Integer`, `LocalDate`, `ZonedDateTime`, an enum; `LocalDate[]`, `int[]`, `Map[]`):
  converted from the JSON value or array.
- **`Map`, `List`, `Set`, or a concrete Map or Collection class**: the tree's root, or a copy into that class. A JSON
  object is not a list: asked for as one, it is an error.
- **A class** (`Person`, `Person[]`, `Optional<Person>`), **or type arguments that ask for more than JSON's own values**
  (`List<LocalDate>`, `Map<String, Person>`): built as Java mode builds them from the tree -- a conversion registered
  from `Map` to the class, its `ClassFactory`, a creator, its constructors. A class the tree names that is not on the
  classpath stays a map. This reads the tree a second time; when the classes are known, `toJava()` is faster.

To take the class where the JSON is one and the JSON where it is not, read the tree first, then the class from it:
```java
JsonObject tree = JsonIo.toMaps(input).asClass(JsonObject.class);
try {
    Person person = JsonIo.toJava(tree, readOptions).asClass(Person.class);
} catch (JsonIoException e) {
    // e names why; the tree is the JSON as it is
}
```

### Representation of JSON Structures as Maps
When `Map` is returned, the root value can represent one of the following:
- **JSON Object (`{...}`)**: Transformed into a `Map` that represents any JSON object `{...}`.
- **JSON Array (`[...]`)**: Represented as a `Map` with a key of `@items` which holds the list representing the JSON array `[...]`.
- **JSON Primitive**: Such as boolean (true/false), null, numbers (long, double), and strings, directly represented as their Java equivalents.

### Manipulating and Rewriting JSON
This `Map` representation can be rewritten to a JSON String or Stream, ensuring that the output JSON will match the
original input JSON stream. This feature is especially useful for handling JSON strings or streams containing class
references not present in the JVM parsing the JSON. It allows complete reading and potential manipulation of the content,
followed by rewriting the String or stream, providing a robust solution for dynamic data handling.

_Example: Manipulate JSON without having the referenced classes_
```java
// Read JSON into Map structure (no classes required!)
Map<String, Object> jsonMap = JsonIo.toMaps(jsonString).asClass(Map.class);

// Manipulate values
jsonMap.put("name", "John Doe");
((Map)jsonMap.get("address")).put("city", "New York");

// Write back to JSON
String updatedJson = JsonIo.toJson(jsonMap, writeOptions);
```

Each `JsonObject` retains the raw `@type` value from the input JSON. You can safely cast to `JsonObject`
to access this metadata:
```java
JsonObject obj = JsonIo.toMaps(jsonString).asClass(JsonObject.class);
String originalType = obj.getTypeString();  // Preserved @type value
```

### Parsing JSON with Unknown Classes
If the JSON contains class references (`@type` entries) that are not available on the classpath,
use the `toMaps()` API which automatically handles unknown types gracefully:

```java
// Simple - automatically allows unknown types
Map<String, Object> graph = JsonIo.toMaps(json).asClass(Map.class);
```

The `toMaps()` method automatically configures the reader to skip type resolution and return a graph of `Map`
instances. This allows arbitrary JSON to be loaded, inspected, and re-serialized without requiring the
referenced classes on the classpath.

All objects will be represented as Maps (or collections) so the entire structure can be traversed or modified.
The original `@type` strings are preserved in the JsonObject metadata for later use if needed.

**Advanced:** If you need strict type validation even in Map mode, you can override the default:
```java
ReadOptions opts = new ReadOptionsBuilder()
        .failOnUnknownType(true)  // Override default
        .build();
Map<String, Object> graph = JsonIo.toMaps(json, opts).asClass(Map.class);  // Will throw if unknown @type found
```

### Generic Type Support
For working with generic types like `List<Employee>` or complex nested generics, use the `TypeHolder` class to preserve full generic type information:

```java
// Reading a list of employees
List<Employee> employees = JsonIo.toJava(json, readOptions)
                                .asType(new TypeHolder<List<Employee>>(){});

// Reading a complex nested structure
Map<String, List<Department<Employee>>> orgMap = JsonIo.toJava(json, readOptions)
                                .asType(new TypeHolder<Map<String, List<Department<Employee>>>>(){});
```

The type arguments apply at the root as they do in a field: a list's elements, a map's keys and values, all the way
down -- also to plain JSON with no `@type` (Jackson's, a REST service's). Before 4.113.0 the root's arguments were
not applied: `[{"name":"ann"}]` read with `TypeHolder<List<Employee>>` gave a list of maps, and numbers came back as
`Long` and `Double`.

### Which Class You Get for an Interface

Ask for an interface or an abstract container type -- with `asClass()`, `asType()`, or as a field's declared type --
and json-io builds this class, in JSON and in TOON alike, when the JSON does not name one with `@type`:

| Asked for | Built |
|---|---|
| `Map`, `AbstractMap` | `LinkedHashMap` |
| `SortedMap`, `NavigableMap` | `TreeMap` |
| `ConcurrentMap` | `ConcurrentHashMapNullSafe` (java-util) |
| `ConcurrentNavigableMap` | `ConcurrentNavigableMapNullSafe` (java-util) |
| `Collection`, `List`, `AbstractCollection`, `AbstractList` | `ArrayList` |
| `Set`, `AbstractSet` | `LinkedHashSet` |
| `SortedSet`, `NavigableSet` | `TreeSet` |
| `Queue`, `AbstractSequentialList` | `LinkedList` |
| `Deque` | `ArrayDeque` |
| `AbstractQueue` | `ConcurrentLinkedQueue` |
| `BlockingQueue` | `LinkedBlockingQueue` (unbounded) |
| `BlockingDeque` | `LinkedBlockingDeque` (unbounded) |
| `TransferQueue` | `LinkedTransferQueue` |

- The concurrent maps are java-util's null-safe ones because JSON carries null values, which `ConcurrentHashMap`
  and `ConcurrentSkipListMap` refuse.
- A `@type` in the JSON wins: `{"@type":"TreeMap",...}` read as a `Map` is a `TreeMap`.
- Ask for a concrete class to get exactly that class: `asClass(LinkedList.class)`, a `ConcurrentHashMap` field.
- An interface json-io knows no class for -- your own `interface Registry extends Map<String, Object>` -- is an
  error that names it; register a `ClassFactory` for it (`ReadOptionsBuilder.addClassFactory`).

`ContainerTypeAskedForTest` pins this table, so it changes only with a test.

## Jackson Compatibility

**json-io is Jackson-compatible — just call `.standardJson()` on your `WriteOptionsBuilder`.**

Out-of-the-box, json-io writes its own flexible format with optional `@type`/`@id`/`@ref` metadata
for perfect round-trip fidelity across any Java object graph. One flag — `.standardJson()` — flips
every default to match what Jackson (with `JavaTimeModule` and the Spring Boot default of
`WRITE_DATES_AS_TIMESTAMPS=false`) produces for the same data. No proprietary metadata, ISO-8601
dates, stringified Map keys, Jackson-style Optional handling.

| Feature | Jackson (Spring Boot default) | json-io with `.standardJson()` |
|---|---|---|
| POJO fields | `{"name":"Alice","age":30}` | `{"name":"Alice","age":30}` ✓ identical |
| `java.util.Date` | `"2026-04-18T10:30:00.000+00:00"` (ISO-8601) | `"2026-04-18T10:30:00Z"` (ISO-8601) ✓ |
| `java.time.*` types | ISO-8601 strings | ISO-8601 strings ✓ |
| `Map<Long, V>` / `Map<UUID, V>` | `{"100":v,"200":v}` | `{"100":v,"200":v}` ✓ |
| `Optional.of("x")` | `"x"` | `"x"` ✓ |
| `Optional.empty()` | `null` | `null` ✓ |
| Shared `List<String>` across two fields | two independent copies | two independent copies ✓ |
| Type metadata | none (controlled by annotations) | none ✓ |
| Cycle tracking | none (throws `StackOverflowError`) | none (throws `JsonIoException` with clear hint) |

```java
// Jackson-compatible output:
WriteOptions opts = new WriteOptionsBuilder().standardJson().build();
String json = JsonIo.toJson(myObject, opts);
```

json-io also offers **JSON5** output (relaxed JSON with unquoted keys, single quotes, trailing commas)
and **TOON** — a token-oriented format that uses 40-50% fewer tokens than equivalent JSON, ideal for
LLM prompts and agent workflows. Both formats default to Jackson-aligned semantics (no `@id`/`@ref`,
no proprietary metadata) without needing `.standardJson()`.

Need the full json-io feature set (cycle preservation, polymorphic `@type` metadata, `@id`/`@ref`
reference tracking)? Simply don't call `.standardJson()`, or override specific flags after calling
it (e.g., `.standardJson().cycleSupport(true)` to turn reference tracking back on).

**Migrating Jackson's `setPropertyNamingStrategy(...)`** — if your Jackson code configures a global
naming strategy (snake_case, kebab-case, UpperCamel, etc.) on the `ObjectMapper`, json-io has a
direct equivalent: `WriteOptionsBuilder.namingStrategy(IoNaming.Strategy.SNAKE_CASE)`. All six
Jackson strategies are supported (`SNAKE_CASE`, `UPPER_SNAKE_CASE`, `KEBAB_CASE`, `UPPER_CAMEL_CASE`,
`LOWER_DOT_CASE`, `LOWER_CASE`). Per-class `@IoNaming` / `@JsonNaming` and per-field `@IoProperty`
/ `@JsonProperty` still win. See
[user-guide-writeOptions.md](user-guide-writeOptions.md#global-naming-strategy) for details.

### JDK Types Compared with Jackson

json-io writes and reads back more JDK types than Jackson does. Measured for 4.113.0 by writing 104 JDK values —
atomics, numbers, dates and times, identifiers, enums, collections and maps, exceptions, records — and reading each
back as its declared type (json-io defaults; Jackson 2.22 with its `jdk8` and `JavaTimeModule` modules, and
Jackson 3.1):

| | Values read back intact |
|---|---|
| json-io, JDK 21 | 100 of 104 |
| json-io, JDK 8 | 100 of 104 |
| Jackson 2.22 (with modules) / Jackson 3.1 | 84 of 104 |

No type in the set reads back intact with Jackson but not with json-io. Where json-io keeps what Jackson loses:

| Type | json-io | Jackson |
|---|---|---|
| `java.sql.Timestamp` | nanoseconds kept | milliseconds only |
| `ZonedDateTime`, `OffsetDateTime` | zone and offset kept | converted to UTC |
| `Calendar` / `GregorianCalendar` | time zone kept | read back in UTC |
| An exception with a cause | the cause's class kept | the cause becomes a plain `Throwable` |
| `BitSet` | its bits | written as `{"empty":false}`; cannot be read back |
| `AtomicIntegerArray`, `AtomicLongArray`, `AtomicReferenceArray` | read back | cannot be written (2.x) or read back (3.x) |
| `AtomicMarkableReference`, `AtomicStampedReference` | read back, with the mark or stamp | written; cannot be read back |
| `Pattern` with flags (`CASE_INSENSITIVE`) | the flags kept, written inline (`"(?i)a+b"`): it matches what it matched | the flags lost: `"a+b"` reads back case-sensitive |
| `AbstractMap.SimpleEntry`, `SimpleImmutableEntry` | read back | cannot be read back |
| `LongAdder`, `DoubleAdder` | read back, as their sum | written as a number; cannot be read back |
| `InetAddress` | the IP address, and a host name it carries — read back without any DNS lookup | the host name if it has one, else the address; a host name is resolved by DNS when read |

json-io never looks up a host name while reading: an `InetAddress` is read from an IP address, and a host name
travels beside it (`"build-server/10.0.0.7"`). A bare host name is rejected rather than resolved, so input cannot
make a read wait on DNS or reach a host of its choosing.

`AtomicReferenceArray`, `AtomicMarkableReference` and `AtomicStampedReference` keep their state in fields the JDK
closes from JDK 17 on, where Gson cannot write them either. json-io writes them through their public methods, as
`{"array":[...]}` and `{"pair":{"reference":...,"mark":...}}` (or `"stamp"`) — the JSON it and Gson wrote on JDK 8
and 11, so that JSON reads back too — and reads them back on every JDK: elements and references typed by the field's
generics (`AtomicReferenceArray<Item>`), objects shared with the rest of the graph kept shared, cycles kept, in JSON
and TOON.

Of the 104 values, the four json-io does not read back exactly are a `TreeMap` and a `TreeSet` with comparators and an
`ArrayBlockingQueue`'s capacity, below, and a `Pattern` with flags, which matches what it matched but whose text comes
back with the flags written inline (`"(?i)a+b"` for `"a+b"` compiled `CASE_INSENSITIVE`).

What neither keeps:

- **A `TreeMap`, `TreeSet` or `ConcurrentSkipList*` comparator** (such as `String.CASE_INSENSITIVE_ORDER`): the
  entries come back in natural order. Comparators are code, not data.
- **An `ArrayBlockingQueue`'s capacity**: its items come back, in a queue as large as its items (as Jackson does).
- **The enum class of an empty `EnumMap`** read with no declared type: declare the field as `EnumMap<YourEnum, V>`.

## Standard JSON Output

json-io can produce standard JSON output identical to Jackson and other mainstream libraries — no
proprietary metadata, no `@type`, no `@id`/`@ref`, and Maps with numeric/UUID/Enum keys written
as regular JSON objects. Use the `standardJson()` convenience method:

```java
WriteOptions options = new WriteOptionsBuilder()
        .standardJson()    // Produces Jackson-compatible output
        .build();

String json = JsonIo.toJson(myObject, options);
```

This sets: `showTypeInfoNever`, `showRootTypeInfo(false)`, `cycleSupport(false)`, `stringifyMapKeys(true)`,
`writeOptionalAsObject(false)`, `preserveLeafContainerIdentity(false)`, and `useMetaPrefixDollar()`. These
will become the defaults in json-io 5.0.0. Individual settings can be overridden after the call
(e.g., `.standardJson().cycleSupport(true)`).

### Leaf-Container Identity (4.101.0 default change)

As of 4.101.0, when writing with `cycleSupport=true`, shared-identity tracking for containers whose
declared element types are all non-referenceable leaves (`List<String>`, `Map<UUID, Date>`, `byte[]`,
`String[]`, etc.) is **off by default** — each reference to such a container is serialized
independently, matching Jackson's default behavior. POJO-holding containers (`List<Foo>`, `Map<String, Foo>`)
still have identity traced. If you need the legacy behavior — shared `@id`/`@ref` for leaf-element
containers — use `preserveLeafContainerIdentity(true)`. See
[user-guide-writeOptions.md](user-guide-writeOptions.md#leaf-container-identity--preserveleafcontaineridentity-4101-0)
for details.

### `Optional` Serialization

As of json-io 4.101.0, `Optional`, `OptionalInt`, `OptionalLong`, and `OptionalDouble` values are
serialized in Jackson/Gson-compatible primitive form by default for JSON, JSON5, and TOON:

- `Optional.empty()` → `null`
- `Optional.of("hello")` → `"hello"` (bare value)
- `OptionalInt.of(42)` → `42` (bare number)

Reading accepts both the primitive form and the legacy json-io object form
`{"present":true,"value":X}` so JSON produced by older json-io versions still deserializes correctly. Both are
read as an `Optional` of the declared type in a field, and (since 4.113.0) as an element of a collection or an
array (`List<Optional<String>>`, `Optional<Item>[]`): the elements were left bare there, so such a list held
Strings. TOON writes an `Optional` as its content wherever it sits, a list element included.
When interoperating with **pre-4.101.0** json-io readers (that cannot parse the primitive form), call
`writeOptionalAsObject(true)` on the WriteOptionsBuilder. `standardJson()` always resets this toggle to
`false` for maximum interoperability.

See [WriteOptions reference](/user-guide-writeOptions.md#standard-json-output--standardjson) for details.

---
## JSON5 Support

[JSON5](https://json5.org/) is an extension to JSON that makes it more human-friendly by adding features
inspired by ECMAScript 5. json-io provides **complete JSON5 support** for both reading and writing —
the only major Java JSON library to do so natively.

### Reading JSON5

By default, json-io accepts all JSON5 extensions. This means you can parse JSON5 files without any
configuration:

```java
// JSON5 is accepted by default - no configuration needed
String json5 = """
    {
        // This is a comment
        name: 'John',          // unquoted key, single-quoted string
        age: 30,               // trailing comma allowed
        salary: 0xFFFF,        // hexadecimal number
        rating: .95,           // leading decimal point
    }
    """;

Person person = JsonIo.toJava(json5, null).asClass(Person.class);
```

#### JSON5 Read Features

| Feature | Example | Supported |
|---------|---------|:---------:|
| **Single-line comments** | `// comment` | ✅ |
| **Block comments** | `/* comment */` | ✅ |
| **Unquoted object keys** | `{name: "John"}` | ✅ |
| **Single-quoted strings** | `{'name': 'John'}` | ✅ |
| **Trailing commas** | `[1, 2, 3,]` | ✅ |
| **Hexadecimal numbers** | `0xFF` or `0xff` | ✅ |
| **Leading decimal point** | `.5` (equals 0.5) | ✅ |
| **Trailing decimal point** | `5.` (equals 5.0) | ✅ |
| **Explicit positive sign** | `+5` | ✅ |
| **Infinity literal** | `Infinity`, `-Infinity` | ✅ |
| **NaN literal** | `NaN` | ✅ |
| **Multi-line strings** | `"line1\↵line2"` (backslash continuation) | ✅ |

#### Strict JSON Mode

If you need to enforce strict RFC 8259 JSON compliance (rejecting JSON5 extensions), use `strictJson()`:

```java
ReadOptions strictOptions = new ReadOptionsBuilder()
        .strictJson()
        .build();

// This will throw JsonIoException because of the comment
String json5 = "{ // comment\n\"name\": \"John\"}";
JsonIo.toJava(json5, strictOptions).asClass(Map.class);  // Throws!
```

When `strictJson()` is enabled, the following will cause parse errors:
- Comments (single-line or block)
- Unquoted object keys
- Single-quoted strings
- Trailing commas
- Hexadecimal numbers
- Non-standard number formats (.5, -.5, 5., 1.e5, +5, and leading zeros such as 01 or 00.5)
- Infinity and NaN literals
- Unescaped control characters (U+0000 through U+001F, such as a raw tab or line feed) inside a string, and the `\'` escape
- A key repeated within one object (RFC 8259: the names within an object SHOULD be unique)

Without `strictJson()`, a key repeated within an object is read once: its last value, where the key first stood -- as
JavaScript's `JSON.parse` and `LinkedHashMap.put` leave it. Written back, it appears once.

For TOON parsing strictness, use `strictToon()` on `ReadOptionsBuilder`:

```java
ReadOptions strictToonOptions = new ReadOptionsBuilder()
        .strictToon()
        .build();
```

`strictToon()` is **off by default**. Both modes read every well-formed TOON document alike, and both refuse
malformed input: a header that is not one, an unterminated string, a row of the wrong width, a line that belongs
to nothing. Non-strict mode differs by exactly the five recoveries the TOON spec allows a reader (§14.4), and no
others:

1. **Counts.** A declared count that does not match what is there (`items[3]:` with two items, or with four) is
   not an error; every item, row and value in the header's scope is read.
2. **Duplicate keys.** A key that stands twice in an object, or a field name twice in a table header, is not an
   error; the last one wins.
3. **Indentation.** A tab in the indentation counts as one level, and indentation that is not a multiple of the
   indent size is rounded down. A line of only spaces and tabs is blank.
4. **Blank lines.** A blank line inside an array or a keyed table is ignored.
5. **Depth jumps.** A nested block whose first line stands more than one level below its key or header is read
   from where it stands.

Strict mode refuses each of these. In neither mode is anything skipped or dropped without an error.

### Writing JSON5

json-io can also **write** JSON5 format, which no other major Java JSON library supports natively.
JSON5 write features are disabled by default to ensure maximum compatibility, but can be enabled
individually or via an umbrella option.

#### The `json5()` Umbrella Option

The simplest way to enable JSON5 writing is the `json5()` umbrella method, which enables the most
commonly useful features:

```java
WriteOptions json5Options = new WriteOptionsBuilder()
        .json5()    // Enables: unquoted keys, smart quotes, Infinity/NaN
        .build();

String json5 = JsonIo.toJson(myObject, json5Options);
```

The `json5()` umbrella enables:
- **Unquoted keys** — object keys that are valid identifiers are written without quotes
- **Smart quotes** — strings containing `"` (but not `'`) use single quotes for cleaner output
- **Infinity/NaN literals** — special float/double values written as literals instead of `null`
- **Stringify map keys** — `Map<Long, V>` writes `{100: value}` instead of `$keys`/`$items`

> **Note:** Trailing commas are **not** enabled by `json5()` — they require explicit opt-in since they
> provide no semantic benefit and some tools still don't accept them.

#### Individual JSON5 Write Options

For fine-grained control, enable features individually:

```java
WriteOptions options = new WriteOptionsBuilder()
        .json5UnquotedKeys(true)      // Write unquoted keys when valid identifiers
        .json5SmartQuotes(true)       // Use single quotes for strings with embedded "
        .json5InfinityNaN(true)       // Write Infinity/NaN as literals
        .json5TrailingCommas(true)    // Add trailing commas (explicit opt-in)
        .build();
```

#### JSON5 Write Features

| Option | Effect | Example Output |
|--------|--------|----------------|
| `json5UnquotedKeys(true)` | Keys that are valid ECMAScript identifiers are unquoted | `{name:"John"}` instead of `{"name":"John"}` |
| `json5SmartQuotes(true)` | Strings with `"` but no `'` use single quotes | `'He said "Hi"'` instead of `"He said \"Hi\""` |
| `json5InfinityNaN(true)` | Special values written as literals | `Infinity` instead of `null` |
| `json5TrailingCommas(true)` | Trailing comma after last element | `[1,2,3,]` and `{a:1,}` |

#### JSON5 Meta Key Prefixes

**Default behavior:** Standard JSON mode uses `@` prefix (quoted keys like `"@type":`), while JSON5 mode uses `$` prefix (unquoted keys like `$type:`). These defaults can be overridden using `useMetaPrefixAt()` or `useMetaPrefixDollar()`.

Combined with the short meta keys option, there are four possible meta key formats:

| Mode | Type | ID | Ref | Items | Keys |
|------|------|----|-----|-------|------|
| Standard | `"@type":` | `"@id":` | `"@ref":` | `"@items":` | `"@keys":` |
| Short | `"@t":` | `"@i":` | `"@r":` | `"@e":` | `"@k":` |
| JSON5 | `$type:` | `$id:` | `$ref:` | `$items:` | `$keys:` |
| JSON5 + Short | `$t:` | `$i:` | `$r:` | `$e:` | `$k:` |

**Why `$` instead of `@`?**
- In JSON5, object keys can be unquoted if they are valid ECMAScript identifiers
- `@` is **not** a valid identifier start character, so `@type` cannot be written unquoted
- `$` **is** a valid identifier start character, so `$type` can be written unquoted in JSON5
- `$` also has precedent in JSON Schema (`$schema`, `$id`, `$ref`)

**Reading compatibility:** json-io accepts all meta key variants when reading (`@type`, `@t`, `$type`, `$t`), ensuring backward compatibility regardless of which format was used to write the JSON.

```java
// JSON5 output with unquoted meta keys
WriteOptions json5Options = new WriteOptionsBuilder().json5().build();
String json5 = JsonIo.toJson(myObject, json5Options);
// Output: {$type:"com.example.MyClass",$id:1,...}

// JSON5 + short meta keys (most compact)
WriteOptions json5ShortOptions = new WriteOptionsBuilder()
        .json5()
        .shortMetaKeys(true)
        .build();
String json5Short = JsonIo.toJson(myObject, json5ShortOptions);
// Output: {$t:"com.example.MyClass",$i:1,...}

// Standard output with quoted meta keys
WriteOptions stdOptions = new WriteOptionsBuilder().build();
String stdJson = JsonIo.toJson(myObject, stdOptions);
// Output: {"@type":"com.example.MyClass","@id":1,...}

// Short meta keys (standard mode)
WriteOptions shortOptions = new WriteOptionsBuilder()
        .shortMetaKeys(true)
        .build();
String shortJson = JsonIo.toJson(myObject, shortOptions);
// Output: {"@t":"com.example.MyClass","@i":1,...}

// Reading works with any prefix
Object obj1 = JsonIo.toJava("{$type:\"java.util.HashMap\"}", null).asClass(Object.class);
Object obj2 = JsonIo.toJava("{$t:\"java.util.HashMap\"}", null).asClass(Object.class);
Object obj3 = JsonIo.toJava("{\"@type\":\"java.util.HashMap\"}", null).asClass(Object.class);
Object obj4 = JsonIo.toJava("{\"@t\":\"java.util.HashMap\"}", null).asClass(Object.class);
```

#### Overriding the Meta Key Prefix

You can force a specific prefix regardless of the JSON mode using `useMetaPrefixAt()` or `useMetaPrefixDollar()`:

```java
// Force @ prefix even in JSON5 mode (keys will be quoted since @ requires quotes)
WriteOptions options = new WriteOptionsBuilder()
        .json5()
        .useMetaPrefixAt()
        .build();
String json = JsonIo.toJson(myObject, options);
// Output: {"@type":"com.example.MyClass", name:"John", ...}

// Force $ prefix in standard JSON mode (keys will be quoted)
WriteOptions options = new WriteOptionsBuilder()
        .useMetaPrefixDollar()
        .build();
String json = JsonIo.toJson(myObject, options);
// Output: {"$type":"com.example.MyClass", "name":"John", ...}
```

This is useful for:
- **Interoperability**: When communicating with systems that expect a specific prefix
- **JSON Schema alignment**: The `$` prefix is used in JSON Schema (`$schema`, `$id`, `$ref`)
- **Migration**: Maintaining prefix consistency when transitioning between formats

See [WriteOptions Meta Key Prefix Override](/user-guide-writeOptions.md#meta-key-prefix-override) for detailed documentation.

#### Unquoted Keys Details

Keys are only unquoted if they are valid ECMAScript identifiers:
- Must start with: letter (a-z, A-Z), underscore (`_`), or dollar sign (`$`)
- May contain: letters, digits (0-9), underscores, or dollar signs
- Keys that don't meet these criteria remain quoted

```java
Map<String, Object> map = new LinkedHashMap<>();
map.put("validKey", 1);       // Will be unquoted: validKey:1
map.put("_private", 2);       // Will be unquoted: _private:2
map.put("$ref", 3);           // Will be unquoted: $ref:3
map.put("key-with-dash", 4);  // Will be quoted: "key-with-dash":4
map.put("123numeric", 5);     // Will be quoted: "123numeric":5
```

#### Smart Quotes Details

Smart quotes only affect string **values**, not keys. Keys are handled separately by `json5UnquotedKeys()`.

**Key behavior** (controlled by `json5UnquotedKeys`):
- Valid identifier → unquoted: `name:`
- Invalid identifier → double-quoted: `"key-with-dash":`
- Keys **never** use single quotes, even if they contain `"`

**Value behavior** (controlled by `json5SmartQuotes`):
- If string contains `"` but no `'` → single quotes (avoids escaping)
- Otherwise → double quotes (standard behavior)

```java
// With json5UnquotedKeys(true) and json5SmartQuotes(true):
map.put("name", "He said \"Hello\"");     // Output: name:'He said "Hello"'
map.put("key-dash", "He said \"Hi\"");    // Output: "key-dash":'He said "Hi"'
map.put("msg", "It's fine");              // Output: msg:"It's fine"
```

Note how `"key-dash"` uses double quotes (invalid identifier) while its value uses single quotes (contains `"`).

#### Complete Example

```java
// Create test data
Map<String, Object> data = new LinkedHashMap<>();
data.put("name", "John");
data.put("message", "He said \"Hello\"");
data.put("score", Double.POSITIVE_INFINITY);
data.put("rating", Double.NaN);

// Write with full JSON5 features
WriteOptions options = new WriteOptionsBuilder()
        .json5()                      // Enable umbrella features
        .json5TrailingCommas(true)    // Also enable trailing commas
        .prettyPrint(true)
        .showTypeInfoNever()
        .build();

String json5 = JsonIo.toJson(data, options);
```

Output:
```json5
{
  name: "John",
  message: 'He said "Hello"',
  score: Infinity,
  rating: NaN,
}
```

This JSON5 output can be read back by json-io (or any JSON5-compliant parser) without any issues.

## TOON Support

[TOON (Token-Oriented Object Notation)](https://toonformat.dev/) is a compact, human-readable format
optimized for LLM token efficiency, using approximately **40-50% fewer tokens** than equivalent JSON.
json-io fully supports both reading and writing TOON format, and conforms to the **TOON v4.4 spec**: all 564
cases of the official conformance fixture suite pass (160 for writing, 404 for reading, in strict and non-strict
mode). Key folding and path expansion, which spec v3.3 had and v4 dropped, remain as options that are off by
default.

### Writing TOON

```java
// Convert any Java object to TOON
Person person = new Person("John", 30);
String toon = JsonIo.toToon(person, null);

// Or stream to an OutputStream
JsonIo.toToon(outputStream, person, writeOptions);
```

**TOON defaults with `null` write options:**
- Type metadata is omitted (`showTypeInfoNever()` behavior) — see [Type Info in TOON](#type-info-in-toon).
- Cycle support is disabled (`cycleSupport(false)`) for ~35-40% faster serialization — TOON targets LLM communication where data is typically acyclic. If a cycle is encountered, a `JsonIoException` is thrown with guidance to enable `cycleSupport(true)`.
- Meta keys use `$` prefix by default (for JSON5-friendly identifiers).
- You can override any of these with explicit `WriteOptionsBuilder` settings.

### Type Info in TOON

Without type info the reader builds what the class you ask for declares. Plain data reads back whole that way,
but a polymorphic graph does not: a `List<Shape>` of circles and squares comes back as `Shape`s (or fails, if
`Shape` is abstract), a `BigDecimal` in an `Object` field as a `Double`, an exception's cause as a `Throwable`.
Turn type info on to keep the classes:

```java
WriteOptions options = new WriteOptionsBuilder().showTypeInfoMinimal().build();
String toon = JsonIo.toToon(drawing, options);
Drawing back = JsonIo.fromToon(toon, null).asClass(Drawing.class);   // its circles, squares, BigDecimal, causes
```

| Setting | Where `$type` is written |
|---------|--------------------------|
| none (the default) | nowhere |
| `showTypeInfoMinimal()` | where the declared type does not determine the class the reader builds — a subclass in a field, list or map, anything in an `Object` slot or a raw list, an exception's cause — and on the root (`omitRootTypeInfo()` leaves that off) |
| `showTypeInfoMinimalPlus()` | as minimal, but not on an int, short, byte or float in an `Object` slot (it reads back as a long or a double), nor on one date or number type where another is declared (it is converted) |
| `showTypeInfoMinimalLegacy()` | as minimal (the legacy form is JSON's: TOON has always written minimal this way) |
| `showTypeInfoAlways()` | on every value but a String, Long, Double or Boolean, and one where its primitive's wrapper is declared (`List<Integer>`, an `Integer` field), as JSON; a -0.0 keeps its typed form (a TOON number has no -0) |

Under minimal, a field, list, set or map that holds its declared class — or the class the reader builds for the
declared interface (`ArrayList` for `List`, `LinkedHashMap` for `Map`, a list of maps for `List<Map<String, Object>>`)
— carries no type, so a graph of plain DTOs costs one line, the root's type, over TOON without types. Elements of a
list nothing declares (a list at the root, a `List<Object>`) carry theirs: a root list of 50 DTOs costs about 24% more
tokens; inside a field declared `List<Person>`, nothing.

A value that carries its type is written as an object, as JSON writes it:

```
anything:
  $type: BigDecimal
  value: "1.25"
shapes[2]:
  - $type: com.example.Circle
    radius: 1
    name: c
  - $type: com.example.Square
    side: 2
    name: s
```

A list writes its type with `$items`; a map whose keys the reader must be told the class of (an `Integer` key where no
key type is declared) is written as `$keys` and `$items`; an `EnumSet` names its enum. A map whose keys are not
strings at all -- objects, lists, other maps -- is written as `$keys` and `$items` with or without type
information, as json-io writes it in JSON: the keys in one list (a table when they are alike), the values beside
them in another, the n-th key belonging to the n-th value. In a list, such a map or a list that carries its `$type`
or `$id` is an object like another, with its first field on the hyphen line (`- $type: ArrayList`, then
`$items[2]: 1,2` one level below the hyphen). A `CompactMap`, `CompactSet` or
`MultiKeyMap` names its base class and writes its configuration from its writer's `toData`, so a case-insensitive map
reads back case-insensitive.

### Reading TOON

```java
// Parse TOON to typed Java object
Person person = JsonIo.fromToon(toon, readOptions).asClass(Person.class);

// Parse TOON with generic types using TypeHolder
List<Person> people = JsonIo.fromToon(toon, readOptions)
        .asType(new TypeHolder<List<Person>>() {});

// Parse TOON to Map structure (class-independent)
Map<String, Object> map = JsonIo.fromToonToMaps(toon, readOptions).asClass(Map.class);

// Stream from InputStream
Person person = JsonIo.fromToon(inputStream, readOptions).asClass(Person.class);

// Stream-to-Maps variant
Map<String, Object> mapFromStream = JsonIo.fromToonToMaps(inputStream, readOptions).asClass(Map.class);
```

The `fromToon()` API returns a fluent builder allowing:
- `.asClass(Class<T>)` — Parse and convert to specific class
- `.asType(TypeHolder<T>)` — Parse with full generic type information (e.g., `List<Person>`, `Map<String, Person>`)

### TOON Format Characteristics

TOON uses a line-oriented, indentation-based structure:

| Feature | JSON | TOON |
|---------|------|------|
| Object delimiters | `{ }` | Indentation (2 spaces) |
| Array delimiters | `[ ]` | Count prefix: `[N]:` |
| Key-value separator | `"key": value` | `key: value` |
| String quoting | Always quoted | Only when necessary |
| Line endings | Any | LF (written); LF or CRLF (read) |

### Example Output

**Java object:**
```java
Map<String, Object> data = new LinkedHashMap<>();
data.put("name", "John");
data.put("age", 30);
data.put("tags", Arrays.asList("java", "json", "toon"));

Map<String, Object> address = new LinkedHashMap<>();
address.put("city", "NYC");
address.put("zip", 10001);
data.put("address", address);
```

**JSON output (91 characters):**
```json
{"name":"John","age":30,"tags":["java","json","toon"],"address":{"city":"NYC","zip":10001}}
```

**JSON5 output (79 characters, ~13% smaller):**
```json5
{name:"John",age:30,tags:["java","json","toon"],address:{city:"NYC",zip:10001}}
```

**TOON output (54 characters, ~41% smaller):**
```
name: John
age: 30
tags[3]: java,json,toon
address:
  city: NYC
  zip: 10001
```

| Format | Size | Savings vs JSON |
|--------|------|-----------------|
| JSON | 91 chars | — |
| JSON5 | 79 chars | ~13% smaller |
| TOON | 54 chars | ~41% smaller |

### TOON String Quoting Rules

TOON only quotes strings when necessary. Strings are quoted when they:

- Are empty
- Have leading/trailing whitespace
- Equal `true`, `false`, or `null`
- Look like numbers (e.g., `"42"`, `"3.14"`, `"05"`, `"+1"`, `"1e-6"`): an optional sign, digits, an optional
  fraction of a point and digits, an optional exponent
- Contain special characters: `:`, `"`, `\`, `[`, `]`, `{`, `}`, newlines, tabs
- Contain the delimiter character (comma by default)
- Start with a hyphen or `#`
- Are the whole document (a root string) and start with U+FEFF, which a reader would take for a byte-order mark

```java
// These strings will NOT be quoted:
"hello"     →  hello
"NYC"       →  NYC
".5"        →  .5        // no number in TOON: a number is what JSON calls one
"1."        →  1.

// These strings WILL be quoted:
""          →  ""
"true"      →  "true"
"42"        →  "42"
"hello:world" → "hello:world"
" spaced "  →  " spaced "
```

A byte-order mark (U+FEFF) at the very start of a document is removed before the document is read, so a file saved
with one reads like the same file without it. json-io writes none.

Lines end at a line feed. json-io writes LF only, and reads CRLF as well: one carriage return before the line
feed belongs to the line ending. A carriage return anywhere else is part of the value it stands in (json-io itself
writes one escaped, as `\r`, inside quotes).

The space is TOON's only whitespace. Spaces around a key, a value, an array's token or a table's cell are removed,
and so are spaces at the end of a line; a tab, a no-break space or any other character there is part of the token
(`name: Ada<TAB>` is the four characters `Ada<TAB>`). json-io writes a string with a tab in quotes, as `"Ada\t"`,
so this matters only for TOON written by hand or by another tool. Indentation is spaces too: strict mode refuses a
tab in it, and non-strict mode counts each tab as one level.

A line is split into its values at every delimiter that is not inside quotes, and an empty value between two
delimiters, or after the last one, is the empty string: `1,,x` holds three values. A quote starts a quoted stretch
wherever it stands, also in the middle of a value, and a delimiter or colon inside it is ordinary text; a backslash
means something only inside quotes, so `C:\,D:\` is two values. A value that *begins* with a quote must end with its
closing quote: `"abc" def` and `"abc` are errors, in strict and non-strict mode.

In a `key: value` line the first colon outside quotes ends the key, and what follows is one value. The key may be
empty (`: 1` is the key `""`, which json-io writes as `"": 1`). The value has no structure of its own: only `[]`
means something there, the empty array, and `a: [2]: x` is the string `[2]: x`. An array's header stands before
the colon, as in `a[2]: x,y`.

Every line of a document belongs to its value, in strict and non-strict mode: a line that nothing takes -- one
indented under a field that holds a plain value, one among list items that is no item, one after a root array -- is
an error, never skipped. The number in a header (`items[3]:`) is a claim that strict mode checks; it never decides
how much is read, so non-strict mode reads every item, row and value that stands in the header's scope, three or
not. A row of a table must have a cell for every field in both modes.

A line with a bracket before its first colon (outside quotes) is a header, and must be one: `key[N]:`, with `:`
after the count for a keyed table, a tab or `|` before the closing bracket to name that delimiter, and a field
list in braces -- nothing else, and no space anywhere between the key and the final colon. `items[2] : 1,2`,
`items [2]: 1,2`, `items[+2]: 1,2` and `rows[1]{a,}:` are errors in strict and non-strict mode; a key that holds
a bracket is written in quotes (`"a[0]": x`).

A blank line means nothing: it never ends an object or an array and is never counted, so `address:`, a blank line,
then the indented fields is still one nested object. The one place a blank line may not stand in strict mode is
inside an array or a keyed table, from its first item or row through the last line that belongs to it; non-strict
mode ignores it there too.

Reading is the other side of the quoting rules. A token without quotes is a number only if it is one in JSON's grammar
(an optional minus, digits without a leading zero, an optional fraction, an optional exponent); `true`, `false` and
`null` are themselves; everything else is a string. So `.5`, `1.`, `+1`, `05`, `1_000`, `0x10`, `NaN` and
`Infinity` are read as strings, as the TOON spec requires. A field declared as a number is still given its value:
`price: .5` read into a `double` is `0.5`.

A key is written without quotes when it starts with a letter or an underscore and holds only letters, digits,
underscores and dots (`name`, `_id`, `server.port`); any other key is quoted (`"content-type"`, `"2fa"`, `""`).
A dotted key is a key like another: `server.port: 8080` reads back as the one key `server.port`, unless the reader
is told to expand paths (`toonExpandPaths(true)`), which splits every unquoted dotted key into nested objects.

### TOON Array Formats

Which form a value takes follows from its shape, as the TOON spec lays down.

**A list of primitives** is written on one line (inline form):
```
tags[3]: foo,bar,baz
numbers[5]: 1,2,3,4,5
```

**A list of objects with the same fields** is a table: the field names once, then a row per object. A field that
holds objects with the same fields is a *nested field group* in the header, and its values are cells of the row:
```
employees[2]{name,age}:
  Alice,25
  Bob,30
orders[2]{id,customer{name,country},total}:
  1,Ada,DK,99
  2,Bob,UK,149
```

**An object whose values are objects with the same fields** -- a `Map<String, Server>`, or any object whose
fields all hold like objects -- is a *keyed table*: the field names once, then a row per entry, led by its key:
```
servers[2:]{host,port}:
  alpha: a.example.com,8080
  beta: b.example.com,9090
```

**Any other list** is written item by item (list form), an object's first field on the hyphen line:
```
employees[2]:
  - name: Alice
    skills[2]: java,sql
  - name: Bob
    skills[1]: go
```

A table needs every row to have the same fields, each holding a primitive or a like object: one `null` where the
others hold an object, an empty object, a list, or a missing field, and the whole list is written item by item (or
the object field by field). So is an object that is shared when references are written (`cycleSupport(true)`),
and one that carries its `$type`. The first row gives the order of the fields; when read back, every row has them
in that order.

Nested field groups and keyed tables came with TOON spec v4. json-io reads them from 4.114.0 on; to write for an
older json-io, or any reader of spec v3, see
[TOON Legacy Forms](user-guide-writeOptions.md#toon-legacy-forms).

### TOON Special Value Handling

Per the TOON specification:
- `NaN` → `null`
- `Infinity` / `-Infinity` → `null`
- `-0` → `0`
- An empty object -- a `Map` with no entries, or an object with no field to write -- is `key:` with nothing after
  it in a field, a bare `-` in a list, and an empty document at the root. TOON has no `{}`: read, it is the string
  of two braces, and written, that string is quoted (`"{}"`).
- An empty array is `key: []`.

### TOON Escape Sequences

TOON only supports 5 escape sequences (fewer than JSON):
- `\\` — backslash
- `\"` — double quote
- `\n` — newline
- `\r` — carriage return
- `\t` — tab

### TOON Supported Types

json-io's TOON support includes all types supported by the [java-util Converter](https://github.com/jdereg/java-util).
This provides comprehensive coverage far exceeding other TOON implementations:

| Category | Types | TOON Output |
|----------|-------|-------------|
| **Primitives & Wrappers** | `byte`, `Byte`, `short`, `Short`, `int`, `Integer`, `long`, `Long`, `float`, `Float`, `double`, `Double`, `boolean`, `Boolean`, `char`, `Character` | Numeric form or `true`/`false` |
| **Big Numbers** | `BigInteger`, `BigDecimal` | Numeric (unquoted); with type info on, a `BigDecimal` as its text (`"1.50"`, and `"1E+3"` for a negative scale), which keeps its scale, as JSON writes it |
| **Atomic Types** | `AtomicInteger`, `AtomicLong`, `AtomicBoolean` | Unwrapped numeric/boolean value |
| **Atomic Holders** | `AtomicReferenceArray`, `AtomicMarkableReference`, `AtomicStampedReference` | Object: `array` (its elements), or `pair` (`reference` with `mark` or `stamp`) |
| **Concurrency** | `AtomicIntegerArray`, `AtomicLongArray`, `Semaphore`, `CountDownLatch`, `ReentrantLock`, `ReentrantReadWriteLock` | Object with what the type's factory reads: `value` (the numbers), `permits` and `fair`, `count`, `fair` |
| **Date/Time (java.util)** | `Date`, `Calendar`, `TimeZone` | ISO-8601 string |
| **Date/Time (java.sql)** | `java.sql.Date`, `Timestamp` | ISO-8601 string |
| **Date/Time (java.time)** | `Instant`, `Duration`, `Period`, `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Year`, `YearMonth`, `MonthDay`, `ZoneId`, `ZoneOffset` | ISO-8601 string |
| **String Types** | `String`, `StringBuffer`, `StringBuilder`, `char[]`, `Character[]`, `CharBuffer` | String value (quoted if needed) |
| **Binary/Buffer** | `byte[]`, `ByteBuffer`, `BitSet` | Base64 encoded string |
| **ID/Location** | `UUID`, `URI`, `URL`, `Locale`, `Currency`, `Pattern`, `File`, `Path`, `Class` | String representation |
| **Geometric (java-util)** | `Color`, `Dimension`, `Point`, `Rectangle`, `Insets` | Object with component fields |
| **Collections** | `List`, `Set`, `Collection`, `EnumSet` | Array `[N]: ...` or list format |
| **Maps** | `Map`, `HashMap`, `TreeMap`, etc. | Object with key-value pairs |
| **Arrays** | `Object[]`, primitive arrays (`int[]`, `double[]`, etc.) | Inline `[N]: a,b,c` or list format |
| **Enums** | Any `Enum` type | Enum name as string; with an `@IoValue` or `@JsonValue` method, that value |
| **Other** | `Throwable`, custom objects with accessible fields | Object with fields |

**Comparison with JToon (toon-format/toon-java):**

| Feature | json-io | JToon |
|---------|---------|-------|
| Primitive types | ✓ All 8 | ✓ All 8 |
| Date/Time types | ✓ 18 types | ✓ 8 types |
| Atomic types | ✓ 6 types | ✗ |
| Geometric types | ✓ 5 types | ✗ |
| `UUID`, `URI`, `URL` | ✓ | ✗ |
| `Locale`, `Currency`, `Pattern` | ✓ | ✗ |
| `File`, `Path`, `Class` | ✓ | ✗ |
| `BitSet`, `ByteBuffer`, `CharBuffer` | ✓ | ✗ |
| `StringBuffer`, `StringBuilder` | ✓ | ✗ |
| Complex object graphs | ✓ Full support | Limited |
| Cyclic reference handling | ✓ `$id`/`$ref` (or `@id`/`@ref`) | N/A |
| Generic type preservation | ✓ Via TypeHolder | ✗ |
| Polymorphic graphs read back (`$type` where the declaration does not give the class) | ✓ [Type info](#type-info-in-toon) | ✗ |
| Custom type extensibility | ✓ ClassFactory/CustomReader | Limited |
| Delimiter options (comma/tab/pipe) | ✓ | ✓ |

### When to Use TOON

**Use TOON when:**
- Communicating with LLMs (significant token savings in both prompts and responses)
- Human readability is important
- You want compact, fast payloads — TOON defaults to `cycleSupport(false)` for optimal performance; enable `cycleSupport(true)` explicitly if your data has cycles

**Use JSON when:**
- Interoperability with other systems is required
- You need strict JSON tooling compatibility
- Standard JSON tooling is needed

### Cycle Handling in TOON

TOON defaults to `cycleSupport(false)` when `null` is passed for `WriteOptions`, skipping the
`traceReferences()` pre-pass for ~35-40% faster serialization. This is appropriate because TOON
targets LLM communication where data is typically acyclic (DTOs, query results, API responses).
If an actual cycle is encountered, a `JsonIoException` is thrown with guidance to enable
`cycleSupport(true)`. To write cyclic object graphs in TOON, pass explicit options:

```java
WriteOptions options = new WriteOptionsBuilder().cycleSupport(true).build();
String toon = JsonIo.toToon(cyclicObject, options);
```

### Fine-tuning TOON

For per-call control beyond the defaults, the dedicated options guides document every TOON-related option:

**Read side** — see [Read Options TOON sections](user-guide-readOptions.md#toon-parsing-strictness):
- `strictToon()` — refuses what non-strict mode recovers from: a count that does not match, a key that stands twice, a tab or uneven indentation, a blank line inside an array, a depth jump
- `toonExpandPaths(boolean)` — opt-in spec §13.4 dotted-key path expansion *(new in 4.103.0)*
- `toonIndentSize(int)` — custom decoder indent width for documents that don't use 2-space indent *(new in 4.103.0)*

Each per-instance option has an `addPermanent*` sibling for JVM-lifetime defaults.

**Write side** — see [Write Options TOON sections](user-guide-writeOptions.md#toon-key-folding):
- `toonKeyFolding(boolean)` — collapse single-key object chains into dotted notation (e.g. `address.city: NYC`)
- `toonDelimiter(char)` — switch tabular/inline delimiter to `','`, `'\t'`, or `'|'`
- `toonLegacyForms(boolean)` — write only the forms of TOON spec v3, for a reader older than json-io 4.114.0 *(new in 4.114.0)*
- `indentationSize(int)` — encoder indent width (also drives JSON pretty-print)

## Annotations

json-io provides 26 annotations in the `com.cedarsoftware.io.annotation` package for controlling serialization and deserialization. In addition, json-io **reflectively honors Jackson annotations** when the Jackson JAR is on the classpath — with zero compile-time dependency on Jackson.

### Annotation Precedence

When both json-io and Jackson annotations are present on the same element:

1. **Programmatic API** (`WriteOptionsBuilder`/`ReadOptionsBuilder` methods like `addExcludedField()`, `addClassFactory()`, etc.) — highest priority, always wins
2. **json-io annotations** (`@IoProperty`, `@IoIgnore`, etc.) — checked first among annotations
3. **Jackson annotations** (`@JsonProperty`, `@JsonIgnore`, etc.) — used as fallback if no json-io annotation is present on the same element

If Jackson annotations are not on the classpath, external annotation detection is silently skipped with zero overhead.

For type-resolution annotations on fields, a more specific precedence applies:
- `@type` in JSON > `@IoDeserialize` > `@IoTypeInfo` > declared field type

### Available Annotations

#### `@IoProperty("name")` — Field Rename and/or Directional Access
Renames a Java field in the serialized JSON output and accepts the renamed key during deserialization. Equivalent to Jackson's `@JsonProperty`.

```java
public class User {
    @IoProperty("full_name")
    private String name;     // Serializes as "full_name" in JSON
    private int age;
}
```

The optional `access` attribute controls direction, mirroring Jackson's `@JsonProperty(access = ...)`:

- `Access.WRITE_ONLY` — value is **deserialized** from JSON (set on Java side) but **never serialized** to output. Use for secrets (`passwordHash`).
- `Access.READ_ONLY` — value is **serialized** to JSON but **never deserialized** (input is silently dropped). Use for computed/derived fields (`computedTotal`, `lastLoginEpoch`).
- `Access.READ_WRITE` / `Access.AUTO` — no constraint (default).

```java
public class User {
    private String name;

    @IoProperty(access = IoProperty.Access.WRITE_ONLY)
    private String passwordHash;     // accepted on input, never output

    @IoProperty(access = IoProperty.Access.READ_ONLY)
    private long lastLoginEpoch;     // written on output, ignored on input
}
```

Rename and access can be combined: `@IoProperty(value = "pwd", access = Access.WRITE_ONLY)`.

#### `@IoIgnore` — Field Exclusion (both directions)
Excludes a field from both serialization and deserialization. Equivalent to Jackson's `@JsonIgnore`. For direction-specific exclusion, use `@IoProperty(access = ...)` instead.

```java
public class Account {
    private String username;
    @IoIgnore
    private String password;  // Never appears in JSON, never read from JSON
}
```

#### `@IoIgnoreProperties({"field1", "field2"})` — Class-Level Exclusion
Excludes multiple fields by name at the class level. Equivalent to Jackson's `@JsonIgnoreProperties`. The directional escapes `allowGetters` and `allowSetters` (default `false`) mirror Jackson exactly:

- `allowGetters = true` — listed properties remain in **output** JSON; only **input** is ignored (class-level equivalent of `@IoProperty(access = READ_ONLY)`).
- `allowSetters = true` — listed properties may be supplied on **input**; only **output** suppresses them (class-level equivalent of `@IoProperty(access = WRITE_ONLY)`).
- Both `true` — no exclusion (no-op for the listed fields).

```java
@IoIgnoreProperties(value = {"computedTotal"}, allowGetters = true)
public class Cart {
    private List<Item> items;
    private BigDecimal computedTotal;   // written on output, ignored on input
}

@IoIgnoreProperties(value = {"passwordHash"}, allowSetters = true)
public class Credentials {
    private String username;
    private String passwordHash;        // accepted on input, suppressed on output
}
```

Default `allowGetters=false, allowSetters=false` preserves the long-standing "exclude on both sides" behavior:
```java
@IoIgnoreProperties({"secret", "internal"})
public class Config {
    private String name;
    private String secret;     // Excluded on both sides
    private String internal;   // Excluded on both sides
    private int value;
}
```

#### `@IoIncludeProperties({"field1", "field2"})` — Class-Level Whitelist
The inverse of `@IoIgnoreProperties` — only the listed fields are included in serialization and deserialization. All other fields are excluded. Equivalent to Jackson's `@JsonIncludeProperties`.

```java
@IoIncludeProperties({"name", "email"})
public class User {
    private String name;       // Included
    private String email;      // Included
    private String password;   // Excluded (not in whitelist)
    private int age;           // Excluded (not in whitelist)
}
```

#### `@IoAlias({"alt1", "alt2"})` — Read-Side Alternate Names
Specifies alternate JSON property names accepted during deserialization. The primary field name (or `@IoProperty` name) is always used for serialization. Equivalent to Jackson's `@JsonAlias`.

```java
public class Person {
    @IoAlias({"firstName", "first_name", "fname"})
    private String name;
}
// Any of {"name":"Alice"}, {"firstName":"Alice"}, {"first_name":"Alice"}, {"fname":"Alice"}
// will deserialize correctly into the 'name' field.
```

#### `@IoPropertyOrder({"field1", "field2", ...})` — Write-Side Field Ordering
Controls the order of fields during JSON serialization. Fields listed in the annotation appear first in the specified order; any remaining fields follow in their natural declaration order. Equivalent to Jackson's `@JsonPropertyOrder`.

```java
@IoPropertyOrder({"id", "name", "email"})
public class User {
    private String email;
    private String name;
    private long id;
    private int age;
}
// Serializes as: {"id":1, "name":"Alice", "email":"alice@example.com", "age":30}
```

#### `@IoInclude(Include.NON_NULL)` — Per-Field Null Skipping
Controls the inclusion of a field during serialization. When set to `NON_NULL`, the field is omitted from JSON output if its value is `null`, regardless of the global `skipNullFields` setting. Equivalent to Jackson's `@JsonInclude(Include.NON_NULL)`.

```java
public class Response {
    @IoInclude(IoInclude.Include.NON_NULL)
    private String optionalMessage;  // Omitted from JSON when null
    private String status;           // Always present, even if null
}
```

#### `@IoNaming(Strategy)` — Class-Level Naming Strategy
Applies a naming strategy to all fields in a class, transforming Java camelCase names to the chosen format. Individual fields can override the strategy with `@IoProperty`. Equivalent to Jackson's `@JsonNaming`.

Available strategies: `SNAKE_CASE`, `KEBAB_CASE`, `UPPER_CAMEL_CASE`, `LOWER_DOT_CASE`.

```java
@IoNaming(IoNaming.Strategy.SNAKE_CASE)
public class UserProfile {
    private String firstName;    // Serializes as "first_name"
    private String lastName;     // Serializes as "last_name"
    private int loginCount;      // Serializes as "login_count"

    @IoProperty("uid")
    private String userId;       // Overrides strategy — serializes as "uid"
}
```

#### `@IoCreator` — Constructor/Factory Deserialization
Marks a constructor or static factory method for json-io to use during deserialization. Parameters are matched to JSON keys by name (or via `@IoProperty` on parameters), and each value is read as its parameter's declared type, as a field's would be: an object, a `List<Address>`, a `Map<String, Address>`, an array, or another class with a creator. One object passed to a creator twice, or also held by a field, stays one object; a creator cannot be passed the object it is creating (a reference back to it is an error). Equivalent to Jackson's `@JsonCreator`.

```java
public class Money {
    private final long cents;
    private final String currency;

    @IoCreator
    Money(@IoProperty("cents") long cents, @IoProperty("currency") String currency) {
        this.cents = cents;
        this.currency = currency;
    }
}

// Static factory also supported:
public class Color {
    private final int r, g, b;
    private Color(int r, int g, int b) { this.r = r; this.g = g; this.b = b; }

    @IoCreator
    static Color of(@IoProperty("r") int r, @IoProperty("g") int g, @IoProperty("b") int b) {
        return new Color(r, g, b);
    }
}
```

#### `@IoValue` — Single-Value Serialization
Marks a no-arg instance method whose return value is used as the serialized representation. Useful for wrapper types that should serialize as a single value rather than an object with fields. Equivalent to Jackson's `@JsonValue`.

```java
public class EmailAddress {
    private final String address;

    @IoCreator
    EmailAddress(@IoProperty("address") String address) { this.address = address; }

    @IoValue
    public String toValue() { return address; }
}
// Serializes as: "user@example.com" (not {"address":"user@example.com"})
```
It reads back through the class's one-parameter `@IoCreator`, which is given that value, converted to its parameter's type; without one, through the constructor whose one parameter has the method's return type (or its boxed or primitive form), as Jackson uses a single-argument constructor. Where type info is needed (a field declared `Object`), it is written as `{"@type":"...EmailAddress","value":"user@example.com"}`, which reads back the same way.
TOON writes it the same way (`email: user@example.com`; with type info, `$type` and `value`). An object written as its value carries no `@id`: each place that holds it holds the value, as each place holding a String does, so it reads back as equal objects rather than one shared object. When the value is itself an object, that object keeps its identity as any object does: a reference to it elsewhere reads back as the value, and a place declared as the class that holds such a reference gets the class built around it.

A list or map class may have the method too (`Tags extends ArrayList<String>` with `@IoValue String joined()` and `@IoCreator static Tags of(String joined)`): it is written as its value, not as a list or map, and given its items or entries it is still built as one.

The method may return an object, a list or a map (`@IoValue Address address()` with `@IoCreator Site(Address address)`). The creator then takes the value whole: a JSON object holding the creator parameter's key is the class's fields (`{"address":{...}}`, as TOON wrote such a class through 4.117.0), and any other object, or an array, is the value itself. Jackson's `@JsonCreator(mode = DELEGATING)` or `mode = PROPERTIES` decides, when given. A null value is written as null and reads back as null.

On an enum, the method's value is written in place of the name, wherever the enum appears (a field, a list, an `EnumSet`'s items, an `EnumMap`'s and any map's keys, the root), as JSON and as TOON, as Jackson writes it: `GREEN("g")` with `@JsonValue String code()` is `"g"`, and an `int` value is a number. It reads back from the value, and from the name, so what json-io wrote before still reads; a value that is also another constant's name is the value. A custom writer registered for the enum still comes first.

An enum with a one-parameter static `@IoCreator` (or Jackson's `@JsonCreator`) factory method is read through it first, as Jackson reads it, wherever the enum is read from a value (with or without an `@IoValue` method): `@IoCreator static Shade from(String text)` that accepts a code or a name in any case reads `"Dark"`, `"dark"` and `"d"`. When the factory throws or returns null, the value is read as above, then as a name, so the names json-io wrote through 4.117.0 still read past a factory that takes codes only. The value is converted to the factory's parameter type (a number for `of(int n)`); one it cannot be converted to skips the factory. `@IoCreator` on an enum's constructor is ignored.

#### `@IoIgnoreType` — Type-Level Exclusion
When placed on a class, all fields of that type are excluded from serialization and deserialization across all classes. Useful for cross-cutting exclusion of internal metadata types. Equivalent to Jackson's `@JsonIgnoreType`.

```java
@IoIgnoreType
public class InternalMetadata {
    private String traceId;
    private long timestamp;
}

public class Order {
    private String orderId;
    private InternalMetadata meta;  // Automatically excluded everywhere
    private String status;
}
```

#### `@IoTypeInfo(ConcreteClass.class)` — Default Concrete Type
Field-level annotation that specifies the default concrete type to use during deserialization when no `@type` metadata is present in the JSON. Useful for polymorphic fields declared as interfaces or abstract classes. If `@type` IS present in the JSON, it takes precedence. Equivalent to Jackson's `@JsonTypeInfo(defaultImpl=...)`.

```java
public class Container {
    @IoTypeInfo(ArrayList.class)
    private Object items;              // Defaults to ArrayList when @type absent

    @IoTypeInfo(LinkedHashMap.class)
    private Map<String, Object> data;  // Defaults to LinkedHashMap
}
```

**Write-side benefit:** When the runtime type of a field matches the `@IoTypeInfo` type, `@type` is omitted from JSON output — the reader infers the type from the annotation, producing smaller JSON.

#### `@IoDeserialize(as=ConcreteClass.class)` — Forced Type Override
Field-level or class-level annotation that always overrides the declared type during deserialization (forced coercion). Unlike `@IoTypeInfo`, this is not just a default — it is always applied unless `@type` is present in the JSON. Equivalent to Jackson's `@JsonDeserialize(as=...)`.

```java
public class Config {
    @IoDeserialize(as = LinkedList.class)
    private List<String> items;              // Always deserialized as LinkedList

    @IoDeserialize(as = LinkedHashMap.class)
    private Map<String, Object> data;        // Always deserialized as LinkedHashMap
}
```

**Difference from `@IoTypeInfo`:** `@IoDeserialize(as=...)` always overrides the declared type (forced coercion). `@IoTypeInfo` only provides a default when no type can be inferred. When both are present on the same field, `@IoDeserialize` takes priority.

**Write-side benefit:** When the runtime type of a field matches the `@IoDeserialize(as=X)` type, `@type` is omitted from JSON output — the reader infers the type from the annotation, producing smaller JSON.

#### `@IoClassFactory(FactoryClass.class)` — Custom ClassFactory
Class-level annotation that specifies a `ClassFactory` implementation to use when deserializing instances of this class. The factory class must have a no-arg constructor. Factory instances are automatically cached and shared. Programmatic `addClassFactory()` takes priority.

```java
@IoClassFactory(WidgetFactory.class)
public class Widget {
    private final String name;
    private final int size;
    private Widget(String name, int size) { this.name = name; this.size = size; }
}

public class WidgetFactory implements ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        String name = (String) jObj.get("name");
        int size = ((Number) jObj.get("size")).intValue();
        return new Widget(name, size);
    }
    public boolean isObjectFinal() { return true; }  // Factory fully populates the object
}
```

#### `@IoGetter("fieldName")` — Custom Getter Method
Method-level annotation that marks a no-arg instance method as the getter for a specific field during serialization. Use this when your class uses non-standard getter names that don't follow the `getXxx()` convention. Programmatic `addNonStandardGetter()` takes priority.

```java
public class Sensor {
    private double temperature;
    private String location;

    @IoGetter("temperature")
    public double readTemperature() { return temperature; }

    @IoGetter("location")
    public String fetchLocation() { return location; }
}
// json-io calls readTemperature() and fetchLocation() instead of reading the fields,
// whether or not WriteOptionsBuilder.useGetters(true) is set
```

#### `@IoSetter("fieldName")` — Custom Setter Method
Method-level annotation that marks a single-argument instance method as the setter for a specific field during deserialization. Use this when your class uses non-standard setter names that don't follow the `setXxx()` convention. Programmatic `addPermanentNonStandardSetter()` takes priority.

```java
public class Sensor {
    private double temperature;
    private String location;

    @IoSetter("temperature")
    public void calibrateTemperature(double temp) { this.temperature = temp; }

    @IoSetter("location")
    public void assignLocation(String loc) { this.location = loc; }
}
// json-io calls calibrateTemperature() and assignLocation() instead of assigning the fields,
// whether or not ReadOptionsBuilder.useSetters(true) is set
```

**Note:** `@IoGetter`/`@IoSetter` are the annotation equivalents of the `nonStandardGetters.txt` and `nonStandardSetters.txt` config files. Config files are used for JDK classes that cannot be annotated; annotations are for user classes.

#### `@IoNonReferenceable` — Suppress `@id`/`@ref`
Marks a class as non-referenceable: instances of this type will never emit `@id` or `@ref` during serialization, even when the same instance is referenced multiple times. This is the annotation equivalent of the `nonRefs.txt` config file. No Jackson equivalent exists — Jackson does not support `@id`/`@ref` graph semantics.

```java
@IoNonReferenceable
public class Token {
    private String value;
    // ...
}

public class Holder {
    private Token first;
    private Token second;
}

Token t = new Token("abc");
Holder h = new Holder();
h.first = t;
h.second = t;  // same instance

String json = JsonIo.toJson(h, writeOptions);
// {"first":{"value":"abc"},"second":{"value":"abc"}}
// No @id/@ref — second is written as a full duplicate
```

On deserialization, each occurrence produces a separate object instance (no instance sharing). This is appropriate for value-like types where identity is not meaningful.

**Note:** `@IoNonReferenceable` is additive with the programmatic API (`addNonReferenceableClass()`) and the `nonRefs.txt` config file. All three sources are OR'd together. The config file is used for JDK classes that cannot be annotated; the annotation is for user classes.

#### `@IoNotCustomReader` — Suppress Custom Reader
Marks a class to prevent custom reader usage during deserialization. Even if a custom reader exists for a parent class (through inheritance), the annotated class will use standard field-by-field deserialization instead. This is the annotation equivalent of the `notCustomRead.txt` config file.

```java
@IoNotCustomReader
public class MySpecialSet extends HashSet<String> {
    private int metadata;
    // Will NOT use HashSet's custom reader — uses standard field-by-field deserialization
}
```

**Note:** `@IoNotCustomReader` is additive with the programmatic API (`addNotCustomReaderClass()`) and the `notCustomRead.txt` config file.

#### `@IoNotCustomWritten` — Suppress Custom Writer
Marks a class to prevent custom writer usage during serialization. Even if a custom writer exists for a parent class (through inheritance), the annotated class will use standard field-by-field serialization instead. This is the annotation equivalent of the `notCustomWritten.txt` config file.

```java
@IoNotCustomWritten
public class MySpecialMap extends HashMap<String, Object> {
    private String label;
    // Will NOT use HashMap's custom writer — uses standard field-by-field serialization
}
```

Both annotations can be combined on the same class:

```java
@IoNotCustomReader
@IoNotCustomWritten
public class MySpecialCollection extends ArrayList<String> {
    // Standard serialization AND deserialization — no custom reader or writer
}
```

**Note:** `@IoNotCustomWritten` is additive with the programmatic API (`addNotCustomWrittenClass()`) and the `notCustomWritten.txt` config file. Config files are used for JDK classes that cannot be annotated; annotations are for user classes.

#### `@IoCustomWriter(MyWriter.class)` — Custom Writer
Specifies a `JsonClassWriter` implementation to use when serializing instances of this class. This is the annotation equivalent of calling `WriteOptionsBuilder.addCustomWrittenClass(Class, JsonClassWriter)` or adding an entry to `customWriters.txt`.

The writer class must have a public no-arg constructor. Instances are cached and shared.

```java
@IoCustomWriter(MoneyWriter.class)
public class Money {
    private BigDecimal amount;
    private Currency currency;
}

public class MoneyWriter implements JsonClassWriter {
    public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
        if (showType) { output.write("\"value\":"); }
        writePrimitiveForm(o, output, context);
    }
    public boolean hasPrimitiveForm(WriterContext context) { return true; }
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Money m = (Money) o;
        output.write("\"" + m.getAmount() + " " + m.getCurrency() + "\"");
    }
}
```

**Note:** Programmatic `addCustomWrittenClass()` takes priority over the annotation.

#### `@IoCustomReader(MyReader.class)` — Custom Reader
Specifies a `JsonClassReader` implementation to use when deserializing instances of this class. This is the annotation equivalent of calling `ReadOptionsBuilder.addCustomReaderClass(Class, JsonClassReader)` or adding an entry to `customReaders.txt`.

The reader class must have a public no-arg constructor. Instances are cached and shared.

```java
@IoCustomReader(MoneyReader.class)
public class Money {
    private BigDecimal amount;
    private Currency currency;
}

public class MoneyReader implements JsonClassReader {
    public Object read(Object jsonObj, Resolver resolver) {
        String s = (jsonObj instanceof String) ? (String) jsonObj : (String) ((JsonObject) jsonObj).get("value");
        String[] parts = s.split(" ");
        return new Money(new BigDecimal(parts[0]), Currency.getInstance(parts[1]));
    }
}
```

Both annotations can be combined on the same class to provide custom serialization in both directions. **Note:** Programmatic `addCustomReaderClass()` takes priority over the annotation. Config files are used for JDK classes that cannot be annotated; annotations are for user classes.

#### `@IoTypeName("ShortName")` — Type Alias
Assigns a short alias name to a class for use in the `@type` field during JSON serialization. Instead of writing the fully-qualified class name, json-io will write the alias. On deserialization, the alias is resolved back to the original class. This is the annotation equivalent of calling `WriteOptionsBuilder.aliasTypeName(Class, String)` / `ReadOptionsBuilder.aliasTypeName(Class, String)` or adding an entry to the `config/aliases.txt` configuration file. Equivalent to Jackson's `@JsonTypeName`.

```java
@IoTypeName("Sensor")
public class SensorReading {
    private double value;
    private String unit;
}
// Serializes as: {"@type":"Sensor","value":23.5,"unit":"C"}
// Instead of:    {"@type":"com.example.SensorReading","value":23.5,"unit":"C"}
```

**Note:** Programmatic `aliasTypeName()` takes priority over the annotation. The alias is registered in a reverse lookup map when the class is first scanned, allowing deserialization to resolve the alias back to the class.

#### `@IoFormat("pattern")` — Per-Field Format Pattern
Specifies a custom format pattern for fields during serialization and deserialization. The pattern engine is auto-detected based on the pattern string and the field's type. Equivalent to Jackson's `@JsonFormat(pattern="...")`.

**Date/time patterns — which formatter is used depends on the field type:**

| Field Type | Pattern Engine | Pattern Syntax |
|---|---|---|
| `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `OffsetDateTime`, `OffsetTime`, `Instant` | `java.time.format.DateTimeFormatter` | `"yyyy-MM-dd"`, `"dd/MM/yyyy HH:mm"`, etc. |
| `java.util.Date`, `java.sql.Date`, `java.sql.Timestamp` | `java.text.SimpleDateFormat` | `"MM/dd/yyyy"`, `"yyyy-MM-dd HH:mm:ss"`, etc. |

> **Note:** `DateTimeFormatter` and `SimpleDateFormat` use similar but not identical pattern letters. For example, `DateTimeFormatter` uses `uuuu` for year (though `yyyy` also works for common era dates), while `SimpleDateFormat` uses `yyyy`. Consult the Javadoc for each formatter when writing patterns.

```java
public class Event {
    @IoFormat("dd/MM/yyyy")
    private LocalDate eventDate;     // DateTimeFormatter — "23/02/2026"

    @IoFormat("yyyy-MM-dd HH:mm")
    private LocalDateTime startTime; // DateTimeFormatter — "2026-02-23 14:30"

    @IoFormat("MM/dd/yyyy")
    private Date legacyDate;         // SimpleDateFormat — "02/23/2026"

    private LocalDate plain;         // Uses default ISO format (no annotation)
}
```

**Numeric patterns — two engines available, distinguished by the `%` character:**

| Pattern Style | Detection | Engine | Example Patterns |
|---|---|---|---|
| C-style | Pattern contains `%` | `String.format()` | `"%,d"`, `"%.2f"`, `"%05d"`, `"%x"` |
| Java-style | Pattern does NOT contain `%` | `java.text.DecimalFormat` | `"#,###"`, `"$#,##0.00"`, `"0.00"` |

Both engines support: `int`, `long`, `double`, `float`, `short`, `byte` (and wrappers), `BigDecimal`, `BigInteger`, `AtomicInteger`, `AtomicLong`.

> **Tip:** The `%` character is the trigger. If your pattern contains `%`, `String.format()` is used. Otherwise, `DecimalFormat` is used. Use `String.format()` patterns for hex output (`%x`), zero-padding (`%05d`), or scientific notation (`%e`). Use `DecimalFormat` patterns for currency (`$#,##0.00`), percentage with DecimalFormat semantics, or locale-sensitive grouping.

```java
public class Invoice {
    // DecimalFormat patterns (no % character)
    @IoFormat("#,###")
    private int quantity;            // DecimalFormat — "1,234"

    @IoFormat("$#,##0.00")
    private BigDecimal total;        // DecimalFormat — "$1,234.56"

    @IoFormat("0.00")
    private double rate;             // DecimalFormat — "3.14"

    private int plain;               // No annotation — default unquoted JSON number
}
```

```java
public class Report {
    // String.format() patterns (contain % character)
    @IoFormat("%,d")
    private int population;          // String.format — "1,234,567"

    @IoFormat("%.2f")
    private double price;            // String.format — "3.14"

    @IoFormat("%05d")
    private int code;                // String.format — "00042"

    @IoFormat("%x")
    private int color;               // String.format — "ff"
}
```

**String patterns — `String.format()` only:**

`String.format()` patterns also work on `String` fields for padding and alignment:

```java
public class Label {
    @IoFormat("%10s")
    private String name;             // String.format — "        hi" (right-aligned)

    @IoFormat("%-10s")
    private String code;             // String.format — "hi        " (left-aligned)
}
```

All formatted values are written as **quoted JSON strings** and parsed back correctly on read. Round-trip precision depends on the pattern — for example, `"%.2f"` on `3.14159` writes `"3.14"` and reads back as `3.14`.

#### `@IoShowType` — Force Type Emission

Field-level annotation that forces `$type` (or `@type`) to be written for the annotated field's value and its elements, regardless of the global `showTypeInfo` setting. This is essential for polymorphic fields when using `showTypeInfoNever()` or TOON/JSON5 mode (which defaults to never showing type), ensuring that concrete types survive round-trip serialization.

```java
static class Fleet {
    @IoShowType
    List<Vehicle> vehicles;       // Each element gets @type even with showTypeInfoNever()

    @IoShowType
    Vehicle primary;              // The value gets @type even with showTypeInfoNever()

    @IoShowType
    Vehicle[] spares;             // Each array element gets @type

    @IoShowType
    Map<String, Vehicle> named;   // Each map value gets @type

    String name;                  // No annotation — follows global showTypeInfo setting
}
```

**When to use:** Place `@IoShowType` on fields declared as an interface or abstract/parent class that hold polymorphic values (subclass instances). Without this annotation, `showTypeInfoNever()` would strip all type metadata, making it impossible to reconstruct the correct concrete types on read.

**Supported field types:**
- **Plain fields** — the field value gets `@type`
- **Collections** (`List`, `Set`, etc.) — each element gets `@type`
- **Arrays** (`Vehicle[]`) — each element gets `@type`
- **Maps** — each map value gets `@type`

**Interaction with showTypeInfo modes:**
- `showTypeInfoNever()` — `@IoShowType` overrides, forcing type emission
- `showTypeInfoMinimal()` / `showTypeInfoMinimalPlus()` — `@IoShowType` is redundant but harmless (type is already shown for polymorphic mismatches)
- `showTypeInfoAlways()` — `@IoShowType` is redundant (type is always shown)
- JSON5 mode — `@IoShowType` overrides the default `showTypeInfoNever()` behavior
- TOON format — `@IoShowType` forces `$type` emission even when type metadata is disabled

**Jackson fallback:** If `@IoShowType` is not present, json-io checks for Jackson's `@JsonTypeInfo` on the field as a synonym. No additional Jackson configuration is needed — the mere presence of `@JsonTypeInfo` on a field triggers forced type emission.

#### `@IoAnySetter` / `@IoAnyGetter` — Extra Field Handling

These annotations allow a class to absorb unrecognized JSON fields during deserialization and emit extra fields during serialization — without requiring a global `MissingFieldHandler`.

**`@IoAnySetter`** marks a method that receives each unrecognized field name and value:

```java
public class FlexibleConfig {
    private String name;
    private Map<String, Object> extras = new LinkedHashMap<>();

    @IoAnySetter
    public void handleUnknown(String key, Object value) {
        extras.put(key, value);
    }
}
```

**Contract:** Non-static instance method with exactly 2 parameters `(String fieldName, Object value)`.

**`@IoAnyGetter`** marks a method that returns a `Map<String, Object>` of extra fields to include during serialization:

```java
public class FlexibleConfig {
    private String name;
    private Map<String, Object> extras = new LinkedHashMap<>();

    @IoAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }
}
```

**Contract:** Non-static, no-arg instance method returning `Map` (or any `Map` subtype).

**Typical usage** — combine both annotations for round-trip support:

```java
public class FlexibleConfig {
    private String name;
    private int version;
    private Map<String, Object> extras = new LinkedHashMap<>();

    @IoAnySetter
    public void handleUnknown(String key, Object value) {
        extras.put(key, value);
    }

    @IoAnyGetter
    public Map<String, Object> getExtras() {
        return extras;
    }
}
```

**Behavior notes:**
- Extra fields from `@IoAnyGetter` are written **after** regular declared fields
- `@IoAnySetter` takes **priority** over the global `MissingFieldHandler` configured via `ReadOptionsBuilder`
- Null values in the `@IoAnyGetter` map respect the `skipNullFields` setting
- If the `@IoAnyGetter` method returns `null` or an empty map, no extra fields are written

Equivalent to Jackson's `@JsonAnySetter` / `@JsonAnyGetter`.

### Combining Annotations

Annotations can be combined on the same field or class:

```java
@IoPropertyOrder({"id", "username"})
@IoIgnoreProperties({"password"})
@IoNaming(IoNaming.Strategy.SNAKE_CASE)
public class UserProfile {
    private long id;

    @IoProperty("username")
    @IoAlias({"user_name", "login"})
    private String name;        // Writes as "username"; reads "username", "user_name", or "login"

    private String password;    // Excluded by class-level annotation

    @IoInclude(IoInclude.Include.NON_NULL)
    private String bio;         // Omitted when null

    @IoDeserialize(as = LinkedList.class)
    private List<String> tags;  // Always deserialized as LinkedList
}
```

### Jackson Annotation Compatibility

If your classes already use Jackson annotations, json-io will honor them automatically — no code changes needed. The following Jackson annotations are supported:

| Jackson Annotation | json-io Equivalent | Effect                                                                                                  |
|---|---|---------------------------------------------------------------------------------------------------------|
| `@JsonProperty("name")` | `@IoProperty("name")` | Renames field in JSON                                                                                   |
| `@JsonIgnore` | `@IoIgnore` | Excludes field                                                                                          |
| `@JsonIgnoreProperties({"a","b"})` | `@IoIgnoreProperties({"a","b"})` | Class-level field exclusion                                                                             |
| `@JsonIncludeProperties({"a","b"})` | `@IoIncludeProperties({"a","b"})` | Class-level field whitelist                                                                             |
| `@JsonAlias({"alt1","alt2"})` | `@IoAlias({"alt1","alt2"})` | Accept alternate names on read                                                                          |
| `@JsonPropertyOrder({"x","y"})` | `@IoPropertyOrder({"x","y"})` | Control field order on write                                                                            |
| `@JsonInclude(Include.NON_NULL)` | `@IoInclude(Include.NON_NULL)` | Per-field null skipping                                                                                 |
| `@JsonCreator` | `@IoCreator` | Custom deserialization constructor/factory                                                              |
| `@JsonValue` | `@IoValue` | Single-value serialization                                                                              |
| `@JsonNaming(SnakeCaseStrategy.class)` | `@IoNaming(Strategy.SNAKE_CASE)` | Class-level naming strategy                                                                             |
| `@JsonIgnoreType` | `@IoIgnoreType` | Exclude all fields of this type                                                                         |
| `@JsonTypeInfo(defaultImpl=...)` | `@IoTypeInfo(...)` | Default concrete type hint                                                                              |
| `@JsonDeserialize(as=...)` | `@IoDeserialize(as=...)` | Forced deserialization type override                                                                    |
| `@JsonGetter("fieldName")` | `@IoGetter("fieldName")` | Custom getter method for serialization                                                                  |
| `@JsonSetter("fieldName")` | `@IoSetter("fieldName")` | Custom setter method for deserialization                                                                |
| `@JsonTypeName("ShortName")` | `@IoTypeName("ShortName")` | Type alias for `@type` in JSON                                                                          |
| `@JsonFormat(pattern="...")` | `@IoFormat("pattern")` | Per-field format pattern (`String.format`, `DecimalFormat`, `DateTimeFormatter`, or `SimpleDateFormat`) |
| `@JsonTypeInfo` (on field) | `@IoShowType` | Force `$type` (or `@type`) emission on field regardless of global showTypeInfo setting                  |

Jackson's `jackson-annotations` JAR (~75KB) is commonly already on the classpath in Spring applications. json-io detects annotations via `Class.forName()` at startup — there is no compile-time dependency. Some annotations (`@JsonNaming`, `@JsonDeserialize`) live in `jackson-databind` and are detected independently. Both **Jackson 2.x and 3.x** are recognized: the `com.fasterxml.jackson.annotation.*` annotations are shared across both majors (Jackson 3.x reuses the 2.x annotations jar), while `@JsonNaming`/`@JsonDeserialize` are detected under both `com.fasterxml.jackson.databind.annotation.*` (2.x) and `tools.jackson.databind.annotation.*` (3.x).

## Streaming API (cursor-style read/write)

For hand-rolled streaming use cases (large documents, transform pipelines, or porting from Jackson), json-io exposes a Jackson-aligned token cursor on each side. These bypass the tree-builder entirely — no `JsonObject` graph is built on read, no reflection is invoked on write.

### `JsonIo.createTokenizer(...)` — streaming read

```java
try (JsonTokenizer t = JsonIo.createTokenizer(jsonString)) {
    while (t.nextToken() != null) {
        switch (t.currentToken()) {
            case FIELD_NAME:        String name  = t.currentName(); break;
            case VALUE_STRING:      String text  = t.getText();    break;
            case VALUE_NUMBER_INT:  long   i     = t.getLongValue(); break;
            case VALUE_NUMBER_FLOAT:double f     = t.getDoubleValue(); break;
            case VALUE_TRUE:        // ...
        }
    }
}
```

Factories: `createTokenizer(String)`, `createTokenizer(InputStream)`, plus `ReadOptions`-aware overloads. The tokenizer respects `strictJson`, `allowNanAndInfinity`, `integerTypeBigInteger`, `floatingPointBigDecimal`, and `stringBufferSize` from the supplied options.

### `JsonIo.createGenerator(...)` — streaming write

```java
try (JsonGenerator g = JsonIo.createGenerator(outputStream)) {
    g.writeStartObject()
        .writeStringField("id", "u-1")
        .writeStringField("name", "Alice")
        .writeArrayFieldStart("tags")
            .writeString("admin").writeString("active")
        .writeEndArray()
        .writeNumberField("age", 30)
     .writeEndObject();
}
```

Factories: `createGenerator(Writer)`, `createGenerator(OutputStream)`, plus `WriteOptions`-aware overloads. The generator honors `prettyPrint`, `indentationSize`, `json5UnquotedKeys`, `json5SmartQuotes`, `allowNanAndInfinity`, and `maxStringLength`. Tree-only options (`showTypeInfo`, `cycleSupport`, custom writers, field filters) are not relevant at the streaming level and are ignored.

#### Splice streaming read into streaming write

`JsonGenerator` exposes `copyCurrentEvent(JsonTokenizer)` (single token) and `copyCurrentStructure(JsonTokenizer)` (current value + children) for "parse → transform → emit" pipelines:

```java
try (JsonTokenizer t = JsonIo.createTokenizer(input);
     JsonGenerator g = JsonIo.createGenerator(out)) {
    while (t.nextToken() != null) {
        g.copyCurrentEvent(t);
    }
}
```

#### Exception conventions

The streaming APIs use Jackson-aligned **checked exceptions** that extend `IOException`:
- `JsonParseException` — thrown by `JsonTokenizer` on malformed JSON or wrong-token-type calls (`getBooleanValue()` on a number, numeric overflow on `getIntValue()`, etc.). Mirrors Jackson's same-named class.
- `JsonGenerationException` — thrown by `JsonGenerator` on structural misuse (`writeFieldName` outside an object, mismatched `writeEndArray` over an object context, value emitted without a preceding field name, etc.). Mirrors Jackson's same-named class.

Both are checked and transparently caught by the `catch (IOException e)` blocks that the cursor-API methods already require — this is the Jackson convention for these surfaces specifically. The rest of json-io continues to use the unchecked `JsonIoException` for runtime errors; `JsonIo.toJava` / `toJson` (the tree-builder APIs) wrap any underlying `JsonParseException` so existing tree-API callers see the original message via the unchecked `JsonIoException` they already catch.

#### When to use streaming vs tree

| Use case | Recommended API |
|---|---|
| Cycle-bearing graphs, full type fidelity, custom readers/writers | `JsonIo.toJava` / `toJson` (tree, default) |
| Very large documents (avoid building a full in-memory `JsonObject` tree) | `JsonGenerator` / `JsonTokenizer` |
| Pass-through transform / re-emit pipelines | `copyCurrentEvent` / `copyCurrentStructure` |
| Direct port from Jackson `JsonGenerator` / `JsonParser` code | Streaming API (method-for-method) |

#### Binary payloads (`byte[]` / `ByteBuffer`)

As of 4.103.0, json-io has a complete binary story:

| Scenario | Recommended approach |
|---|---|
| Tree writer, `byte[]` field, **default** (interop-friendly) | `JsonIo.toJson(...)` emits `[1, 2, 3]` JSON number array; reader handles it transparently. Verbose for large blobs (≈ 4× expansion) but human-readable and language-agnostic. |
| Streaming writer, `byte[]` value | `gen.writeBinary(bytes)` — emits the wrapped form `{"@type":"byte[]","value":"<base64>"}` (≈ 1.33× expansion). Round-trips through `JsonIo.toJava` into the original `byte[]`. Slice overload available: `gen.writeBinary(bytes, offset, length)`. |
| Tree writer, `ByteBuffer` field | Default behaviour is already base64 — `JsonIo.toJson(...)` emits `{"value":"<base64>"}` and the reader decodes automatically. Recommended when wire-size matters and you can choose the field type. |
| Consuming JSON from Jackson / foreign sources where `byte[]` arrives as a bare base64 string | Just works: a JSON string read into a `byte[]` is Base64, as Jackson writes it (standard or URL-safe, padded correctly: `"AQ=="` is `{1}`, while `"AF"` and `"OK"` are not Base64), at the root, in a field, a collection, an array or a Map. A string that is not Base64 gives its own bytes. Hex and other notations are not guessed: read the value as a `String` and decode it with java-util's `ByteUtilities.decode`, which reads hex, hex dumps, MAC-address and number-list notations, Base64 and data URIs. |
| Shared `byte[]` instances across multiple fields | Use `WriteOptionsBuilder.preserveLeafContainerIdentity(true)` with the tree writer; emits `{"@id":N,"@items":[...]}` on the first occurrence and `{"@ref":N}` on subsequent ones. The reader preserves identity (`one == two` in Java). The wrapped form from `writeBinary` also supports `@id`/`@ref` sharing when hand-constructed. |

The `writeBinary` wrapped form `{"@type":"byte[]","value":"<base64>"}` is the recommended emit when writing `byte[]` via the streaming API — it carries unambiguous type information, decodes cleanly via java-util's `MapConversions.toByteArray`, and preserves identity across `@id`/`@ref` references. The bare-base64 string form that Jackson's `writeBinary` produces (and json-io's under `showTypeInfoNever()`) reads back too, as above, so json-io can consume Jackson output without configuration.

## Advanced Usage
Sometimes you will run into a class that does not want to serialize.  On the read-side, this can be a class that does
not want to be instantiated easily.  A class that has private constructors, constructor with many difficult to supply
arguments, etc. There are unlimited Java classes 'out-there' that `json-io` has never seen.  It can instantiate many classes, and
resorts to a lot of "tricks" to make that happen.  As of version 4.56.0 the library itself is compiled with the `-parameters` flag, 
allowing `json-io` to match JSON fields directly to constructor parameter names when your classes are also compiled with this flag.
This greatly reduces the need for custom factories when classes have accessible constructors with named arguments.  However, if a 
particular class is not instantiating, add a `ClassFactory` (one that you write, which subclasses this interface) and associate 
it to the class you want to instantiate. See [examples](/src/test/java/com/cedarsoftware/io/CustomJsonSubObjectsTest.java) for how to do this.
```java
ClassFactory    // Create a class that implements this interface
JsonClassWriter // Create a class that implements this interface
```

Your `ClassFactory` class is called after the JSON is parsed and `json-io` is converting all the Maps to
Java instances.  Your factory class is passed the `JsonObject` (a `Map`) with the fields and values from the JSON so that
you can **create** your class and **populate** it at the same time.  Use the `Resolver` to load complex fields
of your class (Non-primitives, Object[]'s, typed arrays, Lists, Maps), making things easy - you only have to worry about
the primitives in your class (see the examples below for how to 'tee up' the `Resolver` to load the sub-graph for
you.)

The [code examples](/src/test/java/com/cedarsoftware/io/CustomJsonSubObjectsTest.java) below show how to write a `ClassFactory` and `JSonClassWriter`.  There is a JUnit test case in the example
that illustrates how to associate your `ClassFactory` and `JsonClassWriter` to particular
classes. The [WriteOptions Reference](/user-guide-writeOptions.md) and [ReadOptions Reference](/user-guide-readOptions.md)
have lots of additional information for how to register your factory classes with the `ReadOptionsBuilder` and `WriteOptionsBuilder`.

### ClassFactory and CustomWriter Examples

- [Primitive fields](/src/test/java/com/cedarsoftware/io/CustomJsonTest.java)
- [Primitive and non-primitive fields (sub-graph)](/src/test/java/com/cedarsoftware/io/CustomJsonSubObjectTest.java)
- [Primitive, array, type array, List, Map](/src/test/java/com/cedarsoftware/io/CustomJsonSubObjectsTest.java)

### Writing Custom JsonClassWriter

As of 4.103.0, `JsonClassWriter` offers two emission APIs — the **`JsonGenerator`-based form** (recommended) and the legacy **`Writer + WriterContext`** form (deprecated, scheduled for removal in 5.0). The new form is Jackson-shaped (`writeStringField`, `writeNumberField`, `writeObjectField`, …) and handles JSON escape, auto-commas, pretty-printing, JSON5 unquoted keys / smart quotes, NaN/Infinity policy, and `maxStringLength` enforcement automatically.

**Basic Pattern (recommended — `JsonGenerator`):**
```java
class MyWriter implements JsonClassWriter<MyClass> {
    @Override
    public void write(MyClass instance, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        gen.writeStringField("name",  instance.getName());
        gen.writeNumberField("count", instance.getCount());
        gen.writeObjectField("data",  instance.getData());   // delegates to JsonIo for complex graph
    }
}
```

The framework opens the enclosing object (`{`) before calling your method and closes it (`}`) when you return — including any `@id` / `@type` prelude when policy allows. You emit field-name + value pairs for the body; auto-commas fire automatically. Unlike the legacy `WriterContext` API, **there is no "first field is different" pattern** — the generator's structural state knows whether a leading comma is needed.

**Key Methods on `JsonGenerator`:**

| Field-name + value (one call) | Description |
|---|---|
| `writeStringField(name, str)` | `"name":"<escaped>"` |
| `writeNumberField(name, int/long/double/BigDecimal)` | `"name":42` — overloads pick the right numeric form |
| `writeBooleanField(name, bool)` | `"name":true` / `"name":false` |
| `writeNullField(name)` | `"name":null` |
| `writeObjectField(name, pojo)` | `"name":<full serialization>` — delegates to `JsonIo.toJson` (cycles, custom writers, `@type` policy) |
| `writeArrayFieldStart(name)` | `"name":[` — pair with `writeEndArray()` |
| `writeObjectFieldStart(name)` | `"name":{` — pair with `writeEndObject()` |

| Standalone field-name then value | Description |
|---|---|
| `writeFieldName(name)` | `"name":` — next write call must produce the value |
| `writeString(str)` / `writeNumber(n)` / `writeBoolean(b)` / `writeNull()` | scalar value (auto-commas, escapes) |
| `writeStartObject()` / `writeEndObject()` | `{` … `}` for an object value |
| `writeStartArray()` / `writeEndArray()` | `[` … `]` for an array value |
| `writeObject(pojo)` | Full graph serialization for the current value slot (cycles, custom writers, `@type` policy) |

**Complete Example:**
```java
static class PersonWriter implements JsonClassWriter<Person> {
    @Override
    public void write(Person p, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        gen.writeStringField("first", p.firstName);
        gen.writeStringField("last",  p.lastName);
        gen.writeStringField("phone", p.phoneNumber);
        gen.writeStringField("dob",   p.dob.toString());

        // Complex types: writeObjectField delegates to JsonIo
        // (cycles, $id/$ref, nested custom writers all preserved)
        gen.writeObjectField("kids",    p.kids);     // Array
        gen.writeObjectField("friends", p.friends);  // Object[]
        gen.writeObjectField("pets",    p.pets);     // List
        gen.writeObjectField("items",   p.items);    // Map
    }
}
```

**Primitive form** (for objects that write as a JSON scalar instead of `{...}`):
```java
static class TagWriter implements JsonClassWriter<Tag> {
    @Override
    public boolean hasPrimitiveForm(WriterContext ctx) { return true; }

    @Override
    public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext ctx) throws IOException {
        gen.writeString(((Tag) o).getName());   // emits a bare quoted string instead of an object body
    }
}
```

**Benefits:**
- Automatic JSON escape (no manual `\"` handling)
- Automatic comma management (no "first field" boolean pattern)
- Honors `prettyPrint`, JSON5 unquoted keys / smart quotes, NaN/Infinity policy, `maxStringLength`
- `writeObjectField` delegates to the full tree-writer (cycles, references, `@id`/`@ref`, nested custom writers)
- Method names match Jackson's `JsonGenerator` — easy porting from `JsonSerializer` implementations

**Legacy form (still supported, deprecated):**

The deprecated `Writer + WriterContext` form remains in 4.x for backwards compatibility:
```java
@SuppressWarnings("deprecation")
class MyWriter implements JsonClassWriter<MyClass> {
    @Override
    public void write(MyClass instance, boolean showType, Writer output, WriterContext context) throws IOException {
        // First field: no leading comma
        context.writeFieldName("name");
        context.writeValue(instance.getName());
        // Subsequent fields: leading comma auto-emitted by writeStringField / writeNumberField / …
        context.writeStringField("category", instance.getCategory());
    }
}
```

Dispatch rules:
- If your class overrides only the deprecated `Writer`-based method, the legacy path is used.
- If your class overrides the new `JsonGenerator`-based method, the deprecated form is bypassed.
- If both are overridden (e.g. a base class that supports subclass chaining), the new form is preferred.

The legacy form will be removed in 5.0; migrating to the `JsonGenerator` overload is mechanical — replace `output.write(...)` / `context.writeXxx(...)` calls with the matching `gen.writeXxx(...)` calls and drop the "first field" branch.

### Giving Your Object as Data: `toData` (TOON, Shared Objects, Cycles)

`write` produces the JSON. Two parts of json-io work on an object's contents instead: **TOON**, which writes them,
and the **reference pass**, which finds objects written more than once and cycles before JSON or TOON is written.
Both reach an object through its fields. When your writer writes what the fields do not show — state the JDK keeps
in fields it does not open, a computed value, a different shape — override `toData` (since 4.113.0) to give the
object as plain data:

```java
class BoxWriter implements JsonClassWriter<Box> {
    @Override
    public void write(Box box, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        // Object.class: @type only where a field declared Object would need it, as json-io's own fields
        context.writeObjectField("item", box.computeItem(), Object.class);
    }

    @Override
    public Object toData(Box box) {      // the same values write() writes
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item", box.computeItem());
        return data;
    }
}
```

- **A `Map` of name to value:** TOON writes it as the object's fields, with the object's own type and id, and the
  reference pass visits its values. Use the names your `ClassFactory` reads; it reads TOON as it reads JSON (see
  [JSON and TOON](#writing-custom-classfactory-reader) for the one difference, a list inside the object).
- **Any other value:** written in the object's place, as an `Optional` is written as its content.
- **`JsonClassWriter.NO_DATA`** (the default): json-io uses the object's fields, as for any object.

Without it, an object your writer writes but the fields do not reach is invisible to the reference pass: if it also
appears elsewhere in the graph it is written twice and read back as two objects, and a cycle through it recurses until
the stack overflows. In TOON, the fields are written instead of what your writer writes. JSON output never changes:
it is still written by `write`. json-io's own writers for `Optional`, `OptionalInt`/`Long`/`Double`,
`AtomicReferenceArray`, `AtomicMarkableReference`, `AtomicStampedReference`, `AtomicIntegerArray`,
`AtomicLongArray`, `Semaphore`, `CountDownLatch`, `ReentrantLock` and `ReentrantReadWriteLock` use it.

For a writer of a **map or list class**, TOON consults `toData` only where it writes the object's `$type` (with
[type info](#type-info-in-toon) on): there the `Map` it gives is written after the type, as JSON writes the object;
elsewhere TOON writes the entries or elements as its own map or list. json-io's writers for `CompactMap`,
`CompactSet` and `MultiKeyMap` give their configuration this way.

### Writing Custom ClassFactory (Reader)

When creating custom readers, use the **Resolver convenience API** for cleaner, safer code. The API provides methods that handle type conversion and complex object deserialization automatically.

**Basic Pattern:**
```java
class MyFactory implements ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        MyClass instance = new MyClass();

        // Read primitives using convenience methods (automatic type conversion)
        instance.name = resolver.readString(jsonObj, "name");
        instance.count = resolver.readInt(jsonObj, "count");
        instance.price = resolver.readDouble(jsonObj, "price");
        instance.active = resolver.readBoolean(jsonObj, "active");

        // Read complex types (automatic deserialization with cycles/references)
        instance.data = resolver.readObject(jsonObj, "data", DataClass.class);
        instance.items = resolver.readList(jsonObj, "items");
        instance.config = resolver.readMap(jsonObj, "config");

        return instance;
    }
}
```

**Key Methods:**

**Primitive Types:**
- **`readString(jsonObj, fieldName)`** - Read string field with automatic conversion
- **`readInt(jsonObj, fieldName)`** - Read int field with automatic conversion
- **`readLong(jsonObj, fieldName)`** - Read long field with automatic conversion
- **`readFloat(jsonObj, fieldName)`** - Read float field with automatic conversion
- **`readDouble(jsonObj, fieldName)`** - Read double field with automatic conversion
- **`readBoolean(jsonObj, fieldName)`** - Read boolean field with automatic conversion

**Complex Types:**
- **`readObject(jsonObj, fieldName, type)`** - Read and fully deserialize an object
- **`readArray(jsonObj, fieldName, arrayType)`** - Read and deserialize a typed array (e.g., `String[].class`)
- **`readList(jsonObj, fieldName)`** - Read and deserialize a List
- **`readMap(jsonObj, fieldName)`** - Read and deserialize a Map

**JSON and TOON:** the same factory reads both. One difference shows in the raw values: a list inside the object is an
`Object[]` when JSON is read and a `List` when TOON is read (TOON's lists stay lists, as its Maps mode returns them).
The read methods above take either. A factory that reads `jsonObj.get(...)` directly should accept a `Collection` as
well as an array.

**A factory that fills its object itself** (`isObjectFinal()` true) — a container whose contents cannot be set through
fields — reads each value with **`resolveHeldValue(jsonObj, value, type, later)`**. A `@ref` to an object not yet
built returns null for now, and `later` puts that object in place once the graph is read:

```java
box.item = (Item) resolver.resolveHeldValue(jsonObj, jsonObj.get("item"), Item.class,
        (built, value) -> ((Box) built).item = (Item) value);
```

**Complete Example:**
```java
// From CustomJsonSubObjectsTest.java
static class PersonFactory implements ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        Person person = new Person();

        // Read primitives with automatic type conversion
        person.firstName = resolver.readString(jsonObj, "first");
        person.lastName = resolver.readString(jsonObj, "last");
        person.phoneNumber = resolver.readString(jsonObj, "phone");
        person.dob = resolver.readObject(jsonObj, "dob", OffsetDateTime.class);

        // Read complex types with full deserialization
        person.kids = resolver.readArray(jsonObj, "kids", TestObjectKid[].class);
        person.friends = resolver.readArray(jsonObj, "friends", Object[].class);
        person.pets = resolver.readList(jsonObj, "pets");
        person.items = resolver.readMap(jsonObj, "items");

        return person;
    }
}
```

**Benefits:**
- ✅ No manual Map casting or instanceof checks
- ✅ Automatic type conversion via Converter
- ✅ Full support for complex types (cycles, references, @id/@ref)
- ✅ Cleaner, more maintainable code

### Streaming custom reader (Jackson-port style)

`ClassFactory` + `Resolver` is the recommended path for custom readers: it composes with all of json-io's graph features (`@id` / `@ref` cycle resolution, `@type` polymorphism, sub-object dispatch through nested factories and custom readers, type coercion via `Converter`). For two specific use cases — porting a Jackson `JsonDeserializer` with minimal change, or reading a very large single-shape document where the tree pass is the cost you want to avoid — `JsonTokenizer` is available directly, outside the custom-reader machinery:

```java
public class Pojo {
    String id;
    int count;
}

public Pojo readPojo(InputStream in) throws IOException {
    try (JsonTokenizer t = JsonIo.createTokenizer(in)) {
        Pojo p = new Pojo();
        require(t.nextToken() == JsonToken.START_OBJECT, "expected {");
        while (t.nextToken() != JsonToken.END_OBJECT) {
            String field = t.currentName();
            t.nextToken();                     // advance to the value token
            switch (field) {
                case "id":    p.id    = t.getText();     break;
                case "count": p.count = t.getIntValue(); break;
                default: t.skipChildren();     // unknown field — skip its value
            }
        }
        return p;
    }
}
```

**What you give up by going around `ClassFactory`:**
- `@id` / `@ref` cycle resolution — the tokenizer emits these as plain `@id` / `@ref` field tokens; you track them yourself if you want graph identity
- `@type` polymorphism — the declared class is whatever you wrote in your reader code
- Sub-object dispatch through `ClassFactory` / `JsonClassReader` for nested types — your code recurses by hand
- The `Resolver` convenience methods (`readObject`, `readList`, `readMap`) and `Converter`-driven type coercion

**What you gain:**
- No tree allocation — no `JsonObject` Map built first
- Token-by-token control identical to Jackson's `JsonParser.nextToken()`
- Method-for-method portability from Jackson `JsonDeserializer` implementations (`t.currentName()` ⇔ `parser.currentName()`, `t.getIntValue()` ⇔ `parser.getIntValue()`, etc.)

**Why no `ClassFactory.newInstance(Class, JsonTokenizer, Resolver)` overload?**
Custom readers run *after* the framework has resolved `@id` / `@type` / `@ref` metadata and is mid-graph-walk — they receive a `JsonObject` tree because the resolver needs to keep tracking sub-object dispatch and cycle references after the factory returns. A token-stream factory would have to either re-implement those features inside each user-written factory or bypass them entirely. Jackson has the same asymmetry on its side: `JsonSerializer` works against a streaming `JsonGenerator`, but `JsonDeserializer` works against a `JsonParser` that has no equivalent of the graph machinery `ObjectMapper` adds on top.

If you only need direct tokenizer access (no graph features), use `JsonIo.createTokenizer(...)` outside the custom-reader path entirely — that is the supported pattern for the Jackson-port use case.

### Order of Type Resolution and Substitution

#### Aliases (aliases.txt) - First
- Used during writing and reading
- Primarily for shortening class names in JSON output
- Example: `java.math.BigInteger = BigInteger`
- Lightweight, just changes the string representation
- Doesn't affect class loading or behavior
- **Annotation equivalent:** `@IoTypeName("ShortName")` on a class
#### Coerced Types (coercedTypes.txt) - Second
- Used during class instantiation (`Resolver`)
- Changes actual class used for instantiation
- Example: `java.util.RegularEnumSet = java.util.EnumSet`
- More invasive as it affects the actual type created
- Should be used sparingly, only when:
  - Handling internal implementation classes (like `RegularEnumSet`)
  - Managing backward compatibility with older serialized forms
  - Dealing with JDK implementation details that shouldn't leak into JSON
#### ClassFactory (classFactory.txt) - Third
- Used during object instantiation
- Controls how instances are created and populated
- Most flexible and powerful mechanism
- Proper place for custom instantiation logic
- Examples: `EnumSetFactory`, `CollectionFactory`, etc.
- **Annotation equivalent:** `@IoClassFactory(MyFactory.class)` on a class
#### Custom Readers/Writers - (When applicable)
- Used for special serialization/deserialization logic
- Can completely override normal processing
- Most complex but most powerful
- Strongly recommended: Use `ClassFactory` instead of a `CustomReader` as it **creates** and **loads**.
- **Annotation equivalents:** `@IoCustomReader(MyReader.class)`, `@IoCustomWriter(MyWriter.class)` on a class
---
## Javascript
Included is a small Javascript utility (`jsonUtil.js` in the root folder) that will take a JSON output
stream created by the JSON writer and substitute all `@ref`s for the actual pointed to object.  It's a one-line
call - `resolveRefs(json)`. This will substitute `@ref` tags in the JSON for the actual pointed-to (`@id`) object.

## Additional uses for json-io
Even though **json-io** is great for Java / Javascript serialization, here are some other uses for it:

#### Cloning
Many projects use `JsonIo` to write an object to JSON, then read it in, cloning the original object graph:
```java
Employee emp;
// emp obtained from somewhere...
Employee deepCopy = (Employee) JsonIo.deepCopy(emp, null, null);   // ReadOptions, WriteOptions can be null
```
#### Debugging
Instead of `System.out.println()` debugging, call `JsonIo.toJson(obj, writeOptions)` and dump the JSON
string out. That will give you the full referenceable graph dump in JSON.  Use the prettyPrint feature of `WriteOptions`
to make the JSON more human-readable.

#### Type Conversion
json-io includes an extensive type conversion system powered by the [java-util](https://github.com/jdereg/java-util) library.
This allows you to convert between nearly any Java types during deserialization. Run the `JsonIo.main()` method to see
the complete list of supported conversions:

```java
// See all supported conversions
java -cp your-classpath com.cedarsoftware.io.JsonIo
```

## LoggingConfig
[Source](https://github.com/jdereg/java-util/blob/master/src/main/java/com/cedarsoftware/util/LoggingConfig.java)

`LoggingConfig` applies a consistent console format for `java.util.logging`.
Call `LoggingConfig.init()` once during application startup. You may supply a
custom timestamp pattern via `LoggingConfig.init("yyyy/MM/dd HH:mm:ss")` or the
system property `ju.log.dateFormat`.

## Redirecting java.util.logging

`json-io` uses `java.util.logging.Logger` (JUL) internally so as to bring in no depencies to other libraries except `java-util`. Most applications prefer frameworks like SLF4J, Logback or Log4j&nbsp;2. You can bridge JUL to your chosen framework so that logs from this library integrate with the rest of your application.

**All steps below are application-scoped**&mdash;set them up once during your application's initialization.

---

**Optional: Using JUL directly with consistent formatting**

If you are not bridging to another framework, call `LoggingConfig.init()` early in your application's startup. This configures JUL's `ConsoleHandler` with a formatted pattern. Pass a custom pattern via `LoggingConfig.init("yyyy/MM/dd HH:mm:ss")` or set the system property `ju.log.dateFormat`.

```java
// Example initialization
public static void main(String[] args) {
    LoggingConfig.init();
    // ... application startup
}
```

You may also start the JVM with

```bash
java -Dju.log.dateFormat="HH:mm:ss.SSS" -jar your-app.jar
```

---

### Bridging JUL to other frameworks

To route JUL messages to a different framework, add the appropriate bridge dependency and perform a one-time initialization.

#### 1. SLF4J (Logback, Log4j&nbsp;1.x)

Add `jul-to-slf4j` to your build and install the bridge:

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>jul-to-slf4j</artifactId>
    <version>2.0.7</version>
</dependency>
```

```java
import org.slf4j.bridge.SLF4JBridgeHandler;

public class MainApplication {
    public static void main(String[] args) {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }
}
```

#### 2. Log4j&nbsp;2

Add `log4j-jul` and set the `java.util.logging.manager` system property:

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-jul</artifactId>
    <version>2.20.0</version>
</dependency>
```

```bash
java -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager \
     -jar your-app.jar
```

Once configured, JUL output flows through your framework's configuration.

## System Properties

json-io uses several system properties to control behavior and optimize performance. Most are handled automatically, but understanding them can be helpful for troubleshooting or advanced usage.

### Runtime System Properties

#### `java.version`
**Used by**: `com.cedarsoftware.io.reflect.Injector` class  
**Purpose**: Automatically detected to determine the optimal field injection strategy  
**Values**: Automatically set by JVM  
**Behavior**:
- **JDK 8-16**: Uses `Field.set()` for final fields and a `MethodHandle` for regular fields
- **JDK 17+**: Uses a `MethodHandle` for every field

This is handled automatically by json-io and requires no user configuration. The library adapts its internal field injection mechanisms based on the detected JDK version for optimal performance and compatibility.

### Test Environment Properties

When running json-io's test suite, the following system properties are automatically set to ensure consistent behavior across different environments:

#### Test Standardization Properties
- **`user.timezone=America/New_York`**: Ensures consistent date/time handling
- **`user.language=en`**: Standardizes locale-dependent behavior  
- **`user.region=US`**: Sets region for locale consistency
- **`user.country=US`**: Sets country for locale consistency

These properties are set automatically during testing via Maven and generally don't affect runtime usage of json-io in applications.

### Logging Configuration Properties

#### `ju.log.dateFormat`
**Used by**: `LoggingConfig.init()` method from java-util dependency  
**Purpose**: Customizes timestamp format for java.util.logging output  
**Default**: Standard timestamp format  
**Example**:
```bash
java -Dju.log.dateFormat="yyyy/MM/dd HH:mm:ss" -jar your-app.jar
```

Or programmatically:
```java
LoggingConfig.init("yyyy/MM/dd HH:mm:ss");
```

### Maven Test Properties

#### `-Dtest=...`
**Used by**: Maven Surefire plugin  
**Purpose**: Run specific test classes or patterns  
**Examples**:
```bash
# Run a specific test class
mvn test -Dtest=SecurityTest

# Run tests matching a pattern
mvn test -Dtest="*EnumSet*"

# Run performance tests
mvn test -Dtest=JsonPerformanceTest
```

## Environment Variables

json-io does not use any environment variables for configuration. All behavior is controlled through Java APIs (ReadOptions/WriteOptions) or system properties as documented above.
