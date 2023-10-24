package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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
        var zoneId = ZoneId.of((String) zone.get("id"));

        // need to be able to process references for offset and zone.
        var offsetMap = (Map) job.get("offset");
        if (offsetMap == null || !offsetMap.containsKey("totalSeconds")) {
            return ZonedDateTime.of(localDateTime, zoneId);
        }

        var totalSeconds = (Number) offsetMap.get("totalSeconds");
        return ZonedDateTime.ofStrict(localDateTime, ZoneOffset.ofTotalSeconds(totalSeconds.intValue()), zoneId);
    }
}
