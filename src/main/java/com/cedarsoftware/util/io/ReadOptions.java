package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.ReturnType;
import com.cedarsoftware.util.io.factory.CalendarFactory;
import com.cedarsoftware.util.io.factory.DateFactory;
import com.cedarsoftware.util.io.factory.DurationFactory;
import com.cedarsoftware.util.io.factory.EnumClassFactory;
import com.cedarsoftware.util.io.factory.InstantFactory;
import com.cedarsoftware.util.io.factory.LocalDateFactory;
import com.cedarsoftware.util.io.factory.LocalDateTimeFactory;
import com.cedarsoftware.util.io.factory.LocalTimeFactory;
import com.cedarsoftware.util.io.factory.MonthDayFactory;
import com.cedarsoftware.util.io.factory.OffsetDateTimeFactory;
import com.cedarsoftware.util.io.factory.OffsetTimeFactory;
import com.cedarsoftware.util.io.factory.PeriodFactory;
import com.cedarsoftware.util.io.factory.SqlDateFactory;
import com.cedarsoftware.util.io.factory.StackTraceElementFactory;
import com.cedarsoftware.util.io.factory.ThrowableFactory;
import com.cedarsoftware.util.io.factory.TimeZoneFactory;
import com.cedarsoftware.util.io.factory.YearFactory;
import com.cedarsoftware.util.io.factory.YearMonthFactory;
import com.cedarsoftware.util.io.factory.ZoneIdFactory;
import com.cedarsoftware.util.io.factory.ZoneOffsetFactory;
import com.cedarsoftware.util.io.factory.ZonedDateTimeFactory;

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
    private ClassLoader classLoader = ReadOptions.class.getClassLoader();
    private Class<?> unknownTypeClass = null;
    private boolean failOnUnknownType = false;
    private int maxDepth = 1000;
    private JsonReader.MissingFieldHandler missingFieldHandler = null;
    private ReturnType returnType = ReturnType.JAVA_OBJECTS;
    private boolean allowNanAndInfinity = false;
    private Map<String, String> aliasTypeNames = new ConcurrentHashMap<>();
    private Map<Class<?>, Class<?>> coercedTypes = new ConcurrentHashMap<>();
    private Map<Class<?>, JsonReader.JsonClassReader> customReaderClasses = new ConcurrentHashMap<>();
    private Map<Class<?>, JsonReader.ClassFactory> classFactoryMap = new ConcurrentHashMap<>();
    private Set<Class<?>> notCustomReadClasses = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Map<Class<?>, Class<?>> BASE_COERCED_TYPES = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonReader.JsonClassReader> BASE_READERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonReader.ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();
    private boolean built = false;
    // Runtime cache (not feature options)
    private final Map<Class<?>, JsonReader.JsonClassReader> readerCache = new ConcurrentHashMap<>(300);

    private final JsonReader.ClassFactory throwableFactory = new ThrowableFactory();

    private final JsonReader.ClassFactory enumFactory = new EnumClassFactory();

    static {
        // ClassFactories
        JsonReader.ClassFactory mapFactory = new JsonReader.MapFactory();
        assignInstantiator(Map.class, mapFactory);
        assignInstantiator(SortedMap.class, mapFactory);

        JsonReader.ClassFactory colFactory = new JsonReader.CollectionFactory();

        assignInstantiator(Collection.class, colFactory);
        assignInstantiator(List.class, colFactory);
        assignInstantiator(Set.class, colFactory);
        assignInstantiator(SortedSet.class, colFactory);

        assignInstantiator(LocalDate.class, new LocalDateFactory());
        assignInstantiator(LocalTime.class, new LocalTimeFactory());
        assignInstantiator(LocalDateTime.class, new LocalDateTimeFactory());
        assignInstantiator(ZonedDateTime.class, new ZonedDateTimeFactory());
        assignInstantiator(OffsetDateTime.class, new OffsetDateTimeFactory());
        assignInstantiator(OffsetTime.class, new OffsetTimeFactory());
        assignInstantiator(YearMonth.class, new YearMonthFactory());
        assignInstantiator(MonthDay.class, new MonthDayFactory());
        assignInstantiator(Year.class, new YearFactory());
        assignInstantiator(ZoneOffset.class, new ZoneOffsetFactory());
        assignInstantiator(ZoneId.class, new ZoneIdFactory());
        assignInstantiator(Instant.class, new InstantFactory());
        assignInstantiator(Period.class, new PeriodFactory());
        assignInstantiator(Duration.class, new DurationFactory());

        CalendarFactory calendarFactory = new CalendarFactory();
        assignInstantiator(GregorianCalendar.class, calendarFactory);
        assignInstantiator(Calendar.class, calendarFactory);

        DateFactory dateFactory = new DateFactory();
        assignInstantiator(Date.class, dateFactory);
        assignInstantiator(Timestamp.class, dateFactory);
        assignInstantiator(java.sql.Date.class, new SqlDateFactory());

        TimeZoneFactory timeZoneFactory = new TimeZoneFactory();
        assignInstantiator(TimeZone.class, timeZoneFactory);
        Class<?> c = MetaUtils.classForName("sun.util.calendar.ZoneInfo", ReadOptions.class.getClassLoader());
        if (c != null) {
            assignInstantiator(c, timeZoneFactory);
        }

        assignInstantiator(StackTraceElement.class, new StackTraceElementFactory());

        //  Readers
        addPermanentReader(String.class, new Readers.StringReader());
        addPermanentReader(AtomicBoolean.class, new Readers.AtomicBooleanReader());
        addPermanentReader(AtomicInteger.class, new Readers.AtomicIntegerReader());
        addPermanentReader(AtomicLong.class, new Readers.AtomicLongReader());
        addPermanentReader(BigInteger.class, new Readers.BigIntegerReader());
        addPermanentReader(BigDecimal.class, new Readers.BigDecimalReader());

        addPermanentReader(Locale.class, new Readers.LocaleReader());
        addPermanentReader(Class.class, new Readers.ClassReader());
        addPermanentReader(StringBuilder.class, new Readers.StringBuilderReader());
        addPermanentReader(StringBuffer.class, new Readers.StringBufferReader());
        addPermanentReader(UUID.class, new Readers.UUIDReader());
        addPermanentReader(URL.class, new Readers.URLReader());

        //  JVM Readers > 1.8
        addPossiblePermanentReader("java.lang.Record", new Readers.RecordReader());

        // Coerced Types
        addPermanentCoercedType("java.time.ZoneRegion", ZoneId.class);
        addPermanentCoercedType("java.util.Arrays$ArrayList", ArrayList.class);
        addPermanentCoercedType("java.util.LinkedHashMap$LinkedKeySet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.LinkedHashMap$LinkedValues", ArrayList.class);
        addPermanentCoercedType("java.util.HashMap$KeySet", HashSet.class);
        addPermanentCoercedType("java.util.HashMap$Values", ArrayList.class);
        addPermanentCoercedType("java.util.TreeMap$KeySet", TreeSet.class);
        addPermanentCoercedType("java.util.TreeMap$Values", ArrayList.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentHashMap$KeySet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentHashMap$KeySetView", LinkedHashSet.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentHashMap$Values", ArrayList.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentHashMap$ValuesView", ArrayList.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentSkipListMap$KeySet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.concurrent.ConcurrentSkipListMap$Values", ArrayList.class);
        addPermanentCoercedType("java.util.IdentityHashMap$KeySet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.IdentityHashMap$Values", ArrayList.class);
        addPermanentCoercedType("java.util.Collections$EmptyList", Collections.EMPTY_LIST.getClass());
        addPermanentCoercedType("java.util.Collections$SingletonSet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.Collections$SingletonList", ArrayList.class);
        addPermanentCoercedType("java.util.Collections$SingletonMap", LinkedHashMap.class);
        addPermanentCoercedType("java.util.Collections$UnmodifiableRandomAccessList", ArrayList.class);
        addPermanentCoercedType("java.util.Collections$UnmodifiableSet", LinkedHashSet.class);
        addPermanentCoercedType("java.util.Collections$UnmodifiableMap", LinkedHashMap.class);
    }

    /**
     * Start with default options.
     */
    public ReadOptions() {
        // TODO: Blow out alias list for common types, will greatly shrink the JSON content.
        // TODO: This feature is broken until the aliasTypeNames are shared between ReadOptions/WriteOptions
//        aliasTypeName("java.util.ArrayList", "ArrayList");
//        aliasTypeName("java.util.concurrent.atomic.AtomicBoolean", "AtomicBoolean");

        aliasTypeName(Class.class, "class");
        aliasTypeName(String.class, "string");
        aliasTypeName(Date.class, "date");

        // Use true primitive types for the primitive wrappers.
        aliasTypeName(Byte.class, "byte");
        aliasTypeName(Short.class, "short");
        aliasTypeName(Integer.class, "int");
        aliasTypeName(Long.class, "long");
        aliasTypeName(Float.class, "float");
        aliasTypeName(Double.class, "double");
        aliasTypeName(Character.class, "char");
        aliasTypeName(Boolean.class, "boolean");

        coercedTypes.putAll(BASE_COERCED_TYPES);
        customReaderClasses.putAll(BASE_READERS);
        readerCache.putAll(BASE_READERS);
        classFactoryMap.putAll(BASE_CLASS_FACTORIES);
    }

    /**
     * Copy all the settings from the passed in 'other' ReadOptions
     * @param other ReadOptions - source to copy from.
     */
    public ReadOptions(ReadOptions other) {
        classLoader = other.classLoader;
        unknownTypeClass = other.unknownTypeClass;
        failOnUnknownType = other.failOnUnknownType;
        maxDepth = other.maxDepth;
        missingFieldHandler = other.missingFieldHandler;
        returnType = other.returnType;
        allowNanAndInfinity = other.allowNanAndInfinity;
        aliasTypeNames.putAll(other.aliasTypeNames);
        coercedTypes.putAll(other.coercedTypes);
        notCustomReadClasses.addAll(other.notCustomReadClasses);
        customReaderClasses.putAll(other.customReaderClasses);
        readerCache.putAll(other.readerCache);
        classFactoryMap.putAll(other.classFactoryMap);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another during instance creation.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     * @param className String class name (fully qualified name) that will be coerced to another type.
     * @param c Class to coerce to.  For example, java.util.Collections$EmptyList to java.util.ArrayList.
     */
    public static void addPermanentCoercedType(String className, Class<?> c) {
        Class<?> clazz = MetaUtils.classForName(className, ReadOptions.class.getClassLoader());
        if (clazz != null) {
            BASE_COERCED_TYPES.put(clazz, c);
        }
    }

    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.  This method will add
     * the custom reader such that it will be there permanently, for the
     * life of the JVM (static).
     *
     * @param c      Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addPermanentReader(Class<?> c, JsonReader.JsonClassReader reader) {
        BASE_READERS.put(c, reader);
    }

    /**
     * Add a possible Permanent Reader.  The reason it is possible, is that it is added by String
     * fully qualified class name, which may/may not be in the JVM.  For example, if json-io
     * is built with JDK 1.8, and you need a class from JDK 11, for example, use this method
     * to add a permanent reader.
     * @param className String class name for which to add a customized reader.
     * @param reader The customized reader to associate to the fully qualified class name.
     */
    public static void addPossiblePermanentReader(String className, JsonReader.JsonClassReader reader) {
        Class<?> clazz = MetaUtils.classForName(className, JsonReader.class.getClassLoader());
        if (clazz != null) {
            addPermanentReader(clazz, reader);
        }
    }

    /**
     * @param classToCreate Class that will need to use a JsonReader.ClassFactory for instantiation and loading.
     * @param factory JsonReader.ClassFactory instance that has been created to instantiate a difficult
     *                to create and load class (class c).
     */
    public static void assignInstantiator(Class<?> classToCreate, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(classToCreate, factory);
    }

    // Private method to check if the object is built
    private void throwIfBuilt() {
        if (built) {
            throw new JsonIoException("This instance of ReadOptions is built and cannot be modified.  You can create another instance from this instance using the constructor for a mutable copy.");
        }
    }

    /**
     * Seal the instance of this class so that no more changes can be made to it.
     * @return ReadOptions for chained access.
     */
    public ReadOptions build() {
        aliasTypeNames = Collections.unmodifiableMap(new LinkedHashMap<>(aliasTypeNames));
        coercedTypes = Collections.unmodifiableMap(new LinkedHashMap<>(coercedTypes));
        notCustomReadClasses = Collections.unmodifiableSet(new LinkedHashSet<>(notCustomReadClasses));
        customReaderClasses = Collections.unmodifiableMap(new LinkedHashMap<>(customReaderClasses));
        classFactoryMap = Collections.unmodifiableMap(new LinkedHashMap<>(classFactoryMap));
        this.built = true;
        return this;
    }

    /**
     * @return boolean true if the instance of this class is built (read-only), otherwise false is returned,
     * indicating that changes can still be made to this ReadOptions instance.
     */
    public boolean isBuilt() {
        return built;
    }

    /**
     * @return ClassLoader to be used when reading JSON to resolve String named classes.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * @param classLoader ClassLoader to use when reading JSON to resolve String named classes.
     * @return ReadOptions for chained access.
     */
    public ReadOptions classLoader(ClassLoader classLoader) {
        throwIfBuilt();
        this.classLoader = classLoader;
        return this;
    }

    /**
     * @return boolean true if an 'unknownTypeClass' is set, false if it is not sell (null).
     */
    public boolean isFailOnUnknownType() {
        return failOnUnknownType;
    }

    /**
     * @param fail boolean indicating whether we should fail on unknown type encountered.
     * @return ReadOptions for chained access.
     */
    public ReadOptions failOnUnknownType(boolean fail) {
        throwIfBuilt();
        failOnUnknownType = fail;
        return this;
    }

    /**
     * @return the Class which will have unknown fields set upon it.  Typically this is a Map derivative.
     */
    public Class<?> getUnknownTypeClass()
    {
        return unknownTypeClass;
    }

    /**
     * Set a class to use when the JSON reader cannot instantiate a class. When that happens, it will throw
     * a JsonIoException() if no 'unknownTypeClass' is set, otherwise it will create the instance of the class
     * specified as the 'unknownTypeClass.' A common one to use, for example, if java.util.LinkedHashMap.
     * @param c Class to instantiate when a String specified class in the JSON cannot be instantiated.  Set to null
     *          if you want a JsonIoException to be thrown when this happens.
     * @return ReadOptions for chained access.
     */
    public ReadOptions unknownTypeClass(Class<?> c) {
        throwIfBuilt();
        unknownTypeClass = c;
        return this;
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
     * @param maxDepth int maximum of the depth that JSON Objects {...} can be nested.  Set this to prevent
     *                 security risk from StackOverflow attack vectors.
     * @return ReadOptions for chained access.
     */
    public ReadOptions maxDepth(int maxDepth) {
        throwIfBuilt();
        this.maxDepth = maxDepth;
        return this;
    }

    /**
     * @return boolean will return true if NAN and Infinity are allowed to be read in as Doubles and Floats,
     * else a JsonIoException will be thrown if these are encountered.  default is false per JSON standard.
     */
    public boolean isAllowNanAndInfinity() {
        return allowNanAndInfinity;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow Double and Floats to be
     *                            read in as NaN and +Inf, -Inf [infinity], false and a JsonIoException will be
     *                            thrown if these values are encountered
     * @return ReadOptions for chained access.
     */
    public ReadOptions allowNanAndInfinity(boolean allowNanAndInfinity) {
        throwIfBuilt();
        this.allowNanAndInfinity = allowNanAndInfinity;
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
     * @return ReadOptions for chained access.
     */
    public ReadOptions aliasTypeNames(Map<String, String> aliasTypeNames) {
        throwIfBuilt();
        this.aliasTypeNames.clear();
        aliasTypeNames.forEach((key, value) -> this.aliasTypeNames.put(value, key));
        return this;
    }

    /**
     * @param type  String class name
     * @param alias String shorter name to use, typically.
     * @return ReadOptions for chained access.
     */
    public ReadOptions aliasTypeName(Class<?> type, String alias) {
        throwIfBuilt();
        aliasTypeNames.put(alias, type.getName());
        return this;
    }

    /**
     * @param typeName String class name
     * @param alias String shorter name to use, typically.
     * @return ReadOptions for chained access.
     */
    public ReadOptions aliasTypeName(String typeName, String alias) {
        throwIfBuilt();
        aliasTypeNames.put(alias, typeName);
        return this;
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
     * @return Class destination (coerced) class.
     */
    public Class<?> getCoercedClass(Class<?> c)
    {
        return coercedTypes.get(c);
    }

    /**
     * Coerce a class from one type (named in the JSON) to another type.  For example, converting
     * "java.util.Collections$SingletonSet" to a LinkedHashSet.class.
     * @param sourceClassName String class name to coerce to another type.
     * @param destClass Class to coerce into.
     * @return ReadOptions for chained access.
     */
    public ReadOptions coerceClass(String sourceClassName, Class<?> destClass) {
        throwIfBuilt();
        Class<?> clazz = MetaUtils.classForName(sourceClassName, classLoader);
        if (clazz != null) {
            coercedTypes.put(clazz, destClass);
        }
        return this;
    }

    /**
     * @return JsonReader.MissingFieldHandler to be called when a field in the JSON is read in, yet there is no
     * corresponding field on the destination object to receive the field value.
     */
    JsonReader.MissingFieldHandler getMissingFieldHandler() {
        return missingFieldHandler;
    }

    /**
     * @param missingFieldHandler JsonReader.MissingFieldHandler implementation to call.  This method will be
     * called when a field in the JSON is read in, yet there is no corresponding field on the destination object to
     *                            receive the value.
     * @return ReadOptions for chained access.
     */
    public ReadOptions missingFieldHandler(JsonReader.MissingFieldHandler missingFieldHandler) {
        throwIfBuilt();
        this.missingFieldHandler = missingFieldHandler;
        return this;
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
     * @return Set of all Classes on the not-customized list.
     */
    public Set<Class<?>> getNotCustomReadClasses() {
        return built ? notCustomReadClasses : new LinkedHashSet<>(notCustomReadClasses);
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom reader (that could happen through inheritance).
     * @param notCustomClass Class to add to the not-customized list.
     * @return ReadOptions for chained access.
     */
    public ReadOptions addNotCustomReaderClass(Class<?> notCustomClass) {
        throwIfBuilt();
        notCustomReadClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                   will be dropped and this Collection will establish the new list.
     * @return ReadOptions for chained access.
     */
    public ReadOptions setNotCustomReaderClasses(Collection<Class<?>> notCustomClasses) {
        throwIfBuilt();
        notCustomReadClasses.clear();
        notCustomReadClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @return Map of Class to custom JsonClassReader's used to read JSON when the class is encountered during
     * serialization to JSON.
     */
    public Map<Class<?>, JsonReader.JsonClassReader> getCustomReaderClasses() {
        return built ? customReaderClasses : new LinkedHashMap<>(customReaderClasses);
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
     * @param customReaderClasses Map of Class to JsonReader.JsonClassReader.  Establish the passed in Map as the
     *                             established Map of custom readers to be used when reading JSON. Using this method
     *                             more than once, will set the custom readers to only the values from the Set in
     *                             the last call made.
     * @return ReadOptions for chained access.
     */
    public ReadOptions setCustomReaderClasses(Map<? extends Class<?>, ? extends JsonReader.JsonClassReader> customReaderClasses) {
        throwIfBuilt();
        this.customReaderClasses.clear();
        this.customReaderClasses.putAll(customReaderClasses);
        return this;
    }

    /**
     * @param clazz Class to add a custom reader for.
     * @param customReader JsonClassReader to use when the passed in Class is encountered during serialization.
     *                     Custom readers are passed an empty instance.  Use a ClassFactory if you want to instantiate
     *                     and load the class in one step.
     * @return ReadOptions for chained access.
     */
    public ReadOptions addCustomReaderClass(Class<?> clazz, JsonReader.JsonClassReader customReader) {
        throwIfBuilt();
        customReaderClasses.put(clazz, customReader);
        return this;
    }

    /**
     * @return Map of all Class's associated to JsonReader.ClassFactory's.
     */
    public Map<Class<?>, JsonReader.ClassFactory> getClassFactories() {
        return built ? classFactoryMap : new LinkedHashMap<>(classFactoryMap);
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
     * Associate multiple ClassFactory instances to Classes that needs help being constructed and read in.
     * @param factories Map of entries that are Class to Factory. The Factory class knows how to instantiate
     *                  and load the class associated to it.
     * @return ReadOptions for chained access.
     */
    public ReadOptions setClassFactories(Map<Class<?>, ? extends JsonReader.ClassFactory> factories) {
        throwIfBuilt();
        classFactoryMap.clear();
        classFactoryMap.putAll(factories);
        return this;
    }
    
    /**
     * Associate a ClassFactory to a Class that needs help being constructed and read in.
     * @param clazz Class that is difficult to instantiate.
     * @param factory Class written to instantiate the 'clazz' and load it's values.
     * @return ReadOptions for chained access.
     */
    public ReadOptions addClassFactory(Class<?> clazz, JsonReader.ClassFactory factory) {
        throwIfBuilt();
        classFactoryMap.put(clazz, factory);
        return this;
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
     * @return ReconstructionType which is how you will receive the parsed JSON objects.  This will be either
     * JAVA_OBJECTS (default) or JSON_VALUE's (useful for large, more simplistic objects within the JSON data sets).
     */
    public ReturnType getReturnType()
    {
        return returnType;
    }

    /**
     * Set how you want the parsed JSON return - as a Java Object graph or as raw JSON_VALUE's (faster, useful for
     * large, more simple object data sets).  For JAVA_OBJECT's the returned value will be of the class type
     * passed into JsonReader.toJava(json, rootClass).
     * @param reconstructionType which is either ReconstructionType.JAVA_OBJECTS or ReconstructionType.JSON_VALUES
     */
    public ReadOptions returnType(ReturnType reconstructionType) {
        throwIfBuilt();
        this.returnType = reconstructionType;
        return this;
    }
}
