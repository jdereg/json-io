package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Maps mode reads a field of a date, number, UUID, enum or other non-referenceable class as the value Java mode
 * reads, also where the text writes it with its type ({@code showTypeInfoAlways()}, or a Timestamp in a Date field):
 * it read the typed object instead, holding the value -- and for a date's map form, {"@type":"Date","date":..},
 * holding a stray {@code "value"=null} beside its own key, the date never read. So the same object read differently
 * by how it was written; a BigDecimal written by TOON with its type kept its text's scale where Java mode did not.
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
class MapsModeTypedValueTest {
    /** X has a body: its class is a subclass of the enum. */
    public enum Fancy {
        X {
            @Override
            int weight() {
                return 1;
            }
        },
        Y;

        int weight() {
            return 0;
        }
    }

    public static class Values {
        public Date date = new Date(1700000000000L);
        public Date stamp = new Timestamp(1700000000123L);
        public ZonedDateTime zoned = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneId.of("America/New_York"));
        public Instant instant = Instant.ofEpochSecond(1700000000L);
        public LocalDate day = LocalDate.of(2026, 1, 2);
        public Calendar cal = new GregorianCalendar(2026, Calendar.JANUARY, 2);
        public TimeZone zone = TimeZone.getTimeZone("UTC");
        public UUID id = new UUID(1, 2);
        public BigDecimal dec = new BigDecimal("0.000020");
        public BigInteger big = BigInteger.TEN;
        public Number num = new BigDecimal("1.50");
        public Number nan = Float.NaN;
        public Class<?> type = String.class;
        public StringBuilder text = new StringBuilder("x");
        public Fancy fancy = Fancy.X;
        public Fancy plain = Fancy.Y;
    }

    private static final WriteOptions ALWAYS = new WriteOptionsBuilder().showTypeInfoAlways().build();
    private static final WriteOptions TOON_MINIMAL = new WriteOptionsBuilder().showTypeInfoMinimal().build();

    private static Map<?, ?> json(WriteOptions options) {
        return (Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(new Values(), options), null).asClass(null);
    }

    private static Map<?, ?> toon(WriteOptions options) {
        return (Map<?, ?>) JsonIo.fromToonToMaps(JsonIo.toToon(new Values(), options), null).asClass(null);
    }

    /** Each field's value is Java mode's, of its class, whatever type info the text carries. */
    @Test
    void eachFieldIsTheValueJavaModeReads() throws Exception {
        Values java = JsonIo.toJava(JsonIo.toJson(new Values(), ALWAYS), null).asClass(Values.class);
        String[] names = {"json always", "json minimal", "toon always", "toon minimal"};
        Map<?, ?>[] reads = {json(ALWAYS), json(null), toon(ALWAYS), toon(TOON_MINIMAL)};
        for (int r = 0; r < reads.length; r++) {
            for (Field field : Values.class.getFields()) {
                Object expected = field.get(java);
                Object actual = reads[r].get(field.getName());
                String where = names[r] + " " + field.getName() + ": " + actual;
                assertFalse(actual instanceof Map, where);
                if (expected == null) {
                    assertNull(actual, where);
                    continue;
                }
                assertSame(expected.getClass(), actual.getClass(), where);
                assertTrue(DeepEquals.deepEquals(expected, actual), where);
            }
        }
    }

    @Test
    void theTextsExactValues() {
        for (Map<?, ?> read : new Map<?, ?>[] {json(ALWAYS), toon(ALWAYS)}) {
            assertEquals(new Timestamp(1700000000123L), read.get("stamp"));
            assertEquals(new Date(1700000000000L), read.get("date"));
            assertEquals(new BigDecimal("0.000020"), read.get("dec"));      // its scale, as Java mode reads it
            assertEquals(new BigDecimal("1.50"), read.get("num"));
            assertSame(Fancy.X, read.get("fancy"));
            assertTrue(read.containsKey("nan"));
            assertNull(read.get("nan"));
        }
    }

    public static class Shared {
        public Date first;
        public Date second;
    }

    /** As Java mode reads them: a @ref, in either order, is the value it names, one for both fields. */
    @Test
    void aReferenceIsTheValueItNames() {
        String shared = Shared.class.getName();
        String[] texts = {
                "{\"@type\":\"" + shared + "\",\"first\":{\"@id\":1,\"@type\":\"Date\",\"date\":1700000000000},"
                        + "\"second\":{\"@ref\":1}}",
                "{\"@type\":\"" + shared + "\",\"second\":{\"@ref\":1},"
                        + "\"first\":{\"@id\":1,\"@type\":\"Date\",\"date\":1700000000000}}"};
        for (String text : texts) {
            Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(text, null).asClass(null);
            assertEquals(new Date(1700000000000L), read.get("first"), text);
            assertSame(read.get("first"), read.get("second"), text);
            Shared java = JsonIo.toJava(text, null).asClass(Shared.class);
            assertSame(java.first, java.second, text);
        }
    }

    /** Text json-io does not write reads as Java mode reads it too: by the field's type where the object's is not. */
    @Test
    void anotherTypeOrNoTypeReadsByTheField() {
        String values = Values.class.getName();
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps("{\"@type\":\"" + values + "\",\"date\":{\"@type\":\"Long\","
                + "\"value\":1700000000000},\"num\":{\"@type\":\"String\",\"value\":\"7\"},\"nan\":{\"value\":null}}",
                null).asClass(null);
        assertEquals(new Date(1700000000000L), read.get("date"));
        assertEquals(7L, read.get("num"));
        assertTrue(read.containsKey("nan"));
        assertNull(read.get("nan"));        // the Converter has no Number for a null
    }
}
