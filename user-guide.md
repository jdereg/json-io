### Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.

_Example 1: String to Java object_

    String json = // some JSON content
    Object obj = JsonReader.toObjects(json);     // optional 2nd 'options' argument (see below)

This will convert the JSON String to a Java Object graph.

_Example 2: Java object to JSON String_

    Employee emp;
    // Emp fetched from database
    String json = JsonWriter.toJson(emp);     // optional 2nd 'options' argument (see below)

This example will convert the `Employee` instance to a JSON String.  If the `JsonReader` were used on this `String`, 
it would reconstitute a Java `Employee` instance.

_Example 3: `InputStream` to Java object_

    Employee emp = (Employee) JsonReader.toObjects(stream);  // optional 2nd 'options' argument (see below)

In this example, an `InputStream` is supplying the JSON.

_Example 4: Java Object to `OutputStream`_

    Employee emp;
    // emp obtained from database
    JsonWriter jw = new JsonWriter(outputStream);       // optional 2nd 'options' argument (see below)
    jw.write(emp);
    jw.close();

In this example, a Java object is written to an output stream in JSON format.

### Non-typed Usage
**json-io** provides the choice to use the generic "Map of Maps" representation of an object, akin to a Javascript associative array.  When reading from a JSON String or `InputStream` of JSON, the `JsonReader` can be constructed like this:

    String json = // some JSON obtained from wherever
    ReadOptions = new ReadOptionsBuilder().returnAsMaps().build();
    Object obj = JsonReader.toJava(json, readOptions);    

There are plenty of other `read` options, as well as `write` options that can be passed in this way.  They are listed below.
In this example, it will return an untyped object representation of the JSON String as a `Map` of Maps, where the fields are the
`Map` keys (Strings), and the field values are the associated Map's values. In this representation the returned data consists
of Maps, Arrays (Object[]), and JSON values.  The Maps are actually a `JsonObject` instance (from **json-io**).  This 
`JsonObject` implements the `Map` interface permitting access to the entire object.  Cast to a `JsonObject`, you can see 
the type information, position within the JSON stream, and other information.  

This 'Maps' representation can be re-written to a JSON String or Stream and _the output JSON will exactly match the
original input JSON stream_.  This permits a JVM receiving JSON strings / streams that contain class references which 
do not exist in the JVM that is parsing the JSON, to completely read / write the stream.  Additionally, the Maps can 
be modified before being written, and the entire graph can be re-written in one collective write.  _Any object model 
can be read, modified, and then re-written by a JVM that does not contain any of the classes in the JSON data._
---
#### The optional values below are public methods on the `WriteOptions.`

To pass these to `JsonWriter.toJson(root, writeOptions)` set up a `WriteOptions` like below.  The 
`WriteOptions` contains all the "feature" sttings for json-io output JSON.  Below, we show many of the
`WriteOptions` APIs.  See the Javadoc on WriteOptions for detailed information. The `WriteOptions` can be made
stateless options by calling the .seal() method. Once sealed, the options cannot be modified.  If you have multiple
`WriteOptions` features, you can set up distinct instances for each main usage.  A `WriteOptions` can be created from
another `WriteOptions` instance.

    WriteOptions writeOptions = new WriteOptions().prettyPrint(true).writeLongsAsStrings(true);
    JsonWriter.toJson(root, writeOptions);

Set to String Class name, JsonWriter.JsonClassWriter to override the default
JSON output for a given class, or set a bunch of them at once:

    .withCustomWriter(Class, JsonWriter.JsonClassWriter)
    .withCustomWriters(Map<Class, JsonClassWriter>)

    writeOptions.getCustomWriters()     // Get all custom writers
 
Prevent customized writer for a particular class.  Since these are inherited, you may
want to "turn off" write-customization that was unintentionally picked up.

    .withNoCustomizationFor(Class)
    .withNoCustomizationsFor(Collection<Class>)

    writeOptions.getNonCustomClasses()  // Get classes that will not have custom writers

Set the date format for how dates are written.  Example: "yyyy/MM/dd HH:mm".  
                                                                            
    .withDateFormat(String)
    .withIsoDateFormat()
    .withIsoDateTimeFormat()

    writeOptions.getDateFormat()        // Get date format

Force class types to be written out for all JSON objects, or never show Type info, or show the
minimum amount of type info. Shows up as "@type" or "@t" fields on a JSON object.  Only needed
when the reader is unable to determine what type of class to instantiate.  This can happen with a field
that is of type Object and the instance side is specific.  Same with Object[]'s and or List<Object>, etc.

    .showTypeInfoAlways()
    .showTypeInfoNever()
    .showTypeInfoMinimal()

    writeOptions.isAlwaysShowingType()  // To test setting
    writeOptions.isNeverShowingType()
    writeOptions.isShowingMinimalType()
 
Force nicely formatted JSON output.  (See http://jsoneditoronline.org for example format)

    .withPrettyPrint()

    writeOptions.isPrettyPrint()        // Test the setting

Specify which fields to include in the output JSON for a particular class, or a many classes at once:

    .includedFields(Class, Collection<String>)
    .includedFields(Map<Class, Collection<String>>)

    writeOptions.getIncludedFields()    // Get all the Classes and their included fields' lists.

Specify which fields to exclude in the JSON output for a particular class, or many classes at once.

    .excludedFields(Class, Collection<String>)
    .excludedFields(Map<Class, Collection<String>>)

    writeOptions.getExcludedFields()    // Get all the Classes and their excludeed fields' lists.

Specify that only public variables of ENUMs are serialized:

    .doNotWritePrivateEnumFields()
    .writePrivateEnumFields()

    writeOptions.isEnumPublicOnly()     // To check setting.

To have Longs written as strings in quotes, which is Javascript safe.  Obscure bugs happen in Javascript
when a full 19 digit long is sent as-is to Javascript (because Javascript stores them in a double internally).

    .writeLongsAsStrings()

To use custom, shorthand abbreviations for type names (the value-side of the @type field), you can specify
type name mappings:

    .withCustomTypeName(Class, String)
    .withCustomTypeName(String, String)
    .withCustomTypeNames(Map<String, String>)

To make the JSON more compact, you can reduce the size of "meta field" names. @type ==> @t, @ref ==> @r, @id ==> @i, @keys ==> @k, @items ==> @e

    .withShortMetaKeys()

If you do not want to write field values ot JSON when their values are null:

    .skipNullFields()

Set the `ClassLoader` to use when turning String class names into JVM classes:

    .withClassLoader(ClassLoader)

Force Map output to use @keys/@values even if a Map contains all String keys:

    .forceMapOutputAsKeysAndValues()
    .doNotForceMapOutputAsKeysAndValues()
---
#### The optional values below are public methods on the `ReadOptionsBuilder.`

    CUSTOM_READER_MAP       // Set to Map<Class, JsonReader.JsonClassReader> to
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
    FAIL_ON_UNKNOWN_TYPE    // Set to Boolean.TRUE to have JSON reader throw JsonIoException
                            // when a @type value references a class that is not loadable
                            // from the class loader. Set to any other value (or leave out)
                            // and JSON parsing will return a Map to represent the unknown
                            // object defined by the invalid @type value.
    CLASSLOADER             // ClassLoader instance to use when turning String names of     
                            // classes into JVM Class instances.
      
### Customization

#### Customization technique 1: Drop unwanted fields
* **Included fields**: Let's say a class that you want to serialize has a field on it that you do not want written out, like a `ClassLoader` reference.
Use the `WriteOptionsBuilder().includedFields(...)` to associate a `List` of `String` field names to a particular `Class` C.  When the class
is being written, only the fields you list will be written.

* **Excluded fields**: Let's say a class that you want to serialize has a field on it that you do not want written out, like a `ClassLoader` reference.
Use the `WriteOptionsBuilder.excludedFields(...)` to associate a `List` of `String` field names to a particular `Class` C.  When the class
is being written, any field listed here will not be written.  Excluded fields take priority over included fields.

#### Customization technique 2: Custom instantiator  `JsonReader.assignInstantiator(Class c, ClassFactory)`
There are times when **json-io** cannot instantiate a particular class even though it makes many attempts to instantiate 
a class, including looping through all the constructors (public, private) and invoking them with default values, etc.  
However, sometimes a class just cannot be constructed, for example, one that has a constructor that throws an exception 
if particular parameters are not passed into it.
                                                                                  
In these instances, use the `JsonReader.assignInstantiator(Class c, ClassFactory)` to assign a `ClassFactory` 
that you implement to instantiate the class. **json-io** will call your `ClassFactory.newInstance(Class c, JsonObject jObj)` 
to create the class that it could not construct.  You can pull field values from the jObj to create and return your instance.
If you do load the values completely into your class, then override the inherited method `isObjectFinal()` and return `true`. No further
processing will happen on your instance.  If you  decide to only use your ClassFactory to instantiate the class, after
your code creates the instance, **json-io** will reflectively stuff the values from the `jObj` (`JsonObject`) into the
instance you created.
 
#### Customization technique 3: Shorter meta-keys (@type -> @t, @id -> @i, @ref -> @r, @keys -> @k, @items -> @e)  
Use a `new WriteOptions()` and set `withShortMetaKeys()` to see the single-letter meta keys used in the outputted JSON.  
In addition to the shorter meta keys, you can and a list of substitutions of your own to use.  For example, you may want to see 
`alist` instead of `java.util.ArrayList`.  This is only applicable if you are writing with @types in the JSON.

      WriteOptions writeOptions = new WriteOptions().
        withShortMetaKeys().
        withCustomTypeName('java.util.ArrayList', 'alist').
        withCustomTypeName('java.util.LinkedHashMap', 'lmap').
      String json = JsonWriter.toJson(yourObject, writeOptions)
           
#### Customization technique 4: Custom serializer
New APIs have been added to allow you to associate a custom reader / writer class to a particular class if you want it 
to be read / written specially in the JSON output.  The **json-io** approach allows you to customize the JSON format for 
classes for which you do not have the source code.

    Example: Note the Person has a List of pets, and in this case, it re-uses 
    JsonWriter to write that part of the class out (no need to customize it):
    
    public static class CustomPersonWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, WriteOptions writeOptions) throws IOException
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
 
#### Customization technique 5: Processing JSON from external sources.
When reading JSON from external sources, you may want to start with:

    ReadOptions readOptions = new ReadOptionsBuilder().
        returnAsMaps().build();
    Object data = JsonReader.toObjects(json, readOptions);

This will get the JSON read into memory, in a Map-of-Maps format, similar to how JSON is read into memory in Javascript. 
This will get you going right away.
  
To write 'generic' JSON (without `@type` or `@items`, etc.) entries, use:

    WriteOptions writeOptions = new WriteOptions().showTypeInfo(WriteOptions.ShowType.NEVER);
    String json = JsonWriter.toJson(objToWrite, writeOptions);
    
Objects will not include the `@type` flags or `@items`.  This JSON passes nicely to non-Java receivers, like Javascript. 
Keep in mind, you will be working with the JSON as generic `object.field` and `object[index]` with this approach.  

Please note that if you write your object graph out without showing types, the shape of the graph will be 
maintained.  What this means, is that if two different places in your object graph point to the same object, the first 
reference will write the actual object, the 2nd and later references will write a reference (`@ref`) to the first instance.
This will read in just fine with `JsonReader.toObjects()`, and the appropriate `Map` reference will be placed in all 
referenced locations.  If reading this in Javascript, make sure to use the included `jsonUtil.js` to parse the read in JSON
so that it can perform the substitutions of the `@ref`'s. (See root folder for `jsonUtil.js`).

### Javascript
Included is a small Javascript utility (`jsonUtil.js` in the root folder) that will take a JSON output 
stream created by the JSON writer and substitute all `@ref's` for the actual pointed to object.  It's a one-line 
call - `resolveRefs(json)`.  This will substitute `@ref` tags in the JSON for the actual pointed-to object.  

### Additional uses for json-io
Even though **json-io** is perfect for Java / Javascript serialization, there are other great uses for it:

#### Cloning
Many projects use `JsonWriter` to write an object to JSON, then use the `JsonReader` to read it in, perfectly cloning the original object graph:

    Employee emp;
    // emp obtained from database
    Employee deepCopy = (Employee) cloneObject(emp);

    public Object cloneObject(Object root)
    {
        return JsonReader.toObjects(JsonWriter.toJson(root));
    }

#### Debugging
Instead of doing `System.out.println()` debugging, call `JsonWriter.objectToJson(obj)` and dump that String out.  It
will reveal the object in all it's detail.
