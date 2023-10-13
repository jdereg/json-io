### Revision History
* 4.14.2
  * Enum/EnumSet support fully added @kpartlow
* 4.14.1
  * JDK 1.8 is target class file format. @laurgarn
  * JDK 11 is source file format. @laurgarn
  * Bug fix: EnumSet support fixed. @laurgarn
  * Bug fix: Null boxed primitives are preserved round-trip. @laurgarn
  * Enhancement: Filter Blacklisted Fields Before Trying to Access them to prevent exceptions thrown by Proxies (improve hibernate support) @kpartlow
  * Bug fix: Stack overflow error caused by json-io parsing of untrusted JSON String @PoppingSnack
  * Enhancement: Create gradle-publish.yml @devlynnx
  * Enhancement: Added record deserialization, which implies java 16 codebase @reuschling
  * Bug fix: Fixed TestJavaScript @h143570
  * Enhancement: Bump gson from 2.6.2 to 2.8.9 @dependabot
  * Enhancement: support deserialization of Collections.EmptyList on JDK17 @ozhelezniak-talend
* 4.14.0
  * Bug fix: Enum serialization error with Java 17 #155.  According to @wweng-talend, if you set : "--illegal-access=deny" on jvm parameters, it works the same between jdk11 and jdk17. 
  * Bug fix: java.lang primitives serialization - JDK-8256358 - JDK 17 support #154. Fix by @wwang-talend.
  * Bug fix: failed to deserialize EnumSet with json without type #120.  Fix by @sgandon and @wwang-talend
* 4.13.0
   * Enhancement: Clear unresolved references after all have been processed, as opposed to removing each one after it was processed.  
* 4.12.0
  * Bug fix: Enhancement #137 introduced bug for negative numbers on simple values when tolerant/lenient parsing of +/- infinity was turned on.
* 4.11.1
  * Enhancement (#140): New option flag added `FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS:true|false` to allow forcing JSON output format to always write `Map` as `@keys/@items` in the JSON (example: `{"@keys":["a", "b"], "@values":[1, 2]}`, rather than its default behavior of recognizing all `String` keys and writing the `Map` as a JSON object, example: `{"a":1, "b":2}.`  The default value for this flag is `false`.  
* 4.11.0
  * Enhancement (#137): Allow tolerant/lenient parser of +/- infinity and NaN.  New API added, `JsonReader.setAllowNanAndInfinity(boolean)` and `JsonWriter.setAllowNanAndInfinity(boolean)`.  The default is `false` to match the JSON standard.
  * Enhancement (#129): `JsonReader.jsonToJava("")` or `JsonReader.jsonToJava(null)` now returns a `null`, rather than throwing an exception.
  * Bug fix (#123): Removed vulnerability by disallowing `ProcessBuilder` to be serialized.
  * Bug fix (#124): Illegal Reflective Access warning when using json-io in Java 9 or newer.  This was do to call `isAccessible()` on Java's `Field` class.  This has been removed.
  * Bug fix (#132, #133): There was instance when @i was written when it should have been @e, indicating items, when using SHORT_META_KEYS flag. 
  * Bug fix (#135): When reading `{ "@type": "char", "value": "\"" }`, the value was read in as `\u0000`.  It now reads in correctly as a double quote character.
* 4.10.1
  * Enhancement: Made `FastPushbackBufferedReader` constructor public so that this stream reader can be used anywhere.
* 4.10.0
  * Bug fix: When reading into `Maps`, logical primitives that are not `long`, `double`, `boolean`, or `null`, were being kept in `JsonObjects` instead of being converted into their respective types (`int`, `float`, `Date`, etc.) 
* 4.9.12
  * Bug fix: Line number was incorrectly being reported as column number in error output. 
* 4.9.11
  * Enhancement: Added nice JSON-style argument format method, typically used for logging method calls.  See `MetaUtils.getLogMessage()`. 
* 4.9.10
  * Bug fix: When system property file.encoding was not set to UTF-8, json-io was not correctly handling characters outside the ASCII space.  @rednoah
* 4.9.9
  * Enhancement: Missing field handler improvements. Submitted by @sgandon
* 4.9.8
  * Enhancement: Missing field handler improvements. Submitted by @sgandon
* 4.9.7
  * Enhancement: Added `JsonReader.addReaderPermanent()` and `JsonWriter.addWriterPermanent()` to allow for a static (lifecycle of JVM) reader / writer to be added.  Now, custom readers and writers can be added that only exist per-instance of `JsonReader` / `JsonWriter` or permanently, so they do not have to be added each instantiation (through args or call `.addReader()` or `.addWriter()`).
* 4.9.6
  * Enhancement: Improved `enum` handling. Updated how enums are detected so that subclasses of enums are detected.  `ordinal` and `internal` fields no longer output.
* 4.9.5
  * Bug fix: The new FastPushBackBytesReader was incorrectly reading a String byte-by-byte ignoring the code point boundaries.  Because of this, it would blow up during parsing Strings with characters outside the ascii range.  New test case added that causes the failure.  For time being, the FastPushBackBytesReader has been removed.
  * Javadoc updates.
* 4.9.4
  * Optimization: The coercedTypes Map in the Resolver is built one time now.
  * Added test case illustrating gson cannot handle writing then reading back Maps correctly when the keys are not Strings.
* 4.9.3
  * Enhancement: Double.INF and NAN are output as null.
* 4.9.2
  * Optimization: When parsing from String, a different (faster) byte[] based pushback reader is used.
  * Optimization: Built-in Readers and Writers are only instantiated once for all instances of JsonReader / JsonWriter and then re-used.
  * Enhancement: Inner 'view' classes generated from `.keySet()` and `.values()` are coerced to standard mutable collection classes. 
  * Optimization: Identical code consolidated to one function.
* 4.9.1
  * Enhancement: Make it possible to assign instantiator for package private classes, for example com.google.common.collect.RegularImmutableMap.  Contributed by @mhmx (Richard Kovacs)
* 4.9.0
  * Enhancement: AtomicInteger, AtomicLong, and AtomicBoolean are now supported.
* 4.8.0
  * Enhancement: Added support for specifying the ClassLoader to be used when mapping JSON to Objects. Useful within OSGI and other frameworks where multiple ClassLoaders are involved. @lightcycle
  * JavaDoc has been significantly updated / improved.
* 4.7.0
  * Bug fix: failing to set a double field when the JSON from the client contained a whole number (e.g. 300) instead of a decimal (e.g. 300.0). @lordvlad
  * Enhancement: when instantiating classes, json-io iterates through constructors until it can find one that works.  The order of constructors was non-deterministic.  Now the order is public constructors first, then protected, then private.
* 4.6.0
  * Bug fix: custom write serializers were being cleared in the `write()` method, not the `close()` method after full serialization completed.  @darmbrust
  * Enhancement: Access increased to public for the pretty-print support apis, `tabIn()`, `tabOut()`, and `newLine()`. @darmbrust
* 4.5.0
  * Improved read speed.
  * Black-list support for excluding fields.  Submitted by @sgandon
  * Pretty-print with support for options.  Submitted by @dtracers
  * Ability to use `writeObject()` API to write the 'body only'.  Submitted by @francisu
  * Bug fix: Unclear error sometimes when a class could not be loaded.  Submitted by @francisu
  * Enhancement: Provide optional notification of missing field. Submitted by @francisu
* 4.4.0
  * `JsonReader.jsonToMaps()` API is no longer recommended (not yet deprecated).  These can easily be turned into `JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true])`.  The one difference is the return value will match the return value type of the JSON (not always be a Map).
* 4.3.1
  * Enhancement: Skip null fields.  When this flag is set on the `JsonWriter` optional arguments, fields which have a null value are not written in the JSON output.
* 4.3.0
  * Double / Float Nan and inifinity are now written as null, per RFC 4627
  * JsonReader.jsonToJava() can now be used to read input into Maps only (as opposed to attempting to create specific Java objects.
    Using this API allows the return value to support an array [], object, string, double, long, null as opposed to the JsonReader.jsonToMaps()
    API which forces the return value to be a Map.  May deprecate JsonReader.jsonToMaps() in the future.
* 4.2.1
  * Bug fix: The error message showing any parsing errors put the first character of the message at the end of the message (off by one error on a ring buffer).
  * Parsing exceptions always include the line number and column number (there were a couple of places in the code that did not do this).
* 4.2.0
  * Enhancement: In Map of Maps mode, all fields are kept, even if they start with @.  In the past fields starting with @ were skipped.
  * Ehancement: No longer throws ClassNotFound exception when the class associated to the @type is not found.  Instead it returns a LinkedHashMap, which works well in Map of Maps mode.  In Object mode, it*may* work if the field can have the Map set into it, otherwise an error will be thrown indicating that a Map cannot be set into field of type 'x'.
  * Bug fix: In Map of Maps mode, Object[] were being added with an @items field.  The object[] is now stored directly in the field holding it.  If an Object[] is 'pointed to' (re-used), then it will be written as an object { } with an @id identifying the object, and an @items field containing the array's elements.
* 4.1.10
  * Enhancement: Java's EnumSet support added (submitted by @francisu) without need for using custom instantiator.
  * Enhancement: Added support for additional instantiator, ClassFactory2 that takes the Class (c) and the JsonObject which the instance will be filled from.  Useful for custom readers.
* 4.1.9
  * Bug fix: When writing a Map that has all String keys, the keys were not being escaped for quotes (UTF-8 characters in general).
* 4.1.8
  * Bug fix: 4.1.7 skipped ALL transient fields.  If a transient field is listed in the field specifiers map, then it must be traced. 
* 4.1.7
  * Bug fix: Transient fields are skipped during reference tracing. (fix submitted by Francis Upton, @francisu).  Some transient fields could cause an exception to be thrown when being trace for references, stopping serialization.   
* 4.1.6
  * Better support for primitive output when 'never show type' is set. (submitted by @KaiHufenbach)
* 4.1.5
  * Tests updated to use Groovy 2.4.4
  * Deserialization updated to handle objects where the referencing class uses an Object pointer and writes the value out as single primitive value, using the 'value' key. (submitted by @KaiHufenbach)
  * pom filed updated to use a maven bundle plugin (Apache Felix) to generate OSGI headers (submitted by @KaiHufenbach)
* 4.1.4
  * Bug fix: Custom readers will now always have the .target field set if a `JsonObject` is passed to them.  The custom reader's `read()` method was being called before the `.target` field was set on the `JsonObject`.
* 4.1.3
  * Made `JsonReader / JsonWriter getObjectsReferenced()` API `public` (allows custom reader / writers access to these)
  * `Resolver.createJavaObjectInstance()`, used to create the correct Java object for a `JsonObject` peer, no longer calls the .read() API for objects's with custom readers.
* 4.1.2
  * All objects in the graph are 'traced' (JsonWriter.traceReferences) except references.  The code used to not trace fields on objects that were handled by custom writers.
* 4.1.1
  * JDK 1.6 support - Use of `ReflectiveOperationException` changed to `InvocationTargetException`.
* 4.1.0
  * JDK 1.6 support restored. Keeping 1.6 support for Android developers.  Submitted by @kkalisz
* 4.0.1
  * To prevent @type from being written, set the optional argument `JsonWriter.TYPE = false`. This is generally not recommended, as the output JSON may not be able to be re-read into Java objects.  However, if the JSON is destined for a non-Java system, this can be useful.
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
  ***New Feature**: Short class names to reduce the size of the output JSON. This allows you to, for example, substitute `java.util.HashMap` with `hmap` so that it will appear in the JSON as `"@type":"hmap"`.  Pass the substitution map to the `JsonWriter` (or reader) as an entry in the args `Map` with the key of `JsonWriter.TYPE_NAME_MAP` and the value as a `Map` instance with String class names as the keys and short-names as the values. The same map can be passed to the `JsonReader` and it will properly read the substituted types.
  ***New Feature**: Short meta-key names to reduce the size of the output JSON.  The `@type` key name will be shortened to `@t`, `@id` => `@i`, `@ref` => `@r`, `@keys` => `@k`, `@items` => `@e`.  Put a key in the `args` `Map` as `JsonWriter.SHORT_META_KEYS` with the value `true`.   
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
  ***json-io** will now use the generic info on a `Map<Foo, Bar>` or `Collection<Foo>` object's field when the `@type` information is not included.**json-io** will then know to create `Foo` instances, `Bar` instances, etc. within the `Collection` or `Map`.
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
