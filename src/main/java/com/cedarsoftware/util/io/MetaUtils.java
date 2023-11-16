package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.DateFactory;
import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.ClassDescriptor;
import com.cedarsoftware.util.reflect.ClassDescriptors;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

/**
 * This utility class has the methods mostly related to reflection related code.
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
public class MetaUtils
{
    private MetaUtils () {}
    enum Dumpty {}
    private static final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();
    private static final Set<Class<?>> prims = new HashSet<>();
    private static final Map<String, Class<?>> nameToClass = new HashMap<>();
    private static final Byte[] byteCache = new Byte[256];
    private static final Pattern extraQuotes = Pattern.compile("^\"*(.*?)\"*$");
    private static final ConcurrentMap<String, CachedConstructor> constructors = new ConcurrentHashMap<>();
    private static final Collection<?> unmodifiableCollection = Collections.unmodifiableCollection(new ArrayList<>());
    private static final Set<?> unmodifiableSet = Collections.unmodifiableSet(new HashSet<>());
    private static final SortedSet<?> unmodifiableSortedSet = Collections.unmodifiableSortedSet(new TreeSet<>());
    private static final Map<?, ?> unmodifiableMap = Collections.unmodifiableMap(new HashMap<>());
    private static final SortedMap<?, ?> unmodifiableSortedMap = Collections.unmodifiableSortedMap(new TreeMap<>());
    static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    private static boolean useUnsafe = false;
    private static Unsafe unsafe;

    private static final Map<Class<?>, Supplier<Object>> DIRECT_CLASS_MAPPING = new HashMap<>();

    private static final Map<Class<?>, Supplier<Object>> ASSIGNABLE_CLASS_MAPPING = new LinkedHashMap<>();

    private static final Map<Class<?>, Object> FROM_NULL = new LinkedHashMap<>();

    static {
        //  TODO: These might need to go into ReadOptions to allow people to customize?  JD: Agreed.
        DIRECT_CLASS_MAPPING.put(Date.class, Date::new);
        DIRECT_CLASS_MAPPING.put(StringBuilder.class, StringBuilder::new);
        DIRECT_CLASS_MAPPING.put(StringBuffer.class, StringBuffer::new);
        DIRECT_CLASS_MAPPING.put(Locale.class, Locale::getDefault);
        DIRECT_CLASS_MAPPING.put(TimeZone.class, TimeZone::getDefault);
        DIRECT_CLASS_MAPPING.put(Timestamp.class, () -> new Timestamp(System.currentTimeMillis()));
        DIRECT_CLASS_MAPPING.put(java.sql.Date.class, () -> new java.sql.Date(System.currentTimeMillis()));
        DIRECT_CLASS_MAPPING.put(LocalDate.class, LocalDate::now);
        DIRECT_CLASS_MAPPING.put(LocalDateTime.class, LocalDateTime::now);
        DIRECT_CLASS_MAPPING.put(ZonedDateTime.class, ZonedDateTime::now);
        DIRECT_CLASS_MAPPING.put(ZoneId.class, ZoneId::systemDefault);
        DIRECT_CLASS_MAPPING.put(AtomicBoolean.class, AtomicBoolean::new);
        DIRECT_CLASS_MAPPING.put(AtomicInteger.class, AtomicInteger::new);
        DIRECT_CLASS_MAPPING.put(AtomicLong.class, AtomicLong::new);
        DIRECT_CLASS_MAPPING.put(URL.class, () -> safelyIgnoreException(() -> new URL("http://localhost"), null));
        DIRECT_CLASS_MAPPING.put(Object.class, Object::new);
        DIRECT_CLASS_MAPPING.put(String.class, () -> "");
        DIRECT_CLASS_MAPPING.put(BigInteger.class, () -> BigInteger.ZERO);
        DIRECT_CLASS_MAPPING.put(BigDecimal.class, () -> BigDecimal.ZERO);
        DIRECT_CLASS_MAPPING.put(Class.class, () -> String.class);
        DIRECT_CLASS_MAPPING.put(Calendar.class, Calendar::getInstance);
        DIRECT_CLASS_MAPPING.put(Instant.class, Instant::now);

        // order is important
        ASSIGNABLE_CLASS_MAPPING.put(EnumSet.class, () -> null);
        ASSIGNABLE_CLASS_MAPPING.put(List.class, ArrayList::new);
        ASSIGNABLE_CLASS_MAPPING.put(SortedSet.class, TreeSet::new);
        ASSIGNABLE_CLASS_MAPPING.put(Set.class, LinkedHashSet::new);
        ASSIGNABLE_CLASS_MAPPING.put(SortedMap.class, TreeMap::new);
        ASSIGNABLE_CLASS_MAPPING.put(Map.class, LinkedHashMap::new);
        ASSIGNABLE_CLASS_MAPPING.put(Collection.class, ArrayList::new);
        ASSIGNABLE_CLASS_MAPPING.put(Calendar.class, Calendar::getInstance);
        ASSIGNABLE_CLASS_MAPPING.put(LinkedHashSet.class, LinkedHashSet::new);

        FROM_NULL.put(Boolean.class, false);
        FROM_NULL.put(boolean.class, false);
        FROM_NULL.put(byte.class, (byte) 0);
        FROM_NULL.put(Byte.class, (byte) 0);
        FROM_NULL.put(short.class, (short) 0);
        FROM_NULL.put(Short.class, short.class);
        FROM_NULL.put(int.class, 0);
        FROM_NULL.put(Integer.class, 0);
        FROM_NULL.put(long.class, 0L);
        FROM_NULL.put(Long.class, 0L);
        FROM_NULL.put(double.class, 0.0d);
        FROM_NULL.put(Double.class, 0.0d);
        FROM_NULL.put(float.class, 0.0f);
        FROM_NULL.put(Float.class, 0.0f);
        FROM_NULL.put(char.class, '\u0000');
        FROM_NULL.put(Character.class, '\u0000');
    }

    /**
     * Globally turn on (or off) the 'unsafe' option of Class construction.  The unsafe option
     * is used when all constructors have been tried and the Java class could not be instantiated.
     * @param state boolean true = on, false = off.
     */
    public static void setUseUnsafe(boolean state)
    {
        useUnsafe = state;
        if (state)
        {
            try
            {
                unsafe = new Unsafe();
            }
            catch (InvocationTargetException e)
            {
                useUnsafe = false;
            }
        }
    }

    static
    {
        prims.add(Byte.class);
        prims.add(Integer.class);
        prims.add(Long.class);
        prims.add(Double.class);
        prims.add(Character.class);
        prims.add(Float.class);
        prims.add(Boolean.class);
        prims.add(Short.class);

        nameToClass.put("boolean", boolean.class);
        nameToClass.put("char", char.class);
        nameToClass.put("byte", byte.class);
        nameToClass.put("short", short.class);
        nameToClass.put("int", int.class);
        nameToClass.put("long", long.class);
        nameToClass.put("float", float.class);
        nameToClass.put("double", double.class);
        // Logical primitives
        nameToClass.put("string", String.class);
        nameToClass.put("date", Date.class);
        nameToClass.put("class", Class.class);

        // Save memory by re-using all byte instances (Bytes are immutable)
        for (int i = 0; i < byteCache.length; i++)
        {
            byteCache[i] = (byte) (i - 128);
        }
    }

    /**
     * For JDK1.8 support.  Remove this and change to List.of() for JDK11+
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... items)
    {
        if (items == null || items.length ==0)
        {
            return Collections.unmodifiableList(new ArrayList<>());
        }
        List<T> list = new ArrayList<>();
        Collections.addAll(list, items);
        return Collections.unmodifiableList(list);
    }

    /**
     * For JDK1.8 support.  Remove this and change to Set.of() for JDK11+
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... items)
    {
        if (items == null || items.length ==0)
        {
            return (Set<T>) unmodifiableSet;
        }
        Set<T> set = new LinkedHashSet<>();
        Collections.addAll(set, items);
        return set;
    }

    /**
     * For JDK1.8 support.  Remove this and change to Map.of() for JDK11+
     */
    public static <K, V> Map<K, V> mapOf()
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>());
    }

    public static <K, V> Map<K, V> mapOf(K k, V v)
    {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k, v);
        return Collections.unmodifiableMap(map);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2)
    {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return Collections.unmodifiableMap(map);
    }

    public static <K, V> Map<K, V> mapOf(K k1, V v1, K k2, V v2, K k3, V v3)
    {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Return an instance of the Java Field class corresponding to the passed in field name.
     * @param c class containing the field / field name
     * @param field String name of a field on the class.
     * @return Field instance if the field with the corresponding name is found, null otherwise.
     */
    public static Field getField(Class<?> c, String field)
    {
        return getDeepDeclaredFields(c).get(field);
    }

    /**
     * @param c Class instance
     * @return ClassMeta which contains fields of class.  The results are cached internally for performance
     *         when called again with same Class.
     */
    public static Map<String, Field> getDeepDeclaredFields(Class<?> c)
    {
        Map<String, Field> classFields = classMetaCache.get(c);
        if (classFields != null)
        {
            return classFields;
        }

        classFields = new LinkedHashMap<>();
        Class<?> curr = c;

        while (curr != null)
        {
            final Field[] local = curr.getDeclaredFields();

            for (Field field : local)
            {
                int modifiers = field.getModifiers();
                if (isStatic(modifiers))
                {   // skip static fields (allow transient, because  that is an option for json-io)
                    continue;
                }
                String fieldName = field.getName();
                if ("metaClass".equals(fieldName) && "groovy.lang.MetaClass".equals(field.getType().getName()))
                {   // skip Groovy metaClass field if present (without tying this project to Groovy in any way).
                    continue;
                }

                if (field.getDeclaringClass().isAssignableFrom(Enum.class))
                {   // For Enum fields, do not add .hash or .ordinal fields to output
                    // TODO:  We may want to use ClassDescriptor logic since it filters these for us already?
                    if ("hash".equals(fieldName) || "ordinal".equals(fieldName) || "internal".equals(fieldName))
                    {
                        continue;
                    }
                }
                if (classFields.containsKey(fieldName))
                {   // Field name collision in inheritance hierarchy.  Use prefix of parent class name '.' field name to
                    // disambiguate instances of the field (Each one can have it's own unique value).
                    classFields.put(curr.getSimpleName() + '.' + fieldName, field);
                }
                else
                {
                    classFields.put(fieldName, field);
                }

                if (!isPublic(modifiers) || !isPublic(field.getDeclaringClass().getModifiers()))
                {
                    MetaUtils.trySetAccessible(field);
                }
            }

            curr = curr.getSuperclass();
        }

        classMetaCache.put(c, classFields);
        return new LinkedHashMap<>(classFields);
    }

    /**
     * Compare a primitive to a primitive Wrapper.
     * @return 0 if they are the same, -1 if not.  Primitive wrapper classes are consider the same as primitive classes.
     */
    public static int comparePrimitiveToWrapper(Class<?> source, Class<?> destination) {
        try {
            return source.getField("TYPE").get(null).equals(destination) ? 0 : -1;
        }
        catch (Exception e) {
            throw new JsonIoException("Error while attempting comparison of primitive types: " + source.getName() + " vs " + destination.getName(), e);
        }
    }

    /**
     * Computes the inheritance distance between two classes/interfaces/primitive types.
     * @param source      The source class, interface, or primitive type.
     * @param destination The destination class, interface, or primitive type.
     * @return The number of steps from the source to the destination, or -1 if no path exists.
     */
    public static int computeInheritanceDistance(Class<?> source, Class<?> destination) {
        if (source == null || destination == null) {
            return -1;
        }
        if (source.equals(destination)) {
            return 0;
        }

        // Check for primitive types
        if (source.isPrimitive()) {
            if (destination.isPrimitive()) {
                // Not equal because source.equals(destination) already checked.
                return -1;
            }
            if (!MetaUtils.isPrimitive(destination)) {
                return -1;
            }
            return comparePrimitiveToWrapper(destination, source);
        }

        if (destination.isPrimitive()) {
            if (!MetaUtils.isPrimitive(source)) {
                return -1;
            }
            return comparePrimitiveToWrapper(source, destination);
        }

        Queue<Class<?>> queue = new LinkedList<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(source);
        visited.add(source);
        int distance = 0;

        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            distance++;

            for (int i = 0; i < levelSize; i++) {
                Class<?> current = queue.poll();

                // Check superclass
                if (current.getSuperclass() != null) {
                    if (current.getSuperclass().equals(destination)) {
                        return distance;
                    }
                    if (!visited.contains(current.getSuperclass())) {
                        queue.add(current.getSuperclass());
                        visited.add(current.getSuperclass());
                    }
                }

                // Check interfaces
                for (Class<?> interfaceClass : current.getInterfaces()) {
                    if (interfaceClass.equals(destination)) {
                        return distance;
                    }
                    if (!visited.contains(interfaceClass)) {
                        queue.add(interfaceClass);
                        visited.add(interfaceClass);
                    }
                }
            }
        }

        return -1; // No path found
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a Java primitive, false otherwise.  The Wrapper classes
     * Integer, Long, Boolean, etc. are considered primitives by this method.
     */
    public static boolean isPrimitive(Class<?> c)
    {
        return c.isPrimitive() || prims.contains(c);
    }

    /**
     * @param c Class to test
     * @return boolean true if the passed in class is a 'logical' primitive.  A logical primitive is defined
     * as all Java primitives, the primitive wrapper classes, String, Number, and Date.  This covers BigDecimal,
     * BigInteger, AtomicInteger, AtomicLong, as these are 'Number instances. The reason these are considered
     * 'logical' primitives is that they are immutable and therefore can be written without references in JSON
     * content (making the JSON more readable - less @id / @ref), without breaking the semantics (shape) of the
     * object graph being written.
     */
    public static boolean isLogicalPrimitive(Class<?> c)
    {
        return  c.isPrimitive() ||
                prims.contains(c) ||
                String.class.isAssignableFrom(c) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

    public static Optional<Class> getClassIfEnum(Class<?> c) {
        if (c.isEnum()) {
            return Optional.of(c);
        }

        if (!Enum.class.isAssignableFrom(c)) {
            return Optional.empty();
        }

        Class<?> enclosingClass = c.getEnclosingClass();
        return enclosingClass != null && enclosingClass.isEnum() ? Optional.of(enclosingClass) : Optional.empty();
    }

    /**
     * Given the passed in String class name, return the named JVM class.
     * @param name String name of a JVM class.
     * @param classLoader ClassLoader to use when searching for JVM classes.
     * @return Class instance of the named JVM class or null if not found.
     */
    public static Class<?> classForName(String name, ClassLoader classLoader)
    {
        if (name == null || name.isEmpty())
        {
            return null;
        }
        try
        {
            Class<?> c = nameToClass.get(name);
            if (c != null)
            {
                return c;
            }
            c = loadClass(name, classLoader);
            nameToClass.put(name, c);
            return c;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * loadClass() provided by: Thomas Margreiter
     */
    private static Class<?> loadClass(String name, ClassLoader classLoader) throws ClassNotFoundException
    {
        String className = name;
        boolean arrayType = false;
        Class<?> primitiveArray = null;

        while (className.startsWith("["))
        {
            arrayType = true;
            if (className.endsWith(";"))
            {
                className = className.substring(0, className.length() - 1);
            }
            if (className.equals("[B"))
            {
                primitiveArray = byte[].class;
            }
            else if (className.equals("[S"))
            {
                primitiveArray = short[].class;
            }
            else if (className.equals("[I"))
            {
                primitiveArray = int[].class;
            }
            else if (className.equals("[J"))
            {
                primitiveArray = long[].class;
            }
            else if (className.equals("[F"))
            {
                primitiveArray = float[].class;
            }
            else if (className.equals("[D"))
            {
                primitiveArray = double[].class;
            }
            else if (className.equals("[Z"))
            {
                primitiveArray = boolean[].class;
            }
            else if (className.equals("[C"))
            {
                primitiveArray = char[].class;
            }
            int startpos = className.startsWith("[L") ? 2 : 1;
            className = className.substring(startpos);
        }
        Class<?> currentClass = null;
        if (null == primitiveArray)
        {
            try
            {
                currentClass = classLoader.loadClass(className);
            }
            catch (ClassNotFoundException e)
            {
                currentClass = Thread.currentThread().getContextClassLoader().loadClass(className);
            }
        }

        if (arrayType)
        {
            currentClass = (null != primitiveArray) ? primitiveArray : Array.newInstance(currentClass, 0).getClass();
            while (name.startsWith("[["))
            {
                currentClass = Array.newInstance(currentClass, 0).getClass();
                name = name.substring(1);
            }
        }
        return currentClass;
    }

    /**
     * Strip leading and trailing double quotes from the passed in String. If there are more than one
     * set of quotes, ""this is weird"" then all leading and trailing quotes will be removed, yielding
     * this is weird.  Note that: """this is "really" weird" will be: this is "really" weird.
     */
    static String removeLeadingAndTrailingQuotes(String input)
    {
        Matcher m = extraQuotes.matcher(input);
        if (m.find())
        {
            input = m.group(1);
        }
        return input;
    }

    static void throwIfSecurityConcern(Class<?> securityConcern, Class<?> c)
    {
        if (securityConcern.isAssignableFrom(c))
        {
            throw new IllegalArgumentException("For security reasons, json-io does not allow instantiation of: " + securityConcern.getName());
        }
    }

    static Object getArgForType(Class<?> argType) {
        if (argType.isPrimitive() || prims.contains(argType)) {
            return convert(argType, null);  // Get the defaults (false, 0, 0.0d, etc.)
        }

        Supplier<Object> directClassMapping = DIRECT_CLASS_MAPPING.get(argType);

        if (directClassMapping != null) {
            return directClassMapping.get();
        }

        for (Map.Entry<Class<?>, Supplier<Object>> entry : ASSIGNABLE_CLASS_MAPPING.entrySet()) {
            if (entry.getKey().isAssignableFrom(argType)) {
                return entry.getValue().get();
            }
        }

        if (argType.isArray()) {
            return Array.newInstance(argType.getComponentType(), 0);
        }

        return null;
    }

    /**
     * Build a List the same size of parameterTypes, where the objects in the list are ordered
     * to best match the parameters.  Values from the passed in list are used only once or never.
     * @param values A list of potential arguments.  This list can be smaller than parameterTypes
     *               or larger.
     * @param parameterTypes A list of classes that the values will be matched against.
     * @return List of values that are best ordered to match the passed in parameter types.  This
     * list will be the same length as the passed in parameterTypes list.
     */
    public static List<Object> matchArgumentsToParameters(Collection<Object> values, Parameter[] parameterTypes, boolean useNull) {
        List<Object> answer = new ArrayList<>();
        if (parameterTypes == null || parameterTypes.length == 0) {
            return answer;
        }
        List<Object> copyValues = new ArrayList<>(values);

        for (Parameter parameter : parameterTypes) {
            final Class<?> paramType = parameter.getType();
            Object value = pickBestValue(paramType, copyValues);
            if (value == null) {
                if (useNull) {
                    value = paramType.isPrimitive() ? convert(paramType, null) : null;  // don't send null to a primitive parameter
                }
                else {
                    value = getArgForType(paramType);
                }
            }
            answer.add(value);
        }
        return answer;
    }

    /**
     * Pick the best value from the list that has the least 'distance' from the passed in Class 'param.'
     * Note: this method has a side effect - it will remove the value that was chosen from the list.
     * Note: If none of the instances in the 'values' list are instances of the 'param class,
     * then the values list is not modified.
     * @param param Class driving the choice.
     * @param values List of potential argument values to pick from, that would best match the param (class).
     * @return a value from the 'values' list that best matched the 'param,' or null if none of the values
     * were assignable to the 'param'.
     */
    private static Object pickBestValue(Class<?> param, List<Object> values) {
        int[] distances = new int[values.size()];
        int i=0;

        for (Object value : values) {
            distances[i++] = value == null ? -1 : computeInheritanceDistance(value.getClass(), param);
        }

        int index = indexOfBestValue(distances);
        if (index >= 0) {
            Object valueBestMatching = values.get(index);
            values.remove(index);
            return valueBestMatching;
        } else {
            return null;
        }
    }

    /**
     * Returns the index of the smallest value in an array.
     * @param array The array to search.
     * @return The index of the smallest value, or -1 if the array is empty.
     */
    public static int indexOfBestValue(int[] array) {
        if (array == null || array.length == 0) {
            return -1; // Return -1 for null or empty array.
        }

        int minValue = Integer.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < minValue & array[i] > -1) {
                minValue = array[i];
                minIndex = i;
            }
        }

        return minIndex;
    }

    /**
     * Ideal class to hold all constructors for a Class, so that they are sorted in the most
     * appeasing construction order, in terms of public vs protected vs private.  That could be
     * the same, so then it looks at values passed into the arguments, non-null being more
     * valuable than null, as well as number of argument types - more is better than fewer.
     */
    private static class ConstructorWithValues implements Comparable<ConstructorWithValues>
    {
        final Constructor<?> constructor;
        final Object[] argsNull;
        final Object[] argsNonNull;

        ConstructorWithValues(Constructor<?> constructor, Object[] argsNull, Object[] argsNonNull)
        {
            this.constructor = constructor;
            this.argsNull = argsNull;
            this.argsNonNull = argsNonNull;
        }

        public int compareTo(ConstructorWithValues other)
        {
            final int mods = constructor.getModifiers();
            final int otherMods = other.constructor.getModifiers();

            // Rule 1: Visibility: favor public over non-public
            if (!isPublic(mods) && isPublic(otherMods)) {
                return 1;
            }
            else if (isPublic(mods) && !isPublic(otherMods)) {
                return -1;
            }

            // Rule 2: Visibility: favor protected over private
            if (!isProtected(mods) && isProtected(otherMods)) {
                return 1;
            }
            else if (isProtected(mods) && !isProtected(otherMods)) {
                return -1;
            }

            // Rule 3: Sort by score of the argsNull list
            long score1 = scoreArgumentValues(argsNull);
            long score2 = scoreArgumentValues(other.argsNull);
            if (score1 < score2) {
                return 1;
            }
            else if (score1 > score2) {
                return -1;
            }

            // Rule 4: Sort by score of the argsNonNull list
            score1 = scoreArgumentValues(argsNonNull);
            score2 = scoreArgumentValues(other.argsNonNull);
            if (score1 < score2) {
                return 1;
            }
            else if (score1 > score2) {
                return -1;
            }

            // Rule 5: Favor by Class of parameter type alphabetically.  Mainly, distinguish so that no constructors
            // are dropped from the Set.  Although an "arbitrary" rule, it is consistent.
            String params1 = buildParameterTypeString(constructor);
            String params2 = buildParameterTypeString(other.constructor);
            return params1.compareTo(params2);
        }

        /**
         * The more non-null arguments you have, the higher your score. 100 points for each non-null argument.
         * 50 points for each parameter.  So non-null values are twice as high (100 points versus 50 points) as
         * parameter "slots."
         */
        private long scoreArgumentValues(Object[] args)
        {
            if (args.length == 0) {
                return 0L;
            }

            int nonNull = 0;

            for (Object arg : args) {
                if (arg != null) {
                    nonNull++;
                }
            }

            return nonNull * 100L + args.length * 50L;
        }

        private String buildParameterTypeString(Constructor<?> constructor)
        {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            StringBuilder s = new StringBuilder();

            for (Class<?> paramType : paramTypes) {
                s.append(paramType.getName()).append(".");
            }
            return s.toString();
        }
    }

    public static String createCacheKey(Class<?> c, Collection<?> args)
    {
        StringBuilder s = new StringBuilder(c.getName());
        for (Object o : args) {
            if (o == null) {
                s.append(":null");
            } else {
                s.append(':');
                s.append(o.getClass().getSimpleName());
            }
        }
        return s.toString();
    }

    private static class CachedConstructor {
        private final Constructor<?> constructor;
        private final boolean useNullSetting;

        CachedConstructor(Constructor<?> constructor, boolean useNullSetting) {
            this.constructor = constructor;
            this.useNullSetting = useNullSetting;
        }
    }

    /**
     * Create a new instance of the passed in class c.  You can optionally pass in argument values that will
     * be best-matched to a constructor on c.  You can pass in null or an empty list, in which case, other
     * techniques will be used to attempt to instantiate the class.  For security reasons, Process, ClassLoader,
     * ProcessBuilder, Constructor, Method, and Field cannot be instantiated.
     * @param c Class to instantiate.
     * @param argumentValues List of values to supply to a constructor on 'c'.  The constructor chosen on 'c'
     *                       will be the one with a combination of the most fields that are satisfied with
     *                       non-null values from the 'argumentsValues.'  The method will attempt to use values
     *                       from the list as constructor arguments for the passed in class c, ordering them
     *                       to best-fit the constructor, by matching the class type of the argument values
     *                       to the class types of the parameters on 'c' constructors.  It will use all
     *                       constructors exhaustively, until it is successful.  If not, then it will look at
     *                       the 'unsafe' setting and attempt to use that.
     * @return an instance of the passed in class.
     * @throws JsonIoException if it could not instantiate the passed in class.  In that case, it is best to
     * create a ClassFactory for this specific class, and add that to the ReadOptions as an instantiator
     * that is associated to the class 'c' that is not constructing for you.
     */
    public static Object newInstance(Class<?> c, Collection<?> argumentValues) {
        throwIfSecurityConcern(ProcessBuilder.class, c);
        throwIfSecurityConcern(Process.class, c);
        throwIfSecurityConcern(ClassLoader.class, c);
        throwIfSecurityConcern(Constructor.class, c);
        throwIfSecurityConcern(Method.class, c);
        throwIfSecurityConcern(Field.class, c);
        // JDK11+ remove the line below
        if (c.getName().equals("java.lang.ProcessImpl")) {
            throw new IllegalArgumentException("For security reasons, json-io does not allow instantiation of: java.lang.ProcessImpl");
        }

        if (argumentValues == null) {
            argumentValues = new ArrayList<>();
        }

        final String cacheKey = createCacheKey(c, argumentValues);
        CachedConstructor cachedConstructor = constructors.get(cacheKey);
        if (cachedConstructor == null)
        {
            if (unmodifiableSortedMap.getClass().isAssignableFrom(c)) {
                return new TreeMap<>();
            }
            if (unmodifiableMap.getClass().isAssignableFrom(c)) {
                return new LinkedHashMap<>();
            }
            if (unmodifiableSortedSet.getClass().isAssignableFrom(c)) {
                return new TreeSet<>();
            }
            if (unmodifiableSet.getClass().isAssignableFrom(c)) {
                return new LinkedHashSet<>();
            }
            if (unmodifiableCollection.getClass().isAssignableFrom(c)) {
                return new ArrayList<>();
            }
            if (Collections.EMPTY_LIST.getClass().equals(c)) {
                return Collections.emptyList();
            }
            if (c.isInterface()) {
                throw new JsonIoException("Cannot instantiate unknown interface: " + c.getName());
            }

            final Constructor<?>[] declaredConstructors = c.getDeclaredConstructors();
            Set<ConstructorWithValues> constructorOrder = new TreeSet<>();
            List<Object> argValues = new ArrayList<>(argumentValues);   // Copy to allow destruction

            // Spin through all constructors, adding the constructor and the best match of arguments for it, as an
            // Object to a Set.  The Set is ordered by ConstructorWithValues.compareTo().
            for (Constructor<?> constructor : declaredConstructors) {
                Parameter[] parameters = constructor.getParameters();
                List<Object> argumentsNull = matchArgumentsToParameters(argValues, parameters, true);
                List<Object> argumentsNonNull = matchArgumentsToParameters(argValues, parameters, false);
                constructorOrder.add(new ConstructorWithValues(constructor, argumentsNull.toArray(), argumentsNonNull.toArray()));
            }

            for (ConstructorWithValues constructorWithValues : constructorOrder) {
                Constructor<?> constructor = constructorWithValues.constructor;
                try {
                    MetaUtils.trySetAccessible(constructor);
                    Object o = constructor.newInstance(constructorWithValues.argsNull);
                    // cache constructor search effort (null used for parameters of common types not matched to arguments)
                    constructors.put(cacheKey, new CachedConstructor(constructor, true));
                    return o;
                } catch (Exception ignore) {
                    try {
                        if (constructor.getParameterCount() > 0) {
                            // The no-arg constructor should only be tried one time.
                            Object o = constructor.newInstance(constructorWithValues.argsNonNull);
                            // cache constructor search effort (non-null used for parameters of common types not matched to arguments)
                            constructors.put(cacheKey, new CachedConstructor(constructor, false));
                            return o;
                        }
                    }
                    catch (Exception ignored) {
                    }
                }
            }

            Object o = tryUnsafeInstantiation(c);
            if (o != null) {
                return o;
            }
        } else {
            List<Object> argValues = new ArrayList<>(argumentValues);   // Copy to allow destruction
            Parameter[] parameters = cachedConstructor.constructor.getParameters();
            List<Object> arguments = matchArgumentsToParameters(argValues, parameters, cachedConstructor.useNullSetting);

            try {
                // Be nice to person debugging
                Object o = cachedConstructor.constructor.newInstance(arguments.toArray());
                return o;
            }
            catch (Exception ignored) {
            }

            Object o = tryUnsafeInstantiation(c);
            if (o != null) {
                return o;
            }
        }

        throw new JsonIoException("Unable to instantiate: " + c.getName());
    }

    // Try instantiation via unsafe (if turned on.  It is off by default.  Use
    // MetaUtils.setUseUnsafe(true) to enable it. This may result in heap-dumps
    // for e.g. ConcurrentHashMap or can cause problems when the class is not initialized,
    // that's why we try ordinary constructors first.
    private static Object tryUnsafeInstantiation(Class<?> c)
    {
        if (useUnsafe) {
            try {
                Object o = unsafe.allocateInstance(c);
                return o;
            }
            catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * @return a new primitive wrapper instance for the given class, using the
     * rhs parameter as a hint.  For example, convert(long.class, "45")
     * will return 45L.  However, if null is passed for the rhs, then the value 0L
     * would be returned in this case.  For boolean, it would return false if null
     * was passed in.  This method is similar to the GitHub project java-util's
     * Converter.convert() API.
     */
    static Object convert(Class<?> c, Object rhs)
    {
        try {
            if (rhs == null) {
                return FROM_NULL.get(c);
            }

            if (c == boolean.class || c == Boolean.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "false";
                    }
                    return Boolean.parseBoolean((String) rhs);
                }
                return rhs;
            }
            else if (c == byte.class || c == Byte.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0";
                    }
                    return Byte.parseByte((String) rhs);
                }
                return byteCache[((Number) rhs).byteValue() + 128];
            }
            else if (c == char.class || c == Character.class) {
                if (rhs instanceof String) {
                    if (rhs.equals("\"")) {
                        return '\"';
                    }
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "\u0000";
                    }
                    return ((CharSequence) rhs).charAt(0);
                }
                if (rhs instanceof Character) {
                    return rhs;
                }
                // Let it throw exception
            }
            else if (c == double.class || c == Double.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0.0";
                    }
                    return Double.parseDouble((String) rhs);
                }
                return ((Number) rhs).doubleValue();
            }
            else if (c == float.class || c == Float.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0.0f";
                    }
                    return Float.parseFloat((String) rhs);
                }
                return ((Number) rhs).floatValue();
            }
            else if (c == int.class || c == Integer.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0";
                    }
                    return Integer.parseInt((String) rhs);
                }
                return ((Number) rhs).intValue();
            }
            else if (c == long.class || c == Long.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0";
                    }
                    return Long.parseLong((String) rhs);
                }
                return ((Number) rhs).longValue();
            }
            else if (c == short.class || c == Short.class) {
                if (rhs instanceof String) {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs)) {
                        rhs = "0";
                    }
                    return Short.parseShort((String) rhs);
                }
                return ((Number) rhs).shortValue();
            }
            else if (c == Date.class) {
                if (rhs instanceof String) {
                    return DateFactory.parseDate((String) rhs);
                }
                else if (rhs instanceof Long) {
                    return new Date((Long)(rhs));
                }
                else {
                    return new Date();
                }
            }
            else if (c == BigInteger.class) {
                return Readers.bigIntegerFrom(rhs);
            }
            else if (c == BigDecimal.class) {
                return Readers.bigDecimalFrom(rhs);
            }
        }
        catch (Exception e) {
            String className = c == null ? "null" : c.getName();
            throw new JsonIoException("Error creating primitive wrapper instance for Class: " + className, e);
        }

        throw new JsonIoException("Class '" + c.getName() + "' does not have primitive wrapper.");
    }

    /**
     * Format a nice looking method signature for logging output
     */
    public static String getLogMessage(String methodName, Object[] args)
    {
        return getLogMessage(methodName, args, 64);
    }

    public static String getLogMessage(String methodName, Object[] args, int argCharLen)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName);
        sb.append('(');
        for (Object arg : args)
        {
            sb.append(getJsonStringToMaxLength(arg, argCharLen));
            sb.append("  ");
        }
        String result = sb.toString().trim();
        return result + ')';
    }

    private static String getJsonStringToMaxLength(Object obj, int argCharLen)
    {
        String arg = JsonWriter.toJson(obj, new WriteOptionsBuilder().withShortMetaKeys().neverShowTypeInfo().build());
        if (arg.length() > argCharLen)
        {
            arg = arg.substring(0, argCharLen) + "...";
        }
        return arg;
    }

    // Currently, still returning DEEP declared fields.
    public static Map<Class<?>, Collection<Accessor>> convertStringFieldNamesToAccessors(Map<Class<?>, Collection<String>> map) {

        final Map<Class<?>, Collection<Accessor>> copy = new HashMap<>();

        if (map == null) {
            return copy;
        }

        for (Map.Entry<Class<?>, Collection<String>> entry : map.entrySet()) {
            final Class<?> c = entry.getKey();
            final Collection<String> fields = entry.getValue();

            Class<?> current = c;

            while (current != null) {
                final ClassDescriptor descriptor = ClassDescriptors.instance().getClassDescriptor(current);
                final Map<String, Accessor> accessorMap = descriptor.getAccessors();

                for (Map.Entry<String, Accessor> acessorEntry : accessorMap.entrySet()) {

                    if (fields.contains(acessorEntry.getKey())) {
                        final Collection<Accessor> list = copy.computeIfAbsent(c, l -> new LinkedHashSet<>());
                        list.add(acessorEntry.getValue());
                    }
                }
                current = current.getSuperclass();
            }
        }

        return copy;
    }

    public static <K, V> V getValue(Map map, K key) {
        return (V) map.get(key);
    }

    public static <K, V> V getValueWithDefaultForNull(Map map, K key, V defaultValue) {
        V value = (V) map.get(key);
        return (value == null) ? defaultValue : value;
    }

    public static <K, V> V getValueWithDefaultForMissing(Map map, K key, V defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }

        return (V) map.get(key);
    }

    public static void setFieldValue(Field field, Object instance, Object value)
    {
        try
        {
            if (instance == null)
            {
                throw new JsonIoException("Attempting to set field: " + field.getName() + " on null object.");
            }
            field.set(instance, value);
        }
        catch (IllegalAccessException e)
        {
            throw new JsonIoException("Cannot set field: " + field.getName() + " on class: " + instance.getClass().getName() + " as field is not accessible.  Add a ClassFactory implementation to create the needed class, and use JsonReader.assignInstantiator() to associate your ClassFactory to the class: " + instance.getClass().getName(), e);
        }
    }

    public static void trySetAccessible(AccessibleObject object)
    {
        if (object.isAccessible()) {
            return;
        }

        safelyIgnoreException(() -> {
            object.setAccessible(true);
        });
    }

    public static <T> T safelyIgnoreException(Callable<T> callable, T defaultValue) {
        try {
            return callable.call();
        } catch (Throwable e) {
            return defaultValue;
        }
    }

    public static void safelyIgnoreException(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable ignored) { }
    }
}
