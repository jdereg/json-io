package com.cedarsoftware.io;

import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class WritersTest {
    @Test
    void testUnusedAPIs() throws Exception {
        Writers.JsonStringWriter jsw = new Writers.JsonStringWriter();
        jsw.write(8, false, new StringWriter(), null);
    }

    @Test
    void testDurationToJsonObject() {
        Duration d1 = Duration.parse("P2DT3H4M");                      // ISO-8601 string
        Duration d2 = Duration.ofMillis(183840000);                         // Same duration in millis
        Duration d3 = Duration.ofSeconds(183840, 1);  // Same duration plus 1 nano

        String json = JsonIo.toJson(d1, WriteOptionsBuilder.getDefaultWriteOptions());
        Duration dd1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Duration.class);

        json = JsonIo.toJson(d2, WriteOptionsBuilder.getDefaultWriteOptions());
        Duration dd2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Duration.class);

        json = JsonIo.toJson(d3, WriteOptionsBuilder.getDefaultWriteOptions());
        Duration dd3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Duration.class);
        
        assertEquals(d1, dd1);
        assertEquals(d2, dd2);
        assertEquals(d3, dd3);
    }

    @Test
    void testDurationToJsonString() {
        Duration d1 = Duration.parse("P2DT3H4M");                      // ISO-8601 string
        Duration d2 = Duration.ofMillis(183840000);                         // Same duration in millis
        Duration d3 = Duration.ofSeconds(183840, 1);  // Same duration plus 1 nano
        Duration[] durations = new Duration[] {d1, d2, d3};

        String json = JsonIo.toJson(durations, WriteOptionsBuilder.getDefaultWriteOptions());
        Duration[] durs = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Duration[].class);
        assertTrue(DeepEquals.deepEquals(durations, durs));
    }

    @Test
    void testTimestampToJsonObject() {
        // Create timestamps at different precisions
        Timestamp t1 = Timestamp.valueOf("2024-02-02 12:00:00");           // Second precision
        Timestamp t2 = Timestamp.from(Instant.parse("2024-02-02T12:00:00.123Z")); // Milli precision
        Timestamp t3 = new Timestamp(t1.getTime());
        t3.setNanos(123456789);                                           // Nano precision

        String json = JsonIo.toJson(t1, WriteOptionsBuilder.getDefaultWriteOptions());
        Timestamp tt1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Timestamp.class);

        json = JsonIo.toJson(t2, WriteOptionsBuilder.getDefaultWriteOptions());
        Timestamp tt2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Timestamp.class);

        json = JsonIo.toJson(t3, WriteOptionsBuilder.getDefaultWriteOptions());
        Timestamp tt3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Timestamp.class);

        assertEquals(t1, tt1);
        assertEquals(t2, tt2);
        assertEquals(t3, tt3);
    }

    @Test
    void testTimestampToJsonString() {
        // Create timestamps at different precisions
        Timestamp t1 = Timestamp.valueOf("2024-02-02 12:00:00");           // Second precision
        Timestamp t2 = Timestamp.from(Instant.parse("2024-02-02T12:00:00.123Z")); // Milli precision
        Timestamp t3 = new Timestamp(t1.getTime());
        t3.setNanos(123456789);                                           // Nano precision
        Timestamp[] timestamps = new Timestamp[] {t1, t2, t3};

        String json = JsonIo.toJson(timestamps, WriteOptionsBuilder.getDefaultWriteOptions());
        Timestamp[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Timestamp[].class);
        assertTrue(DeepEquals.deepEquals(timestamps, times));
    }

    @Test
    void testSqlDateToJsonObject() {
        // Create dates at different points in time
        java.sql.Date d1 = java.sql.Date.valueOf("2024-02-02");             // Current date
        java.sql.Date d2 = java.sql.Date.valueOf("1970-01-01");             // Epoch
        java.sql.Date d3 = java.sql.Date.valueOf("0001-01-01");             // Earliest valid SQL date

        String json = JsonIo.toJson(d1, WriteOptionsBuilder.getDefaultWriteOptions());
        java.sql.Date dd1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), java.sql.Date.class);

        json = JsonIo.toJson(d2, WriteOptionsBuilder.getDefaultWriteOptions());
        java.sql.Date dd2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), java.sql.Date.class);

        json = JsonIo.toJson(d3, WriteOptionsBuilder.getDefaultWriteOptions());
        java.sql.Date dd3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), java.sql.Date.class);

        assertEquals(d1, dd1);
        assertEquals(d2, dd2);
        assertEquals(d3, dd3);
    }

    @Test
    void testSqlDateToJsonString() {
        // Create dates at different points in time
        java.sql.Date d1 = java.sql.Date.valueOf("2024-02-02");             // Current date
        java.sql.Date d2 = java.sql.Date.valueOf("1970-01-01");             // Epoch
        java.sql.Date d3 = java.sql.Date.valueOf("0001-01-01");             // Earliest valid SQL date
        java.sql.Date[] dates = new java.sql.Date[] {d1, d2, d3};

        String json = JsonIo.toJson(dates, WriteOptionsBuilder.getDefaultWriteOptions());
        java.sql.Date[] sqlDates = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), java.sql.Date[].class);
        assertTrue(DeepEquals.deepEquals(dates, sqlDates));
    }

    @Test
    void testCalendarToJsonObject() {
        // Create calendars with different time zones and dates
        Calendar c1 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        c1.set(3, Calendar.FEBRUARY, 2, 12, 0, 0);
        c1.set(Calendar.MILLISECOND, 0);

        Calendar c2 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        c2.set(3, Calendar.JANUARY, 1, 0, 0, 0);
        c2.set(Calendar.MILLISECOND, 0);

        Calendar c3 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c3.set(3, Calendar.DECEMBER, 31, 23, 59, 59);
        c3.set(Calendar.MILLISECOND, 0);

        String json = JsonIo.toJson(c1, WriteOptionsBuilder.getDefaultWriteOptions());
        Calendar cc1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Calendar.class);

        json = JsonIo.toJson(c2, WriteOptionsBuilder.getDefaultWriteOptions());
        Calendar cc2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Calendar.class);

        json = JsonIo.toJson(c3, WriteOptionsBuilder.getDefaultWriteOptions());
        Calendar cc3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Calendar.class);

        assertEquals(c1.getTimeInMillis(), cc1.getTimeInMillis());
        assertEquals(c1.getTimeZone().getID(), cc1.getTimeZone().getID());
        assertEquals(c2.getTimeInMillis(), cc2.getTimeInMillis());
        assertEquals(c2.getTimeZone().getID(), cc2.getTimeZone().getID());
        assertEquals(c3.getTimeInMillis(), cc3.getTimeInMillis());
        assertEquals(c3.getTimeZone().getID(), cc3.getTimeZone().getID());
    }
    
    @Test
    void testCalendarToJsonString() {
        Calendar c1 = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        c1.set(3, Calendar.FEBRUARY, 2, 12, 0, 0);
        c1.set(Calendar.MILLISECOND, 0);

        Calendar c2 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        c2.set(3, Calendar.JANUARY, 1, 0, 0, 0);
        c2.set(Calendar.MILLISECOND, 0);

        Calendar c3 = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c3.set(3, Calendar.DECEMBER, 31, 23, 59, 59);
        c3.set(Calendar.MILLISECOND, 0);

        Calendar[] calendars = new Calendar[] {c1, c2, c3};

        String json = JsonIo.toJson(calendars, WriteOptionsBuilder.getDefaultWriteOptions());
        Calendar[] cals = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Calendar[].class);

        for (int i = 0; i < calendars.length; i++) {
            assertEquals(calendars[i].getTimeInMillis(), cals[i].getTimeInMillis());
            assertEquals(calendars[i].getTimeZone().getID(), cals[i].getTimeZone().getID());
        }
    }

    @Test
    void testDateToJsonObject() {
        // Create dates at different points in time
        Date d1 = new Date(318430800000L);        // 1980-02-02 12:00:00
        Date d2 = new Date(1706889600000L);       // 2024-02-02 12:00:00
        Date d3 = new Date(1767225599000L);       // 2025-12-31 23:59:59

        String json = JsonIo.toJson(d1, WriteOptionsBuilder.getDefaultWriteOptions());
        Date dd1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Date.class);

        json = JsonIo.toJson(d2, WriteOptionsBuilder.getDefaultWriteOptions());
        Date dd2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Date.class);

        json = JsonIo.toJson(d3, WriteOptionsBuilder.getDefaultWriteOptions());
        Date dd3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Date.class);

        assertEquals(d1.getTime(), dd1.getTime());
        assertEquals(d2.getTime(), dd2.getTime());
        assertEquals(d3.getTime(), dd3.getTime());
    }

    @Test
    void testDateToJsonString() {
        Date d1 = new Date(318430800000L);        // 1980-02-02 12:00:00
        Date d2 = new Date(1706889600000L);       // 2024-02-02 12:00:00
        Date d3 = new Date(1767225599000L);       // 2025-12-31 23:59:59

        Date[] dates = new Date[] {d1, d2, d3};

        String json = JsonIo.toJson(dates, WriteOptionsBuilder.getDefaultWriteOptions());
        Date[] newDates = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Date[].class);

        for (int i = 0; i < dates.length; i++) {
            assertEquals(dates[i].getTime(), newDates[i].getTime());
        }
    }

    @Test
    void testZonedDateTimeToJsonObject() {
        // Create ZonedDateTimes with different precisions and zones
        ZonedDateTime z1 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 0, ZoneId.of("UTC"));  // Second precision
        ZonedDateTime z2 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 123000000, ZoneId.of("America/New_York")); // Milli precision
        ZonedDateTime z3 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 123456789, ZoneId.of("Europe/London")); // Nano precision

        String json = JsonIo.toJson(z1, WriteOptionsBuilder.getDefaultWriteOptions());
        ZonedDateTime zz1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZonedDateTime.class);

        json = JsonIo.toJson(z2, WriteOptionsBuilder.getDefaultWriteOptions());
        ZonedDateTime zz2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZonedDateTime.class);

        json = JsonIo.toJson(z3, WriteOptionsBuilder.getDefaultWriteOptions());
        ZonedDateTime zz3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZonedDateTime.class);

        assertEquals(z1, zz1);
        assertEquals(z2, zz2);
        assertEquals(z3, zz3);
    }

    @Test
    void testZonedDateTimeToJsonString() {
        // Create ZonedDateTimes with different precisions and zones
        ZonedDateTime z1 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 0, ZoneId.of("UTC"));  // Second precision
        ZonedDateTime z2 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 123000000, ZoneId.of("America/New_York")); // Milli precision
        ZonedDateTime z3 = ZonedDateTime.of(2024, 2, 2, 12, 0, 0, 123456789, ZoneId.of("Europe/London")); // Nano precision
        ZonedDateTime[] zonedDateTimes = new ZonedDateTime[] {z1, z2, z3};

        String json = JsonIo.toJson(zonedDateTimes, WriteOptionsBuilder.getDefaultWriteOptions());
        ZonedDateTime[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZonedDateTime[].class);
        assertTrue(DeepEquals.deepEquals(zonedDateTimes, times));
    }

    @Test
    void testOffsetDateTimeToJsonObject() {
        // Create OffsetDateTimes with different precisions and offsets
        OffsetDateTime o1 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 0, ZoneOffset.UTC);  // Second precision
        OffsetDateTime o2 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 123000000, ZoneOffset.ofHours(-5)); // Milli precision
        OffsetDateTime o3 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 123456789, ZoneOffset.ofHours(1)); // Nano precision

        String json = JsonIo.toJson(o1, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetDateTime oo1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetDateTime.class);

        json = JsonIo.toJson(o2, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetDateTime oo2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetDateTime.class);

        json = JsonIo.toJson(o3, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetDateTime oo3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetDateTime.class);

        assertEquals(o1, oo1);
        assertEquals(o2, oo2);
        assertEquals(o3, oo3);
    }

    @Test
    void testOffsetDateTimeToJsonString() {
        // Create OffsetDateTimes with different precisions and offsets
        OffsetDateTime o1 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 0, ZoneOffset.UTC);  // Second precision
        OffsetDateTime o2 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 123000000, ZoneOffset.ofHours(-5)); // Milli precision
        OffsetDateTime o3 = OffsetDateTime.of(2024, 2, 2, 12, 0, 0, 123456789, ZoneOffset.ofHours(1)); // Nano precision
        OffsetDateTime[] offsetDateTimes = new OffsetDateTime[] {o1, o2, o3};

        String json = JsonIo.toJson(offsetDateTimes, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetDateTime[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetDateTime[].class);
        assertTrue(DeepEquals.deepEquals(offsetDateTimes, times));
    }

    @Test
    void testLocalDateTimeToJsonObject() {
        // Create LocalDateTimes with different precisions
        LocalDateTime l1 = LocalDateTime.of(2024, 2, 2, 12, 0, 0);  // Second precision
        LocalDateTime l2 = LocalDateTime.of(2024, 2, 2, 12, 0, 0, 123000000); // Milli precision
        LocalDateTime l3 = LocalDateTime.of(2024, 2, 2, 12, 0, 0, 123456789); // Nano precision

        String json = JsonIo.toJson(l1, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDateTime ll1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDateTime.class);

        json = JsonIo.toJson(l2, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDateTime ll2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDateTime.class);

        json = JsonIo.toJson(l3, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDateTime ll3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDateTime.class);

        assertEquals(l1, ll1);
        assertEquals(l2, ll2);
        assertEquals(l3, ll3);
    }

    @Test
    void testLocalDateTimeToJsonString() {
        // Create LocalDateTimes with different precisions
        LocalDateTime l1 = LocalDateTime.of(2024, 2, 2, 12, 0, 0);  // Second precision
        LocalDateTime l2 = LocalDateTime.of(2024, 2, 2, 12, 0, 0, 123000000); // Milli precision
        LocalDateTime l3 = LocalDateTime.of(2024, 2, 2, 12, 0, 0, 123456789); // Nano precision
        LocalDateTime[] localDateTimes = new LocalDateTime[] {l1, l2, l3};

        String json = JsonIo.toJson(localDateTimes, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDateTime[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDateTime[].class);
        assertTrue(DeepEquals.deepEquals(localDateTimes, times));
    }

    @Test
    void testLocalDateToJsonObject() {
        // Create LocalDates with different scenarios
        LocalDate l1 = LocalDate.of(2024, 2, 2);            // Current era date
        LocalDate l2 = LocalDate.of(-2024, 2, 2);           // BCE date
        LocalDate l3 = LocalDate.of(9999, 12, 31);          // Far future date

        String json = JsonIo.toJson(l1, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDate ll1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDate.class);

        json = JsonIo.toJson(l2, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDate ll2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDate.class);

        json = JsonIo.toJson(l3, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDate ll3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDate.class);

        assertEquals(l1, ll1);
        assertEquals(l2, ll2);
        assertEquals(l3, ll3);
    }

    @Test
    void testLocalDateToJsonString() {
        // Create LocalDates with different scenarios
        LocalDate l1 = LocalDate.of(2024, 2, 2);            // Current era date
        LocalDate l2 = LocalDate.of(-2024, 2, 2);           // BCE date
        LocalDate l3 = LocalDate.of(9999, 12, 31);          // Far future date
        LocalDate[] localDates = new LocalDate[] {l1, l2, l3};

        String json = JsonIo.toJson(localDates, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalDate[] dates = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalDate[].class);
        assertTrue(DeepEquals.deepEquals(localDates, dates));
    }

    @Test
    void testLocalTimeToJsonObject() {
        // Create LocalTimes with different precisions
        LocalTime t1 = LocalTime.of(12, 0, 0);              // Second precision
        LocalTime t2 = LocalTime.of(12, 0, 0, 123000000);   // Milli precision
        LocalTime t3 = LocalTime.of(12, 0, 0, 123456789);   // Nano precision

        String json = JsonIo.toJson(t1, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalTime tt1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalTime.class);

        json = JsonIo.toJson(t2, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalTime tt2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalTime.class);

        json = JsonIo.toJson(t3, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalTime tt3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalTime.class);

        assertEquals(t1, tt1);
        assertEquals(t2, tt2);
        assertEquals(t3, tt3);
    }

    @Test
    void testLocalTimeToJsonString() {
        // Create LocalTimes with different precisions
        LocalTime t1 = LocalTime.of(12, 0, 0);              // Second precision
        LocalTime t2 = LocalTime.of(12, 0, 0, 123000000);   // Milli precision
        LocalTime t3 = LocalTime.of(12, 0, 0, 123456789);   // Nano precision
        LocalTime[] localTimes = new LocalTime[] {t1, t2, t3};

        String json = JsonIo.toJson(localTimes, WriteOptionsBuilder.getDefaultWriteOptions());
        LocalTime[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), LocalTime[].class);
        assertTrue(DeepEquals.deepEquals(localTimes, times));
    }

    @Test
    void testOffsetTimeToJsonObject() {
        // Create OffsetTimes with different precisions and offsets
        OffsetTime t1 = OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC);             // Second precision
        OffsetTime t2 = OffsetTime.of(12, 0, 0, 123000000, ZoneOffset.ofHours(-5)); // Milli precision
        OffsetTime t3 = OffsetTime.of(12, 0, 0, 123456789, ZoneOffset.ofHours(1));  // Nano precision

        String json = JsonIo.toJson(t1, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetTime tt1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetTime.class);

        json = JsonIo.toJson(t2, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetTime tt2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetTime.class);

        json = JsonIo.toJson(t3, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetTime tt3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetTime.class);

        assertEquals(t1, tt1);
        assertEquals(t2, tt2);
        assertEquals(t3, tt3);
    }

    @Test
    void testOffsetTimeToJsonString() {
        // Create OffsetTimes with different precisions and offsets
        OffsetTime t1 = OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC);             // Second precision
        OffsetTime t2 = OffsetTime.of(12, 0, 0, 123000000, ZoneOffset.ofHours(-5)); // Milli precision
        OffsetTime t3 = OffsetTime.of(12, 0, 0, 123456789, ZoneOffset.ofHours(1));  // Nano precision
        OffsetTime[] offsetTimes = new OffsetTime[] {t1, t2, t3};

        String json = JsonIo.toJson(offsetTimes, WriteOptionsBuilder.getDefaultWriteOptions());
        OffsetTime[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), OffsetTime[].class);
        assertTrue(DeepEquals.deepEquals(offsetTimes, times));
    }

    @Test
    void testInstantToJsonObject() {
        // Create Instants with different precisions
        Instant i1 = Instant.parse("2024-02-02T12:00:00Z");                    // Second precision
        Instant i2 = Instant.parse("2024-02-02T12:00:00.123Z");               // Milli precision
        Instant i3 = Instant.parse("2024-02-02T12:00:00.123456789Z");         // Nano precision
        Instant i4 = Instant.parse("-999999999-01-01T00:00:00Z");             // Far past
        Instant i5 = Instant.parse("+999999999-12-31T23:59:59.999999999Z");   // Far future

        String json = JsonIo.toJson(i1, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant ii1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant.class);

        json = JsonIo.toJson(i2, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant ii2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant.class);

        json = JsonIo.toJson(i3, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant ii3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant.class);

        json = JsonIo.toJson(i4, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant ii4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant.class);

        json = JsonIo.toJson(i5, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant ii5 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant.class);

        assertEquals(i1, ii1);
        assertEquals(i2, ii2);
        assertEquals(i3, ii3);
        assertEquals(i4, ii4);
        assertEquals(i5, ii5);
    }

    @Test
    void testInstantToJsonString() {
        // Create Instants with different precisions
        Instant i1 = Instant.parse("2024-02-02T12:00:00Z");                     // Second precision
        Instant i2 = Instant.parse("2024-02-02T12:00:00.123Z");                 // Milli precision
        Instant i3 = Instant.parse("2024-02-02T12:00:00.123456789Z");           // Nano precision
        Instant i4 = Instant.parse("-999999999-01-01T00:00:00Z");                   // Far past
        Instant i5 = Instant.parse("+999999999-12-31T23:59:59.999999999Z");         // Far future
        Instant[] instants = new Instant[] {i1, i2, i3, i4, i5};

        String json = JsonIo.toJson(instants, WriteOptionsBuilder.getDefaultWriteOptions());
        Instant[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Instant[].class);
        assertTrue(DeepEquals.deepEquals(instants, times));
    }

    @Test
    void testMonthDayToJsonObject() {
        // Create MonthDays with different scenarios
        MonthDay md1 = MonthDay.of(12, 25);  // Christmas
        MonthDay md2 = MonthDay.of(2, 29);   // Leap day
        MonthDay md3 = MonthDay.of(7, 4);    // Independence Day
        MonthDay md4 = MonthDay.of(1, 1);    // New Year's Day (tests leading zeros)

        String json = JsonIo.toJson(md1, WriteOptionsBuilder.getDefaultWriteOptions());
        MonthDay mmd1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), MonthDay.class);

        json = JsonIo.toJson(md2, WriteOptionsBuilder.getDefaultWriteOptions());
        MonthDay mmd2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), MonthDay.class);

        json = JsonIo.toJson(md3, WriteOptionsBuilder.getDefaultWriteOptions());
        MonthDay mmd3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), MonthDay.class);

        json = JsonIo.toJson(md4, WriteOptionsBuilder.getDefaultWriteOptions());
        MonthDay mmd4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), MonthDay.class);

        assertEquals(md1, mmd1);
        assertEquals(md2, mmd2);
        assertEquals(md3, mmd3);
        assertEquals(md4, mmd4);
    }

    @Test
    void testMonthDayToJsonString() {
        // Create MonthDays with different scenarios
        MonthDay md1 = MonthDay.of(12, 25);  // Christmas
        MonthDay md2 = MonthDay.of(2, 29);   // Leap day
        MonthDay md3 = MonthDay.of(7, 4);    // Independence Day
        MonthDay md4 = MonthDay.of(1, 1);    // New Year's Day (tests leading zeros)
        MonthDay[] monthDays = new MonthDay[] {md1, md2, md3, md4};

        String json = JsonIo.toJson(monthDays, WriteOptionsBuilder.getDefaultWriteOptions());
        MonthDay[] days = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), MonthDay[].class);
        assertTrue(DeepEquals.deepEquals(monthDays, days));
    }

    @Test
    void testYearMonthToJsonObject() {
        // Create YearMonths with different scenarios
        YearMonth ym1 = YearMonth.of(2024, 3);             // Current year
        YearMonth ym2 = YearMonth.of(-2024, 12);           // BCE
        YearMonth ym3 = YearMonth.of(99999, 1);            // Far future
        YearMonth ym4 = YearMonth.of(2024, 1);             // Test leading zero month

        String json = JsonIo.toJson(ym1, WriteOptionsBuilder.getDefaultWriteOptions());
        YearMonth yym1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), YearMonth.class);

        json = JsonIo.toJson(ym2, WriteOptionsBuilder.getDefaultWriteOptions());
        YearMonth yym2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), YearMonth.class);

        json = JsonIo.toJson(ym3, WriteOptionsBuilder.getDefaultWriteOptions());
        YearMonth yym3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), YearMonth.class);

        json = JsonIo.toJson(ym4, WriteOptionsBuilder.getDefaultWriteOptions());
        YearMonth yym4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), YearMonth.class);

        assertEquals(ym1, yym1);
        assertEquals(ym2, yym2);
        assertEquals(ym3, yym3);
        assertEquals(ym4, yym4);
    }

    @Test
    void testYearMonthToJsonString() {
        // Create YearMonths with different scenarios
        YearMonth ym1 = YearMonth.of(2024, 3);             // Current year
        YearMonth ym2 = YearMonth.of(-2024, 12);           // BCE
        YearMonth ym3 = YearMonth.of(99999, 1);            // Far future
        YearMonth ym4 = YearMonth.of(2024, 1);             // Test leading zero month
        YearMonth[] yearMonths = new YearMonth[] {ym1, ym2, ym3, ym4};

        String json = JsonIo.toJson(yearMonths, WriteOptionsBuilder.getDefaultWriteOptions());
        YearMonth[] months = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), YearMonth[].class);
        assertTrue(DeepEquals.deepEquals(yearMonths, months));
    }

    @Test
    void testPeriodToJsonObject() {
        // Create Periods with different scenarios
        Period p1 = Period.of(1, 2, 3);             // Standard (years, months, days)
        Period p2 = Period.of(-1, 2, -3);           // Mixed positive/negative
        Period p3 = Period.of(0, 0, 5);             // Just days
        Period p4 = Period.of(2, 3, 0);             // No days
        Period p5 = Period.ofYears(5);              // Just years
        Period p6 = Period.ofMonths(14);            // Months > 12

        String json = JsonIo.toJson(p1, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        json = JsonIo.toJson(p2, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        json = JsonIo.toJson(p3, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        json = JsonIo.toJson(p4, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        json = JsonIo.toJson(p5, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp5 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        json = JsonIo.toJson(p6, WriteOptionsBuilder.getDefaultWriteOptions());
        Period pp6 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period.class);

        assertEquals(p1, pp1);
        assertEquals(p2, pp2);
        assertEquals(p3, pp3);
        assertEquals(p4, pp4);
        assertEquals(p5, pp5);
        assertEquals(p6, pp6);
    }

    @Test
    void testPeriodToJsonString() {
        // Create Periods with different scenarios
        Period p1 = Period.of(1, 2, 3);             // Standard (years, months, days)
        Period p2 = Period.of(-1, 2, -3);           // Mixed positive/negative
        Period p3 = Period.of(0, 0, 5);             // Just days
        Period p4 = Period.of(2, 3, 0);             // No days
        Period p5 = Period.ofYears(5);              // Just years
        Period p6 = Period.ofMonths(14);            // Months > 12
        Period[] periods = new Period[] {p1, p2, p3, p4, p5, p6};

        String json = JsonIo.toJson(periods, WriteOptionsBuilder.getDefaultWriteOptions());
        Period[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Period[].class);
        assertTrue(DeepEquals.deepEquals(periods, times));
    }

    @Test
    void testYearToJsonObject() {
        // Create Years with different scenarios
        Year y1 = Year.of(2024);              // Current era
        Year y2 = Year.of(-2024);             // BCE
        Year y3 = Year.of(99999);             // Far future
        Year y4 = Year.of(-99999);            // Far past
        Year y5 = Year.of(0);                 // Year zero (proleptic calendar)

        String json = JsonIo.toJson(y1, WriteOptionsBuilder.getDefaultWriteOptions());
        Year yy1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year.class);

        json = JsonIo.toJson(y2, WriteOptionsBuilder.getDefaultWriteOptions());
        Year yy2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year.class);

        json = JsonIo.toJson(y3, WriteOptionsBuilder.getDefaultWriteOptions());
        Year yy3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year.class);

        json = JsonIo.toJson(y4, WriteOptionsBuilder.getDefaultWriteOptions());
        Year yy4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year.class);

        json = JsonIo.toJson(y5, WriteOptionsBuilder.getDefaultWriteOptions());
        Year yy5 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year.class);

        assertEquals(y1, yy1);
        assertEquals(y2, yy2);
        assertEquals(y3, yy3);
        assertEquals(y4, yy4);
        assertEquals(y5, yy5);
    }

    @Test
    void testYearToJsonString() {
        // Create Years with different scenarios
        Year y1 = Year.of(2024);              // Current era
        Year y2 = Year.of(-2024);             // BCE
        Year y3 = Year.of(99999);             // Far future
        Year y4 = Year.of(-99999);            // Far past
        Year y5 = Year.of(0);                 // Year zero (proleptic calendar)
        Year[] years = new Year[] {y1, y2, y3, y4, y5};

        String json = JsonIo.toJson(years, WriteOptionsBuilder.getDefaultWriteOptions());
        Year[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), Year[].class);
        assertTrue(DeepEquals.deepEquals(years, times));
    }

    @Test
    void testZoneOffsetToJsonObject() {
        // Create ZoneOffsets with different scenarios
        ZoneOffset z1 = ZoneOffset.UTC;                    // UTC/Z
        ZoneOffset z2 = ZoneOffset.ofHours(5);            // Positive hours only
        ZoneOffset z3 = ZoneOffset.ofHours(-5);           // Negative hours only
        ZoneOffset z4 = ZoneOffset.ofHoursMinutes(5, 30); // Positive hours and minutes
        ZoneOffset z5 = ZoneOffset.ofHoursMinutes(-5, -30); // Negative hours and minutes
        ZoneOffset z6 = ZoneOffset.ofHoursMinutesSeconds(5, 30, 15); // Positive hours, minutes, seconds
        ZoneOffset z7 = ZoneOffset.ofHoursMinutesSeconds(-5, -30, -15); // Negative hours, minutes, seconds

        String json = JsonIo.toJson(z1, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz1 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z2, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz2 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z3, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz3 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z4, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz4 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z5, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz5 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z6, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz6 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        json = JsonIo.toJson(z7, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset zz7 = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset.class);

        assertEquals(z1, zz1);
        assertEquals(z2, zz2);
        assertEquals(z3, zz3);
        assertEquals(z4, zz4);
        assertEquals(z5, zz5);
        assertEquals(z6, zz6);
        assertEquals(z7, zz7);
    }

    @Test
    void testZoneOffsetToJsonString() {
        // Create ZoneOffsets with different scenarios
        ZoneOffset z1 = ZoneOffset.UTC;                    // UTC/Z
        ZoneOffset z2 = ZoneOffset.ofHours(5);            // Positive hours only
        ZoneOffset z3 = ZoneOffset.ofHours(-5);           // Negative hours only
        ZoneOffset z4 = ZoneOffset.ofHoursMinutes(5, 30); // Positive hours and minutes
        ZoneOffset z5 = ZoneOffset.ofHoursMinutes(-5, -30); // Negative hours and minutes
        ZoneOffset z6 = ZoneOffset.ofHoursMinutesSeconds(5, 30, 15); // Positive hours, minutes, seconds
        ZoneOffset z7 = ZoneOffset.ofHoursMinutesSeconds(-5, -30, -15); // Negative hours, minutes, seconds
        ZoneOffset[] offsets = new ZoneOffset[] {z1, z2, z3, z4, z5, z6, z7};

        String json = JsonIo.toJson(offsets, WriteOptionsBuilder.getDefaultWriteOptions());
        ZoneOffset[] times = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZoneOffset[].class);
        assertTrue(DeepEquals.deepEquals(offsets, times));
    }

    @Test
    void testGMTHandling() {
        // Test reading GMT and writing as Etc/GMT
        ZonedDateTime gmtInput = ZonedDateTime.parse("2024-02-02T12:00:00Z[GMT]");
        String json = JsonIo.toJson(gmtInput, WriteOptionsBuilder.getDefaultWriteOptions());
        ZonedDateTime restored = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), ZonedDateTime.class);

        // Instead of checking the JSON string directly, verify the restored object
        assertEquals("Etc/GMT", restored.getZone().getId());
        assertEquals(gmtInput.toInstant(), restored.toInstant());

        // Test that we can read both formats
        ZonedDateTime fromGMT = JsonIo.toObjects(
                "{\"@type\":\"ZonedDateTime\",\"zonedDateTime\":\"2024-02-02T12:00:00Z[GMT]\"}",
                ReadOptionsBuilder.getDefaultReadOptions(),
                ZonedDateTime.class
        );

        ZonedDateTime fromEtcGMT = JsonIo.toObjects(
                "{\"@type\":\"ZonedDateTime\",\"zonedDateTime\":\"2024-02-02T12:00:00Z[Etc/GMT]\"}",
                ReadOptionsBuilder.getDefaultReadOptions(),
                ZonedDateTime.class
        );

        // Verify both parse to the same instant and normalized zone
        assertEquals(fromGMT.toInstant(), fromEtcGMT.toInstant());
        assertEquals(fromGMT.getZone(), fromEtcGMT.getZone());
        assertEquals("Etc/GMT", fromGMT.getZone().getId());
    }
}