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
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.CompactMap;
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

    // Standard meta key prefixes (@ prefix - quoted)
    private static final String ID_SHORT = "\"@i\":";
    private static final String ID_LONG = "\"@id\":";
    private static final String TYPE_SHORT = "\"@t\":\"";
    private static final String TYPE_LONG = "\"@type\":\"";
    private static final String REF_SHORT = "\"@r\":";
    private static final String REF_LONG = "\"@ref\":";
    private static final String ITEMS_SHORT = "\"@e\":";
    private static final String ITEMS_LONG = "\"@items\":";
    private static final String KEYS_SHORT = "\"@k\":";
    private static final String KEYS_LONG = "\"@keys\":";

    // JSON5 meta key prefixes ($ prefix - unquoted, valid identifier)
    private static final String ID_JSON5 = "$id:";
    private static final String TYPE_JSON5 = "$type:\"";
    private static final String REF_JSON5 = "$ref:";
    private static final String ITEMS_JSON5 = "$items:";
    private static final String KEYS_JSON5 = "$keys:";

    // JSON5 short meta key prefixes ($ prefix, single character - unquoted)
    private static final String ID_JSON5_SHORT = "$i:";
    private static final String TYPE_JSON5_SHORT = "$t:\"";
    private static final String REF_JSON5_SHORT = "$r:";
    private static final String ITEMS_JSON5_SHORT = "$e:";
    private static final String KEYS_JSON5_SHORT = "$k:";

    // $ prefix with quoted keys (for standard JSON mode with $ prefix override)
    private static final String ID_DOLLAR_QUOTED = "\"$id\":";
    private static final String TYPE_DOLLAR_QUOTED = "\"$type\":\"";
    private static final String REF_DOLLAR_QUOTED = "\"$ref\":";
    private static final String ITEMS_DOLLAR_QUOTED = "\"$items\":";
    private static final String KEYS_DOLLAR_QUOTED = "\"$keys\":";

    // $ prefix short with quoted keys (for standard JSON mode with $ prefix override)
    private static final String ID_DOLLAR_SHORT_QUOTED = "\"$i\":";
    private static final String TYPE_DOLLAR_SHORT_QUOTED = "\"$t\":\"";
    private static final String REF_DOLLAR_SHORT_QUOTED = "\"$r\":";
    private static final String ITEMS_DOLLAR_SHORT_QUOTED = "\"$e\":";
    private static final String KEYS_DOLLAR_SHORT_QUOTED = "\"$k\":";

    // Pre-computed escape strings for ASCII characters
    // null = character doesn't need escaping, non-null = the escape sequence to write
    // This combines "needs escape?" and "what is the escape?" into a single array lookup
    private static final String[] ESCAPE_STRINGS = new String[128];
    static {
        // Control characters (0x00-0x1F) need \\u00xx escaping
        for (int i = 0; i <= 0x1F; i++) {
            ESCAPE_STRINGS[i] = String.format("\\u%04x", i);
        }
        // Override with short escapes for common control characters
        ESCAPE_STRINGS['\b'] = "\\b";
        ESCAPE_STRINGS['\t'] = "\\t";
        ESCAPE_STRINGS['\n'] = "\\n";
        ESCAPE_STRINGS['\f'] = "\\f";
        ESCAPE_STRINGS['\r'] = "\\r";
        // Quote and backslash always need escaping
        ESCAPE_STRINGS['"'] = "\\\"";
        ESCAPE_STRINGS['\\'] = "\\\\";
        // 0x20-0x7E are printable ASCII - leave as null (no escape needed)
        // 0x7F (DEL) needs escaping
        ESCAPE_STRINGS[0x7F] = "\\u007f";
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

    // Cached Field for EnumSet.elementType (lazy-initialized, immutable once set)
    private static volatile Field enumSetElementTypeField;
    private static volatile boolean enumSetFieldResolved = false;

    // Selected prefixes based on options
    private final String idPrefix;
    private final String typePrefix;
    private final String refPrefix;
    private final String itemsPrefix;
    private final String keysPrefix;
    private static final Object[] byteStrings = new Object[256];
    private static final String NEW_LINE = System.lineSeparator();
    // Lookup table for fast ASCII escape detection (128 entries for chars 0-127)
    private static final boolean[] NEEDS_ESCAPE = new boolean[128];
    // Lookup table for single-quoted strings (JSON5) - escapes ' instead of "
    private static final boolean[] NEEDS_ESCAPE_SINGLE_QUOTE = new boolean[128];
    private final WriteOptions writeOptions;
    // Lightweight identity-based maps for reference tracking (faster than IdentityHashMap<Object, Long>)
    // Uses primitive int values and open addressing - no boxing, no Entry objects
    private final IdentityIntMap objVisited = new IdentityIntMap(256);
    private final IdentityIntMap objsReferenced = new IdentityIntMap(256);
    private final Writer out;
    private int identity = 1;  // int is sufficient - max 2.1 billion unique objects
    private int depth = 0;

    // Primitive depth tracking for traceReferences (avoids Integer autoboxing)
    private int[] traceDepths = new int[256];
    private int traceDepthIndex;

    // Element type context: when writing Collection/Map fields, this tracks the declared element type
    // from the field's generic type (e.g., List<NestedData> -> NestedData). Used to eliminate
    // redundant @type output when element instance type == declared element type.
    // For Maps with non-String keys written as @keys/@items arrays, declaredKeyType tracks the key type.
    private Class<?> declaredElementType = null;
    private Class<?> declaredKeyType = null;

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

    // Pre-fetched custom writers for primitive array hot paths (avoids per-array lookup)
    private final com.cedarsoftware.io.JsonClassWriter longBoxedWriter;
    private final com.cedarsoftware.io.JsonClassWriter longPrimitiveWriter;
    private final com.cedarsoftware.io.JsonClassWriter doubleWriter;
    private final com.cedarsoftware.io.JsonClassWriter floatWriter;

    // Context tracking for automatic comma management
    private final Deque<WriteContext> contextStack = new ArrayDeque<>();

    /**
     * Tracks the current write context to enable automatic comma insertion.
     * This eliminates the need for manual "boolean first" tracking in custom writers.
     */
    enum WriteContext {
        ROOT,          // At document root
        OBJECT_EMPTY,  // Inside object, no fields written yet
        OBJECT_FIELD,  // Inside object, field(s) written
        ARRAY_EMPTY,   // Inside array, no elements yet
        ARRAY_ELEMENT  // Inside array, element(s) written
    }

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

        // Initialize escape lookup table for fast ASCII escape detection
        // Mark all control characters (0x00-0x1F) as needing escape
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE[i] = true;
        }
        // Mark specific characters that need escaping
        NEEDS_ESCAPE['"'] = true;   // Quote
        NEEDS_ESCAPE['\\'] = true;  // Backslash
        NEEDS_ESCAPE[0x7F] = true;  // DEL control character

        // Initialize escape lookup table for single-quoted strings (JSON5)
        // Mark all control characters (0x00-0x1F) as needing escape
        for (int i = 0; i < 0x20; i++) {
            NEEDS_ESCAPE_SINGLE_QUOTE[i] = true;
        }
        // Mark specific characters that need escaping for single-quoted strings
        NEEDS_ESCAPE_SINGLE_QUOTE['\''] = true;  // Single quote
        NEEDS_ESCAPE_SINGLE_QUOTE['\\'] = true;  // Backslash
        NEEDS_ESCAPE_SINGLE_QUOTE[0x7F] = true;  // DEL control character
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
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;

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
            // JSON5 + $ prefix + short: $t, $i, $r, $e, $k (unquoted)
            this.idPrefix = ID_JSON5_SHORT;
            this.typePrefix = TYPE_JSON5_SHORT;
            this.refPrefix = REF_JSON5_SHORT;
            this.itemsPrefix = ITEMS_JSON5_SHORT;
            this.keysPrefix = KEYS_JSON5_SHORT;
        } else if (useDollarPrefix && canUseUnquotedKeys) {
            // JSON5 + $ prefix + long: $type, $id, $ref, $items, $keys (unquoted)
            this.idPrefix = ID_JSON5;
            this.typePrefix = TYPE_JSON5;
            this.refPrefix = REF_JSON5;
            this.itemsPrefix = ITEMS_JSON5;
            this.keysPrefix = KEYS_JSON5;
        } else if (useDollarPrefix && isShort) {
            // $ prefix + short + quoted: "$t", "$i", "$r", "$e", "$k"
            this.idPrefix = ID_DOLLAR_SHORT_QUOTED;
            this.typePrefix = TYPE_DOLLAR_SHORT_QUOTED;
            this.refPrefix = REF_DOLLAR_SHORT_QUOTED;
            this.itemsPrefix = ITEMS_DOLLAR_SHORT_QUOTED;
            this.keysPrefix = KEYS_DOLLAR_SHORT_QUOTED;
        } else if (useDollarPrefix) {
            // $ prefix + long + quoted: "$type", "$id", "$ref", "$items", "$keys"
            this.idPrefix = ID_DOLLAR_QUOTED;
            this.typePrefix = TYPE_DOLLAR_QUOTED;
            this.refPrefix = REF_DOLLAR_QUOTED;
            this.itemsPrefix = ITEMS_DOLLAR_QUOTED;
            this.keysPrefix = KEYS_DOLLAR_QUOTED;
        } else if (isShort) {
            // @ prefix + short + quoted: "@t", "@i", "@r", "@e", "@k"
            this.idPrefix = ID_SHORT;
            this.typePrefix = TYPE_SHORT;
            this.refPrefix = REF_SHORT;
            this.itemsPrefix = ITEMS_SHORT;
            this.keysPrefix = KEYS_SHORT;
        } else {
            // @ prefix + long + quoted: "@type", "@id", "@ref", "@items", "@keys"
            this.idPrefix = ID_LONG;
            this.typePrefix = TYPE_LONG;
            this.refPrefix = REF_LONG;
            this.itemsPrefix = ITEMS_LONG;
            this.keysPrefix = KEYS_LONG;
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

        // Pre-fetch custom writers for primitive array hot paths
        this.longBoxedWriter = this.writeOptions.getCustomWriter(Long.class);
        this.longPrimitiveWriter = this.writeOptions.getCustomWriter(long.class);
        this.doubleWriter = this.writeOptions.getCustomWriter(Double.class);
        this.floatWriter = this.writeOptions.getCustomWriter(Float.class);
    }

    public WriteOptions getWriteOptions() {
        return writeOptions;
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
     * Tab the output left (less indented)
     *
     * @throws IOException
     */
    public void tabIn() throws IOException {
        tab(out, 1);
    }

    /**
     * Add newline (\n) to output
     *
     * @throws IOException
     */
    public void newLine() throws IOException {
        tab(out, 0);
    }

    /**
     * Tab the output right (more indented)
     *
     * @throws IOException
     */
    public void tabOut() throws IOException {
        tab(out, -1);
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
            return writeCustom(c, o, !neverShowingType && showType, output);
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
        // Exit early if the declared class is explicitly marked as not-custom-written
        if (writeOptions.isNotCustomWrittenClass(declaredClass)) {
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
            return writeOptions.getCustomWriter(actualClass);
        }

        // Different types: check if declared type has a writer (gatekeeper)
        // If declared type has no writer, don't use custom writing even if actual type has one
        if (writeOptions.getCustomWriter(declaredClass) == null) {
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
        if (neverShowingType) {
            showType = false;
        }

        if (writeOptionalReference(o)) {
            return true;
        }

        boolean referenced = objsReferenced.containsKey(o);

        if (closestWriter.hasPrimitiveForm(this)) {
            if ((!referenced && !showType) || closestWriter instanceof Writers.JsonStringWriter) {
                closestWriter.writePrimitiveForm(o, output, this);
                return true;
            }
        }

        output.write('{');
        tabIn();
        if (referenced) {
            writeId(getIdInt(o));
            if (showType) {
                output.write(',');
                newLine();
            }
        }

        if (showType) {
            String typeName = closestWriter.getTypeName(o);
            writeType(typeName, output);
        }

        if (referenced || showType) {
            output.write(',');
            newLine();
        }

        closestWriter.write(o, showType || referenced, output, this);

        tabOut();
        output.write('}');
        return true;
    }

    /**
     * Write the passed in Java object in JSON format.
     *
     * @param obj Object any Java Object or JsonObject.
     */
    public void write(Object obj) {
        if (cycleSupport) {
            traceReferences(obj);
            objVisited.clear();
        }
        // When cycleSupport=false, objVisited will be used during write to detect cycles
        try {
            boolean showType = writeOptions.isShowingRootTypeInfo();
            if (obj != null) {
                if (neverShowingType) {
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
        } catch (JsonIoException e) {
            throw e;
        } catch (Exception e) {
            throw new JsonIoException("Error writing object to JSON:", e);
        }
        flush();
        objVisited.clear();
        objsReferenced.clear();
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
        objectStack.addFirst(root);
        pushDepth(0);

        final IdentityIntMap visited = objVisited;
        final IdentityIntMap referenced = objsReferenced;

        // Marker value: -1 means "seen but no ID assigned yet", 0 means "not in map", >0 is the assigned ID
        final int FIRST_SEEN = -1;

        // Fix memory leak - prevent stack overflow and unbounded memory growth using configurable limits
        final int MAX_DEPTH = writeOptions.getMaxObjectGraphDepth();
        final int MAX_OBJECTS = writeOptions.getMaxObjectCount();
        int processedCount = 0;

        while (!objectStack.isEmpty() && processedCount < MAX_OBJECTS) {
            final Object obj = objectStack.removeFirst();
            final int currentDepth = traceDepths[--traceDepthIndex];
            processedCount++;

            // Check actual depth, not stack size
            if (currentDepth > MAX_DEPTH) {
                throw new JsonIoException("Object graph too deep (>" + MAX_DEPTH + " levels). This may indicate a circular reference or excessively nested structure.");
            }

            // Cache class once - avoid duplicate getClass() calls (performance optimization)
            final Class<?> clazz = obj.getClass();
            final boolean isNonReferenceable = writeOptions.isNonReferenceableClass(clazz);

            if (!isNonReferenceable) {
                int id = visited.get(obj);
                if (id != 0) {   // Object has been seen before (0 = NOT_FOUND)
                    if (id == FIRST_SEEN) {
                        id = identity++;
                        visited.put(obj, id);
                        referenced.put(obj, id);
                    }
                    continue;
                } else {
                    visited.put(obj, FIRST_SEEN);
                }
            }

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
            } else if (!isNonReferenceable) {
                // Reuse cached isNonReferenceable result
                processFields(objectStack, obj, nextDepth);
            }
        }

        // Check if we hit the object limit
        if (processedCount >= MAX_OBJECTS) {
            throw new JsonIoException("Object graph too large (>" + MAX_OBJECTS + " objects). This may indicate excessive nesting or a memory leak.");
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
        if (!writeOptions.isNonReferenceableClass(componentType)) {
            // Iterate directly over Object[] - primitive arrays are filtered out by isNonReferenceableClass check
            for (final Object element : array) {
                if (element != null) {
                    objectStack.addFirst(element);
                    pushDepth(depth);
                }
            }
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
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (key != null) {
                objectStack.addFirst(key);
                pushDepth(depth);
            }
            if (value != null) {
                objectStack.addFirst(value);
                pushDepth(depth);
            }
        }
    }

    private void processCollection(Collection<?> collection, Deque<Object> objectStack, int depth) {
        for (Object item : collection) {
            if (item != null) {
                objectStack.addFirst(item);
                pushDepth(depth);
            }
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
        // If caller has special Field specifier for a given class
        // then use it, otherwise use reflection.
        Collection<Accessor> fields = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : fields) {
            final Object o = accessor.retrieve(obj);
            if (o != null) {   // Trace through objects that can reference other objects
                objectStack.addFirst(o);
                pushDepth(depth);
            }
        }
    }

    private boolean writeOptionalReference(Object obj) throws IOException {
        if (obj == null || writeOptions.isNonReferenceableClass(obj.getClass())) {
            return false;
        }

        final Writer output = this.out;
        if (objVisited.containsKey(obj)) {    // Only write (define) an object once in the JSON stream, otherwise emit a @ref
            int id = getIdInt(obj);
            if (id == 0) {   // Test for 0 because of Weak/Soft references being gc'd during serialization.
                // When cycleSupport=false, objects won't have IDs, but we still need to skip
                // duplicates to prevent infinite recursion. Return true to skip silently.
                return !cycleSupport;
            }
            output.write('{');
            output.write(refPrefix);
            writeLongDirect(id);
            output.write('}');
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
     * @param obj      Object to be written
     * @param showType if set to true, the @type tag will be output.
     * @throws IOException if one occurs on the underlying output stream.
     */
    public void writeImpl(Object obj, boolean showType) throws IOException {
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

        if (neverShowingType) {
            showType = false;
        }

        // Custom writers and references are checked first to preserve type information
        if (writeUsingCustomWriter(obj, showType, out) || writeOptionalReference(obj)) {
            return;
        }

        final Class<?> objClass = obj.getClass();

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
    }

    private void writeId(final int id) throws IOException {
        out.write(idPrefix);
        writeLongDirect(id);  // int widened to long
    }

    // Pre-computed digit pairs for fast long-to-chars conversion (00-99)
    private static final char[] DIGIT_TENS = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    };
    private static final char[] DIGIT_ONES = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    // Scratch buffer for long-to-chars conversion (max 20 digits for Long.MIN_VALUE)
    private final char[] longBuffer = new char[20];

    /**
     * Write a long value directly to output without creating a String object.
     * Uses digit pair lookup tables for efficient conversion.
     */
    private void writeLongDirect(long value) throws IOException {
        if (value == 0) {
            out.write('0');
            return;
        }

        int idx = longBuffer.length;
        boolean negative = value < 0;
        if (!negative) {
            value = -value;  // Work with negative to handle Long.MIN_VALUE
        }

        // Extract digits two at a time using lookup tables
        while (value <= -100) {
            int q = (int) (value / 100);
            int r = (int) ((q * 100) - value);  // remainder 0-99
            value = q;
            longBuffer[--idx] = DIGIT_ONES[r];
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        // Handle remaining 1-2 digits
        int r = (int) -value;
        longBuffer[--idx] = DIGIT_ONES[r];
        if (r >= 10) {
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        if (negative) {
            longBuffer[--idx] = '-';
        }

        out.write(longBuffer, idx, longBuffer.length - idx);
    }

    /**
     * Write an int value directly to output without creating a String object.
     * Reuses the longBuffer and digit pair lookup tables.
     */
    private void writeIntDirect(int value) throws IOException {
        if (value == 0) {
            out.write('0');
            return;
        }

        int idx = longBuffer.length;
        boolean negative = value < 0;
        if (!negative) {
            value = -value;  // Work with negative to handle Integer.MIN_VALUE
        }

        // Extract digits two at a time using lookup tables
        while (value <= -100) {
            int q = value / 100;
            int r = (q * 100) - value;  // remainder 0-99
            value = q;
            longBuffer[--idx] = DIGIT_ONES[r];
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        // Handle remaining 1-2 digits
        int r = -value;
        longBuffer[--idx] = DIGIT_ONES[r];
        if (r >= 10) {
            longBuffer[--idx] = DIGIT_TENS[r];
        }

        if (negative) {
            longBuffer[--idx] = '-';
        }

        out.write(longBuffer, idx, longBuffer.length - idx);
    }

    // Optimized writeType method
    private void writeType(String name, Writer output) throws IOException {
        if (neverShowingType) {
            return;
        }
        output.write(typePrefix);
        String alias = writeOptions.getTypeNameAlias(name);
        output.write(alias);
        output.write('"');
    }

    private void writePrimitive(final Object obj, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        if (obj instanceof Long && writeLongsAsStrings) {
            if (showType) {
                out.write('{');
                writeType("long", out);
                out.write(',');
            }

            longBoxedWriter.write(obj, showType, out, this);

            if (showType) {
                out.write('}');
            }
        } else if (!isNanInfinityAllowed() && obj instanceof Double && (Double.isNaN((Double) obj) || Double.isInfinite((Double) obj))) {
            out.write("null");
        } else if (!isNanInfinityAllowed() && obj instanceof Float && (Float.isNaN((Float) obj) || Float.isInfinite((Float) obj))) {
            out.write("null");
        } else {
            out.write(obj.toString());
        }
    }

    private void writeObjectArray(final Object[] array, final Class<?> arrayType, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final int len = array.length;  // Direct access - no reflection needed
        boolean referenced = objsReferenced.containsKey(array);
        boolean typeWritten = showType && !(arrayType.equals(Object[].class));
        final Writer output = this.out;

        if (typeWritten || referenced) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(getIdInt(array));
            output.write(',');
            newLine();
        }

        if (typeWritten) {
            writeType(arrayType.getName(), output);
            output.write(',');
            newLine();
        }

        if (len == 0) {
            if (typeWritten || referenced) {
                output.write(itemsPrefix);
                output.write("[]");
                tabOut();
                output.write('}');
            } else {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced) {
            output.write(itemsPrefix);
            output.write('[');
        } else {
            output.write('[');
        }
        tabIn();

        final int lenMinus1 = len - 1;
        final Class<?> componentClass = arrayType.getComponentType();

        // Write array elements with direct array access - no reflection
        for (int i = 0; i < len; i++) {
            final Object value = array[i];

            if (value == null) {
                output.write("null");
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (!writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                    writeImpl(value, forceType);
                }
            }

            if (i != lenMinus1) {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
            tabOut();
            output.write('}');
        }
    }

    private void writePrimitiveArray(final Object array, final Class<?> arrayType, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final int len = ArrayUtilities.getLength(array);
        boolean referenced = objsReferenced.containsKey(array);
        boolean typeWritten = showType;  // Primitive arrays are never Object[], type always written when showType
        final Writer output = this.out;

        if (typeWritten || referenced) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(getIdInt(array));
            output.write(',');
            newLine();
        }

        if (typeWritten) {
            writeType(arrayType.getName(), output);
            output.write(',');
            newLine();
        }

        if (len == 0) {
            if (typeWritten || referenced) {
                output.write(itemsPrefix);
                output.write("[]");
                tabOut();
                output.write('}');
            } else {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced) {
            output.write(itemsPrefix);
            output.write('[');
        } else {
            output.write('[');
        }
        tabIn();

        final int lenMinus1 = len - 1;

        // Handle each primitive array type with optimized loops
        if (byte[].class == arrayType) {
            writeByteArray((byte[]) array, lenMinus1);
        } else if (char[].class == arrayType) {
            writeStringValue(new String((char[]) array));
        } else if (short[].class == arrayType) {
            writeShortArray((short[]) array, lenMinus1);
        } else if (int[].class == arrayType) {
            writeIntArray((int[]) array, lenMinus1);
        } else if (long[].class == arrayType) {
            writeLongArray((long[]) array, lenMinus1);
        } else if (float[].class == arrayType) {
            writeFloatArray((float[]) array, lenMinus1);
        } else if (double[].class == arrayType) {
            writeDoubleArray((double[]) array, lenMinus1);
        } else if (boolean[].class == arrayType) {
            writeBooleanArray((boolean[]) array, lenMinus1);
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
            tabOut();
            output.write('}');
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
        for (int i = 0; i < lenMinus1; i++) {
            writeIntDirect(ints[i]);
            out.write(',');
        }
        writeIntDirect(ints[lenMinus1]);
    }

    private void writeShortArray(short[] shorts, int lenMinus1) throws IOException {
        for (int i = 0; i < lenMinus1; i++) {
            writeIntDirect(shorts[i]);
            out.write(',');
        }
        writeIntDirect(shorts[lenMinus1]);
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

    private void writeCollection(Collection<?> col, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(col);
        boolean isEmpty = col.isEmpty();

        if (referenced || showType) {
            output.write('{');
            tabIn();
        } else if (isEmpty) {
            output.write('[');
        }

        writeIdAndTypeIfNeeded(col, showType, referenced);

        if (isEmpty) {
            if (referenced || showType) {
                tabOut();
                output.write('}');
            } else {
                output.write(']');
            }
            return;
        }

        beginCollection(showType, referenced);
        Iterator<?> i = col.iterator();

        writeElements(output, i);

        tabOut();
        output.write(']');
        if (showType || referenced) {   // Finished object, as it was output as an object if @id or @type was output
            tabOut();
            output.write("}");
        }
    }

    private void writeElements(Writer output, Iterator<?> i) throws IOException {
        boolean wroteElement = false;
        while (i.hasNext()) {
            // Write comma and newline BEFORE element (except first) - avoids double hasNext() call
            if (wroteElement) {
                output.write(',');
                newLine();
            }
            writeCollectionElement(i.next());
            wroteElement = true;
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

    private void writeIdAndTypeIfNeeded(Object col, boolean showType, boolean referenced) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        if (referenced) {
            writeId(getIdInt(col));
        }

        if (showType) {
            if (referenced) {
                out.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(col), out);
        }
    }

    private void beginCollection(boolean showType, boolean referenced) throws IOException {
        if (showType || referenced) {
            out.write(',');
            newLine();
            out.write(itemsPrefix);
            out.write('[');
        } else {
            out.write('[');
        }
        tabIn();
    }

    private void writeJsonObjectArray(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType) {
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

        final Writer output = this.out;
        final boolean isObjectArray = Object[].class == arrayClass;
        final Class<?> componentClass = arrayClass.getComponentType();
        boolean referenced = adjustIfReferenced(jObj);
        boolean typeWritten = showType && !isObjectArray;

        if (typeWritten || referenced) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(jObj.id);
            output.write(',');
            newLine();
        }

        if (typeWritten) {
            writeType(arrayClass.getName(), output);
            output.write(',');
            newLine();
        }

        if (len == 0) {
            if (typeWritten || referenced) {
                output.write(itemsPrefix);
                output.write("[]");
                tabOut();
                output.write("}");
            } else {
                output.write("[]");
            }
            return;
        }

        if (typeWritten || referenced) {
            output.write(itemsPrefix);
            output.write('[');
        } else {
            output.write('[');
        }
        tabIn();

        // items array already fetched at method start
        final int lenMinus1 = len - 1;

        for (int i = 0; i < len; i++) {
            final Object value = items[i];

            if (value == null) {
                output.write("null");
            } else {
                final boolean forceType = isForceType(value.getClass(), componentClass);
                if (writeArrayElementIfMatching(componentClass, value, forceType, output)) {
                } else if (Character.class == componentClass || char.class == componentClass) {
                    writeStringValue((String) value);
                } else if (value instanceof String) {
                    writeStringValue((String) value);
                } else if (value instanceof Boolean || value instanceof Long || value instanceof Double) {
                    writePrimitive(value, forceType);
                } else {   // Specific Class-type arrays - only force type when
                    // the instance is derived from array base class.
                    writeImpl(value, forceType);
                }
            }

            if (i != lenMinus1) {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write(']');
        if (typeWritten || referenced) {
            tabOut();
            output.write('}');
        }
    }

    private void writeJsonObjectCollection(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        Class<?> colClass = jObj.getRawType();
        boolean referenced = adjustIfReferenced(jObj);
        final Writer output = this.out;
        Object[] items = jObj.getItems();
        int len = items != null ? items.length : 0;

        if (referenced || showType || len == 0) {
            output.write('{');
            tabIn();
        }

        if (referenced) {
            writeId(jObj.id);
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }

            writeType(colClass.getName(), output);
        }

        if (len == 0) {
            tabOut();
            output.write('}');
            return;
        }

        beginCollection(showType, referenced);

        // items already fetched at method start
        final int itemsLen = items.length;
        final int itemsLenMinus1 = itemsLen - 1;

        for (int i = 0; i < itemsLen; i++) {
            writeCollectionElement(items[i]);

            if (i != itemsLenMinus1) {
                output.write(',');
                newLine();
            }
        }

        tabOut();
        output.write("]");
        if (showType || referenced) {
            tabOut();
            output.write('}');
        }
    }

    private void writeJsonObjectMap(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final Writer output = this.out;
        showType = emitIdAndTypeIfNeeded(jObj, showType, output);

        if (jObj.isEmpty()) {   // Empty
            tabOut();
            output.write('}');
            return;
        }

        if (showType) {
            output.write(',');
            newLine();
        }

        writeMapToEnd(jObj, output);
    }

    private boolean writeJsonObjectMapWithStringKeys(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }

        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(jObj)) {
            return false;
        }

        Writer output = this.out;
        emitIdAndTypeIfNeeded(jObj, showType, output);

        if (jObj.isEmpty()) { // Empty
            tabOut();
            output.write('}');
            return true;
        }

        if (showType) {
            output.write(',');
            newLine();
        }

        return writeMapBody(jObj.entrySet().iterator());
    }

    private boolean emitIdAndTypeIfNeeded(JsonObject jObj, boolean showType, Writer output) throws IOException {
        boolean referenced = adjustIfReferenced(jObj);
        output.write('{');
        tabIn();

        if (referenced) {
            writeId((int) jObj.getId());  // Safe cast - internal storage is int
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            String type = getTypeNameForOutput(jObj);
            if (type != null) {
                writeType(type, output);
            } else {   // type not displayed
                showType = false;
            }
        }
        return showType;
    }

    /**
     * Write fields of an Object (JsonObject)
     */
    private void writeJsonObjectObject(JsonObject jObj, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = adjustIfReferenced(jObj);
        showType = showType && jObj.getType() != null;

        output.write('{');
        tabIn();
        if (referenced) {
            writeId(jObj.id);
        }

        Class<?> type = null;
        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(jObj), output);
            type = jObj.getRawType();
        }

        if (jObj.isEmpty()) {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced) {
            output.write(',');
            newLine();
        }

        Iterator<Map.Entry<Object, Object>> i = jObj.entrySet().iterator();
        boolean first = true;

        while (i.hasNext()) {
            Map.Entry<Object, Object> entry = i.next();
            if (skipNullFields && entry.getValue() == null) {
                continue;
            }

            if (!first) {
                output.write(',');
                newLine();
            }
            first = false;
            final String fieldName = (String) entry.getKey();
            // Support JSON5 unquoted keys and proper string escaping (consistent with writeMapBody)
            if (json5UnquotedKeys && isValidJson5Identifier(fieldName)) {
                output.write(fieldName);
                output.write(':');
            } else {
                writeJsonUtf8String(output, fieldName, maxStringLength);
                output.write(':');
            }
            Object value = entry.getValue();

            if (value == null) {
                output.write("null");
            } else if (value instanceof BigDecimal || value instanceof BigInteger) {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            } else if (value instanceof Number || value instanceof Boolean) {
                output.write(value.toString());
            } else if (value instanceof String) {
                writeStringValue((String) value);
            } else if (value instanceof Character) {
                writeStringValue(String.valueOf(value));
            } else {
                writeImpl(value, !doesValueTypeMatchFieldType(type, fieldName, value));
            }
        }
        tabOut();
        output.write('}');
    }

    private boolean adjustIfReferenced(JsonObject jObj) {
        int idx = objsReferenced.get(jObj);  // Returns 0 if not found
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

    private void writeMap(Map map, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final Writer output = this.out;
        boolean referenced = this.objsReferenced.containsKey(map);

        output.write('{');
        tabIn();
        if (referenced) {
            writeId(getIdInt(map));
        }

        if (showType) {
            if (referenced) {
                output.write(',');
                newLine();
            }
            writeType(getTypeNameForOutput(map), output);
        }

        if (map.isEmpty()) {
            tabOut();
            output.write('}');
            return;
        }

        if (showType || referenced) {
            output.write(',');
            newLine();
        }

        writeMapToEnd(map, output);
    }

    private void writeMapToEnd(Map map, Writer output) throws IOException {
        // Save current element type (Map value type) and switch to key type for @keys array
        Class<?> savedValueType = declaredElementType;

        output.write(keysPrefix);
        output.write('[');
        tabIn();
        Iterator<?> i = map.keySet().iterator();

        // Use key type context for writing @keys array elements
        declaredElementType = declaredKeyType;
        writeElements(output, i);

        tabOut();
        output.write("],");
        newLine();
        output.write(itemsPrefix);
        output.write('[');
        tabIn();
        i = map.values().iterator();

        // Restore value type context for writing @items array elements
        declaredElementType = savedValueType;
        writeElements(output, i);

        tabOut();
        output.write(']');
        tabOut();
        output.write('}');
    }

    private boolean writeMapWithStringKeys(Map map, boolean showType) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        if (writeOptions.isForceMapOutputAsTwoArrays() || !ensureJsonPrimitiveKeys(map)) {
            return false;
        }

        boolean referenced = this.objsReferenced.containsKey(map);

        out.write('{');
        tabIn();
        writeIdAndTypeIfNeeded(map, showType, referenced);

        if (map.isEmpty()) {
            tabOut();
            out.write('}');
            return true;
        }

        if (showType || referenced) {
            out.write(',');
            newLine();
        }

        return writeMapBody(map.entrySet().iterator());
    }

    private boolean writeMapBody(final Iterator i) throws IOException {
        final Writer output = out;
        boolean wroteEntry = false;

        while (i.hasNext()) {
            Entry att2value = (Entry) i.next();
            Object value = att2value.getValue();

            if (skipNullFields && value == null) {
                continue;
            }

            // Write comma and newline BEFORE entry (except first) - avoids double hasNext() call
            if (wroteEntry) {
                output.write(',');
                newLine();
            }

            // Inline key writing with pre-fetched member variables
            String key = (String) att2value.getKey();
            if (json5UnquotedKeys && isValidJson5Identifier(key)) {
                output.write(key);
                output.write(':');
            } else {
                writeJsonUtf8String(output, key, maxStringLength);
                output.write(':');
            }

            writeCollectionElement(value);
            wroteEntry = true;
        }

        tabOut();
        output.write('}');
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
        } else if (neverShowingType && ClassUtilities.isPrimitive(o.getClass())) {   // If neverShowType, then force primitives (and primitive wrappers)
            // to be output with toString() - prevents {"value":6} for example
            writePrimitive(o, false);
        } else {
            // Use declaredElementType to determine if @type is needed - eliminates redundant @type
            // when element instance type == declared element type from field generic info
            boolean showType = shouldShowTypeForElement(o.getClass());
            writeImpl(o, showType);
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
        out.write('{');
        tabIn();

        boolean referenced = this.objsReferenced.containsKey(enumSet);
        if (referenced) {
            writeId(getIdInt(enumSet));
            out.write(',');
            newLine();
        }

        String typeKey = writeOptions.isEnumSetWrittenOldWay() ? ENUM : TYPE;
        out.write('\"');
        out.write(typeKey);
        out.write("\":");

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

        // Write the @type field with the actual enum class name
        writeBasicString(out, enumClass.getName());

        // EnumSets are always written with an @items key
        out.write(",");
        newLine();
        writeBasicString(out, ITEMS);
        out.write(":[");
        boolean hasItems = !enumSet.isEmpty();

        if (hasItems) {
            newLine();
            tabIn();

            boolean firstInSet = true;
            for (Enum<?> e : enumSet) {
                if (!firstInSet) {
                    out.write(",");
                    newLine();
                }
                firstInSet = false;

                // Determine whether to write the full enum object or just the name
                Collection<Accessor> mapOfFields = writeOptions.getAccessorsForClass(e.getClass());
                int enumFieldsCount = mapOfFields.size();

                if (enumFieldsCount <= 2) {
                    // Write the enum name as a string
                    writeStringValue(e.name());
                } else {
                    // Write the enum as a JSON object with its fields
                    out.write('{');
                    boolean firstInEntry = true;
                    for (Accessor f : mapOfFields) {
                        firstInEntry = writeField(e, firstInEntry, f.getUniqueFieldName(), f);
                    }
                    out.write('}');
                }
            }

            tabOut();
            newLine();
        }

        out.write(']');
        tabOut();
        newLine();
        out.write('}');
    }

    /**
     * @param obj      Object to be written in JSON format
     * @param showType boolean true means show the "@type" field, false
     *                 eliminates it.  Many times the type can be dropped because it can be
     *                 inferred from the field or array type.
     * @param bodyOnly write only the body of the object
     * @throws IOException if an error occurs writing to the output stream.
     */
    public void writeObject(final Object obj, boolean showType, boolean bodyOnly) throws IOException {
        if (neverShowingType) {
            showType = false;
        }
        final boolean referenced = this.objsReferenced.containsKey(obj);
        if (!bodyOnly) {
            out.write('{');
            tabIn();
            if (referenced) {
                writeId(getIdInt(obj));
            }

            if (referenced && showType) {
                out.write(',');
                newLine();
            }

            if (showType) {
                writeType(obj.getClass().getName(), out);
            }
        }

        boolean first = !showType;
        if (referenced && !showType) {
            first = false;
        }

        Collection<Accessor> accessors = writeOptions.getAccessorsForClass(obj.getClass());

        for (final Accessor accessor : accessors) {
            final String fieldName = accessor.getUniqueFieldName();
            first = writeField(obj, first, fieldName, accessor);
        }

        if (!bodyOnly) {
            tabOut();
            out.write('}');
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

    private boolean writeField(Object obj, boolean first, String fieldName, Accessor accessor) throws IOException {
        final Class<?> fieldDeclaringClass = accessor.getDeclaringClass();
        if (Enum.class.isAssignableFrom(fieldDeclaringClass) && !accessor.isPublic() && writeOptions.isEnumPublicFieldsOnly()) {
            return first;
        }
        Object o = accessor.retrieve(obj);

        if (skipNullFields && o == null) {   // If skip null, skip field and return the same status on first field written indicator
            return first;
        }

        if (!first) {
            out.write(',');
            newLine();
        }

        writeKey(fieldName);

        if (o == null) {    // don't quote null
            out.write("null");
            return false;
        }

        // check to see if type needs to be written.
        Class<?> type = accessor.getFieldType();

        // For Collection/Map fields, extract the element type from generic type to eliminate redundant @type on elements
        // Skip this optimization for Throwables - ThrowableFactory uses ClassFactory which needs @type info
        Class<?> savedElementType = declaredElementType;
        Class<?> savedKeyType = declaredKeyType;
        try {
            if ((o instanceof Collection || o instanceof Map) && !Throwable.class.isAssignableFrom(fieldDeclaringClass)) {
                Type genericType = accessor.getGenericType();
                if (genericType != null) {
                    // For Maps, extract both key type and value type
                    if (o instanceof Map) {
                        Type keyType = extractMapKeyType(genericType);
                        if (keyType instanceof Class) {
                            declaredKeyType = (Class<?>) keyType;
                        }
                    }
                    // inferElementType returns value type for Maps, element type for Collections
                    Type elementType = TypeUtilities.inferElementType(genericType, null);
                    if (elementType instanceof Class) {
                        declaredElementType = (Class<?>) elementType;
                    }
                }
            }
            writeImpl(o, isForceType(o.getClass(), type));
        } finally {
            declaredElementType = savedElementType;
            declaredKeyType = savedKeyType;
        }
        return false;
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

        if (neverShowingType && Primitives.isPrimitive(objectClass) && !objectClassIsLongWrittenAsString) {
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
        if ((minimalPlusFormat || neverShowingType) && declaredType == Object.class && NUMERIC_PRIMITIVES_FOR_COMPACT.contains(objectClass)) {
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

    /**
     * Extract the key type from a Map type (e.g., Map<Building, Person> -> Building).
     * Used to eliminate redundant @type on Map keys when key type is known from field generics.
     */
    private Type extractMapKeyType(Type mapType) {
        if (mapType instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) mapType;
            java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length >= 1) {
                return typeArgs[0];  // Key type is at index 0
            }
        }
        return null;
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
        if (o instanceof JsonObject) {
            int id = ((JsonObject) o).id;
            if (id > 0) {
                return id;
            }
        }
        return this.objsReferenced.get(o);  // Returns 0 if not found
    }

    /**
     * Writes out a string without special characters. Use for labels, etc. when you know you
     * will not need extra formattting for UTF-8 or tabs, quotes and newlines in the string
     *
     * @param writer Writer to which the UTF-8 string will be written to
     * @param s      String to be written in UTF-8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeBasicString(final Writer writer, String s) throws IOException {
        writer.write('\"');
        writer.write(s);
        writer.write('\"');
    }

    /**
     * Writes a JSON string value to the output, properly escaped according to JSON specifications.
     * Handles control characters, quotes, backslashes, and properly processes Unicode code points.
     * Uses default string length limit for backward compatibility.
     *
     * @param output The Writer to write to
     * @param s      The string to be written as a JSON string value
     * @throws IOException If an I/O error occurs
     */
    public static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        writeJsonUtf8String(output, s, 1000000); // Use default 1MB limit for backward compatibility
    }

    /**
     * Writes a string value with JSON5 smart quote selection if enabled in WriteOptions.
     * This static method is for use by custom writers that need to respect smart quote settings.
     *
     * @param output The Writer to write to
     * @param s The string value to write
     * @param writeOptions WriteOptions to check for smart quote settings
     * @throws IOException If an I/O error occurs
     */
    public static void writeJson5String(final Writer output, String s, WriteOptions writeOptions) throws IOException {
        if (writeOptions == null || !writeOptions.isJson5SmartQuotes()) {
            writeJsonUtf8String(output, s, writeOptions != null ? writeOptions.getMaxStringLength() : 1000000);
            return;
        }

        int maxLen = writeOptions.getMaxStringLength();

        // Scan string to determine quote strategy
        boolean hasDoubleQuote = false;
        boolean hasSingleQuote = false;
        int len = s != null ? s.length() : 0;

        for (int i = 0; i < len && !(hasDoubleQuote && hasSingleQuote); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                hasDoubleQuote = true;
            } else if (c == '\'') {
                hasSingleQuote = true;
            }
        }

        // Use single quotes only if string has " but no '
        if (hasDoubleQuote && !hasSingleQuote) {
            writeSingleQuotedString(output, s, maxLen);
        } else {
            writeJsonUtf8String(output, s, maxLen);
        }
    }

    /**
     * Writes a JSON string value to the output, properly escaped according to JSON specifications.
     * Handles control characters, quotes, backslashes, and properly processes Unicode code points.
     * <p>
     * OPTIMIZED VERSION: Uses batch scanning to write runs of safe characters in one operation,
     * significantly reducing write() calls and improving performance.
     *
     * @param output          The Writer to write to
     * @param s               The string to be written as a JSON string value
     * @param maxStringLength Maximum allowed string length to prevent memory issues
     * @throws IOException If an I/O error occurs
     */
    public static void writeJsonUtf8String(final Writer output, String s, int maxStringLength) throws IOException {
        if (output == null) {
            throw new JsonIoException("Output writer cannot be null");
        }
        if (s == null) {
            output.write("null");
            return;
        }

        final int len = s.length();
        if (len > maxStringLength) {
            throw new JsonIoException("String too large: " + len + " chars (max: " + maxStringLength + ")");
        }

        output.write('"');
        int last = 0;

        // GSON-style loop: single array lookup determines both "needs escape?" and "what is escape?"
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            String escape;

            if (ch < 128) {
                // ASCII: use pre-computed escape table
                escape = ESCAPE_STRINGS[ch];
                if (escape == null) {
                    continue;  // No escape needed - most common path
                }
            } else if (ch == '\u2028') {
                // Unicode line separator - escape for JavaScript compatibility
                escape = "\\u2028";
            } else if (ch == '\u2029') {
                // Unicode paragraph separator - escape for JavaScript compatibility
                escape = "\\u2029";
            } else {
                continue;  // Non-ASCII characters written as-is (UTF-8 handled by Writer)
            }

            // Write accumulated safe characters, then the escape
            if (last < i) {
                output.write(s, last, i - last);
            }
            output.write(escape);
            last = i + 1;
        }

        // Write remaining safe characters
        if (last < len) {
            output.write(s, last, len - last);
        }
        output.write('"');
    }

    /**
     * Writes a string value with JSON5 smart quote selection if enabled.
     * When json5SmartQuotes is enabled:
     * - Uses single quotes if the string contains " but no '
     * - Uses double quotes otherwise (standard behavior)
     *
     * @param s The string value to write
     * @throws IOException If an I/O error occurs
     */
    private void writeStringValue(String s) throws IOException {
        if (!json5SmartQuotes) {
            writeJsonUtf8String(out, s, maxStringLength);
            return;
        }

        // Scan string to determine quote strategy
        boolean hasDoubleQuote = false;
        boolean hasSingleQuote = false;
        int len = s != null ? s.length() : 0;

        for (int i = 0; i < len && !(hasDoubleQuote && hasSingleQuote); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                hasDoubleQuote = true;
            } else if (c == '\'') {
                hasSingleQuote = true;
            }
        }

        // Use single quotes only if string has " but no '
        if (hasDoubleQuote && !hasSingleQuote) {
            writeSingleQuotedString(out, s, maxStringLength);
        } else {
            writeJsonUtf8String(out, s, maxStringLength);
        }
    }

    /**
     * Writes a JSON5 single-quoted string value to the output, properly escaped.
     * In single-quoted strings, single quotes are escaped and double quotes are not.
     *
     * @param output          The Writer to write to
     * @param s               The string to be written
     * @param maxStringLength Maximum allowed string length
     * @throws IOException If an I/O error occurs
     */
    public static void writeSingleQuotedString(final Writer output, String s, int maxStringLength) throws IOException {
        if (output == null) {
            throw new JsonIoException("Output writer cannot be null");
        }

        if (s == null) {
            output.write("null");
            return;
        }

        output.write('\'');
        final int len = s.length();

        if (len > maxStringLength) {
            throw new JsonIoException("String too large for JSON serialization: " + len + " characters. Maximum allowed: " + maxStringLength);
        }

        int start = 0;

        for (int i = 0; i < len; ) {
            char ch = s.charAt(i);

            // Fast path: ASCII characters that don't need escaping (for single-quoted strings)
            if (ch < 128 && !NEEDS_ESCAPE_SINGLE_QUOTE[ch]) {
                i++;
                continue;
            }

            // Write any accumulated safe characters
            if (i > start) {
                output.write(s, start, i - start);
            }

            int codePoint = s.codePointAt(i);

            if (codePoint < 0x20 || codePoint == 0x7F) {
                // Control characters
                switch (codePoint) {
                    case '\b':
                        output.write("\\b");
                        break;
                    case '\f':
                        output.write("\\f");
                        break;
                    case '\n':
                        output.write("\\n");
                        break;
                    case '\r':
                        output.write("\\r");
                        break;
                    case '\t':
                        output.write("\\t");
                        break;
                    default:
                        output.write(String.format("\\u%04x", codePoint));
                }
            } else if (codePoint == '\'') {
                output.write("\\'");
            } else if (codePoint == '\\') {
                output.write("\\\\");
            } else if (codePoint >= 0x80 && codePoint <= 0xFFFF) {
                output.write(s, i, Character.charCount(codePoint));
            } else if (codePoint > 0xFFFF) {
                output.write(s, i, Character.charCount(codePoint));
            }

            i += Character.charCount(codePoint);
            start = i;
        }

        if (start < len) {
            output.write(s, start, len - start);
        }

        output.write('\'');
    }

    // ======================== Context Stack Management ========================

    /**
     * Writes a comma if the current context requires it.
     * This is called before writing array elements or object start/array start in array context.
     *
     * @throws IOException if an error occurs writing to the output stream
     */
    private void writeCommaIfNeeded() throws IOException {
        WriteContext ctx = contextStack.peek();
        if (ctx == WriteContext.OBJECT_FIELD || ctx == WriteContext.ARRAY_ELEMENT) {
            out.write(',');
        }
    }

    /**
     * Updates the context after writing a value in an array.
     * Transitions ARRAY_EMPTY to ARRAY_ELEMENT.
     */
    private void markArrayElementWritten() {
        if (!contextStack.isEmpty()) {
            WriteContext ctx = contextStack.peek();
            if (ctx == WriteContext.ARRAY_EMPTY) {
                contextStack.pop();
                contextStack.push(WriteContext.ARRAY_ELEMENT);
            }
        }
    }

    /**
     * Updates the context after writing a field in an object.
     * Transitions OBJECT_EMPTY to OBJECT_FIELD.
     */
    private void markObjectFieldWritten() {
        if (!contextStack.isEmpty()) {
            WriteContext ctx = contextStack.peek();
            if (ctx == WriteContext.OBJECT_EMPTY) {
                contextStack.pop();
                contextStack.push(WriteContext.OBJECT_FIELD);
            }
        }
    }

    // ======================== Semantic Write API Implementation ========================

    /**
     * Writes a JSON field name followed by a colon.
     * Example: writeFieldName("name") produces "name":
     * Automatically writes a comma before the field name if this is not the first field.
     */
    @Override
    public void writeFieldName(String name) throws IOException {
        WriteContext ctx = contextStack.peek();
        if (ctx == WriteContext.OBJECT_FIELD) {
            out.write(',');
        }
        writeKey(name);
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON string field with automatic comma handling.
     * Example: writeStringField("name", "John") produces ,"name":"John"
     * <p>
     * This method writes a LEADING comma, making it suitable for fields after the first field.
     * For the first field in an object, either omit the comma manually or use writeFieldName()
     * followed by the value.
     */
    @Override
    public void writeStringField(String name, String value) throws IOException {
        out.write(',');
        writeKey(name);
        if (value == null) {
            out.write("null");
        } else {
            writeStringValue(value);
        }
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON object field with automatic serialization and comma handling.
     * Example: writeObjectField("address", addressObj) produces ,"address":{...}
     * <p>
     * This method writes a LEADING comma, making it suitable for fields after the first field.
     * For the first field in an object, either omit the comma manually or use writeFieldName()
     * followed by writeImpl().
     */
    @Override
    public void writeObjectField(String name, Object value) throws IOException {
        out.write(',');
        writeKey(name);
        writeImpl(value, true);
        markObjectFieldWritten();
    }

    /**
     * Writes a JSON object opening brace.
     * Automatically writes a comma if needed based on context.
     */
    @Override
    public void writeStartObject() throws IOException {
        writeCommaIfNeeded();
        out.write('{');
        markArrayElementWritten();
        contextStack.push(WriteContext.OBJECT_EMPTY);
    }

    /**
     * Writes a JSON object closing brace.
     * Pops the object context from the stack.
     */
    @Override
    public void writeEndObject() throws IOException {
        out.write('}');
        if (!contextStack.isEmpty()) {
            contextStack.pop();
        }
    }

    /**
     * Writes a JSON array opening bracket.
     * Automatically writes a comma if needed based on context.
     */
    @Override
    public void writeStartArray() throws IOException {
        writeCommaIfNeeded();
        out.write('[');
        markArrayElementWritten();
        contextStack.push(WriteContext.ARRAY_EMPTY);
    }

    /**
     * Writes a JSON array closing bracket.
     * Pops the array context from the stack.
     */
    @Override
    public void writeEndArray() throws IOException {
        out.write(']');
        if (!contextStack.isEmpty()) {
            contextStack.pop();
        }
    }

    /**
     * Writes a JSON string value with proper quote escaping.
     * Example: writeValue("Hello") produces "Hello"
     * Automatically writes a comma if this is an array element and not the first element.
     */
    @Override
    public void writeValue(String value) throws IOException {
        writeCommaIfNeeded();
        if (value == null) {
            out.write("null");
        } else {
            writeStringValue(value);
        }
        markArrayElementWritten();
    }

    /**
     * Writes a JSON value by serializing the given object.
     * Example: writeValue(myObject) produces the full JSON representation
     * Automatically writes a comma if this is an array element and not the first element.
     */
    @Override
    public void writeValue(Object value) throws IOException {
        writeCommaIfNeeded();
        writeImpl(value, true);
        markArrayElementWritten();
    }

    /**
     * Writes a complete JSON array field start with automatic comma handling.
     * Example: writeArrayFieldStart("items") produces ,"items":[
     * Manages context by marking the object field written and pushing array context.
     */
    @Override
    public void writeArrayFieldStart(String name) throws IOException {
        out.write(',');
        writeKey(name);
        out.write('[');
        markObjectFieldWritten();
        contextStack.push(WriteContext.ARRAY_EMPTY);
    }

    /**
     * Writes a complete JSON object field start with automatic comma handling.
     * Example: writeObjectFieldStart("config") produces ,"config":{
     * Manages context by marking the object field written and pushing object context.
     */
    @Override
    public void writeObjectFieldStart(String name) throws IOException {
        out.write(',');
        writeKey(name);
        out.write('{');
        markObjectFieldWritten();
        contextStack.push(WriteContext.OBJECT_EMPTY);
    }

    /**
     * Writes a complete JSON number field with automatic comma handling.
     * Example: writeNumberField("count", 42) produces ,"count":42
     */
    @Override
    public void writeNumberField(String name, Number value) throws IOException {
        out.write(',');
        writeKey(name);
        if (value == null) {
            out.write("null");
        } else {
            out.write(value.toString());
        }
        markObjectFieldWritten();
    }

    /**
     * Writes a complete JSON boolean field with automatic comma handling.
     * Example: writeBooleanField("active", true) produces ,"active":true
     */
    @Override
    public void writeBooleanField(String name, boolean value) throws IOException {
        out.write(',');
        writeKey(name);
        out.write(value ? "true" : "false");
        markObjectFieldWritten();
    }

    // ======================== JSON5 Support Methods ========================

    /**
     * Writes a JSON object key, optionally without quotes if JSON5 unquoted keys are enabled
     * and the key is a valid ECMAScript identifier.
     * @param name the key name to write
     */
    private void writeKey(String name) throws IOException {
        if (json5UnquotedKeys && isValidJson5Identifier(name)) {
            out.write(name);
            out.write(':');
        } else {
            writeJsonUtf8String(out, name, maxStringLength);
            out.write(':');
        }
    }

    /**
     * Check if a string is a valid ECMAScript identifier that can be used as an unquoted key in JSON5.
     * Per JSON5 spec, identifiers follow ECMAScript 5.1 IdentifierName production:
     * - Must start with a letter (a-z, A-Z), underscore (_), or dollar sign ($)
     * - Subsequent characters can also include digits (0-9)
     * - Must not be empty
     * @param name the string to check
     * @return true if the string is a valid JSON5 unquoted identifier
     */
    private static boolean isValidJson5Identifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        char first = name.charAt(0);
        if (!isIdentifierStart(first)) {
            return false;
        }

        for (int i = 1; i < name.length(); i++) {
            if (!isIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if character is a valid ECMAScript identifier start character.
     */
    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    /**
     * Check if character is a valid ECMAScript identifier part character.
     */
    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
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
