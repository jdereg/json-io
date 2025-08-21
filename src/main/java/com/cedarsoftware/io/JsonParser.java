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
 * Head-based, iterative JSON parser that avoids Java recursion to safely handle ultra-deep JSON.
 *
 * The previous implementation relied on recursive descent (stack-based) calls to parse objects/arrays.
 * This refactor uses a single iterative state-machine with a movable "head" frame that points to the
 * current container (object/array). Each frame links to its parent, so we never consume the JVM call stack
 * regardless of JSON depth.
 *
 * Design notes:
 * - All scalar token readers (string/number/tokens) are reused.
 * - Objects/arrays are parsed by walking a small state machine per frame (object: key→:→value→,|}; array: value→,|]).
 * - Field type inference honors Injectors and is refreshed if @type changes mid-object.
 * - Reserved fields (@id, @ref, @type, @items, @keys, @enum) behave exactly as before.
 * - Depth limiting via maxParseDepth still enforced (but no risk of StackOverflowError).
 *
 * @author John DeRegnaucourt
 */
class JsonParser {
    private static final JsonObject EMPTY_ARRAY = new JsonObject();  // kept for compatibility
    private final FastReader input;
    private final StringBuilder strBuf;
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0; // maintained as current frame depth
    private final boolean allowNanAndInfinity;
    private final int maxParseDepth;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;
    
    // Frame pool to reduce allocations - disabled for now as it hurts performance
    // private Frame framePool = null;
    // private int framePoolSize = 0;
    // private static final int MAX_POOL_SIZE = 64;

    // Instance-level cache for parser-specific strings
    private final Map<String, String> stringCache;
    private final Map<Number, Number> numberCache;
    private final Map<String, String> substitutes;

    // Optimized string buffer management with size-based strategy
    // Fix ThreadLocal resource leak by providing cleanup utilities
    private static volatile int threadLocalBufferSize = 1024;
    private static volatile int largeThreadLocalBufferSize = 8192;

    // ThreadLocal buffers that respect the configured sizes
    private static final ThreadLocal<char[]> STRING_BUFFER = new ThreadLocal<char[]>() {
        @Override
        protected char[] initialValue() { return new char[threadLocalBufferSize]; }
    };
    private static final ThreadLocal<char[]> LARGE_STRING_BUFFER = new ThreadLocal<char[]>() {
        @Override
        protected char[] initialValue() { return new char[largeThreadLocalBufferSize]; }
    };

    static void configureThreadLocalBufferSizes(int threadLocalBufferSize, int largeThreadLocalBufferSize) {
        JsonParser.threadLocalBufferSize = threadLocalBufferSize;
        JsonParser.largeThreadLocalBufferSize = largeThreadLocalBufferSize;
    }
    public static void clearThreadLocalBuffers() {
        STRING_BUFFER.remove();
        LARGE_STRING_BUFFER.remove();
    }

    // Primary static cache that never changes
    private static final Map<String, String> STATIC_STRING_CACHE = new ConcurrentHashMap<>(64);
    private static final Map<Number, Number> STATIC_NUMBER_CACHE = new ConcurrentHashMap<>(16);
    private static final Map<String, String> SUBSTITUTES = new HashMap<>(5);

    // Static lookup tables for performance
    private static final char[] ESCAPE_CHAR_MAP = new char[128];
    private static final int[] HEX_VALUE_MAP = new int[128];
    private static final boolean[] WHITESPACE_MAP = new boolean[128];

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

        WHITESPACE_MAP[' '] = true;
        WHITESPACE_MAP['\t'] = true;
        WHITESPACE_MAP['\n'] = true;
        WHITESPACE_MAP['\r'] = true;

        SUBSTITUTES.put(SHORT_ID, ID);
        SUBSTITUTES.put(SHORT_REF, REF);
        SUBSTITUTES.put(SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(SHORT_TYPE, TYPE);
        SUBSTITUTES.put(SHORT_KEYS, KEYS);

        String[] commonStrings = {
                "", "true", "True", "TRUE", "false", "False", "FALSE",
                "null", "yes", "Yes", "YES", "no", "No", "NO",
                "on", "On", "ON", "off", "Off", "OFF",
                "id", "ID", "type", "value", "name",
                ID, REF, ITEMS, TYPE, KEYS,
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
        };
        for (String s : commonStrings) STATIC_STRING_CACHE.put(s, s);

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

    // Two-tier caches per instance
    private static class ParserStringCache extends AbstractMap<String, String> {
        private final Map<String, String> staticCache;
        private final Map<String, String> instanceCache;
        ParserStringCache(Map<String, String> staticCache) {
            this.staticCache = staticCache;
            this.instanceCache = new HashMap<>(64);
        }
        @Override public String get(Object key) {
            String s = staticCache.get(key);
            return s != null ? s : instanceCache.get(key);
        }
        @Override public String put(String key, String value) { return instanceCache.put(key, value); }
        @Override public Set<Entry<String, String>> entrySet() {
            Set<Entry<String, String>> e = new HashSet<>();
            e.addAll(staticCache.entrySet());
            e.addAll(instanceCache.entrySet());
            return e;
        }
    }
    private static class ParserNumberCache extends AbstractMap<Number, Number> {
        private final Map<Number, Number> staticCache;
        private final Map<Number, Number> instanceCache;
        ParserNumberCache(Map<Number, Number> staticCache) {
            this.staticCache = staticCache;
            this.instanceCache = new HashMap<>(64);
        }
        @Override public Number get(Object key) {
            Number n = staticCache.get(key);
            return n != null ? n : instanceCache.get(key);
        }
        @Override public Number put(Number key, Number value) { return instanceCache.put(key, value); }
        @Override public Set<Entry<Number, Number>> entrySet() {
            Set<Entry<Number, Number>> e = new HashSet<>();
            e.addAll(staticCache.entrySet());
            e.addAll(instanceCache.entrySet());
            return e;
        }
    }

    JsonParser(FastReader reader, Resolver resolver) {
        this.substitutes = SUBSTITUTES;
        this.stringCache = new ParserStringCache(STATIC_STRING_CACHE);
        this.numberCache = new ParserNumberCache(STATIC_NUMBER_CACHE);
        this.input = reader;
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        this.references = resolver.getReferences();
        this.maxParseDepth = readOptions.getMaxDepth();
        this.allowNanAndInfinity = readOptions.isAllowNanAndInfinity();
        this.strBuf = new StringBuilder(readOptions.getStringBufferSize());
        configureThreadLocalBufferSizes(readOptions.getThreadLocalBufferSize(), readOptions.getLargeThreadLocalBufferSize());
    }

    /** Public entry: read a JSON value (object/array/scalar). */
    Object readValue(Type suggestedType) throws IOException {
        int c = skipWhitespaceRead(true);
        switch (c) {
            case '"':
                // root scalar string
                return readString();
            case '{':
            case '[':
                // Use head-based iterative container parser for objects/arrays
                return readHeadBased(suggestedType, c);
            case ']':   // legacy EMPTY_ARRAY sentinel for recursive impl; retain behavior
                input.pushback(']');
                return EMPTY_ARRAY;
            case 'f': case 'F':
                readToken("false");
                return false;
            case 't': case 'T':
                readToken("true");
                return true;
            case 'n':
                readToken("null");
                return null;
            case 'N': // NaN
            case 'I': // Infinity
            case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                return readNumber(c);
            default:
                return error("Unknown JSON value type");
        }
    }

    // ====== Head-based iterative containers ======

    private enum ObjState { EXPECT_KEY_OR_END, EXPECT_COLON, EXPECT_VALUE, EXPECT_COMMA_OR_END }
    private enum ArrState { EXPECT_VALUE_OR_END, EXPECT_COMMA_OR_END }

    private static final class Frame {
        Frame parent;
        boolean isObject;
        JsonObject obj;             // if object
        List<Object> arr;           // if array
        ObjState objState;          // for objects
        ArrState arrState;          // for arrays
        String pendingKey;          // current field name for object
        Map<String, Injector> injectors; // injector map (refresh if @type encountered)
        Type containerSuggestedType;     // suggested type for this container
        Type elementType;                // for arrays
        int line; int col;               // for diagnostics
    }

    private Object readHeadBased(Type suggestedType, int firstChar) throws IOException {
        Frame head = null;

        // bootstrap the first frame from firstChar
        if (firstChar == '{') {
            head = openObjectFrame(null, suggestedType);
        } else if (firstChar == '[') {
            head = openArrayFrame(null, suggestedType);
        } else {
            return error("Internal error: readHeadBased requires '{' or '['");
        }

        while (true) {
            if (head == null) {
                // shouldn't happen; we always return when root is closed
                return error("Internal parser error: empty frame stack");
            }

            if (head.isObject) {
                switch (head.objState) {
                    case EXPECT_KEY_OR_END: {
                        int c = skipWhitespaceRead(true);
                        if (c == '}') {
                            // close object
                            Object completed = closeObjectFrame(head);
                            head = attachToParentAndMaybeReturn(head, completed);
                            if (head == null) return completed; // root done
                            break;
                        }
                        if (c != '"') {
                            error("Expected quote before field name");
                        }
                        String field = readString();
                        // Only check substitutes for short strings that might be aliases
                        if (field.length() <= 2) {
                            String sub = substitutes.get(field);
                            head.pendingKey = (sub != null) ? sub : field;
                        } else {
                            head.pendingKey = field;
                        }
                        head.objState = ObjState.EXPECT_COLON;
                        break;
                    }
                    case EXPECT_COLON: {
                        int c = skipWhitespaceRead(true);
                        if (c != ':') {
                            error("Expected ':' between field and value, instead found '" + (char)c + "'");
                        }
                        head.objState = ObjState.EXPECT_VALUE;
                        break;
                    }
                    case EXPECT_VALUE: {
                        // infer field generic type via Injector (using current known container type)
                        Type fieldType = null;
                        // Only look up injector for non-reserved fields
                        if (head.injectors != null && !isReservedField(head.pendingKey)) {
                            Injector inj = head.injectors.get(head.pendingKey);
                            if (inj != null) {
                                Type ft = inj.getGenericType();
                                if (ft != null && head.containerSuggestedType != null) {
                                    // resolve against this container's suggested/actual type
                                    fieldType = TypeUtilities.resolveType(head.containerSuggestedType, ft);
                                }
                            }
                        }
                        int c = skipWhitespaceRead(true);
                        // Fast path for common cases
                        if (c == '"') {
                            String s = readString();
                            setObjectField(head, head.pendingKey, s);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else if (c >= '0' && c <= '9') {
                            Number num = readNumber(c);
                            setObjectField(head, head.pendingKey, num);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else if (c == '{') {
                            head = openObjectFrame(head, fieldType);
                        } else if (c == '[') {
                            head = openArrayFrame(head, fieldType);
                        } else if (c == 't' || c == 'T') {
                            readToken("true");
                            setObjectField(head, head.pendingKey, true);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else if (c == 'f' || c == 'F') {
                            readToken("false");
                            setObjectField(head, head.pendingKey, false);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else if (c == 'n') {
                            readToken("null");
                            setObjectField(head, head.pendingKey, null);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else if (c == '-' || c == 'N' || c == 'I') {
                            Number num = readNumber(c);
                            setObjectField(head, head.pendingKey, num);
                            head.objState = ObjState.EXPECT_COMMA_OR_END;
                        } else {
                            error("Unknown JSON value type for field '" + head.pendingKey + "'");
                        }
                        break;
                    }
                    case EXPECT_COMMA_OR_END: {
                        int c = skipWhitespaceRead(true);
                        if (c == ',') {
                            head.pendingKey = null;
                            head.objState = ObjState.EXPECT_KEY_OR_END;
                        } else if (c == '}') {
                            Object completed = closeObjectFrame(head);
                            head = attachToParentAndMaybeReturn(head, completed);
                            if (head == null) return completed;
                        } else {
                            error("Object not ended with '}', instead found '" + (char)c + "'");
                        }
                        break;
                    }
                }
            } else { // array
                switch (head.arrState) {
                    case EXPECT_VALUE_OR_END: {
                        int c = skipWhitespaceRead(true);
                        if (c == ']') {
                            Object completed = closeArrayFrame(head);
                            head = attachToParentAndMaybeReturn(head, completed);
                            if (head == null) return completed;
                            break;
                        }
                        // Fast path for common cases in arrays
                        if (c == '"') {
                            String s = readString();
                            head.arr.add(s);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else if (c >= '0' && c <= '9') {
                            Number num = readNumber(c);
                            head.arr.add(num);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else if (c == '{') {
                            head = openObjectFrame(head, head.elementType);
                        } else if (c == '[') {
                            head = openArrayFrame(head, head.elementType);
                        } else if (c == 't' || c == 'T') {
                            readToken("true");
                            head.arr.add(true);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else if (c == 'f' || c == 'F') {
                            readToken("false");
                            head.arr.add(false);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else if (c == 'n') {
                            readToken("null");
                            head.arr.add(null);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else if (c == '-' || c == 'N' || c == 'I') {
                            Number num = readNumber(c);
                            head.arr.add(num);
                            head.arrState = ArrState.EXPECT_COMMA_OR_END;
                        } else {
                            error("Unknown JSON value type");
                        }
                        break;
                    }
                    case EXPECT_COMMA_OR_END: {
                        int c = skipWhitespaceRead(true);
                        if (c == ',') {
                            head.arrState = ArrState.EXPECT_VALUE_OR_END;
                        } else if (c == ']') {
                            Object completed = closeArrayFrame(head);
                            head = attachToParentAndMaybeReturn(head, completed);
                            if (head == null) return completed;
                        } else {
                            error("Expected ',' or ']' inside array");
                        }
                        break;
                    }
                }
            }
        }
    }

    private Frame openObjectFrame(Frame parent, Type suggestedType) {
        final FastReader in = input; // '{' has already been consumed by caller
        Frame f = new Frame();
        f.parent = parent;
        f.isObject = true;
        f.obj = new JsonObject();
        f.containerSuggestedType = suggestedType;
        // Don't set type on JsonObject unless we encounter explicit @type field
        // This preserves the original behavior where empty objects don't get type annotations
        f.objState = ObjState.EXPECT_KEY_OR_END;
        f.line = in.getLine();
        f.col = in.getCol();
        f.obj.line = f.line;
        f.obj.col = f.col;
        // injector map based on current known container type - only if we have a type
        if (suggestedType != null) {
            Class<?> raw = TypeUtilities.getRawClass(suggestedType);
            f.injectors = readOptions.getDeepInjectorMap(raw);
        }
        pushDepth();
        return f;
    }

    private Frame openArrayFrame(Frame parent, Type suggestedType) {
        Frame f = new Frame();
        f.parent = parent;
        f.isObject = false;
        f.arr = new ArrayList<>();
        f.arrState = ArrState.EXPECT_VALUE_OR_END;
        f.elementType = TypeUtilities.extractArrayComponentType(suggestedType);
        pushDepth();
        return f;
    }

    private Object closeObjectFrame(Frame f) {
        popDepth();
        JsonObject result = f.obj;
        // Don't release frame yet - attachToParentAndMaybeReturn needs f.parent
        return result;
    }
    private Object closeArrayFrame(Frame f) {
        popDepth();
        Object result;
        // For root arrays, we need to call resolveArray to get the correct type
        // For nested arrays, return raw array to preserve structure for field resolution
        if (f.parent == null) {
            // Root array - need proper type resolution
            result = resolver.resolveArray(f.elementType, f.arr);
        } else {
            // Nested array - return raw for field-specific handling
            result = f.arr.toArray();
        }
        // Don't release frame yet - attachToParentAndMaybeReturn needs f.parent
        return result;
    }

    private Frame attachToParentAndMaybeReturn(Frame justClosed, Object value) {
        Frame parent = justClosed.parent;
        if (parent == null) {
            // root complete — validate no trailing junk
            int c;
            try { c = skipWhitespaceRead(false); } catch (IOException e) { throw new JsonIoException(getMessage("IO error after root"), e); }
            if (c != -1) {
                throw new JsonIoException("EOF expected, content found after root value\n" + input.getLastSnippet());
            }
            return null; // caller returns value
        }
        if (parent.isObject) {
            // attach as field value to the parent object
            setObjectField(parent, parent.pendingKey, value);
            parent.objState = ObjState.EXPECT_COMMA_OR_END;
        } else {
            parent.arr.add(value);
            parent.arrState = ArrState.EXPECT_COMMA_OR_END;
        }
        return parent; // head moves back up
    }

    private void setObjectField(Frame objFrame, String field, Object value) {
        JsonObject jObj = objFrame.obj;
        // Use direct comparisons for better performance
        if (TYPE.equals(field)) {
            Class<?> type = loadType(value);
            jObj.setTypeString((String) value);
            jObj.setType(type);
            // refresh injector map with new concrete type
            objFrame.containerSuggestedType = type;
            objFrame.injectors = readOptions.getDeepInjectorMap(type);
        } else if (ENUM.equals(field)) {
            loadEnum(value, jObj);
        } else if (REF.equals(field)) {
            loadRef(value, jObj);
        } else if (ID.equals(field)) {
            loadId(value, jObj);
        } else if (ITEMS.equals(field)) {
            if (value != null && !value.getClass().isArray()) {
                error("Expected @items to have an array [], but found: " + value.getClass().getName());
            }
            loadItems((Object[]) value, jObj);
        } else if (KEYS.equals(field)) {
            if (value != null && !value.getClass().isArray()) {
                error("Expected @keys to have an array [], but found: " + value.getClass().getName());
            }
            loadKeys(value, jObj);
        } else {
            jObj.put(field, value);
        }
    }

    private void pushDepth() {
        curParseDepth++;
        if (curParseDepth > maxParseDepth) {
            error("Maximum parsing depth exceeded");
        }
    }
    private void popDepth() { curParseDepth--; }
    
    // Check if field is a reserved field that won't have injectors
    private static boolean isReservedField(String field) {
        return TYPE.equals(field) || ENUM.equals(field) || REF.equals(field) || 
               ID.equals(field) || ITEMS.equals(field) || KEYS.equals(field);
    }
    

    // ====== Scalar readers (unchanged) ======

    private void readToken(String token) throws IOException {
        final int len = token.length();
        if (len <= 5) {
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) error("EOF reached while reading token: " + token);
                if (c >= 'A' && c <= 'Z') c += 32; // to lowercase
                if (token.charAt(i) != c) error("Expected token: " + token);
            }
        } else {
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) error("EOF reached while reading token: " + token);
                c = Character.toLowerCase((char) c);
                if (token.charAt(i) != c) error("Expected token: " + token);
            }
        }
    }

    private Number readNumber(int c) throws IOException {
        final FastReader in = input;
        if (allowNanAndInfinity && (c == '-' || c == 'N' || c == 'I')) {
            final boolean isNeg = (c == '-');
            if (isNeg) { c = input.read(); }
            if (c == 'I') { readToken("infinity"); return isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY; }
            else if (c == 'N') { readToken("nan"); return Double.NaN; }
            else { input.pushback((char) c); c = '-'; }
        }
        if (c >= '0' && c <= '9') {
            int nextChar = in.read();
            if (nextChar == -1 || nextChar == ',' || nextChar == '}' || nextChar == ']' ||
                    nextChar == ' ' || nextChar == '\t' || nextChar == '\n' || nextChar == '\r') {
                if (nextChar != -1) in.pushback((char) nextChar);
                long value = c - '0';
                final Number cached = numberCache.get(value);
                if (cached != null) return cached; else { numberCache.put(value, value); return value; }
            } else {
                in.pushback((char) nextChar);
                return readNumberFallback(c);
            }
        }
        return readNumberFallback(c);
    }

    private Number readNumberFallback(int firstChar) throws IOException {
        final FastReader in = input;
        boolean isFloat = false;
        StringBuilder number = numBuf;
        number.setLength(0);
        number.append((char) firstChar);
        while (true) {
            int c = in.read();
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                number.append((char) c);
            } else if (c == '.' || c == 'e' || c == 'E') {
                number.append((char) c); isFloat = true;
            } else if (c == -1) { break; }
            else { in.pushback((char) c); break; }
        }
        try {
            Number val = isFloat ? readFloatingPoint(number.toString()) : readInteger(number.toString());
            final Number cached = numberCache.get(val);
            if (cached != null) return cached; else { numberCache.put(val, val); return val; }
        } catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    private Number readInteger(String numStr) {
        if (readOptions.isIntegerTypeBigInteger()) return new BigInteger(numStr);
        try { return Long.parseLong(numStr); }
        catch (Exception e) {
            BigInteger bigInt = new BigInteger(numStr);
            if (readOptions.isIntegerTypeBoth()) return bigInt; else return bigInt.longValue();
        }
    }
    private Number readFloatingPoint(String numStr) {
        if (readOptions.isFloatingPointBigDecimal()) return new BigDecimal(numStr);
        Number number = parseToMinimalNumericType(numStr);
        return readOptions.isFloatingPointBoth() ? number : number.doubleValue();
    }

    // Strings (kept from prior impl; respects curParseDepth==0 root EOF check)
    private String readString() throws IOException {
        final FastReader in = input;
        final char[] ESCAPE_CHARS = ESCAPE_CHAR_MAP;
        final int[] HEX_VALUES = HEX_VALUE_MAP;
        char[] buffer = STRING_BUFFER.get();
        int pos = 0;
        while (true) {
            int c = in.read();
            if (c == -1) error("EOF reached while reading JSON string");
            if (c == '"') {
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) throw new JsonIoException("EOF expected, content found after string");
                }
                return cacheString(new String(buffer, 0, pos));
            }
            if (c == '\\') {
                StringBuilder sb = strBuf; sb.setLength(0);
                sb.append(buffer, 0, pos);
                c = in.read(); if (c == -1) error("EOF reached while reading escape sequence");
                processEscape(sb, c, in, ESCAPE_CHARS, HEX_VALUES);
                return readStringWithEscapes(sb, in, ESCAPE_CHARS, HEX_VALUES);
            }
            if (pos >= buffer.length) {
                char[] largeBuffer = LARGE_STRING_BUFFER.get();
                System.arraycopy(buffer, 0, largeBuffer, 0, pos);
                largeBuffer[pos++] = (char) c;
                return readStringInBuffer(largeBuffer, pos);
            }
            buffer[pos++] = (char) c;
        }
    }

    private String readStringWithEscapes(StringBuilder sb, FastReader in,
                                         char[] ESCAPE_CHARS, int[] HEX_VALUES) throws IOException {
        while (true) {
            int c = in.read();
            if (c == -1) error("EOF reached while reading JSON string");
            if (c != '\\' && c != '"') { sb.append((char) c); continue; }
            if (c == '"') {
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) throw new JsonIoException("EOF expected, content found after string");
                }
                break;
            }
            c = in.read(); if (c == -1) error("EOF reached while reading escape sequence");
            processEscape(sb, c, in, ESCAPE_CHARS, HEX_VALUES);
        }
        return cacheString(sb.toString());
    }

    private void processEscape(StringBuilder sb, int c, FastReader in,
                               char[] ESCAPE_CHARS, int[] HEX_VALUES) throws IOException {
        if (c < 0 || c > 127) error("Invalid escape character code: " + c + " - escape characters must be ASCII (0-127)");
        if (c < ESCAPE_CHARS.length) {
            char escaped = ESCAPE_CHARS[c];
            if (escaped != '\0') { sb.append(escaped); return; }
        }
        if (c == 'u') {
            int value = 0;
            for (int i = 0; i < 4; i++) {
                c = in.read(); if (c == -1) error("EOF reached while reading Unicode escape sequence");
                if (c < 0 || c >= HEX_VALUES.length) error("Invalid character in Unicode escape sequence: " + c + " (out of ASCII range)");
                int digit = HEX_VALUES[c]; if (digit < 0) error("Expected hexadecimal digit, got: " + (char)c);
                value = (value << 4) | digit;
            }
            if (value >= 0xD800 && value <= 0xDBFF) {
                int next = in.read();
                if (next == '\\') {
                    next = in.read();
                    if (next == 'u') {
                        int lowSurrogate = 0;
                        for (int i = 0; i < 4; i++) {
                            c = in.read(); if (c == -1) error("EOF reached while reading Unicode escape sequence");
                            if (c < 0 || c >= HEX_VALUES.length) error("Invalid character in Unicode escape sequence: " + c + " (out of ASCII range)");
                            int digit = HEX_VALUES[c]; if (digit < 0) error("Expected hexadecimal digit, got: " + (char)c);
                            lowSurrogate = (lowSurrogate << 4) | digit;
                        }
                        if (lowSurrogate >= 0xDC00 && lowSurrogate <= 0xDFFF) {
                            int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                            sb.appendCodePoint(codePoint);
                            return;
                        } else {
                            sb.append((char)value); sb.append((char)lowSurrogate); return;
                        }
                    } else { in.pushback((char)next); in.pushback('\\'); }
                } else { if (next != -1) in.pushback((char)next); }
            }
            sb.append((char)value);
        } else {
            error("Invalid character escape sequence specified: " + (char)c);
        }
    }

    private String cacheString(String str) {
        final int length = str.length();
        if (length == 0) return "";
        if (length <= 2) return str.intern();
        if (length < 33) {
            final String cached = stringCache.get(str);
            if (cached != null) return cached;
            stringCache.put(str, str);
        }
        return str;
    }

    private StringBuilder getOptimizedStringBuilder(int estimatedSize) {
        StringBuilder sb = strBuf; sb.setLength(0);
        int target = Math.max(256, estimatedSize * 2);
        if (sb.capacity() < target) sb.ensureCapacity(target);
        return sb;
    }

    private String readStringInBuffer(char[] largeBuffer, int initialPos) throws IOException {
        final FastReader in = input;
        int pos = initialPos;
        while (true) {
            int c = in.read(); if (c == -1) error("EOF reached while reading JSON string");
            if (c == '"') return cacheString(new String(largeBuffer, 0, pos));
            if (c == '\\') {
                StringBuilder sb = getOptimizedStringBuilder(pos + 64);
                sb.append(largeBuffer, 0, pos);
                c = in.read(); if (c == -1) error("EOF reached while reading escape sequence");
                processEscape(sb, c, in, ESCAPE_CHAR_MAP, HEX_VALUE_MAP);
                return readStringWithEscapes(sb, in, ESCAPE_CHAR_MAP, HEX_VALUE_MAP);
            }
            if (pos >= largeBuffer.length) {
                StringBuilder sb = getOptimizedStringBuilder(pos + 64);
                sb.append(largeBuffer, 0, pos).append((char) c);
                return readStringWithEscapes(sb, in, ESCAPE_CHAR_MAP, HEX_VALUE_MAP);
            }
            largeBuffer[pos++] = (char) c;
        }
    }

    private int skipWhitespaceRead(boolean throwOnEof) throws IOException {
        int c;
        do { c = input.read(); } while (c >= 0 && c < 128 && WHITESPACE_MAP[c]);
        if (c == -1 && throwOnEof) error("EOF reached prematurely");
        return c;
    }

    private void loadId(Object value, JsonObject jObj) {
        if (value == null) error("Null value provided for " + ID + " field - expected a number");
        if (jObj == null) error("Null JsonObject provided to loadId method");
        if (!(value instanceof Number)) error("Expected a number for " + ID + ", instead got: " + value.getClass().getSimpleName());
        Long id = ((Number) value).longValue();
        long maxIdValue = readOptions.getMaxIdValue();
        if (id < -maxIdValue || id > maxIdValue) error("ID value out of safe range: " + id + " - IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        references.put(id, jObj);
        jObj.setId(id);
    }

    private void loadRef(Object value, JsonValue jObj) {
        if (value == null) error("Null value provided for " + REF + " field - expected a number");
        if (jObj == null) error("Null JsonValue provided to loadRef method");
        if (!(value instanceof Number)) error("Expected a number for " + REF + ", instead got: " + value.getClass().getSimpleName());
        Long refId = ((Number) value).longValue();
        long maxIdValue = readOptions.getMaxIdValue();
        if (refId < -maxIdValue || refId > maxIdValue) error("Reference ID value out of safe range: " + refId + " - reference IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        jObj.setReferenceId(refId);
    }

    private void loadEnum(Object value, JsonObject jObj) {
        if (!(value instanceof String)) error("Expected a String for " + ENUM + ", instead got: " + value);
        Class<?> enumClass = stringToClass((String) value);
        jObj.setTypeString((String) value);
        jObj.setType(enumClass);
        if (jObj.getItems() == null) jObj.setItems(ArrayUtilities.EMPTY_OBJECT_ARRAY);
    }

    private Class<?> loadType(Object value) {
        if (!(value instanceof String)) error("Expected a String for " + TYPE + ", instead got: " + value);
        String javaType = (String) value;
        final String substitute = readOptions.getTypeNameAlias(javaType);
        if (substitute != null) javaType = substitute;
        return stringToClass(javaType);
    }

    private void loadItems(Object[] value, JsonObject jObj) {
        if (value == null) return;
        if (!value.getClass().isArray()) error("Expected @items to have an array [], but found: " + value.getClass().getName());
        jObj.setItems(value);
    }

    private void loadKeys(Object value, JsonObject jObj) {
        if (value == null) return;
        if (!value.getClass().isArray()) error("Expected @keys to have an array [], but found: " + value.getClass().getName());
        jObj.setKeys((Object[]) value);
    }

    private Class<?> stringToClass(String className) {
        String resolvedName = readOptions.getTypeNameAlias(className);
        Class<?> clazz = ClassUtilities.forName(resolvedName, readOptions.getClassLoader());
        if (clazz == null) {
            if (readOptions.isFailOnUnknownType()) error("Unknown type (class) '" + className + "' not defined.");
            clazz = readOptions.getUnknownTypeClass();
            if (clazz == null) clazz = LinkedHashMap.class;
        }
        return clazz;
    }

    private Object error(String msg) { throw new JsonIoException(getMessage(msg)); }
    private Object error(String msg, Exception e) { throw new JsonIoException(getMessage(msg), e); }
    private String getMessage(String msg) { return msg + "\nline: " + input.getLine() + ", col: " + input.getCol() + "\n" + input.getLastSnippet(); }
}
