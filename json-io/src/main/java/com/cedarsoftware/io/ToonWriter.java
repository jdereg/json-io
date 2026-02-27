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
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final WriteOptions writeOptions;
    private final Writer out;
    private final char delimiter;  // Default comma, configurable to pipe or tab
    private final boolean cycleSupport;
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

    private static String[] buildIndentCache() {
        String[] cache = new String[INDENT_CACHE_SIZE];
        StringBuilder builder = new StringBuilder(INDENT_CACHE_SIZE * INDENT.length());
        for (int i = 0; i < INDENT_CACHE_SIZE; i++) {
            cache[i] = builder.toString();
            builder.append(INDENT);
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
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.delimiter = this.writeOptions.getToonDelimiter();
        this.cycleSupport = this.writeOptions.isCycleSupport();

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
     */
    private void writeValue(Object value) throws IOException {
        if (value == null) {
            out.write("null");
            return;
        }

        Class<?> clazz = value.getClass();

        // Structural types - handle with nested output
        if (clazz.isArray()) {
            writeArray(value);
        } else if (value instanceof Collection) {
            writeCollection((Collection<?>) value);
        } else if (value instanceof Map) {
            writeMap((Map<?, ?>) value);
        }
        // String - write directly (most common case after structures)
        else if (value instanceof String) {
            writeString((String) value);
        }
        // Number - special handling for NaN/Infinity per TOON spec
        else if (value instanceof Number) {
            writeNumber((Number) value);
        }
        // Boolean - write unquoted true/false (Converter returns string which would get quoted)
        else if (value instanceof Boolean) {
            out.write(((Boolean) value) ? "true" : "false");
        }
        // Character - convert to string
        else if (value instanceof Character) {
            writeString(String.valueOf(value));
        }
        // All other types - use Converter if supported (AtomicBoolean, Enum, BitSet, Date, Time, UUID, etc.)
        else if (Converter.isConversionSupportedFor(clazz, String.class)) {
            writeString(Converter.convert(value, String.class));
        }
        // Check @IoValue - serialize via single method return value
        else {
            Method valueMethod = AnnotationResolver.getMetadata(clazz).getValueMethod();
            if (valueMethod != null) {
                try {
                    Object val = valueMethod.invoke(value);
                    writeValue(val);
                } catch (Exception e) {
                    throw new JsonIoException("@IoValue method invocation failed for " + clazz.getName(), e);
                }
            } else {
                // Fallback - complex object as key: value pairs
                writeObject(value);
            }
        }
    }

    private boolean isReferenceable(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> clazz = value.getClass();
        return !isPrimitive(value) && !writeOptions.isNonReferenceableClass(clazz);
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
        if (clazz.isArray() || Map.class.isAssignableFrom(clazz) || Collection.class.isAssignableFrom(clazz)) {
            return false;
        }
        return clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || Converter.isConversionSupportedFor(clazz, String.class);
    }

    private String getTypeNameForOutput(Class<?> clazz) {
        return writeOptions.getTypeNameAlias(clazz.getName());
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
        if (!writeOptions.isToonKeyFolding() && key.contains(".")) {
            // Key folding is OFF, so quote keys with dots to preserve them as literals
            writeQuotedString(key);
        } else {
            writeString(key);
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

        // Check for leading/trailing whitespace
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        if (Character.isWhitespace(first) || Character.isWhitespace(last)) {
            return true;
        }

        // Check for reserved words
        if ("true".equals(str) || "false".equals(str) || "null".equals(str)) {
            return true;
        }

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
     */
    private void writeEscapedString(String str) throws IOException {
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\':
                    out.write("\\\\");
                    break;
                case '"':
                    out.write("\\\"");
                    break;
                case '\n':
                    out.write("\\n");
                    break;
                case '\r':
                    out.write("\\r");
                    break;
                case '\t':
                    out.write("\\t");
                    break;
                default:
                    out.write(c);
            }
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
            out.write(String.valueOf(num.longValue()));
        } else {
            // Integer, Long, Short, Byte
            out.write(num.toString());
        }
    }

    /**
     * Format a decimal number (double or float) according to TOON spec:
     * - No exponent notation
     * - No trailing zeros in fractional part
     * - Whole numbers have no decimal point
     */
    private String formatDecimalNumber(double d) {
        // Use BigDecimal for precise conversion without exponent notation
        BigDecimal bd = BigDecimal.valueOf(d);
        return formatBigDecimal(bd);
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
            out.write(countMarker(length) + ":");
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
            return;
        }

        // Check if all elements are primitives (can use inline format)
        if (isAllPrimitives(array, length)) {
            out.write(" ");
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.write(delimiter);
                }
                writeInlineValue(getArrayElement(array, i));
            }
        } else if (!writeOptions.isPrettyPrint() && !typeMetadataEnabled) {
            // Try tabular format for uniform POJO arrays
            UniformPojoArrayData uniformPOJO = getUniformPOJODataFromArray(array, length);
            if (uniformPOJO != null) {
                out.write("{");
                boolean firstKey = true;
                for (String key : uniformPOJO.keys) {
                    if (!firstKey) {
                        out.write(delimiter);
                    }
                    firstKey = false;
                    out.write(key);
                }
                out.write("}:");
                depth++;
                for (Map<String, Object> fields : uniformPOJO.rows) {
                    out.write(NEW_LINE);
                    writeIndent();
                    boolean firstVal = true;
                    for (String key : uniformPOJO.keys) {
                        if (!firstVal) {
                            out.write(delimiter);
                        }
                        firstVal = false;
                        writeInlineValue(fields.get(key));
                    }
                }
                depth--;
            } else {
                // Mixed/complex array - use list format with hyphens
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
            } else if (!writeOptions.isPrettyPrint() && !typeMetadataEnabled) {
                // Check for uniform POJO array → tabular format (default, compact)
                List<String> uniformPOJOKeys = getUniformPOJOKeys(collection);
                if (uniformPOJOKeys != null) {
                    writeTabularHeader(uniformPOJOKeys);
                    writeTabularPOJORows(collection, uniformPOJOKeys);
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
     * Returns the ordered list of field names if uniform, null otherwise.
     * <p>
     * POJOs qualify for tabular format when:
     * <ul>
     *   <li>All elements are the same concrete class</li>
     *   <li>All field values are primitives (no nested objects/arrays)</li>
     *   <li>All elements have the same fields in the same order</li>
     * </ul>
     */
    private List<String> getUniformPOJOKeys(Collection<?> collection) {
        List<String> keys = null;

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

            List<String> elementKeys = new ArrayList<>(fields.keySet());
            if (keys == null) {
                keys = elementKeys;
            } else if (!keys.equals(elementKeys)) {
                return null;  // Different fields break uniformity
            }
        }

        return keys;
    }

    /**
     * Check if an array contains uniform POJOs eligible for tabular format, and
     * capture field maps so they can be reused when writing rows.
     */
    private UniformPojoArrayData getUniformPOJODataFromArray(Object array, int length) {
        List<String> keys = null;
        List<Map<String, Object>> rows = new ArrayList<>(length);

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

        return keys == null ? null : new UniformPojoArrayData(keys, rows);
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
     * Write tabular rows for a collection of uniform POJOs (CSV-like format).
     * Calls getObjectFields() per element to retrieve field values in key order.
     */
    private void writeTabularPOJORows(Collection<?> collection, List<String> keys) throws IOException {
        depth++;
        for (Object element : collection) {
            out.write(NEW_LINE);
            writeIndent();

            Map<String, Object> fields = getObjectFields(element);
            boolean first = true;
            for (String key : keys) {
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
                    // First field goes on same line as hyphen
                    out.write(" ");
                    first = false;
                } else {
                    // Subsequent fields on new lines at depth+1
                    out.write(NEW_LINE);
                    depth++;
                    writeIndent();
                    depth--;
                }

                // Write key
                Object key = entry.getKey();
                String keyStr = (key == null) ? "null" : key.toString();

                // Write value - handle arrays/collections specially
                Object value = entry.getValue();
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
                        out.write(countMarker(length) + ":");
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
                        // Extra depth for collection elements inside inline map
                        depth++;
                        writeCollectionElementsWithHeader(coll);
                        depth--;
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
                        depth += 2;
                        if (value instanceof Map) {
                            writeMap((Map<?, ?>) value);
                        } else {
                            writeNestedObject(value);
                        }
                        depth -= 2;
                    } else {
                        // Primitive value on same line - space before value
                        out.write(" ");
                        writeValue(value);
                    }
                }
            }
        } finally {
            if (!cycleSupport) {
                exitActivePath(map);
            }
        }
    }

    /**
     * Write an object with first field on the current line (for list elements).
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
        } finally {
            if (!cycleSupport) {
                exitActivePath(obj);
            }
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

            // Write key
            Object key = entry.getKey();
            String keyStr = (key == null) ? "null" : key.toString();

            // Write value - handle arrays/collections specially to combine key with size marker
            Object value = entry.getValue();

            // Check for key folding: collapse single-key map chains into dotted notation
            if (writeOptions.isToonKeyFolding()
                    && value instanceof Map
                    && isValidFoldableKey(keyStr)
                    && !(cycleSupport && isReferenced(value))) {
                FoldedEntry folded = collectFoldedPath(keyStr, value);
                if (folded != null) {
                    writeFoldedEntry(folded.path, folded.value);
                    continue;
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
                    out.write(countMarker(length) + ":");
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
                out.write(countMarker(length) + ":");
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
     */
    private void writeObjectFields(Object obj) throws IOException {
        if (obj == null) {
            return;
        }

        // Use reflection to get fields
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
                if (!idKey.equals(key) && !typeKey.equals(key)) {
                    withMeta.put(key, entry.getValue());
                }
            }
            fields = withMeta;
        }
        writeMap(fields);
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
            if (plan.enumPublicOnlySkipCandidate() && writeOptions.isEnumPublicFieldsOnly()) {
                continue;
            }

            // Use Accessor.retrieve() — supports @IoGetter, LambdaMetafactory, VarHandle, etc.
            Object value = plan.accessor().retrieve(obj);

            // Respect skipNullFields (global) and skipIfNull (per-field @IoInclude NON_NULL)
            if ((writeOptions.isSkipNullFields() || plan.skipIfNull()) && value == null) {
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
                        if (value != null || !writeOptions.isSkipNullFields()) {
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
        } else if (value instanceof String) {
            writeString((String) value);
        } else if (value instanceof Number) {
            writeNumber((Number) value);
        } else if (value instanceof Boolean) {
            // Write unquoted true/false
            out.write(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Character) {
            writeString(String.valueOf(value));
        } else if (Converter.isConversionSupportedFor(value.getClass(), String.class)) {
            // AtomicBoolean, Enum, BitSet, Date, Time, UUID, etc.
            writeString(Converter.convert(value, String.class));
        } else {
            // Fallback for unsupported types
            writeString(value.toString());
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
        // Structural types are not primitives even if Converter supports them
        if (value instanceof Map || value instanceof Collection || value.getClass().isArray()) {
            return false;
        }
        return value instanceof String ||
               value instanceof Number ||
               value instanceof Boolean ||
               value instanceof Character ||
               Converter.isConversionSupportedFor(value.getClass(), String.class);
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
        if (!isReferenceable(root)) {
            return;
        }
        List<Object> stack = new ArrayList<>();
        stack.add(root);

        while (!stack.isEmpty()) {
            Object current = stack.remove(stack.size() - 1);
            if (!isReferenceable(current)) {
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

    private void pushReferenceCandidates(List<Object> stack, Object current) {
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

        Map<String, Object> fields = getObjectFields(current);
        for (Object fieldValue : fields.values()) {
            pushReferenceCandidate(stack, fieldValue);
        }
    }

    private void pushReferenceCandidate(List<Object> stack, Object candidate) {
        if (isReferenceable(candidate)) {
            stack.add(candidate);
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
