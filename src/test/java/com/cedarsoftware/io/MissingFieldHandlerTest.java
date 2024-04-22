package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class MissingFieldHandlerTest
{
    @Test
    public void testMissingHandler()
    {
        CustomPoint pt = new CustomPoint();
        pt.x = 5;
        final Boolean[] madeItHere = new Boolean[]{false};

        JsonReader.MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            ((CustomPoint) object).newY = (long) value;
            madeItHere[0] = true;
        };
        assert !madeItHere[0];
        CustomPoint clonePoint = TestUtil.toObjects(OLD_CUSTOM_POINT, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build(), null);
        assert madeItHere[0];

        assertEquals(pt.x, clonePoint.x);
        assertEquals(7, clonePoint.newY);
    }

    @Test
    public void testMissingHandlerWithRef()
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
        //        TestUtil.printLine( TestUtil.toJson(pt))
        boolean isStringArrayOk[] = new boolean[] {false};
        boolean isInner2WithNoSerializedTypeOk[] = new boolean[]{false};

        JsonReader.MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            switch (fieldName)
            {
                case "inner2":
                    ((CustomPointWithRef) object).inner2Missing = (CustomPointWithRef.Inner2) value;
                    break;
                case "b":
                    ((CustomPointWithRef) object).bMissing = (boolean) value;
                    break;
                case "bb":
                    ((CustomPointWithRef) object).bbMissing = (Boolean) value;
                    break;
                case "by":
                    Long x1 = (Long) value;
                    ((CustomPointWithRef) object).byMissing = x1.byteValue();
                    break;
                case "bby":
                    Long x2 = (Long) value;
                    ((CustomPointWithRef) object).bbyMissing = x2.byteValue();
                    break;
                case "c":
                    Character c1 = Character.valueOf(((String)value).charAt(0));
                    ((CustomPointWithRef) object).cMissing = c1;
                    break;
                case "cc":
                    Character c2 = Character.valueOf(((String)value).charAt(0));
                    ((CustomPointWithRef) object).ccMissing = c2;
                    break;
                case "d":
                    ((CustomPointWithRef) object).dMissing = (double) value;
                    break;
                case "dd":
                    ((CustomPointWithRef) object).ddMissing = (Double) value;
                    break;
                case "f":
                    Double f1 = (Double) value;
                    ((CustomPointWithRef) object).fMissing = f1.floatValue();
                    break;
                case "ff":
                    Double f2 = (Double) value;
                    ((CustomPointWithRef) object).ffMissing = f2.floatValue();
                    break;
                case "i":
                    Long i1 = (Long) value;
                    ((CustomPointWithRef) object).iMissing = i1.intValue();
                    break;
                case "ii":
                    Long i2 = (Long) value;
                    ((CustomPointWithRef) object).iiMissing = i2.intValue();
                    break;
                case "l":
                    ((CustomPointWithRef) object).lMissing = (long) value;
                    break;
                case "ll":
                    ((CustomPointWithRef) object).llMissing = (Long) value;
                    break;
                case "s":
                    Long s1 = (Long) value;
                    ((CustomPointWithRef) object).sMissing = s1.shortValue();
                    break;
                case "ss":
                    Long s2 = (Long) value;
                    ((CustomPointWithRef) object).ssMissing = s2.shortValue();
                    break;
                case "aStringArray":
                    isStringArrayOk[0] = value == null;
                    break;
                case "inner2WithNoSerializedType" :
                    isInner2WithNoSerializedTypeOk[0] = value == null;
                    break;
            }
        };
        CustomPointWithRef clonePoint = TestUtil.toObjects(OLD_CUSTOM_POINT2, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build(), null);
        assertEquals(clonePoint.inner1, clonePoint.inner2Missing.inner12);
        assertTrue(clonePoint.getbMissing());
        assertTrue(clonePoint.getBbMissing());
        assertEquals(9, clonePoint.getByMissing());
        assertEquals(9, (byte) clonePoint.getBbyMissing());
        assertEquals('9', clonePoint.getcMissing());
        assertEquals('9', (char) clonePoint.getCcMissing());
        assertEquals(9, clonePoint.getdMissing());
        assertEquals(9, clonePoint.getDdMissing());
        assertEquals(9, clonePoint.getfMissing());
        assertEquals(9, (float) clonePoint.getFfMissing());
        assertEquals(9, clonePoint.getiMissing());
        assertEquals(9, (int) clonePoint.getIiMissing());
        assertEquals(9, clonePoint.getlMissing());
        assertEquals(9, (long) clonePoint.getLlMissing());
        assertEquals(9, clonePoint.getsMissing());
        assertEquals(9, (short) clonePoint.getSsMissing());
        assertTrue(isStringArrayOk[0]);
        assertTrue(isInner2WithNoSerializedTypeOk[0]);
    }

    private static final String OLD_CUSTOM_POINT = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"y\":7}";
    private static final String OLD_CUSTOM_POINT2 = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPointWithRef\",\"inner1\":{\"@id\":1},\"inner2\":{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPointWithRef$Inner2\",\"inner12\":{\"@ref\":1}},\"b\":true,\"bb\":true,\"by\":9,\"bby\":9,\"c\":\"9\",\"cc\":\"9\",\"d\":9.0,\"dd\":9.0,\"f\":9.0,\"ff\":9.0,\"i\":9,\"ii\":9,\"l\":9,\"ll\":9,\"s\":9,\"ss\":9,\"aStringArray\":[\"foo\",\"bar\"],\"inner2WithNoSerializedType\":{\"inner12\":null}}";

    private static class CustomPoint
    {
        public long x;
        public long newY;
    }

    private static class CustomPointWithRef
    {
        public boolean getbMissing()
        {
            return bMissing;
        }

        public boolean isbMissing()
        {
            return bMissing;
        }

        public void setbMissing(boolean bMissing)
        {
            this.bMissing = bMissing;
        }

        public Boolean getBbMissing()
        {
            return bbMissing;
        }

        public void setBbMissing(Boolean bbMissing)
        {
            this.bbMissing = bbMissing;
        }

        public byte getByMissing()
        {
            return byMissing;
        }

        public void setByMissing(byte byMissing)
        {
            this.byMissing = byMissing;
        }

        public Byte getBbyMissing()
        {
            return bbyMissing;
        }

        public void setBbyMissing(Byte bbyMissing)
        {
            this.bbyMissing = bbyMissing;
        }

        public char getcMissing()
        {
            return cMissing;
        }

        public void setcMissing(char cMissing)
        {
            this.cMissing = cMissing;
        }

        public Character getCcMissing()
        {
            return ccMissing;
        }

        public void setCcMissing(Character ccMissing)
        {
            this.ccMissing = ccMissing;
        }

        public double getdMissing()
        {
            return dMissing;
        }

        public void setdMissing(double dMissing)
        {
            this.dMissing = dMissing;
        }

        public double getDdMissing()
        {
            return ddMissing;
        }

        public void setDdMissing(double ddMissing)
        {
            this.ddMissing = ddMissing;
        }

        public float getfMissing()
        {
            return fMissing;
        }

        public void setfMissing(float fMissing)
        {
            this.fMissing = fMissing;
        }

        public Float getFfMissing()
        {
            return ffMissing;
        }

        public void setFfMissing(Float ffMissing)
        {
            this.ffMissing = ffMissing;
        }

        public int getiMissing()
        {
            return iMissing;
        }

        public void setiMissing(int iMissing)
        {
            this.iMissing = iMissing;
        }

        public Integer getIiMissing()
        {
            return iiMissing;
        }

        public void setIiMissing(Integer iiMissing)
        {
            this.iiMissing = iiMissing;
        }

        public long getlMissing()
        {
            return lMissing;
        }

        public void setlMissing(long lMissing)
        {
            this.lMissing = lMissing;
        }

        public Long getLlMissing()
        {
            return llMissing;
        }

        public void setLlMissing(Long llMissing)
        {
            this.llMissing = llMissing;
        }

        public short getsMissing()
        {
            return sMissing;
        }

        public void setsMissing(short sMissing)
        {
            this.sMissing = sMissing;
        }

        public Short getSsMissing()
        {
            return ssMissing;
        }

        public void setSsMissing(Short ssMissing)
        {
            this.ssMissing = ssMissing;
        }

        public Inner1 inner1;
        public Inner2 inner2Missing;
        public long aLongMissing;
        public String[] aStringArrayMissing;
        public Object[] aObjectArrayMissing;
        private boolean bMissing;
        private Boolean bbMissing;
        private byte byMissing;
        private Byte bbyMissing;
        private char cMissing;
        private Character ccMissing;
        private double dMissing;
        private double ddMissing;
        private float fMissing;
        private Float ffMissing;
        private int iMissing;
        private Integer iiMissing;
        private long lMissing;
        private Long llMissing;
        private short sMissing;
        private Short ssMissing;

        private static class Inner1
        {
        }

        private static class Inner2
        {
            public Object inner12;
        }
    }
}
