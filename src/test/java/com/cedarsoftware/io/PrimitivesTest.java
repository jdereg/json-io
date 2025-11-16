package com.cedarsoftware.io;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class PrimitivesTest
{
    @Test
    void testPrimitivesSetWithStrings()
    {
        String json = "{\"@type\":\"" + AllPrimitives.class.getName() + "\",\"b\":\"true\",\"bb\":\"true\",\"by\":\"9\",\"bby\":\"9\",\"c\":\"B\",\"cc\":\"B\",\"d\":\"9.0\",\"dd\":\"9.0\",\"f\":\"9.0\",\"ff\":\"9.0\",\"i\":\"9\",\"ii\":\"9\",\"l\":\"9\",\"ll\":\"9\",\"s\":\"9\",\"ss\":\"9\"}";
        AllPrimitives ap = TestUtil.toJava(json, null).asClass(null);
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
    void testAbilityToNullPrimitivesWithEmptyString()
    {
        String json = "{\"@type\":\"" + AllPrimitives.class.getName() + "\",\"b\":\"\",\"bb\":\"\",\"by\":\"\",\"bby\":\"\",\"c\":\"\",\"cc\":\"\",\"d\":\"\",\"dd\":\"\",\"f\":\"\",\"ff\":\"\",\"i\":\"\",\"ii\":\"\",\"l\":\"\",\"ll\":\"\",\"s\":\"\",\"ss\":\"\"}";
        AllPrimitives ap = TestUtil.toJava(json, null).asClass(null);
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
    void testEmptyPrimitives()
    {
        final String json = "{\"@type\":\"byte\"}";

        assertThatThrownBy(() -> TestUtil.toJava(json, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("To convert from Map to 'Byte' the map must include: [value] or [_v] as key with associated value");
        
        final String json1 = "{\"@type\":\"short\"}";

        assertThatThrownBy(() -> TestUtil.toJava(json1, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Short' the map must include: [value] or [_v] as key with associated value");

        final String json2 = "{\"@type\":\"int\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json2, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Integer' the map must include: [value] or [_v] as key with associated value");

        final String json3 = "{\"@type\":\"long\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json3, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Long' the map must include: [value] or [_v] as key with associated value");

        final String json4 = "{\"@type\":\"float\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json4, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Float' the map must include: [value] or [_v] as key with associated value");

        final String json5 = "{\"@type\":\"double\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json5, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Double' the map must include: [value] or [_v] as key with associated value");

        final String json6 = "{\"@type\":\"char\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json6, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'char' the map must include: [value] or [_v] as key with associated value");

        final String json7 = "{\"@type\":\"boolean\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json7, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'Boolean' the map must include: [value] or [_v] as key with associated value");

        final String json8 = "{\"@type\":\"string\"}";
        assertThatThrownBy(() -> TestUtil.toJava(json8, null).asClass(null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("convert from Map to 'String' the map must include: [value] or [_v] as key with associated value");
    }

    @Test
    void testAssignPrimitiveToString()
    {
        String json = "{\"@type\":\"" + TestStringField.class.getName() + "\",\"intField\":16,\"booleanField\":true,\"doubleField\":345.12321,\"nullField\":null,\"values\":[10,true,3.14159,null]}";
        TestStringField tsf = TestUtil.toJava(json, null).asClass(null);
        assertEquals("16", tsf.getIntField());
        assertEquals("true", tsf.getBooleanField());
        assertEquals("345.12321", tsf.getDoubleField());
        assertNull(tsf.getNullField());
        assertEquals("10", tsf.getValues()[0]);
        assertEquals("true", tsf.getValues()[1]);
        assertEquals("3.14159", tsf.getValues()[2]);
        assertEquals(null, tsf.getValues()[3]);
    }

    @Test
    void testValueAtRootNoType()
    {
        Object x = TestUtil.toJava("120.1", null).asClass(null);
        assert Double.class.isAssignableFrom(x.getClass());
        assertEquals(x, 120.1d);

        x = TestUtil.toJava("true", null).asClass(null);
        assert Boolean.class.isAssignableFrom(x.getClass());
        assertEquals(x, true);

        x = TestUtil.toJava("false", null).asClass(null);
        assert Boolean.class.isAssignableFrom(x.getClass());
        assertEquals(x, false);

        x = TestUtil.toJava("\"42\"", null).asClass(null);
        assert String.class.isAssignableFrom(x.getClass());
        assertEquals(x, "42");

        x = TestUtil.toJava("1e2", null).asClass(null);
        assert Double.class.isAssignableFrom(x.getClass());
        assertEquals(x, 100d);
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.lang.Byte", "byte"})
    void testByteValueAtRoot(String stringType)
    {
        Class<?> type = ClassUtilities.forName(stringType, ClassUtilities.getClassLoader(PrimitivesTest.class));
        Object x = TestUtil.toJava("120.1", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)120);

        x = TestUtil.toJava("true", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)1);

        x = TestUtil.toJava("false", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)0);

        x = TestUtil.toJava("\"42\"", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)42);

        x = TestUtil.toJava("1e2", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.lang.Byte", "byte"})
    void testByteObjectValueAtRoot(String stringType)
    {
        Class<?> type = stringType.equals("null") ? null : ClassUtilities.forName(stringType, ClassUtilities.getClassLoader(PrimitivesTest.class));
        Object x = TestUtil.toJava("{\"value\":120.1}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)120);

        x = TestUtil.toJava("{\"value\":true}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)1);

        x = TestUtil.toJava("{\"value\":false}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)0);

        x = TestUtil.toJava("{\"value\":\"42\"}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)42);

        x = TestUtil.toJava("{\"value\":1e2}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)100);
    }

    @Test
    void testPrimitiveTypeAtRoot()
    {
        Object x = TestUtil.toJava("{\"value\":120.1}", null).asClass(double.class);
        assert Double.class.isAssignableFrom(x.getClass());
        assertEquals(x, 120.1d);

        x = TestUtil.toJava("{\"value\":true}", null).asClass(boolean.class);
        assert Boolean.class.isAssignableFrom(x.getClass());
        assertEquals(x, true);

        x = TestUtil.toJava("{\"value\":false}", null).asClass(Boolean.class);
        assert Boolean.class.isAssignableFrom(x.getClass());
        assertEquals(x, false);

        x = TestUtil.toJava("{\"value\":\"42\"}", null).asClass(String.class);
        assert String.class.isAssignableFrom(x.getClass());
        assertEquals(x, "42");

        x = TestUtil.toJava("{\"value\":1e2}", null).asClass(Double.class);
        assert Double.class.isAssignableFrom(x.getClass());
        assertEquals(x, 100d);
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.lang.Byte", "byte", "null"})
    void testTypedByteObjectValueAtRoot(String stringType)
    {
        Class<?> type = stringType.equals("null") ? null : ClassUtilities.forName(stringType, ClassUtilities.getClassLoader(PrimitivesTest.class));
        Object x = TestUtil.toJava("{\"@type\":\"byte\",\"value\":120.1}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)120);

        x = TestUtil.toJava("{\"@type\":\"byte\",\"value\":true}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)1);

        x = TestUtil.toJava("{\"@type\":\"byte\",\"value\":false}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)0);

        x = TestUtil.toJava("{\"@type\":\"byte\",\"value\":\"42\"}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)42);

        x = TestUtil.toJava("{\"@type\":\"byte\",\"value\":1e2}", null).asClass(type);
        assert Byte.class.isAssignableFrom(x.getClass());
        assertEquals(x, (byte)100);
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
