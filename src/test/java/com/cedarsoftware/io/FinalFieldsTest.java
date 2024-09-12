package com.cedarsoftware.io;

import java.io.Serializable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FinalFieldsTest {

    @Test
    void testFields() {
        final FinalFieldsIncluded expected = FinalFieldsIncluded.newDefaultObject();
        // we can see that it wasn't really deserialized by changed fields inside final fields
        expected._object._stringB = "Vasyl Stus";

        String jsonOut = TestUtil.toJson(expected);
        TestUtil.printLine(jsonOut);

        FinalFieldsIncluded actual = TestUtil.toObjects(jsonOut, null);

        Assertions.assertEquals(expected._charFinalA, actual._charFinalA);
        Assertions.assertEquals(expected._stringFinalA, actual._stringFinalA);
        Assertions.assertEquals(expected._stringB, actual._stringB);

        Assertions.assertNotSame(expected._object, actual._object);
        Assertions.assertEquals(expected._object._stringFinalA, actual._object._stringFinalA);
        Assertions.assertEquals(expected._object._stringB, actual._object._stringB);
    }

    // it's important that the modifier is public
    public static class FinalFieldsIncluded implements Serializable {
        public final char _charFinalA;
        public final String _stringFinalA;
        public String _stringB;
        public final ComplexObject _object = ComplexObject.newDefaultObject();

        private FinalFieldsIncluded(final char charFinalA, final String stringFinalA) {
            this._charFinalA = charFinalA;
            this._stringFinalA = stringFinalA;
        }

        public static FinalFieldsIncluded newDefaultObject() {
            final FinalFieldsIncluded object = new FinalFieldsIncluded('b', "Lesia Ukrainka");
            object._stringB = "Ivan Franko";
            return object;
        }
    }

    public static class ComplexObject implements Serializable {
        public final String _stringFinalA;
        public String _stringB;

        private ComplexObject(final String stringFinalA) {
            this._stringFinalA = stringFinalA;
        }

        public static ComplexObject newDefaultObject() {
            final ComplexObject object = new ComplexObject("William Shakespeare");
            object._stringB = "Taras Shevchenko";
            return object;
        }
    }
}
