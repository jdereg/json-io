json-io
=======

Perfect Java serialization to and from JSON format (available on Maven Central). To include in your project:
```
<dependency>
  <groupId>com.cedarsoftware</groupId>
  <artifactId>json-io</artifactId>
  <version>2.4.4</version>
</dependency>
```

**json-io** consists of two main classes, a reader (`JsonReader`) and a writer (`JsonWriter`).  There is a 3rd rigorous test class (`TestJsonReaderWriter`).  **json-io** eliminates the need for using `ObjectInputStream / ObjectOutputStream` to serialize Java and instead uses the JSON format.  

**json-io** does not require that Java classes implement `Serializable` or `Externalizable` to be serialized, unlike `ObjectInputStream` / `ObjectOutputStream`.  It will serialize any Java object graph into JSON and retain complete graph semantics / shape and object types.  This includes supporting private fields, private inner classes (static or non-static), of any depth.  It also includes handling cyclic references.  Objects do not need to have public constructors to be serialized.  The output JSON will not include `transient` fields, identical to the ObjectOutputStream behavior.

The `JsonReader / JsonWriter` code does not depend on any native or 3rd party libraries.

Please send 0.1 **Bitcoin** to **1461PT1TYVng8uftp2gg5iG6q4xV7tR6h5** if you use json-io in your software.  This will help ensure that more great open source software like json-io is created.

_For useful and powerful Java utilities, check out java-util at http://github.com/jdereg/java-util_

### Format
**json-io** uses proper JSON format.  As little type information is included in the JSON format to keep it compact as possible.  When an object's class can be inferred from a field type or array type, the object's type information is left out of the stream.  For example, a `String[]` looks like `["abc", "xyz"]`. 

When an object's type must be emitted, it is emitted as a meta-object field `"@type":"package.class"` in the object.  When read, this tells the JsonReader what class to instantiate.

If an object is referenced more than once, or references an object that has not yet been defined, (say A points to B, and B points to C, and C points to A), it emits a `"@ref":n` where 'n' is the object's integer identity (with a corresponding meta entry `"@id":n` defined on the referenced object).  Only referenced objects have IDs in the JSON output, reducing the JSON String length.

### Performance
**json-io** was written with performance in mind.  In most cases **json-io** is faster than the JDK's `ObjectInputStream / ObjectOutputStream`.  As the tests run, a log is written of the time it takes to serialize / deserialize and compares it to `ObjectInputStream / ObjectOutputStream` (if the static variable `_debug` is `true` in `TestJsonReaderWriter`).

Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.  

_Example 1: String to Java object_

    Object obj = JsonReader.jsonToJava("[\"Hello, World\"]");

This will convert the JSON String to a Java Object graph.  In this case, it would consist of an `Object[]` of one `String` element.

_Example 2: Java object to JSON String_

    Employee emp;
    // Emp fetched from database
    String json = JsonWriter.objectToJson(emp);

This example will convert the `Employee` instance to a JSON String.  If the `JsonReader` were used on this `String`, it would reconstitute a Java `Employee` instance.

_Example 3: `InputStream` to Java object_

    JsonReader jr = new JsonReader(inputStream);
    Employee emp = (Employee) jr.readObject();

In this example, an `InputStream` (could be from a File, the Network, etc.) is supplying an unknown amount of JSON.  The `JsonReader` is used to wrap the stream to parse it, and return the Java object graph it represents.

_Example 4: Java Object to `OutputStream`_

    Employee emp;
    // emp obtained from database
    JsonWriter jw = new JsonWriter(outputStream);
    jw.write(emp);
    jw.close();

In this example, a Java object is written to an output stream in JSON format.

### Non-typed Usage
**json-io** provides the choice to use the generic "Map of Maps" representation of an object, akin to a Javascript associative array.  When reading from a JSON String or `InputStream` of JSON, the `JsonReader` can be constructed like this:

    Map graph = JsonReader.jsonToMaps(String json);

-- or --

    JsonReader jr = new JsonReader(InputStream, true);
    Map map = (Map) jr.readObject();

This will return an untyped object representation of the JSON String as a `Map` of Maps, where the fields are the `Map` keys (Strings), and the field values are the associated Map's values. In this representation the `Map` instance returned is actually a `JsonObject` instance (from **json-io**).  This `JsonObject` implements the `Map` interface permitting access to the entire object.  Cast to a `JsonObject`, you can see the type information, position within the JSON stream, and other information.  

This 'Maps' representation can be re-written to a JSON String or Stream and _the output JSON will exactly match the original input JSON stream_.  This permits a JVM receiving JSON strings / streams that contain class references which do not exist in the JVM that is parsing the JSON, to completely read / write the stream.  Additionally, the Maps can be modified before being written, and the entire graph can be re-written in one collective write.  _Any object model can be read, modified, and then re-written by a JVM that does not contain any of the classes in the JSON data_.

### Customization
New APIs have been added to allow you to associate a custom reader / writer class to a particular class if you want it to be read / written specially in the JSON output.  **json-io** 1.x required a custom method be implemented on the object which was having its JSON format customized.  This support has been removed.  That approach required access to the source code for the class being customized.  The new **json-io** 2.0 approach allows you to customize the JSON format for classes for which you do not have the source code.

#### Dates
To specify an alternative date format for JsonWriter:

    Map args = new HashMap();
    args.put(JsonWriter.DATE_FORMAT, JsonWriter.ISO_DATE_TIME);
    String json = JsonWriter.objectToJson(root, args);

In this example, the ISO yyyy/MM/ddTHH:mm:ss format is used to format dates in the JSON output. The 'value' associated to the 'DATE_FORMAT' key can be JsonWriter.ISO_DATE_TIME, JsonWriter.ISO_DATE, a date format String pattern (eg. "yyyy/MM/dd HH:mm"), or a java.text.Format instance.

### Javascript
Included is a small Javascript utility that will take a JSON output stream created by the JSON writer and substitute all `@ref's` for the actual pointed to object.  It's a one-line call - `resolveRefs(json)`.  This will completely fix up the `@ref's` to point to the appropriate objects.

### What's next?
Even though **json-io** is perfect for Java / Javascript serialization, there are other great uses for it: 

### Cloning
Many projects use `JsonWriter` to write an object to JSON, then use the `JsonReader` to read it in, perfectly cloning the original object graph:

    Employee emp;
    // emp obtained from database
    Employee deepCopy = (Employee) cloneObject(emp);

    public Object cloneObject(Object root)
    {
        return JsonReader.jsonToJava(JsonWriter.objectToJson(root));    
    }

### Debugging
Instead of doing System.out.println debugging, call `JsonWriter.toJson(obj)` and dump that String out.  It will reveal the object in all it's glory.  Take that output and paste it into a JSON lint / formatter so you can can easily read it: See http://jsonformatter.curiousconcept.com/ and http://www.jsonlint.com/


### RESTful support
**json-io** can be used as the fundamental data transfer method between a Javascript / JQuery / Ajax client and a web server in a RESTful fashion. Used this way, you can create more active sites like Google's GMail, MyOtherDrive online backup, etc.  

See https://github.com/jdereg/json-command-servlet for a light-weight servlet that processes Ajax / XHR calls.

Featured on http://json.org.
 * 2.4.4
  * Allow "" to be set into BigInteger or BigDecimal when return value is Map (JsonObject). "" to non-String fields will be null, except for primitives and primitive wrappers, that will result in JVM default value.
 * 2.4.2
  * Allow "" to be set into non-String fields, when doing so, null is set on Object type fields, no primitive fields, the JVM default value is set. This is for when converting JSON to Java objects directly.
 * 2.4.1
  * Added support to allow primitives and String to be assigned to abstract / interface / base type field on an object (Serializable, Comparable, Object, etc.). Primitives can now be 'set' into these fields, without any additional type information.
 * 2.4.0
  * Primitives can be set from Strings
  * Strings can be set from primitives
  * BigDecimal and BigInteger can be set from primitives, Strings, BigDecimal, or BigInteger
 * 2.3.0
  * Maps and Collections (Lists, Set, etc.) can be read in, even when there are no `@keys` or `@items` as would come from a Javascript client.
  * **json-io** will now use the generic info on a `Map<Foo, Bar>` or `Collection<Foo>` object's field when the `@type` information is not included. **json-io** will then know to create `Foo` instances, `Bar` instances, etc. within the `Collection` or `Map`.
  * All parsing error messages now output the last 100 characters read, making it easier to locate the problem in JSON text. Furthermore, line and column number are now included (before it was a single position number). This allows you to immediately find the offending location.
  * You can now force `@type` to be written (not recommended) by putting the `JsonWriter.TYPE` key in the `JsonWriter` args map, and assigning the associated value to `true`.
 * 2.2.32
  * Date/Time format can be customized when writing JSON output. New optional `Map args` parameter added to main API of `JsonWriter` that specifies additional parameters for `JsonWriter`. Set the key to `JsonWriter.DATE_FORMAT` and the value to a `SimpleDateFormat` string.  Two ISO formats are available for convenience as constants on `JsonWriter`, `JsonWriter.ISO_DATE_FORMAT` and `JsonWriter.ISO_DATE_TIME_FORMAT`.
  * `JsonReader` updated to read many different date/time formats.
  * When JsonReader encounters a class that cannot be constructed, you can associate a `ClassFactory` to the class, so that then the un-instantiable class is encountered, your factory class will be called to create the class. New API: `JsonReader.assignInstantiator(Class c, ClassFactory factory)`
 * 2.2.31
  * Adds ability to instantiate a wider range of constructors. This was done by attempting construction with both null and non-null values for many common class types (Collections, String, Date, Timezone, etc.)
 * 2.2.30
  * `java.sql.Date` when read in, was instantiated as a `java.util.Date`. This has been corrected.
 * 2.2.29
  * First official release through Maven Central

by John DeRegnaucourt
