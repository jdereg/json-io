package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


public class LocalDateTimeFactory extends AbstractTemporalFactory<LocalDateTime> {

    public LocalDateTimeFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public LocalDateTimeFactory() {
        super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Override
    protected LocalDateTime fromString(String s) {
        return LocalDateTime.parse(s, dateTimeFormatter);
    }

    @Override
    protected LocalDateTime fromJsonObject(JsonObject job) {
        var date = (String) job.get("date");
        var time = (String) job.get("time");

        if (date == null || time == null) {
            throw new IllegalArgumentException("'date' and 'time' or 'value' are required fields when parsing object using LocalDateTimeFactory");
        }

        var localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        var localTime = LocalTime.parse(time, DateTimeFormatter.ISO_LOCAL_TIME);
        return LocalDateTime.of(localDate, localTime);
    }
}
