package com.cedarsoftware.io;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Callable;

import com.cedarsoftware.util.CaseInsensitiveMap;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.ReflectionUtils;
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
        // Security: Validate input parameters to prevent issues
        if (argCharLen < 0) {
            throw new JsonIoException("argCharLen cannot be negative: " + argCharLen);
        }
        
        // Security: Limit maximum string length to prevent memory exhaustion using default limit
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        int maxAllowedLength = Math.min(argCharLen, defaultOptions.getMaxAllowedLength());
        
        try {
            WriteOptions options = new WriteOptionsBuilder().shortMetaKeys(true).showTypeInfoNever().build();
            String arg = JsonIo.toJson(obj, options);
            
            // Security: Validate JSON string length before processing
            if (arg.length() > maxAllowedLength) {
                arg = arg.substring(0, maxAllowedLength) + "...";
            }
            return arg;
        } catch (Exception e) {
            // Security: Safely handle any JSON serialization errors
            return "Error serializing object: " + e.getClass().getSimpleName();
        }
    }

    public static <K, V> V getValueWithDefaultForNull(Map map, K key, V defaultValue) {
        // Security: Validate map parameter to prevent null pointer exceptions
        if (map == null) {
            return defaultValue;
        }
        
        try {
            V value = (V) map.get(key);
            return (value == null) ? defaultValue : value;
        } catch (ClassCastException e) {
            // Security: Handle unsafe casting gracefully
            throw new JsonIoException("Type mismatch when retrieving value for key: " + key + 
                ". Expected type: " + (defaultValue != null ? defaultValue.getClass().getName() : "null") + 
                ", Actual type: " + (map.get(key) != null ? map.get(key).getClass().getName() : "null"), e);
        }
    }

    public static <K, V> V getValueWithDefaultForMissing(Map map, K key, V defaultValue) {
        // Security: Validate map parameter to prevent null pointer exceptions
        if (map == null) {
            return defaultValue;
        }
        
        if (!map.containsKey(key)) {
            return defaultValue;
        }

        try {
            return (V) map.get(key);
        } catch (ClassCastException e) {
            // Security: Handle unsafe casting gracefully
            throw new JsonIoException("Type mismatch when retrieving value for key: " + key + 
                ". Expected type: " + (defaultValue != null ? defaultValue.getClass().getName() : "null") + 
                ", Actual type: " + (map.get(key) != null ? map.get(key).getClass().getName() : "null"), e);
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
     * Load in a Map-style properties file with configurable security limits from ReadOptions.
     * Expects key and value to be separated by a = (whitespace ignored).
     * Ignores lines beginning with a # and it also ignores blank lines.
     *
     * @param resName String name of the resource file.
     * @param readOptions ReadOptions containing configurable security limits.
     */
    public static Map<String, String> loadMapDefinition(String resName, ReadOptions readOptions) {
        // Security: Validate resource name to prevent directory traversal attacks
        if (resName == null || resName.trim().isEmpty()) {
            throw new JsonIoException("Resource name cannot be null or empty");
        }
        
        // Security: Prevent directory traversal attacks
        if (resName.contains("..") || resName.contains("\\") || resName.startsWith("/")) {
            throw new JsonIoException("Invalid resource name: " + resName + ". Resource names cannot contain '..' or path separators.");
        }
        
        Map<String, String> map = new LinkedHashMap<>();
        Scanner scanner = null;
        try {
            String contents = ClassUtilities.loadResourceAsString(resName);
            
            // Security: Validate content size to prevent memory exhaustion using configurable limit
            int maxFileContentSize = readOptions.getMaxFileContentSize();
            if (contents.length() > maxFileContentSize) {
                throw new JsonIoException("Resource file too large: " + resName + " (" + contents.length() + " bytes). Maximum allowed: " + maxFileContentSize + " bytes");
            }
            
            scanner = new Scanner(contents);
            int lineCount = 0;
            int maxLineCount = readOptions.getMaxLineCount();
            int maxLineLength = readOptions.getMaxLineLength();
            
            while (scanner.hasNextLine()) {
                // Security: Prevent unbounded line processing using configurable limit
                if (++lineCount > maxLineCount) {
                    throw new JsonIoException("Resource file has too many lines: " + resName + " (" + lineCount + " lines). Maximum allowed: " + maxLineCount);
                }
                
                String line = scanner.nextLine();
                
                // Security: Validate line length to prevent memory issues using configurable limit
                if (line.length() > maxLineLength) {
                    throw new JsonIoException("Line too long in resource file: " + resName + " (line " + lineCount + ", " + line.length() + " chars). Maximum allowed: " + maxLineLength + " chars per line");
                }
                
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("#") && !trimmedLine.isEmpty()) {
                    String[] parts = line.split("=", 2); // Limit to 2 parts to handle values with '='
                    
                    // Security: Validate that we have exactly 2 parts
                    if (parts.length != 2) {
                        throw new JsonIoException("Invalid format in resource file: " + resName + " at line " + lineCount + ". Expected format: key=value");
                    }
                    
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    // Security: Validate key and value are not empty
                    if (key.isEmpty()) {
                        throw new JsonIoException("Empty key found in resource file: " + resName + " at line " + lineCount);
                    }
                    
                    map.put(key, value);
                }
            }
        } catch (Exception e) {
            if (e instanceof JsonIoException) {
                throw e; // Re-throw security validation errors
            }
            throw new JsonIoException("Error reading in " + resName + ". The file should be in the resources folder. The contents are expected to have two strings separated by '='. You can use # or blank lines in the file, they will be skipped.", e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return map;
    }

    /**
     * Load in a Map-style properties file. Expects key and value to be separated by a = (whitespace ignored).
     * Ignores lines beginning with a # and it also ignores blank lines.
     * Uses default security limits for backward compatibility.
     *
     * @param resName String name of the resource file.
     */
    public static Map<String, String> loadMapDefinition(String resName) {
        // Use default ReadOptions for backward compatibility
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        return loadMapDefinition(resName, defaultOptions);
    }

    /**
     * Load in a Set-style simple file of values with configurable security limits from ReadOptions.
     * Expects values to be one per line. Ignores lines beginning with a # and it also ignores blank lines.
     *
     * @param resName String name of the resource file.
     * @param readOptions ReadOptions containing configurable security limits.
     * @return the set of strings
     */
    public static Set<String> loadSetDefinition(String resName, ReadOptions readOptions) {
        // Security: Validate resource name to prevent directory traversal attacks
        if (resName == null || resName.trim().isEmpty()) {
            throw new JsonIoException("Resource name cannot be null or empty");
        }
        
        // Security: Prevent directory traversal attacks
        if (resName.contains("..") || resName.contains("\\") || resName.startsWith("/")) {
            throw new JsonIoException("Invalid resource name: " + resName + ". Resource names cannot contain '..' or path separators.");
        }
        
        Set<String> set = new LinkedHashSet<>();
        Scanner scanner = null;
        try {
            String contents = ClassUtilities.loadResourceAsString(resName);
            
            // Security: Validate content size to prevent memory exhaustion using configurable limit
            int maxFileContentSize = readOptions.getMaxFileContentSize();
            if (contents.length() > maxFileContentSize) {
                throw new JsonIoException("Resource file too large: " + resName + " (" + contents.length() + " bytes). Maximum allowed: " + maxFileContentSize + " bytes");
            }
            
            scanner = new Scanner(contents);
            int lineCount = 0;
            int maxLineCount = readOptions.getMaxLineCount();
            int maxLineLength = readOptions.getMaxLineLength();
            
            while (scanner.hasNextLine()) {
                // Security: Prevent unbounded line processing using configurable limit
                if (++lineCount > maxLineCount) {
                    throw new JsonIoException("Resource file has too many lines: " + resName + " (" + lineCount + " lines). Maximum allowed: " + maxLineCount);
                }
                
                String line = scanner.nextLine();
                
                // Security: Validate line length to prevent memory issues using configurable limit
                if (line.length() > maxLineLength) {
                    throw new JsonIoException("Line too long in resource file: " + resName + " (line " + lineCount + ", " + line.length() + " chars). Maximum allowed: " + maxLineLength + " chars per line");
                }
                
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("#") && !trimmedLine.isEmpty()) {
                    set.add(trimmedLine);
                }
            }
        } catch (Exception e) {
            if (e instanceof JsonIoException) {
                throw e; // Re-throw security validation errors
            }
            throw new JsonIoException("Error reading in " + resName + ". The file should be in the resources folder. The contents have a single String per line.  You can use # (comment) or blank lines in the file, they will be skipped.", e);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return set;
    }

    /**
     * Load in a Set-style simple file of values. Expects values to be one per line. Ignores lines beginning with a #
     * and it also ignores blank lines.
     * Uses default security limits for backward compatibility.
     *
     * @param resName String name of the resource file.
     * @return the set of strings
     */
    public static Set<String> loadSetDefinition(String resName) {
        // Use default ReadOptions for backward compatibility
        ReadOptions defaultOptions = new ReadOptionsBuilder().build();
        return loadSetDefinition(resName, defaultOptions);
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
        return ClassUtilities.newInstance(converter, c, (Object)argumentValues);
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