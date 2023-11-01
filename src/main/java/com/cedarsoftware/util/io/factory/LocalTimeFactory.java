package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.MetaUtils;

import java.time.LocalTime;
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
public class LocalTimeFactory extends AbstractTemporalFactory<LocalTime> {

    public LocalTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalTimeFactory() {
        super(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    protected LocalTime fromString(String s) {
        return LocalTime.parse(s, dateTimeFormatter);
    }

    @Override
    protected LocalTime fromNumber(Number l) {
        throw new UnsupportedOperationException("Cannot convert to " + LocalTime.class + " from number value");
    }

    @Override
    protected LocalTime fromJsonObject(JsonObject job) {
        Number hour = (Number) job.get("hour");
        Number minute = (Number) job.get("minute");
        Number second = MetaUtils.getValueWithDefaultForNull(job, "second", 0);
        Number nano = MetaUtils.getValueWithDefaultForNull(job, "nano", 0);

        if (hour == null || minute == null) {
            throw new JsonIoException("hour and minute cannot be null if value is null for LocalTimeFactory");
        }

        return LocalTime.of(hour.intValue(), minute.intValue(), second.intValue(), nano.intValue());
    }
}
