## Controlling the input JSON using `ReadOptions`
To configure read options for use with `JsonIo.toJava()`, you need to set up a `ReadOptions` instance using the
`ReadOptionsBuilder`. Here's how you can create and customize a new `ReadOptions` instance:
```java           
ReadOptions readOptions = new ReadOptionsBuilder()
.feature1()
.feature2(args)
.build();
JsonIo.toJava(json, readOptions).asClass(Employee.class);

// Use a TypeHolder to specify the return type
JsonIo.toJava(json, readOptions).asType(new TypeHolder<String, List<Long>>(){});
```
**Detailed Steps:**

_Initialization:_ Start by creating a `ReadOptionsBuilder` instance.

_Feature Toggle:_ Enable or disable features by calling the corresponding methods on the builder. Each method corresponds
to a specific feature you might want to enable (like `.feature1()`) or configure (like `.feature2(args)`).

_Finalization:_ Call the `.build()` method to finalize and create a read-only `ReadOptions` instance. Once built,
`ReadOptions` are immutable and can be safely reused across multiple read operations.

**Additional Tips:**

_Documentation:_ For more detailed information on the available features and methods, refer to the Javadoc documentation
for `ReadOptionsBuilder`.
for `ReadOptionsBuilder`.

_Multiple Instances:_ You can create multiple `ReadOptions` instances for different scenarios. Each instance is stateless
once constructed.

_Copying Instances:_ If you need to create a new `ReadOptions` instance based on an existing one, use `new
ReadOptionsBuilder(readOptionsToCopyFrom)` to start with the copied settings.

By following these guidelines, you can effectively manage how JSON data is processed and ensure that your application handles data consistently across various contexts.



### Constructors
Create new instances of`ReadOptions`.
>#### new ReadOptionsBuilder().feature1().feature2(args).build()
>- [ ] Start with default options and add in feature1 and feature2(requires argument) and make read-only.
>#### new ReadOptionsBuilder(`ReadOptions other`)
>- [ ] Copy all settings from the passed in 'other'`ReadOptions`.

### Class Loader
The`ClassLoader`in the`ReadOptons`is used by `json-io` to turn `String` class names into`Class`instances.
>#### `ClassLoader` getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names into Class instances.

>#### `ReadOptionsBuilder`classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names when reading JSON.

### Handling Unknown Types
When parsing JSON, you might encounter class names that do not exist in your JVM. This feature allows you to control
the behavior in such cases.

**Default Behavior:** By default, if the parser encounters an unknown class name, it will fail, and a `JsonIoException` is
thrown. This ensures that all types used in your JSON must be known and available.

**Handling with LinkedHashMap:** If you prefer not to have the parsing fail on unknown types, you can disable this default
behavior. Instead, the parser will create a `LinkedHashMap` to store the content of the JSON object. This approach is
beneficial because a `LinkedHashMap` can dynamically store any field encountered in the JSON, regardless of the class structure.

**Custom Class Handling:** Alternatively, you can specify your own class to be used instead of `LinkedHashMap` for storing
the JSON data. This custom class should ideally be a type of Map to ensure that it can handle any arbitrary set of JSON
keys and values. Using maps for unknown types provides flexibility, allowing JSON data to be parsed into a usable format even when the exact class structure is not predefined in the JVM.
>#### `boolean` isFailOnUnknownType()
>- [ ] Returns`true` if an 'unknownTypeClass' is set,`false`if it is not set. The default setting is `true.`
>####  `Class` getUnknownTypeClass()
>- [ ] Get the Class used to store content when the indicated class cannot be loaded by the JVM.  Defaults to`null.` When`null`, `LinkedHashMap` is used. It is usually best to use a `Map` derivative, as it will be able to have the various field names encountered to be 'set' into it.

>#### `ReadOptionsBuilder` failOnUnknownType(`boolean fail`)
>- [ ] Set to`true`to indicate that an exception should be thrown if an unknown class type is encountered,`false`otherwise.
   If`fail`is`false,`then the default class used will be`LinkedHashMap` for the particular JSON object encountered. You can
   set this to your own class using the`unknownTypeClass()`feature. The default setting is `true.`
>#### `ReadOptionsBuilder` unknownTypeClass(`Class`)
>- [ ] Set the class to use (defaults to`LinkedHashMap.class`) when a JSON object is encountered and there is no
   corresponding Java class to instantiate to receive the values.

### MaxDepth - Security protection
Set the maximum object nesting level so that no`StackOverflowExceptions`happen.  Instead, you will get a`JsonIoException`letting
you know that the maximum object nesting level has been reached.
> #### `int` getMaxDepth()
>- [ ] Return the max nesting depth for JSON {...}

> #### `ReadOptionsBuilder` maxDepth(`int depth`)
>- [ ] Set the max depth to allow for nested JSON {...}.  Set this to prevent security risk from`StackOverflow`attack
   vectors.

### lruSize - LRU Size [Cache of fields and filters]
Set the maximum number of `Class` to `Field` mappings and `Class` to filter mappings. This will allow infrequently used `Class's`
to drop from the cache - they will be dynamically added back if not in the cache.  Reduces operational memory foot print.
> #### `int` lruSize()
>- [ ] Return the LRU size. Default is 1000.

> #### `ReadOptionsBuilder` lruSize(`int size`)
>- [ ] Set the max LRU cache size

### Floating Point Options

Handling special floating point values and large numbers in JSON can be challenging due to limitations in standard formats and data types.

- **Nan and Infinity**: Although the JSON specification does not natively support `NaN`, `+Infinity`, and `-Infinity`, `json-io` can be configured to recognize and correctly handle these values. This feature is disabled by default because not all external APIs and services support these non-standard JSON values. Enabling this feature allows such values to be correctly written to and read from JSON, ensuring compatibility within your application while possibly limiting interoperability with other systems.


- **Handling Large Numbers**: JSON payloads can sometimes include floating point numbers that exceed the capacity of the standard Java `Double` as defined by IEEE 754. To address this, `json-io` offers several configuration options:
    - _Always use BigDecimal_: Treat all floating point numbers as `BigDecimal`, which can handle arbitrary precision.
    - _Use BigDecimal when needed_: Automatically determine whether to use `BigDecimal` or `Double` based on the size and precision of the number.
    - _Never use BigDecimal_: Stick to using `Double` for all floating point numbers, regardless of size.

These settings provide flexibility in how large or special floating point numbers are handled, allowing for both precision and performance optimization based on your specific application requirements.
> #### `boolean`isAllowNanAndInfinity()
>- [ ] Returns`true`if set to allow serialization of`NaN`and`Infinity`for`doubles`and`floats.`
>#### `ReadOptionsBuilder` allowNanAndInfinity(`boolean allow`)
>- [ ] true will allow`doubles`and`floats`to be output as`NaN`and`INFINITY`,`false`and these values will come across
   as`null.`

>#### `boolean` isFloatingPointDouble()
>- [ ] return `true` if floating point values should always be returned as `Double.`  This is the default.
>#### `ReadOptionsBuilder` floatPointDouble()
>- [ ] Set Floating Point types to be returned as `Double.`  This is the default.

>#### `boolean` isFloatingPointBigDecimal()
>- [ ] return `true` if floating point values should always be returned as `BigDecimal.`
>#### `ReadOptionsBuilder` floatPointBigDecimal()
>- [ ] Set Floating Point types to be returned as `BigDecimal.`

>#### `ReadOptionsBuilder` floatPointBoth()
>- [ ] Set Floating Point types to be returned as either `Double` or `BigDecimal,` depending on precision required to hold the number sourced from JSON.
>#### `boolean` isFloatingPointBoth()
>- [ ] return `true` if floating point values should always be returned dynamically, as `Double` or `BigDecimal,`
   favoring `Double` except when precision would be lost, then `BigDecimal` is returned.

### Integer Options

Handling large integer values in JSON can be problematic, especially when they exceed the range that a standard Java `Long` can accommodate.

`json-io` provides several configuration options to manage how these large integers are handled during parsing:

- **Always use BigInteger**: Configure `json-io` to treat all integer numbers as `BigInteger`, which supports integers of practically unlimited size.
- **Use BigInteger when needed**: Allow `json-io` to decide dynamically whether to use `BigInteger` or `Long` based on the actual size of the integer present in the JSON. This can be particularly useful for optimizing performance without sacrificing the ability to handle large numbers when necessary.
- **Never use BigInteger**: With this setting, all integers are handled as `Long`, which may lead to issues if the JSON contains integers outside the `Long` range.

These settings offer flexibility in dealing with large integers, ensuring that your application can handle both common cases efficiently and rare cases correctly.

>#### `boolean` isIntegerTypeLong()
>- [ ] return `true` if integer values should always be returned as a `Long.`  This is the default.
>#### `ReadOptionsBuilder` integerTypeLong()
>- [ ] Set integer Types to be returned as `Long.` This is the default.

>#### `boolean` isIntegerTypeBigInteger()
>- [ ] return `true` if integer values should always be returned as `BigInteger.`
>#### `ReadOptionsBuilder` integerTypeBigInteger()
>- [ ] Set integer Types to be returned as `BigInteger.`

>#### `boolean` isIntegerTypeBoth()
>- [ ] return `true` if integer values should always be returned dynamically, as `Long` or `BigInteger,` favoring `Long` except when precision would be lost, then `BigInteger` is returned.
>#### `ReadOptionsBuilder` integerTypeBoth()
>- [ ] Set integer types to be returned as either `Long` or `BigInteger,` depending on precision required to hold the
   number sourced from JSON.  If the value is within the range of `Long.MIN_VALUE` to `Long.MAX_VALUE,` then `Long` will be returned
   otherwise a `BigInteger` will be returned.

### Close Stream

The handling of streams after JSON data is read can vary based on the specific needs of your application. `json-io` offers configurable options to either close the stream automatically after reading or keep it open for further use.

- **Automatic Stream Closing**: By default, `json-io` can automatically close the `InputStream` once the JSON has been read. This is useful for single-use stream scenarios where no further data is expected.

- **Keep Stream Open**: In cases where subsequent JSON data might be read from the same stream, such as with NDJSON (Newline Delimited JSON) formats that consist of multiple JSON objects separated by newlines (`{...}\n{...}\n{...}`), you can configure `json-io` to leave the `InputStream` open after each read. This allows for continuous reading without the need to reopen or recreate the stream.

For specific examples and implementation details, refer to the example at the beginning of the user guide.
>#### `boolean` isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after read (the default), or `false`to leave stream open after reading from it.

>#### `ReadOptionsBuilder` closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting,`true`to turn on,`false`will turn off. The default setting is`false.`

### Aliasing - Shorten Class Names in @type

Aliasing simplifies JSON output by converting fully qualified Java class names into shorter, simpler class names. For example, `java.util.ArrayList` can be aliased to just `ArrayList`, reducing the JSON content size and enhancing readability.

- **Default Aliases**: By default, `json-io` includes aliases for many common JDK classes, which helps to minimize the JSON output size automatically.

- **Adding Custom Aliases**: You can add custom aliases for your classes within the application. For instance, adding an alias from `com.mycompany.Foo` to `Foo.`

- **Scope of Aliases**: The aliases added affect only the instance of `ReadOptions` created from a `ReadOptionsBuilder`. To apply aliases across all instances throughout the JVM's lifecycle, refer to the [application-scoped options](/user-guide-readOptions.md#application-scoped-options-full-lifecycle-of-jvm) section.

- **External Alias Configuration**: Alternatively, you can manage aliases externally by creating an [aliases.txt](/src/main/resources/config/aliases.txt) file and placing it in the class path. `json-io` includes a comprehensive list of default aliases, but you can override this by providing your own file.

**Note**: When adding an alias, `json-io` not only adds the alias for the class itself but also for its 1D, 2D, and 3D array representations, ensuring that all forms of the class are consistently aliased in the JSON output. Aliasing `Foo` also generates `Foo[]`, `Foo[][]`, and `Foo[][][]` aliases, ensuring consistency across all usages of the class in JSON
>#### `String` getTypeNameAlias(`String typeName`)
>- [ ] Pass in a String class name, and it will return the alias for it, or it will return the same string you passed in (non-aliased).

>#### `Map<String, String>` aliasTypeNames()
>- [ ] Returns `Map<String, String>` containing String class names to alias names. Use this API to see default aliases.
>#### `ReadOptionsBuilder` aliasTypeNames(`Map<String, String> aliasTypeNames`)
>- [ ] Puts the`Map`containing String class names to alias names. The passed in`Map`will be `putAll()` copied overwriting any
   entries that match values in the passed in Map. New entries in the Map are added.
>#### `ReadOptionsBuilder` aliasTypeName(`String typeName, String alias`)
>- [ ] Sets the alias for a given class name.
>#### `ReadOptionsBuilder` aliasTypeName(`Class, String alias`)
>- [ ] Sets the alias for a given class.
>#### `ReadOptionsBuilder` removeAliasTypeNamesMatching(`String typeNamePattern`)
>- [ ] Remove alias entries from this `ReadOptionsBuilder` instance where the Java fully qualified string class name
   matches the passed in wildcard pattern. The `typeNamePattern` matches using a wild-card pattern, where * matches
   anything and ? matches one character. As many * or ? can be used as needed.

### JSON Object Conversion Mode

`json-io` provides two modes for processing JSON data, controlled by the `returnAsJsonObjects()` and `returnAsJavaObjects()` methods:

- **returnAsJsonObjects()**: When this mode is enabled, parsed JSON is returned as a graph of Maps and primitive types (JsonObjects), not as fully instantiated Java objects. This is useful when:
    - You want to examine or modify the JSON structure before final deserialization
    - The classes referenced in the JSON are not available in your JVM
    - You need to perform operations on the raw JSON data structure

- **returnAsJavaObjects()**: This is the default mode, where JSON is fully converted to Java objects of the specified target types. This provides a direct mapping between JSON and your domain model.

Example with JsonObjects mode:
```java
ReadOptions options = new ReadOptionsBuilder().returnAsJsonObjects().build();
Map<String, Object> jsonMap = JsonIo.toJava(json, options).asClass(Map.class);
// Modify the map if needed
jsonMap.put("name", "Updated Name");
// Later convert to Java objects if desired
Person person = JsonIo.toJava(jsonMap, new ReadOptionsBuilder().build()).asClass(Person.class);
```

>#### `boolean` isReturningJsonObjects()
>- [ ] Returns `true` if parser is configured to return Map of Maps (JsonObjects) instead of instantiated Java objects.

>#### `ReadOptionsBuilder` returnAsJsonObjects()
>- [ ] Configure parser to return Map of Maps (JsonObjects) instead of instantiated Java objects.

>#### `boolean` isReturningJavaObjects()
>- [ ] Returns `true` if parser is configured to return fully instantiated Java objects (default behavior).

>#### `ReadOptionsBuilder` returnAsJavaObjects()
>- [ ] Configure parser to return fully instantiated Java objects (default behavior).

### Class Coercion

Class coercion is a powerful feature in `json-io` that allows you to transform specific Java classes into alternative
implementations during the JSON parsing process. This can be particularly useful for converting derivative classes into
more generic or usable forms when they are loaded into memory.

- **Example Conversion**: For instance, `json-io` can convert `java.util.Collections$UnmodifiableRandomAccessList` into
  a more flexible `ArrayList`. This allows for mutable operations post-deserialization that are not possible with the original unmodifiable class.


- **Handling Collection Variants**: The library already includes substitutions for many common JDK collection variants,
  such as singletons, synchronized collections, and various unmodifiable collections. These pre-configured substitutions
  ensure that collection types are coerced into their most usable forms without additional configuration.


- **Custom Coercions**: You can extend this list by adding custom class coercions as needed. This feature enables you to
  specify alternative classes for specific types that might not be as straightforward or as flexible as required by your
  application's logic.

By leveraging class coercion, you can ensure that the objects resulting from JSON deserialization fit seamlessly into the operational context of your application, enhancing both functionality and developer convenience.
>#### `boolean` isClassCoerced(`String className`)
>- [ ] Return`true`if the passed in Class name is being coerced to another type, `false`otherwise.
>#### `Class` getCoercedClass(`Class`)
>- [ ] Fetch the coerced class for the passed in fully qualified class name.

>#### `ReadOptionsBuilder` coerceClass(`String sourceClassName, Class destClass`)
>- [ ] Coerce a class from one type (named in the JSON) to another type.  For example, converting
   `java.util.Collections$SingletonSet`to a`LinkedHashSet.class.`

### Missing Field Handler

The Missing Field Handler feature in `json-io` addresses scenarios where JSON contains fields that do not exist on the
target Java object. This feature helps manage how such discrepancies are handled during the deserialization process.

- **Functionality**: If a field in the JSON data does not correspond to any field on the Java object, rather than
  ignoring it or causing an error, `json-io` allows you to set up a handler that is specifically invoked for these missing fields.

- **Notification**: The designated handler, configured through the `JsonReader.MissingFieldHandler` interface, is
  notified of all missing fields once deserialization completes. This allows for custom logic to be executed in response,
  such as logging missing information or taking corrective measures.

- **Further Details**: For more comprehensive information on implementing and utilizing the `JsonReader.MissingFieldHandler`,
  refer to its Javadoc documentation.

This feature is particularly useful in maintaining robustness and flexibility in your application's data handling strategy, ensuring that unexpected data does not lead to unhandled exceptions or data integrity issues.

>#### `JsonReader.MissingFieldHandler` getMissingFieldHandler()
>- [ ]  Fetch the method that will be called when a field in the JSON is read in, yet there is no corresponding
   field on the destination object to receive the value.

>#### `ReadOptionsBuilder` missingFieldHandler(`JsonReader.MissingFieldHandler missingFieldHandler`)
>- [ ] Pass the`missing field handler`to be called when a field in the JSON is read in, yet there is no corresponding field on the
   destination object to receive the value.

### Class Factory

In cases where `json-io` encounters difficulties instantiating a class or reading its data correctly through default
processes, you can utilize the `ClassFactory` feature for a tailored solution.

- **Purpose of Class Factory**: A `ClassFactory` is designed to create instances of a specific class and populate them
  with data directly from the JSON being parsed. This approach is particularly useful for classes that have non-standard
  constructors or initialization patterns that `json-io` does not handle natively.

- **How It Works**: When a JSON object is encountered that maps to a class associated with a `ClassFactory`, the factory
  is invoked with the JSON data. It is the responsibility of the `ClassFactory` to construct an instance of the class,
  ensuring that all necessary data from the JSON is correctly translated and loaded into the new object.

- **Advantages Over Custom Readers**: Using a `ClassFactory` is generally preferred over a `CustomReader` for class
  instantiation because it integrates more seamlessly with the standard deserialization process, maintaining consistency
  and reducing the potential for errors or data mismatches.

This feature allows for high customization and flexibility in how classes are instantiated from JSON data, enabling `json-io` to be effectively used with a wider range of Java classes, including those with complex construction requirements.

>#### `Map<Class, JsonReader.ClassFactory>` getClassFactories()
>- [ ] Fetch`Map`of all Class's associated to`JsonReader.ClassFactory's.`
>#### `JsonReader.ClassFactory` getClassFactory(`Class`)
>- [ ] Get the`ClassFactory`associated to the passed in class.

>#### `ReadOptionsBuilder` setClassFactories(`Map<Class, ? extends JsonReader.ClassFactory> factories`)
>- [ ] Associate multiple ClassFactory instances to Classes that needs help being constructed and read in. The
   `Map`of entries associate`Class`to`ClassFactory.` The`ClassFactory`class knows how to instantiate and load
   the class associated to it.
>#### `ReadOptionsBuilder` addClassFactory(`Class clazz, JsonReader.ClassFactory factory`)
>- [ ] Associate a`ClassFactory`to a`Class`that needs help being constructed and read in. If you use this method
   more than once for the same class, the last`ClassFactory`associated to the class will overwrite any prior associations.

### Custom Readers

Custom Readers in `json-io` offer a specialized mechanism for handling JSON deserialization when the default process
does not meet specific needs. They allow for detailed customization of the JSON-to-object conversion process.  This approach
will likely be deprecated in a future release as using a `ClassFactory` is a superset capability (create AND load).

- **Purpose of Custom Readers**: Custom Readers are designed to manage the deserialization of JSON content for specific
  classes, particularly when standard methods fail or require customization. They can also be applied to derived classes,
  ensuring consistent handling across a class hierarchy.

- **Recommendation to Use Class Factory First**: Before implementing a Custom Reader, it's advisable to consider using
  a `ClassFactory`. This approach typically offers more straightforward integration with `json-io`'s deserialization
  process, allowing for both instance creation and data loading simultaneously, thereby reducing complexity and potential issues.

- **Handling Unwanted Associations**: Occasionally, a Custom Reader might inadvertently apply to classes for which it
  was not intended. To prevent this, you can explicitly exclude certain classes from being processed by a Custom Reader by
  adding them to the Not-Custom-Reader list. This ensures that only the intended classes are affected by the custom deserialization logic.

Custom Readers provide a powerful tool for extending and customizing the behavior of `json-io`, making it possible to tailor the deserialization process to fit exact requirements, particularly for complex or non-standard class structures.

>#### `Map<Class, JsonReader.JsonClassReader>` getCustomReaderClasses()
>- [ ] Fetch`Map`of`Class`to custom`JsonClassReader's` used to read JSON when the class is encountered during
   serialization to JSON.  This is the entire`Map`of Custom Readers.
>#### `boolean` isCustomReaderClass(`Class`)
>- [ ] Return`true`if there is a custom reader class associated to the passed in class, `false` otherwise.

>#### `ReadOptionsBuilder` setCustomReaderClasses(`Map<? extends Class>, ? extends JsonReader.JsonClassReader> customReaderClasses`)
>- [ ]  Set all custom readers at once.  Pass in a`Map`of`Class`to`JsonReader.JsonClassReader.` Set the passed
   in`Map`as the established`Map`of custom readers to be used when reading JSON. Using this method  more than once, will
   set the custom readers to only the values from the`Map`in the last call made.
>#### `ReadOptionsBuilder` addCustomReaderClass(`Class, JsonReader.JsonClassReader customReader`)
> - [ ] Add a single association of a class to a custom reader.  If you add another associated reader to the same class, the last one added will overwrite the prior association.

### Not Custom Reader

In `json-io`, the Not Custom Reader list serves as a mechanism to exclude specific classes from being processed by a
Custom Reader. This feature is particularly useful in managing class inheritance issues where a Custom Reader might
unintentionally apply to subclasses.

- **Purpose**: This list allows you to specify classes that should not be associated with any Custom Reader. By adding
  a class to this list, you ensure that it bypasses the custom deserialization process, even if a Custom Reader exists
  that could potentially apply due to class inheritance.

- **Use Case**: It's often used to maintain the default deserialization behavior for certain classes within a hierarchy
  that otherwise might inherit a Custom Reader's behavior, which could lead to incorrect or unwanted data handling.

- **Implementation**: Simply add the classes you want to exclude to the Not Custom Reader list. This straightforward
  approach helps maintain clarity and control over which classes are affected by custom deserialization logic, ensuring
  that only the intended classes are modified.

This functionality is essential for maintaining precise control over the deserialization process in complex inheritance
scenarios, preventing unwanted side effects from broadly applied Custom Readers.

>#### `boolean` isNotCustomReaderClass(`Class`)
>- [ ]  Pass in Class to check if it's on the not-customized list.  Classes are added to this list when a class is being
   picked up through inheritance, and you don't want it to have a custom reader associated to it.
>#### `Set<Class<?>>` getNotCustomReadClasses()
>- [ ] Fetch the`Set`of all Classes on the not-customized list.

>#### `ReadOptionsBuilder` addNotCustomReaderClass(`Class notCustomClass`)
>- [ ] Add a class to the not-customized list - the list of classes that you do not want to be picked up by
   a custom reader (that could happen through inheritance).
>#### `ReadOptionsBuilder` setNotCustomReaderClasses(`Collection<Class> notCustomClasses`)
>- [ ] Initialize the list of classes on the non-customized list.  All prior associations will be dropped and this
   `Collection` will establish the new list.

### Non-Referenceable Classes (Opposite of Instance Folding)

In `json-io`, certain classes are treated as non-referenceable, meaning they do not use `@id`/`@ref` tags in JSON, as they are considered equivalent to primitives due to their immutable nature.

- **Default Non-Referenceable Classes**: By default, all primitive types, primitive wrappers, `BigInteger`, `BigDecimal`, `Atomic*` types, `java.util.Date`, `String`, `Class`, and `ZonedDateTime` are treated as non-referenceable. This setup ensures that instances of these types are always serialized directly into the JSON without references.

- **Customizing Non-Referenceable Classes**: You have the option to designate additional immutable classes as non-referenceable. This can be useful for custom classes that are immutable and do not require identity preservation across different fields in the JSON object.

- **Implications of Non-Referenceability**: One side effect of marking classes as non-referenceable is the alteration of the "shape" of your object graph. For example, multiple fields pointing to the same `String`, `BigDecimal`, or `Integer` instance will not be recognized as the same instance (`==`) in the deserialized object. Each appearance is treated as a separate instance.

- **Handling Common Values**: `json-io` automatically collapses common logical primitive values to maintain consistency. For instance, the string representations of "true", "false", the numbers -1, 0, and 1, and the strings "0" through "9", "on", "off", and other common strings are treated such that multiple occurrences are represented by a single instance in memory, promoting efficiency and reducing memory usage.

This feature enhances the efficiency of serialization and deserialization processes, especially for common and frequently used immutable values, while allowing for customization to suit specific application needs.

>#### `boolean` isNonReferenceableClass(`Class`)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.
>#### `Collection<Class>` getNonReferenceableClasses()
>- [ ] Returns a`Collection`of all non-referenceable classes.

>#### `ReadOptionsBuilder` addNonReferenceableClass(`Class`)
>- [ ] Adds a class to be considered "non-referenceable." Examples are the built-in primitives.

### Add InjectorFactory

The `addInjectorFactory` method in `json-io` allows you to specify a custom, global approach for injecting values into an object. For instance, if you have a specific way of declaring setter methods and want them to be invoked when setting values, this method can accommodate that. Additionally, it can be used to customize value injection for inner classes with special naming conventions.

>#### ReadOptionsBuilder.addInjectorFactory(`InjectorFactory factory`)

### Add FieldFilter

The `addFieldFilter` method in `json-io` allows you to add a custom filter to eliminate fields from consideration.
There are already two built-in `FieldFilters`, one for filtering out static fields and another for filtering out Enum fields.

>#### ReadOptionsBuilder.addFieldFilter(`FieldFilter filter`)
---
## Application Scoped Options (Full Lifecycle of JVM)

`json-io` provides a set of application-scoped options that are effective for the entire lifecycle of the JVM, from start-up to shutdown. These settings allow you to define certain behaviors or configurations globally, so they are automatically applied to all instances of `ReadOptions` created throughout the application.

- **Scope of Application Settings**: Once set, these options persist across the JVM lifecycle, ensuring that every `ReadOptions` instance inherits the same configurations without needing individual setup. This simplifies the management of read options across various parts of your application.

- **Impact on the JVM**: These settings do not alter any files on disk or engage in any operations outside of the JVM's memory. Instead, they modify static memory allocations that influence the behavior of `ReadOptions` instances created during the JVM's operation.

- **Advantages**: By using these permanent settings, you ensure a consistent configuration that is maintained across all operations, reducing redundancy and potential for configuration errors in individual instances.

This feature is particularly useful for large applications or services where maintaining consistent behavior across numerous operations is crucial. It also simplifies setup and maintenance by centralizing configuration changes to a single point in the application lifecycle.


### Add Permanent Class Factory

The `addPermanentClassFactory` method in `json-io` allows you to establish a permanent solution for class instantiation that is reused across the application. This is particularly beneficial for classes that cannot be instantiated through standard constructors due to access restrictions or specialized construction requirements.

- **Purpose**: Use this method to associate a `JsonReader.ClassFactory` that you implement with a specific class. This factory will handle the instantiation of instances of the designated class throughout the application's lifecycle.

- **Implementation**: Implement the `JsonReader.ClassFactory` interface and define how instances of the target class should be created from the JSON object (represented as a `Map` or `JsonObject`). The factory is responsible for creating the class instance and populating it with values from the JSON data.

- **Usage Scenario**: This method is invaluable when dealing with classes that have private constructors or require complex setup that the default deserialization process cannot handle. By setting up a `ClassFactory`, you ensure that `json-io` can effectively manage these classes whenever they are encountered in JSON data.

- **Application Scope**: Once added, the class factory is applied universally to all subsequent `ReadOptions` instances created within the JVM, ensuring consistent behavior and reducing the need for repeated configuration.

This approach not only facilitates customization of the deserialization process for complex class structures but also enhances the robustness and flexibility of your application by ensuring that all components handle specific class constructions consistently.

>#### ReadOptionsBuilder.addPermanentClassFactory(`Class<?> sourceClass, JsonReader.ClassFactory factory`)

### Add Permanent Coerced Type

The `addPermanentCoercedType` method in `json-io` enables you to define a permanent coercion from one class to another that persists for the lifetime of the JVM. This feature is particularly useful for handling special cases where certain classes, like proxies or wrappers, need to be treated as more standard or manageable types during deserialization.

- **Purpose**: Use this method to permanently map one class type to another within the deserialization process. This is essential for scenarios where default handling of certain classes does not meet the specific needs of your application.

- **Typical Use Cases**: Common examples include converting proxy classes, such as those created by frameworks like Hibernate (e.g., HibernateBags), into regular JVM collections. This allows for more straightforward handling and integration within your application, avoiding complications that might arise from using specialized or proxied objects.

- **Implementation and Effect**: Once set, this coercion will automatically apply whenever the specified class type is encountered in JSON data, converting it to the desired type. The mapping is added globally and affects all instances of `ReadOptions` created throughout the JVM's operation.

- **Advantages**: By establishing a permanent type coercion, you ensure consistent behavior across your application, simplifying the handling of specific class types and enhancing the predictability and reliability of your data processing.

This method is a powerful tool for customizing how classes are deserialized, ensuring that they fit into the operational and data models of your application seamlessly.

>#### ReadOptionsBuilder.addPermanentCoercedType(`Class<?> sourceClass, Class<?> destinationClass`)

### Add Permanent Alias

The `addPermanentAlias` method in `json-io` allows you to establish a permanent alias for a class throughout the lifetime of the JVM. This method is used to map a fully qualified class name to a shorter alias that is easier to manage and more readable in JSON content.

- **Purpose**: This method simplifies JSON serialization and deserialization by substituting long class names with shorter, more manageable aliases. These aliases are used in the `@type` field within the JSON, facilitating a cleaner and more concise representation of object types.

- **Implementation Details**: By default, the `@type` field is added to JSON only when the class type cannot be automatically inferred from the context, such as the root class, field type, array component type, or template argument type. Using this method, you can ensure that a specific class is always represented by its alias, regardless of context.

- **Usage Scenario**: This is particularly useful for reducing JSON size and improving readability when dealing with complex Java class names that are frequently used across your application's JSON communications.

- **Long-term Consistency**: Adding a permanent alias ensures that the aliasing is consistent across all JSON operations within the application from startup to shutdown, without the need to redefine or reassess aliasing in each individual operation or session.

This feature enhances the manageability and clarity of JSON output and input within large applications, especially those that utilize complex object hierarchies or extensive data interchange.

>#### ReadOptionsBuilder.addPermanentAlias(`Class<?> sourceClass, String alias`)

### Remove Permanent Alias Type Names Matching

The `removePermanentAliasTypeNamesMatching` method in `json-io` is designed to permanently remove aliases based on specified wildcard patterns from the "base" `ReadOptionsBuilder`. This ensures that any new instances of `ReadOptions` created from `ReadOptionsBuilders` will not include these aliases for the duration of the JVM's lifetime.

- **Purpose**: This method is used to delete alias entries that you no longer wish to use, affecting how JSON is read and recognized within your application.

- **Caution on Usage**: While it's generally safe to maintain a broad set of aliases within `ReadOptions`, be cautious when removing aliases, especially on the 'read' side, to avoid disrupting data ingestion. Conversely, managing `WriteOptions` should be done more stringently, especially in microservice environments where consistent alias interpretation across services is critical.

- **Operational Implications**: This action modifies the alias configuration globally within the JVM, ensuring that all future `ReadOptions` instances conform to the new alias settings. It's recommended to expand your READ alias support before enhancing WRITE alias support unless you are updating all related services simultaneously to accommodate new aliases.

- **Wildcard Pattern Matching**: The API accepts wildcard patterns (`*`, `?`, and regular characters) to identify which aliases to remove, allowing for flexible and powerful pattern matching against fully qualified class names stored in its cache.

- **Alternative Method**: As an alternative to using this API programmatically, you can manage aliases by placing a custom [aliases.txt](/src/main/resources/config/aliases.txt) file in the class path. `json-io` provides a comprehensive default list, but you can override this with your own configurations if preferred.

This method provides a robust tool for managing how aliases are used in JSON serialization and deserialization, ensuring that alias configurations are kept up-to-date and relevant to the application's current operational needs.

>#### WriteOptionsBuilder.removePermanentAliasTypeNamesMatching(`String classNamePattern`)

### Add Permanent Reader

The `addPermanentReader` method in `json-io` allows you to permanently associate a custom JSON reader with a specific class within the JVM's lifecycle. This ensures that the custom reader is used whenever instances of the class are encountered during JSON deserialization. **We recommend using a ClassFactory over a CustomReader, as it allows both instantiation AND loading.**

- **Purpose**: This method is used to customize how JSON data is parsed into Java objects, particularly for classes where the default deserialization does not suffice or needs special handling.

- **Implementation Details**: When you add a custom reader, it is associated with the class 'c' and any subclasses thereof, as determined by `Class.isAssignableFrom()`. This method provides a robust way to ensure that your custom parsing logic is applied consistently throughout your application.

- **Managing Broad Associations**: If the association of the custom reader becomes too broad—potentially affecting more classes than intended—you can fine-tune which classes should not use the custom reader. This is achieved by using the `ReadOptionsBuilder.addNotCustomReader()` method, which excludes specified classes from being processed by the custom reader.

- **Permanent Configuration**: Once added, the custom reader remains active and associated with the class for the entire duration of the JVM's operation (statically set). This permanence ensures that the custom behavior is reliably reproduced across all operations within the application without the need for repeated configuration.

- **Usage Considerations**: This method is particularly useful in complex applications where certain classes require specialized deserialization that the default mechanisms cannot provide. It offers a powerful tool for developers to precisely control how data is interpreted and integrated into the application.

By employing `addPermanentReader`, developers can ensure that their specific deserialization requirements are met, enhancing the flexibility and robustness of data handling within their applications.
>#### ReadOptionsBuilder.addPermanentReader(`Class<?> c, JsonReader.JsonClassReader reader`)

### Add Permanent Non-Referenceable Class

The `addPermanentNonReferenceableClass` method in `json-io` allows you to designate specific classes as non-referenceable across the entire lifecycle of the JVM. This setting ensures that these classes are treated as simple values, similar to primitives, without using `@id`/`@ref` mechanisms in the serialized JSON.

- **Purpose**: This method is used to optimize JSON serialization for classes that are immutable or effectively act like primitive values, where no references are needed because they do not benefit from the `@id/@ref` mechanism.

- **Impact**: By marking a class as non-referenceable, you ensure that instances of this class in JSON are always serialized directly rather than as references. This can greatly simplify the JSON output and prevent unnecessary complexity in the data structure.

- **Application Scope**: Once a class is added to the non-referenceable list, this configuration is applied globally across all `ReadOptions` instances created during the JVM's runtime. This means there is no need to repeatedly specify this setting for each new instance, enhancing consistency and reducing setup redundancy.

- **Use Cases**: Typically used for small, immutable objects that are frequently instantiated with the same values, such as `Color` objects, custom `Money` or `Quantity` classes, which do not change once created and are used extensively throughout an application.

Adding classes to the non-referenceable list can significantly enhance the performance and clarity of JSON serialization processes, especially in complex systems where certain objects are better handled as value types rather than reference types.

>#### ReadOptionsBuilder.addPermanentNonReferenceableClass(Class<?> clazz)

### Add Permanent Not Custom Read Class

The `addPermanentNotCustomReadClass` method in `json-io` allows you to exempt specific classes from custom deserialization across the entire lifecycle of the JVM. This setting ensures these classes bypass any registered custom readers during JSON parsing.

- **Purpose**: This method prevents a class from being processed by custom readers, even if it inherits from a class that would normally use custom deserialization. It essentially forces the class to use the default deserialization behavior.

- **Impact**: By marking a class as not custom read, you ensure that instances of this class in JSON are always deserialized using the standard mechanisms, regardless of any custom readers that might apply through inheritance or direct registration.

- **Application Scope**: Once a class is added to the not-custom-read list, this configuration is applied globally across all `ReadOptions` instances created during the JVM's runtime. This means there is no need to repeatedly specify this setting for each new instance, enhancing consistency and reducing setup redundancy.

- **Use Cases**: Typically used when you need to override inheritance-based custom reader application, or when you want to ensure that certain classes are always deserialized using the default behavior regardless of other configuration settings.

Adding classes to the not-custom-read list can help control deserialization behavior in complex class hierarchies, particularly when you need exceptions to your custom reader rules.

>#### ReadOptionsBuilder.addPermanentNotCustomReadClass(Class<?> clazz)
 
### Add Permanent Not Imported Field

The `addPermanentNotImportedField` method in `json-io` allows you to permanently exclude specific fields from being deserialized into Java objects. This feature is crucial for managing how JSON data is processed into Java representations, particularly when certain JSON fields should not be transferred into the resulting Java object.

- **Purpose**: Use this method to specify fields in the JSON that should be ignored during the deserialization process. This is especially useful for JSON data that contains fields irrelevant to the application logic or potentially problematic when mapping to Java classes.

- **Functionality**: Fields added through this method will not be set (injected) into the Java objects, even if they exist in the incoming JSON. This exclusion is maintained throughout the JVM's lifetime, ensuring that all instances of `ReadOptions` created will automatically apply these settings.

- **Consistency Across Reads**: By configuring fields to be ignored permanently, you maintain consistent behavior in how data is read across different parts of your application, without needing to repeatedly specify these exclusions in each new `ReadOptions` instance.

- **Comparison with Write Options**: Similar to how `addPermanentNotImportedField` works for read operations, `WriteOptionsBuilder` provides analogous capabilities for write operations, allowing you to exclude fields from being included in the serialized JSON.

This method enhances data handling efficiency and accuracy within your application by ensuring that only relevant and necessary data is processed during JSON deserialization, simplifying object models and reducing potential errors.

>#### ReadOptionsBuilder.addPermanentNotImportedField(`Class<?> clazz, String fieldName`)

### Add Permanent Non-Standard Setter

The `addPermanentNonStandardSetter` method in `json-io` allows you to define custom setter methods for specific fields in a class when the standard JavaBean naming conventions do not apply. This is particularly useful for integrating custom or legacy code into your JSON serialization/deserialization processes.

- **Purpose**: This method is used to associate non-standard setter methods with specific fields, ensuring that `json-io` can correctly apply data from JSON to Java objects even when setter methods are named unconventionally.

- **Functionality**: When you specify a non-standard setter, `json-io` will invoke this method instead of the standard setter during the deserialization process. This is essential for classes where the setter methods do not follow the typical `setFieldName` format.

- **Example Usage**: An example of using this feature is with the `Throwable` class in Java. Typically, to set a cause on a `Throwable`, the `initCause()` method is used instead of a standard setter. Configuring `addPermanentNonStandardSetter(Throwable.class, "cause", "initCause")` instructs `json-io` to use `initCause()` to set the cause from the JSON data:

>#### ReadOptionsBuilder.addPermanentNonStandardSetter(`Class<?> clazz, String fieldName, String setterName`)