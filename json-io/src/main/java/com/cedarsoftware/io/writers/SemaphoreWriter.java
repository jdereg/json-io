package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Semaphore;

import com.cedarsoftware.io.JsonClassWriter;
import com.cedarsoftware.io.JsonWriter;
import com.cedarsoftware.io.WriterContext;

/**
 * Custom writer for Semaphore.
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
public class SemaphoreWriter implements JsonClassWriter {

    @Override
    public void write(Object obj, boolean showType, Writer output, WriterContext context) throws IOException {
        Semaphore sem = (Semaphore) obj;
        if (showType) {
            JsonWriter.writeBasicString(output, "permits");
            output.write(':');
            output.write(String.valueOf(sem.availablePermits()));
            output.write(',');
            JsonWriter.writeBasicString(output, "fair");
            output.write(':');
            output.write(sem.isFair() ? "true" : "false");
        } else {
            output.write(String.valueOf(sem.availablePermits()));
        }
    }

    @Override
    public boolean hasPrimitiveForm(WriterContext context) {
        return true;
    }

    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        Semaphore sem = (Semaphore) o;
        output.write(String.valueOf(sem.availablePermits()));
    }
}