package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.EnumSet;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OldSetTest {

    @Test
    void testSet() {
        final String oldSerializedValue = loadJson("oldEnumSets.json");

        final ManySets result = TestUtil.toObjects(oldSerializedValue, null);
        Assertions.assertNotNull(result);

        Assertions.assertNotNull(result.enumSet1);
        Assertions.assertEquals(3, result.enumSet1.size());

        Assertions.assertNotNull(result.enumSet2);
        Assertions.assertEquals(3, result.enumSet2.size());

        Assertions.assertNotNull(result.child);
        Assertions.assertNotNull(result.child.enumSet3);
        Assertions.assertEquals(3, result.child.enumSet3.size());
    }

    private String loadJson(final String fileName) {
        return ClassUtilities.loadResourceAsString("enumSet/" + fileName).trim();
    }

    public static class ManySets implements Serializable {
        private EnumSet<Enum1> enumSet1;
        private EnumSet<Enum2> enumSet2;
        private Child child;
    }

    public static class Child implements Serializable {
        private EnumSet<Enum1> enumSet3;
    }

    public enum Enum1 {
        E1, E2, E3
    }

    public enum Enum2 {
        E1(11), E2(22), E3(33);

        Enum2(int v) {
            this.v = v;
        }

        private final int v;
    }
}
