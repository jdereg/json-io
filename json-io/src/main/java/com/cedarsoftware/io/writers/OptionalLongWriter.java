package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.OptionalLong;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for {@link OptionalLong}.
 * <p>
 * By default writes Jackson/Gson-compatible primitive form: empty → {@code null},
 * present → the bare long value. Legacy object form is used when
 * {@link WriteOptions#isWriteOptionalAsObject()} is true, or when the framework
 * needs to attach type/id metadata.
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
public class OptionalLongWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        OptionalLong opt = (OptionalLong) obj;

        JsonWriter.writeBasicString(output, "present");
        output.write(':');
        output.write(opt.isPresent() ? "true" : "false");

        if (opt.isPresent()) {
            output.write(',');
            JsonWriter.writeBasicString(output, "value");
            output.write(':');
            output.write(Long.toString(opt.getAsLong()));
        }
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        WriteOptions wo = context.getWriteOptions();
        return wo == null || !wo.isWriteOptionalAsObject();
    }

    @Override
    public void writePrimitiveForm(Object obj, Writer output, WriterContext context) throws IOException {
        OptionalLong opt = (OptionalLong) obj;
        if (opt.isPresent()) {
            output.write(Long.toString(opt.getAsLong()));
        } else {
            output.write("null");
        }
    }
}
