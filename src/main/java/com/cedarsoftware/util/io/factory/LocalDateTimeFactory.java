package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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
public class LocalDateTimeFactory extends AbstractTemporalFactory<LocalDateTime> {

    public LocalDateTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalDateTimeFactory() {
        super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    protected LocalDateTime fromString(String s)
    {
        try
        {
            return LocalDateTime.parse(s, dateTimeFormatter);
        }
        catch (Exception e)
        {   // Increase date-time format flexibility - JSON not written by json-io.
            Date date = DateFactory.parseDate(s);
            return date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }

    @Override
    protected LocalDateTime fromNumber(Number l) {
        return LocalDateTime.from(Instant.ofEpochMilli(l.longValue()));
    }

    @Override
    protected LocalDateTime fromJsonObject(JsonObject job) {
        String date = (String) job.get("date");
        String time = (String) job.get("time");

        if (date == null || time == null) {
            throw new IllegalArgumentException("'date' and 'time' or 'value' are required fields when parsing object using LocalDateTimeFactory");
        }

        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ISO_LOCAL_TIME);
        return LocalDateTime.of(localDate, localTime);
    }
}
