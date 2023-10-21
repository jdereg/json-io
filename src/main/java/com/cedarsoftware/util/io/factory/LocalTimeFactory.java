package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


public class LocalTimeFactory implements JsonReader.ClassFactory {

    protected final DateTimeFormatter dateTimeFormatter;

    public LocalTimeFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    public LocalTimeFactory() {
        this(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    public Object newInstance(Class c, Object o)
    {
        if (o instanceof String) {
            return LocalTime.parse((String) o, dateTimeFormatter);
        }

        Map jObj = (Map) o;

        if (jObj.containsKey("value")) {
            return LocalTime.parse((String) jObj.get("value"), dateTimeFormatter);
        }

        Number hour = MetaUtils.getValueWithDefaultForMissing(jObj, "hour", null);
        Number minute = MetaUtils.getValueWithDefaultForMissing(jObj, "minute", null);
        Number second = MetaUtils.getValueWithDefaultForNull(jObj, "second", 0);
        Number nano = MetaUtils.getValueWithDefaultForNull(jObj, "nano", 0);

        if (hour == null || minute == null) {
            throw new JsonIoException("hour and minute cannot be null if value is null for LocalTimeFactory");
        }

        return LocalTime.of(hour.intValue(), minute.intValue(), second.intValue(), nano.intValue());
    }
}
