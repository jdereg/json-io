package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class CalendarFactory implements JsonReader.ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj) {
        Object value = jObj.getValue();
        if (value instanceof String) {
            return fromString((String) value);
        }

        if (value instanceof Number) {
            return fromNumber((Number) value);
        }

        return fromJsonObject(c, jObj);
    }

    private Object fromString(String value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(DateFactory.parseDate(value));
        return calendar;
    }

    private Object fromNumber(Number value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(value.longValue()));
        return calendar;
    }

    protected Object fromJsonObject(Class<?> c, JsonObject object) {
        String time = null;
        try {
            time = (String) object.get("time");
            if (time == null) {
                throw new JsonIoException("Calendar missing 'time' field");
            }

            String zone = (String) object.get("zone");
            TimeZone tz = zone == null ? null : TimeZone.getTimeZone(zone);

            Date date = DateFactory.parseDate(time);

            // If a Calendar reader needs a ClassFactory.newInstance() call, then write a ClassFactory for
            // the special Calendar class, don't try to do that via a custom reader.  That is why only
            // MetaUtils.newInstance() is used below.
            Calendar calendar;

            if (c == Calendar.class) {
                calendar = Calendar.getInstance();
            } else {
                calendar = (Calendar) MetaUtils.newInstance(c, null);   // Can supply args
            }

            calendar.setTime(date);
            if (tz != null) {
                calendar.setTimeZone(tz);
            }
            return calendar;
        } catch (Exception e) {
            throw new JsonIoException("Failed to parse calendar, time: " + time);
        }
    }

    private Class<?> extractClass(JsonObject object) {

        if (object.getTarget() != null) {
            return object.getTarget().getClass();
        }

        return MetaUtils.classForName(object.getType(), object.getClass().getClassLoader());
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
