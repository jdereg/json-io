package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the showRootTypeInfo() and omitRootTypeInfo() WriteOptions settings.
 * These control whether @type is written on the root object.
 *
 * Note: showRootTypeInfo() and omitRootTypeInfo() are only valid when using
 * showTypeInfoMinimal() (the default). Using them with showTypeInfoAlways()
 * or showTypeInfoNever() throws IllegalStateException.
 */
class RootTypeInfoTest {

    static class Person {
        String name;
        int age;

        Person() {}
        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    // ========== Valid usage tests (Minimal mode - the default) ==========

    @Test
    void testDefaultBehaviorShowsRootType() {
        // Default should show root type
        Person person = new Person("John", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();

        assertTrue(writeOptions.isShowingRootTypeInfo(), "Default should be to show root type");

        String json = JsonIo.toJson(person, writeOptions);
        assertTrue(json.contains("@type"), "Default should include @type on root");
        assertTrue(json.contains("Person"), "Should contain Person class name");
    }

    @Test
    void testOmitRootTypeInfo() {
        Person person = new Person("John", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        assertFalse(writeOptions.isShowingRootTypeInfo(), "Should be false after omitRootTypeInfo()");

        String json = JsonIo.toJson(person, writeOptions);
        assertFalse(json.contains("@type"), "Should NOT include @type on root when omitRootTypeInfo()");
        assertTrue(json.contains("\"name\""), "Should still contain name field");
        assertTrue(json.contains("\"John\""), "Should still contain name value");
    }

    @Test
    void testShowRootTypeInfoExplicit() {
        Person person = new Person("John", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()  // First omit
                .showRootTypeInfo()  // Then explicitly show
                .build();

        assertTrue(writeOptions.isShowingRootTypeInfo(), "Should be true after showRootTypeInfo()");

        String json = JsonIo.toJson(person, writeOptions);
        assertTrue(json.contains("@type"), "Should include @type when showRootTypeInfo() is called");
    }

    @Test
    void testCopyConstructorCopiesRootTypeInfoSetting() {
        WriteOptions original = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        WriteOptions copy = new WriteOptionsBuilder(original).build();

        assertFalse(copy.isShowingRootTypeInfo(), "Copy should preserve omitRootTypeInfo setting");
    }

    @Test
    void testRoundTripWithOmitRootTypeAndAsClass() {
        // This is the main use case: omit root type when reading with .asClass()
        Person person = new Person("Jane", 25);

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        String json = JsonIo.toJson(person, writeOptions);
        assertFalse(json.contains("@type"), "Should not have @type");

        // Read back using .asClass() to specify the type
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        Person restored = JsonIo.toJava(json, readOptions).asClass(Person.class);

        assertEquals("Jane", restored.name);
        assertEquals(25, restored.age);
    }

    @Test
    void testOmitRootTypeWithArrayRoot() {
        Person[] people = {new Person("A", 1), new Person("B", 2)};

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        String json = JsonIo.toJson(people, writeOptions);

        // No @type anywhere - root is omitted, and elements match declared component type (Person)
        assertFalse(json.contains("@type"), "No @type should appear - root omitted and elements match Person[]");
    }

    @Test
    void testOmitRootTypeWithCollectionRoot() {
        List<Person> people = new ArrayList<>();
        people.add(new Person("A", 1));
        people.add(new Person("B", 2));

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        String json = JsonIo.toJson(people, writeOptions);

        // Root is a bare array (no @type wrapper for the List itself)
        assertTrue(json.startsWith("["), "Root collection should start with [ not {@type");
        // Elements still have @type because List<Person> generic type is erased at runtime
        assertTrue(json.contains("@type"), "Elements need @type since generic type is erased");
    }

    @Test
    void testOmitRootTypeWithMapRoot() {
        Map<String, Person> people = new HashMap<>();
        people.put("first", new Person("A", 1));

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        String json = JsonIo.toJson(people, writeOptions);

        // Root map has no @type, just starts with the first key
        assertFalse(json.startsWith("{\"@type\""), "Root map should not start with @type");
        assertTrue(json.startsWith("{\"first\""), "Root map should start with first entry key");
        // Value still has @type because Map<String, Person> generic type is erased at runtime
        assertTrue(json.contains("@type"), "Person value needs @type since generic type is erased");
    }

    @Test
    void testOmitRootTypeDoesNotAffectNestedTypes() {
        // When omitRootTypeInfo is set, nested objects should still have @type when needed
        Map<String, Object> root = new HashMap<>();
        root.put("person", new Person("Nested", 99));

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .omitRootTypeInfo()
                .build();

        String json = JsonIo.toJson(root, writeOptions);

        // Root should not have @type
        assertFalse(json.startsWith("{\"@type\""), "Root should not have @type");

        // But nested Person should still have @type (since it's in an Object field)
        assertTrue(json.contains("Person"), "Nested Person should still have type info");
    }

    // ========== Always/Never mode behavior tests ==========

    @Test
    void testShowTypeInfoNeverNoRootType() {
        // When showTypeInfoNever() is set, no @type anywhere including root
        Person person = new Person("John", 30);

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .build();

        String json = JsonIo.toJson(person, writeOptions);
        assertFalse(json.contains("@type"), "showTypeInfoNever means no @type anywhere");
    }

    @Test
    void testShowTypeInfoAlwaysIncludesRootType() {
        // When showTypeInfoAlways() is set, @type appears on all objects including root
        Person person = new Person("John", 30);

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoAlways()
                .build();

        String json = JsonIo.toJson(person, writeOptions);
        assertTrue(json.contains("@type"), "showTypeInfoAlways means @type on all objects");
        assertTrue(json.startsWith("{\"@type\""), "Root should start with @type");
    }

    // ========== Exception tests - invalid combinations ==========

    @Test
    void testOmitRootTypeInfoWithAlwaysThrowsException() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
            new WriteOptionsBuilder()
                    .showTypeInfoAlways()
                    .omitRootTypeInfo()
                    .build();
        });
        assertTrue(e.getMessage().contains("showTypeInfoMinimal()"),
                "Exception message should mention showTypeInfoMinimal()");
    }

    @Test
    void testShowRootTypeInfoWithAlwaysThrowsException() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
            new WriteOptionsBuilder()
                    .showTypeInfoAlways()
                    .showRootTypeInfo()
                    .build();
        });
        assertTrue(e.getMessage().contains("showTypeInfoMinimal()"),
                "Exception message should mention showTypeInfoMinimal()");
    }

    @Test
    void testShowRootTypeInfoWithNeverThrowsException() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
            new WriteOptionsBuilder()
                    .showTypeInfoNever()
                    .showRootTypeInfo()
                    .build();
        });
        assertTrue(e.getMessage().contains("showTypeInfoMinimal()"),
                "Exception message should mention showTypeInfoMinimal()");
    }

    @Test
    void testOmitRootTypeInfoWithNeverThrowsException() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> {
            new WriteOptionsBuilder()
                    .showTypeInfoNever()
                    .omitRootTypeInfo()
                    .build();
        });
        assertTrue(e.getMessage().contains("showTypeInfoMinimal()"),
                "Exception message should mention showTypeInfoMinimal()");
    }

    @Test
    void testExplicitMinimalModeAllowsRootTypeControl() {
        // Explicitly setting Minimal mode should allow root type control
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .showTypeInfoMinimal()
                .omitRootTypeInfo()
                .build();

        assertFalse(writeOptions.isShowingRootTypeInfo());
        assertTrue(writeOptions.isMinimalShowingType());
    }
}
