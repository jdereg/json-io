package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.JsonReader.MissingFieldHandler;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;
import static com.cedarsoftware.util.io.JsonObject.KEYS;

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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
abstract class Resolver
{
    final Collection<UnresolvedReference>  unresolvedRefs = new ArrayList<>();
    protected final JsonReader reader;
    private static final NullClass nullReader = new NullClass();
    final Map<Class, JsonReader.JsonClassReaderBase> readerCache = new HashMap<>();
    private final Collection<Object[]> prettyMaps = new ArrayList<>();
    private final boolean useMaps;
    private final Object unknownClass;
    private final boolean failOnUnknownType;
    private final static Map<String, Class> coercedTypes = new LinkedHashMap<>();
    // store the missing field found during deserialization to notify any client after the complete resolution is done
    protected final Collection<Missingfields> missingFields = new ArrayList<>();
    Class<?> singletonMap = Collections.singletonMap("foo", "bar").getClass();

    static {
        coercedTypes.put("java.util.Arrays$ArrayList", ArrayList.class);
        coercedTypes.put("java.util.LinkedHashMap$LinkedKeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.LinkedHashMap$LinkedValues", ArrayList.class);
        coercedTypes.put("java.util.HashMap$KeySet", HashSet.class);
        coercedTypes.put("java.util.HashMap$Values", ArrayList.class);
        coercedTypes.put("java.util.TreeMap$KeySet", TreeSet.class);
        coercedTypes.put("java.util.TreeMap$Values", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$KeySetView", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$Values", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentHashMap$ValuesView", ArrayList.class);
        coercedTypes.put("java.util.concurrent.ConcurrentSkipListMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.concurrent.ConcurrentSkipListMap$Values", ArrayList.class);
        coercedTypes.put("java.util.IdentityHashMap$KeySet", LinkedHashSet.class);
        coercedTypes.put("java.util.IdentityHashMap$Values", ArrayList.class);
        coercedTypes.put("java.util.Collections$EmptyList", Collections.EMPTY_LIST.getClass());
    }

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
    private static final class NullClass implements JsonReader.JsonClassReaderBase  { }

    protected Resolver(JsonReader reader)
    {
        this.reader = reader;
        Map<String, Object> optionalArgs = reader.getArgs();
        optionalArgs.put(JsonReader.OBJECT_RESOLVER, this);
        useMaps = Boolean.TRUE.equals(optionalArgs.get(JsonReader.USE_MAPS));
        unknownClass = optionalArgs.containsKey(JsonReader.UNKNOWN_OBJECT) ? optionalArgs.get(JsonReader.UNKNOWN_OBJECT) : null;
        failOnUnknownType = Boolean.TRUE.equals(optionalArgs.get(JsonReader.FAIL_ON_UNKNOWN_TYPE));
    }

    protected JsonReader getReader()
    {
        return reader;
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
    protected Object convertMapsToObjects(final JsonObject<String, Object> root)
    {
        final Deque<JsonObject<String, Object>> stack = new ArrayDeque<>();
        stack.addFirst(root);

        while (!stack.isEmpty())
        {
            final JsonObject<String, Object> jsonObj = stack.removeFirst();

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
                if ((special = readIfMatching(jsonObj, null, stack)) != null)
                {
                    jsonObj.target = special;
                }
                else
                {
                    traverseFields(stack, jsonObj);
                }
            }
        }
        return root.target;
    }

    protected abstract Object readIfMatching(final Object o, final Class compType, final Deque<JsonObject<String, Object>> stack);

    public abstract void traverseFields(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj);

    protected abstract void traverseCollection(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj);

    protected abstract void traverseArray(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj);

    protected void cleanup()
    {
        patchUnresolvedReferences();
        rehashMaps();
        reader.getObjectsRead().clear();
        unresolvedRefs.clear();
        prettyMaps.clear();
        readerCache.clear();
        handleMissingFields();
    }

    // calls the missing field handler if any for each recorded missing field.
    private void handleMissingFields()
    {
        MissingFieldHandler missingFieldHandler = reader.getMissingFieldHandler();
        if (missingFieldHandler != null)
        {
            for (Missingfields mf : missingFields)
            {
                missingFieldHandler.fieldMissing(mf.target, mf.fieldName, mf.value);
            }
        }//else no handler so ignore.
    }

    /**
     * Process java.util.Map and it's derivatives.  These can be written specially
     * so that the serialization would not expose the derivative class internals
     * (internal fields of TreeMap for example).
     *
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseMap(Deque<JsonObject<String, Object>> stack, JsonObject<String, Object> jsonObj)
    {
        // Convert @keys to a Collection of Java objects.
        convertMapToKeysItems(jsonObj);
        final Object[] keys = (Object[]) jsonObj.get(KEYS);
        final Object[] items = jsonObj.getArray();

        if (keys == null || items == null)
        {
            if (keys != items)
            {
                throw new JsonIoException("Map written where one of " + KEYS + " or @items is empty");
            }
            return;
        }

        final int size = keys.length;
        if (size != items.length)
        {
            throw new JsonIoException("Map written with " + KEYS + " and @items entries of different sizes");
        }

        Object[] mapKeys = buildCollection(stack, keys, size);
        Object[] mapValues = buildCollection(stack, items, size);

        // Save these for later so that unresolved references inside keys or values
        // get patched first, and then build the Maps.
        prettyMaps.add(new Object[]{jsonObj, mapKeys, mapValues});
    }

    private static Object[] buildCollection(Deque<JsonObject<String, Object>> stack, Object[] items, int size)
    {
        final JsonObject<String, Object> jsonCollection = new JsonObject<String, Object>();
        jsonCollection.put(ITEMS, items);
        final Object[] javaKeys = new Object[size];
        jsonCollection.target = javaKeys;
        stack.addFirst(jsonCollection);
        return javaKeys;
    }

    /**
     * Convert an input JsonObject map (known to represent a Map.class or derivative) that has regular keys and values
     * to have its keys placed into @keys, and its values placed into @items.
     *
     * @param map Map to convert
     */
    protected static void convertMapToKeysItems(final JsonObject<String, Object> map)
    {
        if (!map.containsKey(KEYS) && !map.isReference())
        {
            final Object[] keys = new Object[map.size()];
            final Object[] values = new Object[map.size()];
            int i = 0;

            for (Object e : map.entrySet())
            {
                final Map.Entry entry = (Map.Entry) e;
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            String saveType = map.getType();
            map.clear();
            map.setType(saveType);
            map.put(KEYS, keys);
            map.put(ITEMS, values);
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
    protected Object createJavaObjectInstance(Class clazz, JsonObject jsonObj)
    {
        final boolean useMapsLocal = useMaps;
        String type = jsonObj.type;

        // We can't set values to an Object, so well try to use the contained type instead
        if ("java.lang.Object".equals(type))
        {
            Object value = jsonObj.get("value");
            if (jsonObj.keySet().size() == 1 && value != null)
            {
                type = value.getClass().getName();
            }
        }
        if (type == null)
        {
            Object mayEnumSpecial = jsonObj.get("@enum");
            if (mayEnumSpecial instanceof String)
            {
                type = "java.util.EnumSet";
            }
        }

        Object mate;

        // @type always takes precedence over inferred Java (clazz) type.
        if (type != null)
        {    // @type is explicitly set, use that as it always takes precedence
            Class c;
            try
            {
                c = MetaUtils.classForName(type, reader.getClassLoader(), failOnUnknownType);
            }
            catch (Exception e)
            {
                if (useMapsLocal)
                {
                    jsonObj.type = null;
                    jsonObj.target = null;
                    return jsonObj;
                }
                else
                {
                    String name = clazz == null ? "null" : clazz.getName();
                    throw new JsonIoException("Unable to create class: " + name, e);
                }
            }
            if (c.isArray())
            {    // Handle []
                Object[] items = jsonObj.getArray();
                int size = (items == null) ? 0 : items.length;
                if (c == char[].class)
                {
                    jsonObj.moveCharsToMate();
                    mate = jsonObj.target;
                }
                else
                {
                    mate = Array.newInstance(c.getComponentType(), size);
                }
            }
            else
            {    // Handle regular field.object reference
                if (MetaUtils.isPrimitive(c))
                {
                    mate = MetaUtils.convert(c, jsonObj.get("value"));
                }
                else if (c == Class.class)
                {
                    mate = MetaUtils.classForName((String) jsonObj.get("value"), reader.getClassLoader());
                }
                else if (c.isEnum())
                {
                    mate = getEnum(c, jsonObj);
                }
                else if (Enum.class.isAssignableFrom(c)) // anonymous subclass of an enum
                {
                    mate = getEnum(c.getSuperclass(), jsonObj);
                }
                else if (EnumSet.class.isAssignableFrom(c))
                {
                    mate = extractEnumSet(c, jsonObj);
                }
                else if (TimeZone.class.isAssignableFrom(c) && jsonObj.containsKey("zone"))
                {
                    mate = TimeZone.getTimeZone((String)jsonObj.get("zone"));
                }
                else if ((mate = coerceCertainTypes(c.getName())) != null)
                {   // if coerceCertainTypes() returns non-null, it did the work
                }
                else if (singletonMap.isAssignableFrom(c))
                {
                    Object key = jsonObj.keySet().iterator().next();
                    Object value = jsonObj.values().iterator().next();
                    mate = Collections.singletonMap(key, value);
                }
                else if (!c.getName().startsWith("java.util.Immutable"))
                {
                    mate = newInstance(c, jsonObj);
                }
                else if (c.getName().contains("Set"))
                {
                    mate = new ArrayList<>();
                }
                else if (c.getName().contains("List"))
                {
                    mate = new ArrayList<>();
                }
                else if (c.getName().contains("Map"))
                {
                    mate = new LinkedHashMap<>();
                }
            }
        }
        else
        {    // @type, not specified, figure out appropriate type
            Object[] items = jsonObj.getArray();

            // if @items is specified, it must be an [] type.
            // if clazz.isArray(), then it must be an [] type.
            if (clazz.isArray() || (items != null && clazz == Object.class && !jsonObj.containsKey(KEYS)))
            {
                int size = (items == null) ? 0 : items.length;
                mate = Array.newInstance(clazz.isArray() ? clazz.getComponentType() : Object.class, size);
            }
            else if (clazz.isEnum())
            {
                mate = getEnum(clazz, jsonObj);
            }
            else if (Enum.class.isAssignableFrom(clazz)) // anonymous subclass of an enum
            {
                mate = getEnum(clazz.getSuperclass(), jsonObj);
            }
            else if (EnumSet.class.isAssignableFrom(clazz)) // anonymous subclass of an enum
            {
                mate = extractEnumSet(clazz, jsonObj);
            }
            else if ((mate = coerceCertainTypes(clazz.getName())) != null)
            {   // if coerceCertainTypes() returns non-null, it did the work
            }
            else if (clazz == Object.class && !useMapsLocal)
            {
                if (unknownClass == null)
                {
                    mate = new JsonObject();
                    ((JsonObject)mate).type = Map.class.getName();
                }
                else if (unknownClass instanceof String)
                {
                    mate = newInstance(MetaUtils.classForName(((String)unknownClass).trim(), reader.getClassLoader()), jsonObj);
                }
                else
                {
                    throw new JsonIoException("Unable to determine object type at column: " + jsonObj.col + ", line: " + jsonObj.line + ", content: " + jsonObj);
                }
            }
            else
            {
                mate = newInstance(clazz, jsonObj);
            }
        }
        jsonObj.target = mate;
        return jsonObj.target;
    }

    protected Object coerceCertainTypes(String type)
    {
        Class clazz = coercedTypes.get(type);
        if (clazz == null)
        {
            return null;
        }

        return MetaUtils.newInstance(clazz);
    }

    protected JsonObject getReferencedObj(Long ref)
    {
        JsonObject refObject = reader.getObjectsRead().get(ref);
        if (refObject == null)
        {
            throw new JsonIoException("Forward reference @ref: " + ref + ", but no object defined (@id) with that value");
        }
        return refObject;
    }

    protected JsonReader.JsonClassReaderBase getCustomReader(Class c)
    {
        JsonReader.JsonClassReaderBase reader = readerCache.get(c);
        if (reader == null)
        {
            reader = forceGetCustomReader(c);
            readerCache.put(c, reader);
        }
        return reader == nullReader ? null : reader;
    }

    private JsonReader.JsonClassReaderBase forceGetCustomReader(Class c)
    {
        JsonReader.JsonClassReaderBase closestReader = nullReader;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class, JsonReader.JsonClassReaderBase> entry : getReaders().entrySet())
        {
            Class clz = entry.getKey();
            if (clz == c)
            {
                return entry.getValue();
            }
            int distance = MetaUtils.getDistance(clz, c);
            if (distance < minDistance)
            {
                minDistance = distance;
                closestReader = entry.getValue();
            }
        }
        return closestReader;
    }

    /**
     * Fetch enum value (may need to try twice, due to potential 'name' field shadowing by enum subclasses
     */
    private Object getEnum(Class c, JsonObject jsonObj)
    {
        try
        {
            return Enum.valueOf(c, (String) jsonObj.get("name"));
        }
        catch (Exception e)
        {   // In case the enum class has it's own 'name' member variable (shadowing the 'name' variable on Enum)
            return Enum.valueOf(c, (String) jsonObj.get("java.lang.Enum.name"));
        }
    }

    private static enum OneEnum {
        L1,
        L2,
        L3
    }
    /*
    /* java 17 don't allow to call reflect on internal java api like EnumSet's implement, so need to create like this
     */
    private EnumSet getEmptyEnumSet() {
        return EnumSet.noneOf(OneEnum.class);
    }

    /**
     * Create the EnumSet with its values (it must be created this way)
     */
    private Object getEnumSet(Class c, JsonObject<String, Object> jsonObj)
    {
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            return getEmptyEnumSet();
            //return newInstance(c, jsonObj);
        }
        JsonObject item = (JsonObject) items[0];
        String type = item.getType();
        Class enumClass = MetaUtils.classForName(type, reader.getClassLoader());
        EnumSet enumSet = null;
        for (Object objectItem : items)
        {
            item = (JsonObject) objectItem;
            Enum enumItem = (Enum) getEnum(enumClass, item);
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

    protected EnumSet<?> extractEnumSet(Class c, JsonObject<String, Object> jsonObj)
    {
        String enumClassName = (String) jsonObj.get("@enum");
        Class enumClass = enumClassName == null ? null
			: MetaUtils.classForName(enumClassName, reader.getClassLoader());
        Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
			if (enumClass != null)
			{
				return EnumSet.noneOf(enumClass);
			}
			else
			{
				return EnumSet.noneOf(MetaUtils.Dumpty.class);
			}
        }
		else if (enumClass == null)
		{
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
                JsonObject jsItem = (JsonObject) item;
                enumItem = Enum.valueOf(enumClass, (String) jsItem.get("name"));
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
            Object objToFix = ref.referencingObj.target;
            JsonObject objReferenced = reader.getObjectsRead().get(ref.refId);

            if (ref.index >= 0)
            {    // Fix []'s and Collections containing a forward reference.
                if (objToFix instanceof List)
                {   // Patch up Indexable Collections
                    List list = (List) objToFix;
                    list.set(ref.index, objReferenced.target);
                    String containingTypeName = ref.referencingObj.type;
                    if (containingTypeName != null && containingTypeName.startsWith("java.util.Immutable") && containingTypeName.contains("List"))
                    {
                        if (list.stream().noneMatch(c -> c == null || c instanceof JsonObject))
                        {
                            list = List.of(list.toArray());
                            ref.referencingObj.target = list;
                        }
                    }
                }
                else if (objToFix instanceof Collection)
                {
                    String containingTypeName = ref.referencingObj.type;
                    Collection col = (Collection) objToFix;
                    if (containingTypeName != null && containingTypeName.startsWith("java.util.Immutable") && containingTypeName.contains("Set"))
                    {
                        throw new JsonIoException("Error setting set entry of ImmutableSet '" + ref.referencingObj.type + "', @ref = " + ref.refId);
                    }
                    else
                    {
                        // Add element (since it was not indexable, add it to collection)
                        col.add(objReferenced.target);
                    }
                }
                else
                {
                    Array.set(objToFix, ref.index, objReferenced.target);        // patch array element here
                }
            }
            else
            {    // Fix field forward reference
                Field field = MetaUtils.getField(objToFix.getClass(), ref.field);
                if (field != null)
                {
                    try
                    {
                        field.set(objToFix, objReferenced.target);               // patch field here
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
     * and you would need to provider a custom reader for that set.
     */
    protected void rehashMaps()
    {
        final boolean useMapsLocal = useMaps;
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
                map = (Map) jObj.target;
                javaKeys = (Object[]) mapPieces[1];
                javaValues = (Object[]) mapPieces[2];
                jObj.clear();
            }

            if (singletonMap.isAssignableFrom(map.getClass()))
            {   // Handle SingletonMaps - Do nothing here.  They are single entry maps.  The code before here
                // has already instantiated the SingletonMap, and has filled in its values.
            }
            else
            {
                int j = 0;

                while (javaKeys != null && j < javaKeys.length)
                {
                    map.put(javaKeys[j], javaValues[j]);
                    j++;
                }
            }
        }
    }

    // ========== Keep relationship knowledge below the line ==========
    public static Object newInstance(Class c, JsonObject jsonObject)
    {
        return JsonReader.newInstance(c, jsonObject);
    }

    protected Map<Class, JsonReader.JsonClassReaderBase> getReaders()
    {
        return reader.readers;
    }

    protected boolean notCustom(Class cls)
    {
        return reader.notCustom.contains(cls);
    }
}
