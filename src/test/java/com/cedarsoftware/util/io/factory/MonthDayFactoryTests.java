package com.cedarsoftware.util.io.factory;

import java.time.MonthDay;
import java.time.ZoneId;

import com.cedarsoftware.util.io.JsonReader;

import static org.assertj.core.api.Assertions.assertThat;

public class MonthDayFactoryTests extends HandWrittenDateFactoryTests<MonthDay> {
    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new MonthDayFactory();
    }

    @Override
    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
        return new MonthDayFactory(MonthDayFactory.FORMATTER, zoneId);
    }

    @Override
    protected Class<MonthDay> getClassForFactory() {
        return MonthDay.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(MonthDay dt) {
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(MonthDay dt) {
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
    }

    @Override
    protected void assert_handWrittenDate_withTime(MonthDay dt) {
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(MonthDay dt) {
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
    }

    @Override
    protected void assert_handWrittenDate_inSaigon(MonthDay dt) {
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(4);
    }
}
