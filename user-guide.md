### Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.

_Example 1: String to Java object_

    String json = // some JSON content
    Object obj = JsonReader.jsonToJava(json);     // optional 2nd 'options' argument (see below)

This will convert the JSON String to a Java Object graph.

_Example 2: Java object to JSON String_

    Employee emp;
    // Emp fetched from database
    String json = JsonWriter.objectToJson(emp);     // optional 2nd 'options' argument (see below)

This example will convert the `Employee` instance to a JSON String.  If the `JsonReader` were used on this `String`, 
it would reconstitute a Java `Employee` instance.

_Example 3: `InputStream` to Java object_

    Employee emp = (Employee) JsonReader.jsonToJava(stream);  // optional 2nd 'options' argument (see below)

In this example, an `InputStream` (could be from a File, the Network, etc.) is supplying an unknown amount of JSON.
If you want, you can use the `JsonReader` to wrap the stream to parse it, and return the Java object graph it 
represents. See constructors that take a Stream argument.

_Example 4: Java Object to `OutputStream`_

    Employee emp;
    // emp obtained from database
    JsonWriter jw = new JsonWriter(outputStream);       // optional 2nd 'options' argument (see below)
    jw.write(emp);
    jw.close();

In this example, a Java object is written to an output stream in JSON format.

### Non-typed Usage
**json-io** provides the choice to use the generic "Map of Maps" representation of an object, akin to a Javascript associative array.  When reading from a JSON String or `InputStream` of JSON, the `JsonReader` can be constructed like this:

    // shown using Groovy short-hand for Map of options.  See options below.
    String json = // some JSON obtained from wherever
    Object obj = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true])    

This will return an untyped object representation of the JSON String as a `Map` of Maps, where the fields are the
`Map` keys (Strings), and the field values are the associated Map's values. In this representation the returned data consists
of Maps, Arrays (Object[]), and JSON values.  The Maps are actually a `JsonObject` instance (from **json-io**).  This 
`JsonObject` implements the `Map` interface permitting access to the entire object.  Cast to a `JsonObject`, you can see 
the type information, position within the JSON stream, and other information.  

This 'Maps' representation can be re-written to a JSON String or Stream and _the output JSON will exactly match the
original input JSON stream_.  This permits a JVM receiving JSON strings / streams that contain class references which 
do not exist in the JVM that is parsing the JSON, to completely read / write the stream.  Additionally, the Maps can 
be modified before being written, and the entire graph can be re-written in one collective write.  _Any object model 
can be read, modified, and then re-written by a JVM that does not contain any of the classes in the JSON data._

#### The optional values below are public constants from `JsonWriter`, used by placing them as keys in the arguments map.

    CUSTOM_WRITER_MAP       // Set to Map<Class, JsonWriter.JsonClassWriter> to
                            // override the default JSON output for a given class. 
    NOT_CUSTOM_WRITER_MAP   // Set to Collection<Class> to indicate classes that should
                            // not be written by a custom writer.
    DATE_FORMAT             // Set this format string to control the format dates are 
                            // written. Example: "yyyy/MM/dd HH:mm".  Can also be a 
                            // DateFormat instance.  Can also be the constant 
                            // JsonWriter.ISO_DATE_FORMAT or 
                            // JsonWriter.ISO_DATE_TIME_FORMAT 
    TYPE                    // Set to boolean true to force all data types to be 
                            // output, even where they could have been omitted. Set
                            // to false to prevent @type from being written. Do not set
                            // in order to minimize the number of @type's emitted.
    PRETTY_PRINT            // Force nicely formatted JSON output 
                            // (See http://jsoneditoronline.org for example format)
    FIELD_SPECIFIERS        // Set to a Map<Class, List<String>> which is used to 
                            // control which fields of a class are output.
    FIELD_NAME_BLACK_LIST   // Set value to a Map<Class, List<String>> which will be used
                            // to control which fields on a class are not output. Black 
                            // list always has priority to FIELD_SPECIFIERS                         
    ENUM_PUBLIC_ONLY        // If set, indicates that private variables of ENUMs are not 
                            // serialized.
    WRITE_LONGS_AS_STRINGS  // If set, longs are written in quotes (Javascript safe).
                            // JsonReader will automatically convert Strings back
                            // to longs.  Any Number can be set from a String.
    TYPE_NAME_MAP           // If set, this map will be used when writing @type values.
                            // Allows short-hand abbreviations for type names.
    SHORT_META_KEYS         // If set, then @type => @t, @keys => @k, @items => @e,
                            // @ref => @r, and @id => @i
    SKIP_NULL_FIELDS        // Do not write field values to output JSON when
                            // their value is null. If you have a constructor that takes
                            // primitive wrapper arguments (ie., Integer), json-io will
                            // supply their 'zero' value (0) when looking for constructors
                            // to choose.                             
    CLASSLOADER             // ClassLoader instance to use when turning String names of     
                            // classes into JVM Class instances.
    FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS  // Force Map output to use @keys/@items even if 
                            // the Map contains all Strings as keys.

#### The optional values below are public constants from `JsonReader`, used by placing them as keys in the arguments map.

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
    FAIL_ON_UNKNOWN_TYPE    // Set to Boolean.TRUE to have JSON reader throw JsonIoException
                            // when a @type value references a class that is not loadable
                            // from the class loader. Set to any other value (or leave out)
                            // and JSON parsing will return a Map to represent the unknown
                            // object defined by the invalid @type value.
    CLASSLOADER             // ClassLoader instance to use when turning String names of     
                            // classes into JVM Class instances.
      
### Customization

#### Customization technique 1: Drop unwanted fields
* **White-List support**: Let's say a class that you want to serialize has a field on it that you do not want written out, like a `ClassLoader` reference.
Use the `JsonWriter.FIELD_SPECIFIERS` to associate a `List` of `String` field names to a particular `Class` C.  When the class
is being written, only the fields you list will be written.

* **Black-List support**: Let's say a class that you want to serialize has a field on it that you do not want written out, like a `ClassLoader` reference.
Use the `JsonWriter.FIELD_NAME_BLACK_LIST` to associate a `List` of `String` field names to a particular `Class` C.  When the class
is being written, any field listed here will not be written.  Black-listed fields take priority over white listed
fields.

#### Customization technique 2: Custom instantiator  `JsonReader.assignInstantiator(Class c, ClassFactoryEx)`
There are times when **json-io** cannot instantiate a particular class even though it makes many attempts to instantiate 
a class, including looping through all the constructors (public, private) and invoking them with default values, etc.  
However, sometimes a class just cannot be constructed, for example, one that has a constructor that throws an exception 
if particular parameters are not passed into it.
                                                                                  
In these instances, use the `JsonReader.assignInstantiator(class, Factory)` to assign a `ClassFactory` or `ClassFactoryEx` 
that you implement to instantiate the class. **json-io** will call your `ClassFactory.newInstance(Class c)` 
(or `ClassFactoryEx.newInstance(Class c, Map args)`) to create the class that it could not construct.  Your `ClassFactory` 
will be called to create the instance.  In case you need values from the object being instantiated in order to construct it,
use the `ClassFactoryEx` to instantiate it.  This class factory has the API `newInstance(Class c, Map args)` which will
be called with the Class to instantiate and the JSON object that represents it (already read in).  In the args `Map`, 
the key 'jsonObj' will have the associated `JsonObject` (`Map`) that is currently being read.  You can pull field values
from this object to create and return the instance.  After your code creates the instance, **json-io** will reflectively
stuff the values from the `jsonObj` (`JsonObject`) into the instance you create.
 
#### Customization technique 3: Shorter meta-keys (@type -> @t, @id -> @i, @ref -> @r, @keys -> @k, @items -> @e)  
Set `JsonWriter.SHORT_META_KEYS` to `true` to see the single-letter meta keys used in the outputted JSON.  In addition
to the shorter meta keys, you can and a list of substitutions of your own to use.  For example, you may want to see 
`alist` instead of `java.util.ArrayList`.  This is only applicable if you are writing with @types in the JSON.

  
      Map args = [
              (JsonWriter.SHORT_META_KEYS):true,
              (JsonWriter.TYPE_NAME_MAP):[
                  'java.util.ArrayList':'alist', 
                  'java.util.LinkedHashMap':'lmap', 
                  (TestObject.class.getName()):'testO'
              ]
      ]
      String json = JsonWriter.objectToJson(list, args)
          
In this example, we create an 'args' `Map`, set the key `JsonWriter.SHORT_META_KEYS` to `true` and set the
`JsonWriter.TYPE_NAME_MAP` to a `Map` that will be used to substitute class names for short-hand names.
         
#### Customization technique 4: Custom serializer
New APIs have been added to allow you to associate a custom reader / writer class to a particular class if you want it 
to be read / written specially in the JSON output.  The **json-io** approach allows you to customize the JSON format for 
classes for which you do not have the source code.

    Example (in Groovy). Note the Person has a List of pets, and in this case, it re-uses 
    JsonWriter to write that part of the class out (no need to customize it):
    
    static class CustomPersonWriter implements JsonWriter.JsonClassWriter
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
 
#### Customization technique 5: Processing JSON from external sources.
When reading JSON from external sources, you may want to start with:

 in Groovy:
 
    Object data = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true])
    
In Java:

    Map args = new HashMap();
    args.put(JsonReader.USE_MAPS, true);
    Object data = JsonReader.jsonToJava(json, args);


This will get the JSON read into memory, in a Map-of-Maps format, similar to how JSON is read into memory in Javascript. 
This will get you going right away.
  
To write 'generic' JSON (without `@type` or `@items`, etc.) entries, use:

in Groovy:

    String json = JsonWriter.objectToJson(objToWrite, [(JsonWriter.TYPE):false])

In Java:

    Map args = new HashMap();
    args.put(JsonWriter.TYPE, false);
    String json = JsonWriter.objectToJson(objToWrite, args);
    
Objects will not include the `@type` flags or `@items`.  This JSON passes nicely to non-Java receivers, like Javascript. 
Keep in mind, you will be working with the JSON as generic `object.field` and `object[index]` with this approach.  

Please note that if you write your object graph out with `JsonWriter.TYPE: false`, the shape of the graph will be 
maintained.  What this means, is that if two different places in your object graph point to the same object, the first 
reference will write the actual object, the 2nd and later references will write a reference (`@ref`) to the first instance.
This will read in just fine with `JsonReader.jsonToJava()`, and the appropriate `Map` reference will be placed in all 
referenced locations.  If reading this in Javascript, make sure to use the included `jsonUtil.js` to parse the read in JSON
so that it can perform the substitutions of the `@ref`'s. (See `src/test/resource` folder for `jsonUtil.js`).

### Javascript
Included is a small Javascript utility (`jsonUtil.js` in the `src/test/resources` folder) that will take a JSON output 
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
        return JsonReader.jsonToJava(JsonWriter.objectToJson(root));
    }

#### Debugging
Instead of doing `System.out.println()` debugging, call `JsonWriter.objectToJson(obj)` and dump that String out.  It
will reveal the object in all it's glory.
