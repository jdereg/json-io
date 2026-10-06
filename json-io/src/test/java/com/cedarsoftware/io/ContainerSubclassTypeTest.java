package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A Map or Collection subclass whose own type arguments are not Map's or Collection's, in order: its keys, values and
 * elements are matched to Map's and Collection's own. json-io took the container's first argument as the key and the
 * second as the value -- StringMap&lt;V&gt; extends HashMap&lt;String, V&gt; held in a field StringMap&lt;Integer&gt;
 * had its key "k" parsed as an int -- and the first as a collection's element.
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
class ContainerSubclassTypeTest {

    public static class StringMap<V> extends HashMap<String, V> {
    }

    public static class Reversed<V, K> extends LinkedHashMap<K, V> {
    }

    public static class MultiMap<K, V> extends HashMap<K, List<V>> {
    }

    public static class Tagged<X> extends ArrayList<String> {
    }

    public static class Point {
        public int x;
        public int y;
    }

    public static class Holder {
        public StringMap<Integer> stringMap;
        public Reversed<Integer, Long> reversed;
        public MultiMap<Long, Integer> multiMap;
        public Tagged<Integer> tagged;
        public StringMap<Point> points;
    }

    private static Holder read(String json) {
        return JsonIo.toJava(json, null).asClass(Holder.class);
    }

    @Test
    void aMapSubclassWithItsKeyFixed() {
        Holder h = read("{\"stringMap\":{\"k\":\"1\"}}");
        Map.Entry<String, Integer> e = h.stringMap.entrySet().iterator().next();
        assertEquals("k", e.getKey());
        assertEquals(Integer.valueOf(1), e.getValue());
    }

    @Test
    void aMapSubclassWithItsArgumentsReversed() {
        Holder h = read("{\"reversed\":{\"7\":\"8\"}}");
        Map.Entry<Long, Integer> e = h.reversed.entrySet().iterator().next();
        Object key = e.getKey();
        Object value = e.getValue();
        assertEquals(7L, key);
        assertEquals(8, value);
    }

    @Test
    void aMapSubclassWhoseValueIsAContainerOfItsArgument() {
        Holder h = read("{\"multiMap\":{\"1\":[\"2\",\"3\"]}}");
        Map.Entry<Long, List<Integer>> e = h.multiMap.entrySet().iterator().next();
        Object key = e.getKey();
        assertEquals(1L, key);
        assertEquals(Arrays.asList(2, 3), e.getValue());
    }

    @Test
    void aCollectionSubclassWithItsElementFixed() {
        Holder h = read("{\"tagged\":[1,2]}");
        Object first = h.tagged.get(0);
        assertEquals("1", first);
    }

    @Test
    void objectValuesOfAMapSubclass() {
        Holder h = read("{\"points\":{\"a\":{\"x\":1,\"y\":2}}}");
        Point p = h.points.get("a");
        assertEquals(2, p.y);
        assertInstanceOf(Point.class, p);
    }

    @Test
    void roundTrip() {
        Holder h = new Holder();
        h.stringMap = new StringMap<>();
        h.stringMap.put("k", 5);
        h.reversed = new Reversed<>();
        h.reversed.put(9L, 10);
        h.multiMap = new MultiMap<>();
        h.multiMap.put(3L, new ArrayList<>(Arrays.asList(4, 5)));
        h.tagged = new Tagged<>();
        h.tagged.add("t");
        h.points = new StringMap<>();
        Point p = new Point();
        p.x = 6;
        h.points.put("p", p);
        for (WriteOptions options : new WriteOptions[] {new WriteOptionsBuilder().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build()}) {
            Holder back = read(JsonIo.toJson(h, options));
            assertEquals(Integer.valueOf(5), back.stringMap.get("k"));
            assertEquals(Integer.valueOf(10), back.reversed.get(9L));
            assertEquals(Arrays.asList(4, 5), back.multiMap.get(3L));
            assertEquals("t", back.tagged.get(0));
            assertEquals(6, back.points.get("p").x);
        }
    }
}
