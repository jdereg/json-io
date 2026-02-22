package com.cedarsoftware.io;

import java.util.Map;

import com.cedarsoftware.io.annotation.IoAlias;
import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoIgnore;
import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.io.annotation.IoIgnoreProperties;
import com.cedarsoftware.io.annotation.IoInclude;
import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoPropertyOrder;
import com.cedarsoftware.io.reflect.AnnotationResolver;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
}
