package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public interface InjectorFactory {

    /**
     * Creates accessors for accessing data from an object.
     *
     * @param field           The field we're trying to access
     * @param possibleMethods a map of possible methods from the class itself
     * @return The accessor if one fits for this field, otherwise null.
     */
    Injector createInjector(Field field, Map<String, Method> possibleMethods);
}
