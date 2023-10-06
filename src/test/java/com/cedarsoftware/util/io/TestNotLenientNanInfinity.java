package com.cedarsoftware.util.io;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
public class TestNotLenientNanInfinity
{
    @BeforeAll
    public static void init() {
        JsonReader.setAllowNanAndInfinity(false);
        JsonWriter.setAllowNanAndInfinity(false);
    }
    
    public class A
    {
        private final Double doubleField;
        private final Float floatField;
        
        public A(Double doubleField, Float floatField) {
            this.doubleField = doubleField;
            this.floatField = floatField;
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
    }

    @Test
    public void testFloatDoubleNaNInf()
    {
        // Test NaN, +/-Infinity
        testFloatDouble(Float.NaN, Double.NaN);
        testFloatDouble(Float.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        testFloatDouble(Float.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        
        // Mixed.
        testFloatDouble(Float.NaN, Double.POSITIVE_INFINITY);
        testFloatDouble(Float.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        testFloatDouble(Float.NEGATIVE_INFINITY, Double.NaN);
        
    }

    private final void testFloatDouble(float float1, double double1)
    {
        A a = new A(double1, float1);
        
        String json = TestUtil.getJsonString(a);
        TestUtil.printLine("a = " + a);
        TestUtil.printLine("json = " + json);
        A newA = (A) TestUtil.readJsonObject(json);
        TestUtil.printLine("newA = " + newA);
        
        Double newDoubleField = newA.getDoubleField();
        Float newFloatField = newA.getFloatField();
        assertNull(newDoubleField);
        assertNull(newFloatField);
    }
    
    
}
