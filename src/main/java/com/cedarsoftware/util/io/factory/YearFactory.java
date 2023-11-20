package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Date;

import static java.time.temporal.ChronoField.YEAR;

public class YearFactory extends AbstractTemporalFactory<Year> {

    private static final DateTimeFormatter PARSER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .toFormatter();

    protected YearFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public YearFactory() {
        this(PARSER);
    }

    @Override
    protected Year fromString(String s) {
        try {
            return Year.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            Date date = DateFactory.parseDate(s);
            OffsetDateTime dt = date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime();
            return Year.of(dt.getYear());
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
