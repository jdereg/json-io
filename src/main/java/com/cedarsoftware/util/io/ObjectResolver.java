package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

import static com.cedarsoftware.util.io.JsonObject.ITEMS;
import static com.cedarsoftware.util.io.JsonObject.KEYS;

/**
 * <p>The ObjectResolver converts the raw Maps created from the JsonParser to Java
 * objects (a graph of Java instances).  The Maps have an optional type entry associated
 * to them to indicate what Java peer instance to create.  The reason type is optional
 * is because it can be inferred in a couple instances.  A non-primitive field that
 * points to an object that is of the same type of the field, does not require the
 * '@type' because it can be inferred from the field.  This is not always the case.
 * For example, if a Person field points to an Employee object (where Employee is a
 * subclass of Person), then the resolver cannot create an instance of the field type
 * (Person) because this is not the proper type.  (It had an Employee record with more
 * fields in this example). In this case, the writer recognizes that the instance type
 * and field type are not the same and therefore it writes the @type.
 * </p><p>
 * A similar case as above occurs with specific array types.  If there is a Person[]
 * containing Person and Employee instances, then the Person instances will not have
 * the '@type' but the employee instances will (because they are more derived than Person).
 * </p><p>
 * The resolver 'wires' the original object graph.  It does this by replacing
 * '@ref' values in the Maps with pointers (on the field of the associated instance of the
 * Map) to the object that has the same ID.  If the object has not yet been read, then
 * an UnresolvedReference is created.  These are back-patched at the end of the resolution
 * process.  UnresolvedReference keeps track of what field or array element the actual value
 * should be stored within, and then locates the object (by id), and updates the appropriate
 * value.
 * </p>
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
 *         limitations under the License.
 */
@SuppressWarnings({ "rawtypes", "unchecked", "Convert2Diamond" })
public class ObjectResolver extends Resolver
{
    private final ClassLoader classLoader;
    protected JsonReader.MissingFieldHandler missingFieldHandler;

    /**
     * Constructor
     * @param reader JsonReader instance being used
     * @param classLoader ClassLoader that was set in the passed in 'options' arguments to JsonReader.
     */
    protected ObjectResolver(JsonReader reader, ClassLoader classLoader)
    {
        super(reader);
        this.classLoader = classLoader;
        missingFieldHandler = reader.getMissingFieldHandler();
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final Deque<JsonObject<String, Object>> stack, final JsonObject<String, Object> jsonObj)
    {
        this.traverseFields(stack, jsonObj, JsonWriter.EMPTY_SET);
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final Deque<JsonObject<String, Object>> stack, final JsonObject<String, Object> jsonObj, Set<String> excludeFields)
    {
        final Object javaMate = jsonObj.target;
        final Iterator<Map.Entry<String, Object>> i = jsonObj.entrySet().iterator();
        final Class cls = javaMate.getClass();

        while (i.hasNext())
        {
            Map.Entry<String, Object> e = i.next();
            String key = e.getKey();
            final Field field = MetaUtils.getField(cls, key);
            Object rhs = e.getValue();
            if (field != null)
            {
                assignField(stack, jsonObj, field, rhs);
            }
            else if (missingFieldHandler != null)
            {
                handleMissingField(stack, jsonObj, rhs, key);
            }//else no handler so ignore.
        }
    }

    static boolean isBasicWrapperType(Class clazz)
    {
        return clazz == Boolean.class || clazz == Integer.class ||
            clazz == Short.class || clazz == Character.class ||
            clazz == Byte.class || clazz == Long.class ||
            clazz == Double.class || clazz == Float.class;
    }

    /**
     * Map Json Map object field to Java object field.
     *
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param field   a Java Field object representing where the jsonObj should be converted and stored.
     * @param rhs     the JSON value that will be converted and stored in the 'field' on the associated
     *                Java target object.
     */
    protected void assignField(final Deque<JsonObject<String, Object>> stack, final JsonObject jsonObj,
                               final Field field, final Object rhs)
    {
        final Object target = jsonObj.target;
        final Class targetClass = target.getClass();
        try
        {
            final Class fieldType = field.getType();
            if (rhs == null)
            {   // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                if (fieldType.isPrimitive())
                {
                    if(isBasicWrapperType(targetClass)) {
                        jsonObj.target = MetaUtils.convert(fieldType, "0");
                    } else {
                        field.set(target, MetaUtils.convert(fieldType, "0"));
                    }
                }
                else
                {
                    field.set(target, null);
                }
                return;
            }

            // If there is a "tree" of objects (e.g, Map<String, List<Person>>), the subobjects may not have an
            // @type on them, if the source of the JSON is from JSON.stringify().  Deep traverse the args and
            // mark @type on the items within the Maps and Collections, based on the parameterized type (if it
            // exists).
            if (rhs instanceof JsonObject)
            {
                if (field.getGenericType() instanceof ParameterizedType)
                {   // Only JsonObject instances could contain unmarked objects.
                    markUntypedObjects(field.getGenericType(), rhs, MetaUtils.getDeepDeclaredFields(fieldType));
                }

                // Ensure .type field set on JsonObject
                final JsonObject job = (JsonObject) rhs;
                final String type = job.type;
                if (type == null || type.isEmpty())
                {
                    job.setType(fieldType.getName());
                }
            }

            Object special;
            if (rhs == JsonParser.EMPTY_OBJECT)
            {
                final JsonObject jObj = new JsonObject();
                jObj.type = fieldType.getName();
                Object value = createJavaObjectInstance(fieldType, jObj);
                field.set(target, value);
            }
            else if ((special = readIfMatching(rhs, fieldType, stack)) != null)
            {
                if (Enum.class.isAssignableFrom(fieldType) && special instanceof String) {
                    field.set(target, Enum.valueOf(fieldType, (String) special));
                    //TODO enum class create a field also named : "name"? that's not good rule, so will not consider that
                } else if (Enum.class.isAssignableFrom(field.getDeclaringClass()) && "name".equals(field.getName())) {
                    //no need to set for this case
                } else {
                    field.set(target, special);
                }
            }
            else if (rhs.getClass().isArray())
            {    // LHS of assignment is an [] field or RHS is an array and LHS is Object
                final Object[] elements = (Object[]) rhs;
                JsonObject<String, Object> jsonArray = new JsonObject<String, Object>();
                if (char[].class == fieldType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF8 strings for compactness and speed.
                    if (elements.length == 0)
                    {
                        field.set(target, new char[]{});
                    }
                    else
                    {
                        field.set(target, ((String) elements[0]).toCharArray());
                    }
                }
                else
                {
                    jsonArray.put(ITEMS, elements);
                    createJavaObjectInstance(fieldType, jsonArray);
                    field.set(target, jsonArray.target);
                    stack.addFirst(jsonArray);
                }
            }
            else if (rhs instanceof JsonObject)
            {
                final JsonObject<String, Object> jsRhs = (JsonObject) rhs;
                final Long ref = jsRhs.getReferenceId();

                if (ref != null)
                {    // Correct field references
                    final JsonObject refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        field.set(target, refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, field.getName(), ref));
                    }
                }
                else
                {    // Assign ObjectMap's to Object (or derived) fields
                    Object fieldObject = createJavaObjectInstance(fieldType, jsRhs);
                    field.set(target, fieldObject);
                    if (!MetaUtils.isLogicalPrimitive(jsRhs.getTargetClass()))
                    {
                        // GOTCHA : if the field is an immutable collection,
                        // "work instance", where one can accumulate items in (ArrayList)
                        // and "final instance' (say List.of() ) can _not_ be the same.
                        // So, the later the assignment, the better.
                        Object javaObj = convertMapsToObjects(jsRhs);
                        if (javaObj != fieldObject)
                        {
                            field.set(target, javaObj);
                        }
                    }
                }
            }
            else
            {
                if (MetaUtils.isPrimitive(fieldType))
                {
                    if(isBasicWrapperType(targetClass)) {
                        jsonObj.target = MetaUtils.convert(fieldType, rhs);
                    } else {
                        field.set(target, MetaUtils.convert(fieldType, rhs));
                    }
                }
                else if (rhs instanceof String && "".equals(((String) rhs).trim()) && fieldType != String.class)
                {   // Allow "" to null out a non-String field
                    field.set(target, null);
                }
                else
                {
                    field.set(target, rhs);
                }
            }
        }
        catch (Exception e)
        {
            String message = e.getClass().getSimpleName() + " setting field '" + field.getName() + "' on target: " + safeToString(target) + " with value: " + rhs;
            if (MetaUtils.loadClassException != null)
            {
                message += " Caused by: " + MetaUtils.loadClassException + " (which created a LinkedHashMap instead of the desired class)";
            }
            throw new JsonIoException(message, e);
        }
    }

    /**
     * Try to create a java object from the missing field.
	 * Mostly primitive types and jsonObject that contains @type attribute will
	 * be candidate for the missing field callback, others will be ignored. 
	 * All missing field are stored for later notification
     *
     * @param stack Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param rhs the JSON value that will be converted and stored in the 'field' on the associated Java target object.
     * @param missingField name of the missing field in the java object.
     */
    protected void handleMissingField(final Deque<JsonObject<String, Object>> stack, final JsonObject jsonObj, final Object rhs,
                                      final String missingField)
    {
        final Object target = jsonObj.target;
        try
        {
            if (rhs == null)
            { // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                storeMissingField(target, missingField, null);
                return;
            }

            // we have a jsonobject with a type
            Object special;
            if (rhs == JsonParser.EMPTY_OBJECT)
            {
                storeMissingField(target, missingField, null);
            }
            else if ((special = readIfMatching(rhs, null, stack)) != null)
            {
                storeMissingField(target, missingField, special);
            }
            else if (rhs.getClass().isArray())
            {
                // impossible to determine the array type.
                storeMissingField(target, missingField, null);
            }
            else if (rhs instanceof JsonObject)
            {
                final JsonObject<String, Object> jObj = (JsonObject) rhs;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                { // Correct field references
                    final JsonObject refObject = getReferencedObj(ref);
                    storeMissingField(target, missingField, refObject.target);
                }
                else
                {   // Assign ObjectMap's to Object (or derived) fields
                    // check that jObj as a type
                    if (jObj.getType() != null)
                    {
                        Object createJavaObjectInstance = createJavaObjectInstance(null, jObj);
                        if (!MetaUtils.isLogicalPrimitive(jObj.getTargetClass()))
                        {
                            stack.addFirst((JsonObject) rhs);
                        }
                        storeMissingField(target, missingField, createJavaObjectInstance);
                    } 
                    else //no type found, just notify.
                    {
                        storeMissingField(target, missingField, null);
                    }
                }
            }
            else
            {
                storeMissingField(target, missingField, rhs);
            }
        }
        catch (Exception e)
        {
            String message = e.getClass().getSimpleName() + " missing field '" + missingField + "' on target: "
                    + safeToString(target) + " with value: " + rhs;
            if (MetaUtils.loadClassException != null)
            {
                message += " Caused by: " + MetaUtils.loadClassException
                        + " (which created a LinkedHashMap instead of the desired class)";
            }
            throw new JsonIoException(message, e);
        }
    }

    /**
     * stores the missing field and their values to call back the handler at the end of the resolution, cause some
     * reference may need to be resolved later.
     */
    private void storeMissingField(Object target, String missingField, Object value)
    {
        missingFields.add(new Missingfields(target, missingField, value));
    }
    
    /**
     * @param o Object to turn into a String
     * @return .toString() version of o or "null" if o is null.
     */
    private static String safeToString(Object o)
    {
        if (o == null)
        {
            return "null";
        }
        try
        {
            return o.toString();
        }
        catch (Exception e)
        {
            return o.getClass().toString();
        }
    }

    /**
     * Process java.util.Collection and it's derivatives.  Collections are written specially
     * so that the serialization does not expose the Collection's internal structure, for
     * example, a TreeSet.  All entries are processed, except unresolved references, which
     * are filled in later.  For an indexable collection, the unresolved references are set
     * back into the proper element location.  For non-indexable collections (Sets), the
     * unresolved references are added via .add().
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseCollection(final Deque<JsonObject<String, Object>> stack, final JsonObject<String, Object> jsonObj)
    {
        final String className = jsonObj.type;
        final Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            if (className != null && className.startsWith("java.util.Immutable"))
            {
                if (className.contains("Set"))
                {
                    jsonObj.target = Set.of();
                }
                else if (className.contains("List"))
                {
                    jsonObj.target = List.of();
                }
            }
            return;
        }

        Class mayEnumClass = null;
        String mayEnumClasName = (String)jsonObj.get("@enum");
        if (mayEnumClasName != null)
        {
            mayEnumClass = MetaUtils.classForName(mayEnumClasName, classLoader);
        }

        final boolean isImmutable = className != null && className.startsWith("java.util.Immutable");
        final Collection col = isImmutable ? new ArrayList() : (Collection) jsonObj.target;
        final boolean isList = col instanceof List;
        int idx = 0;

        for (final Object element : items)
        {
            Object special;
            if (element == null)
            {
                col.add(null);
            }
            else if (element == JsonParser.EMPTY_OBJECT)
            {   // Handles {}
                col.add(new JsonObject());
            }
            else if ((special = readIfMatching(element, null, stack)) != null)
            {
                col.add(special);
            }
            else if (element instanceof String || element instanceof Boolean || element instanceof Double || element instanceof Long)
            {    // Allow Strings, Booleans, Longs, and Doubles to be "inline" without Java object decoration (@id, @type, etc.)
                if (mayEnumClass == null)
                    col.add(element);
                else
                    col.add(Enum.valueOf(mayEnumClass, (String)element));
            }
            else if (element.getClass().isArray())
            {
                final JsonObject jObj = new JsonObject();
                jObj.put(ITEMS, element);
                createJavaObjectInstance(Object.class, jObj);
                col.add(jObj.target);
                convertMapsToObjects(jObj);
            }
            else // if (element instanceof JsonObject)
            {
                final JsonObject jObj = (JsonObject) element;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                {
                    JsonObject refObject = getReferencedObj(ref);

                    if (refObject.target != null)
                    {
                        col.add(refObject.target);
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList)
                        {   // Indexable collection, so set 'null' as element for now - will be patched in later.
                            col.add(null);
                        }
                    }
                }
                else
                {
                    createJavaObjectInstance(Object.class, jObj);

                    if (!MetaUtils.isLogicalPrimitive(jObj.getTargetClass()))
                    {
                        convertMapsToObjects(jObj);
                    }
                    
                    if (!(col instanceof EnumSet))
                    {   // EnumSet has already had it's items added to it.
                        col.add(jObj.target);
                    }
                }
            }
            idx++;
        }

        reconcileCollection(jsonObj, col);
        jsonObj.remove(ITEMS);   // Reduce memory required during processing
    }

    static public void reconcileCollection(JsonObject jsonObj, Collection col)
    {
        final String className = jsonObj.type;
        final boolean isImmutable = className != null && className.startsWith("java.util.Immutable");
        
        if (!isImmutable)
        {
            return;
        }
        if (col == null && jsonObj.target instanceof Collection)
        {
            col = (Collection) jsonObj.target;
        }
        if (col == null)
        {
            return;
        }

        if (className.contains("List"))
        {
            if (col.stream().noneMatch(c -> c == null || c instanceof JsonObject))
            {
                jsonObj.target = List.of(col.toArray());
            }
            else
            {
                jsonObj.target = col;
            }
        }
        else if (className.contains("Set"))
        {
            jsonObj.target = Set.of(col.toArray());
        }
        else
        {
            jsonObj.target = col;
        }
    }

    /**
     * Traverse the JsonObject associated to an array (of any type).  Convert and
     * assign the list of items in the JsonObject (stored in the @items field)
     * to each array element.  All array elements are processed excluding elements
     * that reference an unresolved object.  These are filled in later.
     *
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseArray(final Deque<JsonObject<String, Object>> stack, final JsonObject<String, Object> jsonObj)
    {
        final int len = jsonObj.getLength();
        if (len == 0)
        {
            return;
        }

        final Class compType = jsonObj.getComponentType();

        if (char.class == compType)
        {
            return;
        }

        if (byte.class == compType)
        {   // Handle byte[] special for performance boost.
            jsonObj.moveBytesToMate();
            jsonObj.clearArray();
            return;
        }

        final boolean isPrimitive = MetaUtils.isPrimitive(compType);
        final Object array = jsonObj.target;
        final Object[] items =  jsonObj.getArray();

        for (int i=0; i < len; i++)
        {
            final Object element = items[i];

            Object special;
            if (element == null)
            {
                Array.set(array, i, null);
            }
            else if (element == JsonParser.EMPTY_OBJECT)
            {    // Use either explicitly defined type in ObjectMap associated to JSON, or array component type.
                Object arrayElement = createJavaObjectInstance(compType, new JsonObject());
                Array.set(array, i, arrayElement);
            }
            else if ((special = readIfMatching(element, compType, stack)) != null)
            {
                if (compType.isEnum() && special instanceof String) {
                    special = Enum.valueOf(compType, (String)special);
                }
                Array.set(array, i, special);
            }
            else if (isPrimitive)
            {   // Primitive component type array
                Array.set(array, i, MetaUtils.convert(compType, element));
            }
            else if (element.getClass().isArray())
            {   // Array of arrays
                if (char[].class == compType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF-8 strings for compactness and speed.
                    Object[] jsonArray = (Object[]) element;
                    if (jsonArray.length == 0)
                    {
                        Array.set(array, i, new char[]{});
                    }
                    else
                    {
                        final String value = (String) jsonArray[0];
                        final int numChars = value.length();
                        final char[] chars = new char[numChars];
                        for (int j = 0; j < numChars; j++)
                        {
                            chars[j] = value.charAt(j);
                        }
                        Array.set(array, i, chars);
                    }
                }
                else
                {
                    JsonObject<String, Object> jsonObject = new JsonObject<String, Object>();
                    jsonObject.put(ITEMS, element);
                    Array.set(array, i, createJavaObjectInstance(compType, jsonObject));
                    stack.addFirst(jsonObject);
                }
            }
            else if (element instanceof JsonObject)
            {
                JsonObject<String, Object> jsonObject = (JsonObject<String, Object>) element;
                Long ref = jsonObject.getReferenceId();

                if (ref != null)
                {    // Connect reference
                    JsonObject refObject = getReferencedObj(ref);
                    if (refObject.target != null)
                    {   // Array element with reference to existing object
                        Array.set(array, i, refObject.target);
                    }
                    else
                    {    // Array with a forward reference as an element
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                }
                else
                {    // Convert JSON HashMap to Java Object instance and assign values
                    Object arrayElement = createJavaObjectInstance(compType, jsonObject);
                    Array.set(array, i, arrayElement);
                    if (!MetaUtils.isLogicalPrimitive(arrayElement.getClass()))
                    {    // Skip walking primitives, primitive wrapper classes, Strings, and Classes
                        stack.addFirst(jsonObject);
                    }
                }
            }
            else
            {
                if (element instanceof String && "".equals(((String) element).trim()) && compType != String.class && compType != Object.class)
                {   // Allow an entry of "" in the array to set the array element to null, *if* the array type is NOT String[] and NOT Object[]
                    Array.set(array, i, null);
                }
                else
                {
                    Array.set(array, i, element);
                }
            }
        }
        jsonObj.clearArray();
    }

    /**
     * Convert the passed in object (o) to a proper Java object.  If the passed in object (o) has a custom reader
     * associated to it, then have it convert the object.  If there is no custom reader, then return null.
     * @param o Object to read (convert).  Will be either a JsonObject or a JSON primitive String, long, boolean,
     *          double, or null.
     * @param compType Class destination type to which the passed in object should be converted to.
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @return Java object converted from the passed in object o, or if there is no custom reader.
     */
    protected Object readIfMatching(final Object o, final Class compType, final Deque<JsonObject<String, Object>> stack)
    {
        if (o == null)
        {
            throw new JsonIoException("Bug in json-io, null must be checked before calling this method.");
        }

        if (compType != null && notCustom(compType))
        {
            return null;
        }

        final boolean isJsonObject = o instanceof JsonObject;
        if (!isJsonObject && compType == null)
        {   // If not a JsonObject (like a Long that represents a date, then compType must be set)
            return null;
        }

        Class c;
        boolean needsType = false;

        // Set up class type to check against reader classes (specified as @type, or jObj.target, or compType)
        if (isJsonObject)
        {
            JsonObject jObj = (JsonObject) o;
            if (jObj.isReference())
            {
                return null;
            }

            if (jObj.target == null)
            {   // '@type' parameter used (not target instance)
                String typeStr = null;
                try
                {
                    Object type =  jObj.type;
                    if (type != null)
                    {
                        typeStr = (String) type;
                        c = MetaUtils.classForName((String) type, classLoader);
                    }
                    else
                    {
                        if (compType != null)
                        {
                            c = compType;
                            needsType = true;
                        }
                        else
                        {
                            return null;
                        }
                    }
                    createJavaObjectInstance(c, jObj);
                }
                catch(Exception e)
                {
                    throw new JsonIoException("Class listed in @type [" + typeStr + "] is not found", e);
                }
            }
            else
            {   // Type inferred from target object
                c = jObj.target.getClass();
            }
        }
        else
        {
            c = compType;
        }

        if (notCustom(c))
        {
            return null;
        }

        JsonReader.JsonClassReaderBase closestReader = getCustomReader(c);

        if (closestReader == null)
        {
            return null;
        }

        if (needsType)
        {
            ((JsonObject)o).setType(c.getName());
        }

        Object read;
        if (closestReader instanceof JsonReader.JsonClassReaderEx)
        {
            read = ((JsonReader.JsonClassReaderEx)closestReader).read(o, stack, getReader().getArgs());
        }
        else
        {
            read = ((JsonReader.JsonClassReader)closestReader).read(o, stack);
        }
        return read;
    }

    private void markUntypedObjects(final Type type, final Object rhs, final Map<String, Field> classFields)
    {
        final Deque<Object[]> stack = new ArrayDeque<Object[]>();
        stack.addFirst(new Object[] {type, rhs});

        while (!stack.isEmpty())
        {
            Object[] item = stack.removeFirst();
            final Type t = (Type) item[0];
            final Object instance = item[1];
            if (t instanceof ParameterizedType)
            {
                final Class clazz = getRawType(t);
                final ParameterizedType pType = (ParameterizedType)t;
                final Type[] typeArgs = pType.getActualTypeArguments();

                if (typeArgs == null || typeArgs.length < 1 || clazz == null)
                {
                    continue;
                }

                stampTypeOnJsonObject(instance, t);

                if (Map.class.isAssignableFrom(clazz))
                {
                    Map map = (Map) instance;
                    if (!map.containsKey(KEYS) && !map.containsKey(ITEMS) && map instanceof JsonObject)
                    {   // Maps created in Javascript will come over without @keys / @items.
                        convertMapToKeysItems((JsonObject) map);
                    }

                    Object[] keys = (Object[])map.get(KEYS);
                    getTemplateTraverseWorkItem(stack, keys, typeArgs[0]);

                    Object[] items = (Object[])map.get(ITEMS);
                    getTemplateTraverseWorkItem(stack, items, typeArgs[1]);
                }
                else if (Collection.class.isAssignableFrom(clazz))
                {
                    if (instance instanceof Object[])
                    {
                        Object[] array = (Object[]) instance;
                        for (int i=0; i < array.length; i++)
                        {
                            Object vals = array[i];
                            stack.addFirst(new Object[]{t, vals});

                            if (vals instanceof JsonObject)
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                            else if (vals instanceof Object[])
                            {
                                JsonObject coll = new JsonObject();
                                coll.type = clazz.getName();
                                List items = Arrays.asList((Object[]) vals);
                                coll.put(ITEMS, items.toArray());
                                stack.addFirst(new Object[]{t, items});
                                array[i] = coll;
                            }
                            else
                            {
                                stack.addFirst(new Object[]{t, vals});
                            }
                        }
                    }
                    else if (instance instanceof Collection)
                    {
                        final Collection col = (Collection)instance;
                        for (Object o : col)
                        {
                            stack.addFirst(new Object[]{typeArgs[0], o});
                        }
                    }
                    else if (instance instanceof JsonObject)
                    {
                        final JsonObject jObj = (JsonObject) instance;
                        final Object[] array = jObj.getArray();
                        if (array != null)
                        {
                            for (Object o : array)
                            {
                                stack.addFirst(new Object[]{typeArgs[0], o});
                            }
                        }
                    }
                }
                else
                {
                    if (instance instanceof JsonObject)
                    {
                        final JsonObject<String, Object> jObj = (JsonObject) instance;

                        for (Map.Entry<String, Object> entry : jObj.entrySet())
                        {
                            final String fieldName = entry.getKey();
                            if (!fieldName.startsWith("this$"))
                            {
                                // TODO: If more than one type, need to associate correct typeArgs entry to value
                                Field field = classFields.get(fieldName);

                                if (field != null && (field.getType().getTypeParameters().length > 0 || field.getGenericType() instanceof TypeVariable))
                                {
                                    Object pt = typeArgs[0];
                                    if (entry.getValue() instanceof JsonObject && ((JsonObject)entry.getValue()).get("@enum") != null)
                                    {
                                        pt = field.getGenericType();
                                    }
                                    stack.addFirst(new Object[]{pt, entry.getValue()});
                                }
                            }
                        }
                    }
                }
            }
            else
            {
                stampTypeOnJsonObject(instance, t);
            }
        }
    }

    private static void getTemplateTraverseWorkItem(final Deque<Object[]> stack, final Object[] items, final Type type)
    {
        if (items == null || items.length < 1)
        {
            return;
        }
        Class rawType = getRawType(type);
        if (rawType != null && Collection.class.isAssignableFrom(rawType))
        {
            stack.add(new Object[]{type, items});
        }
        else
        {
            for (Object o : items)
            {
                stack.add(new Object[]{type, o});
            }
        }
    }

    // Mark 'type' on JsonObject when the type is missing and it is a 'leaf'
    // node (no further subtypes in it's parameterized type definition)
    private static void stampTypeOnJsonObject(final Object o, final Type t)
    {
        Class clazz = t instanceof Class ? (Class)t : getRawType(t);

        if (o instanceof JsonObject && clazz != null)
        {
            JsonObject jObj = (JsonObject) o;
            if ((jObj.type == null || jObj.type.isEmpty()) && jObj.target == null)
            {
                jObj.type = clazz.getName();
            }
        }
    }

    /**
     * Given the passed in Type t, return the raw type of it, if the passed in value is a ParameterizedType.
     * @param t Type to attempt to get raw type from.
     * @return Raw type obtained from the passed in parameterized type or null if T is not a ParameterizedType
     */
    public static Class getRawType(final Type t)
    {
        if (t instanceof ParameterizedType)
        {
            ParameterizedType pType = (ParameterizedType) t;

            if (pType.getRawType() instanceof Class)
            {
                return (Class) pType.getRawType();
            }
        }
        return null;
    }
}
