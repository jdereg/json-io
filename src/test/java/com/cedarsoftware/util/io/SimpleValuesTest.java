/*
 *
 */
package com.cedarsoftware.util.io;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
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
public class SimpleValuesTest
{
    private static ReadOptions readOptions = new ReadOptions();
    
    public class A {
        private final Double doubleField;
        private final Float floatField;
        private final int intField;

        public A(Double doubleField, Float floatField, int intField) {
            this.doubleField = doubleField;
            this.floatField = floatField;
            this.intField = intField;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof A)) {
                return false;
            }
            final A a = (A) obj;
            if (a.doubleField.equals(doubleField)) {
                if (a.floatField.equals(floatField)) {
                    if (a.intField == intField) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * @return the doubleField
         */
        public Double getDoubleField() {
            return doubleField;
        }

        /**
         * @return the floatField
         */
        public Float getFloatField() {
            return floatField;
        }

        /**
         * @return the intField
         */
        public int getIntField() {
            return intField;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + ": double=" + doubleField + " ; float=" + floatField + " ; int="
                    + intField;
        }
    }

    static boolean readAllowNan;
    static boolean writeAllowNan;

    @BeforeAll
    public static void init() {
        readAllowNan = readOptions.isAllowNanAndInfinity();
    }

    @AfterAll
    public static void tearDown()
    {
        readOptions.allowNanAndInfinity(readAllowNan);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testSimpleCases(boolean allowNanAndInfinity) {
        readOptions.allowNanAndInfinity(allowNanAndInfinity);

        testWriteRead(1234);
        testWriteRead(1f);
        testWriteRead(2.0);


        testWriteRead(-1234);
        testWriteRead(-1f);
        testWriteRead(-2.0);

    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void testNanAndInfinity_whenNanAndInfinityNotAllowed_serializesAsNull(Double d) {
        readOptions.allowNanAndInfinity(true);
        TestUtil.printLine("testObj = " + d);
        final String json = TestUtil.toJson(d, new WriteOptions().allowNanAndInfinity(false).build());

        assertThat(json).contains("null");

    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void testNanAndInfinity_whenAllowingNanAndInfinity_serializeRoundTrip(Double d) {
        readOptions.allowNanAndInfinity(true);
        TestUtil.printLine("testObj = " + d);
        final String json = TestUtil.toJson(d, new WriteOptions().allowNanAndInfinity(true).build());
        final Double newObj = TestUtil.toObjects(json, readOptions, Double.class);
        TestUtil.printLine("newObj = " + newObj);

        assertEquals(d, newObj);
    }

    private final void testWriteRead(Object testObj)
    {

        final String json = TestUtil.toJson(testObj);
        TestUtil.printLine("testObj = " + testObj);
        TestUtil.printLine("json = " + json);
        final Object newObj = TestUtil.toObjects(json, readOptions, null);
        TestUtil.printLine("newObj = " + newObj);

        assertEquals(testObj, newObj);
    }
}
