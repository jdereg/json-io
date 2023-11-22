package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.*;

public class MetaUtilsMap {

    private static final Set<Class<?>> prims = new HashSet<>();

    static {
        prims.add(Byte.class);
        prims.add(Integer.class);
        prims.add(Long.class);
        prims.add(Double.class);
        prims.add(Character.class);
        prims.add(Float.class);
        prims.add(Boolean.class);
        prims.add(Short.class);
    }

    public static <K, V> V getValueWithDefaultForMissing(Map map, K key, V defaultValue) {
        if (!map.containsKey(key)) {
            return defaultValue;
        }

        return (V) map.get(key);
    }

    public static <K, V> V getValue(Map map, K key) {
        return (V) map.get(key);
    }

    public static <K, V> V getValueWithDefaultForNull(Map map, K key, V defaultValue) {
        V value = (V) map.get(key);
        return (value == null) ? defaultValue : value;
    }

    public static <K, V> Map<K, V> computeMapIfAbsent(Map<String, Object> map, String keyName) {
        return (Map<K, V>)map.computeIfAbsent(keyName, k -> new HashMap<K, V>());
    }

    public static <T> Set<T> computeSetIfAbsent(Map<String, Object> map, String keyName) {
        return (Set<T>)map.computeIfAbsent(keyName, k -> new HashSet<T>());
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
                values[i] = MetaUtils.convert(argType, null);
            }
            else if (useNull)
            {
                values[i] = null;
            }
            else if (prims.contains(argType))
            {
                values[i] = MetaUtils.convert(argType, null);
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
     * loadClass() provided by: Thomas Margreiter
     */
    static Class<?> loadClass(String name, ClassLoader classLoader) throws ClassNotFoundException
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
     * When two constructors have the same access type (both public, both private, etc.)
     * then compare constructors by parameter length (fewer params comes before more params).
     * If parameter count is the same, then compare by parameter Class names.  If those are equal,
     * which should never happen, then the constructors are equal.
     */
    static int compareConstructors(Constructor<?> c1, Constructor<?> c2)
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
        Map<String, Object> args = new HashMap<>();
        args.put(JsonWriter.TYPE, false);
        args.put(JsonWriter.SHORT_META_KEYS, true);
        String arg = JsonWriter.objectToJson(obj, args);
        if (arg.length() > argCharLen)
        {
            arg = arg.substring(0, argCharLen) + "...";
        }
        return arg;
    }
}
