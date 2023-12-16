package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.cedarsoftware.util.io.JsonReader;

public class YearFactoryTests extends HandWrittenDateFactoryTests<Year> {
    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new YearFactory();
    }

    @Override
    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
        return new YearFactory(DateTimeFormatter.ISO_INSTANT, zoneId);
    }

    @Override
    protected Class<Year> getClassForFactory() {
        return Year.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(Year dt) {
        assertThat(dt.getValue()).isEqualTo(2011);
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(Year dt) {
        assertThat(dt.getValue()).isEqualTo(2011);
    }

    @Override
    protected void assert_handWrittenDate_withTime(Year dt) {
        assertThat(dt.getValue()).isEqualTo(2011);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(Year dt) {
        assertThat(dt.getValue()).isEqualTo(2011);
    }

    @Override
    protected void assert_handWrittenDate_inSaigon(Year dt) {
        assertThat(dt.getValue()).isEqualTo(2011);
    }
}
