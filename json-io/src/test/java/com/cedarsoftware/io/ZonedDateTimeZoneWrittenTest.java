package com.cedarsoftware.io;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ZonedDateTime is written as ISO-8601 writes it ({@code DateTimeFormatter.ISO_ZONED_DATE_TIME}, its
 * {@code toString()}, the Converter's text): a zone that is an offset is the offset alone -- {@code Z},
 * {@code +05:00} -- and a region follows in brackets, {@code -05:00[America/New_York]}, {@code Z[UTC]}. The zone Z was
 * written {@code Z[UTC]}, so it read back in the region UTC: the same instant, but not equal to what was written; an
 * offset was written {@code +05:00[+05:00]}.
 *
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
class ZonedDateTimeZoneWrittenTest {
    public static class Meeting {
        public ZonedDateTime at;
        public Object any;
        public List<Object> list = new ArrayList<>();
    }

    private static final ZoneId[] ZONES = {ZoneOffset.UTC, ZoneOffset.of("+05:00"), ZoneOffset.of("-03:30"),
            ZoneId.of("UTC"), ZoneId.of("GMT"), ZoneId.of("GMT+05:00"), ZoneId.of("America/New_York"),
            ZoneId.of("Asia/Kolkata")};

    private static ZonedDateTime at(ZoneId zone) {
        return ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, zone);
    }

    @Test
    void writtenAsIsoWritesIt() {
        Converter converter = new Converter(new DefaultConverterOptions());
        for (ZoneId zone : ZONES) {
            ZonedDateTime at = at(zone);
            String text = converter.convert(at, String.class);
            assertEquals(at.toString().replace("T10:00", "T10:00:00"), text, zone.getId());
            assertEquals(text, written(zone), zone.getId());
        }
        assertEquals("2026-01-15T10:00:00Z", written(ZoneOffset.UTC));
        assertEquals("2026-01-15T10:00:00+05:00", written(ZoneOffset.of("+05:00")));
        assertEquals("2026-01-15T10:00:00Z[UTC]", written(ZoneId.of("UTC")));
        assertEquals("2026-01-15T10:00:00-05:00[America/New_York]", written(ZoneId.of("America/New_York")));

        // a year past 9999, written by the formatter
        Meeting far = new Meeting();
        far.at = ZonedDateTime.of(12026, 1, 15, 10, 0, 0, 0, ZoneOffset.of("+05:00"));
        String json = JsonIo.toJson(far, null);
        assertTrue(json.contains("\"at\":\"+12026-01-15T10:00:00+05:00\""), json);
        assertEquals(far.at, JsonIo.toJava(json, null).asClass(Meeting.class).at);
    }

    /** The text a typed field holds for 2026-01-15T10:00 in the zone. */
    private static String written(ZoneId zone) {
        String json = JsonIo.toJson(meeting(zone), null);
        int start = json.indexOf("\"at\":\"") + 6;
        return json.substring(start, json.indexOf('"', start));
    }

    /** The zone Z reads back as Z wherever it stands: in a typed field, an Object field, a list, at the root, a tree. */
    @Test
    void zReadsBackAsZ() {
        ZonedDateTime at = at(ZoneOffset.UTC);
        Meeting meeting = new Meeting();
        meeting.at = at;
        meeting.any = at;
        meeting.list.add(at);
        String json = JsonIo.toJson(meeting, null);
        assertFalse(json.contains("[UTC]"), json);
        Meeting read = JsonIo.toJava(json, null).asClass(Meeting.class);
        assertEquals(at, read.at);
        assertEquals(at, read.any);
        assertEquals(at, read.list.get(0));
        assertEquals(at, JsonIo.toJava(JsonIo.toJson(at, null), null).asClass(ZonedDateTime.class));
        Map<?, ?> tree = JsonIo.toMaps(json, null).asClass(Map.class);
        Meeting again = JsonIo.toJava(JsonIo.toJson(tree, null), null).asClass(Meeting.class);
        assertEquals(at, again.at);
        assertEquals(at, again.any);
        assertEquals(at, again.list.get(0));
    }

    /** In a typed field each zone reads back as written, the offset alone included. */
    @Test
    void aTypedFieldReadsBackItsZone() {
        for (ZoneId zone : ZONES) {
            Meeting meeting = meeting(zone);
            Meeting read = JsonIo.toJava(JsonIo.toJson(meeting, null), null).asClass(Meeting.class);
            assertEquals(meeting.at, read.at, zone.getId());
        }
    }

    private static Meeting meeting(ZoneId zone) {
        Meeting meeting = new Meeting();
        meeting.at = at(zone);
        return meeting;
    }
}
