package com.cedarsoftware.util.io;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.ClassDescriptors;

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
 * @author Kenny Partlow (kpartlow@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class WriteOptions {
    // Constants
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    // Properties
    private boolean shortMetaKeys;
    private ShowType showTypeInfo = ShowType.MINIMAL;
    private boolean prettyPrint = false;
    private boolean writeLongsAsStrings = false;
    private boolean skipNullFields = false;
    private boolean forceMapOutputAsTwoArrays = false;
    private boolean allowNanAndInfinity = false;
    private boolean enumPublicFieldsOnly = false;
    private boolean closeStream = true;
    private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();
    private ClassLoader classLoader = WriteOptions.class.getClassLoader();
    private Map<Class<?>, Set<String>> includedFields = new ConcurrentHashMap<>();
    private Map<Class<?>, Set<Accessor>> includedAccessors = new ConcurrentHashMap<>();
    private Map<Class<?>, Set<String>> excludedFields = new ConcurrentHashMap<>();
    private Map<Class<?>, Set<Accessor>> excludedAccessors = new ConcurrentHashMap<>();
    private Map<String, String> aliasTypeNames = new ConcurrentHashMap<>();
    private Set<Class<?>> notCustomWrittenClasses = Collections.synchronizedSet(new LinkedHashSet<>());
    private Set<Class<?>> nonRefClasses = Collections.synchronizedSet(new LinkedHashSet<>());
    private Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses = new ConcurrentHashMap<>();
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = Collections.synchronizedSet(new LinkedHashSet<>());
    // Runtime cache (not feature options)
    private final Map<Class<?>, JsonWriter.JsonClassWriter> writerCache = new ConcurrentHashMap<>(300);
    
    private boolean built = false;

    static {
        // These are hard-coded below so that the Writer does not get ahead of Readers out in the wild.
        // Uncomment in the future (year or more after the 4.19 release?)
        // About 5 tests need updated if the line below is uncommented (they have hard-coded JSON text
        // used in assertions, e.g. java.util.ArrayList --> ArrayList in the test JSON.
//        MetaUtils.loadMapDefinition(BASE_ALIAS_MAPPINGS, "aliases.txt");

        // Temporary: see above.
        addPermanentAlias(Class.class, "class");
        addPermanentAlias(String.class, "string");
        addPermanentAlias(Date.class, "date");

        addPermanentAlias(Byte.class, "byte");
        addPermanentAlias(Short.class, "short");
        addPermanentAlias(Integer.class, "int");
        addPermanentAlias(Long.class, "long");
        addPermanentAlias(Float.class, "float");
        addPermanentAlias(Double.class, "double");
        addPermanentAlias(Character.class, "char");
        addPermanentAlias(Boolean.class, "boolean");

        BASE_WRITERS.putAll(loadWriters());
        BASE_NON_REFS.addAll(loadNonRefs());
    }

    // Enum for the 3-state property
    public enum ShowType {
        ALWAYS, NEVER, MINIMAL
    }

    /**
     * Start with default options
     */
    public WriteOptions() {
        // Start with all BASE_ALIAS_MAPPINGS (more aliases can be added to this instance, and more aliases
        // can be added to the BASE_ALIAS_MAPPINGS via the static method, so that all instances get them.)
        aliasTypeNames.putAll(BASE_ALIAS_MAPPINGS);
        customWrittenClasses.putAll(BASE_WRITERS);
        writerCache.putAll(BASE_WRITERS);
        nonRefClasses.addAll(BASE_NON_REFS);
    }

    /**
     * Copy all the settings from the passed in 'other' WriteOptions
     * @param other WriteOptions - source to copy from.
     */
    public WriteOptions(WriteOptions other) {
        shortMetaKeys = other.shortMetaKeys;
        showTypeInfo = other.showTypeInfo;
        enumWriter = other.enumWriter;
        prettyPrint = other.prettyPrint;
        writeLongsAsStrings = other.writeLongsAsStrings;
        skipNullFields = other.skipNullFields;
        allowNanAndInfinity = other.allowNanAndInfinity;
        forceMapOutputAsTwoArrays = other.forceMapOutputAsTwoArrays;
        enumPublicFieldsOnly = other.enumPublicFieldsOnly;
        closeStream = other.closeStream;
        notCustomWrittenClasses.addAll(other.notCustomWrittenClasses);
        aliasTypeNames.putAll(other.aliasTypeNames);
        customWrittenClasses.putAll(other.customWrittenClasses);
        classLoader = other.classLoader;
        nonRefClasses.addAll(other.nonRefClasses);
        writerCache.putAll(other.writerCache);

        // Need your own Set instance here per Class, no references to the copied Set.
        includedFields = (Map<Class<?>, Set<String>>) dupe(other.includedFields, false);
        includedAccessors = (Map<Class<?>, Set<Accessor>>) dupe(other.includedAccessors, false);
        excludedFields = (Map<Class<?>, Set<String>>) dupe(other.excludedFields, false);
        excludedAccessors = (Map<Class<?>, Set<Accessor>>) dupe(other.excludedAccessors, false);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.
     * @param clazz Class that will be aliased by a shorter name in the JSON.
     * @param alias Shorter alias name, for example, "ArrayList" as opposed to "java.util.ArrayList"
     */
    public static void addPermanentAlias(Class<?> clazz, String alias) {
        BASE_ALIAS_MAPPINGS.put(clazz.getName(), alias);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) class that should not be treated as referencable
     * when being written out to JSON.  This means it will never have an @id nor @ref.  This feature is
     * useful for small, immutable classes.
     * @param clazz Class that will no longer be treated as referenceable when being written to JSON.
     */
    public static void addPermanentAlias(Class<?> clazz) {
        BASE_NON_REFS.add(clazz);
    }

    /**
     * Call this method to add a custom JSON writer to json-io.  It will
     * associate the Class 'c' to the writer you pass in.  The writers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom writer, you can indicate
     * that json-io should not use a custom write for a particular class,
     * by calling the addNotCustomWrittenClass() method.  This method will add
     * the custom writer such that it will be there permanently, for the
     * life of the JVM (static).
     *
     * @param clazz      Class to assign a custom JSON writer to
     * @param writer The JsonClassWriter which will write the custom JSON format of class.
     */
    public static void addPermanentWriter(Class<?> clazz, JsonWriter.JsonClassWriter writer) {
        BASE_WRITERS.put(clazz, writer);
    }

    // Private method to check if the object is built
    private void throwIfBuilt() {
        if (built) {
            throw new JsonIoException("This instance of WriteOptions is built and cannot be modified.  You can create another instance from this instance using the constructor for a mutable copy.");
        }
    }

    /**
     * @return ClassLoader to be used when writing JSON to resolve String named classes.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @param classLoader ClassLoader to use when writing JSON to resolve String named classes.
     * @return WriteOptions for chained access.
     */
    public WriteOptions classLoader(ClassLoader classLoader) {
        throwIfBuilt();
        this.classLoader = classLoader;
        return this;
    }

    /**
     * @return boolean true if showing short meta-keys (@i instead of @id, @ instead of @ref, @t
     * instead of @type, @k instead of @keys, @v instead of @values), false for full size. 'false' is the default.
     */
    public boolean isShortMetaKeys() {
        return shortMetaKeys;
    }

    /**
     * @param shortMetaKeys boolean true to turn on short meta-keys, false for long.
     * @return WriteOptions for chained access.
     */
    public WriteOptions shortMetaKeys(boolean shortMetaKeys) {
        throwIfBuilt();
        this.shortMetaKeys = shortMetaKeys;
        return this;
    }

    /**
     * Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
     * @param typeName String name of type to fetch alias for.  There are no default aliases.
     * @return String alias name or null if type name is not aliased.
     */
    public String getTypeNameAlias(String typeName) {
        String alias = aliasTypeNames.get(typeName);
        return alias == null ? typeName : alias;
    }

    /**
     * @return Map<String, String> containing String class names to alias names.
     */
    public Map<String, String> aliasTypeNames() {
        return built ? aliasTypeNames : new LinkedHashMap<>(aliasTypeNames);
    }

    /**
     * @param aliasTypeNames Map containing String class names to alias names.  The passed in Map will
     *                       be copied, and be the new baseline settings.
     * @return WriteOptions for chained access.
     */
    public WriteOptions aliasTypeNames(Map<String, String> aliasTypeNames) {
        throwIfBuilt();
        aliasTypeNames.forEach(this::addUniqueAlias);
        return this;
    }

    /**
     * @param type  Class to alias
     * @param alias String shorter name to use, typically.
     * @return ReadOptions for chained access.
     */
    public WriteOptions aliasTypeName(Class<?> type, String alias) {
        throwIfBuilt();
        aliasTypeNames.put(type.getName(), alias);
        return this;
    }

    /**
     * @param typeName String class name
     * @param alias String shorter name to use, typically.
     * @return WriteOptions for chained access.
     */
    public WriteOptions aliasTypeName(String typeName, String alias) {
        throwIfBuilt();
        addUniqueAlias(typeName, alias);
        return this;
    }

    /**
     * Since we are swapping keys/values, we must check for duplicate values (which are now keys).
     * @param typeName String fully qualified class name.
     * @param alias String shorter alias name.
     */
    private void addUniqueAlias(String typeName, String alias) {
        Class<?> clazz = MetaUtils.classForName(typeName, getClassLoader());
        if (clazz == null) {
            throw new JsonIoException("Unknown class: " + typeName + " cannot be added to the WriteOptions alias map.");
        }
        String existType = aliasTypeNames.get(clazz);
        if (existType != null) {
            throw new JsonIoException("Non-unique WriteOptions alias: " + alias + " attempted assign to: " + typeName + ", but is already assigned to: " + existType);
        }
        aliasTypeNames.put(clazz.getName(), alias);
    }

    /**
     * @return boolean true if set to always show type (@type)
     */
    public boolean isAlwaysShowingType() {
        return showTypeInfo == ShowType.ALWAYS;
    }

    /**
     * @return boolean true if set to never show type (no @type)
     */
    public boolean isNeverShowingType() {
        return showTypeInfo == ShowType.NEVER;
    }

    /**
     * @return boolean true if set to show minimal type (@type)
     */
    public boolean isMinimalShowingType() {
        return showTypeInfo == ShowType.MINIMAL;
    }

    /**
     * Set to always show type
     * @return WriteOptions for chained access.
     */
    public WriteOptions showTypeInfoAlways() {
        throwIfBuilt();
        this.showTypeInfo = ShowType.ALWAYS;
        return this;
    }

    /**
     * Set to never show type
     * @return WriteOptions for chained access.
     */
    public WriteOptions showTypeInfoNever() {
        throwIfBuilt();
        this.showTypeInfo = ShowType.NEVER;
        return this;
    }

    /**
     * Set to show minimal type.  This means that when the type of an object can be inferred, a type field will not
     * be output.  A Field that points to an instance of the same time, or a typed [] of objects don't need the type
     * info.  However, an Object[], a Collection with no generics, the reader will need to know what type the JSON
     * object is, in order to instantiate the write Java class to which the information will be copied.
     * @return WriteOptions for chained access.
     */
    public WriteOptions showTypeInfoMinimal() {
        throwIfBuilt();
        this.showTypeInfo = ShowType.MINIMAL;
        return this;
    }

    /**
     * @return boolean 'prettyPrint' setting, true being yes, pretty-print mode using lots of vertical
     * white-space and indentations, 'false' will output JSON in one line.  The default is false.
     */
    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    /**
     * @param prettyPrint boolean 'prettyPrint' setting, true to turn on, false will turn off.
     * @return WriteOptions for chained access.
     */
    public WriteOptions prettyPrint(boolean prettyPrint) {
        throwIfBuilt();
        this.prettyPrint = prettyPrint;
        return this;
    }

    /**
     * @return boolean 'writeLongsAsStrings' setting, true indicating longs will be written as Strings,
     * false to write them out as native JSON longs.  Writing Strings as Longs to the JSON, will fix errors
     * in Javascript when an 18-19 digit long value is sent to Javascript.  This is because Javascript stores
     * them in Doubles, which cannot handle the precision of an 18-19 digit long, but a String will retain
     * the full value into Javascript.  The default is false.
     */
    public boolean isWriteLongsAsStrings() {
        return writeLongsAsStrings;
    }

    /**
     * @param writeLongsAsStrings boolean true to turn on writing longs as Strings, false to write them as
     *                            native JSON longs.
     * @return WriteOptions for chained access.
     */
    public WriteOptions writeLongsAsStrings(boolean writeLongsAsStrings) {
        throwIfBuilt();
        this.writeLongsAsStrings = writeLongsAsStrings;
        return this;
    }

    /**
     * @return boolean skipNullFields setting, true indicates fields with null values will not be written,
     * false will still output the field with an associated null value.  false is the default.
     */
    public boolean isSkipNullFields() {
        return skipNullFields;
    }

    /**
     * @param skipNullFields boolean setting, where true indicates fields with null values will not be written
     * to the JSON, false will allow the field to still be written.
     * @return WriteOptions for chained access.
     */
    public WriteOptions skipNullFields(boolean skipNullFields) {
        throwIfBuilt();
        this.skipNullFields = skipNullFields;
        return this;
    }

    /**
     * @return boolean 'forceMapOutputAsTwoArrays' setting.  true indicates that two arrays will be written to
     * represent a Java Map, one for keys, one for values.  false indicates one Java object will be used, if
     * all the keys of the Map are Strings.  If not, then the Map will be written out with a key array, and a
     * parallel value array. (@keys:[...], @values:[...]).  false is the default.
     */
    public boolean isForceMapOutputAsTwoArrays() {
        return forceMapOutputAsTwoArrays;
    }

    /**
     * @param forceMapOutputAsTwoArrays boolean 'forceMapOutputAsTwoArrays' setting.  true will force Java Maps to be
     *                                  written out as two parallel arrays, once for keys, one array for values.
     *                                  false will write out one JSON { } object, if all keys are Strings.  If not,
     *                                  then the Map will be output as two parallel arrays (@keys:[...], @values:[...]).
     * @return WriteOptions for chained access.
     */
    public WriteOptions forceMapOutputAsTwoArrays(boolean forceMapOutputAsTwoArrays) {
        throwIfBuilt();
        this.forceMapOutputAsTwoArrays = forceMapOutputAsTwoArrays;
        return this;
    }

    /**
     * @return boolean will return true if NAN and Infinity are allowed to be written out for
     * Doubles and Floats, else null will be written out..
     */
    public boolean isAllowNanAndInfinity() {
        return allowNanAndInfinity;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow
     *                            Double and Floats to be output as NAN and INFINITY, false
     *                            and these values will come across as null.
     * @return WriteOptions for chained access.
     */
    public WriteOptions allowNanAndInfinity(boolean allowNanAndInfinity) {
        throwIfBuilt();
        this.allowNanAndInfinity = allowNanAndInfinity;
        return this;
    }

    /**
     * @return boolean true if enums are to be written out as Strings (not a full JSON object) when possible.
     */
    public boolean isWriteEnumAsString() {
        return enumWriter instanceof Writers.EnumsAsStringWriter;
    }

    /**
     * true indicates that only public fields will be output on an Enum.  Enums don't often have fields added to them
     * but if so, then only the public fields will be written.  The Enum will be written out in JSON object { } format.
     * If there are not added fields to an Enum, it will be written out as a single line value.  The default value
     * is true.  If you set this to false, it will change the 'enumFieldsAsObject' to true - because you will be
     * showing potentially more than one value, it will require the enum to be written as an object.
     */
    public boolean isEnumPublicFieldsOnly() {
        return enumPublicFieldsOnly;
    }

    /**
     * Option to write out enums as a String, it will write out the enum.name() field.
     * This is the default way enums will be written out.
     * @return WriteOptions for chained access.
     */
    public WriteOptions writeEnumsAsString() {
        throwIfBuilt();
        this.enumWriter = new Writers.EnumsAsStringWriter();
        return this;
    }

    /**
     * Option to write out all the member fields of an enum.  You can also filter the
     * field to write out only the public fields on the enum.
     * @param writePublicFieldsOnly boolean, only write out the public fields when writing enums as objects
     * @return WriteOptions for chained access.
     */
    public WriteOptions writeEnumAsJsonObject(boolean writePublicFieldsOnly) {
        throwIfBuilt();
        this.enumWriter = nullWriter;
        this.enumPublicFieldsOnly = writePublicFieldsOnly;
        return this;
    }

    /**
     * @return boolean 'true' if the OutputStream should be closed when the reading is finished.  The default is 'true.'
     */
    public boolean isCloseStream() {
        return closeStream;
    }

    /**
     * @param closeStream boolean set to 'true' to have JsonIo close the OutputStream when it is finished writinging
     *                    to it.  The default is 'true'.  If false, the OutputStream will not be closed, allowing
     *                    you to continue writing further.  Example, NDJSON that has new line eliminated JSON
     *                    objects repeated.
     * @return WriteOptions for chained access.
     */
    public WriteOptions closeStream(boolean closeStream) {
        throwIfBuilt();
        this.closeStream = closeStream;
        return this;
    }

    /**
     * @param customWrittenClasses Map of Class to JsonWriter.JsonClassWriter.  Establish the passed in Map as the
     *                             established Map of custom writers to be used when writing JSON. Using this method
     *                             more than once, will set the custom writers to only the values from the Set in
     *                             the last call made.
     * @return WriteOptions for chained access.
     */
    public WriteOptions setCustomWrittenClasses(Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses) {
        throwIfBuilt();
        this.customWrittenClasses.clear();
        this.customWrittenClasses.putAll(customWrittenClasses);
        return this;
    }

    /**
     * @param clazz Class to add a custom writer for.
     * @param customWriter JsonClassWriter to use when the passed in Class is encountered during serialization.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addCustomWrittenClass(Class<?> clazz, JsonWriter.JsonClassWriter customWriter) {
        throwIfBuilt();
        customWrittenClasses.put(clazz, customWriter);
        return this;
    }

    /**
     * @return Map of Class to custom JsonClassWriter's use to write JSON when the class is encountered during
     * serialization to JSON.
     */
    public Map<Class<?>, JsonWriter.JsonClassWriter> getCustomWrittenClasses() {
        return built ? customWrittenClasses : new LinkedHashMap<>(customWrittenClasses);
    }

    /**
     * @param clazz Class to check to see if there is a custom writer associated to it.
     * @return boolean true if there is an associated custom writer class associated to the passed in class,
     * false otherwise.
     */
    public boolean isCustomWrittenClass(Class<?> clazz) {
        return customWrittenClasses.containsKey(clazz);
    }

    /**
     * @param clazz Class to see if it is on the not-customized list.  Classes are added to this list when
     *              a class is being picked up through inheritance, and you don't want it to have a custom
     *              writer associated to it.
     * @return boolean true if the passed in class is on the not-customized list, false otherwise.
     */
    public boolean isNotCustomWrittenClass(Class<?> clazz) {
        return notCustomWrittenClasses.contains(clazz);
    }

    /**
     * @return Set of all Classes on the not-customized list.
     */
    public Set<Class<?>> getNotCustomWrittenClasses() {
        return built ? notCustomWrittenClasses : new LinkedHashSet<>(notCustomWrittenClasses);
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom writer (that could happen through inheritance).
     * @param notCustomClass Class to add to the not-customized list.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addNotCustomWrittenClass(Class<?> notCustomClass) {
        throwIfBuilt();
        notCustomWrittenClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                   will be dropped and this Collection will establish the new list.
     * @return WriteOptions for chained access.
     */
    public WriteOptions setNotCustomWrittenClasses(Collection<Class<?>> notCustomClasses) {
        throwIfBuilt();
        notCustomWrittenClasses.clear();
        notCustomWrittenClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * Get the list of fields associated to the passed in class that are to be included in the written JSON.
     * @param clazz Class for which the fields to be included in JSON output will be returned.
     * @return Set of Strings field names associated to the passed in class or an empty
     * Set if no fields.  This is the list of fields to be included in the written JSON for the given class.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getIncludedFields(Class<?> clazz) {
        return (Set<String>) getItemsForClass(clazz, includedFields);
    }

    /**
     * Get the list of Accessors associated to the passed in class that are to be included in the written JSON.
     * @param clazz Class for which the Accessors to be included in JSON output will be returned.
     * @return Set of Strings Accessor names associated to the passed in class or an empty
     * Set if no Accessors.  This is the list of accessors to be included in the written JSON for the given class.
     */
    @SuppressWarnings("unchecked")
    public Set<Accessor> getIncludedAccessors(Class<?> clazz) {
        return (Set<Accessor>) getItemsForClass(clazz, includedAccessors);
    }

    /**
     * @return Map of all Classes and their associated Sets of fields to be included when serialized to JSON.
     */
    public Map<Class<?>, Set<String>> getIncludedFieldsPerAllClasses() {
        return getClassSetMapFields(includedFields);
    }

    /**
     * @return Map of all Classes and their associated Sets of accessors to be included when serialized to JSON.
     */
    public Map<Class<?>, Set<Accessor>> getIncludedAccessorsPerAllClasses() {
        if (built) {
            return includedAccessors;
        } else {
            Map<Class<?>, Set<Accessor>> copy = new LinkedHashMap<>();
            for (Map.Entry<Class<?>, Set<Accessor>> entry : includedAccessors.entrySet()) {
                copy.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            return copy;
        }
    }

    /**
     * @param clazz Class to add a single field to be included in the written JSON.
     * @param includedField String name of field to include in written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addIncludedField(Class<?> clazz, String includedField) {
        addIncludedFields(clazz, MetaUtils.setOf(includedField));    // checked sealed happens in here.
        return this;
    }

    /**
     * @param clazz Class to add a Collection of fields to be included in written JSON.
     * @param includedFields Collection of String name of fields to include in written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addIncludedFields(Class<?> clazz, Collection<String> includedFields) {
        throwIfBuilt();
        this.includedFields.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(includedFields);
        return copyFieldsToAccessors(clazz, includedFields, includedAccessors);
    }

    /**
     * @param includedFields Map of Class's mapped to Collection of String field names to include in the written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addIncludedFields(Map<Class<?>, Collection<String>> includedFields) {
        throwIfBuilt();

        // Need your own Set instance here per Class, keep no reference to excludedFields parameter.
        for (Map.Entry<Class<?>, Collection<String>> entry : includedFields.entrySet()) {
            addIncludedFields(entry.getKey(), entry.getValue());
        }
        return this;
    }

    private Map<Class<?>, Set<String>> getClassSetMapFields(Map<Class<?>, Set<String>> fieldSet) {
        if (built) {
            return fieldSet;
        } else {
            Map<Class<?>, Set<String>> copy = new LinkedHashMap<>();
            for (Map.Entry<Class<?>, Set<String>> entry : fieldSet.entrySet()) {
                copy.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            return copy;
        }
    }

    /**
     * @param clazz Class to add a single field to be excluded.
     * @param excludedField String name of field to exclude from written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addExcludedField(Class<?> clazz, String excludedField) {
        addExcludedFields(clazz, MetaUtils.setOf(excludedField));   // check sealed happens there.
        return this;
    }

    /**
     * @param clazz Class to add a Collection of fields to be excluded in written JSON.
     * @param excludedFields Collection of String name of fields to exclude in written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addExcludedFields(Class<?> clazz, Collection<String> excludedFields) {
        throwIfBuilt();
        this.excludedFields.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(excludedFields);
        return copyFieldsToAccessors(clazz, excludedFields, excludedAccessors);
    }

    private WriteOptions copyFieldsToAccessors(Class<?> clazz, Collection<String> fields, Map<Class<?>, Set<Accessor>> accessors) {
        ClassDescriptors classDescriptors = ClassDescriptors.instance();
        Map<String, Accessor> accessorMap = classDescriptors.getDeepAccessorMap(clazz);

        for (Map.Entry<String, Accessor> acessorEntry : accessorMap.entrySet()) {
            if (fields.contains(acessorEntry.getKey())) {
                accessors.computeIfAbsent(clazz, l -> new LinkedHashSet<>()).add(acessorEntry.getValue());
            }
        }

        return this;
    }

    /**
     * @param excludedFields Map of Class's mapped to Collection of String field names to exclude from written JSON.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addExcludedFields(Map<Class<?>, Collection<String>> excludedFields) {
        throwIfBuilt();
        for (Map.Entry<Class<?>, Collection<String>> entry : excludedFields.entrySet()) {
            addExcludedFields(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Get the list of fields associated to the passed in class that are to be excluded in the written JSON.
     * @param clazz Class for which the fields to be excluded in JSON output will be returned.
     * @return Set of Strings field names associated to the passed in class or an empty
     * Set if no fields.  This is the list of fields to be excluded in the written JSON for the given class.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getExcludedFields(Class<?> clazz) {
        return (Set<String>) getItemsForClass(clazz, excludedFields);
    }

    /**
     * @return Map of all Classes and their associated Sets of fields to be excluded when serialized to JSON.
     */
    public Map<Class<?>, Set<String>> getExcludedFieldsPerAllClasses() {
        return getClassSetMapFields(excludedFields);
    }

    /**
     * Get the list of Accessors associated to the passed in class that are to be excluded in the written JSON.
     * @param clazz Class for which the Accessors to be excluded in JSON output will be returned.
     * @return Set of Strings Accessor names associated to the passed in class or an empty
     * Set if no Accessors.  This is the list of accessors to be excluded in the written JSON for the given class.
     */
    @SuppressWarnings("unchecked")
    public Set<Accessor> getExcludedAccessors(Class<?> clazz) {
        return (Set<Accessor>) getItemsForClass(clazz, excludedAccessors);
    }

    private Set<?> getItemsForClass(Class<?> clazz, Map<?, ? extends Set<?>> classesToSets)
    {
        Set<?> items = classesToSets.get(clazz);
        if (built) {
            // Give back real - protected from modifications
            return items == null ? Collections.unmodifiableSet(new LinkedHashSet<>()) : items;
        } else {
            // Copy - protects original because it's a copy (they can play with it).
            return new LinkedHashSet<>(items == null ? Collections.emptySet() : items);
        }
    }

    public Collection<Accessor> getIncludedAccessorsForClass(final Class<?> c) {
        return getFilteredAccessors(c, includedAccessors);
    }

    public Collection<Accessor> getExcludedAccessorsForClass(final Class<?> c) {
        return getFilteredAccessors(c, excludedAccessors);
    }

    // This is replacing the reverse walking system that compared all cases for distance
    // since we're caching all classes and their sub-objects correctly we should be ok removing
    // the distance check since we walk up the chain of the class being written.
    private static Collection<Accessor> getFilteredAccessors(final Class<?> c, final Map<Class<?>, ? extends Collection<Accessor>> accessors) {
        Class<?> curr = c;
        Set<Accessor> accessorSets = new LinkedHashSet<>();

        while (curr != null) {
            Collection<Accessor> accessorSet = accessors.get(curr);

            if (accessorSet != null) {
                accessorSets.addAll(accessorSet);
            }

            curr = curr.getSuperclass();
        }

        return accessorSets;
    }

    /**
     * Change the date-time format to the ISO date format: "yyyy-MM-dd".  This is for java.util.Data and
     * java.sql.Date.
     * @return WriteOptions for chained access.
     */
    public WriteOptions isoDateFormat() {
        return dateTimeFormat(ISO_DATE_FORMAT);
    }

    /**
     * Change the date-time format to the ISO date-time format: "yyyy-MM-dd'T'HH:mm:ss" (default).  This is
     * for java.util.Date and java.sql.Date.
     * @return WriteOptions for chained access.
     */
    public WriteOptions isoDateTimeFormat() {
        return dateTimeFormat(ISO_DATE_TIME_FORMAT);
    }

    /**
     * Change the java.uti.Date and java.sql.Date format output to a "long," the number of seconds since Jan 1, 1970
     * at midnight. Useful if you do not need to see the JSON, and want to keep the format smaller.  This is for
     * java.util.Date and java.sql.Date.
     * @return WriteOptions for chained access.
     */
    public WriteOptions longDateFormat() {
        throwIfBuilt();
        addCustomWrittenClass(Date.class, new Writers.DateAsLongWriter());
        return this;
    }

    /**
     * @return boolean true if java.util.Date and java.sql.Date's are being written in long (numeric) format.
     */
    public boolean isLongDateFormat() {
        Object a = customWrittenClasses.get(Date.class);
        if (a == null) {
            return false;
        }
        boolean answer = Writers.DateAsLongWriter.class.equals(a.getClass());
        return answer;
    }
    /**
     * Change the date-time format to the passed in format.  The format pattens can be found in the Java Doc
     * for the java.time.format.DateTimeFormatter class.  There are many constants you can use, as well as
     * the definition of how to construct your own patterns.  This is for java.util.Date and java.sql.Date.
     * @return WriteOptions for chained access.
     */
    public WriteOptions dateTimeFormat(String format) {
        throwIfBuilt();
        addCustomWrittenClass(Date.class, new Writers.DateWriter(format));
        return this;
    }

    /**
     * @param clazz Class to check to see if it is non-referenceable.  Non-referenceable classes will always create
     *              a new instance when read in and never use @id/@ref. This uses more memory when the JSON is read in,
     *              as there will be a separate instance in memory for each occurrence. There are certain classes that
     *              json-io automatically treats as non-referenceable, like Strings, Enums, Class, and any Number
     *              instance (BigDecimal, AtomicLong, etc.)  You can add to this list. Often, non-referenceable classes
     *              are useful for classes that can be defined in one line as a JSON, like a LocalDateTime, for example.
     * @return boolean true if the passed in class is considered a non-referenceable class.
     */
    public boolean isNonReferenceableClass(Class<?> clazz) {
        return nonRefClasses.contains(clazz) ||     // Covers primitives, primitive wrappers, Atomic*, Big*, String
                Number.class.isAssignableFrom(clazz) ||
                Date.class.isAssignableFrom(clazz) ||
                clazz.isEnum();
    }

    /**
     * @return Collection of classes specifically listed as Logical Primitives.  In addition to the return
     * classes, derivatives of Number and Date are also considered Logical Primitives by json-io.
     */
    public Collection<Class<?>> getNonReferenceableClasses()
    {
        return built ? nonRefClasses : new LinkedHashSet<>(nonRefClasses);
    }

    /**
     * @param clazz class to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *              class added here will never use an @id/@ref pair.  The downside, is that when read,
     *              each instance, even if the same as another, will use memory.
     * @return WriteOptions for chained access.
     */
    public WriteOptions addNonReferenceableClass(Class<?> clazz) {
        throwIfBuilt();
        nonRefClasses.add(clazz);
        return this;
    }

    /**
     * Seal the instance of this class so that no more changes can be made to it.
     * @return WriteOptions for chained access.
     */
    @SuppressWarnings("unchecked")
    public WriteOptions build() {
        includedFields = (Map<Class<?>, Set<String>>) dupe(includedFields, true);
        includedAccessors = (Map<Class<?>, Set<Accessor>>) dupe(includedAccessors, true);
        excludedFields = (Map<Class<?>, Set<String>>) dupe(excludedFields, true);
        excludedAccessors = (Map<Class<?>, Set<Accessor>>) dupe(excludedAccessors, true);
        aliasTypeNames = Collections.unmodifiableMap(new LinkedHashMap<>(aliasTypeNames));
        notCustomWrittenClasses = Collections.unmodifiableSet(new LinkedHashSet<>(notCustomWrittenClasses));
        nonRefClasses = Collections.unmodifiableSet(new LinkedHashSet<>(nonRefClasses));
        customWrittenClasses = Collections.unmodifiableMap(new LinkedHashMap<>(customWrittenClasses));
        this.built = true;
        return this;
    }

    private static Map<Class<?>,? extends Set<?>> dupe(Map<Class<?>, ? extends Set<?>> other, boolean unmodifiable)
    {
        Map<Class<?>, Set<?>> newItemsAssocToClass = new LinkedHashMap<>();
        for (Map.Entry<Class<?>,?> entry : other.entrySet()) {
            Set<?> itemsAssocToClass = new LinkedHashSet<>((Collection<?>)entry.getValue());
            if (unmodifiable) {
                newItemsAssocToClass.computeIfAbsent(entry.getKey(), k -> Collections.unmodifiableSet(itemsAssocToClass));
            } else {
                newItemsAssocToClass.computeIfAbsent(entry.getKey(), k -> itemsAssocToClass);
            }
        }
        if (unmodifiable) {
            return Collections.unmodifiableMap(newItemsAssocToClass);
        } else {
            return newItemsAssocToClass;
        }
    }

    /**
     * @return boolean true if the instance of this class is built (read-only), otherwise false is returned,
     * indicating that changes can still be made to this WriteOptions instance.
     */
    public boolean isBuilt() {
        return built;
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    private static final class NullClass implements JsonWriter.JsonClassWriter {
    }

    private static final NullClass nullWriter = new NullClass();

    /**
     * Fetch the custom writer for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom writer
     * and store that result.
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter for the custom class (if one exists), null otherwise.
     */
    public JsonWriter.JsonClassWriter getCustomWriter(Class<?> c) {
        JsonWriter.JsonClassWriter writer = writerCache.get(c);
        if (writer == null) {
            writer = forceGetCustomWriter(c);
            writerCache.put(c, writer);
        }

        if (writer != nullWriter) {
            return writer;
        }

        writer = MetaUtils.getClassIfEnum(c).isPresent() ? enumWriter : nullWriter;
        writerCache.put(c, writer);

        return writer == nullWriter ? null : writer;
    }

    /**
     * Fetch the custom writer for the passed in Class.  This method always fetches the custom writer, doing
     * the complicated inheritance distance checking.  This method is only called when a cache miss has happened.
     * A sentinel 'nullWriter' is returned when no custom writer is found.  This prevents future cache misses
     * from re-attempting to find custom writers for classes that do not have a custom writer.
     *
     * @param c Class of object for which fetch a custom writer
     * @return JsonClassWriter for the custom class (if one exists), nullWriter otherwise.
     */
    private JsonWriter.JsonClassWriter forceGetCustomWriter(Class<?> c) {
        JsonWriter.JsonClassWriter closestWriter = nullWriter;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class<?>, JsonWriter.JsonClassWriter> entry : customWrittenClasses.entrySet()) {
            Class<?> clz = entry.getKey();
            if (clz == c) {
                return entry.getValue();
            }
            int distance = MetaUtils.computeInheritanceDistance(c, clz);
            if (distance != -1 && distance < minDistance) {
                minDistance = distance;
                closestWriter = entry.getValue();
            }
        }
        return closestWriter;
    }

    /**
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     * @return Map<Class<?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, JsonWriter.JsonClassWriter> loadWriters() {
        Map<String, String> map = new LinkedHashMap<>();
        MetaUtils.loadMapDefinition(map, "customWriters.txt");
        Map<Class<?>, JsonWriter.JsonClassWriter> writers = new HashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String writerClassName = entry.getValue();
            Class<?> clazz = MetaUtils.classForName(className, classLoader);
            if (clazz == null)
            {
                System.out.println("Class: " + className + " not defined in the JVM, so custom writer: " + writerClassName + ", will not be used.");
                continue;
            }
            Class<JsonWriter.JsonClassWriter> customWriter = (Class<JsonWriter.JsonClassWriter>) MetaUtils.classForName(writerClassName, classLoader);
            if (customWriter == null)
            {
                throw new JsonIoException("Note: class not found (custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
            try {
                JsonWriter.JsonClassWriter writer = (JsonWriter.JsonClassWriter) MetaUtils.newInstance(customWriter, null);
                writers.put(clazz, writer);
            }
            catch (Exception e) {
                throw new JsonIoException("Note: class failed to instantiate (a custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
        }
        return writers;
    }

    /**
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     * @return Set<Class<?>> which is the loaded from resource/nonRefs.txt and verified to exist in JVM.
     */
    static Set<Class<?>> loadNonRefs() {
        Set<String> set = new LinkedHashSet<>();
        Set<Class<?>> nonRefs = new LinkedHashSet<>();
        MetaUtils.loadSetDefinition(set, "nonRefs.txt");
        set.forEach((className) -> {
            Class<?> clazz = MetaUtils.classForName(className, WriteOptions.class.getClassLoader());
            if (clazz == null) {
                throw new JsonIoException("Class: " + className + " undefined.  Cannot be used as non-referenceable class, listed in resources/nonRefs.txt");
            }
            nonRefs.add(clazz);
        });
        return nonRefs;
    }
}
