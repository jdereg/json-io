package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
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
import java.util.stream.Collectors;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.convert.Converter;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;

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
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
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

    public static final String META_CLASS_FIELD_NAME = "metaClass";

    public static final String META_CLASS_NAME = "groovy.lang.MetaClass";

    private static final Map<String, Class<?>> nameToClass = new HashMap<>();
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
    private static final Pattern extraQuotes = Pattern.compile("^\"*(.*?)\"*$");

    static {
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
    }

    /**
     * Globally turn on (or off) the 'unsafe' option of Class construction.  The unsafe option
     * is used when all constructors have been tried and the Java class could not be instantiated.
     * @param state boolean true = on, false = off.
     */
    public static void setUseUnsafe(boolean state)
    {
        useUnsafe = state;
        if (state) {
            try {
                unsafe = new Unsafe();
            }
            catch (InvocationTargetException e) {
                useUnsafe = false;
            }
        }
    }

    static
    {
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
    
    public static Optional<Class<?>> getClassIfEnum(Class<?> c) {
        if (c.isEnum()) {
            return Optional.of(c);
        }

        if (!Enum.class.isAssignableFrom(c)) {
            return Optional.empty();
        }

        Class<?> enclosingClass = c.getEnclosingClass();
        return enclosingClass != null && enclosingClass.isEnum() ? Optional.of(enclosingClass) : Optional.empty();
    }

    static void throwIfSecurityConcern(Class<?> securityConcern, Class<?> c)
    {
        if (securityConcern.isAssignableFrom(c)) {
            throw new JsonIoException("For security reasons, json-io does not allow instantiation of: " + securityConcern.getName());
        }
    }

    static Object getArgForType(Converter converter, Class<?> argType) {
        if (Primitives.isPrimitive(argType)) {
            return converter.convert(null, argType);  // Get the defaults (false, 0, 0.0d, etc.)
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
    public static List<Object> matchArgumentsToParameters(Converter converter, Collection<Object> values, Parameter[] parameterTypes, boolean useNull) {
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
                    value = paramType.isPrimitive() ? converter.convert(null, paramType) : null;  // don't send null to a primitive parameter
                } else {
                    value = getArgForType(converter, paramType);
                }
            }
            answer.add(value);
        }
        return answer;
    }

    /**
     * Pick the best value from the list that has the least 'distance' from the passed in Class 'param.'
     * Note: this method has a side effect - it will remove the value that was chosen from the list.
     * Note: If none of the instances in the 'values' list are instances of the 'param' class,
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
            distances[i++] = value == null ? -1 : ClassUtilities.computeInheritanceDistance(value.getClass(), param);
        }

        int index = indexOfSmallestValue(distances);
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
    public static int indexOfSmallestValue(int[] array) {
        if (array == null || array.length == 0) {
            return -1; // Return -1 for null or empty array.
        }

        int minValue = Integer.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < array.length; i++) {
            if (array[i] < minValue && array[i] > -1) {
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
     * that is associated to the class 'c'.  In the ClassFactory, the JsonObject containing the data from the
     * associated JsonObject { } is passed in, allowing you to instantiate and load the values in one operation.
     * If you do that, and no further sub-objects exist, or you load the sub-objects in your ClassFactory,
     * make sure to return 'true' for isObjectFinal().
     */
    public static Object newInstance(Converter converter, Class<?> c, Collection<?> argumentValues) {
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
                List<Object> argumentsNull = matchArgumentsToParameters(converter, argValues, parameters, true);
                List<Object> argumentsNonNull = matchArgumentsToParameters(converter, argValues, parameters, false);
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
            List<Object> arguments = matchArgumentsToParameters(converter, argValues, parameters, cachedConstructor.useNullSetting);

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

    // Try instantiation via unsafe (if turned on).  It is off by default.  Use
    // MetaUtils.setUseUnsafe(true) to enable it. This may result in heap-dumps
    // for e.g. ConcurrentHashMap or can cause problems when the class is not initialized,
    // that's why we try ordinary constructors first.
    private static Object tryUnsafeInstantiation(Class<?> c)
    {
        if (useUnsafe) {
            try {
                Object o = unsafe.allocateInstance(c);
                return o;
            } catch (Exception ignored) {}
        }
        return null;
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
        for (Object arg : args) {
            sb.append(getJsonStringToMaxLength(arg, argCharLen));
            sb.append("  ");
        }
        String result = sb.toString().trim();
        return result + ')';
    }

    private static String getJsonStringToMaxLength(Object obj, int argCharLen)
    {
        WriteOptions options = new WriteOptionsBuilder().shortMetaKeys(true).showTypeInfoNever().build();
        String arg = JsonIo.toJson(obj, options);
        if (arg.length() > argCharLen) {
            arg = arg.substring(0, argCharLen) + "...";
        }
        return arg;
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

    public static void setFieldValue(Field field, Object instance, Object value) {
        try {
            if (instance == null) {
                throw new JsonIoException("Attempting to set field: " + field.getName() + " on null object.");
            }
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new JsonIoException("Cannot set field: " + field.getName() + " on class: " + instance.getClass().getName() + " as field is not accessible.  Add a ClassFactory implementation to create the needed class, and use JsonReader.assignInstantiator() to associate your ClassFactory to the class: " + instance.getClass().getName(), e);
        }
    }

    public static void trySetAccessible(AccessibleObject object)
    {
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
        } catch (Throwable ignored) {
        }
    }
    
    /**
     * Use this method when you don't want a length check to
     * throw a NullPointerException when
     *
     * @param s string to return length of
     * @return 0 if string is null, otherwise the length of string.
     */
    public static int length(final String s) {
        return s == null ? 0 : s.length();
    }

    /**
     * Returns the length of the trimmed string.  If the length is
     * null then it returns 0.
     */
    public static int trimLength(final String s) {
        return (s == null) ? 0 : s.trim().length();
    }

    /**
     * Legacy API that many applications consumed.
     */
    public static boolean isPrimitive(Class<?> c)
    {
        return Primitives.isPrimitive(c);
    }

    /**
     * Legacy API that many applications consumed.
     */
    public static boolean isLogicalPrimitive(Class<?> c)
    {
        return  isPrimitive(c) ||
                c.equals(String.class) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

    /**
     * Populates a map with a mapping of Class -> Set of Strings
     */
    public static Map<Class<?>, Set<String>> loadClassToSetOfStrings(String fileName) {
        Map<String, String> map = loadMapDefinition(fileName);

        Map<Class<?>, Set<String>> builtMap = new HashMap<>();
        ClassLoader classLoader = MetaUtils.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                continue;
            }

            Set<String> resultSet = commaSeparatedStringToSet(entry.getValue());
            safelyIgnoreException(() -> builtMap.put(clazz, resultSet));
        }
        return builtMap;
    }

    public static Set<String> commaSeparatedStringToSet(String commaSeparatedString) {
        return Arrays.stream(commaSeparatedString.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    /**
     */
    public static Map<Class<?>, Map<String, String>> loadNonStandardMethodNames(String fileName) {
        Map<String, String> map = MetaUtils.loadMapDefinition(fileName);
        Map<Class<?>, Map<String, String>> nonStandardMapping = new ConcurrentHashMap<>();
        ClassLoader classLoader = WriteOptions.class.getClassLoader();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String className = entry.getKey();
            String mappings = entry.getValue();
            Class<?> clazz = ClassUtilities.forName(className, classLoader);
            if (clazz == null) {
                System.out.println("Class: " + className + " not defined in the JVM");
                continue;
            }

            Map<String, String> mapping = nonStandardMapping.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>());
            for (String split : mappings.split(",")) {
                String[] parts = split.split(":");
                mapping.put(parts[0].trim(), parts[1].trim());
            }
        }
        return nonStandardMapping;
    }

    /**
     * Load in a Map-style properties file. Expects key and value to be separated by a = (whitespace ignored).
     * Ignores lines beginning with a # and it also ignores blank lines.
     * @param resName String name of the resource file.
     */
    public static Map<String, String> loadMapDefinition(String resName) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String contents = MetaUtils.loadResourceAsString(resName);
            Scanner scanner = new Scanner(contents);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.trim().startsWith("#") && !line.isEmpty()) {
                    String[] parts = line.split("=");
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
            scanner.close();
        }  catch (Exception e) {
            throw new JsonIoException("Error reading in " + resName + ". The file should be in the resources folder. The contents are expected to have two strings separated by '='. You can use # or blank lines in the file, they will be skipped.");
        }
        return map;
    }

    /**
     * Load in a Set-style simple file of values. Expects values to be one per line.  Ignores lines beginning with a #
     * and it also ignores blank lines.
     * @param resName String name of the resource file.
     * @return the set of strings
     */
    public static Set<String> loadSetDefinition(String resName) {
        Set<String> set = new LinkedHashSet<>();
        try {
            String contents = MetaUtils.loadResourceAsString(resName);
            Scanner scanner = new Scanner(contents);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.startsWith("#") && !line.isEmpty()) {
                    set.add(line);
                }
            }
            scanner.close();
        }  catch (Exception e) {
            throw new JsonIoException("Error reading in " + resName + ". The file should be in the resources folder. The contents have a single String per line.  You can use # or blank lines in the file, they will be skipped.");
        }
        return set;
    }

    /**
     * Loads resource content as a String.
     * @param resourceName Name of the resource file.
     * @return Content of the resource file as a String.
     */
    public static String loadResourceAsString(String resourceName) {
        byte[] resourceBytes = loadResourceAsBytes(resourceName);
        return new String(resourceBytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Loads resource content as a byte[].
     * @param resourceName Name of the resource file.
     * @return Content of the resource file as a byte[].
     */
    public static byte[] loadResourceAsBytes(String resourceName) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new JsonIoException("Resource not found: " + resourceName);
            }
            return readInputStreamFully(inputStream);
        } catch (Exception e) {
            throw new JsonIoException("Error reading resource: " + resourceName, e);
        }
    }

    /**
     * Reads an InputStream fully and returns its content as a byte array.
     *
     * @param inputStream InputStream to read.
     * @return Content of the InputStream as byte array.
     * @throws IOException if an I/O error occurs.
     */
    private static byte[] readInputStreamFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Strip leading and trailing double quotes from the passed in String. If there are more than one
     * set of quotes, ""this is weird"" then all leading and trailing quotes will be removed, yielding
     * this is weird.  Note that: ["""this is "really" weird""] will be: [this is "really" weird].
     */
    public static String removeLeadingAndTrailingQuotes(String input) {
        Matcher m = extraQuotes.matcher(input);
        if (m.find()) {
            input = m.group(1);
        }
        return input;
    }

    /**
     * Fetch the closest class for the passed in Class.  This method always fetches the closest class or returns
     * the default class, doing the complicated inheritance distance checking.  This method is only called when
     * a cache miss has happened.  A sentinel 'defaultClass' is returned when no class is found.  This prevents
     * future cache misses from re-attempting to find classes that do not have a custom writer.
     *
     * @param c             Class of object for which fetch a custom writer
     * @param workerClasses The classes to search through
     * @param defaultClass  the class to return when no viable class was found.
     * @return JsonClassWriter for the custom class (if one exists), nullWriter otherwise.
     */
    public static <T> T findClosest(Class<?> c, Map<Class<?>, T> workerClasses, T defaultClass) {
        T closestWriter = defaultClass;
        int minDistance = Integer.MAX_VALUE;

        for (Map.Entry<Class<?>, T> entry : workerClasses.entrySet()) {
            Class<?> clz = entry.getKey();
            if (clz == c) {
                return entry.getValue();
            }
            int distance = ClassUtilities.computeInheritanceDistance(c, clz);
            if (distance != -1 && distance < minDistance) {
                minDistance = distance;
                closestWriter = entry.getValue();
            }
        }
        return closestWriter;
    }
    
    /**
     * Load the list of classes that are intended to be treated as non-referenceable, immutable classes.
     *
     * @return {@code Set<Class < ?>>} which is the loaded from resource/nonRefs.txt and verified to exist in JVM.
     */
    public static Set<Class<?>> loadNonRefs() {
        final Set<String> set = loadSetDefinition("config/nonRefs.txt");
        final ClassLoader classLoader = WriteOptions.class.getClassLoader();

        final Set<Class<?>> result = new HashSet<>();

        for (String className : set) {
            Class<?> loadedClass = ClassUtilities.forName(className, classLoader);

            if (loadedClass != null) {
                result.add(loadedClass);
            } else {
                throw new JsonIoException("Class: " + className + " is undefined.");
            }
        }

        return result;
    }
}
