package com.cedarsoftware.util.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.util.io.WriterContext;
import com.cedarsoftware.util.io.Writers;

public class LongWriter extends Writers.PrimitiveTypeWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        if (context.getWriteOptions().isWriteLongsAsStrings()) {
            output.write("\"");
            output.write(o.toString());
            output.write("\"");
        } else {
            output.write(o.toString());
        }
    }
}
