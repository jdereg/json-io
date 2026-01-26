package com.cedarsoftware.io.writers;

import java.io.IOException;
import java.io.Writer;
import java.util.BitSet;

import com.cedarsoftware.io.Writers;
import com.cedarsoftware.io.WriterContext;

/**
 * Writer for BitSet that serializes as a binary string.
 * This produces compact JSON like: {"@type":"BitSet","value":"101010"}
 * where each character represents a bit (rightmost = bit 0).
 * Empty BitSets serialize as empty string "".
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
public class BitSetWriter extends Writers.PrimitiveTypeWriter {

    @Override
    public void writePrimitiveForm(Object o, Writer output, WriterContext context) throws IOException {
        BitSet bitSet = (BitSet) o;
        int length = bitSet.length(); // length() returns highest set bit + 1, or 0 if empty

        output.write('"');
        // Write bits from highest to lowest (MSB first, like normal binary notation)
        for (int i = length - 1; i >= 0; i--) {
            output.write(bitSet.get(i) ? '1' : '0');
        }
        output.write('"');
    }
}
