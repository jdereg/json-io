package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.reflect.Injector;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.StringUtilities;
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
import static com.cedarsoftware.io.JsonValue.JSON5_ID;
import static com.cedarsoftware.io.JsonValue.JSON5_ITEMS;
import static com.cedarsoftware.io.JsonValue.JSON5_KEYS;
import static com.cedarsoftware.io.JsonValue.JSON5_REF;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_ID;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_ITEMS;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_KEYS;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_REF;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_TYPE;
import static com.cedarsoftware.io.JsonValue.JSON5_TYPE;
import static com.cedarsoftware.util.MathUtilities.parseToMinimalNumericType;

/**
 * Parse the JSON input stream supplied by the FastPushbackReader to the constructor.
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
    private final StringBuilder strBuf;
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0;
    private final boolean allowNanAndInfinity;
    private final int maxParseDepth;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;

    // Instance-level LRU cache for string deduplication
    private final Map<String, String> stringCache;
    // Performance: Hoisted ReadOptions constants to avoid repeated method calls
    private final long maxIdValue;
    private final boolean strictJson;
    private final boolean integerTypeBigInteger;
    private final boolean integerTypeBoth;
    private final boolean floatingPointBigDecimal;
    private final boolean floatingPointBoth;
    private final Map<CharSequence, CharSequence> substitutes;
    
    // LRU cache size limit for string deduplication
    private static final int STRING_CACHE_SIZE = 1024;
    private static final Map<CharSequence, CharSequence> SUBSTITUTES = new HashMap<>(16);

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
        java.util.Arrays.fill(HEX_VALUE_MAP, -1);
        for (int i = '0'; i <= '9'; i++) {
            HEX_VALUE_MAP[i] = i - '0';
        }
        for (int i = 'a'; i <= 'f'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'a');
        }
        for (int i = 'A'; i <= 'F'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'A');
        }

        // Initialize substitutions for short meta keys (@t, @i, @r, @e, @k)
        SUBSTITUTES.put(SHORT_ID, ID);
        SUBSTITUTES.put(SHORT_REF, REF);
        SUBSTITUTES.put(SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(SHORT_TYPE, TYPE);
        SUBSTITUTES.put(SHORT_KEYS, KEYS);

        // Initialize substitutions for JSON5 meta keys ($type, $id, $ref, $items, $keys)
        SUBSTITUTES.put(JSON5_ID, ID);
        SUBSTITUTES.put(JSON5_REF, REF);
        SUBSTITUTES.put(JSON5_ITEMS, ITEMS);
        SUBSTITUTES.put(JSON5_TYPE, TYPE);
        SUBSTITUTES.put(JSON5_KEYS, KEYS);

        // Initialize substitutions for JSON5 short meta keys ($t, $i, $r, $e, $k)
        SUBSTITUTES.put(JSON5_SHORT_ID, ID);
        SUBSTITUTES.put(JSON5_SHORT_REF, REF);
        SUBSTITUTES.put(JSON5_SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(JSON5_SHORT_TYPE, TYPE);
        SUBSTITUTES.put(JSON5_SHORT_KEYS, KEYS);
    }

    JsonParser(FastReader reader, Resolver resolver) {
        // For substitutes, use the static map directly (read-only)
        this.substitutes = SUBSTITUTES;

        // Create instance-level LRU cache for string deduplication
        // LinkedHashMap with accessOrder=true provides LRU eviction
        this.stringCache = new LinkedHashMap<String, String>(STRING_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > STRING_CACHE_SIZE;
            }
        };

        input = reader;
        this.resolver = resolver;
        readOptions = resolver.getReadOptions();
        references = resolver.getReferences();
        maxParseDepth = readOptions.getMaxDepth();
        allowNanAndInfinity = readOptions.isAllowNanAndInfinity();

        // Initialize string buffer management using ReadOptions configuration
        this.strBuf = new StringBuilder(readOptions.getStringBufferSize());

        // Performance: Hoist ReadOptions constants to avoid repeated method calls
        this.maxIdValue = readOptions.getMaxIdValue();
        this.strictJson = readOptions.isStrictJson();
        this.integerTypeBigInteger = readOptions.isIntegerTypeBigInteger();
        this.integerTypeBoth = readOptions.isIntegerTypeBoth();
        this.floatingPointBigDecimal = readOptions.isFloatingPointBigDecimal();
        this.floatingPointBoth = readOptions.isFloatingPointBoth();
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

        // Fast path for objects and arrays (most common cases)
        if (c == '{') {
            JsonObject jObj = readJsonObject(suggestedType);
            return jObj;
        }
        if (c == '[') {
            Type elementType = TypeUtilities.extractArrayComponentType(suggestedType);
            return readArray(elementType);
        }

        // Handle less common value types
        switch (c) {
            case '"':
                return readString('"');
            case '\'':
                // JSON5 single-quoted strings
                if (strictJson) {
                    error("Single-quoted strings not allowed in strict JSON mode");
                }
                return readString('\'');
            case ']':   // empty array
                input.pushback(']');
                return EMPTY_ARRAY;
            case 'f':
            case 'F':
                readToken("false");
                return false;
            case 'n':
                readToken("null");
                return null;
            case 'N':
                // Could be null or NaN - let readNumber handle it
                return readNumber(c);
            case 't':
            case 'T':
                readToken("true");
                return true;
            case '-':
            case 'I':
                return readNumber(c);
            case '.':
                // JSON5 leading decimal point (e.g., .5 equals 0.5)
                if (strictJson) {
                    error("Leading decimal point not allowed in strict JSON mode");
                }
                return readNumber(c);
            case '+':
                // JSON5 explicit positive sign (e.g., +5)
                if (strictJson) {
                    error("Explicit positive sign not allowed in strict JSON mode");
                }
                return readNumber(c);
            default:
                if (c >= '0' && c <= '9') {
                    return readNumber(c);
                }
                return error("Unknown JSON value type");
        }
    }

    /**
     * Read a JSON object { ... }
     *
     * @return JsonObject that represents the { ... } being read in.  If the JSON object type can be inferred,
     * from a @type field, containing field type, or containing array type, then the javaType will be set on the
     * JsonObject.
     */
    private JsonObject readJsonObject(Type suggestedType) throws IOException {
        JsonObject jObj = new JsonObject();

        // Set the refined type on the JsonObject.
        Type resolvedSuggestedType = TypeUtilities.resolveType(suggestedType, suggestedType);
        jObj.setType(resolvedSuggestedType);

        final FastReader in = input;

        // The '{' has already been consumed by readValue()
        int c = skipWhitespaceRead(true);
        if (c == '}') {    // empty object
            // Return a new, empty JsonObject (prevents @id/@ref from interfering)
            return new JsonObject();
        }
        in.pushback((char) c);
        ++curParseDepth;

        // Performance: Skip injector resolution when there's no meaningful type context
        Class<?> rawClass = TypeUtilities.getRawClass(suggestedType);
        Map<String, Injector> injectors;
        if (suggestedType == null || rawClass == Object.class || rawClass == null) {
            // No type context - skip expensive injector work
            injectors = java.util.Collections.emptyMap();
        } else {
            injectors = readOptions.getDeepInjectorMap(rawClass);
        }

        while (true) {
            CharSequence field = readFieldName();
            // Performance: Use getOrDefault to avoid double lookup
            field = substitutes.getOrDefault(field, field);

            // For each field, look up the injector.
            Injector injector = injectors.get(field);
            Type fieldGenericType = injector == null ? null : injector.getGenericType();

            // If a field generic type is provided, resolve it using the parent's (i.e. jObj's) resolved type.
            if (fieldGenericType != null) {
                // Use the parent's type (which has been resolved) as context to resolve the field type.
                fieldGenericType = TypeUtilities.resolveType(suggestedType, fieldGenericType);
            }
            Object value = readValue(fieldGenericType);

            // Fast path for regular fields (95%+ of fields don't start with '@')
            // Note: length check MUST come first for short-circuit evaluation (empty field names are valid JSON)
            if (field.length() == 0 || field.charAt(0) != '@') {
                jObj.put(field, value);
            } else {
                // Process special meta fields (@type, @id, @ref, etc.)
                // Use StringUtilities.equals() for CharSequence comparison with String constants
                if (StringUtilities.equals(field, TYPE)) {
                    Class<?> type = loadType(value);
                    jObj.setTypeString((String) value);
                    jObj.setType(type);
                } else if (StringUtilities.equals(field, ENUM)) {
                    // Legacy support (@enum was used to indicate EnumSet in prior versions)
                    loadEnum(value, jObj);
                } else if (StringUtilities.equals(field, REF)) {
                    loadRef(value, jObj);
                } else if (StringUtilities.equals(field, ID)) {
                    loadId(value, jObj);
                } else if (StringUtilities.equals(field, ITEMS)) {
                    if (value != null && !value.getClass().isArray()) {
                        error("Expected @items to have an array [], but found: " + value.getClass().getName());
                    }
                    loadItems((Object[])value, jObj);
                } else if (StringUtilities.equals(field, KEYS)) {
                    loadKeys(value, jObj);
                } else {
                    jObj.put(field, value); // Store unrecognized @-prefixed fields
                }
            }

            c = skipWhitespaceRead(true);
            if (c == '}') {
                break;
            } else if (c != ',') {
                error("Object not ended with '}', instead found '" + (char) c + "'");
            }
            // Check for trailing comma (JSON5 feature)
            c = skipWhitespaceRead(true);
            if (c == '}') {
                // Trailing comma before closing brace
                if (strictJson) {
                    error("Trailing commas not allowed in strict JSON mode");
                }
                break;
            }
            input.pushback((char) c);
        }

        --curParseDepth;
        return jObj;
    }

    /**
     * Read a JSON array
     */
    private Object readArray(Type suggestedType) throws IOException {
        // Performance: Pre-size ArrayList to reduce resizing. Size of 64 eliminates
        // 1-2 resize operations for typical JSON arrays while adding only ~200 bytes overhead.
        final List<Object> list = new ArrayList<>(64);
        ++curParseDepth;

        while (true) {
            // Pass along the full Type to readValue so that any generic information is preserved.
            Object value = readValue(suggestedType);
            if (value != EMPTY_ARRAY) {
                list.add(value);
            }

            int c = skipWhitespaceRead(true);

            if (c == ']') {
                break;
            } else if (c != ',') {
                error("Expected ',' or ']' inside array");
            }
            // Check for trailing comma (JSON5 feature)
            c = skipWhitespaceRead(true);
            if (c == ']') {
                // Trailing comma before closing bracket
                if (strictJson) {
                    error("Trailing commas not allowed in strict JSON mode");
                }
                break;
            }
            input.pushback((char) c);
        }

        --curParseDepth;
        return resolver.resolveArray(suggestedType, list);
    }

    /**
     * Read the field name of a JSON object.
     * Supports both quoted strings (standard JSON) and unquoted identifiers (JSON5).
     *
     * @return CharSequence field name.
     */
    private CharSequence readFieldName() throws IOException {
        int c = skipWhitespaceRead(true);
        CharSequence field;

        if (c == '"') {
            // Standard double-quoted field name
            field = readString('"');
        } else if (c == '\'') {
            // JSON5 single-quoted field name
            if (strictJson) {
                error("Single-quoted strings not allowed in strict JSON mode");
            }
            field = readString('\'');
        } else if (isIdentifierStart(c)) {
            // JSON5 unquoted field name
            if (strictJson) {
                error("Unquoted field names not allowed in strict JSON mode");
            }
            field = readUnquotedIdentifier(c);
        } else {
            error("Expected quote before field name");
            return null; // Unreachable, but satisfies compiler
        }

        c = skipWhitespaceRead(true);
        if (c != ':') {
            error("Expected ':' between field and value, instead found '" + (char) c + "'");
        }
        return field;
    }

    /**
     * Check if character is a valid ECMAScript identifier start character.
     * Per JSON5 spec, identifiers follow ECMAScript 5.1 IdentifierName production.
     */
    private boolean isIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    /**
     * Check if character is a valid ECMAScript identifier part character.
     */
    private boolean isIdentifierPart(int c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    /**
     * Read an unquoted identifier (JSON5 feature).
     * The first character has already been read and validated as identifier start.
     *
     * @param firstChar the first character of the identifier (already read)
     * @return the complete identifier string
     */
    private String readUnquotedIdentifier(int firstChar) throws IOException {
        strBuf.setLength(0);
        strBuf.append((char) firstChar);

        while (true) {
            int c = input.read();
            if (c == -1 || !isIdentifierPart(c)) {
                // Put back the non-identifier character
                if (c != -1) {
                    input.pushback((char) c);
                }
                break;
            }
            strBuf.append((char) c);
        }

        return strBuf.toString();
    }

    /**
     * Return the specified token from the reader.  If it is not found,
     * throw an IOException indicating that.  Converting to c to
     * (char) c is acceptable because the 'tokens' allowed in a
     * JSON input stream (true, false, null) are all ASCII.
     */
    private void readToken(CharSequence token) {
        final int len = token.length();

        // Optimized path for common short tokens
        if (len <= 5) {
            // Fast validation for common tokens: true, false, null
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) {
                    error("EOF reached while reading token: " + token);
                }

                // Fast ASCII lowercase conversion (faster than Character.toLowerCase)
                if (c >= 'A' && c <= 'Z') {
                    c += 32; // Convert uppercase to lowercase
                }

                if (token.charAt(i) != c) {
                    error("Expected token: " + token);
                }
            }
        } else {
            // Fallback for longer tokens (infinity, etc.)
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
        // Handle NaN and Infinity (non-standard JSON extension)
        if (allowNanAndInfinity && (c == '-' || c == 'N' || c == 'I')) {
            final boolean isNeg = (c == '-');
            if (isNeg) {
                c = input.read();
            }

            if (c == 'I') {
                readToken("infinity");
                return isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            } else if (c == 'N') {
                readToken("nan");
                return Double.NaN;
            } else {
                // Number like "-2", not "-Infinity" - continue to normal processing
                input.pushback((char) c);
                c = '-';
            }
        }

        // All numbers go through the general path with direct StringBuilder parsing
        return readNumberGeneral(c);
    }

    /**
     * Parse a JSON number using direct StringBuilder parsing.
     * Integers are parsed directly from StringBuilder without String allocation.
     * This optimization comes from the original heap-based parser.
     * Supports JSON5 hexadecimal numbers (0xFF) in permissive mode.
     */
    private Number readNumberGeneral(int firstChar) throws IOException {
        final FastReader in = input;
        boolean isFloat = false;
        boolean isNegative = (firstChar == '-');
        boolean isPositive = (firstChar == '+');  // JSON5 explicit positive sign

        // Check for hex number (JSON5 feature): 0x or 0X
        int startChar = firstChar;
        if (isNegative || isPositive) {
            startChar = in.read();
        }

        if (startChar == '0') {
            int next = in.read();
            if (next == 'x' || next == 'X') {
                // JSON5 hexadecimal number
                if (strictJson) {
                    error("Hexadecimal numbers not allowed in strict JSON mode");
                }
                return readHexNumber(isNegative);
            } else if (next != -1) {
                in.pushback((char) next);
            }
        }

        // If we read ahead for hex check, we need to reconstruct
        if ((isNegative || isPositive) && startChar != firstChar) {
            in.pushback((char) startChar);
        }

        // We are sure we have a positive or negative number, so we read char by char.
        StringBuilder number = numBuf;
        number.setLength(0);
        // For '+', skip the sign character (don't include in number string)
        if (isPositive) {
            // Read the first digit after the '+' sign
            int c = in.read();
            if (c == -1) {
                return (Number) error("Unexpected end of input after '+'");
            }
            number.append((char) c);
            // Check for leading decimal (e.g., +.5)
            if (c == '.') {
                isFloat = true;
            }
        } else {
            number.append((char) firstChar);
        }

        // JSON5: Leading decimal point (e.g., .5) - mark as float immediately
        if (firstChar == '.') {
            isFloat = true;
        }

        while (true) {
            int c = in.read();
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
            if (isFloat) {
                return readFloatingPoint(number.toString());
            } else {
                return readInteger(number);
            }
        }
        catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    /**
     * Parse integer directly from StringBuilder without String allocation.
     * This optimization comes from the original heap-based parser and avoids
     * creating a String object for most integer values.
     */
    private Number readInteger(CharSequence number) {
        // BigInteger mode - must use String
        if (integerTypeBigInteger) {
            return new BigInteger(number.toString());
        }

        int len = number.length();

        // Direct parsing for integers that fit in a long (up to 18 digits, or 19 if positive)
        // Long.MAX_VALUE = 9223372036854775807 (19 digits)
        // Long.MIN_VALUE = -9223372036854775808 (19 digits + sign)
        boolean isNeg = number.charAt(0) == '-';
        int digitCount = isNeg ? len - 1 : len;

        if (digitCount <= 18) {
            // Safe to parse directly - won't overflow
            long n = 0;
            int start = isNeg ? 1 : 0;
            for (int i = start; i < len; i++) {
                n = n * 10 + (number.charAt(i) - '0');
            }
            return isNeg ? -n : n;
        }

        // For 19+ digit numbers, use String parsing with overflow handling
        String numStr = number.toString();
        try {
            return Long.parseLong(numStr);
        } catch (Exception e) {
            BigInteger bigInt = new BigInteger(numStr);
            if (integerTypeBoth) {
                return bigInt;
            } else {
                // Super-big integers (more than 19 digits) will "wrap around" as expected, similar to casting a long
                // to an int, where the originating long is larger than Integer.MAX_VALUE.
                return bigInt.longValue();
            }
        }
    }

    private Number readFloatingPoint(String numStr) {
        if (floatingPointBigDecimal) {
            return new BigDecimal(numStr);
        }

        Number number = parseToMinimalNumericType(numStr);
        if (floatingPointBoth) {
            return number;
        } else {
            return number.doubleValue();
        }
    }

    /**
     * Read a JSON5 hexadecimal number.
     * Called after "0x" or "0X" has been consumed.
     * Supports optional negative sign before the 0x prefix.
     *
     * @param isNegative true if the number was preceded by a minus sign
     * @return the parsed number as a Long
     * @throws IOException for stream errors or parsing errors
     */
    private Number readHexNumber(boolean isNegative) throws IOException {
        final FastReader in = input;
        long value = 0;
        int digitCount = 0;

        while (true) {
            int c = in.read();
            int digit;

            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digit = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                digit = c - 'A' + 10;
            } else {
                // End of hex digits
                if (c != -1) {
                    in.pushback((char) c);
                }
                break;
            }

            digitCount++;
            if (digitCount > 16) {
                error("Hexadecimal number too large");
            }
            value = (value << 4) | digit;
        }

        if (digitCount == 0) {
            error("Expected hexadecimal digit after 0x");
        }

        return isNegative ? -value : value;
    }

    /**
     * Read a JSON string
     * This method assumes the initial quote has already been read.
     * Supports both double-quoted (standard JSON) and single-quoted (JSON5) strings.
     *
     * @param quoteChar the quote character that started the string ('"' or '\'')
     * @return CharSequence read from JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private CharSequence readString(char quoteChar) throws IOException {
        // Reuse StringBuilder for better performance
        final StringBuilder str = strBuf;
        str.setLength(0);
        final FastReader in = input;

        // Lookup tables for character handling
        final char[] ESCAPE_CHARS = ESCAPE_CHAR_MAP;
        final int[] HEX_VALUES = HEX_VALUE_MAP;

        // Main loop with optimized path for non-escape characters
        while (true) {
            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }

            // Fast path for regular characters (most common)
            if (c != '\\' && c != quoteChar) {
                str.append((char) c);
                continue;
            }

            // Handle string termination
            if (c == quoteChar) {
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

            // Handle escape using lookup table for common escapes
            if (c < ESCAPE_CHARS.length) {
                char escaped = ESCAPE_CHARS[c];
                if (escaped != '\0') {
                    str.append(escaped);
                    continue;
                }
            }

            // Special handling for Unicode escape
            if (c == 'u') {
                // Optimized hex parsing using lookup table
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

                // Fast path for BMP characters (most common case)
                // Surrogates are in range 0xD800-0xDFFF, so anything outside is a simple BMP char
                if (value < 0xD800 || value > 0xDFFF) {
                    str.append((char) value);
                    continue;
                }

                // Handle surrogate pairs (high surrogate: 0xD800-0xDBFF)
                if (value <= 0xDBFF) {
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
                                str.appendCodePoint(codePoint);
                                continue;
                            } else {
                                // Not a valid pair - append separately
                                str.append((char)value);
                                str.append((char)lowSurrogate);
                                continue;
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

                // Orphan surrogate (high without valid low, or lone low surrogate)
                str.append((char)value);
            } else if (c == '\n') {
                // JSON5 multi-line string: backslash followed by newline
                // The backslash and newline are removed, string continues on next line
                if (strictJson) {
                    error("Multi-line strings not allowed in strict JSON mode");
                }
                // Just skip the newline, string continues
            } else if (c == '\r') {
                // JSON5 multi-line string: backslash followed by carriage return
                if (strictJson) {
                    error("Multi-line strings not allowed in strict JSON mode");
                }
                // Check for \r\n (Windows line ending)
                int next = in.read();
                if (next != '\n' && next != -1) {
                    in.pushback((char) next);
                }
                // Skip the line terminator(s), string continues
            } else {
                error("Invalid character escape sequence specified: " + (char)c);
            }
        }

        // Use optimized string caching for the result
        return cacheString(str);
    }

    /**
     * Convert CharSequence to String, using LRU cache for string deduplication.
     * Frequently used strings (field names, common values) are deduplicated.
     * Cache is bounded to STRING_CACHE_SIZE entries with LRU eviction.
     */
    private CharSequence cacheString(CharSequence str) {
        // Convert to String first
        final String s = str.toString();

        // Check LRU cache - also updates access order for LRU tracking
        final String cached = stringCache.get(s);
        if (cached != null) {
            return cached;  // Cache hit - return deduplicated instance
        }

        // Cache miss - store in LRU cache (may evict oldest entry)
        stringCache.put(s, s);
        return s;
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
        // Performance: Direct character comparison is faster than array bounds check + lookup.
        // JSON whitespace is defined as: space (0x20), tab (0x09), newline (0x0A), carriage return (0x0D)
        while (true) {
            c = in.read();

            // Skip standard whitespace
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }

            // Check for comments (JSON5 feature)
            if (c == '/') {
                int next = in.read();
                if (next == '/') {
                    // Single-line comment: skip until end of line
                    if (strictJson) {
                        error("Comments not allowed in strict JSON mode");
                    }
                    skipSingleLineComment();
                    continue;
                } else if (next == '*') {
                    // Block comment: skip until */
                    if (strictJson) {
                        error("Comments not allowed in strict JSON mode");
                    }
                    skipBlockComment();
                    continue;
                } else {
                    // Not a comment, push back and return '/'
                    if (next != -1) {
                        input.pushback((char) next);
                    }
                    return c;
                }
            }

            break;
        }

        if (c == -1 && throwOnEof) {
            error("EOF reached prematurely");
        }
        return c;
    }

    /**
     * Skip a single-line comment (// until end of line).
     * The leading // has already been consumed.
     */
    private void skipSingleLineComment() throws IOException {
        int c;
        while ((c = input.read()) != -1) {
            if (c == '\n' || c == '\r') {
                // End of line reached, comment is done
                // Handle \r\n as a single line ending
                if (c == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        input.pushback((char) next);
                    }
                }
                return;
            }
        }
        // EOF reached - that's OK for single-line comment at end of file
    }

    /**
     * Skip a block comment (slash-star until star-slash).
     * The leading slash-star has already been consumed.
     */
    private void skipBlockComment() throws IOException {
        int c;
        while ((c = input.read()) != -1) {
            if (c == '*') {
                int next = input.read();
                if (next == '/') {
                    // End of block comment
                    return;
                } else if (next != -1) {
                    // Not end of comment, push back and continue
                    input.pushback((char) next);
                }
            }
        }
        // EOF reached without closing comment
        error("Unterminated block comment");
    }

    /**
     * Load the @id field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown.  It is the value associated to the @id field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadId(Object value, JsonObject jObj) {
        // Fix null validation - add comprehensive null and type checks
        if (value == null) {
            error("Null value provided for " + ID + " field - expected a number");
        }
        if (jObj == null) {
            error("Null JsonObject provided to loadId method");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + ID + ", instead got: " + value.getClass().getSimpleName());
        }

        long id = ((Number) value).longValue();

        // Performance: Use hoisted maxIdValue constant
        if (id < -maxIdValue || id > maxIdValue) {
            error("ID value out of safe range: " + id + " - IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        }

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
        // Fix null validation - add comprehensive null and type checks
        if (value == null) {
            error("Null value provided for " + REF + " field - expected a number");
        }
        if (jObj == null) {
            error("Null JsonValue provided to loadRef method");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + REF + ", instead got: " + value.getClass().getSimpleName());
        }

        long refId = ((Number) value).longValue();

        // Performance: Use hoisted maxIdValue constant
        if (refId < -maxIdValue || refId > maxIdValue) {
            error("Reference ID value out of safe range: " + refId + " - reference IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        }

        jObj.setReferenceId(refId);
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
        jObj.setTypeString((String) value);
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
        // Performance: Remove duplicate array check - signature already ensures Object[]
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
        String resolvedName = readOptions.getTypeNameAlias(className);
        Class<?> clazz = ClassUtilities.forName(resolvedName, readOptions.getClassLoader());
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
        return msg + "\n" + input.getLastSnippet();
    }
}