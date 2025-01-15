package com.cedarsoftware.io.factory;

import java.lang.reflect.Constructor;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ExceptionUtilities;
import com.cedarsoftware.util.ReflectionUtils;

/**
 * Factory class to create Throwable instances.  Needed for JDK17+ as the only way to set the
 * 'detailMessage' field on a Throwable is via its constructor.
 * <p>
 *
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public class StackTraceElementFactory implements JsonReader.ClassFactory {

    public static final String DECLARING_CLASS   = "declaringClass";
    public static final String METHOD_NAME       = "methodName";
    public static final String FILE_NAME         = "fileName";
    public static final String LINE_NUMBER       = "lineNumber";
    public static final String CLASS_LOADER_NAME = "classLoaderName";
    public static final String MODULE_NAME       = "moduleName";
    public static final String MODULE_VERSION    = "moduleVersion";

    /**
     *  JDK 9+ introduced a new 7-arg constructor:
     *     StackTraceElement(String classLoaderName,
     *                       String moduleName,
     *                       String moduleVersion,
     *                       String declaringClass,
     *                       String methodName,
     *                       String fileName,
     *                       int lineNumber)
     *  If this exists (constructor2 != null), we can use it.
     */
    private static final Constructor<?> constructor1;  // the 4-arg constructor
    private static final Constructor<?> constructor2;  // the 7-arg constructor

    static {
        // Use ReflectionUtils for caching:
        // getConstructor(klass, paramTypes...) returns null if not found, caching the miss.

        // 4-arg version: StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber)
        constructor1 = ReflectionUtils.getConstructor(
                StackTraceElement.class,
                String.class, String.class, String.class, int.class
        );

        // 7-arg version: StackTraceElement(String loader, String module, String version, String declaringClass, ...)
        constructor2 = ReflectionUtils.getConstructor(
                StackTraceElement.class,
                String.class, String.class, String.class,
                String.class, String.class, String.class,
                int.class
        );
    }

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Pull fields from the JSON map
        String declaringClass  = (String) jObj.get(DECLARING_CLASS);
        String methodName      = (String) jObj.get(METHOD_NAME);
        String fileName        = (String) jObj.get(FILE_NAME);
        Number lineNum         = (Number) jObj.get(LINE_NUMBER);

        String classLoaderName = (String) jObj.get(CLASS_LOADER_NAME);
        String moduleName      = (String) jObj.get(MODULE_NAME);
        String moduleVersion   = (String) jObj.get(MODULE_VERSION);

        // Attempt to call the 7-arg constructor if available
        if (constructor2 != null) {
            StackTraceElement element = ExceptionUtilities.safelyIgnoreException(
                    () -> (StackTraceElement) constructor2.newInstance(
                            classLoaderName, moduleName, moduleVersion,
                            declaringClass, methodName, fileName,
                            lineNum.intValue()
                    ),
                    null
            );
            if (element != null) {
                return element;
            }
        }

        // Otherwise, try the 4-arg constructor
        if (constructor1 != null) {
            StackTraceElement element = ExceptionUtilities.safelyIgnoreException(
                    () -> (StackTraceElement) constructor1.newInstance(
                            declaringClass, methodName, fileName,
                            lineNum.intValue()
                    ),
                    null
            );
            if (element != null) {
                return element;
            }
        }

        // Fallback: If reflection-based calls failed, build one directly
        return new StackTraceElement(
                declaringClass,
                methodName,
                fileName,
                lineNum == null ? -1 : lineNum.intValue()
        );
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}