package com.cedarsoftware.util.io;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This utility class is used to retrieve the fields from a Class.  It uses reflection
 * to do so, allowing it to obtain the non-public fields.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.*
 */
public class MetaUtils
{
    private static final Map<Class, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();
    private static final Set<Class> prims = new HashSet<>();
    private static final Map<String, Class> nameToClass = new HashMap<>();
    private static final Byte[] byteCache = new Byte[256];
    private static final Character[] charCache = new Character[128];
    private static final Pattern extraQuotes = Pattern.compile("([\"]*)([^\"]*)([\"]*)");
    static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>()
    {
        public SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        }
    };

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

        classFields = new LinkedHashMap<>();
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
     * @return inheritance distance between two classes, or Integer.MAX_VALUE if they are not related.
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
                c.equals(Class.class);
    }

    public static Class classForName(String name) throws IOException
    {
        if (name == null || name.isEmpty())
        {
            throw new IllegalArgumentException("Class name cannot be null or empty.");
        }
        Class c = nameToClass.get(name);
        try
        {
            return c == null ? loadClass(name) : c;
        }
        catch (ClassNotFoundException e)
        {
            throw new IOException("Class not found: " + name, e);
        }
    }

    // loadClass() provided by: Thomas Margreiter
    private static Class loadClass(String name) throws ClassNotFoundException
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
            switch (className)
            {
                case "[B":
                    primitiveArray = byte[].class;
                    break;
                case "[S":
                    primitiveArray = short[].class;
                    break;
                case "[I":
                    primitiveArray = int[].class;
                    break;
                case "[J":
                    primitiveArray = long[].class;
                    break;
                case "[F":
                    primitiveArray = float[].class;
                    break;
                case "[D":
                    primitiveArray = double[].class;
                    break;
                case "[Z":
                    primitiveArray = boolean[].class;
                    break;
                case "[C":
                    primitiveArray = char[].class;
                    break;
            }
            int startpos = className.startsWith("[L") ? 2 : 1;
            className = className.substring(startpos);
        }
        Class currentClass = null;
        if (null == primitiveArray)
        {
            currentClass = Thread.currentThread().getContextClassLoader().loadClass(className);
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

    static Object newPrimitiveWrapper(Class c, Object rhs) throws IOException
    {
        final String cname = c.getName();
        switch(cname)
        {
            case "boolean":
            case "java.lang.Boolean":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "false";
                    }
                    return Boolean.parseBoolean((String)rhs);
                }
                return rhs != null ? rhs : Boolean.FALSE;
            case "byte":
            case "java.lang.Byte":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Byte.parseByte((String)rhs);
                }
                return rhs != null ? byteCache[((Number) rhs).byteValue() + 128] : (byte) 0;
            case "char":
            case "java.lang.Character":
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
                    return valueOf(((String) rhs).charAt(0));
                }
                if (rhs instanceof Character)
                {
                    return rhs;
                }
                break;
            case "double":
            case "java.lang.Double":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0";
                    }
                    return Double.parseDouble((String)rhs);
                }
                return rhs != null ? rhs : 0.0d;
            case "float":
            case "java.lang.Float":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0.0f";
                    }
                    return Float.parseFloat((String)rhs);
                }
                return rhs != null ? ((Number) rhs).floatValue() : 0.0f;
            case "int":
            case "java.lang.Integer":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Integer.parseInt((String)rhs);
                }
                return rhs != null ? ((Number) rhs).intValue() : 0;
            case "long":
            case "java.lang.Long":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Long.parseLong((String)rhs);
                }
                return rhs != null ? rhs : 0L;
            case "short":
            case "java.lang.Short":
                if (rhs instanceof String)
                {
                    rhs = removeLeadingAndTrailingQuotes((String) rhs);
                    if ("".equals(rhs))
                    {
                        rhs = "0";
                    }
                    return Short.parseShort((String)rhs);
                }
                return rhs != null ? ((Number) rhs).shortValue() : (short) 0;
        }

        throw new IllegalArgumentException("Class '" + cname + "' requested for special instantiation - isPrimitive() does not match newPrimitiveWrapper()");
    }
}
