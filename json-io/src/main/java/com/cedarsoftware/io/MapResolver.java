package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.TypeUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * <p>The MapResolver converts the raw Maps created from the JsonParser to higher
 * quality Maps representing the implied object graph.  It does this by replacing
 * <code>@ref</code> values with the Map indicated by the @id key with the same value.
 * </p><p>
 * This approach 'wires' the original object graph.  During the resolution process,
 * if 'peer' classes can be found for given Maps (for example, an @type entry is
 * available which indicates the class that would have been associated to the Map,
 * then the associated class is consulted to help 'improve' the quality of the primitive
 * values within the map fields.  For example, if the peer class indicated that a field
 * was of type 'short', and the Map had a long value (JSON only returns long's for integer
 * types), then the long would be converted to a short.
 * </p><p>
 * The final Map representation is a very high-quality graph that represents the original
 * JSON graph.  It can be passed as input to JsonWriter, and the JsonWriter will write
 * out the equivalent JSON to what was originally read.  This technique allows json-io to
 * be used on a machine that does not have any of the Java classes from the original graph,
 * read it in a JSON graph (any JSON graph), return the equivalent maps, allow mutations of
 * those maps, and finally this graph can be written out.
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
public class MapResolver extends Resolver {
    public MapResolver(ReadOptions readOptions, ReferenceTracker references, Converter converter) {
        super(readOptions, references, converter);
    }

    /**
     * The root, as the type asked for: that type, or an error naming why the JSON cannot be it, as in Java mode. A
     * JSON value (a string, number, boolean or null) is converted as Java mode converts it. An object or array asked
     * for as a value type or an array ({@code LocalDate}, {@code int[]}, {@code Map[]}) is read as the JSON it is, then
     * converted -- read in the array's shape, the JSON array itself was filled as its elements converted (a reference
     * a second, empty map; an object in a {@code List[]} a null). One asked for as a Map or a Collection is read in
     * that shape ({@link #jsonShape}); a JSON object is not a list (it was an empty one, its fields lost). A type this
     * read does not reach (a class, type arguments) is reached afterward from the tree ({@link #readsFromTree}).
     */
    @Override
    public Object toJava(Type type, Object value) {
        if (isJsonValue(value)) {
            return super.toJava(type, value);
        }
        if (isValueShape(type)) {
            Object tree = super.toJava(null, value);
            // A collection the JSON names ({"@type":"HashSet","@items":[..]}) converts as its items
            Object from = tree instanceof JsonObject && !((JsonObject) tree).isMapShaped()
                    && ((JsonObject) tree).getItems() != null ? ((JsonObject) tree).getItems() : tree;
            return convertToType(from, type);
        }
        Type shape = jsonShape(type);
        if (isPlainObjectReadAsItems(value, shape)) {
            throw new JsonIoException("Return type mismatch. Expecting: " + TypeUtilities.getRawClass(shape).getName()
                    + ", found: " + JsonObject.class.getName());
        }
        return super.toJava(shape, value);
    }

    /** A JSON string, number, boolean or null at the root: not an object, an array, or a list (TOON). */
    private static boolean isJsonValue(Object value) {
        return value == null || !(value instanceof JsonObject) && !(value instanceof Collection)
                && !value.getClass().isArray();
    }

    /**
     * The type a Maps-mode read takes from the type asked for at the root, as it parses and resolves the JSON: that
     * type, where it is a shape Maps mode reads itself (a {@code Map} or {@code Collection} class);
     * its {@code Map} or {@code Collection} class alone, where its type arguments ask for more than JSON's own values
     * ({@code List<LocalDate>}); none ({@code null}) for a value type, which is converted from the JSON read
     * ({@link #isValueShape}), and for a type Maps mode does not build ({@code Person}, {@code Person[]}, an enum,
     * {@code Optional}) -- the parse does not type the root's fields by it either.
     */
    static Type jsonShape(Type type) {
        if (type == null || type == Object.class || !isMapsModeRootType(type) || isValueShape(type)) {
            return null;
        }
        return type instanceof ParameterizedType && !asksNoMore((ParameterizedType) type)
                ? TypeUtilities.getRawClass(type) : type;
    }

    /**
     * A Maps-mode root type converted from the JSON read, which the conversion leaves as it is: a value type (types
     * supported by Converter.convert(), primitives and their wrappers, {@code Number}), or an array of these, of Maps,
     * Collections or {@code Object} ({@code LocalDate[]}, {@code int[][]}, {@code List[]}). Read in such a shape, the
     * JSON array itself was filled with what its elements converted to, half filled where one did not. Not a Map or a
     * Collection, which Maps mode reads as they are.
     */
    private static boolean isValueShape(Type type) {
        if (type == null || !isMapsModeRootType(type)) {
            return false;
        }
        Class<?> raw = TypeUtilities.getRawClass(type);
        return raw.isArray() || !Map.class.isAssignableFrom(raw) && !Collection.class.isAssignableFrom(raw);
    }

    /**
     * Whether a Maps-mode root is read afterward from the tree, as Java mode reads its text: where the type asked for
     * is one this read does not reach (a class Maps mode does not build, type arguments it does not apply).
     */
    static boolean readsFromTree(Object root, Type type) {
        return type != null && type != Object.class && !isValueShape(type) && jsonShape(type) != type
                && !reaches(root, type);
    }

    /**
     * Whether a root is the type asked for: an instance of its class, whose type arguments ask for no more than JSON's
     * own values. (A primitive is a value type, never asked here.)
     */
    private static boolean reaches(Object root, Type type) {
        if (root == null || type == null || type == Object.class) {
            return true;
        }
        Class<?> raw = TypeUtilities.getRawClass(type);
        return raw == null || raw.isInstance(root)
                && (type instanceof Class || type instanceof ParameterizedType && asksNoMore((ParameterizedType) type));
    }

    /** Type arguments a JSON read gives anyway: anything ({@code Object}, {@code ?}), a map of it, a map's String keys. */
    private static boolean asksNoMore(ParameterizedType type) {
        Type[] args = type.getActualTypeArguments();
        boolean map = Map.class.isAssignableFrom(TypeUtilities.getRawClass(type));
        for (int i = 0; i < args.length; i++) {
            Type arg = args[i];
            if (!isAnything(arg) && arg != JsonObject.class && !isMapItself(arg)
                    && !(map && i == 0 && arg == String.class)) {
                return false;
            }
        }
        return true;
    }

    /** A JSON object naming no type of its own (no @type, no @items), asked for as a Collection. */
    private static boolean isPlainObjectReadAsItems(Object value, Type shape) {
        if (!(value instanceof JsonObject)) {
            return false;
        }
        JsonObject jObj = (JsonObject) value;
        Class<?> raw = TypeUtilities.getRawClass(shape);
        return jObj.isMapShaped() && jObj.getKeys() == null && jObj.getTypeString() == null && raw != null
                && Collection.class.isAssignableFrom(raw);
    }

    /**
     * Override toJavaObjects to validate rootType before resolution.
     * In Maps mode, only certain types are supported as rootType. {@code Object} is asking for whatever the JSON
     * holds, as no type ({@code null}) is.
     */
    @Override
    <T> T toJavaObjects(JsonObject rootObj, Type rootType) {
        if (rootType == Object.class) {
            rootType = null;
        }
        verifyRootType(rootType);
        if (rootType == null && rootObj.getItems() == null && isValueClass(rootObj.getRawType())) {
            // The root's own @type a value type, none asked for: where its value does not convert, the JSON as written
            // (see convertIfNonRefType). Asked for, it is converted afterward, and is an error there.
            try {
                return super.toJavaObjects(rootObj, null);
            } catch (RuntimeException e) {
                return (T) rootObj;
            }
        }
        return super.toJavaObjects(rootObj, rootType);
    }

    /**
     * Validates that the rootType is supported in Maps mode.
     * Maps mode supports: null, primitives/wrappers, simple types, Map, Collection, and arrays of these.
     *
     * @param rootType the requested return type
     * @throws JsonIoException if rootType is not supported in Maps mode
     */
    private void verifyRootType(Type rootType) {
        if (rootType == null || isMapsModeRootType(rootType)) {
            return;
        }

        // Type not supported in Maps mode
        Class<?> rawRootType = TypeUtilities.getRawClass(rootType);
        String typeName = rawRootType != null ? rawRootType.getName() : rootType.toString();
        throw new JsonIoException("In readOptions.isReturningJsonObjects() mode, the rootType '" + typeName +
                "' is not supported. Allowed types are:\n" +
                "- null\n" +
                "- primitive types (e.g., int, boolean) and their wrapper classes (e.g., Integer, Boolean)\n" +
                "- types supported by Converter.convert()\n" +
                "- Map or any of its subclasses\n" +
                "- Collection or any of its subclasses\n" +
                "- Arrays (of any depth) of the above types\n" +
                "Please use one of these types as the rootType, or enable readOptions.isReturningJavaObjects().");
    }

    /**
     * A type Maps mode reads itself: primitives and their wrappers, types supported by Converter.convert(), Map,
     * Collection, and arrays (of any depth) of these.
     */
    private static boolean isMapsModeRootType(Type rootType) {
        Class<?> rawRootType = TypeUtilities.getRawClass(rootType);
        Type typeToCheck = rootType;

        // If the raw type represents an array, drill down to the ultimate component type.
        if (rawRootType != null && rawRootType.isArray()) {
            typeToCheck = getUltimateComponentType(rootType);
            Class<?> ultimateRawType = TypeUtilities.getRawClass(typeToCheck);
            if (isPseudoPrimitive(ultimateRawType) ||
                    ultimateRawType == Number.class ||
                    (ultimateRawType != null && ultimateRawType.equals(Object.class))) {
                return true;
            }
        } else if (isPseudoPrimitive(rawRootType) || rawRootType == Number.class) {
            return true;
        }

        // Check for Collection or Map types
        Class<?> rawTypeToCheck = TypeUtilities.getRawClass(typeToCheck);
        return rawTypeToCheck != null &&
                (Collection.class.isAssignableFrom(rawTypeToCheck) || Map.class.isAssignableFrom(rawTypeToCheck));
    }

    /**
     * Drills down through array types to find the ultimate component type.
     */
    private static Type getUltimateComponentType(Type type) {
        while (true) {
            if (type instanceof Class<?>) {
                Class<?> cls = (Class<?>) type;
                if (cls.isArray()) {
                    type = cls.getComponentType();
                    continue;
                }
            } else if (type instanceof GenericArrayType) {
                type = ((GenericArrayType) type).getGenericComponentType();
                continue;
            }
            return type;
        }
    }

    /**
     * In Maps mode, substitute SortedSet/SortedMap with LinkedHashSet/LinkedHashMap.
     * This avoids Comparator issues since TreeSet/TreeMap require Comparators that aren't serialized.
     * LinkedHashSet/LinkedHashMap preserve insertion order, which is better than HashSet/HashMap.
     *
     * @param jsonObj the JsonObject whose type may be substituted
     */
    private void substituteSortedCollectionType(JsonObject jsonObj) {
        Class<?> javaType = jsonObj.getRawType();
        if (javaType == null) {
            return;
        }
        // Substitute sorted collections with order-preserving equivalents
        if (SortedSet.class.isAssignableFrom(javaType)) {
            jsonObj.setType(LinkedHashSet.class);
        } else if (SortedMap.class.isAssignableFrom(javaType)) {
            jsonObj.setType(LinkedHashMap.class);
        }
    }

    /**
     * In Maps mode, substitute sorted collections from JSON's @type with LinkedHashSet/LinkedHashMap.
     * This is necessary because TreeSet/TreeMap require Comparators that aren't serialized.
     *
     * Only substitute if the type came from JSON's @type (indicated by typeString being set).
     * If the type was set via asClass(TreeMap.class), the user's request should be honored.
     */
    @Override
    protected void adjustTypeBeforeResolve(JsonObject rootObj, Type rootType) {
        // Only substitute if the type came from JSON's @type (indicated by typeString being set),
        // not from the user's request via asClass(TreeMap.class).
        // When @type is in the JSON, the Parser sets both typeString and type.
        // When only suggestedType is used, typeString remains null.
        if (rootObj.getTypeString() != null) {
            substituteSortedCollectionType(rootObj);
        }
    }

    /**
     * In Maps mode, Maps (like HashMap) need to have their entries traversed to patch @ref references.
     * The parent class's traverseMap() doesn't do this - it just processes keys/values arrays.
     * We override to handle both @keys/@items format and regular String-key Maps.
     */
    @Override
    protected void traverseMap(JsonObject jsonObj) {
        jsonObj.readAsMaps = true;
        // Guard against reprocessing already-finished objects
        if (jsonObj.isFinished) {
            return;
        }

        // Apply sorted collection substitution ONLY for types from JSON's @type (typeString set),
        // not for types from user's asClass() request
        if (jsonObj.getTypeString() != null) {
            substituteSortedCollectionType(jsonObj);
        }

        // Check if this is @keys/@items format (complex keys) or regular String-key format
        Object[] complexKeys = jsonObj.getKeys();  // Returns null for regular POJOs
        if (complexKeys != null) {
            // @keys/@items format - traverse keys and items arrays for @ref patching
            Object[] items = jsonObj.getItems();
            if (items != null) {
                traverseArrayForRefs(complexKeys);
                traverseArrayForRefs(items);
            }
        } else {
            // Regular String-key Map - use traverseFields to patch @refs in values
            traverseFields(jsonObj);
        }

        // Add to rehash list so entries are copied to target Map (for asClass(TreeMap.class) etc.)
        addMapToRehash(jsonObj);
    }

    /**
     * Traverse an array looking for @ref JsonObjects to patch.
     * Also handles nested arrays recursively to avoid JsonObject wrapper allocations.
     */
    private void traverseArrayForRefs(Object[] array) {
        final int len = array.length;
        for (int i = 0; i < len; i++) {
            Object element = array[i];
            if (element == null) {
                continue;
            }
            if (element instanceof JsonObject) {
                JsonObject jObj = (JsonObject) element;
                JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {
                    array[i] = valueOf(refObject);  // Patch the @ref
                } else {
                    Object value = valueOf(jObj);
                    if (value != jObj) {
                        array[i] = value;
                    } else {
                        push(jObj);  // Traverse nested object
                    }
                }
            } else if (element instanceof Object[]) {
                // Recursively traverse nested arrays inline to avoid JsonObject allocation
                traverseArrayForRefs((Object[]) element);
            } else if (element instanceof List) {
                traverseListForRefs((List<Object>) element);
            }
        }
    }

    /**
     * A TOON list -- the parser's own java.util.List, where JSON's parser gives an Object[] -- patched in place as
     * {@link #traverseArrayForRefs} patches an array: a {@code $ref} becomes the object it names, any other object
     * is traversed, and a list or an array inside it is patched in turn. Without this a {@code $ref} in such a
     * list stayed the empty map it was parsed as. Not for a document that names no $type, $id or $ref anywhere.
     */
    private void traverseListForRefs(List<Object> list) {
        if (!parsedListsHoldMeta()) {
            return;     // plain data: no $ref to patch, no typed value to convert
        }
        for (int i = 0, len = list.size(); i < len; i++) {
            Object element = list.get(i);
            if (element instanceof JsonObject) {
                JsonObject jObj = (JsonObject) element;
                JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {
                    list.set(i, valueOf(refObject));
                } else {
                    Object value = valueOf(jObj);
                    if (value != jObj) {
                        list.set(i, value);
                    } else {
                        push(jObj);
                    }
                }
            } else if (element instanceof Object[]) {
                traverseArrayForRefs((Object[]) element);
            } else if (element instanceof List) {
                traverseListForRefs((List<Object>) element);
            }
        }
    }

    /**
     * A value json-io writes with its type ({"@type":"BigDecimal","value":"1.50"}, a date, a UUID, a Character, a
     * simple enum's constant) as a field holds it: the value, wherever it sits -- in a list or an array, a typed
     * collection, a map's keys and values. It stayed the object holding it everywhere but in a field, at the root and
     * in a root array. Any other object is returned as it is.
     */
    private Object valueOf(JsonObject jObj) {
        Class<?> type = jObj.getRawType();
        if (!isValueClass(type) || isEnumSet(jObj)) {
            return jObj;
        }
        if (!jObj.isFinished() && jObj.getTarget() == null) {
            Object value;
            try {
                value = super.convertIfNonRefType(jObj, type);
            } catch (RuntimeException e) {
                return jObj;    // a value that does not convert stays the JSON that holds it (see convertIfNonRefType)
            }
            jObj.setFinishedTarget(value, true);
        }
        return jObj.getTarget();
    }

    /**
     * In Maps mode, handle type reconciliation for the root result when no explicit
     * rootType was specified by the user.
     * <p>
     * This logic determines the appropriate return type:
     * - Arrays are always returned as actual arrays (not JsonObject)
     * - If @type is a simple type (String, Number, etc.), convert to that type
     * - If the result is a complex type, return the raw JsonObject
     * - If the result is already a simple type, return it as-is
     */
    @Override
    protected Object reconcileResult(Object result, JsonObject rootObj, Type rootType) {
        // If user specified a rootType, don't apply Maps mode handling here
        // The type compatibility checking happens in Resolver.handleObjectRoot
        if (rootType != null) {
            // Map itself asked for: the tree's own object, not a copy. A @ref to the root inside the tree is that
            // object, so a copy written back held the root twice, each with its own @id; and it was a second map to
            // keep in step. A concrete Map class (LinkedHashMap, TreeMap) is a copy into it, as before.
            // JsonObject asked for: the root, whatever it holds (a typed collection too: it was a mismatch)
            if (rootType == JsonObject.class || isMapItself(rootType) && result instanceof Map && rootObj.isMapShaped()) {
                return rootObj;
            }
            return result;
        }

        // User did not specify rootType - apply Maps mode logic

        // Arrays should always be returned as actual arrays, not JsonObject
        if (result != null && result.getClass().isArray()) {
            return result;
        }

        // javaType is guaranteed non-null: Resolver.toJavaObjects() sets it to at least Object.class
        Type javaType = rootObj.getType();
        Class<?> javaClass = TypeUtilities.getRawClass(javaType);

        // If @type is a simple type or Number, convert jsonObj to its basic type
        if (isPseudoPrimitive(javaClass) ||
                Number.class.isAssignableFrom(javaClass)) {
            Class<?> basicType = getJsonSynonymType(javaClass);
            return converter.convert(rootObj, basicType);
        }

        // A simple enum's constant, as it is anywhere in the tree (it was the object holding it)
        Class<?> enumClass = ClassUtilities.getClassIfEnum(javaClass);
        if (enumClass != null && isSimpleEnum(enumClass)) {
            Object constant = convertIfNonRefType(rootObj, javaClass);
            if (constant != null) {
                return constant;
            }
        }

        // Return primitive results directly, otherwise return the raw JsonObject
        return isBuiltInPrimitive(result, converter) ? result : rootObj;
    }

    /**
     * {@code Map}, or a {@code Map} of nothing more ({@code Map<String, Object>}, {@code Map<?, ?>}): a type every
     * JsonObject is. A Map whose type arguments ask for more (typed values) is not. ({@code JsonObject} itself was a
     * new, empty one where the root named a class not on the classpath.)
     */
    private static boolean isMapItself(Type rootType) {
        if (rootType == Map.class) {
            return true;
        }
        if (!(rootType instanceof ParameterizedType) || ((ParameterizedType) rootType).getRawType() != Map.class) {
            return false;
        }
        Type[] args = ((ParameterizedType) rootType).getActualTypeArguments();
        return isAnything(args[0]) || args[0] == String.class ? isAnything(args[1]) : false;
    }

    private static boolean isAnything(Type type) {
        return type == Object.class || type instanceof WildcardType
                && ((WildcardType) type).getUpperBounds()[0] == Object.class
                && ((WildcardType) type).getLowerBounds().length == 0;
    }

    /**
     * Maps complex types to their simpler JSON-friendly equivalents.
     */
    private Class<?> getJsonSynonymType(Class<?> javaType) {
        if (javaType == StringBuilder.class || javaType == StringBuffer.class) {
            return String.class;
        } else if (javaType == AtomicInteger.class) {
            return Integer.class;
        } else if (javaType == AtomicLong.class) {
            return Long.class;
        } else if (javaType == AtomicBoolean.class) {
            return Boolean.class;
        }
        return javaType;
    }

    /**
     * Checks if the object is a built-in primitive type supported by the Converter.
     */
    private boolean isBuiltInPrimitive(Object obj, Converter converter) {
        if (obj == null) {
            return false;
        }
        return isPseudoPrimitive(obj.getClass());
    }

    protected Object readWithFactoryIfExists(Object o, Type compType) {
        // In Maps mode, convert JsonObjects with simple types (Byte, Short, etc.) to their Java equivalents
        if (o instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) o;
            Object converted = convertIfNonRefType(jsonObj, jsonObj.getRawType());
            if (converted != null) {
                jsonObj.setFinishedTarget(converted, true);
                return converted;
            }
        }
        return null;
    }

    /**
     * Walk the JsonObject fields and perform necessary substitutions so that all references matched up.
     * This code patches @ref and @id pairings up, in the 'Map of Map' mode.  Where the JSON may contain
     * an '@id' of an object which can have more than one @ref to it, this code will make sure that each
     * '@ref' (value side of the Map associated to a given field name) will be pointer to the appropriate Map
     * instance.
     * <p>
     * Note: We intentionally do NOT call setFinished() here because in Maps mode, the JsonObject itself
     * is the final result, and the same JsonObject may later be converted to Java objects via toJava().
     * Marking it finished here would cause the ObjectResolver to skip it.
     * @param jsonObj a Map-of-Map representation of the current object being examined (containing all fields).
     */
    public void traverseFields(final JsonObject jsonObj) {
        jsonObj.readAsMaps = true;
        final Object target = jsonObj.getTarget();
        final ReadOptions readOptions = getReadOptions();

        // Get injector map directly - ReadOptions.getDeepInjectorMap() already caches per class
        Map<String, Injector> injectorMap = null;
        if (target != null && !(target instanceof Map)) {    // a map's keys are its entries, not fields of its class
            // (an object of a class built from its parts is left unbuilt in this mode: its class types its fields)
            injectorMap = readOptions.getDeepInjectorMap(target == UNBUILT ? jsonObj.getRawType() : target.getClass());
        }

        // By index, through the allocation-free accessors: this loop runs for every object a Maps-mode read builds,
        // and is too large for the JIT to eliminate an entrySet() entry per field.
        final int fieldCount = jsonObj.fastEntryCount();
        for (int i = 0; i < fieldCount; i++) {
            final Object fieldKey = jsonObj.fastKeyAt(i);
            final String fieldName = fieldKey instanceof String ? (String) fieldKey : null;
            final Object rhs = jsonObj.fastValueAt(i);
            
            if (rhs == null) {
                // No action needed - null is already the value in the map entry
                continue;
            }
            
            // Pre-cache class and injector to avoid repeated lookups
            final Class<?> rhsClass = rhs.getClass();
            final Injector injector = (injectorMap == null || fieldName == null) ? null : injectorMap.get(fieldName);

            if (rhs instanceof List) {
                // A TOON list: its @refs patched in it, then on as any other value
                traverseListForRefs((List<Object>) rhs);
            }

            if (rhsClass.isArray()) {   // RHS is an array
                // Traverse array inline to patch @refs - avoids JsonObject wrapper allocation
                // No put needed - rhs is already the value in the map entry
                traverseArrayForRefs((Object[]) rhs);
            } else if (rhs instanceof JsonObject) {
                JsonObject jObj = (JsonObject) rhs;
                if (injector != null) {
                    final Class<?> injectorType = injector.getType();
                    boolean isNonRefClass = readOptions.isNonReferenceableClass(injectorType);
                    if (isNonRefClass) {
                        // The value itself, as Java mode reads it and as the field holds it when written untyped
                        // (it stayed the typed object, holding the value -- or, for a date's map form, a stray null).
                        // A @ref names the object it refers to (a class declared non-referenceable for the read
                        // only may be written with @id): one value for both, as Java mode reads them.
                        JsonObject held = jObj.isReference() ? resolveReference(jObj) : jObj;
                        Object value;
                        try {
                            value = held.isFinished() ? held.getTarget() : nonRefValue(held, injectorType);
                        } catch (RuntimeException e) {
                            continue;   // the JSON as written (see convertIfNonRefType)
                        }
                        held.setFinishedTarget(value, true);
                        jsonObj.fastSetValueAt(i, value);   // Direct update - avoids indexOf() lookup
                        continue;
                    }
                }
                JsonObject refObject = resolveReference(jObj);
                if (refObject != null) {    // Correct field references
                    jsonObj.fastSetValueAt(i, refObject);   // Direct update - avoids indexOf() lookup
                } else {
                    // In Maps mode, convert JsonObjects with simple types (Byte, Short, etc.)
                    // to their Java equivalents and update the Map entry
                    Object converted = convertIfNonRefType(jObj, jObj.getRawType());
                    if (converted != null) {
                        jObj.setFinishedTarget(converted, true);
                        jsonObj.fastSetValueAt(i, converted);  // Direct update - avoids indexOf() lookup
                    } else {
                        push(jObj);
                    }
                }
            } else if (injector != null) {
                // The code below is 'upgrading' the RHS values in the JsonObject Map
                // by using the @type class name (when specified and exists), to coerce the vanilla
                // JSON values into the proper types defined by the class listed in @type.  This is
                // a cool feature of json-io, that even when reading a map-of-maps JSON file, it will
                // improve the final types of values in the maps RHS, to be of the field type that
                // was optionally specified in @type.
                final Class<?> fieldType = injector.getType();
                // Performance: Skip conversion if field type is Object or already matches
                if (fieldType == Object.class || rhsClass == fieldType) {
                    continue;
                }
                // Fast path for common JSON primitive coercions (Long->int, Double->float, etc.)
                int fieldTargetKind = scalarTargetKind(fieldType);
                Object fastValue = fastScalarCoercion(rhs, rhsClass, fieldTargetKind);
                // FAST ISO TEMPORAL PARSE — strict java.time parse for the ISO-8601 wire
                // format before the Converter → DateUtilities regex path. Maps-mode
                // counterpart of the ObjectResolver hooks (see FastIsoParser); non-ISO
                // strings fall through to Converter, preserving flexible-format support.
                if (fastValue == null && rhs instanceof String) {
                    fastValue = FastIsoParser.parse((String) rhs, fieldType);
                }
                if (fastValue != null) {
                    jsonObj.fastSetValueAt(i, fastValue);   // Direct update - avoids indexOf() lookup
                } else if (converter.isConversionSupportedFor(rhsClass, fieldType)) {
                    if (rhs instanceof JsonObject && !Map.class.isAssignableFrom(fieldType)) {
                        resolveTypedValues((JsonObject) rhs);
                    }
                    Object fieldValue;
                    try {
                        // (an enum from the value its @IoValue method writes, then its name)
                        fieldValue = convertValue(rhs, fieldType);
                    } catch (RuntimeException e) {
                        // A value the field's type cannot hold ("three" for an int) stays as written (see
                        // convertIfNonRefType), as does a TOON list where an array or collection is declared, of
                        // elements the Converter does not build (an @IoValue class): left a list, as JSON's array is
                        continue;
                    }
                    jsonObj.fastSetValueAt(i, fieldValue);  // Direct update - avoids indexOf() lookup
                } else if (rhs instanceof String && StringUtilities.isEmpty((String)rhs)) {
                    // Fallback: Allow "" to null out a field when converter doesn't support String conversion.
                    // Note: fieldType cannot be String.class here (line 390 checks rhsClass == fieldType).
                    // StringBuilder/StringBuffer have converter support, so they're handled above.
                    jsonObj.fastSetValueAt(i, null);        // Direct update - avoids indexOf() lookup
                }
            }
        }
        // Clear target for non-Map types to save memory (Maps mode returns JsonObjects for POJOs).
        // For Map types (HashMap, TreeMap, etc.), keep the target - rehashMaps() will populate it.
        Object currentTarget = jsonObj.getTarget();
        if (currentTarget != null && !(currentTarget instanceof Map)) {
            jsonObj.setTarget(null);
        }
    }

    /**
     * An enum written as an object, {"@type":..,"name":..} (writeEnumAsJsonObject) or {"@type":..,"value":..} (one
     * written by its @IoValue method, with its type), as its constant: read through its factory, its value, then its
     * name ({@link #convertValue}). The Converter read such a map as a constant named "value", or from a null. Null
     * when {@code type} is no enum or the object holds neither.
     */
    /**
     * The value a typed object holds for a field of a non-referenceable class: by the object's own {@code @type} where
     * that is more exact (a Timestamp in a Date field, a BigDecimal in a Number field) and the Converter makes it,
     * else by the field's; an enum by its name or value; a date's map form ({"@type":"Date","date":..}) as the
     * Converter reads it. A null the Converter has no value for stays null: a Number field's
     * {"@type":"Float","value":null}, a NaN where JSON has none, is null, as Java mode reads it.
     */
    private Object nonRefValue(JsonObject jObj, Class<?> fieldType) {
        boolean valueForm = jObj.hasValue();
        Object value = valueForm ? jObj.getValue() : null;
        Class<?> type = fieldType;
        Class<?> own = jObj.getRawType();
        if (own != null && Enum.class.isAssignableFrom(own)) {
            own = ClassUtilities.getClassIfEnum(own);      // a constant with a body is of its enum
        }
        if (own != null && own != fieldType && fieldType.isAssignableFrom(own)
                && converter.isConversionSupportedFor(value == null ? Map.class : value.getClass(), own)) {
            type = own;
        }
        Object enumConstant = enumOfJsonObject(jObj, type);
        if (enumConstant != null) {
            return enumConstant;
        }
        // An ISO date's text -- its value, or the one entry of its map form ({"@type":"Instant","instant":".."}) --
        // by the strict parser, as a field's bare text is read: the Converter's general parse costs up to 20 times
        Object text = valueForm ? value : jObj.fastEntryCount() == 1 ? jObj.fastValueAt(0) : null;
        if (text instanceof String) {
            Object parsed = FastIsoParser.parse((String) text, type);
            if (parsed != null) {
                return parsed;
            }
        }
        if (!valueForm && converter.isConversionSupportedFor(Map.class, type)) {
            return converter.convert(resolveTypedValues(jObj), type);
        }
        return value == null && !converter.isConversionSupportedFor(Void.class, type) ? null
                : converter.convert(value, type);
    }

    private Object enumOfJsonObject(JsonObject jsonObj, Class<?> type) {
        if (!Enum.class.isAssignableFrom(type)) {
            return null;
        }
        Object name = jsonObj.get("name");
        Object value = name != null ? name : jsonObj.getValue();
        return value == null ? null : convertValue(value, type);
    }

    /**
     * Handles nested array elements, whether they come as JsonObject instances or raw arrays.
     *
     * @param element        The array element to handle.
     * @param componentType  The immediate component type of the current array level.
     * @param target         The parent array where the nested array should be assigned.
     * @param index          The index in the parent array.
     */
    private void handleNestedArray(Object element, Class<?> componentType, Object target, int index) {
        JsonObject jsonObject = null;

        if (element instanceof JsonObject && ((JsonObject) element).isArray()) {
            jsonObject = (JsonObject) element;
        } else if (element instanceof Object[]) {
            jsonObject = new JsonObjectArray();
            jsonObject.setItems((Object[])element);
        }

        if (jsonObject != null) {
            if (componentType.isArray()) {
                // Set the hintType to guide createInstance to instantiate the correct array type
                jsonObject.setType(componentType);

                // Create a new array instance using createInstance, which respects the hintType
                Object arrayElement = createInstance(jsonObject);

                // Assign the newly created array to the parent array's element
                // Nested arrays are always reference types (Object[]), never primitive arrays
                ((Object[]) target)[index] = arrayElement;

            }
            // Push the JsonObject for further processing
            push(jsonObject);
        } else if (target instanceof Object[]) {
            // Primitive nested arrays cannot be wrapped as Object[]; keep original value.
            ((Object[]) target)[index] = element;
        }
    }

    protected void traverseArray(JsonObject jsonObj) {
        jsonObj.readAsMaps = true;
        // Guard against reprocessing already-finished objects
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        Object[] items = jsonObj.getItems();
        if (ArrayUtilities.isEmpty(items)) {
            return;
        }

        Object target = jsonObj.getTarget() != null ? jsonObj.getTarget() : items;
        Class<?> componentType = getArrayComponentType(jsonObj);
        final int targetComponentKind = scalarTargetKind(componentType);
        // An array whose type declares enum elements ({"@type":"Code[]",..}) reads them as that enum, as a field of that
        // type does; Maps mode builds no array for it, so its declaration is its type, not its target's
        Class<?> declared = jsonObj.getRawType();
        final Class<?> elementType = componentType == Object.class && declared != null && declared.isArray()
                && ClassUtilities.getClassIfEnum(declared.getComponentType()) != null ? declared.getComponentType() : componentType;

        // Optimize: check array type ONCE, not on every element assignment
        final boolean isPrimitive = componentType.isPrimitive();
        final Object[] refArray = isPrimitive ? null : (Object[]) target;

        final int len = items.length;
        for (int i = 0; i < len; i++) {
            Object element = items[i];

            if (element == null) {
                setArrayElement(target, refArray, i, null, isPrimitive);
                continue;
            }

            if (element instanceof List) {
                traverseListForRefs((List<Object>) element);    // a TOON list: patched in place, then stored
            }

            final Class<?> elementClass = element.getClass();
            final JsonObject jsonElement = element instanceof JsonObject ? (JsonObject) element : null;
            if (elementClass.isArray() || (jsonElement != null && jsonElement.isArray())) {
                handleNestedArray(element, componentType, target, i);
            } else {
                processArrayElement(element, jsonElement, elementClass, componentType, elementType, target, refArray, i,
                        isPrimitive, targetComponentKind);
            }
        }
        // Note: setFinished() already called by markFinishedIfNot() at method start
    }

    /**
     * Determine the component type of the array target.
     */
    private Class<?> getArrayComponentType(JsonObject jsonObj) {
        if (jsonObj.getTarget() != null) {
            Class<?> targetClass = jsonObj.getTarget().getClass();
            if (targetClass.isArray()) {
                return targetClass.getComponentType();
            }
        }
        return Object.class;
    }

    /**
     * Process a single array element that is not a nested array.
     */
    private void processArrayElement(Object element, JsonObject jsonElement, Class<?> elementClass, Class<?> componentType,
                                     Class<?> elementType, Object target, Object[] refArray, int index, boolean isPrimitive,
                                     int targetComponentKind) {
        if (tryAssignParsedScalarToArray(target, refArray, index, element, elementClass, isPrimitive, targetComponentKind)) {
            return;
        }

        // Fast path for common JSON primitive coercions
        Object fastValue = fastScalarCoercion(element, elementClass, targetComponentKind);
        // FAST ISO TEMPORAL PARSE — array-element counterpart of the traverseFields hook.
        if (fastValue == null && element instanceof String) {
            fastValue = FastIsoParser.parse((String) element, componentType);
        }
        if (fastValue != null) {
            setArrayElement(target, refArray, index, fastValue, isPrimitive);
            return;
        }

        if (converter.isConversionSupportedFor(elementClass, componentType)) {
            Object convertedValue = convertValue(element, componentType);   // an enum by its value, then its name
            setArrayElement(target, refArray, index, convertedValue, isPrimitive);
            return;
        }

        if (jsonElement != null) {
            processJsonObjectArrayElement(jsonElement, elementType, target, refArray, index, isPrimitive);
            return;
        }

        setArrayElement(target, refArray, index, element, isPrimitive);
    }

    /**
     * Process a JsonObject element within an array.
     */
    private void processJsonObjectArrayElement(JsonObject jsonObject, Class<?> componentType, Object target, Object[] refArray,
                                               int index, boolean isPrimitive) {
        if (jsonObject.isReference()) {
            processArrayReference(jsonObject, componentType, target, refArray, index, isPrimitive);
        } else {
            processArrayJsonObject(jsonObject, componentType, target, refArray, index, isPrimitive);
        }
    }

    /**
     * Process an @ref reference within an array.
     */
    private void processArrayReference(JsonObject jsonObject, Class<?> componentType, Object target, Object[] refArray,
                                       int index, boolean isPrimitive) {
        long refId = jsonObject.getReferenceId();
        JsonObject refObject = references.getOrThrow(refId);

        Object convertedRef = convertArrayElement(refObject, componentType);
        if (convertedRef != null) {
            refObject.setFinishedTarget(convertedRef, true);
            setArrayElement(target, refArray, index, refObject.getTarget(), isPrimitive);
        } else {
            setArrayElement(target, refArray, index, refObject, isPrimitive);
        }
    }

    /**
     * Process a non-reference JsonObject within an array.
     */
    private void processArrayJsonObject(JsonObject jsonObject, Class<?> componentType, Object target, Object[] refArray,
                                        int index, boolean isPrimitive) {
        Object converted = convertArrayElement(jsonObject, componentType);
        if (converted != null) {
            setArrayElement(target, refArray, index, converted, isPrimitive);
            jsonObject.setFinished();
        } else {
            push(jsonObject);
        }
    }

    /**
     * Traverse a JsonObject representing a collection (array) and deserialize its elements.
     *
     * @param jsonObj The JsonObject representing the collection.
     */
    @Override
    @SuppressWarnings("unchecked")  // raw Collection cast — JsonObject.getTarget()/createInstance return Object
    protected void traverseCollection(final JsonObject jsonObj) {
        jsonObj.readAsMaps = true;
        // Guard against reprocessing already-finished objects
        if (markFinishedIfNot(jsonObj)) {
            return;
        }

        // Apply sorted collection substitution ONLY for types from JSON's @type (typeString set),
        // not for types from user's asClass() request (consistent with traverseMap behavior)
        if (jsonObj.getTypeString() != null) {
            substituteSortedCollectionType(jsonObj);
        }

        Object[] items = jsonObj.getItems();
        Collection<Object> col = (Collection<Object>) jsonObj.getTarget();
        if (col == null) {
            col = (Collection<Object>) createInstance(jsonObj);
            if (col instanceof EnumSet) {
                // Built from its items by its factory (EnumSetFactory), as at the root: its names were added to it
                // again (a ClassCastException) where an EnumSet stands in a list or map
                return;
            }
        }

        // Performance: Pre-size ArrayList to avoid repeated resizing
        if (items != null) {
            ensureCollectionCapacity(col, items.length);
        }

        final boolean isList = col instanceof List;
        final boolean isEnumSet = col instanceof EnumSet;
        int idx = 0;

        if (items != null) {
            for (Object element : items) {
                if (element == null) {
                    col.add(null);
                } else if (isDirectlyAddableJsonValue(element)) {
                    col.add(element);
                } else if (element instanceof Object[]) {
                    wrapArrayAndAddToCollection((Object[]) element, Object[].class, col);
                } else {
                    if (element instanceof JsonObject) {
                        processJsonObjectElement((JsonObject) element, jsonObj, col, idx, isList, isEnumSet);
                    } else {
                        if (element instanceof List) {
                            traverseListForRefs((List<Object>) element);    // a TOON list: patched in place
                        }
                        // Preserve non-JsonObject values in maps mode instead of hard-casting.
                        col.add(element);
                    }
                }
                idx++;
            }
        }
        // Note: setFinished() already called by markFinishedIfNot() at method start
    }

    /**
     * Process a JsonObject element within a collection traversal.
     */
    private void processJsonObjectElement(JsonObject jObj, JsonObject parent, Collection<Object> col,
                                          int idx, boolean isList, boolean isEnumSet) {
        if (jObj.isReference()) {
            // The object the @ref names, in the tree as in a plain array: the tree kept the object holding the @ref,
            // so written back it was {} and the object it named lost its @id. Every @id is known once the text is
            // parsed, a forward one too. An object left JSON is the list's element itself (it was a null).
            JsonObject named = resolveReference(jObj);
            parent.getItems()[idx] = valueOf(named);
            if (staysJson(named)) {
                col.add(named);
            } else {
                resolveReferenceInCollection(jObj, parent, col, idx, isList);
            }
            return;
        }

        // Handle EnumSet special case: skip enum elements without name
        if (isEnumSet) {
            Class<?> rawType = jObj.getRawType();
            boolean noEnumName = !jObj.containsKey("name") && !jObj.containsKey("Enum.name") && !jObj.hasValue();
            if (rawType != null && rawType.isEnum() && noEnumName) {
                jObj.setFinished();
                return;
            }
        }

        jObj.setType(Object.class);
        if (staysJson(jObj)) {
            // An object stays JSON here, as in a field: not built, its fields as JSON gives them. It was built when its
            // class was on the classpath (its constructor run, its fields typed by its class), and a list read at the
            // root as a List lost it: a null in its place, its target cleared once its fields were read.
            push(jObj);
            col.add(jObj);
            return;
        }
        createInstance(jObj);
        Object value = valueOf(jObj);
        if (value != jObj) {
            parent.getItems()[idx] = value;     // the value, in the tree as in the list
        }
        addResolvedObjectToCollection(jObj, col);
    }

    /**
     * An object of any class, on the classpath or not -- not a collection, a map or an array, nor a value (see
     * {@link #isValueClass}: a number, a date, a UUID, a simple enum's constant), which are read as before. Another
     * enum's constant stays JSON too: one with a body (its class an anonymous subclass of its enum) was a null.
     */
    private boolean staysJson(JsonObject jObj) {
        return jObj.getJsonType() == JsonObject.JsonType.OBJECT && !isValueClass(jObj.getRawType());
    }

    /**
     * The class of a value Maps mode reads as that value: one of the Converter's value types (a number, a date, a
     * UUID: on every JVM), or a simple enum on the classpath. Any other enum -- with fields of its own, or a constant
     * with a body -- stays JSON, as an object of any other class does.
     */
    private boolean isValueClass(Class<?> type) {
        if (type == null) {
            return false;
        }
        Class<?> enumClass = ClassUtilities.getClassIfEnum(type);
        if (enumClass != null) {
            return isSimpleEnum(enumClass);
        }
        return getReadOptions().isNonReferenceableClass(type) && converter.isConversionSupportedFor(Map.class, type);
    }

    /**
     * An enum that is only its constants: no fields of its own and no constant with a body. Told without initializing
     * it: an enum is final unless a constant has a body (JLS 8.9). Once per class (getDeclaredFields() copies them).
     */
    static boolean isSimpleEnum(Class<?> enumClass) {
        return SIMPLE_ENUMS.get(enumClass);
    }

    private static final ClassValue<Boolean> SIMPLE_ENUMS = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> enumClass) {
            if (!Modifier.isFinal(enumClass.getModifiers())) {
                return false;
            }
            for (Field field : enumClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    return false;
                }
            }
            return true;
        }
    };

    /**
     * An element of an array as its value: an enum constant of any enum where the array's type declares enum elements
     * ({"@type":"Code[]",..}), as a field's declared type does; otherwise as any value (see {@link #isValueClass}).
     */
    private Object convertArrayElement(JsonObject element, Class<?> componentType) {
        if (ClassUtilities.getClassIfEnum(componentType) != null) {
            return super.convertIfNonRefType(element, element.getRawType());
        }
        return convertIfNonRefType(element, element.getRawType());
    }

    /** An EnumSet's form: its enum's type, with its constants as @items. A container, read as before. */
    private static boolean isEnumSet(JsonObject jObj) {
        return jObj.getItems() != null;
    }

    /**
     * A constant of an enum other than a simple one stays JSON (see {@link #isValueClass}); a value type is its value.
     * An EnumSet is read as before. A value that does not convert -- {"@type":"BigDecimal","value":"abc"}, a constant
     * its enum does not have -- stays the JSON that holds it, as written: this mode reads JSON it may not be the
     * author of, and the value is still there, unchanged, to read or write back. It failed the read; in Java mode it
     * is an error still.
     */
    @Override
    protected Object convertIfNonRefType(JsonObject jsonObj, Class<?> type) {
        Class<?> enumClass = type == null ? null : ClassUtilities.getClassIfEnum(type);
        if (enumClass != null && !isEnumSet(jsonObj) && !isSimpleEnum(enumClass)) {
            return null;
        }
        try {
            return super.convertIfNonRefType(jsonObj, type);
        } catch (RuntimeException e) {
            if (isEnumSet(jsonObj)) {
                throw e;    // an EnumSet holding a name its enum does not have: read as a set of strings, it failed worse
            }
            return null;
        }
    }

    protected Object resolveArray(Type suggestedType, List<Object> list) {
        // Extract the raw class from the provided Type.
        Class<?> rawType = (suggestedType == null) ? null : TypeUtilities.getRawClass(suggestedType);

        // If there's no suggested type or the raw type is Object, just return an Object[].
        if (suggestedType == null || rawType == Object.class) {
            return list.toArray();
        }

        JsonObject jsonArray = new JsonObjectArray();

        // Store the full, refined type (which may include generics) in the JsonObject.
        jsonArray.setType(suggestedType);

        // Apply sorted collection substitution for Maps mode
        substituteSortedCollectionType(jsonArray);

        // If the Collection is assignable from raw type, create a Collection instance accordingly.
        if (Collection.class.isAssignableFrom(rawType)) {
            jsonArray.setTarget(createInstance(jsonArray));
        } else {
            // Otherwise, assume it's an array type and create a new array instance.
            jsonArray.setTarget(Array.newInstance(rawType, list.size()));
        }

        jsonArray.setItems(list.toArray());
        return jsonArray;
    }

}
