package com.cedarsoftware.util.io

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertSame
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
class TestClass
{
    static class ManyClasses implements Serializable
    {
        private List _classes_a;

        private final Class _booleanClass;
        private final Class _BooleanClass;
        private final Object _booleanClassO;
        private final Object _BooleanClassO;
        private final Class[] _booleanClassArray;
        private final Class[] _BooleanClassArray;
        private final Object[] _booleanClassArrayO;
        private final Object[] _BooleanClassArrayO;

        private final Class _charClass;
        private final Class _CharacterClass;
        private final Object _charClassO;
        private final Object _CharacterClassO;
        private final Class[] _charClassArray;
        private final Class[] _CharacterClassArray;
        private final Object[] _charClassArrayO;
        private final Object[] _CharacterClassArrayO;

        private ManyClasses()
        {
            _classes_a = new ArrayList()
            _classes_a.add(char.class)
            _booleanClass = boolean.class
            _BooleanClass = Boolean.class
            _booleanClassO = boolean.class
            _BooleanClassO = Boolean.class
            _booleanClassArray = [boolean.class] as Class[]
            _BooleanClassArray = [Boolean.class] as Class[]
            _booleanClassArrayO = [boolean.class] as Object[]
            _BooleanClassArrayO = [Boolean.class] as Object[]

            _charClass = char.class
            _CharacterClass = Character.class
            _charClassO = char.class
            _CharacterClassO = Character.class
            _charClassArray = [char.class] as Class[]
            _CharacterClassArray = [Character.class] as Class[]
            _charClassArrayO = [char.class] as Object[]
            _CharacterClassArrayO = [Character.class] as Object[]
        }
    }

    private static class OneNestedClass {
        private Class cls;
    }

    @Test
    void testClassAtRoot()
    {
        Class c = Double.class;
        String json = TestUtil.getJsonString(c)
        TestUtil.printLine("json=" + json)
        Class r = (Class) TestUtil.readJsonObject(json)
        assertTrue(c.getName().equals(r.getName()))
    }

    @Test
    void testOneNestedClass()
    {
        OneNestedClass expected = new OneNestedClass();
        expected.cls = Date.class;

        String json = TestUtil.getJsonString(expected)
        TestUtil.printLine("json=" + json)
        OneNestedClass actual = (OneNestedClass) TestUtil.readJsonObject(json)
        Assertions.assertThat(expected.cls).isEqualTo(actual.cls);
    }

    @Test
    void testClass()
    {
        ManyClasses test = new ManyClasses()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyClasses that = (ManyClasses) TestUtil.readJsonObject(json)

        assertTrue(that._classes_a.get(0) == char.class)

        assertSame(boolean.class, that._booleanClass)
        assertSame(Boolean.class, that._BooleanClass)
        assertSame(boolean.class, that._booleanClassO)
        assertSame(Boolean.class, that._BooleanClassO)
        assertSame(boolean.class, that._booleanClassArray[0])
        assertSame(Boolean.class, that._BooleanClassArray[0])
        assertSame(boolean.class, that._booleanClassArrayO[0])
        assertSame(Boolean.class, that._BooleanClassArrayO[0])

        assertSame(char.class, that._charClass)
        assertSame(Character.class, that._CharacterClass)
        assertSame(char.class, that._charClassO)
        assertSame(Character.class, that._CharacterClassO)
        assertSame(char.class, that._charClassArray[0])
        assertSame(Character.class, that._CharacterClassArray[0])
        assertSame(char.class, that._charClassArrayO[0])
        assertSame(Character.class, that._CharacterClassArrayO[0])
    }

}
