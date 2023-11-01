package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class LocalDateFactory extends AbstractTemporalFactory<LocalDate> {

    public LocalDateFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalDateFactory() {
        super(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    protected LocalDate fromString(String s) {
        return LocalDate.parse(s, dateTimeFormatter);
    }

    @Override
    protected LocalDate fromNumber(Number l) {
        return LocalDate.ofEpochDay(l.longValue());
    }

    @Override
    protected LocalDate fromJsonObject(JsonObject job) {
        Number month = (Number) job.get("month");
        Number day = (Number) job.get("day");
        Number year = (Number) job.get("year");

        return LocalDate.of(year.intValue(), month.intValue(), day.intValue());
    }
}
