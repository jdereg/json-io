package com.cedarsoftware.util.reflect;

import java.lang.reflect.InvocationTargetException;

public interface Accessor {
    Object retrieve(Object o) throws IllegalAccessException, InvocationTargetException;

    String getName();

    boolean isTransient();

    boolean isPublic();

    Class<?> getDeclaringClass();

    Class<?> getType();
}
