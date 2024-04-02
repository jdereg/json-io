## Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.

### Typed Usage

_Example 1: Java object to JSON String_

    Employee emp;
    // Emp fetched from database
    String json = JsonIo.toJson(emp, writeOptions);     // 'writeOptions' argument discussed in detail below

This example will convert the `Employee` instance to a JSON String.  If the `JsonReader` were used on this `String`,
it would reconstitute a Java `Employee` instance.

_Example 2: String to Java object_

    String json = // String JSON representing Employee instance
    Employee employee = JsonIo.toObjects(json, readOptions, Employee.class);  // 'readOptions' argument discussed below

This will convert the JSON String to a Java Object graph.

_Example 3: Java Object to `OutputStream`_

    Employee emp;
    // emp obtained from data store...
    JsonIo.toJson(outputStream, emp, writeOptions);       

In this example, a Java object is written to an `OutputStream` in JSON format.  The stream is closed when finished.  If
you need to keep the `OutputStream` open (e.g. NDJSON), then set `writeOptions.closeStream(false).` Example:

    WriteOptions writeOptions = new WriteOptionsBuilder().closeStream(false).build();
    JsonIo.toJson(outputStream, record1, writeOptions);    
    JsonIo.toJson(outputStream, record2, writeOptions);
    ...
    JsonIo.toJson(outputStream, recordn, writeOptions);
    outputStream.close();


_Example 4: `InputStream` to Java object_

    Employee emp = JsonIo.toObjects(stream, readOptions, Employee.class);

In this example, an`InputStream`is supplying the JSON.

### Untyped Usage
**json-io** provides the choice to use the generic `Map` of `Maps` representation of an object, akin to a Javascript
associative array.  When reading from a JSON `String` or`InputStream`of JSON, use `JsonIo:`

    String json = // or InputStream to JSON providing source
    ReadOptions readOptions = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();
    Map root = JsonIo.toObjects(json, readOptions);    

See the `ReadOptions` below for the feature control options.  In the example #4 above, rather than return the objects
converted into Java classes, the raw JSON values being parsed is returned as `Maps`.  It is a graph that consists of
all `Map` instances, arrays, and primitive types.  

When `Map` is returned, your root value will represent one of:
* `a JSON object {...}`
  * `Map` that represents any JSON object {...}.
* `JSON array [...]`
  * `Map` that has a key of `@items` which represents any JSON array [...].
* `JSON primitive (boolean true/false, null, long, double, String).`

This `Map` representation can be re-written to JSON String or Stream and the output JSON will match the
original input JSON stream.  This permits you to receive JSON strings / streams that contain class references which
do not exist in the JVM that is parsing the JSON, to completely read the String/Stream, perhaps manipulate the content,
and then rewrite the String/stream.

---
## Javascript
Included is a small Javascript utility (`jsonUtil.js` in the root folder) that will take a JSON output
stream created by the JSON writer and substitute all`@ref's`for the actual pointed to object.  It's a one-line
call -`resolveRefs(json)`. This will substitute`@ref`tags in the JSON for the actual pointed-to (`@id`) object.

## Additional uses for json-io
Even though **json-io** is great for Java / Javascript serialization, here are some other uses for it:

#### Cloning
Many projects use `JsonIo` to write an object to JSON, then read it in, cloning the original object graph:

    Employee emp;
    // emp obtained from somewhere...
    Employee deepCopy = (Employee) JsonIo.deepCopy(emp, null, null);   // ReadOptions, WriteOptions can be null

#### Debugging
Instead of `System.out.println()` debugging, call `JsonIo.toJson(obj, writeOptions)` and dump the JSON
string out. That will give you the full referenceable graph dump in JSON.  Use the prettyPrint feature of `WriteOptions`
to make the JSON more human-readable.
