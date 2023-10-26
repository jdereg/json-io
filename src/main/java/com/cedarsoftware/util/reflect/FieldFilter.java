package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;

public abstract class FieldFilter {
    private final int hashCode;

    protected FieldFilter() {
        this.hashCode = getClass().getName().hashCode() + 97;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * returns true if you want to filter the given field
     *
     * @param field - field we're checking.
     * @return true to filter the field, false to keep the field.
     */
    public abstract boolean filter(Field field);
}
