package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A field declared Number -- or a type variable bounded by Number, read raw -- takes number text as java-util's
 * Converter reads it: whole text a Long (a BigInteger beyond a long), other number text a BigDecimal. There was no
 * String to Number conversion: "Unsupported conversion, source type [String (5)] target type 'Number'".
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
class NumberFieldTest {

    public static class Amounts {
        public Number whole;
        public Number big;
        public Number decimal;
        public Number number;
    }

    public static class Box<T extends Number> {
        public T value;
        public List<T> list;
    }

    @Test
    void aNumberFieldTakesNumberText() {
        Amounts a = JsonIo.toJava("{\"whole\":\"5\",\"big\":\"99999999999999999999\",\"decimal\":\"5.5\",\"number\":7}",
                null).asClass(Amounts.class);
        assertEquals(5L, a.whole);
        assertEquals(new BigInteger("99999999999999999999"), a.big);
        assertEquals(new BigDecimal("5.5"), a.decimal);
        assertEquals(7L, a.number);                         // a JSON number, as before
    }

    /** A raw Box: T is its bound, Number, and its text takes the same reading. */
    @Test
    void aRawTypeVariableBoundedByNumberTakesNumberText() {
        Box<?> box = JsonIo.toJava("{\"value\":\"5\",\"list\":[\"1\",\"2.5\"]}", null).asClass(Box.class);
        assertEquals(5L, box.value);
        assertEquals(Arrays.asList(1L, new BigDecimal("2.5")), box.list);
    }
}
