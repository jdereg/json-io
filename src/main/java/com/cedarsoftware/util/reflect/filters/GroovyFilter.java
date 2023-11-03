package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.FieldFilter;

import java.lang.reflect.Field;

/**
 * Skips groovy metaclass if one is present.
 */
public class GroovyFilter extends FieldFilter {

    public static final String META_CLASS_FIELD_NAME = "metaClass";
    public static final String META_CLASS_NAME = "groovy.lang.MetaClass";

    @Override
    public boolean filter(Field field) {
        return META_CLASS_FIELD_NAME.equals(field.getName()) && META_CLASS_NAME.equals(field.getType().getName());
    }
}
