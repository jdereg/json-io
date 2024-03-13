package com.cedarsoftware.io.reflect.filters.method;

import java.lang.reflect.Method;

import com.cedarsoftware.io.reflect.filters.MethodFilter;

public class ModifierMethodFilter implements MethodFilter {

    private final int mask;

    public ModifierMethodFilter(int mask) {
        this.mask = mask;
    }

    @Override
    public boolean filter(Method method) {
        return (method.getModifiers() & mask) == 0;
    }
}
