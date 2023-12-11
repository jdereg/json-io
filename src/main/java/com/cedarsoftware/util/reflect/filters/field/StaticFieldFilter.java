package com.cedarsoftware.util.reflect.filters.field;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.cedarsoftware.util.reflect.filters.FieldFilter;

public class StaticFieldFilter implements FieldFilter {

    @Override
    public boolean filter(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }

    @Override
    public FieldFilter createCopy() {
        return new StaticFieldFilter();
    }
}
