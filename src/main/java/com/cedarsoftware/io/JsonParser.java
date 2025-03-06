package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.TypeUtilities;

import static com.cedarsoftware.io.JsonObject.ENUM;
import static com.cedarsoftware.io.JsonObject.ID;
import static com.cedarsoftware.io.JsonObject.ITEMS;
import static com.cedarsoftware.io.JsonObject.KEYS;
import static com.cedarsoftware.io.JsonObject.REF;
import static com.cedarsoftware.io.JsonObject.SHORT_ID;
import static com.cedarsoftware.io.JsonObject.SHORT_ITEMS;
import static com.cedarsoftware.io.JsonObject.SHORT_KEYS;
import static com.cedarsoftware.io.JsonObject.SHORT_REF;
import static com.cedarsoftware.io.JsonObject.SHORT_TYPE;
import static com.cedarsoftware.io.JsonObject.TYPE;
import static com.cedarsoftware.util.MathUtilities.parseToMinimalNumericType;

/**
 * Parse the JSON input stream supplied by the FastPushbackReader to the constructor.
 * The entire JSON input stream will be read until it is emptied: an EOF (-1) is read.
 * <p>
 * While reading the content, Java Maps (JsonObjects) are used to hold the contents of
 * JSON objects { }.  Lists are used to hold the contents of JSON arrays.  Each object
 * that has an @id field will be copied into the supplied 'objectsMap' constructor
 * argument.  This allows the user of this class to locate any referenced object
 * directly.
 * <p>
 * When this parser completes, the @ref (references to objects identified with @id)
 * are stored as a JsonObject with a @ref as the key and the ID value of the object.
 * No substitution has yet occurred (substituting the @ref pointers with a Java
 * reference to the actual Map (Map containing the @id)).
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
class JsonParser {
    private static final JsonObject EMPTY_ARRAY = new JsonObject();  // compared with ==
    private final FastReader input;
    private final StringBuilder strBuf = new StringBuilder(256);
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0;
    private final boolean allowNanAndInfinity;
    private final int maxParseDepth;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;
    
    // Instance-level cache for parser-specific strings
    private final Map<String, String> stringCache;
    private final Map<Number, Number> numberCache;
    private final Map<String, String> substitutes;

    private static final ThreadLocal<char[]> STRING_BUFFER = ThreadLocal.withInitial(() -> new char[4096]);
    
    // Primary static cache that never changes
    private static final Map<String, String> STATIC_STRING_CACHE = new ConcurrentHashMap<>(64);
    private static final Map<Number, Number> STATIC_NUMBER_CACHE = new ConcurrentHashMap<>(16);
    private static final Map<String, String> SUBSTITUTES = new HashMap<>(5);

    // Static lookup tables for performance
    private static final char[] ESCAPE_CHAR_MAP = new char[128];
    private static final int[] HEX_VALUE_MAP = new int[128];

    static {
        // Initialize escape character map
        ESCAPE_CHAR_MAP['\\'] = '\\';
        ESCAPE_CHAR_MAP['/'] = '/';
        ESCAPE_CHAR_MAP['"'] = '"';
        ESCAPE_CHAR_MAP['\''] = '\'';
        ESCAPE_CHAR_MAP['b'] = '\b';
        ESCAPE_CHAR_MAP['f'] = '\f';
        ESCAPE_CHAR_MAP['n'] = '\n';
        ESCAPE_CHAR_MAP['r'] = '\r';
        ESCAPE_CHAR_MAP['t'] = '\t';

        // Initialize hex value map
        Arrays.fill(HEX_VALUE_MAP, -1);
        for (int i = '0'; i <= '9'; i++) {
            HEX_VALUE_MAP[i] = i - '0';
        }
        for (int i = 'a'; i <= 'f'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'a');
        }
        for (int i = 'A'; i <= 'F'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'A');
        }

        // Initialize substitutions
        SUBSTITUTES.put(SHORT_ID, ID);
        SUBSTITUTES.put(SHORT_REF, REF);
        SUBSTITUTES.put(SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(SHORT_TYPE, TYPE);
        SUBSTITUTES.put(SHORT_KEYS, KEYS);

        // Common strings
        String[] commonStrings = {
                "", "true", "True", "TRUE", "false", "False", "FALSE",
                "null", "yes", "Yes", "YES", "no", "No", "NO",
                "on", "On", "ON", "off", "Off", "OFF",
                "id", "ID", "type", "value", "name",
                ID, REF, ITEMS, TYPE, KEYS,
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
        };

        for (String s : commonStrings) {
            STATIC_STRING_CACHE.put(s, s);
        }

        // Common numbers
        STATIC_NUMBER_CACHE.put(-1L, -1L);
        STATIC_NUMBER_CACHE.put(0L, 0L);
        STATIC_NUMBER_CACHE.put(1L, 1L);
        STATIC_NUMBER_CACHE.put(-1.0d, -1.0d);
        STATIC_NUMBER_CACHE.put(0.0d, 0.0d);
        STATIC_NUMBER_CACHE.put(1.0d, 1.0d);
        STATIC_NUMBER_CACHE.put(Double.MIN_VALUE, Double.MIN_VALUE);
        STATIC_NUMBER_CACHE.put(Double.MAX_VALUE, Double.MAX_VALUE);
        STATIC_NUMBER_CACHE.put(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        STATIC_NUMBER_CACHE.put(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        STATIC_NUMBER_CACHE.put(Double.NaN, Double.NaN);
    }

    // Wrapper class for efficient two-tier string caching
    private static class ParserStringCache extends AbstractMap<String, String> {
        private final Map<String, String> staticCache;
        private final Map<String, String> instanceCache;

        public ParserStringCache(Map<String, String> staticCache) {
            this.staticCache = staticCache;
            this.instanceCache = new HashMap<>(64); // Instance-specific cache
        }

        @Override
        public String get(Object key) {
            // First check static cache (no synchronization needed)
            String result = staticCache.get(key);
            if (result != null) {
                return result;
            }

            // Then check instance-specific cache
            return instanceCache.get(key);
        }

        @Override
        public String put(String key, String value) {
            // Don't modify static cache
            return instanceCache.put(key, value);
        }

        // Implementation of other required methods...
        @Override
        public Set<Entry<String, String>> entrySet() {
            // Merge both caches for entrySet view
            Set<Entry<String, String>> entries = new HashSet<>();
            entries.addAll(staticCache.entrySet());
            entries.addAll(instanceCache.entrySet());
            return entries;
        }
    }

    // Wrapper class for efficient two-tier string caching
    private static class ParserNumberCache extends AbstractMap<Number, Number> {
        private final Map<Number, Number> staticCache;
        private final Map<Number, Number> instanceCache;

        public ParserNumberCache(Map<Number, Number> staticCache) {
            this.staticCache = staticCache;
            this.instanceCache = new HashMap<>(64); // Instance-specific cache
        }

        @Override
        public Number get(Object key) {
            // First check static cache (no synchronization needed)
            Number result = staticCache.get(key);
            if (result != null) {
                return result;
            }

            // Then check instance-specific cache
            return instanceCache.get(key);
        }

        @Override
        public Number put(Number key, Number value) {
            // Don't modify static cache
            return instanceCache.put(key, value);
        }

        // Implementation of other required methods...
        @Override
        public Set<Entry<Number, Number>> entrySet() {
            // Merge both caches for entrySet view
            Set<Entry<Number, Number>> entries = new HashSet<>();
            entries.addAll(staticCache.entrySet());
            entries.addAll(instanceCache.entrySet());
            return entries;
        }
    }
    
    JsonParser(FastReader reader, Resolver resolver) {
        // Reference the static caches
        // For substitutes, use the static map directly (read-only)
        this.substitutes = SUBSTITUTES;

        // For caches that may grow during parsing, create a wrapper
        this.stringCache = new ParserStringCache(STATIC_STRING_CACHE);
        this.numberCache = new ParserNumberCache(STATIC_NUMBER_CACHE);

        input = reader;
        this.resolver = resolver;
        readOptions = resolver.getReadOptions();
        references = resolver.getReferences();
        maxParseDepth = readOptions.getMaxDepth();
        allowNanAndInfinity = readOptions.isAllowNanAndInfinity();
    }

    /**
     * Read a JSON value (see json.org).  A value can be a JSON object, array, string, number, ("true", "false"), or "null".
     * @param suggestedType JsonValue Owning entity.
     */
    Object readValue(Type suggestedType) throws IOException {
        if (curParseDepth > maxParseDepth) {
            error("Maximum parsing depth exceeded");
        }

        int c = skipWhitespaceRead(true);
        if (c >= '0' && c <= '9' || c == '-' || c == 'N' || c == 'I') {
            return readNumber(c);
        }
        switch (c) {
            case '"':
                String str = readString();
                return str;
            case '{':
                input.pushback('{');
                JsonObject jObj = readJsonObject(suggestedType);
                return jObj;
            case '[':
                Type elementType = TypeUtilities.extractArrayComponentType(suggestedType);
                return readArray(elementType);
            case ']':   // empty array
                input.pushback(']');
                return EMPTY_ARRAY;
            case 'f':
            case 'F':
                readToken("false");
                return false;
            case 'n':
            case 'N':
                readToken("null");
                return null;
            case 't':
            case 'T':
                readToken("true");
                return true;
        }

        return error("Unknown JSON value type");
    }

    /**
     * Read a JSON object { ... }
     *
     * @return JsonObject that represents the { ... } being read in.  If the JSON object type can be inferred,
     * from an @type field, containing field type, or containing array type, then the javaType will be set on the
     * JsonObject.
     */
    private JsonObject readJsonObject(Type suggestedType) throws IOException {
        JsonObject jObj = new JsonObject();

        // Set the refined type on the JsonObject.
        Type resolvedSuggestedType = TypeUtilities.resolveType(suggestedType, suggestedType);
        jObj.setType(resolvedSuggestedType);
        
        final FastReader in = input;

        // Start reading the object: skip whitespace and consume '{'
        skipWhitespaceRead(true);  // Consume the '{'
        jObj.line = in.getLine();
        jObj.col = in.getCol();
        int c = skipWhitespaceRead(true);
        if (c == '}') {    // empty object
            // Return a new, empty JsonObject (prevents @id/@ref from interfering)
            return new JsonObject();
        }
        in.pushback((char) c);
        ++curParseDepth;

        // Obtain the injector map.
        Map<String, Injector> injectors = readOptions.getDeepInjectorMap(TypeUtilities.getRawClass(suggestedType));

        while (true) {
            String field = readFieldName();
            if (substitutes.containsKey(field)) {
                field = substitutes.get(field);
            }

            // For each field, look up the injector.
            Injector injector = injectors.get(field);
            Type fieldGenericType = injector == null ? null : injector.getGenericType();

            // If a field generic type is provided, resolve it using the parent's (i.e. jObj's) resolved type.
            if (fieldGenericType != null) {
                // Use the parent's type (which has been resolved) as context to resolve the field type.
                fieldGenericType = TypeUtilities.resolveType(suggestedType, fieldGenericType);
            }
            Object value = readValue(fieldGenericType);

            // Process key-value pairing.
            switch (field) {
                case TYPE:
                    Class<?> type = loadType(value);
                    jObj.setType(type);
                    break;

                case ENUM:  // Legacy support (@enum was used to indicate EnumSet in prior versions)
                    loadEnum(value, jObj);
                    break;

                case REF:
                    loadRef(value, jObj);
                    break;

                case ID:
                    loadId(value, jObj);
                    break;

                case ITEMS:
                    loadItems((Object[])value, jObj);
                    break;

                case KEYS:
                    loadKeys(value, jObj);
                    break;

                default:
                    jObj.put(field, value); // Store the key/value pair.
                    break;
            }

            c = skipWhitespaceRead(true);
            if (c == '}') {
                break;
            } else if (c != ',') {
                error("Object not ended with '}', instead found '" + (char) c + "'");
            }
        }

        --curParseDepth;
        return jObj;
    }

    /**
     * Read a JSON array
     */
    private Object readArray(Type suggestedType) throws IOException {
        final List<Object> list = new ArrayList<>();
        ++curParseDepth;

        while (true) {
            // Pass along the full Type to readValue so that any generic information is preserved.
            Object value = readValue(suggestedType);
            if (value != EMPTY_ARRAY) {
                list.add(value);
            }

            final int c = skipWhitespaceRead(true);

            if (c == ']') {
                break;
            } else if (c != ',') {
                error("Expected ',' or ']' inside array");
            }
        }

        --curParseDepth;
        return resolver.resolveArray(suggestedType, list);
    }

    /**
     * Read the field name of a JSON object.
     *
     * @return String field name.
     */
    private String readFieldName() throws IOException {
        int c = skipWhitespaceRead(true);
        if (c != '"') {
            error("Expected quote before field name");
        }
        String field = readString();
        c = skipWhitespaceRead(true);
        if (c != ':') {
            error("Expected ':' between field and value, instead found '" + (char) c + "'");
        }
        return field;
    }

    /**
     * Return the specified token from the reader.  If it is not found,
     * throw an IOException indicating that.  Converting to c to
     * (char) c is acceptable because the 'tokens' allowed in a
     * JSON input stream (true, false, null) are all ASCII.
     */
    private void readToken(String token) throws IOException {
        final int len = token.length();

        for (int i = 1; i < len; i++) {
            int c = input.read();
            if (c == -1) {
                error("EOF reached while reading token: " + token);
            }
            c = Character.toLowerCase((char) c);
            int loTokenChar = token.charAt(i);

            if (loTokenChar != c) {
                error("Expected token: " + token);
            }
        }
    }

    /**
     * Read a JSON number.
     *
     * @param c int a character representing the first digit of the number that
     *          was already read.
     * @return a Number (a Long or a Double) depending on whether the number is
     * a decimal number or integer.  This choice allows all smaller types (Float, int, short, byte)
     * to be represented as well.
     * @throws IOException for stream errors or parsing errors.
     */
    private Number readNumber(int c) throws IOException {
        final FastReader in = input;
        boolean isFloat = false;

        if (allowNanAndInfinity && (c == '-' || c == 'N' || c == 'I')) {
            /*
             * In this branch, we must have either one of these scenarios: (a) -NaN or NaN (b) Inf or -Inf (c) -123 but
             * NOT 123 (replace 123 by any number)
             *
             * In case of (c), we do nothing and revert input and c for normal processing.
             */

            // Handle negativity.
            final boolean isNeg = (c == '-');
            if (isNeg) {
                // Advance to next character.
                c = input.read();
            }

            // Case "-Infinity", "Infinity" or "NaN".
            if (c == 'I') {
                readToken("infinity");
                // [Out of RFC 4627] accept NaN/Infinity values
                return isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            } else if ('N' == c) {
                // [Out of RFC 4627] accept NaN/Infinity values
                readToken("nan");
                return Double.NaN;
            } else {
                // This is (c) case, meaning there was c = '-' at the beginning.
                // This is a number like "-2", but not "-Infinity". We let the normal code process.
                input.pushback((char) c);
                c = '-';
            }
        }

        // We are sure we have a positive or negative number, so we read char by char.
        StringBuilder number = numBuf;
        number.setLength(0);
        number.append((char) c);
        while (true) {
            c = in.read();
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                number.append((char) c);
            } else if (c == '.' || c == 'e' || c == 'E') {
                number.append((char) c);
                isFloat = true;
            } else if (c == -1) {
                break;
            } else {
                in.pushback((char) c);
                break;
            }
        }

        try {
            Number val;
            String numStr = number.toString();
            if (isFloat) {
                val = readFloatingPoint(numStr);
            } else {
                val = readInteger(numStr);
            }
            final Number cachedInstance = numberCache.get(val);
            if (cachedInstance != null) {
                return cachedInstance;
            } else {
                numberCache.put(val, val);  // caching all numbers (LRU has upper limit)
                return val;
            }
        }
        catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    private Number readInteger(String numStr) {
        if (readOptions.isIntegerTypeBigInteger()) {
            return new BigInteger(numStr);
        }

        try {
            return Long.parseLong(numStr);
        } catch (Exception e) {
            BigInteger bigInt = new BigInteger(numStr);
            if (readOptions.isIntegerTypeBoth()) {
                return bigInt;
            } else {
                // Super-big integers (more than 19 digits) will "wrap around" as expected, similar to casting a long
                // to an int, where the originating long is larger than Integer.MAX_VALUE.
                return bigInt.longValue();
            }
        }
    }

    private Number readFloatingPoint(String numStr) {
        if (readOptions.isFloatingPointBigDecimal()) {
            return new BigDecimal(numStr);
        }

        Number number = parseToMinimalNumericType(numStr);
        if (readOptions.isFloatingPointBoth()) {
            return number;
        } else {
            return number.doubleValue();
        }
    }

    /**
     * Read a JSON string
     * This method assumes the initial quote has already been read.
     *
     * @return String read from JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private String readString() throws IOException {
        final FastReader in = input;
        final char[] ESCAPE_CHARS = ESCAPE_CHAR_MAP;
        final int[] HEX_VALUES = HEX_VALUE_MAP;

        // Get thread-local buffer to avoid allocation
        char[] buffer = STRING_BUFFER.get();
        int pos = 0;

        // Fast path for simple strings (no escapes)
        while (true) {
            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }

            // String termination
            if (c == '"') {
                // Check root level validation
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) {
                        throw new JsonIoException("EOF expected, content found after string");
                    }
                }

                // For simple strings, create string directly from buffer
                return cacheString(new String(buffer, 0, pos));
            }

            // Escape sequence - switch to StringBuilder for complex case
            if (c == '\\') {
                // Initialize StringBuilder only when needed
                StringBuilder sb = strBuf;
                sb.setLength(0);

                // Copy current buffer contents to StringBuilder
                sb.append(buffer, 0, pos);

                // Handle the escape sequence
                c = in.read();
                if (c == -1) {
                    error("EOF reached while reading escape sequence");
                }

                // Process first escape
                processEscape(sb, c, in, ESCAPE_CHARS, HEX_VALUES);

                // Continue with StringBuilder-based processing for the rest of the string
                return readStringWithEscapes(sb, in, ESCAPE_CHARS, HEX_VALUES);
            }

            // Regular character
            if (pos >= buffer.length) {
                // Buffer full, switch to StringBuilder
                StringBuilder sb = strBuf;
                sb.setLength(0);
                sb.append(buffer, 0, pos);
                sb.append((char) c);
                return readStringWithEscapes(sb, in, ESCAPE_CHARS, HEX_VALUES);
            }

            // Add to buffer
            buffer[pos++] = (char) c;
        }
    }

    /**
     * Continue reading a string with StringBuilder after encountering an escape sequence.
     */
    private String readStringWithEscapes(StringBuilder sb, FastReader in,
                                         char[] ESCAPE_CHARS, int[] HEX_VALUES) throws IOException {
        while (true) {
            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }

            // Fast path for regular characters
            if (c != '\\' && c != '"') {
                sb.append((char) c);
                continue;
            }

            // String termination
            if (c == '"') {
                // Check root level validation
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) {
                        throw new JsonIoException("EOF expected, content found after string");
                    }
                }
                break;
            }

            // Must be an escape sequence
            c = in.read();
            if (c == -1) {
                error("EOF reached while reading escape sequence");
            }

            processEscape(sb, c, in, ESCAPE_CHARS, HEX_VALUES);
        }

        return cacheString(sb.toString());
    }

    /**
     * Process a single escape sequence and append to StringBuilder
     */
    private void processEscape(StringBuilder sb, int c, FastReader in,
                               char[] ESCAPE_CHARS, int[] HEX_VALUES) throws IOException {
        // Handle common escapes via lookup table
        if (c < ESCAPE_CHARS.length) {
            char escaped = ESCAPE_CHARS[c];
            if (escaped != '\0') {
                sb.append(escaped);
                return;
            }
        }

        // Unicode escape sequence
        if (c == 'u') {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                c = in.read();
                if (c == -1) {
                    error("EOF reached while reading Unicode escape sequence");
                }

                int digit = (c < 128) ? HEX_VALUES[c] : -1;
                if (digit < 0) {
                    error("Expected hexadecimal digit, got: " + (char)c);
                }
                value = (value << 4) | digit;
            }

            // Handle surrogate pairs
            if (value >= 0xD800 && value <= 0xDBFF) {
                // Look for a low surrogate
                int next = in.read();
                if (next == '\\') {
                    next = in.read();
                    if (next == 'u') {
                        // Parse the potential low surrogate
                        int lowSurrogate = 0;
                        for (int i = 0; i < 4; i++) {
                            c = in.read();
                            if (c == -1) {
                                error("EOF reached while reading Unicode escape sequence");
                            }

                            int digit = (c < 128) ? HEX_VALUES[c] : -1;
                            if (digit < 0) {
                                error("Expected hexadecimal digit, got: " + (char)c);
                            }
                            lowSurrogate = (lowSurrogate << 4) | digit;
                        }

                        // Check if valid surrogate pair
                        if (lowSurrogate >= 0xDC00 && lowSurrogate <= 0xDFFF) {
                            // Valid pair - append as code point
                            int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                            sb.appendCodePoint(codePoint);
                            return;
                        } else {
                            // Not a valid pair - append separately
                            sb.append((char)value);
                            sb.append((char)lowSurrogate);
                            return;
                        }
                    } else {
                        // Not a \\u sequence - push back and append high surrogate
                        in.pushback((char)next);
                        in.pushback('\\');
                    }
                } else {
                    // Not a backslash - push back and append high surrogate
                    if (next != -1) {
                        in.pushback((char)next);
                    }
                }
            }

            // Regular Unicode character or high surrogate without low surrogate
            sb.append((char)value);
        } else {
            error("Invalid character escape sequence specified: " + (char)c);
        }
    }

    /**
     * Optimized string caching that handles both buffer-direct and StringBuilder cases
     */
    private String cacheString(String str) {
        final int length = str.length();

        // Fast path for empty strings
        if (length == 0) {
            return "";
        }

        // Fast path for very small strings - use interning
        if (length <= 2) {
            return str.intern();
        }

        // For small to medium strings, use the cache
        if (length < 33) {
            final String cachedInstance = stringCache.get(str);
            if (cachedInstance != null) {
                return cachedInstance;
            }
            stringCache.put(str, str);
        }

        return str;
    }

    /**
     * Read until non-whitespace character and then return it.
     * This saves extra read/pushback.
     *
     * @return int representing the next non-whitespace character in the stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private int skipWhitespaceRead(boolean throwOnEof) throws IOException {
        final Reader in = input;
        int c;
        do {
            c = in.read();
        } while (c == ' ' || c == '\n' || c == '\r' || c == '\t');

        if (c == -1 && throwOnEof) {
            error("EOF reached prematurely");
        }
        return c;
    }

    /**
     * Load the @id field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown.  It is the value associated to the @id field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadId(Object value, JsonObject jObj) {
        if (!(value instanceof Number)) {
            error("Expected a number for " + ID + ", instead got: " + value);
        }
        Long id = ((Number) value).longValue();
        references.put(id, jObj);
        jObj.setId(id);
    }

    /**
     * Load the @ref field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown. It is the value associated to the @ref field.
     * @param jObj  JsonValue that will be stuffed with the reference id and marked as finished.
     */
    private void loadRef(Object value, JsonValue jObj) {
        if (!(value instanceof Number)) {
            error("Expected a number for " + REF + ", instead got: " + value);
        }
        jObj.setReferenceId(((Number) value).longValue());
    }

    /**
     * Load the @enum (EnumSet) field listed in the JSON
     *
     * @param value Object should be a String, if not exception is thrown. It is the class of the Enum.
     */
    private void loadEnum(Object value, JsonObject jObj) {
        if (!(value instanceof String)) {
            error("Expected a String for " + ENUM + ", instead got: " + value);
        }
        Class<?> enumClass = stringToClass((String) value);
        jObj.setType(enumClass);

        // Only set empty items if no items were specified in JSON
        if (jObj.getItems() == null) {
            jObj.setItems(ArrayUtilities.EMPTY_OBJECT_ARRAY);   // Indicate EnumSet (has both @type and @items)
        }
    }

    /**
     * Load the @type field listed in the JSON
     *
     * @param value Object should be a String, if not an exception is thrown.  It is the value associated to the @type field.
     */
    private Class<?> loadType(Object value) {
        if (!(value instanceof String)) {
            error("Expected a String for " + TYPE + ", instead got: " + value);
        }
        String javaType = (String) value;
        final String substitute = readOptions.getTypeNameAlias(javaType);
        if (substitute != null) {
            javaType = substitute;
        }

        // Resolve class during parsing
        return stringToClass(javaType);
    }

    /**
     * Load the @items field listed in the JSON
     *
     * @param value Object should be an array, if not exception is thrown.  It is the value associated to the @items field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadItems(Object[] value, JsonObject jObj) {
        if (value == null) {
            return;
        }
        if (!value.getClass().isArray()) {
            error("Expected @items to have an array [], but found: " + value.getClass().getName());
        }
        jObj.setItems(value);
    }

    /**
     * Load the @keys field listed in the JSON
     *
     * @param value Object should be an array, if not exception is thrown.  It is the value associated to the @keys field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadKeys(Object value, JsonObject jObj) {
        if (value == null) {
            return;
        }
        if (!value.getClass().isArray()) {
            error("Expected @keys to have an array [], but found: " + value.getClass().getName());
        }
        jObj.setKeys((Object[])value);
    }

    private Class<?> stringToClass(String className) {
        Class<?> clazz = ClassUtilities.forName(className, readOptions.getClassLoader());
        if (clazz == null) {
            if (readOptions.isFailOnUnknownType()) {
                error("Unknown type (class) '" + className + "' not defined.");
            }
            clazz = readOptions.getUnknownTypeClass();
            if (clazz == null) {
                clazz = LinkedHashMap.class;
            }
        }
        return clazz;
    }

    private Object error(String msg) {
        throw new JsonIoException(getMessage(msg));
    }

    private Object error(String msg, Exception e) {
        throw new JsonIoException(getMessage(msg), e);
    }

    private String getMessage(String msg) {
        return msg + "\nline: " + input.getLine() + ", col: " + input.getCol() + "\n" + input.getLastSnippet();
    }
}