package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class PrimitivesTest
{
    @Test
    public void testPrimitivesSetWithStrings()
    {
        String json = "{\"@type\":\"" + AllPrimitives.class.getName() + "\",\"b\":\"true\",\"bb\":\"true\",\"by\":\"9\",\"bby\":\"9\",\"c\":\"B\",\"cc\":\"B\",\"d\":\"9.0\",\"dd\":\"9.0\",\"f\":\"9.0\",\"ff\":\"9.0\",\"i\":\"9\",\"ii\":\"9\",\"l\":\"9\",\"ll\":\"9\",\"s\":\"9\",\"ss\":\"9\"}";
        AllPrimitives ap = TestUtil.toObjects(json, null);
        assertTrue(ap.getB());
        assertTrue(ap.getBb());
        assertEquals(9, ap.getBy());
        assertEquals(9, (byte) ap.getBby());
        assertEquals('B', ap.getC());
        assertEquals('B', (char) ap.getCc());
        assertEquals(9, ap.getD());
        assertEquals(9, (double) ap.getDd());
        assertEquals(9, ap.getF());
        assertEquals(9, (float) ap.getFf());
        assertEquals(9, ap.getI());
        assertEquals(9, (int) ap.getIi());
        assertEquals(9, ap.getL());
        assertEquals(9, (long) ap.getLl());
        assertEquals(9, ap.getS());
        assertEquals(9, (short) ap.getSs());
    }

    @Test
    public void testAbilityToNullPrimitivesWithEmptyString()
    {
        String json = "{\"@type\":\"" + AllPrimitives.class.getName() + "\",\"b\":\"\",\"bb\":\"\",\"by\":\"\",\"bby\":\"\",\"c\":\"\",\"cc\":\"\",\"d\":\"\",\"dd\":\"\",\"f\":\"\",\"ff\":\"\",\"i\":\"\",\"ii\":\"\",\"l\":\"\",\"ll\":\"\",\"s\":\"\",\"ss\":\"\"}";
        AllPrimitives ap = TestUtil.toObjects(json, null);
        assertFalse(ap.getB());
        assertFalse(ap.getBb());
        assertEquals(0, ap.getBy());
        assertEquals(0, (byte) ap.getBby());
        assertEquals(0, ap.getC());
        assertEquals(0, (char) ap.getCc());
        assertEquals(0, ap.getD());
        assertEquals(0, (double) ap.getDd());
        assertEquals(0, ap.getF());
        assertEquals(0, (float) ap.getFf());
        assertEquals(0, ap.getI());
        assertEquals(0, (int) ap.getIi());
        assertEquals(0, ap.getL());
        assertEquals(0, (long) ap.getLl());
        assertEquals(0, ap.getS());
        assertEquals(0, (short) ap.getSs());
    }

    @Test
    public void testEmptyPrimitives()
    {
        String json = "{\"@type\":\"byte\"}";
        Byte b = TestUtil.toObjects(json, null);
        assertEquals(b.getClass(), Byte.class);
        assertEquals(0, (byte) b);

        json = "{\"@type\":\"short\"}";
        Short s = TestUtil.toObjects(json, null);
        assertEquals(s.getClass(), Short.class);
        assertEquals(0, (short) s);

        json = "{\"@type\":\"int\"}";
        Integer i = TestUtil.toObjects(json, null);
        assertEquals(i.getClass(), Integer.class);
        assertEquals(0, (int) i);

        json = "{\"@type\":\"long\"}";
        Long l = TestUtil.toObjects(json, null);
        assertEquals(l.getClass(), Long.class);
        assertEquals(0, (long) l);

        json = "{\"@type\":\"float\"}";
        Float f = TestUtil.toObjects(json, null);
        assertEquals(f.getClass(), Float.class);
        assertEquals(0.0f, f);

        json = "{\"@type\":\"double\"}";
        Double d = TestUtil.toObjects(json, null);
        assertEquals(d.getClass(), Double.class);
        assertEquals(0.0d, d);

        json = "{\"@type\":\"char\"}";
        Character c = TestUtil.toObjects(json, null);
        assertEquals(c.getClass(), Character.class);
        assert c.equals('\u0000');

        json = "{\"@type\":\"boolean\"}";
        Boolean bool = TestUtil.toObjects(json, null);
        assertEquals(bool, Boolean.FALSE);

        json = "{\"@type\":\"string\"}";
        String str = null;
        str = TestUtil.toObjects(json, null);
        assert "".equals(str);
    }

    @Test
    public void testAssignPrimitiveToString()
    {
        String json = "{\"@type\":\"" + TestStringField.class.getName() + "\",\"intField\":16,\"booleanField\":true,\"doubleField\":345.12321,\"nullField\":null,\"values\":[10,true,3.14159,null]}";
        TestStringField tsf = TestUtil.toObjects(json, null);
        assertEquals("16", tsf.getIntField());
        assertEquals("true", tsf.getBooleanField());
        assertEquals("345.12321", tsf.getDoubleField());
        assertNull(tsf.getNullField());
        assertEquals("10", tsf.getValues()[0]);
        assertEquals("true", tsf.getValues()[1]);
        assertEquals("3.14159", tsf.getValues()[2]);
        assertEquals(null, tsf.getValues()[3]);
    }

    public class AllPrimitives
    {
        public boolean getB()
        {
            return b;
        }

        public boolean isB()
        {
            return b;
        }

        public void setB(boolean b)
        {
            this.b = b;
        }

        public Boolean getBb()
        {
            return bb;
        }

        public void setBb(Boolean bb)
        {
            this.bb = bb;
        }

        public byte getBy()
        {
            return by;
        }

        public void setBy(byte by)
        {
            this.by = by;
        }

        public Byte getBby()
        {
            return bby;
        }

        public void setBby(Byte bby)
        {
            this.bby = bby;
        }

        public char  getC()
        {
            return c;
        }

        public void setC(char c)
        {
            this.c = c;
        }

        public Character getCc()
        {
            return cc;
        }

        public void setCc(Character cc)
        {
            this.cc = cc;
        }

        public double getD()
        {
            return d;
        }

        public void setD(double d)
        {
            this.d = d;
        }

        public Double getDd()
        {
            return dd;
        }

        public void setDd(Double dd)
        {
            this.dd = dd;
        }

        public float getF()
        {
            return f;
        }

        public void setF(float f)
        {
            this.f = f;
        }

        public Float getFf()
        {
            return ff;
        }

        public void setFf(Float ff)
        {
            this.ff = ff;
        }

        public int getI()
        {
            return i;
        }

        public void setI(int i)
        {
            this.i = i;
        }

        public Integer getIi()
        {
            return ii;
        }

        public void setIi(Integer ii)
        {
            this.ii = ii;
        }

        public long getL()
        {
            return l;
        }

        public void setL(long l)
        {
            this.l = l;
        }

        public Long getLl()
        {
            return ll;
        }

        public void setLl(Long ll)
        {
            this.ll = ll;
        }

        public short getS()
        {
            return s;
        }

        public void setS(short s)
        {
            this.s = s;
        }

        public Short getSs()
        {
            return ss;
        }

        public void setSs(Short ss)
        {
            this.ss = ss;
        }

        private boolean b;
        private Boolean bb;
        private byte by;
        private Byte bby;
        private char c;
        private Character cc;
        private double d;
        private Double dd;
        private float f;
        private Float ff;
        private int i;
        private Integer ii;
        private long l;
        private Long ll;
        private short s;
        private Short ss;
    }

    public class TestStringField
    {
        public String getIntField()
        {
            return intField;
        }

        public void setIntField(String intField)
        {
            this.intField = intField;
        }

        public String getBooleanField()
        {
            return booleanField;
        }

        public void setBooleanField(String booleanField)
        {
            this.booleanField = booleanField;
        }

        public String getDoubleField()
        {
            return doubleField;
        }

        public void setDoubleField(String doubleField)
        {
            this.doubleField = doubleField;
        }

        public String getNullField()
        {
            return nullField;
        }

        public void setNullField(String nullField)
        {
            this.nullField = nullField;
        }

        public String[] getValues()
        {
            return values;
        }

        public void setValues(String[] values)
        {
            this.values = values;
        }

        private String intField;
        private String booleanField;
        private String doubleField;
        private String nullField;
        private String[] values;
    }
}
