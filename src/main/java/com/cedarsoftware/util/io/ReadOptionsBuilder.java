package com.cedarsoftware.util.io;

import com.cedarsoftware.util.ReconstructionType;
import com.cedarsoftware.util.io.factory.CalendarFactory;
import com.cedarsoftware.util.io.factory.DateFactory;
import com.cedarsoftware.util.io.factory.EnumClassFactory;
import com.cedarsoftware.util.io.factory.LocalDateFactory;
import com.cedarsoftware.util.io.factory.LocalDateTimeFactory;
import com.cedarsoftware.util.io.factory.LocalTimeFactory;
import com.cedarsoftware.util.io.factory.OffsetDateTimeFactory;
import com.cedarsoftware.util.io.factory.OffsetTimeFactory;
import com.cedarsoftware.util.io.factory.SqlDateFactory;
import com.cedarsoftware.util.io.factory.StackTraceElementFactory;
import com.cedarsoftware.util.io.factory.ThrowableFactory;
import com.cedarsoftware.util.io.factory.TimeZoneFactory;
import com.cedarsoftware.util.io.factory.YearFactory;
import com.cedarsoftware.util.io.factory.YearMonthFactory;
import com.cedarsoftware.util.io.factory.ZoneOffsetFactory;
import com.cedarsoftware.util.io.factory.ZonedDateTimeFactory;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.cedarsoftware.util.io.JsonReader.CLASSLOADER;
import static com.cedarsoftware.util.io.JsonReader.CUSTOM_READER_MAP;
import static com.cedarsoftware.util.io.JsonReader.FACTORIES;
import static com.cedarsoftware.util.io.JsonReader.FAIL_ON_UNKNOWN_TYPE;
import static com.cedarsoftware.util.io.JsonReader.MISSING_FIELD_HANDLER;
import static com.cedarsoftware.util.io.JsonReader.MissingFieldHandler;
import static com.cedarsoftware.util.io.JsonReader.NOT_CUSTOM_READER_MAP;
import static com.cedarsoftware.util.io.JsonReader.TYPE_NAME_MAP;
import static com.cedarsoftware.util.io.JsonReader.TYPE_NAME_MAP_REVERSE;
import static com.cedarsoftware.util.io.JsonReader.UNKNOWN_OBJECT;
import static com.cedarsoftware.util.io.JsonReader.USE_MAPS;

/**
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
public class ReadOptionsBuilder {

    private final ReadOptionsImplementation readOptions = new ReadOptionsImplementation();

    private final Map<String, String> typeNameMap = new LinkedHashMap<>();

    private static final Map<Class<?>, JsonReader.JsonClassReader> BASE_READERS = new ConcurrentHashMap<>();

    private static final Map<Class<?>, JsonReader.ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();

    private static final Map<String, Class<?>> BASE_COERCED_TYPES = new LinkedHashMap<>();

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
        assignInstantiator(Year.class, new YearFactory());
        assignInstantiator(ZoneOffset.class, new ZoneOffsetFactory());

        CalendarFactory calendarFactory = new CalendarFactory();
        assignInstantiator(GregorianCalendar.class, calendarFactory);
        assignInstantiator(Calendar.class, calendarFactory);

        DateFactory dateFactory = new DateFactory();
        assignInstantiator(Date.class, dateFactory);
        assignInstantiator(Timestamp.class, dateFactory);

        assignInstantiator(java.sql.Date.class, new SqlDateFactory());

        TimeZoneFactory timeZoneFactory = new TimeZoneFactory();
        assignInstantiator(TimeZone.class, timeZoneFactory);
        assignInstantiator("sun.util.calendar.ZoneInfo", timeZoneFactory);

        JsonReader.ClassFactory throwableFactory = new ThrowableFactory();
        assignInstantiator(Throwable.class, throwableFactory);
        assignInstantiator(Exception.class, throwableFactory);
        assignInstantiator(RuntimeException.class, throwableFactory);
        assignInstantiator(StackTraceElement.class, new StackTraceElementFactory());

        //  Readers
        addReaderPermanent(String.class, new Readers.StringReader());
        addReaderPermanent(AtomicBoolean.class, new Readers.AtomicBooleanReader());
        addReaderPermanent(AtomicInteger.class, new Readers.AtomicIntegerReader());
        addReaderPermanent(AtomicLong.class, new Readers.AtomicLongReader());
        addReaderPermanent(BigInteger.class, new Readers.BigIntegerReader());
        addReaderPermanent(BigDecimal.class, new Readers.BigDecimalReader());

        addReaderPermanent(Locale.class, new Readers.LocaleReader());
        addReaderPermanent(Class.class, new Readers.ClassReader());
        addReaderPermanent(StringBuilder.class, new Readers.StringBuilderReader());
        addReaderPermanent(StringBuffer.class, new Readers.StringBufferReader());
        addReaderPermanent(UUID.class, new Readers.UUIDReader());
        addReaderPermanent(URL.class, new Readers.URLReader());

        //  JVM Readers > 1.8

        // we can just ignore it - we are at java < 16 now. This is for code compatibility Java<16
        addPossiblePermanentReader(BASE_READERS, "java.lang.Record", Readers.RecordReader::new);

        // Coerced Types
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

    static void addPossiblePermanentReader(Map map, String fqClassName, Supplier<JsonReader.JsonClassReader> reader) {
        try {
            map.put(Class.forName(fqClassName), reader.get());
        } catch (ClassNotFoundException e) {
            // we can just ignore it - this class is not in this jvm or possibly expected to not be found
        }
    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another for creatint into an object.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     *
     * @param fqName Class to assign a custom JSON reader to
     * @param c      The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addPermanentCoercedType(String fqName, Class<?> c) {
        BASE_COERCED_TYPES.put(fqName, c);
    }


    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.  This method will add
     * the customer reader such that it will be there permanently, for the
     * life of the JVM (static).
     *
     * @param c      Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public static void addReaderPermanent(Class c, JsonReader.JsonClassReader reader) {
        BASE_READERS.put(c, reader);
    }


    /**
     * For difficult to instantiate classes, you can add your own ClassFactory
     * which will be called when the passed in class 'c' is encountered.  Your ClassFactory
     * will be called with newInstance(class, Object) and your factory is expected to return
     * a new instance of 'c' and can use values from Object (JsonMap or simple value) to
     * initialize the created class with the appropriate values.
     * <p>
     * This API is an 'escape hatch' to allow ANY object to be instantiated by JsonReader
     * and is useful when you encounter a class that JsonReader cannot instantiate using its
     * internal exhausting attempts (trying all constructors, varying arguments to them, etc.)
     *
     * @param className Class name to assign an ClassFactory to
     * @param factory   ClassFactory that will create 'c' instances
     */
    public static void assignInstantiator(String className, JsonReader.ClassFactory factory) {
        Class<?> c = MetaUtils.safelyIgnoreException(() -> MetaUtils.classForName(className, JsonReader.class.getClassLoader()), (Class<?>) null);

        if (c != null) {
            BASE_CLASS_FACTORIES.put(c, factory);
        }
    }

    /**
     * See comment on method JsonReader.assignInstantiator(String, ClassFactory)
     */
    public static void assignInstantiator(Class c, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(c, factory);
    }

    public ReadOptionsBuilder setUnknownTypeClass(Class<?> c) {
        this.readOptions.unknownTypeClass = c;
        return this;
    }

    public ReadOptionsBuilder setMissingFieldHandler(MissingFieldHandler missingFieldHandler) {
        readOptions.missingFieldHandler = missingFieldHandler;
        return this;
    }

    public ReadOptionsBuilder failOnUnknownType() {
        readOptions.isFailOnUnknownType = true;
        return this;
    }

    public ReadOptionsBuilder withClassLoader(ClassLoader classLoader) {
        readOptions.classLoader = classLoader;
        return this;
    }

    public ReadOptionsBuilder returnAsMaps() {
        readOptions.reconstructionType = ReconstructionType.MAPS;
        return this;
    }

    public ReadOptionsBuilder withCoercedType(Class<?> oldType, Class<?> newType) {
        return withCoercedType(oldType.getName(), newType);
    }

    public ReadOptionsBuilder withCoercedType(String fullyQualifedNameOldType, Class<?> newType) {
        this.readOptions.coercedTypes.put(fullyQualifedNameOldType, newType);
        return this;
    }

    public ReadOptionsBuilder withCustomTypeName(Class<?> type, String newTypeName) {
        return withCustomTypeName(type.getName(), newTypeName);
    }

    public ReadOptionsBuilder withCustomTypeName(String type, String newTypeName) {
        this.typeNameMap.put(type, newTypeName);
        return this;
    }

    public ReadOptionsBuilder withCustomTypeNames(Map<String, String> map) {
        this.typeNameMap.putAll(map);
        return this;
    }

    /**
     * Call this method to add a custom JSON reader to json-io.  It will
     * associate the Class 'c' to the reader you pass in.  The readers are
     * found with isAssignableFrom().  If this is too broad, causing too
     * many classes to be associated to the custom reader, you can indicate
     * that json-io should not use a custom reader for a particular class,
     * by calling the addNotCustomReader() method.
     *
     * @param c      Class to assign a custom JSON reader to
     * @param reader The JsonClassReader which will read the custom JSON format of 'c'
     */
    public ReadOptionsBuilder withCustomReader(Class<?> c, JsonReader.JsonClassReader reader) {
        readOptions.readers.put(c, reader);
        return this;
    }

    /**
     * This call will add all entries from the current map into the readers map.
     *
     * @param map This map contains classes mapped to their respctive readers
     */
    public ReadOptionsBuilder withCustomReaders(Map<? extends Class<?>, ? extends JsonReader.JsonClassReader> map) {
        readOptions.readers.putAll(map);
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClass(Class<?> c) {
        readOptions.getNonCustomizableClasses().add(c);
        return this;
    }

    public ReadOptionsBuilder withMaxDepth(int maxDepth) {
        readOptions.maxDepth = maxDepth;
        return this;
    }

    public ReadOptionsBuilder withNonCustomizableClasses(Collection<Class<?>> collection) {
        readOptions.getNonCustomizableClasses().addAll(collection);
        return this;
    }

    public ReadOptionsBuilder withClassFactory(Class<?> type, JsonReader.ClassFactory factory) {
        readOptions.classFactoryMap.put(type, factory);
        return this;
    }

    public ReadOptionsBuilder withClassFactories(Map<Class<?>, ? extends JsonReader.ClassFactory> factories) {
        readOptions.classFactoryMap.putAll(factories);
        return this;
    }

    @SuppressWarnings("unchecked")
    public static ReadOptionsBuilder fromMap(Map<String, Object> args) {
        ReadOptionsBuilder builder = new ReadOptionsBuilder();

        ClassLoader classLoader = args.get(CLASSLOADER) == null ?
                ReadOptionsBuilder.class.getClassLoader() :
                (ClassLoader) args.get(CLASSLOADER);

        builder.withClassLoader(classLoader);

        Map<String, String> typeNames = (Map<String, String>) args.get(TYPE_NAME_MAP);

        if (typeNames != null) {
            builder.withCustomTypeNames(typeNames);
        }

        MissingFieldHandler handler = (MissingFieldHandler) args.get(MISSING_FIELD_HANDLER);

        if (handler != null) {
            builder.setMissingFieldHandler(handler);
        }

        Map<Class<?>, JsonReader.JsonClassReader> customReaders = (Map) args.get(CUSTOM_READER_MAP);

        if (customReaders != null) {
            builder.withCustomReaders(customReaders);
        }

        boolean useMaps = ArgumentHelper.isTrue(args.get(USE_MAPS));
        if (useMaps) {
            builder.returnAsMaps();
        }

        boolean isFailOnUnknownType = ArgumentHelper.isTrue(args.get(FAIL_ON_UNKNOWN_TYPE));
        if (isFailOnUnknownType) {
            builder.failOnUnknownType();
        }

        Collection<Class<?>> nonCustomizableClasses = (Collection<Class<?>>) args.get(NOT_CUSTOM_READER_MAP);

        if (nonCustomizableClasses != null) {
            builder.withNonCustomizableClasses(nonCustomizableClasses);
        }

        Object unknownObjectClass = args.get(UNKNOWN_OBJECT);

        // backword compatible support - map arguments allow both string or class as unknown clas provider.
        if (unknownObjectClass instanceof String) {
            try {
                builder.setUnknownTypeClass(MetaUtils.classForName((String) unknownObjectClass, classLoader));
            } catch (Exception e) {
                // if it fails coming in from map then we
            }
        } else if (unknownObjectClass instanceof Class) {
            builder.setUnknownTypeClass((Class<?>) unknownObjectClass);
        }

        // pull all factories out and add them to the already added
        // global class factories.  The global class factories are
        // replaced by and ith the same name in this object.
        Map<String, JsonReader.ClassFactory> factories = (Map) args.get(FACTORIES);
        if (factories != null) {
            factories.entrySet()
                    .stream()
                    .forEach(entry -> builder.withClassFactory(MetaUtils.classForName(entry.getKey(), classLoader), entry.getValue()));
        }

        return builder;
    }

    public ReadOptions build() {
        // reverse type name mapping since we're on the read side.
        this.readOptions.typeNameMap = this.typeNameMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        return new ReadOptionsImplementation(this.readOptions);
    }

    private static class ReadOptionsImplementation implements ReadOptions {

        private static final int DEFAULT_MAX_PARSE_DEPTH = 1000;


        ReconstructionType reconstructionType;

        @Getter
        private boolean isFailOnUnknownType;

        @Getter
        private int maxDepth;

        @Getter
        private ClassLoader classLoader;

        @Getter
        private JsonReader.MissingFieldHandler missingFieldHandler;

        @Getter
        private Class<?> unknownTypeClass;

        @Getter
        private final Collection<Class<?>> nonCustomizableClasses;

        private final Map<Class<?>, JsonReader.JsonClassReader> readers;

        private final Map<Class, JsonReader.ClassFactory> classFactoryMap;

        private final Map<String, Object> customArguments;

        private final Map<String, Class<?>> coercedTypes;

        private Map<String, String> typeNameMap;

        private JsonReader.ClassFactory throwableFactory = new ThrowableFactory();
        
        private JsonReader.ClassFactory enumFactory = new EnumClassFactory();

        private ReadOptionsImplementation() {
            this.reconstructionType = ReconstructionType.JAVA_OBJECTS;
            this.maxDepth = DEFAULT_MAX_PARSE_DEPTH;
            this.classLoader = ReadOptions.class.getClassLoader();
            this.typeNameMap = new HashMap<>();
            this.readers = new HashMap<>(BASE_READERS);
            this.nonCustomizableClasses = new LinkedHashSet<>();
            this.classFactoryMap = new HashMap<>(BASE_CLASS_FACTORIES);
            this.customArguments = new HashMap<>();
            this.coercedTypes = new LinkedHashMap<>(BASE_COERCED_TYPES);
            this.unknownTypeClass = null;
        }

        private ReadOptionsImplementation(ReadOptionsImplementation options) {
            this.reconstructionType = options.reconstructionType;
            this.maxDepth = options.maxDepth;
            this.classLoader = options.classLoader;
            this.typeNameMap = Collections.unmodifiableMap(options.typeNameMap);
            this.classFactoryMap = Collections.unmodifiableMap(options.classFactoryMap);
            this.customArguments = Collections.unmodifiableMap(options.customArguments);
            this.coercedTypes = Collections.unmodifiableMap(options.coercedTypes);
            this.isFailOnUnknownType = options.isFailOnUnknownType;
            this.unknownTypeClass = options.unknownTypeClass;
            this.missingFieldHandler = options.missingFieldHandler;

            // Note: These 2 members cannot become unmodifiable until we fully deprecate JsonReader.addReader() and JsonReader.addNotCustomReader()
            this.readers = options.readers;
            this.nonCustomizableClasses = options.nonCustomizableClasses;
        }

        @Override
        public boolean isUsingMaps() {
            return reconstructionType == ReconstructionType.MAPS;
        }

        @Override
        public JsonReader.JsonClassReader getReader(Class<?> c) {
            return this.readers.get(c);
        }

        @Override
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

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCustomArgument(String name) {
            return (T) this.customArguments.get(name);
        }

        @Override
        public Class<?> getCoercedType(String fqName) {
            return this.coercedTypes.get(fqName);
        }

        @Override
        public boolean isNonCustomizable(Class<?> c) {
            return this.nonCustomizableClasses.contains(c);
        }

        /**
         * We're cheating here because the booleans are needed to be mutable by the builder.
         *
         * @return
         */
        @Override
        public ReadOptions ensureUsingMaps() {
            this.reconstructionType = ReconstructionType.MAPS;
            return this;
        }

        /**
         * We're cheating here because the booleans are needed to be mutable by the builder.
         * @return
         */
        @Override
        public ReadOptions ensureUsingObjects() {
            this.reconstructionType = ReconstructionType.JAVA_OBJECTS;
            return this;
        }

        @Override
        public void addReader(Class<?> c, JsonReader.JsonClassReader reader) {
            this.readers.put(c, reader);
        }

        @Override
        public void addNonCustomizableClass(Class<?> c) {
            this.nonCustomizableClasses.add(c);
        }

        @Override
        public Optional<JsonReader.JsonClassReader> getClosestReader(Class<?> c) {
            Optional<JsonReader.JsonClassReader> closestReader = Optional.empty();
            int minDistance = Integer.MAX_VALUE;

            for (Map.Entry<Class<?>, JsonReader.JsonClassReader> entry : this.readers.entrySet()) {
                Class<?> key = entry.getKey();

                if (key == c) {
                    return Optional.of(entry.getValue());
                }

                int distance = MetaUtils.computeInheritanceDistance(c, key);

                if (distance != -1 && distance < minDistance) {
                    minDistance = distance;
                    closestReader = Optional.of(entry.getValue());
                }
            }

            return closestReader;
        }

        @Override
        public String getTypeName(String name) {
            return this.typeNameMap.get(name);
        }

        @Override
        public Map toMap() {
            Map args = new HashMap();
            args.put(CLASSLOADER, this.classLoader);
            args.put(TYPE_NAME_MAP_REVERSE, typeNameMap);
            args.put(MISSING_FIELD_HANDLER, missingFieldHandler);
            args.put(CUSTOM_READER_MAP, readers);
            args.put(NOT_CUSTOM_READER_MAP, nonCustomizableClasses);
            args.put(USE_MAPS, reconstructionType == ReconstructionType.MAPS);
            args.put(UNKNOWN_OBJECT, unknownTypeClass);
            args.put(FAIL_ON_UNKNOWN_TYPE, isFailOnUnknownType);
            args.put(FACTORIES, classFactoryMap);

            return args;
        }
    }
}
