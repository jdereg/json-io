## Controlling the output JSON using `WriteOptions`
Create a new `WriteOptions` instance and turn various features on/off using the methods below. Example:
```java
WriteOptions writeOptions = new WriteOptionsBuilder().prettyPrint(true).writeLongsAsStrings(true).build();
JsonIo.toJson(root, writeOptions);
```
To pass these to `JsonIo.toJson(root, writeOptions)` set up a `WriteOptions` using the `WriteOptionsBuilder.`
You can view the Javadoc on the `WriteOptionsBuilder` class for detailed information. The `WriteOptions` are created
and made read-only by calling the `.build()` method on the `WriteOptionsBuilder.` You can have multiple `WriteOptions`
instances for different scenarios, and safely re-use them once built (read-only). A `WriteOptions` instance can be
created from another `WriteOptions` instance by using `new WriteOptionsBuilder(writeOptionsToCopyFrom).`

---
### Constructors
The `ClassLoader` in the `WriteOptionsBuilder` is utilized to convert `String` class names into `Class` instances.
This feature allows dynamic class loading during the serialization process, ensuring that the correct class types are
associated with the serialized data.

Create new `WriteOptions` instances.
>#### new WriteOptionsBuilder().feature1().feature2(args).build()
>- [ ] Start with default options and turn on feature1 and feature2 (which requires an argument)
>#### new WriteOptionsBuilder(`WriteOptions other`)
>- [ ] Copy all the settings from the passed in 'other' `WriteOptions.`

In the descriptions below, the first set of APIs listed are the "getter" (read) methods used to retrieve settings from
the `WriteOptions`. The methods listed subsequently are the "setter" APIs on the `WriteOptionsBuilder`, which are used
to configure and activate various options.

Additionally, the `WriteOptionsBuilder` "setter" APIs are designed to return the `WriteOptionsBuilder` itself,
facilitating chained method calls for streamlined configuration. This chaining allows for the concise and fluent setting
of multiple options in a single statement.

### ClassLoader

The `ClassLoader` in the `WriteOptionsBuilder` is utilized to convert `String` class names into `Class` instances. This
functionality is essential for dynamically loading classes during the serialization process, ensuring that the
appropriate class types are used based on the class names specified in the options.
>#### `ClassLoader` getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names.

>#### `WriteOptionsBuilder` classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names.

### MetaKeys - @id, @ref, @type, @items, @keys, @values

`json-io` utilizes several special fields added to JSON objects to aid in accurate deserialization:
- **@id** - When an object is referenced multiple times within the JSON (e.g., across fields, array elements, collection elements, or map keys/values), its first occurrence is tagged with an `@id` and a unique identifier (`n`). This tagging facilitates referencing the object in subsequent occurrences without redundancy.
- **@ref:_n_** - Subsequent references to an object initially tagged with an `@id` are marked with `@ref` and the same identifier (`n`). This approach greatly reduces JSON size because the object is serialized only once, and subsequent mentions are handled through references.
- **@type** - Specifies the class of the object being serialized, which helps `json-io` determine the correct class to instantiate during deserialization.
- **@items, @keys, @values** - These keys are used to structure collections and maps in JSON, ensuring that the original data structure's integrity is maintained during serialization and deserialization.

These meta-keys enhance the efficiency of the JSON format by supporting circular references (e.g., A => B => C => A), reducing memory usage, and preserving the original object graph's structure through serialization.
>#### `boolean` isShortMetaKeys()
>- [ ] Returns `true` if instructed to use short meta-keys (@i => @id, @r => @ref, @t => @type, @k => @keys,
   @v => @values, @e => @items), `false` for full size. `false` is the default.

>#### `WriteOptionsBuilder` shortMetaKeys(`boolean shortMetaKeys`)
>- [ ] Sets to boolean `true` to turn on short meta-keys, `false` for long.

### Aliasing - Shorten Class Names in @type

Aliasing is a feature in `json-io` that simplifies JSON output by converting fully qualified Java class names into shorter, simpler class names. For example, `java.util.ArrayList` is aliased to just `ArrayList`, making the JSON more compact and readable.

- **Default Aliases**: By default, `json-io` includes aliases for many common JDK classes to reduce the JSON content size automatically.
- **Adding Custom Aliases**: You can add custom aliases for your classes within the application. For instance, adding an alias from `com.mycompany.Foo` to `Foo` will also automatically generate aliases for array types such as `Foo[]`, `Foo[][]`, and `Foo[][][]`, ensuring consistency across all usages of the class in JSON.
- **Scope of Aliases**: The aliases added affect only the instance of `WriteOptions` created from a `WriteOptionsBuilder`. To apply aliases across all instances throughout the JVM's lifecycle, refer to the [application-scoped options](/user-guide-writeOptions.md#application-scoped-options-full-lifecycle-of-jvm) section.
- **External Alias Configuration**: Alternatively, you can manage aliases by creating an [aliases.txt](/src/main/resources/config/aliases.txt) file and placing
  it in the class path. `json-io` provides a comprehensive default list, but you can override this by providing your own
  file.
>#### `String` getTypeNameAlias(`String typeName`)
>- [ ] Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
>#### `Map<String, String>` aliases()
>- [ ] Returns `Map<String, String>` containing all String class names to alias names.

>#### `WriteOptionsBuilder` aliasTypeNames(`Map<String, String> aliasTypeNames`)
>- [ ] Puts the `Map` containing String class names to alias names. The passed in `Map` will be `putAll()` copied overwriting any
   entries that match values in the passed in Map. New entries in the Map are added.
>#### `WriteOptionsBuilder` aliasTypeName(`String typeName, String alias`)
>- [ ] Sets the alias for a given class name.
>#### `WriteOptionsBuilder` aliasTypeName(`Class, String alias`)
>- [ ] Sets the alias for a given class.
>#### `WriteOptionsBuilder` removeAliasTypeNamesMatching(`String typeNamePattern`)
>- [ ] Remove alias entries from this `WriteOptionsBuilder` instance where the Java fully qualified string class name
   matches the passed in wildcard pattern. The `typeNamePattern` matches using a wild-card pattern, where * matches
   anything and ? matches one character. As many * or ? can be used as needed.

### @type

The `@type` field in `json-io` is used to provide hints to `JsonReader` about which classes to instantiate. Typically, `json-io` is able to automatically determine the Java type (class) of an object based on the field type of an object, the component type of an array, or if a root class is explicitly specified. However, there are scenarios where the type cannot be directly inferred.

For example, consider a field in a class declared as an `Object`. If more complex, derived instances are assigned to
this `Object` field, `json-io` needs additional information to correctly handle serialization and deserialization.
In such cases, `json-io` includes an `@type=typename` entry in the JSON object to specify the exact class type that should be instantiated. This ensures that the JsonReader knows to instantiate and populate the specific class with the data from the input JSON.

This mechanism is crucial for maintaining the fidelity of the object graph when the field types are not concrete or are too generic to determine directly, enabling accurate and efficient JSON processing.

>#### `boolean` isAlwaysShowingType()
>- [ ] Returns `true` if set to always show type (@type).
>#### `boolean` isNeverShowingType()
>- [ ] Returns `true` if set to never show type (no @type).
>#### `boolean` isMinimalShowingType()
>- [ ] Returns `true` if set to show minimal type (@type).  This is the default.

>#### `WriteOptionsBuilder` showTypeInfoAlways()
>- [ ] Sets to always show type.
>#### `WriteOptionsBuilder` showTypeInfoNever()
>- [ ] Sets to never show type.
>#### `WriteOptionsBuilder` showTypeInfoMinimal()
>- [ ] Sets to show minimal type. This means that when the type of object can be inferred, a type field will not be output.  This is the default.

### Pretty Print

To generate more readable JSON output with multiple lines and indentations, enable the pretty-print feature in
`json-io.` This feature formats the JSON output to be visually structured, which is particularly helpful for debugging
or when viewing the JSON data directly. Conversely, if compactness is preferred, especially for network transmission or
storage efficiency, you can disable pretty printing to produce a single-line, unindented JSON output.

To activate pretty printing, configure your serialization settings accordingly:

>#### `boolean` isPrettyPrint()
>- [ ] Returns the pretty-print setting, `true` being on, using lots of vertical white-space and indentations, `false` will output JSON in one line. The default is `false.`

>#### `WriteOptionsBuilder` prettyPrint(`boolean prettyPrint`)
>- [ ] Sets the 'prettyPrint' setting, `true` to turn on, `false` will turn off. The default setting is `false.`

### lruSize - LRU Size [Cache of fields and filters]
Set the maximum number of `Class` to `Field` mappings and `Class` to accessor mappings. This will allow infrequently used `Class's`
to drop from the cache - they will be dynamically added back if not in the cache.  Reduces operational memory foot print.
> #### `int` lruSize()
>- [ ] Return the LRU size

> #### `WriteOptionsBuilder` lruSize(`int size`)
>- [ ] Set the max LRU cache size

### Automatically Close OutputStream (or Not)

In `json-io`, you have the option to automatically close the `OutputStream` after writing JSON output or to leave it
open for further writing. This feature is particularly useful in scenarios where multiple JSON objects need to be written
sequentially to the same stream.

#### Example Use Case: NDJSON Format
NDJSON (Newline Delimited JSON) is a format where multiple JSON objects are separated by newlines (`{...}\n{...}\n{...}`). To efficiently create NDJSON, you might prefer not to close the stream after each JSON object is written, allowing continuous addition to the stream:

```java
   WriteOptions options = new WriteOptionsBuilder().closeStream(false).build();
OutputStream outputStream = new FileOutputStream("output.ndjson");
   try {
           JsonIo.toJson(outputStream, object1, options);
        JsonIo.toJson(outputStream, object2, options);
// Continue writing more JSON objects
   } finally {
           outputStream.close(); // Ensure the stream is closed after all operations are complete
   }
```
#### Benefits
This setup ensures that the OutputStream remains open for additional writes, making it ideal for formats like NDJSON or
when batch processing multiple JSON outputs. Refer to the user guide's starting section for a comprehensive example and
additional guidance.
>#### `boolean` isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after write (the default), or `false`to leave stream open after writing to it.

>#### `WriteOptionsBuilder` closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting, `true` to turn on, `false` will turn off. The default setting is `true.`

### `Long` as String

When dealing with large numerical values, especially those ranging between 17 to 19 digits, it's important to consider the limitations of JavaScript's number handling. JavaScript internally uses the `Double` format (IEEE 754) for numbers, which may not accurately represent long integers within this range due to precision limitations.

#### Why Output as String?
To ensure that these large `Long` values are accurately conveyed and processed in JavaScript environments, `json-io` provides the option to serialize `Long` values as strings. This approach preserves the full precision of the data when transmitted to JavaScript, allowing for correct display and usage without loss of fidelity.

#### Default Behavior
By default, this feature is turned off. To enable the output of `Long` values as strings, you need to adjust your serialization settings:
>#### `boolean` isWriteLongsAsStrings()
>- [ ] Returns `true` indicating longs will be written as Strings, `false` to write them out as native JSON numbers.

>#### `WriteOptionsBuilder` writeLongsAsStrings(`boolean writeLongsAsStrings`)
>- [ ] Set to boolean `true` to turn on writing longs as Strings, `false` to write them as native JSON longs. The default setting
   is `false.` This feature is important to marshal JSON with large long values (18 to 19 digits) to Javascript. Long/long values
   are represented in Javascript by storing them in a `Double` internally, which cannot represent a full `long` value. Using
   this feature allows longs to be sent to Javascript with all their precision, however, they will be Strings when received
   in the Javascript. This will let you display them correctly, for example.

### `null` Field Values

In `json-io`, you have the option to configure how `null` values are handled during serialization. By default, fields with `null` values are included in the JSON output. However, you can change this setting to exclude `null` values, which can significantly reduce the size of the JSON output for applications where `null` fields are not necessary.

#### Configuring `null` Value Exclusion
To exclude `null` values from the JSON output, you need to activate this setting in the `WriteOptions`. This can be particularly beneficial in scenarios where minimizing the JSON size is critical or where the presence of `null` values offers no additional value:
>#### `boolean` isSkipNullFields()
>- [ ] Returns `true` indicating fields with null values will not be written, `false` will still output the field with an associated null value. The default is `false.`

>#### `WriteOptionsBuilder` skipNullFields(`boolean skipNullFields`)
>- [ ] Sets the boolean where `true` indicates fields with null values will not be written to the JSON, `false` will allow the field to still be written.

### `Map` Output Format

`json-io` provides flexible serialization options for Java `Map` instances, accommodating different types of keys.

#### Default Handling of `Map` Keys
- **String Keys**: If all keys in the `Map` are `Strings,` `json-io` serializes the `Map` as a standard JSON object
  `{},` using the `Map's,` keys as the keys in the JSON object.
- **Object Keys**: When the keys are not all strings (e.g., they are objects), `json-io` handles this by serializing the
  keys and values separately. The keys are placed in an array under an `@keys` tag, and the corresponding values are
  listed in an `@values` array. This ensures that the structure and associations within the `Map` are preserved.

#### Configuration Option
If you prefer a consistent format for all `Map` instances, regardless of the key types, you can enable a setting to
always serialize `Maps` using the `@keys:[], @values:[]` format. This option eliminates the special treatment for
`Maps` with string-only keys:

>#### `boolean` isForceMapOutputAsTwoArrays()
>- [ ] Returns `true` if set to force Java Maps to be written out as two parallel arrays, once for keys, one array for values. The default is `false.`

>#### `WriteOptionsBuilder` forceMapOutputAsTwoArrays(`boolean forceMapOutputAsTwoArrays`)
>- [ ] Sets the boolean 'forceMapOutputAsTwoArrays' setting. If Map's have String keys they are written as normal JSON objects. With this setting enabled, Maps are written as two parallel arrays.

### Floating Point Options

Handling special floating point values such as NaN (Not a Number) and Infinity (+Inf/-Inf) in JSON can be tricky since the JSON specification does not officially support these values. However, `json-io` offers a feature that allows these values to be serialized and deserialized correctly, although this feature is disabled by default.

#### Considerations for Enabling this Feature:
- **Compatibility**: While enabling the serialization of NaN and Infinity can be useful for applications that understand and can process these values, be cautious as other systems and APIs might not support them, leading to potential interoperability issues.
> #### `boolean` isAllowNanAndInfinity
>- [ ] Returns `true` if set to allow serialization of `NaN` and `Infinity` for `doubles` and `floats.`

>#### `WriteOptionsBuilder` allowNanAndInfinity(`boolean allow`)
>- [ ] true will allow `doubles` and `floats` to be output as `NaN` and `INFINITY,` `false` and these values will come across
   as `null.`

### Enum Options in `json-io`

Enums in Java are commonly used as a discrete list of values, but there are instances where additional fields are added to these enums. These fields can be either public or private, depending on the design requirements.

#### Handling Enum Fields
The `WriteOptions` of `json-io` provide a flexible set of configurations that allow developers to customize
how enums and `EnumSet` objects are serialized into JSON. These options determine whether enums are written
as simple strings, detailed objects with `public` and `private` fields, or represented using specific metadata
like `@enum` or `@type.` By adjusting these settings, users can balance between backward compatibility and
newer, streamlined serialization formats. Below is a detailed breakdown of the available options and their
effects.

#### Configuration Example
Here's how you configure these options in `json-io`:

>#### `boolean` isWriteEnumAsString()
>- [ ] Returns `true` if enums are to be written out as Strings (default). If this is false, then enums are being written as objects, and then the `isEnumPublicFieldsOnly()` API is valid and will indicate if enums are to be written with public/private fields.
>#### `boolean` isEnumPublicFieldsOnly()
>- [ ] Returns `true` indicating that only public fields will be output on an enum (default). The default is to only output public fields as well as to write it as a primitive (single value) instead of a JSON { } object when possible. Set to `false` to write public/private fields.
>#### `boolean` isEnumSetWrittenOldWay()
>- [ ] Returns `true` if `EnumSet` is written with `@enum=enumElementTypeClass,` or `false` if `EnumSets` are written with `@type=enumElementTypeClass`.  Default is `true` for backward compatibility, but will switch to `false` in an future release.

>#### `WriteOptionsBuilder` writeEnumsAsString()
>- [ ] Sets the option to write enums as a `String.` This is the default option.  If you have called `writeEnumAsJsonObject(true or false),` call `writeEnumsAsString()`to return to enum output as `String.`
>#### `WriteOptionsBuilder` writeEnumAsJsonObject(`boolean writePublicFieldsOnly`)
>- [ ] Sets the option to write all the member fields of an enum, using JSON { } format for the enum, to allow for multiple fields. Setting this option to `true` or `false` (include/exclude private fields), turns off the writeEnumsAsString() option. This option is off by default - enums are written as `String` by default.
>#### `WriteOptionsBuilder` writeEnumSetOldWay(`boolean writeEnumSetOldWay`)
>- [ ] Sets the option to write `EnumSet` with `@enum=elementTypeClassName` or `@type=elementTypeClassName`. The default is true (@enum) for backward compatibility. This will change in a future release.

### Customizing JSON Output with `JsonClassWriter`

If you need tailored JSON output for specific Java classes, `json-io` allows you to author and associate a custom
writer (`JsonClassWriter`) to any class. This customization can significantly enhance how your data is serialized,
offering precise control over the output format.

#### How It Works:
- **Create a `JsonClassWriter`**: Implement a `JsonClassWriter` for the class whose output you wish to customize. This
  writer will define how the class is serialized into JSON.
- **Select Fields and Formatting**: Within your custom writer, you have the freedom to select which fields to include
  and how they should be formatted. This is particularly useful for classes where only certain fields need to be exposed,
  or where standard serialization does not meet your needs.
- **Associate the Writer**: Once you've created your `JsonClassWriter`, associate it with the class it should
  serialize. `json-io` will then use your custom writer each time it serializes an instance of that class.

#### Example Implementation:
Here is a simple example of how you might set up a `JsonClassWriter`:

```java
public class MyCustomWriter implements JsonClassWriter {
   @Override
   public void write(Object obj, boolean showType, Writer output, Map args) throws IOException {
      MyCustomClass myObj = (MyCustomClass) obj;
      output.write("{");
      output.write("\"customField\": \"" + myObj.getCustomField() + "\"");
      output.write("}");
   }
}

// Associate the custom writer with the class
new WriteOptionsBuilder().addWriter(MyCustomClass.class, new MyCustomWriter());
```
This example shows a custom writer for MyCustomClass that selectively serializes only a specific field.
This approach can be adapted to any class to meet your specific serialization needs.

>#### `JsonWriter.JsonClassWriter` getCustomWrittenClass( `Class` )
>- [ ] Returns a `Map` of Class to custom JsonClassWriter's use to write JSON when the class is encountered during serialization.
>#### `boolean` isCustomWrittenClass( `Class` )
>- [ ] Checks to see if there is a custom writer associated with a given class. Returns `true` if there is, `false` otherwise.

>#### `WriteOptionsBuilder` setCustomWrittenClasses(`Map<Class, JsonWriter.JsonClassWriter> customWrittenClasses`)
>- [ ] Establishes the passed in `Map` as the complete list of custom writers to be used when writing JSON.
>#### `WriteOptionsBuilder` addCustomWrittenClass(`Class, JsonWriter.JsonClassWriter customWriter`)
>- [ ] Adds a custom writer for a specific Class.

### "Not" Customized Class Writers

In `json-io,` customized writers are typically associated with a specific class and its derivatives. However, there are
situations where the inheritance model might inadvertently cause a class to be handled by a custom writer when this is
not desired. To address this, you can specify classes that should not use customized writers, effectively overriding
the inheritance behavior.

#### How It Works:
- **Priority of "Not" Customized List**: Classes added to the "Not" customized list take precedence over those on the
  customized list. This ensures that even if a class or its parent is associated with a custom writer, you can exclude it
  explicitly, allowing the default `json-io` JSON writer to handle its serialization.
- **Using the Default Writer**: By placing a class on the "Not" customized list, `json-io` reverts to using the
  standard serialization mechanism for that class, bypassing any custom writer logic that might otherwise apply due to
  class inheritance.

#### Benefits:
This feature is particularly useful in complex inheritance structures where you need fine-grained control over
serialization behavior, ensuring that certain classes are serialized in a standard, predictable manner regardless of
the broader customization strategy.

>#### `boolean` isNotCustomWrittenClass( `Class` )
>- [ ] Checks if a class is on the not-customized list. Returns `true` if it is, `false` otherwise.

>#### `WriteOptionsBuilder` addNotCustomWrittenClass(`Class notCustomClass`)
>- [ ] Adds a class to the not-customized list. This class will use 'default' processing when written.  This option is available as custom writers apply to the class and their derivatives.  This allows you to shut off customization for a class that is picking it up due to inheritance.
>#### `WriteOptionsBuilder` setNotCustomWrittenClasses(`Collection<Class> notCustomClasses`)
>- [ ] Initializes the list of classes on the non-customized list.

### Add Custom Options

In `json-io,` you have the flexibility to define custom options — key-value pairs that you can associate with specific serialization behaviors. These options are particularly useful for passing additional data or configuration settings to a custom writer, allowing for more dynamic and context-sensitive serialization.

#### How It Works:
- **Defining Custom Options**: Custom options are essentially string keys associated with values of your choice. Once
  defined, these options can be accessed by custom writers during the serialization process, enabling them to adjust their
  behavior based on the options provided.
- **Usage in Custom Writers**: When a custom writer is invoked, it can retrieve and utilize these custom options to
  make decisions about how to serialize particular aspects of an object. This capability is especially useful for
  implementing advanced serialization logic that depends on runtime conditions or specific application requirements.
>#### `WriteOptionsBuilder` addCustomOption(`String key, Object value`)
>- [ ] Add the custom key/value pair to your WriteOptions. These will be a available to any custom writers you add.  If you add a key with a value of null associated to it, that will remove the custom option.

### Included Fields

The "Included Fields" feature in `json-io` provides a mechanism to selectively control the serialization of fields
within a class. This approach acts as a whitelist, allowing you to specify exactly which fields should be included in
the JSON output for a particular class.

#### How It Works:
- **Field Selection**: By specifying fields in the "Included Fields" setting, you dictate that only these fields will
  be serialized when an instance of the class is processed. This is particularly useful for classes with numerous fields where only a subset is relevant for the JSON output.
- **Whitelist Approach**: This feature adopts a whitelist approach, ensuring that serialization is limited to only
  those fields explicitly listed.

#### Handling Conflicts:
- **Precedence Rules**: If a field appears in both the "Includes" and "Excludes" lists, the "Excludes" list will take precedence. This ensures that the exclusion rules override the inclusion rules in cases of conflict.

#### Example Usage:
To configure the included fields for a class, you might set up your serialization options as follows:

```java
WriteOptions options = new WriteOptionsBuilder()
        .includeFields(MyClass.class, "field1", "field2")
        .build();
String json = JsonIo.toJson(instanceOfMyClass, options);
```

#### Benefits:
This feature is particularly useful for:

- **Reducing Payload Size**: Minimizing the size of the JSON output by excluding unnecessary fields.
- **Enhancing Security and Privacy**: Limiting the exposure of sensitive data by not serializing it.
- **Customizing Output**: Tailoring the JSON output to meet specific front-end or API consumption requirements.

Using the "Included Fields" feature effectively allows developers to fine-tune the serialization process, ensuring that only relevant data is included in the JSON output.

>#### `Set<String>` getIncludedFields( `Class` )
>- [ ] Returns a `Set` of Strings field names associated to the passed in class to be included in the written JSON.

>#### `WriteOptionsBuilder` addIncludedField(`Class, String fieldName`)
>- [ ] Adds a single field to be included in the written JSON for a specific class.
>#### `WriteOptionsBuilder` addIncludedFields(`Class, Collection<String> includedFields`)
>- [ ] Adds a `Collection` of fields to be included in written JSON for a specific class.
>#### `WriteOptionsBuilder` addIncludedFields(`Map<Class, Collection<String>> includedFields`)
>- [ ] Adds multiple Classes and their associated fields to be included in the written JSON.

### Excluded Fields

The "Excluded Fields" feature in `json-io` offers a way to selectively prevent certain fields from being serialized
in the JSON output. This feature works as a blacklist, where you can specify which fields should be excluded for a
particular class.

#### How It Works:
- **Field Exclusion**: By specifying fields in the "Excluded Fields" setting, you ensure that these fields are not
  included when an instance of the class is serialized. This is effective when a class has many fields but only a few
  need to be omitted from the JSON output.
- **Blacklist Approach**: This approach provides a straightforward way to exclude specific fields, which can be
  useful for privacy considerations, omitting unnecessary `ClassLoader` fields, or other sensitive data.

#### Handling Conflicts:
- **Precedence Rules**: In cases where fields are specified in both the "Includes" and "Excludes" lists, the fields in the "Excludes" list will take precedence. This ensures that exclusion rules override inclusion rules where there is a conflict.

#### Example Usage:
To configure the excluded fields for a class, you can set up your serialization options like this:

```java
WriteOptions options = new WriteOptionsBuilder()
        .excludeFields(MyClass.class, "field3", "field4")
        .build();
String json = JsonIo.toJson(instanceOfMyClass, options);
```
In this setup, field3 and field4 of MyClass will not appear in the JSON output, regardless of how many other fields
the class may have.

#### Benefits:
This feature is particularly valuable for:

- **Enhancing Privacy**: Ensuring that sensitive data fields are not inadvertently serialized and exposed.
- **Reducing Output Clutter**: Streamlining the JSON output by removing unnecessary or irrelevant fields.
- **Customizing Output**: Allowing more control over the JSON output to fit specific data presentation or API requirements.

Utilizing the "Excluded Fields" feature effectively allows developers to manage the serialization of class fields meticulously, focusing on only transmitting necessary information.

>#### `Set<String>` getExcludedFields( `Class` )
>- [ ] Returns a `Set` of Strings field names associated to the passed in class to be excluded in the written JSON.

>#### `WriteOptionsBuilder` addExcludedField(`Class, String excludedField`)
>- [ ] Adds a single field to be excluded from the written JSON for a specific class.
>#### `WriteOptionsBuilder` addExcludedFields(`Class, Collection<String> excludedFields`)
>- [ ] Adds a `Collection` of fields to be excluded in written JSON for a specific class.
>#### `WriteOptionsBuilder` addExcludedFields(`Map<Class, Collection<String>> excludedFields`)
>- [ ] Adds multiple Classes and their associated fields to be excluded from the written JSON.

### Non-Standard Accessors

The "Non-Standard Accessors" feature in `json-io` provides the flexibility to define custom accessor methods for
properties in Java objects that do not adhere to the conventional getter/setter naming patterns. This is particularly
useful for interacting with properties where the accessor methods have unique names.

#### Use Case:
- **Custom Accessor Names**: Some classes, such as `java.time.Instant`, use non-standard methods like `getEpochSecond()`
  to access properties, which do not follow the traditional `getPropertyName()` format. This can pose challenges when
  these methods need to be used for serializing properties into JSON, especially in environments like Java 17+ where
  reflective access to private fields is restricted.

#### Default Accessors:
- **JDK Classes**: For many JDK classes, json-io has already configured these non-standard accessors by default. For
  example, accessors for `java.time.Instant` and similar classes are pre-defined, facilitating easier integration and usage
  without additional configuration.

#### Benefits:
- **Compatibility with Java 17+**: Ensures that json-io can continue to function seamlessly with Java versions that enforce stricter encapsulation by using public methods to access property values.
- **Flexibility in Serialization**: Allows developers to precisely control how properties are accessed and serialized, accommodating various coding styles and requirements.
  By enabling custom accessors for non-standard method names, developers can enhance the adaptability and robustness of their serialization logic in json-io, ensuring compatibility across different Java versions and compliance with modern encapsulation practices.

#### Configuring Non-Standard Accessors:
This option allows `json-io` to recognize and utilize these non-standard method names as accessors during serialization, ensuring that property values can be correctly retrieved and included in the JSON output.
>#### `WriteOptionsBuilder` addNonStandardGetter(`Class, String fieldName, String methodName`)
>- [ ] Add another field and non-standard method to the Class's list of non-standard accessors.
   For the example above, use `addNonStandardMapping(Instant.class, "second", "getEpochSecond").`

### FieldFilters

In `json-io`, `FieldFilters` provide a dynamic mechanism for selectively including or excluding fields from serialization based on specific field characteristics rather than just their names. This feature allows you to add or remove custom filters to/from the field filter chain, enhancing control over the serialization process.

#### How FieldFilters Work:
- **Filter Chain**: Each `FieldFilter` in the chain is applied to a reflected field during the serialization process. The filter determines whether a field should be included in the JSON output based on its characteristics.
- **Custom Implementation**: You can implement your own `FieldFilter` to apply specific exclusion criteria. A field that matches the criteria set in the filter will be excluded when the filter returns `true`.

#### Field Characteristics:
Filters can be designed to recognize and act upon various field characteristics, such as:
- `transient`
- `final`
- `volatile`
- `public`
- `protected`
- `private`

This allows for highly granular control over which fields are serialized, based on their modifiers or access levels.

#### Example Filters:
- **EnumFieldFilter**: This built-in filter can be used as a reference for implementing filters that exclude fields based on their type, such as filtering out enum fields.
- **StaticFieldFilter**: Another example that excludes all static fields from being serialized.

#### Implementing a Custom FieldFilter:
Here's how you might define and add a custom `FieldFilter`:

```java
public class MyCustomFieldFilter implements FieldFilter {
    @Override
    public boolean shouldExclude(Field field) {
        // Exclude fields that are final and volatile
        int modifiers = field.getModifiers();
        return Modifier.isFinal(modifiers) && Modifier.isVolatile(modifiers);
    }
}

// Add the custom filter to json-io
new WriteOptionsBuilder().addFieldFilter("final-volatile", new MyCustomFieldFilter());
```
This filter will exclude fields that are final volatile. When adding the filter, a name is required.  This provides an
easy way to identify the filter if you need to remove it.

#### Benefits:
- **Enhanced Flexibility**: Allows developers to tailor the serialization process to specific requirements, excluding fields based on a wide range of attributes.
- **Increased Security**: Enables the exclusion of sensitive fields, such as those marked as private or transient, from the serialization process.
  By utilizing FieldFilters, developers gain a powerful tool to customize the serialization behavior of json-io, ensuring that only relevant and appropriate data is included in the JSON output.
>#### `WriteOptionsBuilder` addFieldFilter(`String filterName, FieldFilter filter`)
>- [ ] Add a named `FielFilter` to the field filter chain.
>#### `WriteOptionsBuilder` removeFieldFilter(`String filterName`)
>- [ ] Remove a named `FieldFilter` from the field filter chain.

### Method Filters

`json-io` allows the customization of serialization behavior through the use of "Method Filters." These filters provide control over which methods are used during serialization, particularly useful for excluding methods that may not be suitable for direct data extraction.

#### How Method Filters Work:
- **Filter Chain**: Method Filters are added to a method filter chain within `json-io`. Each filter in the chain has the opportunity to inspect a reflected method.
- **Exclusion Logic**: If a filter returns `true` for a method, that method is excluded from being used to access data. Instead, `json-io` will revert to direct field access techniques. This is particularly beneficial if a method, such as a getter, triggers undesirable side effects.

#### Criteria for Filtering:
Filters can be applied based on any aspect of the method's signature or behavior, including:
- Method name
- Owning class
- Visibility (public, private, protected)
- Whether the method is static or non-static

#### Example Usage:
Here's how you might implement and add a `MethodFilter` to exclude all static methods from being used in serialization:

```java
public class StaticMethodFilter implements MethodFilter {
   @Override
   public boolean shouldExclude(Method method) {
      // Exclude static methods
      return Modifier.isStatic(method.getModifiers());
   }
}

// Add the method filter to json-io
new WriteOptionsBuilder().addMethodFilter("static", new StaticMethodFilter());
```
This filter excludes all static methods, forcing json-io to use instance fields or non-static methods for serialization.

#### Benefits:
- **Control Over Serialization**: Provides granular control over the serialization process, allowing exclusion of methods based on specific criteria.
- **Avoidance of Side Effects**: Prevents the use of methods that might modify the state or trigger behaviors unsuitable during serialization.
- **Flexibility**: Adapts serialization to the specific needs and constraints of your application, ensuring data is accessed in the most appropriate manner.

By using Method Filters, developers can fine-tune how json-io accesses data during serialization, enhancing the reliability and predictability of the output JSON.

>#### `WriteOptionsBuilder` addMethodFilter(`String filterName, MethodFilter filter`)
>- [ ] Add a named `MethodFilter` filter to the method filter chain. Write a subclass of `MethodFilter` and add it to
   the `WriteOptionsBuilder` using this method, or use the `WriteOptionsBuilder.addPermanent*()` APIs to install it as
   default in all created `WriteOptions.`
>#### `WriteOptionsBuilder` addNamedMethodFilter(`String filterName, Class name, String methodName`)
>- [ ] Add a `NamedMethodFilter` filter to the method filter chain.  You supply the Class name and the String name
   of the accessor (getter), and it will create a NamedMethodFilter for you, and add it to the method filter list. Any accessor
   method (getter) matching this, will not be used and instead direct field level access will be used to obtain the value
   from the field (reflection, etc.) when writing JSON.
>#### `WriteOptionsBuilder` removeMethodFilter(`String filterName, MethodFilter filter`)
>- [ ] Remove a named `MethodFilter` to the field filter chain.

### Method Accessor

In `json-io`, "Method Accessors" are used to define how properties are accessed during serialization, typically
through getter methods. By default, `json-io` recognizes standard "get" and "is" prefixes for method accessors.
However, you can extend this functionality by defining custom accessor patterns and adding them to the method accessor chain.

#### Customizing Accessors:
- **Flexibility**: You can create custom patterns for method accessors to accommodate different naming conventions or method structures within your classes.
- **Method Accessor Chain**: Once defined, your custom accessors are added to a chain. During serialization, `json-io` consults this chain to determine the correct methods to use for accessing field values.

#### Example Usage:
Suppose you have methods in your classes that use a prefix other than "get" or "is" for getters. You can define a custom accessor to handle these:

```java
public class MyCustomAccessor implements MethodAccessor {
   @Override
   public boolean isAccessor(Method method) {
      // Check if the method name starts with 'fetch' and it returns a value
      return method.getName().startsWith("fetch") && method.getParameterTypes().length == 0;
   }

   @Override
   public String getFieldNameFromAccessor(Method method) {
      // Convert method name from 'fetchFieldName' to 'fieldName'
      return method.getName().substring(5, 6).toLowerCase() + method.getName().substring(6);
   }
}

// Add the custom accessor to json-io
new WriteOptionsBuilder().addMethodAccessor(new MyCustomAccessor());
```
This example shows how to set up a custom accessor for methods that start with "fetch," adapting json-io to use these
methods as getters during serialization.

#### Benefits:

- **Enhanced Compatibility**: Allows json-io to handle a wider range of getter conventions, making it more adaptable
  to different coding styles.
- **Customization**: Enables precise control over which methods are used to access properties, ensuring that the
  serialized JSON matches specific requirements.


By leveraging custom method accessors, developers can significantly increase the adaptability and accuracy of JSON serialization in json-io, ensuring that it aligns perfectly with the application's data access patterns.

>#### `WriteOptionsBuilder` addAccessorFactory(`AccessorFactory accessorFactory`)
>- [ ] Add a method accessor pattern to the method accessor chain.

### Date Formatting for `java.util.Date` and `java.sql.Date`

In `json-io`, you can customize the serialization format for `java.util.Date` and `java.sql.Date` fields. By default,
these date types are serialized into JSON as numeric timestamps (long format), which are compact and efficient for
processing. However, this may not always be suitable depending on the application's requirements.

#### Configuring Date Formats:
- **Default Numeric Format**: The default serialization uses the numeric representation of the date (milliseconds
  since the Unix epoch), which is fast to serialize and deserialize but might not be human-readable.
- **Custom String Formats**: If a more readable format is required, you can specify a custom date format using any of
  the standard JDK date formatting options. For example, you can use formats like `"yyyy-MM-dd'T'HH:mm:ss"` to get
  an ISO 8601 compliant representation.

#### Example Usage:
To set a custom date format in `json-io`, you can use the following approach:

```java
WriteOptions options = new WriteOptionsBuilder()
        .dateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .build();
String json = JsonIo.toJson(yourDateObject, options);
```
This configuration will serialize date fields in the specified ISO date time format, making the JSON output more human-readable.

#### Convenience Methods:
- **ISO Date Formats**: json-io provides convenience methods to easily set short and long ISO date formats, simplifying the process of configuring common date representations.
#### Benefits:
- **Flexibility**: Allows the serialization format to be tailored to the needs of different applications, improving interoperability and readability.
- **Standard Compliance**: By using ISO formats or other standard date formats, the serialized JSON can be more easily consumed by various systems and services.
  Customizing date formats ensures that json-io outputs date information in a way that best fits the application's data handling and presentation requirements.

>#### `boolean` isLongDateFormat()
>- [ ] Returns `true` if `java.util.Date` and `java.sql.Date` are being written in `long` (numeric) format.

>#### `WriteOptionsBuilder` isoDateFormat()
>- [ ] Changes the date-time format to the ISO date format: "yyyy-MM-ddThh:mm:ss.SSSZ".  If millis are 0, the fractional portion is omitted.
>#### `WriteOptionsBuilder` longDateFormat()
>- [ ] Changes the `java.util.Date` and `java.sql.Date` format output to a `long,` the number of seconds since Jan 1, 1970 at midnight. For speed, the default format is `long.`Returns `WriteOptionsBuilder` for chained access.

### Non-Referenceable Classes (Opposite of Instance Folding)

In `json-io`, small immutable classes are often treated as primitives, meaning there is no need to use `@id`/`@ref`
mechanisms typically required for object referencing. This approach is automatically applied to all primitives,
primitive wrappers, `BigInteger`, `BigDecimal`, `Atomic*`, `java.util.Date`, `String`, `Class`, and similar immutable
objects, which are marked as non-referenceable by default.

#### Customizing Non-Referenceable Classes:
- **Adding Classes**: You can extend this default behavior by marking additional immutable classes as non-referenceable, thereby treating them like primitives during serialization.
- **Effect on Object Graph**: This setting can alter the "shape" of your object graph. For example, if a `String` value like "hello" appears multiple times in your data, each occurrence is normally treated as a separate instance. Without `@id`/`@ref`, each instance of "hello" is repeated in the JSON, enhancing readability but potentially increasing the size of the output.

#### Considerations:
- **Instance Uniqueness**: Utilizing `@id` and `@ref` can help maintain instance uniqueness across the object graph. For example, if the string "hello" appears 25 times in your JSON, using `@id` = "n" and `@ref` = "n" for each occurrence can preserve the graph structure, ensuring that all references point to a single, shared instance.
- **Readability vs. Efficiency**: Choosing not to use `@id`/`@ref` for simple objects can make the JSON more readable and concise, as it avoids the overhead of tracking object identities. This is typically suitable for scenarios where object identity continuity is not critical.

#### Example Usage:
To mark additional classes as non-referenceable in `json-io`, you might configure your serialization settings like this:

```java
new WriteOptionsBuilder().addNonReferenceable(MyImmutableClass.class);
```

This configuration treats instances of MyImmutableClass as primitives, not using @id/@ref for them, simplifying the
JSON output while still maintaining a clear and accurate representation of the data.

#### Benefits:
- **Simplicity**: Treating simple, immutable objects as primitives simplifies the JSON output.
- **Performance**: Reduces the complexity of the serialization process by avoiding unnecessary references.
- **Customization**: Allows developers to tailor the serialization behavior to match the needs of their application,
  balancing between accuracy of data representation and simplicity of output.

By carefully selecting which classes are marked as non-referenceable, developers can optimize the serialization process in json-io to suit their specific requirements for data integrity and readability.

>#### `boolean` isNonReferenceableClass( `Class` )
>- [ ] Checks if a class is non-referenceable. Returns `true` if the passed in class is considered a non-referenceable class.

>#### `WriteOptionsBuilder` addNonReferenceableClass( `Class` )
>- [ ] Adds a class to be considered "non-referenceable."

---
## Application Scoped Options (Full Lifecycle of JVM)

`json-io` allows the configuration of application-scoped options, which are settings that persist for the entire
lifecycle of the JVM. These settings ensure that all instances of `WriteOptions` are automatically configured with
the specified options from the startup of your application or service until its shutdown.

### Understanding Application Scoped Options:
- **Scope**: These options are set at the application level and affect every `WriteOptions` instance created during the
  JVM's lifecycle. This eliminates the need to repeatedly configure these settings for each instance.
- **Persistence**: The settings are maintained in static memory throughout the JVM session, meaning they do not modify
  any files on disk but are retained across all operations during the session.
- **Lifecycle**: The term "JVM Lifecycle" refers to the period from when the application starts up to when it shuts down.
  All changes to the application-scoped options during this period will affect any new instances of `WriteOptions` created.

### Benefits:
- **Consistency**: Ensures a consistent configuration across all serialization operations without manual reconfiguration
  for each instance.
- **Efficiency**: Reduces the overhead of repeatedly setting options for each new `WriteOptions` instance, simplifying
  code and reducing the potential for configuration errors.
- **Control**: Provides centralized control over the serialization settings, which is especially useful in large
  applications where `WriteOptions` are frequently used.

Application-scoped options provide a powerful mechanism to manage serialization settings globally, enhancing uniformity
and reducing the complexity of managing individual WriteOptions instances throughout an application's runtime.

### addPermanentAlias
Call this method to add a permanent (JVM lifetime) alias of a class to a shorter, name.  All `WriteOptions`
will automatically be created with permanent aliases added to them.

>#### WriteOptionsBuilder.addPermanentAlias(`Class<?> clazz, String alias`)

### Remove Permanent Alias Type Names Matching

The `removePermanentAliasTypeNamesMatching` method in `json-io` provides a mechanism to permanently remove alias entries from the base `WriteOptionsBuilder`. This ensures that all new instances of `WriteOptions` created by `WriteOptionsBuilder` will not include these aliases for the lifetime of the JVM. This feature is particularly useful for dynamically adjusting the serialization behavior based on evolving application requirements.

#### Functionality:
- **Alias Removal**: This method removes substitution pairings, ensuring that written JSON will use the fully qualified class names instead of shorter, aliased names. It is effective for permanently un-aliasing classes that were previously aliased.
- **Wildcard Pattern Matching**: The API supports wildcard patterns containing `*`, `?`, and regular characters, allowing for flexible specification of which class names should have their aliases removed.

#### Usage:
To remove aliases using patterns, you might use the method like this:

```java
// Example of removing aliases that match a pattern

WriteOptionsBuilder.removePermanentAliasTypeNamesMatching("com.mycompany.*");
```
In this example, all aliases for classes within the com.mycompany package are removed from future WriteOptions instances.

#### Alternative Configuration:
Instead of programmatically removing aliases, you can manage aliases through a configuration file:

- **Aliases File**: You can place an [aliases.txt](/src/main/resources/config/aliases.txt) file in the class path with
  your preferred aliases. json-io includes a comprehensive list of default aliases, but you can override these by
  providing your own file.

```
# Example content of aliases.txt
java.util.ArrayList=ArrayList
com.mycompany.MyClass=MyAlias
```

This file-based approach allows for static configuration of aliases, which might be easier to manage depending on your
deployment and development processes.

#### Benefits:
- **Flexibility and Control**: Offers the ability to fine-tune which aliases are used in the serialization process,
  providing greater control over how data is represented in JSON.
- **Adaptability**: Facilitates the adaptation of serialization strategies without requiring code changes, especially
  useful in environments where classes or packages are dynamically loaded or updated.

By using the `removePermanentAliasTypeNamesMatching()` method, developers can ensure the 'write' side never gets ahead of
the 'read' side.

>#### WriteOptionsBuilder.removePermanentAliasTypeNamesMatching(`String classNamePattern`)

### addPermanentNotExportedField
Call this method to add a permanent (JVM lifetime) excluded (not exported) field name of class.  All `WriteOptions` will
automatically be created with the named field on the not-exported list.

>#### WriteOptionsBuilder.addPermanentNotExportedField(`Class<?> clazz, String fieldName`)

### addPermanentNonRef

Call this method to add a permanent (JVM lifetime) class that should not be treated as referencable
when being written out to JSON.  This means it will never have an @id nor @ref.  This feature is
useful for small, immutable classes.
>#### WriteOptionsBuilder.addPermanentNonRef(`Class<?> clazz`)

### addPermanentNotCustomWrittenClass

Register a class to be excluded from custom JSON serialization for the lifetime of the JVM.

This method prevents the specified class from being serialized by any custom writer, even if it 
inherits from a class that would normally use custom serialization. Once registered, this exclusion
persists until the JVM terminates.
>#### WriteOptionsBuilder.addPermanentNotCustomWrittenClass(`Class<?> clazz`)

### addPermanentWriter

Call this method to add a permanent (JVM lifetime) custom JSON writer to `json-io.`  It will associate the
`clazz` to the writer you pass in.  The writers are found with `isAssignableFrom().` If this is too broad, causing too
many classes to be associated to the custom writer, you can indicate that `json-io` should not use a custom write for a
particular class, by calling the `addNotCustomWrittenClass()` method.

>#### WriteOptionsBuilder.addPermanentWriter(`Class<?> clazz, JsonWriter.JsonClassWriter writer`)

### Add Permanent Non-Standard Getter

The `addPermanentNonStandardGetter` method in `json-io` allows you to define and add a permanent getter method for properties in Java objects where the method does not adhere to the standard getter naming conventions. This is particularly useful for ensuring compatibility and functionality across Java versions, especially given the restrictions in Java 17 and later on accessing private member variables reflectively.

#### Purpose:
- **Non-Standard Naming Conventions**: Some classes, like `java.time.Instant`, may use getter methods with unique names that do not follow the traditional `getPropertyName()` format. For instance, `getEpochSecond()` for accessing the `second` field.
- **Permanent Accessors**: By adding a non-standard getter, you ensure that `json-io` can reliably access these properties across the JVM's lifecycle, without needing to conform to standard naming conventions.

#### Example Usage:
To add a non-standard getter for the `second` field of `java.time.Instant`, which uses the `getEpochSecond()` method, you would configure `json-io` as follows:

```java
// Example of setting a permanent non-standard getter
WriteOptionsBuilder.addPermanentNonStandardGetter(Instant.class, "second", "getEpochSecond");
```

In this setup, `json-io` will use `getEpochSecond` instead of the expected `getSecond` to serialize the second field of
an `Instant` object.

#### Preconfigured Accessors:
- **JDK Classes**: It's worth noting that many non-standard accessors for JDK classes are already configured by default
  in `json-io,` including the one for `java.time.Instant.` This preconfiguration simplifies integration and reduces the
  need for additional setup in common use cases.
#### Benefits:
- **Flexibility and Compatibility**: This feature provides flexibility in handling classes with non-standard getter
  methods and ensures compatibility with Java's encapsulation policies post-Java 16.
- **Streamlined Integration**: By preconfiguring getters for common classes and allowing custom configurations,
  `json-io` facilitates streamlined integration and usage, even with complex object models.

The `addPermanentNonStandardGetter()` API enhances the robustness of JSON serialization in json-io, accommodating advanced use cases and modern Java functionalities.

>#### WriteOptionsBuilder.addPermanentNonStandardGetter(`Class<?> clazz, String field, String methodName`)

### addPermanentFieldFilter
>#### WriteOptionsBuilder.addPermanentFieldFilter(`String name, FieldFilter fieldFilter`)

Add a FieldFilter that is JVM lifecycle scoped. All WriteOptions instance will contain this filter. A FieldFilter is
used to filter (eliminate) a particular field from being serialized.  This allows you to filter a field by a field
characteristic, for example, you can eliminate a particular type of field that occurs on Enums. See EnumFieldFilter for
an example.

### addPermanentMethodFilter
Add a `MethodFilter` that is JVM lifecycle scoped. All `WriteOptions` instances will contain this filter. A `MethodFilter`
is used to filter (eliminate) a method accessor (getter) from being called. For example, a `getFoo()` method which one
might think returns the `Foo` member variable, but instead performs undesired extra work before the value is accessed.
In this case, tell `json-io` to eliminate the `getFoo()` accessor and then `json-io` will use techniques to attempt
reading the field directly.

The `MethodFilter` is passed the `Class` and the method name and if it returns 'true' for that pairing, the 'getter' method
will be not be used.  This reading/accessing of fields happens when `json-io` is accessing the Java objects to create
JSON content.

The String `name` is a unique name you give the filter.  It must be unique amongst the method filters.  The `MethodFilter`
is a derived implementation to be added that filters methods a new, particular way.
>#### WriteOptionsBuilder.addPermanentMethodFilter(`String name, MethodFilter methodFilter`)

### addPermanentMethodNameFilter
Works like the `addPermanentMethodFilter()` with one simple difference.  Whereas one must sublass `MethodFilter,` with
this API you pass it the `Class` and method name to filter, and it will create a `NamedMethodFilter` for you. No need
to create a new `MethodFilter` subclass.

To call the API, pass a unique name that is unique across all MethodFilters, the Class on which the accessor (getter)
resides, and the name of the method.
>#### WriteOptionsBuilder.addPermanentNamedMethodFilter(`String name, Class<?> clazz, String methodName`)

### addPermanentAccessorFactory
Add an `AccessorFactory` that is JVM lifecycle scoped.  All `WriteOptions` instances will contain this `AccessorFactory.`
It is the job of an `AccessorFactory` to provide a possible method name for a particular field. `json-io` ships with
a `GetMethodAccessorFactory` and an `IsMethodAccessFactory.` These produce a possible method name for a given field.
When a field on a Java class is being accessed (read), and it cannot be obtained directly, then all `AccessoryFactory`
instances will be consulted until an API can be used to read the field.
>#### WriteOptionsBuilder.addPermanentAccessorFactory(`String name, AccessorFactory factory`)