package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.factories.MethodInjectorFactory;
import com.cedarsoftware.util.reflect.filters.field.EnumFieldFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.cedarsoftware.util.io.MetaUtils.loadMapDefinition;

public class ReadOptionsBuilder {

    private static final Map<Class<?>, JsonReader.JsonClassReader> BASE_READERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, JsonReader.ClassFactory> BASE_CLASS_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<String, String> BASE_ALIAS_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> BASE_COERCED_TYPES = new ConcurrentHashMap<>();
    private static final Set<Class<?>> BASE_NON_REFS = Collections.synchronizedSet(new LinkedHashSet<>());

    private static final Map<Class<?>, Map<String, String>> BASE_NONSTANDARD_MAPPINGS = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Set<String>> BASE_EXCLUDED_FIELD_NAMES = new ConcurrentHashMap<>();

    static {
        // ClassFactories
        BASE_CLASS_FACTORIES.putAll(loadClassFactory());
        BASE_READERS.putAll(loadReaders());
        BASE_ALIAS_MAPPINGS.putAll(loadMapDefinition("aliases.txt"));
        BASE_COERCED_TYPES.putAll(loadCoercedTypes());      // Load coerced types from resource/coerced.txt
        BASE_NON_REFS.addAll(WriteOptions.loadNonRefs());
        BASE_EXCLUDED_FIELD_NAMES.putAll(MetaUtils.loadClassToSetOfStrings("excludedInjectorFields.txt"));
        BASE_NONSTANDARD_MAPPINGS.putAll(MetaUtils.loadNonStandardMethodNames("nonStandardInjectors.txt"));
    }

    private ReadOptions options;

    /**
     * Start with default options
     */
    public ReadOptionsBuilder() {
        this.options = new ReadOptions();

        // Direct copy (with swap) without classForName() lookups for speed.
        // (The classForName check is done with the BASE_ALIAS_MAPPINGS are loaded)
        BASE_ALIAS_MAPPINGS.forEach((srcType, alias) -> {
            this.options.aliasTypeNames.put(alias, srcType);
        });

        this.options.injectorFactories = new ArrayList<>();
        this.options.injectorFactories.add(new MethodInjectorFactory());

        this.options.fieldFilters = new ArrayList<>();
        this.options.fieldFilters.add(new EnumFieldFilter());

        this.options.excludedFieldNames = new HashMap<>();
        this.options.excludedInjectorFields = new HashMap<>();
        this.options.nonStandardMappings = new HashMap<>();
        this.options.coercedTypes = new HashMap<>();

        this.options.coercedTypes.putAll(BASE_COERCED_TYPES);
        this.options.customReaderClasses.putAll(BASE_READERS);
        this.options.classFactoryMap.putAll(BASE_CLASS_FACTORIES);
        this.options.nonRefClasses.addAll(BASE_NON_REFS);
        this.options.nonStandardMappings.putAll(BASE_NONSTANDARD_MAPPINGS);
        this.options.excludedFieldNames.putAll(WriteOptionsBuilder.BASE_EXCLUDED_FIELD_NAMES);
        this.options.excludedInjectorFields.putAll(BASE_EXCLUDED_FIELD_NAMES);

    }

    /**
     * Call this method to add a permanent (JVM lifetime) coercion of one
     * class to another during instance creation.  Examples of classes
     * that might need this are proxied classes such as HibernateBags, etc.
     * That you want to create as regular jvm collections.
     *
     * @param sourceClass String class name (fully qualified name) that will be coerced to another type.
     * @param factory     JsonReader.ClassFactory instance which can create the sourceClass and load it's contents,
     *                    using passed in JsonValues (JsonObject or JsonArray).
     */
    public static void addPermanentClassFactory(Class<?> sourceClass, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(sourceClass, factory);
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
     * @param classToCreate Class that will need to use a JsonReader.ClassFactory for instantiation and loading.
     * @param factory       JsonReader.ClassFactory instance that has been created to instantiate a difficult
     *                      to create and load class (class c).
     */
    public static void assignInstantiator(Class<?> classToCreate, JsonReader.ClassFactory factory) {
        BASE_CLASS_FACTORIES.put(classToCreate, factory);
    }

    /**
     * @return ReadOptions - built options
     */
    public ReadOptions build() {
        this.options.clearCaches();
        this.options.aliasTypeNames = Collections.unmodifiableMap(this.options.aliasTypeNames);
        this.options.coercedTypes = Collections.unmodifiableMap(this.options.coercedTypes);
        this.options.notCustomReadClasses = Collections.unmodifiableSet(this.options.notCustomReadClasses);
        this.options.customReaderClasses = Collections.unmodifiableMap(this.options.customReaderClasses);
        this.options.classFactoryMap = Collections.unmodifiableMap(this.options.classFactoryMap);
        this.options.nonRefClasses = Collections.unmodifiableSet(this.options.nonRefClasses);

        return this.options;
    }

    /**
     * Add a class to the not-customized list - the list of classes that you do not want to be picked up by a
     * custom reader (that could happen through inheritance).
     *
     * @param notCustomClass Class to add to the not-customized list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNotCustomReaderClass(Class<?> notCustomClass) {
        this.options.notCustomReadClasses.add(notCustomClass);
        return this;
    }

    /**
     * @param notCustomClasses initialize the list of classes on the non-customized list.  All prior associations
     *                         will be dropped and this Collection will establish the new list.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceNotCustomReaderClasses(Collection<Class<?>> notCustomClasses) {
        this.options.notCustomReadClasses.clear();
        this.options.notCustomReadClasses.addAll(notCustomClasses);
        return this;
    }

    /**
     * @param customReaderClasses Map of Class to JsonReader.JsonClassReader.  Establish the passed in Map as the
     *                            established Map of custom readers to be used when reading JSON. Using this method
     *                            more than once, will set the custom readers to only the values from the Set in
     *                            the last call made.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceCustomReaderClasses(Map<? extends Class<?>, ? extends JsonReader.JsonClassReader> customReaderClasses) {
        this.options.customReaderClasses.clear();
        this.options.customReaderClasses.putAll(customReaderClasses);
        return this;
    }

    /**
     * @param clazz        Class to add a custom reader for.
     * @param customReader JsonClassReader to use when the passed in Class is encountered during serialization.
     *                     Custom readers are passed an empty instance.  Use a ClassFactory if you want to instantiate
     *                     and load the class in one step.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addCustomReaderClass(Class<?> clazz, JsonReader.JsonClassReader customReader) {
        this.options.customReaderClasses.put(clazz, customReader);
        return this;
    }

    /**
     * Associate multiple ClassFactory instances to Classes that needs help being constructed and read in.
     *
     * @param factories Map of entries that are Class to Factory. The Factory class knows how to instantiate
     *                  and load the class associated to it.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder replaceClassFactories(Map<Class<?>, ? extends JsonReader.ClassFactory> factories) {
        this.options.classFactoryMap.clear();
        this.options.classFactoryMap.putAll(factories);
        return this;
    }

    /**
     * Associate a ClassFactory to a Class that needs help being constructed and read in.
     *
     * @param clazz   Class that is difficult to instantiate.
     * @param factory Class written to instantiate the 'clazz' and load it's values.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addClassFactory(Class<?> clazz, JsonReader.ClassFactory factory) {
        this.options.classFactoryMap.put(clazz, factory);
        return this;
    }

    /**
     * Set to return as JSON_OBJECTS's (faster, useful for large, more simple object data sets). This returns as
     * native json types (Map, Object[], long, double, boolean)
     */
    public ReadOptionsBuilder returnAsNativeJsonObjects() {
        this.options.returnType = ReturnType.JSON_OBJECTS;
        return this;
    }

    /**
     * Return as JAVA_OBJECT's the returned value will be of the class type passed into JsonReader.toJava(json, rootClass).
     * This mode is good for cloning exact objects.
     *
     * @param reconstructionType which is either ReconstructionType.JAVA_OBJECTS or ReconstructionType.JSON_VALUES
     */
    public ReadOptionsBuilder returnAsJavaObjects() {
        this.options.returnType = ReturnType.JAVA_OBJECTS;
        return this;
    }

    /**
     * @param classLoader ClassLoader to use when reading JSON to resolve String named classes.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder classLoader(ClassLoader classLoader) {
        this.options.classLoader = classLoader;
        return this;
    }

    /**
     * @param fail boolean indicating whether we should fail on unknown type encountered.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder failOnUnknownType(boolean fail) {
        this.options.failOnUnknownType = fail;
        return this;
    }

    /**
     * Set a class to use when the JSON reader cannot instantiate a class. When that happens, it will throw
     * a JsonIoException() if no 'unknownTypeClass' is set, otherwise it will create the instance of the class
     * specified as the 'unknownTypeClass.' A common one to use, for example, if java.util.LinkedHashMap.
     *
     * @param c Class to instantiate when a String specified class in the JSON cannot be instantiated.  Set to null
     *          if you want a JsonIoException to be thrown when this happens.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder unknownTypeClass(Class<?> c) {
        this.options.unknownTypeClass = c;
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
        this.options.closeStream = closeStream;
        return this;
    }

    /**
     * @param maxDepth int maximum of the depth that JSON Objects {...} can be nested.  Set this to prevent
     *                 security risk from StackOverflow attack vectors.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder maxDepth(int maxDepth) {
        this.options.maxDepth = maxDepth;
        return this;
    }

    /**
     * @param allowNanAndInfinity boolean 'allowNanAndInfinity' setting.  true will allow Double and Floats to be
     *                            read in as NaN and +Inf, -Inf [infinity], false and a JsonIoException will be
     *                            thrown if these values are encountered
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder allowNanAndInfinity(boolean allowNanAndInfinity) {
        this.options.allowNanAndInfinity = allowNanAndInfinity;
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
     * @param type  String class name
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
     * Since we are swapping keys/values, we must check for duplicate values (which are now keys).
     *
     * @param type  String fully qualified class name.
     * @param alias String shorter alias name.
     */
    private void addUniqueAlias(String type, String alias) {
        Class<?> clazz = MetaUtils.classForName(type, this.options.getClassLoader());
        if (clazz == null) {
            throw new JsonIoException("Unknown class: " + type + " cannot be added to the ReadOptions alias map.");
        }
        String existType = this.options.aliasTypeNames.get(alias);
        if (existType != null) {
            throw new JsonIoException("Non-unique ReadOptions alias: " + alias + " attempted assign to: " + type + ", but is already assigned to: " + existType);
        }
        this.options.aliasTypeNames.put(alias, type);
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
        Class<?> clazz = MetaUtils.classForName(sourceClassName, this.options.getClassLoader());
        if (clazz != null) {
            this.options.coercedTypes.put(clazz, destClass);
        }
        return this;
    }

    /**
     * @param missingFieldHandler JsonReader.MissingFieldHandler implementation to call.  This method will be
     *                            called when a field in the JSON is read in, yet there is no corresponding field on the destination object to
     *                            receive the value.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder missingFieldHandler(JsonReader.MissingFieldHandler missingFieldHandler) {
        this.options.missingFieldHandler = missingFieldHandler;
        return this;
    }

    /**
     * @param clazz class to add to be considered a non-referenceable object.  Just like an "int" for example, any
     *              class added here will never use an @id/@ref pair.  The downside, is that when read,
     *              each instance, even if the same as another, will use memory.
     * @return ReadOptionsBuilder for chained access.
     */
    public ReadOptionsBuilder addNonReferenceableClass(Class<?> clazz) {
        this.options.nonRefClasses.add(clazz);
        return this;
    }

    /**
     * Load JsonReader.ClassFactory classes based on contents of resources/classFactory.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonReader.ClassFactory> containing the resolved Class -> JsonClassFactory instance.
     */
    private static Map<Class<?>, JsonReader.ClassFactory> loadClassFactory() {
        Map<String, String> map = MetaUtils.loadMapDefinition("classFactory.txt");
        Map<Class<?>, JsonReader.ClassFactory> factories = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String factoryClassName = entry.getValue();
            Class<?> clazz = MetaUtils.classForName(className, classLoader);
            if (clazz == null) {
                System.out.println("Skipping class: " + className + " not defined in JVM, but listed in resources/classFactories.txt");
                continue;
            }
            Class<JsonReader.ClassFactory> factoryClass = (Class<JsonReader.ClassFactory>) MetaUtils.classForName(factoryClassName, classLoader);
            if (factoryClass == null) {
                System.out.println("Skipping class: " + factoryClassName + " not defined in JVM, but listed in resources/classFactories.txt, as factory for: " + className);
                continue;
            }
            try {
                factories.put(clazz, ReflectionUtils.newInstance(factoryClass));
            } catch (Exception e) {
                throw new JsonIoException("Unable to create JsonReader.ClassFactory class: " + factoryClassName + ", a factory class for: " + className + ", listed in resources/classFactories.txt", e);
            }
        }
        return factories;
    }

    /**
     * Load custom reader classes based on contents of resources/customReaders.txt.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonReader.JsonClassReader> containing the resolved Class -> JsonClassReader instance.
     */
    private static Map<Class<?>, JsonReader.JsonClassReader> loadReaders() {
        Map<String, String> map = MetaUtils.loadMapDefinition("customReaders.txt");
        Map<Class<?>, JsonReader.JsonClassReader> readers = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String readerClassName = entry.getValue();
            Class<?> clazz = MetaUtils.classForName(className, classLoader);
            if (clazz != null) {
                Class<JsonReader.JsonClassReader> customReaderClass = (Class<JsonReader.JsonClassReader>) MetaUtils.classForName(readerClassName, classLoader);

                try {
                    readers.put(clazz, ReflectionUtils.newInstance(customReaderClass));
                } catch (Exception e) {
                    throw new JsonIoException("Note: could not instantiate (custom JsonClassReader class): " + readerClassName + " from resources/customReaders.txt");
                }
            } else {
                System.out.println("Class: " + className + " not defined in JVM, but listed in resources/customReaders.txt");
            }
        }
        return readers;
    }

    /**
     * Load custom writer classes based on contents of customWriters.txt in the resources folder.
     * Verify that classes listed are indeed valid classes loaded in the JVM.
     *
     * @return Map<Class < ?>, JsonWriter.JsonClassWriter> containing the resolved Class -> JsonClassWriter instance.
     */
    private static Map<Class<?>, Class<?>> loadCoercedTypes() {
        Map<String, String> map = MetaUtils.loadMapDefinition("coercedTypes.txt");
        Map<Class<?>, Class<?>> coerced = new HashMap<>();
        ClassLoader classLoader = ReadOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String srcClassName = entry.getKey();
            String destClassName = entry.getValue();
            Class<?> srcType = MetaUtils.classForName(srcClassName, classLoader);
            if (srcType == null) {
                System.out.println("Skipping class coercion for source class: " + srcClassName + " (not found) to: " + destClassName + ", listed in resources/coercedTypes.txt");
                continue;
            }
            Class<?> destType = MetaUtils.classForName(destClassName, classLoader);
            if (destType == null) {
                System.out.println("Skipping class coercion for source class: " + srcClassName + " cannot be mapped to: " + destClassName + " (not found), listed in resources/coercedTypes.txt");
                continue;
            }
            coerced.put(srcType, destType);
        }
        return coerced;
    }
}
