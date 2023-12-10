package com.cedarsoftware.util.reflect.filters.field;

import java.lang.reflect.Field;

import com.cedarsoftware.util.reflect.filters.FieldFilter;

public class EnumFieldFilter implements FieldFilter {
    @Override
    public boolean filter(Field field) {
        if (field.getDeclaringClass().isEnum() && ("internal".equals(field.getName()) || "ENUM$VALUES".equals(field.getName()))) {
            return true;
        }

        if (field.getDeclaringClass().isAssignableFrom(Enum.class) && ("hash".equals(field.getName()) || "ordinal".equals(field.getName()))) {
            return true;
        }

        return false;
    }

    @Override
    public FieldFilter createCopy(boolean immutable) {
        return new EnumFieldFilter();
    }
}

