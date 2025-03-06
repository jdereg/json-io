package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
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
public class SetTest {
    @Test
    public void testSet() {
        ManySets set = new ManySets();
        set.init();
        String json = TestUtil.toJson(set);

        ManySets testSet = TestUtil.toObjects(json, null);
        TestUtil.printLine("json = " + json);

        assertEquals(26, testSet._treeSet.size());
        assertEquals(26, testSet._hashSet.size());
        assertTrue(testSet._treeSet.containsAll(testSet._hashSet));
        assertTrue(testSet._hashSet.containsAll(testSet._treeSet));
        assertEquals("alpha", testSet._treeSet.iterator().next());
        assertTrue(testSet._enumSet.containsAll(EnumSet.allOf(ManySets.EnumValues.class)));
        assertTrue(testSet._emptyEnumSet.containsAll(EnumSet.allOf(ManySets.EmptyValues.class)));
        assertTrue(testSet._setOfEnums.containsAll(EnumSet.allOf(ManySets.EnumValues.class)));

        testSet._enumSet.remove(ManySets.EnumValues.E1);
        testSet._enumSet.remove(ManySets.EnumValues.E2);
        testSet._enumSet.remove(ManySets.EnumValues.E3);
        json = TestUtil.toJson(testSet);
        TestUtil.printLine(json);
        testSet = TestUtil.toObjects(json, null);
        testSet._enumSet.add(ManySets.EnumValues.E1);
    }

    /**
     * Test Scenario 1:
     * Deserialize an Object[] containing Strings to Set.class in default mode.
     */
    @Test
    void testObjectArrayToSet_DefaultMode() {
        // Step 1: Create an Object[] of Strings
        Object[] originalArray = new Object[]{"a", "b", "c"};

        // Step 2: Serialize the Object[] to JSON
        String json = TestUtil.toJson(originalArray);

        // Step 3: Deserialize the JSON to Set.class in default mode
        ReadOptions readOptions = new ReadOptionsBuilder().build(); // Default options
        Set<String> deserializedSet = (Set<String>) TestUtil.toObjects(json, readOptions, Set.class);

        // Step 4: Assertions
        assertNotNull(deserializedSet, "Deserialized set should not be null");
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), deserializedSet, "Deserialized set should match the original array elements");
        assertTrue(deserializedSet instanceof HashSet, "Deserialized set should be an instance of HashSet");
    }

    /**
     * Test Scenario 2:
     * Deserialize an Object[] containing Strings to Set.class with returnJsonObjects=true.
     */
    @Test
    void testObjectArrayToSet_ReturnJsonObjectsMode() {
        // Step 1: Create an Object[] of Strings
        Object[] originalArray = new Object[]{"a", "b", "c"};

        // Step 2: Serialize the Object[] to JSON
        String json = TestUtil.toJson(originalArray);

        // Step 3: Deserialize the JSON to Set.class with returnJsonObjects=true
        ReadOptions readOptions = new ReadOptionsBuilder()
                .returnAsJsonObjects()
                .build();
        Set<?> deserializedSet = (Set<?>) TestUtil.toObjects(json, readOptions, Set.class);

        // Step 4: Assertions
        assertNotNull(deserializedSet, "Deserialized set should not be null");
        assertEquals(3, deserializedSet.size(), "Deserialized set should contain 3 elements");
        assertTrue(deserializedSet.contains("a"), "Set should contain 'a'");
        assertTrue(deserializedSet.contains("b"), "Set should contain 'b'");
        assertTrue(deserializedSet.contains("c"), "Set should contain 'c'");
        // In returnJsonObjects mode, the set might be a JsonObject representing the Set
        // Verify the actual implementation if necessary
        // For json-io, it might still return a Set instance, but with internal JsonObjects if applicable
    }

    /**
     * Test Scenario 3:
     * Deserialize an Object[] containing Strings to Set.class with returnJavaObjects=true.
     */
    @Test
    void testObjectArrayToSet_ReturnJavaObjectsMode() {
        // Step 1: Create an Object[] of Strings
        Object[] originalArray = new Object[]{"a", "b", "c"};

        // Step 2: Serialize the Object[] to JSON
        String json = TestUtil.toJson(originalArray);

        // Step 3: Deserialize the JSON to Set.class with returnJavaObjects=true
        ReadOptions readOptions = new ReadOptionsBuilder()
                .returnAsJavaObjects() // Ensuring returnJavaObjects=true
                .build();
        Set<String> deserializedSet = (Set<String>) TestUtil.toObjects(json, readOptions, Set.class);

        // Step 4: Assertions
        assertNotNull(deserializedSet, "Deserialized set should not be null");
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), deserializedSet, "Deserialized set should match the original array elements");
        assertTrue(deserializedSet instanceof TreeSet || deserializedSet instanceof HashSet || deserializedSet instanceof LinkedHashSet,
                "Deserialized set should be an instance of a Set implementation");
    }

    /**
     * Test Scenario 4:
     * Deserialize a Set<String> to Object[].class in default mode.
     */
    @Test
    void testSetToObjectArray_DefaultMode() {
        // Step 1: Create a Set<String>
        Set<String> originalSet = new HashSet<>(Arrays.asList("x", "y", "z"));

        // Step 2: Serialize the Set to JSON
        String json = TestUtil.toJson(originalSet);

        // Step 3: Deserialize the JSON to Object[].class in default mode
        ReadOptions readOptions = new ReadOptionsBuilder().build(); // Default options
        Object[] deserializedArray = TestUtil.toObjects(json, readOptions, Object[].class);

        // Step 4: Assertions
        assertNotNull(deserializedArray, "Deserialized array should not be null");
        assertEquals(3, deserializedArray.length, "Deserialized array should have 3 elements");
        assertTrue(Arrays.asList(deserializedArray).containsAll(originalSet), "Deserialized array should contain all elements from the original set");
    }

    /**
     * Test Scenario 5:
     * Deserialize a Set<String> to Object[].class with returnJsonObjects=true.
     */
    @Test
    void testSetToObjectArray_ReturnJsonObjectsMode() {
        // Step 1: Create a Set<String>
        Set<String> originalSet = new HashSet<>(Arrays.asList("x", "y", "z"));

        // Step 2: Serialize the Set to JSON
        String json = TestUtil.toJson(originalSet);

        // Step 3: Deserialize the JSON to Object[].class with returnJsonObjects=true
        ReadOptions readOptions = new ReadOptionsBuilder()
                .returnAsJsonObjects()
                .build();
        String[] deserializedArray = TestUtil.toObjects(json, readOptions, String[].class);

        // Step 4: Assertions
        assertNotNull(deserializedArray, "Deserialized array should not be null");
        assertEquals(originalSet.size(), deserializedArray.length, "Array length should match set size");

        // Convert array to set
        Set<Object> arrayAsSet = new HashSet<>();
        for (Object obj : deserializedArray) {
            arrayAsSet.add(obj);
        }

        assertEquals(originalSet, arrayAsSet,
                "Original set: " + originalSet + ", Array as set: " + arrayAsSet);
    }
    
    /**
     * Test Scenario 6:
     * Deserialize a Set<String> to Object[].class with returnJavaObjects=true.
     */
    @Test
    void testSetToObjectArray_ReturnJavaObjectsMode() {
        // Step 1: Create a Set<String>
        Set<String> originalSet = new LinkedHashSet<>(Arrays.asList("x", "y", "z")); // Using LinkedHashSet to preserve order

        // Step 2: Serialize the Set to JSON
        String json = TestUtil.toJson(originalSet);

        // Step 3: Deserialize the JSON to Object[].class with returnJavaObjects=true
        ReadOptions readOptions = new ReadOptionsBuilder()
                .returnAsJavaObjects() // Ensuring returnJavaObjects=true
                .build();
        Object[] deserializedArray = TestUtil.toObjects(json, readOptions, Object[].class);

        // Step 4: Assertions
        assertNotNull(deserializedArray, "Deserialized array should not be null");
        assertEquals(3, deserializedArray.length, "Deserialized array should have 3 elements");
        assertTrue(Arrays.asList(deserializedArray).containsAll(originalSet), "Deserialized array should contain all elements from the original set");
    }

    /**
     * Additional Utility Method for Printing Set Contents (Optional)
     * Helps in debugging by printing the contents of a Set.
     */
    private void printSetContents(Set<?> set) {
        System.out.println("Set contents:");
        for (Object obj : set) {
            System.out.println(obj);
        }
    }


    public static class ManySets implements Serializable {
        private void init() {
            _hashSet = new HashSet();
            _hashSet.add("alpha");
            _hashSet.add("bravo");
            _hashSet.add("charlie");
            _hashSet.add("delta");
            _hashSet.add("echo");
            _hashSet.add("foxtrot");
            _hashSet.add("golf");
            _hashSet.add("hotel");
            _hashSet.add("indigo");
            _hashSet.add("juliet");
            _hashSet.add("kilo");
            _hashSet.add("lima");
            _hashSet.add("mike");
            _hashSet.add("november");
            _hashSet.add("oscar");
            _hashSet.add("papa");
            _hashSet.add("quebec");
            _hashSet.add("romeo");
            _hashSet.add("sierra");
            _hashSet.add("tango");
            _hashSet.add("uniform");
            _hashSet.add("victor");
            _hashSet.add("whiskey");
            _hashSet.add("xray");
            _hashSet.add("yankee");
            _hashSet.add("zulu");

            _treeSet = new TreeSet<>();
            _treeSet.addAll(_hashSet);

            _enumSet = EnumSet.allOf(EnumValues.class);
            _emptyEnumSet = EnumSet.allOf(EmptyValues.class);
            _setOfEnums = EnumSet.allOf(EnumValues.class);
        }

        protected ManySets() {
        }

        private Set _hashSet;
        private Set _treeSet;
        private EnumSet<EnumValues> _enumSet;
        private EnumSet<EmptyValues> _emptyEnumSet;
        private EnumSet<EnumValues> _setOfEnums;

        private static enum EnumValues {
            E1, E2, E3;
        }

        private static enum EmptyValues {
            ;
        }
    }
}
