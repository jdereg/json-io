package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.OptionalInt;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@link OptionalInt}.
 * <p>
 * By default writes Jackson/Gson-compatible primitive form: empty → {@code null},
 * present → the bare int value. Legacy object form ({@code {"present":X,"value":Y}})
 * is used when {@link WriteOptions#isWriteOptionalAsObject()} is true, or when the
 * framework needs to attach type/id metadata.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based overrides are retained as
 * thin delegates so user subclasses written against the old API can chain via
 * {@code super.write*()} unchanged.
 *
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
public class OptionalIntWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        OptionalInt opt = (OptionalInt) obj;
        gen.writeFieldName("present");
        gen.writeBoolean(opt.isPresent());
        if (opt.isPresent()) {
            gen.writeFieldName("value");
            gen.writeNumber(opt.getAsInt());
        }
    }

    @Override
    @Deprecated
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_insideObjectBody(
                output, context.getWriteOptions());
        write(obj, showType, bridge, context);
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        WriteOptions wo = context.getWriteOptions();
        return wo == null || !wo.isWriteOptionalAsObject();
    }

    @Override
    public void writePrimitiveForm(Object obj, JsonGenerator gen, WriterContext context) throws IOException {
        OptionalInt opt = (OptionalInt) obj;
        if (opt.isPresent()) {
            gen.writeNumber(opt.getAsInt());
        } else {
            gen.writeNull();
        }
    }

    @Override
    @Deprecated
    public void writePrimitiveForm(Object obj, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(obj, bridge, context);
    }
}
