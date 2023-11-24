package com.cedarsoftware.util.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.util.io.JsonUtilities;
import com.cedarsoftware.util.io.WriterContext;
import com.cedarsoftware.util.io.Writers;

public class LongWriter extends Writers.PrimitiveTypeWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        if (context.getWriteOptions().isWriteLongsAsStrings()) {
            JsonUtilities.writeJsonUtf8String(output, o.toString());
        } else {
            output.write(o.toString());
        }
    }
}
