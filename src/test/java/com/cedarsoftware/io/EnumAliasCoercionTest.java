package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify enum aliasing and coercion work correctly
 */
class EnumAliasCoercionTest {
    // Original enum in "old" package
    private enum OldEnum {
        ALPHA, BETA, GAMMA
    }

    // Same enum in "new" package - will be coerced
    private enum NewEnum {
        ALPHA, BETA, GAMMA
    }

    @Test
    void testEnum_aliasing() {
        // Create JSON using full class name
        String json = "{\n" +
                "  \"@type\": \"" + OldEnum.class.getName() + "\",\n" +
                "  \"name\": \"ALPHA\"\n" +
                "}";

        // Add alias
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .aliasTypeName(OldEnum.class.getName(), "OldEnum")
                .build();

        // Write using alias
        OldEnum source = OldEnum.ALPHA;
        String aliasedJson = TestUtil.toJson(source, writeOptions);

        // Verify the alias was used
        assertThat(aliasedJson).contains("\"@type\":\"OldEnum\"");

        // Create ReadOptions with matching alias - note the reversed order
        ReadOptions readOptions = new ReadOptionsBuilder()
                .aliasTypeName(OldEnum.class, "OldEnum")
                .build();

        // Verify it can be read back
        OldEnum target = TestUtil.toObjects(aliasedJson, readOptions, null);
        assertThat(target).isEqualTo(OldEnum.ALPHA);
    }

    @Test
    void testEnum_coercion() {
        // Create JSON with old enum type
        String json = "{\n" +
                "  \"@type\": \"" + OldEnum.class.getName() + "\",\n" +
                "  \"name\": \"BETA\"\n" +
                "}";

        // Add coercion
        ReadOptions readOptions = new ReadOptionsBuilder()
                .coerceClass(OldEnum.class.getName(), NewEnum.class)
                .build();

        // Read with coercion
        Object result = JsonIo.toObjects(json, readOptions, null);

        // Verify coercion worked
        assertThat(result)
                .isInstanceOf(NewEnum.class)
                .isEqualTo(NewEnum.BETA);
    }

    @Test
    void testEnum_aliasingAndCoercion() {
        // Start with old enum
        OldEnum source = OldEnum.GAMMA;

        // Create write options with alias
        WriteOptions writeOptions = new WriteOptionsBuilder()
                .aliasTypeName(OldEnum.class.getName(), "OldEnum")
                .build();

        // Write with alias
        String json = TestUtil.toJson(source, writeOptions);

        // Create read options with coercion
        ReadOptions readOptions = new ReadOptionsBuilder()
                .coerceClass(OldEnum.class.getName(), NewEnum.class)
                .aliasTypeName(OldEnum.class.getName(), "OldEnum")
                .build();

        // Read with coercion
        Object result = JsonIo.toObjects(json, readOptions, null);

        // Verify both alias and coercion worked
        assertThat(result)
                .isInstanceOf(NewEnum.class)
                .isEqualTo(NewEnum.GAMMA);

        // Verify the JSON used the alias
        assertThat(json).contains("\"@type\":\"OldEnum\"");
    }
}