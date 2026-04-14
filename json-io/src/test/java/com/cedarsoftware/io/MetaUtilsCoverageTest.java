package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coverage tests for MetaUtils — targets JaCoCo gaps for non-deprecated
 * methods. Deprecated methods are excluded per user policy (they'll
 * be removed in 5.0).
 *
 * Covers:
 * - getLogMessage variations
 * - getValueWithDefaultForNull (map null, value null, cast error)
 * - getValueWithDefaultForMissing (map null, key missing, cast error)
 * - loadMapDefinition validation (null/empty, directory traversal)
 * - loadSetDefinition validation (null/empty, directory traversal)
 */
class MetaUtilsCoverageTest {

    // ========== getLogMessage ==========

    @Test
    void testGetLogMessageBasic() {
        String msg = MetaUtils.getLogMessage("myMethod", new Object[]{"arg1", 42});
        assertThat(msg).contains("myMethod");
        assertThat(msg).contains("arg1");
        assertThat(msg).contains("42");
    }

    @Test
    void testGetLogMessageWithCustomLength() {
        String msg = MetaUtils.getLogMessage("myMethod", new Object[]{"short"}, 128);
        assertThat(msg).contains("myMethod");
        assertThat(msg).contains("short");
    }

    @Test
    void testGetLogMessageTruncation() {
        // Long arg should be truncated to argCharLen
        StringBuilder longArg = new StringBuilder();
        for (int i = 0; i < 200; i++) longArg.append("x");
        String msg = MetaUtils.getLogMessage("myMethod", new Object[]{longArg.toString()}, 32);
        assertThat(msg).contains("...");
    }

    @Test
    void testGetLogMessageEmptyArgs() {
        String msg = MetaUtils.getLogMessage("noArgs", new Object[0]);
        assertThat(msg).contains("noArgs");
        assertThat(msg).endsWith(")");
    }

    @Test
    void testGetLogMessageWithNullArg() {
        String msg = MetaUtils.getLogMessage("method", new Object[]{null});
        assertThat(msg).contains("method");
    }

    // ========== getValueWithDefaultForNull ==========

    @Test
    void testGetValueWithDefaultForNullMap() {
        // Null map returns default
        String result = MetaUtils.getValueWithDefaultForNull(null, "key", "default");
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testGetValueWithDefaultForNullValue() {
        Map<String, String> map = new HashMap<>();
        map.put("key", null);  // explicit null
        String result = MetaUtils.getValueWithDefaultForNull(map, "key", "default");
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testGetValueWithDefaultForNullPresentValue() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "actual");
        String result = MetaUtils.getValueWithDefaultForNull(map, "key", "default");
        assertThat(result).isEqualTo("actual");
    }

    @Test
    void testGetValueWithDefaultForNullMissingKey() {
        Map<String, String> map = new HashMap<>();
        String result = MetaUtils.getValueWithDefaultForNull(map, "missing", "default");
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testGetValueWithDefaultForNullCastError() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", 42); // Integer, not String
        assertThatThrownBy(() -> {
            String result = MetaUtils.getValueWithDefaultForNull(map, "key", "default");
            // Force a cast through
            result.length();
        }).isInstanceOfAny(ClassCastException.class, JsonIoException.class);
    }

    // ========== getValueWithDefaultForMissing ==========

    @Test
    void testGetValueWithDefaultForMissingMap() {
        String result = MetaUtils.getValueWithDefaultForMissing(null, "key", "default");
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testGetValueWithDefaultForMissingKey() {
        Map<String, String> map = new HashMap<>();
        String result = MetaUtils.getValueWithDefaultForMissing(map, "notThere", "default");
        assertThat(result).isEqualTo("default");
    }

    @Test
    void testGetValueWithDefaultForMissingNullValue() {
        // When key IS present but value is null, returns null (different from ForNull variant)
        Map<String, String> map = new HashMap<>();
        map.put("key", null);
        String result = MetaUtils.getValueWithDefaultForMissing(map, "key", "default");
        assertThat(result).isNull();
    }

    @Test
    void testGetValueWithDefaultForMissingPresentValue() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "actual");
        String result = MetaUtils.getValueWithDefaultForMissing(map, "key", "default");
        assertThat(result).isEqualTo("actual");
    }

    // ========== loadMapDefinition — validation errors ==========

    @Test
    void testLoadMapDefinitionNullName() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void testLoadMapDefinitionEmptyName() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition(""))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void testLoadMapDefinitionWhitespaceName() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition("   "))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void testLoadMapDefinitionDirectoryTraversal() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition("../etc/passwd"))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid resource name");
    }

    @Test
    void testLoadMapDefinitionBackslash() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition("folder\\file"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadMapDefinitionAbsolutePath() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition("/etc/passwd"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadMapDefinitionNonexistentFile() {
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition("nonexistent-" + System.nanoTime() + ".txt"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadMapDefinitionWithReadOptions() {
        ReadOptions opts = new ReadOptionsBuilder().build();
        assertThatThrownBy(() -> MetaUtils.loadMapDefinition(null, opts))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== loadSetDefinition — validation errors ==========

    @Test
    void testLoadSetDefinitionNullName() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void testLoadSetDefinitionEmptyName() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition(""))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadSetDefinitionDirectoryTraversal() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition("../secret"))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Invalid resource");
    }

    @Test
    void testLoadSetDefinitionBackslash() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition("folder\\file"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadSetDefinitionAbsolutePath() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition("/absolute/path"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadSetDefinitionNonexistentFile() {
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition("nonexistent-" + System.nanoTime() + ".txt"))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testLoadSetDefinitionWithReadOptions() {
        ReadOptions opts = new ReadOptionsBuilder().build();
        assertThatThrownBy(() -> MetaUtils.loadSetDefinition(null, opts))
                .isInstanceOf(JsonIoException.class);
    }

    // ========== loadMapDefinition — actually load a valid resource ==========

    @Test
    void testLoadMapDefinitionValidResource() {
        // aliases.txt is in the config/ subfolder
        Map<String, String> map = MetaUtils.loadMapDefinition("config/aliases.txt");
        assertThat(map).isNotNull().isNotEmpty();
    }

    @Test
    void testLoadSetDefinitionValidResource() {
        Set<String> set = MetaUtils.loadSetDefinition("config/nonRefs.txt");
        assertThat(set).isNotNull();
    }
}
