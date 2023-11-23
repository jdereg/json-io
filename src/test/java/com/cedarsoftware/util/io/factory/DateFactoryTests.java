package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import com.cedarsoftware.util.io.JsonReader;

public class DateFactoryTests extends HandWrittenDateFactoryTests<Date> {
    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new DateFactory();
    }

    @Override
    protected Class<Date> getClassForFactory() {
        return Date.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(Date dt) {
        assertThat(dt).hasYear(2011)
                .hasMonth(12)
                .hasDayOfMonth(3)
                .hasHourOfDay(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasMillisecond(0);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(Date dt) {
        assertThat(dt).hasYear(2011)
                .hasMonth(2)
                .hasDayOfMonth(3)
                .hasHourOfDay(0)
                .hasMinute(0)
                .hasSecond(0)
                .hasMillisecond(0);
    }

    @Override
    protected void assert_handWrittenDate_withTime(Date dt) {
        assertThat(dt).hasYear(2011)
                .hasMonth(2)
                .hasDayOfMonth(3)
                .hasHourOfDay(8)
                .hasMinute(9)
                .hasSecond(3)
                .hasMillisecond(0);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(Date dt) {
        assertThat(dt).hasYear(2011)
                .hasMonth(12)
                .hasDayOfMonth(3)
                .hasHourOfDay(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasMillisecond(50);
    }
}
