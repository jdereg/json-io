package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.FieldFilter;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;

public class IncludeFieldsFilter extends FieldFilter {

    protected Set<String> fieldsNamesToInclude;

    public IncludeFieldsFilter(Set<String> fieldsNamesToInclude) {
        this.fieldsNamesToInclude = Collections.unmodifiableSet(fieldsNamesToInclude);
    }

    @Override
    public boolean filter(Field field) {
        return !fieldsNamesToInclude.contains(field.getName());
    }
}
