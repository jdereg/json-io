package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.MetaUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


public class LocalTimeFactory extends AbstractTemporalFactory<LocalTime> {

    public LocalTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalTimeFactory() {
        super(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    protected LocalTime fromString(String s) {
        return LocalTime.parse(s, dateTimeFormatter);
    }

    @Override
    protected LocalTime fromNumber(Number l) {
        throw new UnsupportedOperationException("Cannot convert to " + LocalTime.class + " from number value");
    }

    @Override
    protected LocalTime fromJsonObject(JsonObject job) {
        Number hour = (Number) job.get("hour");
        Number minute = (Number) job.get("minute");
        Number second = MetaUtils.getValueWithDefaultForNull(job, "second", 0);
        Number nano = MetaUtils.getValueWithDefaultForNull(job, "nano", 0);

        if (hour == null || minute == null) {
            throw new JsonIoException("hour and minute cannot be null if value is null for LocalTimeFactory");
        }

        return LocalTime.of(hour.intValue(), minute.intValue(), second.intValue(), nano.intValue());
    }
}
