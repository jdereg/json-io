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
                .showTypeInfoAlways()
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
        OldEnum target = TestUtil.toJava(aliasedJson, readOptions).asClass(null);
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
        Object result = JsonIo.toJava(json, readOptions).asClass(null);

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
                .showTypeInfoAlways()
                .build();

        // Write with alias
        String json = TestUtil.toJson(source, writeOptions);

        // Create read options with coercion
        ReadOptions readOptions = new ReadOptionsBuilder()
                .coerceClass(OldEnum.class.getName(), NewEnum.class)
                .aliasTypeName(OldEnum.class.getName(), "OldEnum")
                .build();

        // Read with coercion
        Object result = JsonIo.toJava(json, readOptions).asClass(null);

        // Verify both alias and coercion worked
        assertThat(result)
                .isInstanceOf(NewEnum.class)
                .isEqualTo(NewEnum.GAMMA);

        // Verify the JSON used the alias
        assertThat(json).contains("\"@type\":\"OldEnum\"");
    }

    // Enum with constant that has a body (creates anonymous subclass)
    private enum EnumWithBody {
        PLAIN,
        WITH_BODY {
            @Override
            public String toString() {
                return "custom";
            }
        }
    }

    // Target enum for coercion
    private enum TargetEnum {
        PLAIN, WITH_BODY
    }

    /**
     * Tests enum coercion when the JSON @type is an anonymous enum subclass.
     * This exercises lines 1025-1026 in Resolver.resolveTargetType() where
     * the coercion is registered for the base enum class, not the anonymous subclass.
     */
    @Test
    void testEnum_coercion_anonymousSubclass() {
        // Get the anonymous subclass type (EnumWithBody$1)
        Class<?> anonymousSubclass = EnumWithBody.WITH_BODY.getClass();

        // Verify it's actually an anonymous subclass
        assertThat(anonymousSubclass).isNotEqualTo(EnumWithBody.class);
        assertThat(anonymousSubclass.isAnonymousClass()).isTrue();

        // Create JSON with the anonymous subclass type
        String json = "{\n" +
                "  \"@type\": \"" + anonymousSubclass.getName() + "\",\n" +
                "  \"name\": \"WITH_BODY\"\n" +
                "}";

        // Add coercion for the BASE enum class (not the anonymous subclass)
        ReadOptions readOptions = new ReadOptionsBuilder()
                .coerceClass(EnumWithBody.class.getName(), TargetEnum.class)
                .build();

        // Read with coercion - this exercises lines 1025-1026
        Object result = JsonIo.toJava(json, readOptions).asClass(null);

        // Verify coercion worked
        assertThat(result)
                .isInstanceOf(TargetEnum.class)
                .isEqualTo(TargetEnum.WITH_BODY);
    }
}