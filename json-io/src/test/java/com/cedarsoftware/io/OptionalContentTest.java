package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object where an {@code Optional} is declared is what the Optional holds -- {"x":6} for an {@code Optional<P>},
 * as Jackson's Jdk8Module writes it and as json-io does where it writes no type ({@code showTypeInfoNever()},
 * {@code standardJson()}) -- unless it is the form json-io writes under {@code writeOptionalAsObject()},
 * {"present":..,"value":..}. Such an object read as an empty Optional: json-io's own text lost the value.
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
class OptionalContentTest {
    public static class P {
        public int x = 6;

        @Override
        public boolean equals(Object o) {
            return o instanceof P && ((P) o).x == x;
        }

        @Override
        public int hashCode() {
            return x;
        }
    }

    /** A class with a field named as the wrapper form's keys, beside another. */
    public static class Flag {
        public boolean present = true;
        public String name = "n";

        @Override
        public boolean equals(Object o) {
            return o instanceof Flag && ((Flag) o).present == present && Objects.equals(((Flag) o).name, name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    public static class Holder {
        public Optional<P> pojo = Optional.of(new P());
        public Optional<Map<String, Integer>> map = Optional.of(map());
        public Optional<List<P>> list = Optional.of(new ArrayList<>(Arrays.asList(new P())));
        public List<Optional<P>> elements = new ArrayList<>(Arrays.asList(Optional.of(new P())));
        public Optional<String> text = Optional.of("s");
        public Optional<Integer> number = Optional.of(7);
        public Optional<P> empty = Optional.empty();
        public Optional<Flag> flag = Optional.of(new Flag());
    }

    private static Map<String, Integer> map() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("k", 1);
        return map;
    }

    private static void assertHolder(Holder back, String text) {
        Holder expected = new Holder();
        assertEquals(expected.pojo, back.pojo, text);
        assertEquals(expected.map, back.map, text);
        assertEquals(expected.list, back.list, text);
        assertEquals(expected.elements, back.elements, text);
        assertEquals(expected.text, back.text, text);
        assertEquals(expected.number, back.number, text);
        assertEquals(expected.empty, back.empty, text);
        assertEquals(expected.flag, back.flag, text);
    }

    @Test
    void jsonWithoutTypesReadsBack() {
        WriteOptions[] untyped = {new WriteOptionsBuilder().showTypeInfoNever().build(),
                new WriteOptionsBuilder().standardJson().build()};
        for (WriteOptions options : untyped) {
            String json = JsonIo.toJson(new Holder(), options);
            assertTrue(json.contains("\"pojo\":{\"x\":6}"), json);
            assertHolder(JsonIo.toJava(json, null).asClass(Holder.class), json);
        }
    }

    /** As Jackson's Jdk8Module writes it: the content, and null for an empty one. */
    @Test
    void jacksonsTextReads() {
        String jackson = "{\"pojo\":{\"x\":6},\"map\":{\"k\":1},\"list\":[{\"x\":6}],\"elements\":[{\"x\":6}],"
                + "\"text\":\"s\",\"number\":7,\"empty\":null,\"flag\":{\"present\":true,\"name\":\"n\"}}";
        assertHolder(JsonIo.toJava(jackson, null).asClass(Holder.class), jackson);
    }

    @Test
    void theOtherWaysStillRead() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().writeOptionalAsObject(true).build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(new Holder(), options);
            assertHolder(JsonIo.toJava(json, null).asClass(Holder.class), json);
            String toon = JsonIo.toToon(new Holder(), options);
            Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
            assertEquals(new Holder().pojo, back.pojo, toon);
            assertEquals(new Holder().flag, back.flag, toon);
            assertTrue(back.map.isPresent(), toon);
            assertEquals(1, ((Number) back.map.get().get("k")).intValue(), toon);
        }
    }

    /** The wrapper form: "present", with at most "value" beside it, or "value" alone, reads as before. */
    @Test
    void theWrapperFormIsTheWrapper() {
        String p = "{\"pojo\":";
        Holder h = JsonIo.toJava(p + "{\"present\":true,\"value\":{\"x\":9}}}", null).asClass(Holder.class);
        assertEquals(9, h.pojo.get().x);
        h = JsonIo.toJava(p + "{\"value\":{\"x\":9}}}", null).asClass(Holder.class);
        assertEquals(9, h.pojo.get().x);
        h = JsonIo.toJava(p + "{\"present\":false}}", null).asClass(Holder.class);
        assertFalse(h.pojo.isPresent());
        // an object of the content's own fields is the content, also one named as a wrapper key beside another
        h = JsonIo.toJava(p + "{\"x\":8}}", null).asClass(Holder.class);
        assertInstanceOf(P.class, h.pojo.get());
        assertEquals(8, h.pojo.get().x);
    }
}
