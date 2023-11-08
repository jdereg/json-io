package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;
import com.cedarsoftware.util.io.Readers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class OffsetDateTimeFactory extends AbstractTemporalFactory<OffsetDateTime> {

    private ZoneId zoneId;

    public OffsetDateTimeFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter);
        this.zoneId = zoneId;
    }

    public OffsetDateTimeFactory() {
        this(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZoneOffset.systemDefault());
    }

    @Override
    protected OffsetDateTime fromNumber(Number l) {
        Instant instant = Instant.ofEpochMilli(l.longValue());
        return OffsetDateTime.from(instant.atZone(zoneId));
    }

    @Override
    protected OffsetDateTime fromString(String s) {
        try {
            return OffsetDateTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {   // Increase date-time format flexibility - JSON not written by json-io.
            Date date = Readers.DateReader.parseDate(s);
            return date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime();
        }
    }

    @Override
    protected OffsetDateTime fromJsonObject(JsonObject job) {

        LocalDateTime dateTime = parseLocalDateTime(job.get("dateTime"));
        ZoneOffset zoneOffset = parseOffset(job.get("offset"));

        if (dateTime == null || zoneOffset == null) {
            throw new JsonIoException("Invalid json for OffsetDateTime");
        }

        return OffsetDateTime.of(dateTime, zoneOffset);
    }

    private LocalDateTime parseLocalDateTime(Object o) {
        if (o instanceof String) {
            return LocalDateTime.parse((String) o, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        if (o instanceof JsonObject) {
            JsonReader reader = ReaderContext.instance().getReader();
            return reader.convertParsedMapsToJava((JsonObject) o, LocalDateTime.class);
        }

        return null;
    }

    private ZoneOffset parseOffset(Object o) {
        if (o instanceof String) {
            return ZoneOffset.of((String) o);
        }

        if (o instanceof JsonObject) {
            JsonReader reader = ReaderContext.instance().getReader();
            return reader.convertParsedMapsToJava((JsonObject) o, ZoneOffset.class);
        }

        return null;
    }

}
