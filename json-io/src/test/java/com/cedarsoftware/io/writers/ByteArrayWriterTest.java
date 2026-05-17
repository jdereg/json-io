package com.cedarsoftware.io.writers;

import java.io.StringWriter;

import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptionsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ByteArrayWriterTest {

    @Test
    void writesBase64EncodedPrimitiveForm() throws Exception {
        ByteArrayWriter writer = new ByteArrayWriter();
        StringWriter out = new StringWriter();
        JsonGenerator gen = JsonIo.createGenerator(out, new WriteOptionsBuilder().build());
        byte[] bytes = {1, 2, 3, 4};

        // Exercises the new JsonGenerator-based primitive-form path that
        // ByteArrayWriter was migrated to in 4.103.0. The deprecated
        // Writer-based override still exists as a delegate for super-chaining
        // compatibility, but it requires a non-null context to construct the
        // bridge generator and is no longer the natural test surface.
        writer.writePrimitiveForm(bytes, gen, null);
        gen.flush();

        assertThat(out.toString()).isEqualTo("\"AQIDBA==\"");
    }

    @Test
    void hasPrimitiveFormAlwaysTrue() {
        ByteArrayWriter writer = new ByteArrayWriter();
        assertThat(writer.hasPrimitiveForm(null)).isTrue();
    }
}
