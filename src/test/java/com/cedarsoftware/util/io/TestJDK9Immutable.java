package com.cedarsoftware.util.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class TestJDK9Immutable {

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
        final Object o = new ArrayList<>(List.of());

        String json = JsonWriter.objectToJson(o);
        List<?> es = (List<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOf() {
        final Object o = List.of();

        String json = JsonWriter.objectToJson(o);
        List<?> es = (List<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOfOne() {
        final Object o = List.of("One");

        String json = JsonWriter.objectToJson(o);
        List<?> es = (List<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(1, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOfTwo() {
        final Object o = List.of("One", "Two");

        String json = JsonWriter.objectToJson(o);
        List<?> es = (List<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(2, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOfThree() {
        final Object o = List.of("One", "Two", "Three");

        String json = JsonWriter.objectToJson(o);
        List<?> es = (List<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(3, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOf() {
        final Object o = Set.of();

        String json = JsonWriter.objectToJson(o);
        Set<?> es = (Set<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOfOne() {
        final Object o = Set.of("One");

        String json = JsonWriter.objectToJson(o);
        Set<?> es = (Set<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(1, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOfTwo() {
        final Object o = Set.of("One", "Two");

        String json = JsonWriter.objectToJson(o);
        Set<?> es = (Set<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(2, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOfThree() {
        final Object o = Set.of("One", "Two", "Three");

        String json = JsonWriter.objectToJson(o);
        Set<?> es = (Set<?>) JsonReader.jsonToJava(json);

        Assert.assertEquals(3, es.size());
        Assert.assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOfThreeRecsMutableBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(List.of());
        rec2.mlinks = new ArrayList<>(List.of(rec1));
        List<Rec> ol = new ArrayList<>(List.of(rec1, rec2, rec1));

        String json = JsonWriter.objectToJson(ol);
        List<Rec> recs = (List<Rec>) JsonReader.jsonToJava(json);

        Assert.assertEquals(ol.getClass(), recs.getClass());
        Assert.assertEquals(ol.size(), recs.size());

        Assert.assertEquals(ol.get(0).s, recs.get(0).s);
        Assert.assertEquals(ol.get(0).i, recs.get(0).i);
        Assert.assertEquals(recs.get(1), recs.get(0).link);
        Assert.assertEquals(ol.get(1).s, recs.get(1).s);
        Assert.assertEquals(ol.get(1).i, recs.get(1).i);
        Assert.assertEquals(recs.get(0), recs.get(1).link);

        Assert.assertEquals(recs.get(0), recs.get(2));

        Assert.assertEquals(ol.get(0).mlinks.size(), recs.get(0).mlinks.size());
        Assert.assertEquals(ol.get(0).mlinks.getClass(), recs.get(0).mlinks.getClass());
        Assert.assertEquals(ol.get(1).mlinks.size(), recs.get(1).mlinks.size());
        Assert.assertEquals(ol.get(1).mlinks.getClass(), recs.get(1).mlinks.getClass());
    }

    @Test
    public void testListOfThreeRecsMutableInside() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(List.of());
        rec2.mlinks = new ArrayList<>(List.of(rec1));
        List<Rec> ol = List.of(rec1, rec2, rec1);

        String json = JsonWriter.objectToJson(ol);
        List<Rec> recs = (List<Rec>) JsonReader.jsonToJava(json);

        Assert.assertEquals(ol.getClass(), recs.getClass());
        Assert.assertEquals(ol.size(), recs.size());

        Assert.assertEquals(ol.get(0).s, recs.get(0).s);
        Assert.assertEquals(ol.get(0).i, recs.get(0).i);
        Assert.assertEquals(recs.get(1), recs.get(0).link);
        Assert.assertEquals(ol.get(1).s, recs.get(1).s);
        Assert.assertEquals(ol.get(1).i, recs.get(1).i);
        Assert.assertEquals(recs.get(0), recs.get(1).link);

        Assert.assertEquals(recs.get(0), recs.get(2));

        Assert.assertEquals(ol.get(0).mlinks.size(), recs.get(0).mlinks.size());
        Assert.assertEquals(ol.get(0).mlinks.getClass(), recs.get(0).mlinks.getClass());
        Assert.assertEquals(ol.get(1).mlinks.size(), recs.get(1).mlinks.size());
        Assert.assertEquals(ol.get(1).mlinks.getClass(), recs.get(1).mlinks.getClass());
    }

    @Test
    public void testListOfThreeRecsBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.ilinks = List.of(rec2);
        rec2.ilinks = List.of();
        rec1.mlinks = new ArrayList<>(List.of());
        rec2.mlinks = new ArrayList<>(List.of(rec1));
        List<Rec> ol = List.of(rec1, rec2, rec1);

        String json = JsonWriter.objectToJson(ol);
        Object es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(((Object) ol).getClass(), es.getClass());

        List<Rec> recs = (List<Rec>) es;
        Assert.assertEquals(ol.size(), recs.size());

        Assert.assertEquals(ol.get(0).s, recs.get(0).s);
        Assert.assertEquals(ol.get(0).i, recs.get(0).i);
        Assert.assertEquals(recs.get(1), recs.get(0).link);
        Assert.assertEquals(ol.get(1).s, recs.get(1).s);
        Assert.assertEquals(ol.get(1).i, recs.get(1).i);
        Assert.assertEquals(recs.get(0), recs.get(1).link);

        Assert.assertEquals(recs.get(0), recs.get(2));

        Assert.assertEquals(ol.get(0).mlinks.size(), recs.get(0).mlinks.size());
        Assert.assertEquals(ol.get(0).mlinks.getClass(), recs.get(0).mlinks.getClass());
        Assert.assertEquals(ol.get(1).mlinks.size(), recs.get(1).mlinks.size());
        Assert.assertEquals(ol.get(1).mlinks.getClass(), recs.get(1).mlinks.getClass());

        Assert.assertEquals(ol.get(0).ilinks.getClass(), recs.get(0).ilinks.getClass());
        Assert.assertEquals(ol.get(0).ilinks.size(), recs.get(0).ilinks.size());
        Assert.assertEquals(ol.get(1).ilinks.getClass(), recs.get(1).ilinks.getClass());
        Assert.assertEquals(ol.get(1).ilinks.size(), recs.get(1).ilinks.size());
    }

    @Test
    public void testListOfThreeRecsImmutableOnly() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.ilinks = List.of(rec2, rec1);
        rec2.ilinks = List.of();
        List<Rec> ol = List.of(rec1, rec2, rec1);

		rec1.smap = Map.of();

        String json = JsonWriter.objectToJson(ol);
        Object es = JsonReader.jsonToJava(json);

        Assert.assertEquals(((Object) ol).getClass(), es.getClass());

        List<Rec> recs = (List<Rec>) es;
        Assert.assertEquals(ol.size(), recs.size());

        Assert.assertEquals(ol.get(0).s, recs.get(0).s);
        Assert.assertEquals(ol.get(0).i, recs.get(0).i);
        Assert.assertEquals(ol.get(1).s, recs.get(1).s);
        Assert.assertEquals(ol.get(1).i, recs.get(1).i);

        Assert.assertEquals(recs.get(0), recs.get(2));

        Assert.assertEquals(ol.get(0).ilinks.getClass(), recs.get(0).ilinks.getClass());
        Assert.assertEquals(ol.get(0).ilinks.size(), recs.get(0).ilinks.size());
        Assert.assertEquals(ol.get(1).ilinks.getClass(), recs.get(1).ilinks.getClass());
        Assert.assertEquals(ol.get(1).ilinks.size(), recs.get(1).ilinks.size());
    }
}
