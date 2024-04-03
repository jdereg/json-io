## Controlling the output JSON using `WriteOptions`
Create a new`WriteOptions`instance and turn various features on/off using the methods below. Example:

    WriteOptions writeOptions = new WriteOptionsBuilder().prettyPrint(true).writeLongsAsStrings(true).build();
    JsonIo.toJson(root, writeOptions);

To pass these to `JsonIo.toJson(root, writeOptions)` set up a `WriteOptions` using the `WriteOptionsBuilder.`
You can view the Javadoc on the `WriteOptionsBuilder` class for detailed information. The `WriteOptions` are created
and made read-only by calling the `.build()` method on the `WriteOptionsBuilder.` You can have multiple `WriteOptions`
instances for different scenarios, and safely re-use them once built (read-only). A `WriteOptions` instance can be
created from another `WriteOptions` instance by using `new WriteOptionsBuilder(writeOptionsToCopyFrom).`

---
### Constructors
Create new`WriteOptions`instances.
>#### new WriteOptionsBuilder().feature1().feature2(args).build()
>- [ ] Start with default options and turn on feature1 and feature2 (which requires an argument)
>#### new WriteOptionsBuilder(`WriteOptions other`)
>- [ ] Copy all the settings from the passed in 'other' `WriteOptions.`
                            
For all the descriptions below, the top APIs listed are the "getter" (read) APIs to retrieve the setting from
the `WriteOptions` and the APIs below it are "setter" APIs on the `WriteOptionsBuilder` to activate the option.

All the `WriteOptionsBuilder`"setter" APIs return the `WriteOptionsBuilder`to permit chained access.

### ClassLoader
The`ClassLoader`in the`WriteOptonsBuilder`is used to turn`String`class names into`Class`instances.
>#### `ClassLoader`getClassLoader()

>- [ ] Returns the ClassLoader to resolve String class names when writing JSON.
>#### `WriteOptionsBuilder` classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names when writing JSON.

### MetaKeys - @id, @ref, @type
A few additional fields are sometimes added to a JSON object {...} to give the`JsonReader`help in determining what
class to instantiate and load.  In addition, if a class is referenced by more than one field, array, collection element, or
Map key or value, then the initial occurrence of the instance will be output with an @id tag and a number _n_.  Subsequent references will
be written as @ref:_n_.  This will significantly shorten the JSON as the object will not be written multiple times, it also
allows for circular reference support, it saves memory when loading from JSON into Java objects, and finally retains
the original shape of the graph that was written (serialized) to JSON.

In a future release, we may be moving from using "@" to "."  Backward compatibility will be retained.
>#### `boolean`isShortMetaKeys()
>- [ ] Returns`true`if showing short meta-keys (@i instead of @id, @r instead of @ref, @t instead of @type, @k instead of @keys, @v instead of @values),`false`for full size. `false` is the default.
 
>#### `WriteOptionsBuilder` shortMetaKeys(`boolean shortMetaKeys`)
>- [ ] Sets the boolean`true`to turn on short meta-keys,`false`for long.

### Aliasing - shorten class names in @type.
Aliasing is used to turn long java package names to simple class names, e.g.`java.util.ArrayList`becomes`ArrayList`
in the JSON.  By default, json-io has most of the common JDK classes aliased to make the JSON content smaller.  You can
add additional aliases for classes in your program.
>#### `String`getTypeNameAlias(`String typeName`)
>- [ ] Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
>#### `Map<String, String>`aliases()      
>- [ ] Returns `Map<String, String>` containing all String class names to alias names.

>#### `WriteOptionsBuilder` aliasTypeNames(`Map<String, String> aliasTypeNames`)
>- [ ] Puts the`Map`containing String class names to alias names. The passed in`Map`will be `putAll()` copied overwriting any
entries that match values in the passed in Map. New entries in the Map are added.
>#### `WriteOptionsBuilder` aliasTypeName(`String typeName, String alias`)
>- [ ] Sets the alias for a given class name.
>#### `WriteOptionsBuilder` aliasTypeName(`Class, String alias`)
>- [ ] Sets the alias for a given class.
>#### `WriteOptionsBuilder` withExtendedAliases()
>- [ ] All the aliases listed in the resources folder `config/extendedAliases.txt` will be used when writing to shorten the length of the output JSON.  This is a text properties file that includes fully qualified class names mapped to shorter alias names.  To add more aliases, copy that file to your config/resources folder and add more alias to it.  Make sure your file is in the classpath ahead of the one that ships with json-io.jar.  

### @type
Used to provide hint to JsonReader to know what Classes to instantiate. Frequently, json-io can determine
what Java type (class) an object is because of the field-type on an object, the component-type of an array, or if the root class
is specified.  Sometimes, though the type cannot be inferred.  An example would be field on a class that is declared
as an `Object` type but more complex, derived instances are assigned to the `Object` field.  In that case, json-io will output an
additional entry in the JSON object, @type=_typename_ to indicate the type of the class that the reader should instantiate and load.

>#### `boolean`isAlwaysShowingType()
>- [ ] Returns`true`if set to always show type (@type).
>#### `boolean`isNeverShowingType()
>- [ ] Returns`true`if set to never show type (no @type).
>#### `boolean`isMinimalShowingType()
>- [ ] Returns`true`if set to show minimal type (@type).  This is the default.

>#### `WriteOptionsBuilder` showTypeInfoAlways()
>- [ ] Sets to always show type.
>#### `WriteOptionsBuilder` showTypeInfoNever()
>- [ ] Sets to never show type.
>#### `WriteOptionsBuilder` showTypeInfoMinimal()
>- [ ] Sets to show minimal type. This means that when the type of object can be inferred, a type field will not be output.  This is the default.

### Pretty Print
In order to have Multiple line, indented JSON output, or one-line output, turn on the pretty-print feature.
>#### `boolean`isPrettyPrint()
>- [ ] Returns the pretty-print setting,`true`being on, using lots of vertical white-space and indentations, `false` will output JSON in one line. The default is`false.`

>#### `WriteOptionsBuilder` prettyPrint(`boolean prettyPrint`)
>- [ ] Sets the 'prettyPrint' setting,`true`to turn on,`false`will turn off. The default setting is`false.`

### Automatically Close OutputStream (or Not)
Sometimes you want to close the stream automatically after output, other times you may want to leave it open to write
additional JSON to the stream.  For example, NDJSON is a format of {...}\n{...}\n{...} To write this format, you can tell 
`JsonIo` not to close the stream after each write.  See example at the beginning of the user guide.
>#### `boolean`isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after write (the default), or `false`to leave stream open after writing to it.

>#### `WriteOptionsBuilder` closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting,`true`to turn on,`false`will turn off. The default setting is`true.`

### `Long`as String
Output long values as a`String.`This is a fix for sending 17-19 digit long values to JavaScript.  Javascript stores
numbers internally as a`Double`(IEEE 754) which cannot retain all 17-19 digits.  By sending them as strings, the
values make it to Javascript and can still be displayed correctly.  The default is off.
>#### `boolean`isWriteLongsAsStrings()
>- [ ] Returns`true`indicating longs will be written as Strings,`false` to write them out as native JSON numbers.

>#### `WriteOptionsBuilder` writeLongsAsStrings(`boolean writeLongsAsStrings`)
>- [ ] Set to boolean`true`to turn on writing longs as Strings,`false`to write them as native JSON longs. The default setting
   is`false.`This feature is important to marshal JSON with large long values (18 to 19 digits) to Javascript. Long/long values
   are represented in Javascript by storing them in a `Double` internally, which cannot represent a full`long`value. Using
   this feature allows longs to be sent to Javascript with all their precision, however, they will be Strings when received
   in the Javascript. This will let you display them correctly, for example.

### `null`field values
You can turn on this setting (off by default) so that fields that have null values are not output to JSON.  For certain
applications, this can dramatically reduce the size of the JSON output.
>#### `boolean`isSkipNullFields()
>- [ ] Returns`true`indicating fields with null values will not be written,`false`will still output the field with an associated null value. The default is`false.`

>#### `WriteOptionsBuilder` skipNullFields(`boolean skipNullFields`)
>- [ ] Sets the boolean where`true`indicates fields with null values will not be written to the JSON,`false`will allow the field to still be written.

### `Map`output format
In Java, not all `Map` instances have `String` keys.  If all keys are Strings, then the Map is output as a JSON object { }
with the keys of the `Map` as the keys of the JSON object.  However, when the key is an object, then all the keys are output
in an @keys:[] and the values are output as @values:[].  If this option is set to true, Maps will always be written 
with @keys:[],@values:[] - no special treatment of all `String` keyed `Maps.`
>#### `boolean`isForceMapOutputAsTwoArrays()
>- [ ] Returns`true`if set to force Java Maps to be written out as two parallel arrays, once for keys, one array for values. The default is`false.`

>#### `WriteOptionsBuilder` forceMapOutputAsTwoArrays(`boolean forceMapOutputAsTwoArrays`)
>- [ ] Sets the boolean 'forceMapOutputAsTwoArrays' setting. If Map's have String keys they are written as normal JSON objects. With this setting enabled, Maps are written as two parallel arrays.

### Floating Point Options
Although the JSON spec does not support Nan and Infinity, it can be convenient to allow it in the JSON, and have
the values be both written and read properly. This feature is off by default.  Although the JSON with Nan and Infinity may work for
your application, keep in mind, NaN, +Inf, -Inf are not necessarily going to be supported by other APIs and services.
The default is false.
> #### `boolean`isAllowNanAndInfinity
>- [ ] Returns`true`if set to allow serialization of`NaN`and`Infinity`for`doubles`and`floats.`

>#### `WriteOptionsBuilder` allowNanAndInfinity(`boolean allow`)
>- [ ] true will allow`doubles`and`floats`to be output as`NaN`and`INFINITY`,`false`and these values will come across
   as`null.`

### Enum Options
Most developers use`enums`as a discrete list of values. However, there are instances where additional fields
are added to the`enum.` Additionally, we have seen both public and private fields in these cases.
The Enum options allow you to skip private fields (retain public only), keep the Enum to only it's "name" value, or 
force the Enum to be written as an object, or a single value.
>#### `boolean`isWriteEnumAsString()
>- [ ] Returns`true`if enums are to be written out as Strings. If this is false, then enums are being written as objects, and then the `isEnumPublicFieldsOnly()` API is valid and will indicate if enums are to be written with public/private fields.
>#### `boolean`isEnumPublicFieldsOnly()
>- [ ] Returns`true`indicating that only public fields will be output on an `enum.`The default is to only output public fields as well as to write it as a primitive (single value) instead of a JSON { } object when possible.

>#### `WriteOptionsBuilder` writeEnumsAsString()
>- [ ] Sets the option to write out enums as a String. This is the default option.  If you have called `writeEnumAsJsonObject(true or false),` call `writeEnumsAsString()`to return to enum output as String.
>#### `WriteOptionsBuilder` writeEnumAsJsonObject(`boolean writePublicFieldsOnly`)
>- [ ] Sets the option to write out all the member fields of an enum, using JSON { } format for the enum, to allow for multiple fields. Setting this option to true or false (include/exclude private fields), turns off the writeEnumsAsString() option.

### Customized JSON Writers
Want to customize JSON output for a particular class?  For any Java class, you can author a custom writer
(`JsonClassWriter`) and associate it to the class.  Then your 'JsonClassWriter' class will be called on to write the 
class out, giving you the capability to selectively choose fields, format it differently, etc.
>#### `JsonWriter.JsonClassWriter` getCustomWrittenClass(`Class`)
>- [ ] Returns a`Map`of Class to custom JsonClassWriter's use to write JSON when the class is encountered during serialization.
>#### `boolean`isCustomWrittenClass(`Class`)
>- [ ] Checks to see if there is a custom writer associated with a given class. Returns`true`if there is,`false`otherwise.

>#### `WriteOptionsBuilder` setCustomWrittenClasses(`Map<Class, JsonWriter.JsonClassWriter> customWrittenClasses`)
>- [ ] Establishes the passed in`Map`as the complete list of custom writers to be used when writing JSON.
>#### `WriteOptionsBuilder` addCustomWrittenClass(`Class, JsonWriter.JsonClassWriter customWriter`)
>- [ ] Adds a custom writer for a specific Class.

### "Not" Customized Class Writers
Customized writers are associated to a particular class AND it's derivatives.  If the inheritance model is causing a class to be custom
written and you do not want that, you can add that class to the "Not" customized list. Being 
on the "Not" customized list takes priority over the customized list, letting the default json-io JSON writer do its job.
>#### `boolean`isNotCustomWrittenClass(`Class`)
>- [ ] Checks if a class is on the not-customized list. Returns`true`if it is,`false`otherwise.

>#### `WriteOptionsBuilder` addNotCustomWrittenClass(`Class notCustomClass`)
>- [ ] Adds a class to the not-customized list. This class will use 'default' processing when written.  This option is available as custom writers apply to the class and their derivatives.  This allows you to shut off customization for a class that is picking it up due to inheritance.
>#### `WriteOptionsBuilder` setNotCustomWrittenClasses(`Collection<Class> notCustomClasses`)
>- [ ] Initializes the list of classes on the non-customized list.

### Add Custom Options (String keys of your choice associated to values of your choice)
Custom options may be useful to pass to a custom writer. Any options you add here will be available to custom writer.
>#### `WriteOptionsBuilder` addCustomOption(`String key, Object value`)
>- [ ] Add the custom key/value pair to your WriteOptions. These will be a available to any custom writers you add.  If you add a key with a value of null associated to it, that will remove the custom option. 

### Included Fields
This feature allows you to indicate which fields should be output for a particular class. If fields are specified here, only
these fields will be output for a particular class.  Think of it as a white-list approach to specifying the fields. For example,
if a class has a lot of fields on it, and you include only two, only those two fields will be output for that class.
If a field is listed in both the includes and excludes list, the exclude list takes precedence.
>#### `Set<String>` getIncludedFields(`Class`)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be included in the written JSON.

>#### `WriteOptionsBuilder` addIncludedField(`Class, String fieldName`)
>- [ ] Adds a single field to be included in the written JSON for a specific class.
>#### `WriteOptionsBuilder` addIncludedFields(`Class, Collection<String> includedFields`)
>- [ ] Adds a`Collection`of fields to be included in written JSON for a specific class.
>#### `WriteOptionsBuilder` addIncludedFields(`Map<Class, Collection<String>> includedFields`)
>- [ ] Adds multiple Classes and their associated fields to be included in the written JSON.

### Excluded Fields
This feature allows you to indicate which fields should be not be output for a particular class. If fields are 
specified here, then these named fields will not be output for a particular class.  Think of it as a black-list approach
to specifying the fields. If a particular class has a large amount of fields and you only want to exclude a few, this
is a great approach to trim off just those few fields (privacy considerations, ClassLoader fields, etc.)  You can also
add fields to an 'include' list, in which case only those fields will be output for the particular class.  If a 
field is listed in both the includes and excludes list, the exclude list takes precedence.
>#### `Set<String>` getExcludedFields(`Class`)
>- [ ] Returns a`Set`of Strings field names associated to the passed in class to be excluded in the written JSON.

>#### `WriteOptionsBuilder` addExcludedField(`Class, String excludedField`)
>- [ ] Adds a single field to be excluded from the written JSON for a specific class.
>#### `WriteOptionsBuilder` addExcludedFields(`Class, Collection<String> excludedFields`)
>- [ ] Adds a`Collection`of fields to be excluded in written JSON for a specific class.
>#### `WriteOptionsBuilder` addExcludedFields(`Map<Class, Collection<String>> excludedFields`)
>- [ ] Adds multiple Classes and their associated fields to be excluded from the written JSON.

### Non-Standard Accessors
This option allows you set accessors (used when writing JSON) that access properties from source Java objects, where 
the method name does not follow a standard setter/getter property name. For example, on `java.time.Instance,` to get 
the `second` field, the accessor method is `getEpochSecond().`
>#### `WriteOptionsBuilder` addNonStandardAccessor(`Class, String fieldName, String methodName`)
>- [ ] Add another field and non-standard method to the Class's list of non-standard accessors. For the example above, use `addNonStandardMapping(Instant.class, "second", "getEpochSecond").`
 
### Field Filters
These options allows you add/remove FieldFilters (your own derived implementation) to/from the field filter chain. 
Each filter in the filter chain is presented the reflected field and can return true to exclude a field.  This works
well when filter a field by a characteristic of a field as opposed to its name. For example, you could exclude fields 
by field characteristics such as transient, final, volatile, etc.  See existing EnumFieldFilter or StaticFieldFilter.
>#### `WriteOptionsBuilder` addFieldFilter(`FieldFilter filter`)
>- [ ] Add a field filter to the field filter chain. 
>#### `WriteOptionsBuilder` removeFieldFilter(`FieldFilter filter`)
>- [ ] Remove a field filter from the field filter chain. 

### java.util.Date and java.sql.Date format
This feature allows you to control the format for`java.util.Date and java.sql.Date`fields.  The default output format
for these fields is numeric`long`format, which is fast and small. This may work great for your application, but not all applications.
You can set the Date format via String format like long ISO date time format, e.g. "yyyy-MM-dd'T'HH:mm:ss". All the standard
JDK formatting options are available for use here.  There are convenience methods for short and long ISO date formats.
>#### `boolean`isLongDateFormat()
>- [ ] Returns`true`if`java.util.Date`and`java.sql.Date` are being written in`long`(numeric) format.

>#### `WriteOptionsBuilder` dateTimeFormat(`String format`)
>- [ ] Changes the date-time format to the passed in format.
>#### `WriteOptionsBuilder` isoDateFormat()
>- [ ] Changes the date-time format to the ISO date format: "yyyy-MM-dd".
>#### `WriteOptionsBuilder` isoDateTimeFormat()
>- [ ] Changes the date-time format to the ISO date-time format: "yyyy-MM-dd'T'HH:mm:ss".
>#### `WriteOptionsBuilder` longDateFormat()
>- [ ] Changes the`java.uti.Date`and`java.sql.Date`format output to a`long,`the number of seconds since Jan 1, 1970 at midnight. For speed, the default format is`long.`Returns `WriteOptionsBuilder`for chained access.

### Non-Referenceable Classes (Opposite of Instance Folding)
For small immutable classes, often there is no need to use @id/@ref with them, as they are effectively primitives.  All primitives,
primitive wrappers, BigInteger, BigDecimal, Atomic*, java.util.Date, String, Class, are automatically marked
as non-referenceable. You can add more immutable classes of your own.  

A side effect of this is that it could change the "shape" of your graph, if you were counting on two different fields
that point to the same String/BigDecimal/Integer to have the same == instance. Example, the String "hello" appears
25 times in your JSON.  Should the word "hello" be repeated 25 times in the text, or would it be better to
see @id = "n", and @ref="n" for every place that the word appeared. The @id/@ref maintain the shape of the object
graph, however, for most simple objects, this is not necessary and the JSON is more human-readable if the small 
objects were treated as "primitives."
>#### `boolean`isNonReferenceableClass(`Class`)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.

>#### `WriteOptionsBuilder` addNonReferenceableClass(`Class`)
>- [ ] Adds a class to be considered "non-referenceable." 
