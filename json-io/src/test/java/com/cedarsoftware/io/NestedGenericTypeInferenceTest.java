package com.cedarsoftware.io;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for type inference of nested generic types when deserializing JSON
 * that does NOT contain @type markers. This is important for interoperability
 * with JSON produced by other libraries (Jackson, Gson, etc.).
 *
 * The key scenario: A POJO class with a List&lt;NestedClass&gt; field should properly
 * deserialize the list elements as NestedClass instances, not as Maps.
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
class NestedGenericTypeInferenceTest {

    // ========== Test Model Classes ==========

    /**
     * Container class with a List of nested User objects.
     * This mimics the structure used in java-json-benchmark.
     */
    public static class Users {
        private List<User> users;

        public List<User> getUsers() {
            return users;
        }

        public void setUsers(List<User> users) {
            this.users = users;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Users)) return false;
            Users that = (Users) o;
            return Objects.equals(users, that.users);
        }

        @Override
        public int hashCode() {
            return Objects.hash(users);
        }

        @Override
        public String toString() {
            return "Users{users=" + users + "}";
        }

        /**
         * Nested static inner class representing a user.
         */
        public static class User {
            private String id;
            private String name;
            private int age;
            private List<Friend> friends;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            public int getAge() { return age; }
            public void setAge(int age) { this.age = age; }

            public List<Friend> getFriends() { return friends; }
            public void setFriends(List<Friend> friends) { this.friends = friends; }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof User)) return false;
                User user = (User) o;
                return age == user.age &&
                        Objects.equals(id, user.id) &&
                        Objects.equals(name, user.name) &&
                        Objects.equals(friends, user.friends);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, name, age, friends);
            }

            @Override
            public String toString() {
                return "User{id=" + id + ", name=" + name + ", age=" + age + ", friends=" + friends + "}";
            }
        }

        /**
         * Deeply nested class to test multi-level generic inference.
         */
        public static class Friend {
            private String id;
            private String name;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Friend)) return false;
                Friend friend = (Friend) o;
                return Objects.equals(id, friend.id) && Objects.equals(name, friend.name);
            }

            @Override
            public int hashCode() {
                return Objects.hash(id, name);
            }

            @Override
            public String toString() {
                return "Friend{id=" + id + ", name=" + name + "}";
            }
        }
    }

    // ========== Tests ==========

    /**
     * Test that a simple container with List&lt;NestedClass&gt; properly infers types.
     * JSON without @type markers should still deserialize to proper Java objects.
     */
    @Test
    void testNestedListTypeInference() {
        // JSON without any @type markers - like what Jackson/Gson would produce
        String json = "{\"users\": [{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}, {\"id\": \"2\", \"name\": \"Bob\", \"age\": 25}]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asClass(Users.class);

        assertNotNull(result, "Result should not be null");
        assertNotNull(result.getUsers(), "Users list should not be null");
        assertEquals(2, result.getUsers().size(), "Should have 2 users");

        // This is the key assertion - elements should be User instances, not Maps
        Object firstElement = result.getUsers().get(0);
        assertTrue(firstElement instanceof Users.User,
                "List elements should be User instances, not " + firstElement.getClass().getName());

        Users.User alice = result.getUsers().get(0);
        assertEquals("1", alice.getId());
        assertEquals("Alice", alice.getName());
        assertEquals(30, alice.getAge());

        Users.User bob = result.getUsers().get(1);
        assertEquals("2", bob.getId());
        assertEquals("Bob", bob.getName());
        assertEquals(25, bob.getAge());
    }

    /**
     * Test deeply nested generic types (List&lt;User&gt; where User has List&lt;Friend&gt;).
     */
    @Test
    void testDeeplyNestedListTypeInference() {
        String json = "{\"users\": [{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30, \"friends\": [{\"id\": \"f1\", \"name\": \"Charlie\"}, {\"id\": \"f2\", \"name\": \"Diana\"}]}]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asClass(Users.class);

        assertNotNull(result);
        assertEquals(1, result.getUsers().size());

        // Check first level nesting
        Object firstUser = result.getUsers().get(0);
        assertTrue(firstUser instanceof Users.User,
                "First level nested objects should be User instances");

        Users.User alice = (Users.User) firstUser;
        assertEquals("Alice", alice.getName());
        assertNotNull(alice.getFriends());
        assertEquals(2, alice.getFriends().size());

        // Check second level nesting
        Object firstFriend = alice.getFriends().get(0);
        assertTrue(firstFriend instanceof Users.Friend,
                "Second level nested objects should be Friend instances, not " + firstFriend.getClass().getName());

        Users.Friend charlie = (Users.Friend) firstFriend;
        assertEquals("f1", charlie.getId());
        assertEquals("Charlie", charlie.getName());
    }

    /**
     * Test with TypeHolder - explicit type specification.
     */
    @Test
    void testWithTypeHolder() {
        String json = "{\"users\": [{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asType(new TypeHolder<Users>() {});

        assertNotNull(result);
        assertEquals(1, result.getUsers().size());

        Object firstElement = result.getUsers().get(0);
        assertTrue(firstElement instanceof Users.User,
                "List elements should be User instances when using TypeHolder");
    }

    /**
     * Test round-trip: serialize with json-io, then deserialize.
     * This should work because json-io adds @type markers.
     */
    @Test
    void testRoundTrip() {
        // Create test data
        Users original = new Users();
        Users.User alice = new Users.User();
        alice.setId("1");
        alice.setName("Alice");
        alice.setAge(30);

        Users.Friend charlie = new Users.Friend();
        charlie.setId("f1");
        charlie.setName("Charlie");
        alice.setFriends(java.util.Arrays.asList(charlie));

        original.setUsers(java.util.Arrays.asList(alice));

        // Serialize (will include @type markers by default)
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(original, writeOptions);

        // Deserialize
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asClass(Users.class);

        assertEquals(original, result, "Round-trip should preserve object equality");
    }

    /**
     * Test with empty lists.
     */
    @Test
    void testEmptyNestedLists() {
        String json = "{\"users\": []}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asClass(Users.class);

        assertNotNull(result);
        assertNotNull(result.getUsers());
        assertTrue(result.getUsers().isEmpty());
    }

    /**
     * Test with null list.
     */
    @Test
    void testNullNestedList() {
        String json = "{\"users\": null}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Users result = JsonIo.toJava(json, readOptions).asClass(Users.class);

        assertNotNull(result);
        assertNull(result.getUsers());
    }

    // ========== Additional test with top-level class (non-inner) ==========

    /**
     * Simpler test case with non-inner classes to isolate the issue.
     */
    @Test
    void testSimpleContainerWithList() {
        String json = "{\"items\": [{\"value\": 10}, {\"value\": 20}]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        SimpleContainer result = JsonIo.toJava(json, readOptions).asClass(SimpleContainer.class);

        assertNotNull(result);
        assertNotNull(result.getItems());
        assertEquals(2, result.getItems().size());

        Object firstElement = result.getItems().get(0);
        assertTrue(firstElement instanceof SimpleItem,
                "List elements should be SimpleItem instances, not " + firstElement.getClass().getName());

        assertEquals(10, result.getItems().get(0).getValue());
        assertEquals(20, result.getItems().get(1).getValue());
    }

    // ========== Map Type Inference Tests ==========

    /**
     * Test that Map<String, NestedClass> properly infers value types.
     */
    @Test
    void testMapValueTypeInference() {
        String json = "{\"userMap\": {\"alice\": {\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}, \"bob\": {\"id\": \"2\", \"name\": \"Bob\", \"age\": 25}}}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        UserMapContainer result = JsonIo.toJava(json, readOptions).asClass(UserMapContainer.class);

        assertNotNull(result);
        assertNotNull(result.getUserMap());
        assertEquals(2, result.getUserMap().size());

        // Key assertion - values should be User instances, not Maps
        Object aliceValue = result.getUserMap().get("alice");
        assertTrue(aliceValue instanceof Users.User,
                "Map values should be User instances, not " + aliceValue.getClass().getName());

        Users.User alice = (Users.User) aliceValue;
        assertEquals("1", alice.getId());
        assertEquals("Alice", alice.getName());
        assertEquals(30, alice.getAge());
    }

    /**
     * Test deeply nested Map with List values: Map<String, List<User>>
     */
    @Test
    void testMapWithListValueTypeInference() {
        String json = "{\"departments\": {\"engineering\": [{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}], \"sales\": [{\"id\": \"2\", \"name\": \"Bob\", \"age\": 25}]}}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        DepartmentContainer result = JsonIo.toJava(json, readOptions).asClass(DepartmentContainer.class);

        assertNotNull(result);
        assertNotNull(result.getDepartments());
        assertEquals(2, result.getDepartments().size());

        List<Users.User> engineering = result.getDepartments().get("engineering");
        assertNotNull(engineering);
        assertEquals(1, engineering.size());

        Object firstEngineer = engineering.get(0);
        assertTrue(firstEngineer instanceof Users.User,
                "Nested list elements should be User instances, not " + firstEngineer.getClass().getName());

        Users.User alice = (Users.User) firstEngineer;
        assertEquals("Alice", alice.getName());
    }

    // ========== Array Type Inference Tests ==========

    /**
     * Test that User[] properly infers element types.
     */
    @Test
    void testArrayElementTypeInference() {
        String json = "{\"users\": [{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}, {\"id\": \"2\", \"name\": \"Bob\", \"age\": 25}]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        UserArrayContainer result = JsonIo.toJava(json, readOptions).asClass(UserArrayContainer.class);

        assertNotNull(result);
        assertNotNull(result.getUsers());
        assertEquals(2, result.getUsers().length);

        // Key assertion - elements should be User instances
        assertTrue(result.getUsers()[0] instanceof Users.User,
                "Array elements should be User instances, not " + result.getUsers()[0].getClass().getName());

        assertEquals("Alice", result.getUsers()[0].getName());
        assertEquals("Bob", result.getUsers()[1].getName());
    }

    /**
     * Test triple-nested generics: List<List<List<User>>>
     */
    @Test
    void testTripleNestedListTypeInference() {
        String json = "{\"groups\": [[[{\"id\": \"1\", \"name\": \"Alice\", \"age\": 30}]]]}";

        ReadOptions readOptions = new ReadOptionsBuilder().build();
        TripleNestedContainer result = JsonIo.toJava(json, readOptions).asClass(TripleNestedContainer.class);

        assertNotNull(result);
        assertNotNull(result.getGroups());
        assertEquals(1, result.getGroups().size());

        List<List<Users.User>> level1 = result.getGroups().get(0);
        assertNotNull(level1);
        assertEquals(1, level1.size());

        List<Users.User> level2 = level1.get(0);
        assertNotNull(level2);
        assertEquals(1, level2.size());

        Object deepElement = level2.get(0);
        assertTrue(deepElement instanceof Users.User,
                "Deeply nested elements should be User instances, not " + deepElement.getClass().getName());

        assertEquals("Alice", ((Users.User) deepElement).getName());
    }

    // Container class for List<List<List<User>>>
    public static class TripleNestedContainer {
        private List<List<List<Users.User>>> groups;

        public List<List<List<Users.User>>> getGroups() { return groups; }
        public void setGroups(List<List<List<Users.User>>> groups) { this.groups = groups; }
    }

    // Container class for User[]
    public static class UserArrayContainer {
        private Users.User[] users;

        public Users.User[] getUsers() { return users; }
        public void setUsers(Users.User[] users) { this.users = users; }
    }

    // Container class for Map<String, User>
    public static class UserMapContainer {
        private java.util.Map<String, Users.User> userMap;

        public java.util.Map<String, Users.User> getUserMap() { return userMap; }
        public void setUserMap(java.util.Map<String, Users.User> userMap) { this.userMap = userMap; }
    }

    // Container class for Map<String, List<User>>
    public static class DepartmentContainer {
        private java.util.Map<String, List<Users.User>> departments;

        public java.util.Map<String, List<Users.User>> getDepartments() { return departments; }
        public void setDepartments(java.util.Map<String, List<Users.User>> departments) { this.departments = departments; }
    }

    // Helper classes for simpler test
    public static class SimpleContainer {
        private List<SimpleItem> items;

        public List<SimpleItem> getItems() { return items; }
        public void setItems(List<SimpleItem> items) { this.items = items; }
    }

    public static class SimpleItem {
        private int value;

        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleItem)) return false;
            return value == ((SimpleItem) o).value;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }
}
