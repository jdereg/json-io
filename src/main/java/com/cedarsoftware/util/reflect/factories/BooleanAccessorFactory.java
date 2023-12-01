package com.cedarsoftware.util.reflect.factories;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;

public class BooleanAccessorFactory implements AccessorFactory {

    @Override
    public Accessor createAccessor(Field field, Map<String, Method> possibleMethods) throws Throwable {
        final Class<?> c = field.getType();

        if (c != Boolean.class && c != boolean.class) {
            return null;
        }

        final String name = field.getName();
        final Method method = possibleMethods.get(createIsName(name));

        if (method == null || !method.getReturnType().isAssignableFrom(field.getType())) {
            return null;
        }

        try {
            return new Accessor(field, method);
        } catch (Throwable t) {
            return null;
        }
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
