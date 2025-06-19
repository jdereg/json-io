package com.cedarsoftware.io.writers;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ByteArrayWriterTest {

    @Test
    void writesBase64EncodedPrimitiveForm() throws Exception {
        ByteArrayWriter writer = new ByteArrayWriter();
        StringWriter out = new StringWriter();
        byte[] bytes = {1, 2, 3, 4};

        writer.writePrimitiveForm(bytes, out, null);

        assertThat(out.toString()).isEqualTo("\"AQIDBA==\"");
    }

    @Test
    void hasPrimitiveFormAlwaysTrue() {
        ByteArrayWriter writer = new ByteArrayWriter();
        assertThat(writer.hasPrimitiveForm(null)).isTrue();
    }
}
