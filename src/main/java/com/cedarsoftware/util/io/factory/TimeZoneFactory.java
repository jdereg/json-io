package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.util.TimeZone;

public class TimeZoneFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class c, Object o) {
        if (o instanceof String) {
            return TimeZone.getTimeZone((String) o);
        }

        JsonObject jObj = (JsonObject) o;
        Object zone = jObj.get("zone");
        if (zone == null) {
            throw new JsonIoException("java.util.TimeZone must specify 'zone' field if its a JsonObject");
        }
        jObj.setFinishedTarget(TimeZone.getTimeZone((String) zone), isObjectFinal());
        return jObj.getTarget();
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
