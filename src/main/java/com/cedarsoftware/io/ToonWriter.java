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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

    private final WriteOptions writeOptions;
    private final Writer out;
    private final char delimiter;  // Default comma, configurable to pipe or tab
    private int depth = 0;

    // Track visited objects to detect cycles (when cycleSupport=false, skip duplicates)
    private final IdentityIntMap objVisited = new IdentityIntMap(256);

    /**
     * Create a ToonWriter that writes to an OutputStream.
     *
     * @param out OutputStream to write TOON content to
     * @param writeOptions configuration options (may be null for defaults)
     */
    public ToonWriter(OutputStream out, WriteOptions writeOptions) {
        this.writeOptions = writeOptions == null ? WriteOptionsBuilder.getDefaultWriteOptions() : writeOptions;
        this.out = new FastWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        this.delimiter = ',';  // TODO: get from writeOptions.getToonDelimiter() when added
    }

    /**
     * Write the given object as TOON format.
     *
     * @param obj the object to serialize
     * @throws JsonIoException if an error occurs during serialization
     */
    public void write(Object obj) {
        try {
            writeValue(obj);
            out.flush();
        } catch (IOException e) {
            throw new JsonIoException("Error writing TOON output", e);
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
        // Fallback - complex object as key: value pairs
        else {
            writeObject(value);
        }
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
            out.write('"');
            writeEscapedString(str);
            out.write('"');
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

        // Check for special characters
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c == ':' || c == '"' || c == '\\' || c == '[' || c == ']' ||
                c == '{' || c == '}' || c == '\n' || c == '\r' || c == '\t' ||
                c == delimiter) {
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
     */
    private void writeNumber(Number num) throws IOException {
        if (num instanceof Double) {
            double d = num.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.write("null");  // TOON spec: NaN/Infinity -> null
            } else if (d == -0.0) {
                out.write("0");  // TOON spec: -0 -> 0
            } else {
                out.write(num.toString());
            }
        } else if (num instanceof Float) {
            float f = num.floatValue();
            if (Float.isNaN(f) || Float.isInfinite(f)) {
                out.write("null");
            } else if (f == -0.0f) {
                out.write("0");
            } else {
                out.write(num.toString());
            }
        } else if (num instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) num;
            out.write(bd.toPlainString());
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
     * Write an array value.
     */
    private void writeArray(Object array) throws IOException {
        int length = ArrayUtilities.getLength(array);

        if (length == 0) {
            out.write("[0]:");
            return;
        }

        // Check if all elements are primitives (can use inline format)
        if (isAllPrimitives(array, length)) {
            out.write("[" + length + "]: ");
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    out.write(delimiter);
                }
                writeInlineValue(ArrayUtilities.getElement(array, i));
            }
        } else {
            // Mixed/complex array - use list format with hyphens
            out.write("[" + length + "]:");
            depth++;
            for (int i = 0; i < length; i++) {
                out.write(NEW_LINE);
                writeIndent();
                out.write("- ");
                Object element = ArrayUtilities.getElement(array, i);
                if (element == null || isPrimitive(element)) {
                    writeValue(element);
                } else if (element.getClass().isArray()) {
                    // Nested array - recurse
                    writeArray(element);
                } else {
                    // Complex object
                    out.write(NEW_LINE);
                    depth++;
                    writeNestedObject(element);
                    depth--;
                }
            }
            depth--;
        }
    }

    /**
     * Write a Collection value.
     */
    private void writeCollection(Collection<?> collection) throws IOException {
        int size = collection.size();

        if (size == 0) {
            out.write("[0]:");
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
            out.write("[" + size + "]: ");
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    out.write(delimiter);
                }
                first = false;
                writeInlineValue(element);
            }
        } else {
            // Mixed/complex - use list format
            out.write("[" + size + "]:");
            depth++;
            for (Object element : collection) {
                out.write(NEW_LINE);
                writeIndent();
                out.write("- ");
                if (element == null || isPrimitive(element)) {
                    writeValue(element);
                } else if (element instanceof Collection) {
                    // Nested collection - recurse
                    writeCollection((Collection<?>) element);
                } else if (element.getClass().isArray()) {
                    // Nested array - recurse
                    writeArray(element);
                } else {
                    // Complex object
                    out.write(NEW_LINE);
                    depth++;
                    writeNestedObject(element);
                    depth--;
                }
            }
            depth--;
        }
    }

    /**
     * Write a Map value.
     */
    private void writeMap(Map<?, ?> map) throws IOException {
        if (map.isEmpty()) {
            out.write("{}");
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
            writeMapWithComplexKeys(map);
        } else {
            writeMapWithSimpleKeys(map);
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
            if (key == null) {
                out.write("null");
            } else {
                writeString(key.toString());
            }
            out.write(": ");

            // Write value
            Object value = entry.getValue();
            if (value != null && !isPrimitive(value)) {
                out.write(NEW_LINE);
                depth++;
                if (value instanceof Map) {
                    // writeMap handles its own indentation per entry
                    writeMap((Map<?, ?>) value);
                } else if (value instanceof Collection) {
                    writeIndent();
                    writeCollection((Collection<?>) value);
                } else if (value.getClass().isArray()) {
                    writeIndent();
                    writeArray(value);
                } else {
                    // Use writeObject for cycle detection
                    writeNestedObject(value);
                }
                depth--;
            } else {
                writeValue(value);
            }
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
        out.write("[" + map.size() + "]:");

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
                writeNestedObject(key);
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

    /**
     * Write a nested object with cycle detection.
     */
    private void writeNestedObject(Object obj) throws IOException {
        // Check for cycle
        int visited = objVisited.get(obj);
        if (visited != 0) {
            // Already visited - emit null to prevent infinite recursion
            writeIndent();
            out.write("null");
            return;
        }
        objVisited.put(obj, 1);
        writeObjectFields(obj);
    }

    /**
     * Write a complex object as key: value pairs.
     */
    private void writeObject(Object obj) throws IOException {
        // Check for cycle
        int visited = objVisited.get(obj);
        if (visited != 0) {
            // Already visited - skip to prevent infinite recursion
            out.write("null");  // Placeholder for cyclic reference
            return;
        }
        objVisited.put(obj, 1);

        writeObjectFields(obj);
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
        writeMap(fields);
    }

    /**
     * Get object fields as a map using WriteOptions' cached reflection.
     * Uses getDeepDeclaredFields() which caches field discovery via ReflectionUtils.
     */
    private Map<String, Object> getObjectFields(Object obj) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Use WriteOptions' cached field retrieval (respects excluded/included fields, filters, etc.)
        Map<String, java.lang.reflect.Field> fieldMap = writeOptions.getDeepDeclaredFields(obj.getClass());

        for (Map.Entry<String, java.lang.reflect.Field> entry : fieldMap.entrySet()) {
            java.lang.reflect.Field field = entry.getValue();
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value != null || !writeOptions.isSkipNullFields()) {
                    result.put(entry.getKey(), value);
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }

        return result;
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
            Object element = ArrayUtilities.getElement(array, i);
            if (element != null && !isPrimitive(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Write indentation for current depth.
     */
    private void writeIndent() throws IOException {
        for (int i = 0; i < depth; i++) {
            out.write(INDENT);
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