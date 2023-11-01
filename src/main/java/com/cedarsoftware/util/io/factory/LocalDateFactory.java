package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class LocalDateFactory extends AbstractTemporalFactory<LocalDate> {

    public LocalDateFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalDateFactory() {
        super(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    protected LocalDate fromString(String s) {
        return LocalDate.parse(s, dateTimeFormatter);
    }

    @Override
    protected LocalDate fromNumber(Number l) {
        return LocalDate.ofEpochDay(l.longValue());
    }

    @Override
    protected LocalDate fromJsonObject(JsonObject job) {
        Number month = (Number) job.get("month");
        Number day = (Number) job.get("day");
        Number year = (Number) job.get("year");

        return LocalDate.of(year.intValue(), month.intValue(), day.intValue());
    }
}
