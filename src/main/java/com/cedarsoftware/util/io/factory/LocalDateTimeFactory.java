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
        String date = (String) job.get("date");
        String time = (String) job.get("time");

        if (date == null || time == null) {
            throw new IllegalArgumentException("'date' and 'time' or 'value' are required fields when parsing object using LocalDateTimeFactory");
        }

        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ISO_LOCAL_TIME);
        return LocalDateTime.of(localDate, localTime);
    }
}
