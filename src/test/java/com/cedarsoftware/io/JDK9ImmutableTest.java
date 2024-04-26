package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.MapUtilities;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.CollectionUtilities.setOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JDK9ImmutableTest
{
    static class Rec {
        final String s;
        final int i;
        Rec(String s, int i) {
            this.s = s;
            this.i = i;
        }

        Rec       link;
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
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add(7));
    }

    @Test
    void testListOfOne() {
        final Object o = listOf("One");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(1, es.size());
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add(3));
    }

    @Test
    void testListOfTwo() {
        final Object o = listOf("One", "Two");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(2, es.size());
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));
    }

    @Test
    void testListOfThree() {
        final Object o = listOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        List<Object> es = TestUtil.toObjects(json, null);

        assertEquals(3, es.size());
        assert DeepEquals.deepEquals(o, es);
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
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));
    }

    @Test
    void testSetOfOne() {
        final Object o = setOf("One");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(1, es.size());
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testSetOfTwo() {
        final Object o = setOf("One", "Two");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(2, es.size());
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testSetOfThree() {
        final Object o = setOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        Set<String> es = TestUtil.toObjects(json, null);

        assertEquals(3, es.size());
        assert DeepEquals.deepEquals(o, es);
        assertThrows(UnsupportedOperationException.class, () -> es.add("nope"));;
    }

    @Test
    void testListOfThreeRecsMutableBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(listOf());
        rec2.mlinks = new ArrayList<>(listOf(rec1));
        List<Rec> ol = new ArrayList<>(listOf(rec1, rec2, rec1));

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toObjects(json, null);

        assertEquals(ol.getClass(), recs.getClass());
        assertEquals(ol.size(), recs.size());

        assertEquals(ol.get(0).s, recs.get(0).s);
        assertEquals(ol.get(0).i, recs.get(0).i);
        assertEquals(recs.get(1), recs.get(0).link);
        assertEquals(ol.get(1).s, recs.get(1).s);
        assertEquals(ol.get(1).i, recs.get(1).i);
        assertEquals(recs.get(0), recs.get(1).link);

        assertEquals(recs.get(0), recs.get(2));

        assertEquals(ol.get(0).mlinks.size(), recs.get(0).mlinks.size());
        assertEquals(ol.get(0).mlinks.getClass(), recs.get(0).mlinks.getClass());
        assertEquals(ol.get(1).mlinks.size(), recs.get(1).mlinks.size());
        assertEquals(ol.get(1).mlinks.getClass(), recs.get(1).mlinks.getClass());
    }

    @Test
    void testListOfThreeRecsMutableInside() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(listOf());
        rec2.mlinks = new ArrayList<>(listOf(rec1));
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toObjects(json, null);

        assert DeepEquals.deepEquals(ol, recs);
    }

    @Test
    void testListOfThreeRecsBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.ilinks = listOf(rec2);
        rec2.ilinks = listOf();
        rec1.mlinks = new ArrayList<>(listOf());
        rec2.mlinks = new ArrayList<>(listOf(rec1));
        List<Rec> ol = listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        Object es = TestUtil.toObjects(json, null);

        assert DeepEquals.deepEquals(es, ol);
    }

    @Test
    void testListOfThreeRecsImmutableOnly() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.ilinks = listOf(rec2, rec1);
        rec2.ilinks = listOf();
        List<Rec> ol = listOf(rec1, rec2, rec1);

        rec1.smap = MapUtilities.mapOf();

        String json = TestUtil.toJson(ol, new WriteOptionsBuilder().withExtendedAliases().build());

        List es = TestUtil.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), null);
        json = TestUtil.toJson(es, new WriteOptionsBuilder().withExtendedAliases().build());

        List again = TestUtil.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), null);
        json = TestUtil.toJson(again, new WriteOptionsBuilder().withExtendedAliases().build());

        assert DeepEquals.deepEquals(ol, es);
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
        
        Object[] lists = new Object[] {l0, l1, l2, l3, l4};
        String json = TestUtil.toJson(lists, new WriteOptionsBuilder().withExtendedAliases().build());
        
        Object[] newLists = TestUtil.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), null);
        assert DeepEquals.deepEquals(lists, newLists);
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
        String json = TestUtil.toJson(sets, new WriteOptionsBuilder().withExtendedAliases().build());

        Object[] newLists = TestUtil.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), null);
        assert DeepEquals.deepEquals(sets, newLists);
    }

    @Test
    void testForwardReferenceImmutableList() {
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");

        // Creating forward reference
        node1.next = node2;
        node2.next = node1; // Forward reference: node2 refers back to node1

        List<Node> nodes = listOf(node1, node2);

        // Serialize the list
        String json = JsonIo.toJson(nodes, new WriteOptionsBuilder().withExtendedAliases().build());

        // Deserialize the list
        List<Node> deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), List.class);

        // Assertions to check if the forward reference is maintained after deserialization
        assertEquals("Node2", deserializedNodes.get(0).next.name);
        assertEquals("Node1", deserializedNodes.get(1).next.name);

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
        deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), List.class);
        // Assertions to check if the forward reference is maintained after deserialization
        assertEquals("Node1", deserializedNodes.get(0).next.name);
        assertEquals("Node2", deserializedNodes.get(1).next.name);
    }

    @Test
    void testForwardReferenceImmutableSet() {
        Node node1 = new Node("Node1");
        Node node2 = new Node("Node2");

        // Creating forward reference
        node1.next = node2;
        node2.next = node1; // Forward reference: node2 refers back to node1

        Set<Node> nodes = setOf(node1, node2);

        // Serialize the list
        String json = JsonIo.toJson(nodes, new WriteOptionsBuilder().withExtendedAliases().build());
        
        // Deserialize the list
        Set<Node> deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), Set.class);

        // Assertions to check if the forward reference is maintained after deserialization
        Iterator<Node> i = deserializedNodes.iterator();
        Node node1st = i.next();
        Node node2nd = i.next();
        assertEquals("Node2", node1st.next.name);
        assertEquals("Node1", node2nd.next.name);

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
        deserializedNodes = JsonIo.toObjects(json, new ReadOptionsBuilder().withExtendedAliases().build(), Set.class);
        // Assertions to check if the forward reference is maintained after deserialization
        i = deserializedNodes.iterator();
        node1st = i.next();
        node2nd = i.next();
        assertEquals("Node2", node1st.next.name);
        assertEquals("Node1", node2nd.next.name);
    }
}