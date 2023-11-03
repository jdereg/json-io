package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class WriteOptionsBuilder {

    private static Map<Class<?>, JsonWriter.JsonClassWriter> BASE_WRITERS;

    private final WriteOptionsImplementation writeOptions;

    private final Map<Class<?>, Collection<String>> fieldNameBlackList = new HashMap<>();

    private final Map<Class<?>, Collection<String>> fieldSpecifiers = new HashMap<>();


    static {
        Map<Class<?>, JsonWriter.JsonClassWriter> temp = new HashMap<>();
        temp.put(String.class, new Writers.JsonStringWriter());
        temp.put(Date.class, new Writers.DateWriter());
        temp.put(AtomicBoolean.class, new Writers.AtomicBooleanWriter());
        temp.put(AtomicInteger.class, new Writers.AtomicIntegerWriter());
        temp.put(AtomicLong.class, new Writers.AtomicLongWriter());
        temp.put(BigInteger.class, new Writers.BigIntegerWriter());
        temp.put(BigDecimal.class, new Writers.BigDecimalWriter());
        temp.put(java.sql.Date.class, new Writers.DateWriter());
        temp.put(Timestamp.class, new Writers.TimestampWriter());
        temp.put(Calendar.class, new Writers.CalendarWriter());
        temp.put(TimeZone.class, new Writers.TimeZoneWriter());
        temp.put(Locale.class, new Writers.LocaleWriter());
        temp.put(Class.class, new Writers.ClassWriter());
        temp.put(StringBuilder.class, new Writers.StringBuilderWriter());
        temp.put(StringBuffer.class, new Writers.StringBufferWriter());
        temp.put(UUID.class, new Writers.UUIDWriter());
        temp.put(URL.class, new Writers.URLWriter());
        temp.put(LocalDate.class, new Writers.LocalDateWriter());
        temp.put(LocalTime.class, new Writers.LocalTimeWriter());
        temp.put(LocalDateTime.class, new Writers.LocalDateTimeWriter());
        temp.put(ZonedDateTime.class, new Writers.ZonedDateTimeWriter());
        temp.put(Throwable.class, new Writers.ThrowableWriter());

        try {
            Class<?> zoneInfoClass = Class.forName("sun.util.calendar.ZoneInfo");
            temp.put(zoneInfoClass, new Writers.TimeZoneWriter());
        } catch (ClassNotFoundException ignore) {
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
        writeOptions.skippingNullFields = true;
        return this;
    }

    public WriteOptionsBuilder withPrettyPrint() {
        writeOptions.isPrettyPrint = true;
        return this;
    }

    public WriteOptionsBuilder writeLongsAsStrings() {
        writeOptions.writingLongsAsStrings = true;
        return this;
    }

    /**
     * This option only applies when you're writing enums as objects so we
     * force the enum write to be ENUMS_AS_OBJECTS
     *
     * @return
     */
    public WriteOptionsBuilder writeEnumsAsObject() {
        writeOptions.enumWriter = new Writers.EnumAsObjectWriter();
        return this;
    }

    public WriteOptionsBuilder doNotWritePrivateEnumFields() {
        writeOptions.enumPublicOnly = true;
        writeOptions.enumWriter = new Writers.EnumAsObjectWriter();
        return this;
    }

    public WriteOptionsBuilder writeEnumsAsPrimitives() {
        writeOptions.enumPublicOnly = false;
        writeOptions.enumWriter = new Writers.EnumsAsStringWriter();
        return this;
    }

    // Map with all String keys, will still output in the @keys/@items approach
    public WriteOptionsBuilder forceMapOutputAsKeysAndItems() {
        writeOptions.forcingMapFormatWithKeyArrays = true;
        return this;
    }

    // Map with all String keys, will output as a JSON object, with the keys of the Map being the keys of the JSON
    // Object, which is the default and more natural.
    public WriteOptionsBuilder doNotForceMapOutputAsKeysAndItems() {
        writeOptions.forcingMapFormatWithKeyArrays = false;
        return this;
    }

    public WriteOptionsBuilder withClassLoader(ClassLoader classLoader) {
        writeOptions.classLoader = classLoader;
        return this;
    }

    public static void addBaseWriter(Class<?> c, JsonWriter.JsonClassWriter writer) {
        BASE_WRITERS.put(c, writer);
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
        return withDateFormat(JsonWriter.ISO_DATE_FORMAT);
    }

    public WriteOptionsBuilder withDateFormat(String format) {
        writeOptions.dateFormat = format;
        return this;
    }

    public WriteOptionsBuilder withShortMetaKeys() {
        this.writeOptions.usingShortMetaKeys = true;
        return this;
    }

    public WriteOptionsBuilder neverShowTypeInfo() {
        this.writeOptions.neverShowingType = true;
        this.writeOptions.alwaysShowingType = false;
        return this;
    }

    public WriteOptionsBuilder alwaysShowTypeInfo() {
        this.writeOptions.alwaysShowingType = true;
        this.writeOptions.neverShowingType = false;
        return this;
    }

    public WriteOptionsBuilder showMinimalTypeInfo() {
        this.writeOptions.alwaysShowingType = false;
        this.writeOptions.neverShowingType = false;
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackList(Class<?> c, Collection<String> fields) {
        Collection<String> collection = this.fieldNameBlackList.computeIfAbsent(c, f -> new LinkedHashSet<>());
        collection.addAll(fields);
        return this;
    }

    public WriteOptionsBuilder withFieldNameBlackListMap(Map<Class<?>, Collection<String>> map) {
        for (Map.Entry<Class<?>, Collection<String>> entry : map.entrySet()) {
            Collection<String> collection = this.fieldNameBlackList.computeIfAbsent(entry.getKey(), f -> new LinkedHashSet<>());
            collection.addAll(entry.getValue());
        }
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifier(Class<?> c, List<String> fields) {
        Collection<String> collection = this.fieldSpecifiers.computeIfAbsent(c, f -> new LinkedHashSet<>());
        collection.addAll(fields);
        return this;
    }

    public WriteOptionsBuilder withFieldSpecifiersMap(Map<Class<?>, Collection<String>> map) {
        for (Map.Entry<Class<?>, Collection<String>> entry : map.entrySet()) {
            Collection<String> collection = this.fieldSpecifiers.computeIfAbsent(entry.getKey(), f -> new LinkedHashSet<>());
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

    public WriteOptionsBuilder withCustomTypeNameMap(Map<String, String> map) {
        assertTypesAreBeingOutput();
        this.writeOptions.customTypeMap.putAll(map);
        return this;
    }

    public WriteOptionsBuilder withCustomWriter(Class<?> c, JsonWriter.JsonClassWriter writer) {
        this.writeOptions.customWriters.put(c, writer);
        return this;
    }

    public WriteOptionsBuilder withCustomWriterMap(Map<? extends Class<?>, ? extends JsonWriter.JsonClassWriter> map) {
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

    public WriteOptionsBuilder withCustomArgument(String name, Object o) {
        this.writeOptions.customArguments.put(name, o);
        return this;
    }

    public WriteOptionsBuilder withCustomArguments(Map<String, Object> map) {
        this.writeOptions.customArguments.putAll(map);
        return this;
    }

    public static Map toMap(WriteOptions options) {
        Map args = new HashMap();

        if (options.isWritingLongsAsStrings()) {
            args.put(WRITE_LONGS_AS_STRINGS, Boolean.TRUE);
        }

        if (options.isSkippingNullFields()) {
            args.put(SKIP_NULL_FIELDS, Boolean.TRUE);
        }

        if (options.isForcingMapFormatWithKeyArrays()) {
            args.put(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, Boolean.TRUE);
        }

        if (options.isEnumPublicOnly()) {
            args.put(ENUM_PUBLIC_ONLY, Boolean.TRUE);
        }

        if (options.isNeverShowingType()) {
            args.put(TYPE, Boolean.FALSE);
        } else if (options.isAlwaysShowingType()) {
            args.put(TYPE, Boolean.TRUE);
        }

        if (options.isPrettyPrint()) {
            args.put(PRETTY_PRINT, Boolean.TRUE);
        }

        if (options.isUsingShortMetaKeys()) {
            args.put(SHORT_META_KEYS, Boolean.TRUE);
        }

        args.put(NOT_CUSTOM_WRITER_MAP, options.getNonCustomClasses());

        args.put(CUSTOM_WRITER_MAP, options.getCustomWriters());

        args.put(CLASSLOADER, options.getClassLoader());
        return args;
    }

    public static WriteOptions fromMap(Map args) {
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
            builder.withCustomTypeNameMap(typeNameMap);
        }

        if (isTrue(args.get(PRETTY_PRINT))) {
            builder.withPrettyPrint();
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
            builder.forceMapOutputAsKeysAndItems();
        }

        ClassLoader loader = (ClassLoader) args.get(CLASSLOADER);
        builder.withClassLoader(loader == null ? JsonWriter.class.getClassLoader() : loader);

        Map<Class<?>, JsonWriter.JsonClassWriter> customWriters = (Map<Class<?>, JsonWriter.JsonClassWriter>) args.get(CUSTOM_WRITER_MAP);
        if (customWriters != null) {
            builder.withCustomWriterMap(customWriters);
        }

        Collection<Class<?>> notCustomClasses = (Collection<Class<?>>) args.get(NOT_CUSTOM_WRITER_MAP);
        if (notCustomClasses != null) {
            builder.withNoCustomizationsFor(notCustomClasses);
        }

        // Convert String field names to Java Field instances (makes it easier for user to set this up)
        Map<Class<?>, Collection<String>> stringSpecifiers = (Map<Class<?>, Collection<String>>) args.get(FIELD_SPECIFIERS);

        if (stringSpecifiers != null) {
            builder.withFieldSpecifiersMap(stringSpecifiers);
        }

        // may have to convert these to juse per class level, but that may be difficult.
        // since the user thinks of all the fields on a class at the class + parents level
        Map<Class<?>, Collection<String>> stringBlackList = (Map<Class<?>, Collection<String>>) args.get(FIELD_NAME_BLACK_LIST);

        if (stringBlackList != null) {
            builder.withFieldNameBlackListMap(stringBlackList);
        }

        return builder.build();
    }

    public WriteOptions build() {

        this.writeOptions.fieldSpecifiers.putAll(MetaUtils.convertStringFieldNamesToAccessors(this.fieldSpecifiers));
        this.writeOptions.fieldNameBlackList.putAll(MetaUtils.convertStringFieldNamesToAccessors(this.fieldNameBlackList));
        return writeOptions;
    }

    private void assertTypesAreBeingOutput() {
        if (writeOptions.neverShowingType) {
            throw new IllegalStateException("There is no need to set the type name map when types are never being written");
        }
    }

    private class WriteOptionsImplementation implements WriteOptions {
        @Getter
        private boolean usingShortMetaKeys = false;

        @Getter
        private boolean alwaysShowingType = false;

        @Getter
        private boolean neverShowingType = false;

        @Getter
        private boolean isPrettyPrint = false;

        @Getter
        private boolean writingLongsAsStrings = false;

        @Getter
        private boolean skippingNullFields = false;

        @Getter
        private boolean forcingMapFormatWithKeyArrays = false;

        @Getter
        private boolean enumPublicOnly = false;

        @Getter
        private ClassLoader classLoader = WriteOptionsImplementation.class.getClassLoader();

        @Getter
        private JsonWriter.JsonClassWriter enumWriter = new Writers.EnumsAsStringWriter();

        @Getter
        private Map<Class<?>, JsonWriter.JsonClassWriter> customWriters;

        @Getter
        private final Map<String, String> customTypeMap;

        @Getter
        private final Collection<Class<?>> nonCustomClasses;

        @Getter
        private final Map<Class<?>, Collection<Accessor>> fieldSpecifiers;

        @Getter
        private final Map<Class<?>, Collection<Accessor>> fieldNameBlackList;

        @Getter
        private String dateFormat;

        private final Map<String, Object> customArguments;


        private WriteOptionsImplementation() {
            this.customWriters = new HashMap<>(BASE_WRITERS);
            this.fieldNameBlackList = new HashMap<>();
            this.fieldSpecifiers = new HashMap<>();
            this.customTypeMap = new HashMap<>();
            this.customArguments = new HashMap<>();
            this.nonCustomClasses = new HashSet<>();
        }

        public Object getCustomArgument(String name) {
            return customArguments.get(name);
        }
    }

}
