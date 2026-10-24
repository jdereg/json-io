package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON honors {@code maxObjectGraphDepth} and {@code maxObjectCount} as JSON does: the same options and permanent
 * defaults (100,000 levels, 1,000,000,000 objects), with the same messages; with cycleSupport, in the reference trace,
 * as JSON checks them; without (TOON's default), the depth as each object is written, in JSON too, which checked
 * nothing there. Up to now TOON checked neither. And a graph nested deeper than the thread's stack can write -- a few
 * thousand levels on a default stack, as a long linked list is -- is a {@code JsonIoException} that says so, in JSON
 * and TOON, rather than a {@code StackOverflowError}.
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
class ToonWriteLimitsTest {
    public static class Node {
        public Node next;
    }

    public static class Wide {
        public List<Node> nodes = new ArrayList<>();
    }

    /** A head and {@code links} more nodes: the last is {@code links} levels below the head. */
    private static Node chain(int links) {
        Node head = new Node();
        Node cur = head;
        for (int i = 0; i < links; i++) {
            cur.next = new Node();
            cur = cur.next;
        }
        return head;
    }

    private static Wide wide(int n) {
        Wide w = new Wide();
        for (int i = 0; i < n; i++) {
            w.nodes.add(new Node());
        }
        return w;
    }

    @Test
    void depthAsJson() {
        WriteOptions limit = new WriteOptionsBuilder().maxObjectGraphDepth(200).build();
        // JSON and TOON agree where the limit falls
        JsonIo.toJson(chain(200), limit);
        JsonIo.toToon(chain(200), limit);
        JsonIoException json = assertThrows(JsonIoException.class, () -> JsonIo.toJson(chain(201), limit));
        JsonIoException toon = assertThrows(JsonIoException.class, () -> JsonIo.toToon(chain(201), limit));
        assertEquals(json.getMessage(), toon.getMessage());
        assertTrue(toon.getMessage().startsWith("Object graph too deep (>200 levels)"), toon.getMessage());
    }

    @Test
    void countAsJson() {
        WriteOptions limit = new WriteOptionsBuilder().maxObjectCount(100).build();
        JsonIo.toToon(wide(50), limit);
        JsonIoException toon = assertThrows(JsonIoException.class, () -> JsonIo.toToon(wide(1000), limit));
        assertTrue(toon.getMessage().startsWith("Object graph too large (>100 objects)"), toon.getMessage());
        JsonIoException json = assertThrows(JsonIoException.class, () -> JsonIo.toJson(wide(1000), limit));
        assertEquals(json.getMessage(), toon.getMessage());
    }

    /** The permanent defaults reach TOON, through options of its own and none at all. */
    @Test
    void thePermanentDefaults() {
        WriteOptions defaults = new WriteOptionsBuilder().build();
        assertEquals(100000, defaults.getMaxObjectGraphDepth());
        assertEquals(1000000000, defaults.getMaxObjectCount());
        int depth = defaults.getMaxObjectGraphDepth();
        int count = defaults.getMaxObjectCount();
        try {
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(50);
            assertThrows(JsonIoException.class, () -> JsonIo.toToon(chain(51), null));      // TOON's default: no trace
            assertThrows(JsonIoException.class, () -> JsonIo.toToon(chain(51), new WriteOptionsBuilder().build()));
            JsonIo.toToon(chain(50), null);
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(depth);
            WriteOptionsBuilder.addPermanentMaxObjectCount(10);
            assertThrows(JsonIoException.class, () -> JsonIo.toToon(wide(100), new WriteOptionsBuilder().build()));
        } finally {
            WriteOptionsBuilder.addPermanentMaxObjectGraphDepth(depth);
            WriteOptionsBuilder.addPermanentMaxObjectCount(count);
        }
        JsonIo.toToon(wide(100), null);
    }

    /**
     * Without cycleSupport -- TOON's default ({@code toToon(obj, null)}) -- there is no trace: the depth is checked as
     * each object enters the path being written, in both formats; the count, which guards what a trace keeps per
     * object, is not.
     */
    @Test
    void withoutCycleSupport() {
        WriteOptions noTrace = new WriteOptionsBuilder().cycleSupport(false).maxObjectGraphDepth(200).build();
        JsonIo.toJson(chain(200), noTrace);
        JsonIo.toToon(chain(200), noTrace);
        JsonIoException json = assertThrows(JsonIoException.class, () -> JsonIo.toJson(chain(201), noTrace));
        JsonIoException toon = assertThrows(JsonIoException.class, () -> JsonIo.toToon(chain(201), noTrace));
        assertEquals(json.getMessage(), toon.getMessage());
        assertTrue(toon.getMessage().startsWith("Object graph too deep (>200 levels)"), toon.getMessage());

        WriteOptions count = new WriteOptionsBuilder().cycleSupport(false).maxObjectCount(100).build();
        JsonIo.toJson(wide(1000), count);
        JsonIo.toToon(wide(1000), count);
    }

    /** Deeper than the stack: a clear error, in JSON and TOON, with cycleSupport on and off. */
    @Test
    void deeperThanTheStack() throws Exception {
        Node deep = chain(50000);
        List<Function<Node, String>> writers = new ArrayList<>();
        writers.add(n -> JsonIo.toJson(n, null));
        writers.add(n -> JsonIo.toToon(n, null));
        writers.add(n -> JsonIo.toJson(n, new WriteOptionsBuilder().cycleSupport(false).build()));
        writers.add(n -> JsonIo.toToon(n, new WriteOptionsBuilder().cycleSupport(false).build()));
        for (Function<Node, String> writer : writers) {
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread small = new Thread(null, () -> {
                try {
                    writer.apply(deep);
                } catch (Throwable t) {
                    thrown.set(t);
                }
            }, "small-stack", 256 * 1024);
            small.start();
            small.join();
            Throwable t = thrown.get();
            assertInstanceOf(JsonIoException.class, t, String.valueOf(t));
            assertTrue(t.getMessage().startsWith("Object graph too deep to write: the thread's stack overflowed"),
                    t.getMessage());
            assertInstanceOf(StackOverflowError.class, t.getCause());
        }
        // and the writer works afterward
        assertTrue(JsonIo.toToon(chain(10), null).contains("next"));
        assertTrue(JsonIo.toJson(chain(10), null).contains("next"));
    }
}
