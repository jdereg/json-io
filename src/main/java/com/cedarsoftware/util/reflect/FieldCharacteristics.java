package com.cedarsoftware.util.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

public class FieldCharacteristics {
    protected final Field field;

    public FieldCharacteristics(Field field) {
        this.field = field;
    }

    public Class<?> getFieldType() {
        return this.field.getType();
    }

    public Class<?> getDeclaringClass() {
        return this.field.getDeclaringClass();
    }

    public Type getGenericType() {
        return this.field.getGenericType();
    }

    public String getFieldName() {
        return this.field.getName();
    }

    public boolean isTransient() {
        return Modifier.isTransient(this.field.getModifiers());
    }
}
