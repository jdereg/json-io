package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SingletonTest
{
    @Test
    public void testSingletonListSerialization()
    {
        List<String> strings = Collections.singletonList("Foo");
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = JsonWriter.objectToJson(strings);
        Object o = JsonReader.toObjects(json);
    }

    @Test
    public void testSingletonSetSerialization()
    {
        Set<String> strings = Collections.singleton("One time");
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = JsonWriter.objectToJson(strings);
        Object o = JsonReader.toObjects(json);
    }

    @Test
    public void testSingletonMapSerialization()
    {
        Map<String, Double> strings = Collections.singletonMap("bitcoin", 35000.0);
        // Simple object with a list field, behaves the same with a class

        // Serialize foo with a singletonList, then deserialization throws
        String json = JsonWriter.objectToJson(strings);
        Object o = JsonReader.toObjects(json);
    }
}