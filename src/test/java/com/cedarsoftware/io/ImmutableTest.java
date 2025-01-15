package com.cedarsoftware.io;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class ImmutableTest
{
    @Test
    public void testImmmutableList()
    {
        List<String> strings = new LinkedList<>();
        strings.add("Foo");
        strings.add("Bar");
        strings = Collections.unmodifiableList(strings);
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = TestUtil.toJson(strings);
        Object o = TestUtil.toObjects(json, null);
    }

    @Test
    public void testImmmutableSet()
    {
        Set<String> strings = new LinkedHashSet<>();
        strings.add("Foo");
        strings.add("Bar");
        strings = Collections.unmodifiableSet(strings);
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a unmodifiableSet, then deserialization comes in with SeableSet
        String json = TestUtil.toJson(strings);
        Object o = TestUtil.toObjects(json, null);
    }

    @Test
    public void testImmutableMap()
    {
        Map<String, Double> strings = new HashMap<>();
        strings.put("BTC", 35000.0);
        strings.put("ETH",1900.0);
        strings.put("LINK", 14.0);
        strings.put("SOL", 43.0);
        Collections.unmodifiableMap(strings);
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = TestUtil.toJson(strings, null);
        Object o = TestUtil.toObjects(json, null);
    }
}