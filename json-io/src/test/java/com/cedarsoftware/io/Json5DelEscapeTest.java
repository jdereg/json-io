package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON5 writes a String holding a double quote (and no single quote) in single quotes, escaping its control chars
 * and DEL (U+007F) as the double-quoted form does. DEL indexed a table of the 32 control chars, so writing such a
 * String threw ArrayIndexOutOfBoundsException ("Index 127 out of bounds for length 32"): every graph holding one
 * failed to write as JSON5.
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
class Json5DelEscapeTest {
    public static class Holder {
        public String text;
        public List<String> list;
    }

    private static final String DEL = String.valueOf((char) 0x7F);

    @Test
    void delInASingleQuotedString() {
        WriteOptions json5 = new WriteOptionsBuilder().json5().build();
        Holder h = new Holder();
        h.text = "say \"hi\"" + DEL;
        h.list = new ArrayList<>(Arrays.asList("a\"" + DEL + "b", DEL + "\u0001\""));
        String json = JsonIo.toJson(h, json5);
        assertTrue(json.contains("'say \"hi\"\\u007f'"), json);
        assertTrue(json.contains("'a\"\\u007fb'"), json);
        assertTrue(json.contains("'\\u007f\\u0001\"'"), json);
        Holder back = JsonIo.toJava(json, null).asClass(Holder.class);
        assertEquals(h.text, back.text);
        assertEquals(h.list, back.list);
        assertEquals("'x\"" + "\\u007f'", JsonIo.toJson("x\"" + DEL, new WriteOptionsBuilder().json5().build()));
    }

    /** Double-quoted, as before. */
    @Test
    void delInADoubleQuotedString() {
        Holder h = new Holder();
        h.text = "a" + DEL;
        String json = JsonIo.toJson(h, new WriteOptionsBuilder().json5().build());
        assertTrue(json.contains("\"a\\u007f\""), json);
        assertEquals(h.text, JsonIo.toJava(json, null).asClass(Holder.class).text);
        assertTrue(JsonIo.toJson(h, null).contains("\"a\\u007f\""));
    }
}
