package com.cedarsoftware.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.lang.reflect.Method;

import com.cedarsoftware.io.reflect.AnnotationResolver;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.Converter;
import com.cedarsoftware.util.FastWriter;
import com.cedarsoftware.util.IOUtilities;

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
    private static final Map<Long, String> SHARED_DOUBLE_FORMAT_CACHE = new ConcurrentHashMap<>(1024);
    private static final Map<Integer, String> SHARED_FLOAT_FORMAT_CACHE = new ConcurrentHashMap<>(512);
    private static final int COUNT_MARKER_CACHE_SIZE = 256;
    private static final String[] COUNT_MARKER_CACHE_COMMA = buildCountMarkerCache(',');
    private static final String[] COUNT_MARKER_CACHE_TAB = buildCountMarkerCache('\t');
    private static final String[] COUNT_MARKER_CACHE_PIPE = buildCountMarkerCache('|');

    // Cached type dispatch enum - mirrors JsonWriter's writeTypeCache pattern.
    // Computed once per class, avoids repeated instanceof + Converter.isConversionSupportedFor() calls.
    private enum ToonWriteType {
        ARRAY, COLLECTION, MAP, STRING, NUMBER, BOOLEAN, CHARACTER,
        CONVERTER_SUPPORTED, VALUE_METHOD, POJO
    }

    private static final ClassValue<ToonWriteType> writeTypeCache = new ClassValue<ToonWriteType>() {
        @Override
        protected ToonWriteType computeValue(Class<?> type) {
            if (type.isArray()) return ToonWriteType.ARRAY;
            if (Collection.class.isAssignableFrom(type)) return ToonWriteType.COLLECTION;
            if (Map.class.isAssignableFrom(type)) return ToonWriteType.MAP;
            if (type == String.class) return ToonWriteType.STRING;
            if (Number.class.isAssignableFrom(type)) return ToonWriteType.NUMBER;
            if (type == Boolean.class) return ToonWriteType.BOOLEAN;
            if (type == Character.class) return ToonWriteType.CHARACTER;
            if (Converter.isConversionSupportedFor(type, String.class)) return ToonWriteType.CONVERTER_SUPPORTED;
            if (AnnotationResolver.getMetadata(type).getValueMethod() != null) return ToonWriteType.VALUE_METHOD;
            return ToonWriteType.POJO;
        }
    };

    private final WriteOptions writeOptions;
    private final Writer out;
    private final char delimiter;  // Default comma, configurable to pipe or tab
    private final Map<String, Boolean> quoteDecisionCache;
    private final AtomicInteger quoteDecisionCacheSize;
    private final boolean cycleSupport;
    private final boolean skipNullFields;
    private final boolean toonKeyFolding;
    private final boolean enumPublicFieldsOnly;
    private final String idKey;
    private final String refKey;
    private final String itemsKey;
    private final String keysKey;
    private final String typeKey;
    private final boolean typeMetadataEnabled;
    private int depth = 0;
    private int nextIdentity = 1;

    // Track objects written during cycleSupport=true pass
    private final IdentityIntMap objVisited = new IdentityIntMap(256);
    // Track objects that require @id emission (shared references/cycles)
    private final IdentityIntMap objsReferenced = new IdentityIntMap(256);
    // Track active traversal path when cycleSupport=false
    private final Map<Object, Boolean> activePath = new IdentityHashMap<>();
    // Cache resolved output type names per class for this write operation.
    private final Map<Class<?>, String> typeNameCache = new IdentityHashMap<>(32);

    private static String[] buildIndentCache() {
        String[] cache = new String[INDENT_CACHE_SIZE];
        StringBuilder builder = new StringBuilder(INDENT_CACHE_SIZE * INDENT.length());
        for (int i = 0; i < INDENT_CACHE_SIZE; i++) {
            cache[i] = builder.toString();
            builder.append(INDENT);
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
        this.out = out;
        this.delimiter = this.writeOptions.getToonDelimiter();
        if (delimiter == '\t') {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_TAB;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_TAB;
        } else if (delimiter == '|') {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_PIPE;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_PIPE;
        } else {
            this.quoteDecisionCache = SHARED_QUOTE_DECISION_CACHE_COMMA;
            this.quoteDecisionCacheSize = SHARED_QUOTE_DECISION_CACHE_SIZE_COMMA;
        }
        this.cycleSupport = this.writeOptions.isCycleSupport();
        this.skipNullFields = this.writeOptions.isSkipNullFields();
        this.toonKeyFolding = this.writeOptions.isToonKeyFolding();
        this.enumPublicFieldsOnly = this.writeOptions.isEnumPublicFieldsOnly();

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
            objVisited.clear();
            objsReferenced.clear();
            activePath.clear();
            nextIdentity = 1;
            if (cycleSupport) {
                traceReferences(obj);
                objVisited.clear();
            }
            writeValue(obj);
            out.flush();
        } catch (IOException e) {
            throw new JsonIoException("Error writing TOON output", e);
        } finally {
            objVisited.clear();
            objsReferenced.clear();
            activePath.clear();
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

        Class<?> clazz = value.getClass();

        switch (writeTypeCache.get(clazz)) {
            case ARRAY:
                writeArray(value);
                break;
            case COLLECTION:
                writeCollection((Collection<?>) value);
                break;
            case MAP:
                writeMap((Map<?, ?>) value);
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
            case CONVERTER_SUPPORTED:
                writeString(Converter.convert(value, String.class));
                break;
            case VALUE_METHOD:
                try {
                    Method valueMethod = AnnotationResolver.getMetadata(clazz).getValueMethod();
                    Object val = valueMethod.invoke(value);
                    writeValue(val);
                } catch (Exception e) {
                    throw new JsonIoException("@IoValue method invocation failed for " + clazz.getName(), e);
                }
                break;
            case POJO:
                writeObject(value);
                break;
        }
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
        out.write(": ");
        out.write(Integer.toString(refId));
    }

    private void writeIdField(Object value) throws IOException {
        out.write(idKey);
        out.write(": ");
        out.write(Integer.toString(getReferenceId(value)));
    }

    private boolean shouldWriteTypeMetadata(Class<?> clazz) {
        return typeMetadataEnabled && clazz != null && !isPrimitiveClass(clazz);
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

    private String getTypeNameForOutput(Class<?> clazz) {
        String cached = typeNameCache.get(clazz);
        if (cached != null) {
            return cached;
        }
        String alias = writeOptions.getTypeNameAlias(clazz.getName());
        typeNameCache.put(clazz, alias);
        return alias;
    }

    private void writeTypeField(Class<?> clazz) throws IOException {
        out.write(typeKey);
        out.write(": ");
        writeString(getTypeNameForOutput(clazz));
    }

    private boolean writeReferenceIfSeen(Object value) throws IOException {
        if (!cycleSupport || !isReferenceable(value)) {
            return false;
        }
        if (objVisited.get(value) != 0) {
            int refId = getReferenceId(value);
            if (refId == 0) {
                throw new JsonIoException("Reference tracking failure for repeated value of type: "
                        + value.getClass().getName());
            }
            writeReferenceValue(refId);
            return true;
        }
        objVisited.put(value, VISITED);
        return false;
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
     * Write a key string, handling dots appropriately based on key folding setting.
     * When key folding is OFF, dots in keys must be quoted to prevent expansion on read.
     */
    private void writeKeyString(String key) throws IOException {
        if (key.isEmpty()) {
            writeQuotedString(key);
            return;
        }

        // When key folding is OFF, dots in keys must be quoted to prevent expansion on read
        if (!toonKeyFolding && key.indexOf('.') >= 0) {
            writeQuotedString(key);
            return;
        }

        if (needsQuoting(key)) {
            writeQuotedString(key);
        } else {
            out.write(key);
        }
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
     *   <li>Start with hyphen</li>
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
     */
    private boolean needsQuoting(String str) {
        if (str == null || str.isEmpty()) {
            return true;  // Empty strings must be quoted
        }

        // Check cache FIRST - covers all cases including reserved literals.
        Boolean cached = quoteDecisionCache.get(str);
        if (cached != null) {
            return cached;
        }

        // Reserved literals must stay quoted to preserve scalar-vs-string semantics.
        if (isReservedScalarLiteral(str)) {
            cacheQuoteDecision(str, Boolean.TRUE);
            return true;
        }

        // Fast path for common identifier-like tokens (field names, enum names, etc.).
        if (isSimpleUnquotedAsciiToken(str)) {
            cacheQuoteDecision(str, Boolean.FALSE);
            return false;
        }

        boolean quote = computeNeedsQuoting(str);
        cacheQuoteDecision(str, quote);
        return quote;
    }

    private void cacheQuoteDecision(String str, Boolean decision) {
        if (quoteDecisionCacheSize.get() < QUOTE_DECISION_CACHE_MAX) {
            Boolean prior = quoteDecisionCache.putIfAbsent(str, decision);
            if (prior == null) {
                quoteDecisionCacheSize.incrementAndGet();
            }
        }
    }

    private static boolean isReservedScalarLiteral(String str) {
        return "true".equals(str) || "false".equals(str) || "null".equals(str);
    }

    private boolean isSimpleUnquotedAsciiToken(String str) {
        char first = str.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_')) {
            return false;
        }

        int len = str.length();
        for (int i = 1; i < len; i++) {
            char c = str.charAt(i);
            if (c == delimiter) {
                return false;
            }
            if (!((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-')) {
                return false;
            }
        }
        return true;
    }

    private boolean computeNeedsQuoting(String str) {
        // Check for leading/trailing whitespace
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if (Character.isWhitespace(first) || Character.isWhitespace(last)) {
            return true;
        }

        // Note: reserved literals (true/false/null) are already handled by needsQuoting() before this call.

        // Check if it looks like a number
        if (looksLikeNumber(str)) {
            return true;
        }

        // Check for hyphen at start
        if (first == '-') {
            return true;
        }

        // Check for special characters and control characters
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            // Control characters (ASCII 0-31) require quoting
            // Note: \n (10), \r (13), \t (9) are control chars that need escaping
            if (c < 32) {
                return true;
            }
            if (c == ':' || c == '"' || c == '\\' || c == '[' || c == ']' ||
                c == '{' || c == '}' || c == delimiter) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if a string looks like a number (needs quoting to preserve as string).
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
     * Write string content with TOON escape sequences.
     * Only valid escapes: \\, \", \n, \r, \t
     * Uses batch-scanning to write runs of safe characters in a single write() call.
     */
    private void writeEscapedString(String str) throws IOException {
        int len = str.length();
        int last = 0;
        for (int i = 0; i < len; i++) {
            String escape;
            switch (str.charAt(i)) {
                case '\\': escape = "\\\\"; break;
                case '"':  escape = "\\\""; break;
                case '\n': escape = "\\n"; break;
                case '\r': escape = "\\r"; break;
                case '\t': escape = "\\t"; break;
                default: continue;
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
        if (num instanceof Double) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.write("null");  // TOON spec: NaN/Infinity -> null
            } else if (d == -0.0) {
                out.write("0");  // TOON spec: -0 -> 0
            } else {
                out.write(formatDecimalNumber(d));
            }
        } else if (num instanceof Float) {
            float f = num.floatValue();
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                out.write("null");
            } else if (f == -0.0f) {
                out.write("0");
            } else {
                out.write(formatDecimalNumber(f));
            }
        } else if (num instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) num;
            out.write(formatBigDecimal(bd));
        } else if (num instanceof BigInteger) {
            out.write(num.toString());
        } else if (num instanceof AtomicInteger || num instanceof AtomicLong) {
            out.write(toCachedLongString(num.longValue()));
        } else {
            // Integer, Long, Short, Byte
            if (num instanceof Integer || num instanceof Long || num instanceof Short || num instanceof Byte) {
                out.write(toCachedLongString(num.longValue()));
            } else {
                out.write(num.toString());
            }
        }
    }

    /**
     * Format a decimal number (double or float) according to TOON spec:
     * - No exponent notation
     * - No trailing zeros in fractional part
     * - Whole numbers have no decimal point
     */
    private String formatDecimalNumber(double d) {
        long bits = Double.doubleToLongBits(d);
        String cached = SHARED_DOUBLE_FORMAT_CACHE.get(bits);
        if (cached != null) {
            return cached;
        }

        String formatted;
        if (d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
            long l = (long) d;
            if (d == l) {
                formatted = toCachedLongString(l);
                if (SHARED_DOUBLE_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
                    SHARED_DOUBLE_FORMAT_CACHE.putIfAbsent(bits, formatted);
                }
                return formatted;
            }
        }

        String text = Double.toString(d);
        if (text.indexOf('E') < 0 && text.indexOf('e') < 0) {
            formatted = text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
        } else {
            formatted = formatBigDecimal(BigDecimal.valueOf(d));
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
        if (f >= Long.MIN_VALUE && f <= Long.MAX_VALUE) {
            long l = (long) f;
            if (f == l) {
                formatted = toCachedLongString(l);
                if (SHARED_FLOAT_FORMAT_CACHE.size() < DECIMAL_FORMAT_CACHE_MAX) {
                    SHARED_FLOAT_FORMAT_CACHE.putIfAbsent(bits, formatted);
                }
                return formatted;
            }
        }

        String text = Float.toString(f);
        if (text.indexOf('E') < 0 && text.indexOf('e') < 0) {
            formatted = text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
        } else {
            formatted = formatBigDecimal(new BigDecimal(text));
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
     * Write an array value (standalone, not as a field value).
     */
    private void writeArray(Object array) throws IOException {
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
            boolean includeType = shouldWriteTypeMetadata(array.getClass());
            boolean wroteMeta = false;
            if (includeId) {
                writeIdField(array);
                wroteMeta = true;
            }
            if (includeType) {
                if (wroteMeta) {
                    out.write(NEW_LINE);
                    writeIndent();
                }
                writeTypeField(array.getClass());
                wroteMeta = true;
            }
            if (wroteMeta) {
                out.write(NEW_LINE);
                writeIndent();
                out.write(itemsKey);
            }
            out.write(countMarker(length));
            writeArrayElements(array, length);
        } finally {
            if (!cycleSupport) {
                exitActivePath(array);
            }
        }
    }

    /**
     * Write array elements (after the [N]: marker has been written).
     * Per TOON spec: first field of object elements goes on hyphen line.
     */
    private void writeArrayElements(Object array, int length) throws IOException {
        if (length == 0) {
            out.write(":");
            return;
        }

        // Check if all elements are primitives (can use inline format)
        if (isAllPrimitives(array, length)) {
            out.write(": ");
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.write(delimiter);
                }
                writeInlineValue(getArrayElement(array, i));
            }
        } else if (!writeOptions.isPrettyPrint()) {
            // Try tabular format for uniform POJO arrays
            UniformPojoArrayData uniformPOJO = getUniformPOJODataFromArray(array, length);
            if (uniformPOJO != null) {
                writeTabularHeader(uniformPOJO.keys);
                writeTabularPOJORows(uniformPOJO);
            } else {
                // Mixed/complex array - use list format with hyphens
                out.write(":");
                depth++;
                for (int i = 0; i < length; i++) {
                    out.write(NEW_LINE);
                    writeIndent();
                    out.write("-");
                    Object element = getArrayElement(array, i);
                    writeListElement(element);
                }
                depth--;
            }
        } else {
            // prettyPrint=true → always use verbose list format
            out.write(":");
            depth++;
            for (int i = 0; i < length; i++) {
                out.write(NEW_LINE);
                writeIndent();
                out.write("-");
                Object element = getArrayElement(array, i);
                writeListElement(element);
            }
            depth--;
        }
    }

    /**
     * Write a Collection value (standalone, not as a field value).
     */
    private void writeCollection(Collection<?> collection) throws IOException {
        if (cycleSupport) {
            if (writeReferenceIfSeen(collection)) {
                return;
            }
        } else if (!enterActivePath(collection)) {
            throw cycleDetected(collection);
        }

        try {
            boolean includeId = cycleSupport && isReferenced(collection);
            boolean includeType = shouldWriteTypeMetadata(collection.getClass());
            boolean wroteMeta = false;
            if (includeId) {
                writeIdField(collection);
                wroteMeta = true;
            }
            if (includeType) {
                if (wroteMeta) {
                    out.write(NEW_LINE);
                    writeIndent();
                }
                writeTypeField(collection.getClass());
                wroteMeta = true;
            }
            if (wroteMeta) {
                out.write(NEW_LINE);
                writeIndent();
                out.write(itemsKey);
            }
            out.write(countMarker(collection.size()));
            writeCollectionElementsWithHeader(collection);
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
        if (collection.isEmpty()) {
            out.write(":");
            return;
        }

        // Check if all elements are primitives
        boolean allPrimitives = true;
        for (Object element : collection) {
            if (element != null && !isPrimitive(element)) {
                allPrimitives = false;
                break;
            }
        }

        if (allPrimitives) {
            out.write(": ");
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
            List<String> uniformKeys = getUniformKeys(collection);
            if (uniformKeys != null) {
                // Tabular format: {key1,key2,...}: followed by CSV rows
                writeTabularHeader(uniformKeys);
                writeTabularRows(collection, uniformKeys);
            } else if (!writeOptions.isPrettyPrint()) {
                // Check for uniform POJO array → tabular format (default, compact)
                UniformPojoArrayData uniformPOJO = getUniformPOJODataFromCollection(collection);
                if (uniformPOJO != null) {
                    writeTabularHeader(uniformPOJO.keys);
                    writeTabularPOJORows(uniformPOJO);
                } else {
                    // Non-uniform or mixed - use list format with hyphens
                    out.write(":");
                    depth++;
                    for (Object element : collection) {
                        out.write(NEW_LINE);
                        writeIndent();
                        out.write("-");
                        writeListElement(element);
                    }
                    depth--;
                }
            } else {
                // prettyPrint=true → always use verbose list format
                out.write(":");
                depth++;
                for (Object element : collection) {
                    out.write(NEW_LINE);
                    writeIndent();
                    out.write("-");
                    writeListElement(element);
                }
                depth--;
            }
        }
    }

    /**
     * Check if a collection contains uniform objects (all Maps with identical keys and primitive values).
     * Returns the ordered list of keys if uniform, null otherwise.
     */
    private List<String> getUniformKeys(Collection<?> collection) {
        List<String> keys = null;

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

            if (map.isEmpty()) {
                return null;  // Empty objects break uniformity
            }

            // Check all keys are simple types (strings)
            List<String> elementKeys = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                if (!(key instanceof String)) {
                    return null;  // Non-string keys break uniformity
                }
                // Check that values are primitives (for tabular format)
                Object value = entry.getValue();
                if (value != null && !isPrimitive(value)) {
                    return null;  // Complex values break tabular format
                }
                elementKeys.add((String) key);
            }

            if (keys == null) {
                keys = elementKeys;
            } else if (!keys.equals(elementKeys)) {
                return null;  // Different keys break uniformity
            }
        }

        return keys;
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
            out.write(key);
        }
        out.write("}:");
    }

    /**
     * Write tabular rows (CSV-like format).
     */
    private void writeTabularRows(Collection<?> collection, List<String> keys) throws IOException {
        depth++;
        for (Object element : collection) {
            out.write(NEW_LINE);
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
     * Returns keys plus precomputed row field maps if uniform, null otherwise.
     * <p>
     * POJOs qualify for tabular format when:
     * <ul>
     *   <li>All elements are the same concrete class</li>
     *   <li>All field values are primitives (no nested objects/arrays)</li>
     *   <li>All elements have the same fields in the same order</li>
     * </ul>
     */
    private UniformPojoArrayData getUniformPOJODataFromCollection(Collection<?> collection) {
        List<String> keys = null;
        List<Map<String, Object>> rows = new ArrayList<>(collection.size());
        Class<?> elementClass = null;

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
            } else if (elementClass != element.getClass()) {
                return null;
            }

            // Get the object's field names and values
            Map<String, Object> fields = getObjectFields(element);
            if (fields.isEmpty()) {
                return null;  // Empty objects break tabular format
            }

            // All field values must be primitive for tabular format
            for (Object value : fields.values()) {
                if (value != null && !isPrimitive(value)) {
                    return null;
                }
            }

            if (keys == null) {
                keys = new ArrayList<>(fields.keySet());
            } else if (!hasSameKeyOrder(fields, keys)) {
                return null;  // Different fields break uniformity
            }
            rows.add(fields);
        }

        if (keys == null) {
            return null;
        }

        // Inject @type as the first column when type metadata is needed
        if (shouldWriteTypeMetadata(elementClass)) {
            String typeName = getTypeNameForOutput(elementClass);
            keys.add(0, typeKey);
            for (Map<String, Object> row : rows) {
                row.put(typeKey, typeName);
            }
        }

        return new UniformPojoArrayData(keys, rows);
    }

    /**
     * Check if an array contains uniform POJOs eligible for tabular format, and
     * capture field maps so they can be reused when writing rows.
     */
    private UniformPojoArrayData getUniformPOJODataFromArray(Object array, int length) {
        List<String> keys = null;
        List<Map<String, Object>> rows = new ArrayList<>(length);
        Class<?> elementClass = null;

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
            } else if (elementClass != element.getClass()) {
                return null;
            }

            Map<String, Object> fields = getObjectFields(element);
            if (fields.isEmpty()) {
                return null;
            }
            for (Object value : fields.values()) {
                if (value != null && !isPrimitive(value)) {
                    return null;
                }
            }

            if (keys == null) {
                keys = new ArrayList<>(fields.keySet());
            } else if (!hasSameKeyOrder(fields, keys)) {
                return null;
            }
            rows.add(fields);
        }

        if (keys == null) {
            return null;
        }

        // Inject @type as the first column when type metadata is needed
        if (shouldWriteTypeMetadata(elementClass)) {
            String typeName = getTypeNameForOutput(elementClass);
            keys.add(0, typeKey);
            for (Map<String, Object> row : rows) {
                row.put(typeKey, typeName);
            }
        }

        return new UniformPojoArrayData(keys, rows);
    }

    private boolean hasSameKeyOrder(Map<String, Object> fields, List<String> keys) {
        if (fields.size() != keys.size()) {
            return false;
        }
        int i = 0;
        for (String fieldKey : fields.keySet()) {
            if (!fieldKey.equals(keys.get(i))) {
                return false;
            }
            i++;
        }
        return true;
    }

    /**
     * Write tabular rows for a collection/array of uniform POJOs (CSV-like format).
     * Uses precomputed rows to avoid re-reading fields.
     */
    private void writeTabularPOJORows(UniformPojoArrayData uniformPOJO) throws IOException {
        depth++;
        for (Map<String, Object> fields : uniformPOJO.rows) {
            out.write(NEW_LINE);
            writeIndent();

            boolean first = true;
            for (String key : uniformPOJO.keys) {
                if (!first) {
                    out.write(delimiter);
                }
                first = false;
                writeInlineValue(fields.get(key));
            }
        }
        depth--;
    }

    /**
     * Write a single list element (after the hyphen has been written).
     * Per TOON spec: for object/map elements, first field goes on hyphen line.
     */
    private void writeListElement(Object element) throws IOException {
        if (element == null || isPrimitive(element)) {
            out.write(" ");
            writeValue(element);
        } else if (element instanceof Map) {
            // Nested map - first field on hyphen line per TOON spec
            writeMapInline((Map<?, ?>) element);
        } else if (element instanceof Collection) {
            // Nested collection
            if (shouldWrapArrayOrCollectionValue(element)) {
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeCollection((Collection<?>) element);
                depth--;
            } else {
                out.write(" ");
                writeCollection((Collection<?>) element);
            }
        } else if (element.getClass().isArray()) {
            // Nested array
            if (shouldWrapArrayOrCollectionValue(element)) {
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeArray(element);
                depth--;
            } else {
                out.write(" ");
                writeArray(element);
            }
        } else {
            // Complex object - first field on hyphen line
            writeObjectInline(element);
        }
    }

    /**
     * Write a map with first field on the current line (for list elements).
     * Per TOON spec: "first field on the hyphen line"
     */
    private void writeMapInline(Map<?, ?> map) throws IOException {
        if (cycleSupport) {
            if (objVisited.get(map) != 0) {
                out.write(" ");
                writeReferenceValue(getReferenceId(map));
                return;
            }
            objVisited.put(map, VISITED);
        } else if (!enterActivePath(map)) {
            throw cycleDetected(map);
        }

        try {
            if (map.isEmpty()) {
                if (cycleSupport && isReferenced(map)) {
                    out.write(" ");
                    writeIdField(map);
                } else {
                    out.write(" {}");
                }
                return;
            }

            boolean first = true;
            boolean includeId = cycleSupport && isReferenced(map);
            boolean includeType = shouldWriteTypeMetadata(map.getClass()) && !map.containsKey(typeKey);
            if (includeId) {
                out.write(" ");
                writeIdField(map);
                first = false;
            }
            if (includeType) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write(NEW_LINE);
                    depth++;
                    writeIndent();
                    depth--;
                }
                writeTypeField(map.getClass());
            }

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write(NEW_LINE);
                    depth++;
                    writeIndent();
                    depth--;
                }

                Object key = entry.getKey();
                String keyStr = (key == null) ? "null" : key.toString();
                writeFieldEntryInline(keyStr, entry.getValue());
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(map);
            }
        }
    }

    /**
     * Write an object with first field on the current line (for list elements).
     * Iterates WriteFieldPlan directly to avoid intermediate LinkedHashMap allocation.
     * Falls back to map-based approach only when @IoAnyGetter is present (rare).
     */
    private void writeObjectInline(Object obj) throws IOException {
        if (cycleSupport) {
            if (objVisited.get(obj) != 0) {
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

            // If @IoAnyGetter is present, fall back to map-based approach (rare path)
            if (annMeta.getAnyGetterMethod() != null) {
                writeObjectInlineViaMap(obj);
                return;
            }

            List<WriteOptionsBuilder.WriteFieldPlan> plans =
                    WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

            boolean includeId = cycleSupport && isReferenced(obj);
            boolean includeType = shouldWriteTypeMetadata(clazz);
            boolean first = true;

            // Write metadata inline
            if (includeId) {
                out.write(" ");
                writeIdField(obj);
                first = false;
            }
            if (includeType) {
                if (first) {
                    out.write(" ");
                    first = false;
                } else {
                    out.write(NEW_LINE);
                    depth++;
                    writeIndent();
                    depth--;
                }
                writeTypeField(clazz);
            }

            // Write fields directly from WriteFieldPlan
            for (int i = 0, len = plans.size(); i < len; i++) {
                WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
                if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
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
                    out.write(NEW_LINE);
                    depth++;
                    writeIndent();
                    depth--;
                }
                writeFieldEntryInline(key, value);
            }

            if (first) {
                out.write(" {}");
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Fallback for objects with @IoAnyGetter in inline context.
     */
    private void writeObjectInlineViaMap(Object obj) throws IOException {
        Map<String, Object> fields = getObjectFields(obj);
        boolean includeId = cycleSupport && isReferenced(obj);
        boolean includeType = shouldWriteTypeMetadata(obj.getClass());
        if (includeId || includeType) {
            LinkedHashMap<String, Object> withMeta = new LinkedHashMap<>(fields.size() + 2);
            if (includeId) {
                withMeta.put(idKey, getReferenceId(obj));
            }
            if (includeType) {
                withMeta.put(typeKey, getTypeNameForOutput(obj.getClass()));
            }
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String key = entry.getKey();
                if (idKey.equals(key) || typeKey.equals(key)) {
                    continue;
                }
                withMeta.put(key, entry.getValue());
            }
            writeMapInline(withMeta);
        } else {
            writeMapInline(fields);
        }
    }

    /**
     * Write a Map value.
     */
    private void writeMap(Map<?, ?> map) throws IOException {
        if (cycleSupport) {
            if (objVisited.get(map) != 0) {
                if (depth > 0) {
                    writeIndent();
                }
                writeReferenceValue(getReferenceId(map));
                return;
            }
            objVisited.put(map, VISITED);
        } else if (!enterActivePath(map)) {
            throw cycleDetected(map);
        }

        try {
            boolean includeId = cycleSupport && isReferenced(map);
            boolean includeType = shouldWriteTypeMetadata(map.getClass()) && !map.containsKey(typeKey);
            if (map.isEmpty()) {
                if (includeId || includeType) {
                    if (includeId) {
                        writeIndent();
                        writeIdField(map);
                        if (includeType) {
                            out.write(NEW_LINE);
                        }
                    }
                    if (includeType) {
                        writeIndent();
                        writeTypeField(map.getClass());
                    }
                } else {
                    out.write("{}");
                }
                return;
            }

            // Check if any key is a complex object that needs special handling
            boolean hasComplexKeys = false;
            for (Object key : map.keySet()) {
                if (key != null && !isSimpleKeyType(key)) {
                    hasComplexKeys = true;
                    break;
                }
            }

            if (hasComplexKeys) {
                if (includeId || includeType) {
                    writeReferencedComplexMap(map, includeId, includeType);
                } else {
                    writeMapWithComplexKeys(map);
                }
            } else {
                if (includeId || includeType) {
                    if (includeId) {
                        writeIndent();
                        writeIdField(map);
                        if (includeType) {
                            out.write(NEW_LINE);
                        }
                    }
                    if (includeType) {
                        writeIndent();
                        writeTypeField(map.getClass());
                    }
                    out.write(NEW_LINE);
                }
                writeMapWithSimpleKeys(map);
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(map);
            }
        }
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
     * Write a map with simple keys (String, Number, etc.) using the standard key: value format.
     * Per TOON spec, array/collection values combine key with size marker: fieldName[N]:
     */
    private void writeMapWithSimpleKeys(Map<?, ?> map) throws IOException {
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.write(NEW_LINE);
            }
            first = false;
            writeIndent();

            Object key = entry.getKey();
            String keyStr = (key == null) ? "null" : key.toString();
            writeFieldEntry(keyStr, entry.getValue());
        }
    }

    /**
     * Write a single key: value field entry. Handles arrays, collections, key folding,
     * nested objects, and primitives. Caller is responsible for indentation and newlines.
     */
    private void writeFieldEntry(String keyStr, Object value) throws IOException {
        // Check for key folding: collapse single-key map chains into dotted notation
        if (toonKeyFolding
                && value instanceof Map
                && isValidFoldableKey(keyStr)
                && !(cycleSupport && isReferenced(value))) {
            FoldedEntry folded = collectFoldedPath(keyStr, value);
            if (folded != null) {
                writeFoldedEntry(folded.path, folded.value);
                return;
            }
        }

        if (value != null && value.getClass().isArray()) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeKeyString(keyStr);
                out.write(":");
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeArray(value);
                depth--;
            } else {
                // Combine key with array size: fieldName[N]:
                int length = ArrayUtilities.getLength(value);
                writeKeyString(keyStr);
                out.write(countMarker(length));
                writeArrayElements(value, length);
            }
        } else if (value instanceof Collection) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeKeyString(keyStr);
                out.write(":");
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeCollection((Collection<?>) value);
                depth--;
            } else {
                // Combine key with collection size: fieldName[N]: or fieldName[N]{cols}:
                Collection<?> coll = (Collection<?>) value;
                writeKeyString(keyStr);
                out.write(countMarker(coll.size()));
                writeCollectionElementsWithHeader(coll);
            }
        } else {
            writeKeyString(keyStr);
            out.write(":");
            if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
                // Empty map - write inline as "key: {}"
                out.write(" {}");
            } else if (value != null && !isPrimitive(value)) {
                // Nested object - newline, no trailing space after colon
                out.write(NEW_LINE);
                depth++;
                if (value instanceof Map) {
                    // writeMap handles its own indentation per entry
                    writeMap((Map<?, ?>) value);
                } else {
                    // Use writeObject for cycle detection
                    writeNestedObject(value);
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
    private void writeFieldEntryInline(String keyStr, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeKeyString(keyStr);
                out.write(":");
                out.write(NEW_LINE);
                depth += 2;
                writeIndent();
                writeArray(value);
                depth -= 2;
            } else {
                int length = ArrayUtilities.getLength(value);
                writeKeyString(keyStr);
                out.write(countMarker(length));
                writeArrayElements(value, length);
            }
        } else if (value instanceof Collection) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeKeyString(keyStr);
                out.write(":");
                out.write(NEW_LINE);
                depth += 2;
                writeIndent();
                writeCollection((Collection<?>) value);
                depth -= 2;
            } else {
                Collection<?> coll = (Collection<?>) value;
                writeKeyString(keyStr);
                out.write(countMarker(coll.size()));
                depth++;
                writeCollectionElementsWithHeader(coll);
                depth--;
            }
        } else {
            writeKeyString(keyStr);
            out.write(":");
            if (value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
                out.write(" {}");
            } else if (value != null && !isPrimitive(value)) {
                out.write(NEW_LINE);
                depth += 2;
                if (value instanceof Map) {
                    writeMap((Map<?, ?>) value);
                } else {
                    writeNestedObject(value);
                }
                depth -= 2;
            } else {
                out.write(" ");
                writeValue(value);
            }
        }
    }

    /**
     * Write a folded entry (key path and value).
     */
    private void writeFoldedEntry(String path, Object value) throws IOException {
        if (value != null && value.getClass().isArray()) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeString(path);
                out.write(":");
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeArray(value);
                depth--;
            } else {
                int length = ArrayUtilities.getLength(value);
                writeString(path);
                out.write(countMarker(length));
                writeArrayElements(value, length);
            }
        } else if (value instanceof Collection) {
            if (shouldWrapArrayOrCollectionValue(value)) {
                writeString(path);
                out.write(":");
                out.write(NEW_LINE);
                depth++;
                writeIndent();
                writeCollection((Collection<?>) value);
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
            out.write(NEW_LINE);
            depth++;
            writeMap((Map<?, ?>) value);
            depth--;
        } else if (value != null && !isPrimitive(value)) {
            // Object at end of chain
            writeString(path);
            out.write(":");
            out.write(NEW_LINE);
            depth++;
            writeNestedObject(value);
            depth--;
        } else {
            // Primitive value
            writeString(path);
            out.write(": ");
            writeValue(value);
        }
    }

    /**
     * Write a map with complex object keys using array-of-entries format:
     * [N]:
     *   -
     *     key:
     *       ...object fields...
     *     value: ...
     */
    private void writeMapWithComplexKeys(Map<?, ?> map) throws IOException {
        // Write as array of entries
        out.write(countMarker(map.size()) + ":");

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.write(NEW_LINE);
            writeIndent();
            out.write("- ");
            out.write(NEW_LINE);
            depth++;

            // Write key (using $key to avoid collision with objects that have a "key" field)
            writeIndent();
            out.write("$key:");
            Object key = entry.getKey();
            if (key == null || isPrimitive(key)) {
                out.write(" ");
                writeValue(key);
            } else {
                out.write(NEW_LINE);
                depth++;
                if (key instanceof Map) {
                    writeMap((Map<?, ?>) key);
                } else if (key instanceof Collection) {
                    writeIndent();
                    writeCollection((Collection<?>) key);
                } else if (key.getClass().isArray()) {
                    writeIndent();
                    writeArray(key);
                } else {
                    writeNestedObject(key);
                }
                depth--;
            }

            // Write value (using $value to avoid collision with objects that have a "value" field)
            out.write(NEW_LINE);
            writeIndent();
            out.write("$value:");
            Object value = entry.getValue();
            if (value == null || isPrimitive(value)) {
                out.write(" ");
                writeValue(value);
            } else {
                out.write(NEW_LINE);
                depth++;
                if (value instanceof Map) {
                    writeMap((Map<?, ?>) value);
                } else if (value instanceof Collection) {
                    writeIndent();
                    writeCollection((Collection<?>) value);
                } else if (value.getClass().isArray()) {
                    writeIndent();
                    writeArray(value);
                } else {
                    writeNestedObject(value);
                }
                depth--;
            }

            depth--;
        }
    }

    private void writeReferencedComplexMap(Map<?, ?> map, boolean includeId, boolean includeType) throws IOException {
        if (includeId) {
            writeIndent();
            out.write(idKey);
            out.write(": ");
            out.write(Integer.toString(getReferenceId(map)));
            out.write(NEW_LINE);
        }
        if (includeType) {
            writeIndent();
            writeTypeField(map.getClass());
            out.write(NEW_LINE);
        }
        writeIndent();
        out.write(keysKey);
        out.write(countMarker(map.size()));
        writeCollectionElementsWithHeader(map.keySet());
        out.write(NEW_LINE);
        writeIndent();
        out.write(itemsKey);
        out.write(countMarker(map.size()));
        writeCollectionElementsWithHeader(map.values());
    }

    /**
     * Write a nested object with cycle detection.
     */
    private void writeNestedObject(Object obj) throws IOException {
        if (cycleSupport) {
            if (objVisited.get(obj) != 0) {
                writeIndent();
                writeReferenceValue(getReferenceId(obj));
                return;
            }
            objVisited.put(obj, VISITED);
        } else if (!enterActivePath(obj)) {
            throw cycleDetected(obj);
        }

        try {
            writeObjectFields(obj);
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Write a complex object as key: value pairs.
     */
    private void writeObject(Object obj) throws IOException {
        if (cycleSupport) {
            if (writeReferenceIfSeen(obj)) {
                return;
            }
        } else if (!enterActivePath(obj)) {
            throw cycleDetected(obj);
        }

        try {
            writeObjectFields(obj);
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
        }
    }

    /**
     * Write an object's fields as key: value pairs.
     * Iterates WriteFieldPlan directly to avoid intermediate LinkedHashMap allocation.
     * Falls back to getObjectFields() only when @IoAnyGetter is present (rare).
     */
    private void writeObjectFields(Object obj) throws IOException {
        if (obj == null) {
            return;
        }

        Class<?> clazz = obj.getClass();
        AnnotationResolver.ClassAnnotationMetadata annMeta = AnnotationResolver.getMetadata(clazz);

        // If @IoAnyGetter is present, fall back to map-based approach (rare path)
        if (annMeta.getAnyGetterMethod() != null) {
            writeObjectFieldsViaMap(obj);
            return;
        }

        List<WriteOptionsBuilder.WriteFieldPlan> plans =
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

        boolean includeId = cycleSupport && isReferenced(obj);
        boolean includeType = shouldWriteTypeMetadata(clazz);
        boolean wroteField = false;

        // Write metadata first
        if (includeId) {
            writeIndent();
            writeIdField(obj);
            wroteField = true;
        }
        if (includeType) {
            if (wroteField) {
                out.write(NEW_LINE);
            }
            writeIndent();
            writeTypeField(clazz);
            wroteField = true;
        }

        // Write fields directly from WriteFieldPlan (no intermediate Map)
        for (int i = 0, len = plans.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
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
                out.write(NEW_LINE);
            }
            wroteField = true;
            writeIndent();
            writeFieldEntry(key, value);
        }

        if (!wroteField) {
            out.write("{}");
        }
    }

    /**
     * Fallback for objects with @IoAnyGetter: uses getObjectFields() to collect all fields
     * including the extra ones from the annotated method.
     */
    private void writeObjectFieldsViaMap(Object obj) throws IOException {
        Map<String, Object> fields = getObjectFields(obj);
        boolean includeId = cycleSupport && isReferenced(obj);
        boolean includeType = shouldWriteTypeMetadata(obj.getClass());
        if (includeId || includeType) {
            if (includeId) {
                writeIndent();
                writeIdField(obj);
            }
            if (includeType) {
                if (includeId) {
                    out.write(NEW_LINE);
                }
                writeIndent();
                writeTypeField(obj.getClass());
            }
            if (!fields.isEmpty()) {
                out.write(NEW_LINE);
                writeMapWithSimpleKeys(fields);
            }
        } else if (fields.isEmpty()) {
            out.write("{}");
        } else {
            writeMapWithSimpleKeys(fields);
        }
    }

    /**
     * Get object fields as a map using the same WriteFieldPlan / Accessor abstraction as JsonWriter.
     * This ensures all write-side annotations are respected: @IoGetter, @IoFormat, @IoAnyGetter,
     * @IoProperty, @IoPropertyOrder, @IoNaming, @IoInclude(NON_NULL), etc.
     */
    private Map<String, Object> getObjectFields(Object obj) {
        Class<?> clazz = obj.getClass();

        // Use the same cached WriteFieldPlan list that JsonWriter uses
        List<WriteOptionsBuilder.WriteFieldPlan> plans =
                WriteOptionsBuilder.getWriteFieldPlans(writeOptions, clazz);

        Map<String, Object> result = new LinkedHashMap<>(plans.size());

        for (WriteOptionsBuilder.WriteFieldPlan plan : plans) {
            // Skip non-public enum fields if configured
            if (plan.enumPublicOnlySkipCandidate() && enumPublicFieldsOnly) {
                continue;
            }

            // Use Accessor.retrieve() — supports @IoGetter, LambdaMetafactory, VarHandle, etc.
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
            result.put(key, value);
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
                            result.put(extra.getKey(), value);
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
            case CONVERTER_SUPPORTED:
                writeString(Converter.convert(value, String.class));
                break;
            default:
                writeString(value.toString());
                break;
        }
    }

    /**
     * Check if a value is a "primitive" for TOON formatting purposes.
     * A primitive is a value that can be written inline (not as nested structure).
     * This includes basic types and any type that Converter can convert to String,
     * but excludes structural types (Map, Collection, Array).
     */
    private boolean isPrimitive(Object value) {
        if (value == null) {
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

    /**
     * Check if all elements in an array are primitives.
     */
    private boolean isAllPrimitives(Object array, int length) {
        Class<?> componentType = array.getClass().getComponentType();
        if (componentType.isPrimitive()) {
            return true;
        }

        for (int i = 0; i < length; i++) {
            Object element = getArrayElement(array, i);
            if (element != null && !isPrimitive(element)) {
                return false;
            }
        }
        return true;
    }

    private void traceReferences(Object root) {
        if (root == null || isPrimitive(root) || writeOptions.isNonReferenceableClass(root.getClass())) {
            return;
        }
        final Deque<Object> stack = new ArrayDeque<>(256);
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
                if (seen == VISITED) {
                    int id = nextIdentity++;
                    objVisited.put(current, VISITED + 1);
                    objsReferenced.put(current, id);
                }
                continue;
            }

            objVisited.put(current, VISITED);
            pushReferenceCandidates(stack, current);
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
        for (int i = 0, len = plans.size(); i < len; i++) {
            WriteOptionsBuilder.WriteFieldPlan plan = plans.get(i);
            if (plan.skipReferenceTrace()) {
                continue;
            }
            Object value = plan.accessor().retrieve(current);
            pushReferenceCandidate(stack, value);
        }
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
        if (depth < INDENT_CACHE_SIZE) {
            out.write(INDENT_CACHE[depth]);
            return;
        }
        for (int i = 0; i < depth; i++) {
            out.write(INDENT);
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
            return new FoldedEntry(path.toString(), current);
        }
        return null;
    }

    private static class UniformPojoArrayData {
        final List<String> keys;
        final List<Map<String, Object>> rows;

        UniformPojoArrayData(List<String> keys, List<Map<String, Object>> rows) {
            this.keys = keys;
            this.rows = rows;
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
