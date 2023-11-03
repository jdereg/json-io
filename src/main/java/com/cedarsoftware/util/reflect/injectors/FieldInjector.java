package com.cedarsoftware.util.reflect.injectors;

import com.cedarsoftware.util.reflect.Injector;

import java.lang.reflect.Field;

public class FieldInjector implements Injector {
    private final Field field;

    public FieldInjector(Field f) {
        this.field = f;
    }

    public void inject(Object object, Object value) throws IllegalAccessException {
        this.field.set(object, value);
    }
}
