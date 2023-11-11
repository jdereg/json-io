package com.cedarsoftware.util.reflect.injectors;

import com.cedarsoftware.util.io.MetaUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static java.lang.reflect.Modifier.isPublic;

public class MethodInjector extends AbstractInjector {
    private final Method method;

    public MethodInjector(Field field, Method method) {
        super(field);

        if (!isPublic(method.getDeclaringClass().getModifiers())) {
            MetaUtils.trySetAccessible(method);
        }

        this.method = method;
    }

    @Override
    public String getExceptionDisplayName() {
        return this.method.getName();
    }

    @Override
    public Class<?> getDeclaringClass() {
        return this.method.getDeclaringClass();
    }


    @Override
    protected void tryInject(Object object, Object value) throws InvocationTargetException, IllegalAccessException {
        method.invoke(object, value);
    }
}
