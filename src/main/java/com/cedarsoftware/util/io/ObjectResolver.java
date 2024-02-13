package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.reflect.Injector;

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
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
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
    /**
     * Constructor
     * @param readOptions Options to use while reading.
     */
    protected ObjectResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter)
    {
        super(readOptions, references, converter);
        this.classLoader = readOptions.getClassLoader();
    }

    /**
     * Walk the Java object fields and copy them from the JSON object to the Java object, performing
     * any necessary conversions on primitives, or deep traversals for field assignments to other objects,
     * arrays, Collections, or Maps.
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final Deque<JsonObject> stack, final JsonObject jsonObj)
    {
        final Object javaMate = jsonObj.getTarget();
        final Iterator<Map.Entry<Object, Object>> i = jsonObj.entrySet().iterator();
        final Class cls = javaMate.getClass();
        final Map<String, Injector> injectorMap = getReadOptions().getDeepInjectorMap(cls);

        while (i.hasNext())
        {
            Map.Entry<Object, Object> e = i.next();
            String key = (String) e.getKey();
            final Injector injector = injectorMap.get(key);
            Object rhs = e.getValue();
            if (injector != null)
            {
                assignField(stack, jsonObj, injector, rhs);
            } else if (getReadOptions().getMissingFieldHandler() != null)
            {
                handleMissingField(stack, jsonObj, rhs, key);
            }//else no handler so ignore.
        }
    }

    static boolean isBasicWrapperType(Class clazz)
    {
        return Primitives.PRIMITIVE_WRAPPERS.contains(clazz);
    }

    /**
     * Map Json Map object field to Java object field.
     *
     * @param stack   Stack (Deque) used for graph traversal.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     * @param injector  instance of injector used for setting values on the object.
     * @param rhs     the JSON value that will be converted and stored in the 'field' on the associated
     *                Java target object.
     */
    protected void assignField(final Deque<JsonObject> stack, final JsonObject jsonObj,
                               final Injector injector, final Object rhs)
    {
        final Object target = jsonObj.getTarget();
        final Class targetClass = target.getClass();
        try
        {
            final Class fieldType = injector.getType();
            if (rhs == null)
            {   // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                if (fieldType.isPrimitive())
                {
                    if (isBasicWrapperType(targetClass))
                    {
                        jsonObj.setTarget(this.getConverter().convert("0", fieldType));
                    }
                    else
                    {
                        injector.inject(target, this.getConverter().convert("0", fieldType));
                    }
                }
                else
                {
                    injector.inject(target, null);
                }
                return;
            }

            // If there is a "tree" of objects (e.g, Map<String, List<Person>>), the sub-objects may not have a
            // @type on them, if the source of the JSON is from JSON.stringify().  Deep traverse the args and
            // mark @type on the items within the Maps and Collections, based on the parameterized type (if it
            // exists).
            if (rhs instanceof JsonObject)
            {
                if (injector.getGenericType() instanceof ParameterizedType)
                {   // Only JsonObject instances could contain unmarked objects.
                    markUntypedObjects(injector.getGenericType(), rhs, fieldType);
                }

                // Ensure 'type' field set on JsonObject
                final JsonObject job = (JsonObject) rhs;
                if (job.getJavaType() == null)
                {
                    job.setJavaType(fieldType);
                }
            }

            Object special;
            if ((special = readWithFactoryIfExists(rhs, fieldType, stack)) != null)
            {
                injector.inject(target, special);
            }
            else if (rhs.getClass().isArray())
            {    // LHS of assignment is an [] field or RHS is an array and LHS is Object
                final Object[] elements = (Object[]) rhs;
                JsonObject jsonArray = new JsonObject();
                if (char[].class == fieldType)
                {   // Specially handle char[] because we are writing these
                    // out as UTF8 strings for compactness and speed.
                    if (elements.length == 0)
                    {
                        injector.inject(target, new char[]{});
                    }
                    else
                    {
                        injector.inject(target, ((String) elements[0]).toCharArray());
                    }
                }
                else
                {
                    jsonArray.put(ITEMS, elements);
                    jsonArray.setHintType(fieldType);
                    createInstance(jsonArray);
                    injector.inject(target, jsonArray.getTarget());
                    stack.addFirst(jsonArray);
                }
            }
            else if (rhs instanceof JsonObject)
            {
                final JsonObject jsRhs = (JsonObject) rhs;
                final Long ref = jsRhs.getReferenceId();

                if (ref != null)
                {    // Correct field references
                    final JsonObject refObject = this.getReferences().get(ref);

                    if (refObject.getTarget() != null)
                    {
                        injector.inject(target, refObject.getTarget());
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, injector.getName(), ref));
                    }
                }
                else
                {    // Assign ObjectMap's to Object (or derived) fields
                    jsRhs.setHintType(fieldType);
                    Object fieldObject = createInstance(jsRhs);
                    injector.inject(target, fieldObject);
                    boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jsRhs.getJavaType());
                    if (!isNonRefClass)
                    {
                        // GOTCHA : if the field is an immutable collection,
                        // "work instance", where one can accumulate items in (ArrayList)
                        // and "final instance' (say MetaUtils.listOf() ) can _not_ be the same.
                        // So, the later the assignment, the better.
                        Object javaObj = convertJsonValuesToJava(jsRhs);
                        if (javaObj != fieldObject) {
                            injector.inject(target, javaObj);
                        }
                    }
                }
            }
            else
            {
                if (rhs instanceof String && ((String) rhs).trim().isEmpty() && fieldType != String.class) {
                    // Allow "" to null out a non-String field
                    injector.inject(target, null);
                } else {
                    injector.inject(target, rhs);
                }
            }
        }
        catch (Exception e)
        {
            if (e instanceof JsonIoException)
            {
                throw e;
            }

            throw new JsonIoException("Unable to set field: " + injector.getName() + " on target: " + safeToString(target) + " with value: " + rhs, e);
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
    protected void handleMissingField(final Deque<JsonObject> stack, final JsonObject jsonObj, final Object rhs,
                                      final String missingField)
    {
        final Object target = jsonObj.getTarget();
        try
        {
            if (rhs == null)
            { // Logically clear field (allows null to be set against primitive fields, yielding their zero value.
                storeMissingField(target, missingField, null);
                return;
            }

            // we have a jsonobject with a type
            Object special;
            if ((special = readWithFactoryIfExists(rhs, null, stack)) != null)
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
                final JsonObject jObj = (JsonObject) rhs;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                { // Correct field references
                    final JsonObject refObject = this.getReferences().get(ref);
                    storeMissingField(target, missingField, refObject.getTarget());
                }
                else
                {   // Assign ObjectMap's to Object (or derived) fields
                    // check that jObj as a type
                    if (jObj.getJavaType() != null)
                    {
                        Object javaInstance = createInstance(jObj);
                        boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jObj.getJavaType());
                        // TODO: Check is finished here?
                        if (!isNonRefClass && !jObj.isFinished)
                        {
                            stack.addFirst((JsonObject) rhs);
                        }
                        storeMissingField(target, missingField, javaInstance);
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
            if (e instanceof JsonIoException)
            {
                throw e;
            }
            String message = e.getClass().getSimpleName() + " missing field '" + missingField + "' on target: "
                    + safeToString(target) + " with value: " + rhs;
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
     * are filled in later.  For an index-able collection, the unresolved references are set
     * back into the proper element location.  For non-index-able collections (Sets), the
     * unresolved references are added via .add().
     * @param jsonObj a Map-of-Map representation of the JSON input stream.
     */
    protected void traverseCollection(final Deque<JsonObject> stack, final JsonObject jsonObj)
    {
        final String className = jsonObj.getJavaTypeName();
        final Object[] items = jsonObj.getArray();
        if (items == null || items.length == 0)
        {
            if (className != null && className.startsWith("java.util.Immutable"))
            {
                if (className.contains("Set"))
                {
                    jsonObj.setTarget(MetaUtils.setOf());
                }
                else if (className.contains("List"))
                {
                    jsonObj.setTarget(MetaUtils.listOf());
                }
            }
            return;
        }

        Class mayEnumClass = null;
        String mayEnumClasName = (String)jsonObj.get("@enum");
        if (mayEnumClasName != null)
        {
            mayEnumClass = ClassUtilities.forName(mayEnumClasName, classLoader);
        }

        final boolean isImmutable = className != null && className.startsWith("java.util.Immutable");
        final Collection col = isImmutable ? new ArrayList<>() : (Collection) jsonObj.getTarget();
        final boolean isList = col instanceof List;
        int idx = 0;

        for (final Object element : items)
        {
            Object special;
            if (element == null)
            {
                col.add(null);
            }
            else if ((special = readWithFactoryIfExists(element, null, stack)) != null)
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
                jObj.setHintType(Object.class);
                jObj.put(ITEMS, element);
                createInstance(jObj);
                col.add(jObj.getTarget());
                convertJsonValuesToJava(jObj);
            }
            else // if (element instanceof JsonObject)
            {
                final JsonObject jObj = (JsonObject) element;
                final Long ref = jObj.getReferenceId();

                if (ref != null)
                {
                    JsonObject refObject = this.getReferences().get(ref);

                    if (refObject.getTarget() != null)
                    {
                        col.add(refObject.getTarget());
                    }
                    else
                    {
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, idx, ref));
                        if (isList)
                        {   // Index-able collection, so set 'null' as element for now - will be patched in later.
                            col.add(null);
                        }
                    }
                }
                else
                {
                    jObj.setHintType(Object.class);
                    createInstance(jObj);
                    boolean isNonRefClass = getReadOptions().isNonReferenceableClass(jObj.getJavaType());
                    if (!isNonRefClass)
                    {
                        convertJsonValuesToJava(jObj);
                    }
                    
                    if (!(col instanceof EnumSet))
                    {   // EnumSet has already had it's items added to it.
                        col.add(jObj.getTarget());
                    }
                }
            }
            idx++;
        }

        reconcileCollection(jsonObj, col);
        jsonObj.remove(ITEMS);   // Reduce memory required during processing
    }

    public static void reconcileCollection(JsonObject jsonObj, Collection col)
    {
        final String className = jsonObj.getJavaTypeName();
        final boolean isImmutable = className != null && className.startsWith("java.util.Immutable");
        
        if (!isImmutable)
        {
            return;
        }
        if (col == null && jsonObj.getTarget() instanceof Collection)
        {
            col = (Collection) jsonObj.getTarget();
        }
        if (col == null)
        {
            return;
        }

        if (className.contains("List"))
        {
            if (col.stream().noneMatch(c -> c == null || c instanceof JsonObject))
            {
                jsonObj.setTarget(MetaUtils.listOf(col.toArray()));
            }
            else
            {
                jsonObj.setTarget(col);
            }
        }
        else if (className.contains("Set"))
        {
            jsonObj.setTarget(MetaUtils.setOf(col.toArray()));
        }
        else
        {
            jsonObj.setTarget(col);
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
    protected void traverseArray(final Deque<JsonObject> stack, final JsonObject jsonObj)
    {
        final int len = jsonObj.getLength();
        if (len == 0) {
            return;
        }

        final Class compType = jsonObj.getComponentType();

        if (char.class == compType) {
            return;
        }

        if (byte.class == compType) {
            // Handle byte[] special for performance boost.
            jsonObj.clearArray();
            return;
        }

        final boolean isPrimitive = Primitives.isPrimitive(compType);
        final Object array = jsonObj.getTarget();
        final Object[] items =  jsonObj.getArray();

        for (int i=0; i < len; i++)
        {
            final Object element = items[i];
            Object special;
            
            if (element == null)
            {
                Array.set(array, i, null);
            }
            else if ((special = readWithFactoryIfExists(element, compType, stack)) != null)
            {
                if (compType.isEnum() && special instanceof String) {
                    special = Enum.valueOf(compType, (String)special);
                }
                Array.set(array, i, special);
            }
            else if (isPrimitive)
            {   // Primitive component type array
                Array.set(array, i, this.getConverter().convert(element, compType));
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
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.put(ITEMS, element);
                    jsonObject.setHintType(compType);
                    Array.set(array, i, createInstance(jsonObject));
                    stack.addFirst(jsonObject);
                }
            }
            else if (element instanceof JsonObject)
            {
                JsonObject jsonObject = (JsonObject) element;
                Long ref = jsonObject.getReferenceId();

                if (ref != null)
                {    // Connect reference
                    JsonObject refObject = this.getReferences().get(ref);
                    if (refObject.getTarget() != null)
                    {   // Array element with reference to existing object
                        Array.set(array, i, refObject.getTarget());
                    }
                    else
                    {    // Array with a forward reference as an element
                        unresolvedRefs.add(new UnresolvedReference(jsonObj, i, ref));
                    }
                }
                else
                {    // Convert JSON HashMap to Java Object instance and assign values
                    jsonObject.setHintType(compType);
                    Object arrayElement = createInstance(jsonObject);
                    Array.set(array, i, arrayElement);
                    boolean isNonRefClass = getReadOptions().isNonReferenceableClass(arrayElement.getClass());
                    if (!isNonRefClass && !jsonObject.isFinished) {
                        // Skip walking primitives and completed objects.
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
     * @param inferredType Class destination type to which the passed in object should be converted to.
     * @param stack   a Stack (Deque) used to support graph traversal.
     * @return Java object converted from the passed in object o, or if there is no custom reader.
     */
    protected Object readWithFactoryIfExists(final Object o, final Class inferredType, final Deque<JsonObject> stack)
    {
        if (o == null)
        {
            throw new JsonIoException("Bug in json-io, null must be checked before calling this method.");
        }

        if (inferredType != null && getReadOptions().isNotCustomReaderClass(inferredType))
        {
            return null;
        }

        final boolean isJsonObject = o instanceof JsonObject;
        if (!isJsonObject && inferredType == null)
        {   // If not a JsonObject (like a Long that represents a date, then compType must be set)
            return null;
        }
        JsonObject jsonObj;

        Class c;

        // Set up class type to check against reader classes (specified as @type, or jObj.target, or compType)
        if (isJsonObject)
        {
            jsonObj = (JsonObject) o;
            if (jsonObj.isReference())
            {   // Don't create a new instance for an @ref.  The pointer to the other instance will be placed
                // where we are now (inside array, inside collection, as a map key, a map value, or a value
                // pointed to by a field.
                return null;
            }

            if (jsonObj.getTarget() == null)
            {   // '@type' parameter used (not target instance)
                String typeStr = null;
                try
                {
                    String type =  jsonObj.getJavaTypeName();
                    if (type != null)
                    {
                        typeStr = type;
                        c = ClassUtilities.forName(type, classLoader);
                    }
                    else
                    {
                        if (inferredType != null)
                        {
                            c = inferredType;
                        }
                        else
                        {
                            return null;
                        }
                    }
                    jsonObj.setHintType(c);
                    Object factoryCreated = createInstance(jsonObj);
                    if (factoryCreated != null && jsonObj.isFinished) {
                        return factoryCreated;
                    }
                } catch (JsonIoException e) {
                    throw e;
                } catch (Exception e) {
                    throw new JsonIoException("Unable to determine type", e);
                }
            }
            else
            {   // Type inferred from target object
                c = jsonObj.getJavaType();
            }
        }
        else
        {
            c = inferredType;

            jsonObj = new JsonObject();
            jsonObj.setValue(o);
        }

        if (null == c) {
            // Class not found using multiple techniques.  There is no custom factory or reader;
            return null;
        }

        if (jsonObj.getJavaType() == null) {
            jsonObj.setJavaType(c);
        }

        if (getReadOptions().isNotCustomReaderClass(c)) {
            // Explicitly instructed not to use a custom reader for this class.
            return null;
        }

        // we could maybe just do the map conversion (JsonObject) if they were all defined out.
        if (jsonObj.getTarget() == null) {
            if (jsonObj.hasValue() && jsonObj.getValue() != null) {
                if (this.getConverter().isConversionSupportedFor(jsonObj.getValue().getClass(), c)) {
//                System.out.println("jsonObj.getValue() = " + jsonObj.getValue());
                    Object target = this.getConverter().convert(jsonObj.getValue(), c);

                    return jsonObj.setFinishedTarget(target, true);
                }
                //  TODO: Handle primitives that are written as map with conversion logic (no refs on these types, I think)
                //  TODO: I'd like to have all types that have map conversion supported here, but we have an issue with refs
                //  TODO: going to the converter and I'm still thinking of a way to handle that.
            } else if (MetaUtils.isLogicalPrimitive(c) && this.getConverter().isConversionSupportedFor(Map.class, c)) {
                Object target = this.getConverter().convert(jsonObj, c);
                return jsonObj.setFinishedTarget(target, true);
            }
        }


        // from here on out it is assumed you have json object.
        // Use custom classFactory if one exists and target hasn't already been created.
        JsonReader.ClassFactory classFactory = getReadOptions().getClassFactory(c);
        if (classFactory != null && jsonObj.getTarget() == null)
        {
            Object target = createInstanceUsingClassFactory(c, jsonObj);

            if (jsonObj.isFinished()) {
                return target;
            }
        }

        // Use custom reader if one exists
        JsonReader.JsonClassReader closestReader = getReadOptions().getCustomReader(c);
        if (closestReader == null) {
            return null;
        }

        Object read = closestReader.read(o, stack, this);
        // Fixes Issue #17 from GitHub.  Make sure to place a pointer to the custom read object on the JsonObject.
        // This way, references to it will be pointed back to the correct instance.
        return jsonObj.setFinishedTarget(read, true);
    }

    private void markUntypedObjects(final Type type, final Object rhs, final Class<?> fieldType)
    {
        final Deque<Object[]> stack = new ArrayDeque<>();
        stack.addFirst(new Object[] {type, rhs});

        Map<String, Injector> classFields = getReadOptions().getDeepInjectorMap(fieldType);
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
                                coll.setJavaType(clazz);
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
                        final JsonObject jObj = (JsonObject) instance;

                        for (Map.Entry<Object, Object> entry : jObj.entrySet())
                        {
                            final String fieldName = (String) entry.getKey();
                            if (!fieldName.startsWith("this$"))
                            {
                                // TODO: If more than one type, need to associate correct typeArgs entry to value
                                Injector injector = classFields.get(fieldName);

                                if (injector != null && (injector.getType().getTypeParameters().length > 0 || injector.getGenericType() instanceof TypeVariable))
                                {
                                    Object pt = typeArgs[0];
                                    if (entry.getValue() instanceof JsonObject && ((JsonObject)entry.getValue()).get("@enum") != null)
                                    {
                                        pt = injector.getGenericType();
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
            if (jObj.getJavaType() == null && jObj.getTarget() == null)
            {
                jObj.setJavaType(clazz);
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
