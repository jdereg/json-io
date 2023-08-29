package com.cedarsoftware.util.io;

import java.util.ArrayList;
import java.util.List;
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

        Rec link;
    }

    @Test
    public void testCopyOfListOf() {
        final Object o = new ArrayList<>(List.of());

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
    }

    @Test
    public void testListOf() {
        final Object o = List.of();

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
    }

    @Test
    public void testListOfOne() {
        final Object o = List.of("One");

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(1, es.size());
    }

    @Test
    public void testListOfTwo() {
        final Object o = List.of("One", "Two");

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(2, es.size());
    }

    @Test
    public void testListOfThree() {
        final Object o = List.of("One", "Two", "Three");

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(3, es.size());
    }

    @Test
    public void testSetOf() {
        final Object o = Set.of();

        String json = JsonWriter.objectToJson(o);
        Set es = (Set) JsonReader.jsonToJava(json);

        Assert.assertEquals(0, es.size());
    }

    @Test
    public void testSetOfOne() {
        final Object o = Set.of("One");

        String json = JsonWriter.objectToJson(o);
        Set es = (Set) JsonReader.jsonToJava(json);

        Assert.assertEquals(1, es.size());
    }

    @Test
    public void testSetOfTwo() {
        final Object o = Set.of("One", "Two");

        String json = JsonWriter.objectToJson(o);
        Set es = (Set) JsonReader.jsonToJava(json);

        Assert.assertEquals(2, es.size());
    }

    @Test
    public void testSetOfThree() {
        final Object o = Set.of("One", "Two", "Three");

        String json = JsonWriter.objectToJson(o);
        Set es = (Set) JsonReader.jsonToJava(json);

        Assert.assertEquals(3, es.size());
    }

    @Test
    public void testListOfThreeRecs() {
        Rec rec1 = new Rec("OneOrThree", 0);
        Rec rec2 = new Rec("Two", 2);
        rec1.link = rec2;
        rec2.link = rec1;
        final Object o = List.of(rec1, rec2, rec1);

        String json = JsonWriter.objectToJson(o);
        List es = (List) JsonReader.jsonToJava(json);

        Assert.assertEquals(o.getClass(), es.getClass());
        Assert.assertEquals(3, es.size());
    }
}
