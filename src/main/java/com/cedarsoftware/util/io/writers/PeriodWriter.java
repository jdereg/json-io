package com.cedarsoftware.util.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.time.Period;

import com.cedarsoftware.util.io.JsonIo;
import com.cedarsoftware.util.io.WriterContext;
import com.cedarsoftware.util.io.Writers;

public class PeriodWriter extends Writers.PrimitiveUtf8StringWriter {
    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Period d = (Period) o;
        JsonIo.writeBasicString(output, d.toString());
    }
}
