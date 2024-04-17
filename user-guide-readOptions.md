## Controlling the input JSON using `ReadOptions`
Create a new`ReadOptions`instance and turn various features on/off using the methods below. Example:

    ReadOptions readOptions = new ReadOptionsBuilder().feature1().feature2(args).build()
    JsonIo.toObjects(root, readOptions, rootClass);

To pass read options to `JsonIo.toObjects(root, readOptions, rootClass),` set up a `ReadOptions` instance using the
`ReadOptionsBuilder.` You can view the Javadoc on the `ReadOptionsBuilder` class for detailed information. The
`ReadOptions` are created and made read-only by calling the `.build()` method on the `ReadOptionsBuilder.` You can have
multiple `ReadOptions` instances for different scenarios, and safely re-use them once built (their stateless). A 
`ReadOptions` instance can be created from another `ReadOptions` instance by using `new ReadOptionsBuilder(readOptionsToCopyFrom).`

### Constructors
Create new instances of`ReadOptions.`
>#### new ReadOptionsBuilder().feature1().feature2(args).build()
>- [ ] Start with default options and add in feature1 and feature2(requires argument) and make read-only.
>#### new ReadOptionsBuilder(`ReadOptions other`)
>- [ ] Copy all settings from the passed in 'other'`ReadOptions.`

### Class Loader
The`ClassLoader`in the`ReadOptons`is used by `json-io` to turn `String` class names into`Class`instances.
>#### `ClassLoader` getClassLoader()
>- [ ] Returns the ClassLoader to resolve String class names into Class instances.

>#### `ReadOptionsBuilder`classLoader(`ClassLoader loader`)
>- [ ] Sets the ClassLoader to resolve String class names when reading JSON. 

### Unknown Types
When reading JSON, what do you want to happen when a class name does not exist in your JVM? Should the parsing fail?
This feature allows you to control what happens here.  You can tell it to fail on an unknown type (default behavior) and
a`JsonIoException`will be thrown. Turn this feature off, and the parser will create a`LinkedHashMap`and use that
to store the content from the JSON object being read.  If you want, you can set your own class to be created
instead of a`LinkedHashMap,`however, it should be noted that`Maps`are useful because any field encountered in the
JSON can be`put`into a`Map.`
>#### `boolean` isFailOnUnknownType()
>- [ ] Returns`true` if an 'unknownTypeClass' is set,`false`if it is not set
>####  `Class` getUnknownTypeClass()
>- [ ] Get the Class used to store content when the indicated class cannot be loaded by the JVM.  Defaults to`null.` When`null`, `LinkedHashMap` is used. It is usually best to use a `Map` derivative, as it will be able to have the various field names encountered to be 'set' into it.

>#### `ReadOptionsBuilder` failOnUnknownType(`boolean fail`)
>- [ ] Set to`true`to indicate that an exception should be thrown if an unknown class type is encountered,`false`otherwise.
   If`fail`is`false,`then the default class used will be`LinkedHashMap` for the particular JSON object encountered. You can
   set this to your own class using the`unknownTypeClass()`feature. 
>####  `ReadOptionsBuilder` unknownTypeClass(`Class`)
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

### Floating Point Options
Although the JSON spec does not support Nan and Infinity, it can be convenient to allow it in the JSON, and have
the values be both written and read properly. This feature is off by default.  Although the JSON with Nan and Infinity may 
work for your application, keep in mind, NaN, +Inf, -Inf are not necessarily going to be supported by other APIs and services.

JSON payloads can carry floating point numbers that are much larger than a Java IEEE 754 `Double` can hold. These options allow you
configure json-io to return these as `BigDecimal` always, only when needed, or never.
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
JSON payloads can carry integer numbers that are much larger than a Java `Long` can hold. These options allow you
configure json-io to return these as `BigInteger` always, only when needed, or never.

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
Sometimes you want to close the stream automatically after reading, other times you may want to leave it open to read
additional JSON from the stream.  For example, NDJSON is a format of {...}\n{...}\{...} To read this format, you can tell
`JsonIo` not to close the `InputStream` after each read.  See example at the beginning of the user guide.
>#### `boolean` isCloseStream()
>- [ ] Returns `true` if set to automatically close stream after read (the default), or `false`to leave stream open after reading from it.

>#### `ReadOptionsBuilder` closeStream(`boolean closeStream`)
>- [ ] Sets the 'closeStream' setting,`true`to turn on,`false`will turn off. The default setting is`false.` 

### Aliasing - shorten class names in @type.
Aliasing is used to turn long java package names to simple class names, e.g. `java.util.ArrayList` becomes `ArrayList`
in the JSON.  By default, `json-io` has most of the common JDK classes aliased to make the JSON content smaller.  You can
add additional aliases for classes in your program.
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

### Class Coercion
Use this feature to turn classes like`java.util.Collections$UnmodifiableRandomAccessList`into an`ArrayList`when parsed and loaded 
into memory.  There are many derivative classes for collection singleton's, synchronized variations of collections, and unmodifiable
variations.  All the common JDK substitutions are already in the coercion list.  You can add more if needed.
>#### `boolean` isClassCoerced(`String className`)
>- [ ] Return`true`if the passed in Class name is being coerced to another type, `false`otherwise.
>#### `Class` getCoercedClass(`Class`) 
>- [ ] Fetch the coerced class for the passed in fully qualified class name.

>#### `ReadOptionsBuilder` coerceClass(`String sourceClassName, Class destClass`)
>- [ ] Coerce a class from one type (named in the JSON) to another type.  For example, converting
`java.util.Collections$SingletonSet`to a`LinkedHashSet.class.`

### Missing Field Handler
When an attempt to set a value from the JSON onto a Java object is made, but the field from the JSON is not
available on the Java object, the missing field handler feature comes into play.  You can set the handler to be
notified of the missing field. It will be notified of all missing fields at the end of deserialization. 
See`JsonReader.MissingFieldHandler`for more information.
>#### `JsonReader.MissingFieldHandler` getMissingFieldHandler()
>- [ ]  Fetch the method that will be called when a field in the JSON is read in, yet there is no corresponding 
field on the destination object to receive the value.

>#### `ReadOptionsBuilder` missingFieldHandler(`JsonReader.MissingFieldHandler missingFieldHandler`)
>- [ ] Pass the`missing field handler`to be called when a field in the JSON is read in, yet there is no corresponding field on the 
destination object to receive the value.

### Class Factory
For a class that is failing to instantiate or be read in correctly by the default processing of json-io, you can
associate a`ClassFactory`to a`Class` to remedy the situation. The`ClassFactory's`job is to create instances of the
associated class, and load the values from the JSON into the class from the JSON object being encountered at the 
time (it is passed to the`ClassFactory`). This is the preferred method for custom handling a class as opposed to using a`CustomReader.`
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
Custom readers can be added to load the JSON content associated to a particular class if the default loading is
not working.  Before using a`CustomReader,`we recommend using a `ClassFactory,`as that allows your code to not
only create the appropriate instance, but also to load it at the same time. `CustomReaders` are used for 
the associated class and any derived classes.  If that picks up a class you do not want to associate to a `CustomReader,`
then can add that class to the Not-Custom-Reader list.
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
Add a class to this list that you do not want it being associated to a`CustomReader.` This list is only used when you
want to turn-off a`CustomReader`for a particular class that is inadvertently being associated to a `CustomReader` via 
inheritance.
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
For small immutable classes, there is no need to use @id/@ref with them, as they are effectively primitives.  All 
primitives, primitive wrappers, BigInteger, BigDecimal, Atomic*, java.util.Date, String, Class, ZonedDateTime, etc. are
automatically marked as non-referenceable. You can add more immutable classes of your own.  The side-effect of this is 
that it technically changes the "shape" of your graph, if you were counting on two different fields that point to the same
String/BigDecimal/Integer to have the same == instance.  Not commonly desired, however, if you have a lot of the same value, 
say the String "Johnson" and expect all those to collapse to the same value, they won't, each would have its own instance.
`json-io` does collapse certain common logical primitive values, such as the String value of "true" and "false", the -1, 0
and 1 value of all number types, the string values of "0" through "9", "on", "off", and a few other very common Strings.
>#### `boolean` isNonReferenceableClass(`Class`)
>- [ ] Checks if a class is non-referenceable. Returns`true`if the passed in class is considered a non-referenceable class.
>#### `Collection<Class>` getNonReferenceableClasses()
>- [ ] Returns a`Collection`of all non-referenceable classes.

>#### `ReadOptionsBuilder` addNonReferenceableClass(`Class`)
>- [ ] Adds a class to be considered "non-referenceable." Examples are the built-in primitives.

---
## Application Scoped Options (full lifecycle of JVM)
These particular options can be set at 'application-scope' to ensure all `ReadOptions` instances are automatically created
with them, so you don't have to configure these options for each `ReadOptions` instance individually.

### addPermanentClassFactory
Call this method to add a factory class that will be used to create instances of another class.  This is useful
when you have a class that `json-io` cannot instantiate due to private or other constructor issues.  Add
a JsonReader.ClassFactory class (that you implement) and associate it to the passed in 'classToCreate'.  Your
JsonReader.ClassFactory class will be called with the Map (JsonObject) that contains the { ... } that should
be loaded into your class. It is your ClassFactory implementations job to create the instance of the associated
class, and then copy the values from the passed in Map to the Java instance you created.
>#### ReadOptionsBuilder.addPermanentClassFactory(`Class<?> sourceClass, JsonReader.ClassFactory factory`)

### addPermanentCoercedType
Call this method to add a permanent (JVM lifetime) coercion of one class to another during instance creation.  Examples
of classes that might need this are proxied classes such as HibernateBags, etc. that you want to create as regular
jvm collections.
>#### ReadOptionsBuilder.addPermanentCoercedType(`Class<?> sourceClass, Class<?> destinationClass`)

### addPermanentAlias
Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.  The shorter name will
be written to and read from the @type field within the JSON.  By default the @type field is only added to the JSON
when the type of the class cannot be determined from a root class, field type, array component type, or template
argument type.
>#### ReadOptionsBuilder.addPermanentAlias(`Class<?> sourceClass, String alias`)

### addPermanentReader
Call this method to add a custom JSON reader to json-io.  It will associate the `Class` 'c' to the reader you pass in. 
The readers are found with `Class.isAssignableFrom().`  If this is too broad, causing too many classes to be associated 
to the custom reader, you can indicate that json-io should not use a custom reader for a particular class, by calling
the `ReadOptionsBuilder.addNotCustomReader()` method.  This method will add the custom reader such that it will be
there permanently, for the life of the JVM (static).
>#### ReadOptionsBuilder.addPermanentReader(`Class<?> c, JsonReader.JsonClassReader reader`)

### addPermanentNonReferenceableClass
Add a class permanently (JVM lifecycle) to the Non-referenceable list. All new `ReadOptions` instances created will 
automatically start with this installed so you will not need to add it into the `ReadOptions` each time it is created.
>#### ReadOptionsBuilder.addPermanentNonReferenceableClass(Class<?> clazz)

### addPermanentNotImportedField
Add a field to exclude (stopped) from being "set" (injected) into the Java object created that is associated to the
portion of JSON being read.  If a field exists in the JSON but you do not want `json-io` to attempt to copy it to the
associated Java class, add it here. These fields are effectively skipped from being read from the JSON.  This is on
the READ side of JSON processing.  There is a similar option on the `WriteOptionsBuilder` to prevent fields from being
written to JSON.
>#### ReadOptionsBuilder.addPermanentNotImportedField(`Class<?> clazz, String fieldName`)

### addPermanentNonStandardSetter 
For the passed in Class (clazz), you can add a field-to-setter names. This is useful when the 'setter' method does not
follow a standard naming convention. For example on the class `Throwable,` the method `initCause()` sets the cause on
the exception (as opposed to 'setCause'). Using `addPermanentNonStandardSetter(Throwable.class, "cause", "initCause"),`
will then use the initCause(cause) set the value on the Java object associated to the JSON object being read in.
>#### ReadOptionsBuilder.addPermanentNonStandardSetter(`Class<?> clazz, String fieldName, String setterName`)