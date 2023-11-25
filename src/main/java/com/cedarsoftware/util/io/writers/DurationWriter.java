package com.cedarsoftware.util.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.time.Duration;

import com.cedarsoftware.util.io.JsonIo;
import com.cedarsoftware.util.io.WriterContext;
import com.cedarsoftware.util.io.Writers;

public class DurationWriter extends Writers.PrimitiveUtf8StringWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Duration d = (Duration) o;
        JsonIo.writeJsonUtf8String(output, d.toString());
    }
}
