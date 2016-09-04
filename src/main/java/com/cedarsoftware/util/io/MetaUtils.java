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

import static java.lang.reflect.Modifier.isPrivate;
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
    
    private static final Map<Class, Map<String, Field>> classMetaCache = new ConcurrentHashMap<Class, Map<String, Field>>();
    private static final Set<Class> prims = new HashSet<Class>();
    private static final Map<String, Class> nameToClass = new HashMap<String, Class>();
    private static final Byte[] byteCache = new Byte[256];
    private static final Character[] charCache = new Character[128];
    private static final Pattern extraQuotes = Pattern.compile("([\"]*)([^\"]*)([\"]*)");
    private static final Class[] emptyClassArray = new Class[]{};
    private static final ConcurrentMap<Class, Object[]> constructors = new ConcurrentHashMap<Class, Object[]>();
    private static final Collection unmodifiableCollection = Collections.unmodifiableCollection(new ArrayList());
    private static final Collection unmodifiableSet = Collections.unmodifiableSet(new HashSet());
    private static final Collection unmodifiableSortedSet = Collections.unmodifiableSortedSet(new TreeSet());
    private static final Map unmodifiableMap = Collections.unmodifiableMap(new HashMap());
    private static final Map unmodifiableSortedMap = Collections.unmodifiableSortedMap(new TreeMap());
    static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>()
    {
        public SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        }
    };
    private static boolean useUnsafe = false;
    private static Unsafe unsafe;

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
     * Return an instance of of the Java Field class corresponding to the passed in field name.
     * @param c class containing the field / field name
     * @param field String name of a field on the class.
     * @return Field instance if the field with the corresponding name is found, null otherwise.
     */
    public static Field getField(Class c, String field)
    {
        return getDeepDeclaredFields(c).get(field);
    }

    /**
     * @param c Class instance
     * @return ClassMeta which contains fields of class.  The results are cached internally for performance
     *         when called again with same Class.
     */
    public static Map<String, Field> getDeepDeclaredFields(Class c)
    {
        Map<String, Field> classFields = classMetaCache.get(c);
        if (classFields != null)
        {
            return classFields;
        }

        classFields = new LinkedHashMap<String, Field>();
        Class curr = c;

        while (curr != null)
        {
            try
            {
                final Field[] local = curr.getDeclaredFields();

                for (Field field : local)
                {
                    if ((field.getModifiers() & Modifier.STATIC) == 0)
                    {   // speed up: do not process static fields.
                        if ("metaClass".equals(field.getName()) && "groovy.lang.MetaClass".equals(field.getType().getName()))
                        {   // Skip Groovy metaClass field if present
                            continue;
                        }

                        if (!field.isAccessible())
                        {
                            try
                            {
                                field.setAccessible(true);
                            }
                            catch (Exception ignored) { }
                        }
                        if (classFields.containsKey(field.getName()))
                        {
                            classFields.put(curr.getName() + '.' + field.getName(), field);
                        }
                        else
                        {
                            classFields.put(field.getName(), field);
                        }
                    }
                }
            }
            catch (ThreadDeath t)
            {
                throw t;
            }
            catch (Throwable ignored) { }

            curr = curr.getSuperclass();
        }

        classMetaCache.put(c, classFields);
        return classFields;
    }

    /**
     * @param a Class source class
     * @param b Class target class
     * @return inheritance distance between two classes, or Integer.MAX_VALUE if they are not related. Each
     * step upward in the inheritance from one class to the next (calling class.getSuperclass()) is counted
     * as 1.
     */
    public static int getDistance(Class a, Class b)
    {
        if (a.isInterface())
        {
            return getDistanceToInterface(a, b);
        }
        Class curr = b;
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

    static int getDistanceToInterface(Class<?> to, Class<?> from)
    {
        Set<Class<?>> possibleCandidates = new LinkedHashSet<Class<?>>();

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
     * Integer, Long, Boolean, etc. are consider primitives by this method.
     */
    public static boolean isPrimitive(Class c)
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
    public static boolean isLogicalPrimitive(Class c)
    {
        return  c.isPrimitive() ||
                prims.contains(c) ||
                String.class.isAssignableFrom(c) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

    static Exception loadClassException;

    static Class classForName(String name) {
        return classForName(name, MetaUtils.class.getClassLoader());
    }

    static Class classForName(String name, ClassLoader classLoader)
    {
        try
        {
            if (name == null || name.isEmpty())
            {
                throw new JsonIoException("Class name cannot be null or empty.");
            }
            Class c = nameToClass.get(name);
            try
            {
                loadClassException = null;
                return c == null ? loadClass(name, classLoader) : c;
            }
            catch (Exception e)
            {
                // Remember why in case later we have a problem
                loadClassException = e;
                return LinkedHashMap.class;
            }
        }
        catch (Exception e)
        {
            throw new JsonIoException("Unable to create class: " + name, e);
        }
    }

    // loadClass() provided by: Thomas Margreiter
    private static Class loadClass(String name, ClassLoader classLoader) throws ClassNotFoundException
    {
        String className = name;
        boolean arrayType = false;
        Class primitiveArray = null;

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
        Class currentClass = null;
        if (null == primitiveArray)
        {
            currentClass = classLoader.loadClass(className);
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

    static String removeLeadingAndTrailingQuotes(String s)
    {
        Matcher m = extraQuotes.matcher(s);
        if (m.find())
        {
            s = m.group(2);
        }
        return s;
    }

    public static Object newInstance(Class c)
    {
        if (unmodifiableSortedMap.getClass().isAssignableFrom(c))
        {
            return new TreeMap();
        }
        if (unmodifiableMap.getClass().isAssignableFrom(c))
        {
            return new LinkedHashMap();
        }
        if (unmodifiableSortedSet.getClass().isAssignableFrom(c))
        {
            return new TreeSet();
        }
        if (unmodifiableSet.getClass().isAssignableFrom(c))
        {
            return new LinkedHashSet();
        }
        if (unmodifiableCollection.getClass().isAssignableFrom(c))
        {
            return new ArrayList();
        }

        // Constructor not cached, go find a constructor
        Object[] constructorInfo = constructors.get(c);
        if (constructorInfo != null)
        {   // Constructor was cached
            Constructor constructor = (Constructor) constructorInfo[0];

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
            Class[] paramTypes = constructor.getParameterTypes();
            if (paramTypes == null || paramTypes.length == 0)
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
    static Object[] newInstanceEx(Class c)
    {
        try
        {
            Constructor constructor = c.getConstructor(emptyClassArray);
            if (constructor != null)
            {
                return new Object[] {constructor.newInstance(), constructor, true};
            }
            return tryOtherConstruction(c);
        }
        catch (Exception e)
        {
            // OK, this class does not have a public no-arg constructor.  Instantiate with
            // first constructor found, filling in constructor values with null or
            // defaults for primitives.
            return tryOtherConstruction(c);
        }
    }

    static Object[] tryOtherConstruction(Class c)
    {
        Constructor[] constructors = c.getDeclaredConstructors();
        if (constructors.length == 0)
        {
            throw new JsonIoException("Cannot instantiate '" + c.getName() + "' - Primitive, interface, array[] or void");
        }

        // Sort constructors - public, protected, private, package-private
        List<Constructor> constructorList = Arrays.asList(constructors);
        Collections.sort(constructorList, new Comparator<Constructor>()
        {
            public int compare(Constructor c1, Constructor c2)
            {
                boolean c1Vis = isPublic(c1.getModifiers());
                boolean c2Vis = isPublic(c2.getModifiers());

                if (c1Vis != c2Vis)
                {   // favor 'public' as first
                    return c1Vis ? -1 : 1;
                }

                c1Vis = isProtected(c1.getModifiers());
                c2Vis = isProtected(c2.getModifiers());

                if (c1Vis != c2Vis)
                {   // favor protected 2nd
                    return c1Vis ? -1 : 1;
                }

                c1Vis = isPrivate(c1.getModifiers());
                c2Vis = isPrivate(c2.getModifiers());

                if (c1Vis != c2Vis)
                {   //
                    return c1Vis ? -1 : 1;
                }

                return 0;
            }
        });

        // Try each constructor (public, protected, private, package-private) with null values for non-primitives.
        for (Constructor constructor : constructorList)
        {
            constructor.setAccessible(true);
            Class[] argTypes = constructor.getParameterTypes();
            Object[] values = fillArgs(argTypes, true);
            try
            {
                return new Object[] {constructor.newInstance(values), constructor, true};
            }
            catch (Exception ignored)
            { }
        }

        // Try each constructor (public, protected, private, package-private) with non-null values for non-primitives.
        for (Constructor constructor : constructorList)
        {
            constructor.setAccessible(true);
            Class[] argTypes = constructor.getParameterTypes();
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

    static Object[] fillArgs(Class[] argTypes, boolean useNull)
    {
        final Object[] values = new Object[argTypes.length];
        for (int i = 0; i < argTypes.length; i++)
        {
            final Class argType = argTypes[i];
            if (isPrimitive(argType))
            {
                values[i] = newPrimitiveWrapper(argType, null);
            }
            else if (useNull)
            {
                values[i] = null;
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
                    values[i] = new ArrayList();
                }
                else if (SortedSet.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeSet();
                }
                else if (Set.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashSet();
                }
                else if (SortedMap.class.isAssignableFrom(argType))
                {
                    values[i] = new TreeMap();
                }
                else if (Map.class.isAssignableFrom(argType))
                {
                    values[i] = new LinkedHashMap();
                }
                else if (Collection.class.isAssignableFrom(argType))
                {
                    values[i] = new ArrayList();
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
                else
                {
                    values[i] = null;
                }
            }
        }

        return values;
    }

    static Object newPrimitiveWrapper(Class c, Object rhs)
    {
        final String cname;
        try
        {
            cname = c.getName();
            if (cname.equals("boolean") || cname.equals("java.lang.Boolean"))
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
            else if (cname.equals("byte") || cname.equals("java.lang.Byte"))
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
            else if (cname.equals("char") || cname.equals("java.lang.Character"))
            {
                if (rhs == null)
                {
                    return '\u0000';
                }
                if (rhs instanceof String)
                {
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
            else if (cname.equals("double") || cname.equals("java.lang.Double"))
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
            else if (cname.equals("float") || cname.equals("java.lang.Float"))
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
            else if (cname.equals("int") || cname.equals("java.lang.Integer"))
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
            else if (cname.equals("long") || cname.equals("java.lang.Long"))
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
            else if (cname.equals("short") || cname.equals("java.lang.Short"))
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
        }
        catch (Exception e)
        {
            String className = c == null ? "null" : c.getName();
            throw new JsonIoException("Error creating primitive wrapper instance for Class: " + className, e);
        }

        throw new JsonIoException("Class '" + cname + "' does not have primitive wrapper.");
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
                Constructor<Unsafe> unsafeConstructor = classForName("sun.misc.Unsafe", MetaUtils.class.getClassLoader()).getDeclaredConstructor();
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
        public Object allocateInstance(Class clazz)
        {
            try
            {
                return allocateInstance.invoke(sunUnsafe, clazz);
            }
            catch (IllegalAccessException e )
            {
                String name = clazz == null ? "null" : clazz.getName();
                throw new JsonIoException("Unable to create instance of class: " + name, e);
            }
            catch (IllegalArgumentException e)
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
