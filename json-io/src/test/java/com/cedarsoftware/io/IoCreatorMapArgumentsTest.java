package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A creator given a map -- or a list of maps, or an object holding one -- finds it filled. json-io fills a map at the
 * end of a read, once every reference it may hold is known, but a creator is called during the read: one that kept
 * the map it was given saw it filled later, and one that read it (a copy, a sum) saw it empty.
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
class IoCreatorMapArgumentsTest {
    private static int sum(Map<String, Integer> m) {
        int total = 0;
        for (int v : m.values()) {
            total += v;
        }
        return total;
    }

    /** Written as its counts, a map; its creator sums them. */
    public static class Totals {
        private final Map<String, Integer> counts;
        private final int total;

        @IoCreator
        public Totals(Map<String, Integer> counts) {
            this.counts = new LinkedHashMap<>(counts);
            this.total = sum(counts);
        }

        @IoValue
        public Map<String, Integer> counts() {
            return counts;
        }
    }

    /** A list written as a map of each element's count; its creator expands the map. */
    public static class Counts extends ArrayList<String> {
        public Counts() {
        }

        @IoCreator
        public static Counts of(Map<String, Integer> counts) {
            Counts c = new Counts();
            counts.forEach((k, n) -> {
                for (int i = 0; i < n; i++) {
                    c.add(k);
                }
            });
            return c;
        }

        @IoValue
        public Map<String, Integer> counts() {
            Map<String, Integer> m = new LinkedHashMap<>();
            forEach(k -> m.merge(k, 1, Integer::sum));
            return m;
        }
    }

    /** Its fields; its creator copies the list of maps and the object holding a map. */
    public static class Ledger {
        private final List<Map<String, Integer>> pages;
        private final int pageTotal;
        private final int boxTotal;

        @IoCreator
        public Ledger(@IoProperty("pages") List<Map<String, Integer>> pages, @IoProperty("box") Box box) {
            this.pages = new ArrayList<>(pages);
            int t = 0;
            for (Map<String, Integer> page : pages) {
                t += sum(page);
            }
            this.pageTotal = t;
            this.boxTotal = sum(box.m);
        }
    }

    public static class Box {
        public Map<String, Integer> m;
    }

    public static class Holder {
        public Totals totals;
        public Counts counts;
        public Ledger ledger;
    }

    private static Map<String, Integer> map(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void theCreatorReadsItsMap() {
        String json = "{\"totals\":{\"a\":2,\"b\":3},\"counts\":{\"x\":2,\"y\":1}}";
        Holder h = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(5, h.totals.total);
        assertEquals(map("a", 2, "b", 3), h.totals.counts);
        assertEquals(Arrays.asList("x", "x", "y"), h.counts);

        String toon = "totals:\n  a: 2\n  b: 3\ncounts:\n  x: 2\n  y: 1\n";
        h = JsonIo.fromToon(toon, null).asClass(Holder.class);
        assertEquals(5, h.totals.total);
        assertEquals(Arrays.asList("x", "x", "y"), h.counts);
    }

    @Test
    void mapsInsideItsArguments() {
        String json = "{\"ledger\":{\"pages\":[{\"a\":1},{\"b\":2,\"c\":3}],\"box\":{\"m\":{\"z\":7}}}}";
        Ledger ledger = JsonIo.toJava(json, null).asClass(Holder.class).ledger;
        assertEquals(6, ledger.pageTotal);
        assertEquals(7, ledger.boxTotal);
        assertEquals(Arrays.asList(map("a", 1), map("b", 2, "c", 3)), ledger.pages);
    }

    /** As json-io writes it, in JSON and TOON, typed and not. */
    @Test
    void roundTrip() {
        Holder h = new Holder();
        h.totals = new Totals(map("a", 2, "b", 3));
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build()}) {
            Holder back = JsonIo.toJava(JsonIo.toJson(h, options), null).asClass(Holder.class);
            assertEquals(5, back.totals.total);
            assertEquals(h.totals.counts, back.totals.counts);
            back = JsonIo.fromToon(JsonIo.toToon(h, options), null).asClass(Holder.class);
            assertEquals(5, back.totals.total);
        }
    }
}
