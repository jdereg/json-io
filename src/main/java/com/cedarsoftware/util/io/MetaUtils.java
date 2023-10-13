package com.cedarsoftware.util.io;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.reflect.Modifier.*;

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
    public enum Dumpty {}

    private MetaUtils () {}

    private static final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();
    private static final Set<Class<?>> prims = new HashSet<>();
    private static final Map<String, Class<?>> nameToClass = new HashMap<>();
    private static final Byte[] byteCache = new Byte[256];
    private static final Character[] charCache = new Character[128];
    private static final Pattern extraQuotes = Pattern.compile("(\"*)([^\"]*)(\"*)");
    private static final Class<?>[] emptyClassArray = new Class[]{};
    private static final ConcurrentMap<Class<?>, Object[]> constructors = new ConcurrentHashMap<>();
    private static final Collection<?> unmodifiableCollection = Collections.unmodifiableCollection(new ArrayList<>());
    private static final Collection<?> unmodifiableSet = Collections.unmodifiableSet(new HashSet<>());
    private static final Collection<?> unmodifiableSortedSet = Collections.unmodifiableSortedSet(new TreeSet<>());
    private static final Map<?, ?> unmodifiableMap = Collections.unmodifiableMap(new HashMap<>());
    private static final Map<?, ?> unmodifiableSortedMap = Collections.unmodifiableSortedMap(new TreeMap<>());
    static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    private static boolean useUnsafe = false;
    private static Unsafe unsafe;
    static Exception loadClassException;

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

        nameToClass.put("string", String.class);
        nameToClass.put("boolean", boolean.class);
        nameToClass.put("char", char.class);
        nameToClass.put("byte", byte.class);
        nameToClass.put("short", short.class);
        nameToClass.put("int", int.class);
        nameToClass.put("long", long.class);
        nameToClass.put("float", float.class);
        nameToClass.put("double", double.class);
        nameToClass.put("date", Date.class);
        nameToClass.put("class", Class.class);


        // Save memory by re-using all byte instances (Bytes are immutable)
        for (int i = 0; i < byteCache.length; i++)
        {
            byteCache[i] = (byte) (i - 128);
        }

        // Save memory by re-using common Characters (Characters are immutable)
        for (int i = 0; i < charCache.length; i++)
        {
            charCache[i] = (char) i;
        }
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
                if (Modifier.isStatic(modifiers))
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
                    if ("hash".equals(fieldName) || "ordinal".equals(fieldName))
                    {
                        continue;
                    }
                }
                if (classFields.containsKey(fieldName))
                {
                    classFields.put(curr.getName() + '.' + fieldName, field);
                }
                else
                {
                    classFields.put(fieldName, field);
                }

                if (!Modifier.isPublic(modifiers))
                {
                    field.trySetAccessible();
                }
            }

            curr = curr.getSuperclass();
        }

        classMetaCache.put(c, classFields);
        return new LinkedHashMap<>(classFields);
    }

    /**
     * @param a Class source class
     * @param b Class target class
     * @return inheritance distance between two classes, or Integer.MAX_VALUE if they are not related. Each
     * step upward in the inheritance from one class to the next (calling class.getSuperclass()) is counted
     * as 1. This can be a lot of computational effort, therefore the results of this determination should be cached.
     */
    public static int getDistance(Class<?> a, Class<?> b)
    {
        if (a.isInterface())
        {
            return getDistanceToInterface(a, b);
        }
        Class<?> curr = b;
        int distance = 0;

        while (curr != a)
        {
            distance++;
            curr = curr.getSuperclass();
            if (curr == null)
            {
                return Integer.MAX_VALUE;
            }
        }

        return distance;
    }

    /**
     * @return int distance between two passed in classes.  This method performs an exhaustive
     * walk up the inheritance chain to compute the distance.  This can be a lot of
     * computational effort, therefore the results of this determination should be cached internally.
     */
    static int getDistanceToInterface(Class<?> to, Class<?> from)
    {
        Set<Class<?>> possibleCandidates = new LinkedHashSet<>();

        Class<?>[] interfaces = from.getInterfaces();
        // is the interface direct inherited or via interfaces extends interface?
        for (Class<?> interfase : interfaces)
        {
            if (to.equals(interfase))
            {
                return 1;
            }
            // because of multi-inheritance from interfaces
            if (to.isAssignableFrom(interfase))
            {
                possibleCandidates.add(interfase);
            }
        }

        // it is also possible, that the interface is included in superclasses
        if (from.getSuperclass() != null  && to.isAssignableFrom(from.getSuperclass()))
        {
            possibleCandidates.add(from.getSuperclass());
        }

        int minimum = Integer.MAX_VALUE;
        for (Class<?> candidate : possibleCandidates)
        {
            // Could do that in a non recursive way later
            int distance = getDistanceToInterface(to, candidate);
            if (distance < minimum)
            {
                minimum = ++distance;
            }
        }
        return minimum;
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
     * as all Java primitives, the primitive wrapper classes, String, Number, and Class.  The reason these are
     * considered 'logical' primitives is that they are immutable and therefore can be written without references
     * in JSON content (making the JSON more readable - less @id / @ref), without breaking the semantics (shape)
     * of the object graph being written.
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

    private static final Pattern anonymousEnumClassNameExtractor = Pattern.compile("(.+)\\$\\d$");
    public static Optional<Class> getClassIfEnum(Class c, ClassLoader classLoader) {
        if (c.isEnum()) {
            return Optional.of(c);
        }

        Matcher anonymousInnerClassMatcher = anonymousEnumClassNameExtractor.matcher(c.getName());

        if (anonymousInnerClassMatcher.matches()) {
            try {
                c = classForName(anonymousInnerClassMatcher.group(1), classLoader);
                return c.isEnum() ? Optional.of(c) : Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Given the passed in String class name, return the named JVM class.
     * @param name String name of a JVM class.
     * @param classLoader ClassLoader to use when searching for JVM classes.
     * @param failOnClassLoadingError If named class is not loadable off classpath: true will raise JsonIoException,
     * false will return default LinkedHashMap.
     * @return Class the named JVM class.
     * @throws JsonIoException if named Class is invalid or not loadable via the classLoader and failOnClassLoadingError is
     * true
     */
    static Class<?> classForName(String name, ClassLoader classLoader, boolean failOnClassLoadingError)
    {
        if (name == null || name.isEmpty())
        {
            throw new JsonIoException("Class name cannot be null or empty.");
        }
        Class<?> c = nameToClass.get(name);
        try
        {
            loadClassException = null;
            return c == null ? loadClass(name, classLoader) : c;
        }
        catch (Exception e)
        {
            // Remember why in case later we have a problem
            loadClassException = e;
            if(failOnClassLoadingError) {
                throw new JsonIoException("Unable to create class: " + name, e);
            }
            return LinkedHashMap.class;
        }
    }


    /**
     * Given the passed in String class name, return the named JVM class.
     * @param name String name of a JVM class.
     * @param classLoader ClassLoader to use when searching for JVM classes.
     * @return Class the named JVM class.
     * @throws JsonIoException if named Class is invalid.
     */
    static Class<?> classForName(String name, ClassLoader classLoader)
    {
        return classForName(name, classLoader, false);
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
     * This is a performance optimization.  The lowest 128 characters are re-used.
     *
     * @param c char to match to a Character.
     * @return a Character that matches the passed in char.  If the value is
     *         less than 127, then the same Character instances are re-used.
     */
    static Character valueOf(char c)
    {
        return c <= 127 ? charCache[(int) c] : c;
    }

    /**
     * Strip leading and trailing double quotes from the passed in String.
     */
    static String removeLeadingAndTrailingQuotes(String s)
    {
        Matcher m = extraQuotes.matcher(s);
        if (m.find())
        {
            s = m.group(2);
        }
        return s;
    }

    /**
     * <p>C language malloc() for Java
     * </p><p>
     * Create a new instance of the passed in Class.  This method will make a valiant effort to instantiate
     * the passed in Class, including calling all of its constructors until successful.  The order they
     * are tried are public with the fewest arguments first to private with the most arguments.  If, after
     * exhausting all constructors, then it will attempt using the 'unsafe allocate' from Sun.  This step is
     * optional - by default it will use this if on a Sun (Oracle) JVM unless MetaUtil.setUseUnsafe(false) is called.
     * </p><p>
     * This method will handle common interfaces, such as Collection, Map, etc. which commonly show up in
     * parameterized types.  Any other interface passed to this method will cause a JsonIoException to be thrown.
     * </p><p>
     * To improve performance, when called a 2nd time for the same Class, the constructor that was successfully
     * used to construct the instance will be retrieved from an internal cache.
     * </p>
     * @param c Class to instantiate
     * @return an instance of the instantiated class.  This instance is intended to have its fields 'stuffed' by
     * direct assignment, not called via setter methods.
     * @throws JsonIoException if it cannot instantiate the passed in class.
     */
    @SuppressWarnings("unchecked")
    public static Object newInstance(Class<?> c)
    {
        if (c.isAssignableFrom(ProcessBuilder.class) && c != Object.class)
        {
            throw new IllegalArgumentException("For security reasons, json-io does not allow instantiation of the ProcessBuilder class.");
        }
        if (unmodifiableSortedMap.getClass().isAssignableFrom(c))
        {
            return new TreeMap<>();
        }
        if (unmodifiableMap.getClass().isAssignableFrom(c))
        {
            return new LinkedHashMap<>();
        }
        if (unmodifiableSortedSet.getClass().isAssignableFrom(c))
        {
            return new TreeSet<>();
        }
        if (unmodifiableSet.getClass().isAssignableFrom(c))
        {
            return new LinkedHashSet<>();
        }
        if (unmodifiableCollection.getClass().isAssignableFrom(c))
        {
            return new ArrayList<>();
        }
        if (Collections.EMPTY_LIST.getClass().equals(c)) {
            return Collections.emptyList();
        }

        if (c.isInterface())
        {
            throw new JsonIoException("Cannot instantiate unknown interface: " + c.getName());
        }

        // Constructor not cached, go find a constructor
        Object[] constructorInfo = constructors.get(c);
        if (constructorInfo != null)
        {   // Constructor was cached
            Constructor<?> constructor = (Constructor<?>) constructorInfo[0];

            if (constructor == null && useUnsafe)
            {   // null constructor --> set to null when object instantiated with unsafe.allocateInstance()
                try
                {
                    return unsafe.allocateInstance(c);
                }
                catch (Exception e)
                {
                    // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                    throw new JsonIoException("Could not instantiate " + c.getName(), e);
                }
            }

            if (constructor == null)
            {
                throw new JsonIoException("No constructor found to instantiate " + c.getName());
            }

            Boolean useNull = (Boolean) constructorInfo[1];
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length == 0)
            {
                try
                {
                    return constructor.newInstance();
                }
                catch (Exception e)
                {   // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                    throw new JsonIoException("Could not instantiate " + c.getName(), e);
                }
            }
            Object[] values = fillArgs(paramTypes, useNull);
            try
            {
                return constructor.newInstance(values);
            }
            catch (Exception e)
            {   // Should never happen, as the code that fetched the constructor was able to instantiate it once already
                throw new JsonIoException("Could not instantiate " + c.getName(), e);
            }
        }

        Object[] ret = newInstanceEx(c);
        constructors.put(c, new Object[]{ret[1], ret[2]});
        return ret[0];
    }

    /**
     * Returns an array with the following:
     * <ol>
     *     <li>object instance</li>
     *     <li>constructor</li>
     *     <li>a Boolean, true if all constructor arguments are to be "null"</li>
     * </ol>
     */
    static Object[] newInstanceEx(Class<?> c)
    {
        try
        {
            Constructor<?> constructor = c.getConstructor(emptyClassArray);
            return new Object[] {constructor.newInstance(), constructor, true};
        }
        catch (Exception e)
        {
            // OK, this class does not have a public no-arg constructor.  Instantiate with
            // first constructor found, filling in constructor values with null or
            // defaults for primitives.
            return tryOtherConstruction(c);
        }
    }

    /**
     * Brute force attempt to locate a constructor to construct the passed in Class.  This involves trying all
     * constructors, public, protected, package-private, and private.  It will try with null arguments as well
     * as it will make a 2nd attempt with populated values for some known types (Collection classes, Dates, Strings,
     * primitive wrappers, TimeZone, Calendar).
     * @param c Class to instantiate
     * @return an Object[] containing three (3) elements.  Position 0 is the instance of the class, position 1
     * is the constructor used, and position 2 indicates whether fillArgs was called with useNull or !useNull.
     */
    static Object[] tryOtherConstruction(Class<?> c)
    {
        Constructor<?>[] constructors = c.getDeclaredConstructors();
        if (constructors.length == 0)
        {
            throw new JsonIoException("Cannot instantiate '" + c.getName() + "' - Primitive, interface, array[] or void");
        }

        // Sort constructors - public, protected, private, package-private
        List<Constructor<?>> constructorList = Arrays.asList(constructors);
        constructorList.sort((c1, c2) -> {
            int c1Vis = c1.getModifiers();
            int c2Vis = c2.getModifiers();

            if (c1Vis == c2Vis)
            {   // both are public, protected, private, etc.  Compare by arguments.
                return compareConstructors(c1, c2);
            }

            if (isPublic(c1Vis) != isPublic(c2Vis))
            {   // favor 'public' as first
                return isPublic(c1Vis) ? -1 : 1;
            }

            if (isProtected(c1Vis) != isProtected(c2Vis))
            {   // favor protected 2nd
                return isProtected(c1Vis) ? -1 : 1;
            }

            if (isPrivate(c1Vis) != isPrivate(c2Vis))
            {   // favor private last
                return isPrivate(c1Vis) ? 1 : -1;
            }

            return 0;
        });

        // Try each constructor (public, protected, private, package-private) with null values for non-primitives.
        for (Constructor<?> constructor : constructorList)
        {
            try {
                constructor.setAccessible(true);
            } catch (Exception ignore) {
                continue;
            }
            Class<?>[] argTypes = constructor.getParameterTypes();
            Object[] values = fillArgs(argTypes, true);
            try
            {
                return new Object[] {constructor.newInstance(values), constructor, true};
            }
            catch (Exception ignored)
            { }
        }

        // Try each constructor (public, protected, private, package-private) with non-null values for non-primitives.
        for (Constructor<?> constructor : constructorList)
        {
            try {
                constructor.setAccessible(true);
            } catch (Exception e) {
                continue;
            }
            Class<?>[] argTypes = constructor.getParameterTypes();
            Object[] values = fillArgs(argTypes, false);
            try
            {
                return new Object[] {constructor.newInstance(values), constructor, false};
            }
            catch (Exception ignored)
            { }
        }

        // Try instantiation via unsafe
        // This may result in heapdumps for e.g. ConcurrentHashMap or can cause problems when the class is not initialized
        // Thats why we try ordinary constructors first
        if (useUnsafe)
        {
            try
            {
                return new Object[]{unsafe.allocateInstance(c), null, null};
            }
            catch (Exception ignored)
            { }
        }

        throw new JsonIoException("Could not instantiate " + c.getName() + " using any constructor");
    }

    /**
     * When two constructors have the same access type (both public, both private, etc.)
     * then compare constructors by parameter length (fewer params comes before more params).
     * If parameter count is the same, then compare by parameter Class names.  If those are equal,
     * which should never happen, then the constructors are equal.
     */
    private static int compareConstructors(Constructor<?> c1, Constructor<?> c2)
    {
        Class<?>[] c1ParamTypes = c1.getParameterTypes();
        Class<?>[] c2ParamTypes = c2.getParameterTypes();
        if (c1ParamTypes.length != c2ParamTypes.length)
        {   // negative value if c1 has less (less parameters will be chosen ahead of more), positive value otherwise.
            return c1ParamTypes.length - c2ParamTypes.length;
        }

        // Has same number of parameters.s
        int len = c1ParamTypes.length;
        for (int i=0; i < len; i++)
        {
            Class<?> class1 = c1ParamTypes[i];
            Class<?> class2 = c2ParamTypes[i];
            int compare = class1.getName().compareTo(class2.getName());
            if (compare != 0)
            {
                return compare;
            }
        }

        return 0;
    }

    /**
     * Return an Object[] of instance values that can be passed into a given Constructor.  This method
     * will return an array of nulls if useNull is true, otherwise it will return sensible values for
     * primitive classes, and null for non-known primitive / primitive wrappers.  This class is used
     * when attempting to call constructors on Java objects to get them instantiated, since there is no
     * 'malloc' in Java.
     */
    static Object[] fillArgs(Class<?>[] argTypes, boolean useNull)
    {
        final Object[] values = new Object[argTypes.length];
        for (int i = 0; i < argTypes.length; i++)
        {
            final Class<?> argType = argTypes[i];
            if (argType.isPrimitive())
            {
                values[i] = convert(argType, null);
            }
            else if (useNull)
            {
                values[i] = null;
            }
            else if (prims.contains(argType))
            {
                values[i] = convert(argType, null);
            }
            else
            {
                if (argType == String.class)
                {
                    values[i] = "";
                }
                else if (argType == Date.class)
                {
                    values[i] = new Date();
                }
                else if (List.class.isAssignableFrom(argType))
                {
                    values[i] = new ArrayList<>();
                }
                else if (SortedSet.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeSet<>();
                }
                else if (Set.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashSet<>();
                }
                else if (SortedMap.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeMap<>();
                }
                else if (Map.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashMap<>();
                }
                else if (Collection.class.isAssignableFrom(argType))
                {
                    values[i] = new ArrayList<>();
                }
                else if (Calendar.class.isAssignableFrom(argType))
                {
                    values[i] = Calendar.getInstance();
                }
                else if (TimeZone.class.isAssignableFrom(argType))
                {
                    values[i] = TimeZone.getDefault();
                }
                else if (argType == BigInteger.class)
                {
                    values[i] = BigInteger.TEN;
                }
                else if (argType == BigDecimal.class)
                {
                    values[i] = BigDecimal.TEN;
                }
                else if (argType == StringBuilder.class)
                {
                    values[i] = new StringBuilder();
                }
                else if (argType == StringBuffer.class)
                {
                    values[i] = new StringBuffer();
                }
                else if (argType == Locale.class)
                {
                    values[i] = Locale.FRANCE;  // overwritten
                }
                else if (argType == Class.class)
                {
                    values[i] = String.class;
                }
                else if (argType == Timestamp.class)
                {
                    values[i] = new Timestamp(System.currentTimeMillis());
                }
                else if (argType == java.sql.Date.class)
                {
                    values[i] = new java.sql.Date(System.currentTimeMillis());
                }
                else if (argType == URL.class)
                {
                    try
                    {
                        values[i] = new URL("http://localhost"); // overwritten
                    }
                    catch (MalformedURLException e)
                    {
                        values[i] = null;
                    }
                }
                else if (argType == Object.class)
                {
                    values[i] = new Object();
                }
                else if (argType.isArray())
                {
                    values[i] = new Object[0];
                }
                else
                {
                    values[i] = null;
                }
            }
        }

        return values;
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
        try
        {
            if (c == boolean.class || c == Boolean.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "false";
                    }
                    return Boolean.parseBoolean((String) rhs);
                }
                return rhs != null ? rhs : Boolean.FALSE;
            }
            else if (c == byte.class || c == Byte.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Byte.parseByte((String) rhs);
                }
                return rhs != null ? byteCache[((Number) rhs).byteValue() + 128] : (byte) 0;
            }
            else if (c == char.class || c == Character.class)
            {
                if (rhs == null)
                {
                    return '\u0000';
                }
                if (rhs instanceof String)
                {
                    if (rhs.equals("\""))
                    {
                        return '\"';
                    }
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "\u0000";
                    }
                    return ((CharSequence) rhs).charAt(0);
                }
                if (rhs instanceof Character)
                {
                    return rhs;
                }
                // Let it throw exception
            }
            else if (c == double.class || c == Double.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0";
                    }
                    return Double.parseDouble((String) rhs);
                }
                return rhs != null ? ((Number) rhs).doubleValue() : 0.0d;
            }
            else if (c == float.class || c == Float.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0f";
                    }
                    return Float.parseFloat((String) rhs);
                }
                return rhs != null ? ((Number) rhs).floatValue() : 0.0f;
            }
            else if (c == int.class || c == Integer.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Integer.parseInt((String) rhs);
                }
                return rhs != null ? ((Number) rhs).intValue() : 0;
            }
            else if (c == long.class || c == Long.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Long.parseLong((String) rhs);
                }
                return rhs != null ? ((Number) rhs).longValue() : 0L;
            }
            else if (c == short.class || c == Short.class)
            {
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Short.parseShort((String) rhs);
                }
                return rhs != null ? ((Number) rhs).shortValue() : (short) 0;
            }
            else if (c == Date.class)
            {
                if (rhs instanceof String)
                {
                    return Readers.DateReader.parseDate((String) rhs);
                }
                else if (rhs instanceof Long)
                {
                    return new Date((Long)(rhs));
                }
            }
            else if (c == BigInteger.class)
            {
                return Readers.bigIntegerFrom(rhs);
            }
            else if (c == BigDecimal.class)
            {
                return Readers.bigDecimalFrom(rhs);
            }
        }
        catch (Exception e)
        {
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
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(JsonWriter.TYPE, false);
        args.put(JsonWriter.SHORT_META_KEYS, true);
        String arg = JsonWriter.objectToJson(obj, args);
        if (arg.length() > argCharLen)
        {
            arg = arg.substring(0, argCharLen) + "...";
        }
        return arg;
    }

    public static <K, V> Map<K, V> computeMapIfAbsent(Map<String, Object> map, String keyName) {
        return (Map<K, V>)map.computeIfAbsent(keyName, k -> new HashMap<K, V>());
    }

    public static <T> Set<T> computeSetIfAbsent(Map<String, Object> map, String keyName) {
        return (Set<T>)map.computeIfAbsent(keyName, k -> new HashSet<T>());
    }



    /**
     * Wrapper for unsafe, decouples direct usage of sun.misc.* package.
     * @author Kai Hufenback
     */
    static final class Unsafe
    {
        private final Object sunUnsafe;
        private final Method allocateInstance;

        /**
         * Constructs unsafe object, acting as a wrapper.
         * @throws InvocationTargetException
         */
        public Unsafe() throws InvocationTargetException
        {
            try
            {
                Constructor<?> unsafeConstructor = classForName("sun.misc.Unsafe", MetaUtils.class.getClassLoader()).getDeclaredConstructor();
                unsafeConstructor.setAccessible(true);
                sunUnsafe = unsafeConstructor.newInstance();
                allocateInstance = sunUnsafe.getClass().getMethod("allocateInstance", Class.class);
                allocateInstance.setAccessible(true);
            }
            catch(Exception e)
            {
                throw new InvocationTargetException(e);
            }
        }

        /**
         * Creates an object without invoking constructor or initializing variables.
         * <b>Be careful using this with JDK objects, like URL or ConcurrentHashMap this may bring your VM into troubles.</b>
         * @param clazz to instantiate
         * @return allocated Object
         */
        public Object allocateInstance(Class<?> clazz)
        {
            try
            {
                return allocateInstance.invoke(sunUnsafe, clazz);
            }
            catch (IllegalAccessException | IllegalArgumentException e )
            {
                String name = clazz == null ? "null" : clazz.getName();
                throw new JsonIoException("Unable to create instance of class: " + name, e);
            }
            catch (InvocationTargetException e)
            {
                String name = clazz == null ? "null" : clazz.getName();
                throw new JsonIoException("Unable to create instance of class: " + name, e.getCause() != null ? e.getCause() : e);
            }
        }
    }
}
