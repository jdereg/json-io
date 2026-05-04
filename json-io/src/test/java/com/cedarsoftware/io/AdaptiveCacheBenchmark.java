package com.cedarsoftware.io;

import java.util.logging.Logger;

/**
 * Targeted micro-benchmark for tuning ObjectResolver.SHAPE_CACHE_THRESHOLD.
 * <p>
 * Sweeps (array-size, threshold) pairs and prints a table of per-call timings
 * so we can pick the threshold value that maximizes the win on large
 * homogeneous arrays without regressing small ones — the failure mode of
 * the always-on R-6 variant.
 * <p>
 * Run as a Java app, not via surefire (no @Test methods):
 * <pre>{@code
 *   java -cp <test-classpath> com.cedarsoftware.io.AdaptiveCacheBenchmark
 * }</pre>
 */
public final class AdaptiveCacheBenchmark {

    private static final Logger LOG = Logger.getLogger(AdaptiveCacheBenchmark.class.getName());

    /** Test POJO modeled on USERS.User: ~8 simple fields, no nested types. */
    public static final class SimplePojo {
        public int    id;
        public long   timestamp;
        public double score;
        public String name;
        public String email;
        public boolean active;
        public int    age;
        public String city;
    }

    /** Sizes correspond roughly to USERS at 1KB / 5KB / 10KB / 25KB / 100KB / 1MB. */
    private static final int[] SIZES = {5, 25, 50, 125, 500, 5000};

    /** Threshold sweep: 0 = always-on (R-6 variant), large = effectively off (baseline). */
    private static final int[] THRESHOLDS = {1, 2, 3, 5, 8, 13, 21, 1_000_000};

    private static final int WARMUP_MS    = 2000;   // per cell warm-up
    private static final int MEASURE_MS   = 3000;   // per cell measure
    private static final int CHECK_EVERY  = 32;     // recheck wall-time after this many iters

    public static void main(String[] args) {
        // Pre-build JSON payloads for each size.
        final String[] jsonBySize = new String[SIZES.length];
        for (int s = 0; s < SIZES.length; s++) {
            jsonBySize[s] = JsonIo.toJson(buildList(SIZES[s]),
                    new WriteOptionsBuilder().standardJson().build());
        }

        // Long pre-warm with the baseline (threshold=∞) on the largest payload to settle JIT.
        ObjectResolver.SHAPE_CACHE_THRESHOLD = THRESHOLDS[THRESHOLDS.length - 1];
        prewarm(jsonBySize[SIZES.length - 1]);

        // Header.
        StringBuilder header = new StringBuilder();
        header.append(String.format("%-10s", "n="));
        for (int t : THRESHOLDS) {
            header.append(String.format(" %12s", t == 1_000_000 ? "off" : "T=" + t));
        }
        header.append("    (μs/call)");
        System.out.println(header);

        // Sweep.
        // For each size, get the baseline (threshold=off) first, then ratio against it.
        final double[][] usPerCallTable = new double[SIZES.length][THRESHOLDS.length];
        for (int s = 0; s < SIZES.length; s++) {
            final int n = SIZES[s];
            final String json = jsonBySize[s];
            for (int t = 0; t < THRESHOLDS.length; t++) {
                ObjectResolver.SHAPE_CACHE_THRESHOLD = THRESHOLDS[t];
                runFor(json, WARMUP_MS);
                // Time the measurement window precisely.
                final long start = System.nanoTime();
                final long deadline = start + MEASURE_MS * 1_000_000L;
                long done = 0;
                while (true) {
                    for (int k = 0; k < CHECK_EVERY; k++) {
                        sink(JsonIo.toJava(json, null).asClass(SimplePojo[].class));
                    }
                    done += CHECK_EVERY;
                    if (System.nanoTime() >= deadline) {
                        break;
                    }
                }
                final long usedNs = System.nanoTime() - start;
                usPerCallTable[s][t] = (usedNs / 1000.0) / done;
            }

            // Print the row.
            StringBuilder row = new StringBuilder();
            row.append(String.format("%-10d", n));
            final double baseline = usPerCallTable[s][THRESHOLDS.length - 1]; // threshold=off
            for (int t = 0; t < THRESHOLDS.length; t++) {
                final double us = usPerCallTable[s][t];
                final double pct = (baseline - us) / baseline * 100.0;
                if (t == THRESHOLDS.length - 1) {
                    row.append(String.format(" %12.2f", us));
                } else {
                    row.append(String.format(" %+11.1f%%", pct));
                }
            }
            System.out.println(row);
            System.out.flush();
        }

        System.out.println();
        System.out.println("Cells are % faster than threshold=off baseline (rightmost μs/call).");
        System.out.println("Positive % = win, negative % = regression.");
    }

    /** Run for at least the given milliseconds; return iterations executed. */
    private static long runFor(final String json, final long ms) {
        final long start = System.nanoTime();
        final long deadline = start + ms * 1_000_000L;
        long done = 0;
        while (true) {
            for (int k = 0; k < CHECK_EVERY; k++) {
                sink(JsonIo.toJava(json, null).asClass(SimplePojo[].class));
            }
            done += CHECK_EVERY;
            if (System.nanoTime() >= deadline) {
                return done;
            }
        }
    }

    private static void prewarm(final String json) {
        runFor(json, 4000);
    }

    /** Volatile sink to defeat dead-code elimination. */
    @SuppressWarnings("unused")
    private static volatile Object SINK;
    private static void sink(Object o) {
        SINK = o;
    }

    private static SimplePojo[] buildList(final int n) {
        SimplePojo[] out = new SimplePojo[n];
        for (int i = 0; i < n; i++) {
            SimplePojo p = new SimplePojo();
            p.id = i;
            p.timestamp = 1_700_000_000_000L + i;
            p.score = i * 0.5;
            p.name = "Name-" + i;
            p.email = "user" + i + "@example.com";
            p.active = (i & 1) == 0;
            p.age = 18 + (i % 60);
            p.city = "City-" + (i % 100);
            out[i] = p;
        }
        return out;
    }

    private AdaptiveCacheBenchmark() { }
}
