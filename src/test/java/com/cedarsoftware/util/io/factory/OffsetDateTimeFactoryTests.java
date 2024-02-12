package com.cedarsoftware.util.io.factory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DateUtilities;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReadOptionsBuilder;
import com.cedarsoftware.util.io.ReaderContext;

import static org.assertj.core.api.Assertions.assertThat;

class OffsetDateTimeFactoryTests extends HandWrittenDateFactoryTests<OffsetDateTime> {

    @Override
    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
        return new OffsetDateTimeFactory();
    }


    @Test
    void newInstance_testOffsetStringFormat() {
        OffsetDateTimeFactory factory = new OffsetDateTimeFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue("2011-12-03T10:15:30+01:00");

        ReaderContext context = new JsonReader(new ReadOptionsBuilder().build());
        OffsetDateTime dt = (OffsetDateTime) factory.newInstance(OffsetDateTime.class, jsonObject, context);

        assertOffsetDateTime(dt);
    }

    @Test
    void newInstant_testMillisecondsFormat() {
        OffsetDateTimeFactory factory = new OffsetDateTimeFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue(1699334240156L);

        ReaderContext context = new JsonReader(new ReadOptionsBuilder().setZoneId(ZoneId.of("America/New_York")).build());
        OffsetDateTime dt = (OffsetDateTime) factory.newInstance(OffsetDateTime.class, jsonObject, context);

        assertThat(dt.getYear()).isEqualTo(2023);
        assertThat(dt.getMonthValue()).isEqualTo(11);
        assertThat(dt.getDayOfMonth()).isEqualTo(7);
        assertThat(dt.getHour()).isEqualTo(0);
        assertThat(dt.getMinute()).isEqualTo(17);
        assertThat(dt.getSecond()).isEqualTo(20);
        assertThat(dt.getNano()).isEqualTo(156000000);
        assertThat(dt.getOffset()).isEqualTo(ZoneId.of("America/New_York").getRules().getOffset(dt.toLocalDateTime()));
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
    void newInstance_oldFormats_simpleCase() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.setJavaType(ClassUtilities.forName("java.time.OffsetDateTime", this.getClass().getClassLoader()));
        jsonObject.put("dateTime", "2019-12-15T09:07:16.000002");
        jsonObject.put("offset", "Z");

        ReaderContext context = new JsonReader(new ReadOptionsBuilder().build());
        OffsetDateTime actual = (OffsetDateTime) new OffsetDateTimeFactory().newInstance(OffsetDateTime.class, jsonObject, context);

        OffsetDateTime original = OffsetDateTime.of(LocalDateTime.parse("2019-12-15T09:07:16.000002", DateTimeFormatter.ISO_LOCAL_DATE_TIME), ZoneOffset.of("Z"));
        assertThat(actual).isEqualTo(original);
    }

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new OffsetDateTimeFactory();
    }

    @Override
    protected Class<OffsetDateTime> getClassForFactory() {
        return OffsetDateTime.class;
    }


    @Override
    protected void assert_handWrittenDate_withNoTime(OffsetDateTime dt) {
        Date date = DateUtilities.parseDate("2011-02-03");
        assertThat(date).isNotNull();

        Instant instant = Instant.ofEpochMilli(date.getTime());
        OffsetDateTime original = OffsetDateTime.from(instant.atZone(ZoneId.systemDefault()));

        assertThat(dt).isEqualTo(original);
    }

    @Override
    protected void assert_handWrittenDate_withTime(OffsetDateTime dt) {
        Date date = DateUtilities.parseDate("2011-02-03T08:09:03");
        assertThat(date).isNotNull();

        Instant instant = Instant.ofEpochMilli(date.getTime());
        OffsetDateTime original = OffsetDateTime.from(instant.atZone(ZoneId.systemDefault()));

        assertThat(dt).isEqualTo(original);
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(OffsetDateTime dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30.050-0500");
        assertThat(date).isNotNull();

        Instant instant = Instant.ofEpochMilli(date.getTime());
        OffsetDateTime original = OffsetDateTime.from(instant.atZone(ZoneId.systemDefault()));

        assertThat(dt).isEqualTo(original);
    }

    @Override
    protected void assert_handWrittenDate_inSaigon(OffsetDateTime dt) {
        Date date = DateUtilities.parseDate("2011-02-03 20:09:03");
        assertThat(date).isNotNull();

        Instant instant = Instant.ofEpochMilli(date.getTime());
        OffsetDateTime original = OffsetDateTime.from(instant.atZone(SAIGON_ZONE_ID));

        assertThat(dt).isEqualTo(original);
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(OffsetDateTime dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30");
        assertThat(date).isNotNull();

        Instant instant = Instant.ofEpochMilli(date.getTime());
        OffsetDateTime original = OffsetDateTime.from(instant.atZone(ZoneId.systemDefault()));

        assertThat(dt).isEqualTo(original);
    }
}
