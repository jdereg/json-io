package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.FieldFilter;

import java.lang.reflect.Field;

public class EnumFilter extends FieldFilter {
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
}

