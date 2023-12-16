package com.cedarsoftware.util.io;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
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

    private final Map<Class<?>, List<Accessor>> accessorsCache = new ConcurrentHashMap<>();


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

        final Set<Class<?>> result = new HashSet<>();

        for (String className : set) {
            Class<?> loadedClass = MetaUtils.classForName(className, classLoader);

            if (loadedClass != null) {
                result.add(loadedClass);
            } else {
                throw new JsonIoException("Class: " + className + " is undefined.");
            }
        }

        return result;
    }


    ///// ACCESSOR PULL IN ???????

    public void clearCaches() {
        deepMethodCache.clear();
        deepFieldCache.clear();
        methodCache.clear();
        accessorsCache.clear();
    }

    public void clearMethodCaches() {
        methodCache.clear();
        deepMethodCache.clear();
        accessorsCache.clear();
    }

    public void clearFieldCaches() {
        deepFieldCache.clear();
        accessorsCache.clear();
    }


    private List<Accessor> buildDeepAccessors(final Class<?> c) {
        final Set<String> inclusions = includedFieldNames.get(c);
        final Set<String> exclusions = new HashSet<>();
        final Map<String, Field> deepDeclaredFields = this.getDeepDeclaredFields(c, exclusions);
        final Map<String, Method> possibleMethods = getDeepDeclaredMethods(c);
        final List<Accessor> accessors = new ArrayList<>(deepDeclaredFields.size());

        final List<Map.Entry<String, Field>> fields = (inclusions == null) ?
                buildExclusiveFields(deepDeclaredFields, exclusions) :
                buildInclusiveFields(deepDeclaredFields, inclusions);

        for (final Map.Entry<String, Field> entry : fields) {

            final Field field = entry.getValue();
            final String key = entry.getKey();

            Accessor accessor = this.findAccessor(field, possibleMethods, key);

            if (accessor == null) {
                accessor = createAccessorFromField(field, key);
            }

            if (accessor != null) {
                accessors.add(accessor);
            }
        }

        return Collections.unmodifiableList(accessors);
    }

    private Accessor findAccessor(Field field, Map<String, Method> possibleMethods, String key) {
        for (final AccessorFactory factory : this.accessorFactories) {
            try {
                final Accessor accessor = factory.createAccessor(field, this.nonStandardMappings, possibleMethods, key);

                if (accessor != null) {
                    return accessor;
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable e) {
                // Handle the exception if needed
                return null;
            }
        }
        return null;
    }

    private List<Map.Entry<String, Field>> buildInclusiveFields(final Map<String, Field> deepDeclaredFields, final Set<String> inclusions) {
        final List<Map.Entry<String, Field>> fields = new ArrayList<>(deepDeclaredFields.size());

        for (Map.Entry<String, Field> entry : deepDeclaredFields.entrySet()) {
            if (inclusions.contains(entry.getKey()) && fieldIsNotFiltered(entry.getValue())) {
                fields.add(entry);
            }
        }

        return fields;
    }

    private List<Map.Entry<String, Field>> buildExclusiveFields(final Map<String, Field> deepDeclaredFields, final Set<String> exclusions) {
        final List<Map.Entry<String, Field>> fields = new ArrayList<>(deepDeclaredFields.size());

        for (Map.Entry<String, Field> entry : deepDeclaredFields.entrySet()) {
            Field field = entry.getValue();

            if (!Modifier.isTransient(field.getModifiers()) && !exclusions.contains(field.getName()) && fieldIsNotFiltered(field)) {
                fields.add(entry);
            }
        }

        return fields;
    }

    private boolean fieldIsNotFiltered(Field field) {
        for (FieldFilter filter : this.fieldFilters) {
            if (filter.filter(field)) {
                return false;
            }
        }
        return true;
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
            for (final Method m : this.getFilteredMethods(currentClass)) {
                map.put(m.getName(), m);
            }

            currentClass = currentClass.getSuperclass();
        }

        return Collections.synchronizedMap(map);
    }

    public List<Method> getFilteredMethods(Class<?> c) {
        return methodCache.computeIfAbsent(c, this::buildFilteredMethods);
    }

    public List<Method> buildFilteredMethods(Class<?> c) {
        final List<Method> methods = new ArrayList<>();

        for (Method method : c.getDeclaredMethods()) {
            if (!filteredMethodNames.contains(method.getName()) && noMethodFiltersMatch(this.methodFilters, method)) {
                methods.add(method);
            }
        }

        return methods;
    }

    // Helper method to check if none of the filters match
    private static boolean noMethodFiltersMatch(List<MethodFilter> filters, Method method) {
        for (MethodFilter filter : filters) {
            if (filter.filter(method)) {
                return false;
            }
        }
        return true;
    }

    public Map<String, Field> buildDeepFieldMap(Class<?> c, final Set<String> exclusions) {
        Convention.throwIfNull(c, "class cannot be null");
        Convention.throwIfNull(exclusions, "exclusions cannot be null");

        final Map<String, Field> map = new LinkedHashMap<>();

        Class<?> curr = c;
        while (curr != Object.class) {
            final Field[] fields = curr.getDeclaredFields();

            final Collection<String> excludedForClass = this.excludedFieldNames.get(curr);

            if (excludedForClass != null) {
                exclusions.addAll(excludedForClass);
            }

            for (Field f : fields) {
                final String name = f.getName();
                if (map.putIfAbsent(name, f) != null) {
                    map.put(f.getDeclaringClass().getSimpleName() + '.' + name, f);
                }
            }

            curr = curr.getSuperclass();
        }

        return Collections.synchronizedMap(map);
    }
}
