package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cedarsoftware.io.JsonReader.MissingFieldHandler;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.convert.Converter;
import lombok.AccessLevel;
import lombok.Getter;

import static com.cedarsoftware.io.JsonObject.ITEMS;
import static com.cedarsoftware.io.JsonObject.KEYS;

/**
 * This class is used to convert a source of Java Maps that were created from
 * the JsonParser.  These are in 'raw' form with no 'pointers'.  This code will
 * reconstruct the 'shape' of the graph by connecting @ref's to @ids.
 *
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
 *         limitations under the License.*
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class Resolver implements ReaderContext
{
    private static final String NO_FACTORY = "_︿_ψ_☼";
    final Collection<UnresolvedReference>  unresolvedRefs = new ArrayList<>();
    final Map<Class<?>, Optional<JsonReader.JsonClassReader>> readerCache = new HashMap<>();

    private final Collection<Object[]> prettyMaps = new ArrayList<>();

    @Getter(AccessLevel.PUBLIC)
    private final ReadOptions readOptions;

    @Getter(AccessLevel.PUBLIC)
    private final ReferenceTracker references;

    @Getter(AccessLevel.PUBLIC)
    private final Converter converter;
    
    /**
     * UnresolvedReference is created to hold a logical pointer to a reference that
     * could not yet be loaded, as the @ref appears ahead of the referenced object's
     * definition.  This can point to a field reference or an array/Collection element reference.
     */
    static final class UnresolvedReference
    {
        private final JsonObject referencingObj;
        private String field;
        private final long refId;
        private int index = -1;

        UnresolvedReference(JsonObject referrer, String fld, long id)
        {
            referencingObj = referrer;
            field = fld;
            refId = id;
        }

        UnresolvedReference(JsonObject referrer, int idx, long id)
        {
            referencingObj = referrer;
            index = idx;
            refId = id;
        }
    }

    /**
     * stores missing fields information to notify client after the complete deserialization resolution
     */

    protected Object target;
    protected String fieldName;
    protected Object value;

    protected final Collection<Object[]> missingFields = new ArrayList<>();
    public void addMissingField(Object target, String missingField, Object value) {
        missingFields.add(new Object[]{target, missingField, value});
    }

    protected Resolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        this.readOptions = readOptions;
        this.references = references;
        this.converter = converter;
    }

    /**
     * This method converts a rootObj Map, (which contains nested Maps
     * and so forth representing a Java Object graph), to a Java
     * object instance.  The rootObj map came from using the JsonReader
     * to parse a JSON graph (using the API that puts the graph
     * into Maps, not the typed representation).
     * @param rootObj JsonObject instance that was the rootObj object from the
     * @param root When you know the type you will be returning.  Can be null (effectively Map.class)
     * JSON input that was parsed in an earlier call to JsonReader.
     * @return a typed Java instance that was serialized into JSON.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T reentrantConvertJsonValueToJava(JsonObject rootObj, Class<T> root) {
        if (rootObj == null) {
            return null;
        }

        if (rootObj.isReference()) {
            rootObj = this.getReferences().get(rootObj);
        }

        T graph;
        if (rootObj.isFinished) {   // Called on a JsonObject that has already been converted
            graph = (T) rootObj.getTarget();
        } else {
            rootObj.setHintType(root);
            Object instance = rootObj.getTarget() == null ? createInstance(rootObj) : rootObj.getTarget();
            if (rootObj.isFinished) {   // Factory method instantiated and completely loaded the object.
                graph = (T) instance;
            } else {
                graph = convertJsonValuesToJava(rootObj);
            }
        }
        return graph;
    }

    /**
     * Walk a JsonObject (Map of String keys to values) and return the
     * Java object equivalent filled in as best as possible (everything
     * except unresolved reference fields or unresolved array/collection elements).
     *
     * @param root JsonObject reference to a Map-of-Maps representation of the JSON
     *             input after it has been completely read.
     * @return Properly constructed, typed, Java object graph built from a Map
     * of Maps representation (JsonObject root).
     */
    protected <T> T convertJsonValuesToJava(final JsonObject root)
    {
        if (root.isFinished) {
            return (T) root.getTarget();
        }

        if (true) {
            final Deque<JsonObject> stack = new ArrayDeque<>();
            stack.addFirst(root);

            while (!stack.isEmpty()) {
                final JsonObject jsonObj = stack.removeFirst();
                if (jsonObj.isFinished) {
                    continue;
                }
                if (jsonObj.isArray()) {
                    traverseArray(stack, jsonObj);
                } else if (jsonObj.isCollection()) {
                    traverseCollection(stack, jsonObj);
                } else if (jsonObj.isMap()) {
                    traverseMap(stack, jsonObj);
                } else {
                    Object special;
                    if ((special = readWithFactoryIfExists(jsonObj, null, stack)) != null) {
                        jsonObj.setTarget(special);
                    } else {
                        traverseFields(stack, jsonObj);
                    }
                }
            }
        }
        return (T) root.getTarget();
    }

    protected abstract Object readWithFactoryIfExists(final Object o, final Class compType, final Deque<JsonObject> stack);

    protected abstract void traverseCollection(Deque<JsonObject> stack, JsonObject jsonObj);

    protected abstract void traverseArray(Deque<JsonObject> stack, JsonObject jsonObj);

    protected void cleanup()
    {
        patchUnresolvedReferences();
        rehashMaps();
        if (references != null) {
            references.clear();
        }
        unresolvedRefs.clear();
        prettyMaps.clear();
        readerCache.clear();
        handleMissingFields();
    }

    // calls the missing field handler if any for each recorded missing field.
    private void handleMissingFields()
    {
        MissingFieldHandler missingFieldHandler = this.readOptions.getMissingFieldHandler();
        if (missingFieldHandler != null)
        {
            for (Object[] mf : missingFields)
            {
                missingFieldHandler.fieldMissing(mf[0], (String) mf[1], mf[2]);
            }
        }//else no handler so ignore.
    }

    /**
     * Process java.util.Map and it's derivatives.  These are written specially
     * so that the serialization does not expose the class internals
     * (internal fields of TreeMap for example).
     *
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseMap(Deque<JsonObject> stack, JsonObject jsonObj)
    {
        // Convert @keys to a Collection of Java objects.
        convertMapToKeysItems(jsonObj);
        final Object[] keys = (Object[]) jsonObj.get(KEYS);
        final Object[] items = jsonObj.getArray();

        if (keys == null || items == null)
        {
            if (keys != items)
            {
                throw new JsonIoException("Unbalanced Object in JSON, it has " + KEYS + " or " + ITEMS + " empty");
            }
            return;
        }

        final int size = keys.length;
        if (size != items.length)
        {
            throw new JsonIoException("Map written with " + KEYS + " and " + ITEMS + "s entries of different sizes");
        }

        buildCollection(stack, keys);
        buildCollection(stack, items);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        prettyMaps.add(new Object[]{jsonObj, keys, items});
    }

    private static void buildCollection(Deque<JsonObject> stack, Object[] arrayContent)
    {
        final JsonObject collection = new JsonObject();
        collection.put(ITEMS, arrayContent);
        collection.setTarget(arrayContent);
        stack.addFirst(collection);
    }

    /**
     * Convert an input JsonObject map (known to represent a Map.class or derivative) that has regular keys and values
     * to have its keys placed into @keys, and its values placed into @items.
     *
     * @param jObj Map to convert
     */
    protected static void convertMapToKeysItems(final JsonObject jObj)
    {
        if (!jObj.containsKey(KEYS) && !jObj.isReference())
        {
            final Object[] keys = new Object[jObj.size()];
            final Object[] values = new Object[jObj.size()];
            int i = 0;

            for (Object e : jObj.entrySet())
            {
                final Map.Entry entry = (Map.Entry) e;
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            String saveType = jObj.getJavaTypeName();
            jObj.clear();
            jObj.setJavaType(ClassUtilities.forName(saveType, Resolver.class.getClassLoader()));
            jObj.put(KEYS, keys);
            jObj.put(ITEMS, values);
        }
    }

    /**
     * This method creates a Java Object instance based on the passed in parameters.
     * If the JsonObject contains a key '@type' then that is used, as the type was explicitly
     * set in the JSON stream.  If the key '@type' does not exist, then the passed in Class
     * is used to create the instance, handling creating an Array or regular Object
     * instance.
     * <br>
     * The '@type' is not often specified in the JSON input stream, as in many
     * cases it can be inferred from a field reference or array component type.
     *
     * @param jsonObj Map-of-Map representation of object to create.
     * @return a new Java object of the appropriate type (clazz) using the jsonObj to provide
     * enough hints to get the right class instantiated.  It is not populated when returned.
     */
    protected Object createInstance(JsonObject jsonObj) {
        // Coerce class first
        Object target = jsonObj.getTarget();
        if (target != null) {
            // TODO: The way this is reached, is always from a traverse* method.  Meaning, once we remove the
            // TODO: traverse at the end of parsing, we will not need this "if" check here.
            return target;
        }
        Class targetType = jsonObj.getJavaType();
        jsonObj.setJavaType(coerceClassIfNeeded(targetType));
        targetType = jsonObj.getJavaType();

        // Does a built-in conversion exist?
        if (jsonObj.hasValue() && jsonObj.getValue() != null) {
            if (converter.isConversionSupportedFor(jsonObj.getValue().getClass(), targetType)) {
//                System.out.println("jsonObj.getValue() = " + jsonObj.getValue());
                Object value = this.getConverter().convert(jsonObj.getValue(), targetType);
                return jsonObj.setFinishedTarget(value, true);
            }
            //  TODO: Handle primitives that are written as map with conversion logic (no refs on these types, I think)
            //  TODO: I'd like to have all types that have mpa conversion supported here, but we have an issue with refs
            //  TODO: going to the converter and I'm still thinking of a way to handle that.
        } else if (MetaUtils.isLogicalPrimitive(targetType) && this.getConverter().isConversionSupportedFor(Map.class, targetType)) {
            Object source = resolveRefs(jsonObj);
            Object value = this.getConverter().convert(source, targetType);
            return jsonObj.setFinishedTarget(value, true);
        }

        // ClassFactory defined
        Object mate = createInstanceUsingClassFactory(jsonObj.getJavaType(), jsonObj);
        if (mate != NO_FACTORY) {
            return mate;
        }
        // TODO: Additional Factory Classes: EnumSet

        // EnumSet
        Object mayEnumSpecial = jsonObj.get("@enum");
        if (mayEnumSpecial instanceof String) {
            // TODO: This should move to EnumSetFactory - Both creating the enum and extracting the enumSet.
            Class<?> clazz = ClassUtilities.forName((String) mayEnumSpecial, Resolver.class.getClassLoader());
            mate = extractEnumSet(clazz, jsonObj);
            jsonObj.setTarget(mate);
            jsonObj.isFinished = true;
            return mate;
        }

        // Arrays
        Class<?> c = jsonObj.getJavaType();
        Object[] items = jsonObj.getArray();
        if (c != null && (c.isArray() || (items != null && c == Object.class && !jsonObj.containsKey(KEYS)))) {    // Handle []
            int size = (items == null) ? 0 : items.length;
            mate = Array.newInstance(c.isArray() ? c.getComponentType() : Object.class, size);
            // TODO: Process array elements NOW (not later)
            jsonObj.setTarget(mate);
            return mate;
        }
        
        return createInstanceUsingType(jsonObj);
    }

    /**
     * Create an instance of a Java class using the ".type" field on the jsonObj.  The clazz argument is not
     * used for determining type, just for clarity in an exception message.
     * TODO: These instances are not all LOADED yet, so that is why they are not in the main createInstance()
     * TODO: method.  As they are loaded, they will move up.  Also, pulling primitives, class, and others into
     * TODO: factories will shrink this to just unknown generic classes, Object[]'s, and Collections of such.
     */
    protected Object createInstanceUsingType(JsonObject jsonObj) {
        InstanceCreator instanceCreator = new InstanceCreator(readOptions);
        return instanceCreator.createInstanceUsingType(jsonObj);
    }

    /**
     * If a ClassFactory is associated to the passed in Class (clazz), then use the ClassFactory
     * to create an instance.  If a ClassFactory create the instance, it may optionall load
     * the values into the instance, using the values from the passed in JsonObject.  If the
     * ClassFactory instance creates AND loads the object, it is indicated on the ClassFactory
     * by the isObjectFinal() method returning true.  Therefore, the JsonObject instance that is
     * loaded, is marked with 'isFinished=true' so that no more process is needed for this instance.
     */
    Object createInstanceUsingClassFactory(Class c, JsonObject jsonObj)
    {
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

        jsonObj.setTarget(target);
        return target;
    }

    protected Class<?> coerceClassIfNeeded(Class<?> type) {
        if (type == null) {
            return null;
        }
        Class clazz = readOptions.getCoercedClass(type);
        return clazz == null ? type : clazz;
    }

    protected EnumSet<?> extractEnumSet(Class c, JsonObject jsonObj)
    {
        String enumClassName = (String) jsonObj.get("@enum");
        Class enumClass = enumClassName == null ? null
                : ClassUtilities.forName(enumClassName, readOptions.getClassLoader());
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0) {
            if (enumClass != null) {
                return EnumSet.noneOf(enumClass);
            } else {
                return EnumSet.noneOf(MetaUtils.Dumpty.class);
            }
        } else if (enumClass == null) {
            throw new JsonIoException("Could not figure out Enum of the not empty set " + jsonObj);
        }

        EnumSet enumSet = null;
        for (Object item : items) {
            Enum enumItem;
            if (item instanceof String) {
                enumItem = Enum.valueOf(enumClass, (String) item);
            } else {
                JsonObject jObj = (JsonObject) item;
                enumItem = Enum.valueOf(enumClass, (String) jObj.get("name"));
            }

            if (enumSet == null) {   // Lazy init the EnumSet
                enumSet = EnumSet.of(enumItem);
            } else {
                enumSet.add(enumItem);
            }
        }
        return enumSet;
    }

    /**
     * For all fields where the value was "@ref":"n" where 'n' was the id of an object
     * that had not yet been encountered in the stream, make the final substitution.
     */
    protected void patchUnresolvedReferences()
    {
        Iterator i = unresolvedRefs.iterator();
        while (i.hasNext())
        {
            UnresolvedReference ref = (UnresolvedReference) i.next();
            Object objToFix = ref.referencingObj.getTarget();
            JsonObject objReferenced = this.references.get(ref.refId);

            if (ref.index >= 0)
            {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List)
                {   // Patch up Indexable Collections
                    List list = (List) objToFix;
                    list.set(ref.index, objReferenced.getTarget());
                    String containingTypeName = ref.referencingObj.getJavaTypeName();
                    if (containingTypeName != null && containingTypeName.startsWith("java.util.Immutable") && containingTypeName.contains("List"))
                    {
                        if (list.stream().noneMatch(c -> c == null || c instanceof JsonObject))
                        {
                            list = MetaUtils.listOf(list.toArray());
                            ref.referencingObj.setTarget(list);
                        }
                    }
                }
                else if (objToFix instanceof Collection)
                {
                    String containingTypeName = ref.referencingObj.getJavaTypeName();
                    Collection col = (Collection) objToFix;
                    if (containingTypeName != null && containingTypeName.startsWith("java.util.Immutable") && containingTypeName.contains("Set"))
                    {
                        String typeName = ref.referencingObj.getJavaTypeName();
                        long refId = ref.refId;
                        String errorMessage = "Error setting set entry of ImmutableSet '" + typeName + "', @ref = " + refId;
                        throw new JsonIoException(errorMessage);
                    }
                    else
                    {
                        // Add element (since it was not indexable, add it to collection)
                        col.add(objReferenced.getTarget());
                    }
                }
                else
                {
                    Array.set(objToFix, ref.index, objReferenced.getTarget());        // patch array element here
                }
            }
            else
            {    // Fix field forward reference
                Field field = getReadOptions().getDeepDeclaredFields(objToFix.getClass()).get(ref.field);
                if (field != null)
                {
                    try
                    {
                        MetaUtils.setFieldValue(field, objToFix, objReferenced.getTarget());    // patch field here
                    }
                    catch (Exception e)
                    {
                        String fieldName = field.getName();
                        long refId = ref.refId;
                        String errorMessage = "Error setting field while resolving references '" + fieldName + "', @ref = " + refId;
                        throw new JsonIoException(errorMessage, e);
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
     * as Maps.  If you have a custom built Set, this would not 'treat' it
     * and you would need to provide a custom reader for that set.
     */
    protected void rehashMaps()
    {
        final boolean useMapsLocal = readOptions.isReturningJsonObjects();

        for (Object[] mapPieces : prettyMaps)
        {
            JsonObject jObj = (JsonObject) mapPieces[0];
            Object[] javaKeys, javaValues;
            Map map;

            if (useMapsLocal)
            {   // Make the @keys be the actual keys of the map.
                map = jObj;
                javaKeys = (Object[]) jObj.remove(KEYS);
                javaValues = (Object[]) jObj.remove(ITEMS);
            }
            else
            {
                map = (Map) jObj.getTarget();
                javaKeys = (Object[]) mapPieces[1];
                javaValues = (Object[]) mapPieces[2];
                jObj.clear();
            }

            int j = 0;

            while (javaKeys != null && j < javaKeys.length)
            {
                map.put(javaKeys[j], javaValues[j]);
                j++;
            }
        }
    }

    protected JsonObject resolveRefs(JsonObject jsonObject) {

        JsonObject object = references.get(jsonObject);


        return object;
    }
}
