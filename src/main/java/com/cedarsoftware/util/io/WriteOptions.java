package com.cedarsoftware.util.io;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.FieldFilter;
import com.cedarsoftware.util.reflect.filters.MethodFilter;

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
    // Properties
    protected boolean shortMetaKeys;
    protected ShowType showTypeInfo;
    protected boolean prettyPrint;
    protected boolean writeLongsAsStrings;
    protected boolean skipNullFields;
    protected boolean forceMapOutputAsTwoArrays;
    protected boolean allowNanAndInfinity;
    protected boolean enumPublicFieldsOnly;
    protected boolean closeStream;
    protected JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();
    protected ClassLoader classLoader = WriteOptions.class.getClassLoader();
    protected Map<Class<?>, Set<String>> includedFieldNames;
    protected Map<Class<?>, Map<String, String>> nonStandardMappings;
    protected Map<String, String> aliasTypeNames;
    protected Set<Class<?>> notCustomWrittenClasses;
    protected Set<Class<?>> nonRefClasses;
    protected Map<Class<?>, Set<String>> excludedFieldNames;
    protected List<FieldFilter> fieldFilters;
    protected List<MethodFilter> methodFilters;
    protected List<AccessorFactory> accessorFactories;
    protected Set<String> filteredMethodNames;
    protected Map<Class<?>, JsonWriter.JsonClassWriter> customWrittenClasses;

    // Runtime caches (not feature options), since looking up writers can be expensive
    // when one does not exist, we cache the write or a nullWriter if one does not exist.
    protected Map<Class<?>, JsonWriter.JsonClassWriter> writerCache = new ConcurrentHashMap<>(300);

    private static final Map<Class<?>, List<Method>> methodCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, Map<String, Method>> deepMethodCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, Map<String, Field>> deepFieldCache = new ConcurrentHashMap<>();

    private final Map<Class<?>, Map<String, Accessor>> accessorsCache = new ConcurrentHashMap<>();


    // Enum for the 3-state property
    public enum ShowType {
        ALWAYS, NEVER, MINIMAL
    }

    WriteOptions() {
        this.shortMetaKeys = false;
        this.showTypeInfo = WriteOptions.ShowType.MINIMAL;
        this.prettyPrint = false;
        this.writeLongsAsStrings = false;
        this.skipNullFields = false;
        this.forceMapOutputAsTwoArrays = false;
        this.allowNanAndInfinity = false;
        this.enumPublicFieldsOnly = false;
        this.closeStream = true;

//        this.notCustomWrittenClasses = Collections.unmodifiableSet(notCustomWrittenClasses);
//        this.nonRefClasses = Collections.unmodifiableSet(nonRefClasses);
//        this.filteredMethodNames = Collections.unmodifiableSet(filteredMethodNames);
//
//        this.aliasTypeNames = Collections.unmodifiableMap(aliasTypeNames);
//        this.customWrittenClasses = Collections.unmodifiableMap(customWrittenClasses);
//        this.writerCache.putAll(customWrittenClasses);
//
//
//        this.excludedFieldNames = MetaUtils.cloneMapOfSets(excludedFieldNames, true);
//        this.includedFieldNames = MetaUtils.cloneMapOfSets(includedFieldNames, true);
//
//        // Need your own Set instance here per Class, no references to the copied Set.
//        this.nonStandardMappings = MetaUtils.cloneMapOfMaps(nonStandardMappings, true);
//
//        this.fieldFilters = Collections.unmodifiableList(fieldFilters.stream()
//                .map(FieldFilter::createCopy)
//                .collect(Collectors.toList()));
//
//        this.methodFilters = Collections.unmodifiableList(methodFilters.stream()
//                .map(MethodFilter::createCopy)
//                .collect(Collectors.toList()));
//
//        this.accessorFactories = Collections.unmodifiableList(accessorFactories.stream()
//                .map(AccessorFactory::createCopy)
//                .collect(Collectors.toList()));
    }

    /**
     * @return ClassLoader to be used when writing JSON to resolve String named classes.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @return boolean true if showing short meta-keys (@i instead of @id, @ instead of @ref, @t
     * instead of @type, @k instead of @keys, @v instead of @values), false for full size. 'false' is the default.
     */
    public boolean isShortMetaKeys() {
        return shortMetaKeys;
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
        return aliasTypeNames;
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
     * Doubles and Floats, else null will be written out..
     */
    public boolean isAllowNanAndInfinity() {
        return allowNanAndInfinity;
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
     * @return boolean 'true' if the OutputStream should be closed when the reading is finished.  The default is 'true.'
     */
    public boolean isCloseStream() {
        return closeStream;
    }


    /**
     * @return Map of Class to custom JsonClassWriter's use to write JSON when the class is encountered during
     * serialization to JSON.
     */
    public Map<Class<?>, JsonWriter.JsonClassWriter> getCustomWrittenClasses() {
        return customWrittenClasses;
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
        return notCustomWrittenClasses;
    }

    /**
     * Get the list of fields associated to the passed in class that are to be included in the written JSON.
     * @param clazz Class for which the fields to be included in JSON output will be returned.
     * @return Set of Strings field names associated to the passed in class or an empty
     * Set if no fields.  This is the list of fields to be included in the written JSON for the given class.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getIncludedFields(Class<?> clazz) {
        return (Set<String>) getItemsForClass(clazz, includedFieldNames);
    }

    private Set<?> getItemsForClass(Class<?> clazz, Map<?, ? extends Set<?>> classesToSets)
    {
        Set<?> items = classesToSets.get(clazz);
        // Give back real - protected from modifications
        return items == null ? Collections.unmodifiableSet(new LinkedHashSet<>()) : items;
    }

    public Collection<Accessor> getAccessorsForClass(final Class<?> c) {
        return getAccessorMapForClass(c).values();
    }

    public Map<String, Accessor> getAccessorMapForClass(final Class<?> c) {
        return accessorsCache.computeIfAbsent(c, this::buildDeepAccessors);
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
        return nonRefClasses;
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
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     * @return Set<Class<?>> which is the loaded from resource/nonRefs.txt and verified to exist in JVM.
     */
    static Set<Class<?>> loadNonRefs() {
        final Set<String> set = MetaUtils.loadSetDefinition("nonRefs.txt");
        final ClassLoader classLoader = WriteOptions.class.getClassLoader();

        return set.stream()
                .map(className -> MetaUtils.classForNameThrowsException(className, classLoader))
                .filter(Objects::isNull)
                .collect(Collectors.toSet());
    }


    ///// ACCESSOR PULL IN ???????

    public void clearCaches() {
        deepMethodCache.clear();
        deepFieldCache.clear();
        methodCache.clear();
    }

    public void clearUnfilteredAccessorCache() {
        methodCache.clear();
    }


    private Map<String, Accessor> buildDeepAccessors(final Class<?> c) {
        final Set<String> inclusions = includedFieldNames.get(c);
        final Set<String> exclusions = new HashSet<>();
        final Map<String, Field> deepDeclaredFields = this.getDeepDeclaredFields(c, exclusions);
        final Map<String, Method> possibleMethods = getDeepDeclaredMethods(c);
        final Map<String, Accessor> accessorMap = new LinkedHashMap<>();

        final boolean isExclusive = inclusions == null;

        final List<Map.Entry<String, Field>> fields = isExclusive ?
                deepDeclaredFields.entrySet().stream()
                        .filter(e -> !Modifier.isTransient(e.getValue().getModifiers()))
                        .filter(e -> !exclusions.contains(e.getValue().getName()))
                        .filter(e -> this.fieldFilters.stream().noneMatch(f -> f.filter(e.getValue())))
                        .collect(Collectors.toList()) :
                deepDeclaredFields.entrySet().stream()
                        .filter(e -> inclusions.contains(e.getKey()))
                        .filter(e -> this.fieldFilters.stream().noneMatch(f -> f.filter(e.getValue())))
                        .collect(Collectors.toList());

        for (final Map.Entry<String, Field> entry : fields) {

            final Field field = entry.getValue();
            final String fieldName = entry.getKey();

            final String key = accessorMap.containsKey(fieldName) ? field.getDeclaringClass().getSimpleName() + '.' + fieldName : fieldName;

            assert key.equals(fieldName);

            final Accessor accessor = this.accessorFactories.stream()
                    .map(createAccessor(field, possibleMethods, key))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(createAccessorFromField(field, key));

            if (accessor != null) {
                accessorMap.put(key, accessor);
            }
        }

        return Collections.synchronizedMap(accessorMap);
    }

    private Map<String, Method> getDeepDeclaredMethods(Class<?> c) {
        return deepMethodCache.computeIfAbsent(c, this::buildDeepMethods);
    }

    public Map<String, Field> getDeepDeclaredFields(final Class<?> c, final Set<String> deepExcludedFields) {
        return deepFieldCache.computeIfAbsent(c, cls -> this.buildDeepFieldMap(cls, deepExcludedFields));
    }

    private static Accessor createAccessorFromField(Field field, String key) {
        try {
            return new Accessor(field, key);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            return null;
        }
    }

    private Function<AccessorFactory, Accessor> createAccessor(Field field, Map<String, Method> possibleMethods, String key) {
        return factory -> {
            try {
                return factory.createAccessor(field, this.nonStandardMappings, possibleMethods, key);
            } catch (Throwable t) {
                return null;
            }
        };
    }

    /**
     * Builds a list of methods with zero parameter methods taking precedence over overrides
     * for a given single level class.
     *
     * @param classToTraverse - class to get the declared methods for
     * @return Map of name of the method to the actual emthod
     */
    public Map<String, Method> buildDeepMethods(Class<?> classToTraverse) {
        Convention.throwIfNull(classToTraverse, "The classToTraverse cannot be null");

        Map<String, Method> map = new LinkedHashMap<>();
        Class<?> currentClass = classToTraverse;

        while (currentClass != Object.class) {
            final List<Method> methods = this.getFilteredMethods(currentClass);
            methods.forEach(m -> map.put(m.getName(), m));
            currentClass = currentClass.getSuperclass();
        }

        return Collections.synchronizedMap(map);
    }

    public List<Method> getFilteredMethods(Class<?> c) {
        return methodCache.computeIfAbsent(c, key -> ReflectionUtils.buildFilteredMethodList(key, methodFilters, filteredMethodNames));
    }

    public Map<String, Field> buildDeepFieldMap(Class<?> c, final Set<String> exclusions) {
        Convention.throwIfNull(c, "class cannot be null");
        Convention.throwIfNull(exclusions, "exclusions cannot be null");

        final Map<String, Field> map = new LinkedHashMap<>();

        Class<?> curr = c;
        while (curr != Object.class) {
            final List<Field> fields = ReflectionUtils.buildFilteredFields(curr);

            Collection<String> excludedForClass = this.excludedFieldNames.get(curr);

            if (excludedForClass != null) {
                exclusions.addAll(excludedForClass);
            }

            fields.forEach(f -> {
                String name = f.getName();
                if (map.putIfAbsent(name, f) != null) {
                    map.put(f.getDeclaringClass().getSimpleName() + '.' + name, f);
                }
            });

            curr = curr.getSuperclass();
        }

        return Collections.synchronizedMap(map);
    }

    ShowType getShowTypeInfo() {
        return showTypeInfo;
    }
}
