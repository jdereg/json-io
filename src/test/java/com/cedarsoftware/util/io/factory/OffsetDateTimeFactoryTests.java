package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

class OffsetDateTimeFactoryTests extends HandWrittenDateFactoryTests<OffsetDateTime> {

    @Test
    void newInstance_testOffsetStringFormat() {
        OffsetDateTimeFactory factory = new OffsetDateTimeFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue("2011-12-03T10:15:30+01:00");

        OffsetDateTime dt = factory.newInstance(OffsetDateTime.class, jsonObject, null);

        assertOffsetDateTime(dt);
    }

    @Test
    void newInstant_testMillisecondsFormat() {
        OffsetDateTimeFactory factory = new OffsetDateTimeFactory(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZoneId.of("UTC"));
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue(1699334240156L);

        OffsetDateTime dt = factory.newInstance(OffsetDateTime.class, jsonObject, null);

        assertThat(dt.getYear()).isEqualTo(2023);
        assertThat(dt.getMonthValue()).isEqualTo(11);
        assertThat(dt.getDayOfMonth()).isEqualTo(7);
        assertThat(dt.getHour()).isEqualTo(5);
        assertThat(dt.getMinute()).isEqualTo(17);
        assertThat(dt.getSecond()).isEqualTo(20);
        assertThat(dt.getNano()).isEqualTo(156000000);
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.of("Z"));
    }

    private static void assertOffsetDateTime(OffsetDateTime dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(10);
        assertThat(dt.getMinute()).isEqualTo(15);
        assertThat(dt.getSecond()).isEqualTo(30);
        assertThat(dt.getNano()).isZero();
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
    }

    @Test
    void newInstance_formattedDateTest() {
        OffsetDateTimeFactory factory = new OffsetDateTimeFactory(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZoneId.of("UTC"));
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("value", "2011-12-03T10:15:30");

        OffsetDateTime dt = factory.newInstance(OffsetDateTime.class, jsonObject, null);

        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(15);
        assertThat(dt.getMinute()).isEqualTo(15);
        assertThat(dt.getSecond()).isEqualTo(30);
        assertThat(dt.getNano()).isZero();
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("Z"));
    }

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new OffsetDateTimeFactory(DateTimeFormatter.ISO_OFFSET_DATE_TIME, ZoneId.of("Z"));
    }

    @Override
    protected Class<OffsetDateTime> getClassForFactory() {
        return OffsetDateTime.class;
    }


    @Override
    protected void assert_handWrittenDate_withNoTime(OffsetDateTime dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(5);
        assertThat(dt.getMinute()).isZero();
        assertThat(dt.getSecond()).isZero();
        assertThat(dt.getNano()).isZero();
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("Z"));
    }

    @Override
    protected void assert_handWrittenDate_withTime(OffsetDateTime dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(2);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(13);
        assertThat(dt.getMinute()).isEqualTo(9);
        assertThat(dt.getSecond()).isEqualTo(3);
        assertThat(dt.getNano()).isZero();
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("Z"));
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(OffsetDateTime dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(15);
        assertThat(dt.getMinute()).isEqualTo(15);
        assertThat(dt.getSecond()).isEqualTo(30);
        assertThat(dt.getNano()).isEqualTo(50000000);
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("Z"));
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(OffsetDateTime dt) {
        assertThat(dt.getYear()).isEqualTo(2011);
        assertThat(dt.getMonthValue()).isEqualTo(12);
        assertThat(dt.getDayOfMonth()).isEqualTo(3);
        assertThat(dt.getHour()).isEqualTo(15);
        assertThat(dt.getMinute()).isEqualTo(15);
        assertThat(dt.getSecond()).isEqualTo(30);
        assertThat(dt.getNano()).isZero();
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("Z"));
    }
}
