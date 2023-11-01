package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.util.TimeZone;

public class TimeZoneFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class c, JsonObject job) {

        Object value = job.getValue();
        if (value != null) {
            return fromString(job, (String) value);
        }

        String zone = (String) job.get("zone");
        if (zone != null) {
            return fromString(job, (zone));
        }

        throw new JsonIoException("java.util.TimeZone missing 'value' field");
    }

    private Object fromString(JsonObject job, String value) {
        return job.setFinishedTarget(TimeZone.getTimeZone(value), isObjectFinal());
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
