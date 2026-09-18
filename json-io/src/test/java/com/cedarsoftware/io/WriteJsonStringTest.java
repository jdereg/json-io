package com.cedarsoftware.io;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One-shot JSON string escaping through the supported public entry points.
 */
class WriteJsonStringTest {
    private static String escape(String s) throws IOException {
        StringWriter out = new StringWriter();
        JsonIo.writeJsonString(out, s);
        return out.toString();
    }

    @Test
    void writesAQuotedEscapedValue() throws IOException {
        assertThat(escape("plain")).isEqualTo("\"plain\"");
        assertThat(escape("he said \"hi\"")).isEqualTo("\"he said \\\"hi\\\"\"");
        assertThat(escape("back\\slash")).isEqualTo("\"back\\\\slash\"");
        assertThat(escape("line\nbreak\ttab")).isEqualTo("\"line\\nbreak\\ttab\"");
        assertThat(escape("")).isEqualTo("\"\"");
    }

    @Test
    void nullWritesTheBareJsonLiteral() throws IOException {
        assertThat(escape(null)).isEqualTo("null");
    }

    @Test
    void theEscapedValueReadsBackUnchanged() throws IOException {
        String original = "quote \" backslash \\ newline \n tab \t unicode é  ";
        String json = "{\"v\":" + escape(original) + "}";

        java.util.Map<?, ?> back = JsonIo.toJava(json, null).asClass(java.util.Map.class);
        assertThat(back.get("v")).isEqualTo(original);
    }

    @Test
    void aNullWriterIsRefused() {
        assertThatThrownBy(() -> JsonIo.writeJsonString(null, "x"))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Output writer cannot be null");
    }

    @Test
    void aStringOverTheCapIsRefusedRatherThanTruncated() {
        String tooLong = new String(new char[50]).replace('\0', 'x');
        assertThatThrownBy(() -> JsonIo.writeJsonString(new StringWriter(), tooLong, 10))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("String too large");
    }

    @Test
    void theJsonWriterStaticsAgreeAndAreNotDeprecated() throws Exception {
        // The long-standing statics remain the same capability by another name -- they are the
        // form existing callers already have, so they must keep producing identical output.
        String value = "he said \"hi\"\nand left\\";
        StringWriter viaWriter = new StringWriter();
        JsonWriter.writeJsonUtf8String(viaWriter, value);
        assertThat(viaWriter.toString()).isEqualTo(escape(value));

        StringWriter capped = new StringWriter();
        JsonWriter.writeJsonUtf8String(capped, value, 1000);
        assertThat(capped.toString()).isEqualTo(escape(value));

        for (Class<?>[] sig : new Class<?>[][]{{Writer.class, String.class},
                {Writer.class, String.class, int.class}}) {
            assertThat(JsonWriter.class.getMethod("writeJsonUtf8String", sig)
                    .isAnnotationPresent(Deprecated.class))
                    .as("writeJsonUtf8String%s should not be deprecated -- it is the only "
                            + "public one-shot escaper besides JsonIo.writeJsonString", java.util.Arrays.toString(sig))
                    .isFalse();
        }
    }
}
