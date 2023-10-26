package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.accessors.MethodAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class EnumNameAccessorFactory implements AccessorFactory {

    private static final String NAME = "name";

    @Override
    public Accessor createAccessor(Field field, Map<String, Method> possibleMethods) {

        if (!field.getDeclaringClass().equals(Enum.class)) {
            return null;
        }

        Method method = possibleMethods.get(NAME);
        return method == null ? null : new MethodAccessor(field, method);
    }
}
