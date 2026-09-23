package com.cedarsoftware.io;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonObject#hashCode()} and {@link JsonObject#equals(Object)} must terminate on a cyclic graph.
 * <p>
 * json-io builds cyclic JsonObject graphs itself: in Maps mode ({@link JsonIo#toMaps(String)}) every {@code @ref}
 * that points back up the document -- a child's {@code parent}, a node's {@code next} in a ring -- becomes a real
 * reference to the ancestor. Both methods used to recurse that graph with no guard, so hashing the result,
 * comparing two reads of it, or putting it in a {@code HashSet} died with {@link StackOverflowError}. So did the
 * READ itself when a cyclic object was a map's complex key ({@code @keys}), because the reader hashes keys.
 * <p>
 * The rules these tests pin:
 * <ul>
 *   <li>{@code hashCode()} hashes the object's own keys and leaf values, and only the KIND of a nested map,
 *       collection or JsonObject. It never descends, so it cannot loop, and a nested object changing later can
 *       no longer leave an ancestor's cached hash inconsistent with {@code equals()}.</li>
 *   <li>{@code equals()} still compares the whole graph. A pair it has already taken as equal is taken as equal
 *       again -- that is the loop closing -- so two reads of the same cyclic document are equal and a difference
 *       anywhere in the loop still makes them unequal. Each pair is compared once, so a densely cross-linked graph
 *       costs linear time, not one visit per path through it; and a pair found unequal is forgotten, with
 *       everything decided after it.</li>
 * </ul>
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
class JsonObjectCycleTest {

    private static final String PARENT_CHILD = "{\"@id\":1,\"name\":\"a\",\"child\":{\"name\":\"b\",\"parent\":{\"@ref\":1}}}";
    private static final String PARENT_CHILD_RENAMED = "{\"@id\":1,\"name\":\"a\",\"child\":{\"name\":\"c\",\"parent\":{\"@ref\":1}}}";
    private static final String SELF = "{\"@id\":1,\"self\":{\"@ref\":1}}";
    private static final String THROUGH_ARRAY = "{\"@id\":1,\"list\":[{\"@ref\":1},\"x\"]}";
    private static final String CYCLIC_KEY = "{\"@keys\":[{\"@id\":2,\"self\":{\"@ref\":2}}],\"@items\":[\"v\"]}";

    // ------------------------------------------------------------- graphs json-io reads itself

    @Test
    void aCyclicReadCanBeHashed() {
        for (String json : new String[] {PARENT_CHILD, SELF, THROUGH_ARRAY}) {
            Object root = JsonIo.toMaps(json).asClass(null);
            assertEquals(root.hashCode(), JsonIo.toMaps(json).asClass(null).hashCode(), json);
        }
    }

    @Test
    void twoReadsOfTheSameCyclicDocumentAreEqual() {
        for (String json : new String[] {PARENT_CHILD, SELF}) {
            Object first = JsonIo.toMaps(json).asClass(null);
            Object second = JsonIo.toMaps(json).asClass(null);
            assertEquals(first, second, json);
            assertEquals(second, first, json);
        }
    }

    @Test
    void aDifferenceInsideTheLoopStillMakesThemUnequal() {
        Object a = JsonIo.toMaps(PARENT_CHILD).asClass(null);
        Object c = JsonIo.toMaps(PARENT_CHILD_RENAMED).asClass(null);
        assertNotEquals(a, c);
        assertNotEquals(c, a);
    }

    @Test
    void aCyclicReadCanGoInAHashSet() {
        Set<Object> set = new HashSet<>();
        set.add(JsonIo.toMaps(PARENT_CHILD).asClass(null));
        assertTrue(set.contains(JsonIo.toMaps(PARENT_CHILD).asClass(null)));
        assertFalse(set.contains(JsonIo.toMaps(PARENT_CHILD_RENAMED).asClass(null)));
    }

    @Test
    void aCyclicReadAsAMapCanBeHashedAndCompared() {
        // asClass(Map.class) hands back a JDK map at the root, whose hashCode()/equals() reach the JsonObjects below
        Map<?, ?> first = JsonIo.toMaps(PARENT_CHILD).asClass(Map.class);
        Map<?, ?> second = JsonIo.toMaps(PARENT_CHILD).asClass(Map.class);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first, second);
        assertEquals(first.get("child").hashCode(), second.get("child").hashCode());
    }

    @Test
    void aCyclicComplexKeyCanBeRead() {
        // The reader puts each @keys object into the map it builds, which hashes it: this used to fail the read itself
        Map<?, ?> map = JsonIo.toMaps(CYCLIC_KEY).asClass(Map.class);
        assertEquals(1, map.size());
        Object key = map.keySet().iterator().next();
        assertSame(key, ((Map<?, ?>) key).get("self"));
        assertEquals("v", map.get(key));
    }

    // ------------------------------------------------------------------ graphs built by hand

    @Test
    void everyCycleShapeHashesAndCompares() {
        for (Shape shape : Shape.values()) {
            JsonObject a = shape.build("leaf");
            JsonObject b = shape.build("leaf");
            JsonObject different = shape.build("other");
            assertEquals(a.hashCode(), b.hashCode(), shape.name());
            assertEquals(a, b, shape.name());
            assertNotEquals(a, different, shape.name());
        }
    }

    enum Shape {
        DIRECT {
            JsonObject build(String leaf) {
                JsonObject o = new JsonObject();
                o.put("leaf", leaf);
                o.put("self", o);
                return o;
            }
        },
        THROUGH_A_JDK_MAP {
            JsonObject build(String leaf) {
                JsonObject o = new JsonObject();
                Map<String, Object> hop = new LinkedHashMap<>();
                o.put("leaf", leaf);
                o.put("hop", hop);
                hop.put("back", o);
                return o;
            }
        },
        THROUGH_A_JDK_LIST {
            JsonObject build(String leaf) {
                JsonObject o = new JsonObject();
                List<Object> hop = new ArrayList<>();
                o.put("leaf", leaf);
                o.put("hop", hop);
                hop.add(o);
                return o;
            }
        },
        THROUGH_ANOTHER_JSON_OBJECT {
            JsonObject build(String leaf) {
                JsonObject parent = new JsonObject();
                JsonObject child = new JsonObject();
                parent.put("child", child);
                child.put("leaf", leaf);           // the difference sits one hop inside the loop
                child.put("parent", parent);
                return parent;
            }
        };

        abstract JsonObject build(String leaf);
    }

    @Test
    void anArrayObjectHoldingItselfHashesAndCompares() {
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[] {"x", a});
        JsonObjectArray b = new JsonObjectArray();
        b.setItems(new Object[] {"x", b});
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);

        JsonObjectArray c = new JsonObjectArray();
        c.setItems(new Object[] {"y", c});
        assertNotEquals(a, c);
    }

    @Test
    void itemsHoldingAnArrayThatHoldsItselfHashAndCompare() {
        Object[] loopA = new Object[1];
        loopA[0] = loopA;
        Object[] loopB = new Object[1];
        loopB[0] = loopB;
        JsonObjectArray a = new JsonObjectArray();
        a.setItems(new Object[] {loopA});
        JsonObjectArray b = new JsonObjectArray();
        b.setItems(new Object[] {loopB});
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
    }

    @Test
    void aComplexKeyedMapInALoopHashesAndCompares() {
        JsonObjectMap a = new JsonObjectMap();
        a.setKeys(new Object[] {"k", a});
        a.setItems(new Object[] {a, "v"});
        JsonObjectMap b = new JsonObjectMap();
        b.setKeys(new Object[] {"k", b});
        b.setItems(new Object[] {b, "v"});
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, b);
    }

    @Test
    void aDenselyCrossLinkedGraphComparesInLinearTime() {
        // Every node links to every other -- a clique, as a friends graph read with @ref can be. Guarding only the
        // current path would explore every simple path through it: 23! of them here, which never finishes.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            assertEquals(clique(24, 23), clique(24, 23));
            assertNotEquals(clique(24, 23), clique(24, 17));    // one node's leaf differs, far from where it starts
        });
    }

    @Test
    void aComparisonThatFailsInsideASetLookupIsNotRememberedAsEqual() {
        // HashSet.contains() tries each candidate in the bucket and moves on from a failure -- so a pair can be found
        // unequal without the whole comparison ending. Remembering that failed pair as "taken as equal" would make a
        // later comparison of the same two objects answer true.
        JsonObject p1 = ring(1);
        JsonObject p2 = ring(2);            // unequal to p1, one hop inside the loop
        JsonObject p3 = ring(1);            // equal to p1
        JsonObject p4 = ring(2);            // equal to p2
        Set<Object> left = new java.util.LinkedHashSet<>();
        left.add(p1);                       // p1 first, so looking up p2 tries p1 -- and fails -- before p4
        left.add(p4);
        Set<Object> right = new java.util.LinkedHashSet<>();
        right.add(p2);
        right.add(p3);
        assertEquals(left, right);

        JsonObject t1 = new JsonObject();
        t1.put("set", left);
        t1.put("again", p1);
        JsonObject t2 = new JsonObject();
        t2.put("set", right);
        t2.put("again", p2);
        assertNotEquals(t1, t2);            // the sets match; p1 and p2 do not
    }

    /** n nodes, each linking to every other; node {@code odd} carries a different leaf. */
    private static JsonObject clique(int n, int odd) {
        JsonObject[] nodes = new JsonObject[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = new JsonObject();
            nodes[i].put("leaf", i == odd ? "odd" : "same");
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    nodes[i].put("link" + j, nodes[j]);
                }
            }
        }
        return nodes[0];
    }

    /** A two-node ring, p -> q -> p, whose inner node carries {@code value}. */
    private static JsonObject ring(int value) {
        JsonObject p = new JsonObject();
        JsonObject q = new JsonObject();
        p.put("tag", "p");
        p.put("next", q);
        q.put("tag", "q");
        q.put("value", value);
        q.put("back", p);
        return p;
    }

    // ----------------------------------------------------------------------- what must NOT change

    @Test
    void acyclicEqualityIsStillDeep() {
        JsonObject a = objectWithChild(1);
        JsonObject b = objectWithChild(1);
        JsonObject c = objectWithChild(2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);       // the difference is one level down
    }

    @Test
    void equalObjectsHashEquallyEvenWhenANestedObjectChangedAfterHashing() {
        // The hash is cached. It used to fold in nested objects, so an ancestor hashed BEFORE a nested object changed
        // disagreed with an equal ancestor hashed after -- equal objects, different hashes, lost in a HashSet.
        JsonObject early = objectWithChild(1);
        int unused = early.hashCode();
        ((JsonObject) early.get("child")).put("added", true);

        JsonObject late = objectWithChild(1);
        ((JsonObject) late.get("child")).put("added", true);

        assertEquals(early, late);
        assertEquals(early.hashCode(), late.hashCode());
    }

    @Test
    void aFailedComparisonLeavesNothingBehindOnTheThread() {
        // A leaf whose equals() throws aborts the comparison. If the pair it was comparing stayed on this thread's
        // path, the next comparison of that same pair would be taken as "the loop closing" and answer true.
        JsonObject a = new JsonObject();
        JsonObject b = new JsonObject();
        a.put("k", new Bomb());
        b.put("k", new Bomb());
        assertThrows(IllegalStateException.class, () -> a.equals(b));

        a.put("k", 1);
        b.put("k", 2);
        assertNotEquals(a, b);
    }

    @Test
    void aJsonObjectStillEqualsOnlyItsOwnShape() {
        JsonObject lite = new JsonObject();
        lite.put("k", "v");
        assertNotEquals(lite, Collections.singletonMap("k", "v"));
        assertNotEquals(lite, new JsonObjectMap());
    }

    private static JsonObject objectWithChild(int value) {
        JsonObject child = new JsonObject();
        child.put("value", value);
        JsonObject parent = new JsonObject();
        parent.put("name", "p");
        parent.put("child", child);
        return parent;
    }

    private static final class Bomb {
        @Override
        public boolean equals(Object o) {
            throw new IllegalStateException("boom");
        }

        @Override
        public int hashCode() {
            return 7;
        }
    }
}
