package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private static final Map<Class<?>, Map<String, Field>> classMetaCache = new ConcurrentHashMap<>();

    private ReflectionUtils() {
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
    public static Method oneParameterMethodPreference(Method method1, Method method2) {
        return method1.getParameterCount() == 1 ? method1 : method2;
    }
}
