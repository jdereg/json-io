package com.cedarsoftware.util.reflect.injectors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;

import static java.lang.reflect.Modifier.isPublic;

public class FieldInjector extends AbstractInjector {
    public FieldInjector(Field f) {
        super(f);

        if (!(isPublic(f.getModifiers()) && isPublic(f.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(f);
        }
    }
}
