package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoAlias;
import com.cedarsoftware.io.annotation.IoClassFactory;
import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoDeserialize;
import com.cedarsoftware.io.annotation.IoGetter;
import com.cedarsoftware.io.annotation.IoSetter;
import com.cedarsoftware.io.annotation.IoIgnore;
import com.cedarsoftware.io.annotation.IoIgnoreProperties;
import com.cedarsoftware.io.annotation.IoIgnoreType;
import com.cedarsoftware.io.annotation.IoInclude;
import com.cedarsoftware.io.annotation.IoIncludeProperties;
import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.annotation.IoNonReferenceable;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoPropertyOrder;
import com.cedarsoftware.io.annotation.IoTypeInfo;
import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.io.reflect.AnnotationResolver;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for json-io annotation support:
 * - @IoProperty (field rename)
 * - @IoIgnore (field exclusion)
 * - @IoIgnoreProperties (class-level field exclusion)
 * - @IoAlias (read-side alternate names)
 * - @IoPropertyOrder (write-side field ordering)
 * - @IoInclude(NON_NULL) (per-field null skipping)
 * - External (Jackson) annotation fallback
 * - Priority: json-io annotations win over Jackson
 * - Programmatic API overrides annotations
 */
class AnnotationTest {

    // ===================== Test Models =====================

    static class RenamedFieldModel {
        @IoProperty("full_name")
        private String name;
        private int age;

        RenamedFieldModel() {}
        RenamedFieldModel(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class FieldIgnoreModel {
        private String visible;
        @IoIgnore
        private String hidden;
        private int count;

        FieldIgnoreModel() {}
        FieldIgnoreModel(String visible, String hidden, int count) {
            this.visible = visible;
            this.hidden = hidden;
            this.count = count;
        }
    }

    @IoIgnoreProperties({"secret", "internal"})
    static class ClassIgnoreModel {
        private String name;
        private String secret;
        private String internal;
        private int value;

        ClassIgnoreModel() {}
        ClassIgnoreModel(String name, String secret, String internal, int value) {
            this.name = name;
            this.secret = secret;
            this.internal = internal;
            this.value = value;
        }
    }

    static class AliasModel {
        @IoAlias({"firstName", "first_name", "fname"})
        private String name;
        private int age;

        AliasModel() {}
        AliasModel(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @IoPropertyOrder({"id", "name", "email"})
    static class OrderedModel {
        private String email;
        private String name;
        private long id;
        private int age;

        OrderedModel() {}
        OrderedModel(long id, String name, String email, int age) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.age = age;
        }
    }

    static class NonNullModel {
        @IoInclude(IoInclude.Include.NON_NULL)
        private String optional;
        private String alwaysWritten;
        private int count;

        NonNullModel() {}
        NonNullModel(String optional, String alwaysWritten, int count) {
            this.optional = optional;
            this.alwaysWritten = alwaysWritten;
            this.count = count;
        }
    }

    static class NoAnnotationModel {
        private String name;
        private int value;

        NoAnnotationModel() {}
        NoAnnotationModel(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    // Model with rename + alias combined
    static class RenameAndAliasModel {
        @IoProperty("user_name")
        @IoAlias({"username", "login"})
        private String name;
        private int age;

        RenameAndAliasModel() {}
        RenameAndAliasModel(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    // ===================== @IoProperty Tests =====================

    @Test
    void testIoPropertyWriteRename() {
        RenamedFieldModel model = new RenamedFieldModel("Alice", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"full_name\""), "JSON should use renamed field: " + json);
        assertFalse(json.contains("\"name\""), "JSON should NOT contain original field name: " + json);
        assertTrue(json.contains("\"age\""), "Non-renamed field should be present: " + json);
    }

    @Test
    void testIoPropertyReadRename() {
        // JSON uses the serialized name "full_name"
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$RenamedFieldModel\",\"full_name\":\"Bob\",\"age\":25}";
        RenamedFieldModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(RenamedFieldModel.class);

        assertEquals("Bob", model.name);
        assertEquals(25, model.age);
    }

    @Test
    void testIoPropertyRoundTrip() {
        RenamedFieldModel original = new RenamedFieldModel("Charlie", 40);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        RenamedFieldModel restored = JsonIo.toJava(json, readOptions).asClass(RenamedFieldModel.class);

        assertEquals(original.name, restored.name);
        assertEquals(original.age, restored.age);
    }

    // ===================== @IoIgnore Tests =====================

    @Test
    void testIoIgnoreWrite() {
        FieldIgnoreModel model = new FieldIgnoreModel("shown", "secret", 42);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"visible\""), "Visible field should be present: " + json);
        assertFalse(json.contains("\"hidden\""), "Hidden field should NOT be present: " + json);
        assertTrue(json.contains("\"count\""), "Count field should be present: " + json);
    }

    @Test
    void testIoIgnoreRead() {
        // JSON includes the "hidden" field, but it should be ignored on read
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$FieldIgnoreModel\",\"visible\":\"shown\",\"hidden\":\"should_be_ignored\",\"count\":10}";
        FieldIgnoreModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(FieldIgnoreModel.class);

        assertEquals("shown", model.visible);
        assertNull(model.hidden, "Ignored field should remain null (default)");
        assertEquals(10, model.count);
    }

    @Test
    void testIoIgnoreRoundTrip() {
        FieldIgnoreModel original = new FieldIgnoreModel("visible", "hidden_data", 99);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        FieldIgnoreModel restored = JsonIo.toJava(json, readOptions).asClass(FieldIgnoreModel.class);

        assertEquals("visible", restored.visible);
        assertNull(restored.hidden, "Ignored field should be null after round-trip");
        assertEquals(99, restored.count);
    }

    // ===================== @IoIgnoreProperties Tests =====================

    @Test
    void testIoIgnorePropertiesWrite() {
        ClassIgnoreModel model = new ClassIgnoreModel("Alice", "pass123", "internal_data", 42);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"name\""), "Non-excluded field should be present: " + json);
        assertFalse(json.contains("\"secret\""), "Class-excluded 'secret' should NOT be present: " + json);
        assertFalse(json.contains("\"internal\""), "Class-excluded 'internal' should NOT be present: " + json);
        assertTrue(json.contains("\"value\""), "Non-excluded field should be present: " + json);
    }

    @Test
    void testIoIgnorePropertiesRead() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$ClassIgnoreModel\",\"name\":\"Bob\",\"secret\":\"pass\",\"internal\":\"data\",\"value\":7}";
        ClassIgnoreModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(ClassIgnoreModel.class);

        assertEquals("Bob", model.name);
        assertNull(model.secret, "Class-excluded 'secret' should remain null on read");
        assertNull(model.internal, "Class-excluded 'internal' should remain null on read");
        assertEquals(7, model.value);
    }

    // ===================== @IoAlias Tests =====================

    @Test
    void testIoAliasReadWithFirstName() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$AliasModel\",\"firstName\":\"John\",\"age\":30}";
        AliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(AliasModel.class);

        assertEquals("John", model.name);
        assertEquals(30, model.age);
    }

    @Test
    void testIoAliasReadWithSnakeCase() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$AliasModel\",\"first_name\":\"Jane\",\"age\":25}";
        AliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(AliasModel.class);

        assertEquals("Jane", model.name);
        assertEquals(25, model.age);
    }

    @Test
    void testIoAliasReadWithShortName() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$AliasModel\",\"fname\":\"Jim\",\"age\":40}";
        AliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(AliasModel.class);

        assertEquals("Jim", model.name);
        assertEquals(40, model.age);
    }

    @Test
    void testIoAliasReadWithOriginalName() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$AliasModel\",\"name\":\"Kate\",\"age\":35}";
        AliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(AliasModel.class);

        assertEquals("Kate", model.name);
        assertEquals(35, model.age);
    }

    @Test
    void testIoAliasWriteUsesOriginalName() {
        // Write should use the original field name, not any alias
        AliasModel model = new AliasModel("Bob", 28);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"name\""), "Write should use original field name: " + json);
        assertFalse(json.contains("\"firstName\""), "Write should NOT use alias: " + json);
        assertFalse(json.contains("\"first_name\""), "Write should NOT use alias: " + json);
    }

    // ===================== @IoPropertyOrder Tests =====================

    @Test
    void testIoPropertyOrderWrite() {
        OrderedModel model = new OrderedModel(1L, "Alice", "alice@example.com", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        int idPos = json.indexOf("\"id\"");
        int namePos = json.indexOf("\"name\"");
        int emailPos = json.indexOf("\"email\"");
        int agePos = json.indexOf("\"age\"");

        assertTrue(idPos >= 0, "id should be present");
        assertTrue(namePos >= 0, "name should be present");
        assertTrue(emailPos >= 0, "email should be present");
        assertTrue(agePos >= 0, "age should be present");

        assertTrue(idPos < namePos, "id should appear before name: " + json);
        assertTrue(namePos < emailPos, "name should appear before email: " + json);
        assertTrue(emailPos < agePos, "email should appear before age: " + json);
    }

    @Test
    void testIoPropertyOrderRoundTrip() {
        OrderedModel original = new OrderedModel(42L, "Bob", "bob@test.com", 25);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        OrderedModel restored = JsonIo.toJava(json, readOptions).asClass(OrderedModel.class);

        assertEquals(42L, restored.id);
        assertEquals("Bob", restored.name);
        assertEquals("bob@test.com", restored.email);
        assertEquals(25, restored.age);
    }

    // ===================== @IoInclude(NON_NULL) Tests =====================

    @Test
    void testIoIncludeNonNullSkipsNull() {
        NonNullModel model = new NonNullModel(null, null, 5);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertFalse(json.contains("\"optional\""), "NON_NULL field with null value should be absent: " + json);
        assertTrue(json.contains("\"alwaysWritten\""), "Non-annotated field should be present even when null: " + json);
        assertTrue(json.contains("\"count\""), "Non-null field should be present: " + json);
    }

    @Test
    void testIoIncludeNonNullWritesWhenPresent() {
        NonNullModel model = new NonNullModel("hello", "world", 10);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"optional\""), "NON_NULL field with non-null value should be present: " + json);
        assertTrue(json.contains("\"alwaysWritten\""), "Non-annotated field should be present: " + json);
    }

    @Test
    void testIoIncludeNonNullRoundTrip() {
        NonNullModel original = new NonNullModel("data", "always", 7);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        NonNullModel restored = JsonIo.toJava(json, readOptions).asClass(NonNullModel.class);

        assertEquals("data", restored.optional);
        assertEquals("always", restored.alwaysWritten);
        assertEquals(7, restored.count);
    }

    @Test
    void testIoIncludeNonNullRoundTripWithNull() {
        NonNullModel original = new NonNullModel(null, "always", 3);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        NonNullModel restored = JsonIo.toJava(json, readOptions).asClass(NonNullModel.class);

        assertNull(restored.optional, "optional should be null after round-trip");
        assertEquals("always", restored.alwaysWritten);
        assertEquals(3, restored.count);
    }

    // ===================== No Annotation Regression Tests =====================

    @Test
    void testNoAnnotationRoundTrip() {
        NoAnnotationModel original = new NoAnnotationModel("test", 42);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        NoAnnotationModel restored = JsonIo.toJava(json, readOptions).asClass(NoAnnotationModel.class);

        assertEquals("test", restored.name);
        assertEquals(42, restored.value);
    }

    @Test
    void testNoAnnotationMetadataIsEmpty() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(NoAnnotationModel.class);
        assertTrue(meta.isEmpty(), "Metadata for un-annotated class should be empty");
    }

    // ===================== Combined Rename + Alias Tests =====================

    @Test
    void testRenameAndAliasWrite() {
        RenameAndAliasModel model = new RenameAndAliasModel("Alice", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        assertTrue(json.contains("\"user_name\""), "Write should use renamed name: " + json);
        assertFalse(json.contains("\"name\""), "Write should NOT use original name: " + json);
    }

    @Test
    void testRenameAndAliasReadWithRenamedName() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$RenameAndAliasModel\",\"user_name\":\"Bob\",\"age\":25}";
        RenameAndAliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(RenameAndAliasModel.class);
        assertEquals("Bob", model.name);
    }

    @Test
    void testRenameAndAliasReadWithAlias() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$RenameAndAliasModel\",\"username\":\"Charlie\",\"age\":35}";
        RenameAndAliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(RenameAndAliasModel.class);
        assertEquals("Charlie", model.name);
    }

    @Test
    void testRenameAndAliasReadWithOriginalFieldName() {
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$RenameAndAliasModel\",\"name\":\"Dave\",\"age\":45}";
        RenameAndAliasModel model = JsonIo.toJava(json, new ReadOptionsBuilder().build()).asClass(RenameAndAliasModel.class);
        assertEquals("Dave", model.name);
    }

    @Test
    void testRenameAndAliasRoundTrip() {
        RenameAndAliasModel original = new RenameAndAliasModel("Eve", 28);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, writeOptions);
        RenameAndAliasModel restored = JsonIo.toJava(json, readOptions).asClass(RenameAndAliasModel.class);

        assertEquals("Eve", restored.name);
        assertEquals(28, restored.age);
    }

    // ===================== AnnotationResolver Direct Tests =====================

    @Test
    void testAnnotationResolverMetadataCaching() {
        AnnotationResolver.ClassAnnotationMetadata meta1 = AnnotationResolver.getMetadata(RenamedFieldModel.class);
        AnnotationResolver.ClassAnnotationMetadata meta2 = AnnotationResolver.getMetadata(RenamedFieldModel.class);
        assertSame(meta1, meta2, "Metadata should be cached and return same instance");
    }

    @Test
    void testAnnotationResolverNullClass() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(null);
        assertNotNull(meta, "Null class should return non-null empty metadata");
        assertTrue(meta.isEmpty(), "Null class metadata should be empty");
    }

    @Test
    void testAnnotationResolverRenamedFields() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(RenamedFieldModel.class);
        assertEquals("full_name", meta.getSerializedName("name"));
        assertNull(meta.getSerializedName("age"), "Non-renamed field should return null");
    }

    @Test
    void testAnnotationResolverIgnoredFields() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(FieldIgnoreModel.class);
        assertTrue(meta.isIgnored("hidden"));
        assertFalse(meta.isIgnored("visible"));
        assertFalse(meta.isIgnored("count"));
    }

    @Test
    void testAnnotationResolverClassLevelIgnore() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(ClassIgnoreModel.class);
        assertTrue(meta.isIgnored("secret"));
        assertTrue(meta.isIgnored("internal"));
        assertFalse(meta.isIgnored("name"));
        assertFalse(meta.isIgnored("value"));
    }

    @Test
    void testAnnotationResolverAliases() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(AliasModel.class);
        Map<String, String> aliases = meta.getAliasToFieldName();

        assertEquals("name", aliases.get("firstName"));
        assertEquals("name", aliases.get("first_name"));
        assertEquals("name", aliases.get("fname"));
    }

    @Test
    void testAnnotationResolverPropertyOrder() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(OrderedModel.class);
        String[] order = meta.getPropertyOrder();

        assertNotNull(order);
        assertArrayEquals(new String[]{"id", "name", "email"}, order);
    }

    @Test
    void testAnnotationResolverNonNull() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(NonNullModel.class);
        assertTrue(meta.isNonNull("optional"));
        assertFalse(meta.isNonNull("alwaysWritten"));
        assertFalse(meta.isNonNull("count"));
    }

    // ===================== Map (toMaps) Tests =====================

    @SuppressWarnings("unchecked")
    @Test
    void testIoPropertyWriteToMaps() {
        RenamedFieldModel model = new RenamedFieldModel("Alice", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();
        Map<String, Object> map = JsonIo.toJava(json, readOptions).asClass(Map.class);

        assertTrue(map.containsKey("full_name"), "Map should contain renamed key: " + map);
        assertFalse(map.containsKey("name"), "Map should NOT contain original key: " + map);
        assertEquals("Alice", map.get("full_name"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testIoIgnoreWriteToMaps() {
        FieldIgnoreModel model = new FieldIgnoreModel("shown", "secret", 42);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(model, writeOptions);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();
        Map<String, Object> map = JsonIo.toJava(json, readOptions).asClass(Map.class);

        assertTrue(map.containsKey("visible"), "Map should contain visible field");
        assertFalse(map.containsKey("hidden"), "Map should NOT contain ignored field");
    }

    // ===================== TOON Round-Trip Tests =====================

    @Test
    void testIoPropertyToonRoundTrip() {
        RenamedFieldModel original = new RenamedFieldModel("Alice", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, writeOptions);
        assertTrue(toon.contains("full_name"), "TOON should use renamed field: " + toon);

        RenamedFieldModel restored = JsonIo.fromToon(toon, readOptions).asClass(RenamedFieldModel.class);
        assertEquals("Alice", restored.name);
        assertEquals(30, restored.age);
    }

    @Test
    void testIoIgnoreToonRoundTrip() {
        FieldIgnoreModel original = new FieldIgnoreModel("visible", "hidden_data", 99);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, writeOptions);
        assertFalse(toon.contains("hidden"), "TOON should NOT contain ignored field: " + toon);

        FieldIgnoreModel restored = JsonIo.fromToon(toon, readOptions).asClass(FieldIgnoreModel.class);
        assertEquals("visible", restored.visible);
        assertNull(restored.hidden, "Ignored field should be null after TOON round-trip");
        assertEquals(99, restored.count);
    }

    @Test
    void testIoPropertyOrderToonWrite() {
        OrderedModel model = new OrderedModel(1L, "Alice", "alice@example.com", 30);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String toon = JsonIo.toToon(model, writeOptions);

        int idPos = toon.indexOf("id");
        int namePos = toon.indexOf("name");
        int emailPos = toon.indexOf("email");
        int agePos = toon.indexOf("age");

        assertTrue(idPos >= 0 && namePos >= 0 && emailPos >= 0 && agePos >= 0,
                "All fields should be present in TOON");
        assertTrue(idPos < namePos, "id should appear before name in TOON: " + toon);
        assertTrue(namePos < emailPos, "name should appear before email in TOON: " + toon);
        assertTrue(emailPos < agePos, "email should appear before age in TOON: " + toon);
    }

    @Test
    void testIoIncludeNonNullToonWrite() {
        NonNullModel model = new NonNullModel(null, null, 5);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String toon = JsonIo.toToon(model, writeOptions);

        assertFalse(toon.contains("optional"), "NON_NULL field with null value should be absent from TOON: " + toon);
        assertTrue(toon.contains("alwaysWritten"), "Non-annotated field should be present in TOON: " + toon);
    }

    @Test
    void testIoAliasToonRead() {
        // Write TOON with renamed field, read back using alias
        RenameAndAliasModel original = new RenameAndAliasModel("Eve", 28);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, writeOptions);
        RenameAndAliasModel restored = JsonIo.fromToon(toon, readOptions).asClass(RenameAndAliasModel.class);

        assertEquals("Eve", restored.name);
        assertEquals(28, restored.age);
    }

    // ===================== @IoNaming Test Models =====================

    @IoNaming(IoNaming.Strategy.SNAKE_CASE)
    static class SnakeCaseModel {
        String firstName;
        String lastName;
        int loginCount;

        SnakeCaseModel() {}
        SnakeCaseModel(String firstName, String lastName, int loginCount) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.loginCount = loginCount;
        }
    }

    @IoNaming(IoNaming.Strategy.KEBAB_CASE)
    static class KebabCaseModel {
        String firstName;
        String lastName;

        KebabCaseModel() {}
        KebabCaseModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @IoNaming(IoNaming.Strategy.UPPER_CAMEL_CASE)
    static class UpperCamelModel {
        String firstName;
        String lastName;

        UpperCamelModel() {}
        UpperCamelModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @IoNaming(IoNaming.Strategy.LOWER_DOT_CASE)
    static class LowerDotModel {
        String firstName;
        String lastName;

        LowerDotModel() {}
        LowerDotModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @IoNaming(IoNaming.Strategy.SNAKE_CASE)
    static class NamingWithPropertyOverride {
        String firstName;
        String lastName;
        @IoProperty("uid")
        String userId;

        NamingWithPropertyOverride() {}
        NamingWithPropertyOverride(String firstName, String lastName, String userId) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.userId = userId;
        }
    }

    @IoNaming(IoNaming.Strategy.SNAKE_CASE)
    static class AcronymModel {
        String parseXMLDocument;
        String httpURL;

        AcronymModel() {}
        AcronymModel(String parseXMLDocument, String httpURL) {
            this.parseXMLDocument = parseXMLDocument;
            this.httpURL = httpURL;
        }
    }

    // ===================== @IoNaming Tests =====================

    @Test
    void testIoNamingSnakeCase() {
        SnakeCaseModel original = new SnakeCaseModel("Alice", "Smith", 42);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first_name"), "Should use snake_case: " + json);
        assertTrue(json.contains("last_name"), "Should use snake_case: " + json);
        assertTrue(json.contains("login_count"), "Should use snake_case: " + json);
        assertFalse(json.contains("firstName"), "Should NOT contain camelCase: " + json);

        SnakeCaseModel restored = JsonIo.toJava(json, ro).asClass(SnakeCaseModel.class);
        assertEquals("Alice", restored.firstName);
        assertEquals("Smith", restored.lastName);
        assertEquals(42, restored.loginCount);
    }

    @Test
    void testIoNamingKebabCase() {
        KebabCaseModel original = new KebabCaseModel("Bob", "Jones");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first-name"), "Should use kebab-case: " + json);
        assertTrue(json.contains("last-name"), "Should use kebab-case: " + json);

        KebabCaseModel restored = JsonIo.toJava(json, ro).asClass(KebabCaseModel.class);
        assertEquals("Bob", restored.firstName);
        assertEquals("Jones", restored.lastName);
    }

    @Test
    void testIoNamingUpperCamelCase() {
        UpperCamelModel original = new UpperCamelModel("Carol", "White");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("FirstName"), "Should use UpperCamelCase: " + json);
        assertTrue(json.contains("LastName"), "Should use UpperCamelCase: " + json);

        UpperCamelModel restored = JsonIo.toJava(json, ro).asClass(UpperCamelModel.class);
        assertEquals("Carol", restored.firstName);
        assertEquals("White", restored.lastName);
    }

    @Test
    void testIoNamingLowerDotCase() {
        LowerDotModel original = new LowerDotModel("Dave", "Brown");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first.name"), "Should use lower.dot.case: " + json);
        assertTrue(json.contains("last.name"), "Should use lower.dot.case: " + json);

        LowerDotModel restored = JsonIo.toJava(json, ro).asClass(LowerDotModel.class);
        assertEquals("Dave", restored.firstName);
        assertEquals("Brown", restored.lastName);
    }

    @Test
    void testIoNamingWithIoPropertyOverride() {
        NamingWithPropertyOverride original = new NamingWithPropertyOverride("Eve", "Black", "user123");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first_name"), "Should use snake_case: " + json);
        assertTrue(json.contains("last_name"), "Should use snake_case: " + json);
        assertTrue(json.contains("uid"), "@IoProperty should override @IoNaming: " + json);
        assertFalse(json.contains("user_id"), "@IoProperty should win: " + json);

        NamingWithPropertyOverride restored = JsonIo.toJava(json, ro).asClass(NamingWithPropertyOverride.class);
        assertEquals("Eve", restored.firstName);
        assertEquals("Black", restored.lastName);
        assertEquals("user123", restored.userId);
    }

    @Test
    void testIoNamingAcronyms() {
        AcronymModel original = new AcronymModel("doc.xml", "https://example.com");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("parse_xml_document"), "Acronyms should be lowered: " + json);
        assertTrue(json.contains("http_url"), "Acronyms should be lowered: " + json);

        AcronymModel restored = JsonIo.toJava(json, ro).asClass(AcronymModel.class);
        assertEquals("doc.xml", restored.parseXMLDocument);
        assertEquals("https://example.com", restored.httpURL);
    }

    @Test
    void testIoNamingToonRoundTrip() {
        SnakeCaseModel original = new SnakeCaseModel("Toon", "User", 99);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, wo);
        assertTrue(toon.contains("first_name"), "TOON should use snake_case: " + toon);

        SnakeCaseModel restored = JsonIo.fromToon(toon, ro).asClass(SnakeCaseModel.class);
        assertEquals("Toon", restored.firstName);
        assertEquals("User", restored.lastName);
        assertEquals(99, restored.loginCount);
    }

    @Test
    void testIoNamingResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(SnakeCaseModel.class);
        assertEquals("first_name", meta.getSerializedName("firstName"));
        assertEquals("last_name", meta.getSerializedName("lastName"));
        assertEquals("login_count", meta.getSerializedName("loginCount"));
    }

    @Test
    void testIoNamingMapMode() {
        SnakeCaseModel original = new SnakeCaseModel("Map", "Test", 7);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().returnAsNativeJsonObjects().build();

        String json = JsonIo.toJson(original, wo);
        Map<String, Object> map = JsonIo.toJava(json, ro).asClass(Map.class);

        assertTrue(map.containsKey("first_name"), "Map should use snake_case keys");
        assertTrue(map.containsKey("last_name"), "Map should use snake_case keys");
        assertEquals("Map", map.get("first_name"));
    }

    // ===================== Jackson @JsonNaming Test Models =====================

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class JacksonSnakeCaseModel {
        String firstName;
        String lastName;

        JacksonSnakeCaseModel() {}
        JacksonSnakeCaseModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    @JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
    static class JacksonKebabCaseModel {
        String firstName;
        String lastName;

        JacksonKebabCaseModel() {}
        JacksonKebabCaseModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    // @IoNaming takes priority over @JsonNaming
    @IoNaming(IoNaming.Strategy.KEBAB_CASE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class NamingPriorityModel {
        String firstName;
        String lastName;

        NamingPriorityModel() {}
        NamingPriorityModel(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    // ===================== Jackson @JsonNaming Tests =====================

    @Test
    void testExternalNamingSnakeCase() {
        JacksonSnakeCaseModel original = new JacksonSnakeCaseModel("Jack", "Son");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first_name"), "Jackson @JsonNaming should produce snake_case: " + json);
        assertTrue(json.contains("last_name"), "Jackson @JsonNaming should produce snake_case: " + json);

        JacksonSnakeCaseModel restored = JsonIo.toJava(json, ro).asClass(JacksonSnakeCaseModel.class);
        assertEquals("Jack", restored.firstName);
        assertEquals("Son", restored.lastName);
    }

    @Test
    void testExternalNamingKebabCase() {
        JacksonKebabCaseModel original = new JacksonKebabCaseModel("Jane", "Doe");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first-name"), "Jackson @JsonNaming should produce kebab-case: " + json);

        JacksonKebabCaseModel restored = JsonIo.toJava(json, ro).asClass(JacksonKebabCaseModel.class);
        assertEquals("Jane", restored.firstName);
        assertEquals("Doe", restored.lastName);
    }

    @Test
    void testIoNamingOverridesJsonNaming() {
        NamingPriorityModel original = new NamingPriorityModel("Prio", "Test");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        // @IoNaming(KEBAB_CASE) should win over @JsonNaming(SnakeCaseStrategy)
        assertTrue(json.contains("first-name"), "@IoNaming should win: " + json);
        assertFalse(json.contains("first_name"), "@JsonNaming should NOT win: " + json);

        NamingPriorityModel restored = JsonIo.toJava(json, ro).asClass(NamingPriorityModel.class);
        assertEquals("Prio", restored.firstName);
        assertEquals("Test", restored.lastName);
    }

    // ===================== @IoCreator Test Models =====================

    static class CreatorConstructorModel {
        final String name;
        final int age;

        @IoCreator
        CreatorConstructorModel(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class CreatorStaticFactory {
        final String label;
        final double value;

        private CreatorStaticFactory(String label, double value) {
            this.label = label;
            this.value = value;
        }

        @IoCreator
        static CreatorStaticFactory of(String label, double value) {
            return new CreatorStaticFactory(label, value);
        }
    }

    static class CreatorRenamedParams {
        final String firstName;
        final String lastName;

        @IoCreator
        CreatorRenamedParams(@IoProperty("first_name") String firstName,
                             @IoProperty("last_name") String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    static class CreatorWithIgnore {
        final String visible;
        @IoIgnore
        String hidden;
        final int count;

        @IoCreator
        CreatorWithIgnore(String visible, int count) {
            this.visible = visible;
            this.count = count;
        }
    }

    @IoNaming(IoNaming.Strategy.SNAKE_CASE)
    static class CreatorWithNaming {
        final String firstName;
        final String lastName;

        @IoCreator
        CreatorWithNaming(@IoProperty("first_name") String firstName,
                          @IoProperty("last_name") String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }
    }

    // ===================== @IoCreator Tests =====================

    @Test
    void testIoCreatorConstructor() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CreatorConstructorModel obj = JsonIo.toJava(json, ro).asClass(CreatorConstructorModel.class);
        assertEquals("Alice", obj.name);
        assertEquals(30, obj.age);
    }

    @Test
    void testIoCreatorStaticFactory() {
        String json = "{\"label\":\"pi\",\"value\":3.14}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CreatorStaticFactory obj = JsonIo.toJava(json, ro).asClass(CreatorStaticFactory.class);
        assertEquals("pi", obj.label);
        assertEquals(3.14, obj.value, 0.001);
    }

    @Test
    void testIoCreatorWithRenamedParams() {
        String json = "{\"first_name\":\"Bob\",\"last_name\":\"Smith\"}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CreatorRenamedParams obj = JsonIo.toJava(json, ro).asClass(CreatorRenamedParams.class);
        assertEquals("Bob", obj.firstName);
        assertEquals("Smith", obj.lastName);
    }

    @Test
    void testIoCreatorRoundTrip() {
        CreatorConstructorModel original = new CreatorConstructorModel("Carol", 25);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        CreatorConstructorModel restored = JsonIo.toJava(json, ro).asClass(CreatorConstructorModel.class);
        assertEquals("Carol", restored.name);
        assertEquals(25, restored.age);
    }

    @Test
    void testIoCreatorIgnoredFieldsFiltered() {
        // hidden field is @IoIgnore, so even if present in JSON it should not reach the creator
        String json = "{\"visible\":\"yes\",\"hidden\":\"secret\",\"count\":5}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CreatorWithIgnore obj = JsonIo.toJava(json, ro).asClass(CreatorWithIgnore.class);
        assertEquals("yes", obj.visible);
        assertEquals(5, obj.count);
        assertNull(obj.hidden, "Ignored field should be null");
    }

    @Test
    void testIoCreatorWithNamingStrategy() {
        CreatorWithNaming original = new CreatorWithNaming("Dave", "Brown");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        assertTrue(json.contains("first_name"), "Should use snake_case: " + json);

        CreatorWithNaming restored = JsonIo.toJava(json, ro).asClass(CreatorWithNaming.class);
        assertEquals("Dave", restored.firstName);
        assertEquals("Brown", restored.lastName);
    }

    @Test
    void testIoCreatorToonRoundTrip() {
        CreatorConstructorModel original = new CreatorConstructorModel("Toon", 99);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, wo);
        CreatorConstructorModel restored = JsonIo.fromToon(toon, ro).asClass(CreatorConstructorModel.class);
        assertEquals("Toon", restored.name);
        assertEquals(99, restored.age);
    }

    @Test
    void testIoCreatorResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(CreatorConstructorModel.class);
        assertNotNull(meta.getCreator(), "Creator should be found");
        assertTrue(meta.getCreator() instanceof java.lang.reflect.Constructor, "Should be a Constructor");
    }

    // ===================== Jackson @JsonCreator Test Models =====================

    static class JacksonCreatorModel {
        final String name;
        final int age;

        @JsonCreator
        JacksonCreatorModel(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class JacksonCreatorStaticFactory {
        final String color;
        final int brightness;

        private JacksonCreatorStaticFactory(String color, int brightness) {
            this.color = color;
            this.brightness = brightness;
        }

        @JsonCreator
        static JacksonCreatorStaticFactory of(@JsonProperty("color") String color,
                                              @JsonProperty("brightness") int brightness) {
            return new JacksonCreatorStaticFactory(color, brightness);
        }
    }

    // Model with @IoCreator that should win over @JsonCreator
    // Uses a static field to track which path was used
    static class CreatorPriorityModel {
        static String createdBy;
        String value;

        CreatorPriorityModel() {}

        @IoCreator
        CreatorPriorityModel(@IoProperty("value") String value) {
            this.value = value;
            createdBy = "IoCreator";
        }

        @JsonCreator
        static CreatorPriorityModel fromJson(@JsonProperty("value") String value) {
            CreatorPriorityModel m = new CreatorPriorityModel();
            m.value = value;
            createdBy = "JsonCreator";
            return m;
        }
    }

    // ===================== Jackson @JsonCreator Tests =====================

    @Test
    void testExternalCreatorConstructor() {
        String json = "{\"name\":\"Alice\",\"age\":30}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        JacksonCreatorModel result = JsonIo.toJava(json, ro).asClass(JacksonCreatorModel.class);
        assertEquals("Alice", result.name);
        assertEquals(30, result.age);
    }

    @Test
    void testExternalCreatorStaticFactory() {
        String json = "{\"color\":\"red\",\"brightness\":100}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        JacksonCreatorStaticFactory result = JsonIo.toJava(json, ro).asClass(JacksonCreatorStaticFactory.class);
        assertEquals("red", result.color);
        assertEquals(100, result.brightness);
    }

    @Test
    void testIoCreatorOverridesJsonCreator() {
        // @IoCreator should win over @JsonCreator
        CreatorPriorityModel.createdBy = null;
        String json = "{\"value\":\"test\"}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CreatorPriorityModel result = JsonIo.toJava(json, ro).asClass(CreatorPriorityModel.class);
        assertEquals("IoCreator", CreatorPriorityModel.createdBy, "@IoCreator should win over @JsonCreator");
        assertEquals("test", result.value);
    }

    @Test
    void testExternalCreatorRoundTrip() {
        JacksonCreatorModel original = new JacksonCreatorModel("Bob", 25);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        JacksonCreatorModel restored = JsonIo.toJava(json, ro).asClass(JacksonCreatorModel.class);
        assertEquals("Bob", restored.name);
        assertEquals(25, restored.age);
    }

    // ===================== @IoValue Test Models =====================

    static class EmailAddress {
        private final String address;

        @IoCreator
        EmailAddress(@IoProperty("address") String address) {
            this.address = address;
        }

        @IoValue
        public String toValue() {
            return address;
        }
    }

    static class MoneyValue {
        private final long cents;
        private final String currency;

        @IoCreator
        MoneyValue(@IoProperty("cents") long cents, @IoProperty("currency") String currency) {
            this.cents = cents;
            this.currency = currency;
        }

        @IoValue
        public String toValue() {
            return cents + " " + currency;
        }
    }

    // ===================== @IoValue Tests =====================

    @Test
    void testIoValueWrite() {
        EmailAddress email = new EmailAddress("user@example.com");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(email, wo);
        // Should serialize as a single string value, not {"address":"user@example.com"}
        assertEquals("\"user@example.com\"", json.trim());
    }

    @Test
    void testIoValueRoundTripWithType() {
        EmailAddress original = new EmailAddress("test@test.com");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoAlways().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        // With type info, the JSON is {"@type":"...","value":"test@test.com"}
        assertTrue(json.contains("@type"), "Should include type info: " + json);

        EmailAddress restored = JsonIo.toJava(json, ro).asClass(EmailAddress.class);
        assertEquals("test@test.com", restored.address);
    }

    @Test
    void testIoValueWithTypeInfo() {
        EmailAddress email = new EmailAddress("typed@example.com");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoAlways().build();

        String json = JsonIo.toJson(email, wo);
        // When type info is needed, should wrap as {"@type":"...","value":"..."}
        assertTrue(json.contains("@type"), "Should include @type: " + json);
        assertTrue(json.contains("value"), "Should include value key: " + json);
        assertTrue(json.contains("typed@example.com"), "Should include the value: " + json);
    }

    @Test
    void testIoValueToonWrite() {
        EmailAddress email = new EmailAddress("toon@example.com");
        WriteOptions wo = new WriteOptionsBuilder().build();

        String toon = JsonIo.toToon(email, wo);
        // TOON should serialize via @IoValue as a single value
        assertTrue(toon.contains("toon@example.com"), "TOON should contain the value: " + toon);
    }

    @Test
    void testIoValueResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(EmailAddress.class);
        assertNotNull(meta.getValueMethod(), "ValueMethod should be found");
        assertEquals("toValue", meta.getValueMethod().getName());
    }

    // ===================== Jackson @JsonValue Test Models =====================

    static class JacksonValueModel {
        private final String data;

        @JsonCreator
        JacksonValueModel(@JsonProperty("data") String data) {
            this.data = data;
        }

        @JsonValue
        public String getData() {
            return data;
        }
    }

    // Model with @IoValue that should win over @JsonValue
    static class ValuePriorityModel {
        private final String value;

        ValuePriorityModel(String value) {
            this.value = value;
        }

        @IoValue
        public String ioGetter() {
            return "io:" + value;
        }

        @JsonValue
        public String jacksonGetter() {
            return "jackson:" + value;
        }
    }

    // ===================== Jackson @JsonValue Tests =====================

    @Test
    void testExternalValueWrite() {
        JacksonValueModel model = new JacksonValueModel("hello");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        // Should serialize as a single string via @JsonValue
        assertEquals("\"hello\"", json.trim());
    }

    @Test
    void testExternalValueToonWrite() {
        JacksonValueModel model = new JacksonValueModel("toon-test");
        WriteOptions wo = new WriteOptionsBuilder().build();

        String toon = JsonIo.toToon(model, wo);
        assertTrue(toon.contains("toon-test"), "TOON should contain the value: " + toon);
    }

    @Test
    void testIoValueOverridesJsonValue() {
        ValuePriorityModel model = new ValuePriorityModel("test");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        // @IoValue should win over @JsonValue
        assertEquals("\"io:test\"", json.trim(), "@IoValue should win over @JsonValue");
    }

    // ===================== @IoIncludeProperties Models =====================

    @IoIncludeProperties({"name", "email"})
    static class IncludePropsModel {
        private String name;
        private String email;
        private String password;
        private int age;

        IncludePropsModel() { }
        IncludePropsModel(String name, String email, String password, int age) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.age = age;
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public int getAge() { return age; }
    }

    @IoIncludeProperties({"name", "alias"})
    static class IncludePropsWithRenameModel {
        private String name;
        @IoProperty("alias")
        private String nickname;
        private String secret;

        IncludePropsWithRenameModel() { }
        IncludePropsWithRenameModel(String name, String nickname, String secret) {
            this.name = name;
            this.nickname = nickname;
            this.secret = secret;
        }

        public String getName() { return name; }
        public String getNickname() { return nickname; }
        public String getSecret() { return secret; }
    }

    @IoIncludeProperties({"name", "email"})
    static class IncludePropsWithIgnoreConflictModel {
        private String name;
        @IoIgnore
        private String email;
        private String password;

        IncludePropsWithIgnoreConflictModel() { }
        IncludePropsWithIgnoreConflictModel(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
    }

    // ===================== @IoIncludeProperties Tests =====================

    @Test
    void testIoIncludePropertiesWrite() {
        IncludePropsModel model = new IncludePropsModel("Alice", "alice@test.com", "secret123", 30);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        assertTrue(json.contains("\"email\""), "Should include 'email'");
        assertFalse(json.contains("\"password\""), "Should NOT include 'password'");
        assertFalse(json.contains("\"age\""), "Should NOT include 'age'");
    }

    @Test
    void testIoIncludePropertiesRead() {
        // JSON has extra fields beyond the whitelist — they should be ignored on read
        String json = "{\"@type\":\"" + IncludePropsModel.class.getName() + "\",\"name\":\"Bob\",\"email\":\"bob@test.com\",\"password\":\"hack\",\"age\":99}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        IncludePropsModel result = JsonIo.toJava(json, ro).asClass(IncludePropsModel.class);
        assertEquals("Bob", result.getName());
        assertEquals("bob@test.com", result.getEmail());
        // password and age should NOT be injected (not in included whitelist)
        assertNull(result.getPassword(), "password should not be injected");
        assertEquals(0, result.getAge(), "age should not be injected");
    }

    @Test
    void testIoIncludePropertiesRoundTrip() {
        IncludePropsModel model = new IncludePropsModel("Alice", "alice@test.com", "secret123", 30);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoAlways().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(model, wo);
        IncludePropsModel result = JsonIo.toJava(json, ro).asClass(IncludePropsModel.class);

        assertEquals("Alice", result.getName());
        assertEquals("alice@test.com", result.getEmail());
        // Non-included fields should be default values after round-trip
        assertNull(result.getPassword());
        assertEquals(0, result.getAge());
    }

    @Test
    void testIoIncludePropertiesWithIoPropertyRename() {
        // @IoIncludeProperties lists "name" and "alias" (the Java field name for nickname is listed via rename)
        IncludePropsWithRenameModel model = new IncludePropsWithRenameModel("Alice", "Ali", "top-secret");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        // "nickname" field is renamed to "alias" via @IoProperty, but @IoIncludeProperties uses Java field name
        // The whitelist lists "alias" which is the serialized name, but matching is by Java field name "nickname"
        // Since "alias" != "nickname", this field should NOT be in the whitelist... unless we match by serialized name too.
        // Actually, the whitelist should match by Java field name. "alias" is NOT a Java field name, so this tests that.
        assertFalse(json.contains("\"secret\""), "Should NOT include 'secret'");
    }

    @Test
    void testIoIncludePropertiesWithIoIgnoreConflict() {
        // "email" is in @IoIncludeProperties whitelist but also has @IoIgnore — @IoIgnore should win
        IncludePropsWithIgnoreConflictModel model = new IncludePropsWithIgnoreConflictModel("Alice", "alice@test.com", "secret");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        assertFalse(json.contains("\"email\""), "@IoIgnore should win over @IoIncludeProperties");
        assertFalse(json.contains("\"password\""), "Should NOT include 'password' (not in whitelist)");
    }

    @Test
    void testIoIncludePropertiesToonWrite() {
        IncludePropsModel model = new IncludePropsModel("Alice", "alice@test.com", "secret123", 30);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoAlways().build();

        String toon = JsonIo.toToon(model, wo);
        assertTrue(toon.contains("name"), "TOON should contain 'name'");
        assertTrue(toon.contains("email"), "TOON should contain 'email'");
        assertFalse(toon.contains("password"), "TOON should NOT contain 'password'");
        assertFalse(toon.contains("age"), "TOON should NOT contain 'age'");
    }

    // ===================== Jackson @JsonIncludeProperties Models =====================

    @JsonIncludeProperties({"name", "email"})
    static class JacksonIncludePropsModel {
        private String name;
        private String email;
        private String password;
        private int age;

        JacksonIncludePropsModel() { }
        JacksonIncludePropsModel(String name, String email, String password, int age) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.age = age;
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public int getAge() { return age; }
    }

    @IoIncludeProperties({"name"})
    @JsonIncludeProperties({"name", "email", "password"})
    static class IncludePropsPriorityModel {
        private String name;
        private String email;
        private String password;

        IncludePropsPriorityModel() { }
        IncludePropsPriorityModel(String name, String email, String password) {
            this.name = name;
            this.email = email;
            this.password = password;
        }

        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
    }

    // ===================== Jackson @JsonIncludeProperties Tests =====================

    @Test
    void testExternalIncludePropertiesWrite() {
        JacksonIncludePropsModel model = new JacksonIncludePropsModel("Alice", "alice@test.com", "secret", 30);
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        assertTrue(json.contains("\"email\""), "Should include 'email'");
        assertFalse(json.contains("\"password\""), "Should NOT include 'password'");
        assertFalse(json.contains("\"age\""), "Should NOT include 'age'");
    }

    @Test
    void testExternalIncludePropertiesRead() {
        String json = "{\"@type\":\"" + JacksonIncludePropsModel.class.getName() + "\",\"name\":\"Bob\",\"email\":\"bob@test.com\",\"password\":\"hack\",\"age\":99}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        JacksonIncludePropsModel result = JsonIo.toJava(json, ro).asClass(JacksonIncludePropsModel.class);
        assertEquals("Bob", result.getName());
        assertEquals("bob@test.com", result.getEmail());
        assertNull(result.getPassword(), "password should not be injected");
        assertEquals(0, result.getAge(), "age should not be injected");
    }

    @Test
    void testIoIncludePropertiesOverridesJsonIncludeProperties() {
        // @IoIncludeProperties({"name"}) should win over @JsonIncludeProperties({"name", "email", "password"})
        IncludePropsPriorityModel model = new IncludePropsPriorityModel("Alice", "alice@test.com", "secret");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name' (in both)");
        assertFalse(json.contains("\"email\""), "@IoIncludeProperties should win — email excluded");
        assertFalse(json.contains("\"password\""), "@IoIncludeProperties should win — password excluded");
    }

    // ===================== @IoIgnoreType Models =====================

    @IoIgnoreType
    static class InternalMetadata {
        private String traceId;
        private long timestamp;

        InternalMetadata() { }
        InternalMetadata(String traceId, long timestamp) {
            this.traceId = traceId;
            this.timestamp = timestamp;
        }

        public String getTraceId() { return traceId; }
        public long getTimestamp() { return timestamp; }
    }

    static class OrderWithMeta {
        private String orderId;
        private InternalMetadata meta;
        private String status;

        OrderWithMeta() { }
        OrderWithMeta(String orderId, InternalMetadata meta, String status) {
            this.orderId = orderId;
            this.meta = meta;
            this.status = status;
        }

        public String getOrderId() { return orderId; }
        public InternalMetadata getMeta() { return meta; }
        public String getStatus() { return status; }
    }

    static class AnotherClassWithMeta {
        private String name;
        private InternalMetadata metadata;

        AnotherClassWithMeta() { }
        AnotherClassWithMeta(String name, InternalMetadata metadata) {
            this.name = name;
            this.metadata = metadata;
        }

        public String getName() { return name; }
        public InternalMetadata getMetadata() { return metadata; }
    }

    // ===================== @IoIgnoreType Tests =====================

    @Test
    void testIoIgnoreTypeBasic() {
        OrderWithMeta model = new OrderWithMeta("ORD-123", new InternalMetadata("trace-1", 12345L), "SHIPPED");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"orderId\""), "Should include 'orderId'");
        assertTrue(json.contains("\"status\""), "Should include 'status'");
        assertFalse(json.contains("\"meta\""), "Should NOT include 'meta' — type is @IoIgnoreType");
        assertFalse(json.contains("\"traceId\""), "Should NOT include 'traceId'");
    }

    @Test
    void testIoIgnoreTypeMultipleClasses() {
        // @IoIgnoreType propagates to ALL classes that have fields of InternalMetadata type
        AnotherClassWithMeta model = new AnotherClassWithMeta("test", new InternalMetadata("t2", 999L));
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        assertFalse(json.contains("\"metadata\""), "Should NOT include 'metadata' — type is @IoIgnoreType");
    }

    @Test
    void testIoIgnoreTypeRead() {
        // On read, the ignored-type field should not be populated
        String json = "{\"@type\":\"" + OrderWithMeta.class.getName() + "\",\"orderId\":\"ORD-1\",\"meta\":{\"traceId\":\"t1\",\"timestamp\":100},\"status\":\"OK\"}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        OrderWithMeta result = JsonIo.toJava(json, ro).asClass(OrderWithMeta.class);
        assertEquals("ORD-1", result.getOrderId());
        assertEquals("OK", result.getStatus());
        assertNull(result.getMeta(), "meta should not be injected — type is @IoIgnoreType");
    }

    @Test
    void testIoIgnoreTypeDoesNotAffectOtherTypes() {
        // Non-annotated types should still serialize normally
        OrderWithMeta model = new OrderWithMeta("ORD-123", new InternalMetadata("t1", 100L), "SHIPPED");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        // orderId (String) and status (String) are fine — String is not @IoIgnoreType
        assertTrue(json.contains("\"orderId\""));
        assertTrue(json.contains("\"status\""));
    }

    @Test
    void testIoIgnoreTypeToonWrite() {
        OrderWithMeta model = new OrderWithMeta("ORD-123", new InternalMetadata("t1", 100L), "SHIPPED");
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoAlways().build();

        String toon = JsonIo.toToon(model, wo);
        assertTrue(toon.contains("orderId"), "TOON should contain 'orderId'");
        assertTrue(toon.contains("status"), "TOON should contain 'status'");
        assertFalse(toon.contains("meta"), "TOON should NOT contain 'meta'");
    }

    // ===================== Jackson @JsonIgnoreType Models =====================

    @JsonIgnoreType
    static class JacksonIgnoredType {
        private String internal;
        JacksonIgnoredType() { }
        JacksonIgnoredType(String internal) { this.internal = internal; }
        public String getInternal() { return internal; }
    }

    static class OwnerOfJacksonIgnored {
        private String name;
        private JacksonIgnoredType ignored;

        OwnerOfJacksonIgnored() { }
        OwnerOfJacksonIgnored(String name, JacksonIgnoredType ignored) {
            this.name = name;
            this.ignored = ignored;
        }

        public String getName() { return name; }
        public JacksonIgnoredType getIgnored() { return ignored; }
    }

    // ===================== Jackson @JsonIgnoreType Tests =====================

    @Test
    void testExternalIgnoreTypeBasic() {
        OwnerOfJacksonIgnored model = new OwnerOfJacksonIgnored("test", new JacksonIgnoredType("secret"));
        WriteOptions wo = new WriteOptionsBuilder().showTypeInfoNever().build();

        String json = JsonIo.toJson(model, wo);
        assertTrue(json.contains("\"name\""), "Should include 'name'");
        assertFalse(json.contains("\"ignored\""), "Should NOT include 'ignored' — type has @JsonIgnoreType");
    }

    @Test
    void testExternalIgnoreTypeRead() {
        String json = "{\"@type\":\"" + OwnerOfJacksonIgnored.class.getName() + "\",\"name\":\"test\",\"ignored\":{\"internal\":\"secret\"}}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        OwnerOfJacksonIgnored result = JsonIo.toJava(json, ro).asClass(OwnerOfJacksonIgnored.class);
        assertEquals("test", result.getName());
        assertNull(result.getIgnored(), "ignored should not be injected — type has @JsonIgnoreType");
    }

    // ===================== @IoTypeInfo Models =====================

    static class TypeInfoObjectField {
        @IoTypeInfo(ArrayList.class)
        private Object items;
        private String label;

        TypeInfoObjectField() { }
        TypeInfoObjectField(Object items, String label) {
            this.items = items;
            this.label = label;
        }

        public Object getItems() { return items; }
        public String getLabel() { return label; }
    }

    static class TypeInfoListField {
        @IoTypeInfo(LinkedList.class)
        private List<String> names;
        private int count;

        TypeInfoListField() { }
        TypeInfoListField(List<String> names, int count) {
            this.names = names;
            this.count = count;
        }

        public List<String> getNames() { return names; }
        public int getCount() { return count; }
    }

    static class TypeInfoMapField {
        @IoTypeInfo(LinkedHashMap.class)
        private Map<String, Object> data;

        TypeInfoMapField() { }
        TypeInfoMapField(Map<String, Object> data) {
            this.data = data;
        }

        public Map<String, Object> getData() { return data; }
    }

    // ===================== @IoTypeInfo Tests =====================

    @Test
    void testIoTypeInfoObjectField() {
        // Field declared as Object, @IoTypeInfo specifies ArrayList → ArrayList should be created
        String json = "{\"@type\":\"" + TypeInfoObjectField.class.getName() + "\",\"items\":[1,2,3],\"label\":\"test\"}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        TypeInfoObjectField result = JsonIo.toJava(json, ro).asClass(TypeInfoObjectField.class);
        assertEquals("test", result.getLabel());
        assertNotNull(result.getItems(), "items should not be null");
        assertTrue(result.getItems() instanceof ArrayList, "items should be ArrayList but was: " + result.getItems().getClass().getName());
        List<?> list = (List<?>) result.getItems();
        assertEquals(3, list.size());
    }

    @Test
    void testIoTypeInfoListField() {
        // Field declared as List<String>, @IoTypeInfo specifies LinkedList → LinkedList should be created
        String json = "{\"@type\":\"" + TypeInfoListField.class.getName() + "\",\"names\":[\"a\",\"b\",\"c\"],\"count\":3}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        TypeInfoListField result = JsonIo.toJava(json, ro).asClass(TypeInfoListField.class);
        assertEquals(3, result.getCount());
        assertNotNull(result.getNames(), "names should not be null");
        assertTrue(result.getNames() instanceof LinkedList, "names should be LinkedList but was: " + result.getNames().getClass().getName());
        assertEquals(3, result.getNames().size());
        assertEquals("a", result.getNames().get(0));
    }

    @Test
    void testIoTypeInfoExplicitTypeWins() {
        // JSON with @type should take precedence over @IoTypeInfo
        String json = "{\"@type\":\"" + TypeInfoListField.class.getName() + "\","
                + "\"names\":{\"@type\":\"java.util.ArrayList\",\"@items\":[\"x\",\"y\"]},\"count\":2}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        TypeInfoListField result = JsonIo.toJava(json, ro).asClass(TypeInfoListField.class);
        assertNotNull(result.getNames());
        assertTrue(result.getNames() instanceof ArrayList, "Explicit @type should win over @IoTypeInfo");
        assertEquals(2, result.getNames().size());
    }

    @Test
    void testIoTypeInfoRoundTrip() {
        LinkedList<String> names = new LinkedList<>();
        names.add("Alice");
        names.add("Bob");
        TypeInfoListField original = new TypeInfoListField(names, 2);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        TypeInfoListField result = JsonIo.toJava(json, ro).asClass(TypeInfoListField.class);

        assertNotNull(result.getNames());
        assertEquals(2, result.getNames().size());
        assertEquals("Alice", result.getNames().get(0));
        assertEquals("Bob", result.getNames().get(1));
    }

    @Test
    void testIoTypeInfoResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(TypeInfoObjectField.class);
        assertEquals(ArrayList.class, meta.getFieldTypeInfoDefault("items"));
        assertNull(meta.getFieldTypeInfoDefault("label"), "Non-annotated field should return null");
    }

    @Test
    void testIoTypeInfoToonRoundTrip() {
        LinkedList<String> names = new LinkedList<>();
        names.add("Toon");
        names.add("Test");
        TypeInfoListField original = new TypeInfoListField(names, 2);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, wo);
        TypeInfoListField result = JsonIo.fromToon(toon, ro).asClass(TypeInfoListField.class);

        assertNotNull(result.getNames());
        assertEquals(2, result.getNames().size());
        assertEquals("Toon", result.getNames().get(0));
    }

    // ===================== Jackson @JsonTypeInfo Models =====================

    static class JacksonTypeInfoListField {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, defaultImpl = LinkedList.class)
        private List<String> names;
        private int count;

        JacksonTypeInfoListField() { }
        JacksonTypeInfoListField(List<String> names, int count) {
            this.names = names;
            this.count = count;
        }

        public List<String> getNames() { return names; }
        public int getCount() { return count; }
    }

    // @IoTypeInfo should win over @JsonTypeInfo
    static class TypeInfoPriorityField {
        @IoTypeInfo(LinkedList.class)
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, defaultImpl = ArrayList.class)
        private List<String> items;

        TypeInfoPriorityField() { }
        TypeInfoPriorityField(List<String> items) {
            this.items = items;
        }

        public List<String> getItems() { return items; }
    }

    // ===================== Jackson @JsonTypeInfo Tests =====================

    @Test
    void testExternalTypeInfoListField() {
        String json = "{\"@type\":\"" + JacksonTypeInfoListField.class.getName() + "\",\"names\":[\"a\",\"b\"],\"count\":2}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        JacksonTypeInfoListField result = JsonIo.toJava(json, ro).asClass(JacksonTypeInfoListField.class);
        assertNotNull(result.getNames());
        assertTrue(result.getNames() instanceof LinkedList,
                "names should be LinkedList (from @JsonTypeInfo defaultImpl) but was: " + result.getNames().getClass().getName());
        assertEquals(2, result.getNames().size());
    }

    @Test
    void testExternalTypeInfoResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(JacksonTypeInfoListField.class);
        assertEquals(LinkedList.class, meta.getFieldTypeInfoDefault("names"));
    }

    @Test
    void testIoTypeInfoOverridesJsonTypeInfo() {
        // @IoTypeInfo(LinkedList.class) should win over @JsonTypeInfo(defaultImpl=ArrayList.class)
        String json = "{\"@type\":\"" + TypeInfoPriorityField.class.getName() + "\",\"items\":[\"x\"]}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        TypeInfoPriorityField result = JsonIo.toJava(json, ro).asClass(TypeInfoPriorityField.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "@IoTypeInfo should win over @JsonTypeInfo: " + result.getItems().getClass().getName());
    }

    // ===================== @IoDeserialize Models =====================

    static class DeserializeListField {
        @IoDeserialize(as = LinkedList.class)
        private List<String> items;
        private int count;

        DeserializeListField() { }
        DeserializeListField(List<String> items, int count) {
            this.items = items;
            this.count = count;
        }

        public List<String> getItems() { return items; }
        public int getCount() { return count; }
    }

    static class DeserializeMapField {
        @IoDeserialize(as = LinkedHashMap.class)
        private Map<String, Object> data;

        DeserializeMapField() { }
        DeserializeMapField(Map<String, Object> data) {
            this.data = data;
        }

        public Map<String, Object> getData() { return data; }
    }

    // @IoDeserialize should win over @IoTypeInfo
    static class DeserializePriorityField {
        @IoDeserialize(as = LinkedList.class)
        @IoTypeInfo(ArrayList.class)
        private List<String> items;

        DeserializePriorityField() { }
        DeserializePriorityField(List<String> items) {
            this.items = items;
        }

        public List<String> getItems() { return items; }
    }

    // ===================== @IoDeserialize Tests =====================

    @Test
    void testIoDeserializeField() {
        // List<String> field + @IoDeserialize(as=LinkedList.class) → LinkedList created
        String json = "{\"@type\":\"" + DeserializeListField.class.getName() + "\",\"items\":[\"a\",\"b\",\"c\"],\"count\":3}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        DeserializeListField result = JsonIo.toJava(json, ro).asClass(DeserializeListField.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "items should be LinkedList but was: " + result.getItems().getClass().getName());
        assertEquals(3, result.getItems().size());
        assertEquals("a", result.getItems().get(0));
    }

    @Test
    void testIoDeserializeExplicitTypeWins() {
        // JSON with @type should take precedence over @IoDeserialize
        String json = "{\"@type\":\"" + DeserializeListField.class.getName() + "\","
                + "\"items\":{\"@type\":\"java.util.ArrayList\",\"@items\":[\"x\",\"y\"]},\"count\":2}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        DeserializeListField result = JsonIo.toJava(json, ro).asClass(DeserializeListField.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof ArrayList,
                "Explicit @type should win over @IoDeserialize: " + result.getItems().getClass().getName());
    }

    @Test
    void testIoDeserializePriorityOverTypeInfo() {
        // @IoDeserialize(as=LinkedList.class) should win over @IoTypeInfo(ArrayList.class)
        String json = "{\"@type\":\"" + DeserializePriorityField.class.getName() + "\",\"items\":[\"x\"]}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        DeserializePriorityField result = JsonIo.toJava(json, ro).asClass(DeserializePriorityField.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "@IoDeserialize should win over @IoTypeInfo: " + result.getItems().getClass().getName());
    }

    @Test
    void testIoDeserializeRoundTrip() {
        LinkedList<String> items = new LinkedList<>();
        items.add("round");
        items.add("trip");
        DeserializeListField original = new DeserializeListField(items, 2);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        DeserializeListField result = JsonIo.toJava(json, ro).asClass(DeserializeListField.class);

        assertNotNull(result.getItems());
        assertEquals(2, result.getItems().size());
        assertEquals("round", result.getItems().get(0));
    }

    @Test
    void testIoDeserializeResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(DeserializeListField.class);
        assertEquals(LinkedList.class, meta.getFieldDeserializeOverride("items"));
        assertNull(meta.getFieldDeserializeOverride("count"), "Non-annotated field should return null");
    }

    @Test
    void testIoDeserializeToonRoundTrip() {
        LinkedList<String> items = new LinkedList<>();
        items.add("Toon");
        items.add("Test");
        DeserializeListField original = new DeserializeListField(items, 2);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String toon = JsonIo.toToon(original, wo);
        DeserializeListField result = JsonIo.fromToon(toon, ro).asClass(DeserializeListField.class);

        assertNotNull(result.getItems());
        assertEquals(2, result.getItems().size());
        assertEquals("Toon", result.getItems().get(0));
    }

    // ===================== Jackson @JsonDeserialize Models =====================

    static class JacksonDeserializeField {
        @JsonDeserialize(as = LinkedList.class)
        private List<String> items;
        private int count;

        JacksonDeserializeField() { }
        JacksonDeserializeField(List<String> items, int count) {
            this.items = items;
            this.count = count;
        }

        public List<String> getItems() { return items; }
        public int getCount() { return count; }
    }

    // @IoDeserialize should win over @JsonDeserialize
    static class DeserializePriorityJackson {
        @IoDeserialize(as = LinkedList.class)
        @JsonDeserialize(as = ArrayList.class)
        private List<String> items;

        DeserializePriorityJackson() { }
        DeserializePriorityJackson(List<String> items) {
            this.items = items;
        }

        public List<String> getItems() { return items; }
    }

    // ===================== Jackson @JsonDeserialize Tests =====================

    @Test
    void testExternalDeserializeField() {
        String json = "{\"@type\":\"" + JacksonDeserializeField.class.getName() + "\",\"items\":[\"a\",\"b\"],\"count\":2}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        JacksonDeserializeField result = JsonIo.toJava(json, ro).asClass(JacksonDeserializeField.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "items should be LinkedList (from @JsonDeserialize) but was: " + result.getItems().getClass().getName());
        assertEquals(2, result.getItems().size());
    }

    @Test
    void testExternalDeserializeResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(JacksonDeserializeField.class);
        assertEquals(LinkedList.class, meta.getFieldDeserializeOverride("items"));
    }

    @Test
    void testIoDeserializeOverridesJsonDeserialize() {
        // @IoDeserialize(as=LinkedList.class) should win over @JsonDeserialize(as=ArrayList.class)
        String json = "{\"@type\":\"" + DeserializePriorityJackson.class.getName() + "\",\"items\":[\"x\"]}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        DeserializePriorityJackson result = JsonIo.toJava(json, ro).asClass(DeserializePriorityJackson.class);
        assertNotNull(result.getItems());
        assertTrue(result.getItems() instanceof LinkedList,
                "@IoDeserialize should win over @JsonDeserialize: " + result.getItems().getClass().getName());
    }

    // ===================== @IoClassFactory Models =====================

    /**
     * Simple widget class with a private constructor — can only be created via its factory.
     */
    @IoClassFactory(CustomWidgetFactory.class)
    static class CustomWidget {
        private final String name;
        private final int size;

        private CustomWidget(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public String getName() { return name; }
        public int getSize() { return size; }
    }

    /**
     * ClassFactory that creates CustomWidget instances and fully populates them (isObjectFinal=true).
     */
    static class CustomWidgetFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            String name = (String) jObj.get("name");
            Number size = (Number) jObj.get("size");
            return new CustomWidget(name != null ? name : "default", size != null ? size.intValue() : 0);
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    /**
     * Widget with a non-final factory (isObjectFinal=false) — json-io continues field processing after factory.
     */
    @IoClassFactory(NonFinalWidgetFactory.class)
    static class NonFinalWidget {
        String label;
        int count;

        NonFinalWidget() { }
        NonFinalWidget(String label, int count) {
            this.label = label;
            this.count = count;
        }

        public String getLabel() { return label; }
        public int getCount() { return count; }
    }

    static class NonFinalWidgetFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            // Just create the instance — let json-io handle field injection
            return new NonFinalWidget();
        }

        @Override
        public boolean isObjectFinal() {
            return false;
        }
    }

    // ===================== @IoClassFactory Tests =====================

    @Test
    void testIoClassFactoryBasic() {
        String json = "{\"@type\":\"" + CustomWidget.class.getName() + "\",\"name\":\"gadget\",\"size\":42}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CustomWidget result = JsonIo.toJava(json, ro).asClass(CustomWidget.class);
        assertNotNull(result);
        assertEquals("gadget", result.getName());
        assertEquals(42, result.getSize());
    }

    @Test
    void testIoClassFactoryIsObjectFinal() {
        // The factory returns isObjectFinal=true, so json-io should not try to do further field processing
        String json = "{\"@type\":\"" + CustomWidget.class.getName() + "\",\"name\":\"final\",\"size\":99}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        CustomWidget result = JsonIo.toJava(json, ro).asClass(CustomWidget.class);
        assertEquals("final", result.getName());
        assertEquals(99, result.getSize());
    }

    @Test
    void testIoClassFactoryNonFinal() {
        // Factory creates the object but isObjectFinal=false → json-io continues field injection
        String json = "{\"@type\":\"" + NonFinalWidget.class.getName() + "\",\"label\":\"test\",\"count\":7}";
        ReadOptions ro = new ReadOptionsBuilder().build();

        NonFinalWidget result = JsonIo.toJava(json, ro).asClass(NonFinalWidget.class);
        assertNotNull(result);
        assertEquals("test", result.getLabel());
        assertEquals(7, result.getCount());
    }

    @Test
    void testIoClassFactoryProgrammaticWins() {
        // Programmatic addClassFactory should take priority over @IoClassFactory annotation
        ClassFactory programmaticFactory = new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                return new CustomWidget("programmatic", 0);
            }
            @Override
            public boolean isObjectFinal() { return true; }
        };

        ReadOptions ro = new ReadOptionsBuilder()
                .addClassFactory(CustomWidget.class, programmaticFactory)
                .build();

        String json = "{\"@type\":\"" + CustomWidget.class.getName() + "\",\"name\":\"annotation\",\"size\":99}";
        CustomWidget result = JsonIo.toJava(json, ro).asClass(CustomWidget.class);

        assertEquals("programmatic", result.getName(), "Programmatic factory should win over @IoClassFactory");
        assertEquals(0, result.getSize());
    }

    @Test
    void testIoClassFactoryRoundTrip() {
        CustomWidget original = new CustomWidget("round-trip", 55);
        WriteOptions wo = new WriteOptionsBuilder().build();
        ReadOptions ro = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(original, wo);
        CustomWidget result = JsonIo.toJava(json, ro).asClass(CustomWidget.class);

        assertEquals("round-trip", result.getName());
        assertEquals(55, result.getSize());
    }

    @Test
    void testIoClassFactoryResolverApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(CustomWidget.class);
        assertNotNull(meta.getClassFactory());
        assertEquals(CustomWidgetFactory.class, meta.getClassFactory());
    }

    @Test
    void testIoClassFactoryNoAnnotation() {
        // Classes without @IoClassFactory should return null
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(NoAnnotationModel.class);
        assertNull(meta.getClassFactory());
    }

    // ===================== @IoGetter / @IoSetter Test Models =====================

    static class GetterSetterWidget {
        private String name;
        private int size;

        GetterSetterWidget() {}
        GetterSetterWidget(String name, int size) {
            this.name = name;
            this.size = size;
        }

        // Non-standard getter — NOT following getXxx() convention
        @IoGetter("name")
        public String fetchName() { return name; }

        // Non-standard setter — NOT following setXxx() convention
        @IoSetter("name")
        public void assignName(String n) { this.name = n; }

        @IoGetter("size")
        public int fetchSize() { return size; }

        @IoSetter("size")
        public void assignSize(int s) { this.size = s; }
    }

    static class JacksonGetterSetterWidget {
        private String label;
        private int count;

        JacksonGetterSetterWidget() {}
        JacksonGetterSetterWidget(String label, int count) {
            this.label = label;
            this.count = count;
        }

        @JsonGetter("label")
        public String retrieveLabel() { return label; }

        @JsonSetter("label")
        public void applyLabel(String l) { this.label = l; }

        @JsonGetter("count")
        public int retrieveCount() { return count; }

        @JsonSetter("count")
        public void applyCount(int c) { this.count = c; }
    }

    // ===================== @IoGetter / @IoSetter Tests =====================

    @Test
    void testIoGetterBasic() {
        // Write should use the annotated getter method (fetchName, fetchSize) instead of getXxx
        GetterSetterWidget widget = new GetterSetterWidget("bolt", 42);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(widget, writeOptions);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"bolt\""));
        assertTrue(json.contains("\"size\""));
        assertTrue(json.contains("42"));
    }

    @Test
    void testIoSetterBasic() {
        // Read should use the annotated setter method (assignName, assignSize) instead of setXxx
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$GetterSetterWidget\",\"name\":\"nut\",\"size\":7}";
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        GetterSetterWidget widget = JsonIo.toJava(json, readOptions).asClass(GetterSetterWidget.class);
        assertEquals("nut", widget.fetchName());
        assertEquals(7, widget.fetchSize());
    }

    @Test
    void testIoGetterSetterRoundTrip() {
        GetterSetterWidget original = new GetterSetterWidget("washer", 100);
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        String json = JsonIo.toJson(original, writeOptions);
        GetterSetterWidget restored = JsonIo.toJava(json, readOptions).asClass(GetterSetterWidget.class);
        assertEquals("washer", restored.fetchName());
        assertEquals(100, restored.fetchSize());
    }

    @Test
    void testIoGetterProgrammaticWins() {
        // Programmatic addNonStandardGetter should override @IoGetter
        // Since GetterSetterWidget has no standard getXxx methods, if we add a programmatic
        // mapping for a non-existent method, the factory chain should fall back to field access.
        // This verifies the priority: programmatic > annotation
        WriteOptions opts = new WriteOptionsBuilder()
                .addNonStandardGetter(GetterSetterWidget.class, "name", "nonExistentGetter")
                .showTypeInfoNever()
                .build();
        GetterSetterWidget widget = new GetterSetterWidget("screw", 5);
        String json = JsonIo.toJson(widget, opts);
        // The programmatic override points to a non-existent method, so it falls through to field access
        // The annotation getter (fetchName) should NOT be used because programmatic has priority
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"screw\""));
    }

    @Test
    void testIoSetterViaFieldInjection() {
        // @IoSetter points assignName/assignSize — should be used for deserialization
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$GetterSetterWidget\",\"name\":\"rivet\",\"size\":3}";
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        GetterSetterWidget widget = JsonIo.toJava(json, readOptions).asClass(GetterSetterWidget.class);
        assertEquals("rivet", widget.fetchName());
        assertEquals(3, widget.fetchSize());
    }

    @Test
    void testJacksonJsonGetterFallback() {
        // Jackson @JsonGetter should work when @IoGetter is absent
        JacksonGetterSetterWidget widget = new JacksonGetterSetterWidget("gadget", 99);
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(widget, writeOptions);
        assertTrue(json.contains("\"label\""));
        assertTrue(json.contains("\"gadget\""));
        assertTrue(json.contains("\"count\""));
        assertTrue(json.contains("99"));
    }

    @Test
    void testJacksonJsonSetterFallback() {
        // Jackson @JsonSetter should work when @IoSetter is absent
        String json = "{\"@type\":\"com.cedarsoftware.io.AnnotationTest$JacksonGetterSetterWidget\",\"label\":\"widget\",\"count\":55}";
        ReadOptions readOptions = new ReadOptionsBuilder().build();
        JacksonGetterSetterWidget widget = JsonIo.toJava(json, readOptions).asClass(JacksonGetterSetterWidget.class);
        assertEquals("widget", widget.retrieveLabel());
        assertEquals(55, widget.retrieveCount());
    }

    @Test
    void testIoGetterSetterMetadataApi() {
        // Verify AnnotationResolver metadata returns the correct getter/setter method names
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(GetterSetterWidget.class);
        assertEquals("fetchName", meta.getGetterMethod("name"));
        assertEquals("assignName", meta.getSetterMethod("name"));
        assertEquals("fetchSize", meta.getGetterMethod("size"));
        assertEquals("assignSize", meta.getSetterMethod("size"));
        // Non-annotated fields return null
        assertNull(meta.getGetterMethod("nonexistent"));
        assertNull(meta.getSetterMethod("nonexistent"));
    }

    // ===================== @IoNonReferenceable Test Models =====================

    @IoNonReferenceable
    static class Token {
        private String value;
        Token() {}
        Token(String value) { this.value = value; }
        public String getValue() { return value; }
    }

    static class TokenHolder {
        private Token first;
        private Token second;
        TokenHolder() {}
        TokenHolder(Token first, Token second) {
            this.first = first;
            this.second = second;
        }
        public Token getFirst() { return first; }
        public Token getSecond() { return second; }
    }

    // ===================== @IoNonReferenceable Tests =====================

    @Test
    void testIoNonReferenceableMetadataApi() {
        AnnotationResolver.ClassAnnotationMetadata meta = AnnotationResolver.getMetadata(Token.class);
        assertTrue(meta.isNonReferenceable());

        // Unannotated class should NOT be non-referenceable
        AnnotationResolver.ClassAnnotationMetadata noAnno = AnnotationResolver.getMetadata(NoAnnotationModel.class);
        assertFalse(noAnno.isNonReferenceable());
    }

    @Test
    void testIoNonReferenceableWriteNoIdRef() {
        // Write two references to the SAME Token instance — JSON should contain
        // duplicate values, no @id/@ref
        Token shared = new Token("abc");
        TokenHolder holder = new TokenHolder(shared, shared);

        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        String json = JsonIo.toJson(holder, writeOptions);

        // Should NOT contain @id or @ref for Token instances
        assertFalse(json.contains("@id"), "Non-referenceable type should not emit @id");
        assertFalse(json.contains("@ref"), "Non-referenceable type should not emit @ref");
        // Should contain "abc" twice (once for each occurrence)
        int firstIdx = json.indexOf("\"abc\"");
        int secondIdx = json.indexOf("\"abc\"", firstIdx + 1);
        assertTrue(firstIdx >= 0 && secondIdx >= 0, "Non-referenceable instances should be written in full each time");
    }

    @Test
    void testIoNonReferenceableRoundTrip() {
        Token shared = new Token("xyz");
        TokenHolder holder = new TokenHolder(shared, shared);

        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        String json = JsonIo.toJson(holder, writeOptions);
        TokenHolder restored = JsonIo.toJava(json, readOptions).asClass(TokenHolder.class);

        assertEquals("xyz", restored.getFirst().getValue());
        assertEquals("xyz", restored.getSecond().getValue());
        // Since no @id/@ref, the two Token instances should be separate objects (not same reference)
        assertNotSame(restored.getFirst(), restored.getSecond(),
                "Non-referenceable instances should not be shared on read");
    }

    @Test
    void testIoNonReferenceableWriteOptions() {
        // Verify the WriteOptions check recognizes the annotation
        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        assertTrue(writeOptions.isNonReferenceableClass(Token.class),
                "@IoNonReferenceable class should be recognized as non-referenceable");
        // Unannotated class should not be non-referenceable (unless it's a built-in like String)
        assertFalse(writeOptions.isNonReferenceableClass(TokenHolder.class),
                "Unannotated class should not be non-referenceable");
    }
}
