package com.cedarsoftware.util.reflect.factories;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Map;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;

public class ZoneRegionAccessorFactory implements AccessorFactory {

    private static final String NAME = "id";

    @Override
    public Accessor createAccessor(Field field, NonStandardMethodNames nonStandardMethodNames, Map<String, Method> possibleAccessors, String key) throws Throwable {

        if (!("id".equals(field.getName()) && ZoneId.class.isAssignableFrom(field.getDeclaringClass()))) {
            return null;
        }

        try {
            return new Accessor(field, ZoneId.class.getMethod("getId"), key);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public AccessorFactory createCopy(boolean immutable) {
        return new ZoneRegionAccessorFactory();
    }
}
