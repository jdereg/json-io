package com.cedarsoftware.util.io.factory;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.cedarsoftware.util.io.DateUtilities;
import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.MetaUtils;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class CalendarFactory implements JsonReader.ClassFactory {

    private final TimeZone timeZone;

    public CalendarFactory(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public CalendarFactory() {
        this(TimeZone.getDefault());
    }

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
        calendar.setTime(DateUtilities.parseDate(value));
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

            Date date = DateUtilities.parseDate(time);

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

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
