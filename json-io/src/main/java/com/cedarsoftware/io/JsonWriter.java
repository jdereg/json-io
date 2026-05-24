package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Deque;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.RandomAccess;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AnnotationResolver;
import com.cedarsoftware.io.WriteOptionsBuilder.WriteFieldPlan;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.ClassValueMap;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.internal.CharBufScratch;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.FastWriter;
import com.cedarsoftware.util.IOUtilities;
import com.cedarsoftware.util.IdentitySet;
import com.cedarsoftware.util.TypeUtilities;

import static com.cedarsoftware.io.JsonValue.ENUM;
import static com.cedarsoftware.io.JsonValue.ITEMS;
import static com.cedarsoftware.io.JsonValue.TYPE;

/**
 * Output a Java object graph in JSON format.  This code handles cyclic
 * references and can serialize any Object graph without requiring a class
 * to be 'Serializable' or have any specific methods on it.
 * <br><ul><li>
 * Call the static method: {@code JsonWriter.objectToJson(employee)}.  This will
 * convert the passed in 'employee' instance into a JSON String.</li>
 * <li>Using streams:
 * <pre>     JsonWriter writer = new JsonWriter(stream);
 *     writer.write(employee);
 *     writer.close();</pre>
 * This will write the 'employee' object to the passed in OutputStream.
 * </li></ul>
 * <p>That's it.  This can be used as a debugging tool.  Output an object
 * graph using the above code.  Use the JsonWriter PRETTY_PRINT option to
 * format the JSON to be human-readable.
 * <br>
 * <p>This will output any object graph deeply (or null).  Object references are
 * properly handled.  For example, if you had {@code A->B, B->C, and C->A}, then
 * A will be serialized with a B object in it, B will be serialized with a C
 * object in it, and then C will be serialized with a reference to A (ref), not a
 * redefinition of A.</p>
 * <br>
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class JsonWriter implements WriterContext, Closeable, Flushable {
    private static final Logger LOG = Logger.getLogger(JsonWriter.class.getName());

    // Items / keys meta-key prefixes — precomputed key+colon strings used by the
    // remaining legacy emission paths for the @items / @keys fields. Six variants
    // cover the matrix of: short-vs-long key form × @-vs-$ prefix × quoted-vs-json5-
    // unquoted-identifier. The @id / @type / @ref counterparts were removed when
    // writeId / writeType / writeOptionalReference / writePrimitive's Long-wrap
    // branch all migrated to drive emission through CharStreamGenerator's Jackson-
    // style API; gen makes the key-quoting decision itself from the bare key name.

    // Standard form: @ prefix, quoted keys
    private static final String ITEMS_SHORT = "\"@e\":";
    private static final String ITEMS_LONG = "\"@items\":";
    private static final String KEYS_SHORT = "\"@k\":";
    private static final String KEYS_LONG = "\"@keys\":";

    // JSON5 form: $ prefix, unquoted-identifier keys (valid because $ identifier-start is legal)
    private static final String ITEMS_JSON5 = "$items:";
    private static final String KEYS_JSON5 = "$keys:";
    private static final String ITEMS_JSON5_SHORT = "$e:";
    private static final String KEYS_JSON5_SHORT = "$k:";

    // $ prefix with quoted keys (for standard JSON mode with $ prefix override)
    private static final String ITEMS_DOLLAR_QUOTED = "\"$items\":";
    private static final String KEYS_DOLLAR_QUOTED = "\"$keys\":";
    private static final String ITEMS_DOLLAR_SHORT_QUOTED = "\"$e\":";
    private static final String KEYS_DOLLAR_SHORT_QUOTED = "\"$k\":";

    // One-time deprecation warning for stringify-able map keys written as @keys/@items
    private static final AtomicBoolean stringifyMapKeysWarned = new AtomicBoolean(false);

    // Cached int→String for common small integers, avoiding Integer.toString() allocation.
    // Covers -128 to 16384 (same range as ToonWriter's SMALL_LONG_STRINGS).
    private static final int SMALL_INT_LOW = -128;
    private static final int SMALL_INT_HIGH = 16384;
    private static final String[] SMALL_INT_STRINGS = buildSmallIntCache();

    private static String[] buildSmallIntCache() {
        int size = SMALL_INT_HIGH - SMALL_INT_LOW + 1;
        String[] cache = new String[size];
        for (int i = 0; i < size; i++) {
            cache[i] = Integer.toString(i + SMALL_INT_LOW);
        }
        return cache;
    }

    // Natural default collection/map types: maps declared interface to the concrete type that CollectionFactory
    // and MapFactory create when reading. Used by compact format to omit @type wrapper when runtime type matches.
    private static final Map<Class<?>, Class<?>> NATURAL_DEFAULTS = new IdentityHashMap<>();
    static {
        NATURAL_DEFAULTS.put(List.class, ArrayList.class);
        NATURAL_DEFAULTS.put(Collection.class, ArrayList.class);
        NATURAL_DEFAULTS.put(Set.class, LinkedHashSet.class);
        NATURAL_DEFAULTS.put(SortedSet.class, TreeSet.class);
        NATURAL_DEFAULTS.put(NavigableSet.class, TreeSet.class);
        NATURAL_DEFAULTS.put(Queue.class, LinkedList.class);
        NATURAL_DEFAULTS.put(Deque.class, ArrayDeque.class);
        NATURAL_DEFAULTS.put(Map.class, LinkedHashMap.class);
        NATURAL_DEFAULTS.put(SortedMap.class, TreeMap.class);
        NATURAL_DEFAULTS.put(NavigableMap.class, TreeMap.class);
    }

    // Types that have lossless String round-trips via PrimitiveTypeWriter (write) and Converter (read).
    // When compact format is enabled and the field's declared type is in this set, @type can be omitted
    // because the reader's Injector will use Converter.convert(String, fieldType) to reconstruct the value.
    private static final Set<Class<?>> CONVERTABLE_TYPES = new IdentitySet<>();
    static {
        // Java Time types
        CONVERTABLE_TYPES.add(Duration.class);
        CONVERTABLE_TYPES.add(Instant.class);
        CONVERTABLE_TYPES.add(LocalDate.class);
        CONVERTABLE_TYPES.add(LocalDateTime.class);
        CONVERTABLE_TYPES.add(LocalTime.class);
        CONVERTABLE_TYPES.add(MonthDay.class);
        CONVERTABLE_TYPES.add(OffsetDateTime.class);
        CONVERTABLE_TYPES.add(OffsetTime.class);
        CONVERTABLE_TYPES.add(Period.class);
        CONVERTABLE_TYPES.add(Year.class);
        CONVERTABLE_TYPES.add(YearMonth.class);
        CONVERTABLE_TYPES.add(ZonedDateTime.class);
        CONVERTABLE_TYPES.add(ZoneId.class);
        CONVERTABLE_TYPES.add(ZoneOffset.class);

        // Date/Calendar types
        CONVERTABLE_TYPES.add(Date.class);
        CONVERTABLE_TYPES.add(java.sql.Date.class);
        CONVERTABLE_TYPES.add(java.sql.Timestamp.class);
        CONVERTABLE_TYPES.add(Calendar.class);
        CONVERTABLE_TYPES.add(GregorianCalendar.class);

        // Numeric types (BigInteger/BigDecimal write as strings, Atomics write as numbers)
        CONVERTABLE_TYPES.add(BigInteger.class);
        CONVERTABLE_TYPES.add(BigDecimal.class);
        CONVERTABLE_TYPES.add(AtomicBoolean.class);
        CONVERTABLE_TYPES.add(AtomicInteger.class);
        CONVERTABLE_TYPES.add(AtomicLong.class);

        // Utility types
        CONVERTABLE_TYPES.add(UUID.class);
        CONVERTABLE_TYPES.add(URI.class);
        CONVERTABLE_TYPES.add(URL.class);
        CONVERTABLE_TYPES.add(File.class);
        CONVERTABLE_TYPES.add(Path.class);
        CONVERTABLE_TYPES.add(Locale.class);
        CONVERTABLE_TYPES.add(TimeZone.class);
        CONVERTABLE_TYPES.add(Currency.class);
        CONVERTABLE_TYPES.add(Pattern.class);
        CONVERTABLE_TYPES.add(Class.class);

        // String-like and buffer types
        CONVERTABLE_TYPES.add(StringBuffer.class);
        CONVERTABLE_TYPES.add(StringBuilder.class);
        CONVERTABLE_TYPES.add(CharBuffer.class);
        CONVERTABLE_TYPES.add(ByteBuffer.class);
    }

    // Numeric primitives that can safely write as plain JSON numbers in MINIMAL/MINIMAL_PLUS modes.
    // These types round-trip as Long (for integers) or Double (for floats) when no @type is present.
    // Excludes: Character (String != Character), Atomic* (behavioral semantics), Big* (precision/range)
    private static final Set<Class<?>> NUMERIC_PRIMITIVES_FOR_COMPACT = new IdentitySet<>();
    static {
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(Byte.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(byte.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(Short.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(short.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(Integer.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(int.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(Float.class);
        NUMERIC_PRIMITIVES_FOR_COMPACT.add(float.class);
    }

    // Dispatch table for primitive array serialization (replaces class == chain)
    @FunctionalInterface
    private interface PrimitiveArrayHandler {
        void write(JsonWriter writer, Object array, int lenMinus1) throws IOException;
    }
    private static final ClassValueMap<PrimitiveArrayHandler> PRIM_ARRAY_WRITERS = new ClassValueMap<>();
    static {
        PRIM_ARRAY_WRITERS.put(byte[].class, (w, a, len) -> w.writeByteArray((byte[]) a, len));
        // char[] is special-cased inline in writePrimitiveArray (single string element via
        // gen.writeString at FRAME_ARRAY_EMPTY) — bypasses this handler map entirely.
        PRIM_ARRAY_WRITERS.put(short[].class, (w, a, len) -> w.writeShortArray((short[]) a, len));
        PRIM_ARRAY_WRITERS.put(int[].class, (w, a, len) -> w.writeIntArray((int[]) a, len));
        PRIM_ARRAY_WRITERS.put(long[].class, (w, a, len) -> w.writeLongArray((long[]) a, len));
        PRIM_ARRAY_WRITERS.put(float[].class, (w, a, len) -> w.writeFloatArray((float[]) a, len));
        PRIM_ARRAY_WRITERS.put(double[].class, (w, a, len) -> w.writeDoubleArray((double[]) a, len));
        PRIM_ARRAY_WRITERS.put(boolean[].class, (w, a, len) -> w.writeBooleanArray((boolean[]) a, len));
    }

    // Cached Field for EnumSet.elementType (lazy-initialized, immutable once set)
    private static volatile Field enumSetElementTypeField;
    private static volatile boolean enumSetFieldResolved = false;

    // Key names (no quotes, no colon) for the dog-food path through gen.writeXxxField —
    // gen handles quoting decisions based on json5UnquotedKeys + isValidJson5Identifier(name),
    // so @id/@i/@type/@t (not valid identifiers) always get quoted, $id/$i/$type/$t (valid
    // identifiers) get unquoted under json5UnquotedKeys.
    private final String idKey;
    private final String typeKey;
    private final String refKey;
    private final String itemsPrefix;
    private final String keysPrefix;
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.lineSeparator();
    private final WriteOptions writeOptions;
    private final WriteOptionsBuilder.DefaultWriteOptions defaultWriteOptions;
    // Lightweight identity-based maps for reference tracking (faster than IdentityHashMap<Object, Long>)
    // Uses primitive int values and open addressing - no boxing, no Entry objects
    // Only allocated when cycleSupport=true (lazy init avoids ~6KB wasted per writer when false)
    private IdentityIntMap objVisited;
    private IdentityIntMap objsReferenced;
    // Active path tracking for cycle detection when cycleSupport=false
    private Map<Object, Boolean> activePath;
    private final Writer out;
    /**
     * Generator wrapping {@link #out} for the same {@link #writeOptions}. Used by
     * the dog-food migration that converts JsonWriter's emission paths from direct
     * {@code out.write(...)} calls to {@code gen.writeXxx(...)} calls. Lives for
     * the JsonWriter's lifetime — no per-call bridge allocation.
     *
     * <p>Typed as the concrete {@link CharStreamGenerator} (package-private) so the
     * hot-path call sites can invoke the package-private {@code resetForBridge*}
     * methods without a cast on every dispatch. The public-facing accessor
     * {@link #getJsonGenerator()} returns it as {@link JsonGenerator} so external
     * callers don't see the concrete type.
     *
     * @since 4.104.0 (infrastructure)
     */
    private final CharStreamGenerator gen;
    private int identity = 1;  // int is sufficient - max 2.1 billion unique objects
    private int depth = 0;

    // Primitive depth tracking for traceReferences (avoids Integer autoboxing)
    // Only allocated when cycleSupport=true
    private int[] traceDepths;
    private int traceDepthIndex;
    // DOS guardrails, read at push-site by traceVisit(). Initialized in traceReferences.
    private int traceProcessedCount;
    private int traceMaxObjects;
    private int traceMaxDepth;

    // Element type context: when writing Collection/Map fields, this tracks the declared element type
    // from the field's generic type (e.g., List<NestedData> -> NestedData). Used to eliminate
    // redundant @type output when element instance type == declared element type.
    // For Maps with non-String keys written as @keys/@items arrays, declaredKeyType tracks the key type.
    private Class<?> declaredElementType = null;
    private Class<?> declaredKeyType = null;
    // Per-field @IoShowType — forces @type emission on elements within a container field
    private boolean forceElementShowType = false;
    // Per-field format pattern from @IoFormat / @JsonFormat — set during writeField(), cleared after
    private String fieldFormatPattern = null;

    // Pre-fetched WriteOptions values for hot path performance (immutable after construction)
    private final boolean skipNullFields;
    private final boolean json5UnquotedKeys;
    private final int maxStringLength;
    private final boolean prettyPrint;
    private final boolean neverShowingType;
    private final boolean alwaysShowingType;
    private final boolean writeLongsAsStrings;
    private final boolean json5SmartQuotes;
    private final int maxIndentationDepth;
    private final int indentationThreshold;
    private final int indentationSize;
    private final boolean cycleSupport;
    private final boolean minimalPlusFormat;
    private final boolean stringifyMapKeys;

    // Pre-fetched custom writers for primitive array hot paths (avoids per-array lookup)
    private final com.cedarsoftware.io.JsonClassWriter longBoxedWriter;
    private final com.cedarsoftware.io.JsonClassWriter longPrimitiveWriter;
    private final com.cedarsoftware.io.JsonClassWriter doubleWriter;
    private final com.cedarsoftware.io.JsonClassWriter floatWriter;

    // (Previously: a parallel state machine — Deque<WriteContext> contextStack + WriteContext
    // enum — tracked structural position for the public Jackson-style API. Removed in 4.103.0;
    // gen's contextStack is now the single source of truth. The Jackson-style API methods
    // below delegate to gen. The writeImpl wrapper handles state-sync across the
    // potentially-recursive writeImplInternal body so callers that mix Jackson API with
    // WriterContext.writeImpl see correct gen state on each return.)

    /**
     * Cached write type for type-indexed dispatch in writeImpl().
     * Avoids repeated instanceof checks for the same class.
     */
    private enum WriteType {
        PRIMITIVE_ARRAY,
        OBJECT_ARRAY,
        ENUM_SET,
        COLLECTION,
        JSON_OBJECT,
        MAP,
        POJO
    }

    // Cache for writeImpl() type dispatch - avoids repeated instanceof checks
    private static final ClassValue<WriteType> writeTypeCache = new ClassValue<WriteType>() {
        @Override
        protected WriteType computeValue(Class<?> type) {
            if (type.isArray()) {
                return type.getComponentType().isPrimitive() ?
                        WriteType.PRIMITIVE_ARRAY : WriteType.OBJECT_ARRAY;
            }
            // Must check JsonObject BEFORE Collection/Map (JsonObject implements Map)
            if (JsonObject.class.isAssignableFrom(type)) return WriteType.JSON_OBJECT;
            if (EnumSet.class.isAssignableFrom(type)) return WriteType.ENUM_SET;
            if (Collection.class.isAssignableFrom(type)) return WriteType.COLLECTION;
            if (Map.class.isAssignableFrom(type)) return WriteType.MAP;
            return WriteType.POJO;
        }
    };

    static {
        for (short i = -128; i <= 127; i++) {
            char[] chars = Integer.toString(i).toCharArray();
            byteStrings[i + 128] = chars;
        }
    }

    /**
     * @deprecated Use top-level {@link com.cedarsoftware.io.JsonClassWriter} instead.
     *             This nested interface is kept for backward compatibility.
     */
    @Deprecated
    public interface JsonClassWriter<T> extends com.cedarsoftware.io.JsonClassWriter<T> {
    }

    /**
     * @param out OutputStream to which the JSON will be written.  Uses the default WriteOptions.
     * @see WriteOptions
     */
    public JsonWriter(OutputStream out) {
        this(out, null);
    }

    /**
     * @param out          OutputStream to which the JSON output will be written.
     * @param writeOptions WriteOptions containing many feature options to control the JSON output.  Can be null,
     *                     in which case the default WriteOptions will be used.
     * @see WriteOptions Javadoc.
     */
    public JsonWriter(OutputStream out, WriteOptions writeOptions) {
        this(new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)), writeOptions);
    }

    /**
     * @param out          Writer to which the JSON output will be written.
     * @param writeOptions WriteOptions containing many feature options to control the JSON output. Can be null,
     *                     in which case the default WriteOptions will be used.
     */
    public JsonWriter(Writer out, WriteOptions writeOptions) {
        this.out = out;
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;
        this.defaultWriteOptions = this.writeOptions instanceof WriteOptionsBuilder.DefaultWriteOptions
                ? (WriteOptionsBuilder.DefaultWriteOptions) this.writeOptions : null;

        // Pre-compute meta key prefixes based on options
        // JSON5 mode uses $ prefix (valid unquoted identifier), standard mode uses @ prefix (requires quotes)
        // Short meta keys use single-character keys (@t/$t, @i/$i, etc.)
        // Users can override the default prefix with useMetaPrefixAt() or useMetaPrefixDollar()
        boolean isJson5UnquotedKeys = this.writeOptions.isJson5UnquotedKeys();
        boolean isShort = this.writeOptions.isShortMetaKeys();
        Character metaPrefixOverride = this.writeOptions.getMetaPrefixOverride();

        // Determine which prefix to use: $ or @
        // Override takes precedence, otherwise use $ for JSON5, @ for standard
        boolean useDollarPrefix = (metaPrefixOverride != null)
                ? (metaPrefixOverride == '$')
                : isJson5UnquotedKeys;

        // Can only use unquoted keys if JSON5 mode is enabled AND using $ prefix
        // (@ prefix always requires quotes since @ is not a valid identifier start)
        boolean canUseUnquotedKeys = isJson5UnquotedKeys && useDollarPrefix;

        if (useDollarPrefix && canUseUnquotedKeys && isShort) {
            // JSON5 + $ prefix + short: $e, $k (unquoted)
            this.itemsPrefix = ITEMS_JSON5_SHORT;
            this.keysPrefix = KEYS_JSON5_SHORT;
        } else if (useDollarPrefix && canUseUnquotedKeys) {
            // JSON5 + $ prefix + long: $items, $keys (unquoted)
            this.itemsPrefix = ITEMS_JSON5;
            this.keysPrefix = KEYS_JSON5;
        } else if (useDollarPrefix && isShort) {
            // $ prefix + short + quoted: "$e", "$k"
            this.itemsPrefix = ITEMS_DOLLAR_SHORT_QUOTED;
            this.keysPrefix = KEYS_DOLLAR_SHORT_QUOTED;
        } else if (useDollarPrefix) {
            // $ prefix + long + quoted: "$items", "$keys"
            this.itemsPrefix = ITEMS_DOLLAR_QUOTED;
            this.keysPrefix = KEYS_DOLLAR_QUOTED;
        } else if (isShort) {
            // @ prefix + short + quoted: "@e", "@k"
            this.itemsPrefix = ITEMS_SHORT;
            this.keysPrefix = KEYS_SHORT;
        } else {
            // @ prefix + long + quoted: "@items", "@keys"
            this.itemsPrefix = ITEMS_LONG;
            this.keysPrefix = KEYS_LONG;
        }

        // Bare key name (no quotes, no colon) for the dog-food path. 4 variants
        // collapse from the 6 prefix variants above: gen.writeFieldName(name) makes
        // the quoting decision itself based on json5UnquotedKeys + whether name is
        // a valid JSON5 identifier (@x starts with @, never valid; $x starts with $,
        // always valid).
        if (useDollarPrefix && isShort) {
            this.idKey = "$i";
            this.typeKey = "$t";
            this.refKey = "$r";
        } else if (useDollarPrefix) {
            this.idKey = "$id";
            this.typeKey = "$type";
            this.refKey = "$ref";
        } else if (isShort) {
            this.idKey = "@i";
            this.typeKey = "@t";
            this.refKey = "@r";
        } else {
            this.idKey = "@id";
            this.typeKey = "@type";
            this.refKey = "@ref";
        }

        // Pre-fetch frequently accessed WriteOptions for hot path performance
        this.skipNullFields = this.writeOptions.isSkipNullFields();
        this.json5UnquotedKeys = isJson5UnquotedKeys;  // Already computed above
        this.maxStringLength = this.writeOptions.getMaxStringLength();
        this.prettyPrint = this.writeOptions.isPrettyPrint();
        this.neverShowingType = this.writeOptions.isNeverShowingType();
        this.alwaysShowingType = this.writeOptions.isAlwaysShowingType();
        this.writeLongsAsStrings = this.writeOptions.isWriteLongsAsStrings();
        this.json5SmartQuotes = this.writeOptions.isJson5SmartQuotes();
        this.maxIndentationDepth = this.writeOptions.getMaxIndentationDepth();
        this.indentationThreshold = this.writeOptions.getIndentationThreshold();
        this.indentationSize = this.writeOptions.getIndentationSize();
        this.cycleSupport = this.writeOptions.isCycleSupport();
        this.minimalPlusFormat = this.writeOptions.isMinimalPlusShowingType();
        this.stringifyMapKeys = this.writeOptions.isStringifyMapKeys();

        // Pre-fetch custom writers for primitive array hot paths
        this.longBoxedWriter = this.writeOptions.getCustomWriter(Long.class);
        this.longPrimitiveWriter = this.writeOptions.getCustomWriter(long.class);
        this.doubleWriter = this.writeOptions.getCustomWriter(Double.class);
        this.floatWriter = this.writeOptions.getCustomWriter(Float.class);

        // Allocate tracking structures only for the active mode
        if (this.cycleSupport) {
            this.objVisited = new IdentityIntMap(256);
            this.objsReferenced = new IdentityIntMap(256);
            this.traceDepths = new int[256];
        } else {
            this.activePath = new IdentityHashMap<>();
        }

        // Dog-food infrastructure (chunks D2+): construct a CharStreamGenerator over
        // the same Writer + WriteOptions this JsonWriter is using. Not yet wired into
        // emission paths — declared here so subsequent chunks can route emissions
        // through it incrementally. Constructor allocates the generator's context
        // stack but emits nothing, so the byte stream is unchanged.
        this.gen = new CharStreamGenerator(this.out, this.writeOptions);
    }

    /**
     * Returns the {@link JsonGenerator} this writer holds for the dog-food migration in
     * progress (chunks D2+). External callers should not use this — the generator's
     * structural-state machine is currently <i>not</i> synchronized with JsonWriter's
     * own {@code contextStack}, so the two emission paths must be considered
     * incompatible until the migration completes. Exposed package-private so future
     * chunks within {@code com.cedarsoftware.io} can drive emission through it.
     *
     * @since 4.104.0 (infrastructure)
     */
    JsonGenerator getJsonGenerator() {
        return gen;
    }

    public WriteOptions getWriteOptions() {
        return writeOptions;
    }

    @Override
    public String getFieldFormatPattern() {
        return fieldFormatPattern;
    }

    /**
     * Map containing all objects that were visited within input object graph.
     * Uses identity comparison (==) for keys.
     */
    protected IdentityIntMap getObjVisited() {
        return objVisited;
    }

    /**
     * Map containing all objects that were referenced within input object graph.
     * Uses identity comparison (==) for keys.
     */
    public IdentityIntMap getObjsReferenced() {
        return objsReferenced;
    }

    /**
     * Add newline (\n) to output at the current indent level.
     *
     * @throws IOException
     */
    public void newLine() throws IOException {
        tab(out, 0);
    }

    /**
     * tab the JSON output by the given number of characters specified by delta.
     *
     * @param output Writer being used for JSON output.
     * @param delta  int number of characters to tab.
     * @throws IOException
     */
    private void tab(Writer output, int delta) throws IOException {
        if (!prettyPrint) {
            return;
        }
        output.write(NEW_LINE);
        depth += delta;

        // Optimized indentation - build spaces once instead of multiple writes
        if (depth > 0) {
            // Prevent excessive indentation to avoid memory issues using pre-fetched limit
            final int actualDepth = Math.min(depth, maxIndentationDepth);

            if (actualDepth <= indentationThreshold) {
                // For small depths, use simple repeated writes (faster for small depths)
                for (int i = 0; i < actualDepth; i++) {
                    for (int j = 0; j < indentationSize; j++) {
                        output.write(' ');
                    }
                }
            } else {
                // For larger depths, build the string once and write it
                char[] spaces = new char[actualDepth * indentationSize];
                Arrays.fill(spaces, ' ');
                output.write(spaces);
            }

            if (depth > maxIndentationDepth) {
                // Warn about excessive depth to help detect issues
                output.write("... (depth=" + depth + ")");
            }
        }
    }

    /**
     * Write the passed in object (o) to the JSON output stream, if and only if, there is a custom
     * writer associated to the Class of object (o).
     *
     * @param o        Object to be (potentially written)
     * @param showType boolean indicating whether to show @type.
     * @param output   Writer where the actual JSON is being written to.
     * @return boolean true if written, false is there is no custom writer for the passed in object.
     */
    public boolean writeUsingCustomWriter(Object o, boolean showType, Writer output) {
        Class<?> c = o.getClass();

        try {
            return writeCustom(c, o, (!neverShowingType || forceElementShowType) && showType, output);
        } catch (Exception e) {
            throw new JsonIoException("Unable to write custom formatted object:", e);
        }
    }

    /**
     * Get the custom writer for the object if custom writing is allowed.
     * Returns null if custom writing should not be used for this object.
     * This method combines the check and lookup to avoid redundant getCustomWriter calls.
     *
     * @param declaredClass the declared type (e.g., field type, array component type)
     * @param o the object to write
     * @return the JsonClassWriter to use, or null if custom writing is not allowed
     */
    private com.cedarsoftware.io.JsonClassWriter getCustomWriterIfAllowed(Class<?> declaredClass, Object o) {
        final boolean notCustomWrittenClass;
        final com.cedarsoftware.io.JsonClassWriter declaredWriter;
        if (defaultWriteOptions != null) {
            WriteOptionsBuilder.DefaultWriteOptions.CustomWriterGate gate = defaultWriteOptions.getCustomWriterGate(declaredClass);
            notCustomWrittenClass = gate.notCustomWrittenClass;
            declaredWriter = gate.declaredWriter;
        } else {
            notCustomWrittenClass = writeOptions.isNotCustomWrittenClass(declaredClass);
            declaredWriter = writeOptions.getCustomWriter(declaredClass);
        }

        // Exit early if the declared class is explicitly marked as not-custom-written
        if (notCustomWrittenClass) {
            return null;
        }

        // Exit early if 'o' is a default CompactSet/CompactMap (use standard serialization)
        if ((o instanceof CompactSet) && ((CompactSet<?>) o).isDefaultCompactSet()) {
            return null;
        }
        if ((o instanceof CompactMap) && ((CompactMap<?, ?>) o).isDefaultCompactMap()) {
            return null;
        }

        // Get the actual runtime class
        Class<?> actualClass = o.getClass();

        // Optimization: when declared type equals runtime type, only one lookup needed
        if (declaredClass == actualClass) {
            return declaredWriter;
        }

        // Different types: check if declared type has a writer (gatekeeper)
        // If declared type has no writer, don't use custom writing even if actual type has one
        if (declaredWriter == null) {
            return null;
        }

        // Get the writer for the actual runtime type (may be more specific)
        return writeOptions.getCustomWriter(actualClass);
    }

    /**
     * Write the passed in array element to the JSON output, if any only if, there is a custom writer
     * for the class of the instance 'o'.
     *
     * @param arrayComponentClass Class type of the array
     * @param o                   Object instance to write
     * @param showType            boolean indicating whether @type should be output.
     * @param output              Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    public boolean writeArrayElementIfMatching(Class<?> arrayComponentClass, Object o, boolean showType, Writer output) {
        if (!arrayComponentClass.isInstance(o)) {
            return false;
        }

        try {
            return writeCustom(arrayComponentClass, o, showType, output);
        } catch (IOException e) {
            throw new JsonIoException("Unable to write custom formatted object as array element:", e);
        }
    }

    /**
     * Perform the actual custom writing for an array element that has a custom writer.
     *
     * @param clazz    Class type of the array
     * @param o        Object instance to write
     * @param showType boolean indicating whether @type should be output.
     * @param output   Writer to write the JSON to (if there is a custom writer for o's Class).
     * @return true if the array element was written, false otherwise.
     */
    protected boolean writeCustom(Class<?> clazz, Object o, boolean showType, Writer output) throws IOException {
        // Combined check and lookup - avoids redundant getCustomWriter calls
        com.cedarsoftware.io.JsonClassWriter closestWriter = getCustomWriterIfAllowed(clazz, o);
        if (closestWriter == null) {
            return false;
        }
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }

        boolean enteredActivePath = !cycleSupport && isReferenceTrackable(o) && !activePath.containsKey(o);
        try {
            if (writeOptionalReference(o)) {
                return true;
            }

            final boolean referenced = cycleSupport && objsReferenced.containsKey(o);

            // Dispatch decision: cached per writer class. See CustomWriterDispatch.
            CustomWriterDispatch.Info dispatch = CustomWriterDispatch.forWriter(closestWriter);

            if (closestWriter.hasPrimitiveForm(this)) {
                if ((!referenced && !showType) || closestWriter instanceof Writers.JsonStringWriter) {
                    if (dispatch.useNewPrimitive) {
                        // Primitive form: custom writer emits one value via gen.
                        // resetForBridgeAtValueSlot puts gen at FRAME_ROOT_EMPTY so the
                        // writer's single emission has no leading separator. Caller's
                        // writeImpl wrapper restores outer state on exit.
                        this.gen.resetForBridgeAtValueSlot();
                        closestWriter.writePrimitiveForm(o, this.gen, this);
                    } else {
                        closestWriter.writePrimitiveForm(o, output, this);
                    }
                    return true;
                }
            }

            // Object-form custom-writer dispatch — emit the wrapping {} + @id/@type prelude
            // via gen-driven structural emission (same pattern as writeObject's chunk-6
            // refactor). writeStartObjectRaw pushes from current gen.depth without
            // overwriting the outer frame, and writeEndObjectRaw's pop + markValue
            // transitions the outer frame on exit.
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, getIdInt(o));
            }
            if (showType) {
                String alias = writeOptions.getTypeNameAlias(closestWriter.getTypeName(o));
                gen.writeStringFieldUnescaped(typeKey, alias);
            }

            // Dispatch the writer's body emission. Two paths:
            // - New-API (dispatch.useNewWrite): writer receives this.gen and emits fields
            //   via gen.writeFieldName / writeXxx. gen state at this point is either
            //   FRAME_OBJECT_EMPTY (no @id/@type emitted) or FRAME_OBJECT_AFTER_VALUE
            //   (after @id/@type) — both states correctly auto-emit the leading separator
            //   for the writer's first writeFieldName call. No reset needed.
            // - Legacy (Writer-based): writer may emit "field":value pairs via raw
            //   output.write OR via context.writeFieldName (which delegates back to gen).
            //   Legacy contract: JsonWriter emitted "," + newLine before the call (so the
            //   raw output.write path's first field has its leading separator on the
            //   stream); gen is positioned at FRAME_OBJECT_EMPTY (so the
            //   context.writeFieldName path's first call doesn't auto-emit ANOTHER
            //   comma). resetForBridgeInsideObjectBody synthesizes that gen state cheaply
            //   without rebuilding a separate bridge generator. This dual-mode tolerance
            //   preserves backward compat for all existing legacy custom writers.
            if (dispatch.useNewWrite) {
                closestWriter.write(o, showType || referenced, this.gen, this);
            } else {
                if (referenced || showType) {
                    output.write(',');
                    newLine();
                }
                // Reset the CURRENT object-body frame to FRAME_OBJECT_EMPTY so legacy
                // writers that call back via context.writeFieldName don't double-emit the
                // leading comma. Caller's writeImpl wrapper restores outer state on exit.
                this.gen.resetCurrentObjectFrame();
                closestWriter.write(o, showType || referenced, output, this);
            }

            gen.writeEndObjectRaw();
            return true;
        } finally {
            if (enteredActivePath) {
                activePath.remove(o);
            }
        }
    }

    /**
     * Write the passed in Java object in JSON format.
     *
     * @param obj Object any Java Object or JsonObject.
     */
    public void write(Object obj) {
        try {
            if (cycleSupport) {
                traceReferences(obj);
                objVisited.clear();
            }
            // When cycleSupport=false, trace pass is skipped (no @id/@ref pre-tracing).
            boolean showType = writeOptions.isShowingRootTypeInfo();
            if (obj != null) {
                if (neverShowingType && !forceElementShowType) {
                    showType = false;
                } else if (!alwaysShowingType) {
                    com.cedarsoftware.io.JsonClassWriter writer = writeOptions.getCustomWriter(obj.getClass());
                    if (writer instanceof Writers.EnumsAsStringWriter) {
                        int fieldCount = writeOptions.getAccessorsForClass(obj.getClass()).size();
                        if (fieldCount <= 2) {
                            String alias = writeOptions.getTypeNameAlias(obj.getClass().getName());
                            if (alias.equals(obj.getClass().getName())) {
                                showType = false;
                            }
                        }
                    }
                }
            }
            writeImpl(obj, showType);
            flush();
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("Error writing object to JSON:", e);
        } finally {
            if (objVisited != null) { objVisited.clear(); }
            if (objsReferenced != null) { objsReferenced.clear(); }
            if (activePath != null) { activePath.clear(); }
        }
    }

    /**
     * Walk object graph and visit each instance, following each field, each Collection, Map and so on.
     * Tracks visited to handle cycles and to determine if an item is referenced elsewhere.  If an
     * object is never referenced more than once, no @id field needs to be emitted for it.
     *
     * @param root Object to be deeply traced.  The objVisited and objsReferenced Maps will be written to
     *             during the trace.
     */
    protected void traceReferences(Object root) {
        if (root == null) {
            return;
        }

        // Use object stack with parallel primitive int[] for depths (avoids Integer autoboxing)
        final Deque<Object> objectStack = new ArrayDeque<>(256);
        traceDepthIndex = 0;  // Reset depth index

        // Snapshot DOS guardrails so traceVisit() can enforce them at push-time,
        // counting non-referenceable leaves toward the limits even though they
        // never land on the stack.
        traceProcessedCount = 0;
        traceMaxDepth = writeOptions.getMaxObjectGraphDepth();
        traceMaxObjects = writeOptions.getMaxObjectCount();

        traceVisit(root, objectStack, 0);

        final IdentityIntMap visited = objVisited;
        final IdentityIntMap referenced = objsReferenced;

        // Marker value: -1 means "seen but no ID assigned yet", 0 means "not in map", >0 is the assigned ID
        final int FIRST_SEEN = -1;

        while (!objectStack.isEmpty()) {
            final Object obj = objectStack.removeFirst();
            final int currentDepth = traceDepths[--traceDepthIndex];

            // Everything on the stack was pre-filtered by traceVisit, so obj is
            // guaranteed non-null and referenceable. No duplicate checks needed.
            int id = visited.get(obj);
            if (id != 0) {   // Object has been seen before (0 = NOT_FOUND)
                if (id == FIRST_SEEN) {
                    id = identity++;
                    visited.put(obj, id);
                    referenced.put(obj, id);
                }
                continue;
            }
            visited.put(obj, FIRST_SEEN);

            final int nextDepth = currentDepth + 1;

            // Use instanceof instead of isAssignableFrom (30x faster)
            if (obj instanceof Object[]) {
                processArray((Object[]) obj, objectStack, nextDepth);
            } else if (obj instanceof JsonObject) {
                processJsonObject((JsonObject) obj, objectStack, nextDepth);
            } else if (obj instanceof Map) {
                processMap((Map<?, ?>) obj, objectStack, nextDepth);
            } else if (obj instanceof Collection) {
                processCollection((Collection<?>) obj, objectStack, nextDepth);
            } else {
                processFields(objectStack, obj, nextDepth);
            }
        }
    }

    /**
     * Enforce DOS limits and push {@code o} onto the trace stack only if it can
     * actually participate in reference cycles. Non-referenceable leaves (Strings,
     * primitive wrappers, temporals, UUID, etc.) are counted toward the object-count
     * limit and depth-checked so that DOS semantics are preserved, but they never
     * land on the stack — saving a deque push + pop + isNonReferenceable re-check
     * per leaf. Mirrors ToonWriter's pushReferenceCandidate pre-filter.
     */
    private void traceVisit(Object o, Deque<Object> stack, int depth) {
        if (o == null) {
            return;
        }
        if (traceProcessedCount >= traceMaxObjects) {
            throw new JsonIoException("Object graph too large (>" + traceMaxObjects + " objects). This may indicate excessive nesting or a memory leak.");
        }
        if (depth > traceMaxDepth) {
            throw new JsonIoException("Object graph too deep (>" + traceMaxDepth + " levels). This may indicate a circular reference or excessively nested structure.");
        }
        traceProcessedCount++;
        if (!writeOptions.isNonReferenceableClass(o.getClass())) {
            stack.addFirst(o);
            pushDepth(depth);
        }
    }

    /**
     * Push a depth value onto the primitive depth stack, growing if necessary.
     */
    private void pushDepth(int depth) {
        if (traceDepthIndex >= traceDepths.length) {
            traceDepths = Arrays.copyOf(traceDepths, traceDepths.length * 2);
        }
        traceDepths[traceDepthIndex++] = depth;
    }

    private void processArray(Object[] array, Deque<Object> objectStack, int depth) {
        final Class<?> componentType = array.getClass().getComponentType();
        // Short-circuit iteration for arrays with non-referenceable components (String[], UUID[]
        // etc.), UNLESS preserveLeafContainerIdentity=true — in which case the caller explicitly
        // wants identity tracking for leaf-element containers, so we must iterate to catch
        // sharing of this array's non-ref elements that happen to appear elsewhere.
        if (writeOptions.isNonReferenceableClass(componentType)
                && !writeOptions.isPreserveLeafContainerIdentity()) {
            return;
        }
        for (final Object element : array) {
            traceVisit(element, objectStack, depth);
        }
    }

    private void processJsonObject(JsonObject jsonObj, Deque<Object> objectStack, int depth) {
        Object[] items = jsonObj.getItems();
        Object[] keys = jsonObj.getKeys();

        if (items != null || keys != null) {
            // Explicit @items/@keys format - process those arrays directly
            if (items != null) {
                processArray(items, objectStack, depth);
            }
            if (keys != null) {
                processArray(keys, objectStack, depth);
            }
        } else {
            // Regular POJO - process via Map interface
            processMap(jsonObj, objectStack, depth);
        }
    }

    private void processMap(Map<?, ?> map, Deque<Object> objectStack, int depth) {
        if (map instanceof JsonObject) {
            JsonObject jsonObject = (JsonObject) map;
            int len = jsonObject.fastEntryCount();
            for (int i = 0; i < len; i++) {
                traceVisit(jsonObject.fastKeyAt(i), objectStack, depth);
                traceVisit(jsonObject.fastValueAt(i), objectStack, depth);
            }
            return;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            traceVisit(entry.getKey(), objectStack, depth);
            traceVisit(entry.getValue(), objectStack, depth);
        }
    }

    private void processCollection(Collection<?> collection, Deque<Object> objectStack, int depth) {
        for (Object item : collection) {
            traceVisit(item, objectStack, depth);
        }
    }

    /**
     * Reach-ability trace to visit all objects within the graph to be written.
     * This API will handle any object, using either reflection APIs or by
     * consulting a specified includedFields map if provided.
     *
     * @param objectStack Deque of objects used to manage descent into graph (rather than using Java stack)
     * @param obj         Object root of graph
     * @param depth       Current depth in the object graph
     */
    protected void processFields(final Deque<Object> objectStack, final Object obj, int depth) {
        List<WriteFieldPlan> fields = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, obj.getClass());
        int len = fields.size();
        for (int i = 0; i < len; i++) {
            WriteFieldPlan fieldPlan = fields.get(i);
            if (fieldPlan.skipReferenceTrace()) {
                continue;
            }
            if (canSkipContainerTrace(fieldPlan)) {
                // Field is a Collection/Map/Array whose element(s) cannot hold references —
                // skip pushing it entirely. Consistent with Jackson's default identity semantics
                // for primitive-element containers: two fields pointing to the same
                // List<String>/Map<String,String>/String[] are written as two copies on the
                // wire (no @id/@ref), matching how Jackson deserializes them.
                continue;
            }
            traceVisit(fieldPlan.accessor().retrieve(obj), objectStack, depth);
        }
    }

    /**
     * Returns true if {@code plan}'s field is a Collection, Map, or Array whose
     * declared element types are all non-referenceable leaves (String, primitive
     * wrappers, temporals, UUID, etc.) — meaning no element could ever hold a
     * reference-typed object. For such fields, traceReferences doesn't need to
     * visit the container or its elements: no cycles are possible, no shared
     * references among elements are possible, and we treat the container itself
     * as value-shaped (two field pointers to the same primitive-element container
     * are serialized as two copies, matching Jackson's default).
     *
     * For Map specifically, we require BOTH key and value types to be non-referenceable —
     * a Map&lt;String, Foo&gt; still needs Foo values traced for sharing detection.
     */
    private boolean canSkipContainerTrace(WriteFieldPlan plan) {
        if (writeOptions.isPreserveLeafContainerIdentity()) {
            return false;
        }
        Class<?> fieldType = plan.declaredFieldType();
        if (fieldType == null) {
            return false;
        }
        if (Map.class.isAssignableFrom(fieldType)) {
            Class<?> keyType = plan.declaredKeyType();
            Class<?> valType = plan.declaredElementType();
            return keyType != null && valType != null
                    && writeOptions.isNonReferenceableClass(keyType)
                    && writeOptions.isNonReferenceableClass(valType);
        }
        if (Collection.class.isAssignableFrom(fieldType)) {
            Class<?> elemType = plan.declaredElementType();
            return elemType != null && writeOptions.isNonReferenceableClass(elemType);
        }
        if (fieldType.isArray()) {
            Class<?> compType = fieldType.getComponentType();
            return compType != null && writeOptions.isNonReferenceableClass(compType);
        }
        return false;
    }

    private boolean isReferenceTrackable(Object obj) {
        if (obj == null) {
            return false;
        }
        // When cycleSupport is off, skip the NonReferenceableClass lookup — it's only needed
        // for $id/$ref decisions. Cycle detection via activePath doesn't need it since
        // non-referenceable types (temporals, BigInteger, UUID, etc.) are leaf values that can't form cycles.
        return !cycleSupport || !writeOptions.isNonReferenceableClass(obj.getClass());
    }

    private JsonIoException cycleDetected(Object obj) {
        return new JsonIoException("Cycle detected while writing JSON with cycleSupport(false): "
                + obj.getClass().getName()
                + ". To write cyclic object graphs, enable cycleSupport(true) on WriteOptions.");
    }

    private boolean writeOptionalReference(Object obj) throws IOException {
        if (!isReferenceTrackable(obj)) {
            return false;
        }

        if (!cycleSupport) {
            if (activePath.containsKey(obj)) {
                throw cycleDetected(obj);
            }
            activePath.put(obj, Boolean.TRUE);
            return false;
        }

        if (objVisited.containsKey(obj)) {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            int id = getIdInt(obj);
            if (id == 0) {   // Test for 0 because of Weak/Soft references being gc'd during serialization.
                return false;
            }
            // Self-contained value-slot object emission: {"@ref":<id>} (or $ref / @r / $r variants).
            // Uses writeStartObjectRaw which pushes from the current gen.depth without
            // overwriting the outer caller's frame — outer state at gen.depth is preserved.
            // writeEndObjectRaw's pop + markValue transitions the outer frame from
            // FRAME_OBJECT_AFTER_FIELD (or whatever value-slot state the caller was in) to
            // FRAME_OBJECT_AFTER_VALUE on exit. Same pattern as writeObject's chunk-6
            // refactor — no reset needed.
            gen.writeStartObjectRaw();
            gen.writeNumberField(refKey, id);
            gen.writeEndObjectRaw();
            return true;
        }

        // Mark the object as visited by putting it in the Map (this map is re-used / clear()'d after walk()).
        objVisited.put(obj, -1);  // -1 = marker value (visited, value doesn't matter for this use case)
        return false;
    }

    /**
     * Main entry point (mostly used internally, but may be called from a Custom JSON writer).
     * This method will write out whatever object type it is given, including JsonObject's.
     * It will handle null, detecting if a custom writer should be called, array, array of
     * JsonObject, Map, Map of JsonObjects, Collection, Collection of JsonObject, any regular
     * object, or a JsonObject representing a regular object.
     *
     * <p>The body is bracketed by {@link CharStreamGenerator#snapshotForExternalValue}
     * / {@link CharStreamGenerator#restoreAfterExternalValue} so that callers (array /
     * collection / map element loops, POJO field emission, top-level emit) see gen in
     * the same state on exit as on entry. The wrapper is load-bearing for two reasons:
     * <ol>
     *   <li>Several body paths intentionally reset gen state (writeCustom's primitive-form
     *       + legacy-writer dispatch, writePrimitive's Long-wrap bare-value path,
     *       writeStringValue's pretty-print indent reset). The wrapper restores outer
     *       state on exit so callers don't need their own snap+restore.</li>
     *   <li>Element loops compute indent off {@code this.depth} (legacy
     *       {@code newLine()} path) which may diverge from {@code gen.depth} when the
     *       body emits its own nested structures. The wrapper restores gen.depth so
     *       subsequent body emissions are at the correct depth.</li>
     * </ol>
     * Removing the wrapper would require migrating all element loops off the
     * {@code this.depth}-based newLine pattern to gen-driven indent emission.
     *
     * @param obj      Object to be written
     * @param showType if set to true, the @type tag will be output.
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType) throws IOException {
        int snap = gen.snapshotForExternalValue();
        writeImplInternal(obj, showType);
        gen.restoreAfterExternalValue(snap);
    }

    private void writeImplInternal(Object obj, boolean showType) throws IOException {
        // For security - write instances of these classes out as null
        if (obj == null ||
                obj instanceof ProcessBuilder ||
                obj instanceof Process ||
                obj instanceof ClassLoader ||
                obj instanceof Constructor ||
                obj instanceof Method ||
                obj instanceof Field) {
            out.write("null");
            return;
        }

        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }

        // Fast path: common primitive wrappers when no @type is needed and no per-field format
        // pattern is active. Bypasses writeUsingCustomWriter -> writeCustom -> PrimitiveValueWriter
        // .writePrimitiveForm dispatch chain, the activePath tracking setup, and the Integer.toString()
        // / Long.toString() String allocation. Only applies when !showType && !forceElementShowType
        // (the overwhelmingly common case) and fieldFormatPattern is null (the slow path handles
        // @IoFormat / @JsonFormat C-style patterns like %,d and %s). Runtime class must match exactly
        // (getClass() ==) so subclasses still route through the slow path.
        if (!showType && !forceElementShowType && fieldFormatPattern == null) {
            final Class<?> c = obj.getClass();
            if (c == Integer.class) {
                int val = (Integer) obj;
                if (val >= SMALL_INT_LOW && val <= SMALL_INT_HIGH) {
                    out.write(SMALL_INT_STRINGS[val - SMALL_INT_LOW]);
                } else {
                    gen.writeIntRaw(val);
                }
                return;
            }
            if (c == Long.class && !writeLongsAsStrings) {
                gen.writeLongRaw((Long) obj);
                return;
            }
            if (c == Boolean.class) {
                out.write(((Boolean) obj) ? "true" : "false");
                return;
            }
        }

        boolean enteredActivePath = !cycleSupport && isReferenceTrackable(obj) && !activePath.containsKey(obj);
        try {
            // Custom writers and references are checked first to preserve type information
            if (writeUsingCustomWriter(obj, showType, out) || writeOptionalReference(obj)) {
                return;
            }

            final Class<?> objClass = obj.getClass();

            // Check @IoValue — serialize via single method return value
            Method valueMethod = AnnotationResolver.getMetadata(objClass).getValueMethod();
            if (valueMethod != null) {
                try {
                    Object val = valueMethod.invoke(obj);
                    if (showType) {
                        // Gen-driven {} envelope around @type + value.
                        gen.writeStartObjectRaw();
                        String alias = writeOptions.getTypeNameAlias(objClass.getName());
                        gen.writeStringFieldUnescaped(typeKey, alias);
                        gen.writeFieldName("value");
                        writeImpl(val, false);
                        gen.writeEndObjectRaw();
                    } else {
                        writeImpl(val, false);
                    }
                } catch (Exception e) {
                    throw new JsonIoException("@IoValue method invocation failed for " + objClass.getName(), e);
                }
                return;
            }

            // Type-indexed dispatch using cached WriteType (avoids repeated instanceof checks)
            switch (writeTypeCache.get(objClass)) {
                case PRIMITIVE_ARRAY:
                    writePrimitiveArray(obj, objClass, showType);
                    break;
                case OBJECT_ARRAY:
                    writeObjectArray((Object[]) obj, objClass, showType);
                    break;
                case ENUM_SET:
                    writeEnumSet((EnumSet<?>) obj);
                    break;
                case COLLECTION:
                    writeCollection((Collection<?>) obj, showType);
                    break;
                case JSON_OBJECT:
                    // Performance: Use cached type classification instead of repeated isArray/isCollection/isMap checks
                    JsonObject jObj = (JsonObject) obj;
                    switch (jObj.getJsonType()) {
                        case ARRAY:
                            writeJsonObjectArray(jObj, showType);
                            break;
                        case COLLECTION:
                            writeJsonObjectCollection(jObj, showType);
                            break;
                        case MAP:
                            if (!writeJsonObjectMapWithStringKeys(jObj, showType)) {
                                writeJsonObjectMap(jObj, showType);
                            }
                            break;
                        default:
                            writeJsonObjectObject(jObj, showType);
                            break;
                    }
                    break;
                case MAP:
                    if (!writeMapWithStringKeys((Map) obj, showType)) {
                        writeMap((Map) obj, showType);
                    }
                    break;
                case POJO:
                    writeObject(obj, showType, false);
                    break;
            }
        } finally {
            if (enteredActivePath) {
                activePath.remove(obj);
            }
        }
    }

    /**
     * Emit a primitive-wrapper value at a value-slot position. Callers gate by runtime
     * type (one of Boolean, Byte, Short, Integer, Long, Float, Double) before invoking;
     * the dispatched branches below mirror that set. Non-Long-wrap paths route through
     * {@link CharStreamGenerator}'s {@code writeXxxRaw} helpers — state-machine-free
     * emission primitives that share the digit-pair / canonical-form logic with the
     * public streaming-write API but skip the {@code startValueContext + markValue}
     * bookkeeping JsonWriter doesn't need for these isolated single-value emissions.
     * Second leaf of the JsonWriter dog-food migration; the Integer/Long paths drop the
     * {@code Integer.toString} / {@code Long.toString} allocations the legacy path paid.
     * The Long-wrap branch (showType + writeLongsAsStrings) remains on legacy
     * {@code out.write} pending {@code @type} prefix alignment.
     */
    private void writePrimitive(final Object obj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        if (obj instanceof Long && writeLongsAsStrings) {
            // Long-wrap path: emits {"@type":"long","value":"<long-as-string>"} when
            // showType, else just "<long-as-string>" at the value slot.
            //
            // showType=true (wrapped): uses writeStartObjectRaw + writeStringFieldUnescaped
            // + longBoxedWriter.write + writeEndObjectRaw. Same pattern as writeObject's
            // chunk-6 refactor — no reset needed; writeStartObjectRaw pushes from current
            // depth, and writeEndObjectRaw's pop + markValue transitions the outer frame.
            //
            // showType=false (bare value): retains the legacy resetForBridgeAtValueSlot.
            // longBoxedWriter.write(obj, false, gen, this) routes through PrimitiveTypeWriter.
            // write -> writePrimitiveForm -> gen.writeString — which goes through the state
            // machine. If the caller (e.g., writeJsonObjectArray's element loop) is in
            // FRAME_ARRAY_AFTER_VALUE state with its own manual `,\n + indent` separator
            // between elements, the gen-driven emission would add a SECOND comma. The reset
            // puts gen at FRAME_ROOT_EMPTY so gen.writeString emits no separator. Caller
            // restores depth afterward. Keeping the reset here preserves custom-writer
            // compatibility (a user-registered LongWriter override fires through gen as
            // expected).
            if (showType) {
                gen.writeStartObjectRaw();
                gen.writeStringFieldUnescaped(typeKey, "long");
                longBoxedWriter.write(obj, true, gen, this);
                gen.writeEndObjectRaw();
            } else {
                // Bare value path. Caller's writeImpl wrapper restores outer state on exit.
                gen.resetForBridgeAtValueSlot();
                longBoxedWriter.write(obj, false, gen, this);
            }
            return;
        }

        if (obj instanceof Integer) {
            int val = (Integer) obj;
            // SMALL_INT_STRINGS fast path — precomputed cache, faster than the digit-pair
            // algorithm for tiny Integer values. Also used by writeField + writePrimitiveFieldDirect.
            if (val >= SMALL_INT_LOW && val <= SMALL_INT_HIGH) {
                out.write(SMALL_INT_STRINGS[val - SMALL_INT_LOW]);
            } else {
                gen.writeIntRaw(val);  // non-small-int Integer — digit-pair, no allocation
            }
            return;
        }

        // NaN/Infinity policy gate — emits "null" when the writer disallows NaN/Inf
        // literals, matching the legacy behavior at this branch.
        if (!isNanInfinityAllowed()) {
            if (obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj))) {
                gen.writeNullRaw();
                return;
            }
            if (obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj))) {
                gen.writeNullRaw();
                return;
            }
        }

        // Dispatch on runtime type; each path goes through a state-machine-free raw
        // emit helper on gen so per-call cost is identical to the legacy direct out.write
        // path (and strictly better for Long where the legacy path allocated a String
        // via Long.toString).
        if (obj instanceof Long) {
            gen.writeLongRaw((Long) obj);
        } else if (obj instanceof Boolean) {
            gen.writeBooleanRaw((Boolean) obj);
        } else if (obj instanceof Double) {
            gen.writeDoubleRaw((Double) obj);
        } else if (obj instanceof Float) {
            gen.writeFloatRaw((Float) obj);
        } else if (obj instanceof Short) {
            gen.writeIntRaw(((Short) obj).intValue());
        } else if (obj instanceof Byte) {
            gen.writeIntRaw(((Byte) obj).intValue());
        } else {
            // Safety net for any unexpected runtime type that slips past the callers'
            // gating. Preserves legacy obj.toString() behavior.
            out.write(obj.toString());
        }
    }

    /**
     * Emit a Java {@code Object[]} (including reference-type arrays like {@code Integer[]},
     * {@code String[]}, {@code Foo[]}, etc.) as JSON. Dog-food path: structural emission
     * ({@code &#123;} / {@code &#125;} / {@code [} / {@code ]}, {@code @id} / {@code @type} /
     * {@code @items} prefix, leading/trailing body indents) goes through
     * {@link CharStreamGenerator} via {@code writeStartObjectRaw} / {@code writeEndObjectRaw} /
     * {@code writeStartArrayRaw} / {@code writeEndArrayRaw} / {@code writeFieldNameRaw} /
     * {@code beginInlineArrayBody}. The per-element loop keeps the legacy specialized
     * fast paths ({@code writePrimitive}, {@code writeStringValue}, {@code writeImpl})
     * for polymorphic value emission — these are bracketed by {@code snapshotForExternalValue}
     * / {@code restoreAfterExternalValue} when they internally reset gen state (the
     * Long-wrap branch of writePrimitive and writeStringValue), so the array body's
     * structural state ({@code FRAME_ARRAY_AFTER_VALUE} at body depth) is preserved
     * across the iteration. {@code this.depth} is temporarily synced to the array body
     * depth so the legacy {@code newLine()} emissions between elements indent correctly.
     */
    private void writeObjectArray(final Object[] array, final Class<?> arrayType, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final int len = array.length;
        final boolean referenced = cycleSupport && objsReferenced.containsKey(array);
        final boolean typeWritten = showType && !(arrayType.equals(Object[].class));
        final boolean wrapped = typeWritten || referenced;

        // Sync gen state to a clean value-slot at the current indent depth.
        gen.resetForBridgeAtValueSlot(this.depth);

        if (wrapped) {
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, getIdInt(array));
            }
            if (typeWritten) {
                String alias = writeOptions.getTypeNameAlias(arrayType.getName());
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            gen.writeFieldNameRaw(itemsPrefix);
        }

        if (len == 0) {
            gen.writeStartArrayRaw();
            gen.writeEndArrayRaw();
            if (wrapped) {
                gen.writeEndObjectRaw();
            }
            return;
        }

        gen.writeStartArrayRaw();
        // Sync this.depth to the array body depth so legacy newLine() inside the loop
        // indents at the correct depth. Restored before writeEndArrayRaw fires its own
        // trailing indent. {@code bodyDepth} captures the same value as a final local so
        // depth-restore calls inside the loop don't depend on the {@code this.depth}
        // field — first step toward eliminating that field entirely.
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + (wrapped ? 2 : 1);
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();   // emit leading body indent, setTop=FRAME_ARRAY_AFTER_VALUE

        final int lenMinus1 = len - 1;
        final Class<?> componentClass = arrayType.getComponentType();
        final Writer output = this.out;

        // Each iteration emits one element + an optional separator. Paths that internally
        // reset gen.depth to 0 (writeStringValue, writePrimitive's Long-wrap branch, and
        // writeArrayElementIfMatching → writeCustom's new-API dispatch) require a
        // lightweight depth restore — they operate at depth 0 and don't touch the array
        // body's contextStack entry, so just restoring gen.depth to bodyDepth is
        // sufficient. writeImpl has its own snapshot/restore wrapper that restores both
        // depth and stack. writePrimitive's non-Long-wrap paths and the null-literal
        // write don't touch gen state at all.
        for (int i = 0; i < len; i++) {
            final Object value = array[i];

            if (value == null) {
                output.write("null");
            } else if ((value instanceof Boolean || value instanceof Double ||
                         value instanceof Integer || value instanceof Float ||
                         value instanceof Short || value instanceof Byte) &&
                        !isForceType(value.getClass(), componentClass)) {
                writePrimitive(value, false);   // state-machine-free helpers, no restore needed
            } else if (value instanceof Long && !isForceType(Long.class, componentClass)) {
                // showType = writeLongsAsStrings. When true → Long-wrap's wrapped path
                // (writeStartObjectRaw + ... + writeEndObjectRaw, no reset since chunk-8).
                // When false → falls through to writeLongRaw (state-machine-free). Neither
                // path resets gen state, so no restoreDepth needed.
                writePrimitive(value, writeLongsAsStrings);
            } else if (value instanceof String && !isForceType(String.class, componentClass)) {
                writeStringValue((String) value);   // state-machine-free; no restore needed
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (!writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                    writeImpl(value, forceType);   // wrapper handles full restore
                } else {
                    gen.restoreDepthAfterExternalValue(bodyDepth);   // writeCustom new-API reset gen.depth=0
                }
            }

            if (i != lenMinus1) {
                output.write(',');
                gen.writeNewlineIndent();
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();   // emits trailing indent + ']'
        if (wrapped) {
            gen.writeEndObjectRaw();
        }
    }

    /**
     * Emit a Java primitive-array (one of {@code boolean[] / byte[] / short[] / int[] /
     * long[] / float[] / double[]}) as JSON. Dog-food path: ALL structural emission
     * ({@code &#123;}, {@code &#125;}, {@code [}, {@code ]}, {@code @id} / {@code @type}
     * / {@code @items} prefixes, and pretty-print whitespace) goes through
     * {@link CharStreamGenerator} via the public {@code writeStartObject} /
     * {@code writeEndObject} / {@code writeStartArray} / {@code writeEndArray} API. The
     * inner per-element loop (the {@code 1,2,3} flat-pack body) stays state-machine-free
     * via the {@code writeXxxRaw} helpers + manual {@code ','} separators, bracketed by
     * {@link CharStreamGenerator#beginInlineArrayBody()} (sets up the array body's
     * leading indent + flips state to {@code FRAME_ARRAY_AFTER_VALUE} so
     * {@code writeEndArray} emits the correct trailing indent).
     * <p>
     * {@code itemsPrefix} (the precomputed {@code "@items":} / {@code "@e":} /
     * {@code $items:} / {@code $e:} string per the writer's meta-key variant) is emitted
     * through {@link CharStreamGenerator#writeFieldNameRaw(String)} — fast path that skips
     * the per-call key-quoting decision while still engaging gen's state machine for
     * auto-separator + auto-indent.
     */
    private void writePrimitiveArray(final Object array, final Class<?> arrayType, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final int len = ArrayUtilities.getLength(array);
        final boolean referenced = cycleSupport && objsReferenced.containsKey(array);
        final boolean typeWritten = showType;  // Primitive arrays are never Object[], type always written when showType
        final boolean wrapped = typeWritten || referenced;

        // Sync gen state to a clean value-slot at the current depth. Required because the
        // writeImpl wrapper doesn't reset (so non-migrated dispatch paths don't pay for
        // the reset); each migrated method owns its own state setup.
        gen.resetForBridgeAtValueSlot(this.depth);

        if (wrapped) {
            // gen is now at FRAME_ROOT_EMPTY @ this.depth with suppressNextIndent=true —
            // startValueContext + emitIndent would no-op. Use the Raw fast path to skip
            // the switch/markValue overhead.
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, getIdInt(array));
            }
            if (typeWritten) {
                String alias = writeOptions.getTypeNameAlias(arrayType.getName());
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            gen.writeFieldNameRaw(itemsPrefix);
        }

        // char[] is emitted as a single quoted string element (not flat-pack of chars).
        // Use gen.writeString from FRAME_ARRAY_EMPTY so no leading separator is emitted —
        // avoids the snapshot/restore overhead that calling JsonWriter.writeStringValue
        // from FRAME_ARRAY_AFTER_VALUE would require.
        if (arrayType == char[].class) {
            gen.writeStartArrayRaw();
            if (len > 0) {
                gen.writeString(new String((char[]) array));
            }
            gen.writeEndArrayRaw();
            if (wrapped) {
                gen.writeEndObjectRaw();
            }
            return;
        }

        if (len == 0) {
            gen.writeStartArrayRaw();
            gen.writeEndArrayRaw();
            if (wrapped) {
                gen.writeEndObjectRaw();
            }
            return;
        }

        gen.writeStartArrayRaw();
        gen.beginInlineArrayBody();

        final int lenMinus1 = len - 1;
        PrimitiveArrayHandler handler = PRIM_ARRAY_WRITERS.getByClass(arrayType);
        if (handler != null) {
            handler.write(this, array, lenMinus1);
        }

        gen.writeEndArrayRaw();
        if (wrapped) {
            gen.writeEndObjectRaw();
        }
    }

    private void writeBooleanArray(boolean[] booleans, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            output.write(booleans[i] ? "true," : "false,");
        }
        output.write(Boolean.toString(booleans[lenMinus1]));
    }

    private void writeDoubleArray(double[] doubles, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            doubleWriter.write(doubles[i], false, output, this);
            output.write(',');
        }
        doubleWriter.write(doubles[lenMinus1], false, output, this);
    }

    private void writeFloatArray(float[] floats, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            floatWriter.write(floats[i], false, output, this);
            output.write(',');
        }
        floatWriter.write(floats[lenMinus1], false, output, this);
    }

    private void writeLongArray(long[] longs, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            longPrimitiveWriter.write(longs[i], false, output, this);
            output.write(',');
        }
        longPrimitiveWriter.write(longs[lenMinus1], false, output, this);
    }

    private void writeIntArray(int[] ints, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            gen.writeIntRaw(ints[i]);
            output.write(',');
        }
        gen.writeIntRaw(ints[lenMinus1]);
    }

    private void writeShortArray(short[] shorts, int lenMinus1) throws IOException {
        final Writer output = this.out;
        for (int i = 0; i < lenMinus1; i++) {
            gen.writeIntRaw(shorts[i]);
            output.write(',');
        }
        gen.writeIntRaw(shorts[lenMinus1]);
    }

    private void writeByteArray(byte[] bytes, int lenMinus1) throws IOException {
        final Writer output = this.out;
        final Object[] byteStrs = byteStrings;
        for (int i = 0; i < lenMinus1; i++) {
            output.write((char[]) byteStrs[bytes[i] + 128]);
            output.write(',');
        }
        output.write((char[]) byteStrs[bytes[lenMinus1] + 128]);
    }

    /**
     * Emit a Java {@link Collection} as JSON. Dog-food path — structural emission
     * ({@code &#123;}/{@code &#125;}/{@code [}/{@code ]} brackets, {@code @id}/{@code @type}/
     * {@code @items} prefixes, leading/trailing body indents) goes through
     * {@link CharStreamGenerator}'s Raw structural-token family. The per-element loop keeps
     * the legacy {@link #writeCollectionElement(Object)} dispatch — each element is
     * bracketed by a lightweight {@link CharStreamGenerator#restoreDepthAfterExternalValue(int)}
     * call since some element paths (writeStringValue, writePrimitive's Long-wrap,
     * writeUsingCustomWriter's new-API dispatch) internally reset gen.depth=0.
     * <p>
     * Note: empty wrapped collection emits {@code &#123;"@type":"...","@id":N&#125;} with
     * NO trailing {@code "@items":[]} field — matches legacy behavior (differs from
     * {@code writePrimitiveArray}/{@code writeObjectArray} which DO emit
     * {@code "@items":[]} for the empty case).
     */
    private void writeCollection(Collection<?> col, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final boolean referenced = cycleSupport && this.objsReferenced.containsKey(col);
        final boolean isEmpty = col.isEmpty();
        final boolean wrapped = referenced || showType;

        gen.resetForBridgeAtValueSlot(this.depth);

        if (wrapped) {
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, getIdInt(col));
            }
            if (showType) {
                String alias = writeOptions.getTypeNameAlias(getTypeNameForOutput(col));
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
        }

        if (isEmpty) {
            if (wrapped) {
                gen.writeEndObjectRaw();
            } else {
                gen.writeStartArrayRaw();
                gen.writeEndArrayRaw();
            }
            return;
        }

        if (wrapped) {
            gen.writeFieldNameRaw(itemsPrefix);
        }
        gen.writeStartArrayRaw();
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + (wrapped ? 2 : 1);
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();

        final Writer output = this.out;
        // Collection is non-empty (isEmpty path returned earlier), so peel off the first
        // element + restore, then loop with the separator BEFORE each subsequent element.
        // Avoids the per-iteration "is this the first element?" check.
        if (col instanceof List && col instanceof RandomAccess) {
            // Indexed loop avoids Iterator allocation for ArrayList and similar
            List<?> list = (List<?>) col;
            int size = list.size();
            writeCollectionElement(list.get(0));
            gen.restoreDepthAfterExternalValue(bodyDepth);
            for (int idx = 1; idx < size; idx++) {
                output.write(',');
                gen.writeNewlineIndent();
                writeCollectionElement(list.get(idx));
                gen.restoreDepthAfterExternalValue(bodyDepth);
            }
        } else {
            Iterator<?> it = col.iterator();
            writeCollectionElement(it.next());
            gen.restoreDepthAfterExternalValue(bodyDepth);
            while (it.hasNext()) {
                output.write(',');
                gen.writeNewlineIndent();
                writeCollectionElement(it.next());
                gen.restoreDepthAfterExternalValue(bodyDepth);
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();
        if (wrapped) {
            gen.writeEndObjectRaw();
        }
    }

    /**
     * Determines the type name to write for an object, preferring preserved typeString
     * over actual class name for middleware safety.
     *
     * This handles the case where JSON is parsed on a system without the original class
     * (e.g., com.example.House), stored as a fallback type (LinkedHashMap), and needs to
     * be re-serialized preserving the original @type for downstream systems.
     *
     * @param obj the object to get the type name for
     * @return the type name to write in the JSON @type field
     */
    private String getTypeNameForOutput(Object obj) {
        // Check if this is a JsonObject with a preserved typeString (middleware case)
        if (obj instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) obj;
            String typeString = jsonObj.getTypeString();

            // If typeString exists and differs from actual class, use it
            // This handles the middleware case where class wasn't available
            if (typeString != null &&
                !typeString.isEmpty() &&
                !typeString.equals(obj.getClass().getName())) {
                return typeString;  // Use preserved original @type
            }
        }

        // Normal case: use actual class name
        return obj.getClass().getName();
    }

    /**
     * Emit a {@link JsonObject} that represents an Object array (the toMaps / direct
     * JsonObject form). Same structural shape as {@link #writeObjectArray(Object[], Class, boolean)};
     * the per-element dispatch has a slightly different priority order (try writeArrayElement
     * If matching custom writer first, then check char/String/Boolean/Long/Double, then
     * fall through to writeImpl) inherited from the legacy implementation.
     */
    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        Object[] items = jObj.getItems();
        int len = items != null ? items.length : 0;
        Class<?> arrayClass;
        Class<?> jsonObjectType = jObj.getRawType();

        if (jsonObjectType == null || Object[].class.equals(jsonObjectType)) {
            arrayClass = Object[].class;
        } else {
            arrayClass = jsonObjectType;
        }

        final boolean isObjectArray = Object[].class == arrayClass;
        final Class<?> componentClass = arrayClass.getComponentType();
        final boolean referenced = adjustIfReferenced(jObj);
        final boolean typeWritten = showType && !isObjectArray;
        final boolean wrapped = typeWritten || referenced;

        gen.resetForBridgeAtValueSlot(this.depth);

        if (wrapped) {
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, jObj.id);
            }
            if (typeWritten) {
                String alias = writeOptions.getTypeNameAlias(arrayClass.getName());
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            gen.writeFieldNameRaw(itemsPrefix);
        }

        if (len == 0) {
            gen.writeStartArrayRaw();
            gen.writeEndArrayRaw();
            if (wrapped) {
                gen.writeEndObjectRaw();
            }
            return;
        }

        gen.writeStartArrayRaw();
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + (wrapped ? 2 : 1);
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();

        final Writer output = this.out;
        final int lenMinus1 = len - 1;
        for (int i = 0; i < len; i++) {
            final Object value = items[i];

            if (value == null) {
                output.write("null");
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                    gen.restoreDepthAfterExternalValue(bodyDepth);
                } else if (Character.class == componentClass || char.class == componentClass) {
                    writeStringValue((String) value);   // state-machine-free; no restore needed
                } else if (value instanceof String) {
                    writeStringValue((String) value);   // state-machine-free; no restore needed
                } else if (value instanceof Boolean || value instanceof Long || value instanceof Double) {
                    writePrimitive(value, forceType);
                    // writePrimitive's Long-wrap bare-value path (showType=false +
                    // writeLongsAsStrings + Long value) still resets gen state to
                    // FRAME_ROOT_EMPTY at depth=0 (preserves custom-writer compat for
                    // LongWriter overrides). Restore depth unconditionally — a no-op for
                    // the wrapped path / state-machine-free paths, correct for the bare
                    // Long-as-string path.
                    gen.restoreDepthAfterExternalValue(bodyDepth);
                } else {
                    writeImpl(value, forceType);   // wrapper handles full restore
                }
            }

            if (i != lenMinus1) {
                output.write(',');
                gen.writeNewlineIndent();
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();
        if (wrapped) {
            gen.writeEndObjectRaw();
        }
    }

    /**
     * Emit a {@link JsonObject} that represents a {@link Collection} during streaming
     * (toMaps / direct JsonObject input). Same structural shape as
     * {@link #writeCollection(Collection, boolean)} — see that method's javadoc for the
     * dog-food details. Empty wrapped path emits {@code &#123;"@type":"...","@id":N&#125;}
     * (no trailing {@code "@items":[]}); empty unwrapped emits {@code &#123;&#125;}
     * (because the legacy code unconditionally wraps when {@code len == 0}, preserved here).
     */
    private void writeJsonObjectCollection(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        Class<?> colClass = jObj.getRawType();
        final boolean referenced = adjustIfReferenced(jObj);
        Object[] items = jObj.getItems();
        final int len = items != null ? items.length : 0;
        final boolean isEmpty = len == 0;
        // Note: legacy emits '{' even when !showType && !referenced && isEmpty (i.e., emits
        // '{}' for an unwrapped empty JsonObject-collection). Preserved by treating the
        // empty case as wrapped for emission purposes.
        final boolean wrapped = referenced || showType || isEmpty;

        gen.resetForBridgeAtValueSlot(this.depth);

        if (wrapped) {
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, (int) jObj.getId());
            }
            if (showType) {
                String alias = writeOptions.getTypeNameAlias(colClass.getName());
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
        }

        if (isEmpty) {
            // Wrapped (or always wrapped per legacy) — close the object body. No @items field.
            gen.writeEndObjectRaw();
            return;
        }

        if (referenced || showType) {
            gen.writeFieldNameRaw(itemsPrefix);
        }
        gen.writeStartArrayRaw();
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + ((referenced || showType) ? 2 : 1);
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();

        final Writer output = this.out;
        final int itemsLenMinus1 = len - 1;
        for (int i = 0; i < len; i++) {
            writeCollectionElement(items[i]);
            gen.restoreDepthAfterExternalValue(bodyDepth);
            if (i != itemsLenMinus1) {
                output.write(',');
                gen.writeNewlineIndent();
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();
        if (referenced || showType) {
            gen.writeEndObjectRaw();
        }
    }

    /**
     * Emit a JsonObject representing a Map in the {@code @keys}/{@code @items}-array form.
     * See {@link #writeMap(Map, boolean)} for the dog-food details. Note: if
     * {@code showType} is true but {@link #getTypeNameForOutput(Object)} returns null, the
     * type field is silently skipped (matches the legacy {@code emitIdAndTypeIfNeeded}
     * behavior).
     */
    private void writeJsonObjectMap(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final boolean referenced = adjustIfReferenced(jObj);

        gen.resetForBridgeAtValueSlot(this.depth);
        gen.writeStartObjectRaw();

        if (referenced) {
            gen.writeNumberField(idKey, (int) jObj.getId());
        }
        if (showType) {
            String type = getTypeNameForOutput(jObj);
            if (type != null) {
                String alias = writeOptions.getTypeNameAlias(type);
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            // else: type silently skipped — matches legacy emitIdAndTypeIfNeeded behavior
        }

        if (jObj.isEmpty()) {
            gen.writeEndObjectRaw();
            return;
        }

        writeMapToEnd(jObj, this.out);
    }

    /**
     * Emit a JsonObject-as-Map in the string-key {@code {"k":v,...}} form. JsonObject
     * counterpart to {@link #writeMapWithStringKeys(Map, boolean)}.
     */
    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }

        if (writeOptions.isForceMapOutputAsTwoArrays()) {
            return false;
        }

        boolean keysAreStrings = ensureJsonPrimitiveKeys(jObj);
        boolean canStringify = !keysAreStrings && stringifyMapKeys && canStringifyMapKeys(jObj);

        if (!keysAreStrings && !canStringify) {
            return false;
        }

        final boolean referenced = adjustIfReferenced(jObj);

        gen.resetForBridgeAtValueSlot(this.depth);
        gen.writeStartObjectRaw();

        if (referenced) {
            gen.writeNumberField(idKey, (int) jObj.getId());
        }
        if (showType) {
            String type = getTypeNameForOutput(jObj);
            if (type != null) {
                String alias = writeOptions.getTypeNameAlias(type);
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            // else: type silently skipped — matches legacy emitIdAndTypeIfNeeded behavior
        }

        if (jObj.isEmpty()) {
            gen.writeEndObjectRaw();
            return true;
        }

        if (canStringify) {
            return writeStringifiedMapBody(jObj.entrySet().iterator());
        }
        return writeMapBody(jObj);
    }

    /**
     * Write fields of a JsonObject representing a POJO. Dog-food path — outer {} via
     * {@link CharStreamGenerator#writeStartObjectRaw()} / {@link CharStreamGenerator#writeEndObjectRaw()};
     * the {@code @id} / {@code @type} fields via
     * {@link CharStreamGenerator#writeNumberField(String, int)} /
     * {@link CharStreamGenerator#writeStringFieldUnescaped(String, String)}; per-field
     * key emission via {@link CharStreamGenerator#writeFieldName(String)} (auto-comma +
     * indent, supports json5UnquotedKeys); per-value emission via the appropriate gen
     * scalar method or writeImpl for complex / BigDecimal / BigInteger paths.
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final boolean referenced = adjustIfReferenced(jObj);
        showType = showType && jObj.getType() != null;

        gen.resetForBridgeAtValueSlot(this.depth);
        gen.writeStartObjectRaw();

        if (referenced) {
            gen.writeNumberField(idKey, jObj.id);
        }

        Class<?> type = null;
        if (showType) {
            String alias = writeOptions.getTypeNameAlias(getTypeNameForOutput(jObj));
            gen.writeStringFieldUnescaped(typeKey, alias);
            type = jObj.getRawType();
        }

        if (jObj.isEmpty()) {
            gen.writeEndObjectRaw();
            return;
        }

        final int depthAtEntry = this.depth;
        this.depth = depthAtEntry + 1;   // inside object body

        Iterator<Map.Entry<Object, Object>> i = jObj.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<Object, Object> entry = i.next();
            if (skipNullFields && entry.getValue() == null) {
                continue;
            }

            final String fieldName = (String) entry.getKey();
            gen.writeFieldName(fieldName);   // auto-separator + indent + key (handles json5 / escaping)

            Object value = entry.getValue();
            if (value == null) {
                gen.writeNull();
            } else if (value instanceof BigDecimal || value instanceof BigInteger) {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            } else if (value instanceof Boolean) {
                gen.writeBoolean((Boolean) value);
            } else if (value instanceof Number) {
                gen.writeNumber(value.toString());   // emit the toString() form like legacy
            } else if (value instanceof String) {
                gen.writeString((String) value);
            } else if (value instanceof Character) {
                gen.writeString(String.valueOf(value));
            } else {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndObjectRaw();
    }

    private boolean adjustIfReferenced(JsonObject jObj) {
        int idx = cycleSupport ? objsReferenced.get(jObj) : 0;  // Returns 0 if not found
        if (!jObj.hasId() && idx > 0) {   // Referenced object that needs an ID copied to it.
            jObj.id = idx;
        }
        return idx > 0 && jObj.hasId();
    }

    // Hopefully this method goes away when the converters are done.
    // We're asking for a DeepDeclaredFields to check One Field Type
    // Its cached, but we shouldn't have to do that here.  I would think if its a known converter type
    // that we avoid this section all together.
    private boolean doesValueTypeMatchFieldType(Class<?> type, String fieldName, Object value) {
        if (type != null) {
            Map<String, Field> fieldMap = writeOptions.getDeepDeclaredFields(type);
            Field field = fieldMap.get(fieldName);
            return field != null && field.getType().equals(value.getClass());
        }
        return false;
    }

    /**
     * Emit a Java {@link Map} as JSON in the {@code @keys}/{@code @items}-array form
     * (used when keys aren't strings or the writer is forced to two-arrays mode). Dog-food
     * path — structural emission ({@code &#123;}/{@code &#125;}, the two {@code [}/{@code ]}
     * array bodies, {@code @id}/{@code @type}/{@code @keys}/{@code @items} prefixes) goes
     * through {@link CharStreamGenerator}'s Raw structural-token family + writeFieldNameRaw.
     * The per-element loops use legacy {@link #writeCollectionElement(Object)} dispatch with
     * a lightweight {@code gen.restoreDepthAfterExternalValue(bodyDepth)} after each call.
     */
    private void writeMap(Map map, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final boolean referenced = cycleSupport && this.objsReferenced.containsKey(map);

        gen.resetForBridgeAtValueSlot(this.depth);
        gen.writeStartObjectRaw();

        if (referenced) {
            gen.writeNumberField(idKey, getIdInt(map));
        }
        if (showType) {
            String alias = writeOptions.getTypeNameAlias(getTypeNameForOutput(map));
            gen.writeStringFieldUnescaped(typeKey, alias);
        }

        if (map.isEmpty()) {
            gen.writeEndObjectRaw();
            return;
        }

        writeMapToEnd(map, this.out);
    }

    /**
     * Emit a Map's two-array body ({@code "@keys":[...]}, {@code "@items":[...]}) and the
     * closing {@code &#125;}. Caller has already opened the outer object via
     * {@code gen.writeStartObjectRaw()} + any {@code @id}/{@code @type} fields. Each
     * per-element call goes through {@link #writeCollectionElement(Object)} with a
     * lightweight depth restore — same pattern as the array migrations.
     */
    private void writeMapToEnd(Map map, Writer output) throws IOException {
        // Save current element type (Map value type) and switch to key type for @keys array
        final Class<?> savedValueType = declaredElementType;
        final int depthAtEntry = this.depth;
        // Both the @keys and @items array bodies sit at the same depth — inside the outer
        // object body + inside one of the two array bodies.
        final int bodyDepth = depthAtEntry + 2;

        // @keys array
        gen.writeFieldNameRaw(keysPrefix);
        gen.writeStartArrayRaw();
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();

        // Map is non-empty (caller's isEmpty path returned earlier), so the iterators
        // each have at least one element — peel off the first element + restore, then
        // loop with the separator BEFORE each subsequent element. Avoids the per-iteration
        // "is this the first element?" check.
        Iterator<?> i = map.keySet().iterator();
        declaredElementType = declaredKeyType;
        writeCollectionElement(i.next());
        gen.restoreDepthAfterExternalValue(bodyDepth);
        while (i.hasNext()) {
            output.write(',');
            gen.writeNewlineIndent();
            writeCollectionElement(i.next());
            gen.restoreDepthAfterExternalValue(bodyDepth);
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();

        // @items array
        gen.writeFieldNameRaw(itemsPrefix);
        gen.writeStartArrayRaw();
        this.depth = bodyDepth;
        gen.beginInlineArrayBody();

        i = map.values().iterator();
        declaredElementType = savedValueType;
        writeCollectionElement(i.next());
        gen.restoreDepthAfterExternalValue(bodyDepth);
        while (i.hasNext()) {
            output.write(',');
            gen.writeNewlineIndent();
            writeCollectionElement(i.next());
            gen.restoreDepthAfterExternalValue(bodyDepth);
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();

        // Close the object body that the caller opened via gen.writeStartObjectRaw.
        gen.writeEndObjectRaw();
    }

    /**
     * Emit a Java Map in the string-key {@code {"k":v,...}} form (the standard JSON
     * representation, when keys are Strings or can be stringified). Dog-food path —
     * outer {@code {}} via {@link CharStreamGenerator#writeStartObjectRaw()} /
     * {@link CharStreamGenerator#writeEndObjectRaw()}; @id / @type via gen field helpers;
     * body via {@link #writeMapBody(Iterator)} or {@link #writeStringifiedMapBody(Iterator)}.
     */
    private boolean writeMapWithStringKeys(Map map, boolean showType) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        if (writeOptions.isForceMapOutputAsTwoArrays()) {
            return false;
        }

        // Determine if keys can be written as JSON object keys (strings)
        final boolean keysKnownString = declaredKeyType == String.class;
        final boolean keysAreStrings = keysKnownString || ensureJsonPrimitiveKeys(map);
        final boolean canStringify = !keysAreStrings && stringifyMapKeys && canStringifyMapKeys(map);

        if (!keysAreStrings && !canStringify) {
            // Log a one-time deprecation warning if the keys COULD be stringified but the option is off
            if (!stringifyMapKeys && !map.isEmpty() && canStringifyMapKeys(map)
                    && stringifyMapKeysWarned.compareAndSet(false, true)) {
                LOG.warning("json-io: Map with non-String keys (e.g., " + map.keySet().iterator().next().getClass().getSimpleName()
                        + ") written using @keys/@items format. Use WriteOptionsBuilder.stringifyMapKeys(true) or .standardJson() "
                        + "to write as standard JSON (e.g., {\"100\": value}). This will become the default in json-io 5.0.");
            }
            return false;  // Fall back to @keys/@items
        }

        final boolean referenced = cycleSupport && this.objsReferenced.containsKey(map);

        gen.resetForBridgeAtValueSlot(this.depth);
        gen.writeStartObjectRaw();

        if (referenced) {
            gen.writeNumberField(idKey, getIdInt(map));
        }
        if (showType) {
            String alias = writeOptions.getTypeNameAlias(getTypeNameForOutput(map));
            gen.writeStringFieldUnescaped(typeKey, alias);
        }

        if (map.isEmpty()) {
            gen.writeEndObjectRaw();
            return true;
        }

        if (canStringify) {
            return writeStringifiedMapBody(map.entrySet().iterator());
        }
        return writeMapBody(map.entrySet().iterator());
    }

    /**
     * Write the body of a string-key Map (the {@code {"k1":v1,"k2":v2}} form). Caller has
     * already opened the outer object via {@code gen.writeStartObjectRaw()} +
     * any {@code @id}/{@code @type} fields. Each entry emits via
     * {@link CharStreamGenerator#writeFieldName(String)} (auto-comma + indent + json5-aware
     * key emission), then {@link #writeCollectionElement(Object)} for the value followed
     * by a lightweight depth restore + {@code markValue} to transition the outer frame
     * from {@code FRAME_OBJECT_AFTER_FIELD} to {@code FRAME_OBJECT_AFTER_VALUE}. Closes
     * with {@code gen.writeEndObjectRaw()}.
     */
    private boolean writeMapBody(final Iterator i) throws IOException {
        final boolean skipNulls = skipNullFields;
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + 1;   // inside the object body
        this.depth = bodyDepth;   // newLine() in nested writeImpl uses correct depth

        while (i.hasNext()) {
            Entry att2value = (Entry) i.next();
            Object value = att2value.getValue();
            if (skipNulls && value == null) {
                continue;
            }
            gen.writeFieldName((String) att2value.getKey());
            writeCollectionElement(value);
            gen.restoreDepthAfterExternalValue(bodyDepth);
            gen.markValue();
        }

        this.depth = depthAtEntry;
        gen.writeEndObjectRaw();
        return true;
    }

    /**
     * JsonObject overload of {@link #writeMapBody(Iterator)}. Iterates via the fast
     * {@code fastKeyAt}/{@code fastValueAt} primitives.
     */
    private boolean writeMapBody(final JsonObject jObj) throws IOException {
        final boolean skipNulls = skipNullFields;
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + 1;
        this.depth = bodyDepth;
        final int len = jObj.fastEntryCount();

        for (int idx = 0; idx < len; idx++) {
            Object value = jObj.fastValueAt(idx);
            if (skipNulls && value == null) {
                continue;
            }
            gen.writeFieldName((String) jObj.fastKeyAt(idx));
            writeCollectionElement(value);
            gen.restoreDepthAfterExternalValue(bodyDepth);
            gen.markValue();
        }

        this.depth = depthAtEntry;
        gen.writeEndObjectRaw();
        return true;
    }

    /**
     * Ensure that all keys within the Map are String instances
     *
     * @param map Map to inspect that all keys are primitive.  This allows the output JSON
     *            to be optimized into {"key1":value1, "key2": value2} format if all the
     *            keys of the Map are Strings.  If not, then a Map is written as two
     *            arrays, a @keys array and an @items array.  This allows support for Maps
     *            with non-String keys.
     */
    public static boolean ensureJsonPrimitiveKeys(Map map) {
        for (Object o : map.keySet()) {
            if (!(o instanceof String)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if all keys in the Map can be stringified via Converter (bidirectional String conversion).
     * This requires both key→String and String→key conversions to be supported, ensuring round-trip fidelity.
     * Null keys cause this to return false (fall back to @keys/@items to avoid "null" ambiguity).
     */
    private boolean canStringifyMapKeys(Map map) {
        if (declaredKeyType != null && declaredKeyType != Object.class) {
            // Known declared key type — check it once
            return Converter.isConversionSupportedFor(declaredKeyType, String.class)
                    && Converter.isConversionSupportedFor(String.class, declaredKeyType);
        }
        // Unknown declared type — check each actual key
        for (Object key : map.keySet()) {
            if (key == null) {
                return false;
            }
            Class<?> keyClass = key.getClass();
            if (!(Converter.isConversionSupportedFor(keyClass, String.class)
                    && Converter.isConversionSupportedFor(String.class, keyClass))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Write map entries with non-String keys converted to Strings via Converter.
     * Same structure as writeMapBody() but keys are stringified instead of cast to String.
     */
    /**
     * Write the body of a Map whose keys are stringified via {@code Converter.convert}.
     * Same pattern as {@link #writeMapBody(Iterator)} but each key is first converted
     * to its String form via the framework's bidirectional converter. Used when the Map
     * has non-String keys but {@code stringifyMapKeys} is enabled.
     */
    private boolean writeStringifiedMapBody(final Iterator i) throws IOException {
        final boolean skipNulls = skipNullFields;
        final int depthAtEntry = this.depth;
        final int bodyDepth = depthAtEntry + 1;
        this.depth = bodyDepth;

        while (i.hasNext()) {
            Entry att2value = (Entry) i.next();
            Object value = att2value.getValue();
            if (skipNulls && value == null) {
                continue;
            }
            Object key = att2value.getKey();
            String keyStr = (key == null) ? "null" : Converter.convert(key, String.class);
            gen.writeFieldName(keyStr);
            writeCollectionElement(value);
            gen.restoreDepthAfterExternalValue(bodyDepth);
            gen.markValue();
        }

        this.depth = depthAtEntry;
        gen.writeEndObjectRaw();
        return true;
    }

    /**
     * Write an element that is contained in some type of Collection or Map.
     *
     * @param o Collection element to output in JSON format.
     * @throws IOException if an error occurs writing to the output stream.
     */
    private void writeCollectionElement(Object o) throws IOException {
        if (o == null) {
            out.write("null");
        } else if (o instanceof Boolean || o instanceof Double) {
            writePrimitive(o, false);
        } else if (o instanceof Long) {
            writePrimitive(o, writeLongsAsStrings);
        } else if (o instanceof String) {   // Never do an @ref to a String (they are treated as logical primitives and intern'ed on read)
            writeStringValue((String) o);
        } else if (o instanceof Integer || o instanceof Float || o instanceof Short || o instanceof Byte) {
            // Fast path for remaining numeric wrappers when @type is not needed.
            // These bypass the writeImpl() → writeCustom() dispatch chain.
            if (!shouldShowTypeForElement(o.getClass())) {
                writePrimitive(o, false);
            } else {
                writeImpl(o, true);
            }
        } else if (neverShowingType && !forceElementShowType && ClassUtilities.isPrimitive(o.getClass())) {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
        } else {
            // Use declaredElementType to determine if @type is needed - eliminates redundant @type
            // when element instance type == declared element type from field generic info
            boolean showType = shouldShowTypeForElement(o.getClass());

            // Short-circuit for POJO elements when cycleSupport is off: skip writeImpl's
            // null/security/primitive checks, activePath tracking, and try/finally overhead
            // and dispatch to writeObject directly. Custom writers and @IoValue annotations
            // are checked first to preserve correctness. This flattens the call stack from
            // writeCollectionElement → writeImpl → writeTypeCache → writeObject (4 levels)
            // to writeCollectionElement → writeObject (2 levels), giving the JIT better
            // inlining budget for the inner write loop.
            if (!cycleSupport && writeTypeCache.get(o.getClass()) == WriteType.POJO
                    && AnnotationResolver.getMetadata(o.getClass()).getValueMethod() == null) {
                // POJO short-circuit dispatches writeObject directly. writeObject no longer
                // resets gen state (uses writeStartObjectRaw which pushes without
                // overwriting the outer frame), so no caller-side snapshot/restore is
                // needed — the outer state at gen.depth is preserved across the call.
                // writeUsingCustomWriter (its writeCustom internal reset) operates at
                // gen.depth=0 and doesn't touch contextStack[outer-depth].
                if (!writeUsingCustomWriter(o, showType, out)) {
                    writeObject(o, showType, false);
                }
            } else {
                writeImpl(o, showType);
            }
        }
    }

    /**
     * Determines whether @type should be written for a collection/map element.
     * If declaredElementType is set (from a field with generic info like {@code List<Foo>}),
     * and the element's class exactly matches (==) the declared type, @type is not needed.
     *
     * This optimization works because JsonParser propagates element type context when
     * parsing @items arrays - see JsonParser.pushArrayFrame() which receives element type,
     * and pushNestedContainerFrame() which passes it to pushObjectFrame().
     *
     * @param elementClass the actual class of the element being written
     * @return true if @type should be written, false if it can be omitted
     */
    private boolean shouldShowTypeForElement(Class<?> elementClass) {
        // @IoShowType on the field forces type emission for all elements (unless primitive/native JSON type)
        if (forceElementShowType) {
            return !Primitives.isNativeJsonType(elementClass);
        }
        // If no declared element type context (raw collection), treat numeric primitives as safe to omit @type
        // in NEVER and MINIMAL_PLUS modes since they round-trip as Long/Double.
        if (declaredElementType == null) {
            if ((minimalPlusFormat || neverShowingType) && NUMERIC_PRIMITIVES_FOR_COMPACT.contains(elementClass)) {
                return false;
            }
            return true;
        }
        // Enums always need @type for proper deserialization (written as String, need type to convert back)
        if (elementClass.isEnum()) {
            return true;
        }
        // Must be exact match (==), not isAssignableFrom
        // This ensures the Resolver can instantiate the correct concrete type
        if (elementClass == declaredElementType) {
            return false;
        }
        // Minimal plus format: treat natural defaults as matching (e.g., ArrayList for List element type)
        if (minimalPlusFormat && NATURAL_DEFAULTS.getOrDefault(declaredElementType, Void.class) == elementClass) {
            return false;
        }
        // Minimal plus format: treat convertable types as matching (e.g., ZonedDateTime element in Temporal list)
        if (minimalPlusFormat && CONVERTABLE_TYPES.contains(declaredElementType) && CONVERTABLE_TYPES.contains(elementClass)) {
            return false;
        }
        // Numeric primitives (Byte/Short/Integer/Float) write as plain JSON numbers when element type is Object.
        // They round-trip as Long (for integers) or Double (for floats).
        // This applies to NEVER and MINIMAL_PLUS modes (not MINIMAL or ALWAYS).
        if ((minimalPlusFormat || neverShowingType) && declaredElementType == Object.class && NUMERIC_PRIMITIVES_FOR_COMPACT.contains(elementClass)) {
            return false;
        }
        return true;
    }

    private void writeEnumSet(final EnumSet<?> enumSet) throws IOException {
        final boolean referenced = cycleSupport && this.objsReferenced.containsKey(enumSet);

        gen.writeStartObjectRaw();
        if (referenced) {
            gen.writeNumberField(idKey, getIdInt(enumSet));
        }

        // Obtain the actual Enum class
        Class<?> enumClass = null;

        // Attempt to get the enum class from the 'elementType' field of EnumSet (cached)
        if (!enumSetFieldResolved) {
            enumSetElementTypeField = writeOptions.getDeepDeclaredFields(EnumSet.class).get("elementType");
            enumSetFieldResolved = true;
        }
        Field elementTypeField = enumSetElementTypeField;
        if (elementTypeField != null) {
            enumClass = (Class<?>) getValueByReflect(enumSet, elementTypeField);
            // Ensure we get the actual enum class, not an anonymous subclass
            Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(enumClass);
            enumClass = (actualEnumClass != null) ? actualEnumClass : enumClass;
        }

        // If we couldn't get it from 'elementType', try to get from the first enum constant
        if (enumClass == null) {
            if (!enumSet.isEmpty()) {
                Enum<?> e = enumSet.iterator().next();
                Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(e.getClass());
                enumClass = (actualEnumClass != null) ? actualEnumClass : e.getClass();
            } else {
                // EnumSet is empty; try to get the enum class from the complement
                EnumSet<?> complement = EnumSet.complementOf(enumSet);
                if (!complement.isEmpty()) {
                    Enum<?> e = complement.iterator().next();
                    Class<?> actualEnumClass = ClassUtilities.getClassIfEnum(e.getClass());
                    enumClass = (actualEnumClass != null) ? actualEnumClass : e.getClass();
                } else {
                    // Cannot determine the enum class; use a placeholder
                    enumClass = MetaUtils.Dumpty.class;
                }
            }
        }

        // Write the @type / @enum field with the actual enum class name.
        // The "old way" used @enum; the new way uses @type. Both are emitted via gen
        // through writeStringField (which engages the state machine for separator/indent).
        final String enumTypeFieldKey = writeOptions.isEnumSetWrittenOldWay() ? ENUM : TYPE;
        gen.writeStringField(enumTypeFieldKey, enumClass.getName());

        // @items field opens the element array. Track gen.depth for the inner field loop's
        // legacy newLine() emissions.
        gen.writeFieldName(ITEMS);
        gen.writeStartArrayRaw();
        if (enumSet.isEmpty()) {
            gen.writeEndArrayRaw();
            gen.writeEndObjectRaw();
            return;
        }

        final int depthAtEntry = this.depth;
        this.depth = depthAtEntry + 2;   // inside outer object body + inside @items array body
        gen.beginInlineArrayBody();

        boolean firstInSet = true;
        for (Enum<?> e : enumSet) {
            if (!firstInSet) {
                out.write(',');
                gen.writeNewlineIndent();
            }
            firstInSet = false;

            // Determine whether to write the full enum object or just the name
            List<WriteFieldPlan> mapOfFields = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, e.getClass());
            int enumFieldsCount = mapOfFields.size();

            if (enumFieldsCount <= 2) {
                // Write the enum name as a string (state-machine-free via writeStringValue).
                writeStringValue(e.name());
            } else {
                // Write the enum as a JSON object with its fields. Gen-driven structural
                // emission via writeStartObjectRaw + writeField loop + writeEndObjectRaw;
                // writeField's writeFieldNameRaw + value emission keeps gen state correct
                // throughout.
                gen.writeStartObjectRaw();
                for (int p = 0, pLen = mapOfFields.size(); p < pLen; p++) {
                    writeField(e, mapOfFields.get(p));
                }
                gen.writeEndObjectRaw();
            }
        }

        this.depth = depthAtEntry;
        gen.writeEndArrayRaw();
        gen.writeEndObjectRaw();
    }

    /**
     * @param obj      Object to be written in JSON format
     * @param showType boolean true means show the "@type" field, false
     *                 eliminates it.  Many times the type can be dropped because it can be
     *                 inferred from the field or array type.
     * @param bodyOnly write only the body of the object
     * @throws IOException if an error occurs writing to the output stream.
     */
    /**
     * Write a Java POJO as JSON. Dog-food path — outer {@code {/&#125;} via
     * {@link CharStreamGenerator#writeStartObjectRaw()} /
     * {@link CharStreamGenerator#writeEndObjectRaw()}; @id / @type via gen field-level
     * helpers; per-field emission via {@link #writeField(Object, WriteFieldPlan)} which
     * uses {@link CharStreamGenerator#writeFieldNameRaw(String)} for the precomputed key
     * emission. The {@code first}-flag tracking from the legacy code is no longer needed —
     * gen's state machine auto-emits the leading separator for subsequent fields. When
     * {@code bodyOnly} is true, the caller has already opened the outer {@code {} and is
     * responsible for closing it; this method emits only the field block inside.
     */
    public void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException {
        if (neverShowingType && !forceElementShowType) {
            showType = false;
        }
        final boolean referenced = cycleSupport && this.objsReferenced.containsKey(obj);
        final int depthAtEntry = this.depth;
        if (!bodyOnly) {
            // No resetForBridgeAtValueSlot here: writeStartObjectRaw pushes from the current
            // gen.depth without overwriting contextStack[gen.depth]. This preserves the
            // outer caller's frame state at gen.depth (e.g., the FRAME_OBJECT_AFTER_FIELD
            // set by a preceding writeFieldName in writeMapBody). On exit,
            // writeEndObjectRaw's pop + markValue transitions the outer frame from
            // FRAME_OBJECT_AFTER_FIELD to FRAME_OBJECT_AFTER_VALUE — matching what an
            // intervening writeImpl wrapper's snap+restore+markValue would do, but without
            // the snap/restore overhead.
            gen.writeStartObjectRaw();
            if (referenced) {
                gen.writeNumberField(idKey, getIdInt(obj));
            }
            if (showType) {
                String alias = writeOptions.getTypeNameAlias(obj.getClass().getName());
                gen.writeStringFieldUnescaped(typeKey, alias);
            }
            this.depth = depthAtEntry + 1;   // inside object body
        }

        List<WriteFieldPlan> accessors = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, obj.getClass());
        for (int i = 0, len = accessors.size(); i < len; i++) {
            writeField(obj, accessors.get(i));
        }

        // @IoAnyGetter — write extra fields from annotated method
        Method anyGetter = AnnotationResolver.getMetadata(obj.getClass()).getAnyGetterMethod();
        if (anyGetter != null) {
            writeAnyGetterFields(obj, anyGetter);
        }

        if (!bodyOnly) {
            this.depth = depthAtEntry;
            gen.writeEndObjectRaw();
        }
    }

    private Object getValueByReflect(Object obj, Field field) {
        // Fix unsafe reflection access - add comprehensive null and security checks
        if (field == null) {
            return null;
        }

        // Allow static field access even with null object
        if (obj == null && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
            return null;
        }

        try {
            // Security enhancement - only allow access to public fields or already accessible fields
            if (!field.isAccessible() && !java.lang.reflect.Modifier.isPublic(field.getModifiers())) {
                // Private/protected fields are not made accessible for security
                return null;
            }

            // Ensure field is accessible before attempting access (Java 8 compatible)
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }

            return field.get(obj);
        } catch (IllegalAccessException e) {
            // Field access denied - return null rather than exposing internal error
            return null;
        } catch (SecurityException e) {
            // Security manager denied access - fail safely
            return null;
        } catch (Exception e) {
            // Any other reflection-related exception - fail safely without exposing details
            return null;
        }
    }

    /**
     * Write one POJO field via gen-driven structural emission. Caller (writeObject) has
     * gen positioned inside the object body — the field name goes through
     * {@link CharStreamGenerator#writeFieldNameRaw(String)} (auto-comma + indent based on
     * gen state), the value through the appropriate scalar gen method or writeImpl for
     * the slow path. State-machine-free emissions (SMALL_INT_STRINGS / writeIntRaw /
     * writeLongRaw / writeBoolean toString) are followed by {@link CharStreamGenerator#markValue()}
     * to transition the outer frame from {@code FRAME_OBJECT_AFTER_FIELD} to
     * {@code FRAME_OBJECT_AFTER_VALUE} so the next field's writeFieldNameRaw emits the
     * correct separator.
     */
    private void writeField(Object obj, WriteFieldPlan plan) throws IOException {
        final Accessor accessor = plan.accessor();
        if (plan.enumPublicOnlySkipCandidate() && writeOptions.isEnumPublicFieldsOnly()) {
            return;
        }

        if (canWritePrimitiveFieldDirect(plan)) {
            gen.writeFieldNameRaw(plan.serializedKey());
            writePrimitiveFieldDirect(obj, plan);
            return;
        }

        Object o = accessor.retrieve(obj);

        if ((skipNullFields || plan.skipIfNull()) && o == null) {   // If skip null (global or per-field annotation), skip field
            return;
        }

        gen.writeFieldNameRaw(plan.serializedKey());

        if (o == null) {
            out.write("null");
            gen.markValue();
            return;
        }

        // Fast path for primitive/String field values: when no @IoShowType, no @IoFormat,
        // and the value type doesn't need @type (isForceType returns false), write the value
        // directly without going through writeImpl's full dispatch chain (security checks,
        // activePath tracking, custom writer lookup, @IoValue check, writeTypeCache switch).
        if (!plan.forceShowType() && !forceElementShowType && plan.formatPattern() == null) {
            Class<?> oClass = o.getClass();
            if (oClass == String.class) {
                // Use gen.writeString directly (NOT writeStringValue which resets gen.depth=0).
                // When this.depth=0 (e.g., inside writeEnumSet's enum-as-object block), the
                // object context lives in contextStack[0] — the reset would overwrite it.
                // gen.writeString starts from FRAME_OBJECT_AFTER_FIELD (set by the preceding
                // writeFieldNameRaw), emits the quoted string, and transitions to AFTER_VALUE.
                gen.writeString((String) o);
                return;
            }
            if (!isForceType(oClass, plan.effectiveDeclaredType())) {
                if (oClass == Integer.class) {
                    int val = (Integer) o;
                    if (val >= SMALL_INT_LOW && val <= SMALL_INT_HIGH) {
                        out.write(SMALL_INT_STRINGS[val - SMALL_INT_LOW]);
                    } else {
                        gen.writeIntRaw(val);
                    }
                    gen.markValue();
                    return;
                }
                if (oClass == Long.class && !writeLongsAsStrings) {
                    gen.writeLongRaw((Long) o);
                    gen.markValue();
                    return;
                }
                if (oClass == Boolean.class) {
                    out.write(((Boolean) o) ? "true" : "false");
                    gen.markValue();
                    return;
                }
                if (oClass == Double.class) {
                    writePrimitive(o, false);
                    gen.markValue();
                    return;
                }
            }
        }

        // Slow path: save/restore container state, call writeImpl for full dispatch.
        Class<?> type = plan.effectiveDeclaredType();
        Class<?> savedElementType = declaredElementType;
        Class<?> savedKeyType = declaredKeyType;
        boolean savedForceElementShowType = forceElementShowType;
        String savedFormatPattern = fieldFormatPattern;
        try {
            if (plan.applyDeclaredContainerTypes() && (o instanceof Collection || o instanceof Map)) {
                declaredKeyType = plan.declaredKeyType();
                declaredElementType = plan.declaredElementType();
            }
            if (plan.forceShowType()) {
                forceElementShowType = true;
            }
            fieldFormatPattern = plan.formatPattern();
            boolean showType = plan.forceShowType() || isForceType(o.getClass(), type);
            writeImpl(o, showType);   // wrapper's restore + markValue transitions outer frame
        } finally {
            declaredElementType = savedElementType;
            declaredKeyType = savedKeyType;
            forceElementShowType = savedForceElementShowType;
            fieldFormatPattern = savedFormatPattern;
        }
    }

    private boolean canWritePrimitiveFieldDirect(WriteFieldPlan plan) {
        byte primitiveKind = plan.primitiveWriteKind();
        return primitiveKind != WriteFieldPlan.PRIMITIVE_NONE
                && !plan.forceShowType()
                && !forceElementShowType
                && plan.formatPattern() == null
                && (primitiveKind != WriteFieldPlan.PRIMITIVE_LONG || !writeLongsAsStrings);
    }

    private void writePrimitiveFieldDirect(Object obj, WriteFieldPlan plan) throws IOException {
        Accessor accessor = plan.accessor();
        switch (plan.primitiveWriteKind()) {
            case WriteFieldPlan.PRIMITIVE_BOOLEAN:
                out.write(accessor.getBoolean(obj) ? "true" : "false");
                break;
            case WriteFieldPlan.PRIMITIVE_BYTE:
                gen.writeIntRaw(accessor.getByte(obj));
                break;
            case WriteFieldPlan.PRIMITIVE_CHAR:
                // Use gen.writeString directly (NOT writeStringValue) — see the analogous
                // String fast path in writeField for why this matters when this.depth=0.
                gen.writeString(String.valueOf(accessor.getChar(obj)));
                return;   // gen.writeString already transitioned state via markValue; skip the trailing markValue
            case WriteFieldPlan.PRIMITIVE_SHORT:
                gen.writeIntRaw(accessor.getShort(obj));
                break;
            case WriteFieldPlan.PRIMITIVE_INT:
                int intVal = accessor.getInt(obj);
                if (intVal >= SMALL_INT_LOW && intVal <= SMALL_INT_HIGH) {
                    out.write(SMALL_INT_STRINGS[intVal - SMALL_INT_LOW]);
                } else {
                    gen.writeIntRaw(intVal);
                }
                break;
            case WriteFieldPlan.PRIMITIVE_LONG:
                gen.writeLongRaw(accessor.getLong(obj));
                break;
            case WriteFieldPlan.PRIMITIVE_FLOAT:
                float floatVal = accessor.getFloat(obj);
                if (!isNanInfinityAllowed() && (Float.isNaN(floatVal) || Float.isInfinite(floatVal))) {
                    out.write("null");
                } else {
                    out.write(Float.toString(floatVal));
                }
                break;
            case WriteFieldPlan.PRIMITIVE_DOUBLE:
                double doubleVal = accessor.getDouble(obj);
                if (!isNanInfinityAllowed() && (Double.isNaN(doubleVal) || Double.isInfinite(doubleVal))) {
                    out.write("null");
                } else {
                    out.write(Double.toString(doubleVal));
                }
                break;
            default:
                throw new JsonIoException("Unsupported primitive field kind: " + plan.primitiveWriteKind());
        }
        // All branches emit state-machine-free; transition outer frame
        // FRAME_OBJECT_AFTER_FIELD -> FRAME_OBJECT_AFTER_VALUE so the next field's
        // writeFieldNameRaw emits the correct leading separator.
        gen.markValue();
    }

    /**
     * Write extra fields from an @IoAnyGetter method. The method returns a Map&lt;String, Object&gt;
     * whose entries are written as additional JSON fields after the regular declared fields.
     */
    @SuppressWarnings("unchecked")
    private void writeAnyGetterFields(Object obj, Method anyGetter) throws IOException {
        Map<String, Object> extras;
        try {
            extras = (Map<String, Object>) anyGetter.invoke(obj);
        } catch (Exception e) {
            throw new JsonIoException("Error invoking @IoAnyGetter method: " + anyGetter.getName(), e);
        }
        if (extras == null || extras.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            Object value = entry.getValue();
            if (skipNullFields && value == null) {
                continue;
            }
            gen.writeFieldName(entry.getKey());   // auto-separator + indent + key (json5-aware)
            if (value == null) {
                gen.writeNull();
            } else {
                writeImpl(value, isForceType(value.getClass(), Object.class));
            }
        }
    }

    private boolean isForceType(Class<?> objectClass, Class<?> declaredType) {
        // When no type is written we can check the Object itself not the declaration
        // Uses pre-fetched writeLongsAsStrings member variable
        final boolean objectClassIsLongWrittenAsString = (objectClass == Long.class || objectClass == long.class) && writeLongsAsStrings;
        final boolean declaredClassIsLongWrittenAsString = (declaredType == Long.class || declaredType == long.class) && writeLongsAsStrings;

        if (Primitives.isNativeJsonType(objectClass) && !objectClassIsLongWrittenAsString) {
            return false;
        }

        if (Primitives.isPrimitive(declaredType) && !declaredClassIsLongWrittenAsString) {
            return false;
        }

        if (neverShowingType && !forceElementShowType && Primitives.isPrimitive(objectClass) && !objectClassIsLongWrittenAsString) {
            return false;
        }

        if (alwaysShowingType) {
            return true;
        }

        if (objectClass == declaredType) {
            return false;
        }

        // Handle Long.class <-> long.class equivalence (autoboxing)
        if (objectClass == Long.class && declaredType == long.class) {
            return false;
        }

        // Numeric primitives (Byte/Short/Integer/Float) write as plain JSON numbers when declared type is Object.
        // They round-trip as Long (for integers) or Double (for floats).
        // This applies to NEVER and MINIMAL_PLUS modes (not MINIMAL or ALWAYS).
        if ((minimalPlusFormat || neverShowingType) && !forceElementShowType && declaredType == Object.class && NUMERIC_PRIMITIVES_FOR_COMPACT.contains(objectClass)) {
            return false;
        }

        // Minimal plus format: omit @type when the runtime type is the "natural default" for the declared type.
        // E.g., ArrayList for List, LinkedHashSet for Set, LinkedHashMap for Map.
        // The reader creates these same defaults via CollectionFactory/MapFactory when @type is absent.
        if (minimalPlusFormat && NATURAL_DEFAULTS.getOrDefault(declaredType, Void.class) == objectClass) {
            return false;
        }

        // Minimal plus format: omit @type when both the declared type and runtime type are "convertable" types
        // that have lossless String round-trips. The reader's Injector catches ClassCastException and calls
        // Converter.convert(value, fieldType) to reconstruct the correct type from the primitive form.
        if (minimalPlusFormat && CONVERTABLE_TYPES.contains(declaredType) && CONVERTABLE_TYPES.contains(objectClass)) {
            return false;
        }

        if (declaredType.isEnum() && declaredType.isAssignableFrom(objectClass)) {
            Class<?> enumClass = ClassUtilities.getClassIfEnum(objectClass);
            return !declaredType.equals(enumClass);
        }

        return true;
    }

    public void flush() {
        if (out != null) {
            IOUtilities.flush(out);
        }
    }

    public void close() {
        if (out != null) {
            IOUtilities.close(out);
        }
    }

    /**
     * Get the ID for an object. Returns the ID as an int (0 if not referenced).
     * For JsonObject instances, uses the stored id field.
     * For other objects, looks up in objsReferenced map.
     */
    private int getIdInt(Object o) {
        if (!cycleSupport) {
            return 0;
        }
        if (o instanceof JsonObject) {
            int id = ((JsonObject) o).id;
            if (id > 0) {
                return id;
            }
        }
        return this.objsReferenced.get(o);  // Returns 0 if not found
    }

    /**
     * Writes a quoted string without escape-scanning the content. Use for labels and inputs
     * known a-priori to be JSON-safe (no embedded {@code "}, {@code \\}, or control chars).
     *
     * @param writer Writer to which the quoted string is written
     * @param s      String to write \u2014 must be JSON-safe
     * @throws IOException if an error occurs writing to the output stream
     * @deprecated since 4.103.0; the implementation moved to {@code CharStreamGenerator}.
     *             Internal callers route there directly; external callers should switch to
     *             {@link JsonGenerator#writeString(String)} via {@link JsonIo#createGenerator}
     *             for the full state-machine-aware API, or accept the deprecation warning here.
     */
    @Deprecated
    public static void writeBasicString(final Writer writer, String s) throws IOException {
        CharStreamGenerator.writeBasicString(writer, s);
    }

    /**
     * Writes a JSON string value, properly escaped per JSON specifications. Uses the
     * default 1MB string-length limit.
     *
     * @param output The Writer to write to
     * @param s      The string to write as a JSON string value
     * @throws IOException If an I/O error occurs
     * @deprecated since 4.103.0; the implementation moved to {@code CharStreamGenerator}.
     *             See {@link #writeBasicString(Writer, String)} for migration notes.
     */
    @Deprecated
    public static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        CharStreamGenerator.writeJsonUtf8String(output, s);
    }

    /**
     * Writes a JSON string value, properly escaped per JSON specifications, with explicit
     * max-length cap.
     *
     * @param output          The Writer to write to
     * @param s               The string to write as a JSON string value
     * @param maxStringLength Maximum allowed string length
     * @throws IOException If an I/O error occurs
     * @deprecated since 4.103.0; the implementation moved to {@code CharStreamGenerator}.
     *             See {@link #writeBasicString(Writer, String)} for migration notes.
     */
    @Deprecated
    public static void writeJsonUtf8String(final Writer output, String s, int maxStringLength) throws IOException {
        CharStreamGenerator.writeJsonUtf8String(output, s, maxStringLength);
    }

    /**
     * Writes a string value at a value slot, delegating to {@link CharStreamGenerator#writeString(String)}
     * via the bridge-reset hook. The generator applies the same smart quote-style selection in
     * {@code json5SmartQuotes} mode that this method historically applied — picks single quotes only
     * when the string contains {@code "} but not {@code '}, double quotes otherwise. Callers are
     * responsible for surrounding separator/indent emission; this method emits only the quoted
     * string content (or {@code null} if {@code s} is null). First leaf of the JsonWriter dog-food
     * migration onto {@link CharStreamGenerator}.
     *
     * @param s The string value to write (may be null)
     * @throws IOException If an I/O error occurs
     */
    /**
     * State-machine-free quoted-string emission. Writes {@code "value"} (or {@code 'value'}
     * under json5SmartQuotes when the string contains {@code "} but not {@code '}) directly
     * to the underlying writer — does NOT touch gen's structural state machine. Caller is
     * responsible for any state transition (e.g. {@link CharStreamGenerator#markValue()} to
     * flip {@code FRAME_OBJECT_AFTER_FIELD} to {@code FRAME_OBJECT_AFTER_VALUE} when called
     * inside an object body). The state-machine bypass is the {@code writeStringValue}
     * counterpart of the {@code writeXxxRaw} family for scalars — same design intent:
     * gen-driven structural emission engages the state machine via Raw helpers, value
     * emissions in element loops skip the per-call state machine work to keep the per-
     * element overhead minimal. A {@code null} input emits the JSON literal {@code null}.
     */
    private void writeStringValue(String s) throws IOException {
        if (s == null) {
            out.write("null");
        } else if (json5SmartQuotes && shouldUseSingleQuotedString(s)) {
            CharStreamGenerator.writeSingleQuotedString(out, s, maxStringLength);
        } else {
            CharStreamGenerator.writeJsonUtf8String(out, s, maxStringLength);
        }
    }

    // Package-private so {@link CharStreamGenerator#writeString(String)} can apply the same
    // smart quote-style selection in json5SmartQuotes mode that {@link #writeStringValue(String)}
    // applies — picks the quote style that minimizes escaping.
    static boolean shouldUseSingleQuotedString(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        // Use single quotes only if string contains double quote and does not contain single quote.
        // indexOf() is intrinsified in modern JDKs and is usually faster than manual char-by-char scans.
        return s.indexOf('"') >= 0 && s.indexOf('\'') < 0;
    }

    /**
     * Writes a JSON5 single-quoted string value, properly escaped. In single-quoted form
     * the single quote is escaped (as {@code \\'}) while the double quote is not — the
     * inverse of JSON's standard double-quoted form.
     *
     * @param output          The Writer to write to
     * @param s               The string to be written
     * @param maxStringLength Maximum allowed string length
     * @throws IOException If an I/O error occurs
     * @deprecated since 4.103.0; the implementation moved to {@code CharStreamGenerator}.
     *             See {@link #writeBasicString(Writer, String)} for migration notes.
     */
    @Deprecated
    public static void writeSingleQuotedString(final Writer output, String s, int maxStringLength) throws IOException {
        CharStreamGenerator.writeSingleQuotedString(output, s, maxStringLength);
    }

    // ======================== Context Stack Management ========================

    // ======================== Jackson-Style Semantic Write API ========================
    //
    // Every method in this section is a thin delegate to {@link CharStreamGenerator}.
    // gen owns the structural state machine (auto-comma, indent in pretty mode, validation);
    // JsonWriter is purely a facade so existing callers that drive JsonWriter directly as
    // a Jackson-style writer keep working with the same method names + signatures.
    //
    // Migrated in 4.103.0 — the prior implementation maintained a parallel state machine
    // (a separate Deque<WriteContext> contextStack with writeCommaIfNeeded /
    // markObjectFieldWritten / markArrayElementWritten helpers). That parallel machine is
    // gone; gen is the single source of truth.
    //
    // Behavior change: the legacy writeStringField / writeObjectField / writeNumberField /
    // writeBooleanField / writeArrayFieldStart / writeObjectFieldStart documented "writes
    // a LEADING comma" semantics — callers had to avoid them for the first field in an
    // object. That deviation from Jackson is REMOVED — gen auto-decides comma based on
    // structural position, matching Jackson's actual writeStringField semantics. Calls
    // that followed the legacy convention (always for non-first fields) continue to produce
    // identical output; calls for the first field that previously emitted invalid JSON
    // (leading comma immediately after '{') now emit correct JSON.
    //
    // State-sync across writeImpl: writeImpl is wrapped with snapshotForExternalValue /
    // restoreAfterExternalValue (see method-level Javadoc), so writeObjectField and
    // writeValue(Object) — which transitively call writeImpl — see gen's state correctly
    // restored on return. This preserves backward compatibility with custom writers
    // (MultiKeyMapWriter, CompactMapWriter) that mix WriterContext.writeImpl with
    // context.writeFieldName / writeObjectField in their write methods.

    @Override
    public void writeFieldName(String name) throws IOException {
        gen.writeFieldName(name);
    }

    @Override
    public void writeStringField(String name, String value) throws IOException {
        gen.writeStringField(name, value);
    }

    @Override
    public void writeObjectField(String name, Object value) throws IOException {
        gen.writeFieldName(name);
        // writeFieldName left state at FRAME_OBJECT_AFTER_FIELD — startValueContext
        // at that state is a no-op (no separator needed after a field name), so the
        // writeImpl call below proceeds straight to the value emission. writeImpl's
        // wrapper handles state transition to FRAME_OBJECT_AFTER_VALUE via markValue.
        writeImpl(value, true);
    }

    @Override
    public void writeStartObject() throws IOException {
        gen.writeStartObject();
    }

    @Override
    public void writeEndObject() throws IOException {
        gen.writeEndObject();
    }

    @Override
    public void writeStartArray() throws IOException {
        gen.writeStartArray();
    }

    @Override
    public void writeEndArray() throws IOException {
        gen.writeEndArray();
    }

    @Override
    public void writeValue(String value) throws IOException {
        gen.writeString(value);
    }

    @Override
    public void writeValue(Object value) throws IOException {
        // startValueContext emits the separator (comma for non-empty array, no-op for
        // first element) + indent before writeImpl's value emission. writeImpl's
        // wrapper handles the state transition via markValue.
        gen.startValueContext();
        writeImpl(value, true);
    }

    @Override
    public void writeArrayFieldStart(String name) throws IOException {
        gen.writeArrayFieldStart(name);
    }

    @Override
    public void writeObjectFieldStart(String name) throws IOException {
        gen.writeObjectFieldStart(name);
    }

    @Override
    public void writeNumberField(String name, Number value) throws IOException {
        if (value == null) {
            gen.writeNullField(name);
            return;
        }
        gen.writeFieldName(name);
        // Dispatch to the right typed gen.writeNumber overload for the runtime type, so
        // each Number subtype gets its allocation-free digit-pair / canonical-form path.
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            gen.writeNumber(value.intValue());
        } else if (value instanceof Long) {
            gen.writeNumber(value.longValue());
        } else if (value instanceof Float) {
            gen.writeNumber(value.floatValue());
        } else if (value instanceof Double) {
            gen.writeNumber(value.doubleValue());
        } else if (value instanceof BigInteger) {
            gen.writeNumber((BigInteger) value);
        } else if (value instanceof BigDecimal) {
            gen.writeNumber((BigDecimal) value);
        } else {
            // AtomicInteger / AtomicLong / other Number subclasses — fall back to the
            // string-passthrough overload (trusted canonical form via toString).
            gen.writeNumber(value.toString());
        }
    }

    @Override
    public void writeBooleanField(String name, boolean value) throws IOException {
        gen.writeBooleanField(name, value);
    }

    /**
     * Check if NaN and Infinity values should be written as literals.
     * Returns true if either the legacy allowNanAndInfinity option or
     * the JSON5 json5InfinityNaN option is enabled.
     */
    private boolean isNanInfinityAllowed() {
        return writeOptions.isAllowNanAndInfinity() || writeOptions.isJson5InfinityNaN();
    }
}
