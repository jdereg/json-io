package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class OffsetDateTimeFactoryTests {

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
        assertThat(dt.getNano()).isEqualTo(0);
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
        assertThat(dt.getHour()).isEqualTo(10);
        assertThat(dt.getMinute()).isEqualTo(15);
        assertThat(dt.getSecond()).isEqualTo(30);
        assertThat(dt.getNano()).isEqualTo(0);
        assertThat(dt.getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }
}
