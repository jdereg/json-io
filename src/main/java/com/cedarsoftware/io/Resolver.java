package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.cedarsoftware.io.JsonReader.MissingFieldHandler;
import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.CompactCIHashMap;
import com.cedarsoftware.util.CompactCIHashSet;
import com.cedarsoftware.util.CompactCILinkedMap;
import com.cedarsoftware.util.CompactCILinkedSet;
import com.cedarsoftware.util.CompactLinkedMap;
import com.cedarsoftware.util.CompactLinkedSet;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.ConcurrentList;
import com.cedarsoftware.util.ConcurrentNavigableSetNullSafe;
import com.cedarsoftware.util.ConcurrentSet;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * This class is used to convert a source of Java Maps that were created from
 * the JsonParser.  These are in 'raw' form with no 'pointers'.  This code will
 * reconstruct the 'shape' of the graph by connecting @ref's to @ids.
 * <p>
 * The subclasses that override this class can build an object graph using Java
 * classes or a Map-of-Map representation.  In both cases, the @ref value will
 * be replaced with the Object (or Map) that had the corresponding @id.
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
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class Resolver {
    private static final String NO_FACTORY = "_︿_ψ_☼";
    final Collection<UnresolvedReference> unresolvedRefs = new ArrayList<>();
    private final Map<Object, Object> visited = new IdentityHashMap<>();
    protected final Deque<JsonObject> stack = new ArrayDeque<>();
    private final Collection<JsonObject> mapsToRehash = new ArrayList<>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    final Collection<Missingfields> missingFields = new ArrayList<>();
    private ReadOptions readOptions;
    private ReferenceTracker references;
    private final Converter converter;
    private SealedSupplier sealedSupplier = new SealedSupplier();

    /**
     * UnresolvedReference is created to hold a logical pointer to a reference that
     * could not yet be loaded, as the @ref appears ahead of the referenced object's
     * definition.  This can point to a field reference or an array/Collection element reference.
     */
    static final class UnresolvedReference {
        private final JsonObject referencingObj;
        private String field;
        private final long refId;
        private int index = -1;

        UnresolvedReference(JsonObject referrer, String fld, long id) {
            referencingObj = referrer;
            field = fld;
            refId = id;
        }

        UnresolvedReference(JsonObject referrer, int idx, long id) {
            referencingObj = referrer;
            index = idx;
            refId = id;
        }
    }

    /**
     * stores missing fields information to notify client after the complete deserialization resolution
     */
    protected static class Missingfields {
        private final Object target;
        private final String fieldName;
        private final Object value;

        public Missingfields(Object target, String fieldName, Object value) {
            this.target = target;
            this.fieldName = fieldName;
            this.value = value;
        }
    }

    protected Resolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        this.readOptions = readOptions;
        this.references = references;
        this.converter = converter;
    }

    public ReadOptions getReadOptions() {
        return readOptions;
    }

    public ReferenceTracker getReferences() {
        return references;
    }

    public Converter getConverter() {
        return converter;
    }

    /**
     * <h2>Convert a Parsed JsonObject to a Fully Resolved Java Object</h2>
     *
     * <p>
     * This method converts a root-level {@code JsonObject}—a Map-of-Maps representation of parsed JSON—into an actual
     * Java object instance. The {@code JsonObject} is typically produced by a prior call to {@code JsonIo.toObjects(String)}
     * or {@code JsonIo.toObjects(InputStream)} when using the {@code ReadOptions.returnAsJsonObjects()} setting.
     * The conversion process uses the provided <code>root</code> parameter, a {@link java.lang.reflect.Type} that represents
     * the expected root type (including any generic type parameters). Although the full type information is preserved for
     * resolution, the {@code JsonObject}'s legacy {@code hintType} field (which remains a {@code Class<?>}) is set using the
     * raw class extracted from the provided type.
     * </p>
     *
     * <p>
     * The resolution process works as follows:
     * </p>
     * <ol>
     *   <li>
     *     <strong>Reference Resolution:</strong> If the {@code JsonObject} is a reference, it is resolved using the internal
     *     reference map. If a referenced object is found, it is returned immediately.
     *   </li>
     *   <li>
     *     <strong>Already Converted Check:</strong> If the {@code JsonObject} has already been fully converted (i.e. its
     *     {@code isFinished} flag is set), then its target (the converted Java object) is returned.
     *   </li>
     *   <li>
     *     <strong>Instance Creation and Traversal:</strong> Otherwise, the method sets the hint type on the {@code JsonObject}
     *     (using the raw class extracted from the provided full {@code Type}), creates a new instance (if necessary),
     *     and then traverses the object graph to resolve nested references and perform conversions.
     *   </li>
     * </ol>
     *
     * <h3>Parameters</h3>
     * <ul>
     *   <li>
     *     <b>rootObj</b> - The root {@code JsonObject} (a Map-of-Maps) representing the parsed JSON data.
     *   </li>
     *   <li>
     *     <b>root</b> - A {@code Type} representing the expected Java type (including full generic details) for the resulting
     *     object. If {@code null}, type inference defaults to a generic {@code Map} representation.
     *   </li>
     * </ul>
     *
     * <h3>Return Value</h3>
     * <p>
     * Returns a Java object that represents the fully resolved version of the JSON data. Depending on the JSON structure
     * and the provided type hint, the result may be a user-defined DTO, a collection, an array, or a primitive value.
     * </p>
     *
     * @param rootObj the root {@code JsonObject} representing parsed JSON data.
     * @param rootType a {@code Type} representing the expected Java type (with full generic details) for the root object; may be {@code null}.
     * @param <T> the type of the resulting Java object.
     * @return a fully resolved Java object representing the JSON data.
     */
    @SuppressWarnings("unchecked")
    public <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
        if (rootObj == null) {
            return null;
        }

        // If the JsonObject is a reference, resolve it.
        if (rootObj.isReference()) {
            rootObj = getReferences().get(rootObj.refId);
            if (rootObj != null) {
                return (T) rootObj;
            }
        }

        // If already converted, return its target.
        if (rootObj.isFinished) {
            return (T) rootObj.getTarget();
        } else {
            if (rootObj.getType() == null) {
                // If there is no explicit type hint in the JSON, use the provided root type.
                rootObj.setType(rootType);
            }
            Object instance = (rootObj.getTarget() == null ? createInstance(rootObj) : rootObj.getTarget());
            if (rootObj.isFinished) {
                return (T) instance;
            } else {
                return traverseJsonObject(rootObj);
            }
        }
    }

    /**
     * Walk a JsonObject (Map of String keys to values) and return the
     * Java object equivalent filled in as good as possible (everything
     * except unresolved reference fields or unresolved array/collection elements).
     *
     * @param root JsonObject reference to a Map-of-Maps representation of the JSON
     *             input after it has been completely read.
     * @return Properly constructed, typed, Java object graph built from a Map
     * of Maps representation (JsonObject root).
     */
    public <T> T traverseJsonObject(JsonObject root) {
        push(root);

        while (!stack.isEmpty()) {
            final JsonObject jsonObj = stack.pop();

            if (jsonObj.isFinished) {
                continue;
            }

            visited.put(jsonObj, null);
            traverseSpecificType(jsonObj);
        }
        return (T) root.getTarget();
    }

    protected void traverseSpecificType(JsonObject jsonObj) {
        if (jsonObj.isArray()) {
            traverseArray(jsonObj);
        } else if (jsonObj.isCollection()) {
            traverseCollection(jsonObj);
        } else if (jsonObj.isMap()) {
            traverseMap(jsonObj);
        } else {
            traverseObject(jsonObj);
        }
    }

    protected void traverseObject(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        Object special;
        if ((special = readWithFactoryIfExists(jsonObj, null)) != null) {
            jsonObj.setTarget(special);
        } else {
            traverseFields(jsonObj);
        }
    }

    public SealedSupplier getSealedSupplier() {
        return sealedSupplier;
    }

    /**
     * Push a JsonObject on the work stack that has not yet had it's fields move over to it's Java peer (.target)
     * @param jsonObject JsonObject that supplies the source values for the Java peer (target)
     */
    public void push(JsonObject jsonObject) {
        stack.push(jsonObject);
    }

    public abstract void traverseFields(final JsonObject jsonObj);

    protected abstract Object readWithFactoryIfExists(final Object o, final Type compType);

    protected abstract void traverseCollection(JsonObject jsonObj);

    protected abstract void traverseArray(JsonObject jsonObj);

    protected void cleanup() {
        patchUnresolvedReferences();
        rehashMaps();
        references.clear();
        unresolvedRefs.clear();
        mapsToRehash.clear();
        handleMissingFields();
        missingFields.clear();
        stack.clear();
        visited.clear();
        references = null;
        readOptions = null;
        sealedSupplier.seal();
        sealedSupplier = null;
    }

    // calls the missing field handler if any for each recorded missing field.
    private void handleMissingFields() {
        MissingFieldHandler missingFieldHandler = readOptions.getMissingFieldHandler();
        if (missingFieldHandler != null) {
            for (Missingfields mf : missingFields) {
                missingFieldHandler.fieldMissing(mf.target, mf.fieldName, mf.value);
            }
        }//else no handler so ignore.
    }

    /**
     * Process java.util.Map and it's derivatives.  These are written specially
     * so that the serialization does not expose the class internals
     * (internal fields of TreeMap for example).
     *
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseMap(JsonObject jsonObj) {
        if (jsonObj.isFinished) {
            return;
        }
        jsonObj.setFinished();
        Map.Entry<Object[], Object[]> pair = jsonObj.asTwoArrays();
        final Object[] keys = pair.getKey();
        final Object[] items = pair.getValue();

        if (keys == null) {  // If keys is null, items is also null due to JsonObject validation
            return;
        }

        buildCollection(keys);
        buildCollection(items);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        mapsToRehash.add(jsonObj);
    }

    private void buildCollection(Object[] arrayContent) {
        final JsonObject collection = new JsonObject();
        collection.setItems(arrayContent);
        collection.setTarget(arrayContent);
        push(collection);
    }

    // Create a ClassValueMap for direct instantiation of certain classes
    private static final ClassValueMap<Function<JsonObject, Object>> DEFAULT_INSTANTIATORS = new ClassValueMap<>();

    static {
        // Initialize the map with lambda factory instantiators for each class
        DEFAULT_INSTANTIATORS.put(ArrayList.class, jsonObj -> new ArrayList<>());
        DEFAULT_INSTANTIATORS.put(LinkedList.class, jsonObj -> new LinkedList<>());
        DEFAULT_INSTANTIATORS.put(CopyOnWriteArrayList.class, jsonObj -> new CopyOnWriteArrayList<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentList.class, jsonObj -> new ConcurrentList<>());
        DEFAULT_INSTANTIATORS.put(CompactCIHashMap.class, jsonObj -> new CompactCIHashMap<>());
        DEFAULT_INSTANTIATORS.put(CompactCILinkedMap.class, jsonObj -> new CompactCILinkedMap<>());
        DEFAULT_INSTANTIATORS.put(CompactLinkedMap.class, jsonObj -> new CompactLinkedMap<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentHashMap.class, jsonObj -> new ConcurrentHashMap<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSkipListMap.class, jsonObj -> new ConcurrentSkipListMap<>());
        DEFAULT_INSTANTIATORS.put(Vector.class, jsonObj -> new Vector<>());
        DEFAULT_INSTANTIATORS.put(HashMap.class, jsonObj -> new HashMap<>());
        DEFAULT_INSTANTIATORS.put(TreeMap.class, jsonObj -> new TreeMap<>());
        DEFAULT_INSTANTIATORS.put(CompactCIHashSet.class, jsonObj -> new CompactCIHashSet<>());
        DEFAULT_INSTANTIATORS.put(CompactCILinkedSet.class, jsonObj -> new CompactCILinkedSet<>());
        DEFAULT_INSTANTIATORS.put(CompactLinkedSet.class, jsonObj -> new CompactLinkedSet<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSkipListSet.class, jsonObj -> new ConcurrentSkipListSet<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentNavigableSetNullSafe.class, jsonObj -> new ConcurrentNavigableSetNullSafe<>());
        DEFAULT_INSTANTIATORS.put(ConcurrentSet.class, jsonObj -> new ConcurrentSet<>());
        DEFAULT_INSTANTIATORS.put(HashSet.class, jsonObj -> new HashSet<>());
        DEFAULT_INSTANTIATORS.put(LinkedHashSet.class, jsonObj -> new LinkedHashSet<>());
        DEFAULT_INSTANTIATORS.put(TreeSet.class, jsonObj -> new TreeSet<>());
        DEFAULT_INSTANTIATORS.put(LinkedHashMap.class, jsonObj -> new LinkedHashMap<>());
        DEFAULT_INSTANTIATORS.put(CompactMap.class, jsonObj -> {
            // If the map does not have both config and data keys, then it is a regular CompactMap
            // Note: CompactMap in custom form, has two key/values at the Object level (config and data)
            if (!jsonObj.containsKey("config") || !jsonObj.containsKey("data")) {
                return new CompactMap<>();
            }

            // if the map has both config and data keys, then make sure the config value has < 2 slashes to remain a pure CompactMap
            Object configValue = jsonObj.get("config");
            if (!(configValue instanceof String) ||
                    ((String) configValue).split("/", -1).length < 3) {
                return new CompactMap<>();
            }

            return null; // Return null to indicate custom factory should be used
        });
        DEFAULT_INSTANTIATORS.put(CompactSet.class, jsonObj -> {
            // CompactSet instantiator with special logic

            // If the set does not have both config and data keys, then it is a regular CompactSet
            // Note: CompactSet in custom form, has two key/values at the Object level (config and data)
            if (!jsonObj.containsKey("config") || !jsonObj.containsKey("data")) {
                return new CompactSet<>();
            }

            // if the map has both config and data keys, then make sure the config value has < 2 slashes to remain a pure CompactSet
            Object configValue = jsonObj.get("config");
            if (!(configValue instanceof String) || StringUtilities.count((String) configValue, '/') < 2) {
                return new CompactSet<>();
            }

            return null; // Return null to indicate custom factory should be used
        });
    }

    /**
     * This method creates a Java Object instance based on the passed in parameters.
     * If the JsonObject contains a key '@type' then that is used, as the type was explicitly
     * set in the JSON stream.  If the key '@type' does not exist, then the passed in Class
     * is used to create the instance, handling creating an Array or regular Object
     * instance.<p></p>
     * The '@type' is not often specified in the JSON input stream, as in many
     * cases it can be inferred from a field reference or array component type.
     * For Enum handling, the following rules are applied:
     * <p><pre>
     * Detection Rules:
     * 1. Single Enum Instance
     *    Detect:  javaType is an enum class (enumClass != null) AND jsonObj.getItems() == null
     *    Create:  Single enum instance using enum class
     *    Factory: EnumClassFactory
     *
     * 2. EnumSet Instance
     *    Detect:  javaType is an enum class (enumClass != null) AND jsonObj.getItems() != null
     *    Create:  EnumSet using enum class
     *    Factory: EnumSetFactory
     *
     * 3. Regular Class
     *    Detect:  Non-enum javaType (enumClass == null)
     *    Create:  Regular class instance
     *    Factory: Standard Factory Logic
     * </pre>
     *
     * @param jsonObj Map-of-Map representation of object to create.
     * @return a new Java object of the appropriate type (clazz) using the jsonObj to provide
     * enough hints to get the right class instantiated.  It is not populated when returned.
     */
    Object createInstance(JsonObject jsonObj) {
        // If an instance is already set, return it.
        Object target = jsonObj.getTarget();
        if (target != null) {
            return target;
        }

        // Use the refined Type (if available) to determine the target type.
        Class<?> targetType = resolveTargetType(jsonObj);

        // Check if we have a direct instantiator for this class
        Function<JsonObject, Object> instantiator = DEFAULT_INSTANTIATORS.get(targetType);
        if (instantiator != null) {
            Object instance = instantiator.apply(jsonObj);
            if (instance != null) {
                return jsonObj.setTarget(instance);
            }
        }

        // Knock out popular easy classes to instantiate and finish.
        if (converter.isSimpleTypeConversionSupported(targetType, targetType)) {
            return jsonObj.setFinishedTarget(converter.convert(jsonObj, targetType), true);
        }

        // Determine the factory type, considering enums and collections.
        Class<?> factoryType = determineFactoryType(jsonObj, targetType);

        // Try creating an instance using the class factory.
        Object mate = createInstanceUsingClassFactory(factoryType, jsonObj);
        if (mate != NO_FACTORY) {
            return mate;
        }

        // Attempt conversion using the Converter.
        Object sourceValue = jsonObj.hasValue() ? jsonObj.getValue() : null;
        Class<?> sourceType = sourceValue != null ? sourceValue.getClass() : (!jsonObj.isEmpty() ? Map.class : null);

        if (!Throwable.class.isAssignableFrom(targetType)) {
            if (sourceType != null && converter.isConversionSupportedFor(sourceType, targetType)) {
                try {
                    Object value = converter.convert(sourceValue != null ? sourceValue : jsonObj, targetType);
                    return jsonObj.setFinishedTarget(value, true);
                } catch (Exception ignored) { }
            }
        }

        // Handle array creation.
        if (shouldCreateArray(jsonObj, targetType)) {
            mate = createArrayInstance(jsonObj, targetType);
            return jsonObj.setTarget(mate);
        }

        // Fallback: create an instance using the type directly.
        return createInstanceUsingType(jsonObj);
    }

    // Resolve target type with proper coercion and enum handling
    private Class<?> resolveTargetType(JsonObject jsonObj) {
        Class<?> targetType = coerceClassIfNeeded(jsonObj.getRawType());
        jsonObj.setType(targetType);

        // Handle enum coercion as before.
        Class<?> enumClass = ClassUtilities.getClassIfEnum(targetType);
        if (enumClass != null) {
            Class<?> coercedEnumClass = getCoercedEnumClass(enumClass);
            if (coercedEnumClass != null) {
                targetType = coercedEnumClass;
                jsonObj.setType(coercedEnumClass);
            }
        }
        return targetType;
    }

    private Class<?> coerceClassIfNeeded(Class<?> type) {
        Class clazz = readOptions.getCoercedClass(type);
        return clazz == null ? type : clazz;
    }

    private Class<?> getCoercedEnumClass(Class<?> enumClass) {
        Class<?> coercedClass = readOptions.getCoercedClass(enumClass);
        if (coercedClass != null) {
            return ClassUtilities.getClassIfEnum(coercedClass);
        }
        return null;
    }

    // Determine the factory type, considering enums and collections
    private Class<?> determineFactoryType(JsonObject jsonObj, Class<?> targetType) {
        Class<?> enumClass = ClassUtilities.getClassIfEnum(targetType);
        if (enumClass != null) {
            boolean isEnumSet = jsonObj.getItems() != null;
            return isEnumSet ? EnumSet.class : enumClass;
        }
        return targetType;
    }

    private boolean shouldCreateArray(JsonObject jsonObj, Class<?> targetType) {
        Object[] items = jsonObj.getItems();
        return targetType.isArray() || (items != null && targetType == Object.class && jsonObj.getKeys() == null);
    }

    private Object createArrayInstance(JsonObject jsonObj, Class<?> targetType) {
        Object[] items = jsonObj.getItems();
        int size = (items == null) ? 0 : items.length;
        Class<?> componentType = targetType.isArray() ? targetType.getComponentType() : Object.class;
        return Array.newInstance(componentType, size);
    }

    /**
     * Create an instance of a Java class using the JavaType field on the jsonObj.
     */
    private Object createInstanceUsingType(JsonObject jsonObj) {
        Class<?> c = jsonObj.getRawType();
        boolean useMaps = readOptions.isReturningJsonObjects();
        Object mate;

        if (c == Object.class && !useMaps) {  // JsonObject
            Class<?> unknownClass = readOptions.getUnknownTypeClass();
            if (unknownClass == null) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.setType(Map.class);
                mate = jsonObject;
            } else {
                mate = ClassUtilities.newInstance(converter, unknownClass, null);   // can add constructor arg values
            }
        } else {
            // Handle regular field.object reference
            // ClassFactory already consulted above, likely regular business/data classes.
            // If the newInstance(c) fails, it throws a JsonIoException.
            mate = ClassUtilities.newInstance(converter, c, null);  // can add constructor arg values
        }
        return jsonObj.setTarget(mate);
    }

    /**
     * If a ClassFactory is associated to the passed in Class (clazz), then use the ClassFactory
     * to create an instance.  If a ClassFactory creates the instance, it may optionally load
     * the values into the instance, using the values from the passed in JsonObject.  If the
     * ClassFactory instance creates AND loads the object, it is indicated on the ClassFactory
     * by the isObjectFinal() method returning true.  Therefore, the JsonObject instance that is
     * loaded, is marked with 'isFinished=true' so that no more processing is needed for this instance.
     */
    Object createInstanceUsingClassFactory(Class c, JsonObject jsonObj) {
        // If a ClassFactory exists for a class, use it to instantiate the class.  The ClassFactory
        // may optionally load the newly created instance, in which case, the JsonObject is marked finished, and
        // return.

        JsonReader.ClassFactory classFactory = readOptions.getClassFactory(c);

        if (classFactory == null) {
            return NO_FACTORY;
        }

        Object target = classFactory.newInstance(c, jsonObj, this);

        // don't pass in classFactory.isObjectFinal, only set it to true if classFactory says its so.
        // it allows the factory itself to set final on the jsonObj internally where it depends
        // on how the data comes back, but that value can be a hard true if the factory knows
        // it's always true.
        if (classFactory.isObjectFinal()) {
            return jsonObj.setFinishedTarget(target, true);
        }

        return jsonObj.setTarget(target);
    }

    /**
     * For all fields where the value was "@ref":"n" where 'n' was the id of an object
     * that had not yet been encountered in the stream, make the final substitution.
     */
    private void patchUnresolvedReferences() {
        for (UnresolvedReference ref : unresolvedRefs) {
            Object objToFix = ref.referencingObj.getTarget();
            JsonObject objReferenced = this.references.getOrThrow(ref.refId);

            if (ref.index >= 0) {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List) {
                    List list = (List) objToFix;
                    list.set(ref.index, objReferenced.getTarget());
                } else if (objToFix instanceof Collection) {   // Patch up Indexable Collections
                    Collection col = (Collection) objToFix;
                    col.add(objReferenced.getTarget());
                } else {
                    Array.set(objToFix, ref.index, objReferenced.getTarget());        // patch array element here
                }
            } else {    // Fix field forward reference
                Field field = getReadOptions().getDeepDeclaredFields(objToFix.getClass()).get(ref.field);
                Map<String, Injector> injectors = getReadOptions().getDeepInjectorMap(objToFix.getClass());
                if (field != null && injectors.containsKey(field.getName())) {
                    try {
                        injectors.get(field.getName()).inject(objToFix, objReferenced.getTarget());
                    } catch (Exception e) {
                        throw new JsonIoException("Error setting field while resolving references '" + field.getName() + "', @ref = " + ref.refId, e);
                    }
                }
            }
        }
        unresolvedRefs.clear();
    }

    /**
     * Process Maps/Sets (fix up their internal indexing structure)
     * This is required because Maps hash items using hashCode(), which will
     * change between VMs.  Rehashing the map fixes this.
     * <br>
     * If useMaps==true, then move @keys to keys and @items to values
     * and then drop these two entries from the map.
     * <br>
     * This hashes both Sets and Maps because the JDK sets are implemented
     * as Maps.  If you have a custom-built Set, this would not 'treat' it,
     * and you would need to provide a custom reader for that set.
     */
    private void rehashMaps() {
        for (JsonObject jsonObj : mapsToRehash) {
            jsonObj.rehashMaps();
        }
    }

    public boolean valueToTarget(JsonObject jsonObject) {
        if (jsonObject.getType() == null) {
            return false;
        }

        Class<?> javaType = jsonObject.getRawType();
        // For arrays, attempt simple type conversion.
        if (javaType.isArray() && converter.isSimpleTypeConversionSupported(javaType.getComponentType(), javaType)) {
            Object[] jsonItems = jsonObject.getItems();
            Class<?> componentType = javaType.getComponentType();
            if (jsonItems == null) {    // empty array
                jsonObject.setFinishedTarget(null, true);
                return true;
            }
            int len = jsonItems.length;
            Object javaArray = Array.newInstance(componentType, len);
            for (int i = 0; i < len; i++) {
                try {
                    Class<?> type = componentType;
                    Object item = jsonItems[i];
                    if (item instanceof JsonObject) {
                        JsonObject jObj = (JsonObject) item;
                        if (jObj.getType() != null) {
                            type = jObj.getRawType();
                        }
                    }
                    Array.set(javaArray, i, converter.convert(item, type));
                } catch (Exception e) {
                    JsonIoException jioe = new JsonIoException(e.getMessage());
                    jioe.setStackTrace(e.getStackTrace());
                    throw jioe;
                }
            }
            jsonObject.setFinishedTarget(javaArray, true);
            return true;
        }

        if (!converter.isSimpleTypeConversionSupported(javaType, javaType)) {
            return false;
        }

        try {
            Object value = converter.convert(jsonObject, javaType);
            jsonObject.setFinishedTarget(value, true);
            return true;
        } catch (Exception e) {
            JsonIoException jioe = new JsonIoException(e.getMessage());
            jioe.setStackTrace(e.getStackTrace());
            throw jioe;
        }
    }

    protected void setArrayElement(Object array, int index, Object element) {
        // Fast path: Most common case is setting to Object[] array
        if (array instanceof Object[]) {
            try {
                ((Object[])array)[index] = element;
                return;
            } catch (ArrayStoreException e) {
                // Let it fall through to the error handling below
            }
        } else {
            // For primitive arrays, use type-specific assignments to avoid boxing/unboxing
            Class<?> componentType = array.getClass().getComponentType();

            // Use if/else instead of reflection for common primitive types
            try {
                if (componentType == int.class) {
                    ((int[])array)[index] = element == null ? 0 : ((Number)element).intValue();
                    return;
                } else if (componentType == long.class) {
                    ((long[])array)[index] = element == null ? 0L : ((Number)element).longValue();
                    return;
                } else if (componentType == double.class) {
                    ((double[])array)[index] = element == null ? 0.0 : ((Number)element).doubleValue();
                    return;
                } else if (componentType == boolean.class) {
                    ((boolean[])array)[index] = element != null && (element instanceof Boolean) && (Boolean)element;
                    return;
                } else if (componentType == byte.class) {
                    ((byte[])array)[index] = element == null ? 0 : ((Number)element).byteValue();
                    return;
                } else if (componentType == char.class) {
                    if (element == null) {
                        ((char[])array)[index] = '\0';
                    } else if (element instanceof Character) {
                        ((char[])array)[index] = (Character)element;
                    } else if (element instanceof String && ((String)element).length() > 0) {
                        ((char[])array)[index] = ((String)element).charAt(0);
                    } else {
                        ((char[])array)[index] = '\0';
                    }
                    return;
                } else if (componentType == short.class) {
                    ((short[])array)[index] = element == null ? 0 : ((Number)element).shortValue();
                    return;
                } else if (componentType == float.class) {
                    ((float[])array)[index] = element == null ? 0.0f : ((Number)element).floatValue();
                    return;
                } else {
                    // For other array types, fall back to reflection
                    Array.set(array, index, element);
                    return;
                }
            } catch (ClassCastException | NullPointerException e) {
                // Let it fall through to the error handling below
            }
        }

        // Error handling
        String elementType = element == null ? "null" : element.getClass().getName();
        String arrayType = array.getClass().getComponentType().getName() + "[]";

        throw new IllegalArgumentException("Cannot set '" + elementType + "' (value: " + element +
                ") into '" + arrayType + "' at index " + index);
    }

    protected abstract Object resolveArray(Type suggestedType, List<Object> list);
}