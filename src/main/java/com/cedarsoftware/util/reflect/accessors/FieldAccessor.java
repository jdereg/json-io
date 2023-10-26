package com.cedarsoftware.util.reflect.accessors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FieldAccessor extends AbstractAccessor {

    public FieldAccessor(Field f) {
        super(f);

        if (!(Modifier.isPublic(f.getModifiers()) && Modifier.isPublic(f.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(f);
        }
    }

    @Override
    public Object retrieve(Object o) throws IllegalAccessException {
        return this.getField().get(o);
    }
}
