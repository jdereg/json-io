package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Under {@code showTypeInfoAlways()}, TOON writes a value bare where a primitive's wrapper is declared -- a
 * {@code List<Integer>}'s elements, an {@code Integer[]}'s, a {@code Map<String, Short>}'s values, an
 * {@code Integer} field -- as JSON writes it: the declaration gives the type. It wrote {$type: Integer, value: 7}.
 * Where the declaration does not give it (an {@code Object} field or a raw list), the value keeps its type.
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
class ToonAlwaysWrapperTest {
    public static class Holder {
        public List<Integer> ints;
        public Integer[] intArray;
        public Map<String, Short> shorts;
        public Integer boxed;
        public Float f;
        public Byte b;
        public Character c;
        public Float negFloat;
        public Double negDouble;
        public List<Double> doubles;
        public Object any;
        public List<Object> objects;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.ints = new ArrayList<>(Collections.singletonList(7));
        h.intArray = new Integer[] {9};
        h.shorts = new LinkedHashMap<>();
        h.shorts.put("k", (short) 3);
        h.boxed = 5;
        h.f = 1.5f;
        h.b = (byte) 2;
        h.c = 'x';
        h.negFloat = -0.0f;
        h.negDouble = -0.0d;
        h.doubles = new ArrayList<>(java.util.Arrays.asList(-0.0d, 2.5d));
        h.any = 11;
        h.objects = new ArrayList<>(Collections.singletonList(13));
        return h;
    }

    private static final WriteOptions ALWAYS = new WriteOptionsBuilder().showTypeInfoAlways().build();

    @Test
    void declaredWrappersGoBare() {
        String toon = JsonIo.toToon(holder(), ALWAYS);
        assertTrue(toon.contains("ints:\n  $type: ArrayList\n  $items[1]: 7\n"), toon);
        assertTrue(toon.contains("$items[1]: 9\n"), toon);
        assertTrue(toon.contains("shorts:\n  $type: LinkedHashMap\n  k: 3\n"), toon);
        assertTrue(toon.contains("\nboxed: 5\n"), toon);
        assertTrue(toon.contains("\nf: 1.5\n"), toon);
        assertTrue(toon.contains("\nb: 2\n"), toon);
        assertTrue(toon.contains("\nc: x\n"), toon);
        assertFalse(toon.contains("$type: Short"), toon);
        assertFalse(toon.contains("$type: Byte"), toon);
        assertFalse(toon.contains("$type: Character"), toon);
    }

    @Test
    void undeclaredKeepTheirType() {
        String toon = JsonIo.toToon(holder(), ALWAYS);
        assertTrue(toon.contains("any:\n  $type: Integer\n  value: 11\n"), toon);
        assertTrue(toon.contains("objects:\n  $type: ArrayList\n  $items[1]:\n    - $type: Integer\n      value: 13"),
                toon);
    }

    @Test
    void readsBackAsWritten() {
        Holder h = holder();
        String toon = JsonIo.toToon(h, ALWAYS);
        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
        Map<String, Object> options = new HashMap<>();
        assertTrue(DeepEquals.deepEquals(h, back, options), () -> toon + "\n" + options.get("diff"));
        assertEquals(Integer.class, back.ints.get(0).getClass());
        assertEquals(Short.class, back.shorts.get("k").getClass());
        assertEquals(Integer.class, back.any.getClass());
    }

    /** A TOON number has no -0: where a wrapper is declared, -0.0 keeps its typed form, which keeps the sign. */
    @Test
    void negativeZeroKeepsItsSign() {
        String toon = JsonIo.toToon(holder(), ALWAYS);
        assertTrue(toon.contains("negFloat:\n  $type: Float\n  value: \"-0.0\"\n"), toon);
        assertTrue(toon.contains("negDouble:\n  $type: Double\n  value: \"-0.0\"\n"), toon);
        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
        assertEquals(Float.floatToRawIntBits(-0.0f), Float.floatToRawIntBits(back.negFloat));
        assertEquals(Double.doubleToRawLongBits(-0.0d), Double.doubleToRawLongBits(back.negDouble));
        assertEquals(Double.doubleToRawLongBits(-0.0d), Double.doubleToRawLongBits(back.doubles.get(0)));
        assertEquals(2.5d, back.doubles.get(1));
    }

    /** As JSON writes always mode. */
    @Test
    void jsonIsTheSame() {
        String json = JsonIo.toJson(holder(), ALWAYS);
        assertTrue(json.contains("\"ints\":{\"@type\":\"ArrayList\",\"@items\":[7]}"), json);
        assertTrue(json.contains("\"boxed\":5"), json);
        assertTrue(json.contains("\"any\":{\"@type\":\"Integer\",\"value\":11}"), json);
    }
}
