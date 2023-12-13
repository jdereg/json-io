package com.cedarsoftware.util.io;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;
import static com.cedarsoftware.util.io.JsonObject.KEYS;

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

import com.cedarsoftware.util.ReturnType;
import com.cedarsoftware.util.io.JsonReader.MissingFieldHandler;

import lombok.AccessLevel;
import lombok.Getter;

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
    final Collection<UnresolvedReference>  unresolvedRefs = new ArrayList<>();
    final Map<Class<?>, Optional<JsonReader.JsonClassReader>> readerCache = new HashMap<>();

    private final Collection<Object[]> prettyMaps = new ArrayList<>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    protected final Collection<Missingfields> missingFields = new ArrayList<>();

    @Getter(AccessLevel.PUBLIC)
    private final ReadOptions readOptions;

    @Getter(AccessLevel.PUBLIC)
    private final ReferenceTracker references;

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
    @SuppressWarnings("FieldMayBeFinal")
    protected static class Missingfields
    {
        private Object target;
        private String fieldName;
        private Object value;

        public Missingfields(Object target, String fieldName, Object value)
        {
            this.target = target;
            this.fieldName = fieldName;
            this.value = value;
        }
    }

    /**
     * Dummy place-holder class exists only because ConcurrentHashMap cannot contain a
     * null value.  Instead, singleton instance of this class is placed where null values
     * are needed.
     */
    private static final class NullClass implements JsonReader.JsonClassReader { }

    protected Resolver(ReadOptions readOptions, ReferenceTracker references) {
        this.readOptions = readOptions;
        this.references = references;
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
    public <T> T reentrantConvertJsonValueToJava(JsonObject rootObj, Class<T> root)
    {
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
            Object instance = createInstance(root, rootObj);
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

        final Deque<JsonObject> stack = new ArrayDeque<>();
        stack.addFirst(root);

        while (!stack.isEmpty())
        {
            final JsonObject jsonObj = stack.removeFirst();

            if (jsonObj.isArray())
            {
                traverseArray(stack, jsonObj);
            }
            else if (jsonObj.isCollection())
            {
                traverseCollection(stack, jsonObj);
            }
            else if (jsonObj.isMap())
            {
                traverseMap(stack, jsonObj);
            }
            else
            {
                Object special;
                if ((special = readWithFactoryIfExists(jsonObj, null, stack)) != null)
                {
                    jsonObj.setTarget(special);
                }
                else
                {
                    traverseFields(stack, jsonObj);
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
            for (Missingfields mf : missingFields)
            {
                missingFieldHandler.fieldMissing(mf.target, mf.fieldName, mf.value);
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
            jObj.setJavaType(MetaUtils.classForName(saveType, Resolver.class.getClassLoader()));
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
     * @param clazz   Instance will be create of this class.
     * @param jsonObj Map-of-Map representation of object to create.
     * @return a new Java object of the appropriate type (clazz) using the jsonObj to provide
     * enough hints to get the right class instantiated.  It is not populated when returned.
     */
    protected Object createInstance(Class<?> clazz, JsonObject jsonObj) {
        String type = jsonObj.getJavaTypeName();

        // We can't set values to an Object, so well try to use the contained type instead
        if ("java.lang.Object".equals(type)) {   // Primitive
            Object value = jsonObj.getValue();
            if (jsonObj.keySet().size() == 1 && value != null) {
                jsonObj.setJavaType(value.getClass());
                type = value.getClass().getName();
            }
        }
        if (type == null) {   // Enum
            Object mayEnumSpecial = jsonObj.get("@enum");
            if (mayEnumSpecial instanceof String) {
                type = "java.util.EnumSet";
                jsonObj.setJavaType(MetaUtils.classForName(type, Resolver.class.getClassLoader()));
            }
        }

        // @type always takes precedence over inferred Java (clazz) type.
        if (type != null) {    // @type is explicitly set, use that as it always takes precedence
            return createInstanceUsingType(jsonObj);
        }
        else {
            return createInstanceUsingClass(clazz, jsonObj);
        }
    }

    /**
     * Create an instance of a Java class using the ".type" field on the jsonObj.  The clazz argument is not
     * used for determining type, just for clarity in an exception message.
     */
    protected Object createInstanceUsingType(JsonObject jsonObj) {
        String type = jsonObj.getJavaTypeName();
        Class<?> c;
        if (jsonObj.getJavaType() == null) {
            c = MetaUtils.classForName(type, readOptions.getClassLoader());
        }
        else {
            c = jsonObj.getJavaType();
        }
        c = coerceClassIfNeeded(c);

        // If a ClassFactory exists for a class, use it to instantiate the class.
        Object mate = createInstanceUsingClassFactory(c, jsonObj);
        if (mate != null) {
            return mate;
        }

        // Use other methods to determine the type of class to be instantiated, including looking at the
        // component type of the array.  Also, need to look at primitives, Enums, Immutable collection types.
        if (c.isArray()) {    // Handle []
            Object[] items = jsonObj.getArray();
            int size = (items == null) ? 0 : items.length;
            if (c == char[].class) {
                jsonObj.moveCharsToMate();
                mate = jsonObj.getTarget();
            }
            else {
                mate = Array.newInstance(c.getComponentType(), size);
            }
        }
        else {   // Handle regular field.object reference
            if (Primitives.isPrimitive(c)) {
                mate = MetaUtils.convert(c, jsonObj.getValue());
                jsonObj.isFinished = true;
            }
            else if (c == Class.class) {
                mate = MetaUtils.classForName((String) jsonObj.getValue(), readOptions.getClassLoader());
            }
            else if (EnumSet.class.isAssignableFrom(c)) {
                mate = extractEnumSet(c, jsonObj);
                jsonObj.isFinished = true;
            }
            else if ((mate = coerceCertainTypes(c)) != null) {   // if coerceCertainTypes() returns non-null, it did the work
            }
            else {
                // ClassFactory already consulted above, likely regular business/data classes.
                // If the newInstance(c) fails, it throws a JsonIoException.
                mate = MetaUtils.newInstance(c, null);  // can add constructor arg values
            }
        }
        jsonObj.setTarget(mate);
        return mate;
    }

    /**
     * Create an instance using the Class (clazz) provided and the values in the jsonObj.
     */
    protected Object createInstanceUsingClass(Class clazz, JsonObject jsonObj)
    {
        // If a ClassFactory exists for a class, use it to instantiate the class.  The ClassFactory
        // may optionally load the newly created instance, in which case, the JsonObject is marked finished, and
        // return.
        Object mate = createInstanceUsingClassFactory(clazz, jsonObj);
        if (mate != null)
        {
            return mate;
        }

        Object[] items = jsonObj.getArray();

        final boolean useMaps = readOptions.getReturnType() == ReturnType.JSON_OBJECTS;

        // if @items is specified, it must be an [] type.
        // if clazz.isArray(), then it must be an [] type.
        if (clazz.isArray() || (items != null && clazz == Object.class && !jsonObj.containsKey(KEYS)))
        {
            int size = (items == null) ? 0 : items.length;
            mate = Array.newInstance(clazz.isArray() ? clazz.getComponentType() : Object.class, size);
        } else if ((mate = coerceCertainTypes(clazz)) != null)
        {   // if coerceCertainTypes() returns non-null, it did the work
        }
        else if (clazz == Object.class && !useMaps)
        {
            final Class<?> unknownClass = readOptions.getUnknownTypeClass();

            if (unknownClass == null)
            {
                JsonObject jsonObject = new JsonObject();
                jsonObject.setJavaType(Map.class);
                mate = jsonObject;
            }
            else
            {
                mate = MetaUtils.newInstance(unknownClass, null);   // can add constructor arg values
            }
        }
        else
        {
            // ClassFactory consulted above, no need to check it here.
            mate = MetaUtils.newInstance(clazz, null);  // can add constructor arg values
        }

        jsonObj.setTarget(mate);
        return jsonObj.getTarget();
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
        //  If a target exists then the item has already gone through
        //  the create instance process. Don't recreate
        if (jsonObj.getTarget() != null) {
            return jsonObj.getTarget();
        }

        // If a ClassFactory exists for a class, use it to instantiate the class.  The ClassFactory
        // may optionally load the newly created instance, in which case, the JsonObject is marked finished, and
        // return.
        JsonReader.ClassFactory classFactory = readOptions.getClassFactory(c);

        if (classFactory == null) {
            return null;
        }

        Object target = classFactory.newInstance(c, jsonObj, this);

        // don't pass in classFactory.isObjectFinal, only set it to true if classFactory says its so.
        // it allows the factory iteself to set final on the jsonObj internally where it depends
        // on how the data comes back, but that value can be a hard true if the factory knows
        // its always true.
        if (classFactory.isObjectFinal()) {
            return jsonObj.setFinishedTarget(target, true);
        }

        jsonObj.setTarget(target);
        return target;
    }

    protected Class<?> coerceClassIfNeeded(Class<?> type) {
        Class clazz = readOptions.getCoercedClass(type);
        return clazz == null ? type : clazz;
    }

    protected Object coerceCertainTypes(Class<?> type)
    {
        Class clazz = readOptions.getCoercedClass(type);
        if (clazz == null)
        {
            return null;
        }

        return MetaUtils.newInstance(clazz, null);  // can add constructor arg values
    }

    protected EnumSet<?> extractEnumSet(Class c, JsonObject jsonObj)
    {
        String enumClassName = (String) jsonObj.get("@enum");
        Class enumClass = enumClassName == null ? null
                : MetaUtils.classForName(enumClassName, readOptions.getClassLoader());
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            if (enumClass != null) {
                return EnumSet.noneOf(enumClass);
            } else {
                return EnumSet.noneOf(MetaUtils.Dumpty.class);
            }
        } else if (enumClass == null) {
            throw new JsonIoException("Could not figure out Enum of the not empty set " + jsonObj);
        }

        EnumSet enumSet = null;
        for (Object item : items)
        {
            Enum enumItem;
            if (item instanceof String)
            {
                enumItem = Enum.valueOf(enumClass, (String)item);
            }
            else
            {
                JsonObject jObj = (JsonObject) item;
                enumItem = Enum.valueOf(enumClass, (String) jObj.get("name"));
            }

            if (enumSet == null)
            {   // Lazy init the EnumSet
                enumSet = EnumSet.of(enumItem);
            }
            else
            {
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
                        throw new JsonIoException("Error setting set entry of ImmutableSet '" + ref.referencingObj.getJavaTypeName() + "', @ref = " + ref.refId);
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
                Field field = MetaUtils.getField(objToFix.getClass(), ref.field);
                if (field != null)
                {
                    try
                    {
                        MetaUtils.setFieldValue(field, objToFix, objReferenced.getTarget());    // patch field here
                    }
                    catch (Exception e)
                    {
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
     * as Maps.  If you have a custom built Set, this would not 'treat' it
     * and you would need to provide a custom reader for that set.
     */
    protected void rehashMaps()
    {
        final boolean useMapsLocal = readOptions.getReturnType() == ReturnType.JSON_OBJECTS;

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
}
