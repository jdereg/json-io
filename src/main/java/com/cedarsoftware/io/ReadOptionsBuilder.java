package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.lang.reflect.Method;

import com.cedarsoftware.io.factory.ArrayFactory;
import com.cedarsoftware.io.factory.EnumClassFactory;
import com.cedarsoftware.io.factory.RecordFactory;
import com.cedarsoftware.io.factory.ThrowableFactory;
import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.io.reflect.InjectorFactory;
import com.cedarsoftware.io.reflect.factories.MethodInjectorFactory;
import com.cedarsoftware.io.reflect.filters.FieldFilter;
import com.cedarsoftware.io.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.io.reflect.filters.field.StaticFieldFilter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.ClassValueSet;
import com.cedarsoftware.util.RegexUtilities;
import com.cedarsoftware.util.ConcurrentSet;
import com.cedarsoftware.util.Convention;
import com.cedarsoftware.util.ReflectionUtils;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.convert.CommonValues;
import com.cedarsoftware.util.convert.Convert;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.ConverterOptions;
import com.cedarsoftware.util.LoggingConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cedarsoftware.io.MetaUtils.loadMapDefinition;

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
public class ReadOptionsBuilder {

    private static final Logger LOG = Logger.getLogger(ReadOptionsBuilder.class.getName());
    static { LoggingConfig.init(); }

    // The BASE_* Maps are regular ConcurrentHashMap's because they are not constantly searched, otherwise they would be ClassValueMaps.
    private static final Map<Class<?>, JsonClassReader> BASE_READERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();
    // BASE_ALIAS_MAPPINGS uses LinkedHashMap to maintain insertion order so later aliases override earlier ones
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<Class<?>, Class<?>> BASE_COERCED_TYPES = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = new ConcurrentSet<>();
    private static final Set<Class<?>> BASE_NOT_CUSTOM_READ = new ConcurrentSet<>();
    private static final Map<Class<?>, Map<String, String>> BASE_NONSTANDARD_SETTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> BASE_NOT_IMPORTED_FIELDS = new ConcurrentHashMap<>();
    
    // Base permanent security limits - default to unlimited for backward compatibility
    private static volatile int BASE_MAX_UNRESOLVED_REFERENCES = Integer.MAX_VALUE;
    private static volatile int BASE_MAX_STACK_DEPTH = Integer.MAX_VALUE;
    private static volatile int BASE_MAX_MAPS_TO_REHASH = Integer.MAX_VALUE;
    private static volatile int BASE_MAX_MISSING_FIELDS = Integer.MAX_VALUE;
    
    // Base permanent JSON parsing security limits - default to generous but finite values for backward compatibility
    private static volatile int BASE_MAX_OBJECT_REFERENCES = 100000000;      // 100M objects max
    private static volatile int BASE_MAX_REFERENCE_CHAIN_DEPTH = 100000;     // 100K chain depth max
    private static volatile int BASE_MAX_ENUM_NAME_LENGTH = 1024;            // 1024 chars max
    
    // Base permanent JsonParser-specific security limits - default to backward compatible values
    private static volatile long BASE_MAX_ID_VALUE = 1000000000L;            // ±1B ID range max
    private static volatile int BASE_STRING_BUFFER_SIZE = 256;               // 256 chars initial capacity

    // Base permanent MetaUtils-specific security limits - default to backward compatible values
    private static volatile int BASE_MAX_ALLOWED_LENGTH = 65536;             // 64KB max allowed length
    private static volatile int BASE_MAX_FILE_CONTENT_SIZE = 1048576;        // 1MB max file content size
    private static volatile int BASE_MAX_LINE_COUNT = 10000;                 // 10K max line count
    private static volatile int BASE_MAX_LINE_LENGTH = 8192;                 // 8KB max line length
    
    // JsonValue-specific permanent security limits (JVM lifetime defaults)
    private static volatile int BASE_MAX_TYPE_RESOLUTION_CACHE_SIZE = 1000;  // 1K cache entries max
    
    // Collection/Map Factory-specific permanent security limits - default to backward compatible values
    private static volatile int BASE_DEFAULT_COLLECTION_CAPACITY = 16;      // 16 default collection capacity
    private static volatile float BASE_COLLECTION_LOAD_FACTOR = 0.75f;      // 0.75 load factor for hash collections
    private static volatile int BASE_MIN_COLLECTION_CAPACITY = 16;           // 16 minimum collection capacity
    
    // JsonObject-specific permanent performance limits - default to backward compatible values
    private static volatile int BASE_LINEAR_SEARCH_THRESHOLD = 8;            // 8 linear search threshold
    
    // Core ReadOptions permanent settings - default to backward compatible values
    private static volatile int BASE_MAX_DEPTH = 1000;                       // 1000 max parsing depth
    private static volatile int BASE_LRU_SIZE = 1000;                        // 1000 LRU cache size
    private static volatile boolean BASE_ALLOW_NAN_AND_INFINITY = false;     // false allow NaN/Infinity
    private static volatile boolean BASE_FAIL_ON_UNKNOWN_TYPE = true;        // true fail on unknown type
    private static volatile boolean BASE_CLOSE_STREAM = true;                // true close stream after parsing
    
    // Cache of fields used for accessors. Controlled by ignoredFields
    private static final Map<Class<?>, Map<String, Field>> classMetaCache = new ClassValueMap<>();
    private static final Map<Class<?>, Map<String, Injector>> injectorsCache = new ClassValueMap<>();

    private final static ReadOptions defReadOptions;
    private final DefaultReadOptions options;

    static {
        // ClassFactories
        loadBaseClassFactory();
        loadBaseReaders();
        loadBaseAliasMappings(ReadOptionsBuilder::addPermanentAlias);
        loadBaseCoercedTypes();
        loadBaseNonRefs();
        loadBaseNotCustomReadClasses();
        loadBaseFieldsNotImported();
        loadBaseNonStandardSetters();

        defReadOptions = new ReadOptionsBuilder().build();
    }

    /**
     * @return Default ReadOptions - no construction needed, unmodifiable.
     */
    public static ReadOptions getDefaultReadOptions() {
        return defReadOptions;
    }
    
    /**
     * Start with default options
     */
    public ReadOptionsBuilder() {
        options = new DefaultReadOptions();

        // Direct copy (with swap) without classForName() lookups for speed.
        // (The classForName check is done with the BASE_ALIAS_MAPPINGS are loaded)
        BASE_ALIAS_MAPPINGS.forEach((srcType, alias) -> {
            options.aliasTypeNames.put(alias, srcType);
        });

        addInjectorFactory(new MethodInjectorFactory());

        addFieldFilter(new StaticFieldFilter());
        addFieldFilter(new EnumFieldFilter());

        options.coercedTypes.putAll(BASE_COERCED_TYPES);
        options.customReaderClasses.putAll(BASE_READERS);
        options.classFactoryMap.putAll(BASE_CLASS_FACTORIES);
        options.nonRefClasses.addAll(BASE_NON_REFS);
        options.notCustomReadClasses.addAll(BASE_NOT_CUSTOM_READ);
        options.excludedFieldNames.putAll(WriteOptionsBuilder.BASE_EXCLUDED_FIELD_NAMES);
        options.fieldsNotImported.putAll(BASE_NOT_IMPORTED_FIELDS);
        
        // Copy base permanent security limits
        options.maxUnresolvedReferences = BASE_MAX_UNRESOLVED_REFERENCES;
        options.maxStackDepth = BASE_MAX_STACK_DEPTH;
        options.maxMapsToRehash = BASE_MAX_MAPS_TO_REHASH;
        options.maxMissingFields = BASE_MAX_MISSING_FIELDS;
        
        // Copy base permanent JSON parsing security limits
        options.maxObjectReferences = BASE_MAX_OBJECT_REFERENCES;
        options.maxReferenceChainDepth = BASE_MAX_REFERENCE_CHAIN_DEPTH;
        options.maxEnumNameLength = BASE_MAX_ENUM_NAME_LENGTH;
        
        // Copy base permanent JsonParser-specific security limits
        options.maxIdValue = BASE_MAX_ID_VALUE;
        options.stringBufferSize = BASE_STRING_BUFFER_SIZE;

        // Copy base permanent MetaUtils-specific security limits
        options.maxAllowedLength = BASE_MAX_ALLOWED_LENGTH;
        options.maxFileContentSize = BASE_MAX_FILE_CONTENT_SIZE;
        options.maxLineCount = BASE_MAX_LINE_COUNT;
        options.maxLineLength = BASE_MAX_LINE_LENGTH;
        
        // Copy base permanent JsonValue-specific security limits
        options.maxTypeResolutionCacheSize = BASE_MAX_TYPE_RESOLUTION_CACHE_SIZE;
        
        // Copy base permanent Collection/Map Factory-specific security limits
        options.defaultCollectionCapacity = BASE_DEFAULT_COLLECTION_CAPACITY;
        options.collectionLoadFactor = BASE_COLLECTION_LOAD_FACTOR;
        options.minCollectionCapacity = BASE_MIN_COLLECTION_CAPACITY;
        
        // Copy base permanent JsonObject-specific performance limits
        options.linearSearchThreshold = BASE_LINEAR_SEARCH_THRESHOLD;
        
        // Copy base permanent core ReadOptions settings
        options.maxDepth = BASE_MAX_DEPTH;
        options.lruSize = BASE_LRU_SIZE;
        options.allowNanAndInfinity = BASE_ALLOW_NAN_AND_INFINITY;
        options.failOnUnknownType = BASE_FAIL_ON_UNKNOWN_TYPE;
        options.closeStream = BASE_CLOSE_STREAM;
    }

    /**
     * Start with a copy of another ReadOptions.  If copy is null, then you get the default options.
     */
    public ReadOptionsBuilder(ReadOptions copy) {
        this(); // initialize to empty
        if (copy != null) {
            DefaultReadOptions other = (DefaultReadOptions) copy;

            // Pointing this ReadOptions.converterOptions to the other ReadOptions.converterOptions
            // is OK, as we are not (yet) changing any of the values. However, if we decide to override
            // one of the ConverterOptions settings, then we must deep-copy the ConverterOptions.
            options.converterOptions = other.converterOptions;

            // Copy simple settings
            options.allowNanAndInfinity = other.allowNanAndInfinity;
            options.closeStream = other.closeStream;
            options.failOnUnknownType = other.failOnUnknownType;
            options.maxDepth = other.maxDepth;
            options.lruSize = other.lruSize;
            options.returnType = other.returnType;
            options.unknownTypeClass = other.unknownTypeClass;
            options.missingFieldHandler = other.missingFieldHandler;
            options.decimalType = other.decimalType;
            options.integerType = other.integerType;
            options.useUnsafe = other.useUnsafe;
            options.strictJson = other.strictJson;

            // Copy security limits
            options.maxUnresolvedReferences = other.maxUnresolvedReferences;
            options.maxStackDepth = other.maxStackDepth;
            options.maxMapsToRehash = other.maxMapsToRehash;
            options.maxMissingFields = other.maxMissingFields;
            
            // Copy JSON parsing security limits
            options.maxObjectReferences = other.maxObjectReferences;
            options.maxReferenceChainDepth = other.maxReferenceChainDepth;
            options.maxEnumNameLength = other.maxEnumNameLength;
            
            // Copy JsonParser-specific security limits
            options.maxIdValue = other.maxIdValue;
            options.stringBufferSize = other.stringBufferSize;

            // Copy MetaUtils-specific security limits
            options.maxAllowedLength = other.maxAllowedLength;
            options.maxFileContentSize = other.maxFileContentSize;
            options.maxLineCount = other.maxLineCount;
            options.maxLineLength = other.maxLineLength;
            
            // Copy JsonValue-specific security limits
            options.maxTypeResolutionCacheSize = other.maxTypeResolutionCacheSize;
            
            // Copy Collection/Map Factory-specific security limits
            options.defaultCollectionCapacity = other.defaultCollectionCapacity;
            options.collectionLoadFactor = other.collectionLoadFactor;
            options.minCollectionCapacity = other.minCollectionCapacity;
            
            // Copy JsonObject-specific performance limits
            options.linearSearchThreshold = other.linearSearchThreshold;

            // Copy complex settings
            options.aliasTypeNames.clear();
            options.aliasTypeNames.putAll(other.aliasTypeNames);

            options.injectorFactories.clear();
            options.injectorFactories.addAll(other.injectorFactories);

            options.fieldFilters.clear();
            options.fieldFilters.addAll(other.fieldFilters);

            options.excludedFieldNames.clear();
            options.excludedFieldNames.putAll(other.excludedFieldNames);

            options.fieldsNotImported.clear();
            options.fieldsNotImported.putAll(other.fieldsNotImported);

            options.coercedTypes.clear();
            options.coercedTypes.putAll(other.coercedTypes);

            options.customReaderClasses.clear();
            options.customReaderClasses.putAll(other.customReaderClasses);

            options.notCustomReadClasses.clear();
            options.notCustomReadClasses.addAll(other.notCustomReadClasses);

            options.classFactoryMap.clear();
            options.classFactoryMap.putAll(other.classFactoryMap);

            options.nonRefClasses.clear();
            options.nonRefClasses.addAll(other.nonRefClasses);
        }
    }

    /**
     * Call this method to add a factory class that will be used to create instances of another class.  This is useful
     * when you have a class that `json-io` cannot instantiate due to private or other constructor issues.  Add
     * a ClassFactory class (that you implement) and associate it to the passed in 'classToCreate'.  Your
     * ClassFactory class will be called with the Map (JsonObject) that contains the { ... } that should
     * be loaded into your class. It is your ClassFactory implementations job to create the instance of the associated
     * class, and then copy the values from the passed in Map to the Java instance you created.
     * @param sourceClass String class name (fully qualified name) to associate to your ClassFactory implementation.
     * @param factory     ClassFactory your ClassFactory implementation that creates/loads the associated
     *                    class using the Map (JsonObject) of data passed to it.
     */
    public static void addPermanentClassFactory(Class<?> sourceClass, ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(sourceClass, factory);
    }

    /**
     * @param classToCreate Class that will need to use a ClassFactory for instantiation and loading.
     * @param factory       ClassFactory instance that has been created to instantiate a difficult
     *                      to create and load class (class c).
     * @deprecated use {@link ReadOptionsBuilder#addPermanentClassFactory} instead.
     */
    @Deprecated
    public static void assignInstantiator(Class<?> classToCreate, ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(classToCreate, factory);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another during instance creation.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     *
     * @param sourceClass      String class name (fully qualified name) that will be coerced to another type.
     * @param destinationClass Class to coerce to.  For example, java.util.Collections$EmptyList to java.util.ArrayList.
     */
    public static void addPermanentCoercedType(Class<?> sourceClass, Class<?> destinationClass) {
        BASE_COERCED_TYPES.put(sourceClass, destinationClass);
    }

    /**
     * Call this method to add a permanent (JVM lifetime) alias of a class to an often shorter, name.
     *
     * @param sourceClass String class name (fully qualified name) that will be aliased by a shorter name in the JSON.
     * @param alias       Shorter alias name, for example, "ArrayList" as opposed to "java.util.ArrayList"
     */
    public static void addPermanentAlias(Class<?> sourceClass, String alias) {
        BASE_ALIAS_MAPPINGS.put(sourceClass.getName(), alias);
    }

    /**
     * Call this method to remove alias patterns from the ReadsOptionsBuilder. Be careful doing so. In a micro-services
     * environment, you want the receiving services to know as many substitutions as possible. It is important to control
     * the aliases on the "written" side, to ensure that written JSON does not include aliases that the receiving
     * micro-services do not yet 'understand.' Therefore, you should rarely need this API.<br>
     * <br>
     * This API matches your wildcard pattern of *, ?, and characters against class names in its cache. It removes the
     * entries (className to aliasName) from the cache to prevent the resultant classes from being aliased during output.
     *
     * @param classNamePattern String pattern to match class names. This String matches using a wild-card
     * pattern, where * matches anything and ? matches one character. As many * or ? can be used as needed.
     */
    public static void removePermanentAliasTypeNamesMatching(String classNamePattern) {
        String regex = StringUtilities.wildcardToRegexString(classNamePattern);
        Pattern pattern = RegexUtilities.getCachedPattern(regex);
        if (pattern != null) {
            BASE_ALIAS_MAPPINGS.values().removeIf(value -> RegexUtilities.safeMatches(pattern, value));
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
    public static void addPermanentReader(Class<?> c, JsonClassReader reader) {
        BASE_READERS.put(c, reader);
    }

    /**
     * Add a class permanently (JVM lifecycle) to the Non-referenceable list. All new ReadOptions instances created
     * will automatically start with this, and you will not need to add it into the ReadOptions through the
     * ReadOptionsBuilder each time.
     *
     * @param clazz Class to be added to the non-reference list.  A class that is on this list, will be written
     *              out to the JSON fully, each time it is encountered, and never be written with an @ref.  Use
     *              for very simple, atomic type (single field classes).
     */
    public static void addPermanentNonReferenceableClass(Class<?> clazz) {
        BASE_NON_REFS.add(clazz);
    }

    /**
     * Add a class permanently (JVM lifecycle) to the Not-Custom reader list. All new ReadOptions instances created
     * will automatically start with this, and you will not need to add it into the ReadOptions through the
     * ReadOptionsBuilder each time.
     *
     * @param clazz Class to be added to the Not-Custom reader list.  A class that is on this list, will not use
     *              a Custom Reader or Class Factory when read from the JSON.
     */
    public static void addPermanentNotCustomReadClass(Class<?> clazz) {
        BASE_NOT_CUSTOM_READ.add(clazz);
    }

    /**
     * Add a field to a class that should not be imported. Any class's field added here will be excluded when read from
     * the JSON.  The value will not be set (injected) into the associated Java instance.
     * @param clazz Class on which fields will be excluded.
     * @param fieldName String field name to exclude for the given class.
     */
    public static void addPermanentNotImportedField(Class<?> clazz, String fieldName) {
        BASE_NOT_IMPORTED_FIELDS.computeIfAbsent(clazz, k -> ConcurrentHashMap.newKeySet()).add(fieldName);
    }

    /**
     * Add the String name of the "setter" (injector) associated to the field name. mappings for the passed in Class.
     * @param clazz Class to which the association will be added.
     * @param fieldName String name of field that has a non-standard setter (injector)
     * @param alias String name of setter method that is used to write to the fieldName. An example is Throwable's
     *              initCause() setter which does not follow a standard naming convention (should be 'setCause()'). In
     *              this example, use addPermanentNonStandardSetter(Throwable.class, "cause", "initCause").
     */
    public static void addPermanentNonStandardSetter(Class<?> clazz, String fieldName, String alias) {
        BASE_NONSTANDARD_SETTERS.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>()).put(fieldName, alias);
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum number of unresolved references allowed during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxUnresolvedReferences int maximum number of unresolved references allowed. Set this to prevent 
     *                                memory exhaustion from DoS attacks via unbounded forward references.
     *                                Use Integer.MAX_VALUE for unlimited (backward compatible default).
     */
    public static void addPermanentMaxUnresolvedReferences(int maxUnresolvedReferences) {
        BASE_MAX_UNRESOLVED_REFERENCES = maxUnresolvedReferences;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum traversal stack depth allowed during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxStackDepth int maximum traversal stack depth allowed. Set this to prevent stack overflow
     *                      attacks via deeply nested structures. Use Integer.MAX_VALUE for unlimited 
     *                      (backward compatible default).
     */
    public static void addPermanentMaxStackDepth(int maxStackDepth) {
        BASE_MAX_STACK_DEPTH = maxStackDepth;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum number of maps that can be queued for rehashing during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxMapsToRehash int maximum number of maps that can be queued for rehashing. Set this to prevent
     *                        memory exhaustion from DoS attacks via excessive map creation. Use Integer.MAX_VALUE
     *                        for unlimited (backward compatible default).
     */
    public static void addPermanentMaxMapsToRehash(int maxMapsToRehash) {
        BASE_MAX_MAPS_TO_REHASH = maxMapsToRehash;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum number of missing fields that can be tracked during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxMissingFields int maximum number of missing fields that can be tracked. Set this to prevent
     *                         memory exhaustion from DoS attacks via excessive missing field tracking. 
     *                         Use Integer.MAX_VALUE for unlimited (backward compatible default).
     */
    public static void addPermanentMaxMissingFields(int maxMissingFields) {
        BASE_MAX_MISSING_FIELDS = maxMissingFields;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum number of object references that can be tracked during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxObjectReferences int maximum number of object references that can be tracked. Set this to prevent
     *                            memory exhaustion from DoS attacks via unbounded object reference growth.
     *                            Use 10,000,000 for backward compatible default.
     */
    public static void addPermanentMaxObjectReferences(int maxObjectReferences) {
        BASE_MAX_OBJECT_REFERENCES = maxObjectReferences;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum depth of reference chains that can be traversed during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxReferenceChainDepth int maximum depth of reference chains that can be traversed. Set this to prevent
     *                               infinite loops from circular reference attacks. Use 10,000 for backward
     *                               compatible default.
     */
    public static void addPermanentMaxReferenceChainDepth(int maxReferenceChainDepth) {
        BASE_MAX_REFERENCE_CHAIN_DEPTH = maxReferenceChainDepth;
    }
    
    /**
     * Set a permanent (JVM lifecycle) maximum length of enum name strings during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxEnumNameLength int maximum length of enum name strings. Set this to prevent memory exhaustion
     *                          from DoS attacks via excessively long enum names. Use 256 for backward 
     *                          compatible default.
     */
    public static void addPermanentMaxEnumNameLength(int maxEnumNameLength) {
        BASE_MAX_ENUM_NAME_LENGTH = maxEnumNameLength;
    }

    /**
     * Set a permanent (JVM lifecycle) maximum absolute value for @id and @ref values during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxIdValue long maximum absolute value for ID values. Set this to prevent issues with extreme ID values
     *                   that could cause problems. Use 1,000,000,000 for backward compatible default.
     */
    public static void addPermanentMaxIdValue(long maxIdValue) {
        BASE_MAX_ID_VALUE = maxIdValue;
    }

    /**
     * Set a permanent (JVM lifecycle) initial capacity for string parsing buffers during JSON processing.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param stringBufferSize int initial capacity for string parsing buffers. This affects memory allocation for
     *                         string parsing operations. Use 256 for backward compatible default.
     */
    public static void addPermanentStringBufferSize(int stringBufferSize) {
        BASE_STRING_BUFFER_SIZE = stringBufferSize;
    }

    /**
     * Set a permanent (JVM lifecycle) maximum allowed length for argument character processing in MetaUtils.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxAllowedLength int maximum allowed length for argument processing. Set this to prevent memory exhaustion
     *                         when processing large arguments. Use 65536 (64KB) for backward compatible default.
     */
    public static void addPermanentMaxAllowedLength(int maxAllowedLength) {
        BASE_MAX_ALLOWED_LENGTH = maxAllowedLength;
    }

    /**
     * Set a permanent (JVM lifecycle) maximum file content size when loading resource files in MetaUtils.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxFileContentSize int maximum file content size in bytes. Set this to prevent memory exhaustion from
     *                           oversized resource files. Use 1048576 (1MB) for backward compatible default.
     */
    public static void addPermanentMaxFileContentSize(int maxFileContentSize) {
        BASE_MAX_FILE_CONTENT_SIZE = maxFileContentSize;
    }

    /**
     * Set a permanent (JVM lifecycle) maximum number of lines when processing resource files in MetaUtils.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxLineCount int maximum number of lines. Set this to prevent resource exhaustion from files with
     *                     excessive line counts. Use 10000 for backward compatible default.
     */
    public static void addPermanentMaxLineCount(int maxLineCount) {
        BASE_MAX_LINE_COUNT = maxLineCount;
    }

    /**
     * Set a permanent (JVM lifecycle) maximum length per line when processing resource files in MetaUtils.
     * All new ReadOptions instances created will automatically start with this setting, preventing the need to
     * configure it for each ReadOptions instance.
     * 
     * @param maxLineLength int maximum length per line. Set this to prevent memory exhaustion from excessively
     *                      long lines. Use 8192 (8KB) for backward compatible default.
     */
    public static void addPermanentMaxLineLength(int maxLineLength) {
        BASE_MAX_LINE_LENGTH = maxLineLength;
    }

    /**
     * Call this method to set a permanent (JVM lifetime) maximum type resolution cache size for JsonValue.
     * All ReadOptions instances will be initialized with this value unless explicitly overridden.
     * 
     * @param maxTypeResolutionCacheSize int maximum size of the type resolution cache used by JsonValue.
     *                                   Must be at least 1. Default is 1,000.
     */
    public static void addPermanentMaxTypeResolutionCacheSize(int maxTypeResolutionCacheSize) {
        if (maxTypeResolutionCacheSize < 1) {
            throw new JsonIoException("maxTypeResolutionCacheSize must be at least 1, value: " + maxTypeResolutionCacheSize);
        }
        BASE_MAX_TYPE_RESOLUTION_CACHE_SIZE = maxTypeResolutionCacheSize;
        // Apply to JsonValue's static cache immediately
        JsonValue.setMaxTypeResolutionCacheSize(maxTypeResolutionCacheSize);
    }

    /**
     * All new ReadOptions instances will use this default collection capacity.
     * @param defaultCollectionCapacity int default initial capacity for collections during JSON processing.
     *                                  Default is 16 for backward compatibility.
     */
    public static void addPermanentDefaultCollectionCapacity(int defaultCollectionCapacity) {
        if (defaultCollectionCapacity < 1) {
            throw new JsonIoException("defaultCollectionCapacity must be at least 1, value: " + defaultCollectionCapacity);
        }
        BASE_DEFAULT_COLLECTION_CAPACITY = defaultCollectionCapacity;
    }

    /**
     * All new ReadOptions instances will use this collection load factor.
     * @param collectionLoadFactor float load factor for hash-based collections during JSON processing.
     *                             Default is 0.75f for backward compatibility.
     */
    public static void addPermanentCollectionLoadFactor(float collectionLoadFactor) {
        if (collectionLoadFactor <= 0.0f || collectionLoadFactor >= 1.0f) {
            throw new JsonIoException("collectionLoadFactor must be between 0.0 and 1.0 (exclusive), value: " + collectionLoadFactor);
        }
        BASE_COLLECTION_LOAD_FACTOR = collectionLoadFactor;
    }

    /**
     * All new ReadOptions instances will use this minimum collection capacity.
     * @param minCollectionCapacity int minimum collection capacity for collections during JSON processing.
     *                              Default is 16 for backward compatibility.
     */
    public static void addPermanentMinCollectionCapacity(int minCollectionCapacity) {
        if (minCollectionCapacity < 1) {
            throw new JsonIoException("minCollectionCapacity must be at least 1, value: " + minCollectionCapacity);
        }
        BASE_MIN_COLLECTION_CAPACITY = minCollectionCapacity;
    }

    /**
     * All new ReadOptions instances will use this linear search threshold.
     * @param linearSearchThreshold int threshold for switching between linear and binary search in JsonObject operations.
     *                               Default is 8 for backward compatibility.
     */
    public static void addPermanentLinearSearchThreshold(int linearSearchThreshold) {
        if (linearSearchThreshold < 1) {
            throw new JsonIoException("linearSearchThreshold must be at least 1, value: " + linearSearchThreshold);
        }
        BASE_LINEAR_SEARCH_THRESHOLD = linearSearchThreshold;
        // Apply to JsonObject's static configuration immediately
        JsonObject.setLinearSearchThreshold(linearSearchThreshold);
    }

    /**
     * All new ReadOptions instances will use this maximum parsing depth.
     * @param maxDepth int maximum depth for JSON parsing to prevent stack overflow attacks.
     *                 Default is 1000 for backward compatibility.
     */
    public static void addPermanentMaxDepth(int maxDepth) {
        if (maxDepth < 1) {
            throw new JsonIoException("maxDepth must be at least 1, value: " + maxDepth);
        }
        BASE_MAX_DEPTH = maxDepth;
    }

    /**
     * All new ReadOptions instances will use this LRU cache size.
     * @param lruSize int size of the LRU cache for object references during JSON processing.
     *                Default is 500 for backward compatibility.
     */
    public static void addPermanentLruSize(int lruSize) {
        if (lruSize < 1) {
            throw new JsonIoException("lruSize must be at least 1, value: " + lruSize);
        }
        BASE_LRU_SIZE = lruSize;
    }

    /**
     * All new ReadOptions instances will use this NaN and Infinity handling setting.
     * @param allowNanAndInfinity boolean whether to allow NaN and Infinity values in JSON parsing.
     *                            Default is true for backward compatibility.
     */
    public static void addPermanentAllowNanAndInfinity(boolean allowNanAndInfinity) {
        BASE_ALLOW_NAN_AND_INFINITY = allowNanAndInfinity;
    }

    /**
     * All new ReadOptions instances will use this unknown type handling setting.
     * @param failOnUnknownType boolean whether to fail when encountering unknown types in JSON.
     *                          Default is false for backward compatibility.
     */
    public static void addPermanentFailOnUnknownType(boolean failOnUnknownType) {
        BASE_FAIL_ON_UNKNOWN_TYPE = failOnUnknownType;
    }

    /**
     * All new ReadOptions instances will use this stream closing setting.
     * @param closeStream boolean whether to close input streams after JSON parsing.
     *                    Default is true for backward compatibility.
     */
    public static void addPermanentCloseStream(boolean closeStream) {
        BASE_CLOSE_STREAM = closeStream;
    }
    
    /**
     * @return ReadOptions - built options
     */
    public ReadOptions build() {
        options.clearCaches();
        
        // Apply the type resolution cache size to JsonValue's static cache
        JsonValue.setMaxTypeResolutionCacheSize(options.maxTypeResolutionCacheSize);
        
        // Apply the linear search threshold to JsonObject's static configuration
        JsonObject.setLinearSearchThreshold(options.linearSearchThreshold);
        
        options.aliasTypeNames = Collections.unmodifiableMap(options.aliasTypeNames);
        options.coercedTypes = ((ClassValueMap<Class<?>>)options.coercedTypes).unmodifiableView();
        options.notCustomReadClasses = ((ClassValueSet)options.notCustomReadClasses).unmodifiableView();
        options.customReaderClasses = ((ClassValueMap<JsonClassReader>)options.customReaderClasses).unmodifiableView();
        options.classFactoryMap = ((ClassValueMap<ClassFactory>)options.classFactoryMap).unmodifiableView();
        options.nonRefClasses = ((ClassValueSet)options.nonRefClasses).unmodifiableView();
        options.converterOptions.converterOverrides = Collections.unmodifiableMap(options.converterOptions.converterOverrides);
        options.converterOptions.customOptions = Collections.unmodifiableMap(options.converterOptions.customOptions);
        options.excludedFieldNames = ((ClassValueMap<Set<String>>)options.excludedFieldNames).unmodifiableView();
        options.fieldsNotImported = ((ClassValueMap<Set<String>>)options.fieldsNotImported).unmodifiableView();
        options.fieldFilters = Collections.unmodifiableList(options.fieldFilters);
        options.injectorFactories = Collections.unmodifiableList(options.injectorFactories);
        options.customOptions = Collections.unmodifiableMap(options.customOptions);
        return options;
    }

    /**
     * Add a class to the injector factory list - allows to add injector to the class
     *
     * @param factory Class to add to the injector factory list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addInjectorFactory(InjectorFactory factory) {
        options.injectorFactories.add(factory);
        return this;
    }

    /**
     * Add a class to the field filter list - allows adding more field filter types
     *
     * @param filter FieldFilter to add
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addFieldFilter(FieldFilter filter) {
        options.fieldFilters.add(filter);
        return this;
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom reader (that could happen through inheritance).
     *
     * @param notCustomClass Class to add to the not-customized list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNotCustomReaderClass(Class<?> notCustomClass) {
        options.notCustomReadClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                         will be dropped and this Collection will establish the new list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceNotCustomReaderClasses(Collection<? extends Class<?>> notCustomClasses) {
        options.notCustomReadClasses.clear();
        options.notCustomReadClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @param source             source class.
     * @param target             target class.
     * @param conversionFunction functional interface to run Conversion.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addConverterOverride(Class<?> source, Class<?> target, Convert<?> conversionFunction) {
        source = ClassUtilities.toPrimitiveWrapperClass(source);
        target = ClassUtilities.toPrimitiveWrapperClass(target);
        options.converterOptions.converterOverrides.put(Converter.pair(source, target), conversionFunction);
        return this;
    }

    /**
     * @param customReaderClasses Map of Class to JsonClassReader.  Establish the passed in Map as the
     *                            established Map of custom readers to be used when reading JSON. Using this method
     *                            more than once, will set the custom readers to only the values from the Set in
     *                            the last call made.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceCustomReaderClasses(Map<? extends Class<?>, ? extends JsonClassReader> customReaderClasses) {
        options.customReaderClasses.clear();
        options.customReaderClasses.putAll(customReaderClasses);
        return this;
    }

    /**
     * @param clazz        Class to add a custom reader for.
     * @param customReader JsonClassReader to use when the passed in Class is encountered during serialization.
     *                     Custom readers are passed an empty instance.  Use a ClassFactory if you want to instantiate
     *                     and load the class in one step.
     * @return ReadOptionsBuilder for chained access.
     */
    public <T> ReadOptionsBuilder addCustomReaderClass(Class<T> clazz, JsonClassReader<? super T> customReader) {
        options.customReaderClasses.put(clazz, customReader);
        return this;
    }

    /**
     * Associate multiple ClassFactory instances to Classes that needs help being constructed and read in.
     *
     * @param factories Map of entries that are Class to Factory. The Factory class knows how to instantiate
     *                  and load the class associated to it.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceClassFactories(Map<? extends Class<?>, ? extends ClassFactory> factories) {
        options.classFactoryMap.clear();
        options.classFactoryMap.putAll(factories);
        return this;
    }

    /**
     * Associate a ClassFactory to a Class that needs help being constructed and read in.
     *
     * @param clazz   Class that is difficult to instantiate.
     * @param factory Class written to instantiate the 'clazz' and load it's values.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addClassFactory(Class<?> clazz, ClassFactory factory) {
        options.classFactoryMap.put(clazz, factory);
        return this;
    }

    /**
     * Set to return as JSON_OBJECTS's (faster, useful for large, more simple object data sets). This returns as
     * native json types (Map, Object[], long, double, boolean)
     * @deprecated use {@link ReadOptionsBuilder#returnAsJsonObjects()} instead.
     */
    @Deprecated
    public ReadOptionsBuilder returnAsNativeJsonObjects() {
        options.returnType = ReadOptions.ReturnType.JSON_OBJECTS;
        return this;
    }

    /**
     * Set to return as JSON_OBJECTS's (faster, useful for large, more simple object data sets). This returns as
     * native json types (Map, Object[], long, double, boolean).
     * <p>
     * This method automatically sets {@code failOnUnknownType(false)} because the Map-of-Maps representation
     * is designed to work without requiring classes on the classpath. This allows json-io to parse any JSON
     * structure (including JSON with unknown @type entries) and return it as a traversable Map graph.
     * <p>
     * If you need strict type validation even in Map mode, explicitly call {@code .failOnUnknownType(true)}
     * after this method.
     */
    public ReadOptionsBuilder returnAsJsonObjects() {
        options.returnType = ReadOptions.ReturnType.JSON_OBJECTS;
        options.failOnUnknownType = false;  // Auto-enable for Map mode - parse any JSON without class dependencies
        return this;
    }

    /**
     * Return as JAVA_OBJECT's the returned value will be of the class type passed into JsonIo.toJava(json, rootClass).
     * This mode is good for cloning exact objects.
     */
    public ReadOptionsBuilder returnAsJavaObjects() {
        options.returnType = ReadOptions.ReturnType.JAVA_OBJECTS;
        return this;
    }

    /**
     * Set Floating Point types to be returned as Doubles.  This is the default.
     */
    public ReadOptionsBuilder floatPointDouble() {
        options.decimalType = ReadOptions.Decimals.DOUBLE;
        return this;
    }

    /**
     * Set Floating Point types to be returned as BigDecimals.
     */
    public ReadOptionsBuilder floatPointBigDecimal() {
        options.decimalType = ReadOptions.Decimals.BIG_DECIMAL;
        return this;
    }

    /**
     * Set Floating Point types to be returned as either Doubles or BigDecimals, depending on precision required to
     * hold the number sourced from JSON.
     */
    public ReadOptionsBuilder floatPointBoth() {
        options.decimalType = ReadOptions.Decimals.BOTH;
        return this;
    }

    /**
     * Set Integer Types to be returned as Longs.  This is the default.
     */
    public ReadOptionsBuilder integerTypeLong() {
        options.integerType = ReadOptions.Integers.LONG;
        return this;
    }

    /**
     * Set Integer Types to always be returned as BigIntegers, regardless of size.
     */
    public ReadOptionsBuilder integerTypeBigInteger() {
        options.integerType = ReadOptions.Integers.BIG_INTEGER;
        return this;
    }

    /**
     * Set Integer Types to be returned as either Longs or BigIntegers, depending on precision required to
     * hold the number sourced from JSON.
     */
    public ReadOptionsBuilder integerTypeBoth() {
        options.integerType = ReadOptions.Integers.BOTH;
        return this;
    }

    /**
     * @param classLoader ClassLoader to use when reading JSON to resolve String named classes.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder classLoader(ClassLoader classLoader) {
        Convention.throwIfNull(classLoader, "classloader cannot be null");
        options.converterOptions.classloader = classLoader;
        return this;
    }

    /**
     * @param fail boolean indicating whether we should fail on unknown type encountered.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder failOnUnknownType(boolean fail) {
        options.failOnUnknownType = fail;
        return this;
    }

    /**
     * Set a class to use when the JSON reader cannot instantiate a class. When that happens, it will throw
     * a JsonIoException() if no 'unknownTypeClass' is set and failOnUnknownType is true. If failOnUnknownType
     * is false and unknownTypeClass is null, it will create a JsonObject instance. Otherwise it will create 
     * an instance of the class specified as the 'unknownTypeClass.' A common one to use is java.util.LinkedHashMap.
     *
     * @param c Class to instantiate when a String specified class in the JSON cannot be instantiated. Set to null
     *          to use the default JsonObject. Common alternatives include LinkedHashMap.class or HashMap.class.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder unknownTypeClass(Class<?> c) {
        options.unknownTypeClass = c;
        return this;
    }

    /**
     * @param closeStream boolean set to 'true' to have JsonIo close the InputStream when it is finished reading
     *                    from it.  The default is 'true'.  If false, the InputStream will not be closed, allowing
     *                    you to continue reading further.  Example, NDJSON that has new line eliminated JSON
     *                    objects repeated.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder closeStream(boolean closeStream) {
        options.closeStream = closeStream;
        return this;
    }

    /**
     * @param maxDepth int maximum of the depth that JSON Objects {...} can be nested.  Set this to prevent
     *                 security risk from StackOverflow attack vectors.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxDepth(int maxDepth) {
        options.maxDepth = maxDepth;
        return this;
    }

    /**
     * @param maxUnresolvedReferences int maximum number of unresolved references allowed during JSON processing.
     *                                Set this to prevent memory exhaustion from DoS attacks via unbounded 
     *                                forward references. Default is Integer.MAX_VALUE (unlimited) for 
     *                                backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxUnresolvedReferences(int maxUnresolvedReferences) {
        options.maxUnresolvedReferences = maxUnresolvedReferences;
        return this;
    }

    /**
     * @param maxStackDepth int maximum traversal stack depth allowed during JSON processing.
     *                      Set this to prevent stack overflow attacks via deeply nested structures.
     *                      Default is Integer.MAX_VALUE (unlimited) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxStackDepth(int maxStackDepth) {
        options.maxStackDepth = maxStackDepth;
        return this;
    }

    /**
     * @param maxMapsToRehash int maximum number of maps that can be queued for rehashing during JSON processing.
     *                        Set this to prevent memory exhaustion from DoS attacks via excessive map creation.
     *                        Default is Integer.MAX_VALUE (unlimited) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxMapsToRehash(int maxMapsToRehash) {
        options.maxMapsToRehash = maxMapsToRehash;
        return this;
    }

    /**
     * @param maxMissingFields int maximum number of missing fields that can be tracked during JSON processing.
     *                         Set this to prevent memory exhaustion from DoS attacks via excessive missing 
     *                         field tracking. Default is Integer.MAX_VALUE (unlimited) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxMissingFields(int maxMissingFields) {
        options.maxMissingFields = maxMissingFields;
        return this;
    }

    /**
     * @param maxObjectReferences int maximum number of object references that can be tracked during JSON processing.
     *                            Set this to prevent memory exhaustion from DoS attacks via unbounded object 
     *                            reference growth. Default is 10,000,000 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxObjectReferences(int maxObjectReferences) {
        options.maxObjectReferences = maxObjectReferences;
        return this;
    }

    /**
     * @param maxReferenceChainDepth int maximum depth of reference chains that can be traversed during JSON processing.
     *                               Set this to prevent infinite loops from circular reference attacks. 
     *                               Default is 10,000 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxReferenceChainDepth(int maxReferenceChainDepth) {
        options.maxReferenceChainDepth = maxReferenceChainDepth;
        return this;
    }

    /**
     * @param maxEnumNameLength int maximum length of enum name strings during JSON processing.
     *                          Set this to prevent memory exhaustion from DoS attacks via excessively long 
     *                          enum names. Default is 256 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxEnumNameLength(int maxEnumNameLength) {
        options.maxEnumNameLength = maxEnumNameLength;
        return this;
    }

    /**
     * @param maxIdValue long maximum absolute value for @id and @ref values during JSON processing.
     *                   Set this to prevent issues with extreme ID values that could cause problems.
     *                   Default is 1,000,000,000 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxIdValue(long maxIdValue) {
        options.maxIdValue = maxIdValue;
        return this;
    }

    /**
     * @param stringBufferSize int initial capacity for string parsing buffers during JSON processing.
     *                         This affects memory allocation for string parsing operations.
     *                         Default is 256 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder stringBufferSize(int stringBufferSize) {
        options.stringBufferSize = stringBufferSize;
        return this;
    }

    /**
     * @param maxAllowedLength int maximum allowed length for argument character processing in MetaUtils.
     *                         Set this to prevent memory exhaustion when processing large arguments.
     *                         Default is 65536 (64KB) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxAllowedLength(int maxAllowedLength) {
        options.maxAllowedLength = maxAllowedLength;
        return this;
    }

    /**
     * @param maxFileContentSize int maximum file content size in bytes when loading resource files in MetaUtils.
     *                           Set this to prevent memory exhaustion from oversized resource files.
     *                           Default is 1048576 (1MB) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxFileContentSize(int maxFileContentSize) {
        options.maxFileContentSize = maxFileContentSize;
        return this;
    }

    /**
     * @param maxLineCount int maximum number of lines when processing resource files in MetaUtils.
     *                     Set this to prevent resource exhaustion from files with excessive line counts.
     *                     Default is 10000 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxLineCount(int maxLineCount) {
        options.maxLineCount = maxLineCount;
        return this;
    }

    /**
     * @param maxLineLength int maximum length per line when processing resource files in MetaUtils.
     *                      Set this to prevent memory exhaustion from excessively long lines.
     *                      Default is 8192 (8KB) for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxLineLength(int maxLineLength) {
        options.maxLineLength = maxLineLength;
        return this;
    }

    /**
     * @param maxTypeResolutionCacheSize int maximum size of the type resolution cache used by JsonValue.
     *                                   Default is 1,000 entries. This prevents unbounded memory growth while maintaining performance benefits.
     *                                   The cache is used to store whether Java Types are fully resolved (no type variables).
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxTypeResolutionCacheSize(int maxTypeResolutionCacheSize) {
        if (maxTypeResolutionCacheSize < 1) {
            throw new JsonIoException("maxTypeResolutionCacheSize must be at least 1, value: " + maxTypeResolutionCacheSize);
        }
        options.maxTypeResolutionCacheSize = maxTypeResolutionCacheSize;
        return this;
    }

    /**
     * @param defaultCollectionCapacity int default initial capacity for collections during JSON processing.
     *                                  This affects memory allocation for collection factory operations.
     *                                  Default is 16 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder defaultCollectionCapacity(int defaultCollectionCapacity) {
        if (defaultCollectionCapacity < 1) {
            throw new JsonIoException("defaultCollectionCapacity must be at least 1, value: " + defaultCollectionCapacity);
        }
        options.defaultCollectionCapacity = defaultCollectionCapacity;
        return this;
    }

    /**
     * @param collectionLoadFactor float load factor for hash-based collections during JSON processing.
     *                             This affects hash collection performance and memory usage.
     *                             Default is 0.75f for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder collectionLoadFactor(float collectionLoadFactor) {
        if (collectionLoadFactor <= 0.0f || collectionLoadFactor >= 1.0f) {
            throw new JsonIoException("collectionLoadFactor must be between 0.0 and 1.0 (exclusive), value: " + collectionLoadFactor);
        }
        options.collectionLoadFactor = collectionLoadFactor;
        return this;
    }

    /**
     * @param minCollectionCapacity int minimum collection capacity for collections during JSON processing.
     *                              This sets a lower bound on collection sizes for performance reasons.
     *                              Default is 16 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder minCollectionCapacity(int minCollectionCapacity) {
        if (minCollectionCapacity < 1) {
            throw new JsonIoException("minCollectionCapacity must be at least 1, value: " + minCollectionCapacity);
        }
        options.minCollectionCapacity = minCollectionCapacity;
        return this;
    }

    /**
     * @param linearSearchThreshold int threshold for switching between linear and binary search in JsonObject operations.
     *                               For arrays with {@code size <= this threshold}, linear search is used for better cache locality.
     *                               Default is 8 for backward compatibility.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder linearSearchThreshold(int linearSearchThreshold) {
        if (linearSearchThreshold < 1) {
            throw new JsonIoException("linearSearchThreshold must be at least 1, value: " + linearSearchThreshold);
        }
        options.linearSearchThreshold = linearSearchThreshold;
        return this;
    }

    /**
     * @param lruSize int maximum size of the LRU cache that holds reflective information like fields of a Class and
     *                injectors associated to a class.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder lruSize(int lruSize) {
        options.lruSize = lruSize;
        // Not used at the moment, but may be used at a later point
        return this;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow Double and Floats to be
     *                            read in as NaN and +Inf, -Inf [infinity], false and a JsonIoException will be
     *                            thrown if these values are encountered
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder allowNanAndInfinity(boolean allowNanAndInfinity) {
        options.allowNanAndInfinity = allowNanAndInfinity;
        return this;
    }

    /**
     * Enable strict JSON parsing mode (RFC 8259 compliance only).
     * When enabled, JSON5 extensions will cause parse errors:
     * <ul>
     *   <li>Unquoted object keys</li>
     *   <li>Single-quoted strings</li>
     *   <li>Comments (single-line and block)</li>
     *   <li>Trailing commas</li>
     *   <li>Hexadecimal numbers</li>
     *   <li>Leading/trailing decimal points</li>
     *   <li>Positive sign on numbers</li>
     *   <li>Multi-line strings</li>
     * </ul>
     * Default is permissive mode (JSON5 features accepted).
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder strictJson() {
        options.strictJson = true;
        return this;
    }

    /**
     * @param aliasTypeNames Map containing String class names to alias names.  The passed in Map will
     *                       be copied, and be the new baseline settings.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeNames(Map<String, String> aliasTypeNames) {
        aliasTypeNames.forEach(this::addUniqueAlias);
        return this;
    }

    /**
     * @param type  Class to alias
     * @param alias String shorter name to use, typically.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeName(Class<?> type, String alias) {
        addUniqueAlias(type.getName(), alias);
        return this;
    }

    /**
     * @param typeName String class name
     * @param alias    String shorter name to use, typically.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder aliasTypeName(String typeName, String alias) {
        addUniqueAlias(typeName, alias);
        return this;
    }

    /**
     * Remove alias entries from this ReadOptionsBuilder instance where the Java fully qualified string
     * class name matches the passed in wildcard pattern.
     * @param typeNamePattern String pattern to match class names. This String matches using a wild-card
     * pattern, where * matches anything and ? matches one character. As many * or ? can be used as needed.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder removeAliasTypeNameMatching(String typeNamePattern) {
        String regex = StringUtilities.wildcardToRegexString(typeNamePattern);
        Pattern pattern = RegexUtilities.getCachedPattern(regex);
        if (pattern != null) {
            options.aliasTypeNames.values().removeIf(key -> RegexUtilities.safeMatches(pattern, key));
        }
        return this;
    }

    /**
     * @param locale target locale for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setLocale(Locale locale) {
        options.converterOptions.locale = locale;
        return this;
    }

    /**
     * @param charset source charset for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setCharset(Charset charset) {
        options.converterOptions.charset = charset;
        return this;
    }

    /**
     * @param zoneId source charset for conversions
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setZoneId(ZoneId zoneId) {
        options.converterOptions.zoneId = zoneId;
        return this;
    }

    /**
     * @param ch Character used when converting true -> char
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setTrueCharacter(Character ch) {
        options.converterOptions.trueChar = ch;
        return this;
    }

    /**
     * @param ch Character used when converting false -> char
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder setFalseCharacter(Character ch) {
        options.converterOptions.falseChar = ch;
        return this;
    }

    /**
     * Add a custom option, which may be useful when writing custom readers.
     * @param key String name of the custom option
     * @param value Object value of the custom option
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addCustomOption(String key, Object value) {
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
     * Since we are swapping keys/values, we must check for duplicate values (which are now keys).
     *
     * @param type  String fully qualified class name.
     * @param alias String shorter alias name.
     */
    private void addUniqueAlias(String type, String alias) {
        Class<?> clazz = ClassUtilities.forName(type, options.getClassLoader());
        if (clazz == null) {
            LOG.warning("Unknown class: " + type + " cannot be added to the ReadOptions alias map.");
        }
        String existType = options.aliasTypeNames.get(alias);
        if (existType != null) {
            LOG.warning("Non-unique ReadOptions alias: " + alias + " attempted assign to: " + type + ", but is already assigned to: " + existType);
        }
        options.aliasTypeNames.put(alias, type);
    }

    /**
     * Coerce a class from one type (named in the JSON) to another type.  For example, converting
     * "java.util.Collections$SingletonSet" to a LinkedHashSet.class.
     *
     * @param sourceClassName String class name to coerce to another type.
     * @param destClass       Class to coerce into.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder coerceClass(String sourceClassName, Class<?> destClass) {
        Class<?> clazz = ClassUtilities.forName(sourceClassName, options.getClassLoader());
        if (clazz != null) {
            options.coercedTypes.put(clazz, destClass);
        }
        return this;
    }

    /**
     * @param missingFieldHandler MissingFieldHandler implementation to call.  This method will be
     *                            called when a field in the JSON is read in, yet there is no corresponding field on the destination object to
     *                            receive the value.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder missingFieldHandler(MissingFieldHandler missingFieldHandler) {
        options.missingFieldHandler = missingFieldHandler;
        return this;
    }

    /**
     * @param clazz class to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *              class added here will never use an @id/@ref pair.  The downside, is that when read,
     *              each instance, even if the same as another, will use memory.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNonReferenceableClass(Class<?> clazz) {
        options.nonRefClasses.add(clazz);
        return this;
    }

    /**
     * @param useUnsafe boolean set to 'true' to enable unsafe mode for object instantiation. When enabled,
     *                  json-io can instantiate classes that have no public constructors, package-private classes,
     *                  inner classes, and classes whose constructors throw exceptions. This bypasses normal Java
     *                  constructor invocation. Default is 'false' for security reasons.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder useUnsafe(boolean useUnsafe) {
        options.useUnsafe = useUnsafe;
        return this;
    }

    /**
     * Load ClassFactory classes based on contents of resources/classFactory.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     */
    private static void loadBaseClassFactory() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/classFactory.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String factoryClassName = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);

            if (clazz == null) {
                LOG.fine("Skipping class: " + className + " not defined in JVM, but listed in resources/classFactories.txt");
                continue;
            }

            if (factoryClassName.equalsIgnoreCase("ArrayFactory")) {
                addPermanentClassFactory(clazz, new ArrayFactory<>(clazz));
            } else {
                try {
                    Class<? extends ClassFactory> factoryClass = (Class<? extends ClassFactory>) ClassUtilities.forName(factoryClassName, classLoader);
                    if (factoryClass == null) {
                        LOG.fine("Skipping class: " + factoryClassName + " not defined in JVM, but listed in resources/classFactories.txt, as factory for: " + className);
                        continue;
                    }
                    // Add this if javac issues an unchecked cast warning for this line
                    Constructor<? extends ClassFactory> ctor = (Constructor<? extends ClassFactory>) ReflectionUtils.getConstructor(factoryClass);
                    addPermanentClassFactory(clazz, ctor.newInstance());
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Unable to create ClassFactory class: " + factoryClassName + ", a factory class for: " + className + ", listed in resources/classFactories.txt", e);
                }
            }
        }
    }

    /**
     * Load custom reader classes based on contents of resources/customReaders.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     */
    private static void loadBaseReaders() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/customReaders.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String readerClassName = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz != null) {
                Class<? extends JsonClassReader> customReaderClass = (Class<JsonClassReader>) ClassUtilities.forName(readerClassName, classLoader);

                try {
                    // Add this if javac issues an unchecked cast warning for this line
                    Constructor<? extends JsonClassReader> ctor = ReflectionUtils.getConstructor(customReaderClass);
                    addPermanentReader(clazz, ctor.newInstance());
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Note: could not instantiate (custom JsonClassReader class): " + readerClassName + " from resources/customReaders.txt", e);
                }
            } else {
                LOG.fine("Class: " + className + " not defined in JVM, but listed in resources/customReaders.txt");
            }
        }
    }

    /**
     * Load custom writer classes based on contents of customWriters.txt in the resources folder.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     */
    private static void loadBaseCoercedTypes() {
        Map<String, String> map = MetaUtils.loadMapDefinition("config/coercedTypes.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String srcClassName = entry.getKey();
            String destClassName = entry.getValue();
            Class<?> srcType = ClassUtilities.forName(srcClassName, classLoader);
            if (srcType == null) {
                LOG.fine("Skipping class coercion for source class: " + srcClassName + " (not found) to: " + destClassName + ", listed in resources/coercedTypes.txt");
                continue;
            }
            Class<?> destType = ClassUtilities.forName(destClassName, classLoader);
            if (destType == null) {
                LOG.fine("Skipping class coercion for source class: " + srcClassName + " cannot be mapped to: " + destClassName + " (not found), listed in resources/coercedTypes.txt");
                continue;
            }
            addPermanentCoercedType(srcType, destType);
        }
    }

    public static class DefaultConverterOptions implements ConverterOptions {
        private ZoneId zoneId = ZoneId.systemDefault();
        private Locale locale = Locale.getDefault();
        private Charset charset = StandardCharsets.UTF_8;
        private ClassLoader classloader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);
        private Character trueChar = CommonValues.CHARACTER_ONE;
        private Character falseChar = CommonValues.CHARACTER_ZERO;
        private Map<String, Object> customOptions = new ConcurrentHashMap<>();
        private Map<Converter.ConversionPair, Convert<?>> converterOverrides = new ConcurrentHashMap<>(100, .8f);

        public ZoneId getZoneId() {
            return zoneId;
        }

        public Locale getLocale() {
            return locale;
        }

        public Charset getCharset() {
            return charset;
        }

        public ClassLoader getClassLoader() {
            return classloader;
        }

        public Character trueChar() {
            return trueChar;
        }

        public Character falseChar() {
            return falseChar;
        }

        public Map<Converter.ConversionPair, Convert<?>> getConverterOverrides() {
            return converterOverrides;
        }

        public <T> T getCustomOption(String name) {
            return (T) customOptions.get(name);
        }
    }

    static class DefaultReadOptions implements ReadOptions {
        private Class<?> unknownTypeClass = null;
        private boolean failOnUnknownType = true;
        private boolean closeStream = true;
        private int maxDepth = 1000;
        private int lruSize = 1000;
        private MissingFieldHandler missingFieldHandler = null;
        private DefaultConverterOptions converterOptions = new DefaultConverterOptions();
        private ReadOptions.ReturnType returnType = ReadOptions.ReturnType.JAVA_OBJECTS;
        private ReadOptions.Decimals decimalType = Decimals.DOUBLE;
        private ReadOptions.Integers integerType = Integers.LONG;
        private boolean allowNanAndInfinity = false;
        private boolean strictJson = false;  // Default to false (permissive JSON5 mode)
        private boolean useUnsafe = false;  // Default to false for security
        
        // Security limits - default to unlimited for backward compatibility
        private int maxUnresolvedReferences = Integer.MAX_VALUE;
        private int maxStackDepth = Integer.MAX_VALUE;
        private int maxMapsToRehash = Integer.MAX_VALUE;
        private int maxMissingFields = Integer.MAX_VALUE;
        
        // Resolver security limits - default to generous but finite values for backward compatibility
        private int maxObjectReferences = 100000000;      // 100M objects max (same as Resolver hardcoded default)
        private int maxReferenceChainDepth = 100000;      // 100K chain depth max (same as Resolver hardcoded default)
        private int maxEnumNameLength = 1024;             // 1024 chars max (same as Resolver hardcoded default)
        
        // JsonParser-specific security limits - default to backward compatible values
        private long maxIdValue = 1000000000L;            // ±1B ID range max (same as JsonParser hardcoded default)
        private int stringBufferSize = 256;               // 256 chars initial capacity (same as JsonParser hardcoded default)

        // MetaUtils-specific security limits - default to backward compatible values
        private int maxAllowedLength = 65536;             // 64KB max allowed length (same as MetaUtils hardcoded default)
        private int maxFileContentSize = 1048576;         // 1MB max file content size (same as MetaUtils hardcoded default)
        private int maxLineCount = 10000;                 // 10K max line count (same as MetaUtils hardcoded default)
        private int maxLineLength = 8192;                 // 8KB max line length (same as MetaUtils hardcoded default)
        
        // JsonValue-specific security limits - default to backward compatible values
        private int maxTypeResolutionCacheSize = 1000;    // 1K cache entries max (same as JsonValue hardcoded default)
        
        // Collection/Map Factory-specific security limits - default to backward compatible values
        private int defaultCollectionCapacity = 16;       // 16 default collection capacity (same as CollectionFactory/MapFactory hardcoded default)
        private float collectionLoadFactor = 0.75f;       // 0.75 load factor for hash collections (same as CollectionFactory/MapFactory hardcoded default)
        private int minCollectionCapacity = 16;           // 16 minimum collection capacity (same as CollectionFactory/MapFactory hardcoded default)
        
        // JsonObject-specific performance limits - default to backward compatible values
        private int linearSearchThreshold = 8;            // 8 linear search threshold (same as JsonObject hardcoded default)
        
        private Map<String, String> aliasTypeNames = new LinkedHashMap<>();
        private Map<Class<?>, Class<?>> coercedTypes = new ClassValueMap<>();
        private Set<Class<?>> notCustomReadClasses = new ClassValueSet();
        private Map<Class<?>, JsonClassReader> customReaderClasses = new ClassValueMap<>();
        private Map<Class<?>, ClassFactory> classFactoryMap = new ClassValueMap<>();
        private Set<Class<?>> nonRefClasses = new ClassValueSet();
        private Map<Class<?>, Set<String>> excludedFieldNames = new ClassValueMap<>();
        private Map<Class<?>, Set<String>> fieldsNotImported = new ClassValueMap<>();
        private List<FieldFilter> fieldFilters = new ArrayList<>();
        private List<InjectorFactory> injectorFactories = new ArrayList<>();
        private Map<String, Object> customOptions = new LinkedHashMap<>();

        // Runtime cache (not feature options)
        private final Map<Class<?>, JsonClassReader> readerCache = new ClassValueMap<>();
        private final ClassFactory throwableFactory = new ThrowableFactory();
        private final ClassFactory enumFactory = new EnumClassFactory();
        private static final ClassFactory recordFactory = new RecordFactory();
        private static final Method isRecordMethod;

        static {
            Method m = null;
            try {
                m = ReflectionUtils.getMethod(Class.class, "isRecord");
            } catch (Exception ignore) {
            }
            isRecordMethod = m;
        }

        /**
         * Default constructor. Prevent instantiation outside of package.
         */
        private DefaultReadOptions() {
        }

        /**
         * @return boolean will return true if NAN and Infinity are allowed to be read in as Doubles and Floats,
         * else a JsonIoException will be thrown if these are encountered.  default is false per JSON standard.
         */
        public boolean isAllowNanAndInfinity() {
            return allowNanAndInfinity;
        }

        public boolean isStrictJson() {
            return strictJson;
        }

        /**
         * @return ClassLoader to be used when reading JSON to resolve String named classes.
         */
        public ClassLoader getClassLoader() {
            return this.converterOptions.getClassLoader();
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
        public Class<?> getUnknownTypeClass() {
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
         * @return int maximum number of unresolved references allowed during JSON processing.
         * Once this limit is reached, a JsonIoException will be thrown to prevent memory exhaustion
         * from DoS attacks via unbounded forward references. Default is Integer.MAX_VALUE (unlimited).
         */
        public int getMaxUnresolvedReferences() {
            return maxUnresolvedReferences;
        }

        /**
         * @return int maximum traversal stack depth allowed during JSON processing.
         * Once this limit is reached, a JsonIoException will be thrown to prevent stack overflow
         * attacks via deeply nested structures. Default is Integer.MAX_VALUE (unlimited).
         */
        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        /**
         * @return int maximum number of maps that can be queued for rehashing during JSON processing.
         * Once this limit is reached, a JsonIoException will be thrown to prevent memory exhaustion
         * from DoS attacks via excessive map creation. Default is Integer.MAX_VALUE (unlimited).
         */
        public int getMaxMapsToRehash() {
            return maxMapsToRehash;
        }

        /**
         * @return int maximum number of missing fields that can be tracked during JSON processing.
         * Once this limit is reached, a JsonIoException will be thrown to prevent memory exhaustion
         * from DoS attacks via excessive missing field tracking. Default is Integer.MAX_VALUE (unlimited).
         */
        public int getMaxMissingFields() {
            return maxMissingFields;
        }

        public int getMaxObjectReferences() {
            return maxObjectReferences;
        }

        public int getMaxReferenceChainDepth() {
            return maxReferenceChainDepth;
        }

        public int getMaxEnumNameLength() {
            return maxEnumNameLength;
        }

        public long getMaxIdValue() {
            return maxIdValue;
        }

        public int getStringBufferSize() {
            return stringBufferSize;
        }

        public int getMaxAllowedLength() {
            return maxAllowedLength;
        }

        public int getMaxFileContentSize() {
            return maxFileContentSize;
        }

        public int getMaxLineCount() {
            return maxLineCount;
        }

        public int getMaxLineLength() {
            return maxLineLength;
        }

        public int getMaxTypeResolutionCacheSize() {
            return maxTypeResolutionCacheSize;
        }

        public int getDefaultCollectionCapacity() {
            return defaultCollectionCapacity;
        }

        public float getCollectionLoadFactor() {
            return collectionLoadFactor;
        }

        public int getMinCollectionCapacity() {
            return minCollectionCapacity;
        }

        public int getLinearSearchThreshold() {
            return linearSearchThreshold;
        }

        public boolean isUseUnsafe() {
            return useUnsafe;
        }
        
        /**
         * @return int LRU size, which is the size of the maximum number of class to fields, and field to injector
         * caches. It defaults to 1,0000. Higher values will help improve performance, however, infrequently used
         * classes (mapped to Fields) will be kept in the cache. Adjust this as needed if memory usage becomes a
         * problem.  If you have a high variety of classes being serialized, say 10's of thousands, then you would
         * want to monitor your cache size, balancing performance versus memory usage.
         */
        public int getLruSize() {
            return lruSize;
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
         * @return boolean true if the passed in Class name is being coerced to another type, false otherwise.
         */
        public boolean isClassCoerced(Class<?> clazz) {
            return coercedTypes.containsKey(clazz);
        }

        /**
         * Fetch the coerced class for the passed in fully qualified class name.
         *
         * @param c Class to coerce
         * @return Class destination (coerced) class or null if there is none.
         */
        public Class<?> getCoercedClass(Class<?> c) {
            return coercedTypes.get(c);
        }

        /**
         * @return MissingFieldHandler to be called when a field in the JSON is read in, yet there is no
         * corresponding field on the destination object to receive the field value.
         */
        public MissingFieldHandler getMissingFieldHandler() {
            return missingFieldHandler;
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
         *
         * @param c Class for which to fetch the ClassFactory.
         * @return ClassFactory instance associated to the passed in class.
         */
        public ClassFactory getClassFactory(Class<?> c) {
            if (c == null) {
                return null;
            }

            ClassFactory factory = this.classFactoryMap.get(c);

            if (factory != null) {
                return factory;
            }

            // Special case exceptions - they will "inherit" from ThrowableFactory unless explicitly overridden in the classFactory.txt resource file.
            if (Throwable.class.isAssignableFrom(c)) {
                return throwableFactory;
            }

            Class<?> enumClass = ClassUtilities.getClassIfEnum(c);

            if (enumClass != null) {
                return enumFactory;
            }

            // Special case Records - they will inherit from RecordFactory unless explicity overridden in the classFactory.txt resource file.
            if (isRecord(c)) {
                return recordFactory;
            }

            return null;
        }

        private static boolean isRecord(Class<?> c) {
            if (isRecordMethod == null || c == null) {
                return false;
            }
            try {
                return (Boolean) isRecordMethod.invoke(c);
            } catch (Exception ignore) {
                return false;
            }
        }

        /**
         * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
         * null value.  Instead, singleton instance of this class is placed where null values
         * are needed.
         */
        private static final class NullClass implements JsonClassReader {
        }

        private static final NullClass nullReader = new NullClass();

        /**
         * Fetch the custom reader for the passed in Class.  If it is cached (already associated to the
         * passed in Class), return the same instance, otherwise, make a call to get the custom reader
         * and store that result.
         *
         * @param c Class of object for which fetch a custom reader
         * @return JsonClassReader for the custom class (if one exists), null otherwise.
         */
        public JsonClassReader getCustomReader(Class<?> c) {
            JsonClassReader reader = readerCache.computeIfAbsent(c, cls -> ClassUtilities.findClosest(c, customReaderClasses, nullReader));
            return reader == nullReader ? null : reader;
        }

        /**
         * @return true if returning items in basic JSON object format
         */
        public boolean isReturningJsonObjects() {
            return returnType == ReadOptions.ReturnType.JSON_OBJECTS;
        }

        /**
         * @return true if returning items in full Java object formats.  Useful for accurate reproduction of graphs
         * into the orginal types such as when cloning objects.
         */
        public boolean isReturningJavaObjects() {
            return returnType == ReadOptions.ReturnType.JAVA_OBJECTS;
        }

        /**
         * @return true if floating point values should always be returned as Doubles.  This is the default.
         */
        public boolean isFloatingPointDouble() {
            return decimalType == Decimals.DOUBLE;
        }

        /**
         * @return true if floating point values should always be returned as BigDecimals.
         */
        public boolean isFloatingPointBigDecimal() {
            return decimalType == Decimals.BIG_DECIMAL;
        }

        /**
         * @return true if floating point values should always be returned dynamically, as Double or BigDecimal, favoring
         * Double except when precision would be lost, then BigDecimal is returned.
         */
        public boolean isFloatingPointBoth() {
            return decimalType == Decimals.BOTH;
        }

        /**
         * @return true if integer values should always be returned as Longs.  This is the default.
         */
        public boolean isIntegerTypeLong() {
            return integerType == Integers.LONG;
        }

        /**
         * @return true if integer values should always be returned as BigIntegers.
         */
        public boolean isIntegerTypeBigInteger() {
            return integerType == Integers.BIG_INTEGER;
        }

        /**
         * @return true if integer values should always be returned dynamically, as Long or BigInteger, favoring
         * Long except when precision would be lost, then BigInteger is returned.
         */
        public boolean isIntegerTypeBoth() {
            return integerType == Integers.BOTH;
        }

        public Map<String, Injector> getDeepInjectorMap(Class<?> classToTraverse) {
            if (classToTraverse == null) {
                return Collections.emptyMap();
            }
            return injectorsCache.computeIfAbsent(classToTraverse, this::buildInjectors);
        }

        public void clearCaches() {
            injectorsCache.clear();
        }

        private Map<String, Injector> buildInjectors(Class<?> c) {
            final Map<String, Field> fields = getDeepDeclaredFields(c);
            final Map<String, Injector> injectors = new LinkedHashMap<>(fields.size());

            for (final Map.Entry<String, Field> entry : fields.entrySet()) {
                final Field field = entry.getValue();
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
                    final Injector injector = factory.createInjector(field, BASE_NONSTANDARD_SETTERS, key);

                    if (injector != null) {
                        return injector;
                    }
                } catch (Exception ignore) {
                }
            }
            return null;
        }

        public ConverterOptions getConverterOptions() {
            return this.converterOptions;
        }

        public Object getCustomOption(String key)
        {
            return customOptions.get(key);
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param c - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> getDeepDeclaredFields(final Class<?> c) {
            return classMetaCache.computeIfAbsent(c, this::buildDeepFieldMap);
        }

        /**
         * Gets the declared fields for the full class hierarchy of a given class
         *
         * @param clazz - given class.
         * @return Map - map of string fieldName to Field Object.  This will have the
         * deep list of fields for a given class.
         */
        public Map<String, Field> buildDeepFieldMap(final Class<?> clazz) {
            Convention.throwIfNull(clazz, "class cannot be null");

            final Map<String, Field> map = new LinkedHashMap<>();
            final Set<String> excludedFields = new HashSet<>();

            Class<?> curr = clazz;
            while (curr != null) {
                List<Field> fields = ReflectionUtils.getDeclaredFields(curr);
                final Set<String> excludedForClass = excludedFieldNames.get(curr);

                if (excludedForClass != null) {
                    excludedFields.addAll(excludedForClass);
                }

                final Set<String> notImported = fieldsNotImported.get(curr);

                if (notImported != null) {
                    excludedFields.addAll(notImported);
                }

                for (Field field : fields) {
                    if (excludedFields.contains(field.getName()) || fieldIsFiltered(field)) {
                        continue;
                    }

                    String name = field.getName();

                    if (map.putIfAbsent(name, field) != null) {
                        map.put(field.getDeclaringClass().getSimpleName() + '.' + name, field);
                        // to support inner classes that were serialized by previous version of json-io
                        map.put(field.getDeclaringClass().getName() + '.' + name, field);
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

    /**
     * Populates a map with a mapping of Class -> Set of Strings
     */
    static Map<Class<?>, Set<String>> loadClassToSetOfStrings(String fileName) {
        Map<String, String> map = loadMapDefinition(fileName);
        Map<Class<?>, Set<String>> builtMap = new LinkedHashMap<>();
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                continue;
            }

            Set<String> resultSet = ConcurrentHashMap.newKeySet();
            resultSet.addAll(StringUtilities.commaSeparatedStringToSet(entry.getValue()));
            builtMap.put(clazz, resultSet);
        }
        return builtMap;
    }
    
    static Map<Class<?>, Map<String, String>> loadClassToFieldAliasNameMapping(String fileName) {
        Map<String, String> map = MetaUtils.loadMapDefinition(fileName);
        Map<Class<?>, Map<String, String>> nonStandardMapping = new ConcurrentHashMap<>();
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String mappings = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                LOG.fine("Class: " + className + " not defined in the JVM");
                continue;
            }

            Map<String, String> mapping = nonStandardMapping.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
            Set<String> pairs = StringUtilities.commaSeparatedStringToSet(mappings);
            for (String pair : pairs) {
                String[] fieldAlias = pair.split(":");
                mapping.put(fieldAlias[0], fieldAlias[1]);
            }
        }
        return nonStandardMapping;
    }

    /**
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     */
    private static void loadBaseNonRefs() {
        final Set<String> set = MetaUtils.loadSetDefinition("config/nonRefs.txt");
        final ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (String className : set) {
            Class<?> loadedClass = ClassUtilities.forName(className, classLoader);

            if (loadedClass == null) {
                LOG.warning("Class: " + className + " undefined.  Cannot be used as non-referenceable class, listed in config/nonRefs.txt");
            } else {
                addPermanentNonReferenceableClass(loadedClass);
            }
        }
    }

    /**
     * Load the list of classes that are intended to not (never) be read using a custom reader.  This is useful to
     * terminate a "custom reader" inheritance chain.  For example, if you have a custom reader for a base class, and
     * you don't want it to be used for a subclass, you can add the subclass to this list.
     */
    private static void loadBaseNotCustomReadClasses() {
        final Set<String> set = MetaUtils.loadSetDefinition("config/notCustomRead.txt");
        final ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);

        for (String className : set) {
            Class<?> loadedClass = ClassUtilities.forName(className, classLoader);

            if (loadedClass == null) {
                LOG.warning("Class: " + className + " undefined.  Cannot be used as to turn off custom reading for this class, listed in config/notCustomRead.txt");
            } else {
                addPermanentNotCustomReadClass(loadedClass);
            }
        }
    }

    @FunctionalInterface
    public interface AliasApplier {
        void apply(Class<?> clazz, String alias);
    }

    static void loadBaseAliasMappings(AliasApplier aliasApplier) {
        Map<String, String> aliasMappings = MetaUtils.loadMapDefinition("config/aliases.txt");
        ClassLoader classLoader = ClassUtilities.getClassLoader(ReadOptionsBuilder.class);
        for (Map.Entry<String, String> entry : aliasMappings.entrySet()) {
            String className = entry.getKey();
            String alias = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);

            if (clazz == null) {
                LOG.fine("Could not find class: " + className + " which has associated alias value: " + alias + " config/aliases.txt");
            } else {
                // Add the alias and 1D to 3D array versions of it.
                Class<?> clazz1 = Array.newInstance(clazz, 0).getClass();
                Class<?> clazz2 = Array.newInstance(clazz1, 0).getClass();
                Class<?> clazz3 = Array.newInstance(clazz2, 0).getClass();
                aliasApplier.apply(clazz, alias);
                aliasApplier.apply(clazz1, alias + "[]");
                aliasApplier.apply(clazz2, alias + "[][]");
                aliasApplier.apply(clazz3, alias + "[][][]");
            }
        }
    }

    private static void loadBaseFieldsNotImported() {
        Map<Class<?>, Set<String>> allFieldsNotImported = loadClassToSetOfStrings("config/fieldsNotImported.txt");
        for (Map.Entry<Class<?>, Set<String>> entry : allFieldsNotImported.entrySet()) {
            Class<?> clazz = entry.getKey();
            Set<String> notImportedFields = entry.getValue();
            for (String notImportedField : notImportedFields) {
                addPermanentNotImportedField(clazz, notImportedField);
            }
        }
    }

    private static void loadBaseNonStandardSetters() {
        Map<Class<?>, Map<String, String>> allNonStandardSetters = loadClassToFieldAliasNameMapping("config/nonStandardSetters.txt");
        for (Map.Entry<Class<?>, Map<String, String>> entry : allNonStandardSetters.entrySet()) {
            Class<?> clazz = entry.getKey();
            Map<String, String> nonStandardSetters = entry.getValue();
            for (Map.Entry<String, String> stringEntry : nonStandardSetters.entrySet()) {
                addPermanentNonStandardSetter(clazz, stringEntry.getKey(), stringEntry.getValue());
            }
        }
    }
}
