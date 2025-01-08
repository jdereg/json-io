package com.cedarsoftware.io;

import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import com.cedarsoftware.util.convert.CollectionsWrappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.CollectionUtilities.setOf;
import static com.cedarsoftware.util.DeepEquals.deepEquals;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This class tests the JDK9+ List.of(), Set.of(), Map.of() which return immutable collections.  In addition,
 * it tests Unmodifiable collections, and finally, in the process it tests forward references within immutable
 * collections.
 *
 * @author Laurent Garnier (original)
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
class JDK9ImmutableTest
{
    static class Rec {
        final String s;
        final int i;
        Rec(String s, int i) {
            this.s = s;
            this.i = i;
        }

        Rec link;
        List<Rec> ilinks;
        List<Rec> mlinks;

		Map<String, Rec> smap;
    }

    @Test
    public void testCopyOfListOf() {
        final Object o = new ArrayList<>(listOf());

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(0, es.size());
        assertEquals(o.getClass(), es.getClass());
        es.add(7);  // does not throw exception
    }

    @Test
    void testListOf() {
        final Object o = listOf();

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(0, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add(7));
    }

    @Test
    void testListOfOne() {
        final Object o = listOf("One");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(1, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add(3));
    }

    @Test
    void testListOfTwo() {
        final Object o = listOf("One", "Two");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(2, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));
    }

    @Test
    void testListOfThree() {
        final Object o = listOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(3, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));
    }

    @Test
    public void testCopyOfSetOf() {
        final Object o = new HashSet<>(setOf());

        String json = TestUtil.toJson(o);

        Set<Object> es = TestUtil.toObjects(json, null);

        assertEquals(0, es.size());
        assertEquals(o.getClass(), es.getClass());
        es.add(7);  // does not throw exception
    }

    @Test
    void testSetOf() {
        final Object o = setOf();

        String json = TestUtil.toJson(o);
        Set<Object> es = TestUtil.toObjects(json, null);

        assertEquals(0, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));
    }

    @Test
    void testSetOfOne() {
        final Object o = setOf("One");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(1, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testSetOfTwo() {
        final Object o = setOf("One", "Two");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(2, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testSetOfThree() {
        final Object o = setOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(3, es.size());
        assert deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testListOfThreeRecsMutableBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = listOf();
        rec2.mlinks = listOf(rec1);
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toObjects(json, null);

        assertEquals(ol.size(), recs.size());
        assert deepEquals(ol, recs);
        assertThrows(UnsupportedOperationException.class, () -> recs.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> rec1.mlinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> rec2.mlinks.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> ol.add(rec1));
    }

    @Test
    void testListOfThreeRecsMutableInside() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = listOf();
        rec2.mlinks = listOf(rec1);
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toObjects(json, null);

        assert deepEquals(ol, recs);
        assertThrows(UnsupportedOperationException.class, () -> recs.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> rec1.mlinks.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> rec2.mlinks.add(rec1));
    }

    @Test
    void testListOfThreeRecsBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.ilinks = listOf(rec2);
        rec2.ilinks = listOf();
        rec1.mlinks = listOf();
        rec2.mlinks = listOf(rec1);
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        Object es = TestUtil.toObjects(json, null);

        assert deepEquals(es, ol);
        assertThrows(UnsupportedOperationException.class, () -> ol.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> rec1.ilinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> rec2.ilinks.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> rec1.mlinks.add(rec2));
        assertThrows(UnsupportedOperationException.class, () -> rec2.mlinks.add(rec1));
    }

    @Test
    void testListOfThreeRecsImmutableOnly() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.ilinks = listOf(rec2, rec1);
        rec2.ilinks = listOf();
        rec1.smap = mapOf("a", rec1, "b", rec2);
        rec2.smap = new TreeMap<>(); // NavigableMap
        rec2.smap.put("alpha", rec2);
        rec2.smap.put("beta", rec1);
        rec2.smap = Collections.unmodifiableNavigableMap((NavigableMap<String, ? extends Rec>) rec2.smap);
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol, new WriteOptionsBuilder().build());

        List<Rec> es = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        json = TestUtil.toJson(es, new WriteOptionsBuilder().build());

        List<Rec> again = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        json = TestUtil.toJson(again, new WriteOptionsBuilder().build());

        assert deepEquals(ol, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> again.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> es.get(0).ilinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> again.get(0).ilinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> es.get(1).ilinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> again.get(1).ilinks.add(rec1));
        assertThrows(UnsupportedOperationException.class, () -> es.get(0).smap.put("foo", rec2));
        assertThrows(UnsupportedOperationException.class, () -> again.get(0).smap.put("foo", rec2));
        assertThrows(UnsupportedOperationException.class, () -> es.get(1).smap.put("foo", rec2));
        assertThrows(UnsupportedOperationException.class, () -> again.get(1).smap.put("foo", rec2));

        final Map<String, Rec> map0 = again.get(0).smap;
        assertThrows(UnsupportedOperationException.class, () -> map0.remove("a"));
        final Map<String, Rec> map1 = again.get(1).smap;
        assertThrows(UnsupportedOperationException.class, () -> map1.remove("a"));

        final Iterator<Map.Entry<String, Rec>> i = map0.entrySet().iterator();
        assertThrows(UnsupportedOperationException.class, () -> i.remove());
        final Iterator<Map.Entry<String, Rec>> j = map1.entrySet().iterator();
        assertThrows(UnsupportedOperationException.class, () -> j.remove());

        final Map.Entry<String, Rec> entry0 = i.next();
        assertNotNull(entry0);
        assertThrows(UnsupportedOperationException.class, () -> entry0.setValue(rec1));

        final Map.Entry<String, Rec> entry1 = j.next();
        assertNotNull(entry1);
        assertThrows(UnsupportedOperationException.class, () -> entry1.setValue(rec2));
    }

    @Test
    void testListOfMany()
    {
        List l0;
        List l1;
        List l2;
        List l3;
        List l4;
        
        l0 = listOf();
        l1 = listOf(1L);
        l2 = listOf("a", 2L);
        l3 = listOf("a", 2L, 4.3);
        l4 = listOf("a", 2L, 3.14, (byte)7);
        assertThrows(UnsupportedOperationException.class, () -> l0.add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> l1.add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> l2.add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> l3.add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> l4.add("foo"));

        Object[] lists = new Object[] {l0, l1, l2, l3, l4};
        String json = TestUtil.toJson(lists, new WriteOptionsBuilder().build());
        
        Object[] newLists = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert deepEquals(lists, newLists);
        assertThrows(UnsupportedOperationException.class, () -> ((List)newLists[0]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((List)newLists[1]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((List)newLists[2]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((List)newLists[3]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((List)newLists[4]).add("foo"));
    }

    static class Node {
        String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    @Test
    void testImmutableSet()
    {
        Set s0 = setOf();
        Set s1 = setOf(1);
        Set s2 = setOf("a", 2);
        Set s3 = setOf("a", 2, 4.3);
        Set s4 = setOf("a", 2, 4.3, (byte)7);

        Object[] sets = new Object[] {s0, s1, s2, s3, s4};
        String json = TestUtil.toJson(sets, new WriteOptionsBuilder().build());

        Object[] newSets = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert deepEquals(sets, newSets);
        assertThrows(UnsupportedOperationException.class, () -> ((Set)newSets[0]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((Set)newSets[1]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((Set)newSets[2]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((Set)newSets[3]).add("foo"));
        assertThrows(UnsupportedOperationException.class, () -> ((Set)newSets[4]).add("foo"));
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_9)
    void testForwardReferenceImmutableList() {
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");

        // Creating forward reference
        node1.next = node2;
        node2.next = node1; // Forward reference: node2 refers back to node1

        List<Node> nodes = listOf(node1, node2);

        // Serialize the list
        String json = JsonIo.toJson(nodes, new WriteOptionsBuilder().build());

        // Deserialize the list
        final List<Node> deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().build(), CollectionsWrappers.getUnmodifiableListClass());

        // Assertions to check if the forward reference is maintained after deserialization
        assertEquals("Node2", deserializedNodes.get(0).next.name);
        assertEquals("Node1", deserializedNodes.get(1).next.name);
        assertThrows(UnsupportedOperationException.class, () -> deserializedNodes.add(node2));

        // Hand-crafted JSON, intentionally listing the @ref before the @id occurs.
        json = "{\n" +
                "  \"@type\": \"List12\",\n" +
                "  \"@items\": [\n" +
                "    {\n" +
                "      \"@ref\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"@id\": 2,\n" +
                "      \"@type\": \"com.cedarsoftware.io.JDK9ImmutableTest$Node\",\n" +
                "      \"name\": \"Node1\",\n" +
                "      \"next\": {\n" +
                "        \"@id\": 1,\n" +
                "        \"name\": \"Node2\",\n" +
                "        \"next\": {\n" +
                "          \"@ref\": 2\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // Deserialize the list (hand created to force forward reference)
        final List<Node> deserializedNodes2 = JsonIo.toObjects(json, new ReadOptionsBuilder().build(), CollectionsWrappers.getUnmodifiableListClass());
        assertThrows(UnsupportedOperationException.class, () -> deserializedNodes.add(node2));

        // Assertions to check if the forward reference is maintained after deserialization
        assertEquals("Node1", deserializedNodes2.get(0).next.name);
        assertEquals("Node2", deserializedNodes2.get(1).next.name);
        assertThrows(UnsupportedOperationException.class, () -> deserializedNodes2.remove(node1));

        final Iterator<Node> i = deserializedNodes2.iterator();
        assertThrows(UnsupportedOperationException. class, () -> i.remove());
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_9)
    void testForwardReferenceImmutableSet() {
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");

        // Creating forward reference
        node1.next = node2;
        node2.next = node1; // Forward reference: node2 refers back to node1

        Set<Node> nodes = setOf(node1, node2);

        // Serialize the list
        String json = JsonIo.toJson(nodes, new WriteOptionsBuilder().build());
        
        // Deserialize the list
        final Set<Node> deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().build(), CollectionsWrappers.getUnmodifiableSetClass());
        assert deepEquals(nodes, deserializedNodes);
        assertThrows(UnsupportedOperationException.class, () -> deserializedNodes.add(node1));
        // Assertions to check if the forward reference is maintained after deserialization
        Iterator<Node> i = deserializedNodes.iterator();
        Node node1st = i.next();
        Node node2nd = i.next();

        // Set order is unknown
        if (node1st.name.equals("Node1")) {
            assertEquals("Node2", node1st.next.name);
            assertEquals("Node1", node2nd.next.name);

        } else {
            assertEquals("Node1", node1st.next.name);
            assertEquals("Node2", node2nd.next.name);
        }

        // Hand-crafted JSON, intentionally listing the @ref before the @id occurs.
        json = "{\n" +
                "  \"@type\": \"Set12\",\n" +
                "  \"@items\": [\n" +
                "    {\n" +
                "      \"@ref\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"@id\": 2,\n" +
                "      \"@type\": \"com.cedarsoftware.io.JDK9ImmutableTest$Node\",\n" +
                "      \"name\": \"Node1\",\n" +
                "      \"next\": {\n" +
                "        \"@id\": 1,\n" +
                "        \"name\": \"Node2\",\n" +
                "        \"next\": {\n" +
                "          \"@ref\": 2\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        // Deserialize the list (hand created to force forward reference)
        final Set<Node> deserializedNodes2 = JsonIo.toObjects(json, new ReadOptionsBuilder().build(), CollectionsWrappers.getUnmodifiableSetClass());
        // Assertions to check if the forward reference is maintained after deserialization
        i = deserializedNodes2.iterator();
        node1st = i.next();
        node2nd = i.next();

        // Set order is unknown
        if (node1st.name.equals("Node1")) {
            assertEquals("Node2", node1st.next.name);
            assertEquals("Node1", node2nd.next.name);

        } else {
            assertEquals("Node1", node1st.next.name);
            assertEquals("Node2", node2nd.next.name);
        }

        final Iterator<Node> j = deserializedNodes2.iterator();
        assertThrows(UnsupportedOperationException.class, () -> j.remove());
        assertThrows(UnsupportedOperationException.class, () -> deserializedNodes2.clear());
    }

    @Test
    void testMapKeyForwardReference() {
        Map<Temporal, Object> map = new LinkedHashMap<>();
        ZonedDateTime zdt = ZonedDateTime.parse("2024-04-27T20:13:00-05:00");
        map.put(zdt, zdt);
        map.put(ZonedDateTime.parse("2024-01-27T20:10:00-05:00"), 16L);
        map.put(ZonedDateTime.parse("2024-02-27T20:11:00-05:00"), 8L);
        map.put(ZonedDateTime.parse("2024-03-27T20:12:00-05:00"), 4L);

        String json = TestUtil.toJson(map, new WriteOptionsBuilder().build());
        json = "{\n" +
                "  \"@type\": \"LinkedHashMap\",\n" +
                "  \"@keys\": [\n" +
                "    {\n" +
                "      \"@ref\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-01-27T20:10:00-05:00\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-02-27T20:11:00-05:00\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-03-27T20:12:00-05:00\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"@items\": [\n" +
                "    {\n" +
                "      \"@id\": 1,\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-04-27T20:13:00-05:00\"\n" +
                "    },\n" +
                "    16,\n" +
                "    8,\n" +
                "    4\n" +
                "  ]\n" +
                "}\n";

        Map<Temporal, Object> map2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Map.class);
        assertSame(map2.keySet().iterator().next(), map2.values().iterator().next());
    }

    @Test
    void testMapValueReferenceKey() {
        Map<Temporal, Object> map = new LinkedHashMap<>();
        ZonedDateTime zdt = ZonedDateTime.parse("2024-04-27T20:13:00-05:00");
        map.put(zdt, zdt);
        map.put(ZonedDateTime.parse("2024-01-27T20:10:00-05:00"), 16L);
        map.put(ZonedDateTime.parse("2024-02-27T20:11:00-05:00"), 8L);
        map.put(ZonedDateTime.parse("2024-03-27T20:12:00-05:00"), 4L);

        String json = TestUtil.toJson(map, new WriteOptionsBuilder().build());
        // JSON below has been manually rearranged to force forward reference
        json = "{\n" +
                "  \"@type\": \"LinkedHashMap\",\n" +
                "  \"@keys\": [\n" +
                "    {\n" +
                "      \"@id\": 1,\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-04-27T20:13:00-05:00\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-01-27T20:10:00-05:00\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-02-27T20:11:00-05:00\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@type\": \"ZonedDateTime\",\n" +
                "      \"value\": \"2024-03-27T20:12:00-05:00\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"@items\": [\n" +
                "    {\n" +
                "      \"@ref\": 1" +
                "    },\n" +
                "    16,\n" +
                "    8,\n" +
                "    4\n" +
                "  ]\n" +
                "}\n";

        Map<Temporal, Object> map2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Map.class);
        assertSame(map2.keySet().iterator().next(), map2.values().iterator().next());
    }

    @Test
    void testMapKeyForwardReferenceOutsideMap() {
        Map<Temporal, Object> map = new LinkedHashMap<>();
        ZonedDateTime zdt = ZonedDateTime.parse("2024-04-27T20:13:00-05:00");
        map.put(zdt, 32);
        map.put(ZonedDateTime.parse("2024-01-27T20:10:00-05:00"), 16L);
        map.put(ZonedDateTime.parse("2024-02-27T20:11:00-05:00"), 8L);
        map.put(ZonedDateTime.parse("2024-03-27T20:12:00-05:00"), 4L);
        Object[] items = new Object[] { map, zdt };

        String json = TestUtil.toJson(items, new WriteOptionsBuilder().build());
        // JSON below has been manually rearranged to force forward reference
        json = "[\n" +
                "  {\n" +
                "    \"@type\": \"LinkedHashMap\",\n" +
                "    \"@keys\": [\n" +
                "      {\n" +
                "        \"@ref\":1\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-01-27T20:10:00-05:00\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-02-27T20:11:00-05:00\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-03-27T20:12:00-05:00\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"@items\": [\n" +
                "      {\n" +
                "        \"@type\": \"int\",\n" +
                "        \"value\": 32\n" +
                "      },\n" +
                "      16,\n" +
                "      8,\n" +
                "      4\n" +
                "    ]\n" +
                "  },\n" +
                "  {\n" +
                "    \"@id\": 1,\n" +
                "    \"@type\": \"ZonedDateTime\",\n" +
                "    \"value\": \"2024-04-27T20:13:00-05:00\"\n" +
                "  }\n" +
                "]";

        Object[] objs = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Object[].class);
        Map map2 = (Map) objs[0];
        ZonedDateTime zdt2 = (ZonedDateTime) objs[1];
        assertEquals(zdt2.toEpochSecond(),zdt.toEpochSecond());
        assertEquals(((ZonedDateTime)map2.keySet().iterator().next()).toEpochSecond(), zdt2.toEpochSecond());
    }

    @Test
    void testMapValueForwardReferenceOutsideMap() {
        Map<Temporal, Object> map = new LinkedHashMap<>();
        ZonedDateTime zdt = ZonedDateTime.parse("2024-04-27T20:13:00-05:00");
        map.put(zdt, 32);
        map.put(ZonedDateTime.parse("2024-01-27T20:10:00-05:00"), 16L);
        map.put(ZonedDateTime.parse("2024-02-27T20:11:00-05:00"), 8L);
        map.put(ZonedDateTime.parse("2024-03-27T20:12:00-05:00"), 4L);
        Object[] items = new Object[] { map, zdt };

        String json = TestUtil.toJson(items, new WriteOptionsBuilder().build());
        
        // JSON below has been manually rearranged to force forward reference
        json = "[\n" +
                "  {\n" +
                "    \"@type\": \"LinkedHashMap\",\n" +
                "    \"@keys\": [\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-04-27T20:13:00-05:00\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-01-27T20:10:00-05:00\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-02-27T20:11:00-05:00\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"@type\": \"ZonedDateTime\",\n" +
                "        \"value\": \"2024-03-27T20:12:00-05:00\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"@items\": [{\"@ref\": 1}, 16, 8, 4]\n" +
                "  },\n" +
                "  {\n" +
                "    \"@id\": 1,\n" +
                "    \"@type\": \"ZonedDateTime\",\n" +
                "    \"value\": \"2024-04-27T20:13:00-05:00\"\n" +
                "  }\n" +
                "]\n";

        // No forward reference exceptions
        Object[] objs = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Object[].class);
        Map<String, Object> dateKeys = (Map<String, Object>) objs[0];
        assert dateKeys.values().toArray()[0] instanceof ZonedDateTime;
        assert dateKeys.values().toArray()[1] instanceof Long;
        assert dateKeys.values().toArray()[2] instanceof Long;
        assert dateKeys.values().toArray()[3] instanceof Long;
    }

    @Test
    void testMapValueForwardReferenceInsideMap() {
        // JSON below has been manually rearranged to force forward reference
        String json = "{\n" +
                "  \"@type\": \"LinkedHashMap\",\n" +
                "  \"@keys\": [\n" +
                "    {\n" +
                "      \"@ref\": 1\n" +
                "    },\n" +
                "    {\n" +
                "      \"@ref\": 2\n" +
                "    }\n" +
                "  ],\n" +
                "  \"@items\": [\n" +
                "    {\n" +
                "      \"@id\": 1,\n" +
                "      \"@type\": \"String\",\n" +
                "      \"_v\": \"foo\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"@id\": 2,\n" +
                "      \"@type\": \"String\",\n" +
                "      \"_v\": \"bar\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Map<String, Object> map = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Map.class);
        assertEquals("foo", map.keySet().toArray()[0]);
        assertEquals("foo", map.values().toArray()[0]);
        assertEquals("bar", map.keySet().toArray()[1]);
        assertEquals("bar", map.values().toArray()[1]);
    }
}