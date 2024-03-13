package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.time.Duration;

import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.io.Writers;

public class DurationWriter extends Writers.PrimitiveUtf8StringWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Duration d = (Duration) o;
        JsonWriter.writeJsonUtf8String(output, d.toString());
    }
}
