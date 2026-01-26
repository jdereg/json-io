package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the cycleSupport WriteOption.
 *
 * When cycleSupport=true (default): Full cycle support with @id/@ref
 * When cycleSupport=false: Skip traceReferences for performance, cycles silently skipped
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
public class CycleSupportOptionTest {

    static class Node {
        String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    @Test
    public void testCycleSupportTrue_default() {
        // Default behavior: cycleSupport=true, @id/@ref emitted for cycles
        Node alpha = new Node("alpha");
        Node beta = new Node("beta");
        alpha.next = beta;
        beta.next = alpha;

        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        assertTrue(writeOptions.isCycleSupport());

        String json = JsonIo.toJson(alpha, writeOptions);

        // Should contain @id and @ref for cycle handling
        assertTrue(json.contains("@id") || json.contains("\"@id\"") ||
                   json.contains("$id") || json.contains("\"$id\""));
        assertTrue(json.contains("@ref") || json.contains("\"@ref\"") ||
                   json.contains("$ref") || json.contains("\"$ref\""));

        // Round-trip should preserve the cycle
        Node a2 = JsonIo.toJava(json, null).asClass(Node.class);
        assertEquals("alpha", a2.name);
        assertEquals("beta", a2.next.name);
        assertSame(a2, a2.next.next);  // Cycle preserved
    }

    @Test
    public void testCycleSupportFalse_noCycle() {
        // When cycleSupport=false and data has no cycles, should work normally
        Node alpha = new Node("alpha");
        Node beta = new Node("beta");
        alpha.next = beta;
        // No cycle - beta.next is null

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .cycleSupport(false)
                .build();
        assertFalse(writeOptions.isCycleSupport());

        String json = JsonIo.toJson(alpha, writeOptions);

        // Should NOT contain @id or @ref (no need without cycles)
        assertFalse(json.contains("@id"));
        assertFalse(json.contains("\"@id\""));
        assertFalse(json.contains("$id"));
        assertFalse(json.contains("\"$id\""));
        assertFalse(json.contains("@ref"));
        assertFalse(json.contains("\"@ref\""));
        assertFalse(json.contains("$ref"));
        assertFalse(json.contains("\"$ref\""));

        // Round-trip should work
        Node a2 = JsonIo.toJava(json, null).asClass(Node.class);
        assertEquals("alpha", a2.name);
        assertEquals("beta", a2.next.name);
        assertNull(a2.next.next);
    }

    @Test
    public void testCycleSupportFalse_withCycle_noInfiniteLoop() {
        // When cycleSupport=false and data HAS cycles, should not infinite loop
        Node alpha = new Node("alpha");
        Node beta = new Node("beta");
        alpha.next = beta;
        beta.next = alpha;  // Cycle!

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .cycleSupport(false)
                .build();

        // This should complete without infinite loop - cycles are silently skipped
        String json = JsonIo.toJson(alpha, writeOptions);

        // Should NOT contain @id or @ref
        assertFalse(json.contains("@id"));
        assertFalse(json.contains("$id"));
        assertFalse(json.contains("@ref"));
        assertFalse(json.contains("$ref"));

        // JSON is valid but incomplete (cycle broken)
        assertNotNull(json);
        assertTrue(json.contains("alpha"));
        assertTrue(json.contains("beta"));
    }

    @Test
    public void testCycleSupportFalse_selfReference() {
        // Self-referencing object
        Node self = new Node("self");
        self.next = self;  // Self-cycle!

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .cycleSupport(false)
                .build();

        // Should complete without infinite loop
        String json = JsonIo.toJson(self, writeOptions);

        assertNotNull(json);
        assertTrue(json.contains("self"));
        // The self-reference will be skipped
        assertFalse(json.contains("@ref"));
        assertFalse(json.contains("$ref"));
    }

    @Test
    public void testCycleSupportOption_copiedCorrectly() {
        // Test that cycleSupport is copied in the copy constructor
        WriteOptions original = new WriteOptionsBuilder()
                .cycleSupport(false)
                .build();

        WriteOptions copied = new WriteOptionsBuilder(original).build();

        assertFalse(copied.isCycleSupport());
    }

    @Test
    public void testCycleSupportFalse_multipleSameObjects() {
        // Same object referenced multiple times (not a cycle, but shared reference)
        Node shared = new Node("shared");
        Node[] array = new Node[] { shared, shared, shared };

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .cycleSupport(false)
                .build();

        // Should complete - duplicates after first are skipped
        String json = JsonIo.toJson(array, writeOptions);

        assertNotNull(json);
        // Should contain "shared" at least once
        assertTrue(json.contains("shared"));
    }
}
