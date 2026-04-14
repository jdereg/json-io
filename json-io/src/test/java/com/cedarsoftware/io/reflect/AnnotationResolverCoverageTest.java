package com.cedarsoftware.io.reflect;

import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import com.cedarsoftware.io.annotation.IoAlias;
import com.cedarsoftware.io.annotation.IoIgnore;
import com.cedarsoftware.io.annotation.IoIgnoreProperties;
import com.cedarsoftware.io.annotation.IoIncludeProperties;
import com.cedarsoftware.io.annotation.IoNaming;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoPropertyOrder;
import com.cedarsoftware.io.annotation.IoTypeName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage tests for AnnotationResolver — targets JaCoCo gaps for
 * json-io's own @Io* annotations. Jackson-reflective paths remain
 * uncovered in environments without Jackson on classpath (which is
 * our case — Jackson is a test-only dependency).
 */
class AnnotationResolverCoverageTest {

    // ========== @IoProperty rename ==========

    public static class PropertyRenamed {
        @IoProperty("full_name")
        public String name;
    }

    @Test
    void testIoPropertyRename() {
        PropertyRenamed p = new PropertyRenamed();
        p.name = "Alice";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(p, opts);
        assertThat(json).contains("full_name");
        assertThat(json).doesNotContain("\"name\"");
    }

    // ========== @IoIgnore ==========

    public static class FieldIgnored {
        public String keep;
        @IoIgnore
        public String skip;
    }

    @Test
    void testIoIgnore() {
        FieldIgnored f = new FieldIgnored();
        f.keep = "kept";
        f.skip = "ignored";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(f, opts);
        assertThat(json).contains("keep");
        assertThat(json).doesNotContain("skip");
    }

    // ========== @IoIgnoreProperties on class ==========

    @IoIgnoreProperties({"secret", "password"})
    public static class ClassIgnoreProps {
        public String publicData;
        public String secret;
        public String password;
    }

    @Test
    void testIoIgnoreProperties() {
        ClassIgnoreProps c = new ClassIgnoreProps();
        c.publicData = "ok";
        c.secret = "SENSITIVE";
        c.password = "SECRET";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertThat(json).contains("publicData");
        assertThat(json).doesNotContain("SENSITIVE");
        assertThat(json).doesNotContain("SECRET");
    }

    // ========== @IoIncludeProperties (whitelist) ==========

    @IoIncludeProperties({"keep"})
    public static class ClassIncludeOnly {
        public String keep;
        public String drop;
    }

    @Test
    void testIoIncludeProperties() {
        ClassIncludeOnly c = new ClassIncludeOnly();
        c.keep = "yes";
        c.drop = "no";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertThat(json).contains("yes");
        assertThat(json).doesNotContain("drop");
    }

    // ========== @IoAlias for read ==========

    public static class Aliased {
        @IoAlias({"n", "full_name"})
        public String name;
    }

    @Test
    void testIoAliasShortName() {
        String json = "{\"@type\":\"" + Aliased.class.getName() + "\",\"n\":\"Alice\"}";
        Aliased a = JsonIo.toJava(json).asClass(Aliased.class);
        assertThat(a.name).isEqualTo("Alice");
    }

    @Test
    void testIoAliasFullName() {
        String json = "{\"@type\":\"" + Aliased.class.getName() + "\",\"full_name\":\"Bob\"}";
        Aliased a = JsonIo.toJava(json).asClass(Aliased.class);
        assertThat(a.name).isEqualTo("Bob");
    }

    // ========== @IoPropertyOrder ==========

    @IoPropertyOrder({"z", "y", "x"})
    public static class OrderedFields {
        public int x;
        public int y;
        public int z;
    }

    @Test
    void testIoPropertyOrder() {
        OrderedFields o = new OrderedFields();
        o.x = 1;
        o.y = 2;
        o.z = 3;
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(o, opts);
        // Field order should respect the annotation
        int posZ = json.indexOf("\"z\"");
        int posY = json.indexOf("\"y\"");
        int posX = json.indexOf("\"x\"");
        assertThat(posZ).isLessThan(posY);
        assertThat(posY).isLessThan(posX);
    }

    // ========== @IoTypeName alias ==========

    @IoTypeName("shortName")
    public static class TypeNamed {
        public String label;
    }

    @Test
    void testIoTypeNameAlias() {
        TypeNamed t = new TypeNamed();
        t.label = "test";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoAlways().build();
        String json = JsonIo.toJson(t, opts);
        assertThat(json).contains("shortName");
    }

    // ========== @IoNaming strategy — SNAKE_CASE ==========

    @IoNaming(value = IoNaming.Strategy.SNAKE_CASE)
    public static class SnakeNamed {
        public String firstName;
        public String lastName;
    }

    @Test
    void testIoNamingSnakeCase() {
        SnakeNamed s = new SnakeNamed();
        s.firstName = "Alice";
        s.lastName = "Smith";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(s, opts);
        assertThat(json).contains("first_name");
        assertThat(json).contains("last_name");
    }

    // ========== @IoNaming strategy — KEBAB_CASE ==========

    @IoNaming(value = IoNaming.Strategy.KEBAB_CASE)
    public static class KebabNamed {
        public String firstName;
    }

    @Test
    void testIoNamingKebabCase() {
        KebabNamed k = new KebabNamed();
        k.firstName = "Alice";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(k, opts);
        assertThat(json).contains("first-name");
    }

    // ========== @IoNaming strategy — UPPER_CAMEL_CASE ==========

    @IoNaming(value = IoNaming.Strategy.UPPER_CAMEL_CASE)
    public static class UpperCamelNamed {
        public String firstName;
    }

    @Test
    void testIoNamingUpperCamelCase() {
        UpperCamelNamed u = new UpperCamelNamed();
        u.firstName = "Alice";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, opts);
        assertThat(json).contains("FirstName");
    }

    // ========== @IoNaming strategy — LOWER_DOT_CASE ==========

    @IoNaming(value = IoNaming.Strategy.LOWER_DOT_CASE)
    public static class DotNamed {
        public String firstName;
    }

    @Test
    void testIoNamingLowerDotCase() {
        DotNamed d = new DotNamed();
        d.firstName = "Alice";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(d, opts);
        assertThat(json).contains("first.name");
    }

    // ========== AnnotationResolver public access ==========

    @Test
    void testGetMetadataForSimpleClass() {
        AnnotationResolver.ClassAnnotationMetadata md = AnnotationResolver.getMetadata(PropertyRenamed.class);
        assertThat(md).isNotNull();
    }

    @Test
    void testGetMetadataCaching() {
        // Two calls should return same instance (cached)
        AnnotationResolver.ClassAnnotationMetadata m1 = AnnotationResolver.getMetadata(Aliased.class);
        AnnotationResolver.ClassAnnotationMetadata m2 = AnnotationResolver.getMetadata(Aliased.class);
        assertThat(m1).isSameAs(m2);
    }

    @Test
    void testGetMetadataForObject() {
        // Object.class should return non-null metadata
        AnnotationResolver.ClassAnnotationMetadata md = AnnotationResolver.getMetadata(Object.class);
        assertThat(md).isNotNull();
    }

    // ========== Inheritance — annotation on superclass ==========

    @IoIgnoreProperties({"inherited_skip"})
    public static class SuperClass {
        public String keep;
        public String inherited_skip;
    }

    public static class ChildClass extends SuperClass {
        public String childField;
    }

    @Test
    void testAnnotationsOnSuperclassInstance() {
        // SuperClass @IoIgnoreProperties applies to instances of SuperClass
        SuperClass s = new SuperClass();
        s.keep = "yes";
        s.inherited_skip = "no";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(s, opts);
        assertThat(json).contains("keep");
        assertThat(json).doesNotContain("inherited_skip");
    }

    // ========== Round-trip with annotations ==========

    @Test
    void testRoundTripWithRename() {
        PropertyRenamed p = new PropertyRenamed();
        p.name = "Alice";
        String json = JsonIo.toJson(p);
        PropertyRenamed restored = JsonIo.toJava(json).asClass(PropertyRenamed.class);
        assertThat(restored.name).isEqualTo("Alice");
    }

    @Test
    void testRoundTripWithSnakeCase() {
        SnakeNamed s = new SnakeNamed();
        s.firstName = "Alice";
        s.lastName = "Smith";
        String json = JsonIo.toJson(s);
        SnakeNamed restored = JsonIo.toJava(json).asClass(SnakeNamed.class);
        assertThat(restored.firstName).isEqualTo("Alice");
        assertThat(restored.lastName).isEqualTo("Smith");
    }
}
