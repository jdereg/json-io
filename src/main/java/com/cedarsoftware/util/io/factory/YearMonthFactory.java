package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.util.Date;

import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public class YearMonthFactory extends AbstractTemporalFactory<YearMonth> {

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .toFormatter();


    public YearMonthFactory(DateTimeFormatter dateFormatter) {
        super(dateFormatter);
    }

    public YearMonthFactory() {
        super(FORMATTER);
    }

    @Override
    protected YearMonth fromString(String s) {
        try {
            return YearMonth.parse(s, dateTimeFormatter);
        } catch (Exception e) {
            Date date = DateFactory.parseDate(s);
            OffsetDateTime dt = date.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toOffsetDateTime();
            return YearMonth.of(dt.getYear(), dt.getMonthValue());
        }
    }

    @Override
    protected YearMonth fromJsonObject(JsonObject job) {
        Number month = (Number) job.get("month");
        Number year = (Number) job.get("year");

        return YearMonth.of(year.intValue(), month.intValue());
    }

}
