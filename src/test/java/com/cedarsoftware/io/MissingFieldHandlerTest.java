package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            ((CustomPoint) object).newY = (long) value;
            madeItHere[0] = true;
        };
        assert !madeItHere[0];
        CustomPoint clonePoint = TestUtil.toJava(OLD_CUSTOM_POINT, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);
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

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
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
        CustomPointWithRef clonePoint = TestUtil.toJava(OLD_CUSTOM_POINT2, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);
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

    // ========== Tests for handleMissingField coverage in ObjectResolver ==========

    /**
     * Test missing field with null value.
     * This tests lines 200-202 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithNullValue() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("nullField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"nullField\":null}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        assertEquals(null, capturedValue[0], "Value should be null");
        assertEquals(5, result.x);
    }

    /**
     * Test missing field with array value.
     * This tests lines 209-211 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithArrayValue() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("arrayField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"arrayField\":[1,2,3]}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        // The value should be passed (either as array or null depending on type resolution)
        assertEquals(5, result.x);
    }

    /**
     * Test missing field with JsonObject reference.
     * This tests lines 215-218 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithReference() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("refField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        // JSON with a reference to an existing object
        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$RefTestClass\",\"point\":{\"@id\":1,\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":42},\"refField\":{\"@ref\":1}}";
        RefTestClass result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called for reference");
        assertNotNull(capturedValue[0], "Reference should have been resolved");
        assertTrue(capturedValue[0] instanceof CustomPoint, "Value should be resolved CustomPoint");
        assertEquals(42, ((CustomPoint) capturedValue[0]).x);
    }

    /**
     * Test missing field with JsonObject that has a type.
     * This tests lines 219-224 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithTypedJsonObject() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("typedField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"typedField\":{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":99}}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        assertNotNull(capturedValue[0], "Value should not be null");
        assertTrue(capturedValue[0] instanceof CustomPoint, "Value should be CustomPoint");
        assertEquals(99, ((CustomPoint) capturedValue[0]).x);
    }

    /**
     * Test missing field with JsonObject that has no type (anonymous object).
     * This tests lines 225-226 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithUntypedJsonObject() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("untypedField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        // JSON with an untyped nested object
        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"untypedField\":{\"a\":1,\"b\":2}}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        // Value should be null since there's no type info to create instance
        assertEquals(5, result.x);
    }

    /**
     * Test missing field with primitive value (String).
     * This tests lines 228-229 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithStringValue() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("stringField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"stringField\":\"hello\"}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        assertEquals("hello", capturedValue[0], "Value should be the string");
        assertEquals(5, result.x);
    }

    /**
     * Test missing field with numeric value.
     * This tests lines 228-229 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithNumberValue() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("numberField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"numberField\":123}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        assertEquals(123L, capturedValue[0], "Value should be the number");
        assertEquals(5, result.x);
    }

    /**
     * Test missing field with boolean value.
     * This tests lines 228-229 of ObjectResolver.handleMissingField().
     */
    @Test
    public void testMissingFieldWithBooleanValue() {
        final Object[] capturedValue = new Object[1];
        final boolean[] handlerCalled = new boolean[]{false};

        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            if ("boolField".equals(fieldName)) {
                capturedValue[0] = value;
                handlerCalled[0] = true;
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"boolField\":true}";
        CustomPoint result = TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);

        assertTrue(handlerCalled[0], "Missing field handler should have been called");
        assertEquals(true, capturedValue[0], "Value should be true");
        assertEquals(5, result.x);
    }

    /**
     * Test exception handling in handleMissingField when processing an invalid @ref.
     * This tests lines 231-237 of ObjectResolver.handleMissingField().
     * When a @ref points to a non-existent @id, the exception is caught and wrapped.
     */
    @Test
    public void testMissingFieldWithInvalidReference() {
        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            // Just capture - we're testing the exception from bad @ref, not handler exception
        };

        // JSON with a reference to a non-existent @id (999 doesn't exist)
        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"badRef\":{\"@ref\":999}}";

        try {
            TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);
            assertTrue(false, "Should have thrown JsonIoException for invalid reference");
        } catch (JsonIoException e) {
            // Expected - invalid reference should throw
            assertTrue(e.getMessage().contains("999") || e.getMessage().contains("reference") || e.getMessage().contains("missing field"),
                    "Message should mention the invalid reference or missing field: " + e.getMessage());
        }
    }

    /**
     * Test exception handling when MissingFieldHandler itself throws RuntimeException.
     * The handler is called from notifyMissingFields(), so this exception propagates directly.
     */
    @Test
    public void testMissingFieldHandlerThrowsRuntimeException() {
        MissingFieldHandler throwingHandler = (object, fieldName, value) -> {
            if ("badField".equals(fieldName)) {
                throw new RuntimeException("Handler exploded!");
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"badField\":\"test\"}";

        try {
            TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(throwingHandler).build()).asClass(null);
            assertTrue(false, "Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            // Handler exceptions propagate - message may include exception class name
            assertTrue(e.getMessage().contains("Handler exploded!"), "Message should contain original message");
        }
    }

    /**
     * Test that JsonIoException thrown by handler is re-thrown directly.
     */
    @Test
    public void testMissingFieldHandlerThrowsJsonIoException() {
        MissingFieldHandler throwingHandler = (object, fieldName, value) -> {
            if ("jsonIoField".equals(fieldName)) {
                throw new JsonIoException("Direct JsonIoException from handler");
            }
        };

        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"jsonIoField\":\"data\"}";

        try {
            TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(throwingHandler).build()).asClass(null);
            assertTrue(false, "Should have thrown JsonIoException");
        } catch (JsonIoException e) {
            assertEquals("Direct JsonIoException from handler", e.getMessage());
        }
    }

    /**
     * Test exception wrapping in handleMissingField when createInstance throws a non-JsonIoException.
     * This tests lines 231-233 of ObjectResolver.handleMissingField().
     * When a typed JsonObject's class cannot be instantiated, the exception is caught and wrapped.
     */
    @Test
    public void testMissingFieldExceptionWrapping() {
        MissingFieldHandler missingHandler = (object, fieldName, value) -> {
            // Handler will be called if we get that far
        };

        // JSON with a missing field whose value is a typed object that will fail to instantiate
        // Using a class with a constructor that throws an exception
        String json = "{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$CustomPoint\",\"x\":5,\"failField\":{\"@type\":\"com.cedarsoftware.io.MissingFieldHandlerTest$FailingConstructorClass\",\"data\":1}}";

        try {
            TestUtil.toJava(json, new ReadOptionsBuilder().missingFieldHandler(missingHandler).build()).asClass(null);
            assertTrue(false, "Should have thrown JsonIoException wrapping the constructor exception");
        } catch (JsonIoException e) {
            // Lines 231-233: The exception should be wrapped with field info in the message
            assertTrue(e.getMessage().contains("missing field") || e.getMessage().contains("failField") || e.getCause() != null,
                    "Message should contain field info or have a cause: " + e.getMessage());
        }
    }

    // Helper class that throws exception in constructor - used to test exception wrapping
    public static class FailingConstructorClass {
        public int data;

        public FailingConstructorClass() {
            throw new IllegalStateException("Constructor intentionally failed for testing");
        }
    }

    // Helper class for reference tests
    public static class RefTestClass {
        public CustomPoint point;
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
