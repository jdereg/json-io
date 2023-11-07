package com.cedarsoftware.util.reflect.injectors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FieldInjector extends AbstractInjector {
    public FieldInjector(Field f) {
        super(f);

        if (!(Modifier.isPublic(f.getModifiers()) && Modifier.isPublic(f.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(f);
        }
    }
}
