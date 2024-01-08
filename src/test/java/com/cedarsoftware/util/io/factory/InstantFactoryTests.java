package com.cedarsoftware.util.io.factory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import com.cedarsoftware.util.DateUtilities;
import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReadOptionsBuilder;
import com.cedarsoftware.util.io.ReaderContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantFactoryTests extends HandWrittenDateFactoryTests<Instant> {

    @Override
    protected JsonReader.ClassFactory createFactory() {
        return new InstantFactory();
    }

    @Override
    protected JsonReader.ClassFactory createFactory(ZoneId zoneId) {
//        return new InstantFactory(DateTimeFormatter.ISO_INSTANT, zoneId);
        return new ConvertableFactory() {
            @Override
            public Class<?> getType() {
                return Instant.class;
            }
        };
    }

    @Override
    protected Class<Instant> getClassForFactory() {
        return Instant.class;
    }

    @Override
    protected void assert_handWrittenDate_withNoZone(Instant dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30");

        assertThat(date).isNotNull();
        assertThat(dt).isEqualTo(date.toInstant());
    }

    @Override
    protected void assert_handWrittenDate_withNoTime(Instant dt) {
        Date date = DateUtilities.parseDate("2011-2-3");

        assertThat(date).isNotNull();
        assertThat(dt).isEqualTo(date.toInstant());
    }

    @Override
    protected void assert_handWrittenDate_withTime(Instant dt) {
        Date date = DateUtilities.parseDate("02/03/2011 08:09:03");

        assertThat(date).isNotNull();
        assertThat(dt).isEqualTo(date.toInstant());
    }

    @Override
    protected void assert_handWrittenDate_withMilliseconds(Instant dt) {
        Date date = DateUtilities.parseDate("2011-12-03T10:15:30.050-0500");

        assertThat(date).isNotNull();
        assertThat(dt).isEqualTo(date.toInstant());
    }

    @Override
    protected void assert_handWrittenDate_inSaigon(Instant dt) {
        Date date = DateUtilities.parseDate("2011-02-03T20:09:03");
        assertThat(date).isNotNull();

        Instant actual = date.toInstant();

        assertThat(actual).isEqualTo(dt);
    }

    @Test
    void newInstance_testOldFormat() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("seconds", 1700668272);
        jsonObject.put("nanos", 163000000);

        ReaderContext context = new JsonReader(new ReadOptionsBuilder().build());
        Instant actual = (Instant) createFactory().newInstance(Instant.class, jsonObject, context);

        assertThat(actual).isEqualTo(Instant.ofEpochSecond(1700668272, 163000000));
    }
}
