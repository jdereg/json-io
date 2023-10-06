package com.cedarsoftware.util.io

import groovy.transform.CompileStatic

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

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
@CompileStatic
class TestMissingFieldHandler
{
    private static class CustomPoint
    {
        public long x
        // y is deleted
        //public long y
        // replaced by newY
        public long newY
    }

    private static class CustomPointWithRef
    {
        private static class Inner1
        {
        }

        private static class Inner2
        {
            public Object inner12
        }
        public Inner1 inner1
        //deleted fields
        //        public Object inner2
        //        boolean b
        //        Boolean bb
        //        byte by
        //        Byte bby
        //        char c
        //        Character cc
        //        double d
        //        double dd
        //        float f
        //        Float ff
        //        int i
        //        Integer ii
        //        long l
        //        Long ll
        //        short s
        //        Short ss
        //        public String[] aStringArray
        //        public Inner2 inner2WithNoSerializedType
        //those new fields are  only used to store the missing field callback result
        public Inner2 inner2Missing
        public long aLongMissing
        public String[] aStringArrayMissing
        public Object[] aObjectArrayMissing
        boolean bMissing
        Boolean bbMissing
        byte byMissing
        Byte bbyMissing
        char cMissing
        Character ccMissing
        double dMissing
        double ddMissing
        float fMissing
        Float ffMissing
        int iMissing
        Integer iiMissing
        long lMissing
        Long llMissing
        short sMissing
        Short ssMissing
    }

    private static final String OLD_CUSTOM_POINT = '{"@type":"com.cedarsoftware.util.io.TestMissingFieldHandler$CustomPoint","x":5,"y":7}';
    private static final String OLD_CUSTOM_POINT2 = '{"@type":"com.cedarsoftware.util.io.TestMissingFieldHandler$CustomPointWithRef","inner1":{"@id":1},"inner2":{"@type":"com.cedarsoftware.util.io.TestMissingFieldHandler$CustomPointWithRef$Inner2","inner12":{"@ref":1}},"b":true,"bb":true,"by":9,"bby":9,"c":"9","cc":"9","d":9.0,"dd":9.0,"f":9.0,"ff":9.0,"i":9,"ii":9,"l":9,"ll":9,"s":9,"ss":9,"aStringArray":["foo","bar"],"inner2WithNoSerializedType":{"inner12":null}}';

    @Test
    void testMissingHandler()
    {
        CustomPoint pt = new CustomPoint()
        pt.x = 5

        JsonReader.MissingFieldHandler missingHandler = new JsonReader.MissingFieldHandler() {
            void fieldMissing(Object object, String fieldName, Object value)
            {
                ((CustomPoint) object).newY = (long) value
            }
        }

        Map<String, Object> args = [(JsonReader.MISSING_FIELD_HANDLER): missingHandler] as Map
        CustomPoint clonePoint = JsonReader.jsonToJava(OLD_CUSTOM_POINT, args) as CustomPoint
        assertEquals(pt.x, clonePoint.x)
        assertEquals(7, clonePoint.newY)
    }

    @Test
    void testMissingHandlerWithRef()
    {
        //this is used to generate the OLD_CUSTOM_POINT2 string
        //        CustomPointWithRef pt = new CustomPointWithRef()
        //        pt.inner1 = new CustomPointWithRef.Inner1()
        //        pt.b = 9
        //        pt.bb = 9
        //        pt.by = 9
        //        pt.bby = 9
        //        pt.c = '9'
        //        pt.cc = '9'
        //        pt.d = 9
        //        pt.dd = 9
        //        pt.f = 9.0
        //        pt.ff = 9.0
        //        pt.i = 9
        //        pt.ii = 9
        //        pt.l = 9
        //        pt.ll = 9
        //        pt.s = 9
        //        pt.ss = 9
        //        pt.inner2 = new CustomPointWithRef.Inner2()
        //        pt.inner2.inner12 = pt.inner1
        //        pt.aStringArray = ["foo", "bar"]
        //        pt.inner2WithNoSerializedType = new CustomPointWithRef.Inner2()
        //        println( JsonWriter.objectToJson(pt))
        def isStringArrayOk = false
        def isInner2WithNoSerializedTypeOk = false
        JsonReader.MissingFieldHandler missingHandler = new JsonReader.MissingFieldHandler() {
            void fieldMissing(Object object, String fieldName, Object value)
            {
                switch (fieldName)
                {
                    case "inner2":
                        ((CustomPointWithRef) object).inner2Missing = (CustomPointWithRef.Inner2) value
                        break
                    case 'b':
                        ((CustomPointWithRef) object).bMissing = (boolean) value
                        break
                    case 'bb':
                        ((CustomPointWithRef) object).bbMissing = (Boolean) value
                        break
                    case 'by':
                        ((CustomPointWithRef) object).byMissing = (byte) value
                        break
                    case 'bby':
                        ((CustomPointWithRef) object).bbyMissing = (Byte) value
                        break
                    case 'c':
                        ((CustomPointWithRef) object).cMissing = (char) value
                        break
                    case 'cc':
                        ((CustomPointWithRef) object).ccMissing = (Character) value
                        break
                    case 'd':
                        ((CustomPointWithRef) object).dMissing = (double) value
                        break
                    case 'dd':
                        ((CustomPointWithRef) object).ddMissing = (Double) value
                        break
                    case 'f':
                        ((CustomPointWithRef) object).fMissing = (float) value
                        break
                    case 'ff':
                        ((CustomPointWithRef) object).ffMissing = (Float) value
                        break
                    case 'i':
                        ((CustomPointWithRef) object).iMissing = (int) value
                        break
                    case 'ii':
                        ((CustomPointWithRef) object).iiMissing = (Integer) value
                        break
                    case 'l':
                        ((CustomPointWithRef) object).lMissing = (long) value
                        break
                    case 'll':
                        ((CustomPointWithRef) object).llMissing = (Long) value
                        break
                    case 's':
                        ((CustomPointWithRef) object).sMissing = (short) value
                        break
                    case 'ss':
                        ((CustomPointWithRef) object).ssMissing = (Short) value
                        break
                    case "aStringArray":
                        isStringArrayOk = value == null
                        break
                    case "inner2WithNoSerializedType" :
                        isInner2WithNoSerializedTypeOk = value == null
                        break
                }
            }
        }
        Map<String, Object> args = [(JsonReader.MISSING_FIELD_HANDLER): missingHandler] as Map
        CustomPointWithRef clonePoint = JsonReader.jsonToJava(OLD_CUSTOM_POINT2, args) as CustomPointWithRef
        assertEquals(clonePoint.inner1, clonePoint.inner2Missing.inner12)
        assertTrue(clonePoint.bMissing)
        assertTrue(clonePoint.bbMissing)
        assertTrue(clonePoint.byMissing == 9)
        assertTrue(clonePoint.bbyMissing == 9)
        assertTrue(clonePoint.cMissing == '9')
        assertTrue(clonePoint.ccMissing == '9')
        assertTrue(clonePoint.dMissing == 9)
        assertTrue(clonePoint.ddMissing == 9)
        assertTrue(clonePoint.fMissing == 9)
        assertTrue(clonePoint.ffMissing == 9)
        assertTrue(clonePoint.iMissing == 9)
        assertTrue(clonePoint.iiMissing == 9)
        assertTrue(clonePoint.lMissing == 9)
        assertTrue(clonePoint.llMissing == 9)
        assertTrue(clonePoint.sMissing == 9)
        assertTrue(clonePoint.ssMissing == 9)
        assertTrue(isStringArrayOk)
        assertTrue(isInner2WithNoSerializedTypeOk)
    }
}
