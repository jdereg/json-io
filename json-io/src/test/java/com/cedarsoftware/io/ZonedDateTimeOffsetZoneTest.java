package com.cedarsoftware.io;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ZonedDateTime whose zone is an offset (+05:00, not a region) reads back, in a field, in an Object field or at the
 * root: json-io writes it as the offset alone (2026-01-15T10:00:00+05:00). The form json-io 4.x wrote,
 * {"zonedDateTime":"2026-01-15T10:00:00+05:00[+05:00]"}, it could not read ("Issue parsing date-time, other characters
 * present: [+05:00]") in every 4.x release; with java-util 4.118.0 it reads: the same instant and offset, in the zone
 * GMT+05:00 (as the text without the bracket gives where the Converter reads it).
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
class ZonedDateTimeOffsetZoneTest {
    public static class Meeting {
        public ZonedDateTime at;
        public Object any;
    }

    @Test
    void anOffsetZoneReadsBack() {
        for (String offset : new String[] {"+05:00", "-03:30", "+05:45", "+14:00"}) {
            ZonedDateTime at = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.of(offset));
            String json = JsonIo.toJson(at, null);
            assertTrue(json.contains("10:00:00" + offset + "\""), json);
            ZonedDateTime back = JsonIo.toJava(json, null).asClass(ZonedDateTime.class);
            assertEquals(at.toInstant(), back.toInstant(), json);
            assertEquals(at.getOffset(), back.getOffset(), json);

            Meeting meeting = new Meeting();
            meeting.at = at;
            meeting.any = at;
            Meeting read = JsonIo.toJava(JsonIo.toJson(meeting, null), null).asClass(Meeting.class);
            assertEquals(at.toInstant(), read.at.toInstant());
            assertEquals(at.toInstant(), ((ZonedDateTime) read.any).toInstant());
            Map<?, ?> tree = JsonIo.toMaps(JsonIo.toJson(meeting, null), null).asClass(Map.class);
            assertEquals(at.toInstant(), ((ZonedDateTime) tree.get("any")).toInstant());

            String written4x = "{\"@type\":\"ZonedDateTime\",\"zonedDateTime\":\"2026-01-15T10:00:00" + offset + "["
                    + offset + "]\"}";
            ZonedDateTime old = JsonIo.toJava(written4x, null).asClass(ZonedDateTime.class);
            assertEquals(at.toInstant(), old.toInstant(), written4x);
            assertEquals(at.getOffset(), old.getOffset(), written4x);
        }
    }
}
