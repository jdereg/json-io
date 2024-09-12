package com.cedarsoftware.io;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OldParentChildTest {

    @Test
    void chainedSetterUsedCorrectly() {
        String json = "{\"@type\":\"com.cedarsoftware.io.OldParentChildTest$Child\",\"name\":42,\"com.cedarsoftware.io.OldParentChildTest$Parent.name\":\"Stus\"}";

        Child actual = TestUtil.toObjects(json, null);

        Assertions.assertEquals(42L, actual.name);
        Assertions.assertEquals("Stus", actual.getName());
    }

    public static class Parent {
        private String name;

        public String getName() {
            return name;
        }

        public Parent setName(final String name) {
            this.name = name;
            return this;
        }
    }

    public static class Child extends Parent {
        public Long name;

        public Child(final Long name, final String surname) {
            super.setName(surname);
            this.name = name;
        }
    }
}
