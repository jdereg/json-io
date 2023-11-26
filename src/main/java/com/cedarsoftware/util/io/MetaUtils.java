package com.cedarsoftware.util.io;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

import com.cedarsoftware.util.io.factory.DateFactory;

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

    private static final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();
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
     * Strip leading and trailing double quotes from the passed in String. If there are more than one
     * set of quotes, ""this is weird"" then all leading and trailing quotes will be removed, yielding
     * this is weird.  Note that: """this is "really" weird" will be: this is "really" weird.
     */
    public static String removeLeadingAndTrailingQuotes(String input)
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
        if (Primitives.isPrimitive(argType)) {
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
     * that is associated to the class 'c'.  In the ClassFactory, the JsonObject containing the data from the
     * associated JsonObject { } is passed in, allowing you to instantiate and load the values in one operation.
     * If you do that, and no further sub-objects exist, or you load the sub-objects in your ClassFactory,
     * make sure to return 'true' for isObjectFinal().
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
                List<Object> argumentsNull = MetaUtilsHelper.matchArgumentsToParameters(argValues, parameters, true);
                List<Object> argumentsNonNull = MetaUtilsHelper.matchArgumentsToParameters(argValues, parameters, false);
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
            List<Object> arguments = MetaUtilsHelper.matchArgumentsToParameters(argValues, parameters, cachedConstructor.useNullSetting);

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
