package com.cedarsoftware.util.reflect.injectors;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.reflect.Injector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

public abstract class AbstractInjector implements Injector {

    private final Field field;

    protected AbstractInjector(Field field) {
        this.field = field;
    }

    @Override
    public Class<?> getType() {
        return this.field.getType();
    }

    @Override
    public Class<?> getDeclaringClass() {
        return this.field.getDeclaringClass();
    }

    @Override
    public Type getGenericType() {
        return this.field.getGenericType();
    }

    @Override
    public String getName() {
        return this.field.getName();
    }


    private String getExceptionDisplayName() {
        return this.getName();
    }


    public void inject(Object object, Object value) {
        if (object == null) {
            throw new JsonIoException("Attempting to set field: " + this.getName() + " on null object.");
        }

        try {
            tryInject(object, value);
        } catch (IllegalAccessException e) {
            throw new JsonIoException("Cannot set field: " + this.getName() + " on class: " + this.getDeclaringClass().getName() + " as field or method is not accessible.  Add or create a ClassFactory implementation to create the needed class, and use JsonReader.assignInstantiator() to associate your ClassFactory to the class: " + object.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new JsonIoException("Cannot call method: " + this.getExceptionDisplayName() + " on class: " + this.getDeclaringClass().getName() + " as method threw an exception on object " + object.getClass().getName(), e);
        }
    }

    protected void tryInject(Object object, Object value) throws InvocationTargetException, IllegalAccessException {
        this.field.set(object, value);
    }
}
