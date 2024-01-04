package com.cedarsoftware.util.reflect.filters.field;

import com.cedarsoftware.util.reflect.filters.FieldFilter;

import java.lang.reflect.Field;

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
