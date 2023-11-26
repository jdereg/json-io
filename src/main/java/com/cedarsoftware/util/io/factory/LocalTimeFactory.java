package com.cedarsoftware.util.io.factory;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.cedarsoftware.util.io.*;

/**
 * Abstract class to help create temporal items.
 * <p>
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 * <p>
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
public class LocalTimeFactory extends AbstractTemporalFactory<LocalTime> {

    public LocalTimeFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public LocalTimeFactory() {
        super(DateTimeFormatter.ISO_LOCAL_TIME, ZoneId.systemDefault());
    }

    @Override
    protected LocalTime fromString(String s) {
        try {
            return LocalTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            return convertToZonedDateTime(s).toLocalTime();
        }
    }

    @Override
    protected LocalTime fromNumber(Number l) {
        return LocalTime.from(Instant.ofEpochMilli(l.longValue()));
    }

    @Override
    protected LocalTime fromJsonObject(JsonObject job, ReaderContext context) {
        Number hour = (Number) job.get("hour");
        Number minute = (Number) job.get("minute");
        Number second = MetaUtilsHelper.getValueWithDefaultForNull(job, "second", 0);
        Number nano = MetaUtilsHelper.getValueWithDefaultForNull(job, "nano", 0);

        if (hour == null || minute == null) {
            throw new JsonIoException("hour and minute cannot be null if value is null for LocalTimeFactory");
        }

        return LocalTime.of(hour.intValue(), minute.intValue(), second.intValue(), nano.intValue());
    }
}
