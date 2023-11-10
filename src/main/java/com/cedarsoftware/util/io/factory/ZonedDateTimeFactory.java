package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class ZonedDateTimeFactory extends AbstractTemporalFactory<ZonedDateTime> {
    public ZonedDateTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public ZonedDateTimeFactory() {
        super(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    @Override
    protected ZonedDateTime fromString(String s) {
        try {
            return ZonedDateTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            Date date = DateFactory.parseDate(s);
            return date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .atZone(ZoneId.systemDefault());
        }
    }

    @Override
    protected ZonedDateTime fromJsonObject(JsonObject job) {
        String dateTime = (String) job.get("dateTime");
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        JsonObject zone = (JsonObject) job.get("zone");
        String id = checkReferences(zone, "id");
        ZoneId zoneId = ZoneId.of(id);

        // need to be able to process references for offset and zone.
        JsonObject offsetMap = (JsonObject) job.get("offset");
        Number totalSeconds = checkReferences(offsetMap, "totalSeconds");
        if (totalSeconds == null) {
            return ZonedDateTime.of(localDateTime, zoneId);
        }

        return ZonedDateTime.ofStrict(localDateTime, ZoneOffset.ofTotalSeconds(totalSeconds.intValue()), zoneId);
    }

    private <T> T checkReferences(JsonObject job, String key) {
        if (job == null) {
            return null;
        }
        return (T) ReaderContext.instance().getReferenceTracker().get(job).get(key);
    }
}
