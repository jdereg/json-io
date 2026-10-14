package com.cedarsoftware.io;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code java.sql.Time} is a {@code Date}: {@code new Time(millis)} takes any instant, and JDBC's time of day is
 * the case where the date is 1970-01-01. json-io gives back the instant it was given (4.115.0).
 * <p>
 * With java-util 4.113.0 and 4.114.0 the date was lost wherever a Time travelled as text. Text that names a date
 * ("2020-06-28T01:00:00") was read as its time of day on 1970-01-01, where every release up to 4.112.0 had read the
 * instant; and a Time on another day was written "01:00:00" under {@code isoDateFormat()} and in TOON. Nothing
 * failed and nothing looked different: {@code Time.toString()} prints the time of day only, so the two Times print
 * the same and differ in {@code getTime()} and {@code equals()}.
 */
class SqlTimeKeepsItsDateTest {

    public static class Holder {
        public Time time;
        public Object any;
        public Time[] times;
        public List<Time> list;
        public Map<String, Object> map;
    }

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** 2020-06-28T01:00:00 in the JVM's zone: the value of the report. */
    private static final long REPORTED =
            LocalDateTime.parse("2020-06-28T01:00:00").atZone(ZONE).toInstant().toEpochMilli();

    private static WriteOptions[] everyDateFormat() {
        return new WriteOptions[]{
                new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().isoDateFormat().build(),
                new WriteOptionsBuilder().longDateFormat().build()};
    }

    private static Holder holderOf(Time time) {
        Holder holder = new Holder();
        holder.time = time;
        holder.any = time;
        holder.times = new Time[]{time};
        holder.list = new ArrayList<>();
        holder.list.add(time);
        holder.map = new LinkedHashMap<>();
        holder.map.put("t", time);
        return holder;
    }

    private static void assertIs(Object read, long millis, String where) {
        assertThat(read).as(where).isExactlyInstanceOf(Time.class);
        assertThat(((Time) read).getTime()).as(where).isEqualTo(millis);
    }

    @Test
    void aTimeWithADateComesBackFromJsonUnderEveryDateFormat() {
        Time time = new Time(REPORTED);
        for (WriteOptions writeOptions : everyDateFormat()) {
            String rootJson = JsonIo.toJson(time, writeOptions);
            assertIs(JsonIo.toJava(rootJson, null).asClass(Time.class), REPORTED, rootJson);

            String json = JsonIo.toJson(holderOf(time), writeOptions);
            Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
            assertIs(back.time, REPORTED, json);
            assertIs(back.any, REPORTED, json);
            assertIs(back.times[0], REPORTED, json);
            assertIs(back.list.get(0), REPORTED, json);
            assertIs(back.map.get("t"), REPORTED, json);
        }
        // isoDateFormat() writes text: the Time as the instant it is, the way a Date is written
        String iso = JsonIo.toJson(time, new WriteOptionsBuilder().isoDateFormat().build());
        String instant = Instant.ofEpochMilli(REPORTED).toString().replace("Z", ".000Z");      // whole seconds
        assertThat(iso).isEqualTo("{\"@type\":\"java.sql.Time\",\"date\":\"" + instant + "\"}");
    }

    @Test
    void aTimeWithADateComesBackFromToon() {
        Time time = new Time(REPORTED);
        for (WriteOptions writeOptions : everyDateFormat()) {
            String rootToon = JsonIo.toToon(time, writeOptions);
            assertIs(JsonIo.fromToon(rootToon, null).asClass(Time.class), REPORTED, rootToon);

            String toon = JsonIo.toToon(holderOf(time), writeOptions);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertIs(back.time, REPORTED, toon);
            assertIs(back.times[0], REPORTED, toon);
            assertIs(back.list.get(0), REPORTED, toon);
        }
    }

    @Test
    void textThatNamesADateIsReadAsItsInstant() {
        // A wire format that writes a Time as a local date-time, read with no custom reader
        String[] documents = {
                "{\"@type\":\"java.sql.Time\",\"value\":\"2020-06-28T01:00:00\"}",
                "{\"@type\":\"java.sql.Time\",\"date\":\"2020-06-28T01:00:00\"}",
                "{\"@type\":\"java.sql.Time\",\"sqlTime\":\"2020-06-28T01:00:00\"}",
                "{\"@type\":\"java.sql.Time\",\"value\":\"2020-06-28 01:00:00\"}",
                "{\"@type\":\"java.sql.Time\",\"value\":" + REPORTED + "}",
                "{\"@type\":\"java.sql.Time\",\"date\":" + REPORTED + "}"};
        for (String json : documents) {
            assertIs(JsonIo.toJava(json, null).asClass(Time.class), REPORTED, json);
            assertIs(JsonIo.toMaps(json, null).asClass(null), REPORTED, "Maps mode: " + json);
        }
        assertIs(JsonIo.toJava("\"2020-06-28T01:00:00\"", null).asClass(Time.class), REPORTED, "a bare string");
        Holder holder = JsonIo.toJava("{\"time\":\"2020-06-28T01:00:00\",\"times\":[\"2020-06-28T01:00:00\"],"
                + "\"any\":{\"@type\":\"java.sql.Time\",\"value\":\"2020-06-28T01:00:00\"}}", null).asClass(Holder.class);
        assertIs(holder.time, REPORTED, "field");
        assertIs(holder.times[0], REPORTED, "array element");
        assertIs(holder.any, REPORTED, "Object field");
        assertThat(holder.time.toString()).isEqualTo("01:00:00");

        assertIs(JsonIo.fromToon("time: \"2020-06-28T01:00:00\"", null).asClass(Holder.class).time, REPORTED, "TOON");

        // Text with a zone or an offset is that instant, whatever the reader's zone
        long utc = Instant.parse("2020-06-28T01:00:00Z").toEpochMilli();
        assertIs(JsonIo.toJava("{\"time\":\"2020-06-28T01:00:00Z\"}", null).asClass(Holder.class).time, utc, "Z");
        assertIs(JsonIo.toJava("{\"time\":\"2020-06-28T03:00:00+02:00\"}", null).asClass(Holder.class).time, utc,
                "offset");
    }

    @Test
    void aTimeOfDayIsWrittenAndReadAsBefore() {
        Time timeOfDay = new Time(Time.valueOf("13:14:15").getTime() + 123);
        WriteOptions iso = new WriteOptionsBuilder().isoDateFormat().build();
        assertThat(JsonIo.toJson(timeOfDay, iso)).isEqualTo("{\"@type\":\"java.sql.Time\",\"date\":\"13:14:15.123\"}");
        assertThat(JsonIo.toToon(timeOfDay, null)).isEqualTo("\"13:14:15.123\"");
        assertThat(JsonIo.toJson(timeOfDay, null))
                .isEqualTo("{\"@type\":\"java.sql.Time\",\"date\":" + timeOfDay.getTime() + "}");

        assertIs(JsonIo.toJava("{\"time\":\"13:14:15.123\"}", null).asClass(Holder.class).time, timeOfDay.getTime(),
                "time of day");
        assertIs(JsonIo.fromToon("time: \"13:14:15.123\"", null).asClass(Holder.class).time, timeOfDay.getTime(),
                "time of day, TOON");
    }

    @Test
    void everyTimeComesBack() {
        // 600 Times from a fixed seed, a third of them times of day: each comes back exactly from JSON under every
        // date format and from TOON. The oracle for the written form is the rule, with java.time finding the day.
        long startOfEpochDay = Time.valueOf("00:00:00").getTime();
        LocalDate epochDay = LocalDate.of(1970, 1, 1);
        WriteOptions iso = new WriteOptionsBuilder().isoDateFormat().build();
        Random random = new Random(4115L);
        int timesOfDay = 0;
        for (int i = 0; i < 600; i++) {
            long millis = i % 3 == 0
                    ? startOfEpochDay + random.nextInt(86_400_000)
                    : (long) ((random.nextDouble() - 0.5) * 2 * 100 * 365.25 * 86_400_000L);    // 1870 to 2070
            Time time = new Time(millis);
            for (WriteOptions writeOptions : everyDateFormat()) {
                String json = JsonIo.toJson(holderOf(time), writeOptions);
                Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
                assertIs(back.time, millis, json);
                assertIs(back.any, millis, json);
                assertIs(back.times[0], millis, json);
                assertIs(back.map.get("t"), millis, json);
            }
            String toon = JsonIo.toToon(holderOf(time), null);
            Holder fromToon = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertIs(fromToon.time, millis, toon);
            assertIs(fromToon.times[0], millis, toon);

            String isoJson = JsonIo.toJson(time, iso);              // {"@type":"java.sql.Time","date":"<text>"}
            String text = isoJson.substring(isoJson.indexOf("\"date\":\"") + 8, isoJson.length() - 2);
            if (Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().equals(epochDay)) {
                assertThat(text).startsWith(time.toString()).hasSizeLessThanOrEqualTo(12);
                timesOfDay++;
            } else {
                assertThat(Instant.parse(text).toEpochMilli()).isEqualTo(millis);
            }
        }
        assertThat(timesOfDay).isEqualTo(200);
    }
}
