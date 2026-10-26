package com.cedarsoftware.io;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A JsonObject writes an Integer, a Short, a Byte, a Float, a Character, an AtomicInteger with its type where no class
 * written with it declares the field's type -- as a LinkedHashMap and a List write one. It wrote each bare, so a Maps
 * mode rewrite lost the type of an object's value, a nested object's, an Optional's: it read back JSON's own Long,
 * Double or String (every release). Where the object's class declares the field ({@code int qty}) it is bare as before;
 * {@code showTypeInfoNever()} writes it bare as before.
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
class JsonObjectWritesValueTypesTest {
    public static class Line {
        public int qty;
        public Object any;
    }

    public static class Holder {
        public Object any;
    }

    private static final String[][] VALUES = {{"Integer", "5"}, {"Short", "5"}, {"Byte", "5"}, {"Float", "1.5"},
            {"Character", "\"x\""}, {"AtomicInteger", "5"}};

    /** A Maps-mode rewrite writes back the same text: at the root, nested, an Optional's value. */
    @Test
    void aMapsModeRewriteKeepsTheType() {
        for (String[] v : VALUES) {
            String typed = "{\"@type\":\"" + v[0] + "\",\"value\":" + v[1] + "}";
            String[] jsons = {"{\"v\":" + typed + "}", "{\"o\":{\"v\":" + typed + "}}",
                    "{\"o\":{\"@type\":\"Optional\",\"present\":true,\"value\":" + typed + "}}"};
            for (String json : jsons) {
                assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null), v[0]);
            }
        }
    }

    /** Read back in Java mode, an Optional's value is the type it was written as (it was a Long). */
    @Test
    void readBackAsWritten() {
        Holder holder = new Holder();
        holder.any = Optional.of(5);
        String rewritten = JsonIo.toJson(JsonIo.toMaps(JsonIo.toJson(holder, null), null).asClass(null), null);
        Holder back = JsonIo.toJava(rewritten, null).asClass(Holder.class);
        assertEquals(Optional.of(5), back.any);
        assertInstanceOf(Integer.class, ((Optional<?>) back.any).get());
        Map<?, ?> tree = JsonIo.toMaps(JsonIo.toJson(JsonIo.toMaps("{\"n\":{\"@type\":\"AtomicInteger\",\"value\":3}}", null)
                .asClass(null), null), null).asClass(Map.class);
        assertInstanceOf(AtomicInteger.class, tree.get("n"));
    }

    /** Where the class written with it declares the field, bare; a field of Object, with its type. */
    @Test
    void bareWhereTheClassDeclaresIt() {
        String json = "{\"@type\":\"" + Line.class.getName() + "\",\"qty\":3,\"any\":{\"@type\":\"Integer\",\"value\":4}}";
        assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null));
    }

    /** A JsonObject built by hand writes as a LinkedHashMap does; with no type info, bare. */
    @Test
    void aJsonObjectBuiltByHand() {
        JsonObject built = new JsonObject();
        built.put("n", 5);
        built.put("l", 7L);
        built.put("d", 2.5d);
        assertEquals("{\"n\":{\"@type\":\"Integer\",\"value\":5},\"l\":7,\"d\":2.5}", JsonIo.toJson(built, null));
        assertEquals("{\"n\":5,\"l\":7,\"d\":2.5}", JsonIo.toJson(built, new WriteOptionsBuilder().showTypeInfoNever().build()));
    }
}
