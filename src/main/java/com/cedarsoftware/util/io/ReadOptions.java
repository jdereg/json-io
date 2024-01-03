package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.EnumClassFactory;
import com.cedarsoftware.util.io.factory.ThrowableFactory;
import com.cedarsoftware.util.reflect.Injector;
import com.cedarsoftware.util.reflect.InjectorFactory;
import com.cedarsoftware.util.reflect.filters.FieldFilter;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class contains all the "feature" control (options) for controlling json-io's
 * flexibility in reading JSON. An instance of this class is passed to the JsonReader.toJson() APIs
 * to set the desired features.
 * <br/><br/>
 * You can make this class immutable and then store the class for re-use.
 * Call the ".build()" method and then no longer can any methods that change state be
 * called - it will throw a JsonIoException.
 * <br/><br/>
 * This class can be created from another ReadOptions instance, using the "copy constructor"
 * that takes a ReadOptions. All properties of the other ReadOptions will be copied to the
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
public class ReadOptions {
    protected ClassLoader classLoader = ReadOptions.class.getClassLoader();
    protected Class<?> unknownTypeClass = null;
    protected boolean failOnUnknownType = false;
    protected boolean closeStream = true;
    protected int maxDepth = 1000;
    protected JsonReader.MissingFieldHandler missingFieldHandler = null;

    /**
     * @return ReconstructionType which is how you will receive the parsed JSON objects.  This will be either
     * JAVA_OBJECTS (default) or JSON_VALUE's (useful for large, more simplistic objects within the JSON data sets).
     */
    protected ReturnType returnType = ReturnType.JAVA_OBJECTS;

    /**
     * @return boolean will return true if NAN and Infinity are allowed to be read in as Doubles and Floats,
     * else a JsonIoException will be thrown if these are encountered.  default is false per JSON standard.
     */
    @Getter
    protected boolean allowNanAndInfinity = false;

    protected Map<String, String> aliasTypeNames = new ConcurrentHashMap<>();
    protected Map<Class<?>, Class<?>> coercedTypes = new ConcurrentHashMap<>();
    protected Map<Class<?>, JsonReader.JsonClassReader> customReaderClasses = new ConcurrentHashMap<>();
    protected Map<Class<?>, JsonReader.ClassFactory> classFactoryMap = new ConcurrentHashMap<>();
    protected Set<Class<?>> notCustomReadClasses = Collections.synchronizedSet(new LinkedHashSet<>());
    protected Set<Class<?>> nonRefClasses = Collections.synchronizedSet(new LinkedHashSet<>());

    protected Map<Class<?>, Set<String>> excludedFieldNames;

    protected List<FieldFilter> fieldFilters;

    protected List<InjectorFactory> injectorFactories;

    // Creating the Accessors (methodHandles) is expensive so cache the list of Accessors per Class
    private final Map<Class<?>, Map<String, Injector>> injectorsCache = new ConcurrentHashMap<>(200, 0.8f, Runtime.getRuntime().availableProcessors());

    protected Map<Class<?>, Map<String, String>> nonStandardMappings;

    // Runtime cache (not feature options)
    private final Map<Class<?>, JsonReader.JsonClassReader> readerCache = new ConcurrentHashMap<>(300);
    private final JsonReader.ClassFactory throwableFactory = new ThrowableFactory();
    private final JsonReader.ClassFactory enumFactory = new EnumClassFactory();

    //  Cache of fields used for accessors.  controlled by ignoredFields
    private final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap(200, 0.8f, Runtime.getRuntime().availableProcessors());


    /**
     * Default constructor.  Prevent instantiation outside of package.
     */
    ReadOptions() {
    }

    /**
     * @return ClassLoader to be used when reading JSON to resolve String named classes.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @return boolean true if an 'unknownTypeClass' is set, false if it is not sell (null).
     */
    public boolean isFailOnUnknownType() {
        return failOnUnknownType;
    }

    /**
     * @return the Class which will have unknown fields set upon it.  Typically this is a Map derivative.
     */
    public Class<?> getUnknownTypeClass()
    {
        return unknownTypeClass;
    }

    /**
     * @return boolean 'true' if the InputStream should be closed when the reading is finished.  The default is 'true.'
     */
    public boolean isCloseStream() {
        return closeStream;
    }

    /**
     * @return int maximum level the JSON can be nested.  Once the parsing nesting level reaches this depth, a
     * JsonIoException will be thrown instead of a StackOverflowException.  Prevents security risk from StackOverflow
     * attack vectors.
     */
    public int getMaxDepth() {
        return maxDepth;
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
     * @return boolean true if the passed in Class name is being coerced to another type, false otherwise.
     */
    public boolean isClassCoerced(String className)
    {
        return coercedTypes.containsKey(className);
    }

    /**
     * Fetch the coerced class for the passed in fully qualified class name.
     * @param c Class to coerce
     * @return Class destination (coerced) class or null if there is none.
     */
    public Class<?> getCoercedClass(Class<?> c)
    {
        return coercedTypes.get(c);
    }

    /**
     * @return JsonReader.MissingFieldHandler to be called when a field in the JSON is read in, yet there is no
     * corresponding field on the destination object to receive the field value.
     */
    JsonReader.MissingFieldHandler getMissingFieldHandler() {
        return missingFieldHandler;
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
     * @param clazz Class to see if it is on the not-customized list.  Classes are added to this list when
     *              a class is being picked up through inheritance, and you don't want it to have a custom
     *              reader associated to it.
     * @return boolean true if the passed in class is on the not-customized list, false otherwise.
     */
    public boolean isNotCustomReaderClass(Class<?> clazz) {
        return notCustomReadClasses.contains(clazz);
    }

    /**
     * @param clazz Class to check to see if there is a custom reader associated to it.
     * @return boolean true if there is an associated custom reader class associated to the passed in class,
     * false otherwise.
     */
    public boolean isCustomReaderClass(Class<?> clazz) {
        return customReaderClasses.containsKey(clazz);
    }

    /**
     * Get the ClassFactory associated to the passed in class.
     * @param c Class for which to fetch the ClassFactory.
     * @return JsonReader.ClassFactory instance associated to the passed in class.
     */
    public JsonReader.ClassFactory getClassFactory(Class<?> c) {
        if (c == null) {
            return null;
        }

        JsonReader.ClassFactory factory = this.classFactoryMap.get(c);

        if (factory != null) {
            return factory;
        }

        if (Throwable.class.isAssignableFrom(c)) {
            return throwableFactory;
        }

        Optional optional = MetaUtils.getClassIfEnum(c);

        if (optional.isPresent()) {
            return enumFactory;
        }

        return null;
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    private static final class NullClass implements JsonReader.JsonClassReader {
    }

    private static final ReadOptions.NullClass nullReader = new ReadOptions.NullClass();

    /**
     * Fetch the custom reader for the passed in Class.  If it is cached (already associated to the
     * passed in Class), return the same instance, otherwise, make a call to get the custom reader
     * and store that result.
     * @param c Class of object for which fetch a custom reader
     * @return JsonClassReader for the custom class (if one exists), null otherwise.
     */
    public JsonReader.JsonClassReader getCustomReader(Class<?> c) {
        JsonReader.JsonClassReader reader = readerCache.get(c);
        if (reader == null) {
            reader = forceGetCustomReader(c);
            readerCache.put(c, reader);
        }

        return reader == nullReader ? null : reader;
    }

    private JsonReader.JsonClassReader forceGetCustomReader(Class<?> c) {
        JsonReader.JsonClassReader closestReader = nullReader;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class<?>, JsonReader.JsonClassReader> entry : customReaderClasses.entrySet()) {
            Class<?> clz = entry.getKey();
            if (clz == c) {
                return entry.getValue();
            }
            int distance = MetaUtils.computeInheritanceDistance(c, clz);
            if (distance != -1 && distance < minDistance) {
                minDistance = distance;
                closestReader = entry.getValue();
            }
        }

        return closestReader;
    }


    /**
     * @return true if returning items in basic JSON object format
     */
    public boolean isReturningJsonObjects() {
        return returnType == ReturnType.JSON_OBJECTS;
    }

    /**
     * @return true if returning items in full Java object formats.  Useful for accurate reproduction of graphs
     * into the orginal types such as when cloning objects.
     */
    public boolean isReturningJavaObjects() {
        return returnType == ReturnType.JAVA_OBJECTS;
    }

    public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {
        if (classToTraverse == null) {
            return Collections.emptyMap();
        }
        return this.injectorsCache.computeIfAbsent(classToTraverse, this::buildInjectors);
    }


    public void clearCaches() {
        injectorsCache.clear();
    }

    private Map<String, Injector> buildInjectors(Class<?> c) {
        final Set<String> exclusions = new HashSet<>();
        final Map<String, Field> fields = getDeepDeclaredFields(c, exclusions);
        final Map<String, Injector> injectors = new LinkedHashMap<>(fields.size());

        for (final Map.Entry<String, Field> entry : fields.entrySet()) {
            final Field field = entry.getValue();

            if (exclusions.contains(field.getName()) || fieldIsFiltered(field)) {
                continue;
            }

            final String fieldName = entry.getKey();
            Injector injector = this.findInjector(field, fieldName);

            if (injector == null) {
                injector = Injector.create(field, fieldName);
            }

            if (injector != null) {
                injectors.put(fieldName, injector);
            }

        }
        return injectors;
    }

    private Injector findInjector(Field field, String key) {
        for (final InjectorFactory factory : this.injectorFactories) {
            try {
                final Injector injector = factory.createInjector(field, this.nonStandardMappings, key);

                if (injector != null) {
                    return injector;
                }
            } catch (Exception ignore) {
                System.out.println(ignore);
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
    public Map<String, Field> getDeepDeclaredFields(final Class<?> c, final Set<String> exclusions) {
        return classMetaCache.computeIfAbsent(c, this::buildDeepFieldMap);
    }

    /**
     * Gets the declared fields for the full class hierarchy of a given class
     *
     * @param c - given class.
     * @return Map - map of string fieldName to Field Object.  This will have the
     * deep list of fields for a given class.
     */
    public Map<String, Field> buildDeepFieldMap(final Class<?> c) {
        Convention.throwIfNull(c, "class cannot be null");

        final Map<String, Field> map = new LinkedHashMap<>();
        final Set<String> ignoredFields = new HashSet<>();

        Class<?> curr = c;
        while (curr != null) {
            final Field[] fields = curr.getDeclaredFields();

            final Set<String> excludedForClass = this.excludedFieldNames.get(curr);

            if (excludedForClass != null) {
                ignoredFields.addAll(excludedForClass);
            }

            for (Field field : fields) {
                String name = field.getName();

                if (map.containsKey(name)) {
                    name = field.getDeclaringClass().getSimpleName() + '.' + name;
                }

                if (ignoredFields.contains(name) || fieldIsFiltered(field)) {
                    continue;
                }

                map.put(name, field);
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
