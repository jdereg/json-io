package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class LocalDateFactory implements JsonReader.ClassFactory {

    protected final DateTimeFormatter dateTimeFormatter;

    public LocalDateFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    public LocalDateFactory() {
        this(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    public Object newInstance(Class c, JsonObject jsonObject) {
        Object value = jsonObject.get("value");

        if (value != null) {
            return loadFromValue(value);
        }

        Long year = (Long)jsonObject.get("year");
        Long month = (Long)jsonObject.get("month");
        Long day = (Long)jsonObject.get("day");

        if (year == null || month == null || day == null) {
            throw new JsonIoException("year, month, and day cannot be null for LocalDateFactory");
        }

        return LocalDate.of(year.intValue(), month.intValue(), day.intValue());
    }

    public Object loadFromValue(Object value)
    {
        if (value instanceof String)
        {
            return LocalDate.parse((String)value, dateTimeFormatter);
        }

        if (value instanceof Long)
        {
            return LocalDate.ofEpochDay((Long)value);
        }

        throw new JsonIoException("Unknown object type to parse");
    }
}
