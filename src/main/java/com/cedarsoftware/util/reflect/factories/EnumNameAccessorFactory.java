package com.cedarsoftware.util.reflect.factories;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;

public class EnumNameAccessorFactory implements AccessorFactory {

    private static final String NAME = "name";

    @Override
    public Accessor createAccessor(Field field, Map<String, Method> possibleMethods) throws Throwable {

        if (!field.getDeclaringClass().equals(Enum.class)) {
            return null;
        }

        Method method = possibleMethods.get(NAME);

        try {
            return new Accessor(field, method);
        } catch (Throwable t) {
            return null;
        }
    }
}
