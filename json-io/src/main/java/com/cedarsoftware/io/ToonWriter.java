package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.lang.reflect.Method;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AnnotationResolver;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.FastWriter;
import com.cedarsoftware.util.IOUtilities;
import com.cedarsoftware.util.MathUtilities;
import com.cedarsoftware.util.internal.DoubleDecimal;
import com.cedarsoftware.util.internal.FloatDecimal;

/**
 * Output a Java object graph in TOON (Token-Oriented Object Notation) format.
 * <p>
 * TOON is a compact, human-readable format optimized for LLM token efficiency,
 * using approximately 40-50% fewer tokens than equivalent JSON.
 * <p>
 * Key TOON characteristics:
 * <ul>
 *   <li>Indentation-based structure (no braces/brackets)</li>
 *   <li>Tabular arrays with CSV-like rows: {@code items[2]{id,name}: 1,Alice 2,Bob}</li>
 *   <li>Minimal quoting (only when necessary)</li>
 *   <li>Key: value syntax for objects</li>
 * </ul>
 * <p>
 * Example TOON output:
 * <pre>
 * name: John
 * age: 30
 * address:
 *   city: NYC
 *   zip: 10001
 * tags[3]: java,json,toon
 * </pre>
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
public class ToonWriter implements Closeable, Flushable {

    private static final String NEW_LINE = "\n";  // TOON spec requires LF only (not CRLF)
    private static final String INDENT = "  ";     // 2 spaces per indent level (TOON default)
    private static final int INDENT_CACHE_SIZE = 32;
    private static final String[] INDENT_CACHE = buildIndentCache();
    private static final int VISITED = 1;
    // In the trace's visited map: an object written as the value of its @IoValue method (see pushValueOf)
    private static final int VALUE_HOLDER = -1;
    private static final int QUOTE_DECISION_CACHE_MAX = 4096;
    private static final int DECIMAL_FORMAT_CACHE_MAX = 4096;
    private static final int SMALL_LONG_CACHE_LOW = -128;
    private static final int SMALL_LONG_CACHE_HIGH = 16384;
    private static final String[] SMALL_LONG_STRINGS = buildSmallLongStringCache();
    private static final Map<String, Boolean> SHARED_QUOTE_DECISION_CACHE_COMMA = new ConcurrentHashMap<>(1024);
    private static final Map<String, Boolean> SHARED_QUOTE_DECISION_CACHE_TAB = new ConcurrentHashMap<>(512);
    private static final Map<String, Boolean> SHARED_QUOTE_DECISION_CACHE_PIPE = new ConcurrentHashMap<>(512);
    private static final AtomicInteger SHARED_QUOTE_DECISION_CACHE_SIZE_COMMA = new AtomicInteger();
    private static final AtomicInteger SHARED_QUOTE_DECISION_CACHE_SIZE_TAB = new AtomicInteger();
    private static final AtomicInteger SHARED_QUOTE_DECISION_CACHE_SIZE_PIPE = new AtomicInteger();

    // Per-delimiter lookup tables: true at index c means char c requires quoting.
    // Merges control-char check (c < 32) and special-char branches into a single array access.
    private static final boolean[] MUST_QUOTE_COMMA = buildMustQuoteTable(',');
    private static final boolean[] MUST_QUOTE_TAB = buildMustQuoteTable('\t');
    private static final boolean[] MUST_QUOTE_PIPE = buildMustQuoteTable('|');

    /**
     * ASCII lookup table for the §7.3 strict-identifier key-quoting rule. An unquoted key
     * MUST match {@code ^[A-Za-z_][A-Za-z0-9_.]*$}, so any character outside
     * {@code [A-Za-z0-9_]} forces quoting. (Dot is handled at the caller via the keyFolding
     * flag — when folding is on, dots are part of the path syntax and may be unquoted;
     * when off, dotted keys are quoted to avoid round-trip ambiguity with path-expansion
     * decoders.) Non-ASCII characters (codepoint &ge; 128) always force quoting per §7.3
     * — there is no allowance for letters outside ASCII in the unquoted-key grammar.
     */
    private static final boolean[] KEY_MUST_QUOTE_CHAR = buildKeyMustQuoteTable();

    private static boolean[] buildMustQuoteTable(char delim) {
        boolean[] table = new boolean[128];
        for (int i = 0; i < 32; i++) {
            table[i] = true;   // control chars (covers \n, \r, \t, etc.)
        }
        table[':'] = true;
        table['"'] = true;
        table['\\'] = true;
        table['['] = true;
        table[']'] = true;
        table['{'] = true;
        table['}'] = true;
        table[delim] = true;
        return table;
    }

    private static boolean[] buildKeyMustQuoteTable() {
        boolean[] table = new boolean[128];
        // Every ASCII codepoint outside [A-Za-z0-9_] forces key quoting per §7.3.
        // Dot ('.') is part of the unquoted-key grammar and is OFF in this table. The caller
        // quotes a dotted key only under toonLegacyForms without toonKeyFolding, as json-io
        // 4.113.0 wrote it.
        for (int i = 0; i < 128; i++) {
            boolean identifierChar = (i >= 'A' && i <= 'Z')
                                  || (i >= 'a' && i <= 'z')
                                  || (i >= '0' && i <= '9')
                                  || i == '_';
            table[i] = !identifierChar;
        }
        return table;
    }

    /**
     * Compute whether a POJO field key needs TOON quoting, given the target WriteOptions.
     * Called once at {@link WriteOptionsBuilder.WriteFieldPlan} build time so the runtime
     * write path can read a precomputed boolean instead of probing the shared
     * quoteDecisionCache (a {@link ConcurrentHashMap}) on every field. Captures the complete
     * decision: empty check, dot-in-key check (when keyFolding is off), reserved-literal
     * check, and the single-pass quote scan against the delimiter-appropriate lookup table.
     */
    static boolean computeKeyNeedsQuoting(String key, WriteOptions options) {
        if (key == null || key.isEmpty()) {
            return true;
        }
        // §7.3: unquoted keys must match ^[A-Za-z_][A-Za-z0-9_.]*$.
        // First char must be a letter or underscore (digit/dot/anything else forces quoting).
        char first = key.charAt(0);
        if (!isIdentifierStart(first)) {
            return true;
        }
        // Dots are part of the §7.3 unquoted-key grammar: a.b is a key like another, written without quotes.
        // json-io 4.113.0 and earlier quoted a dotted key unless toonKeyFolding was on, to keep it from a
        // reader that expands paths (toonExpandPaths); under toonLegacyForms it is still written that way.
        boolean quoteDots = options.isToonLegacyForms() && !options.isToonKeyFolding();
        int len = key.length();
        for (int i = 1; i < len; i++) {
            char c = key.charAt(i);
            if (c == '.') {
                if (quoteDots) {
                    return true;
                }
                continue;
            }
            if (c >= 128 || KEY_MUST_QUOTE_CHAR[c]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_';
    }
    private static final Map<Long, String> SHARED_DOUBLE_FORMAT_CACHE = new ConcurrentHashMap<>(1024);
    private static final Map<Integer, String> SHARED_FLOAT_FORMAT_CACHE = new ConcurrentHashMap<>(512);
    private static final int COUNT_MARKER_CACHE_SIZE = 256;
    private static final String[] COUNT_MARKER_CACHE_COMMA = buildCountMarkerCache(',');
    private static final String[] COUNT_MARKER_CACHE_TAB = buildCountMarkerCache('\t');
    private static final String[] COUNT_MARKER_CACHE_PIPE = buildCountMarkerCache('|');

    // Cached type dispatch enum - mirrors JsonWriter's writeTypeCache pattern.
    // Computed once per class, avoids repeated instanceof + Converter.isConversionSupportedFor() calls.
    // DATA: an object its custom writer gives as data (JsonClassWriter.toData), written as that data: a Map as the
    // object's fields, anything else in the object's place. Depends on the writers in the WriteOptions, so it comes
    // from the options' own cache (DefaultWriteOptions.getToonWriteType); the static cache below never holds it.
    enum ToonWriteType {
        ARRAY, COLLECTION, MAP, STRING, NUMBER, BOOLEAN, CHARACTER,
        CONVERTER_SUPPORTED, POJO, DATA
    }

    /** How TOON writes {@code type} whatever the custom writers: every category but DATA. */
    static ToonWriteType writeTypeOf(Class<?> type) {
        if (type.isArray()) return ToonWriteType.ARRAY;
        if (Collection.class.isAssignableFrom(type)) return ToonWriteType.COLLECTION;
        if (Map.class.isAssignableFrom(type)) return ToonWriteType.MAP;
        if (type == String.class) return ToonWriteType.STRING;
        if (Number.class.isAssignableFrom(type)) return ToonWriteType.NUMBER;
        if (type == Boolean.class) return ToonWriteType.BOOLEAN;
        if (type == Character.class) return ToonWriteType.CHARACTER;
        // An enum with an @IoValue (or @JsonValue) method is written as that value (DATA), as any such class is
        if (Enum.class.isAssignableFrom(type) && AnnotationResolver.getMetadata(type).getValueMethod() != null) {
            return ToonWriteType.POJO;
        }
        if (Converter.isConversionSupportedFor(type, String.class)) return ToonWriteType.CONVERTER_SUPPORTED;
        return ToonWriteType.POJO;
    }

    private static final ClassValue<ToonWriteType> writeTypeCache = new ClassValue<ToonWriteType>() {
        @Override
        protected ToonWriteType computeValue(Class<?> type) {
            return writeTypeOf(type);
        }
    };

    /**
     * True when TOON writes objects of {@code type} as the data their custom writer gives (JsonClassWriter.toData), or
     * as the value their {@code @IoValue} method returns, in their place, as JSON writes them: "user@example.com"
     * rather than {address: user@example.com}. Asked only of what the static category calls a POJO: the hot switches
     * below stay on the static cache, so the other values never pay for it (and the methods holding those switches
     * stay small enough to inline).
     */
    private boolean isDataClass(Class<?> type) {
        if (writeTypeCache.get(type) != ToonWriteType.POJO) {
            return false;
        }
        if (defaultWriteOptions != null) {
            return defaultWriteOptions.getToonWriteType(type) == ToonWriteType.DATA;
        }
        return WriteOptionsBuilder.getDataWriter(writeOptions, type) != null
                || WriteOptionsBuilder.findValueMethod(writeOptions, type) != null || Enum.class.isAssignableFrom(type);
    }

    // The last object asked for its data, and that data: isPrimitive and the writer ask in turn for the same object
    private Object dataSource;
    private Object data;

    /**
     * The data {@code obj}'s custom writer gives for it (a DATA object): a Map, another value, or NO_DATA; for a class
     * without one, the value its {@code @IoValue} method returns.
     */
    private Object dataOf(Object obj) {
        if (obj != dataSource) {
            JsonClassWriter writer = WriteOptionsBuilder.getDataWriter(writeOptions, obj.getClass());
            data = writer != null ? writer.toData(obj) : valueOf(obj);
            dataSource = obj;
        }
        return data;
    }

    /** The value {@code holder}'s {@code @IoValue} method returns. */
    private Object valueOf(Object holder) {
        Class<?> c = holder.getClass();
        Method valueMethod = WriteOptionsBuilder.getValueMethod(writeOptions, c,
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, c));
        if (valueMethod == null && holder instanceof Enum) {
            return ((Enum<?>) holder).name();      // a custom writer set aside its @IoValue method: its name, as before
        }
        try {
            return valueMethod.invoke(holder);
        } catch (Exception e) {
            throw new JsonIoException("@IoValue method invocation failed for " + c.getName(), e);
        }
    }

    /** True for data written in the object's place (an Optional's content) rather than as its fields. */
    private static boolean isInPlace(Object data) {
        return data != JsonClassWriter.NO_DATA && !(data instanceof Map);
    }

    /** What is written in {@code value}'s place: its data when that is not a Map of fields, else {@code value}. */
    private Object inPlaceOf(Object value) {
        if (value == null || value instanceof Map || !isDataClass(value.getClass())) {
            return value;
        }
        Object d = dataOf(value);
        return isInPlace(d) ? d : value;
    }

    /** An object written as the fields its custom writer gives (a Map from toData). */
    private boolean isWrittenAsDataFields(Object obj) {
        return isDataClass(obj.getClass()) && dataOf(obj) instanceof Map;
    }

    private final WriteOptions writeOptions;
    private final WriteOptionsBuilder.DefaultWriteOptions defaultWriteOptions;   // null for other implementations
    private final Writer out;
    private final char delimiter;  // Default comma, configurable to pipe or tab
    private final Map<String, Boolean> quoteDecisionCache;
    private final AtomicInteger quoteDecisionCacheSize;
    private final boolean[] mustQuoteChar;  // Per-delimiter lookup table for single-pass quoting scan
    private final boolean cycleSupport;
    private final boolean skipNullFields;
    private final boolean toonKeyFolding;
    private final boolean v4Forms;          // the forms of TOON spec v4: keyed tables, nested field groups (not legacy)
    private boolean onHyphenLine;           // the next line is already begun, by a list item's "- ": no indent
    private final boolean prettyPrint;
    // Set for the one array written next, on a list item's hyphen line with no key: such an array is never a table
    // (spec 9.4: a keyless header with a field list stands only at the root). Taken by the array's writer.
    private boolean keylessListItemArray;
    private final String indentUnit;       // single indent level (e.g. "  " for size 2)
    private final String[] indentCache;    // per-instance cache for non-default indent sizes
    private final boolean enumPublicFieldsOnly;
    // Per-field @IoShowType — forces $type emission for the field value and its elements
    private boolean forceShowType = false;
    private final String idKey;
    private final String refKey;
    private final String itemsKey;
    private final String keysKey;
    private final String typeKey;
    private final boolean typeMetadataEnabled;
    private final boolean alwaysType;       // showTypeInfoAlways: $type on every value but the four TOON reads as is
    private final boolean minimalPlusType;  // showTypeInfoMinimalPlus: also leaves off what JSON's minimal-plus does
    private final boolean rootType;         // $type on the root (always, or minimal unless omitRootTypeInfo)
    // Type info on: the declared element (Map: value) and key types of the container about to be written, from its
    // field's generics. Set around a field's write; a container takes them on entry, so anything inside sees Object.
    private Class<?> elementType = Object.class;
    private Class<?> keyType = Object.class;
    private int depth = 0;
    private int nextIdentity = 1;

    // Allocated lazily based on cycleSupport mode to avoid ~6 KB of wasted arrays
    // when cycle support is disabled (the default for TOON).
    private IdentityIntMap objVisited;       // cycleSupport=true: track visited objects
    private IdentityIntMap objsReferenced;   // cycleSupport=true: track shared references
    private Map<Object, Boolean> activePath; // cycleSupport=false: detect cycles via active path

    // Set by plan-aware callers (writeObjectFields, writeObjectInline) before dispatching
    // a Map field value; read by writeMap to skip the hasComplexKeys() iteration when the
    // field's declared key type is known "simple" (Converter-stringifiable). Cleared after
    // the call. Non-plan paths (e.g., Map as a direct root, or nested inside Collection/Array
    // element iteration) leave this false, preserving the hasComplexKeys scan.
    private boolean currentMapHasSimpleKeyTypeHint = false;
    // §13.4 safe-mode folding: the set of LITERAL sibling keys at the depth currently being
    // written. When a folded chain's computed dotted path would equal one of these literal
    // keys, folding MUST be skipped to avoid collision. Set by writeMapWithSimpleKeys when
    // keyFolding is enabled; null when folding is off (no overhead in the common case).
    private java.util.Set<String> currentSiblingLiteralKeys;
    // Set true while emitting a chain whose root-level fold collided with a literal sibling
    // (§13.4 safe mode). Suppresses folding in the subtree so the entire blocked chain emits
    // unfolded — matching ref-impl behavior. Reset when the suppressed branch closes.
    private boolean foldingSuppressed = false;
    // Sentinel set by collectFoldedPath when it returns null due to §13.4 sibling-literal
    // collision (as opposed to "chain isn't foldable at all"). Caller reads to decide
    // whether to suppress folding in the nested descent.
    private boolean lastFoldCollision = false;
    private ArrayDeque<Object> traceStack;   // cycleSupport=true: work deque for traceReferences()
    // Cache resolved output type names per class for this write operation.
    private final Map<Class<?>, String> typeNameCache = new IdentityHashMap<>(32);

    private static String[] buildIndentCache() {
        return buildIndentCache(INDENT);
    }

    private static String[] buildIndentCache(String unit) {
        String[] cache = new String[INDENT_CACHE_SIZE];
        StringBuilder builder = new StringBuilder(INDENT_CACHE_SIZE * unit.length());
        for (int i = 0; i < INDENT_CACHE_SIZE; i++) {
            cache[i] = builder.toString();
            builder.append(unit);
        }
        return cache;
    }

    private static String[] buildSmallLongStringCache() {
        int size = SMALL_LONG_CACHE_HIGH - SMALL_LONG_CACHE_LOW + 1;
        String[] cache = new String[size];
        for (int i = 0; i < size; i++) {
            cache[i] = Integer.toString(i + SMALL_LONG_CACHE_LOW);
        }
        return cache;
    }

    private static String toCachedLongString(long value) {
        if (value >= SMALL_LONG_CACHE_LOW && value <= SMALL_LONG_CACHE_HIGH) {
            return SMALL_LONG_STRINGS[(int) (value - SMALL_LONG_CACHE_LOW)];
        }
        return Long.toString(value);
    }

    private static String[] buildCountMarkerCache(char delim) {
        String[] cache = new String[COUNT_MARKER_CACHE_SIZE];
        for (int i = 0; i < COUNT_MARKER_CACHE_SIZE; i++) {
            cache[i] = delim == ',' ? "[" + i + "]" : "[" + i + delim + "]";
        }
        return cache;
    }

    /**
     * Create a ToonWriter that writes to an OutputStream.
     *
     * @param out OutputStream to write TOON content to
     * @param writeOptions configuration options (may be null for defaults)
     */
    public ToonWriter(OutputStream out, WriteOptions writeOptions) {
        this(new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)), writeOptions);
    }

    ToonWriter(Writer out, WriteOptions writeOptions) {
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;
        this.defaultWriteOptions = this.writeOptions instanceof WriteOptionsBuilder.DefaultWriteOptions
                ? (WriteOptionsBuilder.DefaultWriteOptions) this.writeOptions : null;
        this.out = out;
        this.delimiter = this.writeOptions.getToonDelimiter();
        if (delimiter == '\t') {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_TAB;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_TAB;
            this.mustQuoteChar = MUST_QUOTE_TAB;
        } else if (delimiter == '|') {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_PIPE;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_PIPE;
            this.mustQuoteChar = MUST_QUOTE_PIPE;
        } else {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_COMMA;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_COMMA;
            this.mustQuoteChar = MUST_QUOTE_COMMA;
        }
        this.cycleSupport = this.writeOptions.isCycleSupport();
        this.skipNullFields = this.writeOptions.isSkipNullFields();
        this.toonKeyFolding = this.writeOptions.isToonKeyFolding();
        this.v4Forms = !this.writeOptions.isToonLegacyForms();
        this.prettyPrint = this.writeOptions.isPrettyPrint();
        this.enumPublicFieldsOnly = this.writeOptions.isEnumPublicFieldsOnly();

        // §12 honors WriteOptions.indentationSize for TOON output. Default (2) reuses the
        // static INDENT_CACHE; non-default sizes build a per-instance cache so the
        // writeIndent() hot path stays O(1) for typical depths.
        int indentSize = this.writeOptions.getIndentationSize();
        if (indentSize == INDENT.length()) {
            this.indentUnit = INDENT;
            this.indentCache = INDENT_CACHE;
        } else {
            char[] unitChars = new char[indentSize];
            java.util.Arrays.fill(unitChars, ' ');
            this.indentUnit = new String(unitChars);
            this.indentCache = buildIndentCache(this.indentUnit);
        }

        // TOON defaults to '$' meta keys for JSON5-friendly unquoted identifiers.
        char prefix = '$';
        Character override = this.writeOptions.getMetaPrefixOverride();
        if (override != null) {
            prefix = override;
        }
        boolean shortMeta = this.writeOptions.isShortMetaKeys();
        this.idKey = prefix + (shortMeta ? "i" : "id");
        this.refKey = prefix + (shortMeta ? "r" : "ref");
        this.itemsKey = prefix + (shortMeta ? "e" : "items");
        this.keysKey = prefix + (shortMeta ? "k" : "keys");
        this.typeKey = prefix + (shortMeta ? "t" : "type");
        boolean typeModeEnabled = !this.writeOptions.isNeverShowingType()
                && (this.writeOptions.isAlwaysShowingType() || this.writeOptions.isMinimalShowingType());
        boolean typeExplicitlyRequested = !(this.writeOptions instanceof WriteOptionsBuilder.DefaultWriteOptions)
                || ((WriteOptionsBuilder.DefaultWriteOptions) this.writeOptions).isShowTypeInfoExplicitlySet();
        this.typeMetadataEnabled = typeModeEnabled && typeExplicitlyRequested;
        this.alwaysType = typeMetadataEnabled && this.writeOptions.isAlwaysShowingType();
        this.minimalPlusType = typeMetadataEnabled && this.writeOptions.isMinimalPlusShowingType();
        this.rootType = alwaysType || (typeMetadataEnabled && this.writeOptions.isShowingRootTypeInfo());
    }

    /**
     * Return the TOON array count marker string.
     * For comma (default): "[N]"
     * For tab: "[N\t]"  (reader auto-detects via trailing char)
     * For pipe: "[N|]"
     */
    private String countMarker(int count) {
        if (count >= 0 && count < COUNT_MARKER_CACHE_SIZE) {
            if (delimiter == ',') return COUNT_MARKER_CACHE_COMMA[count];
            if (delimiter == '\t') return COUNT_MARKER_CACHE_TAB[count];
            return COUNT_MARKER_CACHE_PIPE[count];
        }
        if (delimiter == ',') {
            return "[" + count + "]";
        }
        return "[" + count + delimiter + "]";
    }

    /**
     * Write the given object as TOON format.
     *
     * @param obj the object to serialize
     * @throws JsonIoException if an error occurs during serialization
     */
    public void write(Object obj) {
        try {
            depth = 0;
            nextIdentity = 1;
            elementType = Object.class;
            keyType = Object.class;
            if (cycleSupport) {
                // Lazy-init cycle-support structures
                if (objVisited == null) {
                    objVisited = new IdentityIntMap(256);
                    objsReferenced = new IdentityIntMap(256);
                    traceStack = new ArrayDeque<>(256);
                } else {
                    objVisited.clear();
                    objsReferenced.clear();
                }
                traceReferences(obj);
                objVisited.clear();
            } else {
                // Lazy-init acyclic path tracker
                if (activePath == null) {
                    activePath = new IdentityHashMap<>();
                } else {
                    activePath.clear();
                }
            }
            keylessListItemArray = false;
            if (obj instanceof String && ((String) obj).startsWith("\uFEFF")) {
                // Spec 7.2: without quotes the U+FEFF would be the first character of the document, which a
                // reader removes as a byte-order mark (spec 12). Anywhere but at the root it needs none.
                writeQuotedString((String) obj);
            } else if (v4Forms && writeRootKeyedTable(obj)) {
                // spec 9.5: a root object of uniform objects is a keyed table with no key
            } else if (typeMetadataEnabled) {
                writeRoot(obj);
            } else {
                writeValue(obj);
            }
            out.flush();
        } catch (IOException e) {
            throw new JsonIoException("Error writing TOON output", e);
        } finally {
            if (objVisited != null) objVisited.clear();
            if (objsReferenced != null) objsReferenced.clear();
            if (activePath != null) activePath.clear();
            nextIdentity = 1;
        }
    }

    /**
     * Write any value - dispatches to appropriate handler based on type.
     * Uses ClassValue cache for O(1) type dispatch (avoids repeated instanceof + Converter checks).
     */
    private void writeValue(Object value) throws IOException {
        if (value == null) {
            out.write("null");
            return;
        }

        // Fast path: common primitive wrappers detected via cheap instanceof, skipping the
        // ClassValue (writeTypeCache) lookup entirely. Integer/Long go directly to
        // toCachedLongString without the writeNumber method call and its 6-wide instanceof
        // chain (Double/Float/BigDecimal/BigInteger/AtomicInteger/AtomicLong all miss
        // before the common case). JFR previously showed the ClassValue dispatch was ~6.6%
        // of ToonWriter samples, and writeNumber's per-call dispatch contributed additional
        // samples for every int/long field. These short-circuits cover the common POJO-field
        // case without disturbing the slow path for CONVERTER_SUPPORTED / POJO / DATA
        // types.
        if (value instanceof String) {
            writeString((String) value);
            return;
        }
        if (value instanceof Integer) {
            out.write(toCachedLongString((Integer) value));
            return;
        }
        if (value instanceof Long) {
            out.write(toCachedLongString((Long) value));
            return;
        }
        if (value instanceof Number) {
            writeNumber((Number) value);
            return;
        }
        if (value instanceof Boolean) {
            out.write(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof Character) {
            writeString(String.valueOf(value));
            return;
        }

        // Unwrap a resolved-Maps-graph carrier (JsonObjectArray / JsonObjectMap) to a plain
        // container so its @items/@keys payload is written instead of an empty body.
        value = unwrapCarrier(value);

        Class<?> clazz = value.getClass();

        switch (writeTypeCache.get(clazz)) {
            case ARRAY:
                writeArray(value, needsType(clazz, Object.class));
                break;
            case COLLECTION:
                writeCollection((Collection<?>) value, needsType(clazz, Object.class));
                break;
            case MAP:
                writeMap((Map<?, ?>) value, needsType(clazz, Object.class));
                break;
            case STRING:
                writeString((String) value);
                break;
            case NUMBER:
                writeNumber((Number) value);
                break;
            case BOOLEAN:
                out.write(((Boolean) value) ? "true" : "false");
                break;
            case CHARACTER:
                writeString(String.valueOf(value));
                break;
            case CONVERTER_SUPPORTED: {
                // Temporal fast path: hand-rolled ISO emit (TemporalChars) instead of
                // Converter -> DateTimeFormatter (JFR: ~11% of toon-write CPU). The
                // String still flows through writeString, so TOON quoting semantics
                // are unchanged; non-temporals and exotic years take the Converter path.
                writeString(converterText(value));
                break;
            }
            case POJO:
                writePojo(value, needsType(clazz, Object.class));
                break;
        }
    }

    /**
     * Type info on: the root, with $type (unless omitRootTypeInfo(): then only on what it contains that the reader
     * could not build from the class it is asked for).
     */
    private void writeRoot(Object obj) throws IOException {
        Object value = unwrapCarrier(obj);
        if (value == null) {
            writeValue(null);
            return;
        }
        Class<?> declared = rootType ? Object.class : value.getClass();
        value = typedForm(value, declared);
        Class<?> clazz = value.getClass();
        boolean typed = needsType(clazz, declared);
        switch (writeTypeCache.get(clazz)) {
            case ARRAY:
                writeArray(value, typed);
                break;
            case COLLECTION:
                writeCollection((Collection<?>) value, typed);
                break;
            case MAP:
                writeMap((Map<?, ?>) value, typed);
                break;
            case POJO:
                writePojo(value, typed);
                break;
            default:
                writeValue(value);
                break;
        }
    }

    /** An object: as the data its custom writer gives in its place (an Optional's content), else its fields. */
    private void writePojo(Object value, boolean typed) throws IOException {
        if (isDataClass(value.getClass())) {
            Object d = dataOf(value);
            if (isInPlace(d)) {
                Object form = typeMetadataEnabled ? typedForm(d, contentTypeOf(value)) : d;
                if (form != d) {
                    writeMap((Map<?, ?>) form, false);     // {$type, value}: a whole-number Double's, say
                } else {
                    writeValue(d);
                }
                return;
            }
        }
        writeObject(value, typed);
    }

    private boolean isReferenceable(Object value) {
        if (value == null || isPrimitive(value)) {
            return false;
        }
        // When cycleSupport is off, skip the NonReferenceableClass lookup — it's only needed
        // for $id/$ref decisions. Cycle detection via activePath only needs the isPrimitive() filter.
        return !cycleSupport || !writeOptions.isNonReferenceableClass(value.getClass());
    }

    private boolean isReferenceContainer(Object value) {
        return value instanceof Collection || (value != null && value.getClass().isArray());
    }

    private boolean isReferenced(Object value) {
        return cycleSupport && objsReferenced.get(value) != 0;
    }

    private int getReferenceId(Object value) {
        return objsReferenced.get(value);
    }

    private void writeReferenceValue(int refId) throws IOException {
        out.write(refKey);
        out.write(':'); out.write(' ');
        out.write(Integer.toString(refId));
    }

    private void writeIdField(Object value) throws IOException {
        out.write(idKey);
        out.write(':'); out.write(' ');
        out.write(Integer.toString(getReferenceId(value)));
    }

    // ---------- Type info: showTypeInfoMinimal() / showTypeInfoMinimalPlus() / showTypeInfoAlways() ----------

    // The declared type of a value written as itself, never with $type: a typed scalar's value, an object's $id, a
    // custom writer's scalars. A primitive type, so needsType answers no in every mode.
    private static final Class<?> AS_IS = void.class;

    /**
     * Whether a value of class {@code c}, written where the reader expects {@code declared} (Object where nothing is
     * declared: the root, an element of a raw list, a map's value), carries $type.
     * <ul>
     *   <li>Minimal: only where the declared type does not determine the class the reader builds -- not for the
     *       declared class itself, an enum constant of the declared enum, or the class the reader builds for a declared
     *       interface (ArrayList for List, LinkedHashMap for Map, ...).</li>
     *   <li>Minimal-plus: nor where JSON's minimal-plus leaves it off -- an int, short, byte or float in an Object slot
     *       (read back as a long or a double), one date or number type where another is declared (converted).</li>
     *   <li>Always: on every value.</li>
     * </ul>
     * Never on a String, Long, Double or Boolean class, which TOON reads back as themselves (but see
     * {@link #needsType(Object, Class)} for a whole-number Double), nor where a primitive is
     * declared, nor on json-io's own carriers (JsonObject, JsonObjectMap, JsonObjectArray, from re-writing a Maps
     * graph), which are not a type the reader builds. With type info off, only under {@code @IoShowType}, and only
     * on objects and containers.
     */
    private boolean needsType(Class<?> c, Class<?> declared) {
        if (!typeMetadataEnabled) {
            return forceShowType && !isPrimitiveClass(c) && !JsonObject.class.isAssignableFrom(c);
        }
        if (c == String.class || c == Long.class || c == Double.class || c == Boolean.class
                || declared.isPrimitive() || JsonObject.class.isAssignableFrom(c)) {
            return false;
        }
        if (alwaysType || forceShowType) {
            return true;
        }
        // Object, the most common declaration (a map's value, a raw list's element), has no natural default and is no
        // enum: skip both lookups for it
        if (c == declared || (declared != Object.class && (JsonWriter.naturalDefault(declared) == c
                || (declared.isEnum() && declared.isAssignableFrom(c))))) {
            return false;
        }
        return !minimalPlusType
                || !((declared == Object.class && JsonWriter.isNumericPrimitiveForCompact(c))
                    || (JsonWriter.isConvertableType(declared) && JsonWriter.isConvertableType(c)));
    }

    /**
     * {@link #needsType(Class, Class)} for a value at hand: also a Double or Float that TOON writes as a whole number
     * (1.0 as 1, 1e23 and Double.MAX_VALUE as their digits, -0.0 as 0), which a slot that does not declare its type
     * reads back as a Long or BigInteger. Where double, Double, float or Float is declared the reader converts, so
     * nothing is added; NaN and Infinity are written as null whatever is declared.
     */
    private boolean needsType(Object value, Class<?> declared) {
        Class<?> c = value.getClass();
        if (needsType(c, declared)) {
            return true;
        }
        if (!typeMetadataEnabled || (c != Double.class && c != Float.class) || declared == c
                || !declared.isAssignableFrom(c)) {
            return false;
        }
        double d = ((Number) value).doubleValue();
        return d == Math.rint(d) && !Double.isInfinite(d);
    }

    /**
     * Type info on: what is written for {@code value} where the reader expects {@code declared}. A value whose type
     * is written but which TOON writes as something other than an object becomes that object: a scalar
     * {$type, value}; an object written as the value in its place {$type, value: that value} (an Optional's
     * content); a list or map whose custom writer gives it as data {$type, ...that data} (a CompactMap's
     * configuration and entries, as JSON writes it). Anything else is itself.
     */
    private Object typedForm(Object value, Class<?> declared) {
        if (value == null || value instanceof Fields) {
            return value;
        }
        if (!needsType(value, declared)) {
            // Its custom writer's data even where its class goes without saying, as JSON writes it: a MultiKeyMap's
            // or CompactMap's configuration is its content, not type info
            return value instanceof Map || value instanceof Collection ? containerForm(value, false) : value;
        }
        Object content;
        Object source;
        if (isPrimitive(value)) {
            // TOON writes the number -0 as 0 (spec section 2); as text, -0.0 reads back with its sign
            content = isNegativeZero(value) ? "-0.0" : value;
            source = null;
        } else if (value instanceof char[]) {
            // As JSON writes it: {$type: char[], $items: [its text]}
            Fields fields = new Fields(null);
            fields.put(typeKey, typeNameOf(value), AS_IS);
            fields.put(itemsKey, new String[] {new String((char[]) value)}, AS_IS);
            return fields;
        } else if (value instanceof Map || value instanceof Collection) {
            return containerForm(value, true);
        } else if (isDataClass(value.getClass()) && isInPlace(dataOf(value))) {
            content = dataOf(value);
            source = value;
        } else {
            return value;
        }
        Fields fields = new Fields(source);
        fields.put(typeKey, typeNameOf(value), AS_IS);
        fields.put("value", content, source == null || !GENERIC.get(source.getClass()) ? AS_IS : Object.class);
        return fields;
    }

    /**
     * A list or map whose custom writer gives it as data, as that data ({$type, ...that data} when {@code typed});
     * any other, itself.
     */
    private Object containerForm(Object container, boolean typed) {
        Map<?, ?> data = containerData(container);
        if (data == null) {
            return container;
        }
        Fields fields = new Fields(container);
        if (typed) {
            fields.put(typeKey, typeNameOf(container), AS_IS);
        }
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            fields.put(String.valueOf(entry.getKey()), entry.getValue(), dataType(entry.getValue()));
        }
        return fields;
    }

    /** A list's or map's data from its custom writer (JsonClassWriter.toData) when that is a Map, else null. */
    private Map<?, ?> containerData(Object container) {
        JsonClassWriter writer = WriteOptionsBuilder.getDataWriter(writeOptions, container.getClass());
        Object data = writer == null ? JsonClassWriter.NO_DATA : writer.toData(container);
        return data instanceof Map ? (Map<?, ?>) data : null;
    }

    /**
     * The type the reader expects for a value in a custom writer's data: a scalar, list, map or array as itself (the
     * writer's factory reads it as such, as from the writer's JSON), any other object as Object (so it carries $type).
     */
    private Class<?> dataType(Object value) {
        if (value != null && (isPrimitive(value) || value instanceof Map || value instanceof Collection
                || value.getClass().isArray())) {
            return AS_IS;
        }
        return Object.class;
    }

    /** The name written as $type for {@code value}: an EnumSet's enum class, else its custom writer's name for it. */
    private String typeNameOf(Object value) {
        if (value instanceof EnumSet) {
            return writeOptions.getTypeNameAlias(enumClassOf((EnumSet<?>) value).getName());
        }
        Class<?> c = value.getClass();
        String cached = typeNameCache.get(c);
        if (cached == null) {
            JsonClassWriter writer = writeOptions.isNotCustomWrittenClass(c) ? null : writeOptions.getCustomWriter(c);
            cached = writeOptions.getTypeNameAlias(writer == null ? c.getName() : writer.getTypeName(value));
            typeNameCache.put(c, cached);
        }
        return cached;
    }

    /** The enum an EnumSet holds: what JSON writes as its type, so it reads back at the root, where nothing declares it. */
    private static Class<?> enumClassOf(EnumSet<?> set) {
        EnumSet<?> constants = set.isEmpty() ? EnumSet.complementOf(set) : set;
        return constants.isEmpty() ? set.getClass() : constants.iterator().next().getDeclaringClass();
    }

    /**
     * Type info on: whether a map's keys need the {$keys, $items} form to read back -- some key is not a String, and
     * the declared key type does not make it one (the plain form's keys are text, which the reader converts only to a
     * declared key type).
     */
    private boolean keysNeedTypes(Map<?, ?> map, Class<?> declaredKey) {
        if (!typeMetadataEnabled) {
            return false;
        }
        for (Object key : map.keySet()) {
            if (key != null && !(key instanceof String)
                    && (declaredKey == Object.class || needsType(key, declaredKey))) {
                return true;
            }
        }
        return false;
    }

    /** Type info on: the declared element and key types of the field's value, for a container to take. */
    private void declareContainerTypes(WriteOptionsBuilder.WriteFieldPlan plan) {
        Class<?> element = plan.elementClass();
        Class<?> key = plan.keyClass();
        elementType = element == null ? Object.class : element;
        keyType = key == null ? Object.class : key;
    }

    /** The declared element (Map: value) type for the container being written; what it contains sees Object. */
    private Class<?> takeElementType() {
        Class<?> declared = elementType;
        elementType = Object.class;
        return declared;
    }

    /** The declared key type for the map being written; what it contains sees Object. */
    private Class<?> takeKeyType() {
        Class<?> declared = keyType;
        keyType = Object.class;
        return declared;
    }

    // Classes with a type parameter (Optional<T>): what such a holder holds is not given by its class
    private static final ClassValue<Boolean> GENERIC = new ClassValue<Boolean>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return type.getTypeParameters().length > 0;
        }
    };

    /**
     * Type info on: the type the reader expects for what an object written in its place holds (an Optional's
     * content). A holder with no type parameter (OptionalInt) determines it; a generic one holds what its field's type
     * argument says (Address for Optional&lt;Address&gt;, taken as a container takes its element type), else anything.
     */
    private Class<?> contentTypeOf(Object holder) {
        return GENERIC.get(holder.getClass()) ? takeElementType() : AS_IS;
    }

    /** Type info on: a generic holder written in its place (an Optional in a list) whose content needs its $type. */
    private boolean holdsTypedContent(Object value) {
        if (!GENERIC.get(value.getClass()) || !isDataClass(value.getClass())) {
            return false;
        }
        Object content = dataOf(value);
        return isInPlace(content) && content != null && needsType(content, Object.class);
    }

    /** A map key's quoting: an object's $type, $id and $items (in its Fields) are meta keys, written bare. */
    private boolean keyNeedsQuoting(Map<?, ?> map, String key) {
        return !(map instanceof Fields && (key.equals(typeKey) || key.equals(idKey) || key.equals(itemsKey)))
                && needsQuotingForMapKey(key);
    }

    /** The type the reader expects for a map's value: an object's field's own, else the map's declared value type. */
    private static Class<?> valueTypeIn(Map<?, ?> map, Object key, Class<?> valueType) {
        return map instanceof Fields ? ((Fields) map).typeOf(key) : valueType;
    }

    /** The object a map stands for (a typed scalar, a custom writer's data): its identity for $id, $ref and cycles. */
    private static Object identityOf(Map<?, ?> map) {
        return map instanceof Fields ? ((Fields) map).source : map;
    }

    /**
     * An object's fields by name, each with the type the reader expects for it, written as a map: what json-io writes
     * for an object it does not write field by field (an @IoAnyGetter's fields, a custom writer's data), and the
     * object that carries a value's $type. Its own class is never written; it stands for {@code source}.
     */
    private static final class Fields extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        private final transient Object source;
        private transient Map<String, Class<?>> types;   // null until a type is recorded (type info on)

        Fields(Object source) {
            this.source = source == null ? this : source;
        }

        void put(String key, Object value, Class<?> type) {
            put(key, value);
            if (type != null) {
                if (types == null) {
                    types = new java.util.HashMap<>();
                }
                types.put(key, type);
            }
        }

        Class<?> typeOf(Object key) {
            Class<?> type = recordedType(key);
            return type == null ? Object.class : type;
        }

        Class<?> recordedType(Object key) {
            return types == null ? null : types.get(key);
        }
    }

    private boolean isPrimitiveClass(Class<?> clazz) {
        switch (writeTypeCache.get(clazz)) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
            case CHARACTER:
            case CONVERTER_SUPPORTED:
                return true;
            default:
                return false;
        }
    }

    private void writeTypeField(Object value) throws IOException {
        out.write(typeKey);
        out.write(':'); out.write(' ');
        writeString(typeNameOf(value));
    }

    /**
     * The text written for a value the Converter writes as text: a temporal's ISO form, a ByteBuffer's remaining bytes
     * as Base64 (as JSON writes them -- the Converter gives their text in its charset, which read back as other
     * bytes from 128 up), anything else as the Converter writes it.
     */
    private static String converterText(Object value) {
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) value;
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);      // a duplicate: the buffer's own position does not move
            return Base64.getEncoder().encodeToString(bytes);
        }
        String iso = TemporalChars.toIsoString(value);
        return iso != null ? iso : Converter.convert(value, String.class);
    }

    private boolean writeReferenceIfSeen(Object value) throws IOException {
        if (!cycleSupport || !isReferenceable(value)) {
            return false;
        }
        if (isWrittenAndShared(value)) {
            writeReferenceValue(getReferenceId(value));
            return true;
        }
        objVisited.put(value, VISITED);
        return false;
    }

    /**
     * True when {@code value} was written already and the trace found it shared, so it has an id to refer to. One
     * written already that the trace did not find shared -- a container declared with leaf elements
     * ({@code List<String>}, {@code Map<String, String>}), which the trace skips as JSON does -- is written again, as
     * JSON writes it, rather than referenced as id 0 (which nothing defines) or refused.
     */
    private boolean isWrittenAndShared(Object value) {
        return objVisited.get(value) != 0 && getReferenceId(value) != 0;
    }

    /**
     * An object written as its content, in the content's place (an Optional of a list, with no type info), can carry
     * no $id of its own. When it is shared, its content carries one -- $id where it is first written, $ref after --
     * so the reader gets the content once, held twice. Its content is given an id only before it is first written.
     */
    private void lendIdentityToContent(Object holder, Object content) {
        if (cycleSupport && content != null && isReferenced(holder) && !isReferenced(content)
                && isReferenceable(content) && objVisited.get(content) == 0) {
            objsReferenced.put(content, nextIdentity++);
        }
    }

    private boolean enterActivePath(Object value) {
        if (value == null || !isReferenceable(value)) {
            return true;
        }
        if (activePath.containsKey(value)) {
            return false;
        }
        activePath.put(value, Boolean.TRUE);
        return true;
    }

    private void exitActivePath(Object value) {
        if (value != null && isReferenceable(value)) {
            activePath.remove(value);
        }
    }

    private JsonIoException cycleDetected(Object value) {
        return new JsonIoException("Cycle detected while writing TOON with cycleSupport(false): "
                + value.getClass().getName()
                + ". To write cyclic object graphs, enable cycleSupport(true) on WriteOptions.");
    }

    private boolean shouldWrapArrayOrCollectionValue(Object value) {
        return cycleSupport
                && isReferenceContainer(value)
                && (isReferenced(value) || objVisited.get(value) != 0);
    }

    private Object getArrayElement(Object array, int index) {
        return array instanceof Object[] ? ((Object[]) array)[index] : ArrayUtilities.getElement(array, index);
    }

    /**
     * Write a TOON key. {@code needsQuote} is precomputed — either at
     * {@link WriteOptionsBuilder.WriteFieldPlan} build time for POJO field names
     * (via {@link #computeKeyNeedsQuoting}) or at the call site for dynamic Map
     * keys (via {@link #needsQuotingForMapKey}). This moves the quoting decision
     * off the hot write path; a prior static ConcurrentHashMap lookup was showing
     * as ~11.7% of ToonWriter samples in JFR.
     */
    private void writeKeyStringKnown(String key, boolean needsQuote) throws IOException {
        if (needsQuote) {
            writeQuotedString(key);
        } else {
            out.write(key);
        }
    }

    /**
     * Compute the quoting decision for a dynamic Map key (no WriteFieldPlan available).
     * Mirrors the preflight checks in the original writeKeyString so callers can pass
     * the full decision to writeFieldEntry/writeFieldEntryInline uniformly.
     */
    private boolean needsQuotingForMapKey(String key) {
        // §7.3 strict-identifier rule for keys: must match ^[A-Za-z_][A-Za-z0-9_.]*$.
        // Stricter than the value table (§7.2 allows internal spaces, hyphens, Unicode);
        // a separate path keeps the lenient value rules untouched.
        if (key == null || key.isEmpty()) {
            return true;
        }
        char first = key.charAt(0);
        if (!isIdentifierStart(first)) {
            return true;
        }
        int len = key.length();
        for (int i = 1; i < len; i++) {
            char c = key.charAt(i);
            if (c == '.') {
                if (!v4Forms && !toonKeyFolding) {
                    return true;        // as json-io 4.113.0 wrote it: see computeKeyNeedsQuoting
                }
                continue;
            }
            if (c >= 128 || KEY_MUST_QUOTE_CHAR[c]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Write a quoted string with escaping.
     */
    private void writeQuotedString(String str) throws IOException {
        out.write('"');
        writeEscapedString(str);
        out.write('"');
    }

    /**
     * Write a string value, quoting only when necessary per TOON spec.
     * <p>
     * Strings must be quoted when they:
     * <ul>
     *   <li>Are empty</li>
     *   <li>Have leading/trailing whitespace</li>
     *   <li>Equal "true", "false", or "null"</li>
     *   <li>Look like numbers</li>
     *   <li>Contain special chars: :, ", \, [, ], {, }, newlines, tabs</li>
     *   <li>Contain the active delimiter</li>
     *   <li>Start with hyphen or #</li>
     * </ul>
     */
    private void writeString(String str) throws IOException {
        if (needsQuoting(str)) {
            writeQuotedString(str);
        } else {
            out.write(str);
        }
    }

    /**
     * Determine if a string needs quoting in TOON format.
     * Uses a lookup-table-based single-pass scan for cache misses,
     * merging reserved-literal, control-char, and special-char checks.
     */
    private boolean needsQuoting(String str) {
        if (str == null) {
            return true;
        }
        int len = str.length();
        if (len == 0) {
            return true;  // Empty strings must be quoted
        }

        // Check cache FIRST - covers all cases including reserved literals.
        Boolean cached = quoteDecisionCache.get(str);
        if (cached != null) {
            return cached;
        }

        int answer = scanNeedsQuoting(str, len);
        if (answer == QUOTE_LEGACY_ONLY) {
            // .5, 1. and their like: the two rules differ, and the cache is shared by writers of both kinds
            return !v4Forms;
        }
        boolean result = answer == QUOTE_YES;
        cacheQuoteDecision(str, result ? Boolean.TRUE : Boolean.FALSE);
        return result;
    }

    // What scanNeedsQuoting answers
    private static final int QUOTE_NO = 0;
    private static final int QUOTE_YES = 1;
    private static final int QUOTE_LEGACY_ONLY = 2;     // only when writing for a reader of json-io 4.113.0

    /**
     * Single-pass quoting decision. Replaces the former isReservedScalarLiteral(),
     * isSimpleUnquotedAsciiToken(), and computeNeedsQuoting() multi-method chain.
     * Uses a pre-built boolean[128] lookup table (mustQuoteChar) so the inner loop
     * performs one array access per character instead of 8+ branch comparisons.
     *
     * @return QUOTE_YES, QUOTE_NO, or QUOTE_LEGACY_ONLY for a string that is no number in TOON's grammar but that
     * json-io 4.113.0 and earlier read as one ({@code .5}, {@code 1.}, {@code 1e}): written without quotes by the
     * spec (7.2), and with them under {@code toonLegacyForms}, for the reader that would make a number of it.
     */
    private int scanNeedsQuoting(String str, int len) {
        char first = str.charAt(0);

        // Leading whitespace, hyphen, or # (spec 7.2). A line whose first character is # is a
        // comment (spec 5.1), so an unquoted "#ff0000" that starts a line is dropped by the reader.
        if (first <= ' ' || first == '-' || first == '#') {
            return QUOTE_YES;
        }

        // Trailing whitespace
        if (str.charAt(len - 1) <= ' ') {
            return QUOTE_YES;
        }

        // Reserved literals — length-gated and first-char-gated for near-zero cost on non-matching strings
        if (len <= 5) {
            switch (first) {
                case 't': if (len == 4 && "true".equals(str)) return QUOTE_YES; break;
                case 'f': if (len == 5 && "false".equals(str)) return QUOTE_YES; break;
                case 'n': if (len == 4 && "null".equals(str)) return QUOTE_YES; break;
            }
        }

        // Single-pass scan: control chars + special chars + delimiter via lookup table
        boolean[] mq = mustQuoteChar;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c < 128 ? mq[c] : false) {
                return QUOTE_YES;
            }
        }

        // Number check — only when the first char makes it plausible (digit, '+', '.')
        if ((first >= '0' && first <= '9') || first == '+' || first == '.') {
            if (isNumericLike(str, len)) {
                return QUOTE_YES;
            }
            return looksLikeNumber(str) ? QUOTE_LEGACY_ONLY : QUOTE_NO;
        }

        return QUOTE_NO;
    }

    /**
     * The spec's "numeric-like" (7.2): {@code [+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?}. Such a string is
     * quoted so that it is read back as a string. It is wider than the number grammar a reader applies
     * ({@code 05} and {@code +1} are numeric-like and no numbers) and narrower than {@link #looksLikeNumber}.
     */
    private static boolean isNumericLike(String str, int len) {
        int i = 0;
        char c = str.charAt(0);
        if (c == '+' || c == '-') {
            i++;
        }
        int digits = i;
        while (i < len && str.charAt(i) >= '0' && str.charAt(i) <= '9') {
            i++;
        }
        if (i == digits) {
            return false;
        }
        if (i < len && str.charAt(i) == '.') {
            digits = ++i;
            while (i < len && str.charAt(i) >= '0' && str.charAt(i) <= '9') {
                i++;
            }
            if (i == digits) {
                return false;
            }
        }
        if (i < len && (str.charAt(i) == 'e' || str.charAt(i) == 'E')) {
            i++;
            if (i < len && (str.charAt(i) == '+' || str.charAt(i) == '-')) {
                i++;
            }
            digits = i;
            while (i < len && str.charAt(i) >= '0' && str.charAt(i) <= '9') {
                i++;
            }
            if (i == digits) {
                return false;
            }
        }
        return i == len;
    }

    private void cacheQuoteDecision(String str, Boolean decision) {
        if (quoteDecisionCacheSize.get() < QUOTE_DECISION_CACHE_MAX) {
            Boolean prior = quoteDecisionCache.putIfAbsent(str, decision);
            if (prior == null) {
                quoteDecisionCacheSize.incrementAndGet();
            }
        }
    }

    /**
     * What json-io 4.113.0 and earlier took for a number, and so quoted: besides the spec's numeric-like strings
     * also {@code .5}, {@code 5.}, {@code 1e} and {@code 1.e5}. Decides the quotes under {@code toonLegacyForms}.
     */
    private boolean looksLikeNumber(String str) {
        if (str.isEmpty()) {
            return false;
        }

        int start = 0;
        char first = str.charAt(0);
        int len = str.length();

        // Allow leading +/-
        if (first == '+' || first == '-') {
            if (len == 1) {
                return false;
            }
            start = 1;
        }

        boolean hasDigit = false;
        boolean hasDot = false;
        boolean hasE = false;

        for (int i = start; i < len; i++) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c == '.' && !hasDot && !hasE) {
                hasDot = true;
            } else if ((c == 'e' || c == 'E') && hasDigit && !hasE) {
                hasE = true;
                // Allow +/- after e
                if (i + 1 < len) {
                    char next = str.charAt(i + 1);
                    if (next == '+' || next == '-') {
                        i++;
                    }
                }
            } else {
                return false;  // Not a valid number character
            }
        }

        return hasDigit;
    }

    /**
     * Write string content with TOON §7.1 escape sequences.
     * Explicit escapes for {@code \}, {@code "}, LF, CR, HTAB; other U+0000–U+001F control
     * characters are emitted as {@code \}u + 4 hex digits with lowercase hex per §7.1. BMP and
     * supplementary codepoints pass through as literal UTF-8.
     * Uses batch-scanning to write runs of safe characters in a single write() call.
     */
    private void writeEscapedString(String str) throws IOException {
        int len = str.length();
        int last = 0;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            String escape;
            switch (c) {
                case '\\': escape = "\\\\"; break;
                case '"':  escape = "\\\""; break;
                case '\n': escape = "\\n"; break;
                case '\r': escape = "\\r"; break;
                case '\t': escape = "\\t"; break;
                default:
                    if (c < 0x20) {
                        // Other U+0000–U+001F controls → backslash-u + 4 lowercase hex digits per §7.1.
                        if (last < i) {
                            out.write(str, last, i - last);
                        }
                        out.write("\\u00");
                        int hi = (c >> 4) & 0x0F;
                        int lo = c & 0x0F;
                        out.write(hi < 10 ? '0' + hi : 'a' + hi - 10);
                        out.write(lo < 10 ? '0' + lo : 'a' + lo - 10);
                        last = i + 1;
                    }
                    continue;
            }
            if (last < i) {
                out.write(str, last, i - last);
            }
            out.write(escape);
            last = i + 1;
        }
        if (last < len) {
            out.write(str, last, len - last);
        }
    }

    /**
     * Write a number value.
     * TOON spec: Numbers normalize to non-exponential decimal form with no trailing zeros.
     */
    private void writeNumber(Number num) throws IOException {
        // Integer and Long fast paths are handled in writeValue; this method is called for
        // the less-common Number subtypes (Double/Float/BigDecimal/BigInteger/Atomic*/Short/Byte).
        // Integer/Long/Short/Byte are still checked first here in case writeNumber is reached
        // from a path that did not go through writeValue (e.g. nested field dispatch).
        if (num instanceof Integer || num instanceof Long || num instanceof Short || num instanceof Byte) {
            out.write(toCachedLongString(num.longValue()));
            return;
        }
        if (num instanceof Double) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.write("null");  // TOON spec: NaN/Infinity -> null
            } else if (d == -0.0) {
                out.write("0");  // TOON spec: -0 -> 0
            } else {
                out.write(formatDecimalNumber(d));
            }
            return;
        }
        if (num instanceof Float) {
            float f = num.floatValue();
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                out.write("null");
            } else if (f == -0.0f) {
                out.write("0");
            } else {
                out.write(formatDecimalNumber(f));
            }
            return;
        }
        if (num instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) num;
            if (!MathUtilities.fitsPlainDigits(bd.stripTrailingZeros(),
                    writeOptions.getConverterOptions().getMaxPlainDigits())) {
                // TOON numbers have no exponent, and written out this one is too long (1E+999999999 is a billion
                // digits): the exponent form as a string, which reads back into a BigDecimal
                writeQuotedString(bd.toString());
                return;
            }
            out.write(formatBigDecimal(bd));
            return;
        }
        if (num instanceof BigInteger) {
            out.write(num.toString());
            return;
        }
        if (num instanceof AtomicInteger || num instanceof AtomicLong) {
            out.write(toCachedLongString(num.longValue()));
            return;
        }
        // Fallback for unknown Number subclasses
        out.write(num.toString());
    }

    /**
     * Format a decimal number (double or float) according to TOON spec:
     * - No exponent notation
     * - No trailing zeros in fractional part
     * - Whole numbers have no decimal point
     * The digits are the decimal the number prints, as the reference encoder's JavaScript String(value) gives them:
     * 2^60 is 1152921504606847000, not its binary value 1152921504606846976. Below 2^53 (2^24 for a float) a whole
     * number is exact and is written from its long; above, the long was the binary value, and at 2^63 the cast
     * clamped to Long.MAX_VALUE (9223372036854775807 for 9223372036854775808).
     */
    private String formatDecimalNumber(double d) {
        long bits = Double.doubleToLongBits(d);
        String cached = SHARED_DOUBLE_FORMAT_CACHE.get(bits);
        if (cached != null) {
            return cached;
        }

        String formatted;
        if (d > -0x1p53 && d < 0x1p53) {
            long l = (long) d;
            if (d == l) {
                formatted = toCachedLongString(l);
                if (SHARED_DOUBLE_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
                    SHARED_DOUBLE_FORMAT_CACHE.putIfAbsent(bits, formatted);
                }
                return formatted;
            }
        }

        String text = DoubleDecimal.toString(d);           // the JDK 19 decimal, on every JDK
        if (text.indexOf('E') < 0 && text.indexOf('e') < 0) {
            formatted = text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
        } else {
            formatted = formatBigDecimal(DoubleDecimal.printedDecimal(d));
        }

        if (SHARED_DOUBLE_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
            SHARED_DOUBLE_FORMAT_CACHE.putIfAbsent(bits, formatted);
        }
        return formatted;
    }

    /**
     * Float fast path equivalent of {@link #formatDecimalNumber(double)}.
     */
    private String formatDecimalNumber(float f) {
        int bits = Float.floatToIntBits(f);
        String cached = SHARED_FLOAT_FORMAT_CACHE.get(bits);
        if (cached != null) {
            return cached;
        }

        String formatted;
        if (f > -0x1p24f && f < 0x1p24f) {
            long l = (long) f;
            if (f == l) {
                formatted = toCachedLongString(l);
                if (SHARED_FLOAT_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
                    SHARED_FLOAT_FORMAT_CACHE.putIfAbsent(bits, formatted);
                }
                return formatted;
            }
        }

        String text = FloatDecimal.toString(f);            // the JDK 19 decimal, on every JDK
        if (text.indexOf('E') < 0 && text.indexOf('e') < 0) {
            formatted = text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
        } else {
            formatted = formatBigDecimal(FloatDecimal.printedDecimal(f));
        }

        if (SHARED_FLOAT_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
            SHARED_FLOAT_FORMAT_CACHE.putIfAbsent(bits, formatted);
        }
        return formatted;
    }

    /**
     * Format a BigDecimal according to TOON spec:
     * - No exponent notation (use toPlainString)
     * - No trailing zeros in fractional part
     * - Whole numbers have no decimal point
     */
    private String formatBigDecimal(BigDecimal bd) {
        // Strip trailing zeros first
        bd = bd.stripTrailingZeros();
        String plain = bd.toPlainString();

        // If the result ends with ".0" style patterns, we need to handle it
        // stripTrailingZeros handles fractional trailing zeros but may leave
        // numbers like "3" with scale 0 which toPlainString renders correctly

        // However, for numbers like 3.0, stripTrailingZeros gives us scale 0,
        // and toPlainString gives us "3" directly.
        // For numbers like 3.10, stripTrailingZeros gives us 3.1.

        // Special case: if stripTrailingZeros resulted in a negative scale
        // (e.g., 1000 becomes 1E+3 internally), toPlainString still works correctly
        return plain;
    }

    /**
     * Write an array value (standalone, not as a field value), with $type when {@code typed}.
     */
    private void writeArray(Object array, boolean typed) throws IOException {
        // char[] is written as a plain string value, not as an array.
        // Converter handles String → char[] on read.
        if (array instanceof char[]) {
            writeString(new String((char[]) array));
            return;
        }

        if (cycleSupport) {
            if (writeReferenceIfSeen(array)) {
                return;
            }
        } else if (!enterActivePath(array)) {
            throw cycleDetected(array);
        }

        try {
            int length = ArrayUtilities.getLength(array);
            boolean includeId = cycleSupport && isReferenced(array);
            boolean wroteMeta = false;
            if (includeId) {
                writeIdField(array);
                wroteMeta = true;
            }
            if (typed) {
                if (wroteMeta) {
                    out.write('\n');
                    writeIndent();
                }
                writeTypeField(array);
                wroteMeta = true;
            }
            if (wroteMeta) {
                out.write('\n');
                writeIndent();
                out.write(itemsKey);
            }
            if (length == 0) {
                // §9.1: canonical empty-array literal "[]". The legacy "[0]:" form is still
                // accepted by decoders but encoders SHOULD emit "[]". When metadata was
                // written this becomes "@items: []".
                if (wroteMeta) {
                    out.write(": []");
                } else {
                    out.write("[]");
                }
            } else {
                out.write(countMarker(length));
                writeArrayElements(array, length);
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(array);
            }
        }
    }

    /**
     * Unwrap a {@link JsonObject} carrier from a resolved Maps graph to a plain container so the
     * normal container-writing paths emit its contents instead of an empty body (the historical
     * TreeSet-writes-empty bug — ToonWriter has no per-shape JsonObject writers like JsonWriter's
     * writeJsonObjectArray/writeJsonObjectMap, so a carrier's @items/@keys payload was invisible
     * to the Map-entry iteration).
     * <ul>
     *   <li>Complex-key map carrier ({@code @keys}/{@code @items}) &rarr; a {@link LinkedHashMap}
     *       (non-String keys are then stringified per TOON's String-keyed object model).</li>
     *   <li>Array/collection carrier ({@code @items}) &rarr; the element {@code Object[]} (written
     *       via the normal array path — inline {@code key[N]:} or the wrapped form, as context
     *       dictates).</li>
     *   <li>Plain String-keyed {@link JsonObject} or any non-carrier &rarr; returned unchanged.</li>
     * </ul>
     * The carrier's specific collection/map subtype is normalized away (e.g. a resolved TreeSet
     * writes as a plain array). This matches JsonWriter output under {@code showTypeInfoNever}
     * (the conversion default); full type-faithful TOON re-emission is the parked 4.105.0
     * verbatim-transcode work, which sidesteps carriers entirely.
     */
    private Object unwrapCarrier(Object value) {
        if (value instanceof JsonObject) {
            JsonObject jObj = (JsonObject) value;
            Object[] keys = jObj.getKeys();
            if (keys != null) {
                Object[] vals = jObj.getItems();
                LinkedHashMap<Object, Object> rebuilt = new LinkedHashMap<>(Math.max(16, keys.length + (keys.length >> 1)));
                for (int i = 0; i < keys.length; i++) {
                    rebuilt.put(keys[i], (vals != null && i < vals.length) ? vals[i] : null);
                }
                return rebuilt;
            }
            Object[] items = jObj.getItems();
            if (items != null) {
                return items;
            }
        }
        return value;
    }

    /**
     * Write array elements (after the [N]: marker has been written).
     * Per TOON spec: first field of object elements goes on hyphen line.
     */
    private void writeArrayElements(Object array, int length) throws IOException {
        boolean tables = !keylessListItemArray;
        keylessListItemArray = false;
        if (length == 0) {
            out.write(":");
            return;
        }

        // Check if all elements are primitives (can use inline format)
        Class<?> componentType = array.getClass().getComponentType();   // what the reader expects for an element
        if (isAllPrimitives(array, length)) {
            out.write(':'); out.write(' ');
            if (componentType.isPrimitive()) {
                // Typed loops skip the Integer/Long/Double... wrapper that
                // ArrayUtilities.getPrimitiveElement would otherwise allocate
                // per element. JFR on the perf-test toon-write workload showed
                // ~1.8 GB of Integer + ~2.4 GB of Long sampled-allocation weight
                // funneling through this path; the typed loops emit the same
                // bytes via toCachedLongString(long) / formatDecimalNumber(d|f)
                // / boolean ternary with no boxing on the way in.
                writePrimitiveInlineElements(array, componentType, length);
            } else {
                final Object[] arr = (Object[]) array;
                for (int i = 0; i < length; i++) {
                    if (i > 0) {
                        out.write(delimiter);
                    }
                    writeInlineValue(arr[i]);
                }
            }
        } else if (tables && array instanceof Object[]
                && tryWriteUniformMapTabular((Object[]) array, length, componentType)) {
            // Uniform Map array emitted in tabular form per spec §9.3.
        } else {
            // Try tabular format for uniform POJO arrays (prettyPrint=true → always the verbose list format)
            UniformPojoArrayData uniformPOJO = tables && !writeOptions.isPrettyPrint()
                    ? getUniformPOJODataFromArray(array, length, componentType) : null;
            RowShape shape = uniformPOJO == null && tables && v4Forms && array instanceof Object[]
                    ? tableShape(Arrays.asList((Object[]) array), componentType) : null;
            if (uniformPOJO != null) {
                writeTabularHeader(uniformPOJO.keys);
                writeTabularPOJORowsFromArray(uniformPOJO, array, length);
            } else if (shape != null) {
                // A table whose columns hold uniform objects: nested field groups (spec 9.3)
                writeTable(shape, Arrays.asList((Object[]) array));
            } else {
                // Mixed/complex array - use list format with hyphens
                out.write(":");
                depth++;
                for (int i = 0; i < length; i++) {
                    out.write('\n');
                    writeIndent();
                    out.write("-");
                    Object element = getArrayElement(array, i);
                    writeListElement(element, componentType);
                }
                depth--;
            }
        }
    }

    /**
     * Inline-emit a primitive array (int[], long[], double[], float[], short[],
     * byte[], boolean[]) directly from typed storage. char[] is intercepted
     * upstream in {@link #writeArray(Object)} and never reaches this method.
     * Each typed loop preserves the byte-for-byte output of the boxed path
     * (via {@code toCachedLongString} / {@code formatDecimalNumber} / boolean
     * literal) while skipping the per-element wrapper allocation that
     * {@link com.cedarsoftware.util.ArrayUtilities#getPrimitiveElement(Object, int)}
     * would otherwise create.
     */
    private void writePrimitiveInlineElements(Object array, Class<?> componentType, int length) throws IOException {
        if (componentType == int.class) {
            final int[] arr = (int[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(toCachedLongString(arr[i]));
            }
        } else if (componentType == long.class) {
            final long[] arr = (long[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(toCachedLongString(arr[i]));
            }
        } else if (componentType == double.class) {
            final double[] arr = (double[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(formatDecimalNumber(arr[i]));
            }
        } else if (componentType == boolean.class) {
            final boolean[] arr = (boolean[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(arr[i] ? "true" : "false");
            }
        } else if (componentType == byte.class) {
            final byte[] arr = (byte[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(toCachedLongString(arr[i]));
            }
        } else if (componentType == float.class) {
            final float[] arr = (float[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(formatDecimalNumber(arr[i]));
            }
        } else if (componentType == short.class) {
            final short[] arr = (short[]) array;
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                out.write(toCachedLongString(arr[i]));
            }
        } else {
            // Defensive: char[] is intercepted upstream; no other primitive
            // type exists in Java. Fall back to the boxed path if we ever
            // reach here so behavior degrades to "still correct."
            for (int i = 0; i < length; i++) {
                if (i > 0) out.write(delimiter);
                writeInlineValue(getArrayElement(array, i));
            }
        }
    }

    /**
     * Write a Collection value (standalone, not as a field value), with $type when {@code typed}.
     */
    private void writeCollection(Collection<?> collection, boolean typed) throws IOException {
        if (cycleSupport) {
            if (writeReferenceIfSeen(collection)) {
                return;
            }
        } else if (!enterActivePath(collection)) {
            throw cycleDetected(collection);
        }

        try {
            boolean includeId = cycleSupport && isReferenced(collection);
            boolean wroteMeta = false;
            if (includeId) {
                writeIdField(collection);
                wroteMeta = true;
            }
            if (typed) {
                if (wroteMeta) {
                    out.write('\n');
                    writeIndent();
                }
                writeTypeField(collection);
                wroteMeta = true;
            }
            if (wroteMeta) {
                out.write('\n');
                writeIndent();
                out.write(itemsKey);
            }
            if (collection.isEmpty()) {
                // §9.1 canonical empty-array literal "[]" (or "@items: []" when metadata
                // was written). Mirrors the writeArray fix above.
                if (wroteMeta) {
                    out.write(": []");
                } else {
                    out.write("[]");
                }
            } else {
                out.write(countMarker(collection.size()));
                if (typeMetadataEnabled && collection instanceof EnumSet) {
                    elementType = AS_IS;   // its elements are of the enum its $type names: their names, as in JSON
                }
                writeCollectionElementsWithHeader(collection);
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(collection);
            }
        }
    }

    /**
     * Write collection elements with header (colon or tabular header).
     * Called from writeMapWithSimpleKeys after [N] has been written.
     */
    private void writeCollectionElementsWithHeader(Collection<?> collection) throws IOException {
        Class<?> declared = typeMetadataEnabled ? takeElementType() : Object.class;   // what the reader expects
        boolean tables = !keylessListItemArray;
        keylessListItemArray = false;
        if (collection.isEmpty()) {
            out.write(":");
            return;
        }

        // Check if all elements are primitives (that carry no $type)
        boolean allPrimitives = true;
        for (Object element : collection) {
            if (element != null && !isPlainInline(element, declared)) {
                allPrimitives = false;
                break;
            }
        }

        if (allPrimitives) {
            out.write(':'); out.write(' ');
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    out.write(delimiter);
                }
                first = false;
                writeInlineValue(element);
            }
        } else {
            // Check for uniform Map array (all Maps with same keys) → tabular format
            List<String> uniformKeys = tables ? getUniformKeys(collection, declared) : null;
            // Check for uniform POJO array → tabular format (prettyPrint=true → always the verbose list format)
            UniformPojoArrayData uniformPOJO = uniformKeys == null && tables && !writeOptions.isPrettyPrint()
                    ? getUniformPOJODataFromCollection(collection, declared) : null;
            RowShape shape = uniformKeys == null && uniformPOJO == null && tables && v4Forms
                    ? tableShape(collection, declared) : null;
            if (uniformKeys != null) {
                // Tabular format: {key1,key2,...}: followed by CSV rows
                writeTabularHeader(uniformKeys);
                writeTabularRows(collection, uniformKeys);
            } else if (uniformPOJO != null) {
                writeTabularHeader(uniformPOJO.keys);
                writeTabularPOJORows(uniformPOJO, collection);
            } else if (shape != null) {
                // A table whose columns hold uniform objects: nested field groups (spec 9.3)
                writeTable(shape, collection);
            } else {
                // Non-uniform or mixed - use list format with hyphens
                out.write(":");
                depth++;
                for (Object element : collection) {
                    out.write('\n');
                    writeIndent();
                    out.write("-");
                    writeListElement(element, declared);
                }
                depth--;
            }
        }
    }

    /**
     * Detect a uniform-Map {@code Object[]} (all Maps with the same primitive-valued keys,
     * spec §9.3) and emit it in tabular form when matched. Returns {@code true} if the
     * array was emitted; {@code false} if the caller should fall back to its existing
     * non-tabular path (POJO check or expanded list form).
     *
     * <p>Mirrors the uniform-Map branch of
     * {@link #writeCollectionElementsWithHeader(Collection)} so {@code Object[]} inputs —
     * which are the tree-builder's representation for JSON arrays inside a parsed
     * {@code Map} tree — get the same tabular treatment that {@code Collection} inputs
     * already receive. {@code Arrays.asList} is a non-copying view so the path stays cheap.
     */
    private boolean tryWriteUniformMapTabular(Object[] array, int length, Class<?> declared) throws IOException {
        List<Object> view = (length == array.length) ? Arrays.asList(array) : Arrays.asList(array).subList(0, length);
        List<String> uniformKeys = getUniformKeys(view, declared);
        if (uniformKeys == null) {
            return false;
        }
        writeTabularHeader(uniformKeys);
        writeTabularRows(view, uniformKeys);
        return true;
    }

    /**
     * Check if a collection contains uniform objects (all Maps with identical keys and primitive values).
     * Returns the ordered list of keys if uniform, null otherwise. With type info on, a map or a value that carries
     * $type ({@code declared} is what the reader expects for an element) is not a row.
     */
    private List<String> getUniformKeys(Collection<?> collection, Class<?> declared) {
        // §9.3: tabular form applies when all elements are Maps with the SAME KEY SET
        // (set-equality, not list-order). The header uses the first object's key order
        // per the spec ("uses field order from first object for tabular headers"); rows
        // look values up by key in writeTabularRows so non-first elements with different
        // key order render correctly.
        List<String> firstKeys = null;
        java.util.Set<String> firstKeySet = null;

        for (Object element : collection) {
            if (element == null) {
                return null;  // Null elements break uniformity
            }
            if (cycleSupport && isReferenced(element)) {
                return null;
            }

            // Only Maps are candidates for tabular format
            // Collections, arrays, and arbitrary objects don't qualify
            if (!(element instanceof Map)) {
                return null;
            }

            Map<?, ?> map = (Map<?, ?>) element;

            if (map.isEmpty() || (typeMetadataEnabled && needsType(map.getClass(), declared))) {
                return null;  // Empty objects break uniformity
            }

            // Check all keys are simple types (strings) and all values are primitives
            java.util.Set<String> elementKeySet = new java.util.HashSet<>(map.size() * 2);
            List<String> elementKeys = null;
            if (firstKeys == null) {
                elementKeys = new ArrayList<>(map.size());
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String)) {
                    return null;  // Non-string keys break uniformity
                }
                Object value = entry.getValue();
                if (value != null && (!isPrimitive(value)
                        || (typeMetadataEnabled && needsType(value, Object.class)))) {
                    return null;  // Complex values break tabular format
                }
                String k = (String) key;
                elementKeySet.add(k);
                if (elementKeys != null) {
                    elementKeys.add(k);
                }
            }

            if (firstKeys == null) {
                firstKeys = elementKeys;
                firstKeySet = elementKeySet;
            } else if (!firstKeySet.equals(elementKeySet)) {
                return null;  // Different key sets break uniformity
            }
        }

        return firstKeys;
    }

    /**
     * Write tabular header: {col1,col2,...}:
     */
    private void writeTabularHeader(List<String> keys) throws IOException {
        out.write("{");
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.write(delimiter);
            }
            first = false;
            // §7.3: header keys follow the same identifier rule as object keys — quote
            // when the key contains chars outside [A-Za-z0-9_.] (e.g. colons, spaces).
            writeKeyStringKnown(key, needsQuotingForMapKey(key));
        }
        out.write("}:");
    }

    /**
     * Write tabular rows (CSV-like format).
     */
    private void writeTabularRows(Collection<?> collection, List<String> keys) throws IOException {
        depth++;
        for (Object element : collection) {
            out.write('\n');
            writeIndent();

            // Elements are guaranteed to be Maps by getUniformKeys check
            Map<?, ?> map = (Map<?, ?>) element;

            // Write values in key order
            boolean first = true;
            for (String key : keys) {
                if (!first) {
                    out.write(delimiter);
                }
                first = false;
                writeInlineValue(map.get(key));
            }
        }
        depth--;
    }

    /**
     * Check if a collection contains uniform POJOs (all non-Map objects with identical
     * field names and primitive field values).
     * Returns keys plus active WriteFieldPlans if uniform, null otherwise.
     * Avoids intermediate LinkedHashMap allocations — field values are read directly
     * from accessors during the write phase.
     * <p>
     * POJOs qualify for tabular format when:
     * <ul>
     *   <li>All elements are the same concrete class</li>
     *   <li>All field values are primitives (no nested objects/arrays)</li>
     *   <li>All elements have the same fields in the same order</li>
     * </ul>
     */
    private UniformPojoArrayData getUniformPOJODataFromCollection(Collection<?> collection, Class<?> declared) {
        List<String> keys = null;
        List<WriteOptionsBuilder.WriteFieldPlan> activePlans = null;
        List<WriteOptionsBuilder.WriteFieldPlan> allPlans = null;
        Class<?> elementClass = null;
        Object first = null;

        for (Object element : collection) {
            if (element == null) {
                return null;  // Null elements break uniformity
            }
            if (cycleSupport && isReferenced(element)) {
                return null;
            }

            // Skip Maps, Collections, and arrays — already handled by getUniformKeys
            if (element instanceof Map || element instanceof Collection || element.getClass().isArray()) {
                return null;
            }

            // Skip pure primitive types — those belong in the primitive path
            if (isPrimitive(element)) {
                return null;
            }

            // All elements must be the same concrete class for tabular format
            if (elementClass == null) {
                elementClass = element.getClass();
                first = element;

                // Bail out if @IoAnyGetter (extra dynamic fields could vary), or when a custom writer gives the
                // objects as data (their fields are not what is written)
                if (AnnotationResolver.getMetadata(elementClass).getAnyGetterMethod() != null
                        || isDataClass(elementClass)) {
                    return null;
                }

                allPlans = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, elementClass);
                activePlans = new ArrayList<>(allPlans.size());
                keys = new ArrayList<>(allPlans.size());

                for (WriteOptionsBuilder.WriteFieldPlan plan : allPlans) {
                    if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) continue;
                    if (isPrimitiveFieldPlan(plan)) {
                        activePlans.add(plan);
                        keys.add(plan.accessor().getUniqueFieldName());
                        continue;
                    }
                    Object value = plan.accessor().retrieve(element);
                    if ((skipNullFields || plan.skipIfNull()) && value == null) continue;
                    if (value != null && !isPlainCell(value, plan)) return null;
                    activePlans.add(plan);
                    keys.add(plan.accessor().getUniqueFieldName());
                }
                if (keys.isEmpty()) {
                    return null;  // Empty objects break tabular format
                }
            } else if (elementClass != element.getClass()) {
                return null;
            } else {
                // Verify same active plan pattern and all values primitive
                if (!verifyActivePlans(element, allPlans, activePlans)) return null;
            }
        }

        if (keys == null) {
            return null;
        }

        // Inject @type as the first column when type metadata is needed
        String typeName = null;
        if (needsType(elementClass, declared)) {
            typeName = typeNameOf(first);
            keys.add(0, typeKey);
        }

        return new UniformPojoArrayData(keys, activePlans, typeName);
    }

    /**
     * Check if an array contains uniform POJOs eligible for tabular format.
     * Returns keys plus active WriteFieldPlans if uniform, null otherwise.
     * Avoids intermediate LinkedHashMap allocations — field values are read directly
     * from accessors during the write phase.
     */
    private UniformPojoArrayData getUniformPOJODataFromArray(Object array, int length, Class<?> declared) {
        List<String> keys = null;
        List<WriteOptionsBuilder.WriteFieldPlan> activePlans = null;
        List<WriteOptionsBuilder.WriteFieldPlan> allPlans = null;
        Class<?> elementClass = null;
        Object first = null;

        for (int i = 0; i < length; i++) {
            Object element = getArrayElement(array, i);
            if (element == null) {
                return null;
            }
            if (cycleSupport && isReferenced(element)) {
                return null;
            }
            if (element instanceof Map || element instanceof Collection || element.getClass().isArray()) {
                return null;
            }
            if (isPrimitive(element)) {
                return null;
            }

            // All elements must be the same concrete class for tabular format
            if (elementClass == null) {
                elementClass = element.getClass();
                first = element;

                // Bail out if @IoAnyGetter (extra dynamic fields could vary), or when a custom writer gives the
                // objects as data (their fields are not what is written)
                if (AnnotationResolver.getMetadata(elementClass).getAnyGetterMethod() != null
                        || isDataClass(elementClass)) {
                    return null;
                }

                allPlans = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, elementClass);
                activePlans = new ArrayList<>(allPlans.size());
                keys = new ArrayList<>(allPlans.size());

                for (WriteOptionsBuilder.WriteFieldPlan plan : allPlans) {
                    if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) continue;
                    if (isPrimitiveFieldPlan(plan)) {
                        activePlans.add(plan);
                        keys.add(plan.accessor().getUniqueFieldName());
                        continue;
                    }
                    Object value = plan.accessor().retrieve(element);
                    if ((skipNullFields || plan.skipIfNull()) && value == null) continue;
                    if (value != null && !isPlainCell(value, plan)) return null;
                    activePlans.add(plan);
                    keys.add(plan.accessor().getUniqueFieldName());
                }
                if (keys.isEmpty()) {
                    return null;
                }
            } else if (elementClass != element.getClass()) {
                return null;
            } else {
                // Verify same active plan pattern and all values primitive
                if (!verifyActivePlans(element, allPlans, activePlans)) return null;
            }
        }

        if (keys == null) {
            return null;
        }

        // Inject @type as the first column when type metadata is needed
        String typeName = null;
        if (needsType(elementClass, declared)) {
            typeName = typeNameOf(first);
            keys.add(0, typeKey);
        }

        return new UniformPojoArrayData(keys, activePlans, typeName);
    }

    /**
     * Verify that a subsequent element has the same active field pattern as the first element.
     * Walks through allPlans and checks that the same plans are active (not skipped) and
     * all values are primitive.
     */
    private boolean verifyActivePlans(Object element,
                                      List<WriteOptionsBuilder.WriteFieldPlan> allPlans,
                                      List<WriteOptionsBuilder.WriteFieldPlan> activePlans) {
        int activeIdx = 0;
        for (int i = 0, len = allPlans.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = allPlans.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) continue;
            if (isPrimitiveFieldPlan(plan)) {
                if (activeIdx >= activePlans.size() || activePlans.get(activeIdx) != plan) {
                    return false;
                }
                activeIdx++;
                continue;
            }
            Object value = plan.accessor().retrieve(element);
            if ((skipNullFields || plan.skipIfNull()) && value == null) {
                // Skipped — check it wasn't active in first element
                if (activeIdx < activePlans.size() && activePlans.get(activeIdx) == plan) {
                    return false;
                }
                continue;
            }
            if (value != null && !isPlainCell(value, plan)) return false;
            if (activeIdx >= activePlans.size() || activePlans.get(activeIdx) != plan) {
                return false;
            }
            activeIdx++;
        }
        return activeIdx == activePlans.size();
    }

    /**
     * A field value a tabular row can hold: a primitive, carrying no $type where its field is declared (with type
     * info on, an @IoShowType field's value is written as a field, where its type is).
     */
    private boolean isPlainCell(Object value, WriteOptionsBuilder.WriteFieldPlan plan) {
        return isPrimitive(value) && !(typeMetadataEnabled
                && (plan.forceShowType() || needsType(value, plan.effectiveDeclaredType())));
    }

    private boolean isPrimitiveFieldPlan(WriteOptionsBuilder.WriteFieldPlan plan) {
        return plan.primitiveWriteKind() != WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_NONE;
    }

    /**
     * Write tabular rows for a collection of uniform POJOs (CSV-like format).
     * Streams field values directly from accessors — no intermediate map allocation.
     */
    private void writeTabularPOJORows(UniformPojoArrayData data, Collection<?> collection) throws IOException {
        depth++;
        for (Object element : collection) {
            out.write('\n');
            writeIndent();
            writeTabularPOJORow(data, element);
        }
        depth--;
    }

    /**
     * Write tabular rows for an array of uniform POJOs (CSV-like format).
     * Streams field values directly from accessors — no intermediate map allocation.
     */
    private void writeTabularPOJORowsFromArray(UniformPojoArrayData data, Object array, int length) throws IOException {
        depth++;
        for (int i = 0; i < length; i++) {
            Object element = getArrayElement(array, i);
            out.write('\n');
            writeIndent();
            writeTabularPOJORow(data, element);
        }
        depth--;
    }

    /**
     * Write a single tabular POJO row using active WriteFieldPlans.
     */
    private void writeTabularPOJORow(UniformPojoArrayData data, Object element) throws IOException {
        boolean first = true;
        if (data.typeName != null) {
            writeInlineValue(data.typeName);
            first = false;
        }
        for (int i = 0, len = data.activePlans.size(); i < len; i++) {
            if (!first) {
                out.write(delimiter);
            }
            first = false;
            WriteOptionsBuilder.WriteFieldPlan plan = data.activePlans.get(i);
            if (canWritePrimitiveFieldDirect(plan)) {
                writePrimitiveFieldValue(element, plan);
                continue;
            }
            Object value = plan.accessor().retrieve(element);
            String formatPattern = plan.formatPattern();
            if (formatPattern != null && value != null) {
                value = applyFormatPattern(value, formatPattern);
            }
            writeInlineValue(value);
        }
    }

    // ---------- TOON spec v4: nested field groups (9.3) and the keyed tabular form (9.5) ----------

    /**
     * The field list that the rows of a table share: its names in the first row's order, and for each name either
     * a leaf -- one primitive cell -- or the field list of the objects that column holds, a nested field group
     * (spec 9.3). The rows are Maps with String keys, or objects of one class read through their field plans.
     */
    private static final class RowShape {
        final Class<?> rowClass;                                    // null: the rows are Maps
        final List<WriteOptionsBuilder.WriteFieldPlan> allPlans;    // rowClass's plans, written or not; null for Maps
        final String[] names;
        final WriteOptionsBuilder.WriteFieldPlan[] plans;           // one per name; null for Maps
        final RowShape[] groups;                                    // one per name, null for a leaf; null: all leaves
        private Map<String, Integer> index;                         // Maps: name -> position, made when first asked
        String typeName;                                            // rows of a class that carry $type: its name

        RowShape(Class<?> rowClass, List<WriteOptionsBuilder.WriteFieldPlan> allPlans, String[] names,
                 WriteOptionsBuilder.WriteFieldPlan[] plans, RowShape[] groups) {
            this.rowClass = rowClass;
            this.allPlans = allPlans;
            this.names = names;
            this.plans = plans;
            this.groups = groups;
        }

        RowShape group(int i) {
            return groups == null ? null : groups[i];
        }

        Integer positionOf(Object key) {
            if (index == null) {
                index = new HashMap<>(names.length * 2);
                for (int i = 0; i < names.length; i++) {
                    index.put(names[i], i);
                }
            }
            return index.get(key);
        }
    }

    /** An object of uniform objects, ready to be written in the keyed tabular form: its entries and their shape. */
    private static final class KeyedTable {
        final RowShape shape;
        final String[] keys;
        final boolean[] quoted;     // per key: whether it is written in quotes
        final Object[] values;

        KeyedTable(RowShape shape, String[] keys, boolean[] quoted, Object[] values) {
            this.shape = shape;
            this.keys = keys;
            this.quoted = quoted;
            this.values = values;
        }
    }

    /**
     * Whether {@code value}, written where the reader expects {@code declared}, can be a table row, a keyed table's
     * entry or the object of a nested field group: a Map or an object written field by field that is not empty, is
     * written once (a shared one needs its $id), and carries no $type -- a row of a table may, in the table's $type
     * column ({@code typeColumn}), as before. With prettyPrint an object is always written field by field.
     */
    private boolean isRowObject(Object value, Class<?> declared, boolean typeColumn) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return false;
        }
        Class<?> c = value.getClass();
        ToonWriteType kind = writeTypeCache.get(c);
        if ((kind != ToonWriteType.MAP && kind != ToonWriteType.POJO) || (cycleSupport && isReferenced(value))) {
            return false;
        }
        switch (kind) {
            case MAP:
                if (value instanceof JsonObject) {
                    // A carrier from a Maps graph holds its keys or items aside: not a plain object
                    JsonObject carrier = (JsonObject) value;
                    if (carrier.getKeys() != null || carrier.getItems() != null) {
                        return false;
                    }
                }
                return !((Map<?, ?>) value).isEmpty() && !needsType(c, declared);
            case POJO:
                return !prettyPrint && !isDataClass(c)
                        && AnnotationResolver.getMetadata(c).getAnyGetterMethod() == null
                        && (typeColumn || !needsType(c, declared));
            default:
                return false;
        }
    }

    /**
     * The field list of {@code row}, which {@link #isRowObject} accepted, or null when a field holds what a cell
     * cannot: an array, an empty object, a shared object, a value that carries $type. An object that holds itself
     * has no field list either; the path that writes it field by field reports the cycle.
     */
    private RowShape shapeOf(Object row) {
        if (!cycleSupport && !enterActivePath(row)) {
            return null;
        }
        try {
            return row instanceof Map ? mapShape((Map<?, ?>) row) : classShape(row);
        } finally {
            if (!cycleSupport) {
                exitActivePath(row);
            }
        }
    }

    private RowShape mapShape(Map<?, ?> map) {
        int size = map.size();
        String[] names = new String[size];
        RowShape[] groups = null;
        int n = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String) || n == size) {
                return null;
            }
            Object value = entry.getValue();
            if (!isMapCell(value)) {
                RowShape group = isRowObject(value, Object.class, false) ? shapeOf(value) : null;
                if (group == null) {
                    return null;
                }
                if (groups == null) {
                    groups = new RowShape[size];
                }
                groups[n] = group;
            }
            names[n++] = (String) key;
        }
        return n == size ? new RowShape(null, null, names, null, groups) : null;
    }

    /** A Map's value that is one cell: a primitive that carries no $type (what a map's value does is not declared). */
    private boolean isMapCell(Object value) {
        return value == null
                || (isPrimitive(value) && !(typeMetadataEnabled && needsType(value, Object.class)));
    }

    private RowShape classShape(Object row) {
        Class<?> c = row.getClass();
        List<WriteOptionsBuilder.WriteFieldPlan> all = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, c);
        int max = all.size();
        String[] names = new String[max];
        WriteOptionsBuilder.WriteFieldPlan[] plans = new WriteOptionsBuilder.WriteFieldPlan[max];
        RowShape[] groups = null;
        int n = 0;
        for (int i = 0; i < max; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = all.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }
            if (!isPrimitiveFieldPlan(plan)) {
                Object value = plan.accessor().retrieve(row);
                if (value == null) {
                    if (skipNullFields || plan.skipIfNull()) {
                        continue;
                    }
                } else if (!isPlainCell(value, plan)) {
                    // @IoShowType: the value's $type is written with it, as a field
                    RowShape group = !plan.forceShowType() && isRowObject(value, plan.effectiveDeclaredType(), false)
                            ? shapeOf(value) : null;
                    if (group == null) {
                        return null;
                    }
                    if (groups == null) {
                        groups = new RowShape[max];
                    }
                    groups[n] = group;
                }
            }
            names[n] = plan.accessor().getUniqueFieldName();
            plans[n++] = plan;
        }
        if (n == 0) {
            return null;
        }
        return new RowShape(c, all, Arrays.copyOf(names, n), Arrays.copyOf(plans, n),
                groups == null ? null : Arrays.copyOf(groups, n));
    }

    /**
     * Whether {@code row}, which {@link #isRowObject} accepted, has the fields of {@code shape}: the same names
     * (in any order for a Map), a primitive in every leaf, and in every nested field group an object that fits it.
     */
    private boolean fits(RowShape shape, Object row) {
        // No guard against an object that holds itself is needed here: the walk follows the shape, which ends
        return shape.rowClass == null ? mapFits(shape, row) : classFits(shape, row);
    }

    private boolean mapFits(RowShape shape, Object row) {
        if (!(row instanceof Map)) {
            return false;
        }
        Map<?, ?> map = (Map<?, ?>) row;
        if (map.size() != shape.names.length) {
            return false;
        }
        // As many keys as names, every key one of the names: the same set, each name spelled as the header spells it
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Integer at = key instanceof String ? shape.positionOf(key) : null;
            if (at == null) {
                return false;
            }
            Object value = entry.getValue();
            RowShape group = shape.group(at);
            if (group == null ? !isMapCell(value)
                    : !(isRowObject(value, Object.class, false) && fits(group, value))) {
                return false;
            }
        }
        return true;
    }

    private boolean classFits(RowShape shape, Object row) {
        if (row.getClass() != shape.rowClass) {
            return false;
        }
        List<WriteOptionsBuilder.WriteFieldPlan> all = shape.allPlans;
        WriteOptionsBuilder.WriteFieldPlan[] plans = shape.plans;
        int n = 0;
        for (int i = 0, len = all.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = all.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }
            boolean inHeader = n < plans.length && plans[n] == plan;
            if (isPrimitiveFieldPlan(plan)) {
                if (!inHeader) {
                    return false;
                }
                n++;
                continue;
            }
            Object value = plan.accessor().retrieve(row);
            if (value == null && (skipNullFields || plan.skipIfNull())) {
                if (inHeader) {
                    return false;       // left out of this row, but a column of the header
                }
                continue;
            }
            if (!inHeader) {
                return false;
            }
            RowShape group = shape.group(n);
            if (group == null ? value != null && !isPlainCell(value, plan)
                    : !(isRowObject(value, plan.effectiveDeclaredType(), false) && fits(group, value))) {
                return false;
            }
            n++;
        }
        return n == plans.length;
    }

    /**
     * The field list of a table for {@code elements}, {@code declared} being what the reader expects for one, or
     * null when they are not all objects with the same fields (spec 9.3). Asked after the two older checks, which
     * know only tables of primitive columns, so what this adds is the table with a nested field group.
     */
    private RowShape tableShape(Collection<?> elements, Class<?> declared) {
        RowShape shape = null;
        Object first = null;
        for (Object element : elements) {
            if (!isRowObject(element, declared, true)) {
                return null;
            }
            if (shape == null) {
                shape = shapeOf(element);
                first = element;
                if (shape == null) {
                    return null;
                }
            } else if (!fits(shape, element)) {
                return null;
            }
        }
        if (shape != null && shape.rowClass != null && needsType(shape.rowClass, declared)) {
            shape.typeName = typeNameOf(first);     // the rows' $type, as the table's first column
        }
        return shape;
    }

    /** The field list in braces, a nested field group after its field's name: {id,customer{name,country},total} */
    private void writeFieldList(RowShape shape) throws IOException {
        out.write('{');
        boolean first = true;
        if (shape.typeName != null) {
            writeKeyStringKnown(typeKey, needsQuotingForMapKey(typeKey));
            first = false;
        }
        String[] names = shape.names;
        for (int i = 0; i < names.length; i++) {
            if (!first) {
                out.write(delimiter);
            }
            first = false;
            writeKeyStringKnown(names[i], shape.plans == null
                    ? needsQuotingForMapKey(names[i]) : shape.plans[i].toonKeyNeedsQuoting());
            RowShape group = shape.group(i);
            if (group != null) {
                writeFieldList(group);
            }
        }
        out.write('}');
    }

    /**
     * The cells of one row: the leaves in the header's order, a nested field group's cells in its field's place.
     * Returns false once a cell has been written ({@code first} for the next one).
     */
    private boolean writeCells(RowShape shape, Object row, boolean first) throws IOException {
        if (shape.rowClass == null) {
            Map<?, ?> map = (Map<?, ?>) row;
            String[] names = shape.names;
            for (int i = 0; i < names.length; i++) {
                Object value = map.get(names[i]);
                RowShape group = shape.group(i);
                if (group != null) {
                    first = writeCells(group, value, first);
                    continue;
                }
                if (!first) {
                    out.write(delimiter);
                }
                first = false;
                writeInlineValue(value);
            }
            return first;
        }
        WriteOptionsBuilder.WriteFieldPlan[] plans = shape.plans;
        for (int i = 0; i < plans.length; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = plans[i];
            RowShape group = shape.group(i);
            if (group != null) {
                first = writeCells(group, plan.accessor().retrieve(row), first);
                continue;
            }
            if (!first) {
                out.write(delimiter);
            }
            first = false;
            if (canWritePrimitiveFieldDirect(plan)) {
                writePrimitiveFieldValue(row, plan);
                continue;
            }
            Object value = plan.accessor().retrieve(row);
            String formatPattern = plan.formatPattern();
            if (formatPattern != null && value != null) {
                value = applyFormatPattern(value, formatPattern);
            }
            writeInlineValue(value);
        }
        return first;
    }

    /** A table after its [N]: the field list, then one row per element. */
    private void writeTable(RowShape shape, Collection<?> rows) throws IOException {
        writeFieldList(shape);
        out.write(':');
        depth++;
        for (Object row : rows) {
            out.write('\n');
            writeIndent();
            boolean first = true;
            if (shape.typeName != null) {
                writeInlineValue(shape.typeName);
                first = false;
            }
            writeCells(shape, row, first);
        }
        depth--;
    }

    /**
     * {@code value}, an object in a field's place or at the root that carries no $type, as a keyed table (spec
     * 9.5), or null when it is not one: it needs at least two entries, each an object, all with the same fields.
     * A Map's entries are its entries, an object's are its fields. Not for an object that is shared (it needs its
     * $id), nor for a Map whose keys need the {$keys, $items} form. The declared types of the Map's keys and values
     * are read here and, when the answer is a table, taken as {@link #writeMap} takes them.
     */
    private KeyedTable keyedTable(Object value) {
        // Asked for every object and Map written as a field's value, so the usual answer has to be cheap: nearly
        // all are out at their first value, a primitive, before anything is tracked or allocated. (Measured: with
        // the full check first, writing lists of objects that are no tables took 13-17% longer.)
        boolean isMap = value instanceof Map;
        if (isMap ? !mayBeKeyedMap((Map<?, ?>) value) : !mayBeKeyedObject(value)) {
            return null;
        }
        if (cycleSupport && isReferenced(value)) {
            return null;        // shared: it needs its $id
        }
        return isMap ? keyedMap((Map<?, ?>) value) : keyedObject(value);
    }

    /** At least two entries, the first one's value an object that can be a row. */
    private boolean mayBeKeyedMap(Map<?, ?> map) {
        if (map.size() < 2) {
            return false;
        }
        // Through the entry set, which the writer walks next anyway: the Map makes that view once
        for (Map.Entry<?, ?> first : map.entrySet()) {
            return isRowObject(first.getValue(), elementType, false);
        }
        return false;
    }

    /** An object written field by field, with no field of a primitive type, whose first field can be a row. */
    private boolean mayBeKeyedObject(Object obj) {
        Class<?> c = obj.getClass();
        if (prettyPrint || writeTypeCache.get(c) != ToonWriteType.POJO) {
            return false;
        }
        List<WriteOptionsBuilder.WriteFieldPlan> all = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, c);
        if (all instanceof WriteOptionsBuilder.WriteFieldPlanList
                && ((WriteOptionsBuilder.WriteFieldPlanList) all).anyPrimitiveField) {
            return false;       // known without looking at a field: the loop below would find it out as well
        }
        for (int i = 0, max = all.size(); i < max; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = all.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }
            if (isPrimitiveFieldPlan(plan) || plan.forceShowType()) {
                return false;
            }
            Object value = plan.accessor().retrieve(obj);
            if (value == null && (skipNullFields || plan.skipIfNull())) {
                continue;
            }
            return isRowObject(value, plan.effectiveDeclaredType(), false);
        }
        return false;
    }

    /** The keyed table of a Map that {@link #mayBeKeyedMap} let through, or null. */
    private KeyedTable keyedMap(Map<?, ?> map) {
        int size = map.size();
        if (map instanceof JsonObject) {
            JsonObject carrier = (JsonObject) map;
            if (carrier.getKeys() != null || carrier.getItems() != null) {
                return null;
            }
        }
        Class<?> valueType = elementType;
        Class<?> keyDeclared = keyType;
        RowShape shape = null;
        Object[] values = new Object[size];
        int n = 0;
        for (Object entryValue : map.values()) {
            if (n == size || !isRowObject(entryValue, valueType, false)) {
                return null;
            }
            if (shape == null) {
                shape = shapeOf(entryValue);
                if (shape == null) {
                    return null;
                }
            } else if (!fits(shape, entryValue)) {
                return null;
            }
            values[n++] = entryValue;
        }
        if (n != size || keysNeedTypes(map, keyDeclared) || (!currentMapHasSimpleKeyTypeHint && hasComplexKeys(map))) {
            return null;
        }
        String[] keys = new String[size];
        boolean[] quoted = new boolean[size];
        n = 0;
        for (Object key : map.keySet()) {
            if (n == size) {
                return null;
            }
            keys[n] = keyText(key);
            quoted[n] = needsQuotingForMapKey(keys[n]);
            n++;
        }
        // The map's declared types are used up, as writeMap uses them up: what the rows hold sees Object
        elementType = Object.class;
        keyType = Object.class;
        currentMapHasSimpleKeyTypeHint = false;
        return new KeyedTable(shape, keys, quoted, values);
    }

    /** The keyed table of an object that {@link #mayBeKeyedObject} let through, or null. */
    private KeyedTable keyedObject(Object obj) {
        Class<?> c = obj.getClass();
        List<WriteOptionsBuilder.WriteFieldPlan> all = WriteOptionsBuilder.getWriteFieldPlans(writeOptions, c);
        int max = all.size();
        RowShape shape = null;
        String[] keys = new String[max];
        boolean[] quoted = new boolean[max];
        Object[] values = new Object[max];
        int n = 0;
        for (int i = 0; i < max; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = all.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }
            if (isPrimitiveFieldPlan(plan) || plan.forceShowType()) {
                return null;
            }
            Object value = plan.accessor().retrieve(obj);
            if (value == null && (skipNullFields || plan.skipIfNull())) {
                continue;
            }
            if (!isRowObject(value, plan.effectiveDeclaredType(), false)) {
                return null;
            }
            if (shape == null) {
                // An object with an @IoAnyGetter or a custom writer's data is not written field by field
                if (isDataClass(c) || AnnotationResolver.getMetadata(c).getAnyGetterMethod() != null) {
                    return null;
                }
                shape = shapeOf(value);
                if (shape == null) {
                    return null;
                }
            } else if (!fits(shape, value)) {
                return null;
            }
            keys[n] = plan.accessor().getUniqueFieldName();
            quoted[n] = plan.toonKeyNeedsQuoting();
            values[n++] = value;
        }
        if (n < 2) {
            return null;
        }
        return new KeyedTable(shape, Arrays.copyOf(keys, n), Arrays.copyOf(quoted, n), Arrays.copyOf(values, n));
    }

    /**
     * A keyed table after its key: the header {@code [N:]{fields}:}, then one row per entry, {@code key: cells},
     * {@code levels} below the line the header is on (one, or two when that line is a list item's hyphen line).
     */
    private void writeKeyedTable(KeyedTable table, int levels) throws IOException {
        out.write('[');
        out.write(Integer.toString(table.keys.length));
        out.write(':');
        if (delimiter != ',') {
            out.write(delimiter);
        }
        out.write(']');
        writeFieldList(table.shape);
        out.write(':');
        depth += levels;
        for (int i = 0; i < table.keys.length; i++) {
            out.write('\n');
            writeIndent();
            writeKeyStringKnown(table.keys[i], table.quoted[i]);
            out.write(':');
            out.write(' ');
            writeCells(table.shape, table.values[i], true);
        }
        depth -= levels;
    }

    /**
     * The root as a keyed table (spec 9.5: its header has no key), when it is an object of uniform objects that
     * carries no $type. Returns false, having written nothing, when it is not.
     */
    private boolean writeRootKeyedTable(Object root) throws IOException {
        if (root == null || isPrimitive(root)) {
            return false;
        }
        Class<?> c = root.getClass();
        if (typeMetadataEnabled) {
            Class<?> declared = rootType ? Object.class : c;
            if (needsType(c, declared) || typedForm(root, declared) != root) {
                return false;
            }
        }
        KeyedTable table = keyedTable(root);
        if (table == null) {
            return false;
        }
        writeKeyedTable(table, 1);
        return true;
    }

    /**
     * Write a single list element (after the hyphen has been written), {@code declared} being what the reader expects
     * for it (with type info on). Per TOON spec: for object/map elements, first field goes on hyphen line.
     */
    private void writeListElement(Object element, Class<?> declared) throws IOException {
        element = unwrapCarrier(element);   // resolved-Maps-graph carrier -> plain container
        if (typeMetadataEnabled) {
            element = typedForm(element, declared);   // a scalar that carries its type: {$type, value}
        }
        if (element == null || isPrimitive(element)) {
            out.write(" ");
            writeValue(element);
            return;
        }
        boolean typed = needsType(element.getClass(), declared);
        if (element instanceof Map) {
            // Nested map - first field on hyphen line per TOON spec
            Map<?, ?> mapElement = (Map<?, ?>) element;
            if (hasComplexKeys(mapElement) || keysNeedTypes(mapElement, Object.class)) {
                // Spec 10: an object's first field stands on the hyphen line, its other fields one level below
                // the hyphen. A field on a line of its own under a bare hyphen is refused by a reader.
                out.write(' ');
                depth++;
                onHyphenLine = true;
                writeMap(mapElement, typed);
                onHyphenLine = false;
                depth--;
            } else {
                writeMapInline(mapElement, typed);
            }
        } else if (element instanceof Collection) {
            // Nested collection
            if (shouldWrapArrayOrCollectionValue(element) || typed) {
                // With its type or id the collection is an object ($type, $items): its first field stands on
                // the hyphen line and the others one level below the hyphen (spec 10).
                out.write(' ');
                depth++;
                writeCollection((Collection<?>) element, typed);
                depth--;
            } else if (((Collection<?>) element).isEmpty()) {
                // §9.1: empty array as a list element uses the legacy "[0]:" form on the
                // hyphen line. The inline "[]" literal applies only to field-value position
                // ("key: []"); a bare hyphen-line empty array stays in count-marker form.
                out.write(" [0]:");
            } else {
                out.write(" ");
                keylessListItemArray = true;        // spec 9.4, in both forms: a reader refuses "- [N]{fields}:"
                try {
                    writeCollection((Collection<?>) element, false);
                } finally {
                    keylessListItemArray = false;
                }
            }
        } else if (element.getClass().isArray()) {
            // Nested array
            if (shouldWrapArrayOrCollectionValue(element) || typed) {
                out.write(' ');
                depth++;
                writeArray(element, typed);
                depth--;
            } else if (ArrayUtilities.getLength(element) == 0) {
                // §9.1 list-element empty array — see Collection branch above.
                out.write(" [0]:");
            } else {
                out.write(" ");
                keylessListItemArray = true;        // spec 9.4, in both forms: a reader refuses "- [N]{fields}:"
                try {
                    writeArray(element, false);
                } finally {
                    keylessListItemArray = false;
                }
            }
        } else {
            // An object whose writer gives it as data written in its place (an Optional's content): that value
            Object inPlace = inPlaceOf(element);
            if (inPlace != element) {
                lendIdentityToContent(element, inPlace);
                writeListElement(inPlace, typeMetadataEnabled ? contentTypeOf(element) : Object.class);
                return;
            }
            // Complex object - first field on hyphen line
            writeObjectInline(element, typed);
        }
    }

    /**
     * Write a map with first field on the current line (for list elements), with $type when {@code typed}.
     * Per TOON spec: "first field on the hyphen line"
     */
    private void writeMapInline(Map<?, ?> map, boolean typed) throws IOException {
        Object identity = identityOf(map);
        if (cycleSupport) {
            if (isWrittenAndShared(identity)) {
                out.write(" ");
                writeReferenceValue(getReferenceId(identity));
                return;
            }
            objVisited.put(identity, VISITED);
        } else if (!enterActivePath(identity)) {
            throw cycleDetected(identity);
        }

        try {
            Class<?> valueType = Object.class;
            if (typeMetadataEnabled) {
                valueType = takeElementType();
                takeKeyType();
            }
            boolean first = true;
            boolean includeId = cycleSupport && isReferenced(identity);
            boolean includeType = typed && !map.containsKey(typeKey);
            if (includeId) {
                out.write(" ");
                writeIdField(identity);
                first = false;
            }
            if (includeType) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write('\n');
                    depth++;
                    writeIndent();
                    depth--;
                }
                writeTypeField(map);
            }
            // §9.4: an empty object as a list element emits as bare hyphen — the
            // caller already wrote "-" on the line; we write nothing more here. The
            // previous " {}" emission was non-spec.

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write('\n');
                    depth++;
                    writeIndent();
                    depth--;
                }

                Object key = entry.getKey();
                String keyStr = keyText(key);
                writeFieldEntryInline(keyStr, entry.getValue(), keyNeedsQuoting(map, keyStr),
                        valueTypeIn(map, key, valueType));
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(identity);
            }
        }
    }

    /**
     * Write an object with first field on the current line (for list elements), with $type when {@code typed}.
     * Iterates WriteFieldPlan directly to avoid intermediate LinkedHashMap allocation.
     * Falls back to map-based approach only when @IoAnyGetter is present (rare).
     */
    private void writeObjectInline(Object obj, boolean typed) throws IOException {
        if (cycleSupport) {
            if (isWrittenAndShared(obj)) {
                out.write(" ");
                writeReferenceValue(getReferenceId(obj));
                return;
            }
            objVisited.put(obj, VISITED);
        } else if (!enterActivePath(obj)) {
            throw cycleDetected(obj);
        }

        try {
            Class<?> clazz = obj.getClass();
            AnnotationResolver.ClassAnnotationMetadata annMeta = AnnotationResolver.getMetadata(clazz);

            // If @IoAnyGetter is present, fall back to map-based approach (rare path); also for an object whose
            // custom writer gives it as fields (JsonClassWriter.toData)
            if (annMeta.getAnyGetterMethod() != null || isWrittenAsDataFields(obj)) {
                writeObjectInlineViaMap(obj, typed);
                return;
            }

            List<WriteOptionsBuilder.WriteFieldPlan> plans =
                    WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

            boolean includeId = cycleSupport && isReferenced(obj);
            boolean first = true;

            // Write metadata inline
            if (includeId) {
                out.write(" ");
                writeIdField(obj);
                first = false;
            }
            if (typed) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write('\n');
                    depth++;
                    writeIndent();
                    depth--;
                }
                writeTypeField(obj);
            }

            // Write fields directly from WriteFieldPlan
            for (int i = 0, len = plans.size(); i < len; i++) {
                WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
                if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                    continue;
                }
                if (canWritePrimitiveFieldDirect(plan)) {
                    if (first) {
                        out.write(" ");
                        first = false;
                    } else {
                        out.write('\n');
                        depth++;
                        writeIndent();
                        depth--;
                    }
                    writePrimitiveFieldEntry(obj, plan);
                    continue;
                }
                Object value = plan.accessor().retrieve(obj);
                if ((skipNullFields || plan.skipIfNull()) && value == null) {
                    continue;
                }
                String formatPattern = plan.formatPattern();
                if (formatPattern != null && value != null) {
                    value = applyFormatPattern(value, formatPattern);
                }
                String key = plan.accessor().getUniqueFieldName();

                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write('\n');
                    depth++;
                    writeIndent();
                    depth--;
                }
                boolean savedForceShowType = forceShowType;
                forceShowType = plan.forceShowType();
                boolean savedMapSimpleKey = currentMapHasSimpleKeyTypeHint;
                currentMapHasSimpleKeyTypeHint = plan.mapKeyTypeIsSimple();
                if (typeMetadataEnabled) {
                    declareContainerTypes(plan);
                }
                writeFieldEntryInline(key, value, plan.toonKeyNeedsQuoting(), plan.effectiveDeclaredType());
                if (typeMetadataEnabled) {
                    elementType = Object.class;
                    keyType = Object.class;
                }
                currentMapHasSimpleKeyTypeHint = savedMapSimpleKey;
                forceShowType = savedForceShowType;
            }

            // Spec 9.4: an object with nothing to write is the bare hyphen the caller has written, as an
            // empty map is. " {}" would be read as the string "{}".
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Fallback for objects with @IoAnyGetter in inline context (and objects written as their custom writer's data),
     * with $type when {@code typed}.
     */
    private void writeObjectInlineViaMap(Object obj, boolean typed) throws IOException {
        Fields fields = getObjectFields(obj);
        boolean includeId = cycleSupport && isReferenced(obj);
        if (includeId || typed) {
            Fields withMeta = new Fields(null);
            if (includeId) {
                withMeta.put(idKey, getReferenceId(obj), AS_IS);
            }
            if (typed) {
                withMeta.put(typeKey, typeNameOf(obj), AS_IS);
            }
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String key = entry.getKey();
                if (idKey.equals(key) || typeKey.equals(key)) {
                    continue;
                }
                withMeta.put(key, entry.getValue(), fields.recordedType(key));
            }
            writeMapInline(withMeta, false);
        } else {
            writeMapInline(fields, false);
        }
    }

    /**
     * Write a Map value, with $type when {@code typed}.
     */
    private void writeMap(Map<?, ?> map, boolean typed) throws IOException {
        Object identity = identityOf(map);
        if (cycleSupport) {
            if (isWrittenAndShared(identity)) {
                if (depth > 0) {
                    writeIndent();
                }
                writeReferenceValue(getReferenceId(identity));
                return;
            }
            objVisited.put(identity, VISITED);
        } else if (!enterActivePath(identity)) {
            throw cycleDetected(identity);
        }

        try {
            Class<?> valueType = Object.class;
            Class<?> keyDeclared = Object.class;
            if (typeMetadataEnabled) {
                valueType = takeElementType();
                keyDeclared = takeKeyType();
            }
            boolean includeId = cycleSupport && isReferenced(identity);
            boolean includeType = typed && !map.containsKey(typeKey);
            if (map.isEmpty()) {
                if (includeId || includeType) {
                    if (includeId) {
                        writeIndent();
                        writeIdField(identity);
                        if (includeType) {
                            out.write('\n');
                        }
                    }
                    if (includeType) {
                        writeIndent();
                        writeTypeField(map);
                    }
                }
                // Empty map with no metadata: emit nothing.
                // At the root: produces an empty document (§8 "An empty object at the root
                // yields an empty document"). At a field-value position: the caller has
                // already emitted "key:"; the empty body is correctly absent. The previous
                // "{}" emission was non-spec.
                return;
            }

            // Skip the hasComplexKeys() iteration when the enclosing field's plan told us
            // the Map's declared key type is Converter-stringifiable (String, Long, UUID,
            // Date, Enum, BigDecimal, etc.). Consumed once per writeMap — nested Maps get
            // their own dispatch with their own hint. The field flag is restored by the
            // caller after the dispatch returns.
            boolean knownSimpleKeyType = currentMapHasSimpleKeyTypeHint;
            currentMapHasSimpleKeyTypeHint = false;  // don't propagate into nested children
            if (keysNeedTypes(map, keyDeclared) || (!knownSimpleKeyType && hasComplexKeys(map))) {
                // Keys that are not strings, and keys whose class the reader must be told, are written as a
                // list beside the list of their values: $keys[N] and $items[N], two fields of an object.
                writeReferencedComplexMap(map, includeId, includeType, keyDeclared, valueType);
            } else {
                if (includeId || includeType) {
                    if (includeId) {
                        writeIndent();
                        writeIdField(identity);
                        if (includeType) {
                            out.write('\n');
                        }
                    }
                    if (includeType) {
                        writeIndent();
                        writeTypeField(map);
                    }
                    out.write('\n');
                }
                writeMapWithSimpleKeys(map, valueType);
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(identity);
            }
        }
    }

    /**
     * Check if any key in the map is a complex object that needs special handling.
     */
    private boolean hasComplexKeys(Map<?, ?> map) {
        // Through the entry set, as the check for a keyed table before it and the writer after it: a Map makes
        // each of its views once, so one view serves all three
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key != null && !isSimpleKeyType(key)) {
                return true;
            }
        }
        return false;
    }

    /** A simple map key's text: an enum written by its {@code @IoValue} method is its value, as JSON writes it. */
    private String keyText(Object key) {
        if (key == null) {
            return "null";
        }
        return key instanceof Enum && isDataClass(key.getClass()) ? String.valueOf(dataOf(key)) : key.toString();
    }

    /**
     * Check if an object can be used as a simple map key (written as a string).
     * Simple keys are: null, String, Number, Boolean, Character, Enum, Class.
     */
    private boolean isSimpleKeyType(Object key) {
        return key instanceof String
                || key instanceof Number
                || key instanceof Boolean
                || key instanceof Character
                || key instanceof Enum
                || key instanceof Class;
    }

    /**
     * Write a map with simple keys (String, Number, etc.) using the standard key: value format, {@code valueType}
     * being what the reader expects for a value (with type info on).
     * Per TOON spec, array/collection values combine key with size marker: fieldName[N]:
     */
    private void writeMapWithSimpleKeys(Map<?, ?> map, Class<?> valueType) throws IOException {
        // §13.4 safe-mode folding: capture the literal sibling key set so folded chains
        // can detect a collision with an existing literal key at this depth and skip
        // folding. Only built when keyFolding is on; null otherwise.
        java.util.Set<String> previousSiblings = currentSiblingLiteralKeys;
        if (toonKeyFolding) {
            java.util.Set<String> keys = new java.util.HashSet<>(map.size() * 2);
            for (Object k : map.keySet()) {
                if (k instanceof String) {
                    keys.add((String) k);
                }
            }
            currentSiblingLiteralKeys = keys;
        }
        try {
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.write('\n');
                }
                first = false;
                writeIndent();

                Object key = entry.getKey();
                String keyStr = keyText(key);
                writeFieldEntry(keyStr, entry.getValue(), keyNeedsQuoting(map, keyStr),
                        valueTypeIn(map, key, valueType));
            }
        } finally {
            currentSiblingLiteralKeys = previousSiblings;
        }
    }

    /**
     * Write a single key: value field entry. Handles arrays, collections, key folding,
     * nested objects, and primitives. Caller is responsible for indentation and newlines.
     * {@code declared} is what the reader expects for the value: its field's type, a map's value type, or Object.
     */
    private void writeFieldEntry(String keyStr, Object value, boolean keyNeedsQuoting, Class<?> declared)
            throws IOException {
        // Fast path: null and primitive/String field values are the most common field types
        // in a POJO. Writing them directly avoids falling through the container checks
        // (char[], array, Collection, Map) that all miss for leaf values.
        if (value == null) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(": null");
            return;
        }
        if (typeMetadataEnabled) {
            Object form = typedForm(value, declared);   // a scalar that carries its type: {$type, value}
            if (form != value) {
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth++;
                writeMap((Map<?, ?>) form, false);
                depth--;
                return;
            }
        }
        if (value instanceof String) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeString((String) value);
            return;
        }
        if (value instanceof Integer) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            out.write(toCachedLongString((Integer) value));
            return;
        }
        if (value instanceof Long) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            out.write(toCachedLongString((Long) value));
            return;
        }
        if (value instanceof Boolean) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(((Boolean) value) ? ": true" : ": false");
            return;
        }
        if (value instanceof Double) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeNumber((Number) value);
            return;
        }

        // Unwrap a resolved-Maps-graph carrier (JsonObjectArray / JsonObjectMap) before the
        // container checks below — otherwise an @items/@keys carrier (empty as a Map) would be
        // mistaken for an empty object and emit "key:" with no body.
        value = unwrapCarrier(value);

        // Check for key folding: collapse single-key map chains into dotted notation.
        // §13.4 safe-mode collision avoidance: when the candidate folded path would clash
        // with a literal sibling key, skip folding for this chain AND suppress folding in
        // the recursive descent so the entire chain emits unfolded.
        if (toonKeyFolding
                && !typeMetadataEnabled   // a folded path has nowhere to put a $type
                && !foldingSuppressed
                && value instanceof Map
                && isValidFoldableKey(keyStr)
                && !(cycleSupport && isReferenced(value))) {
            FoldedEntry folded = collectFoldedPath(keyStr, value);
            if (folded != null) {
                writeFoldedEntry(folded.path, folded.value);
                return;
            }
            if (lastFoldCollision) {
                boolean savedSuppressed = foldingSuppressed;
                foldingSuppressed = true;
                try {
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(":");
                    out.write('\n');
                    depth++;
                    writeMap((Map<?, ?>) value, needsType(value.getClass(), declared));
                    depth--;
                } finally {
                    foldingSuppressed = savedSuppressed;
                }
                return;
            }
        }

        if (value instanceof char[]) {
            // char[] is written as a plain string value (Converter handles String → char[] on read)
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeString(new String((char[]) value));
        } else if (value != null && value.getClass().isArray()) {
            // With type info off, an @IoShowType array is written in the key[N]: form, its type written only when a
            // reference wraps it (as before type info was declared-type aware)
            boolean typed = needsType(value.getClass(), declared);
            if (shouldWrapArrayOrCollectionValue(value) || (typed && typeMetadataEnabled)) {
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth++;
                writeIndent();
                writeArray(value, typed);
                depth--;
            } else {
                int length = ArrayUtilities.getLength(value);
                if (length == 0) {
                    // §9.1: empty array as field value uses inline literal "key: []",
                    // not the legacy "key[0]:" form.
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(": []");
                } else {
                    // Combine key with array size: fieldName[N]:
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(countMarker(length));
                    writeArrayElements(value, length);
                }
            }
        } else if (value instanceof Collection) {
            boolean typed = needsType(value.getClass(), declared);
            if (shouldWrapArrayOrCollectionValue(value) || typed) {
                // When type metadata is needed, use writeCollection (which emits $type/$items).
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth++;
                writeIndent();
                writeCollection((Collection<?>) value, typed);
                depth--;
            } else {
                Collection<?> coll = (Collection<?>) value;
                if (coll.isEmpty()) {
                    // §9.1: empty collection as field value uses inline literal "key: []".
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(": []");
                } else {
                    // Combine key with collection size: fieldName[N]: or fieldName[N]{cols}:
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(countMarker(coll.size()));
                    writeCollectionElementsWithHeader(coll);
                }
            }
        } else {
            // An object whose writer gives it as data written in its place (an Optional's content): that value,
            // laid out as itself (a list as "key[N]:", a scalar on the key's line)
            Object inPlace = inPlaceOf(value);
            if (inPlace != value) {
                lendIdentityToContent(value, inPlace);
                writeFieldEntry(keyStr, inPlace, keyNeedsQuoting,
                        typeMetadataEnabled ? contentTypeOf(value) : Object.class);
                return;
            }
            boolean typed = needsType(value.getClass(), declared);
            KeyedTable keyed = v4Forms && !typed ? keyedTable(value) : null;
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            if (keyed != null) {
                writeKeyedTable(keyed, 1);      // spec 9.5: an object of uniform objects
                return;
            }
            out.write(":");
            if (value instanceof Map && ((Map<?, ?>) value).isEmpty() && !typed) {
                // Empty map field value emits "key:" only — no body, no trailing "{}".
                // Per §8: nested empty object is "key:" on its own line.
                return;
            } else if (value != null && !isPrimitive(value)) {
                // Nested object - newline, no trailing space after colon
                depth++;
                if (value instanceof Map) {
                    // writeMap handles its own indentation per entry
                    out.write('\n');
                    writeMap((Map<?, ?>) value, typed);
                } else {
                    // Breaks the line itself, if it writes anything: an object with nothing to write is "key:"
                    writeNestedObject(value, typed);
                }
                depth--;
            } else {
                // Primitive value on same line - space before value
                out.write(" ");
                writeValue(value);
            }
        }
    }

    /**
     * Write a single key: value field entry in inline context (for list elements).
     * Uses depth+2 for nested structures (instead of depth+1 in regular context).
     * Caller is responsible for indentation and newlines.
     */
    private void writeFieldEntryInline(String keyStr, Object value, boolean keyNeedsQuoting, Class<?> declared)
            throws IOException {
        // Fast path: null and primitive/String field values — same as writeFieldEntry
        if (value == null) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(": null");
            return;
        }
        if (typeMetadataEnabled) {
            Object form = typedForm(value, declared);   // a scalar that carries its type: {$type, value}
            if (form != value) {
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth += 2;
                writeMap((Map<?, ?>) form, false);
                depth -= 2;
                return;
            }
        }
        if (value instanceof String) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeString((String) value);
            return;
        }
        if (value instanceof Integer) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            out.write(toCachedLongString((Integer) value));
            return;
        }
        if (value instanceof Long) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            out.write(toCachedLongString((Long) value));
            return;
        }
        if (value instanceof Boolean) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(((Boolean) value) ? ": true" : ": false");
            return;
        }
        if (value instanceof Double) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeNumber((Number) value);
            return;
        }

        // Unwrap a resolved-Maps-graph carrier before the container checks (see writeFieldEntry).
        value = unwrapCarrier(value);

        if (value instanceof char[]) {
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            out.write(':'); out.write(' ');
            writeString(new String((char[]) value));
        } else if (value != null && value.getClass().isArray()) {
            boolean typed = needsType(value.getClass(), declared);   // see writeFieldEntry
            if (shouldWrapArrayOrCollectionValue(value) || (typed && typeMetadataEnabled)) {
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth += 2;
                writeIndent();
                writeArray(value, typed);
                depth -= 2;
            } else {
                int length = ArrayUtilities.getLength(value);
                if (length == 0) {
                    // §9.1 inline empty-array literal — see writeFieldEntry.
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(": []");
                } else {
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(countMarker(length));
                    // Bump depth so tabular rows / list-element hyphens emit at +1 from the
                    // surrounding inline-list-element context. Mirrors the Collection branch
                    // below; without this, nested tabular rows under a list element wind up
                    // at the same indent as the list element's subsequent fields (wrong).
                    depth++;
                    writeArrayElements(value, length);
                    depth--;
                }
            }
        } else if (value instanceof Collection) {
            boolean typed = needsType(value.getClass(), declared);
            if (shouldWrapArrayOrCollectionValue(value) || typed) {
                writeKeyStringKnown(keyStr, keyNeedsQuoting);
                out.write(":");
                out.write('\n');
                depth += 2;
                writeIndent();
                writeCollection((Collection<?>) value, typed);
                depth -= 2;
            } else {
                Collection<?> coll = (Collection<?>) value;
                if (coll.isEmpty()) {
                    // §9.1 inline empty-array literal — see writeFieldEntry.
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(": []");
                } else {
                    writeKeyStringKnown(keyStr, keyNeedsQuoting);
                    out.write(countMarker(coll.size()));
                    depth++;
                    writeCollectionElementsWithHeader(coll);
                    depth--;
                }
            }
        } else {
            // An object whose writer gives it as data written in its place (an Optional's content): that value,
            // laid out as itself (a list as "key[N]:", a scalar on the key's line)
            Object inPlace = inPlaceOf(value);
            if (inPlace != value) {
                lendIdentityToContent(value, inPlace);
                writeFieldEntryInline(keyStr, inPlace, keyNeedsQuoting,
                        typeMetadataEnabled ? contentTypeOf(value) : Object.class);
                return;
            }
            boolean typed = needsType(value.getClass(), declared);
            KeyedTable keyed = v4Forms && !typed ? keyedTable(value) : null;
            writeKeyStringKnown(keyStr, keyNeedsQuoting);
            if (keyed != null) {
                writeKeyedTable(keyed, 2);      // spec 9.5 and 10: its entry rows two levels below the hyphen
                return;
            }
            out.write(":");
            if (value instanceof Map && ((Map<?, ?>) value).isEmpty() && !typed) {
                // Empty map field value in inline list context: just "key:" — see writeFieldEntry.
                return;
            } else if (value != null && !isPrimitive(value)) {
                depth += 2;
                if (value instanceof Map) {
                    out.write('\n');
                    writeMap((Map<?, ?>) value, typed);
                } else {
                    writeNestedObject(value, typed);
                }
                depth -= 2;
            } else {
                out.write(" ");
                writeValue(value);
            }
        }
    }

    /**
     * Write a folded entry (key path and value). Folding is off with type info on, so only @IoShowType types here.
     */
    private void writeFoldedEntry(String path, Object value) throws IOException {
        if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
            // §8 + §13.4: folded chain ending in an empty object emits bare "path:".
            // Mirrors the empty-map field-value behavior in writeFieldEntry.
            writeString(path);
            out.write(":");
            return;
        }
        if (value instanceof char[]) {
            writeString(path);
            out.write(':'); out.write(' ');
            writeString(new String((char[]) value));
        } else if (value != null && value.getClass().isArray()) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeString(path);
                out.write(":");
                out.write('\n');
                depth++;
                writeIndent();
                writeArray(value, needsType(value.getClass(), Object.class));
                depth--;
            } else {
                int length = ArrayUtilities.getLength(value);
                writeString(path);
                out.write(countMarker(length));
                writeArrayElements(value, length);
            }
        } else if (value instanceof Collection) {
            boolean typed = needsType(value.getClass(), Object.class);
            if (shouldWrapArrayOrCollectionValue(value) || typed) {
                writeString(path);
                out.write(":");
                out.write('\n');
                depth++;
                writeIndent();
                writeCollection((Collection<?>) value, typed);
                depth--;
            } else {
                Collection<?> coll = (Collection<?>) value;
                writeString(path);
                out.write(countMarker(coll.size()));
                writeCollectionElementsWithHeader(coll);
            }
        } else if (value instanceof Map) {
            // Non-foldable map at the end of the chain
            writeString(path);
            out.write(":");
            out.write('\n');
            depth++;
            writeMap((Map<?, ?>) value, needsType(value.getClass(), Object.class));
            depth--;
        } else if (value != null && !isPrimitive(value)) {
            // Object at end of chain
            writeString(path);
            out.write(":");
            depth++;
            writeNestedObject(value, needsType(value.getClass(), Object.class));
            depth--;
        } else {
            // Primitive value
            writeString(path);
            out.write(':'); out.write(' ');
            writeValue(value);
        }
    }

    /**
     * A map as its keys and its values, two lists ({$keys, $items}): for keys that are objects, or (type info on)
     * whose class the reader must be told. {@code keyType} and {@code valueType} are what the reader expects.
     */
    private void writeReferencedComplexMap(Map<?, ?> map, boolean includeId, boolean includeType,
                                           Class<?> keyType, Class<?> valueType) throws IOException {
        if (includeId) {
            writeIndent();
            out.write(idKey);
            out.write(':'); out.write(' ');
            out.write(Integer.toString(getReferenceId(identityOf(map))));
            out.write('\n');
        }
        if (includeType) {
            writeIndent();
            writeTypeField(map);
            out.write('\n');
        }
        // The keys and the values are taken from one pass over the entries, so that the n-th key stands
        // beside the n-th value whatever order the map's keySet() and values() keep for themselves.
        List<Object> keys = new ArrayList<>(map.size());
        List<Object> values = new ArrayList<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }
        writeIndent();
        out.write(keysKey);
        out.write(countMarker(keys.size()));
        if (typeMetadataEnabled) {
            elementType = keyType;
        }
        writeCollectionElementsWithHeader(keys);
        out.write('\n');
        writeIndent();
        out.write(itemsKey);
        out.write(countMarker(values.size()));
        if (typeMetadataEnabled) {
            elementType = valueType;
        }
        writeCollectionElementsWithHeader(values);
    }

    /**
     * Write a nested object with cycle detection, with $type when {@code typed}. The caller has written
     * "key:" and nothing after it: each line written here is led by its own line break. An object with nothing
     * to write (no field, or every field skipped as null) therefore stays "key:", TOON's empty object (spec 8).
     */
    private void writeNestedObject(Object obj, boolean typed) throws IOException {
        if (cycleSupport) {
            if (isWrittenAndShared(obj)) {
                out.write('\n');
                writeIndent();
                writeReferenceValue(getReferenceId(obj));
                return;
            }
            objVisited.put(obj, VISITED);
        } else if (!enterActivePath(obj)) {
            throw cycleDetected(obj);
        }

        try {
            writeObjectFields(obj, typed, true);
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Write a complex object as key: value pairs, with $type when {@code typed}.
     */
    private void writeObject(Object obj, boolean typed) throws IOException {
        if (cycleSupport) {
            if (writeReferenceIfSeen(obj)) {
                return;
            }
        } else if (!enterActivePath(obj)) {
            throw cycleDetected(obj);
        }

        try {
            writeObjectFields(obj, typed, false);
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Write an object's fields as key: value pairs, after its $type when {@code typed}.
     * Iterates WriteFieldPlan directly to avoid intermediate LinkedHashMap allocation.
     * Falls back to getObjectFields() only when @IoAnyGetter is present (rare).
     * <p>
     * With nothing to write, nothing is written: the empty object is "key:" in a field and an empty document
     * at the root (spec 8), never "{}", which a reader takes for a string.
     *
     * @param breakFirst whether the first line written is led by a line break too (the object follows "key:")
     */
    private void writeObjectFields(Object obj, boolean typed, boolean breakFirst) throws IOException {
        if (obj == null) {
            return;
        }

        Class<?> clazz = obj.getClass();
        AnnotationResolver.ClassAnnotationMetadata annMeta = AnnotationResolver.getMetadata(clazz);

        // If @IoAnyGetter is present, fall back to map-based approach (rare path); also for an object whose
        // custom writer gives it as fields (JsonClassWriter.toData)
        if (annMeta.getAnyGetterMethod() != null || isWrittenAsDataFields(obj)) {
            writeObjectFieldsViaMap(obj, typed, breakFirst);
            return;
        }

        List<WriteOptionsBuilder.WriteFieldPlan> plans =
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

        boolean includeId = cycleSupport && isReferenced(obj);
        boolean wroteField = breakFirst;        // whether the next line written is led by a line break

        // Write metadata first
        if (includeId) {
            if (wroteField) {
                out.write('\n');
            }
            writeIndent();
            writeIdField(obj);
            wroteField = true;
        }
        if (typed) {
            if (wroteField) {
                out.write('\n');
            }
            writeIndent();
            writeTypeField(obj);
            wroteField = true;
        }

        // Write fields directly from WriteFieldPlan (no intermediate Map)
        for (int i = 0, len = plans.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }
            if (canWritePrimitiveFieldDirect(plan)) {
                if (wroteField) {
                    out.write('\n');
                }
                wroteField = true;
                writeIndent();
                writePrimitiveFieldEntry(obj, plan);
                continue;
            }
            Object value = plan.accessor().retrieve(obj);
            if ((skipNullFields || plan.skipIfNull()) && value == null) {
                continue;
            }
            String formatPattern = plan.formatPattern();
            if (formatPattern != null && value != null) {
                value = applyFormatPattern(value, formatPattern);
            }
            String key = plan.accessor().getUniqueFieldName();

            if (wroteField) {
                out.write('\n');
            }
            wroteField = true;
            writeIndent();
            boolean savedForceShowType = forceShowType;
            forceShowType = plan.forceShowType();
            boolean savedMapSimpleKey = currentMapHasSimpleKeyTypeHint;
            currentMapHasSimpleKeyTypeHint = plan.mapKeyTypeIsSimple();
            if (typeMetadataEnabled) {
                declareContainerTypes(plan);
            }
            writeFieldEntry(key, value, plan.toonKeyNeedsQuoting(), plan.effectiveDeclaredType());
            if (typeMetadataEnabled) {
                elementType = Object.class;
                keyType = Object.class;
            }
            currentMapHasSimpleKeyTypeHint = savedMapSimpleKey;
            forceShowType = savedForceShowType;
        }
    }

    private boolean canWritePrimitiveFieldDirect(WriteOptionsBuilder.WriteFieldPlan plan) {
        // TOON primitive fields never emit per-field type metadata, and TOON has no
        // writeLongsAsStrings equivalent. Existing primitive wrapper field fast paths
        // also ignore forceShowType, so @IoFormat is the only primitive direct-write
        // disqualifier here.
        return plan.primitiveWriteKind() != WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_NONE
                && plan.formatPattern() == null;
    }

    private void writePrimitiveFieldEntry(Object obj, WriteOptionsBuilder.WriteFieldPlan plan) throws IOException {
        writeKeyStringKnown(plan.accessor().getUniqueFieldName(), plan.toonKeyNeedsQuoting());
        out.write(':'); out.write(' ');
        writePrimitiveFieldValue(obj, plan);
    }

    private void writePrimitiveFieldValue(Object obj, WriteOptionsBuilder.WriteFieldPlan plan) throws IOException {
        Accessor accessor = plan.accessor();
        switch (plan.primitiveWriteKind()) {
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_BOOLEAN:
                out.write(accessor.getBoolean(obj) ? "true" : "false");
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_BYTE:
                out.write(toCachedLongString(accessor.getByte(obj)));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_CHAR:
                writeString(String.valueOf(accessor.getChar(obj)));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_SHORT:
                out.write(toCachedLongString(accessor.getShort(obj)));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_INT:
                out.write(toCachedLongString(accessor.getInt(obj)));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_LONG:
                out.write(toCachedLongString(accessor.getLong(obj)));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_FLOAT:
                writeFloatPrimitive(accessor.getFloat(obj));
                break;
            case WriteOptionsBuilder.WriteFieldPlan.PRIMITIVE_DOUBLE:
                writeDoublePrimitive(accessor.getDouble(obj));
                break;
            default:
                throw new JsonIoException("Unsupported primitive field kind: " + plan.primitiveWriteKind());
        }
    }

    private void writeFloatPrimitive(float value) throws IOException {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            out.write("null");
        } else if (value == -0.0f) {
            out.write("0");
        } else {
            out.write(formatDecimalNumber(value));
        }
    }

    private void writeDoublePrimitive(double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            out.write("null");
        } else if (value == -0.0d) {
            out.write("0");
        } else {
            out.write(formatDecimalNumber(value));
        }
    }

    /**
     * Fallback for objects with @IoAnyGetter: uses getObjectFields() to collect all fields
     * including the extra ones from the annotated method (and for objects written as their custom writer's data),
     * after the object's $type when {@code typed}.
     */
    private void writeObjectFieldsViaMap(Object obj, boolean typed, boolean breakFirst) throws IOException {
        Fields fields = getObjectFields(obj);
        boolean includeId = cycleSupport && isReferenced(obj);
        if (!includeId && !typed && fields.isEmpty()) {
            return;     // the empty object: "key:" alone, or an empty document
        }
        if (breakFirst) {
            out.write('\n');
        }
        if (includeId || typed) {
            if (includeId) {
                writeIndent();
                writeIdField(obj);
            }
            if (typed) {
                if (includeId) {
                    out.write('\n');
                }
                writeIndent();
                writeTypeField(obj);
            }
            if (!fields.isEmpty()) {
                out.write('\n');
                writeMapWithSimpleKeys(fields, Object.class);
            }
        } else {
            writeMapWithSimpleKeys(fields, Object.class);
        }
    }

    /**
     * Get object fields as a map using the same WriteFieldPlan / Accessor abstraction as JsonWriter.
     * This ensures all write-side annotations are respected: {@code @IoGetter}, {@code @IoFormat},
     * {@code @IoAnyGetter}, {@code @IoProperty}, {@code @IoPropertyOrder}, {@code @IoNaming},
     * {@code @IoInclude(NON_NULL)}, etc. With type info on, each field with the type the reader expects for it.
     */
    private Fields getObjectFields(Object obj) {
        if (isDataClass(obj.getClass())) {
            Object d = dataOf(obj);
            if (d instanceof Map) {
                // The fields its custom writer gives (JsonClassWriter.toData), by name
                Fields fields = new Fields(null);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) d).entrySet()) {
                    Object value = entry.getValue();
                    fields.put(String.valueOf(entry.getKey()), value, typeMetadataEnabled ? dataType(value) : null);
                }
                return fields;
            }
        }
        Class<?> clazz = obj.getClass();

        // Use the same cached WriteFieldPlan list that JsonWriter uses
        List<WriteOptionsBuilder.WriteFieldPlan> plans =
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

        Fields result = new Fields(null);

        for (WriteOptionsBuilder.WriteFieldPlan plan : plans) {
            // Skip non-public enum fields if configured
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }

            // Accessor.retrieve() reads the field, or calls the getter named for it (@IoGetter, useGetters)
            Object value = plan.accessor().retrieve(obj);

            // Respect skipNullFields (global) and skipIfNull (per-field @IoInclude NON_NULL)
            if ((skipNullFields || plan.skipIfNull()) && value == null) {
                continue;
            }

            // Apply @IoFormat pattern if present
            String formatPattern = plan.formatPattern();
            if (formatPattern != null && value != null) {
                value = applyFormatPattern(value, formatPattern);
            }

            // Field name already has @IoProperty rename and @IoNaming applied
            String key = plan.accessor().getUniqueFieldName();
            result.put(key, value, typeMetadataEnabled ? plan.effectiveDeclaredType() : null);
        }

        // @IoAnyGetter — add extra fields from annotated method
        AnnotationResolver.ClassAnnotationMetadata annMeta = AnnotationResolver.getMetadata(clazz);
        Method anyGetter = annMeta.getAnyGetterMethod();
        if (anyGetter != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> extras = (Map<String, Object>) anyGetter.invoke(obj);
                if (extras != null) {
                    for (Map.Entry<String, Object> extra : extras.entrySet()) {
                        Object value = extra.getValue();
                        if (value != null || !skipNullFields) {
                            result.put(extra.getKey(), value, typeMetadataEnabled ? Object.class : null);
                        }
                    }
                }
            } catch (Exception e) {
                throw new JsonIoException("@IoAnyGetter invocation failed on " + clazz.getName(), e);
            }
        }

        return result;
    }

    /**
     * Apply a format pattern to a value, producing a formatted String.
     * Supports C-style String.format (detected by '%'), DateTimeFormatter,
     * SimpleDateFormat, and DecimalFormat patterns.
     */
    private static Object applyFormatPattern(Object value, String pattern) {
        try {
            if (pattern.indexOf('%') >= 0) {
                return String.format(pattern, value);
            }
            if (value instanceof java.time.temporal.TemporalAccessor) {
                return java.time.format.DateTimeFormatter.ofPattern(pattern)
                        .format((java.time.temporal.TemporalAccessor) value);
            }
            if (value instanceof java.util.Date) {
                return new java.text.SimpleDateFormat(pattern).format((java.util.Date) value);
            }
            if (value instanceof Number) {
                return new java.text.DecimalFormat(pattern).format(value);
            }
        } catch (Exception ignore) {
            // If formatting fails, fall through and return the original value
        }
        return value;
    }

    /**
     * Write a value inline (for primitive arrays).
     */
    private void writeInlineValue(Object value) throws IOException {
        if (value == null) {
            out.write("null");
            return;
        }
        // Fast path: common kinds via cheap instanceof, skipping the ClassValue lookup.
        // Mirrors the fast path in isPrimitive() — element loops call isPrimitive then
        // writeInlineValue on the same value, so without this the ClassValue dispatch
        // ran twice per element (JFR: ~58 samples across the pair).
        if (value instanceof String) {
            writeString((String) value);
            return;
        }
        if (value instanceof Number) {
            writeNumber((Number) value);
            return;
        }
        if (value instanceof Boolean) {
            out.write(((Boolean) value) ? "true" : "false");
            return;
        }
        if (value instanceof Character) {
            writeString(String.valueOf(value));
            return;
        }
        switch (writeTypeCache.get(value.getClass())) {
            case STRING:
                writeString((String) value);
                break;
            case NUMBER:
                writeNumber((Number) value);
                break;
            case BOOLEAN:
                out.write(((Boolean) value) ? "true" : "false");
                break;
            case CHARACTER:
                writeString(String.valueOf(value));
                break;
            case CONVERTER_SUPPORTED: {
                // Same temporal fast path as writeValue's CONVERTER_SUPPORTED case.
                writeString(converterText(value));
                break;
            }
            default:
                writeInlineDefault(value);
                break;
        }
    }

    /** Inline only when isPrimitive said so: an object's data written in its place, else its toString(). */
    private void writeInlineDefault(Object value) throws IOException {
        if (isDataClass(value.getClass())) {
            Object d = dataOf(value);
            if (isInPlace(d)) {
                writeInlineValue(d);
                return;
            }
        }
        writeString(value.toString());
    }

    /**
     * Check if a value is a "primitive" for TOON formatting purposes.
     * A primitive is a value that can be written inline (not as nested structure).
     * This includes basic types and any type that Converter can convert to String,
     * but excludes structural types (Map, Collection, Array).
     */
    private static boolean isNegativeZero(Object value) {
        return (value instanceof Double && Double.doubleToRawLongBits((Double) value) == Long.MIN_VALUE)
                || (value instanceof Float && Float.floatToRawIntBits((Float) value) == Integer.MIN_VALUE);
    }

    private boolean isPrimitive(Object value) {
        if (value == null) {
            return true;
        }
        // Fast path: common primitive wrappers detected via cheap instanceof, skipping the
        // ClassValue (writeTypeCache) lookup. Mirrors the fast path in writeValue().
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return true;
        }
        switch (writeTypeCache.get(value.getClass())) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
            case CHARACTER:
            case CONVERTER_SUPPORTED:
                return true;
            default:
                return false;
        }
    }

    /** An object written as data in its place is as inline as that data: an Optional of a string is. */
    private boolean isPrimitiveData(Object value) {
        if (!isDataClass(value.getClass())) {
            return false;
        }
        Object d = dataOf(value);
        return isInPlace(d) && isPrimitive(d);
    }

    /**
     * Check if all elements in an array are primitives (that carry no $type where the component type is declared).
     */
    private boolean isAllPrimitives(Object array, int length) {
        Class<?> componentType = array.getClass().getComponentType();
        if (componentType.isPrimitive()) {
            return true;
        }

        for (int i = 0; i < length; i++) {
            Object element = getArrayElement(array, i);
            if (element != null && !isPlainInline(element, componentType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a list's element sits on the list's line with no $type where {@code declared} is expected: a primitive
     * that needs no $type, or an object written as data in its place that is (an Optional of a string) and whose
     * content needs none either. A primitive is never such an object, so it is not asked what it holds.
     */
    private boolean isPlainInline(Object element, Class<?> declared) {
        if (isPrimitive(element)) {
            return !typeMetadataEnabled || !needsType(element, declared);
        }
        return isPrimitiveData(element) && (!typeMetadataEnabled
                || (!needsType(element.getClass(), declared) && !holdsTypedContent(element)));
    }

    private void traceReferences(Object root) {
        if (root == null || isPrimitive(root) || writeOptions.isNonReferenceableClass(root.getClass())) {
            return;
        }
        traceStack.clear();
        final Deque<Object> stack = traceStack;
        stack.addFirst(root);

        while (!stack.isEmpty()) {
            final Object current = stack.removeFirst();
            if (current == null) {
                continue;
            }
            final Class<?> clazz = current.getClass();
            final boolean isNonReferenceable = isPrimitive(current) || writeOptions.isNonReferenceableClass(clazz);
            if (isNonReferenceable) {
                continue;
            }

            int seen = objVisited.get(current);
            if (seen != 0) {
                seenAgain(stack, current, seen);
                continue;
            }

            objVisited.put(current, VISITED);
            pushReferenceCandidates(stack, current);
        }
    }

    /**
     * {@code current} met again in the trace: referenced, if it was met once; for one written as its {@code @IoValue}
     * method's value, its value again, as each place holding it holds its value.
     */
    private void seenAgain(Deque<Object> stack, Object current, int seen) {
        if (seen == VISITED) {
            int id = nextIdentity++;
            objVisited.put(current, VISITED + 1);
            objsReferenced.put(current, id);
        } else if (seen == VALUE_HOLDER) {
            pushValueOf(stack, current);
        }
    }

    private void pushReferenceCandidates(Deque<Object> stack, Object current) {
        if (current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                pushReferenceCandidate(stack, entry.getKey());
                pushReferenceCandidate(stack, entry.getValue());
            }
            return;
        }

        if (current instanceof Collection) {
            for (Object element : (Collection<?>) current) {
                pushReferenceCandidate(stack, element);
            }
            return;
        }

        if (current.getClass().isArray()) {
            if (current instanceof Object[]) {
                for (Object element : (Object[]) current) {
                    pushReferenceCandidate(stack, element);
                }
            }
            return;
        }

        List<WriteOptionsBuilder.WriteFieldPlan> plans =
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, current.getClass());
        if (WriteOptionsBuilder.mayBeWrittenAsData(plans) && pushWrittenData(stack, current, plans)) {
            return;
        }
        for (int i = 0, len = plans.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
            if (plan.skipReferenceTrace()) {
                continue;
            }
            if (canSkipContainerTrace(plan)) {
                // Field is a Collection/Map/Array of non-referenceable leaves — skip tracing.
                // See JsonWriter.canSkipContainerTrace for rationale and the
                // preserveLeafContainerIdentity flag governing this behavior.
                continue;
            }
            Object value = plan.accessor().retrieve(current);
            pushReferenceCandidate(stack, value);
        }
    }

    private boolean canSkipContainerTrace(WriteOptionsBuilder.WriteFieldPlan plan) {
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

    /**
     * Pushes what is written for {@code current} in place of its fields, when that is not them: the value of its
     * {@code @IoValue} method, or what its custom writer gives (JsonClassWriter.toData), which its fields may not show
     * (an Optional's content or an AtomicReferenceArray's elements are in fields the JDK keeps to itself). False for
     * an object written as its fields. Out of line, so the trace's hot method stays small enough to compile well.
     */
    private boolean pushWrittenData(Deque<Object> stack, Object current,
                                    List<WriteOptionsBuilder.WriteFieldPlan> plans) {
        if (WriteOptionsBuilder.getValueMethod(writeOptions, current.getClass(), plans) != null) {
            pushValueOf(stack, current);
            return true;
        }
        JsonClassWriter dataWriter = WriteOptionsBuilder.getDataWriter(writeOptions, current.getClass(), plans);
        if (dataWriter == null) {
            return false;
        }
        Object d = dataWriter.toData(current);
        if (d == JsonClassWriter.NO_DATA) {
            return false;
        }
        if (d instanceof Map) {
            for (Object value : ((Map<?, ?>) d).values()) {
                pushReferenceCandidate(stack, value);
            }
        } else {
            pushReferenceCandidate(stack, d);
        }
        return true;
    }

    /**
     * {@code holder}, written as the value of its {@code @IoValue} method in its place, where no $id can go: it is
     * never referenced itself, and each place holding it holds its value, traced each time (so a value object held
     * twice, through one holder or two, gets the $id), as JSON's reference pass does. A value that is itself such an
     * object is followed here, up to the depth limit: one made afresh each time, or two whose values are each other,
     * would never end.
     */
    private void pushValueOf(Deque<Object> stack, Object holder) {
        int depthLimit = writeOptions.getMaxObjectGraphDepth();
        Object value = holder;
        for (int depth = 0; ; depth++) {
            objVisited.put(value, VALUE_HOLDER);
            value = valueOf(value);
            if (value == null || isPrimitive(value) || writeTypeCache.get(value.getClass()) != ToonWriteType.POJO
                    || WriteOptionsBuilder.getValueMethod(writeOptions, value.getClass(),
                            WriteOptionsBuilder.getWriteFieldPlans(writeOptions, value.getClass())) == null) {
                break;
            }
            if (depth >= depthLimit) {
                throw new JsonIoException("Object graph too deep (>" + depthLimit
                        + " levels): each @IoValue method returns another object written by its @IoValue method.");
            }
        }
        pushReferenceCandidate(stack, value);
    }

    private void pushReferenceCandidate(Deque<Object> stack, Object candidate) {
        if (candidate != null && !isPrimitive(candidate) && !writeOptions.isNonReferenceableClass(candidate.getClass())) {
            stack.addFirst(candidate);
        }
    }

    /**
     * Write indentation for current depth.
     */
    private void writeIndent() throws IOException {
        if (onHyphenLine) {
            onHyphenLine = false;       // the line was begun by a list item's hyphen: its first field follows it
            return;
        }
        if (depth < INDENT_CACHE_SIZE) {
            out.write(indentCache[depth]);
            return;
        }
        for (int i = 0; i < depth; i++) {
            out.write(indentUnit);
        }
    }

    // ========== Key Folding Support ==========

    /**
     * Check if a key is a valid identifier for folding (matches ^[A-Za-z_][A-Za-z0-9_]*$).
     */
    private boolean isValidFoldableKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        char first = key.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a map is foldable (has exactly one entry with a valid identifier key).
     */
    private boolean isFoldableMap(Map<?, ?> map) {
        if (map.size() != 1) {
            return false;
        }
        Object key = map.keySet().iterator().next();
        return key instanceof String && isValidFoldableKey((String) key);
    }

    /**
     * Collect the folded key path and leaf value from a chain of single-key maps.
     * Returns null if the chain is not foldable.
     */
    private FoldedEntry collectFoldedPath(String firstKey, Object value) {
        lastFoldCollision = false;
        StringBuilder path = new StringBuilder(firstKey);
        Object current = value;
        Map<Object, Boolean> seen = new IdentityHashMap<>();

        while (current instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) current;
            if (cycleSupport && isReferenced(map)) {
                return null;
            }
            if (seen.put(map, Boolean.TRUE) != null) {
                return null;
            }
            if (!isFoldableMap(map)) {
                break;
            }
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();
            String key = (String) entry.getKey();
            path.append('.').append(key);
            current = entry.getValue();
        }

        // Only fold if we actually folded something (path contains a dot)
        if (path.indexOf(".") > 0) {
            String pathStr = path.toString();
            // §13.4 safe-mode requirement: the resulting folded key string MUST NOT equal
            // any existing sibling literal key at the same object depth. If it would
            // collide with a literal key already in the parent map, skip folding so the
            // chain is emitted as a normal nested structure.
            if (currentSiblingLiteralKeys != null && currentSiblingLiteralKeys.contains(pathStr)) {
                lastFoldCollision = true;
                return null;
            }
            return new FoldedEntry(pathStr, current);
        }
        return null;
    }

    private static class UniformPojoArrayData {
        final List<String> keys;
        final List<WriteOptionsBuilder.WriteFieldPlan> activePlans;
        final String typeName; // null if no type metadata needed

        UniformPojoArrayData(List<String> keys, List<WriteOptionsBuilder.WriteFieldPlan> activePlans, String typeName) {
            this.keys = keys;
            this.activePlans = activePlans;
            this.typeName = typeName;
        }
    }

    /**
     * Helper class to hold folded key path and leaf value.
     */
    private static class FoldedEntry {
        final String path;
        final Object value;

        FoldedEntry(String path, Object value) {
            this.path = path;
            this.value = value;
        }
    }

    @Override
    public void close() throws IOException {
        if (writeOptions.isCloseStream()) {
            IOUtilities.close(out);
        }
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }
}
