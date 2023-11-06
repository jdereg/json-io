package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

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
            return Year.parse(s);
        }
    }

    @Override
    protected Year fromNumber(Number num) {
        return Year.of(num.intValue());
    }

    @Override
    protected Year fromJsonObject(JsonObject job) {
        Number year = (Number) job.get("year");
        return fromNumber(year);
    }
}
