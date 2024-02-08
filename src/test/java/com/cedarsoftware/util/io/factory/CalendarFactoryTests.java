package com.cedarsoftware.util.io.factory;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

import com.cedarsoftware.util.DateUtilities;
import com.cedarsoftware.util.io.JsonReader;

import static org.assertj.core.api.Assertions.assertThat;

public class CalendarFactoryTests extends HandWrittenDateFactoryTests<Calendar> {

    protected JsonReader.ClassFactory createFactory() {
        return new CalendarFactory();
    }

    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
        return new CalendarFactory();
    }

    protected Class<Calendar> getClassForFactory() {
        return Calendar.class;
    }

    protected void assert_handWrittenDate_withNoZone(Calendar dt) {
        assertThat(dt.getTime()).hasYear(2011)
                .hasMonth(12)
                .hasDayOfMonth(3)
                .hasHourOfDay(10)
                .hasMinute(15)
                .hasSecond(30)
                .hasMillisecond(0);
    }

    protected void assert_handWrittenDate_withNoTime(Calendar dt) {
        assertThat(dt.getTime()).hasYear(2011)
                .hasMonth(2)
                .hasDayOfMonth(3)
                .hasHourOfDay(0)
                .hasMinute(0)
                .hasSecond(0)
                .hasMillisecond(0);
    }

    protected void assert_handWrittenDate_withTime(Calendar dt) {
        assertThat(dt.getTime()).hasYear(2011)
                .hasMonth(2)
                .hasDayOfMonth(3)
                .hasHourOfDay(8)
                .hasMinute(9)
                .hasSecond(3)
                .hasMillisecond(0);
    }

    protected void assert_handWrittenDate_withMilliseconds(Calendar dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30.050-0500");

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        assertThat(dt.getTime().getTime()).isEqualTo(cal.getTime().getTime());
    }

    protected void assert_handWrittenDate_inSaigon(Calendar dt) {
        Date date = DateUtilities.parseDate("2011-2-3 20:09:03");

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        assertThat(dt).isEqualTo(cal);
    }

}
