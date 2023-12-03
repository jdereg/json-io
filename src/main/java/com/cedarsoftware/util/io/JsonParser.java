package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ReturnType;
import sun.misc.FloatingDecimal;

import static com.cedarsoftware.util.io.JsonObject.ID;
import static com.cedarsoftware.util.io.JsonObject.ITEMS;
import static com.cedarsoftware.util.io.JsonObject.KEYS;
import static com.cedarsoftware.util.io.JsonObject.REF;
import static com.cedarsoftware.util.io.JsonObject.SHORT_ID;
import static com.cedarsoftware.util.io.JsonObject.SHORT_ITEMS;
import static com.cedarsoftware.util.io.JsonObject.SHORT_KEYS;
import static com.cedarsoftware.util.io.JsonObject.SHORT_REF;
import static com.cedarsoftware.util.io.JsonObject.SHORT_TYPE;
import static com.cedarsoftware.util.io.JsonObject.TYPE;

/**
 * Parse the JSON input stream supplied by the FastPushbackReader to the constructor.
 * The entire JSON input stream will be read until it is emptied: an EOF (-1) is read.
 *
 * While reading the content, Java Maps (JsonObjects) are used to hold the contents of
 * JSON objects { }.  Lists are used to hold the contents of JSON arrays.  Each object
 * that has an @id field will be copied into the supplied 'objectsMap' constructor
 * argument.  This allows the user of this class to locate any referenced object
 * directly.
 *
 * When this parser completes, the @ref (references to objects identified with @id)
 * are stored as a JsonObject with an @ref as the key and the ID value of the object.
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
class JsonParser
{
    protected static final JsonObject EMPTY_OBJECT = new JsonObject();  // compared with ==
    private static final JsonObject EMPTY_ARRAY = new JsonObject();  // compared with ==
    private static final int STATE_READ_START_OBJECT = 0;
    private static final int STATE_READ_FIELD = 1;
    private static final int STATE_READ_VALUE = 2;
    private static final Map<String, String> stringCache = new HashMap<>();
    private static final Map<Number, Number> numberCache = new HashMap<>();
    private final FastReader input;
    private final StringBuilder strBuf = new StringBuilder(256);
    private final StringBuilder hexBuf = new StringBuilder();
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0;
    private boolean allowNanAndInfinity;
    private final int maxParseDepth;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;
    private final Resolver resolver;

    static
    {
        // Save heap memory by re-using common strings (String's immutable)
        stringCache.put("", "");
        stringCache.put("true", "true");
        stringCache.put("True", "True");
        stringCache.put("TRUE", "TRUE");
        stringCache.put("false", "false");
        stringCache.put("False", "False");
        stringCache.put("FALSE", "FALSE");
        stringCache.put("null", "null");
        stringCache.put("yes", "yes");
        stringCache.put("Yes", "Yes");
        stringCache.put("YES", "YES");
        stringCache.put("no", "no");
        stringCache.put("No", "No");
        stringCache.put("NO", "NO");
        stringCache.put("on", "on");
        stringCache.put("On", "On");
        stringCache.put("ON", "ON");
        stringCache.put("off", "off");
        stringCache.put("Off", "Off");
        stringCache.put("OFF", "OFF");
        stringCache.put(ID, ID);
        stringCache.put(REF, REF);
        stringCache.put(ITEMS, ITEMS);
        stringCache.put(TYPE, TYPE);
        stringCache.put(KEYS, KEYS);
        stringCache.put(SHORT_ID, ID);
        stringCache.put(SHORT_REF, REF);
        stringCache.put(SHORT_ITEMS, ITEMS);
        stringCache.put(SHORT_TYPE, TYPE);
        stringCache.put(SHORT_KEYS, KEYS);
        stringCache.put("0", "0");
        stringCache.put("1", "1");
        stringCache.put("2", "2");
        stringCache.put("3", "3");
        stringCache.put("4", "4");
        stringCache.put("5", "5");
        stringCache.put("6", "6");
        stringCache.put("7", "7");
        stringCache.put("8", "8");
        stringCache.put("9", "9");

        numberCache.put(-1L, -1L);
        numberCache.put(0L, 0L);
        numberCache.put(1L, 1L);
        numberCache.put(-1.0d, -1.0d);
        numberCache.put(0.0d, 0.0d);
        numberCache.put(1.0d, 1.0d);
        numberCache.put(Double.MIN_VALUE, Double.MIN_VALUE);
        numberCache.put(Double.MAX_VALUE, Double.MAX_VALUE);
        numberCache.put(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        numberCache.put(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        numberCache.put(Double.NaN, Double.NaN);

        EMPTY_OBJECT.type = JsonObject.class.getName();
        EMPTY_OBJECT.isFinished = true;
        EMPTY_ARRAY.type = Object[].class.getName();
        EMPTY_ARRAY.put(ITEMS, new Object[]{});
        EMPTY_ARRAY.isFinished = true;
    }
    
    JsonParser(FastReader reader, Resolver resolver) {
        this.input = reader;
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        this.references = resolver.getReferences();
        maxParseDepth = readOptions.getMaxDepth();
        allowNanAndInfinity = readOptions.isAllowNanAndInfinity();
    }

    private JsonObject readJsonObject() throws IOException {
        boolean done = false;
        String field = null;
        JsonObject object = new JsonObject();
        int state = STATE_READ_START_OBJECT;
        final FastReader in = input;

        while (!done) {
            int c;
            switch (state) {
                case STATE_READ_START_OBJECT:
                    // c read and pushed back before STATE_READ_START_OBJECT, so 'c' always '{' here.
                    skipWhitespaceRead();
                    object.line = in.getLine();
                    object.col = in.getCol();
                    c = skipWhitespaceRead();
                    if (c == '}') {    // empty object
                        return new JsonObject();
                    }
                    in.pushback((char) c);
                    state = STATE_READ_FIELD;
                    ++curParseDepth;
                    break;

                case STATE_READ_FIELD:
                    c = skipWhitespaceRead();
                    if (c == '"') {
                        field = readString();
                        c = skipWhitespaceRead();
                        if (c != ':') {
                            error("Expected ':' between field and value, instead found '" + (char)c + "'");
                        }

                        if (field.startsWith("@") || field.startsWith(".")) {   // Expand shorthand meta keys
                            String temp = stringCache.get(field);

                            if (temp != null) {
                                field = temp;
                            }
                        }
                        state = STATE_READ_VALUE;
                    }
                    else {
                        error("Expected quote before field name");
                    }
                    break;

                case STATE_READ_VALUE:
                    if (field == null) {
                        // field is null when you have an untyped Object[], so we place
                        // the JsonArray on the @items field.
                        field = ITEMS;
                    }

                    Object value = readValue(object, false);

                    // process key-value pairing
                    switch (field) {
                        case TYPE:
                            loadType(value, object);
                            break;
                        case REF:
                            loadRef(value, object);
                            break;
                        case ID:
                            loadId(value, object);
                            break;
                        default:
                            object.put(field, value);
                            break;
                    }

                    c = skipWhitespaceRead();
                    switch(c) {
                        case -1:
                            error("EOF reached before closing '}'");
                        case ',':
                            state = STATE_READ_FIELD;       // more field pairs
                            break;
                        case '}':
                            done = true;                    // no more field pairs, object done
                            --curParseDepth;
                            break;
                        default:
                            error("Object not ended with '}', instead found '" + (char)c + "'");
                    }
                    break;
            }
        }
        return object;
    }

    Object readValue(JsonValue object, boolean top) throws IOException {
        if (curParseDepth > maxParseDepth) {
            error("Maximum parsing depth exceeded");
        }

        int c = skipWhitespaceRead();
        if (c >= '0' && c <= '9' || c == '-' || c == 'N' || c == 'I') {
            return readNumber(c);
        }
        switch (c) {
            case '"':
                String str = readString();
                return str;
            case '{':
                input.pushback('{');                 
                JsonObject jObj = readJsonObject();

                /////////////////////////////////////////////////////
                final boolean useMaps = readOptions.getReturnType() == ReturnType.JSON_VALUES;

                if (jObj.isLogicalPrimitive()) {
                    if (useMaps) {
                        jObj.isFinished = true;
                        return jObj.getPrimitiveValue();
                    }
                }

                Class<?> clazz = jObj.getJavaType() == null ? LinkedHashMap.class : jObj.getJavaType();
                JsonObject localObject = new JsonObject();
                localObject.type = clazz.getName();
                localObject.setJavaType(clazz);
                localObject.putAll(jObj);
                Object foo = resolver.createInstance(clazz, localObject);
                /////////////////////////////////////////////////////

                return jObj;
            case '[':
                Object[] array = readArray(object);
                return array;
            case ']':   // empty array
                input.pushback(']');
                return EMPTY_ARRAY;
            case 'f':
            case 'F':
                readToken("false");
                return Boolean.FALSE;
            case 'n':
            case 'N':
                readToken("null");
                return null;
            case 't':
            case 'T':
                readToken("true");
                return Boolean.TRUE;
            case -1:
                return top ? null : (JsonValue) error("EOF reached prematurely");
        }

        return error("Unknown JSON value type");
    }

    /**
     * Read a JSON array
     */
    private Object[] readArray(JsonValue object) throws IOException {
        final List<Object> array = new ArrayList<>();
        ++curParseDepth;

        while (true) {
            final Object value = readValue(object, false);
            if (value != EMPTY_ARRAY)
            {
                array.add(value);
            }
            final int c = skipWhitespaceRead();

            if (c == ']') {
                break;
            }
            else if (c != ',') {
                error("Expected ',' or ']' inside array");
            }
        }

        --curParseDepth;
        return array.toArray();
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
     * @param c int a character representing the first digit of the number that
     *          was already read.
     * @return a Number (a Long or a Double) depending on whether the number is
     *         a decimal number or integer.  This choice allows all smaller types (Float, int, short, byte)
     *         to be represented as well.
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
            }
            else if ('N' == c) {
                // [Out of RFC 4627] accept NaN/Infinity values
                readToken("nan");
                return Double.NaN;
            }
            else {
                // This is (c) case, meaning there was c = '-' at the beginning.
                // This is a number like "-2", but not "-Infinity". We let the normal code process.
                input.pushback((char) c);
                c = '-';
            }
        }

        // We are sure we have a positive or negative number, so we read char by char.
        StringBuilder number = numBuf;
        number.setLength(0);
        number.append((char)c);
        while (true) {
            c = in.read();
            if ((c >= '0' && c <= '9') || c == '-' || c == '+') {
                number.append((char)c);
            }
            else if (c == '.' || c == 'e' || c == 'E') {
                number.append((char)c);
                isFloat = true;
            }
            else if (c == -1) {
                break;
            }
            else {
                in.pushback((char) c);
                break;
            }
        }

        try {
            Number val;
            if (isFloat) {
                val = FloatingDecimal.parseDouble(number.toString());
            } else {
                val = Long.parseLong(number.toString(), 10);
            }
            Number translate = numberCache.get(val);
            return translate == null ? val : translate;
        }
        catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    private static final int STRING_START = 0;
    private static final int STRING_SLASH = 1;
    private static final int HEX_DIGITS = 2;

    /**
     * Read a JSON string
     * This method assumes the initial quote has already been read.
     *
     * @return String read from JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private String readString() throws IOException {
        final StringBuilder str = strBuf;
        final StringBuilder hex = hexBuf;
        str.setLength(0);
        int state = STRING_START;
        final FastReader in = input;

        while (true) {
            final int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }

            if (state == STRING_START) {
                if (c == '"') {
                    break;
                }
                else if (c == '\\') {
                    state = STRING_SLASH;
                }
                else {
                    str.append((char) c);
                }
            }
            else if (state == STRING_SLASH) {
                switch (c) {
                    case '\\':
                        str.append('\\');
                        break;
                    case '/':
                        str.append('/');
                        break;
                    case '"':
                        str.append('"');
                        break;
                    case '\'':
                        str.append('\'');
                        break;
                    case 'b':
                        str.append('\b');
                        break;
                    case 'f':
                        str.append('\f');
                        break;
                    case 'n':
                        str.append('\n');
                        break;
                    case 'r':
                        str.append('\r');
                        break;
                    case 't':
                        str.append('\t');
                        break;
                    case 'u':
                        hex.setLength(0);
                        state = HEX_DIGITS;
                        break;
                    default:
                        error("Invalid character escape sequence specified: " + c);
                }

                if (c != 'u') {
                    state = STRING_START;
                }
            }
            else {
                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                    hex.append((char) c);
                    if (hex.length() == 4) {
                        int value = Integer.parseInt(hex.toString(), 16);
                        str.append((char) value);
                        state = STRING_START;
                    }
                }
                else {
                    error("Expected hexadecimal digits");
                }
            }
        }

        final String s = str.toString();
        final String translate = stringCache.get(s);
        return translate == null ? s : translate;
    }

    /**
     * Read until non-whitespace character and then return it.
     * This saves extra read/pushback.
     *
     * @return int representing the next non-whitespace character in the stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private int skipWhitespaceRead() throws IOException
    {
        Reader in = input;
        int c;
        do {
            c = in.read();
        } while (c == ' ' || c == '\n' || c == '\r' || c == '\t');
        return c;
    }

    /**
     * Load the @id field listed in the JSON
     * @param value Object should be a Long, if not exception is thrown.  It is the value associated to the @id field.
     * @param object JsonObject representing the current item in the JSON being loaded.
     */
    private void loadId(Object value, JsonObject object) {
        if (!(value instanceof Long)) {
            error("Expected a number for " + ID + ", instead got: " + value);
        }
        Long id = (Long) value;
        references.put(id, object);
        object.setId(id);
    }

    /**
     * Load the @ref field listed in the JSON
     * @param value Object should be a Long, if not exception is thrown. It is the value associated to the @ref field.
     * @param object JsonValue that will be stuffed with the reference id and marked as finished.
     */
    private void loadRef(Object value, JsonValue object) {
        if (!(value instanceof Long)) {
            error("Expected a number for " + REF + ", instead got: " + value);
        }
        object.setReferenceId((Long) value);
        object.setFinished();   // "Nothing further to load, your honor."
    }

    /**
     * Load the @type field listed in the JSON
     * @param value Object should be a String, if not an exception is thrown.  It is the value associated to the @type field.
     * @param object JsonObject that will have the JavaType set on to it to indicate what the peer class should be.
     */
    private void loadType(Object value, JsonObject object) {
        if (!(value instanceof String)) {
            error("Expected a String for " + TYPE + ", instead got: " + value);
        }
        String javaType = (String) value;
        final String substitute = readOptions.getTypeNameAlias(javaType);
        if (substitute != null) {
            javaType = substitute;
        }

        // Resolve class during parsing
        Class<?> clazz = MetaUtils.classForName(javaType, readOptions.getClassLoader());
        if (clazz == null) {
            if (readOptions.isFailOnUnknownType()) {
                error("Class: " + javaType + " not defined.");
            }
            clazz = readOptions.getUnknownTypeClass();
            if (clazz == null) {
                clazz = LinkedHashMap.class;
            }
        }
        object.setJavaType(clazz);
        object.setType(clazz.getName());  // type field on JsonObject needs to go away (we have JavaType now)
    }
    
    Object error(String msg)
    {
        throw new JsonIoException(getMessage(msg));
    }

    Object error(String msg, Exception e)
    {
        throw new JsonIoException(getMessage(msg), e);
    }

    String getMessage(String msg)
    {
        return msg + "\nline: " + input.getLine()+ ", col: " + input.getCol()+ "\n" + input.getLastSnippet();
    }
}
