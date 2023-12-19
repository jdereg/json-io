package com.cedarsoftware.util.io.writers;

import com.cedarsoftware.util.io.WriterContext;

import java.io.IOException;
import java.io.Writer;
import java.util.Base64;

import static com.cedarsoftware.util.io.JsonWriter.JsonClassWriter;
import static com.cedarsoftware.util.io.JsonWriter.writeBasicString;

public class ByteArrayWriter implements JsonClassWriter {

    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        final byte[] bytes = (byte[]) o;
        writeBasicString(output, Base64.getEncoder().encodeToString(bytes));
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        return true;
    }
}
