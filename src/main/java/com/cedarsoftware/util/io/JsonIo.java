package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.util.ReturnType;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
 *         limitations under the License.*
 */
public class JsonIo {

    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        if (writeOptions.isBuilt())
        {
            writeOptions = new WriteOptions(writeOptions);
        }
        writeOptions.prettyPrint(true);

        if (readOptions.isBuilt())
        {
            readOptions = new ReadOptions(readOptions);
        }
        readOptions.returnType(ReturnType.JSON_VALUES);
        
        Object object = JsonReader.toObjects(json, readOptions, null);
        return JsonWriter.toJson(object, writeOptions);
    }

    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptions().returnType(ReturnType.JSON_VALUES),
                new WriteOptions().prettyPrint(true));
    }

    public static <T> T deepCopy(Object o) {
        return deepCopy(o, new ReadOptions(), new WriteOptions());
    }

    public static <T> T deepCopy(Object o, ReadOptions readOptions, WriteOptions writeOptions) {
        if (o == null) {
            // They asked to copy null.  The copy of null is null.
            return null;
        }
        String json = JsonWriter.toJson(o, writeOptions);
        return JsonReader.toObjects(json, readOptions, o.getClass());
    }

    /**
     * Writes out a string without special characters. Use for labels, etc. when you know you
     * will not need extra formattting for UTF-8 or tabs, quotes and newlines in the string
     *
     * @param writer Writer to which the UTF-8 string will be written to
     * @param s      String to be written in UTF-8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeBasicString(final Writer writer, String s) throws IOException {
        writer.write('\"');
        writer.write(s);
        writer.write('\"');
    }

    public static void writeJsonUtf8Char(final Writer writer, char c) throws IOException {
        writer.write('\"');
        writeChar(writer, c);
        writer.write('\"');
    }

    private static void writeChar(Writer writer, char c) throws IOException {
        if (c < ' ') {    // Anything less than ASCII space, write either in \\u00xx form, or the special \t, \n, etc. form
            switch (c) {
                case '\b':
                    writer.write("\\b");
                    break;
                case '\f':
                    writer.write("\\f");
                    break;
                case '\n':
                    writer.write("\\n");
                    break;
                case '\r':
                    writer.write("\\r");
                    break;
                case '\t':
                    writer.write("\\t");
                    break;
                default:
                    writer.write(String.format("\\u%04X", (int) c));
                    break;
            }
        } else if (c == '\\' || c == '"') {
            writer.write('\\');
            writer.write(c);
        } else {   // Anything else - write in UTF-8 form (multibyte encoded) (OutputStreamWriter is UTF-8)
            writer.write(c);
        }
    }

    /**
     * Write out special characters "\b, \f, \t, \n, \r", as such, backslash as \\
     * quote as \" and values less than an ASCII space (20hex) as "\\u00xx" format,
     * characters in the range of ASCII space to a '~' as ASCII, and anything higher in UTF-8.
     *
     * @param output Writer to which the UTF-8 string will be written to
     * @param s      String to be written in UTF-8 format on the output stream.
     * @throws IOException if an error occurs writing to the output stream.
     */
    public static void writeJsonUtf8String(final Writer output, String s) throws IOException {
        output.write('\"');
        final int len = s.length();

        for (int i = 0; i < len; i++) {
            writeChar(output, s.charAt(i));
        }
        output.write('\"');
    }
}
