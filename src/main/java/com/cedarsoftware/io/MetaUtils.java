package com.cedarsoftware.io;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.convert.Converter;

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

    @Deprecated
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

    /**
     * Legacy API that many applications consumed.
     */
    @Deprecated
    public static boolean isLogicalPrimitive(Class<?> c) {
        return ClassUtilities.isPrimitive(c) ||
                c.equals(String.class) ||
                Number.class.isAssignableFrom(c) ||
                Date.class.isAssignableFrom(c) ||
                c.isEnum() ||
                c.equals(Class.class);
    }

    /**
     * Load in a Map-style properties file. Expects key and value to be separated by a = (whitespace ignored).
     * Ignores lines beginning with a # and it also ignores blank lines.
     *
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
     *
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
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ClassUtilities#loadResourceAsString(String)} directly instead.
     */
    @Deprecated
    public static String loadResourceAsString(String resourceName) {
        return ClassUtilities.loadResourceAsString(resourceName);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ClassUtilities#loadResourceAsBytes(String)} directly instead.
     */
    @Deprecated
    public static byte[] loadResourceAsBytes(String resourceName) {
        return ClassUtilities.loadResourceAsBytes(resourceName);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * This method has been moved to {@link StringUtilities#removeLeadingAndTrailingQuotes(String)}.
     */
    @Deprecated
    public static String removeLeadingAndTrailingQuotes(String input) {
        return StringUtilities.removeLeadingAndTrailingQuotes(input);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ClassUtilities#findClosest(Class, Map, Object)} directly instead.
     */
    @Deprecated
    public static <T> T findClosest(Class<?> clazz, Map<Class<?>, T> workerClasses, T defaultClass) {
        return ClassUtilities.findClosest(clazz, workerClasses, defaultClass);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use ClassUtilities.setUseUnsafe(boolean) going forward.
     */
    @Deprecated
    public static void setUseUnsafe(boolean state) {
        ClassUtilities.setUseUnsafe(state);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use ClassUtilities.newInstance(Converter, Class, Collection) going forward.
     */
    @Deprecated
    public static Object newInstance(Converter converter, Class<?> c, Collection<?> argumentValues) {
        return ClassUtilities.newInstance(converter, c, argumentValues);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use ExceptionUtilities.safelyIgnoreException(Callable, T)
     */
    @Deprecated
    public static <T> T safelyIgnoreException(Callable<T> callable, T defaultValue) {
        return ExceptionUtilities.safelyIgnoreException(callable, defaultValue);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use ExceptionUtilities.safelyIgnoreException(Runnable)
     */
    @Deprecated
    public static void safelyIgnoreException(Runnable runnable) {
        ExceptionUtilities.safelyIgnoreException(runnable);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link StringUtilities#length(String)} to safely determine the length of a string without risking a {@code NullPointerException}.
     */
    @Deprecated
    public static int length(final String s) {
        return StringUtilities.length(s);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link StringUtilities#trimLength(String)} directly instead.
     */
    @Deprecated
    public static int trimLength(final String s) {
        return StringUtilities.trimLength(s);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ClassUtilities#getClassIfEnum(Class)} directly instead.
     */
    @Deprecated
    public static Class<?> getClassIfEnum(Class<?> c) {
        return ClassUtilities.getClassIfEnum(c);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ClassUtilities#isPrimitive(Class)} directly instead.
     */
    @Deprecated
    public static boolean isPrimitive(Class<?> c) {
        return ClassUtilities.isPrimitive(c);
    }

    /**
     * @deprecated This method is deprecated and will be removed in a future version.
     * Use {@link ExceptionUtilities#safelyIgnoreException(Runnable)} directly instead.
     */
    @Deprecated
    public static void trySetAccessible(AccessibleObject object) {
        ExceptionUtilities.safelyIgnoreException(() -> object.setAccessible(true));
    }
}