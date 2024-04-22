/*
 *
 */
package com.cedarsoftware.io;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
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
public class SimpleValuesTest
{
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testSimpleCases(boolean allowNanAndInfinity) {
        testWriteRead(1234, allowNanAndInfinity);
        testWriteRead(1f, allowNanAndInfinity);
        testWriteRead(2.0, allowNanAndInfinity);

        testWriteRead(-1234, allowNanAndInfinity);
        testWriteRead(-1f, allowNanAndInfinity);
        testWriteRead(-2.0, allowNanAndInfinity);

    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void testNanAndInfinity_whenNanAndInfinityNotAllowed_serializesAsNull(Double d) {
        final String json = TestUtil.toJson(d, new WriteOptionsBuilder().allowNanAndInfinity(false).build());
        assertThat(json).contains("null");
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void testNanAndInfinity_whenAllowingNanAndInfinity_serializeRoundTrip(Double d) {
        WriteOptions writeOptions = new WriteOptionsBuilder().allowNanAndInfinity(true).build();
        final String json = TestUtil.toJson(d, writeOptions);
        ReadOptions readOptions = new ReadOptionsBuilder().allowNanAndInfinity(true).build();
        final Double newObj = TestUtil.toObjects(json, readOptions, Double.class);

        assertEquals(d, newObj);
    }

    private final void testWriteRead(Object testObj, boolean allowNanAndInfinity)
    {

        WriteOptions writeOptions = new WriteOptionsBuilder().allowNanAndInfinity(allowNanAndInfinity).build();
        final String json = TestUtil.toJson(testObj, writeOptions);
        TestUtil.printLine("testObj = " + testObj);
        TestUtil.printLine("json = " + json);
        ReadOptions readOptions = new ReadOptionsBuilder().allowNanAndInfinity(allowNanAndInfinity).build();
        final Object newObj = TestUtil.toObjects(json, readOptions, null);
        TestUtil.printLine("newObj = " + newObj);

        assertEquals(testObj, newObj);
    }
}
