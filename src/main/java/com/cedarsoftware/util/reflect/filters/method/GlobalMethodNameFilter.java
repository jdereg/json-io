package com.cedarsoftware.util.reflect.filters.method;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import com.cedarsoftware.util.reflect.filters.MethodFilter;

public class GlobalMethodNameFilter implements MethodFilter {

    private final Set<String> filteredMethodNames;

    public GlobalMethodNameFilter(Set<String> filteredMethodNames) {
        this.filteredMethodNames = filteredMethodNames;
    }

    @Override
    public boolean filter(Method method) {
        return this.filteredMethodNames.contains(method.getName());
    }

    @Override
    public MethodFilter createCopy(boolean immutable) {
        if (immutable) {
            return new GlobalMethodNameFilter(Collections.unmodifiableSet(filteredMethodNames));
        } else {
            return new GlobalMethodNameFilter(new ConcurrentSkipListSet<>(filteredMethodNames));
        }
    }
}
