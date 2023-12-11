package com.cedarsoftware.util.io;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.factories.MethodAccessorFactory;
import com.cedarsoftware.util.reflect.filters.FieldFilter;
import com.cedarsoftware.util.reflect.filters.MethodFilter;
import com.cedarsoftware.util.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.util.reflect.filters.field.GroovyFieldFilter;
import com.cedarsoftware.util.reflect.filters.field.StaticFieldFilter;
import com.cedarsoftware.util.reflect.filters.method.AccessorMethodFilter;
import com.cedarsoftware.util.reflect.filters.method.ModifierMethodFilter;

/**
 * Builder class for building the writeOptions.
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
public class WriteOptionsBuilder {
    // Constants
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    // Properties
    private boolean shortMetaKeys;
    private WriteOptions.ShowType showTypeInfo = WriteOptions.ShowType.MINIMAL;
    private boolean prettyPrint = false;
    private boolean writeLongsAsStrings = false;
    private boolean skipNullFields = false;
    private boolean forceMapOutputAsTwoArrays = false;
    private boolean allowNanAndInfinity = false;
    private boolean enumPublicFieldsOnly = false;
    private boolean closeStream = true;
    private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();
    private ClassLoader classLoader = WriteOptions.class.getClassLoader();
    private Map<Class<?>, Set<String>> includedFieldNames = new ConcurrentHashMap<>();
    private Map<String, String> aliasTypeNames = new ConcurrentHashMap<>();
    private Set<Class<?>> notCustomWrittenClasses = new LinkedHashSet<>();
    private Set<Class<?>> nonRefClasses = new LinkedHashSet<>();

    private final Map<Class<?>, Map<String, String>> nonStandardMappings;

    private final List<FieldFilter> fieldFilters;
    private final List<MethodFilter> methodFilters;
    private final List<AccessorFactory> accessorFactories;

    private final Set<String> filteredMethodNames;

    private final Map<Class<?>, Set<String>> excludedFieldNames;

    private Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses = new ConcurrentHashMap<>();

    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = new LinkedHashSet<>();

    static {
        BASE_ALIAS_MAPPINGS.putAll(MetaUtils.loadMapDefinition("aliases.txt"));
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
    public WriteOptionsBuilder() {
        this.shortMetaKeys = false;
        this.showTypeInfo = WriteOptions.ShowType.MINIMAL;
        this.prettyPrint = false;
        this.writeLongsAsStrings = false;
        this.skipNullFields = false;
        this.forceMapOutputAsTwoArrays = false;
        this.allowNanAndInfinity = false;
        this.enumPublicFieldsOnly = false;
        this.closeStream = true;


        this.filteredMethodNames = MetaUtils.loadSetDefinition("excludedAccessorMethods.txt");
        this.excludedFieldNames = MetaUtils.loadClassToSetOfStrings("excludedAccessorFields.txt");

        this.nonStandardMappings = loadNonStandardMethodNames();

        this.fieldFilters = new ArrayList<>();
        this.fieldFilters.add(new StaticFieldFilter());
        this.fieldFilters.add(new GroovyFieldFilter());
        this.fieldFilters.add(new EnumFieldFilter());

        this.methodFilters = new ArrayList<>();
        this.methodFilters.add(new AccessorMethodFilter());
        this.methodFilters.add(new ModifierMethodFilter(Modifier.PUBLIC));

        this.accessorFactories = new ArrayList<>();
        this.accessorFactories.add(new MethodAccessorFactory());

        // Start with all BASE_ALIAS_MAPPINGS (more aliases can be added to this instance, and more aliases
        // can be added to the BASE_ALIAS_MAPPINGS via the static method, so that all instances get them.)
        aliasTypeNames.putAll(BASE_ALIAS_MAPPINGS);
        customWrittenClasses.putAll(BASE_WRITERS);
        nonRefClasses.addAll(BASE_NON_REFS);
    }

    public WriteOptionsBuilder(WriteOptions options) {
        this.shortMetaKeys = options.isShortMetaKeys();
        this.showTypeInfo = options.showTypeInfo;
        this.prettyPrint = options.isPrettyPrint();
        this.writeLongsAsStrings = options.isWriteLongsAsStrings();
        this.skipNullFields = options.isSkipNullFields();
        this.forceMapOutputAsTwoArrays = options.isForceMapOutputAsTwoArrays();
        this.allowNanAndInfinity = options.isAllowNanAndInfinity();
        this.enumPublicFieldsOnly = options.isEnumPublicFieldsOnly();
        this.closeStream = options.isCloseStream();
        this.enumWriter = options.isWriteEnumAsString() ? new Writers.EnumsAsStringWriter() : nullWriter;

        this.filteredMethodNames = new LinkedHashSet<>(options.filteredMethodNames);

        this.notCustomWrittenClasses = new LinkedHashSet<>(options.notCustomWrittenClasses);
        this.nonRefClasses.addAll(options.nonRefClasses);

        this.aliasTypeNames = new LinkedHashMap<>(options.aliasTypeNames());
        this.customWrittenClasses = new LinkedHashMap<>(options.getCustomWrittenClasses());

        this.excludedFieldNames = MetaUtils.cloneMapOfSets(options.excludedFieldNames, false);
        this.includedFieldNames = MetaUtils.cloneMapOfSets(options.includedFieldNames, false);
        this.nonStandardMappings = MetaUtils.cloneMapOfMaps(options.nonStandardMappings, false);

        this.fieldFilters = options.fieldFilters.stream()
                .map(FieldFilter::createCopy)
                .collect(Collectors.toList());

        this.methodFilters = options.methodFilters.stream()
                .map(MethodFilter::createCopy)
                .collect(Collectors.toList());

        this.accessorFactories = options.accessorFactories.stream()
                .map(AccessorFactory::createCopy)
                .collect(Collectors.toList());
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.
     *
     * @param clazz Class that will be aliased by a shorter name in the JSON.
     * @param alias Shorter alias name, for example, "ArrayList" as opposed to "java.util.ArrayList"
     */
    public static void addPermanentAlias(Class<?> clazz, String alias) {
        BASE_ALIAS_MAPPINGS.put(clazz.getName(), alias);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.
     *
     * @param clazz Class that will be aliased from fullyQualifiedName -> simpleName
     */
    public static void addPermanentAlias(Class<?> clazz) {
        BASE_ALIAS_MAPPINGS.put(clazz.getName(), clazz.getSimpleName());
    }

    /**
     * Call this method to add a permanent (JVM lifetime) class that should not be treated as referencable
     * when being written out to JSON.  This means it will never have an @id nor @ref.  This feature is
     * useful for small, immutable classes.
     *
     * @param clazz Class that will no longer be treated as referenceable when being written to JSON.
     */
    public static void addPermanentNonRef(Class<?> clazz) {
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
     * @param clazz  Class to assign a custom JSON writer to
     * @param writer The JsonClassWriter which will write the custom JSON format of class.
     */
    public static void addPermanentWriter(Class<?> clazz, JsonWriter.JsonClassWriter writer) {
        BASE_WRITERS.put(clazz, writer);
    }

    /**
     * @return ClassLoader to be used when writing JSON to resolve String named classes.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @param classLoader ClassLoader to use when writing JSON to resolve String named classes.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder classLoader(ClassLoader classLoader) {
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder shortMetaKeys(boolean shortMetaKeys) {
        this.shortMetaKeys = shortMetaKeys;
        return this;
    }

    /**
     * Alias Type Names, e.g. "ArrayList" instead of "java.util.ArrayList".
     *
     * @param typeName String name of type to fetch alias for.  There are no default aliases.
     * @return String alias name or null if type name is not aliased.
     */
    public String getTypeNameAlias(String typeName) {
        String alias = aliasTypeNames.get(typeName);
        return alias == null ? typeName : alias;
    }

    /**
     * @param aliasTypeNames Map containing String class names to alias names.  The passed in Map will
     *                       be copied, and be the new baseline settings.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder aliasTypeNames(Map<String, String> aliasTypeNames) {
        aliasTypeNames.forEach(this::addUniqueAlias);
        return this;
    }

    /**
     * @param type  Class to alias
     * @param alias String shorter name to use, typically.
     * @return ReadOptions for chained access.
     */
    public WriteOptionsBuilder aliasTypeName(Class<?> type, String alias) {
        aliasTypeNames.put(type.getName(), alias);
        return this;
    }

    /**
     * @param typeName String class name
     * @param alias    String shorter name to use, typically.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder aliasTypeName(String typeName, String alias) {
        addUniqueAlias(typeName, alias);
        return this;
    }

    /**
     * Since we are swapping keys/values, we must check for duplicate values (which are now keys).
     *
     * @param typeName String fully qualified class name.
     * @param alias    String shorter alias name.
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
    public WriteOptionsBuilder withExtendedAliases() {
        Map<String, String> extendedAliases = MetaUtils.loadMapDefinition("extendedAliases.txt");
        extendedAliases.forEach((key, value) -> aliasTypeNames.putIfAbsent(key, value));
        return this;
    }

    /**
     * Set to always show type
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoAlways() {
        this.showTypeInfo = WriteOptions.ShowType.ALWAYS;
        return this;
    }

    /**
     * Set to never show type
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoNever() {
        this.showTypeInfo = WriteOptions.ShowType.NEVER;
        return this;
    }

    /**
     * Set to show minimal type.  This means that when the type of an object can be inferred, a type field will not
     * be output.  A Field that points to an instance of the same time, or a typed [] of objects don't need the type
     * info.  However, an Object[], a Collection with no generics, the reader will need to know what type the JSON
     * object is, in order to instantiate the write Java class to which the information will be copied.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoMinimal() {
        this.showTypeInfo = WriteOptions.ShowType.MINIMAL;
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder prettyPrint(boolean prettyPrint) {
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeLongsAsStrings(boolean writeLongsAsStrings) {
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
     *                       to the JSON, false will allow the field to still be written.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder skipNullFields(boolean skipNullFields) {
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder forceMapOutputAsTwoArrays(boolean forceMapOutputAsTwoArrays) {
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder allowNanAndInfinity(boolean allowNanAndInfinity) {
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
     *
     * @return WriteOptionsBuilder for chained access.per
     */
    public WriteOptionsBuilder writeEnumsAsString() {
        this.enumWriter = new Writers.EnumsAsStringWriter();
        return this;
    }

    /**
     * Option to write out all the member fields of an enum.  You can also filter the
     * field to write out only the public fields on the enum.
     *
     * @param writePublicFieldsOnly boolean, only write out the public fields when writing enums as objects
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeEnumAsJsonObject(boolean writePublicFieldsOnly) {
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
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder closeStream(boolean closeStream) {
        this.closeStream = closeStream;
        return this;
    }

    /**
     * @param customWrittenClasses Map of Class to JsonWriter.JsonClassWriter.  Establish the passed in Map as the
     *                             established Map of custom writers to be used when writing JSON. Using this method
     *                             more than once, will set the custom writers to only the values from the Set in
     *                             the last call made.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setCustomWrittenClasses(Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses) {
        this.customWrittenClasses.clear();
        addCustomWrittenClasses(customWrittenClasses);
        return this;
    }

    /**
     * @param customWrittenClasses Map of Class to JsonWriter.JsonClassWriter.  Adds all custom writers into the custom
     *                             writers map.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addCustomWrittenClasses(Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses) {
        this.customWrittenClasses.putAll(customWrittenClasses);
        return this;
    }

    /**
     * @param clazz        Class to add a custom writer for.
     * @param customWriter JsonClassWriter to use when the passed in Class is encountered during serialization.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addCustomWrittenClass(Class<?> clazz, JsonWriter.JsonClassWriter customWriter) {
        customWrittenClasses.put(clazz, customWriter);
        return this;
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
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom writer (that could happen through inheritance).
     *
     * @param notCustomClass Class to add to the not-customized list.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNotCustomWrittenClass(Class<?> notCustomClass) {
        notCustomWrittenClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                         will be dropped and this Collection will establish the new list.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setNotCustomWrittenClasses(Collection<Class<?>> notCustomClasses) {
        notCustomWrittenClasses.clear();
        notCustomWrittenClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @param clazz         Class to add a single field to be included in the written JSON.
     * @param includedField String name of field to include in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedField(Class<?> clazz, String includedField) {
        addIncludedFields(clazz, MetaUtils.setOf(includedField));    // checked sealed happens in here.
        return this;
    }

    /**
     * @param clazz          Class to add a Collection of fields to be included in written JSON.
     * @param includedFields Collection of String name of fields to include in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedFields(Class<?> clazz, Collection<String> includedFields) {
        this.includedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(includedFields);
        return this;
    }

    /**
     * @param includedFields Map of Class's mapped to Collection of String field names to include in the written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedFields(Map<Class<?>, Collection<String>> includedFields) {

        // Need your own Set instance here per Class, keep no reference to excludedFields parameter.
        for (Map.Entry<Class<?>, Collection<String>> entry : includedFields.entrySet()) {
            addIncludedFields(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * @param clazz         Class to add a single field to be excluded.
     * @param excludedField String name of field to exclude from written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedField(Class<?> clazz, String excludedField) {
        this.excludedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).add(excludedField);
        return this;
    }

    /**
     * @param clazz          Class to add a Collection of fields to be excluded in written JSON.
     * @param excludedFields Collection of String name of fields to exclude in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedFields(Class<?> clazz, Collection<String> excludedFields) {
        this.excludedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(excludedFields);
        return this;
    }

    /**
     * @param excludedFieldNames Map of Class's mapped to Collection of String field names to exclude from written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedFields(Map<Class<?>, Collection<String>> excludedFieldNames) {
        for (Map.Entry<Class<?>, Collection<String>> entry : excludedFieldNames.entrySet()) {
            addExcludedFields(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Change the date-time format to the ISO date format: "yyyy-MM-dd".  This is for java.util.Data and
     * java.sql.Date.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder isoDateFormat() {
        return dateTimeFormat(ISO_DATE_FORMAT);
    }

    /**
     * Change the date-time format to the ISO date-time format: "yyyy-MM-dd'T'HH:mm:ss" (default).  This is
     * for java.util.Date and java.sql.Date.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder isoDateTimeFormat() {
        return dateTimeFormat(ISO_DATE_TIME_FORMAT);
    }

    /**
     * Change the java.uti.Date and java.sql.Date format output to a "long," the number of seconds since Jan 1, 1970
     * at midnight. Useful if you do not need to see the JSON, and want to keep the format smaller.  This is for
     * java.util.Date and java.sql.Date.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder longDateFormat() {
        addCustomWrittenClass(Date.class, new Writers.DateAsLongWriter());
        return this;
    }

    /**
     * Change the date-time format to the passed in format.  The format pattens can be found in the Java Doc
     * for the java.time.format.DateTimeFormatter class.  There are many constants you can use, as well as
     * the definition of how to construct your own patterns.  This is for java.util.Date and java.sql.Date.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder dateTimeFormat(String format) {
        addCustomWrittenClass(Date.class, new Writers.DateWriter(format));
        return this;
    }

    /**
     * @param methodNames Replaces the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setFilteredMethodNames(Collection<String> methodNames) {
        Convention.throwIfNull(methodNames, "methodNames cannot be null");
        filteredMethodNames.clear();
        filteredMethodNames.addAll(methodNames);
        return this;
    }

    /**
     * @param methodNames Adds to the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addFilteredMethodNames(Collection<String> methodNames) {
        Convention.throwIfNull(methodNames, "methodNames cannot be null");
        filteredMethodNames.addAll(methodNames);
        return this;
    }

    /**
     * @param methodName Adds to the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addFilteredMethodName(String methodName) {
        Convention.throwIfNull(methodName, "methodName cannot be null");
        filteredMethodNames.add(methodName);
        return this;
    }

    /**
     * @param nonStandardMappings Replaces the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    WriteOptionsBuilder setNonStandardMappings(Map<Class<?>, Map<String, String>> nonStandardMappings) {
        Convention.throwIfNull(nonStandardMappings, "nonStandardMappings cannot be null");

        //TODO:  make copy in constructor?
        this.nonStandardMappings.clear();
        this.nonStandardMappings.putAll(nonStandardMappings);
        return this;
    }

    /**
     * @param nonStandardMappings Adds to the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonStandardMappings(Map<Class<?>, Map<String, String>> nonStandardMappings) {
        Convention.throwIfNull(nonStandardMappings, "nonStandardMappings cannot be null");
        this.nonStandardMappings.putAll(nonStandardMappings);
        return this;
    }

    /**
     * @param methodName Adds to the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonStandardMapping(Class<?> c, String fieldName, String methodName) {
        Convention.throwIfNull(c, "class cannot be null");
        Convention.throwIfNull(fieldName, "fieldName cannot be null");
        Convention.throwIfNull(methodName, "methodName cannot be null");
        nonStandardMappings.computeIfAbsent(c, cls -> new LinkedHashMap<>()).put(fieldName, methodName);
        return this;
    }

    /**
     * @param classes Replaces the collection of classes to treat as non-referenceable with the given class.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setNonReferenceableClasses(Collection<Class<?>> classes) {
        Convention.throwIfNull(classes, "classes cannot be null");
        nonRefClasses.clear();
        nonRefClasses.addAll(classes);
        return this;
    }

    /**
     * @param classes classes to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *                class added here will never use an @id/@ref pair.  The downside, is that when read,
     *                each instance, even if the same as another, will use memory.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonReferenceableClasses(Collection<Class<?>> classes) {
        Convention.throwIfNull(classes, "classes cannot be null");
        nonRefClasses.addAll(classes);
        return this;
    }

    /**
     * @param clazz class to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *              class added here will never use an @id/@ref pair.  The downside, is that when read,
     *              each instance, even if the same as another, will use memory.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonReferenceableClass(Class<?> clazz) {
        Convention.throwIfNull(clazz, "clazz cannot be null");
        nonRefClasses.add(clazz);
        return this;
    }

    /**
     * Seal the instance of this class so that no more changes can be made to it.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    @SuppressWarnings("unchecked")
    public WriteOptions build() {
        return new WriteOptions(shortMetaKeys,
                prettyPrint,
                writeLongsAsStrings,
                skipNullFields,
                allowNanAndInfinity,
                forceMapOutputAsTwoArrays,
                enumPublicFieldsOnly,
                closeStream,
                showTypeInfo,
                classLoader,
                enumWriter,
                notCustomWrittenClasses,
                nonRefClasses,
                filteredMethodNames,
                aliasTypeNames,
                customWrittenClasses,
                excludedFieldNames,
                includedFieldNames,
                nonStandardMappings,
                fieldFilters,
                methodFilters,
                accessorFactories);
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
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, JsonWriter.JsonClassWriter> loadWriters() {
        Map<String, String> map = MetaUtils.loadMapDefinition("customWriters.txt");
        Map<Class<?>, JsonWriter.JsonClassWriter> writers = new HashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String writerClassName = entry.getValue();
            Class<?> clazz = MetaUtils.classForName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " not defined in the JVM, so custom writer: " + writerClassName + ", will not be used.");
                continue;
            }
            Class<JsonWriter.JsonClassWriter> customWriter = (Class<JsonWriter.JsonClassWriter>) MetaUtils.classForName(writerClassName, classLoader);
            if (customWriter == null) {
                throw new JsonIoException("Note: class not found (custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
            try {
                JsonWriter.JsonClassWriter writer = (JsonWriter.JsonClassWriter) MetaUtils.newInstance(customWriter, null);
                writers.put(clazz, writer);
            } catch (Exception e) {
                throw new JsonIoException("Note: class failed to instantiate (a custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
        }
        return writers;
    }

    /**
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, Map<String, String>> loadNonStandardMethodNames() {
        Map<String, String> map = MetaUtils.loadMapDefinition("nonStandardAccessors.txt");
        Map<Class<?>, Map<String, String>> nonStandardMapping = new ConcurrentHashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String mappings = entry.getValue();
            Class<?> clazz = MetaUtils.classForName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " not defined in the JVM");
                continue;
            }

            Map<String, String> mapping = nonStandardMapping.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
            for (String split : mappings.split(",")) {
                String[] parts = split.split(":");
                mapping.put(parts[0].trim(), parts[1].trim());
            }
        }
        return nonStandardMapping;
    }


    /**
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     *
     * @return Set<Class < ?>> which is the loaded from resource/nonRefs.txt and verified to exist in JVM.
     */
    static Set<Class<?>> loadNonRefs() {
        Set<Class<?>> nonRefs = new LinkedHashSet<>();
        Set<String> set = MetaUtils.loadSetDefinition("nonRefs.txt");
        set.forEach((className) -> {
            Class<?> clazz = MetaUtils.classForName(className, WriteOptions.class.getClassLoader());
            if (clazz == null) {
                throw new JsonIoException("Class: " + className + " undefined.  Cannot be used as non-referenceable class, listed in resources/nonRefs.txt");
            }
            nonRefs.add(clazz);
        });
        return nonRefs;
    }


    public boolean addFilter(FieldFilter filter) {
        return this.fieldFilters.add(filter);
    }

    public boolean removeFilter(FieldFilter filter) {
        return this.fieldFilters.remove(filter);
    }

}
