package com.cedarsoftware.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.cedarsoftware.util.FastReader;

import static com.cedarsoftware.util.MathUtilities.parseBigDecimal;
import static com.cedarsoftware.util.MathUtilities.parseBigInteger;
import static com.cedarsoftware.util.MathUtilities.parseDouble;
import static com.cedarsoftware.util.MathUtilities.parseToMinimalNumericType;

/**
 * Char-based concrete {@link JsonTokenizer} backed by a {@link FastReader}.
 * Implements the cursor API by copying the tokenization helpers currently
 * embedded in {@link JsonParser}; commit 3 of the Phase A extraction will
 * retire those copies and route JsonParser through this class.
 *
 * <p>The cursor state machine is a small explicit stack of {@code OBJECT} /
 * {@code ARRAY} contexts. {@link #nextToken()} consumes whatever lookahead
 * (whitespace, structural punctuation, comments) is needed and emits exactly
 * one token per call. EOF is signaled by returning {@code null}.
 *
 * <p>This tokenizer deliberately omits the depth-0 "no trailing content"
 * assertion that today's {@link JsonParser#readString(char)} performs at the
 * end of a top-level string value: that check is a tree-builder concern, not
 * a tokenization concern, and will be applied by the caller in commit 3.
 *
 * <p>Package-private until 4.104.0+.
 */
final class CharStreamTokenizer extends JsonTokenizer {

    // String-cache sizing (mirrors JsonParser).
    private static final int STRING_CACHE_MASK = 2047;
    private static final int MAX_CACHED_STRING_LENGTH = 64;
    private static final int NO_PREFETCH = -2;

    // Static lookup tables (mirror JsonParser).
    private static final char[] ESCAPE_CHAR_MAP = new char[128];
    private static final int[] HEX_VALUE_MAP = new int[128];

    static {
        ESCAPE_CHAR_MAP['\\'] = '\\';
        ESCAPE_CHAR_MAP['/'] = '/';
        ESCAPE_CHAR_MAP['"'] = '"';
        ESCAPE_CHAR_MAP['\''] = '\'';
        ESCAPE_CHAR_MAP['b'] = '\b';
        ESCAPE_CHAR_MAP['f'] = '\f';
        ESCAPE_CHAR_MAP['n'] = '\n';
        ESCAPE_CHAR_MAP['r'] = '\r';
        ESCAPE_CHAR_MAP['t'] = '\t';

        Arrays.fill(HEX_VALUE_MAP, -1);
        for (int i = '0'; i <= '9'; i++) HEX_VALUE_MAP[i] = i - '0';
        for (int i = 'a'; i <= 'f'; i++) HEX_VALUE_MAP[i] = 10 + (i - 'a');
        for (int i = 'A'; i <= 'F'; i++) HEX_VALUE_MAP[i] = 10 + (i - 'A');
    }

    // Input + scratch buffers.
    private final FastReader input;
    private final char[] readBuf = new char[256];
    private final FastReader.BufferSlice readSlice = new FastReader.BufferSlice();
    private final StringBuilder strBuf;
    private final StringBuilder numBuf = new StringBuilder();
    private final String[] stringCacheArray = new String[STRING_CACHE_MASK + 1];

    // Tokenization-policy flags.
    private final boolean strictJson;
    private final boolean allowNanAndInfinity;
    private final boolean integerTypeBigInteger;
    private final boolean integerTypeBoth;
    private final boolean floatingPointBigDecimal;
    private final boolean floatingPointBoth;
    private final Object sourceRef;

    // Cursor / state-machine fields.
    private static final byte CTX_OBJECT = 1;
    private static final byte CTX_ARRAY = 2;
    private byte[] contextStack = new byte[16];
    private int contextDepth;

    private JsonToken currentToken;
    private String currentName;
    private String currentText;
    private NumberType numberType;
    private long longValue;
    private double doubleValue;
    private BigInteger bigIntegerValue;
    private BigDecimal bigDecimalValue;
    private boolean booleanValue;
    private boolean done;

    /**
     * Build a tokenizer over the given char input, with all tokenization-policy
     * flags supplied explicitly. Tests construct directly via this constructor
     * so they don't need a {@link ReadOptions} instance.
     */
    CharStreamTokenizer(FastReader input,
                        boolean strictJson,
                        boolean allowNanAndInfinity,
                        boolean integerTypeBigInteger,
                        boolean integerTypeBoth,
                        boolean floatingPointBigDecimal,
                        boolean floatingPointBoth,
                        int stringBufferSize,
                        Object sourceRef) {
        this.input = input;
        this.strictJson = strictJson;
        this.allowNanAndInfinity = allowNanAndInfinity;
        this.integerTypeBigInteger = integerTypeBigInteger;
        this.integerTypeBoth = integerTypeBoth;
        this.floatingPointBigDecimal = floatingPointBigDecimal;
        this.floatingPointBoth = floatingPointBoth;
        this.strBuf = new StringBuilder(stringBufferSize);
        this.sourceRef = sourceRef;
    }

    // -------------------------------------------------------------------
    // Cursor API
    // -------------------------------------------------------------------

    @Override
    public JsonToken nextToken() throws IOException {
        if (done) {
            return null;
        }

        // Hot path: just emitted a FIELD_NAME — read its value next.
        if (currentToken == JsonToken.FIELD_NAME) {
            int c = skipWhitespaceRead(true);
            return advanceToValue(c);
        }

        if (contextDepth == 0) {
            // First call, OR called after a root value to detect trailing
            // content. Skip whitespace+comments; null on clean EOF, otherwise
            // emit the next token (Jackson-aligned behavior — caller decides
            // whether trailing content is allowed).
            int c = skipWhitespaceRead(false);
            if (c == -1) {
                done = true;
                return null;
            }
            return advanceToValue(c);
        }

        // Inside an open container.
        byte ctx = contextStack[contextDepth - 1];
        if (ctx == CTX_OBJECT) {
            return advanceWithinObject();
        }
        return advanceWithinArray();
    }

    private JsonToken advanceWithinObject() throws IOException {
        int c;
        if (currentToken == JsonToken.START_OBJECT) {
            c = skipWhitespaceRead(true);
            if (c == '}') {
                popContext();
                return setToken(JsonToken.END_OBJECT, "}");
            }
            return readFieldNameAt(c);
        }
        c = skipWhitespaceRead(true);
        if (c == '}') {
            popContext();
            return setToken(JsonToken.END_OBJECT, "}");
        }
        if (c != ',') {
            error("Object not ended with '}', instead found '" + (char) c + "'");
        }
        c = skipWhitespaceRead(true);
        if (c == '}') {
            if (strictJson) {
                error("Trailing commas not allowed in strict JSON mode");
            }
            popContext();
            return setToken(JsonToken.END_OBJECT, "}");
        }
        return readFieldNameAt(c);
    }

    private JsonToken advanceWithinArray() throws IOException {
        int c;
        if (currentToken == JsonToken.START_ARRAY) {
            c = skipWhitespaceRead(true);
            if (c == ']') {
                popContext();
                return setToken(JsonToken.END_ARRAY, "]");
            }
            return advanceToValue(c);
        }
        c = skipWhitespaceRead(true);
        if (c == ']') {
            popContext();
            return setToken(JsonToken.END_ARRAY, "]");
        }
        if (c != ',') {
            error("Expected ',' or ']' inside array");
        }
        c = skipWhitespaceRead(true);
        if (c == ']') {
            if (strictJson) {
                error("Trailing commas not allowed in strict JSON mode");
            }
            popContext();
            return setToken(JsonToken.END_ARRAY, "]");
        }
        return advanceToValue(c);
    }

    private JsonToken advanceToValue(int c) throws IOException {
        // Mirrors JsonParser.readValue(int, Type) in dispatch but emits tokens.
        if (c == '{') {
            pushContext(CTX_OBJECT);
            return setToken(JsonToken.START_OBJECT, "{");
        }
        if (c == '[') {
            pushContext(CTX_ARRAY);
            return setToken(JsonToken.START_ARRAY, "[");
        }
        switch (c) {
            case '"':
                return emitString(readString('"'));
            case '\'':
                if (strictJson) {
                    error("Single-quoted strings not allowed in strict JSON mode");
                }
                return emitString(readString('\''));
            case 'f':
            case 'F':
                readToken("false");
                booleanValue = false;
                return setToken(JsonToken.VALUE_FALSE, "false");
            case 'n':
                readToken("null");
                return setToken(JsonToken.VALUE_NULL, "null");
            case 'N':
                return emitNumber(readNumber(c));
            case 't':
            case 'T':
                readToken("true");
                booleanValue = true;
                return setToken(JsonToken.VALUE_TRUE, "true");
            case '-':
            case 'I':
                return emitNumber(readNumber(c));
            case '.':
                if (strictJson) {
                    error("Leading decimal point not allowed in strict JSON mode");
                }
                return emitNumber(readNumber(c));
            case '+':
                if (strictJson) {
                    error("Explicit positive sign not allowed in strict JSON mode");
                }
                return emitNumber(readNumber(c));
            default:
                if (c >= '0' && c <= '9') {
                    return emitNumber(readNumber(c));
                }
                if (c == -1) {
                    error("EOF reached prematurely");
                }
                error("Unknown JSON value type");
                return null;
        }
    }

    private JsonToken readFieldNameAt(int c) throws IOException {
        CharSequence name;
        if (c == '"') {
            name = readString('"');
        } else if (c == '\'') {
            if (strictJson) {
                error("Single-quoted strings not allowed in strict JSON mode");
            }
            name = readString('\'');
        } else if (isIdentifierStart(c)) {
            if (strictJson) {
                error("Unquoted field names not allowed in strict JSON mode");
            }
            name = readUnquotedIdentifier(c);
        } else {
            error("Expected quote before field name");
            return null;
        }
        int colon = skipWhitespaceRead(true);
        if (colon != ':') {
            error("Expected ':' between field and value, instead found '" + (char) colon + "'");
        }
        currentName = name.toString();
        currentText = currentName;
        currentToken = JsonToken.FIELD_NAME;
        return JsonToken.FIELD_NAME;
    }

    private JsonToken setToken(JsonToken token, String text) {
        currentToken = token;
        currentText = text;
        return token;
    }

    private JsonToken emitString(CharSequence value) {
        currentText = value.toString();
        currentToken = JsonToken.VALUE_STRING;
        return JsonToken.VALUE_STRING;
    }

    private JsonToken emitNumber(Number n) {
        // Categorize the parsed Number into a NumberType + typed-value cache.
        if (n instanceof Long) {
            long l = (Long) n;
            longValue = l;
            doubleValue = (double) l;
            bigIntegerValue = null;
            bigDecimalValue = null;
            numberType = (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
                    ? NumberType.INT : NumberType.LONG;
            currentText = Long.toString(l);
            currentToken = JsonToken.VALUE_NUMBER_INT;
        } else if (n instanceof Double) {
            double d = (Double) n;
            doubleValue = d;
            longValue = (long) d;
            bigIntegerValue = null;
            bigDecimalValue = null;
            numberType = NumberType.DOUBLE;
            currentText = Double.toString(d);
            currentToken = JsonToken.VALUE_NUMBER_FLOAT;
        } else if (n instanceof BigInteger) {
            BigInteger bi = (BigInteger) n;
            bigIntegerValue = bi;
            longValue = bi.longValue();
            doubleValue = bi.doubleValue();
            bigDecimalValue = null;
            numberType = NumberType.BIG_INTEGER;
            currentText = bi.toString();
            currentToken = JsonToken.VALUE_NUMBER_INT;
        } else if (n instanceof BigDecimal) {
            BigDecimal bd = (BigDecimal) n;
            bigDecimalValue = bd;
            doubleValue = bd.doubleValue();
            longValue = bd.longValue();
            bigIntegerValue = null;
            numberType = NumberType.BIG_DECIMAL;
            currentText = bd.toString();
            currentToken = JsonToken.VALUE_NUMBER_FLOAT;
        } else if (n instanceof Float) {
            float f = (Float) n;
            doubleValue = f;
            longValue = (long) f;
            bigIntegerValue = null;
            bigDecimalValue = null;
            numberType = NumberType.FLOAT;
            currentText = Float.toString(f);
            currentToken = JsonToken.VALUE_NUMBER_FLOAT;
        } else {
            // Integer / Short / Byte etc. — coerce to long.
            long l = n.longValue();
            longValue = l;
            doubleValue = (double) l;
            bigIntegerValue = null;
            bigDecimalValue = null;
            numberType = NumberType.INT;
            currentText = Long.toString(l);
            currentToken = JsonToken.VALUE_NUMBER_INT;
        }
        return currentToken;
    }

    @Override
    public JsonToken currentToken() {
        return currentToken;
    }

    @Override
    public String currentName() {
        return currentName;
    }

    @Override
    public String getText() {
        return currentText;
    }

    @Override
    public int getTextLength() {
        return currentText == null ? 0 : currentText.length();
    }

    @Override
    public boolean getTextSlice(FastReader.BufferSlice slice) {
        // No long-lived slice in v1; signal "fall back to getText()".
        return false;
    }

    @Override
    public int getIntValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_INTEGER) {
            return bigIntegerValue.intValueExact();
        }
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue.intValueExact();
        }
        if (numberType == NumberType.DOUBLE || numberType == NumberType.FLOAT) {
            return (int) doubleValue;
        }
        return (int) longValue;
    }

    @Override
    public long getLongValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_INTEGER) {
            return bigIntegerValue.longValueExact();
        }
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue.longValueExact();
        }
        if (numberType == NumberType.DOUBLE || numberType == NumberType.FLOAT) {
            return (long) doubleValue;
        }
        return longValue;
    }

    @Override
    public float getFloatValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue.floatValue();
        }
        if (numberType == NumberType.BIG_INTEGER) {
            return bigIntegerValue.floatValue();
        }
        return (float) doubleValue;
    }

    @Override
    public double getDoubleValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue.doubleValue();
        }
        if (numberType == NumberType.BIG_INTEGER) {
            return bigIntegerValue.doubleValue();
        }
        return doubleValue;
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_INTEGER) {
            return bigIntegerValue;
        }
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue.toBigInteger();
        }
        if (numberType == NumberType.DOUBLE || numberType == NumberType.FLOAT) {
            return BigDecimal.valueOf(doubleValue).toBigInteger();
        }
        return BigInteger.valueOf(longValue);
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException {
        ensureNumberToken();
        if (numberType == NumberType.BIG_DECIMAL) {
            return bigDecimalValue;
        }
        if (numberType == NumberType.BIG_INTEGER) {
            return new BigDecimal(bigIntegerValue);
        }
        if (numberType == NumberType.DOUBLE || numberType == NumberType.FLOAT) {
            return new BigDecimal(currentText);
        }
        return BigDecimal.valueOf(longValue);
    }

    @Override
    public boolean getBooleanValue() {
        return booleanValue;
    }

    @Override
    public NumberType getNumberType() {
        return numberType;
    }

    @Override
    public void skipChildren() throws IOException {
        if (currentToken != JsonToken.START_OBJECT && currentToken != JsonToken.START_ARRAY) {
            return;
        }
        int targetDepth = contextDepth - 1;
        while (contextDepth > targetDepth) {
            JsonToken t = nextToken();
            if (t == null) {
                error("EOF reached while skipping children");
            }
        }
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return new JsonLocation(-1L, input.getLine(), input.getCol(), sourceRef);
    }

    @Override
    boolean hasNonWhitespaceContent() throws IOException {
        int c = skipWhitespaceRead(false);
        if (c == -1) {
            return false;
        }
        input.pushback((char) c);
        return true;
    }

    @Override
    public int getDepth() {
        return contextDepth;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private void ensureNumberToken() {
        if (currentToken != JsonToken.VALUE_NUMBER_INT
                && currentToken != JsonToken.VALUE_NUMBER_FLOAT) {
            error("Numeric value requested, but current token is " + currentToken);
        }
    }

    // -------------------------------------------------------------------
    // Context stack
    // -------------------------------------------------------------------

    private void pushContext(byte ctx) {
        if (contextDepth == contextStack.length) {
            contextStack = Arrays.copyOf(contextStack, contextStack.length * 2);
        }
        contextStack[contextDepth++] = ctx;
    }

    private void popContext() {
        contextDepth--;
    }

    // -------------------------------------------------------------------
    // Tokenization helpers — copied verbatim from JsonParser, with the
    // depth-0 trailing-content checks in readString stripped (those are
    // tree-builder concerns and live in JsonParser, not here).
    // -------------------------------------------------------------------

    private boolean isIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    private boolean isIdentifierPart(int c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    private String readUnquotedIdentifier(int firstChar) {
        strBuf.setLength(0);
        strBuf.append((char) firstChar);

        while (true) {
            int c = input.read();
            if (c == -1 || !isIdentifierPart(c)) {
                if (c != -1) {
                    input.pushback((char) c);
                }
                break;
            }
            strBuf.append((char) c);
        }

        return strBuf.toString();
    }

    private void readToken(CharSequence token) {
        final int len = token.length();

        if (len <= 5) {
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) {
                    error("EOF reached while reading token: " + token);
                }
                if (c >= 'A' && c <= 'Z') {
                    c += 32;
                }
                if (token.charAt(i) != c) {
                    error("Expected token: " + token);
                }
            }
        } else {
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) {
                    error("EOF reached while reading token: " + token);
                }
                if (c >= 'A' && c <= 'Z') {
                    c += 32;
                }
                int loTokenChar = token.charAt(i);
                if (loTokenChar != c) {
                    error("Expected token: " + token);
                }
            }
        }
    }

    private Number readNumber(int c) {
        // Fast path: simple positive integers (1-9 followed by digits).
        if (c >= '1' && c <= '9' && !integerTypeBigInteger) {
            final FastReader in = input;
            long n = c - '0';
            int digitCount = 1;

            while (true) {
                int d = in.read();
                if (d >= '0' && d <= '9') {
                    if (++digitCount > 18) {
                        return readNumberContinuation(n, d);
                    }
                    n = n * 10 + (d - '0');
                } else if (d == '.' || d == 'e' || d == 'E') {
                    return readNumberContinuation(n, d);
                } else {
                    if (d != -1) {
                        in.pushback((char) d);
                    }
                    return n;
                }
            }
        }

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
                return readNumberGeneral('-', c);
            }
        }

        return readNumberGeneral(c);
    }

    private Number readNumberContinuation(long prefix, int c) {
        final FastReader in = input;
        StringBuilder number = numBuf;
        number.setLength(0);
        number.append(prefix);

        boolean isFloat = false;
        boolean seenDot = false;
        boolean seenExp = false;

        while (true) {
            if (c >= '0' && c <= '9') {
                number.append((char) c);
            } else if (c == '.') {
                if (seenDot || seenExp) return (Number) error("Invalid number: " + number + ".");
                number.append((char) c);
                isFloat = true;
                seenDot = true;
            } else if (c == 'e' || c == 'E') {
                if (seenExp) return (Number) error("Invalid number: " + number + (char) c);
                number.append((char) c);
                isFloat = true;
                seenExp = true;
                int next = in.read();
                if (next == '+' || next == '-') { number.append((char) next); next = in.read(); }
                if (next < '0' || next > '9') {
                    if (next != -1) in.pushback((char) next);
                    return (Number) error("Invalid exponent in number: " + number);
                }
                number.append((char) next);
            } else {
                if (c != -1) in.pushback((char) c);
                break;
            }
            c = in.read();
        }

        try {
            if (isFloat) return readFloatingPoint(number);
            return readInteger(number);
        } catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    private Number readNumberGeneral(int firstChar) {
        return readNumberGeneral(firstChar, NO_PREFETCH);
    }

    private Number readNumberGeneral(int firstChar, int prefetchedAfterSign) {
        final FastReader in = input;
        boolean isFloat = false;
        boolean isNegative = (firstChar == '-');
        boolean isPositive = (firstChar == '+');
        boolean seenDot = false;
        boolean seenExp = false;
        boolean seenDigit = false;

        int firstNumberChar = firstChar;
        if (isNegative || isPositive) {
            firstNumberChar = prefetchedAfterSign == NO_PREFETCH ? in.read() : prefetchedAfterSign;
        }

        int pendingChar = NO_PREFETCH;
        if (firstNumberChar == '0') {
            int next = in.read();
            if (next == 'x' || next == 'X') {
                if (strictJson) {
                    error("Hexadecimal numbers not allowed in strict JSON mode");
                }
                return readHexNumber(isNegative);
            }
            pendingChar = next;
        }

        StringBuilder number = numBuf;
        number.setLength(0);
        if (isNegative) {
            number.append((char) firstChar);
        }
        if ((isPositive || isNegative) && firstNumberChar == -1) {
            return (Number) error(isPositive ? "Unexpected end of input after '+'" : "Invalid number: -");
        }

        if (firstNumberChar >= '0' && firstNumberChar <= '9') {
            number.append((char) firstNumberChar);
            seenDigit = true;
        } else if (firstNumberChar == '.') {
            number.append((char) firstNumberChar);
            isFloat = true;
            seenDot = true;
        } else {
            return (Number) error("Invalid number: " + (isPositive ? "+" : "") + number + (char) firstNumberChar);
        }

        while (true) {
            int c = pendingChar == NO_PREFETCH ? in.read() : pendingChar;
            pendingChar = NO_PREFETCH;
            if (c >= '0' && c <= '9') {
                number.append((char) c);
                seenDigit = true;
            } else if (c == '.') {
                if (seenDot || seenExp) {
                    return (Number) error("Invalid number: " + number + ".");
                }
                number.append((char) c);
                isFloat = true;
                seenDot = true;
            } else if (c == 'e' || c == 'E') {
                if (seenExp || !seenDigit) {
                    return (Number) error("Invalid number: " + number + (char) c);
                }
                number.append((char) c);
                isFloat = true;
                seenExp = true;

                int next = in.read();
                if (next == '+' || next == '-') {
                    number.append((char) next);
                    next = in.read();
                }
                if (next < '0' || next > '9') {
                    if (next != -1) {
                        in.pushback((char) next);
                    }
                    return (Number) error("Invalid exponent in number: " + number);
                }
                number.append((char) next);
            } else if (c == -1) {
                break;
            } else {
                in.pushback((char) c);
                break;
            }
        }

        if (!seenDigit) {
            return (Number) error("Invalid number: " + number);
        }

        try {
            if (isFloat) {
                return readFloatingPoint(number);
            }
            return readInteger(number);
        } catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    private Number readInteger(CharSequence number) {
        if (integerTypeBigInteger) {
            return parseBigInteger(number);
        }

        int len = number.length();
        boolean isNeg = number.charAt(0) == '-';
        int digitCount = isNeg ? len - 1 : len;

        if (digitCount <= 18) {
            long n = 0;
            int start = isNeg ? 1 : 0;
            for (int i = start; i < len; i++) {
                n = n * 10 + (number.charAt(i) - '0');
            }
            return isNeg ? -n : n;
        }

        String numStr = number.toString();
        try {
            return Long.parseLong(numStr);
        } catch (Exception e) {
            BigInteger bigInt = parseBigInteger(numStr);
            if (integerTypeBoth) {
                return bigInt;
            }
            return bigInt.longValue();
        }
    }

    private Number readFloatingPoint(CharSequence numStr) {
        if (floatingPointBigDecimal) {
            return parseBigDecimal(numStr);
        }
        if (!floatingPointBoth) {
            return parseDouble(numStr);
        }
        return parseToMinimalNumericType(numStr);
    }

    private Number readHexNumber(boolean isNegative) {
        final FastReader in = input;
        final int[] hexMap = HEX_VALUE_MAP;
        long value = 0;
        int digitCount = 0;

        while (true) {
            int c = in.read();
            int digit = (c >= 0 && c < 128) ? hexMap[c] : -1;
            if (digit < 0) {
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

    private CharSequence readString(char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;

        final FastReader.BufferSlice slice = readSlice;
        int charsRead = in.readUntilBorrowed(slice, buf.length, quoteChar, '\\');
        char[] chars;
        int offset;
        boolean borrowed = false;
        if (charsRead == FastReader.COPY_REQUIRED) {
            charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
            chars = buf;
            offset = 0;
        } else if (charsRead >= 0) {
            borrowed = true;
            chars = slice.getBuffer();
            offset = slice.getOffset();
        } else {
            chars = buf;
            offset = 0;
        }
        if (charsRead >= 0 && charsRead < buf.length) {
            if (borrowed) {
                int delimiter = chars[offset + charsRead];
                if (delimiter == quoteChar) {
                    CharSequence value = cacheStringFromChars(chars, offset, charsRead);
                    slice.release();

                    int c = in.read();
                    if (c == -1) {
                        error("EOF reached while reading JSON string");
                    }
                    if (c != quoteChar) {
                        error("Expected closing quote while reading JSON string");
                    }
                    return value;
                }

                final StringBuilder str = strBuf;
                str.setLength(0);
                if (charsRead > 0) {
                    str.append(chars, offset, charsRead);
                }
                slice.release();

                int c = in.read();
                if (c == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (c != '\\') {
                    error("Expected escape delimiter while reading JSON string");
                }
                int escapeChar = in.read();
                if (escapeChar == -1) {
                    error("EOF reached while reading escape sequence");
                }
                return readStringWithEscapes(str, escapeChar, quoteChar);
            }

            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }
            if (c == quoteChar) {
                return cacheStringFromChars(chars, offset, charsRead);
            }
            int escapeChar = in.read();
            if (escapeChar == -1) {
                error("EOF reached while reading escape sequence");
            }
            final StringBuilder str = strBuf;
            str.setLength(0);
            if (charsRead > 0) {
                str.append(chars, offset, charsRead);
            }
            return readStringWithEscapes(str, escapeChar, quoteChar);
        }

        // String exceeds buffer or EOF — slow path.
        final StringBuilder str = strBuf;
        str.setLength(0);
        if (charsRead == -1) {
            error("EOF reached while reading JSON string");
        }
        if (charsRead > 0) {
            str.append(chars, offset, charsRead);
        }
        if (borrowed) {
            slice.release();
        }
        return readStringSlowPath(str, quoteChar);
    }

    private CharSequence readStringSlowPath(StringBuilder str, char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;

        while (true) {
            int charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
            if (charsRead == -1) {
                error("EOF reached while reading JSON string");
            }
            if (charsRead > 0) {
                str.append(buf, 0, charsRead);
            }
            if (charsRead == buf.length) {
                continue;
            }

            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }
            if (c == quoteChar) {
                break;
            }
            return readStringWithEscapes(str, c, quoteChar);
        }
        return cacheString(str);
    }

    private CharSequence readStringWithEscapes(StringBuilder str, int delimChar, char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;
        final char[] ESCAPE_CHARS = ESCAPE_CHAR_MAP;
        final int[] HEX_VALUES = HEX_VALUE_MAP;

        int c = delimChar;
        while (true) {
            if (c == -1) {
                error("EOF reached while reading escape sequence");
            }

            if (c < ESCAPE_CHARS.length) {
                char escaped = ESCAPE_CHARS[c];
                if (escaped != '\0') {
                    str.append(escaped);
                } else if (c == 'u') {
                    handleUnicodeEscape(str, HEX_VALUES);
                } else if (c == '\n') {
                    if (strictJson) { error("Multi-line strings not allowed in strict JSON mode"); }
                } else if (c == '\r') {
                    if (strictJson) { error("Multi-line strings not allowed in strict JSON mode"); }
                    int next = in.read();
                    if (next != '\n' && next != -1) { in.pushback((char) next); }
                } else {
                    error("Invalid character escape sequence specified: " + (char) c);
                }
            } else {
                error("Invalid character escape sequence specified: " + (char) c);
            }

            while (true) {
                int charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
                if (charsRead == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (charsRead > 0) {
                    str.append(buf, 0, charsRead);
                }
                if (charsRead == buf.length) {
                    continue;
                }

                c = in.read();
                if (c == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (c == quoteChar) {
                    return cacheString(str);
                }
                c = in.read();
                break;
            }
        }
    }

    private void handleUnicodeEscape(StringBuilder str, int[] HEX_VALUES) {
        final FastReader in = input;

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int c = in.read();
            if (c == -1) { error("EOF reached while reading Unicode escape sequence"); }
            int digit = (c < 128) ? HEX_VALUES[c] : -1;
            if (digit < 0) { error("Expected hexadecimal digit, got: " + (char) c); }
            value = (value << 4) | digit;
        }

        if (value < 0xD800 || value > 0xDFFF) {
            str.append((char) value);
            return;
        }

        if (value <= 0xDBFF) {
            int next = in.read();
            if (next == '\\') {
                next = in.read();
                if (next == 'u') {
                    int lowSurrogate = 0;
                    for (int i = 0; i < 4; i++) {
                        int c = in.read();
                        if (c == -1) { error("EOF reached while reading Unicode escape sequence"); }
                        int digit = (c < 128) ? HEX_VALUES[c] : -1;
                        if (digit < 0) { error("Expected hexadecimal digit, got: " + (char) c); }
                        lowSurrogate = (lowSurrogate << 4) | digit;
                    }
                    if (lowSurrogate >= 0xDC00 && lowSurrogate <= 0xDFFF) {
                        int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                        str.appendCodePoint(codePoint);
                        return;
                    }
                    str.append((char) value);
                    str.append((char) lowSurrogate);
                    return;
                }
                in.pushback((char) next);
                in.pushback('\\');
            } else if (next != -1) {
                in.pushback((char) next);
            }
        }
        str.append((char) value);
    }

    private static int cacheHash(char first, char mid, char last, int len) {
        return (first * 31 + mid) * 31 + last + len;
    }

    private CharSequence cacheString(CharSequence str) {
        final int len = str.length();
        if (len == 0) {
            return "";
        }

        if (len > MAX_CACHED_STRING_LENGTH) {
            return str.toString();
        }

        final int slot = cacheHash(str.charAt(0), str.charAt(len >> 1), str.charAt(len - 1), len) & STRING_CACHE_MASK;
        final String cached = stringCacheArray[slot];

        if (cached != null && cached.length() == len && cached.contentEquals(str)) {
            return cached;
        }

        final String s = str.toString();
        stringCacheArray[slot] = s;
        return s;
    }

    private CharSequence cacheStringFromChars(char[] buf, int offset, int len) {
        if (len == 0) {
            return "";
        }

        if (len > MAX_CACHED_STRING_LENGTH) {
            return new String(buf, offset, len);
        }

        final int slot = cacheHash(buf[offset], buf[offset + (len >> 1)], buf[offset + len - 1], len) & STRING_CACHE_MASK;
        final String cached = stringCacheArray[slot];

        if (cached != null && cached.length() == len) {
            boolean match = true;
            for (int i = 0; i < len; i++) {
                if (cached.charAt(i) != buf[offset + i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return cached;
            }
        }

        final String s = new String(buf, offset, len);
        stringCacheArray[slot] = s;
        return s;
    }

    private int skipWhitespaceRead(boolean throwOnEof) throws IOException {
        final FastReader in = input;
        int c;
        if (strictJson) {
            while (true) {
                c = in.read();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    continue;
                }
                if (c == '/') {
                    int next = in.read();
                    if (next == '/' || next == '*') {
                        error("Comments not allowed in strict JSON mode");
                    }
                    if (next != -1) {
                        in.pushback((char) next);
                    }
                    return c;
                }
                break;
            }
            if (c == -1 && throwOnEof) {
                error("EOF reached prematurely");
            }
            return c;
        }

        while (true) {
            c = in.read();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            if (c == '/') {
                int next = in.read();
                if (next == '/') {
                    skipSingleLineComment();
                    continue;
                } else if (next == '*') {
                    skipBlockComment();
                    continue;
                } else {
                    if (next != -1) {
                        in.pushback((char) next);
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

    private void skipSingleLineComment() {
        int c;
        while ((c = input.read()) != -1) {
            if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        input.pushback((char) next);
                    }
                }
                return;
            }
        }
    }

    private void skipBlockComment() {
        boolean sawStar = false;
        int c;
        while ((c = input.read()) != -1) {
            if (sawStar && c == '/') {
                return;
            }
            sawStar = c == '*';
        }
        error("Unterminated block comment");
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
