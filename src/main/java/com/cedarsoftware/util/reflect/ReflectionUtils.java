package com.cedarsoftware.util.reflect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cedarsoftware.util.io.Convention;

/**
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
 *         limitations under the License.*
 */
public class ReflectionUtils {

    public static int ACCESSOR_MASK = Modifier.PUBLIC | Modifier.STATIC;

    private ReflectionUtils() {
    }

    /**
     * Builds a list of methods with zero parameter methods taking precedence over overrides
     * for a given single level class.
     *
     * @param classToTraverse - class to get the declared methods for
     * @return Map of name of the method to the actual emthod
     */
    public static Map<String, Method> buildDeepAccessorMethods(Class<?> classToTraverse) {
        Convention.throwIfNull(classToTraverse, "The classToTraverse cannot be null");

        Map<String, Method> map = new LinkedHashMap<>();
        Class<?> currentClass = classToTraverse;
        while (currentClass != Object.class) {
            Arrays.stream(currentClass.getDeclaredMethods())
                    .filter(m -> m.getParameterCount() == 0 &&
                            // filter out anything static and not public
                            (m.getModifiers() & ACCESSOR_MASK) == Modifier.PUBLIC &&
                            // class has to be public, too, or we cannot access
                            Modifier.isPublic(m.getDeclaringClass().getModifiers()))
                    .forEach(m -> map.put(m.getName(), m));
            currentClass = currentClass.getSuperclass();
        }

        return map;
    }

    /**
     * Builds a list of methods with zero parameter methods taking precedence over overrides
     * for a given single level class.
     *
     * @param c - class to get the declared methods for
     * @return Map of name of the method to the actual emthod
     */
    public static Map<String, Method> buildInjectorMap(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods())
                .filter(m -> m.getParameterCount() == 1)
                .collect(Collectors.toMap(Method::getName, Function.identity(), ReflectionUtils::oneParameterMethodPreference, LinkedHashMap::new));
    }


    /**
     * Binary Operator that returns a method with zero parameters on conflict.
     *
     * @param method1 - 1st method to compare
     * @param method2 - 2nd method to compare
     * @return in the case over overloads choose the method with 0 parameters.
     */
    public static Method zeroParameterMethodPreference(Method method1, Method method2) {
        return method1.getParameterCount() == 0 ? method1 : method2;
    }

    /**
     * Binary Operator that returns a method with zero parameters on conflict.
     *
     * @param method1 - 1st method to compare
     * @param method2 - 2nd method to compare
     * @return in the case over overloads choose the method with 0 parameters.
     */
    public static Method oneParameterMethodPreference(Method method1, Method method2) {
        return method1.getParameterCount() == 1 ? method1 : method2;
    }
}
