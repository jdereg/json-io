package com.cedarsoftware.util.io.factory;

import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.ReaderContext;

public class YearMonthFactory extends AbstractTemporalFactory<YearMonth> {

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .toFormatter();


    public YearMonthFactory(DateTimeFormatter dateFormatter, ZoneId zoneId) {
        super(dateFormatter, zoneId);
    }

    public YearMonthFactory() {
        super(FORMATTER, ZoneId.systemDefault());
    }

    @Override
    protected YearMonth fromString(String s) {
        try {
            return YearMonth.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            ZonedDateTime dt = convertToZonedDateTime(s);
            return YearMonth.of(dt.getYear(), dt.getMonthValue());
        }
    }

    @Override
    protected YearMonth fromJsonObject(JsonObject job, ReaderContext context) {
        Number month = (Number) job.get("month");
        Number year = (Number) job.get("year");

        return YearMonth.of(year.intValue(), month.intValue());
    }

}
