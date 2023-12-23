package com.cedarsoftware.util.io.factory;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cedarsoftware.util.io.DateUtilities;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
 *         limitations under the License.*
 */
public class DateFactory implements JsonReader.ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();

        if (value instanceof String) {
            return fromString((String) value);
        }

        if (value instanceof Number) {
            return fromNumber((Number) value);
        }

        return fromJsonObject(c, jObj);
    }

    protected Object fromString(String value) {
        return DateUtilities.parseDate(value);
    }

    protected Object fromNumber(Number value) {
        return new Date(value.longValue());
    }

    //  if date comes in as full json object its timestamp type
    //  due to
    protected Object fromJsonObject(Class<?> c, JsonObject object) {
        Object time = object.get("time");
        if (time == null) {
            throw new IllegalArgumentException("'time' field must be specified'");
        }
        Object nanos = object.get("nanos");

        Timestamp timestamp = new Timestamp(Long.parseLong((String) time));

        if (nanos == null) {
            return timestamp;
        }

        timestamp.setNanos(Integer.parseInt((String) nanos));
        return timestamp;
    }
    
    @Override
    public boolean isObjectFinal() {
        return true;
    }
}

