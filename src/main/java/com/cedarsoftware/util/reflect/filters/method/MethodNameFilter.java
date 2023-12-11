package com.cedarsoftware.util.reflect.filters.method;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import com.cedarsoftware.util.reflect.filters.MethodFilter;

public class MethodNameFilter implements MethodFilter {

    private final Set<String> filteredMethodNames;

    public MethodNameFilter(Set<String> filteredMethodNames) {
        this.filteredMethodNames = filteredMethodNames;
    }

    @Override
    public boolean filter(Method method) {
        return this.filteredMethodNames.contains(method.getName());
    }

    @Override
    public MethodFilter createCopy() {
        return new MethodNameFilter(Collections.unmodifiableSet(filteredMethodNames));
    }
}
