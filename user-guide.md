## Usage
**json-io** can be used directly on JSON Strings or with Java's Streams.

### Typed Usage

_Example 1: Java object to JSON String_

    Employee emp;
    // Emp fetched from database
    String json = JsonWriter.toJson(emp, writeOptions);     // 'writeOptions' argument discussed in detail below

This example will convert the `Employee` instance to a JSON String.  If the `JsonReader` were used on this `String`,
it would reconstitute a Java `Employee` instance.

_Example 2: String to Java object_

    String json = // String JSON representing Employee instance
    Employee employee = JsonReader.toObjects(json, readOptions, Employee.class);  // 'readOptions' argument discussed below

This will convert the JSON String to a Java Object graph.

_Example 3: Java Object to `OutputStream`_

    Employee emp;
    // emp obtained from data store...
    JsonWriter.toJson(outputStream, emp, writeOptions);       

In this example, a Java object is written to an `OutputStream` in JSON format.  The stream is closed when finished.  If
you need to keep the `OutputStream` open (e.g. NDJSON), then use the `JsonWriter()` constructor that takes an `OutputStream`
and a `WriteOptions` instance.  Example:
     
    JsonWriter writer = new JsonWriter(outputStream, writeOptions); 
    jsonWriter.write(root);  
    jsonWriter.close();

_Example 4: `InputStream` to Java object_

    Employee emp = JsonReader.toObjects(stream, readOptions, Employee.class);

In this example, an`InputStream`is supplying the JSON.

### Untyped Usage
**json-io** provides the choice to use the generic "Map of Maps" representation of an object, akin to a Javascript 
associative array.  When reading from a JSON String or`InputStream`of JSON, the`JsonReader`can be constructed like this:

    String json = // some JSON obtained from wherever
    ReadOptions readOptions = new ReadOptions().returnAsJson();
    JsonValue value = JsonReader.toJava(json, readOptions);    

See the `ReadOptions` below for al the feature control options.  In the example above, rather than return the objects
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
### Controlling the output JSON using `WriteOptions`
Create a new`WriteOptions`instance and turn various features on/off using the methods below. Example:

    WriteOptions writeOptions = new WriteOptions().prettyPrint(true).writeLongsAsStrings(true);
    JsonWriter.toJson(root, writeOptions);

To pass these to`JsonWriter.toJson(root, writeOptions)`set up a`WriteOptions`using the feature settings below.
You can view the Javadoc on the`WriteOptions`class for detailed information. The`WriteOptions`can be made
read-only by calling the`.build()`method. You can have multiple`WriteOptions`instances for different scenarios, and safely
re-use them once built (read-only). A`WriteOptions`instance can be created from another`WriteOptions`instance
(use "copy constructor" discussed below).

---
### Constructors
>#### WriteOptions()
>- [ ] Start with default options and in malleable state. 
>#### WriteOptions(WriteOptions other)
>- [ ] Copy all the settings from the passed in 'other' `WriteOptions.`  The`WriteOptions`instance starts off in malleable state.

### Class Loader
>#### ClassLoader getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names when writing JSON.
>#### WriteOptions classLoader(ClassLoader loader)
>- [ ] Sets the ClassLoader to resolve String class names when writing JSON. Returns`WriteOptions`for chained access.

### MetaKeys - @id, @ref, @type
>#### boolean isShortMetaKeys()
>- [ ] Returns`true`if showing short meta-keys (@i instead of @id, @ instead of @ref, @t instead of @type, @k instead of @keys, @v instead of @values),`false`for full size. `false` is the default.
>#### WriteOptions shortMetaKeys(boolean shortMetaKeys)
>- [ ] Sets the boolean`true`to turn on short meta-keys,`false`for long. Returns`WriteOptions`for chained access.

### Aliasing - shorten class names in @type.
>#### String getTypeNameAlias(String typeName)
>- [ ] Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
>#### Map<String, String> aliasTypeNames()
>- [ ] Returns Map<String, String> containing String class names to alias names. Use this API to see default aliases.
>#### WriteOptions aliasTypeNames(Map<String, String> aliasTypeNames)
>- [ ] Sets the`Map`containing String class names to alias names. The passed in`Map`will be copied, and be the new baseline settings. Returns`WriteOptions`for chained access.
>#### WriteOptions aliasTypeName(String typeName, String alias)
>- [ ] Sets the alias for a given class name. Returns`WriteOptions`for chained access.

### @Type - used to provide hint to JsonReader to know what Classes to instantiate.
>#### boolean isAlwaysShowingType()
>- [ ] Returns`true`if set to always show type (@type).
>#### boolean isNeverShowingType()
>- [ ] Returns`true`if set to never show type (no @type).
>#### boolean isMinimalShowingType()
>- [ ] Returns`true`if set to show minimal type (@type).  This is the default setting.
>#### WriteOptions showTypeInfoAlways()
>- [ ] Sets to always show type. Returns`WriteOptions`for chained access.
>#### WriteOptions showTypeInfoNever()
>- [ ] Sets to never show type. Returns`WriteOptions`for chained access. 
>#### WriteOptions showTypeInfoMinimal()
>- [ ] Sets to show minimal type. This means that when the type of object can be inferred, a type field will not be output. Returns`WriteOptions`for chained access.

### Pretty Print - Multiple line, indented JSON output, or one-line output
>#### boolean isPrettyPrint()
>- [ ] Returns the pretty-print setting,`true`being on, using lots of vertical white-space and indentations, `false` will output JSON in one line. The default is`false.`
>#### WriteOptions prettyPrint(boolean prettyPrint)
>- [ ] Sets the 'prettyPrint' setting,`true`to turn on,`false`will turn off. The default setting is`false.` Returns`WriteOptions`for chained access.

### Long as String (Fix JavaScript issue with large long values)
>#### boolean isWriteLongsAsStrings()
>- [ ] Returns`true`indicating longs will be written as Strings,`false` to write them out as native JSON longs.
>#### WriteOptions writeLongsAsStrings(boolean writeLongsAsStrings)
>- [ ] Set to boolean`true`to turn on writing longs as Strings,`false`to write them as native JSON longs. The default setting 
is`false.`This feature is important to marshal JSON with large long values (18 to 19 digits) to Javascript. Long/long values 
are represented in Javascript by storing them in a `Double` internally, which cannot represent a full`long`value. Using 
this feature allows longs to be sent to Javascript with all their precision, however, they will be Strings when received
in the Javascript. This will let you display them correctly, for example. Returns`WriteOptions`for chained access.

### null field values
>#### boolean isSkipNullFields()
>- [ ] Returns`true`indicating fields with null values will not be written,`false`will still output the field with an associated null value. The default is`false.`
>#### WriteOptions skipNullFields(boolean skipNullFields)
>- [ ] Sets the boolean where`true`indicates fields with null values will not be written to the JSON,`false`will allow the field to still be written. Returns`WriteOptions`for chained access.

### `Map`output
>#### boolean isForceMapOutputAsTwoArrays()
>- [ ] Returns`true`if set to force Java Maps to be written out as two parallel arrays, once for keys, one array for values. The default is`false.`
>#### WriteOptions forceMapOutputAsTwoArrays(boolean forceMapOutputAsTwoArrays)
>- [ ] Sets the boolean 'forceMapOutputAsTwoArrays' setting. If Map's have String keys they are written as normal JSON objects. With this setting enabled, Maps are written as two parallel arrays. Returns`WriteOptions`for chained access.

### Floating Point Options

> #### boolean isAllowNanAndInfinity
>- [ ] Returns `true` if set to allow serialization of `NaN` and `Infinity` for doubles and floats.
>#### WriteOptions allowNanAndInfinity(boolean allow)
>- [ ] true will allow Double and Floats to be output as NaN and INFINITY, false and these values will come across as null.
### Enum Options
>#### boolean isWriteEnumAsString()
>- [ ] Returns`true`if enums are to be written out as Strings (not a full JSON object) when possible.
>#### WriteOptions writeEnumsAsString()
>- [ ] Sets the option to write out enums as a String. Returns`WriteOptions`for chained access.
>#### boolean isEnumPublicFieldsOnly()
>- [ ] Returns`true`indicating that only public fields will be output on an Enum. The default is to only output public fields as well as to write it as a primitive (single value) instead of a JSON { } object when possible.
>#### WriteOptions writeEnumAsJsonObject(boolean writePublicFieldsOnly)
>- [ ] Sets the option to write out all the member fields of an enum. Returns`WriteOptions`for chained access.

### Customized JSON Writers
>#### WriteOptions setCustomWrittenClasses(Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses)
>- [ ] Establishes the passed in`Map`as the complete list of custom writers to be used when writing JSON. Returns`WriteOptions`for chained access.
>#### WriteOptions addCustomWrittenClass(Class<?> clazz, JsonWriter.JsonClassWriter customWriter)
>- [ ] Adds a custom writer for a specific Class. Returns`WriteOptions`for chained access.
>#### Map<Class<?>, JsonWriter.JsonClassWriter> getCustomWrittenClasses()
>- [ ] Returns a`Map`of Class to custom JsonClassWriter's use to write JSON when the class is encountered during serialization.
>#### boolean isCustomWrittenClass(Class<?> clazz)
>- [ ] Checks to see if there is a custom writer associated with a given class. Returns`true`if there is,`false`otherwise.

### "Not" Customized Class Writers
>#### boolean isNotCustomWrittenClass(Class<?> clazz)
>- [ ] Checks if a class is on the not-customized list. Returns`true`if it is,`false`otherwise.
>#### Set<Class<?>> getNotCustomWrittenClasses()
>- [ ] Returns a`Set`of all Classes on the not-customized list.
>#### WriteOptions addNotCustomWrittenClass(Class<?> notCustomClass)
>- [ ] Adds a class to the not-customized list. This class will use 'default' processing when written.  This option is available as custom writers apply to the class and their derivatives.  This allows you to shut off customization for a class that is picking it up due to inheritance. Returns`WriteOptions`for chained access.
>#### WriteOptions setNotCustomWrittenClasses(Collection<Class<?>> notCustomClasses)
>- [ ] Initializes the list of classes on the non-customized list. Returns`WriteOptions`for chained access.

### Included Fields
>#### Set<String> getIncludedFields(Class<?> clazz)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be included in the written JSON.
>#### Map<Class<?>, Set<String>> getIncludedFieldsPerAllClasses()
>- [ ] Returns a`Map`of all Classes and their associated Sets of fields to be included when serialized to JSON.
>#### WriteOptions addIncludedField(Class<?> clazz, String includedField)
>- [ ] Adds a single field to be included in the written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### WriteOptions addIncludedFields(Class<?> clazz, Collection<String> includedFields)
>- [ ] Adds a`Collection`of fields to be included in written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### WriteOptions addIncludedFields(Map<Class<?>, Collection<String>> includedFields)
>- [ ] Adds multiple Classes and their associated fields to be included in the written JSON. Returns`WriteOptions`for chained access.

### Excluded Fields
>#### Set<String> getExcludedFields(Class<?> clazz)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be excluded in the written JSON.
>#### Map<Class<?>, Set<String>> getExcludedFieldsPerAllClasses()
>- [ ] Returns a`Map`of all Classes and their associated Sets of fields to be excluded when serialized to JSON.
>#### WriteOptions addExcludedField(Class<?> clazz, String excludedField)
>- [ ] Adds a single field to be excluded from the written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### WriteOptions addExcludedFields(Class<?> clazz, Collection<String> excludedFields)
>- [ ] Adds a`Collection`of fields to be excluded in written JSON for a specific class. Returns`WriteOptions`for chained access.
>#### WriteOptions addExcludedFields(Map<Class<?>, Collection<String>> excludedFields)
>- [ ] Adds multiple Classes and their associated fields to be excluded from the written JSON. Returns`WriteOptions`for chained access.

### java.util.Date and java.sql.Date format
>#### WriteOptions isoDateFormat()
>- [ ] Changes the date-time format to the ISO date format: "yyyy-MM-dd". Returns`WriteOptions`for chained access.
>#### WriteOptions isoDateTimeFormat()
>- [ ] Changes the date-time format to the ISO date-time format: "yyyy-MM-dd'T'HH:mm:ss". Returns`WriteOptions`for chained access.
>#### WriteOptions longDateFormat()
>- [ ] Changes the`java.uti.Date`and`java.sql.Date`format output to a`long,`the number of seconds since Jan 1, 1970 at midnight. For speed, the default format is`long.`Returns`WriteOptions`for chained access.
>#### boolean isLongDateFormat()
>- [ ] Returns`true`if`java.util.Date`and`java.sql.Date` are being written in`long`(numeric) format.
>#### WriteOptions dateTimeFormat(String format)
>- [ ] Changes the date-time format to the passed in format. Returns`WriteOptions`for chained access.

### Non-Referenceable Classes (Opposite of Instance Folding)
>#### boolean isNonReferenceableClass(Class<?> clazz)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.
>#### Collection<Class<?>> getNonReferenceableClasses()
>- [ ] Returns a`Collection`of all non-referenceable classes.
>#### WriteOptions addNonReferenceableClass(Class<?> clazz)
>- [ ] Adds a class to be considered "non-referenceable." Examples are the built-in primitives.  Making a class non-referenceable means that it will never 
have an @id tag, nor @ref tag in the output JSON. When loaded, these classes will always have an instance created for them. Typically used
for small classes like `Date, LocalDate, LocalDateTime,` where you are not pointing many fields to the same instance. Using 
this option for a class will cause more memory to be consumed on the reading side, as each class of this type 
output will always create a new instance.  If you have a class with many fields (large instance) and many other fields outside this
class pointed to it, and that large instance is marked as "non-referenceable," then the large instance would be loaded 
into memory uniquely for each object that pointed to it.  This could change the "shape" of your object graph for each non-referenceable instance. 
---
### Controlling the input JSON using `ReadOptions`
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
 
#### Customization technique 4: Custom serializer
New APIs have been added to allow you to associate a custom reader / writer class to a particular class if you want it 
to be read / written specially in the JSON output.  The **json-io** approach allows you to customize the JSON format for 
classes for which you do not have the source code.
 
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
