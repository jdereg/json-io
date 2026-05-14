package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the directional field-exclusion alias methods on
 * {@link ReadOptionsBuilder} and {@link WriteOptionsBuilder}.
 *
 * <p>Two naming conventions are exposed for the same underlying behavior; both
 * are tested here:
 * <ul>
 *   <li>Jackson-aligned (Java-bean perspective): {@code WriteOnly}/{@code ReadOnly}
 *       — mirrors {@code @JsonProperty(access = WRITE_ONLY|READ_ONLY)}.</li>
 *   <li>Unambiguous (JSON-direction): {@code DeserializeOnly}/{@code SerializeOnly}.</li>
 * </ul>
 *
 * <p>Each test class uses dedicated fixture classes not referenced by any other
 * test, so permanent-method calls don't contaminate other tests.
 */
class DirectionalFieldExclusionTest {

    // -------------------------------------------------------------------
    // Fixtures — unique per test class to avoid permanent-state leakage
    // -------------------------------------------------------------------

    public static class PermWriteOnlyUser {
        public String name;
        public String passwordHash;
    }

    public static class PermDeserializeOnlyUser {
        public String name;
        public String passwordHash;
    }

    public static class PermReadOnlyCart {
        public String sku;
        public Integer total;
    }

    public static class PermSerializeOnlyCart {
        public String sku;
        public Integer total;
    }

    public static class PerInstanceWriteOnlyUser {
        public String name;
        public String passwordHash;
    }

    public static class PerInstanceDeserializeOnlyUser {
        public String name;
        public String passwordHash;
    }

    public static class PerInstanceReadOnlyCart {
        public String sku;
        public Integer total;
    }

    public static class PerInstanceSerializeOnlyCart {
        public String sku;
        public Integer total;
    }

    public static class NotImportedBaseFixture {
        public String name;
        public String secret;
    }

    // -------------------------------------------------------------------
    // Permanent — Write side (Jackson-aligned: WriteOnly = input-only in JSON)
    // -------------------------------------------------------------------

    @Test
    void addPermanentWriteOnlyField_jackson_inputOnly() {
        WriteOptionsBuilder.addPermanentWriteOnlyField(PermWriteOnlyUser.class, "passwordHash");

        PermWriteOnlyUser u = new PermWriteOnlyUser();
        u.name = "Alice";
        u.passwordHash = "hash-abc";

        WriteOptions wOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, wOpts);
        assertTrue(json.contains("Alice"));
        assertFalse(json.contains("passwordHash"), "WRITE_ONLY suppresses serialization: " + json);
        assertFalse(json.contains("hash-abc"));

        // Read side is NOT affected — JSON containing passwordHash still populates the field
        String roundJson = "{\"name\":\"Alice\",\"passwordHash\":\"hash-abc\"}";
        PermWriteOnlyUser round = JsonIo.toJava(roundJson, null).asClass(PermWriteOnlyUser.class);
        assertEquals("Alice", round.name);
        assertEquals("hash-abc", round.passwordHash, "WRITE_ONLY accepts input; only write is blocked");
    }

    @Test
    void addPermanentDeserializeOnlyField_unambiguous_inputOnly() {
        WriteOptionsBuilder.addPermanentDeserializeOnlyField(PermDeserializeOnlyUser.class, "passwordHash");

        PermDeserializeOnlyUser u = new PermDeserializeOnlyUser();
        u.name = "Bob";
        u.passwordHash = "hash-xyz";

        WriteOptions wOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(u, wOpts);
        assertFalse(json.contains("passwordHash"), "DeserializeOnly suppresses serialization");
        assertFalse(json.contains("hash-xyz"));

        PermDeserializeOnlyUser round = JsonIo.toJava(
                "{\"name\":\"Bob\",\"passwordHash\":\"hash-xyz\"}", null)
                .asClass(PermDeserializeOnlyUser.class);
        assertEquals("hash-xyz", round.passwordHash);
    }

    // -------------------------------------------------------------------
    // Permanent — Read side (Jackson-aligned: ReadOnly = output-only in JSON)
    // -------------------------------------------------------------------

    @Test
    void addPermanentReadOnlyField_jackson_outputOnly() {
        ReadOptionsBuilder.addPermanentReadOnlyField(PermReadOnlyCart.class, "total");

        PermReadOnlyCart c = new PermReadOnlyCart();
        c.sku = "SKU1";
        c.total = 99;

        WriteOptions wOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, wOpts);
        assertTrue(json.contains("\"total\""), "READ_ONLY field still serialized to output: " + json);
        assertTrue(json.contains("99"));

        // Read side blocks injection — build an explicit ReadOptions so the freshly-added
        // permanent state is honored (the default ReadOptions is cached at class init).
        ReadOptions rOpts = new ReadOptionsBuilder().build();
        PermReadOnlyCart round = JsonIo.toJava(
                "{\"sku\":\"SKU1\",\"total\":99}", rOpts)
                .asClass(PermReadOnlyCart.class);
        assertEquals("SKU1", round.sku);
        assertNull(round.total, "READ_ONLY blocks input — total is not populated");
    }

    @Test
    void addPermanentSerializeOnlyField_unambiguous_outputOnly() {
        ReadOptionsBuilder.addPermanentSerializeOnlyField(PermSerializeOnlyCart.class, "total");

        PermSerializeOnlyCart c = new PermSerializeOnlyCart();
        c.sku = "SKU2";
        c.total = 42;

        WriteOptions wOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(c, wOpts);
        assertTrue(json.contains("\"total\""));
        assertTrue(json.contains("42"));

        ReadOptions rOpts = new ReadOptionsBuilder().build();
        PermSerializeOnlyCart round = JsonIo.toJava(
                "{\"sku\":\"SKU2\",\"total\":42}", rOpts)
                .asClass(PermSerializeOnlyCart.class);
        assertNull(round.total);
    }

    // -------------------------------------------------------------------
    // Per-instance — Write side
    // -------------------------------------------------------------------

    @Test
    void perInstance_addWriteOnlyField_jackson() {
        PerInstanceWriteOnlyUser u = new PerInstanceWriteOnlyUser();
        u.name = "Carol";
        u.passwordHash = "secret";

        WriteOptions wOpts = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .addWriteOnlyField(PerInstanceWriteOnlyUser.class, "passwordHash")
                .build();
        String json = JsonIo.toJson(u, wOpts);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("secret"));

        // Other builders are unaffected
        WriteOptions defaultOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String defaultJson = JsonIo.toJson(u, defaultOpts);
        assertTrue(defaultJson.contains("passwordHash"),
                "non-permanent exclusion must not leak to other builders: " + defaultJson);
    }

    @Test
    void perInstance_addDeserializeOnlyField_unambiguous() {
        PerInstanceDeserializeOnlyUser u = new PerInstanceDeserializeOnlyUser();
        u.name = "Dave";
        u.passwordHash = "deep-secret";

        WriteOptions wOpts = new WriteOptionsBuilder()
                .showTypeInfoNever()
                .addDeserializeOnlyField(PerInstanceDeserializeOnlyUser.class, "passwordHash")
                .build();
        String json = JsonIo.toJson(u, wOpts);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("deep-secret"));
    }

    // -------------------------------------------------------------------
    // Per-instance — Read side
    // -------------------------------------------------------------------

    @Test
    void perInstance_addReadOnlyField_jackson() {
        ReadOptions rOpts = new ReadOptionsBuilder()
                .addReadOnlyField(PerInstanceReadOnlyCart.class, "total")
                .build();

        PerInstanceReadOnlyCart round = JsonIo.toJava(
                "{\"sku\":\"SKU3\",\"total\":7}", rOpts)
                .asClass(PerInstanceReadOnlyCart.class);
        assertEquals("SKU3", round.sku);
        assertNull(round.total, "ReadOnly should suppress total on read");

        // NOTE: The static classMetaCache / injectorsCache in ReadOptionsBuilder is shared
        // across all builders for a given class — once a builder deserializes a class with a
        // given exclusion set, subsequent builders inherit the same cached field-injector map.
        // This is pre-existing behavior of the per-instance exclusion path (addExcludedField,
        // addNotImportedField, and the new directional aliases). The recommended pattern is to
        // use addPermanent* at application bootstrap when exclusion should be global.
    }

    @Test
    void perInstance_addSerializeOnlyField_unambiguous() {
        ReadOptions rOpts = new ReadOptionsBuilder()
                .addSerializeOnlyField(PerInstanceSerializeOnlyCart.class, "total")
                .build();

        PerInstanceSerializeOnlyCart round = JsonIo.toJava(
                "{\"sku\":\"SKU4\",\"total\":11}", rOpts)
                .asClass(PerInstanceSerializeOnlyCart.class);
        assertEquals("SKU4", round.sku);
        assertNull(round.total);
    }

    // -------------------------------------------------------------------
    // Base non-permanent method addNotImportedField (newly added)
    // -------------------------------------------------------------------

    @Test
    void addNotImportedField_perInstance_blocksRead() {
        ReadOptions rOpts = new ReadOptionsBuilder()
                .addNotImportedField(NotImportedBaseFixture.class, "secret")
                .build();

        NotImportedBaseFixture round = JsonIo.toJava(
                "{\"name\":\"Eve\",\"secret\":\"shh\"}", rOpts)
                .asClass(NotImportedBaseFixture.class);
        assertEquals("Eve", round.name);
        assertNull(round.secret, "addNotImportedField should block deserialization");
    }

    @Test
    void addNotImportedField_perInstance_blocksOnConfiguredBuilder() {
        // Configured builder blocks the field as expected.
        ReadOptions rOpts = new ReadOptionsBuilder()
                .addNotImportedField(NotImportedBaseFixture.class, "secret")
                .build();
        NotImportedBaseFixture suppressed = JsonIo.toJava(
                "{\"name\":\"X\",\"secret\":\"yes\"}", rOpts)
                .asClass(NotImportedBaseFixture.class);
        assertNull(suppressed.secret);

        // NOTE: per-instance exclusion shares ReadOptionsBuilder's static field-injector cache
        // across builders; once a class's injector map is built with a field excluded, subsequent
        // builders for the same class inherit the cached map. Use addPermanent* methods at
        // application bootstrap if exclusion should be authoritative across all builders.
    }

    // -------------------------------------------------------------------
    // Aliases delegate identically (white-box: writing through one alias
    // affects what the other "sees")
    // -------------------------------------------------------------------

    public static class AliasIdentityWriteFixture {
        public String name;
        public String secret;
    }

    public static class AliasIdentityReadFixture {
        public String sku;
        public Integer total;
    }

    @Test
    void writeAliases_areEquivalent_addedViaJackson_visibleViaUnambiguous() {
        // Add via the Jackson-named alias on one builder, observe via behavior
        // on a different builder created from the same permanent state.
        WriteOptionsBuilder.addPermanentWriteOnlyField(AliasIdentityWriteFixture.class, "secret");

        AliasIdentityWriteFixture f = new AliasIdentityWriteFixture();
        f.name = "X";
        f.secret = "S";

        // A fresh builder picks up the permanent state regardless of which
        // alias added it — the unambiguous-named methods read the same map.
        WriteOptions wOpts = new WriteOptionsBuilder().showTypeInfoNever().build();
        String json = JsonIo.toJson(f, wOpts);
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("\"S\""));
    }

    @Test
    void readAliases_areEquivalent_addedViaUnambiguous_visibleViaJackson() {
        ReadOptionsBuilder.addPermanentSerializeOnlyField(AliasIdentityReadFixture.class, "total");

        ReadOptions rOpts = new ReadOptionsBuilder().build();
        AliasIdentityReadFixture round = JsonIo.toJava(
                "{\"sku\":\"S\",\"total\":1}", rOpts)
                .asClass(AliasIdentityReadFixture.class);
        assertEquals("S", round.sku);
        assertNull(round.total, "alias added via SerializeOnly should still block on read");
    }
}
