package com.cedarsoftware.util.reflect;

import static java.lang.reflect.Modifier.isPublic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.MetaUtils;

import lombok.Getter;

public class Injector {

    @Getter
    private final Class<?> type;

    @Getter
    private final Class<?> declaringClass;

    @Getter
    private final Type genericType;

    @Getter
    private final String name;

    @Getter
    private final String displayName;

    private final MethodHandle injector;

    public Injector(Field field) throws Throwable {
        this.name = field.getName();
        this.displayName = field.getName();
        this.type = field.getType();
        this.declaringClass = field.getDeclaringClass();
        this.genericType = field.getGenericType();

        if (!(isPublic(field.getModifiers()) && isPublic(field.getDeclaringClass().getModifiers()))) {
            MetaUtils.trySetAccessible(field);
        }

        this.injector = MethodHandles.lookup().unreflectSetter(field);
    }

    public Injector(Field field, Method method) throws Throwable {
        this.name = field.getName();
        this.type = field.getType();
        this.declaringClass = field.getDeclaringClass();
        this.genericType = field.getGenericType();
        this.displayName = method.getName();

        if (!isPublic(method.getDeclaringClass().getModifiers())) {
            MetaUtils.trySetAccessible(method);
        }

        this.injector = MethodHandles.lookup().unreflect(method);
    }

    public void inject(Object object, Object value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + this.getName() + " on null object.");
        }

        try {
            this.injector.invoke(object, value);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            throw new JsonIoException("Attempting to set field: " + this.getName() + " using " + this.getDisplayName(), t);
        }
    }
}
