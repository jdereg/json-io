package com.cedarsoftware.util.reflect;

public interface Injector {
    void inject(Object object, Object value) throws IllegalAccessException;
}
