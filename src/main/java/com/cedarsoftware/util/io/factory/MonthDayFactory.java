package com.cedarsoftware.util.io.factory;

import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.*
 */
public class MonthDayFactory extends AbstractTemporalFactory<MonthDay> {

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .toFormatter();


    public MonthDayFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public MonthDayFactory() {
        super(FORMATTER, ZoneId.systemDefault());
    }

    @Override
    protected MonthDay fromString(String s) {
        try {
            return MonthDay.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            ZonedDateTime dt = convertToZonedDateTime(s);
            return MonthDay.of(dt.getMonthValue(), dt.getDayOfMonth());
        }
    }

    @Override
    protected MonthDay fromJsonObject(JsonObject job, ReaderContext context) {
        Number month = (Number) job.get("month");
        Number day = (Number) job.get("day");

        return MonthDay.of(month.intValue(), day.intValue());
    }
}
