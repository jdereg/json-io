package com.cedarsoftware.util.io.factory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

/**
 * Abstract class to help create temporal items.
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
public class ZonedDateTimeFactory extends AbstractTemporalFactory<ZonedDateTime> {
    public ZonedDateTimeFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public ZonedDateTimeFactory() {
        super(DateTimeFormatter.ISO_ZONED_DATE_TIME, ZoneId.systemDefault());
    }

    @Override
    protected ZonedDateTime fromString(String s) {
        try {
            return ZonedDateTime.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            return convertToZonedDateTime(s);
        }
    }

    @Override
    protected ZonedDateTime fromJsonObject(JsonObject job, ReaderContext context) {
        String dateTime = (String) job.get("dateTime");
        LocalDateTime localDateTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        JsonObject zone = (JsonObject) job.get("zone");
        String id = checkReferences(context, zone, "id");
        ZoneId zoneId = ZoneId.of(id);

        // need to be able to process references for offset and zone.
        JsonObject offsetMap = (JsonObject) job.get("offset");
        Number totalSeconds = checkReferences(context, offsetMap, "totalSeconds");
        if (totalSeconds == null) {
            return ZonedDateTime.of(localDateTime, zoneId);
        }

        return ZonedDateTime.ofStrict(localDateTime, ZoneOffset.ofTotalSeconds(totalSeconds.intValue()), zoneId);
    }

    @SuppressWarnings("unchecked")
    private <T> T checkReferences(ReaderContext context, JsonObject job, String key) {
        if (job == null) {
            return null;
        }
        return (T) context.getReferences().get(job).get(key);
    }
}
