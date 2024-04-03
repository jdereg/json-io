package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import com.cedarsoftware.io.reflect.factories.GetMethodAccessorFactory;
import com.cedarsoftware.io.reflect.factories.IsMethodAccessorFactory;
import com.cedarsoftware.io.reflect.filters.FieldFilter;
import com.cedarsoftware.io.reflect.filters.MethodFilter;
import com.cedarsoftware.io.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.io.reflect.filters.field.StaticFieldFilter;
import com.cedarsoftware.io.reflect.filters.method.AccessorMethodFilter;
import com.cedarsoftware.io.reflect.filters.method.ModifierMethodFilter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.ReflectionUtils;

/**
 * Builder class for building the writeOptions.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
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
 *         limitations under the License.*
 */
public class WriteOptionsBuilder {
    // Constants
    public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private final DefaultWriteOptions options;
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = ConcurrentHashMap.newKeySet();
    static final Map<Class<?>, Set<String>> BASE_EXCLUDED_FIELD_NAMES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, String>> BASE_NONSTANDARD_MAPPINGS = new ConcurrentHashMap<>();
    
    static {
        BASE_ALIAS_MAPPINGS.putAll(MetaUtils.loadMapDefinition("config/aliases.txt"));
        BASE_WRITERS.putAll(loadWriters());
        BASE_NON_REFS.addAll(loadNonRefs());
        BASE_EXCLUDED_FIELD_NAMES.putAll(MetaUtils.loadClassToSetOfStrings("config/ignoredFields.txt"));
        BASE_NONSTANDARD_MAPPINGS.putAll(MetaUtils.loadNonStandardMethodNames("config/nonStandardAccessors.txt"));
    }

    /**
     * Start with default options
     */
    public WriteOptionsBuilder() {
        options = new DefaultWriteOptions();

        options.fieldFilters.add(new StaticFieldFilter());
        options.fieldFilters.add(new EnumFieldFilter());

        options.methodFilters.add(new AccessorMethodFilter());
        options.methodFilters.add(new ModifierMethodFilter(Modifier.PUBLIC));

        options.accessorFactories.add(new GetMethodAccessorFactory());
        options.accessorFactories.add(new IsMethodAccessorFactory());

        // Start with all BASE_ALIAS_MAPPINGS (more aliases can be added to this instance, and more aliases
        // can be added to the BASE_ALIAS_MAPPINGS via the static method, so that all instances get them.)
        options.nonStandardMappings.putAll(BASE_NONSTANDARD_MAPPINGS);
        options.aliasTypeNames.putAll(BASE_ALIAS_MAPPINGS);
        options.customWrittenClasses.putAll(BASE_WRITERS);
        options.nonRefClasses.addAll(BASE_NON_REFS);
        options.excludedFieldNames.putAll(BASE_EXCLUDED_FIELD_NAMES);
    }

    /**
     * Copy another WriteOptions as a starting point.  If null is passed in, then you get the default options.
     */
    public WriteOptionsBuilder(WriteOptions copy) {
        this();
        if (copy != null) {
            DefaultWriteOptions other = (DefaultWriteOptions) copy;

            options.includedFieldNames.clear();
            options.includedFieldNames.putAll(other.includedFieldNames);

            options.nonStandardMappings.clear();
            options.nonStandardMappings.putAll(other.nonStandardMappings);

            options.aliasTypeNames.clear();
            options.aliasTypeNames.putAll(other.aliasTypeNames);

            options.excludedFieldNames.clear();
            options.excludedFieldNames.putAll(other.excludedFieldNames);

            options.customWrittenClasses.clear();
            options.customWrittenClasses.putAll(other.customWrittenClasses);

            options.notCustomWrittenClasses.clear();
            options.notCustomWrittenClasses.addAll(other.notCustomWrittenClasses);

            options.nonRefClasses.clear();
            options.nonRefClasses.addAll(other.nonRefClasses);

            options.fieldFilters.clear();
            options.fieldFilters.addAll(other.fieldFilters);

            options.methodFilters.clear();
            options.methodFilters.addAll(other.methodFilters);

            options.accessorFactories.clear();
            options.accessorFactories.addAll(other.accessorFactories);
        }
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
     * @param loader ClassLoader to use when writing JSON to resolve String named classes.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder classLoader(ClassLoader loader) {
        options.classLoader = loader;
        return this;
    }

    /**
     * @param shortMetaKeys boolean true to turn on short meta-keys, false for long.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder shortMetaKeys(boolean shortMetaKeys) {
        options.shortMetaKeys = shortMetaKeys;
        return this;
    }

    /**
     * @param aliases Map containing String class names to alias names.  The passed in Map will
     *                       be copied, and be the new baseline settings.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder aliasTypeNames(Map<String, String> aliases) {
        aliases.forEach(this::addUniqueAlias);
        return this;
    }
    
    /**
     * @param type  Class to alias
     * @param alias String shorter name to use, typically.
     * @return ReadOptions for chained access.
     */
    public WriteOptionsBuilder aliasTypeName(Class<?> type, String alias) {
        options.aliasTypeNames.put(type.getName(), alias);
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
     * @param typeName String fully qualified class name.
     * @param alias String shorter alias name.
     */
    private void addUniqueAlias(String typeName, String alias) {
        Convention.throwIfClassNotFound(typeName, options.classLoader);
        Convention.throwIfKeyExists(options.aliasTypeNames, typeName, "Tried to create @type alias" + typeName + " -> " + alias + ", but it is already aliased to: " + options.aliasTypeNames.get(typeName));

        options.aliasTypeNames.put(typeName, alias);
    }

    /**
     * Add all the aliases in the config/extendedAliases.txt to the alias list.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder withExtendedAliases() {
        Map<String, String> extendedAliases = MetaUtils.loadMapDefinition("config/extendedAliases.txt");
        extendedAliases.forEach((key, value) -> options.aliasTypeNames.putIfAbsent(key, value));
        return this;
    }

    /**
     * Set to always show type
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoAlways() {
        options.showTypeInfo = WriteOptions.ShowType.ALWAYS;
        return this;
    }

    /**
     * Set to never show type
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoNever() {
        options.showTypeInfo = WriteOptions.ShowType.NEVER;
        return this;
    }

    /**
     * Set to show minimal type.  This means that when the type of object can be inferred, a type field will not
     * be output.  A Field that points to an instance of the same time, or a typed [] of objects don't need the type
     * info.  However, an Object[], a Collection with no generics, the reader will need to know what type the JSON
     * object is, in order to instantiate the write Java class to which the information will be copied.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder showTypeInfoMinimal() {
        options.showTypeInfo = WriteOptions.ShowType.MINIMAL;
        return this;
    }

    /**
     * @param prettyPrint boolean 'prettyPrint' setting, true to turn on, false will turn off.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder prettyPrint(boolean prettyPrint) {
        options.prettyPrint = prettyPrint;
        return this;
    }

    /**
     * @param writeLongsAsStrings boolean true to turn on writing longs as Strings, false to write them as
     *                            native JSON longs.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeLongsAsStrings(boolean writeLongsAsStrings) {
        options.writeLongsAsStrings = writeLongsAsStrings;
        return this;
    }

    /**
     * @param skipNullFields boolean setting, where true indicates fields with null values will not be written
     *                       to the JSON, false will allow the field to still be written.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder skipNullFields(boolean skipNullFields) {
        options.skipNullFields = skipNullFields;
        return this;
    }

    /**
     * @param forceMapOutputAsTwoArrays boolean 'forceMapOutputAsTwoArrays' setting.  true will force Java Maps to be
     *                                  written out as two parallel arrays, once for keys, one array for values.
     *                                  false will write out one JSON { } object, if all keys are Strings.  If not,
     *                                  then the Map will be output as two parallel arrays (@keys:[...], @values:[...]).
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder forceMapOutputAsTwoArrays(boolean forceMapOutputAsTwoArrays) {
        options.forceMapOutputAsTwoArrays = forceMapOutputAsTwoArrays;
        return this;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow
     *                            Double and Floats to be output as NAN and INFINITY, false
     *                            and these values will come across as null.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder allowNanAndInfinity(boolean allowNanAndInfinity) {
        options.allowNanAndInfinity = allowNanAndInfinity;
        return this;
    }

    /**
     * Option to write out enums as a String, it will write out the enum.name() field.
     * This is the default way enums will be written out.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeEnumsAsString() {
        options.enumWriter = new Writers.EnumsAsStringWriter();
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
        options.enumWriter = DefaultWriteOptions.nullWriter;
        options.enumPublicFieldsOnly = writePublicFieldsOnly;
        return this;
    }

    /**
     * @param closeStream boolean set to 'true' to have JsonIo close the OutputStream when it is finished writing
     *                    to it.  The default is 'true'.  If false, the OutputStream will not be closed, allowing
     *                    you to continue writing further.  Example, NDJSON that has new line eliminated JSON
     *                    objects repeated.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder closeStream(boolean closeStream) {
        options.closeStream = closeStream;
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
        options.customWrittenClasses.clear();
        addCustomWrittenClasses(customWrittenClasses);
        return this;
    }

    /**
     * @param customWrittenClasses Map of Class to JsonWriter.JsonClassWriter.  Adds all custom writers into the custom
     *                             writers map.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addCustomWrittenClasses(Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses) {
        options.customWrittenClasses.putAll(customWrittenClasses);
        return this;
    }

    /**
     * @param clazz        Class to add a custom writer for.
     * @param customWriter JsonClassWriter to use when the passed in Class is encountered during serialization.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addCustomWrittenClass(Class<?> clazz, JsonWriter.JsonClassWriter customWriter) {
        options.customWrittenClasses.put(clazz, customWriter);
        return this;
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom writer (that could happen through inheritance).
     *
     * @param notCustomClass Class to add to the not-customized list.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNotCustomWrittenClass(Class<?> notCustomClass) {
        options.notCustomWrittenClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                         will be dropped and this Collection will establish the new list.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setNotCustomWrittenClasses(Collection<Class<?>> notCustomClasses) {
        options.notCustomWrittenClasses.clear();
        options.notCustomWrittenClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @param clazz         Class to add a single field to be included in the written JSON.
     * @param includedFieldName String name of field to include in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedField(Class<?> clazz, String includedFieldName) {
        Convention.throwIfNull(includedFieldName, "includedFieldName cannot be null");
        options.includedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).add(includedFieldName);
        return this;
    }

    /**
     * @param clazz          Class to add a Collection of fields to be included in written JSON.
     * @param includedFieldNames Collection of String name of fields to include in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedFields(Class<?> clazz, Collection<String> includedFieldNames) {
        options.includedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(includedFieldNames);
        return this;
    }

    /**
     * @param includedFieldNames Map of Class's mapped to Collection of String field names to include in the written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addIncludedFields(Map<Class<?>, Collection<String>> includedFieldNames) {
        includedFieldNames.forEach(this::addIncludedFields);
        return this;
    }

    /**
     * @param clazz         Class to add a single field to be excluded.
     * @param excludedFieldName String name of field to exclude from written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedField(Class<?> clazz, String excludedFieldName) {
        options.excludedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).add(excludedFieldName);
        return this;
    }

    /**
     * @param clazz          Class to add a Collection of fields to be excluded in written JSON.
     * @param excludedFields Collection of String name of fields to exclude in written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedFields(Class<?> clazz, Collection<String> excludedFields) {
        options.excludedFieldNames.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(excludedFields);
        return this;
    }

    /**
     * @param excludedFieldNames Map of Class's mapped to Collection of String field names to exclude from written JSON.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addExcludedFields(Map<Class<?>, Collection<String>> excludedFieldNames) {
        excludedFieldNames.forEach(this::addExcludedFields);
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
     * @param nonStandardMappings Replaces the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    WriteOptionsBuilder setNonStandardMappings(Map<Class<?>, Map<String, String>> nonStandardMappings) {
        Convention.throwIfNull(nonStandardMappings, "nonStandardMappings cannot be null");

        options.nonStandardMappings.clear();
        options.nonStandardMappings.putAll(nonStandardMappings);
        return this;
    }

    /**
     * @param nonStandardMappings Adds to the collection of methodNames that are not to be considered as accessors.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonStandardMappings(Map<Class<?>, Map<String, String>> nonStandardMappings) {
        Convention.throwIfNull(nonStandardMappings, "nonStandardMappings cannot be null");
        options.nonStandardMappings.putAll(nonStandardMappings);
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
        options.nonStandardMappings.computeIfAbsent(c, cls -> new LinkedHashMap<>()).put(fieldName, methodName);
        return this;
    }

    /**
     * @param classes Replaces the collection of classes to treat as non-referenceable with the given class.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder setNonReferenceableClasses(Collection<Class<?>> classes) {
        Convention.throwIfNull(classes, "classes cannot be null");
        options.nonRefClasses.clear();
        options.nonRefClasses.addAll(classes);
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
        options.nonRefClasses.addAll(classes);
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
        options.nonRefClasses.add(clazz);
        return this;
    }

    /**
     * Add a custom option, which may be useful when writing custom writers.  To remove a custom option, add the
     * option with the appropriate key and null as the value.
     * @param key String name of the custom option
     * @param value Object value of the custom option
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addCustomOption(String key, Object value) {
        if (key == null) {
            throw new JsonIoException("Custom option key must not be null.");
        }
        if (value == null) {
            options.customOptions.remove(key);
        } else {
            options.customOptions.put(key, value);
        }
        return this;
    }

    /**
     * Add FieldFilter
     * @param filter FieldFilter
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addFilter(FieldFilter filter) {
        options.fieldFilters.add(filter);
        return this;
    }

    /**
     * Remove FieldFilter
     * @param filter FieldFilter
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder removeFilter(FieldFilter filter) {
        options.fieldFilters.remove(filter);
        return this;
    }

    /**
     * Seal the instance of this class so that no more changes can be made to it.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    @SuppressWarnings("unchecked")
    public WriteOptions build() {
        options.clearCaches();
        options.includedFieldNames = Collections.unmodifiableMap(options.includedFieldNames);
        options.nonStandardMappings = Collections.unmodifiableMap(options.nonStandardMappings);
        options.aliasTypeNames = Collections.unmodifiableMap(options.aliasTypeNames);
        options.notCustomWrittenClasses = Collections.unmodifiableSet(options.notCustomWrittenClasses);
        options.nonRefClasses = Collections.unmodifiableSet(options.nonRefClasses);
        options.excludedFieldNames = Collections.unmodifiableMap(options.excludedFieldNames);
        options.fieldFilters = Collections.unmodifiableList(options.fieldFilters);
        options.methodFilters = Collections.unmodifiableList(options.methodFilters);
        options.accessorFactories = Collections.unmodifiableList(options.accessorFactories);
        options.customWrittenClasses = Collections.unmodifiableMap(options.customWrittenClasses);
        options.customOptions = Collections.unmodifiableMap(options.customOptions);
        return options;
    }

    /**
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, JsonWriter.JsonClassWriter> loadWriters() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/customWriters.txt");
        Map<Class<?>, JsonWriter.JsonClassWriter> writers = new HashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String writerClassName = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " not defined in the JVM, so custom writer: " + writerClassName + ", will not be used.");
                continue;
            }
            Class<JsonWriter.JsonClassWriter> customWriter = (Class<JsonWriter.JsonClassWriter>) ClassUtilities.forName(writerClassName, classLoader);
            if (customWriter == null) {
                System.out.println("Note: class not found (custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
            try {
                JsonWriter.JsonClassWriter writer = customWriter.newInstance();
                writers.put(clazz, writer);
            } catch (Exception e) {
                System.out.println("Note: class failed to instantiate (a custom JsonClassWriter class): " + writerClassName + ", listed in resources/customWriters.txt as a custom writer for: " + className);
            }
        }
        return writers;
    }

    /**
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class<?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, Map<String, String>> loadNonStandardMethodNames() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/nonStandardAccessors.txt");
        Map<Class<?>, Map<String, String>> nonStandardMapping = new ConcurrentHashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String mappings = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
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
        Set<String> set = MetaUtils.loadSetDefinition("config/nonRefs.txt");
        set.forEach((className) -> {
            Class<?> clazz = ClassUtilities.forName(className, WriteOptions.class.getClassLoader());
            if (clazz == null) {
                System.out.println("Class: " + className + " undefined.  Cannot be used as non-referenceable class, listed in resources/nonRefs.txt");
            }
            nonRefs.add(clazz);
        });
        return nonRefs;
    }

    static class DefaultWriteOptions implements WriteOptions {
        private boolean shortMetaKeys = false;
        private ShowType showTypeInfo = WriteOptions.ShowType.MINIMAL;
        private boolean prettyPrint = false;
        private boolean writeLongsAsStrings = false;
        private boolean skipNullFields = false;
        private boolean forceMapOutputAsTwoArrays = false;
        private boolean allowNanAndInfinity = false;
        private boolean enumPublicFieldsOnly = false;
        private boolean closeStream = true;
        private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();
        private ClassLoader classLoader = WriteOptions.class.getClassLoader();
        private Map<Class<?>, Set<String>> includedFieldNames = new LinkedHashMap<>();
        private Map<Class<?>, Map<String, String>> nonStandardMappings = new LinkedHashMap<>();
        private Map<String, String> aliasTypeNames = new LinkedHashMap<>();
        private Set<Class<?>> notCustomWrittenClasses = new LinkedHashSet<>();
        private Set<Class<?>> nonRefClasses = new LinkedHashSet<>();
        private Map<Class<?>, Set<String>> excludedFieldNames = new LinkedHashMap<>();
        private List<FieldFilter> fieldFilters = new ArrayList<>();
        private List<MethodFilter> methodFilters = new ArrayList<>();
        private List<AccessorFactory> accessorFactories = new ArrayList<>();
        private Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses = new LinkedHashMap<>();
        private Map<String, Object> customOptions = new LinkedHashMap<>();

        // Runtime caches (not feature options), since looking up writers can be expensive
        // when one does not exist, we cache the write or a nullWriter if one does not exist.
        private final Map<Class<?>, JsonWriter.JsonClassWriter> writerCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());

        // Creating the Accessors (methodHandles) is expensive so cache the list of Accessors per Class
        private final Map<Class<?>, List<Accessor>> accessorsCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());

        private final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());

        /**
         * Default Constructor.  Prevent instantiation outside of package.
         */
        private DefaultWriteOptions() {
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
         * @return Map<String, String> of all aliases.
         */
        public Map<String, String> aliases() {
            return Collections.unmodifiableMap(aliasTypeNames);
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

        public List<Accessor> getAccessorsForClass(final Class<?> c) {
            return accessorsCache.computeIfAbsent(c, this::buildDeepAccessors);
        }

        /**
         * @return boolean true if java.util.Date and java.sql.Date's are being written in long (numeric) format.
         */
        public boolean isLongDateFormat() {
            Object a = customWrittenClasses.get(Date.class);
            return a instanceof Writers.DateAsLongWriter;
        }

        /**
         * @param clazz Class to check to see if it is non-referenceable.  Non-referenceable classes will always create
         *              a new instance when read in and never use @id/@ref. This uses more memory when the JSON is read in,
         *              as there will be a separate instance in memory for each occurrence. There are certain classes that
         *              json-io automatically treats as non-referenceable, like Strings, Enums, Class, and any Number
         *              instance (BigDecimal, AtomicLong, etc.)  You can add to this list. Often, non-referenceable classes
         *              are useful for classes that can be defined in one line as a JSON, like a ZonedDateTime, for example.
         * @return boolean true if the passed in class is considered a non-referenceable class.
         */
        public boolean isNonReferenceableClass(Class<?> clazz) {
            return nonRefClasses.contains(clazz) ||     // Covers primitives, primitive wrappers, Atomic*, Big*, String
                    Number.class.isAssignableFrom(clazz) ||
                    Date.class.isAssignableFrom(clazz) ||
                    String.class.isAssignableFrom(clazz) ||
                    clazz.isEnum();
        }

        /**
         * @return boolean true if showing short meta-keys (@i instead of @id, @ instead of @ref, @t
         * instead of @type, @k instead of @keys, @v instead of @values), false for full size. 'false' is the default.
         */
        public boolean isShortMetaKeys() {
            return shortMetaKeys;
        }

        /**
         * @return boolean 'prettyPrint' setting, true being yes, pretty-print mode using lots of vertical
         * white-space and indentations, 'false' will output JSON in one line.  The default is false.
         */
        public boolean isPrettyPrint() {
            return prettyPrint;
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
         * @return boolean skipNullFields setting, true indicates fields with null values will not be written,
         * false will still output the field with an associated null value.  false is the default.
         */
        public boolean isSkipNullFields() {
            return skipNullFields;
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
         * @return boolean will return true if NAN and Infinity are allowed to be written out for
         * Doubles and Floats, else null will be written out. The default is false.
         */
        public boolean isAllowNanAndInfinity() {
            return allowNanAndInfinity;
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
         * @return boolean 'true' if the OutputStream should be closed when the reading is finished.  The default is 'true.'
         */
        public boolean isCloseStream() {
            return closeStream;
        }

        /**
         * @return ClassLoader to be used when writing JSON to resolve String named classes.
         */
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        /**
         * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
         * null value.  Instead, singleton instance of this class is placed where null values
         * are needed.
         */
        private static final class NullClass implements JsonWriter.JsonClassWriter {
        }

        static final NullClass nullWriter = new NullClass();

        /**
         * Fetch the custom writer for the passed in Class.  If it is cached (already associated to the
         * passed in Class), return the same instance, otherwise, make a call to get the custom writer
         * and store that result.
         *
         * @param c Class of object for which fetch a custom writer
         * @return JsonClassWriter for the custom class (if one exists), null otherwise.
         */
        public JsonWriter.JsonClassWriter getCustomWriter(Class<?> c) {
            JsonWriter.JsonClassWriter writer = writerCache.computeIfAbsent(c, this::findCustomWriter);
            return writer == nullWriter ? null : writer;
        }

        public JsonWriter.JsonClassWriter findCustomWriter(Class<?> c) {
            JsonWriter.JsonClassWriter writer = MetaUtils.findClosest(c, customWrittenClasses, nullWriter);
            return writer != nullWriter ? writer : MetaUtils.getClassIfEnum(c).isPresent() ? enumWriter : nullWriter;
        }

        public Object getCustomOption(String key)
        {
            return customOptions.get(key);
        }

        public Set<String> getIncludedFields(Class<?> c) {
            return Collections.unmodifiableSet(includedFieldNames.get(c));
        }

        public Set<String> getExcludedFields(Class<?> c) {
            return Collections.unmodifiableSet(excludedFieldNames.get(c));
        }

        public void clearCaches() {
            accessorsCache.clear();
        }

        private List<Accessor> buildDeepAccessors(final Class<?> c) {

            final Map<String, Field> fields = getDeepDeclaredFields(c);

            final List<Accessor> accessors = new ArrayList<>(fields.size());
            for (final Map.Entry<String, Field> entry : fields.entrySet()) {

                final Field field = entry.getValue();
                final String key = entry.getKey();

                Accessor accessor = this.findAccessor(field, key);

                if (accessor == null) {
                    accessor = Accessor.create(field, key);
                }

                if (accessor != null) {
                    accessors.add(accessor);
                }
            }

            return Collections.unmodifiableList(accessors);
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param c - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> getDeepDeclaredFields(final Class<?> c) {
            final Set<String> included = includedFieldNames.get(c);

            return (included == null) ?
                    classMetaCache.computeIfAbsent(c, this::buildExclusiveFields) :
                    classMetaCache.computeIfAbsent(c, cls -> buildInclusiveFields(cls, included));
        }

        private Accessor findAccessor(Field field, String key) {
            for (final AccessorFactory factory : this.accessorFactories) {
                try {
                    final Accessor accessor = factory.buildAccessor(field, this.nonStandardMappings, key);

                    if (accessor != null) {
                        return accessor;
                    }
                } catch (Throwable ignore) {
                }
            }
            return null;
        }

        private Map<String, Field> buildInclusiveFields(Class<?> c, final Set<String> inclusions) {
            Convention.throwIfNull(c, "class cannot be null");
            final Map<String, Field> map = new LinkedHashMap<>();
            Class<?> curr = c;

            while (curr != null) {
                List<Field> fields = ReflectionUtils.getDeclaredFields(curr);

                for (Field field : fields) {
                    String name = field.getName();

                    if (map.containsKey(name)) {
                        name = field.getDeclaringClass().getSimpleName() + '.' + name;
                    }
                    
                    if (inclusions.contains(name) && !fieldIsFiltered(field)) {
                        map.put(name, field);
                    }
                }
                curr = curr.getSuperclass();
            }
            return Collections.unmodifiableMap(map);
        }

        private Map<String, Field> buildExclusiveFields(final Class<?> c) {
            Convention.throwIfNull(c, "class cannot be null");
            final Map<String, Field> map = new LinkedHashMap<>();
            final Set<String> exclusions = new HashSet<>();
            Class<?> curr = c;
            
            while (curr != null) {
                final List<Field> fields = ReflectionUtils.getDeclaredFields(curr);
                final Set<String> excludedForClass = this.excludedFieldNames.get(curr);

                if (excludedForClass != null) {
                    exclusions.addAll(excludedForClass);
                }

                for (Field field : fields) {
                    if (Modifier.isTransient(field.getModifiers()) ||
                            exclusions.contains(field.getName()) ||
                            fieldIsFiltered(field)) {
                        continue;
                    }

                    String name = field.getName();

                    if (map.putIfAbsent(name, field) != null) {
                        map.put(field.getDeclaringClass().getSimpleName() + '.' + name, field);
                    }
                }
                curr = curr.getSuperclass();
            }
            return Collections.unmodifiableMap(map);
        }

        private boolean fieldIsFiltered(Field field) {
            for (FieldFilter filter : this.fieldFilters) {
                if (filter.filter(field)) {
                    return true;
                }
            }
            return false;
        }
    }
}




