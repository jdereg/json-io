package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.cedarsoftware.util.Converter;

/**
 * Diagnostic instrumentation that records, per Class, what objects pass
 * through {@link Resolver#traverseJsonObject(JsonObject)} — the deferred
 * "make it through to the Resolver" path. Activated at JVM start via the
 * system property {@code -Djsonio.instrumentResolver=true}; otherwise the
 * recording calls are a single volatile-boolean read away from being JIT-
 * eliminated.
 *
 * <p>Three dimensions tracked per class:
 * <ol>
 *   <li><b>Visit count + metadata pattern</b> — for each visit, classify
 *       what JSON-level metadata flags were present on the {@link JsonObject}
 *       (PLAIN / HAS_TYPE / HAS_ID / HAS_REF / HAS_ITEMS_OR_KEYS / HAS_ENUM
 *       / COMPLEX_METADATA when multiple flags). Surfaces which
 *       metadata patterns dominate a workload — useful input for deciding
 *       which optimization tier (eager-construction, @id-aware eager,
 *       Tier 3 per-document fallback, etc.) would unlock the most wins.</li>
 *   <li><b>Field-kind distribution</b> — for each visit, walk the populated
 *       fields and classify each by what the parser produces vs what the
 *       target field type wants:
 *       {@link #FK_DIRECT} (parser-type matches: String, Boolean),
 *       {@link #FK_PRIMITIVE_COERCION} (primitive numeric),
 *       {@link #FK_CONVERTER_NEEDED} (Converter-supported scalar:
 *       Date, UUID, BigInteger, ...), or
 *       {@link #FK_COMPLEX} (POJO, Collection, array, generic Object).
 *       Reveals which classes are leaf-only vs deeply-nested — informs the
 *       practical reach of any eager-construction optimization.</li>
 *   <li><b>Source</b> — simplified to {@link #SRC_ROOT} (this object was
 *       the top-level value passed into {@link Resolver#toJavaObjects})
 *       vs {@link #SRC_NON_ROOT} (anything else). Tells us whether a hot
 *       class shows up because callers pass it as the call's target type
 *       or because it appears nested inside other documents.</li>
 * </ol>
 *
 * <p>Per-class stats are stored in a {@link ConcurrentHashMap} keyed by the
 * class object. {@link #dump()} prints a sorted table to {@code System.out};
 * {@link #snapshot()} returns the same data programmatically.
 */
public final class ResolverInstrumentation {

    private ResolverInstrumentation() {
        // utility — no instances
    }

    /**
     * Compile-time-foldable enable flag. Read once at class initialization
     * from the {@code jsonio.instrumentResolver} system property. When false,
     * every recording call short-circuits at the first instruction and the
     * entire instrumentation tree is dead code from the JIT's perspective.
     */
    public static final boolean ENABLED = Boolean.getBoolean("jsonio.instrumentResolver");

    // --- Metadata flag indices ----------------------------------------------
    // Each flag tracked independently — a visit can match multiple flags
    // (e.g., @type AND @id together is common). The "plain" count
    // (computed at dump time) is total - withAnyFlag.
    public static final int FLAG_TYPE         = 0;
    public static final int FLAG_ID           = 1;
    public static final int FLAG_REF          = 2;
    public static final int FLAG_ITEMS_KEYS   = 3;
    public static final int FLAG_ENUM         = 4;
    private static final int FLAG_BUCKETS     = 5;
    private static final String[] FLAG_NAMES = {
            "@type", "@id", "@ref", "items/keys", "@enum"
    };

    // --- Field-kind buckets --------------------------------------------------
    public static final int FK_DIRECT             = 0;  // String, Boolean — parser-type matches
    public static final int FK_PRIMITIVE_COERCION = 1;  // numeric primitive / wrapper
    public static final int FK_CONVERTER_NEEDED   = 2;  // Converter scalar: Date/UUID/BigInt/etc.
    public static final int FK_COMPLEX            = 3;  // POJO/Collection/Map/Object — needs Resolver
    private static final int FK_BUCKETS           = 4;
    private static final String[] FK_NAMES = {
            "direct", "primitive", "converter", "complex"
    };

    // --- Source --------------------------------------------------------------
    public static final int SRC_ROOT     = 0;
    public static final int SRC_NON_ROOT = 1;
    private static final int SRC_BUCKETS = 2;
    private static final String[] SRC_NAMES = { "root", "nested" };

    // --- Stats ---------------------------------------------------------------

    /**
     * Per-class counts. {@link LongAdder} for total because it's the only
     * counter incremented on every visit (high contention under multi-threaded
     * runs); plain {@code long[]} for the bucketed dimensions because we
     * accept a tiny amount of write tearing in exchange for cheap reads
     * (instrumentation is diagnostic, not transactional).
     */
    public static final class Stats {
        private final LongAdder total = new LongAdder();
        private long visitsWithAnyFlag;        // visits where any metadata flag was set
        private final long[] flags = new long[FLAG_BUCKETS];
        private final long[] fieldKinds = new long[FK_BUCKETS];
        private final long[] sources = new long[SRC_BUCKETS];

        public long getTotal() { return total.sum(); }
        public long getPlain() { return getTotal() - visitsWithAnyFlag; }
        public long[] getFlags() { return flags.clone(); }
        public long[] getFieldKinds() { return fieldKinds.clone(); }
        public long[] getSources() { return sources.clone(); }
    }

    private static final Map<Class<?>, Stats> STATS_BY_CLASS = new ConcurrentHashMap<>();

    /** Reset all counters. Tests call this between scenarios. */
    public static void reset() {
        STATS_BY_CLASS.clear();
    }

    /** Programmatic snapshot (immutable view of the current per-class stats). */
    public static Map<Class<?>, Stats> snapshot() {
        return java.util.Collections.unmodifiableMap(STATS_BY_CLASS);
    }

    /** Total objects recorded across all classes. */
    public static long totalVisits() {
        long sum = 0;
        for (Stats s : STATS_BY_CLASS.values()) {
            sum += s.getTotal();
        }
        return sum;
    }

    /**
     * Record one visit. Called by {@link Resolver#traverseJsonObject} for each
     * popped non-finished object. {@code source} is {@link #SRC_ROOT} when
     * the object is the top-level value passed into the Resolver (caller's
     * target), {@link #SRC_NON_ROOT} otherwise.
     */
    static void recordVisit(JsonObject jObj, ReadOptions readOptions, int source) {
        if (!ENABLED) {
            return;
        }
        Class<?> javaClass = jObj.getRawType();
        if (javaClass == null) {
            javaClass = Object.class;
        }
        Stats s = STATS_BY_CLASS.computeIfAbsent(javaClass, k -> new Stats());
        s.total.increment();
        recordMetadataFlags(s, jObj);
        s.sources[source]++;
        recordFieldKinds(s, jObj, javaClass, readOptions);
    }

    private static void recordMetadataFlags(Stats s, JsonObject jObj) {
        boolean anyFlag = false;
        // jObj's set type came from @type (getTypeString returns the literal
        // @type value; applyPendingMetadata only sets type-string when @type
        // was emitted).
        if (jObj.getTypeString() != null) { s.flags[FLAG_TYPE]++; anyFlag = true; }
        if (jObj.getId() != 0)            { s.flags[FLAG_ID]++; anyFlag = true; }
        if (jObj.isReference())           { s.flags[FLAG_REF]++; anyFlag = true; }
        if (jObj instanceof JsonObjectArray || jObj instanceof JsonObjectMap) {
            s.flags[FLAG_ITEMS_KEYS]++;
            anyFlag = true;
        }
        // @enum is hard to detect post-parse — it manifests as an EnumSet
        // target with @items. Distinguished here by checking if the raw type
        // is an EnumSet (cheap structural test).
        Class<?> rawType = jObj.getRawType();
        if (rawType != null && java.util.EnumSet.class.isAssignableFrom(rawType)) {
            s.flags[FLAG_ENUM]++;
            anyFlag = true;
        }
        if (anyFlag) {
            s.visitsWithAnyFlag++;
        }
    }

    private static void recordFieldKinds(Stats s, JsonObject jObj,
                                         Class<?> javaClass, ReadOptions readOptions) {
        if (javaClass == Object.class || javaClass.isInterface() || javaClass.isPrimitive()) {
            return;
        }
        ReadOptionsBuilder.InjectorPlan plan = ReadOptionsBuilder.getInjectorPlan(readOptions, javaClass);
        if (plan == null || plan.isEmpty()) {
            return;
        }
        final int n = jObj.size();
        for (int i = 0; i < n; i++) {
            Object key = jObj.fastKeyAt(i);
            // InjectorPlan.get(...) returns the Injector or null; use it as a
            // proxy for "is this field name known on the target class".
            // We compute kind directly from the field's declared raw type
            // because we don't pull in any of the failed Tier 2 c1 scaffolding.
            com.cedarsoftware.io.reflect.Injector injector = plan.get(key);
            Class<?> fieldRawType;
            if (injector == null) {
                continue;
            }
            try {
                fieldRawType = com.cedarsoftware.util.TypeUtilities.getRawClass(injector.getGenericType());
            } catch (Exception e) {
                continue;
            }
            s.fieldKinds[classifyFieldKind(fieldRawType)]++;
        }
    }

    private static int classifyFieldKind(Class<?> rawType) {
        if (rawType == null) return FK_COMPLEX;
        if (rawType == String.class || rawType == Boolean.class || rawType == boolean.class) {
            return FK_DIRECT;
        }
        if (rawType.isPrimitive()) return FK_PRIMITIVE_COERCION;
        if (rawType == Integer.class || rawType == Long.class
                || rawType == Short.class || rawType == Byte.class
                || rawType == Float.class || rawType == Double.class
                || rawType == Character.class) {
            return FK_PRIMITIVE_COERCION;
        }
        if (rawType == Class.class || rawType.isEnum() || Enum.class.isAssignableFrom(rawType)) {
            return FK_CONVERTER_NEEDED;
        }
        if (Converter.isSimpleTypeConversionSupported(String.class, rawType)
                || Converter.isSimpleTypeConversionSupported(Long.class, rawType)
                || Converter.isSimpleTypeConversionSupported(Double.class, rawType)) {
            return FK_CONVERTER_NEEDED;
        }
        return FK_COMPLEX;
    }

    // --- Output --------------------------------------------------------------

    /**
     * Dump the current snapshot to {@link System#out}, sorted by descending
     * total-visit count. Each row is a single Class with all three dimensions.
     */
    public static void dump() {
        dump(System.out);
    }

    /** Same as {@link #dump()} but to a custom stream. */
    public static void dump(java.io.PrintStream out) {
        if (STATS_BY_CLASS.isEmpty()) {
            out.println("=== ResolverInstrumentation: no data recorded ===");
            return;
        }
        List<Map.Entry<Class<?>, Stats>> rows = new ArrayList<>(STATS_BY_CLASS.entrySet());
        rows.sort(Comparator.comparingLong((Map.Entry<Class<?>, Stats> e) -> e.getValue().getTotal()).reversed());

        long grandTotal = totalVisits();
        out.println("=== ResolverInstrumentation: " + rows.size() + " classes, " + grandTotal + " total visits ===");
        out.printf("%-58s %10s %10s | ", "class", "total", "plain");
        for (String n : FLAG_NAMES) out.printf("%10s ", n);
        out.print("|");
        for (String n : FK_NAMES) out.printf("%10s ", "f-" + n);
        out.print("|");
        for (String n : SRC_NAMES) out.printf("%9s ", n);
        out.println();

        for (Map.Entry<Class<?>, Stats> e : rows) {
            Stats s = e.getValue();
            out.printf("%-58s %10d %10d | ", abbreviate(e.getKey().getName(), 58), s.getTotal(), s.getPlain());
            long[] flags = s.getFlags();
            for (int i = 0; i < FLAG_BUCKETS; i++) out.printf("%10d ", flags[i]);
            out.print("|");
            long[] fk = s.getFieldKinds();
            for (int i = 0; i < FK_BUCKETS; i++) out.printf("%10d ", fk[i]);
            out.print("|");
            long[] src = s.getSources();
            for (int i = 0; i < SRC_BUCKETS; i++) out.printf("%9d ", src[i]);
            out.println();
        }
    }

    private static String abbreviate(String s, int maxLen) {
        return s.length() <= maxLen ? s : "…" + s.substring(s.length() - maxLen + 1);
    }

    // ---- Compile-time link smoke-tests (unused at runtime; keep API stable) ----
    @SuppressWarnings("unused")
    private static void linkSmokeReferences() {
        // Force javac to keep imports if buckets are referenced indirectly.
        Object[] ignore = { BigDecimal.class, BigInteger.class, Arrays.class, Collection.class };
    }
}
