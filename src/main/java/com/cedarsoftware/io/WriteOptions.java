package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.io.reflect.Accessor;

/**
 * This class contains all the "feature" control (options) for controlling json-io's
 * output JSON. An instance of this class is passed to the JsonWriter.toJson() APIs
 * to set the desired capabilities.
 * <br/><br/>
 * You can make this class immutable and then store the class for re-use.
 * Call the ".build()" method and then no longer can any methods that change state be
 * called - it will throw a JsonIoException.
 * <br/><br/>
 * This class can be created from another WriteOptions instance, using the "copy constructor"
 * that takes a WriteOptions. All properties of the other WriteOptions will be copied to the
 * new instance, except for the 'built' property. That always starts off as false (mutable)
 * so that you can make changes to options.
 * <br/><br/>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Ken Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public interface WriteOptions {
    // Properties

    // Enum for the 3-state property
    enum ShowType {
        ALWAYS, NEVER, MINIMAL
    }

    /**
     * @return boolean 'prettyPrint' setting, true being yes, pretty-print mode using lots of vertical
     * white-space and indentations, 'false' will output JSON in one line.  The default is false.
     */
    boolean isPrettyPrint();

    /**
     * @return boolean 'writeLongsAsStrings' setting, true indicating longs will be written as Strings,
     * false to write them out as native JSON longs.  Writing Strings as Longs to the JSON, will fix errors
     * in Javascript when an 18-19 digit long value is sent to Javascript.  This is because Javascript stores
     * them in Doubles, which cannot handle the precision of an 18-19 digit long, but a String will retain
     * the full value into Javascript.  The default is false.
     */
    boolean isWriteLongsAsStrings();

    /**
     * @return boolean skipNullFields setting, true indicates fields with null values will not be written,
     * false will still output the field with an associated null value.  false is the default.
     */
    boolean isSkipNullFields();

    /**
     * @return boolean 'forceMapOutputAsTwoArrays' setting.  true indicates that two arrays will be written to
     * represent a Java Map, one for keys, one for values.  false indicates one Java object will be used, if
     * all the keys of the Map are Strings.  If not, then the Map will be written out with a key array, and a
     * parallel value array. (@keys:[...], @values:[...]).  false is the default.
     */
    boolean isForceMapOutputAsTwoArrays();

    /**
     * @return boolean will return true if NAN and Infinity are allowed to be written out for
     * Doubles and Floats, else null will be written out. Default is false.
     */
    boolean isAllowNanAndInfinity();

    /**
     * true indicates that only public fields will be output on an Enum.  Enums don't often have fields added to them
     * but if so, then only the public fields will be written.  The Enum will be written out in JSON object { } format.
     * If there are not added fields to an Enum, it will be written out as a single line value.  The default value
     * is true.  If you set this to false, it will change the 'enumFieldsAsObject' to true - because you will be
     * showing potentially more than one value, it will require the enum to be written as an object.
     */
    boolean isEnumPublicFieldsOnly();

    /**
     * true indicates that EnumSet instances are written with an "@enum":"enum_classname" where enum_classname is the
     * Class name of the enum type that is held in the EnumSet. When 'false' EnumSets are written with
     * "@type":"enum_classname" the same as before - the distinction is that @enum is now written as @type. EnumSets
     * are resolved because these are always written with @items:[] so the resolver knows that if the @type is an
     * Enum, and there is an @items[], then it can infere that it is an EnumSet, not a enum.  When an enum is written
     * out as "@type":"com.foo.myEnum" there will not be an @items, therefore it can be considered an Enum, not an
     * EnumSet. <p></p>
     * The default is true for backward compatibility but will be switched to false in the next major release.
     */
    boolean isEnumSetWrittenOldWay();

    /**
     * @return boolean 'true' if the OutputStream should be closed when the reading is finished.  The default is 'true.'
     */
    boolean isCloseStream();

    /**
     * @return int size of LRU Cache used to cache Class to Field and Class to Accessor
     */
    int getLruSize();

    /**
     * @return ClassLoader to be used when writing JSON to resolve String named classes.
     */
    ClassLoader getClassLoader();

    /**
     * @return boolean true if showing short meta-keys (@i instead of @id, @ instead of @ref, @t
     * instead of @type, @k instead of @keys, @v instead of @values), false for full size. 'false' is the default.
     */
    boolean isShortMetaKeys();

    /**
     * Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
     * @param typeName String name of type to fetch alias for.  There are no default aliases.
     * @return String alias name or null if type name is not aliased.
     */
    String getTypeNameAlias(String typeName);

    /**
     * @return {@see Map<String, String>} of all aliases within the WriteOptions.
     */
    Map<String, String> aliases();

    /**
     * @return boolean true if set to always show type (@type)
     */
    boolean isAlwaysShowingType();

    /**
     * @return boolean true if set to never show type (no @type)
     */
    boolean isNeverShowingType();

    /**
     * @return boolean true if set to show minimal type (@type)
     */
    boolean isMinimalShowingType();

    /**
     * @param clazz Class to check to see if there is a custom writer associated to it.
     * @return boolean true if there is an associated custom writer class associated to the passed in class,
     * false otherwise.
     */
    boolean isCustomWrittenClass(Class<?> clazz);

    /**
     * @param clazz Class to see if it is on the not-customized list.  Classes are added to this list when
     *              a class is being picked up through inheritance, and you don't want it to have a custom
     *              writer associated to it.
     * @return boolean true if the passed in class is on the not-customized list, false otherwise.
     */
    boolean isNotCustomWrittenClass(Class<?> clazz);

    List<Accessor> getAccessorsForClass(final Class<?> c);

    /**
     * @return boolean true if java.util.Date and java.sql.Date's are being written in long (numeric) format.
     */
    boolean isLongDateFormat();

    /**
     * @param clazz Class to check to see if it is non-referenceable.  Non-referenceable classes will always create
     *              a new instance when read in and never use @id/@ref. This uses more memory when the JSON is read in,
     *              as there will be a separate instance in memory for each occurrence. There are certain classes that
     *              json-io automatically treats as non-referenceable, like Strings, Enums, Class, and any Number
     *              instance (BigDecimal, AtomicLong, etc.)  You can add to this list. Often, non-referenceable classes
     *              are useful for classes that can be defined in one line inJSON, like a LocalDateTime, for example.
     * @return boolean true if the passed in class is considered a non-referenceable class.
     */
    boolean isNonReferenceableClass(Class<?> clazz);

    /**
     * Fetch the custom writer for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom writer
     * and store that result.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter for the custom class (if one exists), null otherwise.
     */
    JsonWriter.JsonClassWriter getCustomWriter(Class<?> c);
    
    void clearCaches();

    /**
     * Gets the declared fields for the full class hierarchy of a given class
     *
     * @param c - given class.
     * @return Map - map of string fieldName to Field Object.  This will have the
     * deep list of fields for a given class.
     */
    Map<String, Field> getDeepDeclaredFields(final Class<?> c);

    /**
     * Get a custom option
     * @param key String name of the custom option
     * @return Object value of the custom option
     */
    Object getCustomOption(String key);

    /**
     * @param c Class to get the included fields for.
     * @return a Set of field names that are to be "included" for the class.  This is the list of fields that were defined
     * in the WriteOptionsBuilder, and only these fields will be output in the JSON for the given class.
     */
    Set<String> getIncludedFields(Class<?> c);

    /**
     * @param c Class to get the excluded fields for.
     * @return a {@see Set<String>} of field names that are to be "excluded" for the class.  This is the list of fields
     * that were defined in the WriteOptionsBuilder, and these fields will be excluded in the output in the JSON for the
     * given class.
     */
    Set<String> getExcludedFields(Class<?> c);
}