package com.cedarsoftware.util.reflect.filters.method;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.cedarsoftware.util.reflect.filters.MethodFilter;

public class AccessorMethodFilter implements MethodFilter {
    @Override
    public boolean filter(Method m) {
        return m.getParameterCount() != 0 ||
                Modifier.isStatic(m.getModifiers()) ||
                !Modifier.isPublic(m.getDeclaringClass().getModifiers());
    }
}
