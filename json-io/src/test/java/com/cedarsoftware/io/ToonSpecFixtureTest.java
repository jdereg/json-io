package com.cedarsoftware.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
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
 * / CI test report. A case is identified by its file and its 0-based position in
 * that file ({@code decode/comments.json#3}), never by its name: the spec rewrites
 * the names whenever its terminology changes.
 *
 * <p>Two sets of fixtures run:
 * <ul>
 *   <li>{@code fixtures/} - the official set, unchanged.</li>
 *   <li>{@code v3-options/} - spec v3.3's fixtures for key folding and path expansion.
 *       Spec v4.0 removed both options; json-io keeps them as opt-in extensions
 *       ({@code toonKeyFolding}, {@code toonExpandPaths}), so their fixtures stay.</li>
 * </ul>
 *
 * <p>The suite is offline - fixtures are loaded from the classpath, never
 * fetched at test time. Refresh the snapshot with
 * {@code scripts/refresh-toon-fixtures.sh <commit-sha>}.
 */
class ToonSpecFixtureTest {

    private static final String RESOURCE_BASE = "toon-spec-fixtures";

    /** The official fixtures, vendored unchanged. A case id under this root has no prefix. */
    private static final String OFFICIAL = "fixtures";

    /** json-io's opt-in key folding and path expansion, from spec v3.3. */
    private static final String V3_OPTIONS = "v3-options";

    private static final String KNOWN_GAPS_RESOURCE = RESOURCE_BASE + "/known-gaps.txt";

    /** Encoder options json-io does not implement; tests using these are skipped. */
    private static final Set<String> UNSUPPORTED_ENCODE_OPTIONS = new HashSet<>(Arrays.asList(
            "flattenDepth"
    ));

    /** Decoder options json-io does not implement; tests using these are skipped. */
    private static final Set<String> UNSUPPORTED_DECODE_OPTIONS = new HashSet<>();

    /**
     * The cases json-io does not pass yet, read from {@code toon-spec-fixtures/known-gaps.txt}.
     * A listed case that fails is reported as skipped, so the build stays green while the
     * gap is visible in the skipped count. A listed case that <b>passes</b> fails the build:
     * the fix that made it pass must remove it from the file in the same commit. A case that
     * is not listed must pass.
     */
    private static final Set<String> KNOWN_GAPS = loadKnownGaps();

    @TestFactory
    Stream<DynamicTest> encodeFixtures() throws Exception {
        return fixturesFor(OFFICIAL, "encode").stream().flatMap(this::generateEncodeCases);
    }

    @TestFactory
    Stream<DynamicTest> decodeFixtures() throws Exception {
        return fixturesFor(OFFICIAL, "decode").stream().flatMap(this::generateDecodeCases);
    }

    @TestFactory
    Stream<DynamicTest> v3OptionEncodeFixtures() throws Exception {
        return fixturesFor(V3_OPTIONS, "encode").stream().flatMap(this::generateEncodeCases);
    }

    @TestFactory
    Stream<DynamicTest> v3OptionDecodeFixtures() throws Exception {
        return fixturesFor(V3_OPTIONS, "decode").stream().flatMap(this::generateDecodeCases);
    }

    /** A gap that names no case is stale: a fixture refresh moved or removed the case it meant. */
    @Test
    void everyKnownGapNamesACase() throws Exception {
        Set<String> ids = new HashSet<>();
        for (String root : new String[]{OFFICIAL, V3_OPTIONS}) {
            for (String category : new String[]{"encode", "decode"}) {
                for (FixtureFile f : fixturesFor(root, category)) {
                    int count = asMapList(f.contents.get("tests")).size();
                    for (int i = 0; i < count; i++) {
                        ids.add(f.name + "#" + i);
                    }
                }
            }
        }
        Set<String> stale = new LinkedHashSet<>(KNOWN_GAPS);
        stale.removeAll(ids);
        assertTrue(stale.isEmpty(), "known-gaps.txt names cases that do not exist: " + stale);
    }

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    private Stream<DynamicTest> generateEncodeCases(FixtureFile f) {
        List<Map<String, Object>> tests = asMapList(f.contents.get("tests"));
        List<DynamicTest> cases = new ArrayList<>(tests.size());
        for (int i = 0; i < tests.size(); i++) {
            Map<String, Object> tc = tests.get(i);
            String id = f.name + "#" + i;
            String fullName = id + " :: " + tc.get("name");
            cases.add(DynamicTest.dynamicTest(fullName,
                    () -> runWithKnownGapTolerance(id, fullName, () -> runEncodeCase(f.name, tc))));
        }
        return cases.stream();
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
        Object indent = indentOption(options);
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
        List<DynamicTest> cases = new ArrayList<>(tests.size());
        for (int i = 0; i < tests.size(); i++) {
            Map<String, Object> tc = tests.get(i);
            String id = f.name + "#" + i;
            String fullName = id + " :: " + tc.get("name");
            cases.add(DynamicTest.dynamicTest(fullName,
                    () -> runWithKnownGapTolerance(id, fullName, () -> runDecodeCase(f.name, tc))));
        }
        return cases.stream();
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
        // §13 decoder defaults: strict = true (per spec). json-io's library default is
        // false (permissive) for backwards compatibility, so the conformance test mapping
        // explicitly enables strict unless the fixture says otherwise.
        ReadOptionsBuilder b = new ReadOptionsBuilder().strictToon(true);
        if (options == null) {
            return b.build();
        }
        Object strict = options.get("strict");
        if (strict instanceof Boolean) {
            b.strictToon((Boolean) strict);
        }
        Object expandPaths = options.get("expandPaths");
        if ("safe".equals(expandPaths)) {
            b.toonExpandPaths(true);
        } else if (expandPaths != null && !"off".equals(expandPaths)) {
            // Future expandPaths modes are deferred; let the unsupported-option skip
            // path catch them once any fixture starts using them.
            Assumptions.abort("expandPaths='" + expandPaths + "' is not implemented in json-io");
        }
        Object indent = indentOption(options);
        if (indent instanceof Number) {
            int n = ((Number) indent).intValue();
            if (n >= 1) {
                b.toonIndentSize(n);
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /** Spec v4.0 renamed the fixture option {@code indent} to {@code indentSize}; the v3.3 files keep the old name. */
    private static Object indentOption(Map<String, Object> options) {
        Object indentSize = options.get("indentSize");
        return indentSize != null ? indentSize : options.get("indent");
    }

    /**
     * Runs one case under the {@link #KNOWN_GAPS} rule: a listed case that fails is skipped,
     * a listed case that passes fails the build, and an unlisted case must pass. Cases skipped
     * for an unsupported option (via {@link #skipIfUnsupported}) pass through untouched.
     */
    private static void runWithKnownGapTolerance(String id, String fullName, Executable action) throws Throwable {
        boolean knownGap = KNOWN_GAPS.contains(id);
        Throwable thrown = null;
        try {
            action.execute();
        } catch (TestAbortedException abort) {
            // Already aborted (unsupported option). Pass through unchanged.
            throw abort;
        } catch (Throwable t) {
            thrown = t;
        }
        if (knownGap) {
            if (thrown == null) {
                fail("known-gaps.txt lists '" + id + "' but the case now passes."
                        + " Remove it from known-gaps.txt in the same commit as the fix.");
            }
            Assumptions.abort("known gap: " + fullName);
        }
        if (thrown != null) {
            // Re-wrap so the JUnit failure message always carries the fixture-case name,
            // not just the raw json-io exception text. Without this, decoder errors
            // (e.g. "Invalid escape sequence: \\u at line 1") strip the fixture context
            // and become hard to diagnose.
            throw new AssertionError(fullName + " ==> " + thrown.getMessage(), thrown);
        }
    }

    /**
     * Reads {@code known-gaps.txt}: one case id per line ({@code decode/comments.json#3}),
     * optionally followed by spaces and a description. Blank lines and lines starting
     * with {@code #} are ignored.
     */
    private static Set<String> loadKnownGaps() {
        Set<String> gaps = new LinkedHashSet<>();
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(KNOWN_GAPS_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Missing " + KNOWN_GAPS_RESOURCE + " on the classpath");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                    continue;
                }
                int space = trimmed.indexOf(' ');
                String id = space < 0 ? trimmed : trimmed.substring(0, space);
                if (!gaps.add(id)) {
                    throw new IllegalStateException(KNOWN_GAPS_RESOURCE + " lists " + id + " twice");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + KNOWN_GAPS_RESOURCE, e);
        }
        return Collections.unmodifiableSet(gaps);
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
    private static List<FixtureFile> fixturesFor(String fixtureRoot, String category) throws IOException {
        String resourceDir = RESOURCE_BASE + "/" + fixtureRoot + "/" + category;
        URL root = Thread.currentThread().getContextClassLoader().getResource(resourceDir);
        if (root == null) {
            fail("Missing fixture directory on classpath: " + resourceDir);
        }
        // An official case is "decode/comments.json#3"; any other root shows in the id.
        String prefix = OFFICIAL.equals(fixtureRoot) ? category : fixtureRoot + "/" + category;
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
                            result.add(new FixtureFile(prefix + "/" + p.getFileName().toString(), fixture));
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
