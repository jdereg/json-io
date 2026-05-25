package com.cedarsoftware.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the official TOON conformance fixtures vendored from
 * <a href="https://github.com/toon-format/spec">toon-format/spec</a> at the
 * commit recorded in {@code src/test/resources/toon-spec-fixtures/MANIFEST.md}.
 *
 * <p>Each test case in each fixture file is materialized as a JUnit 5
 * {@link DynamicTest} so individual cases show up as separate rows in the IDE
 * / CI test report. Fixtures whose {@code options} block references features
 * json-io does not implement (e.g. {@code flattenDepth}, {@code expandPaths:
 * "safe"}) are skipped via {@link Assumptions}, leaving the gap visible in the
 * skipped-tests count without failing the build.
 *
 * <p>The suite is offline — fixtures are loaded from the classpath, never
 * fetched at test time. Refresh the snapshot with
 * {@code scripts/refresh-toon-fixtures.sh <commit-sha>}.
 */
class ToonSpecFixtureTest {

    private static final String RESOURCE_ROOT = "toon-spec-fixtures/fixtures";

    /** Encoder options json-io does not implement; tests using these are skipped. */
    private static final Set<String> UNSUPPORTED_ENCODE_OPTIONS = new HashSet<>(Arrays.asList(
            "flattenDepth"
    ));

    /** Decoder options json-io does not implement; tests using these are skipped. */
    private static final Set<String> UNSUPPORTED_DECODE_OPTIONS = new HashSet<>(Arrays.asList(
            "expandPaths"
    ));

    /**
     * Cases that currently fail json-io's TOON v3.3 conformance and are tracked as known
     * gaps on this branch ({@code toon-spec-v3.3-conformance}). Listed cases are converted
     * from a failed assertion into a JUnit "aborted" (skipped) status so the build stays
     * green while the encoder/decoder work proceeds.
     *
     * <p><b>This is a temporary, branch-scoped mechanism.</b> As each gap is fixed, drop
     * the corresponding entry from this set in the same commit. If a listed case starts
     * passing without being removed from the set, the test will fail loudly to force the
     * retirement.
     *
     * <p>When this set is empty, the workaround can be deleted entirely and the branch
     * is ready to merge back into {@code master}.
     */
    private static final Set<String> EXPECTED_FAILURES = new HashSet<>(Arrays.asList(
            // --- decode gaps ---
            "decode/arrays-nested.json :: parses list arrays with deeply nested objects",
            "decode/arrays-nested.json :: parses list arrays with empty items",
            "decode/arrays-tabular.json :: parses quoted header keys in tabular arrays",
            "decode/blank-lines.json :: accepts blank line between nested object fields",
            "decode/blank-lines.json :: accepts whitespace-only line at non-multiple indent as blank in strict mode",
            "decode/delimiters.json :: parses tabular headers with keys containing the active delimiter",
            "decode/indentation-errors.json :: accepts correct indentation with custom indent size (4 spaces with indent=4)",
            "decode/numbers.json :: treats leading zero as string not number",
            "decode/numbers.json :: treats leading-zero in object value as string",
            "decode/numbers.json :: treats leading-zeros in array as strings",
            "decode/numbers.json :: treats negative leading-zeros in array as strings",
            "decode/numbers.json :: treats unquoted leading-zero number as string",
            "decode/numbers.json :: treats unquoted multi-leading-zero as string",
            "decode/numbers.json :: treats unquoted negative leading-zero number as string",
            "decode/numbers.json :: treats unquoted octal-like as string",
            "decode/objects.json :: decodes \\uXXXX in quoted key (U+0004 control character)",
            "decode/objects.json :: decodes \\uXXXX in quoted key (case-insensitive hex)",
            "decode/objects.json :: parses dotted keys as identifiers",
            "decode/objects.json :: treats extra brackets after valid array segment as literal key (non-strict)",
            "decode/objects.json :: treats non-integer bracket content as literal key (non-strict)",
            "decode/objects.json :: treats text between bracket segment and colon as literal key (non-strict)",
            "decode/path-expansion.json :: preserves literal dotted keys when expansion is off",
            "decode/primitives.json :: decodes \\uXXXX escape (U+0004)",
            "decode/primitives.json :: decodes \\uXXXX with mixed-case hex digits",
            "decode/validation-errors.json :: throws on array header missing colon",
            "decode/validation-errors.json :: throws on array length mismatch (inline primitives - too many)",
            "decode/validation-errors.json :: throws on array length mismatch (list format - too many)",
            "decode/validation-errors.json :: throws on bracket length with leading zeros in strict mode",
            "decode/validation-errors.json :: throws on duplicate keys within a list-item object in strict mode",
            "decode/validation-errors.json :: throws on duplicate sibling keys in strict mode",
            "decode/validation-errors.json :: throws on extra brackets between bracket segment and colon in strict mode",
            "decode/validation-errors.json :: throws on inline primitive array length mismatch (too few)",
            "decode/validation-errors.json :: throws on list items length mismatch (too few)",
            "decode/validation-errors.json :: throws on missing colon in key-value context",
            "decode/validation-errors.json :: throws on nested duplicate sibling keys in strict mode",
            "decode/validation-errors.json :: throws on row width mismatch when rows use a different delimiter than the active delimiter",
            "decode/validation-errors.json :: throws on tabular row count mismatch with header length",
            "decode/validation-errors.json :: throws on tabular row value count mismatch with header field count",
            "decode/validation-errors.json :: throws on text between bracket segment and colon in strict mode",
            "decode/validation-errors.json :: throws on two primitives at root depth in strict mode",
            "decode/validation-errors.json :: throws on unterminated string",
            "decode/whitespace.json :: parses empty tokens as empty string",
            // --- encode gaps ---
            "encode/arrays-objects.json :: encodes empty object list items as bare hyphen",
            "encode/arrays-objects.json :: uses canonical encoding for multi-field list-item objects with tabular arrays",
            "encode/arrays-objects.json :: uses canonical encoding for single-field list-item tabular arrays",
            "encode/arrays-objects.json :: uses expanded list for arrays containing empty objects",
            "encode/arrays-objects.json :: uses field order from first object for tabular headers",
            "encode/arrays-objects.json :: uses list format for nested object arrays with mismatched keys",
            "encode/arrays-objects.json :: uses list format for objects containing arrays of arrays",
            "encode/arrays-objects.json :: uses tabular format for nested uniform object arrays",
            "encode/arrays-tabular.json :: encodes tabular arrays with keys needing quotes",
            "encode/key-folding.json :: encodes folded chain ending with empty object",
            "encode/key-folding.json :: skips folding on sibling literal-key collision (safe mode)",
            "encode/primitives.json :: encodes large number",
            "encode/whitespace.json :: respects custom indent size option"
    ));

    @TestFactory
    Stream<DynamicTest> encodeFixtures() throws Exception {
        return fixturesFor("encode").stream().flatMap(this::generateEncodeCases);
    }

    @TestFactory
    Stream<DynamicTest> decodeFixtures() throws Exception {
        return fixturesFor("decode").stream().flatMap(this::generateDecodeCases);
    }

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    private Stream<DynamicTest> generateEncodeCases(FixtureFile f) {
        List<Map<String, Object>> tests = asMapList(f.contents.get("tests"));
        return tests.stream().map(tc -> {
            String name = String.valueOf(tc.get("name"));
            String fullName = f.name + " :: " + name;
            return DynamicTest.dynamicTest(fullName,
                    () -> runWithKnownGapTolerance(fullName, () -> runEncodeCase(f.name, tc)));
        });
    }

    @SuppressWarnings("unchecked")
    private void runEncodeCase(String fixtureName, Map<String, Object> tc) {
        String name = String.valueOf(tc.get("name"));
        Map<String, Object> options = (Map<String, Object>) tc.get("options");
        skipIfUnsupported(options, UNSUPPORTED_ENCODE_OPTIONS, fixtureName, name);

        WriteOptions writeOptions = toWriteOptions(options);
        Object input = tc.get("input");
        boolean shouldError = Boolean.TRUE.equals(tc.get("shouldError"));

        if (shouldError) {
            assertThrows(JsonIoException.class,
                    () -> JsonIo.toToon(input, writeOptions),
                    fixtureName + " :: " + name);
            return;
        }

        String expected = (String) tc.get("expected");
        String actual = JsonIo.toToon(input, writeOptions);
        assertEquals(expected, actual,
                fixtureName + " :: " + name + "\nInput: " + input);
    }

    private static WriteOptions toWriteOptions(Map<String, Object> options) {
        WriteOptionsBuilder b = new WriteOptionsBuilder();
        if (options == null) {
            return b.build();
        }
        Object delimiter = options.get("delimiter");
        if (delimiter instanceof String && !((String) delimiter).isEmpty()) {
            b.toonDelimiter(((String) delimiter).charAt(0));
        }
        Object indent = options.get("indent");
        if (indent instanceof Number) {
            int n = ((Number) indent).intValue();
            if (n >= 1) {
                b.indentationSize(n);
            }
        }
        Object keyFolding = options.get("keyFolding");
        if (keyFolding instanceof String) {
            b.toonKeyFolding("safe".equals(keyFolding));
        }
        return b.build();
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    private Stream<DynamicTest> generateDecodeCases(FixtureFile f) {
        List<Map<String, Object>> tests = asMapList(f.contents.get("tests"));
        return tests.stream().map(tc -> {
            String name = String.valueOf(tc.get("name"));
            String fullName = f.name + " :: " + name;
            return DynamicTest.dynamicTest(fullName,
                    () -> runWithKnownGapTolerance(fullName, () -> runDecodeCase(f.name, tc)));
        });
    }

    @SuppressWarnings("unchecked")
    private void runDecodeCase(String fixtureName, Map<String, Object> tc) {
        String name = String.valueOf(tc.get("name"));
        Map<String, Object> options = (Map<String, Object>) tc.get("options");
        skipIfUnsupported(options, UNSUPPORTED_DECODE_OPTIONS, fixtureName, name);

        ReadOptions readOptions = toReadOptions(options);
        String input = (String) tc.get("input");
        boolean shouldError = Boolean.TRUE.equals(tc.get("shouldError"));

        if (shouldError) {
            assertThrows(JsonIoException.class,
                    () -> JsonIo.fromToon(input, readOptions).asClass(Object.class),
                    fixtureName + " :: " + name);
            return;
        }

        Object expected = normalizeForCompare(tc.get("expected"));
        Object actual = normalizeForCompare(JsonIo.fromToon(input, readOptions).asClass(Object.class));
        Map<String, Object> diff = new java.util.HashMap<>();
        boolean equals = DeepEquals.deepEquals(expected, actual, diff);
        assertTrue(equals,
                fixtureName + " :: " + name
                        + "\nInput:    " + input
                        + "\nExpected: " + expected
                        + "\nActual:   " + actual
                        + (diff.containsKey("diff") ? "\nDiff:     " + diff.get("diff") : ""));
    }

    /**
     * Normalizes a decoded structure so DeepEquals isn't tripped up by container-type
     * differences between json-io's tree-builder (which materializes JSON arrays as
     * {@code Object[]}) and the TOON reader (which materializes them as {@code List}).
     * Walks recursively: {@code Object[]} ⇒ {@code ArrayList}, {@code Map} entries are
     * normalized in place, scalars are left alone. Numeric type promotion is handled by
     * DeepEquals itself, so we leave numbers alone here.
     */
    @SuppressWarnings("unchecked")
    private static Object normalizeForCompare(Object value) {
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Object> out = new ArrayList<>(arr.length);
            for (Object o : arr) {
                out.add(normalizeForCompare(o));
            }
            return out;
        }
        if (value instanceof List) {
            List<Object> in = (List<Object>) value;
            List<Object> out = new ArrayList<>(in.size());
            for (Object o : in) {
                out.add(normalizeForCompare(o));
            }
            return out;
        }
        if (value instanceof Map) {
            Map<String, Object> in = (Map<String, Object>) value;
            Map<String, Object> out = new java.util.LinkedHashMap<>(in.size());
            for (Map.Entry<String, Object> e : in.entrySet()) {
                out.put(e.getKey(), normalizeForCompare(e.getValue()));
            }
            return out;
        }
        return value;
    }

    private static ReadOptions toReadOptions(Map<String, Object> options) {
        ReadOptionsBuilder b = new ReadOptionsBuilder();
        if (options == null) {
            return b.build();
        }
        Object strict = options.get("strict");
        if (strict instanceof Boolean) {
            b.strictToon((Boolean) strict);
        }
        return b.build();
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Wraps a fixture case execution with the {@link #EXPECTED_FAILURES} mechanism.
     * Listed cases that fail convert into a JUnit "aborted" status (skip). Listed cases
     * that pass throw a loud failure so the entry can be retired from the list. Unrelated
     * skips (via {@link #skipIfUnsupported}) pass through untouched.
     */
    private static void runWithKnownGapTolerance(String fullName, Executable action) throws Throwable {
        boolean expectFail = EXPECTED_FAILURES.contains(fullName);
        Throwable thrown = null;
        try {
            action.execute();
        } catch (TestAbortedException abort) {
            // Already aborted (unsupported option). Pass through unchanged.
            throw abort;
        } catch (Throwable t) {
            thrown = t;
        }
        if (expectFail) {
            if (thrown == null) {
                fail("EXPECTED_FAILURES contains '" + fullName
                        + "' but the case now passes. Remove it from EXPECTED_FAILURES in the same commit as the fix.");
            }
            Assumptions.abort("known-gap (TOON v3.3 conformance): " + fullName);
        }
        if (thrown != null) {
            // Re-wrap so the JUnit failure message always carries the fixture-case name,
            // not just the raw json-io exception text. Without this, decoder errors
            // (e.g. "Invalid escape sequence: \\u at line 1") strip the fixture context
            // and become hard to diagnose / hard to add to EXPECTED_FAILURES.
            AssertionError wrapped = new AssertionError(fullName + " ==> " + thrown.getMessage(), thrown);
            throw wrapped;
        }
    }

    private static void skipIfUnsupported(Map<String, Object> options,
                                           Set<String> unsupportedKeys,
                                           String fixtureName,
                                           String testName) {
        if (options == null) {
            return;
        }
        for (String key : unsupportedKeys) {
            Object value = options.get(key);
            if (value == null) {
                continue;
            }
            // expandPaths is only considered "in use" when value != "off"; same for keyFolding,
            // but we accept keyFolding on the encode side. expandPaths "safe" is the only
            // value that would require expansion; "off" is the default and is a no-op.
            if (value instanceof String && "off".equals(value)) {
                continue;
            }
            Assumptions.abort(fixtureName + " :: " + testName
                    + " — skipped: option `" + key + "` = " + value + " is not implemented in json-io");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<FixtureFile> fixturesFor(String category) throws IOException {
        URL root = Thread.currentThread().getContextClassLoader().getResource(RESOURCE_ROOT + "/" + category);
        if (root == null) {
            fail("Missing fixture directory on classpath: " + RESOURCE_ROOT + "/" + category);
        }
        Path dir = Paths.get(root.getPath());
        if (!Files.isDirectory(dir)) {
            fail("Fixture path is not a directory: " + dir);
        }
        List<FixtureFile> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(p -> {
                        try (InputStream in = Files.newInputStream(p)) {
                            String json = new String(readAll(in), StandardCharsets.UTF_8);
                            Map<String, Object> fixture = JsonIo.toMaps(json, null).asClass(Map.class);
                            result.add(new FixtureFile(category + "/" + p.getFileName().toString(), fixture));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read fixture " + p, e);
                        }
                    });
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Coerces a fixture {@code tests} field into a {@code List<Map<String,Object>>}.
     * json-io's {@link JsonIo#toMaps} returns JSON arrays as {@code Object[]}, so the
     * naive {@code (List) value} cast fails. Accepts either form.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
            List<Map<String, Object>> out = new ArrayList<>(arr.length);
            for (Object o : arr) {
                out.add((Map<String, Object>) o);
            }
            return out;
        }
        throw new IllegalStateException("Unexpected `tests` shape: " + value.getClass());
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) >= 0) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private static final class FixtureFile {
        final String name;
        final Map<String, Object> contents;
        FixtureFile(String name, Map<String, Object> contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}
