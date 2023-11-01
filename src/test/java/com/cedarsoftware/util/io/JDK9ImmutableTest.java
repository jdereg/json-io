package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDK9ImmutableTest
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
        final Object o = new ArrayList<>(MetaUtils.listOf());

        String json = TestUtil.toJson(o);
        List<?> es = TestUtil.toJava(json);

        assertEquals(0, es.size());
        assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOf() {
        final Object o = MetaUtils.listOf();

        String json = TestUtil.toJson(o);
        List<?> es = TestUtil.toJava(json);

        assertEquals(0, es.size());
        assert DeepEquals.deepEquals(o, es);
    }

    @Test
    public void testListOfOne() {
        final Object o = MetaUtils.listOf("One");

        String json = TestUtil.toJson(o);
        List<?> es = TestUtil.toJava(json);

        assertEquals(1, es.size());
        assert DeepEquals.deepEquals(o, es);
    }

    @Test
    public void testListOfTwo() {
        final Object o = MetaUtils.listOf("One", "Two");

        String json = TestUtil.toJson(o);
        List<?> es = TestUtil.toJava(json);

        assertEquals(2, es.size());
        assert DeepEquals.deepEquals(o, es);
    }

    @Test
    public void testListOfThree() {
        final Object o = MetaUtils.listOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        List<?> es = TestUtil.toJava(json);

        assertEquals(3, es.size());
        assert DeepEquals.deepEquals(o, es);
    }

    @Test
    public void testSetOf() {
        final Object o = MetaUtils.setOf();

        String json = TestUtil.toJson(o);
        Set<?> es = TestUtil.toJava(json);

        assertEquals(0, es.size());
        assert Set.class.isAssignableFrom(o.getClass());
        assert Set.class.isAssignableFrom(es.getClass());
    }

    @Test
    public void testSetOfOne() {
        final Object o = MetaUtils.setOf("One");

        String json = TestUtil.toJson(o);
        Set<?> es = TestUtil.toJava(json);

        assertEquals(1, es.size());
        assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOfTwo() {
        final Object o = MetaUtils.setOf("One", "Two");

        String json = TestUtil.toJson(o);
        Set<?> es = TestUtil.toJava(json);

        assertEquals(2, es.size());
        assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testSetOfThree() {
        final Object o = MetaUtils.setOf("One", "Two", "Three");

        String json = TestUtil.toJson(o);
        Set<?> es = TestUtil.toJava(json);

        assertEquals(3, es.size());
        assertEquals(o.getClass(), es.getClass());
    }

    @Test
    public void testListOfThreeRecsMutableBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(MetaUtils.listOf());
        rec2.mlinks = new ArrayList<>(MetaUtils.listOf(rec1));
        List<Rec> ol = new ArrayList<>(MetaUtils.listOf(rec1, rec2, rec1));

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toJava(json);

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
    public void testListOfThreeRecsMutableInside() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.mlinks = new ArrayList<>(MetaUtils.listOf());
        rec2.mlinks = new ArrayList<>(MetaUtils.listOf(rec1));
        List<Rec> ol = MetaUtils.listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        List<Rec> recs = TestUtil.toJava(json);

        assert DeepEquals.deepEquals(ol, recs);
    }

    @Test
    public void testListOfThreeRecsBoth() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        rec1.ilinks = MetaUtils.listOf(rec2);
        rec2.ilinks = MetaUtils.listOf();
        rec1.mlinks = new ArrayList<>(MetaUtils.listOf());
        rec2.mlinks = new ArrayList<>(MetaUtils.listOf(rec1));
        List<Rec> ol = MetaUtils.listOf(rec1, rec2, rec1);

        String json = TestUtil.toJson(ol);
        Object es = TestUtil.toJava(json);

        assert DeepEquals.deepEquals(es, ol);
    }

    @Test
    public void testListOfThreeRecsImmutableOnly() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.ilinks = MetaUtils.listOf(rec2, rec1);
        rec2.ilinks = MetaUtils.listOf();
        List<Rec> ol = MetaUtils.listOf(rec1, rec2, rec1);

		rec1.smap = MetaUtils.mapOf();

        String json = TestUtil.toJson(ol);
        Object es = TestUtil.toJava(json);

        assert DeepEquals.deepEquals(ol, es);
    }
}
