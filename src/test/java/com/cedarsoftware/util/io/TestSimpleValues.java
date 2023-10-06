/*
 *
 */
package com.cedarsoftware.util.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class TestSimpleValues
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
            if (null == obj || !(obj instanceof A)) {
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
        readAllowNan = JsonReader.isAllowNanAndInfinity();
        writeAllowNan = JsonWriter.isAllowNanAndInfinity();
    }

    @AfterAll
    public static void tearDown()
    {
        JsonReader.setAllowNanAndInfinity(readAllowNan);
        JsonWriter.setAllowNanAndInfinity(writeAllowNan);
    }

    public void simpleCases()
    {
        testWriteRead(1234);
        testWriteRead(1f);
        testWriteRead(2.0);


        testWriteRead(-1234);
        testWriteRead(-1f);
        testWriteRead(-2.0);
    }

    @Test
    public void testSimpleCases() {
        JsonReader.setAllowNanAndInfinity(false);
        JsonWriter.setAllowNanAndInfinity(false);
        simpleCases();
        JsonReader.setAllowNanAndInfinity(true);
        JsonWriter.setAllowNanAndInfinity(true);
        simpleCases();

        testWriteRead(Double.POSITIVE_INFINITY);
        testWriteRead(Double.NEGATIVE_INFINITY);
        testWriteRead(Double.NaN);
    }

    private final void testWriteRead(Object testObj)
    {

        final String json = TestUtil.getJsonString(testObj);
        TestUtil.printLine("testObj = " + testObj);
        TestUtil.printLine("json = " + json);
        final Object newObj = TestUtil.readJsonObject(json);
        TestUtil.printLine("newObj = " + newObj);

        assertEquals(testObj, newObj);
    }


}
