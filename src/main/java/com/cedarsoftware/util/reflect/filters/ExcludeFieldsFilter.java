package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.FieldFilter;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

public class ExcludeFieldsFilter extends FieldFilter {

    protected Set<String> fieldNamesToIgnore;

    public ExcludeFieldsFilter(Set<String> fieldNamesToIgnore) {
        this.fieldNamesToIgnore = Collections.unmodifiableSet(fieldNamesToIgnore);
    }

    @Override
    public boolean filter(Field field) {
        return fieldNamesToIgnore.contains(field.getName());
    }
}
