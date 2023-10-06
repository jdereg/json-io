package com.cedarsoftware.util.io;

public class GenericSubObject<T> {
    private T object;

    public GenericSubObject(T object) {
        this.object = object;
    }

    public T getObject() { return this.object; }
}
