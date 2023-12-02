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

    WriteOptions writeOptions = new WriteOptions().closeStream(false);
    JsonIo.toJson(outputStream, record1, writeOptions);    
    JsonIo.toJson(outputStream, record2, writeOptions);
    ...
    JsonIo.toJson(outputStream, recordn, writeOptions);
    outputStream.close();


_Example 4: `InputStream` to Java object_

    Employee emp = JsonIo.toObjects(stream, readOptions, Employee.class);

In this example, an`InputStream`is supplying the JSON.

### Untyped Usage
**json-io** provides the choice to use the generic "Map of Maps" representation of an object, akin to a Javascript
associative array.  When reading from a JSON String or`InputStream`of JSON, use `JsonIo:`

    String json = // some JSON obtained from wherever
    ReadOptions readOptions = new ReadOptions().returnon();
    JsonValue value = JsonIo.toJsonValues(json, readOptions);    

See the `ReadOptions` below for the feature control options.  In the example above, rather than return the objects
converted into Java classes, you are being returned the raw`JsonValues`being parsed.  It is a graph that consists of
all`JsonValue`instances.  In addition to`JsonValue,`there is`JsonObject,`which represents `{...}`, `JsonArray`, which
represents `[...]`, and `JsonPrimitive`, which represents `long, double, String, boolean (true/false),`and`null.`
JsonObject, JsonArray  and JsonPrimitive are all subclasses of JsonValue.

With this approach, you can read JSON content and it will contain`JsonObject's`,`JsonArrays`,`JsonPrimitives`, across the
five (5) JSON primitive types (`String`, `long`, `double`, `boolean`, and `null`).  This content can be very easy to work with
in data science applications.

This JsonValue representation can be re-written to JSON String or Stream and the output JSON will exactly match the
original input JSON stream.  This permits you to receive JSON strings / streams that contain class references which
do not exist in the JVM that is parsing the JSON, to completely read the String/Stream, perhaps manipulate the content,
and then rewrite the String/stream.

---
## Controlling the output JSON using `WriteOptions`
Create a new`WriteOptions`instance and turn various features on/off using the methods below. Example:

   `WriteOptions`writeOptions = new WriteOptions().prettyPrint(true).writeLongsAsStrings(true);
    JsonWriter.toJson(root, writeOptions);

To pass these to`JsonWriter.toJson(root, writeOptions)`set up a`WriteOptions`using the feature settings below.
You can view the Javadoc on the`WriteOptions`class for detailed information. The`WriteOptions`can be made
read-only by calling the`.build()`method. You can have multiple`WriteOptions`instances for different scenarios, and safely
re-use them once built (read-only). A`WriteOptions`instance can be created from another`WriteOptions`instance
(use "copy constructor" discussed below).

Please note that if you write your object graph out without showing types, the shape of the graph will still be
maintained.  What this means, is that if two different places in your object graph point to the same object, the first
reference will write the actual object (with an id - `@id`), the 2nd and later references will write a reference (`@ref`)
to the first instance. This will read in just fine with `JsonReader.toObjects()`, and the appropriate `Map` reference
will be placed in all referenced locations.  If reading this in Javascript, make sure to use the included `jsonUtil.js`
to parse the read in JSON so that it can perform the substitutions of the `@ref`'s. (See root folder for `jsonUtil.js`).

---
### Constructors
Create new`WriteOptions`instances.
>#### WriteOptions()
>- [ ] Start with default options and in malleable state.
>#### WriteOptions(`WriteOptions other`)
>- [ ] Copy all the settings from the passed in 'other' `WriteOptions.`  The`WriteOptions`instance starts off in malleable state.

### Class Loader
The`ClassLoader`in the`WriteOptons`is used to turn`String`class names into`Class`instances.
>#### `ClassLoader`getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names when writing JSON.
>#### `WriteOptions`classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names when writing JSON. Returns`WriteOptions`for chained access.

### MetaKeys - @id, @ref, @type
A few additional fields are sometimes added to a JSON object {...} to give the`JsonReader`help in determining what
class to instantiate and load.  In addition, if a class is referenced by more than one field, array, collection element, or
Map key or value, then the initial occurrence of the instance will be output with an @id tag and a number _n_.  Subsequent references will
be written as @ref:_n_.  This will significantly shorten the JSON as the object will not be written multiple times, it also
allows for circular reference support, it saves memory when loading from JSON into Java objects, and finally retains
the original shape of the graph that was written (serialized) to JSON.

In a future release, we may be moving from using "@" to "."  Backward compatibility will be retained.
>#### `boolean`isShortMetaKeys()
>- [ ] Returns`true`if showing short meta-keys (@i instead of @id, @ instead of @ref, @t instead of @type, @k instead of @keys, @v instead of @values),`false`for full size. `false` is the default.
>#### `WriteOptions`shortMetaKeys(`boolean shortMetaKeys`)
>- [ ] Sets the boolean`true`to turn on short meta-keys,`false`for long. Returns`WriteOptions`for chained access.

### Aliasing - shorten class names in @type.
Aliasing is used to turn long java package names to simple class names, e.g.`java.util.ArrayList`becomes`ArrayList`
in the JSON.  By default, json-io has most of the common JDK classes aliased to make the JSON content smaller.  You can
add additional aliases for classes in your program.
>#### `String`getTypeNameAlias(`String typeName`)
>- [ ] Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
>#### `Map<String, String>`aliasTypeNames()
>- [ ] Returns Map<String, String> containing String class names to alias names. Use this API to see default aliases.
>#### `WriteOptions`aliasTypeNames(`Map<String, String> aliasTypeNames`)
>- [ ] Puts the`Map`containing String class names to alias names. The passed in`Map`will be `putAll()` copied overwriting any
entries that match values in the passed in Map. New entries in the Map are added.  Returns`WriteOptions`for chained access.
>#### `WriteOptions`aliasTypeName(`String typeName, String alias`)
>- [ ] Sets the alias for a given class name. Returns`WriteOptions`for chained access.
>#### `WriteOptions`aliasTypeName(`Class, String alias`)
>- [ ] Sets the alias for a given class. Returns`WriteOptions`for chained access.

### @Type
Used to provide hint to JsonReader to know what Classes to instantiate. Frequently, json-io can determine
what Java type (class) an object is because of a field of an object, the type of array, or if the root class
is specified.  Sometimes, though the type cannot be inferred.  An example would be field on a class that is declared
as an 'Object' type but more complex, derived instances are placed there.  In that case, json-io will output an
@type=_typename_ to indicate the type of the class that the reader should instantiate and load.

Note: The meta properties like @type will soon be output using ".type" although support will be maintained for "@type"
going forward.  Also note, you can specify "short" meta-keys, and alias type names to further shorten the written JSON.
>#### `boolean`isAlwaysShowingType()
>- [ ] Returns`true`if set to always show type (@type).
>#### `boolean`isNeverShowingType()
>- [ ] Returns`true`if set to never show type (no @type).
>#### `boolean`isMinimalShowingType()
>- [ ] Returns`true`if set to show minimal type (@type).  This is the default setting.
>#### `WriteOptions`showTypeInfoAlways()
>- [ ] Sets to always show type. Returns`WriteOptions`for chained access.
>#### `WriteOptions`showTypeInfoNever()
>- [ ] Sets to never show type. Returns`WriteOptions`for chained access.
>#### `WriteOptions`showTypeInfoMinimal()
>- [ ] Sets to show minimal type. This means that when the type of object can be inferred, a type field will not be output. Returns`WriteOptions`for chained access.

### Pretty Print
In order to have Multiple line, indented JSON output, or one-line output, turn on the pretty-print feature.
>#### `boolean`isPrettyPrint()
>- [ ] Returns the pretty-print setting,`true`being on, using lots of vertical white-space and indentations, `false` will output JSON in one line. The default is`false.`
>#### `WriteOptions`prettyPrint(`boolean prettyPrint`)
>- [ ] Sets the 'prettyPrint' setting,`true`to turn on,`false`will turn off. The default setting is`false.` Returns`WriteOptions`for chained access.

### Close Stream
Sometimes you want to close the stream automatically after output, other times you may want to leave it open to write
additional JSON to the stream.  For example, NDJSON is a format of {...}\n{...}\{...} To write this format, you can tell 
`JsonIo` not to close the stream after each write.  See example at the beginning of the user guide.
>#### `boolean`isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after write (the default), or `false`to leave stream open after writing to it.
>#### `WriteOptions`closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting,`true`to turn on,`false`will turn off. The default setting is`false.` Returns`WriteOptions`for chained access.

### `Long`as String
Output long values as a`String.`This is a fix for sending 17-19 digit long values to JavaScript.  Javascript stores
numbers internally as a`Double`(IEEE 754) which cannot retain all 17-19 digits.  By sending them as strings, the
values make it to Javascript and can still be displayed correctly.  The default is off.
>#### `boolean`isWriteLongsAsStrings()
>- [ ] Returns`true`indicating longs will be written as Strings,`false` to write them out as native JSON longs.
>#### `WriteOptions`writeLongsAsStrings(`boolean writeLongsAsStrings`)
>- [ ] Set to boolean`true`to turn on writing longs as Strings,`false`to write them as native JSON longs. The default setting
   is`false.`This feature is important to marshal JSON with large long values (18 to 19 digits) to Javascript. Long/long values
   are represented in Javascript by storing them in a `Double` internally, which cannot represent a full`long`value. Using
   this feature allows longs to be sent to Javascript with all their precision, however, they will be Strings when received
   in the Javascript. This will let you display them correctly, for example. Returns`WriteOptions`for chained access.

### `null`field values
You can turn on this setting (off by default) so that fields that have null values are not output to JSON.  For certain
applications, this can dramatically reduce the size of the JSON output.
>#### `boolean`isSkipNullFields()
>- [ ] Returns`true`indicating fields with null values will not be written,`false`will still output the field with an associated null value. The default is`false.`
>#### `WriteOptions`skipNullFields(`boolean skipNullFields`)
>- [ ] Sets the boolean where`true`indicates fields with null values will not be written to the JSON,`false`will allow the field to still be written. Returns`WriteOptions`for chained access.

### `Map`output
If this feature is turned on (off by default) then the raw parsed types are returned, not the Java object instances.  For
certain applications, the objects may not be that complex, nor even have references between objects.  Working with the
raw JsonValues (JSON Objects, JsonArrays, and primitives) may be all that is needed.  This will make parsing
JSON much faster. Keep in mind that you are working with Maps, Arrays, and primitives, not well typed DTOs.
>#### `boolean`isForceMapOutputAsTwoArrays()
>- [ ] Returns`true`if set to force Java Maps to be written out as two parallel arrays, once for keys, one array for values. The default is`false.`
>#### `WriteOptions`forceMapOutputAsTwoArrays(`boolean forceMapOutputAsTwoArrays`)
>- [ ] Sets the boolean 'forceMapOutputAsTwoArrays' setting. If Map's have String keys they are written as normal JSON objects. With this setting enabled, Maps are written as two parallel arrays. Returns`WriteOptions`for chained access.

### Floating Point Options
Although the JSON spec does not support Nan and Infinity, it can be convenient to allow it in the JSON, and have
the values be both written and read properly. This feature is off by default.  Although the JSON with Nan and Infinity may work for
your application, keep in mind, NaN, +Inf, -Inf are not necessarily going to be supported by other APIs and services.
> #### `boolean`isAllowNanAndInfinity
>- [ ] Returns`true`if set to allow serialization of`NaN`and`Infinity`for`doubles`and`floats.`
>#### `WriteOptions`allowNanAndInfinity(`boolean allow`)
>- [ ] true will allow`doubles`and`floats`to be output as`NaN`and`INFINITY`,`false`and these values will come across
   as`null.` Returns`WriteOptions`for chained access.

### Enum Options
Most developers use`Enums`as a discrete list of values. However, we have seen instances where additional fields
have been added to the`Enum`classes. Additionally, we have seen both public and private fields in these cases.
The Enum options allow you to skip private fields (retain public only), keep the Enum to only it's "name" value, or 
force the Enum to be written as an object, or a single value.
>#### `boolean`isWriteEnumAsString()
>- [ ] Returns`true`if enums are to be written out as Strings (not a full JSON object) when possible.
>#### `WriteOptions`writeEnumsAsString()
>- [ ] Sets the option to write out enums as a String. Returns`WriteOptions`for chained access.
>#### `boolean`isEnumPublicFieldsOnly()
>- [ ] Returns`true`indicating that only public fields will be output on an Enum. The default is to only output public fields as well as to write it as a primitive (single value) instead of a JSON { } object when possible.
>#### `WriteOptions`writeEnumAsJsonObject(`boolean writePublicFieldsOnly`)
>- [ ] Sets the option to write out all the member fields of an enum. Returns`WriteOptions`for chained access.

### Customized JSON Writers
Want to customize JSON output for a particular class?  For any Java class, you can author a custom writer
(`JsonClassWriter`) and associate it to the class.  Then your 'JsonClassWriter' class will be called on to write the 
class out, given you the capability to selectively choose fields, format it differently, etc.
>#### `WriteOptions`setCustomWrittenClasses(`Map<Class, JsonWriter.JsonClassWriter> customWrittenClasses`)
>- [ ] Establishes the passed in`Map`as the complete list of custom writers to be used when writing JSON. Returns`WriteOptions`for chained access.
>#### `WriteOptions`addCustomWrittenClass(`Class, JsonWriter.JsonClassWriter customWriter`)
>- [ ] Adds a custom writer for a specific Class. Returns`WriteOptions`for chained access.
>#### `Map<Class, JsonWriter.JsonClassWriter>` getCustomWrittenClasses()
>- [ ] Returns a`Map`of Class to custom JsonClassWriter's use to write JSON when the class is encountered during serialization.
>#### `boolean`isCustomWrittenClass(`Class`)
>- [ ] Checks to see if there is a custom writer associated with a given class. Returns`true`if there is,`false`otherwise.

### "Not" Customized Class Writers
Customized writers are associated to a particular class AND it's derivatives.  If you have a derivative class that
is having its output customized and you do not want that, you can add that class to the "Not" customized list. Being 
on the "Not" customized list takes priority over the customized list, letting the default json-io JSON writer do its job.
>#### `boolean`isNotCustomWrittenClass(`Class`)
>- [ ] Checks if a class is on the not-customized list. Returns`true`if it is,`false`otherwise.
>#### `Set<Class>` getNotCustomWrittenClasses()
>- [ ] Returns a`Set`of all Classes on the not-customized list.
>#### `WriteOptions`addNotCustomWrittenClass(`Class notCustomClass`)
>- [ ] Adds a class to the not-customized list. This class will use 'default' processing when written.  This option is available as custom writers apply to the class and their derivatives.  This allows you to shut off customization for a class that is picking it up due to inheritance. Returns`WriteOptions`for chained access.
>#### `WriteOptions`setNotCustomWrittenClasses(`Collection<Class> notCustomClasses`)
>- [ ] Initializes the list of classes on the non-customized list. Returns`WriteOptions`for chained access.

### Included Fields
This feature allows you to indicate which fields should be output for a particular class. If fields are specified here, only
these fields will be output for a particular class.  Think of it as a white-list approach to specifying the fields.
If a particular class has a large amount of fields and you only want to exclude a few, use 'Excluded' fields instead.
If a field is listed in both the includes and excludes list, the exclude list takes precedence.  That may change
in a future release, and an exception will be thrown if you attempt to add the same field name to both the include and exclude list.
>#### `Set<String>` getIncludedFields(`Class`)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be included in the written JSON.
>#### `Map<Class, Set<String>>` getIncludedFieldsPerAllClasses()
>- [ ] Returns a`Map`of all Classes and their associated Sets of fields to be included when serialized to JSON.
>#### `WriteOptions`addIncludedField(`Class, String includedField`)
>- [ ] Adds a single field to be included in the written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### `WriteOptions`addIncludedFields(`Class, Collection<String> includedFields`)
>- [ ] Adds a`Collection`of fields to be included in written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### `WriteOptions`addIncludedFields(`Map<Class, Collection<String>> includedFields`)
>- [ ] Adds multiple Classes and their associated fields to be included in the written JSON. Returns`WriteOptions`for chained access.

### Excluded Fields
This feature allows you to indicate which fields should be not be output for a particular class. If fields are 
specified here, then these named fields will not be output for a particular class.  Think of it as a black-list approach
to specifying the fields. If a particular class has a large amount of fields and you only want to exclude a few, this
is a great approach to trim off just those few fields (privacy considerations, ClassLoader fields, etc.)  You can also
add fields to an 'include' list, in which case only those fields will be output for the particular class.  If a 
field is listed in both the includes and excludes list, the exclude list takes precedence.  That may change
in a future release, and an exception will be thrown if you attempt to add the same field name to both the include and exclude list.
>#### `Set<String>` getExcludedFields(`Class`)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be excluded in the written JSON.
>#### `Map<Class, Set<String>>` getExcludedFieldsPerAllClasses()
>- [ ] Returns a`Map`of all Classes and their associated Sets of fields to be excluded when serialized to JSON.
>#### `WriteOptions`addExcludedField(`Class, String excludedField`)
>- [ ] Adds a single field to be excluded from the written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### `WriteOptions`addExcludedFields(`Class, Collection<String> excludedFields`)
>- [ ] Adds a`Collection`of fields to be excluded in written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### `WriteOptions`addExcludedFields(`Map<Class, Collection<String>> excludedFields`)
>- [ ] Adds multiple Classes and their associated fields to be excluded from the written JSON. Returns`WriteOptions`for chained access.

### java.util.Date and java.sql.Date format
This feature allows you to control the format for`java.util.Date and java.sql.Date`fields.  The default output format
for these fields is numeric`long`format, which is fast and small. This may work great for your application, but not all applications.
You can set the Date format via String format like long ISO date time format, e.g. "yyyy-MM-dd'T'HH:mm:ss". All the standard
JDK formatting options are available for use here.  There are convenience methods for short and long ISO date formats.
>#### `WriteOptions`isoDateFormat()
>- [ ] Changes the date-time format to the ISO date format: "yyyy-MM-dd". Returns`WriteOptions`for chained access.
>#### `WriteOptions`isoDateTimeFormat()
>- [ ] Changes the date-time format to the ISO date-time format: "yyyy-MM-dd'T'HH:mm:ss". Returns`WriteOptions`for chained access.
>#### `WriteOptions`longDateFormat()
>- [ ] Changes the`java.uti.Date`and`java.sql.Date`format output to a`long,`the number of seconds since Jan 1, 1970 at midnight. For speed, the default format is`long.`Returns`WriteOptions`for chained access.
>#### `boolean`isLongDateFormat()
>- [ ] Returns`true`if`java.util.Date`and`java.sql.Date` are being written in`long`(numeric) format.
>#### `WriteOptions`dateTimeFormat(`String format`)
>- [ ] Changes the date-time format to the passed in format. Returns`WriteOptions`for chained access.

### Non-Referenceable Classes (Opposite of Instance Folding)
For small immutable classes, no need to use @id/@ref with them, as they are effectively primitives.  All primitives,
primitive wrappers, BigInteger, BigDecimal, Atomic*, java.util.Date, String, Class, are automatically marked
as non-referenceable. You can add more immutable classes of your own.  The side-effect of this is that it could change
the "shape" of your graph, if you were counting on two different fields that point to the same String/BigDecimal/Integer
to have the same == instance.  Rarely a concern, however, if you have a lot of the same value, say Integer(0) and expect
all those to collapse to the same value, they won't Each one would be read in as its own instance.
>#### `boolean`isNonReferenceableClass(`Class`)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.
>#### `Collection<Class>` getNonReferenceableClasses()
>- [ ] Returns a`Collection`of all non-referenceable classes.
>#### `WriteOptions`addNonReferenceableClass(`Class`)
>- [ ] Adds a class to be considered "non-referenceable." 
---
## Controlling the input JSON using `ReadOptions`
Create a new`ReadOptions`instance and turn various features on/off using the methods below. Example:

    ReadOptions readOptions = new ReadOptions().
    JsonWriter.toJson(root, writeOptions);

To pass these to`JsonRead.toJava(root, readOptions)`set up a`ReadOptions`using the feature settings below.
You can view the Javadoc on the`ReadOptions`class for detailed information. The`ReadOptions`can be made
read-only by calling the`.build()`method. You can have multiple`ReadOptions`instances for different scenarios, and safely
re-use them once built (read-only). A`ReadOptions`instance can be created from another`ReadOptions`instance
(use "copy constructor" discussed below).

### Constructors
Create new instances of`ReadOptions.`
>#### ReadOptions()
>- [ ] Start with default options and in malleable state.
>#### ReadOptions(`ReadOptions other`)
>- [ ] Copy all settings from the passed in 'other'`ReadOptions.`  The`ReadOptions`instance starts off in malleable state.

### Class Loader
The`ClassLoader`in the`ReadOptons`is used to turn`String`class names into`Class`instances.
>#### `ClassLoader`getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names into Class instances.
>#### `WriteOptions`classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names when reading JSON. Returns`ReadOptions`for chained access.

### Unknown Types
When reading JSON, what do you want to happen when a class name does not exist in your JVM? Should the parsing fail?
This feature allows you to control what happens here.  You can tell it to fail on an unknown type (default behavior) and
a`JsonIoException`will be thrown. Turn this feature off, and the parser will create a`LinkedHashMap`and use that
to store the content from the JSON object being read.  If you want, you can set your own class to be created
instead of a`LinkedHashMap,`however, it should be noted that`Maps`are useful because any field encountered in the
JSON can be`put`into a`Map.`
>#### `boolean`isFailOnUnknownType()
>- [ ] Returns`true` if an 'unknownTypeClass' is set,`false`if it is not set
>#### `ReadOptions`failOnUnknownType(`boolean fail`)
>- [ ] Set to`true`to indicate that an exception should be thrown if an unknown class type is encountered,`false`otherwise.
   If`fail`is`false,`then the default class used will be`LinkedHashMap` for the particular JSON object encountered. You can
   set this to your own class using the`unknownTypeClass()`feature. Returns`ReadOptions`for chained access.
>####  `Class` getUnknownTypeClass()
>- [ ] Get the Class used to store content when the indicated class cannot be loaded by the JVM.  Defaults to`null.` When`null`, `LinkedHashMap` is used. It is usually best to use a `Map` derivative, as it will be able to have the various field names encountered to be 'set' into it.
>####  `ReadOptions`unknownTypeClass(`Class`)
>- [ ] Set the class to use (defaults to`LinkedHashMap.class`) when a JSON object is encountered and there is no
   corresponding Java class to instantiate to receive the values. Returns`ReadOptions`for chained access.

### MaxDepth - Security protection
Set the maximum object nesting level so that no`StackOverflowExceptions`happen.  Instead, you will get a`JsonIoException`letting
you know that the maximum object nesting level has been reached.
> #### `int` getMaxDepth()
>- [ ] Return the max nesting depth for JSON {...}
> #### `ReadOptions`maxDepth(`int depth`)
>- [ ] Set the max depth to allow for nested JSON {...}.  Set this to prevent security risk from`StackOverflow`attack
>- vectors. Returns`ReadOptions`for chained access.

### Floating Point Options
Although the JSON spec does not support Nan and Infinity, it can be convenient to allow it in the JSON, and have
the values be both written and read properly. This feature is off by default.  Although the JSON with Nan and Infinity may 
work for your application, keep in mind, NaN, +Inf, -Inf are not necessarily going to be supported by other APIs and services.
> #### `boolean`isAllowNanAndInfinity
>- [ ] Returns`true`if set to allow serialization of`NaN`and`Infinity`for`doubles`and`floats.`
>#### `ReadOptions`allowNanAndInfinity(`boolean allow`)
>- [ ] true will allow`doubles`and`floats`to be output as`NaN`and`INFINITY`,`false`and these values will come across
   as`null.` Returns`ReadOptions`for chained access.

### Close Stream
Sometimes you want to close the stream automatically after reading, other times you may want to leave it open to read
additional JSON from the stream.  For example, NDJSON is a format of {...}\n{...}\{...} To read this format, you can tell
`JsonIo` not to close the `InputStream` after each read.  See example at the beginning of the user guide.
>#### `boolean`isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after read (the default), or `false`to leave stream open after reading from it.
>#### `ReadOptions`closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting,`true`to turn on,`false`will turn off. The default setting is`false.` Returns`ReadOptions`for chained access.

### Aliasing - shorten class names in @type.
Aliasing is used to turn long java package names to simple class names, e.g. `java.util.ArrayList` becomes `ArrayList`
in the JSON.  By default, json-io has most of the common JDK classes aliased to make the JSON content smaller.  You can
add additional aliases for classes in your program.
>#### `String` getTypeNameAlias(`String typeName`)
>- [ ] Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
>#### `Map<String, String>` aliasTypeNames()
>- [ ] Returns `Map<String, String>` containing String class names to alias names. Use this API to see default aliases.
>#### `ReadOptions`aliasTypeNames(`Map<String, String> aliasTypeNames`)
>- [ ] Puts the`Map`containing String class names to alias names. The passed in`Map`will be `putAll()` copied overwriting any
entries that match values in the passed in Map. New entries in the Map are added.  Returns`ReadOptions`for chained access.
>#### `ReadOptions`aliasTypeName(`String typeName, String alias`)
>- [ ] Sets the alias for a given class name. Returns`ReadOptions`for chained access.
>#### `ReadOptions`aliasTypeName(`Class, String alias`)
>- [ ] Sets the alias for a given class. Returns`ReadOptions`for chained access.

### Class Coercion
Use this feature to turn classes like`java.util.Collections$UnmodifiableRandomAccessList`into an`ArrayList`when parsed and loaded 
into memory.  There are many derivative classes for collection singleton's, synchronized variations of collections, and unmodifiable
variations.  All the common JDK substitutions are already in the coercion list.  You can add more if needed.
>#### `boolean`isClassCoerced(`String className`)
>- [ ] Return`true`if the passed in Class name is being coerced to another type, `false`otherwise.
>#### `Class` getCoercedClass(`Class`) 
>- [ ] Fetch the coerced class for the passed in fully qualified class name.
>#### `ReadOptions`coerceClass(`String sourceClassName, Class destClass`)
>- [ ] Coerce a class from one type (named in the JSON) to another type.  For example, converting
`java.util.Collections$SingletonSet`to a`LinkedHashSet.class.`

### Missing Field Handler
When an attempt to set a value from the JSON onto a Java object is made, but the field from the JSON is not
available on the Java object, the missing field handler feature comes into play.  You can set the handler to be
notified of the missing field. It will be notified of all missing fields at the end of deserialization. 
See`JsonReader.MissingFieldHandler`for more information.
>#### `JsonReader.MissingFieldHandler`getMissingFieldHandler()
>- [ ]  Fetch the method that will be called when a field in the JSON is read in, yet there is no corresponding 
field on the destination object to receive the value.
>#### `ReadOptions`missingFieldHandler(`JsonReader.MissingFieldHandler missingFieldHandler`)
>- [ ] Pass the`missing field handler`to be called when a field in the JSON is read in, yet there is no corresponding field on the 
destination object to receive the value.

### Class Factory
For a class that is failing to instantiate or be read in correctly by the default processing of json-io, you can
associate a`ClassFactory`to a`Class` to remedy the situation. The`ClassFactory's`job is to create instances of the
associated class, and load the values from the JSON into the class from the JSON object being encountered at the 
time (it is passed to the`ClassFactory`). This is the preferred method for custom handling a class as opposed to using a`CustomReader.`
>#### `Map<Class, JsonReader.ClassFactory>`getClassFactories()
>- [ ] Fetch`Map`of all Class's associated to`JsonReader.ClassFactory's.`
>#### `JsonReader.ClassFactory`getClassFactory(`Class`)
>- [ ] Get the`ClassFactory`associated to the passed in class.
>#### `ReadOptions`setClassFactories(`Map<Class, ? extends JsonReader.ClassFactory> factories`)
>- [ ] Associate multiple ClassFactory instances to Classes that needs help being constructed and read in. The
`Map`of entries associate`Class`to`ClassFactory.` The`ClassFactory`class knows how to instantiate and load
the class associated to it.
>#### `ReadOptions`addClassFactory(`Class clazz, JsonReader.ClassFactory factory`)
>- [ ] Associate a`ClassFactory`to a`Class`that needs help being constructed and read in. If you use this method
more than once for the same class, the last`ClassFactory`associated to the class will overwrite any prior associations.
 
### Custom Readers
Custom readers can be added to load the JSON content associated to a particular class if the default loading is
not working.  Before using a`CustomReader,`we recommend using a `ClassFactory,`as that allows your code to not
only create the appropriate instance, but also to load it at the same time. `CustomReaders` are used for 
the associated class and any derived classes.  If that picks up a class you do not want to associate to a `CustomReader,`
then can add that class to the Not-Custom-Reader list.
>#### `Map<Class, JsonReader.JsonClassReader>`getCustomReaderClasses()
>- [ ] Fetch`Map`of`Class`to custom`JsonClassReader's` used to read JSON when the class is encountered during
 serialization to JSON.  This is the entire`Map`of Custom Readers.
>#### `boolean`isCustomReaderClass(`Class`)
>- [ ] Return`true`if there is a custom reader class associated to the passed in class, `false` otherwise.
>#### `ReadOptions`setCustomReaderClasses(`Map<? extends Class>, ? extends JsonReader.JsonClassReader> customReaderClasses`)
>- [ ]  Set all custom readers at once.  Pass in a`Map`of`Class`to`JsonReader.JsonClassReader.` Set the passed 
in`Map`as the established`Map`of custom readers to be used when reading JSON. Using this method  more than once, will
set the custom readers to only the values from the`Map`in the last call made.
>#### `ReadOptions`addCustomReaderClass(`Class, JsonReader.JsonClassReader customReader`)
> - [ ] Add a single association of a class to a custom reader.  If you add another associated reader to the same class, the last one added will overwrite the prior association.

### Not Custom Reader
Add a class to this list that you do not want being associated to a`CustomReader.` This list is only used when you
want to turn-off a`CustomReader`for a particular class that is inadvertently being associated to a `CustomReader` through inheritance.
>#### `boolean`isNotCustomReaderClass(`Class`)
>- [ ]  Pass in Class to check if it's on the not-customized list.  Classes are added to this list when a class is being 
picked up through inheritance, and you don't want it to have a custom reader associated to it.
>#### `Set<Class<?>>`getNotCustomReadClasses()
>- [ ] Fetch the`Set`of all Classes on the not-customized list. 
>#### `ReadOptions`addNotCustomReaderClass(`Class notCustomClass`) 
>- [ ] Add a class to the not-customized list - the list of classes that you do not want to be picked up by
a custom reader (that could happen through inheritance). 
>#### `ReadOptions`setNotCustomReaderClasses(`Collection<Class> notCustomClasses`) 
>- [ ] Initialize the list of classes on the non-customized list.  All prior associations will be dropped and this 
`Collection` will establish the new list.

### Non-Referenceable Classes (Opposite of Instance Folding)
For small immutable classes, no need to use @id/@ref with them, as they are effectively primitives.  All primitives,
primitive wrappers, BigInteger, BigDecimal, Atomic*, java.util.Date, String, Class, are automatically marked
as non-referenceable. You can add more immutable classes of your own.  The side-effect of this is that it could change
the "shape" of your graph, if you were counting on two different fields that point to the same String/BigDecimal/Integer
to have the same == instance.  Rarely a concern, however, if you have a lot of the same value, say Integer(0) and expect
all those to collapse to the same value, they won't Each one would be read in as its own instance.
>#### `boolean`isNonReferenceableClass(`Class`)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.
>#### `Collection<Class>` getNonReferenceableClasses()
>- [ ] Returns a`Collection`of all non-referenceable classes.
>#### `WriteOptions`addNonReferenceableClass(`Class`)
>- [ ] Adds a class to be considered "non-referenceable." Examples are the built-in primitives. 
---
## Javascript
Included is a small Javascript utility (`jsonUtil.js` in the root folder) that will take a JSON output
stream created by the JSON writer and substitute all`@ref's`for the actual pointed to object.  It's a one-line
call -`resolveRefs(json)`. This will substitute`@ref`tags in the JSON for the actual pointed-to (`@id`) object.

## Additional uses for json-io
Even though **json-io** is great for Java / Javascript serialization, here are some other uses for it:

#### Cloning
Many projects use `JsonWriter` to write an object to JSON, then use the `JsonReader` to read it in, cloning the original object graph:

    Employee emp;
    // emp obtained from somewhere...
    Employee deepCopy = (Employee) Json.Io.deepCopy(emp, null, null);   // ReadOptions, WriteOptions can be null

#### Debugging
Instead of `System.out.println()` debugging, call `JsonIo.toJson(obj, writeOptions)` and dump the JSON
string out. That will give you the full referenceable graph dump in JSON.  Use the pretty-print feature of `WriteOptions`
to make the JSON more human-readable.
