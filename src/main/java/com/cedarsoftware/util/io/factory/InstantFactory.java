package com.cedarsoftware.util.io.factory;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

public class InstantFactory extends AbstractTemporalFactory<Instant> {

    public InstantFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public InstantFactory() {
        super(ISO_INSTANT, ZoneId.systemDefault());
    }

    @Override
    protected Instant fromString(String s) {
        try {
            return dateTimeFormatter.parse(s, Instant::from);
        } catch (Exception e) {   // Increase date format flexibility - JSON not written by json-io.
            Date date = DateFactory.parseDate(s);

            if (date == null) {
                throw new JsonIoException("Could not parse date: " + s);
            }

            return date.toInstant();
        }
    }

    @Override
    protected Instant fromJsonObject(JsonObject job, ReaderContext context) {
        return null;
    }
}
