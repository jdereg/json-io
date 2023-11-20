package com.cedarsoftware.util.io;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.util.PrintStyle;

public class JsonUtilities {

    public static String formatJson(String json, ReadOptions readOptions, WriteOptions writeOptions) {
        Object object = JsonReader.toObjects(json, readOptions.ensureUsingMaps(), null);
        return JsonWriter.toJson(object, writeOptions.ensurePrettyPrint());
    }

    public static String formatJson(String json) {
        return formatJson(json,
                new ReadOptionsBuilder().returnAsMaps().build(),
                new WriteOptionsBuilder().withPrintStyle(PrintStyle.PRETTY_PRINT).build());
    }

    public static <T> T deepCopy(Object o) {
        return deepCopy(o, new ReadOptionsBuilder().build(), new WriteOptionsBuilder().build());
    }

    public static <T> T deepCopy(Object o, ReadOptions readOptions, WriteOptions writeOptions) {
        String json = JsonWriter.toJson(o, writeOptions);
        return JsonReader.toObjects(json, readOptions, null);
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
            char c = s.charAt(i);

            if (c < ' ') {    // Anything less than ASCII space, write either in \\u00xx form, or the special \t, \n, etc. form
                switch (c) {
                    case '\b':
                        output.write("\\b");
                        break;
                    case '\f':
                        output.write("\\f");
                        break;
                    case '\n':
                        output.write("\\n");
                        break;
                    case '\r':
                        output.write("\\r");
                        break;
                    case '\t':
                        output.write("\\t");
                        break;
                    default:
                        output.write(String.format("\\u%04X", (int) c));
                        break;
                }
            } else if (c == '\\' || c == '"') {
                output.write('\\');
                output.write(c);
            } else {   // Anything else - write in UTF-8 form (multibyte encoded) (OutputStreamWriter is UTF-8)
                output.write(c);
            }
        }
        output.write('\"');
    }
}
