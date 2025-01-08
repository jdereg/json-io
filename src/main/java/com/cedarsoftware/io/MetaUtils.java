package com.cedarsoftware.io;

import java.io.UncheckedIOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
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

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.StringUtilities;
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
 *         limitations under the License.
 */
public class MetaUtils {
    private MetaUtils() {
    }

    public enum Dumpty {}

    private static final ConcurrentMap<String, CachedConstructor> constructors = new ConcurrentHashMap<>();
    static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    private static boolean useUnsafe = false;
    private static Unsafe unsafe;
    private static final Map<Class<?>, Supplier<Object>> DIRECT_CLASS_MAPPING = new HashMap<>();
    private static final Map<Class<?>, Supplier<Object>> ASSIGNABLE_CLASS_MAPPING = new LinkedHashMap<>();

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
        DIRECT_CLASS_MAPPING.put(OffsetDateTime.class, OffsetDateTime::now);
        DIRECT_CLASS_MAPPING.put(ZonedDateTime.class, ZonedDateTime::now);
        DIRECT_CLASS_MAPPING.put(ZoneId.class, ZoneId::systemDefault);
        DIRECT_CLASS_MAPPING.put(AtomicBoolean.class, AtomicBoolean::new);
        DIRECT_CLASS_MAPPING.put(AtomicInteger.class, AtomicInteger::new);
        DIRECT_CLASS_MAPPING.put(AtomicLong.class, AtomicLong::new);
        DIRECT_CLASS_MAPPING.put(URL.class, () -> ExceptionUtilities.safelyIgnoreException(() -> new URL("http://localhost"), null));
        DIRECT_CLASS_MAPPING.put(URI.class, () -> ExceptionUtilities.safelyIgnoreException(() -> new URI("http://localhost"), null));
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
        ASSIGNABLE_CLASS_MAPPING.put(NavigableSet.class, TreeSet::new);
        ASSIGNABLE_CLASS_MAPPING.put(SortedSet.class, TreeSet::new);
        ASSIGNABLE_CLASS_MAPPING.put(Set.class, LinkedHashSet::new);
        ASSIGNABLE_CLASS_MAPPING.put(NavigableMap.class, TreeMap::new);
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
    public static void setUseUnsafe(boolean state) {
        useUnsafe = state;
        if (state) {
            try {
                unsafe = new Unsafe();
            } catch (InvocationTargetException e) {
                useUnsafe = false;
            }
        }
    }

    public static Class<?> getClassIfEnum(Class<?> c) {
        if (c == null) {
            return null;
        }

        // Step 1: Traverse up the class hierarchy
        Class<?> current = c;
        while (current != null && current != Object.class) {
            if (current.isEnum() && !Enum.class.equals(current)) {
                return current;
            }
            current = current.getSuperclass();
        }

        // Step 2: Traverse the enclosing classes
        current = c.getEnclosingClass();
        while (current != null) {
            if (current.isEnum() && !Enum.class.equals(current)) {
                return current;
            }
            current = current.getEnclosingClass();
        }

        return null;
    }

    private static void throwIfSecurityConcern(Class<?> securityConcern, Class<?> c) {
        if (securityConcern.isAssignableFrom(c)) {
            throw new IllegalArgumentException("For security reasons, json-io does not allow instantiation of: " + securityConcern.getName());
        }
    }

    private static Object getArgForType(Converter converter, Class<?> argType) {
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
    private static List<Object> matchArgumentsToParameters(Converter converter, Collection<Object> values, Parameter[] parameterTypes, boolean useNull) {
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
        int i = 0;

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
    private static class ConstructorWithValues implements Comparable<ConstructorWithValues> {
        final Constructor<?> constructor;
        final Object[] argsNull;
        final Object[] argsNonNull;

        ConstructorWithValues(Constructor<?> constructor, Object[] argsNull, Object[] argsNonNull) {
            this.constructor = constructor;
            this.argsNull = argsNull;
            this.argsNonNull = argsNonNull;
        }

        public int compareTo(ConstructorWithValues other) {
            final int mods = constructor.getModifiers();
            final int otherMods = other.constructor.getModifiers();

            // Rule 1: Visibility: favor public over non-public
            if (!isPublic(mods) && isPublic(otherMods)) {
                return 1;
            } else if (isPublic(mods) && !isPublic(otherMods)) {
                return -1;
            }

            // Rule 2: Visibility: favor protected over private
            if (!isProtected(mods) && isProtected(otherMods)) {
                return 1;
            } else if (isProtected(mods) && !isProtected(otherMods)) {
                return -1;
            }

            // Rule 3: Sort by score of the argsNull list
            long score1 = scoreArgumentValues(argsNull);
            long score2 = scoreArgumentValues(other.argsNull);
            if (score1 < score2) {
                return 1;
            } else if (score1 > score2) {
                return -1;
            }

            // Rule 4: Sort by score of the argsNonNull list
            score1 = scoreArgumentValues(argsNonNull);
            score2 = scoreArgumentValues(other.argsNonNull);
            if (score1 < score2) {
                return 1;
            } else if (score1 > score2) {
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
        private long scoreArgumentValues(Object[] args) {
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

        private String buildParameterTypeString(Constructor<?> constructor) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            StringBuilder s = new StringBuilder();

            for (Class<?> paramType : paramTypes) {
                s.append(paramType.getName()).append(".");
            }
            return s.toString();
        }
    }

    private static String createCacheKey(Class<?> c, Collection<?> args) {
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
     * Create a new instance of the specified class, optionally using provided constructor arguments.
     * <p>
     * This method attempts to instantiate a class using the following strategies in order:
     * <ol>
     *     <li>Using cached constructor information from previous successful instantiations</li>
     *     <li>Matching constructor parameters with provided argument values</li>
     *     <li>Using default values for unmatched parameters</li>
     *     <li>Using unsafe instantiation (if enabled)</li>
     * </ol>
     *
     * <p>Constructor selection prioritizes:
     * <ul>
     *     <li>Public over non-public constructors</li>
     *     <li>Protected over private constructors</li>
     *     <li>Constructors with more non-null argument matches</li>
     *     <li>Constructors with more parameters</li>
     * </ul>
     *
     * @param converter Converter instance used to convert null values to appropriate defaults for primitive types
     * @param c Class to instantiate
     * @param argumentValues Optional collection of values to match to constructor parameters. Can be null or empty.
     * @return A new instance of the specified class
     * @throws IllegalArgumentException if:
     *         <ul>
     *             <li>The class cannot be instantiated</li>
     *             <li>The class is a security-sensitive class (Process, ClassLoader, etc.)</li>
     *             <li>The class is an unknown interface</li>
     *         </ul>
     * @throws IllegalStateException if constructor invocation fails
     *
     * <p><b>Security Note:</b> For security reasons, this method prevents instantiation of:
     * <ul>
     *     <li>ProcessBuilder</li>
     *     <li>Process</li>
     *     <li>ClassLoader</li>
     *     <li>Constructor</li>
     *     <li>Method</li>
     *     <li>Field</li>
     * </ul>
     *
     * <p><b>Usage Example:</b>
     * <pre>{@code
     * // Create instance with no arguments
     * MyClass obj1 = (MyClass) newInstance(converter, MyClass.class, null);
     *
     * // Create instance with constructor arguments
     * List<Object> args = Arrays.asList("arg1", 42);
     * MyClass obj2 = (MyClass) newInstance(converter, MyClass.class, args);
     * }</pre>
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
        if (cachedConstructor == null) {
            if (c.isInterface()) {
                throw new IllegalArgumentException("Cannot instantiate unknown interface: " + c.getName());
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
                    } catch (Exception ignored) {
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
            } catch (Exception ignored) {
            }

            Object o = tryUnsafeInstantiation(c);
            if (o != null) {
                return o;
            }
        }

        throw new IllegalArgumentException("Unable to instantiate: " + c.getName());
    }

    // Try instantiation via unsafe (if turned on).  It is off by default.  Use
    // MetaUtils.setUseUnsafe(true) to enable it. This may result in heap-dumps
    // for e.g. ConcurrentHashMap or can cause problems when the class is not initialized,
    // that's why we try ordinary constructors first.
    private static Object tryUnsafeInstantiation(Class<?> c) {
        if (useUnsafe) {
            try {
                Object o = unsafe.allocateInstance(c);
                return o;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Format a nice looking method signature for logging output
     */
    public static String getLogMessage(String methodName, Object[] args) {
        return getLogMessage(methodName, args, 64);
    }

    public static String getLogMessage(String methodName, Object[] args, int argCharLen) {
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

    private static String getJsonStringToMaxLength(Object obj, int argCharLen) {
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
                throw new IllegalStateException("Attempting to set field: " + field.getName() + " on null object.");
            }
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set field: " + field.getName() + " on class: " + instance.getClass().getName() + " as field is not accessible.  Add a ClassFactory implementation to create the needed class, and use JsonReader.assignInstantiator() to associate your ClassFactory to the class: " + instance.getClass().getName(), e);
        }
    }

    public static void trySetAccessible(AccessibleObject object) {
        ExceptionUtilities.safelyIgnoreException(() -> object.setAccessible(true));
    }

    /**
     * @deprecated As of version 2.19.0.  Use ExceptionUtilities.safelyIgnoreException(Callable, T)
     */
    @Deprecated
    public static <T> T safelyIgnoreException(Callable<T> callable, T defaultValue) {
        return ExceptionUtilities.safelyIgnoreException(callable, defaultValue);
    }

    /**
     * @deprecated As of version 2.19.0.  Use ExceptionUtilities.safelyIgnoreException(Runnable)
     */
    @Deprecated
    public static void safelyIgnoreException(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Returns the length of the given string. If the string is {@code null}, it returns {@code 0} instead of throwing a {@code NullPointerException}.
     *
     * @param s the string whose length is to be determined
     * @return {@code 0} if the string is {@code null}, otherwise the length of the string
     *
     * @deprecated As of version X.Y, replaced by {@link StringUtilities#length(String)}.
     *             This method is no longer recommended for use and may be removed in future releases.
     *             Use {@link StringUtilities#length(String)} to safely determine the length of a string without risking a {@code NullPointerException}.
     */
    @Deprecated
    public static int length(final String s) {
        return StringUtilities.length(s);
    }

    /**
     * Returns the length of the trimmed string.  If the length is
     * null then it returns 0.
     *
     * @param s the string to get the trimmed length of
     * @return the length of the trimmed string, or 0 if the input is null
     * @deprecated This method is deprecated and will be removed in a future version.
     *             Use {@link StringUtilities#trimLength(String)} directly instead.
     */
    @Deprecated
    public static int trimLength(final String s) {
        return StringUtilities.trimLength(s);
    }

    /**
     * Legacy API that many applications consumed.
     */
    public static boolean isPrimitive(Class<?> c) {
        return Primitives.isPrimitive(c);
    }

    /**
     * Legacy API that many applications consumed.
     */
    public static boolean isLogicalPrimitive(Class<?> c) {
        return isPrimitive(c) ||
                c.equals(String.class) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

    /**
     * Load in a Map-style properties file. Expects key and value to be separated by a = (whitespace ignored).
     * Ignores lines beginning with a # and it also ignores blank lines.
     * @param resName String name of the resource file.
     */
    public static Map<String, String> loadMapDefinition(String resName) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            String contents = ClassUtilities.loadResourceAsString(resName);
            Scanner scanner = new Scanner(contents);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.trim().startsWith("#") && !line.isEmpty()) {
                    String[] parts = line.split("=");
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
            scanner.close();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading in " + resName + ". The file should be in the resources folder. The contents are expected to have two strings separated by '='. You can use # or blank lines in the file, they will be skipped.");
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
            String contents = ClassUtilities.loadResourceAsString(resName);
            Scanner scanner = new Scanner(contents);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.startsWith("#") && !line.isEmpty()) {
                    set.add(line);
                }
            }
            scanner.close();
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading in " + resName + ". The file should be in the resources folder. The contents have a single String per line.  You can use # (comment) or blank lines in the file, they will be skipped.");
        }
        return set;
    }

    /**
     * Loads resource content as a String.
     * @param resourceName Name of the resource file.
     * @return Content of the resource file as a String.
     * @deprecated This method is deprecated and will be removed in a future version.
     *             Use {@link ClassUtilities#loadResourceAsString(String)} directly instead.
     */
    @Deprecated
    public static String loadResourceAsString(String resourceName) {
        return ClassUtilities.loadResourceAsString(resourceName);
    }

    /**
     * Loads resource content as a byte[].
     * @param resourceName Name of the resource file.
     * @return Content of the resource file as a byte[].
     * @throws IllegalArgumentException if the resource cannot be found
     * @throws UncheckedIOException if there is an error reading the resource
     * @throws NullPointerException if resourceName is null
     * @deprecated This method is deprecated and will be removed in a future version.
     *             Use {@link ClassUtilities#loadResourceAsBytes(String)} directly instead.
     */
    @Deprecated
    public static byte[] loadResourceAsBytes(String resourceName) {
        return ClassUtilities.loadResourceAsBytes(resourceName);
    }
    
    /**
     * Removes all leading and trailing double quotes from a String. Multiple consecutive quotes
     * at the beginning or end of the string will all be removed.
     * <p>
     * Examples:
     * <ul>
     *     <li>"text" → text</li>
     *     <li>""text"" → text</li>
     *     <li>"""text""" → text</li>
     *     <li>"text with "quotes" inside" → text with "quotes" inside</li>
     * </ul>
     *
     * @param input the String from which to remove quotes (may be null)
     * @return the String with all leading and trailing quotes removed, or null if input was null
     * @deprecated This method has been moved to {@link com.cedarsoftware.util.StringUtilities#removeLeadingAndTrailingQuotes(String)}.
     * Please use {@code StringUtilities.removeLeadingAndTrailingQuotes()} instead.
     * This method will be removed in a future release.
     */
    @Deprecated
    public static String removeLeadingAndTrailingQuotes(String input) {
        return StringUtilities.removeLeadingAndTrailingQuotes(input);
    }

    /**
     * Finds the closest matching class in an inheritance hierarchy from a map of candidate classes.
     * <p>
     * This method searches through a map of candidate classes to find the one that is most closely
     * related to the input class in terms of inheritance distance. The search prioritizes:
     * <ul>
     *     <li>Exact class match (returns immediately)</li>
     *     <li>Closest superclass/interface in the inheritance hierarchy</li>
     * </ul>
     * <p>
     * This method is typically used for cache misses when looking up class-specific handlers
     * or processors.
     *
     * @param <T> The type of value stored in the workerClasses map
     * @param clazz The class to find a match for (must not be null)
     * @param workerClasses Map of candidate classes and their associated values (must not be null)
     * @param defaultClass Default value to return if no suitable match is found
     * @return The value associated with the closest matching class, or defaultClass if no match found
     * @throws NullPointerException if clazz or workerClasses is null
     * @deprecated This method is deprecated and will be removed in a future version.
     *             Use {@link ClassUtilities#findClosest(Class, Map, Object)} directly instead.
     *
     * @see ClassUtilities#computeInheritanceDistance(Class, Class)
     */
    @Deprecated
    public static <T> T findClosest(Class<?> clazz, Map<Class<?>, T> workerClasses, T defaultClass) {
        return ClassUtilities.findClosest(clazz, workerClasses, defaultClass);
    }
}