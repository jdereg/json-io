package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicLongArray;

import com.cedarsoftware.io.Writers;
import com.cedarsoftware.io.WriterContext;

/**
 * Writer for AtomicLongArray that serializes as an array of longs.
 * This produces JSON like: {"@type":"AtomicLongArray","value":[1,2,3]}
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
public class AtomicLongArrayWriter extends Writers.PrimitiveTypeWriter {

    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        AtomicLongArray array = (AtomicLongArray) o;
        int length = array.length();

        output.write('[');
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                output.write(',');
            }
            output.write(Long.toString(array.get(i)));
        }
        output.write(']');
    }
}
