package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.accessors.MethodAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class BooleanAccessorFactory implements AccessorFactory {

    private static final int METHOD_MODIFIERS = Modifier.PUBLIC | Modifier.STATIC;

    @Override
    public Accessor createAccessor(Field field, Map<String, Method> possibleMethods) {
        final Class<?> c = field.getType();

        if (c != Boolean.class && c != boolean.class) {
            return null;
        }

        final String name = field.getName();
        final Method method = possibleMethods.get(createIsName(name));

        if (method == null || (method.getModifiers() & METHOD_MODIFIERS) != Modifier.PUBLIC) {
            return null;
        }

        return new MethodAccessor(field, method);
    }


    /**
     * Creates one of the names for boolean accessor
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    public static String createIsName(String fieldName) {
        return "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
