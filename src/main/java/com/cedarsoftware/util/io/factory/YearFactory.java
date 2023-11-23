package com.cedarsoftware.util.io.factory;

import static java.time.temporal.ChronoField.YEAR;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

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
