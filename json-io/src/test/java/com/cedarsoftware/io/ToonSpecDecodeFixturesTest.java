package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs official TOON decode fixtures from toon-format/spec against JsonIo.fromToon().
 * <p>
 * This suite is opt-in to avoid hard network dependency in default test runs.
 * Set environment variable RUN_TOON_SPEC_FIXTURES=true to execute.
 */
class ToonSpecDecodeFixturesTest {
    private static final String BASE =
            "https://raw.githubusercontent.com/toon-format/spec/main/tests/fixtures/decode/";

    private static final List<String> FIXTURES = Arrays.asList(
            "primitives.json",
            "numbers.json",
            "objects.json",
            "arrays-primitive.json",
            "arrays-tabular.json",
            "arrays-nested.json",
            "delimiters.json",
            "whitespace.json",
            "root-form.json",
            "validation-errors.json",
            "indentation-errors.json",
            "blank-lines.json",
            "path-expansion.json"
    );

    @Test
    @SuppressWarnings("unchecked")
    void testOfficialToonDecodeFixtures() throws Exception {
        boolean enabled = "true".equalsIgnoreCase(System.getenv("RUN_TOON_SPEC_FIXTURES"));
        Assumptions.assumeTrue(enabled, "Set RUN_TOON_SPEC_FIXTURES=true to run official TOON fixture suite.");

        for (String fixtureName : FIXTURES) {
            String json = fetchText(BASE + fixtureName);
            Map<String, Object> fixture = JsonIo.toMaps(json, null).asClass(Map.class);
            List<Map<String, Object>> tests = (List<Map<String, Object>>) fixture.get("tests");
            if (tests == null) {
                continue;
            }

            for (Map<String, Object> tc : tests) {
                String name = String.valueOf(tc.get("name"));
                String input = (String) tc.get("input");
                boolean shouldError = Boolean.TRUE.equals(tc.get("shouldError"));
                Map<String, Object> options = (Map<String, Object>) tc.get("options");
                ReadOptions readOptions = toReadOptions(options);

                if (shouldError) {
                    assertThrows(JsonIoException.class,
                            () -> JsonIo.fromToon(input, readOptions).asClass(Object.class),
                            fixtureName + " :: " + name);
                    continue;
                }

                Object expected = tc.get("expected");
                Object actual = JsonIo.fromToon(input, readOptions).asClass(Object.class);
                assertTrue(DeepEquals.deepEquals(expected, actual),
                        fixtureName + " :: " + name + "\nExpected: " + expected + "\nActual: " + actual);
            }
        }
    }

    private static ReadOptions toReadOptions(Map<String, Object> options) {
        ReadOptionsBuilder builder = new ReadOptionsBuilder();
        if (options != null) {
            Object strict = options.get("strict");
            if (strict instanceof Boolean) {
                builder.strictToon((Boolean) strict);
            }
        }
        return builder.build();
    }

    private static String fetchText(String url) throws Exception {
        URL u = new URL(url);
        try (InputStream in = u.openStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
