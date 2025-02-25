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
resorts to a lot of "tricks" to make that happen.  However, if a particular class is not instantiating, add a
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

- Custom `ClassFactory` and `JsonClassWriter` built for [CompactMap](https://github.com/jdereg/java-util/blob/master/userguide.md#compactmap)

  - [CompactMap reader](src/main/java/com/cedarsoftware/io/factory/CompactMapFactory.java) (`ClassFactory`)
  - [CompactMap writer](src/main/java/com/cedarsoftware/io/writers/CompactMapWriter.java)  (`JsonClassWriter`)

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