json-io
=======

Perfect Java serialization to and from JSON format (available on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cjson-io)). To include in your project:

    <dependency>
      <groupId>com.cedarsoftware</groupId>
      <artifactId>json-io</artifactId>
      <version>4.0.0</version>
    </dependency>

[Donations welcome](https://coinbase.com/jdereg)

**json-io** consists of two main classes, a reader (`JsonReader`) and a writer (`JsonWriter`).  **json-io** eliminates the need for using `ObjectInputStream / ObjectOutputStream` to serialize Java and instead uses the JSON format.  There is a 3rd optional class (`JsonObject`) see 'Non-typed Usage' below.

**json-io** does not require that Java classes implement `Serializable` or `Externalizable` to be serialized, unlike `ObjectInputStream` / `ObjectOutputStream`.  It will serialize any Java object graph into JSON and retain complete graph semantics / shape and object types.  This includes supporting private fields, private inner classes (static or non-static), of any depth.  It also includes handling cyclic references.  Objects do not need to have public constructors to be serialized.  The output JSON will not include `transient` fields, identical to the ObjectOutputStream behavior.

The `JsonReader / JsonWriter` code does not depend on any native or 3rd party libraries.

_For useful and powerful Java utilities, check out java-util at http://github.com/jdereg/java-util_

### Format
**json-io** uses proper JSON format.  As little type information is included in the JSON format to keep it compact as possible.  When an object's class can be inferred from a field type or array type, the object's type information is left out of the stream.  For example, a `String[]` looks like `["abc", "xyz"]`.

When an object's type must be emitted, it is emitted as a meta-object field `"@type":"package.class"` in the object.  When read, this tells the JsonReader what class to instantiate.

If an object is referenced more than once, or references an object that has not yet been defined, (say A points to B, and B points to C, and C points to A), it emits a `"@ref":n` where 'n' is the object's integer identity (with a corresponding meta entry `"@id":n` defined on the referenced object).  Only referenced objects have IDs in the JSON output, reducing the JSON String length.

### Performance
**json-io** was written with performance in mind.  In most cases **json-io** is faster than the JDK's `ObjectInputStream / ObjectOutputStream`.  As the tests run, a log is written of the time it takes to serialize / deserialize and compares it to `ObjectInputStream / ObjectOutputStream` (if the static variable `_debug` is `true` in `TestUtil`).

### Usage
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

This 'Maps' representation can be re-written to a JSON String or Stream and _the output JSON will exactly match the original input JSON stream_.  This permits a JVM receiving JSON strings / streams that contain class references which do not exist in the JVM that is parsing the JSON, to completely read / write the stream.  Additionally, the Maps can be modified before being written, and the entire graph can be re-written in one collective write.  _Any object model can be read, modified, and then re-written by a JVM that does not contain any of the classes in the JSON data!_

### Optional Arguments (Features)

Both the `JsonWriter` and `JsonReader` allow you to pass in an optional arguments `Map<String, Object>`.  This `Map` has well known keys (constants from `JsonWriter` / `JsonWriter`).  To enable the respective feature, first create a `Map`.  Then place the well known key in the `Map` and associate the appropriate setting as the value.  Below is a complete list of features and some example usages.  Shown in Groovy for brevity.

    Map args = [
            (JsonWriter.SHORT_META_KEYS):true,
            (JsonWriter.TYPE_NAME_MAP):[
                'java.util.ArrayList':'alist', 
                'java.util.LinkedHashMap':'lmap', 
                (TestObject.class.getName()):'testO'
            ]
    ]
    String json = JsonWriter.objectToJson(list, args)
        
In this example, we create an 'args' `Map`, set the key `JsonWriter.SHORT_META_KEYS` to `true` and set the `JsonWriter.TYPE_NAME_MAP` to a `Map` that will be used to substitute class names for short-hand names.         

#### All of the optional values below are public constants from `JsonWriter`, used by placing them as keys in the arguments map.

    CUSTOM_WRITER_MAP       // Set to Map<Class, JsonWriter.JsonClassWriterEx> to
                            // override the default JSON output for a given class. 
    NOT_CUSTOM_WRITER_MAP   // Set to Collection<Class> to indicate classes that should
                            // not be written by a custom writer.
    DATE_FORMAT             // Set this format string to control the format dates are 
                            // written. Example: "yyyy/MM/dd HH:mm".  Can also be a 
                            // DateFormat instance.  Can also be the constant 
                            // JsonWriter.ISO_DATE_FORMAT or 
                            // JsonWriter.ISO_DATE_TIME_FORMAT 
    TYPE                    // Set to boolean true to force all data types to be 
                            // output, even where they could have been omitted.
    PRETTY_PRINT            // Force nicely formatted JSON output 
                            // (See http://jsoneditoronline.org for example format)
    FIELD_SPECIFIERS        // Set to a Map<Class, List<String>> which is used to 
                            // control which fields of a class are output. 
    ENUM_PUBLIC_ONLY        // If set, indicates that private variables of ENUMs are not 
                            // serialized.
    WRITE_LONGS_AS_STRINGS  // If set, longs are written in quotes (Javascript safe).
                            // JsonReader will automatically convert Strings back
                            // to longs.  Any Number can be set from a String.
    TYPE_NAME_MAP           // If set, this map will be used when writing @type values.
                            // Allows short-hand abbreviations for type names.
    SHORT_META_KEYS         // If set, then @type => @t, @keys => @k, @items => @e,
                            // @ref => @r, and @id => @i 

#### All of the optional values below are public constants from `JsonReader`, used by placing them as keys in the arguments map.

    CUSTOM_READER_MAP       // Set to Map<Class, JsonReader.JsonClassReaderEx> to
                            // override the default JSON reader for a given class. 
    NOT_CUSTOM_READER_MAP   // Set to Collection<Class> to indicate classes that should
                            // not be read by a custom reader.
    USE_MAPS                // If set to boolean true, the read-in JSON will be 
                            // turned into a Map of Maps (JsonObject) representation. Note
                            // that calling the JsonWriter on this root Map will indeed
                            // write the equivalent JSON stream as was read.
    TYPE_NAME_MAP           // If set, this map will be used when writing @type values. 
                            // Allows short-hand abbreviations of type names.
    UNKNOWN_TYPE            // Set to null (or leave out), unknown objects are returned
                            // as Maps.  Set to String class name, and unknown objects 
                            // will be created as with this class name, and the fields 
                            // will be set on it. Set to false, and an exception will be 
                            // thrown when an unknown object type is encountered.  The 
                            // location in the JSON will be given.
      
### Customization
New APIs have been added to allow you to associate a custom reader / writer class to a particular class if you want it to be read / written specially in the JSON output.  **json-io** 1.x required a custom method be implemented on the object which was having its JSON format customized.  This support has been removed.  That approach required access to the source code for the class being customized.  The new **json-io** 2.0+ approach allows you to customize the JSON format for classes for which you do not have the source code.

    Example (in Groovy). Note the Person has a List of pets, and in this case, it re-uses 
    JsonWriter to write that part of the class out (not need to customize it):
    
    static class CustomPersonWriter implements JsonWriter.JsonClassWriterEx
    {
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            Person p = (Person) o
            output.write('"first":"')
            output.write(p.getFirstName())
            output.write('","last":"')
            output.write(p.getLastName())
            JsonWriter writer = Support.getWriter(args)
            writer.writeImpl(p.getPets(), true)
        }
    }

### Javascript
Included is a small Javascript utility that will take a JSON output stream created by the JSON writer and substitute all `@ref's` for the actual pointed to object.  It's a one-line call - `resolveRefs(json)`.  This will substitute `@ref` tags in the JSON for the actual pointed-to object.  In addition, the `@keys` / `@items` will also be converted into Javascript Maps and Arrays.  Finally, there is a Javascript API that will convert a full Javascript object graph to JSON, (even if it has cycles within the graph).  This will maintain the proper graph-shape when sending it from the client back to the server.

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
Instead of doing `System.out.println()` debugging, call `JsonWriter.objectToJson(obj)` and dump that String out.  It will reveal the object in all it's glory.

### Pretty-Printing JSON
Use `JsonWriter.formatJson()` API to format a passed in JSON string to a nice, human readable format.  Also, when writing JSON data, use the `JsonWriter.objectToJson(o, args)` API, where args is a `Map` with a key of `JsonWriter.PRETTY_PRINT` and a value of 'true' (`boolean` or `String`).  When run this way, the JSON written by the `JsonWriter` will be formatted in a nice, human readable format.

### RESTful support
**json-io** can be used as the fundamental data transfer method between a Javascript / JQuery / Ajax client and a web server in a RESTful fashion. Used this way, you can create more active sites like Google's GMail, MyOtherDrive online backup, etc.

See https://github.com/jdereg/json-command-servlet for a light-weight servlet that processes Ajax / XHR calls.

Featured on http://json.org.

### Sponsors
![Alt text](https://www.yourkit.com/images/yklogo.png "Yourkit")
YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/index.jsp">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/index.jsp">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

### Revision History
 * 4.0.0
  * Custom readers / writers are set now per-instance of `JsonReader` / `JsonWriter`, not static.  This allows using different customization for cloning, for example, than for serialization to client.
  * `JsonReader.jsonToJava()` and `JsonReader.jsonToMaps()` now allow an `InputStream` to be used.
  * Custom readers / writers can now be set all-at-once through the optional 'args' `Map`.
  * 'notCustom' readers / writers can now be set all-at-once through the optional 'args' `Map`.
  * The `removeReader()`, `removeWriter()`, `removeNotCustomReader()`, and `removeNotCustomWriter()` APIs have been removed since customizers are set per-instance. 
 * 3.3.2
  * Added new `JsonObject.isReference()` API which will return 'true' if the `JsonObject` is currently representing a reference `@ref`
  * Added new `JsonReader.getRefTarget(jsonObject)` API which will follow the `@ref` links until it resolves to the referenced (target) instance.
  * Added new `JsonReader()` constructor that only takes the args (`Map`).  It is expected that you will call `JsonReader.jsonObjectsToJava(rootJsonObject)` which will parse the passed in JsonObject graph.
  * Added new `JsonReader.removeReader()` API to remove a custom reader association to a given class.
  * Added new `JsonWriter.removeWriter()` API to remove a custom writer association to a given class.
  * Added new `JsonReader.removeNotCustomReader()` API to remove a `not custom` reader - if a `notCustom()` reader has been added (preventing inherited object from using custom reader), the association can be eliminated using this API.
  * Added new `JsonWriter.removeNotCustomWriter()` API to remove a `not custom` writer - if a `notCustom()` writer has been added (preventing inherited object from using custom writer), the association can be eliminated using this API.
 * 3.3.1
  * Re-entrancy issue fixed.  If a CustomReader (or CustomWriter) instantiated another copy of JsonReader or JsonWriter (indirectly, through recursion, for example), the 2nd instance of JsonReader or JsonWriter would clobber the ThreadLocal values inside JsonReader / JsonWriter.  Those ThreadLocal values have been removed and converted to per-instance member variables.
 * 3.3.0
  * Consolidate all 3.2.x changes
  * Last snippet read no longer shows 'boxes' for unused internal buffer characters.
  * `JsonWriter` - moved reference check 'up' to `writeImpl()` so that each specific 'write' routine did not have to test / call `writeOptionalReference()`.
  * If you have a custom reader that does not bother to resolve references from 'deeper' internal `JsonObject` maps, an exception will no longer be thrown.  It is OK for a custom reader not to 'care' about internal deeper fields if it wants to ignore them.
 * 3.2.3
  * Cache Map's for custom reader's updated to be `ConcurrentMap` instead of `Map`.
 * 3.2.2
  * `JsonCustomReaderEx` added, which passes the 'args' `Map` through to the custom reader.
  * Both `JsonCustomReaderEx` and `JsonCustomWriterEx` have a `Map` as the last argument in their single method that is implemented by the custom reader / writer.  This `Map` is the same as the 'args' `Ma` passed into to the `JsonReader` / `JsonWriter`, with the added `JSON_READER` or `JSON_WRITER` key and associated value of the calling `JsonReader` / `JsonWriter` instance.
 * 3.2.1
  * Made `Support.getWriter()` method `public static` so that CustomWriters can easily use it
  * Changed `JsonCustomWriterEx` to no longer inherit from `JsonCustomWriter` and instead added a common parent (`JsonCustomWriterBase`).  This allows only one method to be overridden to create a `JsonCustomWriterEx`.
 * 3.2.0
  * New `JsonCustomWriterEx` interface which adds the `JsonWriter` access to the implementing class so that it can call back and use `jsonWriter.writeImpl()` API.
  * Change `JsonWriter.writeImpl()` from protected to public
 * 3.1.3
  * Performance improvement: No longer using .classForName() inside JsonObject to determine isMap() or isCollection().  Reading JSON into Map of Maps mode significantly faster.
 * 3.1.2
  * Bug fix: Version 3.1.1 introduced a bug where it would always run as though it was in JSON to Java mode always (as opposed to supporting JSON to Maps).  This has been fixed.
 * 3.1.1 
  * `JsonReader.UNKNOWN_OBJECT` added as an option to indicate what to do when an unknown object is encountered in the JSON.  Default is a `Map` will be created.  However, you can set this argument to a `String` class name to instantiate, or set it to false to force an exception to be thrown.
 * 3.1.0
  * **New Feature**: Short class names to reduce the size of the output JSON. This allows you to, for example, substitute `java.util.HashMap` with `hmap` so that it will appear in the JSON as `"@type":"hmap"`.  Pass the substitution map to the `JsonWriter` (or reader) as an entry in the args `Map` with the key of `JsonWriter.TYPE_NAME_MAP` and the value as a `Map` instance with String class names as the keys and short-names as the values. The same map can be passed to the `JsonReader` and it will properly read the substituted types.
  * **New Feature**: Short meta-key names to reduce the size of the output JSON.  The `@type` key name will be shortened to `@t`, `@id` => `@i`, `@ref` => `@r`, `@keys` => `@k`, `@items` => `@e`.  Put a key in the `args` `Map` as `JsonWriter.SHORT_META_KEYS` with the value `true`.   
 * 3.0.2
  * Bug fix: Using a CustomReader in a Collection with at least two identical elements causes an exception (submitted by @KaiHufenbach).    
 * 3.0.1
  * Added new flag `JsonWriter.WRITE_LONGS_AS_STRINGS` which forces long/Long's to be written as Strings.  When sending JSON data to a Javascript, longs can lose precision because Javascript only maintains 53-bits of info (Javascript uses IEEE 754 `double` for numbers).  The precision is lost due to some of the bits used for maintaining an exponent.  With this flag set, longs will be sent as Strings, however, on return back to a Java server, json-io allows Strings to be set right back into long (fields, array elements, collections, etc.)
 * 3.0.0
   * Performance improvement: caching the custom readers and writes associated to given classes.
   * Ease of use: `json-io` throws a `JsonIoException` (unchecked) instead of checked exception `IOException`.  This allows more flexibility in terms of error handling for the user.
   * Code cleanup: Moved reflection related code from `JsonReader` into separate `MetaUtils` class.
   * Code cleanup: Moved `FastPushbackReader` from `JsonReader` into separate class.
   * Code cleanup: Moved JSON parsing code from `JsonReader` into separate `JsonParser` class.
   * Code cleanup: Moved built-in readers from `JsonReader` to separate `Readers` class.
   * Code cleanup: Moved resolver code (marshals map of maps to Java instances) into separate `Resolver` classes.
 * 2.9.4
  * `JsonReader.newInstance()` API made public
  * Bumped version of junit from 4.11 to 4.12
  * Added additional tests to ensure that null and "" can be properly assigned to primitive values (matching behavior of java-util's `Converter.convert()` API).
 * 2.9.3
  * Bug fix: When writing a `Map` with JSON primitive keys (`String`, `Long`, `Double`, or `Boolean`), a `ClassCastException` was being thrown if the type was `Long`, `Double`, or `Boolean`.  This has been fixed with test added.
 * 2.9.2
  * Android: Rearranged `[:.]` to `[.:]` in regular expressions for Android compatibility.  Technically, it should not matter, but `[:.]` was causing `java.util.regex.PatternSyntaxException: Syntax error U_ILLEGAL_ARGUMENT_ERROR` on Android JVM.
  * Bug fix: When using the `JsonWriter` arguments `Map` with `FIELD_SPECIFIERS`, if you specified a field that was transient, it was not serialized.  This has been corrected.  When you specify the field list for a given class, the `Map` can contain any non-static fields in the class, including transient fields.
  * All JUnit tests converted to Groovy.
 * 2.9.1
  * Bug fix: Parameterized types are only internally stamped onto generic Maps (Maps read with no `@type`) if the field that points to the `Map` is a template variable or it has template arguments.
  * Performance optimization: tracing references specially handles `Collection` and `Map`.  By avoiding internal structures, the reference trace is much faster.
 * 2.9.0
  * Unmodifiable `Collections` and `Maps` can now be serialized.
  * Added tests to ensure that `JsonReader.jsonToMaps()` coerces the RHS values when logical primitives, to the optional associated `@type's` fields.
  * More tests and improved code-coverage.
 * 2.8.1
  * Bug fix: `JsonReader.jsonToMaps()` API was incorrectly attempting to instantiate peer objects (specified by "@type" field in the JSON) when in 'maps' mode.  This made `JsonReader.jsonToMaps()` fail if all referenced class names did not exist in the JVM.  This has been fixed.
  * Minor Javadoc cleanup (Daniel Darabos @darabos)
  * Began migration of tests from one monolithic Java class (`TestJsonReaderWriter`) to individual Groovy test classes.
 * 2.8.0
  * Additional attempt to instantiate classes via `sun.misc.Unsafe` added (optional must be turned on by calling `JsonReader.setUseUnsafe(true)`). json-io already tries all constructors (private or public) with varying arguments, etc.  If this fails and unsafe is true, it will try `sun.misc.Unsafe.allocateInstance()` which effectively does a C-style `malloc()`.  This is OK, because the rest of `JsonReader` fills in the member variables from the serialized content.  (Submitted by @KaiHufenbach).
 * 2.7.6
  * Performance optimizations.  Use of switch statement instead of if-else chains.
  * JDK 1.7 for source code and target JVM.
 * 2.7.5
  * Bug fix: ArrayIndexOutOfBounds could still occur when serializing a class with multiple Templated fields.  The exception has been fixed.
 * 2.7.4
  * Bug fix: ArrayIndexOutOfBounds exception occurring when serializing non-static inner class with nested template parameters.  JsonReader was incorrectly passing on the 'this$0' field for further template argument processing when it should not have.
 * 2.7.3
  * `JsonReader` executes faster (more efficiently manages internal 'snippet' buffer and last line and column read.)
  * Improved date parsing: day of week support (long or short name), days with suffix (3rd, 25th, etc.), Java's default `.toString()` output for `Date` now parses, full time zone support, extra whitespace allowed within the date string.
  * Added ability to have custom JSON writers for interfaces (submitted by @KaiHufenbach).
 * 2.7.2
  * When writing JSON, less memory is used to manage referenced objects.  `JsonWriter` requires a smaller memory foot print during writing.
  * New option available to JsonWriter that allows you to force enums to not write private variables.  First you can make them transient.  However, if you do not own the code or cannot change it, you can set the `JsonWriter.getArgs().put(ENUM_PUBLIC_ONLY, true)`, and then only public fields on enums will be emitted.
 * 2.7.1
  * `BigDecimal` and `BigInteger` are now always written as a primitive (immutable, non-referenced) value.  This uniformizes their output.
 * 2.7.0
  * Updated to support JSON root of `String`, `Integer`, Floating point, and `Boolean`, per the updated JSON RFP.  Example, the `String` "football" is considered valid JSON.  The `JsonReader.readObject()` API and `JsonReader.jsonToJava()` will return a `String` in this case.  The `JsonReader.jsonToMaps()` API will still return a `Map (JsonObject)`, and the `@items` key will contain an `Object[]` with the single value (`String, Integer, Double, Boolean`) in it.
  * When a Java `Map` has only `String` keys in it, json-io will use the JSON object keys directly and associate the values to the keys as expected.  For example, the `Map` ['Football':true] would be written `{"Football":true}`.  However, if the keys are non-Strings, then Maps will be written as a JSON object with `{"@keys":[...], "@items":[...]}`, where `@keys` is an array [] of all the keys, and the `@items` is an array [] of all the values.  Entry 0 of `@keys` matches with Entry 0 in the `@items` array, and so on.  Thanks for Christian Reuschling for making the request and then supplying the implementation.
  * Change some APIs from `private` to `protected` to allow for subclasses to more easily override the default behavior.
 * 2.6.1
  * Bug fix: An internal `Map` that kept meta-information about a Java Class, changed to `ConcurrentHashMap` from `HashMap`.
 * 2.6.0
  * Added support for specifying which fields on a class will be serialized.  Use the `JsonWriter.FIELD_SPECIFIERS` key and assign the value to a `Map<Class, List<String>>`, where the keys of the `Map` are classes (e.g. Bingo.class) and the values are `List<String>`, which indicates the fields to serialize for the class.  This provides a way to reduce the number of fields written for a given class.  For example, you may encounter a 3rd Party class which fails to serialize because it has an oddball field like a `ClassLoader` reference as a non-static, non-transient field. You may not have access to the source code to mark the field as `transient`. In this case, add the appropriate entries in the `FIELD_SPECIFIERS` map. Voila, problem solved. Use the `JsonWriter` API that takes `optionalArgs Map`.  The key for this `Map` is `JsonWriter.FIELD_SPECIFIER` and the value is `Map<Class, List<String>>`.
 * 2.5.2
  * `java.net.URL` can now be used as a constructor argument.  The reader was throwing an exception instantiating a constructor with a `URL` parameter.
  * `java.lang.Object` parameters in constructor arguments are now tried with both null and `new Object()` now.
 * 2.5.1
  * Fixed a bug (introduced in 2.5.0) in the processing of a `Map` that has a `Collection` as a key.
 * 2.5.0
  * New 'Pretty-Print' option available.  If the 'args' Map passed to `JsonWriter.objectToJson(o, args)` contains the key `JsonWriter.PRETTY_PRINT` and the value 'true' (`boolean` or `String`), the `JsonWriter` output will be formatted in a nice human readable format.
  * Convert a JSON String to Pretty-Print format using `JsonWriter.formatJson(String json)`.  A `String` will be returned with the JSON formatted in a nice, human readable format.
  * If a Field contains Parameterized types (e.g., `Map<String, Set<Long>>`, and so on), `JsonReader` will use those fields to process objects deep within Maps, Collections, etc. and still create the proper Java class.
 * 2.4.5
  * Allow "" to be set into `Date` field, setting the `Date` field (or `Date` array element) as null.
 * 2.4.4
  * Allow "" to be set into `BigInteger` or `BigDecimal` when return value is `Map` (`JsonObject`). "" to non-String fields will be null, except for primitives and primitive wrappers, that will result in JVM default value.
 * 2.4.2
  * Allow "" to be set into non-String fields, when doing so, null is set on Object type fields; on primitive fields, the JVM default value is set. This is for when converting JSON to Java objects directly.
 * 2.4.1
  * Added support to allow primitives and `String` to be assigned to abstract / interface / base type field on an object (`Serializable`, `Comparable`, `Object`, etc.). Primitives can now be 'set' into these fields, without any additional type information.
 * 2.4.0
  * Primitives can be set from `Strings`
  * `Strings` can be set from primitives
  * `BigDecimal` and `BigInteger` can be set from primitives, `Strings`, `BigDecimal`, or `BigInteger`
 * 2.3.0
  * `Maps` and `Collections` (`Lists`, `Set`, etc.) can be read in, even when there are no `@keys` or `@items` as would come from a Javascript client.
  * **json-io** will now use the generic info on a `Map<Foo, Bar>` or `Collection<Foo>` object's field when the `@type` information is not included. **json-io** will then know to create `Foo` instances, `Bar` instances, etc. within the `Collection` or `Map`.
  * All parsing error messages now output the last 100 characters read, making it easier to locate the problem in JSON text. Furthermore, line and column number are now included (before it was a single position number). This allows you to immediately find the offending location.
  * You can now force `@type` to be written (not recommended) by putting the `JsonWriter.TYPE` key in the `JsonWriter` args map, and assigning the associated value to `true`.
 * 2.2.32
  * Date/Time format can be customized when writing JSON output. New optional `Map args` parameter added to main API of `JsonWriter` that specifies additional parameters for `JsonWriter`. Set the key to `JsonWriter.DATE_FORMAT` and the value to a `SimpleDateFormat` string.  Two ISO formats are available for convenience as constants on `JsonWriter`, `JsonWriter.ISO_DATE_FORMAT` and `JsonWriter.ISO_DATE_TIME_FORMAT`.
  * `JsonReader` updated to read many different date/time formats.
  * When `JsonReader` encounters a class that cannot be constructed, you can associate a `ClassFactory` to the class, so that then the un-instantiable class is encountered, your factory class will be called to create the class. New API: `JsonReader.assignInstantiator(Class c, ClassFactory factory)`
 * 2.2.31
  * Adds ability to instantiate a wider range of constructors. This was done by attempting construction with both null and non-null values for many common class types (`Collections`, `String`, `Date`, `Timezone`, etc.)
 * 2.2.30
  * `java.sql.Date` when read in, was instantiated as a `java.util.Date`. This has been corrected.
 * 2.2.29
  * First official release through Maven Central

by John DeRegnaucourt
