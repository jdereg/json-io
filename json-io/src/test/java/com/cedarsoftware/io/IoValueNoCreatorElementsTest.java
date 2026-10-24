package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class written as the single value its {@code @IoValue} method returns, that nothing builds from that value (no
 * one-parameter {@code @IoCreator}, no constructor taking it), is an error wherever it is read from the value: in a
 * list, a set, a map's keys and values, an array, an {@code Optional}, as in a field and at the root, as Jackson
 * throws for each. Up to now a list, a set, a map and an {@code Optional} held the bare String in its place, so the
 * read "worked" and the first use of an element threw ClassCastException; an array threw "java.lang.String".
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
class IoValueNoCreatorElementsTest {
    /** Written as its text; nothing builds it from the text. */
    public static class Mail {
        private String v;

        public Mail() {
        }

        static Mail of(String v) {
            Mail m = new Mail();
            m.v = v;
            return m;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    /** Written as a number; its only constructor takes text, not the number. */
    public static class Code {
        private final int n;

        public Code(String n) {
            this.n = Integer.parseInt(n);
        }

        @IoValue
        public int n() {
            return n;
        }
    }

    /** The same, with a creator: reads back everywhere. */
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
    }

    public static class Holder {
        public List<Mail> list;
        public Set<Mail> set;
        public Map<String, Mail> values;
        public Map<Mail, String> keys;
        public Mail[] array;
        public Optional<Mail> opt;
        public List<List<Mail>> nested;
        public List<Code> codes;
    }

    public static class EmailHolder {
        public List<Email> list;
        public Set<Email> set;
        public Map<String, Email> values;
        public Map<Email, String> keys;
        public Email[] array;
        public Optional<Email> opt;
        public List<List<Email>> nested;
    }

    private static void assertExplains(Class<?> c, Executable read) {
        JsonIoException e = assertThrows(JsonIoException.class, read);
        String message = e.getMessage();
        assertTrue(message.contains(c.getName() + " is written as the single value its @IoValue method"), message);
        assertTrue(message.contains("takes an @IoCreator with one parameter or a constructor that takes a"), message);
    }

    private static Executable json(String json) {
        return () -> JsonIo.toJava(json, null).asClass(Holder.class);
    }

    private static Executable toon(String toon) {
        return () -> JsonIo.fromToon(toon, null).asClass(Holder.class);
    }

    @Test
    void eachPlaceExplainsAsAFieldDoes() {
        String[] places = {"{\"list\":[\"a\"]}", "{\"set\":[\"a\"]}", "{\"values\":{\"k\":\"a\"}}", "{\"keys\":{\"a\":\"v\"}}",
                "{\"array\":[\"a\"]}", "{\"opt\":\"a\"}", "{\"nested\":[[\"a\"]]}", "{\"list\":[null,\"a\"]}"};
        for (String place : places) {
            assertExplains(Mail.class, json(place));
        }
        assertExplains(Code.class, json("{\"codes\":[7]}"));
    }

    @Test
    void toonToo() {
        String[] places = {"list[1]: a", "set[1]: a", "values:\n  k: a", "array[1]: a", "opt: a", "nested[1]:\n  - [1]: a",
                "codes[1]: 7"};
        for (String place : places) {
            assertExplains(place.startsWith("codes") ? Code.class : Mail.class, toon(place));
        }
    }

    @Test
    void atTheRoot() {
        assertExplains(Mail.class, () -> JsonIo.toJava("[\"a\"]", null).asType(new TypeHolder<List<Mail>>() {}));
        assertExplains(Mail.class, () -> JsonIo.toJava("[\"a\"]", null).asClass(Mail[].class));
        assertExplains(Mail.class, () -> JsonIo.toJava("\"a\"", null).asType(new TypeHolder<Optional<Mail>>() {}));
        assertExplains(Mail.class, () -> JsonIo.fromToon("[1]: a", null).asType(new TypeHolder<List<Mail>>() {}));
    }

    /** What json-io writes for such a class does not read back, and now says so rather than reading Strings. */
    @Test
    void whatJsonIoWritesSaysWhy() {
        Holder h = new Holder();
        h.list = new ArrayList<>(Collections.singletonList(Mail.of("a@x.com")));
        String json = JsonIo.toJson(h, null);
        assertTrue(json.contains("\"list\":[\"a@x.com\"]"), json);
        assertExplains(Mail.class, json(json));
        String toon = JsonIo.toToon(h, null);
        assertExplains(Mail.class, toon(toon));
    }

    /** A null element, and the empty forms, read as before; the value form of a class with a creator reads. */
    @Test
    void nullsAndBuildableClassesRead() {
        Holder h = JsonIo.toJava("{\"list\":[null],\"opt\":null,\"array\":[null],\"values\":{\"k\":null}}", null)
                .asClass(Holder.class);
        assertEquals(Collections.singletonList(null), h.list);
        assertEquals(Optional.empty(), h.opt);
        assertNull(h.array[0]);
        assertTrue(h.values.containsKey("k") && h.values.get("k") == null);

        EmailHolder e = JsonIo.toJava("{\"list\":[\"a\"],\"set\":[\"a\"],\"values\":{\"k\":\"a\"},\"keys\":{\"a\":\"v\"},"
                + "\"array\":[\"a\"],\"opt\":\"a\",\"nested\":[[\"a\"]]}", null).asClass(EmailHolder.class);
        assertEquals("a", e.list.get(0).v());
        assertEquals("a", e.set.iterator().next().v());
        assertEquals("a", e.values.get("k").v());
        assertEquals("a", e.keys.keySet().iterator().next().v());
        assertEquals("a", e.array[0].v());
        assertEquals("a", e.opt.get().v());
        assertEquals("a", e.nested.get(0).get(0).v());
        List<Email> root = JsonIo.toJava("[\"a\",\"b\"]", null).asType(new TypeHolder<List<Email>>() {});
        assertEquals(Arrays.asList("a", "b"), Arrays.asList(root.get(0).v(), root.get(1).v()));
    }

    /** Maps mode builds no classes: the value stays the value. */
    @Test
    void mapsModeIsUnchanged() {
        Map<?, ?> back = JsonIo.toMaps("{\"@type\":\"" + Holder.class.getName() + "\",\"list\":[\"a\"]}", null)
                .asClass(null);
        assertEquals(Collections.singletonList("a"), Arrays.asList((Object[]) back.get("list")));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k", Mail.of("a"));
        Set<Object> set = new LinkedHashSet<>(Collections.singletonList(Mail.of("b")));
        assertEquals("{\"k\":\"a\"}", JsonIo.toJson(map, new WriteOptionsBuilder().showTypeInfoNever().build()));
        assertEquals("[\"b\"]", JsonIo.toJson(set, new WriteOptionsBuilder().showTypeInfoNever().build()));
    }
}
