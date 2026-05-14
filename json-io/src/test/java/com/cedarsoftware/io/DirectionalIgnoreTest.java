package com.cedarsoftware.io;

import java.math.BigDecimal;

import com.cedarsoftware.io.annotation.IoIgnore;
import com.cedarsoftware.io.annotation.IoIgnoreProperties;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.reflect.AnnotationResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Jackson-aligned directional ignore controls:
 * <ul>
 *   <li>{@code @IoProperty(access = WRITE_ONLY)} — accepted on read, suppressed on write
 *       (mirrors {@code @JsonProperty(access = WRITE_ONLY)}).</li>
 *   <li>{@code @IoProperty(access = READ_ONLY)} — serialized to output, ignored on input
 *       (mirrors {@code @JsonProperty(access = READ_ONLY)}).</li>
 *   <li>{@code @IoIgnoreProperties(allowGetters = true)} — class-level read-only escape.</li>
 *   <li>{@code @IoIgnoreProperties(allowSetters = true)} — class-level write-only escape.</li>
 * </ul>
 */
class DirectionalIgnoreTest {

    // -------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------

    /** Password is deserialized but never written. Classic "secret hash" use case. */
    static class UserWithWriteOnlyPassword {
        public String name;
        @IoProperty(access = IoProperty.Access.WRITE_ONLY)
        public String passwordHash;
    }

    /** Computed total is written to output, but ignored if input JSON tries to set it. */
    static class CartWithReadOnlyTotal {
        public String sku;
        @IoProperty(access = IoProperty.Access.READ_ONLY)
        public BigDecimal computedTotal;
    }

    /** access = AUTO (default) — same as no constraint. */
    static class UserAuto {
        public String name;
        @IoProperty(access = IoProperty.Access.AUTO)
        public String tag;
    }

    /** access = READ_WRITE — explicit "both directions", same effect as AUTO. */
    static class UserReadWrite {
        public String name;
        @IoProperty(access = IoProperty.Access.READ_WRITE)
        public String tag;
    }

    /** Combined rename + directional access. */
    static class CombinedRenameAccess {
        @IoProperty(value = "pwd", access = IoProperty.Access.WRITE_ONLY)
        public String passwordHash;
        public String name;
    }

    /** @IoIgnore (both sides) plus @IoProperty(access=...) — both-sides wins. */
    static class IgnoreAndAccessConflict {
        public String name;
        @IoIgnore
        @IoProperty(access = IoProperty.Access.WRITE_ONLY)
        public String redundantField;
    }

    /** Class-level allowGetters=true: write allowed, read blocked. */
    @IoIgnoreProperties(value = {"computedTotal"}, allowGetters = true)
    static class ClassLevelAllowGetters {
        public String sku;
        public BigDecimal computedTotal;
    }

    /** Class-level allowSetters=true: read allowed, write blocked. */
    @IoIgnoreProperties(value = {"passwordHash"}, allowSetters = true)
    static class ClassLevelAllowSetters {
        public String name;
        public String passwordHash;
    }

    /** Class-level both=true: no exclusion. */
    @IoIgnoreProperties(value = {"foo"}, allowGetters = true, allowSetters = true)
    static class ClassLevelBothAllowed {
        public String name;
        public String foo;
    }

    /** Class-level defaults (both false) — full both-sides exclusion (existing behavior). */
    @IoIgnoreProperties({"foo"})
    static class ClassLevelDefaultIgnore {
        public String name;
        public String foo;
    }

    /** Empty IoProperty (rename-only path, no access constraint). */
    static class RenameOnly {
        @IoProperty("display_name")
        public String name;
    }

    // -------------------------------------------------------------------
    // @IoProperty(access = WRITE_ONLY) — write-blocked, read-allowed
    // -------------------------------------------------------------------

    @Test
    void writeOnly_passwordSuppressedOnSerialize() {
        UserWithWriteOnlyPassword u = new UserWithWriteOnlyPassword();
        u.name = "Alice";
        u.passwordHash = "abc123";

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, opts);

        assertTrue(json.contains("\"name\":\"Alice\""), "name should be serialized: " + json);
        assertFalse(json.contains("passwordHash"), "passwordHash must not appear in JSON: " + json);
        assertFalse(json.contains("abc123"), "secret value must not leak: " + json);
    }

    @Test
    void writeOnly_passwordAcceptedOnDeserialize() {
        String json = "{\"name\":\"Alice\",\"passwordHash\":\"abc123\"}";
        UserWithWriteOnlyPassword u = JsonIo.toJava(json, null).asClass(UserWithWriteOnlyPassword.class);

        assertEquals("Alice", u.name);
        assertEquals("abc123", u.passwordHash, "WRITE_ONLY field should still be set from input JSON");
    }

    // -------------------------------------------------------------------
    // @IoProperty(access = READ_ONLY) — read-blocked, write-allowed
    // -------------------------------------------------------------------

    @Test
    void readOnly_totalSerializedToOutput() {
        CartWithReadOnlyTotal c = new CartWithReadOnlyTotal();
        c.sku = "SKU1";
        c.computedTotal = new BigDecimal("99.99");

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);

        assertTrue(json.contains("\"sku\":\"SKU1\""));
        assertTrue(json.contains("computedTotal"), "READ_ONLY field should appear in output: " + json);
    }

    @Test
    void readOnly_totalIgnoredOnDeserialize() {
        // Input tries to set computedTotal — it should be silently dropped
        String json = "{\"sku\":\"SKU1\",\"computedTotal\":\"99.99\"}";
        CartWithReadOnlyTotal c = JsonIo.toJava(json, null).asClass(CartWithReadOnlyTotal.class);

        assertEquals("SKU1", c.sku);
        assertNull(c.computedTotal, "READ_ONLY field must not be populated from input JSON");
    }

    // -------------------------------------------------------------------
    // AUTO / READ_WRITE — no constraint (default behaviour)
    // -------------------------------------------------------------------

    @Test
    void auto_behavesAsUnconstrained() {
        UserAuto u = new UserAuto();
        u.name = "X";
        u.tag = "TAG";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, opts);
        assertTrue(json.contains("\"tag\":\"TAG\""));

        UserAuto round = JsonIo.toJava(json, null).asClass(UserAuto.class);
        assertEquals("TAG", round.tag);
    }

    @Test
    void readWrite_behavesAsUnconstrained() {
        UserReadWrite u = new UserReadWrite();
        u.name = "X";
        u.tag = "TAG";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, opts);
        assertTrue(json.contains("\"tag\":\"TAG\""));

        UserReadWrite round = JsonIo.toJava(json, null).asClass(UserReadWrite.class);
        assertEquals("TAG", round.tag);
    }

    // -------------------------------------------------------------------
    // Combined rename + access
    // -------------------------------------------------------------------

    @Test
    void combined_renameAndWriteOnly() {
        CombinedRenameAccess c = new CombinedRenameAccess();
        c.passwordHash = "secret";
        c.name = "U";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertFalse(json.contains("pwd"), "WRITE_ONLY suppresses output of the renamed field: " + json);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("secret"));

        // On input the rename target is recognized and the field is populated
        CombinedRenameAccess round = JsonIo.toJava("{\"pwd\":\"secret\",\"name\":\"U\"}", null)
                .asClass(CombinedRenameAccess.class);
        assertEquals("secret", round.passwordHash, "rename + WRITE_ONLY: input under 'pwd' populates passwordHash");
        assertEquals("U", round.name);
    }

    // -------------------------------------------------------------------
    // @IoIgnore + @IoProperty(access=...) — @IoIgnore is strictly stronger
    // -------------------------------------------------------------------

    @Test
    void ioIgnore_overridesAccess() {
        IgnoreAndAccessConflict c = new IgnoreAndAccessConflict();
        c.name = "X";
        c.redundantField = "VAL";

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertFalse(json.contains("redundantField"), "field should be missing from output");

        IgnoreAndAccessConflict round = JsonIo.toJava(
                "{\"name\":\"X\",\"redundantField\":\"VAL\"}", null).asClass(IgnoreAndAccessConflict.class);
        assertNull(round.redundantField, "@IoIgnore strictly excludes on both sides");
    }

    // -------------------------------------------------------------------
    // @IoIgnoreProperties(allowGetters = true) — class-level read-blocked
    // -------------------------------------------------------------------

    @Test
    void classLevelAllowGetters_writeAllowed_readBlocked() {
        ClassLevelAllowGetters c = new ClassLevelAllowGetters();
        c.sku = "S1";
        c.computedTotal = new BigDecimal("50.00");

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertTrue(json.contains("computedTotal"), "allowGetters=true keeps field in output: " + json);

        ClassLevelAllowGetters round = JsonIo.toJava(json, null).asClass(ClassLevelAllowGetters.class);
        assertEquals("S1", round.sku);
        assertNull(round.computedTotal, "allowGetters=true still blocks deserialization");
    }

    // -------------------------------------------------------------------
    // @IoIgnoreProperties(allowSetters = true) — class-level write-blocked
    // -------------------------------------------------------------------

    @Test
    void classLevelAllowSetters_readAllowed_writeBlocked() {
        ClassLevelAllowSetters c = new ClassLevelAllowSetters();
        c.name = "U";
        c.passwordHash = "secret";

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertFalse(json.contains("passwordHash"), "allowSetters=true blocks the serialize side: " + json);

        ClassLevelAllowSetters round = JsonIo.toJava(
                "{\"name\":\"U\",\"passwordHash\":\"secret\"}", null).asClass(ClassLevelAllowSetters.class);
        assertEquals("secret", round.passwordHash, "allowSetters=true keeps deserialization working");
    }

    // -------------------------------------------------------------------
    // Both true — no exclusion at all
    // -------------------------------------------------------------------

    @Test
    void classLevelBothAllowed_noExclusion() {
        ClassLevelBothAllowed c = new ClassLevelBothAllowed();
        c.name = "U";
        c.foo = "F";

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertTrue(json.contains("foo"));

        ClassLevelBothAllowed round = JsonIo.toJava(json, null).asClass(ClassLevelBothAllowed.class);
        assertEquals("F", round.foo);
    }

    // -------------------------------------------------------------------
    // Class-level defaults — both sides ignored (existing behaviour)
    // -------------------------------------------------------------------

    @Test
    void classLevelDefault_bothSidesIgnored() {
        ClassLevelDefaultIgnore c = new ClassLevelDefaultIgnore();
        c.name = "U";
        c.foo = "F";

        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, opts);
        assertFalse(json.contains("foo"));

        ClassLevelDefaultIgnore round = JsonIo.toJava(
                "{\"name\":\"U\",\"foo\":\"F\"}", null).asClass(ClassLevelDefaultIgnore.class);
        assertNull(round.foo, "default IoIgnoreProperties still blocks input");
    }

    // -------------------------------------------------------------------
    // Rename-only path — @IoProperty("name") with no access still works
    // -------------------------------------------------------------------

    @Test
    void renameOnly_stillWorks() {
        RenameOnly r = new RenameOnly();
        r.name = "Alice";
        WriteOptions opts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(r, opts);
        assertTrue(json.contains("display_name"), "rename without access should still rename: " + json);

        RenameOnly round = JsonIo.toJava(json, null).asClass(RenameOnly.class);
        assertEquals("Alice", round.name);
    }

    // -------------------------------------------------------------------
    // Direct AnnotationResolver assertions (white-box)
    // -------------------------------------------------------------------

    @Test
    void resolver_writeOnly_directionalSets() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(UserWithWriteOnlyPassword.class);
        assertTrue(m.isIgnoredOnWrite("passwordHash"));
        assertFalse(m.isIgnoredOnRead("passwordHash"));
        assertTrue(m.isIgnored("passwordHash"), "union still flags it");
        assertFalse(m.isIgnoredOnRead("name"));
        assertFalse(m.isIgnoredOnWrite("name"));
    }

    @Test
    void resolver_readOnly_directionalSets() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(CartWithReadOnlyTotal.class);
        assertTrue(m.isIgnoredOnRead("computedTotal"));
        assertFalse(m.isIgnoredOnWrite("computedTotal"));
        assertTrue(m.isIgnored("computedTotal"));
    }

    @Test
    void resolver_classLevelAllowGetters_readOnlySet() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(ClassLevelAllowGetters.class);
        assertTrue(m.isIgnoredOnRead("computedTotal"));
        assertFalse(m.isIgnoredOnWrite("computedTotal"));
    }

    @Test
    void resolver_classLevelAllowSetters_writeOnlySet() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(ClassLevelAllowSetters.class);
        assertTrue(m.isIgnoredOnWrite("passwordHash"));
        assertFalse(m.isIgnoredOnRead("passwordHash"));
    }

    @Test
    void resolver_classLevelBothAllowed_noIgnoreAtAll() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(ClassLevelBothAllowed.class);
        assertFalse(m.isIgnoredOnRead("foo"));
        assertFalse(m.isIgnoredOnWrite("foo"));
        assertFalse(m.isIgnored("foo"));
    }

    @Test
    void resolver_classLevelDefault_bothSidesIgnored() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(ClassLevelDefaultIgnore.class);
        assertTrue(m.isIgnoredOnRead("foo"));
        assertTrue(m.isIgnoredOnWrite("foo"));
        assertTrue(m.isIgnored("foo"));
    }

    @Test
    void resolver_ioIgnoreUnionWithAccess() {
        AnnotationResolver.ClassAnnotationMetadata m =
                AnnotationResolver.getMetadata(IgnoreAndAccessConflict.class);
        // @IoIgnore wins — both directions blocked
        assertTrue(m.isIgnoredOnRead("redundantField"));
        assertTrue(m.isIgnoredOnWrite("redundantField"));
    }
}
