package com.cedarsoftware.util.reflect.accessors;

import com.cedarsoftware.util.reflect.Accessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;

public abstract class AbstractAccessor implements Accessor {

    private final Field field;

    protected AbstractAccessor(Field field) {

        this.field = field;
    }

    public Field getField() {
        return this.field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractAccessor that = (AbstractAccessor) o;
        return Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field);
    }

    @Override
    public String getName() {
        return this.field.getName();
    }

    @Override
    public boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(field.getModifiers());
    }

    @Override
    public Class<?> getType() {
        return this.field.getType();
    }

    @Override
    public Class<?> getDeclaringClass() {
        return this.field.getDeclaringClass();
    }
}
