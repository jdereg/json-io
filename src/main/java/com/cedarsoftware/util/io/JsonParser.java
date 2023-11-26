package com.cedarsoftware.util.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ReturnType;

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
    private static final int STATE_READ_POST_VALUE = 3;
    private static final Map<String, String> stringCache = new HashMap<>();
    private static final int DEFAULT_MAX_PARSE_DEPTH = 1000;
    private final FastReader input;
    private final StringBuilder strBuf = new StringBuilder(256);
    private final StringBuilder hexBuf = new StringBuilder();
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0;

    private final ReadOptions readOptions;

    private final ReferenceTracker references;

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

        EMPTY_OBJECT.type = JsonObject.class.getName();
        EMPTY_OBJECT.isFinished = true;
        EMPTY_ARRAY.type = Object[].class.getName();
        EMPTY_ARRAY.put(ITEMS, new Object[]{});
        EMPTY_ARRAY.isFinished = true;
    }
    
    JsonParser(FastReader reader, ReadOptions readOptions, ReferenceTracker references) {
        this.input = reader;
        this.readOptions = readOptions;
        this.references = references;
    }

    private Object readJsonObject() throws IOException
    {
        boolean done = false;
        String field = null;
        JsonObject object = new JsonObject();
        int state = STATE_READ_START_OBJECT;
        final FastReader in = input;

        while (!done)
        {
            int c;
            switch (state)
            {
                case STATE_READ_START_OBJECT:
                    // c read and pushed back before STATE_READ_START_OBJECT, so 'c' always '{' here.
                    skipWhitespaceRead();
                    object.line = in.getLine();
                    object.col = in.getCol();
                    c = skipWhitespaceRead();
                    if (c == '}')
                    {    // empty object
                        return new JsonObject();
                    }
                    in.pushback((char)c);
                    state = STATE_READ_FIELD;
                    ++curParseDepth;
                    break;

                case STATE_READ_FIELD:
                    c = skipWhitespaceRead();
                    if (c == '"')
                    {
                        field = readString();
                        c = skipWhitespaceRead();
                        if (c != ':')
                        {
                            error("Expected ':' between string field and value");
                        }

                        if (field.startsWith("@"))
                        {   // Expand shorthand meta keys
                            String temp = stringCache.get(field);

                            if (temp != null) {
                                field = temp;
                            }
                        }
                        state = STATE_READ_VALUE;
                    }
                    else
                    {
                        error("Expected quote");
                    }
                    break;

                case STATE_READ_VALUE:
                    if (field == null)
                    {	// field is null when you have an untyped Object[], so we place
                        // the JsonArray on the @items field.
                        field = ITEMS;
                    }

                    Object value = readValue(object, false);
                    if (TYPE.equals(field))
                    {
                        final String substitute = readOptions.getTypeNameAlias(value.toString());
                        if (substitute != null)
                        {
                            value = substitute;
                        }
                    }
                    object.put(field, value);

                    // If object is referenced (has @id), then add it to the ReferenceTracker
                    if (ID.equals(field))
                    {
                        this.references.put((Long) value, object);
                    }
                    state = STATE_READ_POST_VALUE;
                    break;

                case STATE_READ_POST_VALUE:
                    c = skipWhitespaceRead();
                    if (c == -1)
                    {
                        error("EOF reached before closing '}'");
                    }
                    if (c == '}')
                    {
                        done = true;
                        --curParseDepth;
                    }
                    else if (c == ',')
                    {
                        state = STATE_READ_FIELD;
                    }
                    else
                    {
                        error("Object not ended with '}'");
                    }
                    break;
            }
        }

        final boolean useMaps = readOptions.getReturnType() == ReturnType.JSON_VALUES;

        if (useMaps && object.isLogicalPrimitive())
        {
            return object.getPrimitiveValue();
        }

        return object;
    }

    Object readValue(JsonObject object, boolean top) throws IOException
    {
        final int maxParseDepth = readOptions.getMaxDepth();
        if (curParseDepth > maxParseDepth) {
            return error("Maximum parsing depth exceeded");
        }

        int c = skipWhitespaceRead();
        if (c == '"')
        {
            return readString();
        }
        else
        {
            boolean isNumber = (c >= '0' && c <= '9' || c == '-' || c == 'N' || c == 'I');
            if (isNumber) {
                return readNumber(c);
            }
        }
        switch(c)
        {
            case '{':
                input.pushback('{');
                return readJsonObject();
            case '[':
                return readArray(object);
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
                return top ? null : error("EOF reached prematurely");
        }

        return error("Unknown JSON value type");
    }

    /**
     * Read a JSON array
     */
    private Object[] readArray(JsonObject object) throws IOException
    {
        final List<Object> array = new ArrayList<>();
        ++curParseDepth;

        while (true)
        {
            final Object o = readValue(object, false);
            if (o != EMPTY_ARRAY)
            {
                array.add(o);
            }
            final int c = skipWhitespaceRead();

            if (c == ']')
            {
                break;
            }
            else if (c != ',')
            {
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
    private void readToken(String token) throws IOException
    {
        final int len = token.length();

        for (int i = 1; i < len; i++)
        {
            int c = input.read();
            if (c == -1)
            {
                error("EOF reached while reading token: " + token);
            }
            c = Character.toLowerCase((char) c);
            int loTokenChar = token.charAt(i);

            if (loTokenChar != c)
            {
                error("Expected token: " + token);
            }
        }
    }

    /**
     * Read a JSON number
     *
     * @param c int a character representing the first digit of the number that
     *          was already read.
     * @return a Number (a Long or a Double) depending on whether the number is
     *         a decimal number or integer.  This choice allows all smaller types (Float, int, short, byte)
     *         to be represented as well.
     * @throws IOException for stream errors or parsing errors.
     */
    private Number readNumber(int c) throws IOException
    {
        final FastReader in = input;
        boolean isFloat = false;

        if (readOptions.isAllowNanAndInfinity() && (c == '-' || c == 'N' || c == 'I') ) {
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
                return (isNeg) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            } else if ('N' == c) {
                // [Out of RFC 4627] accept NaN/Infinity values
                readToken("nan");
                return Double.NaN;
            } else {
                // This is (c) case, meaning there was c = '-' at the beginning.
                // This is a number like "-2", but not "-Infinity". We let the normal code process.
                input.pushback((char)c);
                c = '-';
            }
        }

        // We are sure we have a positive or negative number, so we read char by char.
        final StringBuilder number = numBuf;
        number.setLength(0);
        number.appendCodePoint(c);
        while (true)
        {
            c = in.read();
            if ((c >= '0' && c <= '9') || c == '-' || c == '+')
            {
                number.appendCodePoint(c);
            }
            else if (c == '.' || c == 'e' || c == 'E')
            {
                number.appendCodePoint(c);
                isFloat = true;
            }
            else if (c == -1)
            {
                break;
            }
            else
            {
                in.pushback((char)c);
                break;
            }
        }

        try
        {
            if (isFloat)
            {   // Floating point number needed
                return Double.parseDouble(number.toString());
            }
            else
            {
                return Long.parseLong(number.toString(), 10);
            }
        }
        catch (Exception e)
        {
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
    private String readString() throws IOException
    {
        final StringBuilder str = strBuf;
        final StringBuilder hex = hexBuf;
        str.setLength(0);
        int state = STRING_START;
        final FastReader in = input;

        while (true)
        {
            final int c = in.read();
            if (c == -1)
            {
                error("EOF reached while reading JSON string");
            }

            if (state == STRING_START)
            {
                if (c == '"')
                {
                    break;
                }
                else if (c == '\\')
                {
                    state = STRING_SLASH;
                }
                else
                {
                    str.append((char)c);
                }
            }
            else if (state == STRING_SLASH)
            {
                switch(c)
                {
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

                if (c != 'u')
                {
                    state = STRING_START;
                }
            }
            else
            {
                boolean isHexaDecimal = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');

                if (isHexaDecimal)
                {
                    hex.append((char) c);
                    if (hex.length() == 4)
                    {
                        int value = Integer.parseInt(hex.toString(), 16);
                        str.append((char)value);
                        state = STRING_START;
                    }
                }
                else
                {
                    error("Expected hexadecimal digits");
                }
            }
        }

        final String s = str.toString();
        final String translate =  stringCache.get(s);
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
        FastReader in = input;
        int c;
        do
        {
            c = in.read();
        } while (c == ' ' || c == '\n' || c == '\r' || c == '\t');
        return c;
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
