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
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        if (context.getWriteOptions().isWriteLongsAsStrings()) {
            output.write(String.format("\"%d\"", (long)o));
        } else {
            output.write(o.toString());
        }
    }
}
