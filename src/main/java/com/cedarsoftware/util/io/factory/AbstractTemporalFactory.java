package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Abstract class to help create temporal items.
 */
public abstract class AbstractTemporalFactory<T extends TemporalAccessor> implements JsonReader.ClassFactory {
    protected final DateTimeFormatter dateTimeFormatter;

    protected AbstractTemporalFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    @Override
    public T newInstance(Class<?> c, JsonObject<String, Object> job) {
        Object value = job.get("value");

        if (value instanceof String) {
            return (T) fromString((String) value);
        }

        if (value instanceof Number) {
            return (T) fromNumber((Number) value);
        }

        return fromJsonObject(job);
    }

    protected abstract T fromString(String s);

    protected T fromNumber(Number l) {
        throw new IllegalArgumentException("Long Timestamps are not supported for this Temporal class");
    }

    protected abstract T fromJsonObject(JsonObject<String, Object> job);

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
