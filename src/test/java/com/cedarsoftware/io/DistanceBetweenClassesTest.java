package com.cedarsoftware.io;

import java.util.Date;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
class DistanceBetweenClassesTest {

    // Example classes and interfaces for testing
    interface TestInterface {}
    interface SubInterface extends TestInterface {}
    static class TestClass {}
    static class SubClass extends TestClass implements TestInterface {}
    static class AnotherClass {}

    @Test
    void testClassToClassDirectInheritance() {
        assertEquals(1, ClassUtilities.computeInheritanceDistance(SubClass.class, TestClass.class),
                "Direct class to class inheritance should have a distance of 1.");
    }

    @Test
    void testClassToClassNoInheritance() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(TestClass.class, AnotherClass.class),
                "No inheritance between classes should return -1.");
    }

    @Test
    void testClassToInterfaceDirectImplementation() {
        assertEquals(1, ClassUtilities.computeInheritanceDistance(SubClass.class, TestInterface.class),
                "Direct class to interface implementation should have a distance of 1.");
    }

    @Test
    void testClassToInterfaceNoImplementation() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(TestClass.class, TestInterface.class),
                "No implementation of the interface by the class should return -1.");
    }

    @Test
    void testInterfaceToClass() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(TestInterface.class, TestClass.class),
                "Interface to class should always return -1 as interfaces cannot inherit from classes.");
    }

    @Test
    void testInterfaceToInterfaceDirectInheritance() {
        assertEquals(1, ClassUtilities.computeInheritanceDistance(SubInterface.class, TestInterface.class),
                "Direct interface to interface inheritance should have a distance of 1.");
    }

    @Test
    void testInterfaceToInterfaceNoInheritance() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(TestInterface.class, SubInterface.class),
                "No inheritance between interfaces should return -1.");
    }

    @Test
    void testSameClass() {
        assertEquals(0, ClassUtilities.computeInheritanceDistance(TestClass.class, TestClass.class),
                "Distance from a class to itself should be 0.");
    }

    @Test
    void testSameInterface() {
        assertEquals(0, ClassUtilities.computeInheritanceDistance(TestInterface.class, TestInterface.class),
                "Distance from an interface to itself should be 0.");
    }

    @Test
    void testWithNullSource() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(null, TestClass.class),
                "Should return -1 when source is null.");
    }

    @Test
    void testWithNullDestination() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(TestClass.class, null),
                "Should return -1 when destination is null.");
    }

    @Test
    void testWithBothNull() {
        assertEquals(-1, ClassUtilities.computeInheritanceDistance(null, null),
                "Should return -1 when both source and destination are null.");
    }

    @Test
    void testPrimitives() {
        assert 0 == ClassUtilities.computeInheritanceDistance(byte.class, Byte.TYPE);
        assert 0 == ClassUtilities.computeInheritanceDistance(Byte.TYPE, byte.class);
        assert 0 == ClassUtilities.computeInheritanceDistance(Byte.TYPE, Byte.class);
        assert 0 == ClassUtilities.computeInheritanceDistance(Byte.class, Byte.TYPE);
        assert 0 == ClassUtilities.computeInheritanceDistance(Byte.class, byte.class);
        assert 0 == ClassUtilities.computeInheritanceDistance(int.class, Integer.class);
        assert 0 == ClassUtilities.computeInheritanceDistance(Integer.class, int.class);

        assert -1 == ClassUtilities.computeInheritanceDistance(Byte.class, int.class);
        assert -1 == ClassUtilities.computeInheritanceDistance(int.class, Byte.class);
        assert -1 == ClassUtilities.computeInheritanceDistance(int.class, String.class);
        assert -1 == ClassUtilities.computeInheritanceDistance(int.class, String.class);
        assert -1 == ClassUtilities.computeInheritanceDistance(Short.TYPE, Integer.TYPE);
        assert -1 == ClassUtilities.computeInheritanceDistance(String.class, Integer.TYPE);

        assert -1 == ClassUtilities.computeInheritanceDistance(Date.class, java.sql.Date.class);
        assert 1 == ClassUtilities.computeInheritanceDistance(java.sql.Date.class, Date.class);
    }
}
