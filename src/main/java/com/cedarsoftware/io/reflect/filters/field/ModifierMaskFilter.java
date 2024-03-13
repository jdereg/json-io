package com.cedarsoftware.io.reflect.filters.field;

import java.lang.reflect.Field;

import com.cedarsoftware.io.reflect.filters.FieldFilter;

public class ModifierMaskFilter implements FieldFilter {

    private final int mask;

    public ModifierMaskFilter(int mask) {
        this.mask = mask;
    }

    @Override
    public boolean filter(Field field) {
        return (field.getModifiers() & mask) != 0;
    }
}
