package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.FieldFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class StaticFilter extends FieldFilter {
    @Override
    public boolean filter(Field field) {
        return Modifier.isStatic(field.getModifiers());
    }
}
