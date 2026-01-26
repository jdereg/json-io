package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;

import com.cedarsoftware.io.WriterContext;
import com.cedarsoftware.io.Writers;

/**
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
    /**
     * Writes the primitive form of a Long value.
     * <p>
     * Note: This is a callback method called by the framework where comma handling
     * is already managed by the caller. Therefore, we write directly to output rather
     * than using context.writeValue() which would add unwanted comma management.
     * </p>
     */
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        String value = o.toString();
        if (context.getWriteOptions().isWriteLongsAsStrings()) {
            // Write long as quoted string for JavaScript compatibility
            output.write('"');
            output.write(value);
            output.write('"');
        } else {
            // Write long as unquoted number
            output.write(value);
        }
    }
}
