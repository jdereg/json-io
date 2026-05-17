package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.locks.ReentrantLock;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonGenerator;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for ReentrantLock that outputs the fairness setting
 * instead of the internal sync field (which cannot be deserialized).
 * <p>
 * Output format: {"@type":"java.util.concurrent.locks.ReentrantLock","fair":true/false}
 * <p>
 * Note: Lock state (held count, owner thread) is NOT preserved - only the
 * fairness policy is serialized.
 *
 * <p>Migrated to the {@link JsonGenerator}-based {@link JsonClassWriter} API in
 * json-io 4.103.0. The deprecated {@link Writer}-based overrides are retained
 * as thin delegates so user subclasses written against the old API can chain
 * via {@code super.write*()} unchanged.
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
public class ReentrantLockWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
        ReentrantLock lock = (ReentrantLock) obj;
        if (showType) {
            gen.writeFieldName("fair");
            gen.writeBoolean(lock.isFair());
        } else {
            // Effectively unreachable: framework folds (referenced || originalShowType) into
            // the showType param. Preserved for parity with the pre-migration behaviour.
            gen.writeRaw(lock.isFair() ? "true" : "false");
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
        return true;
    }

    @Override
    public void writePrimitiveForm(Object o, JsonGenerator gen, WriterContext context) throws IOException {
        ReentrantLock lock = (ReentrantLock) o;
        gen.writeBoolean(lock.isFair());
    }

    @Override
    @Deprecated
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        JsonGenerator bridge = JsonGenerator.deprecatedWriterBridge_atValueSlot(
                output, context.getWriteOptions());
        writePrimitiveForm(o, bridge, context);
    }
}
