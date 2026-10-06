package com.cedarsoftware.io;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A double or float is written as the decimal Double.toString / Float.toString print from JDK 19 on, on every JDK.
 * JDK 8 through 18 printed many doubles differently -- 1.0E23 as 9.999999999999999E22, 2.0E23 as
 * 1.9999999999999998E23, 8.41E21 as 8.409999999999999E21, 2^60 as 1.15292150460684698E18 -- and 19 floats longer
 * (9.9E-44 as 1.0E-43), so the same object wrote different JSON on JDK 8 and JDK 21. Every path that writes a number
 * is covered: a primitive field, a boxed field, a List and a Map element, double[] and float[], the generator API,
 * TOON, and the tokenizer's text for a number it read. On JDK 19+ the old code passes too: it fails on JDK 8.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class FloatingPointTextTest {

    private static final double[] DOUBLES = {1e23, 2e23, 8.41E21, 0x1p60, 0.1};
    private static final String DOUBLE_TEXT = "1.0E23,2.0E23,8.41E21,1.152921504606847E18,0.1";
    private static final float TINY = 9.9E-44f;                 // JDK 8: 1.0E-43

    private final WriteOptions plain = new WriteOptionsBuilder().showTypeInfoNever().build();

    public static class Holder {
        public double d = 1e23;
        public float f = TINY;
        public Double boxed = 2e23;
        public Object any = 8.41E21;
    }

    @Test
    void primitiveBoxedAndObjectFields() {
        assertEquals("{\"d\":1.0E23,\"f\":9.9E-44,\"boxed\":2.0E23,\"any\":8.41E21}",
                JsonIo.toJson(new Holder(), plain));
    }

    @Test
    void listMapAndArrayElements() {
        List<Object> list = new ArrayList<>();
        for (double d : DOUBLES) {
            list.add(d);
        }
        list.add(TINY);
        assertEquals("[" + DOUBLE_TEXT + ",9.9E-44]", JsonIo.toJson(list, plain));
        assertEquals("[" + DOUBLE_TEXT + "]", JsonIo.toJson(DOUBLES, plain));
        assertEquals("[9.9E-44,0.1]", JsonIo.toJson(new float[] {TINY, 0.1f}, plain));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1e23);
        map.put("b", TINY);
        assertEquals("{\"a\":1.0E23,\"b\":9.9E-44}", JsonIo.toJson(map, plain));
    }

    @Test
    void theGeneratorApi() throws Exception {
        StringWriter out = new StringWriter();
        JsonGenerator gen = JsonIo.createGenerator(out, plain);
        gen.writeStartArray();
        for (double d : DOUBLES) {
            gen.writeNumber(d);
        }
        gen.writeNumber(TINY);
        gen.writeEndArray();
        gen.flush();
        assertEquals("[" + DOUBLE_TEXT + ",9.9E-44]", out.toString());
    }

    /** TOON writes numbers without an exponent: the decimal the double prints, in full. */
    @Test
    void toon() {
        String toon = JsonIo.toToon(Arrays.asList(1e23, 2e23, 8.41E21, TINY), plain);
        assertTrue(toon.contains("100000000000000000000000,200000000000000000000000,8410000000000000000000,"
                + "0.000000000000000000000000000000000000000000099"), toon);
    }

    /** The text of a number the tokenizer read is the canonical one: the JDK 19 decimal, on every JDK. */
    @Test
    void theTokenizersText() throws Exception {
        try (JsonTokenizer t = JsonIo.createTokenizer("[9.999999999999999E22, 1.9999999999999998E23]")) {
            t.nextToken();
            t.nextToken();
            assertEquals("1.0E23", t.getText());
            t.nextToken();
            assertEquals("2.0E23", t.getText());
        }
    }
}
