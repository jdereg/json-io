package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
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
public class CompactMapSetTest {
    @Test
    void testCompactMap() {
        Map map = CompactMap.builder().insertionOrder().build();
        map.put("a", "alpha");
        map.put("b", "beta");
        map.put("c", "charlie");
        map.put("d", "delta");
        String json = JsonIo.toJson(map, WriteOptionsBuilder.getDefaultWriteOptions());
        Map map2 = JsonIo.toJava(json, ReadOptionsBuilder.getDefaultReadOptions()).asType(new TypeHolder<Map<String, String>>(){});
        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(map, map2, options);
        if (!equals) {
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);
    }

    @Test
    void testCompactSet() {
        Set<String> set = CompactSet.<String>builder().insertionOrder().build();
        set.add("alpha");
        set.add("beta");
        set.add("charlie");
        set.add("delta");
        String json = JsonIo.toJson(set, WriteOptionsBuilder.getDefaultWriteOptions());
        Set<String> set2 = JsonIo.toJava(json, ReadOptionsBuilder.getDefaultReadOptions()).asType(new TypeHolder<Set<String>>() {});
        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, set2, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);
    }

    @Test
    void testCompactSetDefaultConfig() {
        // Test with default configuration
        CompactSet<String> set = new CompactSet<>();
        set.add("alpha");
        set.add("beta");
        set.add("charlie");

        String json = JsonIo.toJson(set, null);
        CompactSet<String> deserializedSet = JsonIo.toJava(json, null).asType(new TypeHolder<CompactSet<String>>(){});

        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, deserializedSet, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);

        // Verify the configuration was preserved
        Map<String, Object> originalConfig = set.getConfig();
        Map<String, Object> deserializedConfig = deserializedSet.getConfig();

        assertEquals(originalConfig.get(CompactMap.COMPACT_SIZE), deserializedConfig.get(CompactMap.COMPACT_SIZE));
        assertEquals(originalConfig.get(CompactMap.CASE_SENSITIVE), deserializedConfig.get(CompactMap.CASE_SENSITIVE));
        assertEquals(originalConfig.get(CompactMap.ORDERING), deserializedConfig.get(CompactMap.ORDERING));
    }

    @Test
    void testCompactSetWithCustomConfig() {
        // Test with custom configuration
        CompactSet<String> set = CompactSet.<String>builder()
                .compactSize(30)
                .caseSensitive(false)
                .sortedOrder()
                .build();

        set.add("Charlie");
        set.add("Alpha");
        set.add("Beta");

        // Serialization options
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .prettyPrint(true)
                .build();

        // Deserialization options
        ReadOptions readOptions = new ReadOptionsBuilder()
                .build();

        String json = JsonIo.toJson(set, writeOptions);
        CompactSet<String> deserializedSet = JsonIo.toJava(json, readOptions).asType(new TypeHolder<CompactSet<String>>(){});
        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, deserializedSet, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);

        // Verify the configuration was preserved
        Map<String, Object> originalConfig = set.getConfig();
        Map<String, Object> deserializedConfig = deserializedSet.getConfig();

        assertEquals(originalConfig.get(CompactMap.COMPACT_SIZE), deserializedConfig.get(CompactMap.COMPACT_SIZE));
        assertEquals(originalConfig.get(CompactMap.CASE_SENSITIVE), deserializedConfig.get(CompactMap.CASE_SENSITIVE));
        assertEquals(originalConfig.get(CompactMap.ORDERING), deserializedConfig.get(CompactMap.ORDERING));

        // Verify case insensitivity was preserved
        assertTrue(deserializedSet.contains("ALPHA"));
        assertTrue(deserializedSet.contains("beta"));

        // Verify order was preserved (should be sorted alphabetically)
        Iterator<String> iterator = deserializedSet.iterator();
        assertEquals("Alpha", iterator.next());
        assertEquals("Beta", iterator.next());
        assertEquals("Charlie", iterator.next());
    }

    @Test
    void testCompactSetWithComplexElements() {
        // Test with complex object elements
        CompactSet<Person> set = CompactSet.<Person>builder()
                .reverseOrder()  // Use reverse order
                .build();

        set.add(new Person("John", 30));
        set.add(new Person("Alice", 25));
        set.add(new Person("Bob", 35));

        String json = JsonIo.toJson(set, null);
        CompactSet<Person> deserializedSet = JsonIo.toJava(json, null).asType(new TypeHolder<CompactSet<Person>>(){});

        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, deserializedSet, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);

        // Verify reverse ordering was preserved
        Iterator<Person> iterator = deserializedSet.iterator();
        assertEquals("Bob", iterator.next().getName());   // Age 35
        assertEquals("John", iterator.next().getName());  // Age 30
        assertEquals("Alice", iterator.next().getName()); // Age 25
    }

    @Test
    void testCompactSetConfigReflection() {
        // Test that configuration reflection works correctly
        CompactSet<String> set = CompactSet.<String>builder()
                .compactSize(25)
                .insertionOrder()
                .build();

        // Add in specific order to test insertion order
        set.add("third");
        set.add("first");
        set.add("second");

        String json = JsonIo.toJson(set, null);
        CompactSet<String> deserializedSet = JsonIo.toJava(json, null).asType(new TypeHolder<CompactSet<String>>(){});
        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, deserializedSet, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);

        // Verify config was preserved
        assertEquals(25, deserializedSet.getConfig().get(CompactMap.COMPACT_SIZE));
        assertEquals(CompactMap.INSERTION, deserializedSet.getConfig().get(CompactMap.ORDERING));

        // Verify insertion order was preserved
        Iterator<String> iterator = deserializedSet.iterator();
        assertEquals("third", iterator.next());
        assertEquals("first", iterator.next());
        assertEquals("second", iterator.next());
    }

    @Test
    void testCompactSetWithNestedCollections() {
        // Test with nested collections
        CompactSet<Object> set = new CompactSet<>();

        // Add a nested CompactMap
        CompactMap<String, Integer> nestedMap = new CompactMap<>();
        nestedMap.put("one", 1);
        nestedMap.put("two", 2);
        set.add(nestedMap);

        // Add a nested CompactSet
        CompactSet<String> nestedSet = new CompactSet<>();
        nestedSet.add("a");
        nestedSet.add("b");
        set.add(nestedSet);

        // Add a list
        List<String> nestedList = Arrays.asList("x", "y", "z");
        set.add(nestedList);

        String json = JsonIo.toJson(set, null);
        CompactSet<Object> deserializedSet = JsonIo.toJava(json, null).asType(new TypeHolder<CompactSet<Object>>(){});
        Map <String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(set, deserializedSet, options);
        if (!equals) {
            System.out.println(json);
            System.out.println(options.get("diff"));
        }
        assertTrue(equals);

        // Verify structure and content of nested collections
        for (Object item : deserializedSet) {
            if (item instanceof Map) {
                Map<String, Integer> map = (Map<String, Integer>) item;
                assertEquals(2, map.size());
                assertEquals(Integer.valueOf(1), map.get("one"));
                assertEquals(Integer.valueOf(2), map.get("two"));
            } else if (item instanceof Set) {
                Set<String> nestedSetItem = (Set<String>) item;
                assertEquals(2, nestedSetItem.size());
                assertTrue(nestedSetItem.contains("a"));
                assertTrue(nestedSetItem.contains("b"));
            } else if (item instanceof List) {
                List<String> listItem = (List<String>) item;
                assertEquals(3, listItem.size());
                assertEquals("x", listItem.get(0));
                assertEquals("y", listItem.get(1));
                assertEquals("z", listItem.get(2));
            } else {
                fail("Unexpected item type in deserialized set: " + item.getClass());
            }
        }
    }

    // Helper class for testing complex objects
    static class Person implements Comparable<Person> {
        private String name;
        private int age;

        // Default constructor needed for deserialization
        public Person() { }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }

        public void setName(String name) { this.name = name; }
        public void setAge(int age) { this.age = age; }

        @Override
        public int compareTo(Person other) {
            return Integer.compare(this.age, other.age);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Person person = (Person) o;
            return age == person.age && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }

        @Override
        public String toString() {
            return "Person{name='" + name + "', age=" + age + '}';
        }
    }
}
