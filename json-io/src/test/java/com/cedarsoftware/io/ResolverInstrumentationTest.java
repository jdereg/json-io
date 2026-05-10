package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity tests for {@link ResolverInstrumentation}.
 *
 * <p>The harness is gated by {@code -Djsonio.instrumentResolver=true} read at
 * {@link ResolverInstrumentation} class-init time. Most tests in this file
 * only verify the static API surface (snapshot, reset, dump signatures) so
 * they don't depend on JVM-level configuration.
 *
 * <p>To collect production-style data on a workload, run:
 * <pre>{@code
 *   mvn -pl json-io exec:java \
 *       -Dexec.classpathScope=test \
 *       -Dexec.mainClass=com.cedarsoftware.io.JsonPerformanceTest \
 *       -Djsonio.instrumentResolver=true
 * }</pre>
 * then call {@link ResolverInstrumentation#dump()} from main() (or expose a
 * shutdown hook) to print the per-class breakdown.
 */
class ResolverInstrumentationTest {

    static class Person {
        public String name;
        public int age;
        public Date birthday;
    }

    static class Wrapper {
        public Person person;
        public List<Person> friends;
    }

    @Test
    void apiSurface_dumpAndSnapshotWork() {
        // The static methods exist and work whether instrumentation is enabled
        // or not. When disabled, snapshot() returns an empty (or current
        // process-state) map and dump() prints a "no data" message.
        ResolverInstrumentation.reset();

        Map<Class<?>, ResolverInstrumentation.Stats> snap = ResolverInstrumentation.snapshot();
        assertNotNull(snap);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf)) {
            ResolverInstrumentation.dump(ps);
        }
        // After reset, dump should report "no data" — independent of ENABLED.
        assertTrue(buf.toString().contains("no data recorded"));
    }

    @Test
    void enabledFlagReadsSystemProperty() {
        // The flag is final, set once at class init. Just verify the constant is
        // accessible. Tests that flip it require a forked JVM.
        boolean enabled = ResolverInstrumentation.ENABLED;
        assertEquals(Boolean.getBoolean("jsonio.instrumentResolver"), enabled);
    }

    @Test
    void resetClearsCounters() {
        ResolverInstrumentation.reset();
        assertEquals(0, ResolverInstrumentation.totalVisits());
    }

    /**
     * If the JVM was launched with {@code -Djsonio.instrumentResolver=true},
     * this test exercises the recording end-to-end. Otherwise it falls
     * through (counters stay zero); that's expected.
     */
    @Test
    void endToEndRecordingWhenEnabled() {
        if (!ResolverInstrumentation.ENABLED) {
            // Smoke run only: parse without instrumentation. Result must
            // still be correct; counter stays at zero.
            ResolverInstrumentation.reset();
            String json = "{\"@type\":\"" + Wrapper.class.getName() + "\","
                    + "\"person\":{\"@type\":\"" + Person.class.getName() + "\","
                    + "\"name\":\"Alice\",\"age\":30,"
                    + "\"birthday\":\"1990-01-15\"}}";
            Wrapper w = JsonIo.toJava(json, null).asClass(Wrapper.class);
            assertNotNull(w);
            assertEquals("Alice", w.person.name);
            assertEquals(0, ResolverInstrumentation.totalVisits());
            return;
        }

        // Instrumentation IS enabled — verify counters increment.
        ResolverInstrumentation.reset();
        String json = "{\"@type\":\"" + Wrapper.class.getName() + "\","
                + "\"person\":{\"@type\":\"" + Person.class.getName() + "\","
                + "\"name\":\"Alice\",\"age\":30,"
                + "\"birthday\":\"1990-01-15\"}}";
        Wrapper w = JsonIo.toJava(json, null).asClass(Wrapper.class);
        assertNotNull(w);

        // At minimum the root Wrapper and the nested Person should both
        // have been visited.
        Map<Class<?>, ResolverInstrumentation.Stats> snap = ResolverInstrumentation.snapshot();
        assertFalse(snap.isEmpty());
        assertTrue(ResolverInstrumentation.totalVisits() >= 2);

        ResolverInstrumentation.Stats wStats = snap.get(Wrapper.class);
        assertNotNull(wStats);
        assertTrue(wStats.getTotal() >= 1);
        // Wrapper had @type → @type flag count >= 1.
        long[] flags = wStats.getFlags();
        assertTrue(flags[ResolverInstrumentation.FLAG_TYPE] >= 1);
        // Wrapper was the root → SRC_ROOT incremented.
        long[] sources = wStats.getSources();
        assertEquals(1, sources[ResolverInstrumentation.SRC_ROOT]);

        ResolverInstrumentation.Stats pStats = snap.get(Person.class);
        assertNotNull(pStats);
        // Person was nested → SRC_NON_ROOT incremented.
        long[] pSources = pStats.getSources();
        assertEquals(1, pSources[ResolverInstrumentation.SRC_NON_ROOT]);
    }

    @Test
    void manualClassifyExamples_smoke() {
        // Verify the dump output format includes the dimension headers when
        // there's recorded data. We can't inject data unless ENABLED is on,
        // so this is a smoke check on the static API only.
        ResolverInstrumentation.reset();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf)) {
            ResolverInstrumentation.dump(ps);
        }
        String out = buf.toString();
        // After reset (no data), we get the "no data" path.
        assertTrue(out.contains("no data recorded"));
    }

    @SuppressWarnings("unused")
    private static UUID uuidUnusedReference() {
        // Force the UUID import to stick; useful for future test extensions.
        return UUID.randomUUID();
    }
}
