package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code showTypeInfoAlways()} writes {@code @type} on every object, and on an element of a declared collection or
 * map too, as it does on a field and an array element of the same declared type: a {@code List<Point>}'s elements,
 * a {@code Map<String, Point>}'s values and keys, a {@code List<UUID>}, a {@code List<LocalDate>}, a list of an
 * {@code @IoValue} class ({@code {"@type":"Email","value":"a@x.com"}}). Up to now such an element was written as in
 * minimal mode, without its type, when its class was the declared one. A String, Long, Double, Boolean, and an
 * element declared as a primitive wrapper ({@code List<Integer>}), are written bare, as in an array.
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
class AlwaysTypesElementsTest {
    public static class Point {
        public int x = 1;

        @Override
        public boolean equals(Object o) {
            return o instanceof Point && ((Point) o).x == x;
        }

        @Override
        public int hashCode() {
            return x;
        }
    }

    public static class Email {
        private final String v;

        @IoCreator
        public Email(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Email && ((Email) o).v.equals(v);
        }

        @Override
        public int hashCode() {
            return v.hashCode();
        }
    }

    public enum Shade { LIGHT, DARK }

    public static class Holder {
        public List<Shade> shades;
        public List<Point> points;
        public Point[] pointArray;
        public Map<String, Point> pointValues;
        public Map<Point, String> pointKeys;
        public List<Email> emails;
        public Set<Email> emailSet;
        public Map<String, Email> emailValues;
        public List<UUID> uuids;
        public List<LocalDate> dates;
        public List<BigDecimal> decimals;
        public List<List<Point>> nested;
        public List<Integer> ints;
        public List<Long> longs;
        public List<String> strings;
        public Integer[] intArray;
    }

    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private static Holder holder() {
        Holder h = new Holder();
        h.shades = new ArrayList<>(Collections.singletonList(Shade.DARK));
        h.points = new ArrayList<>(Collections.singletonList(new Point()));
        h.pointArray = new Point[] {new Point()};
        h.pointValues = new LinkedHashMap<>();
        h.pointValues.put("k", new Point());
        h.pointKeys = new LinkedHashMap<>();
        h.pointKeys.put(new Point(), "v");
        h.emails = new ArrayList<>(Collections.singletonList(new Email("a@x.com")));
        h.emailSet = new LinkedHashSet<>(Collections.singletonList(new Email("b@x.com")));
        h.emailValues = new LinkedHashMap<>();
        h.emailValues.put("k", new Email("c@x.com"));
        h.uuids = new ArrayList<>(Collections.singletonList(ID));
        h.dates = new ArrayList<>(Collections.singletonList(LocalDate.of(2026, 1, 15)));
        h.decimals = new ArrayList<>(Collections.singletonList(new BigDecimal("1.50")));
        h.nested = new ArrayList<>(Collections.singletonList(new ArrayList<>(Collections.singletonList(new Point()))));
        h.ints = new ArrayList<>(Collections.singletonList(7));
        h.longs = new ArrayList<>(Collections.singletonList(8L));
        h.strings = new ArrayList<>(Collections.singletonList("s"));
        h.intArray = new Integer[] {9};
        return h;
    }

    private static String always(Object o) {
        return JsonIo.toJson(o, new WriteOptionsBuilder().showTypeInfoAlways().build());
    }

    @Test
    void everyElementCarriesItsType() {
        String json = always(holder());
        String point = "{\"@type\":\"" + Point.class.getName() + "\",\"x\":1}";
        String email = "{\"@type\":\"" + Email.class.getName() + "\",\"value\":\"";
        assertTrue(json.contains("\"points\":{\"@type\":\"ArrayList\",\"@items\":[" + point + "]}"), json);
        assertTrue(json.contains("\"@items\":[" + point + "]},\"pointValues\""), json);    // the array, as before
        assertTrue(json.contains("\"pointValues\":{\"@type\":\"LinkedHashMap\",\"k\":" + point + "}"), json);
        assertTrue(json.contains("\"pointKeys\":{\"@type\":\"LinkedHashMap\",\"@keys\":[" + point + "]"), json);
        assertTrue(json.contains("\"emails\":{\"@type\":\"ArrayList\",\"@items\":[" + email + "a@x.com\"}]}"), json);
        assertTrue(json.contains("\"@items\":[" + email + "b@x.com\"}]}"), json);
        assertTrue(json.contains("\"emailValues\":{\"@type\":\"LinkedHashMap\",\"k\":" + email + "c@x.com\"}}"), json);
        assertTrue(json.contains("\"uuids\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"UUID\",\"value\":\"" + ID
                + "\"}]}"), json);
        assertTrue(json.contains("\"dates\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"LocalDate\""), json);
        assertTrue(json.contains("\"decimals\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"BigDecimal\","
                + "\"value\":\"1.50\"}]}"), json);
        assertTrue(json.contains("\"nested\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"ArrayList\",\"@items\":["
                + point + "]}]}"), json);
    }

    @Test
    void nativeAndWrapperElementsStayBare() {
        String json = always(holder());
        assertTrue(json.contains("\"ints\":{\"@type\":\"ArrayList\",\"@items\":[7]}"), json);
        assertTrue(json.contains("\"longs\":{\"@type\":\"ArrayList\",\"@items\":[8]}"), json);
        assertTrue(json.contains("\"strings\":{\"@type\":\"ArrayList\",\"@items\":[\"s\"]}"), json);
        assertTrue(json.contains("\"intArray\":{\"@type\":\"Integer[]\",\"@items\":[9]}"), json);
    }

    @Test
    void readsBackAsWritten() {
        Holder h = holder();
        String json = always(h);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        Map<String, Object> options = new HashMap<>();
        assertTrue(DeepEquals.deepEquals(h, back, options), () -> json + "\n" + options.get("diff"));
        assertEquals(Arrays.asList(new Email("a@x.com")), back.emails);
    }

    /** The other modes are unchanged: the declared element type is the element's, so no type. */
    @Test
    void minimalIsUnchanged() {
        String json = JsonIo.toJson(holder(), null);
        assertTrue(json.contains("\"points\":[{\"x\":1}]"), json);
        assertTrue(json.contains("\"emails\":[\"a@x.com\"]"), json);
        assertTrue(json.contains("\"uuids\":[\"" + ID + "\"]"), json);
        assertTrue(json.contains("\"pointValues\":{\"k\":{\"x\":1}}"), json);
        assertTrue(json.contains("\"shades\":[{\"@type\":\"" + Shade.class.getName() + "\""), json);   // as before
    }
}
