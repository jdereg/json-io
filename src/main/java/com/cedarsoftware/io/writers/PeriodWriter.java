package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.time.Period;

import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.io.Writers;

public class PeriodWriter extends Writers.PrimitiveUtf8StringWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Period d = (Period) o;
        JsonWriter.writeBasicString(output, d.toString());
    }
}
