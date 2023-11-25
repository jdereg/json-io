package com.cedarsoftware.util.io.factory;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import static java.time.temporal.ChronoField.YEAR;

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
 *         limitations under the License.*
 */
public class YearFactory extends AbstractTemporalFactory<Year> {

    private static final DateTimeFormatter PARSER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .toFormatter();

    protected YearFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public YearFactory() {
        this(PARSER, ZoneId.systemDefault());
    }

    @Override
    protected Year fromString(String s) {
        try {
            return Year.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            return Year.of(convertToZonedDateTime(s).getYear());
        }
    }

    @Override
    protected Year fromNumber(Number num) {
        return Year.of(num.intValue());
    }

    @Override
    protected Year fromJsonObject(JsonObject job, ReaderContext context) {
        Number year = (Number) job.get("year");
        return fromNumber(year);
    }
}
