## Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.

### Typed Usage

_Example 1: Java object graph to JSON String_
```java
Employee emp;
// Emp fetched from database
String json = JsonIo.toJson(emp, writeOptions);
```
This example will convert the `Employee` instance to a JSON String, including nested sub-objects.  If
`JsonIo.toJava(json, readOptions).asClass(Employee.class)` were used on this JSON `String,` a new Java `Employee` instance would be returned.

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
LinkedHashMap ordering), arrays, and primitive types.

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
- Non-standard number formats (.5, 5., +5)
- Infinity and NaN literals

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

When creating custom writers, use the **WriterContext semantic API** for cleaner, safer code. The API provides methods that handle quote escaping, comma management, and proper JSON formatting automatically.

**Basic Pattern:**
```java
class MyWriter implements JsonClassWriter {
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        MyClass instance = (MyClass) obj;

        // First field: no leading comma
        context.writeFieldName("fieldName");
        context.writeValue(instance.getFieldValue());

        // Subsequent fields: automatic comma handling
        context.writeStringField("name", instance.getName());
        context.writeNumberField("count", instance.getCount());
        context.writeObjectField("data", instance.getData());
    }
}
```

**Key Methods:**

- **`writeFieldName(name)`** - Writes field name with colon (no comma): `"name":`
- **`writeValue(value)`** - Writes any value with automatic type detection and escaping
- **`writeStringField(name, value)`** - Complete string field with comma: `,"name":"value"`
- **`writeNumberField(name, number)`** - Complete number field with comma: `,"count":42`
- **`writeBooleanField(name, bool)`** - Complete boolean field with comma: `,"active":true`
- **`writeObjectField(name, obj)`** - Complete object field with full serialization: `,"data":{...}`
- **`writeArrayFieldStart(name)`** - Field name with opening bracket: `,"items":[`
- **`writeObjectFieldStart(name)`** - Field name with opening brace: `,"config":{`

**Why First Field is Different:**

Custom writers are called *inside* the object that JsonWriter has already opened with `{`. The first field should NOT have a leading comma:

```json
{
  "fieldName": "value",    // ← First field (no comma)
  "name": "John",          // ← Subsequent fields (comma)
  "count": 42
}
```

**Complete Example:**
```java
// From CustomJsonSubObjectsTest.java
static class PersonWriter implements JsonClassWriter {
    public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException {
        Person p = (Person) o;

        // First field: no leading comma
        context.writeFieldName("first");
        context.writeValue(p.firstName);

        // Subsequent fields: include leading comma
        context.writeStringField("last", p.lastName);
        context.writeStringField("phone", p.phoneNumber);
        context.writeStringField("dob", p.dob.toString());

        // Complex types: automatic serialization with cycles/references
        context.writeObjectField("kids", p.kids);      // Array
        context.writeObjectField("pets", p.pets);      // List
        context.writeObjectField("items", p.items);    // Map
    }
}
```

**Benefits:**
- ✅ Automatic quote escaping (no manual `\"` handling)
- ✅ Automatic comma management (no "boolean first" pattern)
- ✅ Type-safe methods for primitives (no manual formatting)
- ✅ Full support for complex types (cycles, references, @id/@ref)
- ✅ Cleaner, more maintainable code

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

### Order of Type Resolution and Substitution

#### Aliases (aliases.txt) - First
- Used during writing and reading
- Primarily for shortening class names in JSON output
- Example: `java.math.BigInteger = BigInteger`
- Lightweight, just changes the string representation
- Doesn't affect class loading or behavior
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
#### Custom Readers/Writers - (When applicable)
- Used for special serialization/deserialization logic
- Can completely override normal processing
- Most complex but most powerful
- Strongly recommended: Use `ClassFactory` instead of a `CustomReader` as it **creates** and **loads**.
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
- **JDK 8-16**: Uses `Field.set()` for final fields and `MethodHandle` for regular fields
- **JDK 17+**: Uses `VarHandle` for improved performance and module system compatibility

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
