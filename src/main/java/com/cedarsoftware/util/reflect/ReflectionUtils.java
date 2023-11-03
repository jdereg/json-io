package com.cedarsoftware.util.reflect;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ReflectionUtils {

    /**
     * Builds a list of methods with zero parameter methods taking precedence over overrides
     * for a given single level class.
     *
     * @param c - class to get the declared methods for
     * @return Map of name of the method to the actual emthod
     */
    public static Map<String, Method> buildAccessorMap(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods())
                .collect(Collectors.toMap(Method::getName, Function.identity(), ReflectionUtils::zeroMethodParameterPrecedence, LinkedHashMap::new));
    }


    /**
     * Binary Operator that returns a method with zero parameters on conflict.
     *
     * @param method1 - 1st method to compare
     * @param method2 - 2nd method to compare
     * @return in the case over overloads choose the method with 0 parameters.
     */
    public static Method zeroMethodParameterPrecedence(Method method1, Method method2) {
        return method1.getParameterCount() == 0 ? method1 : method2;
    }
}
