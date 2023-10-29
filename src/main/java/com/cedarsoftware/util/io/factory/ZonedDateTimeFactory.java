package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReferenceTracker;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDateTimeFactory extends AbstractTemporalFactory<ZonedDateTime> {
    public ZonedDateTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public ZonedDateTimeFactory() {
        super(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    @Override
    protected ZonedDateTime fromString(String s) {
        return ZonedDateTime.parse(s, dateTimeFormatter);
    }

    @Override
    protected ZonedDateTime fromJsonObject(JsonObject job) {
        var dateTime = (String) job.get("dateTime");
        var localDateTime = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);


        var zone = (JsonObject) job.get("zone");
        String id = checkReferences(zone, "id");
        var zoneId = ZoneId.of(id);


        // need to be able to process references for offset and zone.
        var offsetMap = (JsonObject) job.get("offset");
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
        return (T) ReferenceTracker.instance().getRefTarget(job).get(key);
    }
}
