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

### Untyped Usage
**json-io** provides the choice to use the generic `Map` of `Maps` representation of an object, akin to a Javascript
associative array.  When reading from a JSON `String` or `InputStream` of JSON, use `JsonIo`:

```java 
String json = // or InputStream to JSON providing source
ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
Map root = JsonIo.toJava(json, readOptions).asClass(Map.class);    
```
See the `ReadOptions` below for the feature control options. In the provided example, rather than returning the objects
converted into Java classes, the raw JSON values are parsed and returned as `Maps`. This forms a graph consisting of all
`Map` instances, arrays, and primitive types.

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
// Read JSON into Map structure
ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
Map<String, Object> jsonMap = JsonIo.toJava(jsonString, readOptions).asClass(Map.class);

// Manipulate values
jsonMap.put("name", "John Doe");
((Map)jsonMap.get("address")).put("city", "New York");

// Write back to JSON
String updatedJson = JsonIo.toJson(jsonMap, writeOptions);
```

Each `JsonObject` retains the raw `@type` value from the input JSON. Call
`getTypeString()` to retrieve this value without triggering type resolution.

### Parsing JSON with Unknown Classes
If the JSON contains class references that are not available on the classpath,
configure the reader to skip type resolution and return a graph of `Map`
instances. This allows arbitrary JSON to be loaded, inspected, and
re-serialized.

```java
ReadOptions opts = new ReadOptionsBuilder()
        .returnAsJsonObjects()
        .failOnUnknownType(false)
        .build();
Map<String, Object> graph = JsonIo.toJava(json, opts).asClass(Map.class);
```

All objects will be represented as Maps (or collections) so the entire structure
can be traversed or modified without requiring the referenced classes.

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

## Advanced Usage
Sometimes you will run into a class that does not want to serialize.  On the read-side, this can be a class that does
not want to be instantiated easily.  A class that has private constructors, constructor with many difficult to supply
arguments, etc. There are unlimited Java classes 'out-there' that `json-io` has never seen.  It can instantiate many classes, and
resorts to a lot of "tricks" to make that happen.  As of version 4.56.0 the library itself is compiled with the `-parameters` flag, allowing `json-io` to match JSON fields directly to constructor parameter names when your classes are also compiled with this flag.  This greatly reduces the need for custom factories when classes have accessible constructors with named arguments.  However, if a particular class is not instantiating, add a
`JsonReader.ClassFactory` (one that you write, which subclasses this interface) and associate it to the class you want to
instantiate. See [examples](/src/test/java/com/cedarsoftware/io/CustomJsonSubObjectsTest.java) for how to do this.
```java
JsonReader.ClassFactory    // Create a class that implements this interface
JsonWriter.JsonClassWriter // Create a class that implements this interface
```

Your `JsonReader.ClassFactory` class is called after the JSON is parsed and `json-io` is converting all the Maps to
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
class MyWriter implements JsonWriter.JsonClassWriter {
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
static class PersonWriter implements JsonWriter.JsonClassWriter {
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
class MyFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jsonObj, Resolver resolver) {
        MyClass instance = new MyClass();

        // Read primitives using convenience methods (automatic type conversion)
        instance.name = resolver.readString(jsonObj, "name");
        instance.count = resolver.readInt(jsonObj, "count");
        instance.price = resolver.readDouble(jsonObj, "price");
        instance.active = resolver.readBoolean(jsonObj, "active");

        // Read complex types (automatic deserialization with cycles/references)
        instance.data = resolver.readObject(jsonObj, "data", DataClass.class);
        instance.items = resolver.readList(jsonObj, "items", ItemClass.class);
        instance.config = resolver.readMap(jsonObj, "config", String.class, Object.class);

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
- **`readList(jsonObj, fieldName, elementType)`** - Read and deserialize a List
- **`readMap(jsonObj, fieldName, keyType, valueType)`** - Read and deserialize a Map

**Complete Example:**
```java
// From CustomJsonSubObjectsTest.java
static class PersonFactory implements JsonReader.ClassFactory {
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
        person.pets = resolver.readList(jsonObj, "pets", TestObjectKid.class);
        person.items = resolver.readMap(jsonObj, "items", String.class, Object.class);

        return person;
    }
}
```

**Benefits:**
- ✅ No manual Map casting or instanceof checks
- ✅ Automatic type conversion via Converter
- ✅ Full support for complex types (cycles, references, @id/@ref)
- ✅ No need to create JsonReader manually for sub-objects
- ✅ Cleaner, more maintainable code

### CompactMap Usage

Support for [CompactMap](https://github.com/jdereg/java-util/blob/master/userguide.md#compactmap) is built in.

- Use `CompactMap` directly when the defaults work for you.
- Choose a provided subclass from **java-util** for common configurations.
- Derive your own subclass to bake in specific settings.
- The `builder()` API allows per-instance configuration. It generates helper
  classes at run time so the application must run with a full JDK, not just the
  JRE.

json-io includes:

  - [CompactMap reader](src/main/java/com/cedarsoftware/io/factory/CompactMapFactory.java) (`ClassFactory`)
  - [CompactMap writer](src/main/java/com/cedarsoftware/io/writers/CompactMapWriter.java) (`JsonClassWriter`)

### Order of Type Resolution and Substitution

#### Aliases (aliases.txt) - First
- Used during writing (`JsonWriter`) and reading (`JsonReader`)
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
