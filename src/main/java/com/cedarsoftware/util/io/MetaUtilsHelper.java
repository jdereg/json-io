package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.util.*;

public class MetaUtilsHelper {
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
        String arg = JsonWriter.toJson(obj, new WriteOptions().shortMetaKeys(true).showTypeInfoNever());
        if (arg.length() > argCharLen)
        {
            arg = arg.substring(0, argCharLen) + "...";
        }
        return arg;
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
            if (!Primitives.isPrimitive(destination)) {
                return -1;
            }
            return MetaUtils.comparePrimitiveToWrapper(destination, source);
        }

        if (destination.isPrimitive()) {
            if (!Primitives.isPrimitive(source)) {
                return -1;
            }
            return MetaUtils.comparePrimitiveToWrapper(source, destination);
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
                    value = paramType.isPrimitive() ? MetaUtils.convert(paramType, null) : null;  // don't send null to a primitive parameter
                }
                else {
                    value = MetaUtils.getArgForType(paramType);
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

        int index = MetaUtils.indexOfBestValue(distances);
        if (index >= 0) {
            Object valueBestMatching = values.get(index);
            values.remove(index);
            return valueBestMatching;
        } else {
            return null;
        }
    }
}
