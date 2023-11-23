package com.cedarsoftware.util.io.factory;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * Abstract class to help create temporal items.
 * <p>
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 * <p>
 *
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
 * limitations under the License.
 */
public class OffsetTimeFactory extends AbstractTemporalFactory<OffsetTime> {

    /**
     * Specify zone if you don't want system default
     */
    public OffsetTimeFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public OffsetTimeFactory() {
        this(DateTimeFormatter.ISO_OFFSET_TIME, ZoneId.systemDefault());
    }

    @Override
    protected OffsetTime fromNumber(Number l) {
        return OffsetTime.from(Instant.ofEpochMilli(l.longValue()));
    }

    @Override
    protected OffsetTime fromString(String s) {
        try {
            return OffsetTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {   // Increase date-time format flexibility - JSON not written by json-io.
            return convertToZonedDateTime(s).toOffsetDateTime().toOffsetTime();
        }
    }

    @Override
    protected OffsetTime fromJsonObject(JsonObject job, ReaderContext context) {

        LocalTime time = parseLocalTime(job.get("time"), context);
        ZoneOffset zoneOffset = parseOffset(job.get("offset"), context);

        if (time == null || zoneOffset == null) {
            throw new JsonIoException("Invalid json for OffsetDateTime");
        }

        return OffsetTime.of(time, zoneOffset);
    }


    private LocalTime parseLocalTime(Object o, ReaderContext context) {
        if (o instanceof String) {
            return LocalTime.parse((String) o, dateTimeFormatter);
        }

        if (o instanceof JsonObject) {
            return context.reentrantConvertParsedMapsToJava((JsonObject) o, LocalTime.class);
        }

        return null;
    }

    private ZoneOffset parseOffset(Object o, ReaderContext context) {
        if (o instanceof String) {
            return ZoneOffset.of((String) o);
        }

        if (o instanceof JsonObject) {
            return context.reentrantConvertParsedMapsToJava((JsonObject) o, ZoneOffset.class);
        }

        return null;
    }
}
