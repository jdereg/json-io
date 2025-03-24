package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import com.cedarsoftware.io.reflect.factories.GetMethodAccessorFactory;
import com.cedarsoftware.io.reflect.factories.IsMethodAccessorFactory;
import com.cedarsoftware.io.reflect.filters.FieldFilter;
import com.cedarsoftware.io.reflect.filters.MethodFilter;
import com.cedarsoftware.io.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.io.reflect.filters.field.StaticFieldFilter;
import com.cedarsoftware.io.reflect.filters.method.DefaultMethodFilter;
import com.cedarsoftware.io.reflect.filters.method.NamedMethodFilter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.ClassValueSet;
import com.cedarsoftware.util.ConcurrentSet;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.LRUCache;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.StringUtilities;

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
 *         limitations under the License.
 */
public class WriteOptionsBuilder {
    // The BASE_* Maps are regular ConcurrentHashMap's because they are not constantly searched, otherwise they would be ClassValueMaps.
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = new ConcurrentSet<>();
    private static final Set<Class<?>> BASE_NOT_CUSTOM_WRITTEN = new ConcurrentSet<>();
    static final Map<Class<?>, Set<String>> BASE_EXCLUDED_FIELD_NAMES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, String>> BASE_NONSTANDARD_GETTERS = new ConcurrentHashMap<>();;
    private static final Map<String, FieldFilter> BASE_FIELD_FILTERS = new ConcurrentHashMap<>();
    private static final Map<String, MethodFilter> BASE_METHOD_FILTERS = new ConcurrentHashMap<>();
    private static final Map<String, AccessorFactory> BASE_ACCESSOR_FACTORIES = new ConcurrentHashMap<>();
    private static final WriteOptions defWriteOptions;
    private final DefaultWriteOptions options;

    static {
        ReadOptionsBuilder.loadBaseAliasMappings(WriteOptionsBuilder::addPermanentAlias);
        loadBaseWriters();
        loadBaseNonRefs();
        loadBaseNotCustomWrittenClasses();
        loadBaseExcludedFields();
        loadBaseNonStandardGetters();

        // If the lists below become large, then break these out into load* APIs like we've done for the ones above.
        addPermanentFieldFilter("static", new StaticFieldFilter());
        addPermanentFieldFilter("enum", new EnumFieldFilter());
        addPermanentMethodFilter("default", new DefaultMethodFilter());
        addPermanentAccessorFactory("get", new GetMethodAccessorFactory());
        addPermanentAccessorFactory("is", new IsMethodAccessorFactory());

        defWriteOptions = new WriteOptionsBuilder().build();

        // low-level alias support (bigint, bigdec)
        ClassUtilities.addPermanentClassAlias(BigInteger.class, "bigint");
        ClassUtilities.addPermanentClassAlias(BigInteger.class, "BigInt");
        ClassUtilities.addPermanentClassAlias(BigDecimal.class, "bigdec");
        ClassUtilities.addPermanentClassAlias(BigDecimal.class, "BigDec");
    }

    /**
     * @return Default WriteOptions - no construction needed, unmodifiable.
     */
    public static WriteOptions getDefaultWriteOptions() {
        return defWriteOptions;
    }

    /**
     * Start with default options
     */
    public WriteOptionsBuilder() {
        options = new DefaultWriteOptions();

        // Start with all BASE_ALIAS_MAPPINGS (more aliases can be added to this instance, and more aliases
        // can be added to the BASE_ALIAS_MAPPINGS via the static method, so that all instances get them.)
        options.nonStandardGetters.putAll(BASE_NONSTANDARD_GETTERS);
        options.aliasTypeNames.putAll(BASE_ALIAS_MAPPINGS);
        options.customWrittenClasses.putAll(BASE_WRITERS);
        options.nonRefClasses.addAll(BASE_NON_REFS);
        options.notCustomWrittenClasses.addAll(BASE_NOT_CUSTOM_WRITTEN);
        options.excludedFieldNames.putAll(BASE_EXCLUDED_FIELD_NAMES);
        options.fieldFilters.putAll(BASE_FIELD_FILTERS);
        options.methodFilters.putAll(BASE_METHOD_FILTERS);
        options.accessorFactories.putAll(BASE_ACCESSOR_FACTORIES);
    }

    /**
     * Copy another WriteOptions as a starting point.  If null is passed in, then you get the default options.
     */
    public WriteOptionsBuilder(WriteOptions copy) {
        this();
        if (copy != null) {
            DefaultWriteOptions other = (DefaultWriteOptions) copy;

            // Copy simple settings
            options.allowNanAndInfinity = other.allowNanAndInfinity;
            options.closeStream = other.closeStream;
            options.classLoader = other.classLoader;
            options.enumPublicFieldsOnly = other.enumPublicFieldsOnly;
            options.enumSetWrittenOldWay = other.enumSetWrittenOldWay;
            options.forceMapOutputAsTwoArrays = other.forceMapOutputAsTwoArrays;
            options.prettyPrint = other.prettyPrint;
            options.lruSize = other.lruSize;
            options.shortMetaKeys = other.shortMetaKeys;
            options.showTypeInfo = other.showTypeInfo;
            options.skipNullFields = other.skipNullFields;
            options.writeLongsAsStrings = other.writeLongsAsStrings;

            // Copy complex settings
            options.includedFieldNames.clear();
            options.includedFieldNames.putAll(other.includedFieldNames);

            options.nonStandardGetters.clear();
            options.nonStandardGetters.putAll(other.nonStandardGetters);

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
            options.fieldFilters.putAll(other.fieldFilters);

            options.methodFilters.clear();
            options.methodFilters.putAll(other.methodFilters);

            options.accessorFactories.clear();
            options.accessorFactories.putAll(other.accessorFactories);

            // Copy caches
            options.accessorsCache = new LRUCache<>(other.lruSize);
            options.accessorsCache.putAll(other.accessorsCache);

            options.classMetaCache = new LRUCache<>(other.lruSize);
            options.classMetaCache.putAll(other.classMetaCache);
        }
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.  All WriteOptions
     * will automatically be created with any permanent aliases added to them.
     *
     * @param clazz Class that will be aliased by a shorter name in the JSON.
     * @param alias Shorter alias name, for example, "ArrayList" as opposed to "java.util.ArrayList"
     */
    public static void addPermanentAlias(Class<?> clazz, String alias) {
        BASE_ALIAS_MAPPINGS.put(clazz.getName(), alias);
    }

    /**
     * Call this method to remove alias patterns from the WriteOptionsBuilder so that when it writes JSON,
     * the classes that match the passed in pattern are not aliased. This API matches your wildcard pattern
     * of *, ?, and characters against class names in its cache. It removes the entries (className to aliasName)
     * from the cache to prevent the resultant classes from being aliased during output.
     *
     * @param classNamePattern String pattern to match class names. This String matches using a wild-card
     * pattern, where * matches anything and ? matches one character. As many * or ? can be used as needed.
     */
    public static void removePermanentAliasTypeNamesMatching(String classNamePattern) {
        String regex = StringUtilities.wildcardToRegexString(classNamePattern);
        Pattern pattern = Pattern.compile(regex);
        BASE_ALIAS_MAPPINGS.keySet().removeIf(key -> pattern.matcher(key).matches());
    }

    /**
     * Call this method to add a permanent (JVM lifetime) excluded field name of class.  All WriteOptions will
     * automatically be created this field field on the excluded list.
     *
     * @param clazz Class that contains the named field.
     * @param fieldName to be excluded.
     */
    public static void addPermanentExcludedField(Class<?> clazz, String fieldName) {
        BASE_EXCLUDED_FIELD_NAMES.computeIfAbsent(clazz, cls -> ConcurrentHashMap.newKeySet()).add(fieldName);
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
     * Call this method to add a permanent (JVM lifetime) class that should not be written with a custom
     * writer when being written out to JSON.
     *
     * @param clazz Class that will no longer be written with a custom writer.
     */
    public static void addPermanentNotCustomWrittenClass(Class<?> clazz) {
        BASE_NOT_CUSTOM_WRITTEN.add(clazz);
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
     * This option permits adding non-standard getters (used when writing JSON) that access properties from objects,
     * where the method name does not follow a standard setter/getter property name. For example, on java.time.Instance,
     * to get the 'second' field, the accessor method is 'getEpochSecond()'.
     * Anything added here will automatically be made in all WriteOptions.
     * @param clazz Class that has the non-standard getter.  java.time.Instance in the example above.
     * @param field String name of the class property. 'second' in the example above.
     * @param methodName The name of the non-standard method used to get the field value. 'getEpochSecond' in the example above.
     */
    public static void addPermanentNonStandardGetter(Class<?> clazz, String field, String methodName) {
        BASE_NONSTANDARD_GETTERS.computeIfAbsent(clazz, cls -> new ConcurrentHashMap<>()).put(field, methodName);
    }

    /**
     * Add a FieldFilter that is JVM lifecycle scoped.
     * @param fieldFilter {@link FieldFilter} class used to eliminate fields from being included in the serialized JSON.
     */
    public static void addPermanentFieldFilter(String name, FieldFilter fieldFilter) {
        BASE_FIELD_FILTERS.put(name, fieldFilter);
    }

    /**
     * Add a MethodFilter that is JVM lifecycle scoped. All WriteOptions instances will contain this filter.
     * @param name String name of this particular MethodFilter instance.
     * @param methodFilter {@link MethodFilter} class used to eliminate a particular accessor method from being used.
     */
    public static void addPermanentMethodFilter(String name, MethodFilter methodFilter) {
        BASE_METHOD_FILTERS.put(name, methodFilter);
    }

    /**
     * Add a MethodFilter that is JVM lifecycle scoped. All WriteOptions instances will contain this filter.
     * @param name String name of this particular method filter instance.
     * @param clazz class that contains the method to be filtered (can be derived class, with field defined on parent class).
     * @param methodName String name of no-argument method to be filtered.
     */
    public static void addPermanentNamedMethodFilter(String name, Class<?> clazz, String methodName) {
        BASE_METHOD_FILTERS.put(name, new NamedMethodFilter(clazz, methodName));
    }

    /**
     * Add an AccessorFactory that is JVM lifecycle scoped.  All WriteOptions instances will contain this AccessorFactory.
     * It is the job of an AccessorFactory to provide a possible method name for a particular field. json-io ships with
     * a GetMethodAccessorFactory and an IsMethodAccessFactory.  These produce a possible method name for a given field.
     * When a field on a Java class is being accessed (read), and it cannot be obtained directly, then all AccessoryFactory
     * instances will be consulted until an API can be used to read the field.
     * @param name String name of AccessorFactory.  Each factory should have it's own unique name. This will allow these
     *             to be defined in a file, later if needed.
     * @param factory AccessorFactory subclass.
     */
    public static void addPermanentAccessorFactory(String name, AccessorFactory factory) {
        BASE_ACCESSOR_FACTORIES.put(name, factory);
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
     * @return WriteOptionsBuilder for chained access.
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
        Convention.throwIfKeyExists(options.aliasTypeNames, typeName, "Tried to create @type alias '" + alias + "' for '" + typeName + "', but it is already aliased to: " + options.aliasTypeNames.get(typeName));
        options.aliasTypeNames.put(typeName, alias);
    }

    /**
     * Remove alias entries from this WriteOptionsBuilder instance where the Java fully qualified string
     * class name matches the passed in wildcard pattern.
     * @param typeNamePattern String pattern to match class names. This String matches using a wild-card
     * pattern, where * matches anything and ? matches one character. As many * or ? can be used as needed.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder removeAliasTypeNamesMatching(String typeNamePattern) {
        String regex = StringUtilities.wildcardToRegexString(typeNamePattern);
        Pattern pattern = Pattern.compile(regex);
        options.aliasTypeNames.keySet().removeIf(key -> pattern.matcher(key).matches());
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
     * @param size Max size of the cache for Class fields and Class accessors
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder lruSize(int size) {
        options.lruSize = size;

        Map<Class<?>, List<Accessor>> accessorCacheCopy = options.accessorsCache;
        options.accessorsCache = new LRUCache<>(options.getLruSize());
        options.accessorsCache.putAll(accessorCacheCopy);

        Map<Class<?>, Map<String, Field>> classMetaCacheCopy = options.classMetaCache;
        options.classMetaCache = new LRUCache<>(options.getLruSize());
        options.classMetaCache.putAll(classMetaCacheCopy);
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
     * @param writePublicFieldsOnly boolean, only write out the public fields when writing enums as objects. Defaults to false.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeEnumAsJsonObject(boolean writePublicFieldsOnly) {
        options.enumWriter = DefaultWriteOptions.nullWriter;
        options.enumPublicFieldsOnly = writePublicFieldsOnly;
        return this;
    }

    /**
     * Option to write out all the member fields of an enum.  You can also filter the
     * field to write out only the public fields on the enum.
     *
     * @param writeOldWay boolean, write EnumSet using @enum instead of @type.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder writeEnumSetOldWay(boolean writeOldWay) {
        options.enumSetWrittenOldWay = writeOldWay;
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
     * Change the date-time format to the ISO date format: "yyyy-MM-ddThh:mm:ss.SSSZ".  This is for java.util.Date and
     * java.sql.Date.  The fractional sections are omitted if millis are 0.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder isoDateFormat() {
        addCustomWrittenClass(Date.class, new Writers.DateWriter());
        return this;
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
     * This option permits adding non-standard accessors (used when writing JSON) that access properties from objects,
     * where the method name does not follow a standard setter/getter property name. For example, on java.time.Instance,
     * to get the 'second' field, the accessor method is 'getEpochSecond()'.
     * @param c Class that has the non-standard accessor.  java.time.Instance in the example above.
     * @param fieldName String name of the class property.  'second' in the example above.
     * @param methodName The name of the non-standard method used to get the field value. 'getEpochSecond' in the example above.
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addNonStandardGetter(Class<?> c, String fieldName, String methodName) {
        Convention.throwIfNull(c, "class cannot be null");
        Convention.throwIfNull(fieldName, "fieldName cannot be null");
        Convention.throwIfNull(methodName, "methodName cannot be null");
        options.nonStandardGetters.computeIfAbsent(c, cls -> new LinkedHashMap<>()).put(fieldName, methodName);
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
     * Add FieldFilter to the field filter chain. FieldFilters are presented a chance to eliminate a field by returning true
     * from its boolean filter() method.  If any FieldFilter returns true, the field is excluded.
     * @param filterName String name of filter
     * @param filter FieldFilter to add
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder addFieldFilter(String filterName, FieldFilter filter) {
        options.fieldFilters.put(filterName, filter);
        return this;
    }

    /**
     * Remove named FieldFilter from the filter chain.
     * @param filterName String name of FieldFilter to delete
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptionsBuilder removeFieldFilter(String filterName) {
        options.fieldFilters.remove(filterName);
        return this;
    }

    /**
     * Add MethodFilter to the filter chain. MethodFilters are presented a chance to eliminate a "getter" type accessing
     * method by returning true from the boolean filter() method.  If any MethodFilter returns true, the accessor method
     * is excluded.  This means it will drop back to use field access (attempting to access private field in same module
     * with setAccessible()).  This API is used when you want to write your own implementation of a MethodFilter.
     * @param filterName String name of filter
     * @param methodFilter {@link MethodFilter} to add
     * @return {@link WriteOptionsBuilder} for chained access.
     */
    public WriteOptionsBuilder addMethodFilter(String filterName, MethodFilter methodFilter) {
        options.methodFilters.put(filterName, methodFilter);
        return this;
    }

    /**
     * Add a NamedMethodFilter to the filter chain. NamedMethodFilters are presented a chance to eliminate a "getter"
     * type accessing method by returning true from the boolean filter() method.  If the NamedMethodFilter matches a class
     * and accessor method name, and it has no args, is public, and non-static, the accessor method is excluded.  This
     * means it will drop back to use field access (attempting to access private field in same module with setAccessible()).
     * @param filterName String name of filter
     * @param clazz Class containing method to filter
     * @param methodName String name of method to not use for accessing value.
     * @return {@link WriteOptionsBuilder} for chained access.
     */
    public WriteOptionsBuilder addNamedMethodFilter(String filterName, Class<?> clazz, String methodName) {
        options.methodFilters.put(filterName, new NamedMethodFilter(clazz, methodName));
        return this;
    }

    /**
     * Remove named MethodFilter from the method filter chain.
     * @param filterName String name of filter
     * @return {@link WriteOptionsBuilder} for chained access.
     */
    public WriteOptionsBuilder removeMethodFilter(String filterName) {
        options.methodFilters.remove(filterName);
        return this;
    }

    /**
     * Add AccessorFactory to the accessor factories chain. AccessFactories permit adding a standard pattern of
     * locating "read" methods, for example, there is a built-in "get" and "is" AccessoryFactory.
     * @param factoryName String name of accessor factory
     * @param  accessorFactory {@link AccessorFactory} to add
     * @return {@link WriteOptionsBuilder} for chained access.
     */
    public WriteOptionsBuilder addAccessorFactory(String factoryName, AccessorFactory accessorFactory) {
        options.accessorFactories.put(factoryName, accessorFactory);
        return this;
    }

    /**
     * Remove named AccessorFactory from the access factories.
     * @param factoryName String name of accessor filter
     * @return {@link WriteOptionsBuilder} for chained access.
     */
    public WriteOptionsBuilder removeAccessorFactory(String factoryName) {
        options.accessorFactories.remove(factoryName);
        return this;
    }

    /**
     * Seal the instance of this class so that no more changes can be made to it.
     *
     * @return WriteOptionsBuilder for chained access.
     */
    public WriteOptions build() {
        options.clearCaches();
        options.includedFieldNames = ((ClassValueMap<Set<String>>)options.includedFieldNames).unmodifiableView();
        options.nonStandardGetters = ((ClassValueMap<Map<String, String>>)options.nonStandardGetters).unmodifiableView();
        options.aliasTypeNames = Collections.unmodifiableMap(options.aliasTypeNames);
        options.notCustomWrittenClasses = ((ClassValueSet)options.notCustomWrittenClasses).unmodifiableView();
        options.nonRefClasses = ((ClassValueSet)options.nonRefClasses).unmodifiableView();
        options.excludedFieldNames = ((ClassValueMap<Set<String>>)options.excludedFieldNames).unmodifiableView();
        options.fieldFilters = Collections.unmodifiableMap(options.fieldFilters);
        options.methodFilters = Collections.unmodifiableMap(options.methodFilters);
        options.accessorFactories = Collections.unmodifiableMap(options.accessorFactories);
        options.customWrittenClasses = ((ClassValueMap<JsonWriter.JsonClassWriter>)options.customWrittenClasses).unmodifiableView();
        options.customOptions = Collections.unmodifiableMap(options.customOptions);
        return options;
    }

    static class DefaultWriteOptions implements WriteOptions {
        private boolean shortMetaKeys = false;
        private ShowType showTypeInfo = WriteOptions.ShowType.MINIMAL;
        private boolean prettyPrint = false;
        private int lruSize = 1000;
        private boolean writeLongsAsStrings = false;
        private boolean skipNullFields = false;
        private boolean forceMapOutputAsTwoArrays = false;
        private boolean allowNanAndInfinity = false;
        private boolean enumPublicFieldsOnly = false;
        private boolean enumSetWrittenOldWay = true;
        private boolean closeStream = true;
        private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();
        private ClassLoader classLoader = ClassUtilities.getClassLoader(DefaultWriteOptions.class);
        private Map<Class<?>, Set<String>> includedFieldNames = new ClassValueMap<>();
        private Map<Class<?>, Map<String, String>> nonStandardGetters = new ClassValueMap<>();
        private Map<String, String> aliasTypeNames = new LinkedHashMap<>();
        private Set<Class<?>> notCustomWrittenClasses = new ClassValueSet();
        private Set<Class<?>> nonRefClasses = new ClassValueSet();
        private Map<Class<?>, Set<String>> excludedFieldNames = new ClassValueMap<>();
        private Map<String, FieldFilter> fieldFilters = new LinkedHashMap<>();
        private Map<String, MethodFilter> methodFilters = new LinkedHashMap<>();
        private Map<String, AccessorFactory> accessorFactories = new LinkedHashMap<>();
        private Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses = new ClassValueMap<>();
        private Map<String, Object> customOptions = new LinkedHashMap<>();

        // Runtime caches (not feature options), since looking up writers can be expensive
        // when one does not exist, we cache the writer or a nullWriter if one does not exist.
        private final Map<Class<?>, JsonWriter.JsonClassWriter> writerCache = new ClassValueMap<>();

        // Creating the Accessors (methodHandles) is expensive so cache the list of Accessors per Class
        private Map<Class<?>, List<Accessor>> accessorsCache = new ClassValueMap<>();
        private Map<Class<?>, Map<String, Field>> classMetaCache = new ClassValueMap<>();

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
         * @return int 'LRU Size' setting for holding reflected fields and accessors.  Default is 1000.
         */
        public int getLruSize() {
            return lruSize;
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
         * true indicates that EnumSet instances are written with an "@enum":"enum_classname" where enum_classname is
         * the Class name of the enum type that is held in the EnumSet. When 'false' EnumSets are written with
         * "@type":"enum_classname" the same as before - the distinction is that @enum is now written as @type. EnumSets
         * are resolved unambiguously because they are always written with @items:[] so the resolver knows that if
         * the @type is an Enum, and there is an @items[], then it can infer that it is an EnumSet, not an enum.  When
         * an enum is written out as "@type":"com.foo.myEnum" there will not be an @items, therefore it can be
         * considered an Enum, not an EnumSet. <p></p>
         * The default is true for backward compatibility but will be switched to false in the next major release.
         */
        public boolean isEnumSetWrittenOldWay() {
            return enumSetWrittenOldWay;
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

        JsonWriter.JsonClassWriter findCustomWriter(Class<?> c) {
            JsonWriter.JsonClassWriter writer = ClassUtilities.findClosest(c, customWrittenClasses, nullWriter);
            if (writer != nullWriter) {
                return writer;
            } else {
                Class<?> enumClass = ClassUtilities.getClassIfEnum(c);
                return (enumClass != null) ? enumWriter : nullWriter;
            }
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
            classMetaCache.clear();
            accessorsCache.clear();
        }

        private List<Accessor> buildDeepAccessors(final Class<?> clazz) {
            final Map<String, Field> fields = getDeepDeclaredFields(clazz);
            final List<Accessor> accessors = new ArrayList<>(fields.size());
            
            for (final Map.Entry<String, Field> entry : fields.entrySet()) {

                final Field field = entry.getValue();
                final String uniqueFieldName = entry.getKey();

                Accessor accessor = findMethodAccessor(field, uniqueFieldName);
                if (accessor != null && isMethodFiltered(clazz, accessor.getFieldOrMethodName())) {
                    accessor = null;
                }

                if (accessor == null) {
                    accessor = Accessor.createFieldAccessor(field, uniqueFieldName);
                }

                accessors.add(accessor);
            }
            return Collections.unmodifiableList(accessors);
        }

        private boolean isMethodFiltered(Class<?> clazz, String methodName) {
            for (MethodFilter filter : methodFilters.values()) {
                if (filter.filter(clazz, methodName)) {
                    return true;
                }
            }
            return false;
        }

        private Accessor findMethodAccessor(Field field, String uniqueFieldName) {
            for (final AccessorFactory factory : accessorFactories.values()) {
                try {
                    final Accessor accessor = factory.buildAccessor(field, nonStandardGetters, uniqueFieldName);

                    if (accessor != null) {
                        return accessor;
                    }
                } catch (Throwable ignore) {
                }
            }
            return null;
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param c - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> getDeepDeclaredFields(final Class<?> c) {
            return classMetaCache.computeIfAbsent(c, this::buildWithIncludedMinusExcluded);
        }

        private Map<String, Field> buildWithIncludedMinusExcluded(Class<?> c) {
            Convention.throwIfNull(c, "class cannot be null");
            final Map<String, Field> map = new LinkedHashMap<>();
            final Set<String> excluded = new HashSet<>();
            final Set<String> includedFields = includedFieldNames.get(c);
            final Set<String> included = includedFields == null ? new HashSet<>() : includedFields;
            Class<?> curr = c;

            while (curr != null) {
                List<Field> fields = ReflectionUtils.getDeclaredFields(curr);
                final Set<String> excludedForClass = this.excludedFieldNames.get(curr);

                if (excludedForClass != null) {
                    excluded.addAll(excludedForClass);
                }

                for (Field field : fields) {
                    String fieldName = field.getName();
                    if (map.containsKey(fieldName)) {
                        fieldName = field.getDeclaringClass().getSimpleName() + '.' + fieldName;
                    }

                    // FieldFilter or 'Excluded listing' automatically eliminates field.
                    if (isFieldFiltered(field) || excluded.contains(fieldName)) {
                        continue;
                    }

                    boolean isTransient = Modifier.isTransient(field.getModifiers());
                    boolean includedExplicitly = included.contains(fieldName);
                    
                    if (isTransient && !includedExplicitly) {
                        continue;
                    }

                    // If included not specified, then default to ALL, otherwise only consider explicitly included fields.
                    if (included.isEmpty() || includedExplicitly) {
                        map.put(fieldName, field);
                    }
                }
                curr = curr.getSuperclass();
            }
            return Collections.unmodifiableMap(map);
        }

        private boolean isFieldFiltered(Field field) {
            for (FieldFilter filter : this.fieldFilters.values()) {
                if (filter.filter(field)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Load custom writer classes based on contents of resources/customWriters.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static void loadBaseWriters() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/customWriters.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(WriteOptionsBuilder.class);

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
                System.out.println("Note: class not found (custom JsonClassWriter class): " + writerClassName + ", listed in config/customWriters.txt as a custom writer for: " + className);
            } else {
                try {
                    JsonWriter.JsonClassWriter writer = customWriter.newInstance();
                    addPermanentWriter(clazz, writer);
                } catch (Exception e) {
                    System.out.println("Note: class failed to instantiate (a custom JsonClassWriter class): " + writerClassName + ", listed in config/customWriters.txt as a custom writer for: " + className);
                }
            }
        }
    }

    /**
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     */
    private static void loadBaseNonRefs() {
        Set<String> set = MetaUtils.loadSetDefinition("config/nonRefs.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(WriteOptionsBuilder.class);
        for (String className : set) {
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " undefined.  Cannot be used as non-referenceable class, listed in config/nonRefs.txt");
            } else {
                addPermanentNonRef(clazz);
            }
        }
    }

    /**
     * Load the list of classes that are intended to not (never) be written using a custom writer.  This is useful to
     * terminate a "custom writer" inheritance chain.  For example, if you have a custom writer for a base class, and
     * you don't want it to be used for a subclass, you can add the subclass to this list.
     */
    private static void loadBaseNotCustomWrittenClasses() {
        Set<String> set = MetaUtils.loadSetDefinition("config/notCustomWritten.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(WriteOptionsBuilder.class);
        for (String className : set) {
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " undefined.  Cannot be used as to turn off custom writing for this class, listed in config/notCustomWritten.txt");
            } else {
                addPermanentNotCustomWrittenClass(clazz);
            }
        }
    }
    
    private static void loadBaseExcludedFields() {
        Map<Class<?>, Set<String>> allExcludedFields = ReadOptionsBuilder.loadClassToSetOfStrings("config/fieldsNotExported.txt");
        for (Map.Entry<Class<?>, Set<String>> entry : allExcludedFields.entrySet()) {
            Class<?> clazz = entry.getKey();
            Set<String> excludedFields = entry.getValue();
            for (String fieldName : excludedFields) {
                addPermanentExcludedField(clazz, fieldName);
            }
        }
    }
    
    private static void loadBaseNonStandardGetters() {
        Map<Class<?>, Map<String, String>> nonStandardGetterMap = ReadOptionsBuilder.loadClassToFieldAliasNameMapping("config/nonStandardGetters.txt");
        for (Map.Entry<Class<?>, Map<String, String>> entry : nonStandardGetterMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            Map<String, String> pairingsMap = entry.getValue();
            for (Map.Entry<String, String> fieldToAltName : pairingsMap.entrySet()) {
                addPermanentNonStandardGetter(clazz, fieldToAltName.getKey(), fieldToAltName.getValue());
            }
        }
    }
}