package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.accessors.MethodAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

public class GetMethodAccessorFactory implements AccessorFactory {
    private static final int METHOD_MODIFIERS = Modifier.PUBLIC | Modifier.STATIC;

    @Override
    public Accessor createAccessor(Field field, Map<String, Method> possibleMethods) {
        final String name = field.getName();
        final Method method = possibleMethods.get(createGetterName(name));

        if (method == null || (method.getModifiers() & METHOD_MODIFIERS) != Modifier.PUBLIC) {
            return null;
        }

        return new MethodAccessor(field, method);
    }

    /**
     * Creates the commone name for a get Method
     *
     * @param fieldName - String representing the field name
     * @return String - returns the appropriate method name to access this fieldName.
     */
    private static String createGetterName(String fieldName) {
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
}
