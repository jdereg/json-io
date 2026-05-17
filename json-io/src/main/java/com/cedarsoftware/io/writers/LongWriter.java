package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.io.Writers;

/**
 * Custom writer for {@link Long}.
 * <p>
 * Honors the active {@link com.cedarsoftware.io.WriteOptions#isWriteLongsAsStrings()} switch:
 * when {@code true} the long is emitted as a quoted JSON string (avoids the loss of precision
 * downstream JavaScript clients can suffer at values beyond {@code 2^53}); when {@code false}
 * (the default) it is emitted as a bare numeric literal. Also honors {@code @IoFormat}
 * patterns when present via {@link Writers#writeWithStringFormat} /
 * {@link Writers#writeNumericWithFieldFormat}.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link com.cedarsoftware.io.JsonClassWriter}
 * API in json-io 4.103.0. The deprecated {@link Writer}-based override is retained as a thin
 * delegate so user subclasses written against the old API can chain via
 * {@code super.writePrimitiveForm(...)} unchanged.
 *
 * @author Kenny Partlow (kpartlow@gmail.com)
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class LongWriter extends Writers.PrimitiveTypeWriter {

    @Override
    public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        if (Writers.writeWithStringFormat(o, gen, context)) { return; }
        if (Writers.writeNumericWithFieldFormat(o, gen, context)) { return; }
        long value = ((Long) o).longValue();
        if (context.getWriteOptions().isWriteLongsAsStrings()) {
            // Quoted string form for JavaScript precision compatibility.
            gen.writeString(Long.toString(value));
        } else {
            gen.writeNumber(value);
        }
    }

    @Override
    @Deprecated
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(o, bridge, context);
    }
}
