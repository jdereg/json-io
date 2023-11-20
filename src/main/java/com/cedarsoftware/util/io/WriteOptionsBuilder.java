package com.cedarsoftware.util.io;

import static com.cedarsoftware.util.io.ArgumentHelper.isTrue;
import static com.cedarsoftware.util.io.JsonWriter.CLASSLOADER;
import static com.cedarsoftware.util.io.JsonWriter.CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.ENUM_PUBLIC_ONLY;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_NAME_BLACK_LIST;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_SPECIFIERS;
import static com.cedarsoftware.util.io.JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS;
import static com.cedarsoftware.util.io.JsonWriter.NOT_CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.PRETTY_PRINT;
import static com.cedarsoftware.util.io.JsonWriter.SHORT_META_KEYS;
import static com.cedarsoftware.util.io.JsonWriter.SKIP_NULL_FIELDS;
import static com.cedarsoftware.util.io.JsonWriter.TYPE;
import static com.cedarsoftware.util.io.JsonWriter.TYPE_NAME_MAP;
import static com.cedarsoftware.util.io.JsonWriter.WRITE_LONGS_AS_STRINGS;
import static com.cedarsoftware.util.io.JsonWriter.nullWriter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.util.PrintStyle;
import com.cedarsoftware.util.reflect.Accessor;

import lombok.Getter;

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
 *         limitations under the License.
 */
public class WriteOptionsBuilder {

    private static final Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS;

    private final WriteOptionsImplementation writeOptions;

    private final Map<Class<?>, Collection<String>> excludedFields = new HashMap<>();

    private final Map<Class<?>, Collection<String>> includedFields = new HashMap<>();


    private boolean usingShortMetaKeys = false;


    private boolean writingLongsAsStrings = false;


    private boolean skippingNullFields = false;

    private boolean forcingMapFormatWithKeyArrays = false;


    private boolean enumPublicOnly = false;


    private ClassLoader classLoader = WriteOptionsImplementation.class.getClassLoader();


    private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();

    private PrintStyle printStyle;

    private TypeWriter typeWriter;

    static {

        Map<Class<?>, JsonWriter.JsonClassWriter> temp = new HashMap<>();
        temp.put(String.class, new Writers.JsonStringWriter());
        temp.put(BigInteger.class, new Writers.BigIntegerWriter());
        temp.put(BigDecimal.class, new Writers.BigDecimalWriter());
        temp.put(Timestamp.class, new Writers.TimestampWriter());
        temp.put(Calendar.class, new Writers.CalendarWriter());
        temp.put(TimeZone.class, new Writers.TimeZoneWriter());
        temp.put(Locale.class, new Writers.LocaleWriter());
        temp.put(Class.class, new Writers.ClassWriter());
        temp.put(UUID.class, new Writers.UUIDWriter());
        temp.put(LocalDate.class, new Writers.LocalDateWriter());
        temp.put(LocalTime.class, new Writers.LocalTimeWriter());
        temp.put(LocalDateTime.class, new Writers.LocalDateTimeWriter());
        temp.put(ZonedDateTime.class, new Writers.ZonedDateTimeWriter());
        temp.put(OffsetDateTime.class, new Writers.OffsetDateTimeWriter());
        temp.put(YearMonth.class, new Writers.YearMonthWriter());
        temp.put(Year.class, new Writers.YearWriter());
        temp.put(ZoneOffset.class, new Writers.ZoneOffsetWriter());

        JsonWriter.JsonClassWriter dateWriter = new Writers.DateAsTimestampWriter();
        temp.put(Date.class, dateWriter);
        temp.put(java.sql.Date.class, dateWriter);

        JsonWriter.JsonClassWriter stringWriter = new Writers.PrimitiveUtf8StringWriter();
        temp.put(StringBuilder.class, stringWriter);
        temp.put(StringBuffer.class, stringWriter);
        temp.put(URL.class, stringWriter);
        temp.put(ZoneOffset.class, stringWriter);

        JsonWriter.JsonClassWriter primitiveValueWriter = new Writers.PrimitiveValueWriter();
        temp.put(AtomicBoolean.class, primitiveValueWriter);
        temp.put(AtomicInteger.class, primitiveValueWriter);
        temp.put(AtomicLong.class, primitiveValueWriter);

        Class<?> zoneInfoClass = MetaUtils.classForName("sun.util.calendar.ZoneInfo", WriteOptions.class.getClassLoader());
        if (zoneInfoClass != null)
        {
            temp.put(zoneInfoClass, new Writers.TimeZoneWriter());
        }

        BASE_WRITERS = temp;
    }
    
    public WriteOptionsBuilder() {
        this.writeOptions = new WriteOptionsImplementation();
    }

    public WriteOptionsBuilder withDefaultOptimizations() {
        return withIsoDateTimeFormat()
                .withShortMetaKeys()
                .skipNullFields();
    }

    public WriteOptionsBuilder skipNullFields() {
        this.skippingNullFields = true;
        return this;
    }

    public WriteOptionsBuilder withPrettyPrint() {
        this.printStyle = PrintStyle.PRETTY_PRINT;
        return this;
    }

    public WriteOptionsBuilder withPrintStyle(PrintStyle style) {
        this.printStyle = style;
        return this;
    }

    public WriteOptionsBuilder writeLongsAsStrings() {
        this.writingLongsAsStrings = true;
        return this;
    }

    /**
     * This option only applies when you're writing enums as objects so we
     * force the enum write to be ENUMS_AS_OBJECTS
     *
     * @return
     */
    public WriteOptionsBuilder writeEnumsAsObject() {
        this.enumWriter = nullWriter;
        return this;
    }

    public WriteOptionsBuilder doNotWritePrivateEnumFields() {
        this.enumPublicOnly = true;
        this.enumWriter = nullWriter;
        return this;
    }

    public WriteOptionsBuilder writeEnumsAsPrimitives() {
        this.enumPublicOnly = false;
        this.enumWriter = new Writers.EnumsAsStringWriter();
        return this;
    }

    // Map with all String keys, will still output in the @keys/@items approach
    public WriteOptionsBuilder forceMapOutputAsKeysAndValues() {
        this.forcingMapFormatWithKeyArrays = true;
        return this;
    }

    // Map with all String keys, will output as a JSON object, with the keys of the Map being the keys of the JSON
    // Object, which is the default and more natural.
    public WriteOptionsBuilder doNotForceMapOutputAsKeysAndValues() {
        this.forcingMapFormatWithKeyArrays = false;
        return this;
    }

    public WriteOptionsBuilder withClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public static void addBaseWriter(Class<?> c, JsonWriter.JsonClassWriter writer) {
        BASE_WRITERS.put(c, writer);
    }

    public WriteOptionsBuilder withLogicalPrimitive(Class<?> c) {
        this.writeOptions.logicalPrimitives.add(c);
        return this;
    }

    public WriteOptionsBuilder withLogicalPrimitives(Collection<Class<?>> collection) {
        this.writeOptions.logicalPrimitives.addAll(collection);
        return this;
    }

    public WriteOptionsBuilder writeLocalDateAsTimeStamp() {
        return withCustomWriter(LocalDate.class, new Writers.LocalDateAsTimestamp());
    }

    public WriteOptionsBuilder writeLocalDateWithFormat(DateTimeFormatter formatter) {
        return withCustomWriter(LocalDate.class, new Writers.LocalDateWriter(formatter));
    }

    public WriteOptionsBuilder withIsoDateTimeFormat() {
        return withDateFormat(JsonWriter.ISO_DATE_TIME_FORMAT);
    }

    public WriteOptionsBuilder withIsoDateFormat() {
        return this.withDateFormat(JsonWriter.ISO_DATE_FORMAT);
    }

    public WriteOptionsBuilder withDateFormat(String format) {
        return this.withCustomWriter(Date.class, new Writers.DateWriter(format));
    }

    public WriteOptionsBuilder withShortMetaKeys() {
        this.usingShortMetaKeys = true;
        return this;
    }

    public WriteOptionsBuilder neverShowTypeInfo() {
        this.typeWriter = TypeWriter.NEVER;
        return this;
    }

    public WriteOptionsBuilder alwaysShowTypeInfo() {
        this.typeWriter = TypeWriter.ALWAYS;
        return this;
    }

    public WriteOptionsBuilder showMinimalTypeInfo() {
        this.typeWriter = TypeWriter.MINIMAL;
        return this;
    }

    public WriteOptionsBuilder excludedFields(Class<?> c, Collection<String> fields) {
        Collection<String> collection = this.excludedFields.computeIfAbsent(c, f -> new LinkedHashSet<>());
        collection.addAll(fields);
        return this;
    }

    public WriteOptionsBuilder excludedFields(Map<Class<?>, Collection<String>> map) {
        for (Map.Entry<Class<?>, Collection<String>> entry : map.entrySet()) {
            Collection<String> collection = this.excludedFields.computeIfAbsent(entry.getKey(), f -> new LinkedHashSet<>());
            collection.addAll(entry.getValue());
        }
        return this;
    }

    public WriteOptionsBuilder includedFields(Class<?> c, List<String> fields) {
        Collection<String> collection = this.includedFields.computeIfAbsent(c, f -> new LinkedHashSet<>());
        collection.addAll(fields);
        return this;
    }

    public WriteOptionsBuilder includedFields(Map<Class<?>, Collection<String>> map) {
        for (Map.Entry<Class<?>, Collection<String>> entry : map.entrySet()) {
            Collection<String> collection = this.includedFields.computeIfAbsent(entry.getKey(), f -> new LinkedHashSet<>());
            collection.addAll(entry.getValue());
        }
        return this;
    }

    public WriteOptionsBuilder withCustomTypeName(Class<?> type, String newTypeName) {
        return withCustomTypeName(type.getName(), newTypeName);
    }

    public WriteOptionsBuilder withCustomTypeName(String type, String newTypeName) {
        assertTypesAreBeingOutput();
        this.writeOptions.customTypeMap.put(type, newTypeName);
        return this;
    }

    public WriteOptionsBuilder withCustomTypeNames(Map<String, String> map) {
        assertTypesAreBeingOutput();
        this.writeOptions.customTypeMap.putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomWriter(Class<?> c, JsonWriter.JsonClassWriter writer) {
        this.writeOptions.customWriters.put(c, writer);
        return this;
    }

    public WriteOptionsBuilder withCustomWriters(Map<? extends Class<?>, ? extends JsonWriter.JsonClassWriter> map) {
        this.writeOptions.customWriters.putAll(map);
        return this;
    }

    public WriteOptionsBuilder withNoCustomizationFor(Class<?> c) {
        this.writeOptions.nonCustomClasses.add(c);
        return this;
    }

    public WriteOptionsBuilder withNoCustomizationsFor(Collection<Class<?>> collection) {
        this.writeOptions.nonCustomClasses.addAll(collection);
        return this;
    }

    public static WriteOptionsBuilder fromMap(Map args) {
        WriteOptionsBuilder builder = new WriteOptionsBuilder();

        if (isTrue(args.get(SHORT_META_KEYS))) {
            builder.withShortMetaKeys();
        }

        Object type = args.get(TYPE);
        if (isTrue(type)) {
            builder.alwaysShowTypeInfo();
        }

        if (Boolean.FALSE.equals(type) || "false".equals(args.get(TYPE))) {
            builder.neverShowTypeInfo();
        }

        Map<String, String> typeNameMap = (Map<String, String>) args.get(TYPE_NAME_MAP);

        if (typeNameMap != null) {
            builder.withCustomTypeNames(typeNameMap);
        }

        if (isTrue(args.get(PRETTY_PRINT))) {
            builder.withPrintStyle(PrintStyle.PRETTY_PRINT);
        }

        if (isTrue(args.get(WRITE_LONGS_AS_STRINGS))) {
            builder.writeLongsAsStrings();
        }

        if (isTrue(args.get(SKIP_NULL_FIELDS))) {
            builder.skipNullFields();
        }

        // eventually let's get rid of this member variable and just use the one being passed into the writer object.
        boolean isEnumPublicOnly = isTrue(args.get(ENUM_PUBLIC_ONLY));

        if (isEnumPublicOnly) {
            builder.doNotWritePrivateEnumFields();
        }

        if (isTrue(args.get(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS))) {
            builder.forceMapOutputAsKeysAndValues();
        }

        ClassLoader loader = (ClassLoader) args.get(CLASSLOADER);
        builder.withClassLoader(loader == null ? JsonWriter.class.getClassLoader() : loader);

        Map<Class<?>, JsonWriter.JsonClassWriter> customWriters = (Map<Class<?>, JsonWriter.JsonClassWriter>) args.get(CUSTOM_WRITER_MAP);
        if (customWriters != null) {
            builder.withCustomWriters(customWriters);
        }

        Collection<Class<?>> notCustomClasses = (Collection<Class<?>>) args.get(NOT_CUSTOM_WRITER_MAP);
        if (notCustomClasses != null) {
            builder.withNoCustomizationsFor(notCustomClasses);
        }

        // Convert String field names to Java Field instances (makes it easier for user to set this up)
        Map<Class<?>, Collection<String>> stringSpecifiers = (Map<Class<?>, Collection<String>>) args.get(FIELD_SPECIFIERS);

        if (stringSpecifiers != null) {
            builder.includedFields(stringSpecifiers);
        }

        // may have to convert these to juse per class level, but that may be difficult.
        // since the user thinks of all the fields on a class at the class + parents level
        Map<Class<?>, Collection<String>> stringBlackList = (Map<Class<?>, Collection<String>>) args.get(FIELD_NAME_BLACK_LIST);

        if (stringBlackList != null) {
            builder.excludedFields(stringBlackList);
        }

        return builder;
    }

    public WriteOptions build() {
        this.writeOptions.includedFields.putAll(MetaUtils.convertStringFieldNamesToAccessors(this.includedFields));
        this.writeOptions.excludedFields.putAll(MetaUtils.convertStringFieldNamesToAccessors(this.excludedFields));
        return new WriteOptionsImplementation(this.writeOptions,
                typeWriter,
                printStyle,
                usingShortMetaKeys,
                writingLongsAsStrings,
                skippingNullFields,
                forcingMapFormatWithKeyArrays,
                enumPublicOnly,
                enumWriter,
                classLoader);
    }

    private void assertTypesAreBeingOutput() {
        if (typeWriter == TypeWriter.NEVER) {
            throw new IllegalStateException("There is no need to set the type name map when types are never being written");
        }
    }

    private static class WriteOptionsImplementation implements WriteOptions {
        @Getter
        private final Set<Class<?>> logicalPrimitives;

        @Getter
        private final boolean usingShortMetaKeys;

        @Getter
        private final boolean writingLongsAsStrings;

        @Getter
        private final boolean skippingNullFields;

        @Getter
        private final boolean forcingMapFormatWithKeyArrays;

        @Getter
        private final boolean enumPublicOnly;

        @Getter
        private final ClassLoader classLoader;

        @Getter
        private final JsonWriter.JsonClassWriter enumWriter;

        private final Map<Class<?>, JsonWriter.JsonClassWriter> customWriters;

        private final Map<String, String> customTypeMap;

        private final Collection<Class<?>> nonCustomClasses;

        private final Map<Class<?>, Collection<Accessor>> includedFields;

        private final Map<Class<?>, Collection<Accessor>> excludedFields;

        /**
         * classes where the implementation type will be different from the field declaration type, so it would always
         * force the type if not for our custom writer.  These will typically be sun style classes where there is
         * a static initializer
         */
        private final Map<Class<?>, JsonWriter.JsonClassWriter> writerCache = new HashMap<>();

        private final PrintStyle printStyle;

        private final TypeWriter typeWriter;

        private WriteOptionsImplementation() {
            this.usingShortMetaKeys = true;
            this.writingLongsAsStrings = false;
            this.skippingNullFields = false;
            this.forcingMapFormatWithKeyArrays = false;
            this.enumPublicOnly = false;
            this.logicalPrimitives = new HashSet<>(Primitives.PRIMITIVE_WRAPPERS);
            this.customWriters = new HashMap<>(BASE_WRITERS);
            this.excludedFields = new HashMap<>();
            this.includedFields = new HashMap<>();
            this.customTypeMap = new HashMap<>();
            this.nonCustomClasses = new HashSet<>();
            this.printStyle = PrintStyle.ONE_LINE;
            this.typeWriter = TypeWriter.MINIMAL;
            this.enumWriter = new Writers.EnumsAsStringWriter();
            this.classLoader = WriteOptionsImplementation.class.getClassLoader();
        }

        private WriteOptionsImplementation(WriteOptionsImplementation options,
                                           TypeWriter typeWriter,
                                           PrintStyle printStyle,
                                           boolean usingShortMetaKeys,
                                           boolean writingLongsAsStrings,
                                           boolean skippingNullFields,
                                           boolean forcingMapFormatWithKeyArrays,
                                           boolean enumPublicOnly,
                                           JsonWriter.JsonClassWriter enumWriter,
                                           ClassLoader classLoader) {
            this.usingShortMetaKeys = usingShortMetaKeys;
            this.typeWriter = typeWriter;
            this.printStyle = printStyle;
            this.writingLongsAsStrings = writingLongsAsStrings;
            this.skippingNullFields = skippingNullFields;
            this.forcingMapFormatWithKeyArrays = forcingMapFormatWithKeyArrays;
            this.enumPublicOnly = enumPublicOnly;
            this.classLoader = classLoader;
            this.enumWriter = enumWriter;
            this.customWriters = Collections.unmodifiableMap(options.customWriters);
            this.customTypeMap = Collections.unmodifiableMap(options.customTypeMap);
            this.nonCustomClasses = Collections.unmodifiableCollection(options.nonCustomClasses);
            this.includedFields = Collections.unmodifiableMap(options.includedFields);
            this.excludedFields = Collections.unmodifiableMap(options.excludedFields);
            this.logicalPrimitives = Collections.unmodifiableSet(options.logicalPrimitives);
        }

        @Override
        public String getCustomNameOrDefault(String name, String def) {
            String customName = this.customTypeMap.get(name);
            return customName == null ? def : customName;
        }

        /**
         * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
         * null value.  Instead, singleton instance of this class is placed where null values
         * are needed.
         */
        static final class NullClass implements JsonWriter.JsonClassWriter {
        }

        /**
         * Fetch the customer writer for the passed in Class.  If it is cached (already associated to the
         * passed in Class), return the same instance, otherwise, make a call to get the custom writer
         * and store that result.
         *
         * @param c Class of object for which fetch a custom writer
         * @return JsonClassWriter for the custom class (if one exists), null otherwise.
         */
        @Override
        public JsonWriter.JsonClassWriter getCustomWriter(Class<?> c) {
            JsonWriter.JsonClassWriter writer = writerCache.get(c);
            if (writer == null) {
                writer = forceGetCustomWriter(c);
                writerCache.put(c, writer);
            }

            if (writer != nullWriter) {
                return writer;
            }

            writer = MetaUtils.getClassIfEnum(c).isPresent() ? this.enumWriter : nullWriter;
            writerCache.put(c, writer);

            return writer == nullWriter ? null : writer;
        }

        @Override
        public void clearWriterCache() {
            this.writerCache.clear();
        }

        /**
         * Fetch the customer writer for the passed in Class.  This method always fetches the custom writer, doing
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

            for (Map.Entry<Class<?>, JsonWriter.JsonClassWriter> entry : this.customWriters.entrySet()) {
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

        public boolean isNonCustomClass(Class<?> c) {
            return this.nonCustomClasses.contains(c);
        }

        @Override
        public boolean isAlwaysShowingType() {
            return typeWriter == TypeWriter.ALWAYS;
        }

        @Override
        public boolean isNeverShowingType() {
            return typeWriter == TypeWriter.NEVER;
        }

        @Override
        public boolean isPrettyPrint() {
            return this.printStyle == PrintStyle.PRETTY_PRINT;
        }

        @Override
        public boolean isLogicalPrimitive(Class<?> c) {
            return Primitives.isLogicalPrimitive(c, logicalPrimitives);
        }

        public Collection<Accessor> getIncludedAccessors(final Class<?> c) {
            return this.getAccessorsUsingSpecifiers(c, this.includedFields);
        }

        public Collection<Accessor> getExcludedAccessors(final Class<?> c) {
            return this.getAccessorsUsingSpecifiers(c, this.excludedFields);
        }

        // This is replacing the reverse walking system that compared all cases for distance
        // since we're caching all classes and their sub-objects correctly we should be ok removing
        // the distance check since we walk up the chain of the class being written.
        public Collection<Accessor> getAccessorsUsingSpecifiers(final Class<?> c, Map<Class<?>, Collection<Accessor>> limiter) {
            Class<?> curr = c;
            List<Collection<Accessor>> accessorLists = new ArrayList<>();

            while (curr != null) {
                Collection<Accessor> accessorList = limiter.get(curr);

                if (accessorList != null) {
                    accessorLists.add(accessorList);
                }

                curr = curr.getSuperclass();
            }

            if (accessorLists.isEmpty()) {
                return null;
            }

            Collection<Accessor> accessors = new ArrayList<>();
            accessorLists.forEach(accessors::addAll);
            return accessors;
        }
        /**
         * We're cheating here because the booleans are needed to be mutable by the builder.
         *
         * @return
         */
        public WriteOptions ensurePrettyPrint() {
            return new WriteOptionsImplementation(this,
                    this.typeWriter,
                    PrintStyle.PRETTY_PRINT,
                    this.usingShortMetaKeys,
                    this.writingLongsAsStrings,
                    this.skippingNullFields,
                    this.forcingMapFormatWithKeyArrays,
                    this.enumPublicOnly,
                    this.enumWriter,
                    this.classLoader);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public Map toMap() {
            Map args = new HashMap();

            if (this.isWritingLongsAsStrings()) {
                args.put(WRITE_LONGS_AS_STRINGS, Boolean.TRUE);
            }

            if (this.isSkippingNullFields()) {
                args.put(SKIP_NULL_FIELDS, Boolean.TRUE);
            }

            if (this.isForcingMapFormatWithKeyArrays()) {
                args.put(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, Boolean.TRUE);
            }

            if (this.isEnumPublicOnly()) {
                args.put(ENUM_PUBLIC_ONLY, Boolean.TRUE);
            }

            if (this.isNeverShowingType()) {
                args.put(TYPE, Boolean.FALSE);
            } else if (this.isAlwaysShowingType()) {
                args.put(TYPE, Boolean.TRUE);
            }

            if (this.isPrettyPrint()) {
                args.put(PRETTY_PRINT, Boolean.TRUE);
            }

            if (this.isUsingShortMetaKeys()) {
                args.put(SHORT_META_KEYS, Boolean.TRUE);
            }

            args.put(NOT_CUSTOM_WRITER_MAP, new HashSet<>(this.nonCustomClasses));

            args.put(CUSTOM_WRITER_MAP, new HashMap<>(this.customWriters));

            args.put(CLASSLOADER, this.getClassLoader());
            return args;
        }
    }

}
