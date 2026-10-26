package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tree read from plain JSON -- JSON that names no type -- writes its big numbers back as the JSON numbers they were:
 * {@code 123456789012345678901234567890} (a BigInteger) and, read with {@code floatPointBigDecimal()}, {@code 1.10}
 * (a BigDecimal), in an object, in an array, nested. They were written {@code {"@type":"BigInteger","value":".."}}
 * (or as a JSON string with no type info), so a service passing JSON through changed every such number's shape for
 * whoever read it next -- a column of numbers became objects or text. JSON that names a type was written by json-io,
 * and its typed values come back typed, as before; so does a JsonObject built by hand.
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
class MapsModeBigNumbersTest {
    private static final String BIG = "123456789012345678901234567890";
    private static final String[] PLAIN = {
            "{\"id\":" + BIG + ",\"amt\":1.10,\"qty\":3,\"rate\":0.5}",
            "{\"vals\":[1.10," + BIG + ",3],\"m\":{\"x\":1.10,\"y\":-" + BIG + "},"
                    + "\"deep\":[[2.50,[0.000,0.0000000001]]]}",
            "{\"rows\":[{\"amt\":1.10},{\"amt\":2.25}],\"id\":{\"@id\":1,\"n\":" + BIG + "},\"again\":{\"@ref\":1}}"};

    public static class Priced {
        public Object amount = new BigDecimal("1.10");
    }

    private static final ReadOptions DECIMALS = new ReadOptionsBuilder().floatPointBigDecimal().build();
    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();

    @Test
    void plainJsonWritesBackAsItWasRead() {
        for (String json : PLAIN) {
            Object tree = JsonIo.toMaps(json, DECIMALS).asClass(null);
            assertEquals(json, JsonIo.toJson(tree, null));
            assertEquals(json, JsonIo.toJson(tree, NEVER));
        }
        // read as JSON's own numbers: the big integers still a BigInteger, the decimals Doubles
        Object tree = JsonIo.toMaps(PLAIN[0], null).asClass(null);
        assertEquals("{\"id\":" + BIG + ",\"amt\":1.1,\"qty\":3,\"rate\":0.5}", JsonIo.toJson(tree, null));
    }

    /** Read back, each is the number it was. */
    @Test
    void readBackTheSameNumbers() {
        Map<?, ?> tree = JsonIo.toMaps(PLAIN[1], DECIMALS).asClass(Map.class);
        Map<?, ?> again = JsonIo.toMaps(JsonIo.toJson(tree, null), DECIMALS).asClass(Map.class);
        Object[] vals = (Object[]) again.get("vals");
        assertEquals(new BigDecimal("1.10"), vals[0]);
        assertEquals(new BigInteger(BIG), vals[1]);
        assertEquals(new BigInteger("-" + BIG), ((Map<?, ?>) again.get("m")).get("y"));
        assertEquals(new BigDecimal("0.000"), ((Object[]) ((Object[]) ((Object[]) again.get("deep"))[0])[1])[0]);
    }

    /** TOON reads a number as the smallest type that holds it exactly: a BigInteger, a BigDecimal past a double. */
    @Test
    void plainToonWritesBackAsJsonNumbers() {
        String pi = "3.14159265358979323846264338327950288";
        Object tree = JsonIo.fromToonToMaps("id: " + BIG + "\npi: " + pi + "\nvals[2]: 3," + BIG + "\n", null)
                .asClass(null);
        String json = "{\"id\":" + BIG + ",\"pi\":" + pi + ",\"vals\":[3," + BIG + "]}";
        assertEquals(json, JsonIo.toJson(tree, NEVER));
    }

    /** JSON that names a type was written by json-io: a typed value comes back typed, as before. */
    @Test
    void typedJsonStaysTyped() {
        String json = "{\"@type\":\"LinkedHashMap\",\"amt\":{\"@type\":\"BigDecimal\",\"value\":\"1.50\"},"
                + "\"n\":{\"@type\":\"BigInteger\",\"value\":\"" + BIG + "\"}}";
        assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null));
        String partly = "{\"a\":{\"@type\":\"UUID\",\"value\":\"123e4567-e89b-12d3-a456-426614174000\"},\"b\":1.10}";
        assertEquals("{\"a\":{\"@type\":\"UUID\",\"value\":\"123e4567-e89b-12d3-a456-426614174000\"},"
                + "\"b\":{\"@type\":\"BigDecimal\",\"value\":\"1.10\"}}",
                JsonIo.toJson(JsonIo.toMaps(partly, DECIMALS).asClass(null), null));
    }

    /**
     * A JsonObject built by hand writes as a LinkedHashMap does; put into a tree read from plain JSON, as that tree,
     * and so does any container put there.
     */
    @Test
    void builtByHand() {
        JsonObject made = new JsonObject();
        made.put("amt", new BigDecimal("1.10"));
        assertEquals("{\"amt\":{\"@type\":\"BigDecimal\",\"value\":\"1.10\"}}", JsonIo.toJson(made, null));
        JsonObject read = JsonIo.toMaps("{\"x\":1}", null).asClass(JsonObject.class);
        read.put("made", made);
        assertEquals("{\"x\":1,\"made\":{\"amt\":1.10}}", JsonIo.toJson(read, null));

        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("n", new BigInteger(BIG));
        JsonObject items = JsonObject.newArrayInstance();
        items.setItems(new Object[] {new BigDecimal("2.50")});
        JsonObject tree = JsonIo.toMaps("{\"x\":1}", null).asClass(JsonObject.class);
        tree.put("map", map);
        tree.put("items", items);
        tree.put("list", new java.util.ArrayList<>(java.util.Collections.singletonList(new BigDecimal("3.75"))));
        // each container as it is written anywhere (a Map and a List with their class), the numbers as JSON holds them
        assertEquals("{\"x\":1,\"map\":{\"@type\":\"LinkedHashMap\",\"n\":" + BIG + "},\"items\":[2.50],"
                + "\"list\":{\"@type\":\"ArrayList\",\"@items\":[3.75]}}", JsonIo.toJson(tree, null));

        // an object of a class with fields writes them as Java mode does, wherever it stands
        JsonObject holder = JsonIo.toMaps("{\"x\":1}", null).asClass(JsonObject.class);
        holder.put("priced", new Priced());
        holder.put("list", new java.util.ArrayList<>(java.util.Collections.singletonList(new Priced())));
        holder.put("after", new BigDecimal("2.5"));
        String json = JsonIo.toJson(holder, null);
        String field = "\"amount\":{\"@type\":\"BigDecimal\",\"value\":\"1.10\"}";
        assertEquals(2, json.split(java.util.regex.Pattern.quote(field), -1).length - 1, json);
        assertTrue(json.endsWith(",\"after\":2.5}"), json);

        // and a tree read from plain JSON, put into one built by hand, leaves the rest of that one as it was
        JsonObject outer = new JsonObject();
        outer.put("plain", JsonIo.toMaps("{\"n\":" + BIG + "}", null).asClass(JsonObject.class));
        outer.put("amt", new BigDecimal("1.10"));
        assertEquals("{\"plain\":{\"n\":" + BIG + "},\"amt\":{\"@type\":\"BigDecimal\",\"value\":\"1.10\"}}",
                JsonIo.toJson(outer, null));
    }
}
