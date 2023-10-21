package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


public class LocalTimeFactory implements JsonReader.ClassFactory {

    protected final DateTimeFormatter dateTimeFormatter;

    public LocalTimeFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    public LocalTimeFactory() {
        this(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    public Object newInstance(Class c, Object o, JsonReader reader) {

        if (o instanceof String) {
            return LocalTime.parse((String) o, dateTimeFormatter);
        }

        JsonObject job = (JsonObject) o;

        if (job.containsKey("value")) {
            return LocalTime.parse((String) job.get("value"), dateTimeFormatter);
        }

        Number hour = MetaUtils.getValueWithDefaultForMissing(job, "hour", null);
        Number minute = MetaUtils.getValueWithDefaultForMissing(job, "minute", null);
        Number second = MetaUtils.getValueWithDefaultForNull(job, "second", 0);
        Number nano = MetaUtils.getValueWithDefaultForNull(job, "nano", 0);

        if (hour == null || minute == null) {
            throw new JsonIoException("hour and minute cannot be null if value is null for LocalTimeFactory");
        }

        return LocalTime.of(hour.intValue(), minute.intValue(), second.intValue(), nano.intValue());
    }
}
