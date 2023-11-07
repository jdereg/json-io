package com.cedarsoftware.util.reflect;

import java.lang.reflect.Type;

public interface Injector {
    void inject(Object object, Object value);

    Class<?> getType();

    Class<?> getDeclaringClass();

    Type getGenericType();

    String getName();
}
