package com.cedarsoftware.io.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonClassReader;
import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.annotation.IoAlias;
import com.cedarsoftware.io.annotation.IoAnyGetter;
import com.cedarsoftware.io.annotation.IoAnySetter;
import com.cedarsoftware.io.annotation.IoClassFactory;
import com.cedarsoftware.io.annotation.IoCustomReader;
import com.cedarsoftware.io.annotation.IoCustomWriter;
import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoDeserialize;
import com.cedarsoftware.io.annotation.IoFormat;
import com.cedarsoftware.io.annotation.IoGetter;
import com.cedarsoftware.io.annotation.IoIgnore;
import com.cedarsoftware.io.annotation.IoIgnoreProperties;
import com.cedarsoftware.io.annotation.IoIgnoreType;
import com.cedarsoftware.io.annotation.IoInclude;
import com.cedarsoftware.io.annotation.IoIncludeProperties;
import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.annotation.IoNonReferenceable;
import com.cedarsoftware.io.annotation.IoNotCustomReader;
import com.cedarsoftware.io.annotation.IoNotCustomWritten;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoSetter;
import com.cedarsoftware.io.annotation.IoTypeInfo;
import com.cedarsoftware.io.annotation.IoTypeName;
import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.io.annotation.IoPropertyOrder;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.ReflectionUtils;

/**
 * Scans classes for json-io annotation metadata and caches the results.
 * <p>
 * Supports two annotation sources with the following priority:
 * <ol>
 *   <li>json-io native annotations ({@code com.cedarsoftware.io.annotation.*}) — checked first</li>
 *   <li>External annotations (e.g., Jackson's {@code com.fasterxml.jackson.annotation.*}) —
 *       checked via reflection only if native annotation is absent on the same element</li>
 * </ol>
 * <p>
 * External annotations are detected lazily via {@code Class.forName()} with no compile-time
 * dependency. If the external annotation library is not on the classpath, detection is silently
 * skipped with zero overhead.
 * <p>
 * Results are cached in a static {@link ClassValueMap} — each class is scanned exactly once
 * per JVM lifetime. Annotation metadata is intrinsic to classes and does not depend on
 * ReadOptions/WriteOptions settings.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 */
public class AnnotationResolver {

    // ======================== External annotation detection ========================
    // All external annotation classes and methods are resolved once in this static block.
    // If the external library is not on the classpath, externalAvailable = false and
    // all external lookups short-circuit with zero overhead.

    private static final boolean externalAvailable;
    // @JsonNaming and @JsonDeserialize live in jackson-databind (separate jar), detected independently
    private static final boolean extNamingAvailable;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_NAMING;
    private static final Method EXT_NAMING_VALUE;
    private static final boolean extDeserializeAvailable;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_DESERIALIZE;
    private static final Method EXT_DESERIALIZE_AS;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_CREATOR;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_VALUE;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_PROPERTY;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_IGNORE;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_IGNORE_PROPERTIES;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_ALIAS;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_PROPERTY_ORDER;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_INCLUDE;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_INCLUDE_PROPERTIES;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_IGNORE_TYPE;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_TYPE_INFO;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_GETTER;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_SETTER;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_TYPE_NAME;
    @SuppressWarnings("unchecked")
    private static final Class<? extends Annotation> EXT_FORMAT;
    private static final Class<? extends Annotation> EXT_ANY_SETTER;
    private static final Class<? extends Annotation> EXT_ANY_GETTER;

    private static final Method EXT_GETTER_VALUE;
    private static final Method EXT_SETTER_VALUE;
    private static final Method EXT_TYPE_NAME_VALUE;
    private static final Method EXT_FORMAT_PATTERN;
    private static final Method EXT_PROPERTY_VALUE;
    private static final Method EXT_IGNORE_PROPERTIES_VALUE;
    private static final Method EXT_ALIAS_VALUE;
    private static final Method EXT_PROPERTY_ORDER_VALUE;
    private static final Method EXT_INCLUDE_VALUE;
    private static final Object EXT_INCLUDE_NON_NULL;
    private static final Method EXT_INCLUDE_PROPERTIES_VALUE;
    private static final Method EXT_TYPE_INFO_DEFAULT_IMPL;

    static {
        boolean available = false;
        Class<? extends Annotation> extCreator = null;
        Class<? extends Annotation> extValue = null;
        Class<? extends Annotation> extProperty = null;
        Class<? extends Annotation> extIgnore = null;
        Class<? extends Annotation> extIgnoreProperties = null;
        Class<? extends Annotation> extAlias = null;
        Class<? extends Annotation> extPropertyOrder = null;
        Class<? extends Annotation> extInclude = null;
        Class<? extends Annotation> extIncludeProperties = null;
        Class<? extends Annotation> extIgnoreType = null;
        Class<? extends Annotation> extTypeInfo = null;
        Class<? extends Annotation> extGetter = null;
        Class<? extends Annotation> extSetter = null;
        Class<? extends Annotation> extTypeName = null;
        Class<? extends Annotation> extFormat = null;
        Class<? extends Annotation> extAnySetter = null;
        Class<? extends Annotation> extAnyGetter = null;
        Method extGetterValue = null;
        Method extSetterValue = null;
        Method extTypeNameValue = null;
        Method extPropertyValue = null;
        Method extIgnorePropertiesValue = null;
        Method extAliasValue = null;
        Method extPropertyOrderValue = null;
        Method extIncludeValue = null;
        Object extIncludeNonNull = null;
        Method extIncludePropertiesValue = null;
        Method extTypeInfoDefaultImpl = null;
        Method extFormatPattern = null;

        try {
            extCreator = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonCreator");
            extValue = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonValue");
            extProperty = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonProperty");
            extIgnore = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonIgnore");
            extIgnoreProperties = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonIgnoreProperties");
            extAlias = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonAlias");
            extPropertyOrder = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonPropertyOrder");
            extInclude = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonInclude");
            extIncludeProperties = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonIncludeProperties");
            extIgnoreType = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonIgnoreType");
            extTypeInfo = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonTypeInfo");
            extGetter = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonGetter");
            extSetter = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonSetter");
            extTypeName = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonTypeName");
            extFormat = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonFormat");
            extAnySetter = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonAnySetter");
            extAnyGetter = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.annotation.JsonAnyGetter");

            extGetterValue = extGetter.getMethod("value");
            extSetterValue = extSetter.getMethod("value");
            extTypeNameValue = extTypeName.getMethod("value");
            extFormatPattern = extFormat.getMethod("pattern");
            extPropertyValue = extProperty.getMethod("value");
            extIncludePropertiesValue = extIncludeProperties.getMethod("value");
            extIgnorePropertiesValue = extIgnoreProperties.getMethod("value");
            extTypeInfoDefaultImpl = extTypeInfo.getMethod("defaultImpl");
            extAliasValue = extAlias.getMethod("value");
            extPropertyOrderValue = extPropertyOrder.getMethod("value");
            extIncludeValue = extInclude.getMethod("value");

            // Resolve the NON_NULL enum constant from JsonInclude.Include
            Class<?> includeEnum = Class.forName("com.fasterxml.jackson.annotation.JsonInclude$Include");
            for (Object enumConst : includeEnum.getEnumConstants()) {
                if ("NON_NULL".equals(((Enum<?>) enumConst).name())) {
                    extIncludeNonNull = enumConst;
                    break;
                }
            }

            available = true;
        } catch (Throwable t) {
            // External annotations not available — silently skip
        }

        externalAvailable = available;
        EXT_CREATOR = extCreator;
        EXT_VALUE = extValue;
        EXT_PROPERTY = extProperty;
        EXT_IGNORE = extIgnore;
        EXT_IGNORE_PROPERTIES = extIgnoreProperties;
        EXT_ALIAS = extAlias;
        EXT_PROPERTY_ORDER = extPropertyOrder;
        EXT_INCLUDE = extInclude;
        EXT_INCLUDE_PROPERTIES = extIncludeProperties;
        EXT_IGNORE_TYPE = extIgnoreType;
        EXT_TYPE_INFO = extTypeInfo;
        EXT_GETTER = extGetter;
        EXT_SETTER = extSetter;
        EXT_TYPE_NAME = extTypeName;
        EXT_FORMAT = extFormat;
        EXT_ANY_SETTER = extAnySetter;
        EXT_ANY_GETTER = extAnyGetter;
        EXT_GETTER_VALUE = extGetterValue;
        EXT_SETTER_VALUE = extSetterValue;
        EXT_TYPE_NAME_VALUE = extTypeNameValue;
        EXT_FORMAT_PATTERN = extFormatPattern;
        EXT_PROPERTY_VALUE = extPropertyValue;
        EXT_INCLUDE_PROPERTIES_VALUE = extIncludePropertiesValue;
        EXT_IGNORE_PROPERTIES_VALUE = extIgnorePropertiesValue;
        EXT_ALIAS_VALUE = extAliasValue;
        EXT_PROPERTY_ORDER_VALUE = extPropertyOrderValue;
        EXT_INCLUDE_VALUE = extIncludeValue;
        EXT_INCLUDE_NON_NULL = extIncludeNonNull;
        EXT_TYPE_INFO_DEFAULT_IMPL = extTypeInfoDefaultImpl;

        // @JsonNaming lives in jackson-databind (separate jar from jackson-annotations)
        boolean namingAvail = false;
        Class<? extends Annotation> extNaming = null;
        Method extNamingValue = null;
        try {
            extNaming = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.databind.annotation.JsonNaming");
            extNamingValue = extNaming.getMethod("value");
            namingAvail = true;
        } catch (Throwable t) {
            // jackson-databind not on classpath — silently skip
        }
        extNamingAvailable = namingAvail;
        EXT_NAMING = extNaming;
        EXT_NAMING_VALUE = extNamingValue;

        // @JsonDeserialize also lives in jackson-databind
        boolean deserAvail = false;
        Class<? extends Annotation> extDeserialize = null;
        Method extDeserializeAs = null;
        try {
            extDeserialize = (Class<? extends Annotation>) Class.forName("com.fasterxml.jackson.databind.annotation.JsonDeserialize");
            extDeserializeAs = extDeserialize.getMethod("as");
            deserAvail = true;
        } catch (Throwable t) {
            // jackson-databind not on classpath — silently skip
        }
        extDeserializeAvailable = deserAvail;
        EXT_DESERIALIZE = extDeserialize;
        EXT_DESERIALIZE_AS = extDeserializeAs;
    }

    // ======================== Cache ========================

    private static final ClassValueMap<ClassAnnotationMetadata> cache = new ClassValueMap<>();
    // Reverse map for @IoTypeName / @JsonTypeName: alias → fully-qualified class name
    private static final Map<String, String> annotationAliasToClassName = new ConcurrentHashMap<>();
    // ThreadLocal to track classes currently being scanned (prevents recursive stack overflow)
    private static final ThreadLocal<Set<Class<?>>> SCANNING = ThreadLocal.withInitial(LinkedHashSet::new);
    private static final ClassAnnotationMetadata EMPTY = new ClassAnnotationMetadata(
            Collections.<String, String>emptyMap(),
            Collections.<String>emptySet(),
            Collections.<String, String>emptyMap(),
            null,
            Collections.<String>emptySet(),
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null);

    /**
     * Resolve an annotation-based type alias back to the fully-qualified class name.
     * Populated by {@code @IoTypeName} / {@code @JsonTypeName} scan results.
     * @param alias the alias string (e.g., "Sensor")
     * @return the fully-qualified class name, or null if not found
     */
    public static String resolveAnnotationAlias(String alias) {
        return annotationAliasToClassName.get(alias);
    }

    /**
     * Get annotation metadata for a class. Scans once, caches forever.
     * Thread-safe via ClassValueMap. Recursive calls during scanning
     * (e.g., @IoIgnoreType check on field types) return EMPTY to prevent stack overflow.
     */
    public static ClassAnnotationMetadata getMetadata(Class<?> clazz) {
        if (clazz == null) {
            return EMPTY;
        }
        ClassAnnotationMetadata meta = cache.get(clazz);
        if (meta == null) {
            Set<Class<?>> scanning = SCANNING.get();
            if (!scanning.add(clazz)) {
                // Already scanning this class on this thread — return EMPTY to break cycle
                return EMPTY;
            }
            try {
                meta = scan(clazz);
                cache.put(clazz, meta);
            } finally {
                scanning.remove(clazz);
            }
        }
        return meta;
    }

    /**
     * @return true if external annotation support is available on the classpath
     */
    public static boolean isExternalAnnotationAvailable() {
        return externalAvailable;
    }

    // ======================== Scanning ========================

    private static ClassAnnotationMetadata scan(Class<?> clazz) {
        Map<String, String> renames = new LinkedHashMap<>();
        Set<String> ignored = new LinkedHashSet<>();
        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> nonNullFields = new LinkedHashSet<>();
        Map<String, Class<?>> fieldTypeInfoDefaults = null;
        Map<String, Class<?>> fieldDeserializeOverrides = null;
        Map<String, String> fieldFormatPatterns = null;
        String[] order = null;

        // 1. Class-level annotations
        order = scanClassLevelAnnotations(clazz, ignored);

        // 1b. @IoIncludeProperties — class-level whitelist
        Set<String> includedFields = scanIncludeProperties(clazz);

        // 1c. @IoIgnoreType — class-level type exclusion flag (or Jackson @JsonIgnoreType)
        boolean ignoredType = clazz.isAnnotationPresent(IoIgnoreType.class)
                || (externalAvailable && EXT_IGNORE_TYPE != null && clazz.isAnnotationPresent(EXT_IGNORE_TYPE));

        // 1d. @IoClassFactory — class-level factory for deserialization
        Class<? extends ClassFactory> classFactory = null;
        IoClassFactory factoryAnn = clazz.getAnnotation(IoClassFactory.class);
        if (factoryAnn != null) {
            classFactory = factoryAnn.value();
        }

        // 1e. @IoNonReferenceable — class-level non-referenceable flag
        boolean nonReferenceable = clazz.isAnnotationPresent(IoNonReferenceable.class);

        // 1f. @IoNotCustomReader — class-level flag to suppress custom reader
        boolean notCustomRead = clazz.isAnnotationPresent(IoNotCustomReader.class);

        // 1g. @IoNotCustomWritten — class-level flag to suppress custom writer
        boolean notCustomWrite = clazz.isAnnotationPresent(IoNotCustomWritten.class);

        // 1h. @IoCustomWriter — class-level custom writer
        Class<? extends JsonClassWriter> customWriter = null;
        IoCustomWriter writerAnn = clazz.getAnnotation(IoCustomWriter.class);
        if (writerAnn != null) {
            customWriter = writerAnn.value();
        }

        // 1i. @IoCustomReader — class-level custom reader
        Class<? extends JsonClassReader> customReader = null;
        IoCustomReader readerAnn = clazz.getAnnotation(IoCustomReader.class);
        if (readerAnn != null) {
            customReader = readerAnn.value();
        }

        // 1j. @IoTypeName — class-level type alias (or Jackson @JsonTypeName fallback)
        String typeName = null;
        IoTypeName typeNameAnn = clazz.getAnnotation(IoTypeName.class);
        if (typeNameAnn != null) {
            typeName = typeNameAnn.value();
        } else if (externalAvailable && EXT_TYPE_NAME != null) {
            Annotation jtn = clazz.getAnnotation(EXT_TYPE_NAME);
            if (jtn != null) {
                try {
                    typeName = (String) EXT_TYPE_NAME_VALUE.invoke(jtn);
                } catch (Exception ignore) {
                }
            }
        }
        if (typeName != null && !typeName.isEmpty()) {
            annotationAliasToClassName.put(typeName, clazz.getName());
        } else {
            typeName = null;  // normalize empty string to null
        }

        // 2. @IoNaming — class-level naming strategy
        IoNaming.Strategy namingStrategy = scanNamingStrategy(clazz);

        // 3. Field-level annotations — walk entire class hierarchy
        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            Field[] fields;
            try {
                fields = curr.getDeclaredFields();
            } catch (SecurityException e) {
                curr = curr.getSuperclass();
                continue;
            }

            for (Field field : fields) {
                String fieldName = field.getName();

                // Skip synthetic/bridge fields
                if (field.isSynthetic()) {
                    continue;
                }

                // @IoIgnore / external equivalent
                if (scanIgnore(field)) {
                    ignored.add(fieldName);
                    continue;
                }

                // @IoIgnoreType — check if field's declared type is marked for exclusion
                if (isFieldTypeIgnored(field)) {
                    ignored.add(fieldName);
                    continue;
                }

                // @IoProperty / external equivalent
                String rename = scanProperty(field);
                if (rename != null) {
                    renames.put(fieldName, rename);
                } else if (namingStrategy != null) {
                    // Apply @IoNaming strategy when no explicit @IoProperty
                    String transformed = applyNamingStrategy(fieldName, namingStrategy);
                    if (transformed != null && !transformed.equals(fieldName)) {
                        renames.put(fieldName, transformed);
                    }
                }

                // @IoAlias / external equivalent
                String[] fieldAliases = scanAlias(field);
                if (fieldAliases != null) {
                    for (String alt : fieldAliases) {
                        aliases.put(alt, fieldName);
                    }
                }

                // @IoInclude / external equivalent
                if (scanNonNull(field)) {
                    nonNullFields.add(fieldName);
                }

                // @IoTypeInfo — field-level default concrete type (or Jackson @JsonTypeInfo fallback)
                IoTypeInfo typeInfo = field.getAnnotation(IoTypeInfo.class);
                if (typeInfo != null) {
                    if (fieldTypeInfoDefaults == null) {
                        fieldTypeInfoDefaults = new LinkedHashMap<>();
                    }
                    fieldTypeInfoDefaults.put(fieldName, typeInfo.value());
                } else if (externalAvailable && EXT_TYPE_INFO != null) {
                    Annotation extTI = field.getAnnotation(EXT_TYPE_INFO);
                    if (extTI != null) {
                        try {
                            Class<?> defaultImpl = (Class<?>) EXT_TYPE_INFO_DEFAULT_IMPL.invoke(extTI);
                            // Jackson uses JsonTypeInfo.None.class as the default (meaning "not specified")
                            if (defaultImpl != null && !defaultImpl.getSimpleName().equals("None")
                                    && defaultImpl != Void.class) {
                                if (fieldTypeInfoDefaults == null) {
                                    fieldTypeInfoDefaults = new LinkedHashMap<>();
                                }
                                fieldTypeInfoDefaults.put(fieldName, defaultImpl);
                            }
                        } catch (Exception e) {
                            // Ignore reflection failure
                        }
                    }
                }

                // @IoDeserialize — field-level type override (or Jackson @JsonDeserialize fallback)
                IoDeserialize deser = field.getAnnotation(IoDeserialize.class);
                if (deser != null && deser.as() != Void.class) {
                    if (fieldDeserializeOverrides == null) {
                        fieldDeserializeOverrides = new LinkedHashMap<>();
                    }
                    fieldDeserializeOverrides.put(fieldName, deser.as());
                } else if (extDeserializeAvailable && EXT_DESERIALIZE != null) {
                    Annotation extDeser = field.getAnnotation(EXT_DESERIALIZE);
                    if (extDeser != null) {
                        try {
                            Class<?> asClass = (Class<?>) EXT_DESERIALIZE_AS.invoke(extDeser);
                            // Jackson uses Void.class as the default (meaning "not specified")
                            if (asClass != null && asClass != Void.class) {
                                if (fieldDeserializeOverrides == null) {
                                    fieldDeserializeOverrides = new LinkedHashMap<>();
                                }
                                fieldDeserializeOverrides.put(fieldName, asClass);
                            }
                        } catch (Exception e) {
                            // Ignore reflection failure
                        }
                    }
                }

                // @IoFormat — per-field date/time format pattern (or Jackson @JsonFormat fallback)
                IoFormat ioFormat = field.getAnnotation(IoFormat.class);
                if (ioFormat != null && !ioFormat.value().isEmpty()) {
                    if (fieldFormatPatterns == null) {
                        fieldFormatPatterns = new LinkedHashMap<>();
                    }
                    fieldFormatPatterns.put(fieldName, ioFormat.value());
                } else if (externalAvailable && EXT_FORMAT != null) {
                    Annotation jf = field.getAnnotation(EXT_FORMAT);
                    if (jf != null) {
                        try {
                            String pat = (String) EXT_FORMAT_PATTERN.invoke(jf);
                            if (pat != null && !pat.isEmpty()) {
                                if (fieldFormatPatterns == null) {
                                    fieldFormatPatterns = new LinkedHashMap<>();
                                }
                                fieldFormatPatterns.put(fieldName, pat);
                            }
                        } catch (Exception e) {
                            // Ignore reflection failure
                        }
                    }
                }
            }
            curr = curr.getSuperclass();
        }

        // Renamed fields: the serialized name should also be accepted on read
        for (Map.Entry<String, String> entry : renames.entrySet()) {
            String serializedName = entry.getValue();
            String javaFieldName = entry.getKey();
            if (!aliases.containsKey(serializedName)) {
                aliases.put(serializedName, javaFieldName);
            }
        }

        // 4. Scan for @IoCreator on constructors and static factory methods
        Executable creator = scanCreator(clazz);

        // 5. Scan for @IoValue on instance methods
        Method valueMethod = scanValueMethod(clazz);

        // 6. Scan for @IoGetter/@IoSetter on instance methods
        Map<String, String> getterMethods = null;
        Map<String, String> setterMethods = null;
        Map<String, String>[] getterSetterMaps = scanGetterSetterMethods(clazz);
        if (getterSetterMaps[0] != null) {
            getterMethods = getterSetterMaps[0];
        }
        if (getterSetterMaps[1] != null) {
            setterMethods = getterSetterMaps[1];
        }

        // 7. Scan for @IoAnySetter/@IoAnyGetter on instance methods
        Method[] anyMethods = scanAnySetterGetterMethods(clazz);
        Method anySetterMethod = anyMethods[0];
        Method anyGetterMethod = anyMethods[1];

        if (renames.isEmpty() && ignored.isEmpty() && aliases.isEmpty()
                && order == null && nonNullFields.isEmpty() && creator == null
                && valueMethod == null && includedFields == null && !ignoredType
                && fieldTypeInfoDefaults == null && fieldDeserializeOverrides == null
                && classFactory == null && getterMethods == null && setterMethods == null
                && !nonReferenceable && !notCustomRead && !notCustomWrite
                && customWriter == null && customReader == null
                && typeName == null && fieldFormatPatterns == null
                && anySetterMethod == null && anyGetterMethod == null) {
            return EMPTY;
        }

        return new ClassAnnotationMetadata(
                Collections.unmodifiableMap(renames),
                Collections.unmodifiableSet(ignored),
                Collections.unmodifiableMap(aliases),
                order,
                Collections.unmodifiableSet(nonNullFields),
                creator,
                valueMethod,
                includedFields != null ? Collections.unmodifiableSet(includedFields) : null,
                ignoredType,
                fieldTypeInfoDefaults != null ? Collections.unmodifiableMap(fieldTypeInfoDefaults) : null,
                fieldDeserializeOverrides != null ? Collections.unmodifiableMap(fieldDeserializeOverrides) : null,
                classFactory,
                getterMethods != null ? Collections.unmodifiableMap(getterMethods) : null,
                setterMethods != null ? Collections.unmodifiableMap(setterMethods) : null,
                nonReferenceable,
                notCustomRead,
                notCustomWrite,
                customWriter,
                customReader,
                typeName,
                fieldFormatPatterns != null ? Collections.unmodifiableMap(fieldFormatPatterns) : null,
                anySetterMethod,
                anyGetterMethod);
    }

    // ---- Class-level scanners ----

    private static String[] scanClassLevelAnnotations(Class<?> clazz, Set<String> ignored) {
        String[] order = null;

        // @IoIgnoreProperties
        IoIgnoreProperties iip = clazz.getAnnotation(IoIgnoreProperties.class);
        if (iip != null) {
            Collections.addAll(ignored, iip.value());
        } else if (externalAvailable) {
            Annotation extIgp = clazz.getAnnotation(EXT_IGNORE_PROPERTIES);
            if (extIgp != null) {
                try {
                    String[] vals = (String[]) EXT_IGNORE_PROPERTIES_VALUE.invoke(extIgp);
                    Collections.addAll(ignored, vals);
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }

        // @IoPropertyOrder
        IoPropertyOrder ipo = clazz.getAnnotation(IoPropertyOrder.class);
        if (ipo != null) {
            order = ipo.value();
        } else if (externalAvailable) {
            Annotation extOrd = clazz.getAnnotation(EXT_PROPERTY_ORDER);
            if (extOrd != null) {
                try {
                    order = (String[]) EXT_PROPERTY_ORDER_VALUE.invoke(extOrd);
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }

        return order;
    }

    /**
     * Scan for @IoIncludeProperties or Jackson @JsonIncludeProperties (class-level whitelist).
     * Returns the set of included field names, or null if not present.
     */
    private static Set<String> scanIncludeProperties(Class<?> clazz) {
        IoIncludeProperties iip = clazz.getAnnotation(IoIncludeProperties.class);
        if (iip != null && iip.value().length > 0) {
            Set<String> included = new LinkedHashSet<>();
            Collections.addAll(included, iip.value());
            return included;
        }
        // Fall back to Jackson @JsonIncludeProperties
        if (externalAvailable && EXT_INCLUDE_PROPERTIES != null) {
            Annotation extIip = clazz.getAnnotation(EXT_INCLUDE_PROPERTIES);
            if (extIip != null) {
                try {
                    String[] vals = (String[]) EXT_INCLUDE_PROPERTIES_VALUE.invoke(extIip);
                    if (vals != null && vals.length > 0) {
                        Set<String> included = new LinkedHashSet<>();
                        Collections.addAll(included, vals);
                        return included;
                    }
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return null;
    }

    /**
     * Check if a field's declared type is annotated with @IoIgnoreType.
     * Uses getMetadata() which is cached, so recursive calls are safe.
     */
    private static boolean isFieldTypeIgnored(Field field) {
        Class<?> fieldType = field.getType();
        // Skip primitives and common JDK types (they can't have @IoIgnoreType)
        if (fieldType.isPrimitive() || fieldType == String.class || fieldType == Object.class) {
            return false;
        }
        return getMetadata(fieldType).isIgnoredType();
    }

    private static IoNaming.Strategy scanNamingStrategy(Class<?> clazz) {
        IoNaming naming = clazz.getAnnotation(IoNaming.class);
        if (naming != null) {
            return naming.value();
        }
        if (extNamingAvailable) {
            Annotation extNaming = clazz.getAnnotation(EXT_NAMING);
            if (extNaming != null) {
                try {
                    Class<?> strategyClass = (Class<?>) EXT_NAMING_VALUE.invoke(extNaming);
                    return mapExternalNamingStrategy(strategyClass);
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return null;
    }

    /**
     * Map a Jackson PropertyNamingStrategies strategy class to the corresponding IoNaming.Strategy.
     * Uses class name matching to avoid compile-time dependency on jackson-databind.
     */
    private static IoNaming.Strategy mapExternalNamingStrategy(Class<?> strategyClass) {
        if (strategyClass == null) {
            return null;
        }
        String name = strategyClass.getSimpleName();
        switch (name) {
            case "SnakeCaseStrategy":
                return IoNaming.Strategy.SNAKE_CASE;
            case "KebabCaseStrategy":
                return IoNaming.Strategy.KEBAB_CASE;
            case "UpperCamelCaseStrategy":
                return IoNaming.Strategy.UPPER_CAMEL_CASE;
            case "LowerDotCaseStrategy":
                return IoNaming.Strategy.LOWER_DOT_CASE;
            default:
                return null;
        }
    }

    /**
     * Apply a naming strategy to transform a Java field name.
     * Returns null if the field name would not change (e.g., single-word lowercase).
     */
    static String applyNamingStrategy(String fieldName, IoNaming.Strategy strategy) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        switch (strategy) {
            case SNAKE_CASE:
                return camelToSeparated(fieldName, '_');
            case KEBAB_CASE:
                return camelToSeparated(fieldName, '-');
            case LOWER_DOT_CASE:
                return camelToSeparated(fieldName, '.');
            case UPPER_CAMEL_CASE:
                return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            default:
                return null;
        }
    }

    /**
     * Convert a camelCase name to a separated format (snake_case, kebab-case, lower.dot.case).
     * Handles consecutive uppercase letters (e.g., parseXMLDocument → parse_xml_document).
     */
    private static String camelToSeparated(String name, char separator) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    // Insert separator before uppercase that follows lowercase,
                    // or before uppercase that precedes lowercase (end of acronym)
                    char prev = name.charAt(i - 1);
                    if (Character.isLowerCase(prev)) {
                        sb.append(separator);
                    } else if (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1))) {
                        sb.append(separator);
                    }
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- Creator scanner ----

    /**
     * Scan constructors and static methods for @IoCreator.
     * Returns the annotated Executable, or null if none found.
     */
    private static Executable scanCreator(Class<?> clazz) {
        // Check constructors for @IoCreator — iterate declared constructors without making them accessible,
        // then use ReflectionUtils.getConstructor() only for the annotated one (handles caching + accessibility)
        try {
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (ctor.isAnnotationPresent(IoCreator.class)) {
                    return ReflectionUtils.getConstructor(clazz, ctor.getParameterTypes());
                }
            }
        } catch (SecurityException e) {
            // Skip if constructors are inaccessible
        }

        // Check static methods for @IoCreator
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(IoCreator.class)
                        && Modifier.isStatic(method.getModifiers())) {
                    return ReflectionUtils.getMethod(clazz, method.getName(), method.getParameterTypes());
                }
            }
        } catch (SecurityException e) {
            // Skip if methods are inaccessible
        }

        // Fall back to Jackson @JsonCreator if no native annotation found
        if (externalAvailable && EXT_CREATOR != null) {
            try {
                for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                    if (ctor.isAnnotationPresent(EXT_CREATOR)) {
                        return ReflectionUtils.getConstructor(clazz, ctor.getParameterTypes());
                    }
                }
            } catch (SecurityException e) {
                // Skip if constructors are inaccessible
            }

            try {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(EXT_CREATOR)
                            && Modifier.isStatic(method.getModifiers())) {
                        return ReflectionUtils.getMethod(clazz, method.getName(), method.getParameterTypes());
                    }
                }
            } catch (SecurityException e) {
                // Skip if methods are inaccessible
            }
        }

        return null;
    }

    // ---- Value method scanner ----

    /**
     * Scan instance methods for @IoValue.
     * Returns the annotated Method, or null if none found.
     * The method must be a no-arg instance method with a non-void return type.
     */
    private static Method scanValueMethod(Class<?> clazz) {
        // Check for @IoValue first
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(IoValue.class)
                        && !Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0
                        && method.getReturnType() != void.class) {
                    return ReflectionUtils.getMethod(clazz, method.getName());
                }
            }
        } catch (SecurityException e) {
            // Skip if methods are inaccessible
        }

        // Fall back to Jackson @JsonValue if no native annotation found
        if (externalAvailable && EXT_VALUE != null) {
            try {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(EXT_VALUE)
                            && !Modifier.isStatic(method.getModifiers())
                            && method.getParameterCount() == 0
                            && method.getReturnType() != void.class) {
                        return ReflectionUtils.getMethod(clazz, method.getName());
                    }
                }
            } catch (SecurityException e) {
                // Skip if methods are inaccessible
            }
        }
        return null;
    }

    // ---- Getter/Setter scanner ----

    /**
     * Scan instance methods for @IoGetter/@IoSetter (and Jackson @JsonGetter/@JsonSetter fallback).
     * Returns a two-element array: [0] = getterMethods (fieldName → methodName), [1] = setterMethods.
     * Either element may be null if no annotations are found.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String>[] scanGetterSetterMethods(Class<?> clazz) {
        Map<String, String> getterMethods = null;
        Map<String, String> setterMethods = null;

        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            Method[] methods;
            try {
                methods = curr.getDeclaredMethods();
            } catch (SecurityException e) {
                curr = curr.getSuperclass();
                continue;
            }

            for (Method method : methods) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                // --- @IoGetter / @JsonGetter ---
                IoGetter ioGetter = method.getAnnotation(IoGetter.class);
                String getterFieldName = null;
                if (ioGetter != null && !ioGetter.value().isEmpty()
                        && method.getParameterCount() == 0
                        && method.getReturnType() != void.class) {
                    getterFieldName = ioGetter.value();
                } else if (externalAvailable && EXT_GETTER != null && ioGetter == null) {
                    Annotation extGet = method.getAnnotation(EXT_GETTER);
                    if (extGet != null
                            && method.getParameterCount() == 0
                            && method.getReturnType() != void.class) {
                        try {
                            String val = (String) EXT_GETTER_VALUE.invoke(extGet);
                            if (val != null && !val.isEmpty()) {
                                getterFieldName = val;
                            }
                        } catch (Exception e) {
                            // Ignore reflection failure
                        }
                    }
                }

                if (getterFieldName != null) {
                    if (getterMethods == null) {
                        getterMethods = new LinkedHashMap<>();
                    }
                    getterMethods.putIfAbsent(getterFieldName, method.getName());
                }

                // --- @IoSetter / @JsonSetter ---
                IoSetter ioSetter = method.getAnnotation(IoSetter.class);
                String setterFieldName = null;
                if (ioSetter != null && !ioSetter.value().isEmpty()
                        && method.getParameterCount() == 1) {
                    setterFieldName = ioSetter.value();
                } else if (externalAvailable && EXT_SETTER != null && ioSetter == null) {
                    Annotation extSet = method.getAnnotation(EXT_SETTER);
                    if (extSet != null && method.getParameterCount() == 1) {
                        try {
                            String val = (String) EXT_SETTER_VALUE.invoke(extSet);
                            if (val != null && !val.isEmpty()) {
                                setterFieldName = val;
                            }
                        } catch (Exception e) {
                            // Ignore reflection failure
                        }
                    }
                }

                if (setterFieldName != null) {
                    if (setterMethods == null) {
                        setterMethods = new LinkedHashMap<>();
                    }
                    setterMethods.putIfAbsent(setterFieldName, method.getName());
                }
            }
            curr = curr.getSuperclass();
        }

        return new Map[]{getterMethods, setterMethods};
    }

    // ---- @IoAnySetter / @IoAnyGetter scanner ----

    /**
     * Scan instance methods for @IoAnySetter and @IoAnyGetter (and Jackson @JsonAnySetter/@JsonAnyGetter fallback).
     * Returns a two-element array: [0] = anySetterMethod, [1] = anyGetterMethod.
     * Either element may be null if no annotation is found.
     */
    private static Method[] scanAnySetterGetterMethods(Class<?> clazz) {
        Method anySetter = null;
        Method anyGetter = null;

        Class<?> curr = clazz;
        while (curr != null && curr != Object.class) {
            Method[] methods;
            try {
                methods = curr.getDeclaredMethods();
            } catch (SecurityException e) {
                curr = curr.getSuperclass();
                continue;
            }

            for (Method method : methods) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                // --- @IoAnySetter / @JsonAnySetter ---
                if (anySetter == null) {
                    if (method.isAnnotationPresent(IoAnySetter.class)
                            && method.getParameterCount() == 2) {
                        anySetter = ReflectionUtils.getMethod(curr, method.getName(), method.getParameterTypes());
                    } else if (externalAvailable && EXT_ANY_SETTER != null
                            && method.isAnnotationPresent(EXT_ANY_SETTER)
                            && method.getParameterCount() == 2) {
                        anySetter = ReflectionUtils.getMethod(curr, method.getName(), method.getParameterTypes());
                    }
                }

                // --- @IoAnyGetter / @JsonAnyGetter ---
                if (anyGetter == null) {
                    if (method.isAnnotationPresent(IoAnyGetter.class)
                            && method.getParameterCount() == 0
                            && Map.class.isAssignableFrom(method.getReturnType())) {
                        anyGetter = ReflectionUtils.getMethod(curr, method.getName());
                    } else if (externalAvailable && EXT_ANY_GETTER != null
                            && method.isAnnotationPresent(EXT_ANY_GETTER)
                            && method.getParameterCount() == 0
                            && Map.class.isAssignableFrom(method.getReturnType())) {
                        anyGetter = ReflectionUtils.getMethod(curr, method.getName());
                    }
                }
            }
            curr = curr.getSuperclass();
        }

        return new Method[]{anySetter, anyGetter};
    }

    // ---- Parameter-level helpers ----

    /**
     * Get the JSON key name for a constructor/method parameter, checking @IoProperty first,
     * then Jackson @JsonProperty as fallback, then the parameter's declared name.
     *
     * @param param the parameter to inspect
     * @return the JSON key name to use for matching
     */
    public static String getParameterJsonKey(Parameter param) {
        // Check @IoProperty first
        IoProperty prop = param.getAnnotation(IoProperty.class);
        if (prop != null && !prop.value().isEmpty()) {
            return prop.value();
        }
        // Fall back to Jackson @JsonProperty on the parameter
        if (externalAvailable && EXT_PROPERTY != null) {
            Annotation extProp = param.getAnnotation(EXT_PROPERTY);
            if (extProp != null) {
                try {
                    String val = (String) EXT_PROPERTY_VALUE.invoke(extProp);
                    if (val != null && !val.isEmpty()) {
                        return val;
                    }
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return param.getName();
    }

    // ---- Field-level scanners ----

    private static boolean scanIgnore(Field field) {
        if (field.isAnnotationPresent(IoIgnore.class)) {
            return true;
        }
        if (externalAvailable) {
            return field.getAnnotation(EXT_IGNORE) != null;
        }
        return false;
    }

    private static String scanProperty(Field field) {
        IoProperty prop = field.getAnnotation(IoProperty.class);
        if (prop != null) {
            String val = prop.value();
            return (val != null && !val.isEmpty()) ? val : null;
        }
        if (externalAvailable) {
            Annotation extProp = field.getAnnotation(EXT_PROPERTY);
            if (extProp != null) {
                try {
                    String val = (String) EXT_PROPERTY_VALUE.invoke(extProp);
                    return (val != null && !val.isEmpty()) ? val : null;
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return null;
    }

    private static String[] scanAlias(Field field) {
        IoAlias alias = field.getAnnotation(IoAlias.class);
        if (alias != null) {
            return alias.value().length > 0 ? alias.value() : null;
        }
        if (externalAvailable) {
            Annotation extAlias = field.getAnnotation(EXT_ALIAS);
            if (extAlias != null) {
                try {
                    String[] vals = (String[]) EXT_ALIAS_VALUE.invoke(extAlias);
                    return (vals != null && vals.length > 0) ? vals : null;
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return null;
    }

    private static boolean scanNonNull(Field field) {
        IoInclude inc = field.getAnnotation(IoInclude.class);
        if (inc != null) {
            return inc.value() == IoInclude.Include.NON_NULL;
        }
        if (externalAvailable && EXT_INCLUDE_NON_NULL != null) {
            Annotation extInc = field.getAnnotation(EXT_INCLUDE);
            if (extInc != null) {
                try {
                    Object incValue = EXT_INCLUDE_VALUE.invoke(extInc);
                    return EXT_INCLUDE_NON_NULL.equals(incValue);
                } catch (Exception e) {
                    // Ignore reflection failure
                }
            }
        }
        return false;
    }

    // ======================== Metadata Container ========================

    /**
     * Immutable per-class annotation metadata. Computed once, cached by ClassValueMap.
     */
    public static final class ClassAnnotationMetadata {
        private final Map<String, String> renamedFields;
        private final Set<String> ignoredFields;
        private final Map<String, String> aliasToFieldName;
        private final String[] propertyOrder;
        private final Set<String> nonNullFields;
        private final Executable creator;
        private final Method valueMethod;
        private final Set<String> includedFields;
        private final boolean ignoredType;
        private final Map<String, Class<?>> fieldTypeInfoDefaults;
        private final Map<String, Class<?>> fieldDeserializeOverrides;
        private final Class<? extends ClassFactory> classFactory;
        private final Map<String, String> getterMethods;
        private final Map<String, String> setterMethods;
        private final boolean nonReferenceable;
        private final boolean notCustomRead;
        private final boolean notCustomWrite;
        private final Class<? extends JsonClassWriter> customWriter;
        private final Class<? extends JsonClassReader> customReader;
        private final String typeName;
        private final Map<String, String> fieldFormatPatterns;
        private final Method anySetterMethod;
        private final Method anyGetterMethod;

        ClassAnnotationMetadata(Map<String, String> renamedFields,
                                Set<String> ignoredFields,
                                Map<String, String> aliasToFieldName,
                                String[] propertyOrder,
                                Set<String> nonNullFields,
                                Executable creator,
                                Method valueMethod,
                                Set<String> includedFields,
                                boolean ignoredType,
                                Map<String, Class<?>> fieldTypeInfoDefaults,
                                Map<String, Class<?>> fieldDeserializeOverrides,
                                Class<? extends ClassFactory> classFactory,
                                Map<String, String> getterMethods,
                                Map<String, String> setterMethods,
                                boolean nonReferenceable,
                                boolean notCustomRead,
                                boolean notCustomWrite,
                                Class<? extends JsonClassWriter> customWriter,
                                Class<? extends JsonClassReader> customReader,
                                String typeName,
                                Map<String, String> fieldFormatPatterns,
                                Method anySetterMethod,
                                Method anyGetterMethod) {
            this.renamedFields = renamedFields;
            this.ignoredFields = ignoredFields;
            this.aliasToFieldName = aliasToFieldName;
            this.propertyOrder = propertyOrder;
            this.nonNullFields = nonNullFields;
            this.creator = creator;
            this.valueMethod = valueMethod;
            this.includedFields = includedFields;
            this.ignoredType = ignoredType;
            this.fieldTypeInfoDefaults = fieldTypeInfoDefaults;
            this.fieldDeserializeOverrides = fieldDeserializeOverrides;
            this.classFactory = classFactory;
            this.getterMethods = getterMethods;
            this.setterMethods = setterMethods;
            this.nonReferenceable = nonReferenceable;
            this.notCustomRead = notCustomRead;
            this.notCustomWrite = notCustomWrite;
            this.customWriter = customWriter;
            this.customReader = customReader;
            this.typeName = typeName;
            this.fieldFormatPatterns = fieldFormatPatterns;
            this.anySetterMethod = anySetterMethod;
            this.anyGetterMethod = anyGetterMethod;
        }

        /**
         * Get the serialized JSON name for a Java field, or null if the field is not renamed.
         * @param javaFieldName the Java field name
         * @return the serialized name, or null if no rename is specified
         */
        public String getSerializedName(String javaFieldName) {
            return renamedFields.get(javaFieldName);
        }

        /**
         * Check if a field should be ignored (excluded from serialization/deserialization).
         * @param fieldName the Java field name
         * @return true if the field should be ignored
         */
        public boolean isIgnored(String fieldName) {
            return ignoredFields.contains(fieldName);
        }

        /**
         * Get the alias-to-field-name mapping for read-side alternate name support.
         * @return unmodifiable map of alternate JSON name → Java field name
         */
        public Map<String, String> getAliasToFieldName() {
            return aliasToFieldName;
        }

        /**
         * Get the property order for serialization, or null if not specified.
         * @return ordered array of field names, or null
         */
        public String[] getPropertyOrder() {
            return propertyOrder;
        }

        /**
         * Check if a field should skip null values during serialization.
         * @param fieldName the Java field name
         * @return true if the field should be omitted when its value is null
         */
        public boolean isNonNull(String fieldName) {
            return nonNullFields.contains(fieldName);
        }

        /**
         * Get the @IoCreator constructor or static factory method, or null if not specified.
         * @return the annotated Executable, or null
         */
        public Executable getCreator() {
            return creator;
        }

        /**
         * Get the @IoValue method for single-value serialization, or null if not specified.
         * @return the annotated Method, or null
         */
        public Method getValueMethod() {
            return valueMethod;
        }

        /**
         * Get the set of field names included by @IoIncludeProperties, or null if not specified.
         * @return unmodifiable set of included field names, or null
         */
        public Set<String> getIncludedFields() {
            return includedFields;
        }

        /**
         * @return true if @IoIncludeProperties is present on the class
         */
        public boolean hasIncludedFields() {
            return includedFields != null;
        }

        /**
         * @return true if this class is annotated with @IoIgnoreType
         */
        public boolean isIgnoredType() {
            return ignoredType;
        }

        /**
         * Get the @IoTypeInfo default concrete class for a field, or null if not specified.
         * @param fieldName the Java field name
         * @return the default concrete class, or null
         */
        public Class<?> getFieldTypeInfoDefault(String fieldName) {
            return fieldTypeInfoDefaults == null ? null : fieldTypeInfoDefaults.get(fieldName);
        }

        /**
         * Get the @IoDeserialize override class for a field, or null if not specified.
         * @param fieldName the Java field name
         * @return the override class, or null
         */
        public Class<?> getFieldDeserializeOverride(String fieldName) {
            return fieldDeserializeOverrides == null ? null : fieldDeserializeOverrides.get(fieldName);
        }

        /**
         * Get the @IoClassFactory factory class for this class, or null if not specified.
         * @return the ClassFactory implementation class, or null
         */
        public Class<? extends ClassFactory> getClassFactory() {
            return classFactory;
        }

        /**
         * Get the @IoGetter method name for a field, or null if not specified.
         * @param fieldName the Java field name
         * @return the getter method name, or null
         */
        public String getGetterMethod(String fieldName) {
            return getterMethods == null ? null : getterMethods.get(fieldName);
        }

        /**
         * Get the @IoSetter method name for a field, or null if not specified.
         * @param fieldName the Java field name
         * @return the setter method name, or null
         */
        public String getSetterMethod(String fieldName) {
            return setterMethods == null ? null : setterMethods.get(fieldName);
        }

        /**
         * @return true if this class is annotated with @IoNonReferenceable
         */
        public boolean isNonReferenceable() {
            return nonReferenceable;
        }

        /**
         * @return true if this class is annotated with @IoNotCustomReader
         */
        public boolean isNotCustomRead() {
            return notCustomRead;
        }

        /**
         * @return true if this class is annotated with @IoNotCustomWritten
         */
        public boolean isNotCustomWrite() {
            return notCustomWrite;
        }

        /**
         * Get the @IoCustomWriter class for this type, or null if not specified.
         * @return the JsonClassWriter implementation class, or null
         */
        public Class<? extends JsonClassWriter> getCustomWriter() {
            return customWriter;
        }

        /**
         * Get the @IoCustomReader class for this type, or null if not specified.
         * @return the JsonClassReader implementation class, or null
         */
        public Class<? extends JsonClassReader> getCustomReader() {
            return customReader;
        }

        /**
         * Get the @IoTypeName alias for this class, or null if not specified.
         * @return the type alias string, or null
         */
        public String getTypeName() {
            return typeName;
        }

        /**
         * Get the @IoFormat pattern for a field, or null if not specified.
         * @param fieldName the Java field name
         * @return the format pattern string, or null
         */
        public String getFieldFormatPattern(String fieldName) {
            return fieldFormatPatterns == null ? null : fieldFormatPatterns.get(fieldName);
        }

        /**
         * Get the @IoAnySetter method for handling unrecognized fields during deserialization, or null if not specified.
         * @return the annotated Method, or null
         */
        public Method getAnySetterMethod() {
            return anySetterMethod;
        }

        /**
         * Get the @IoAnyGetter method for providing extra fields during serialization, or null if not specified.
         * @return the annotated Method, or null
         */
        public Method getAnyGetterMethod() {
            return anyGetterMethod;
        }

        /**
         * @return true if this metadata has no annotation information (empty/default)
         */
        public boolean isEmpty() {
            return renamedFields.isEmpty() && ignoredFields.isEmpty()
                    && aliasToFieldName.isEmpty() && propertyOrder == null
                    && nonNullFields.isEmpty() && creator == null
                    && valueMethod == null && includedFields == null
                    && !ignoredType && fieldTypeInfoDefaults == null
                    && fieldDeserializeOverrides == null && classFactory == null
                    && getterMethods == null && setterMethods == null
                    && !nonReferenceable && !notCustomRead && !notCustomWrite
                    && customWriter == null && customReader == null
                    && typeName == null && fieldFormatPatterns == null
                    && anySetterMethod == null && anyGetterMethod == null;
        }
    }
}
