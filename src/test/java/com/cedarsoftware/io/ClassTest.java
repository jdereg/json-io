package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
public class ClassTest
{
    @Test
    public void testClassAtRoot()
    {
        Class<?> c = Double.class;
        String json = TestUtil.toJson(c);
        TestUtil.printLine("json=" + json);
        Class<?> r = TestUtil.toObjects(json, null);
        assertEquals(c.getName(), r.getName());
    }

    @Test
    public void testOneNestedClass()
    {
        OneNestedClass expected = new OneNestedClass();
        expected.cls = Date.class;

        String json = TestUtil.toJson(expected);
        TestUtil.printLine("json=" + json);
        OneNestedClass actual = TestUtil.toObjects(json, null);
        assert expected.cls.equals(actual.cls);
    }

    @Test
    void testBadClassName()
    {
        String json = "{\"@type\":\"class\",\"value\":\"foo.bar.baz.Qux\"}";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Cannot convert String 'foo.bar.baz.Qux' to class.  Class not found");
    }

    @Test
    void testBadClassNameRoot()
    {
        String json = "\"foo.bar.baz.Qux\"";
        assertThatThrownBy(() -> TestUtil.toObjects(json, Class.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Return type mismatch. Expecting: java.lang.Class, found: java.lang.String");
    }

    @Test
    void testNoClassNameValue()
    {
        String json = "{\"@type\":\"class\"}";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Map to 'Class' the map must include: [class], [value], or [_v] as key with associated value");

    }

    @Test
    void testBadClassValueType()
    {
        String json = "{\"@type\":\"class\",\"value\":16.0}";
        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unsupported conversion, source type [Double (16.0)] target type 'Class'");
    }

    @Test
    void testGoodClassNameRoot()
    {
        String json = "\"java.util.HashMap\"";
        Object x = TestUtil.toObjects(json, Class.class);
        assert x.equals(HashMap.class);
    }

    @Test
    void testBadClassValueTypeAtRoot()
    {
        String json = "16.0";
        assertThatThrownBy(() -> TestUtil.toObjects(json, Class.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Return type mismatch. Expecting: java.lang.Class, found: java.lang.Double");
    }

    @Test
    public void testClass()
    {
        ManyClasses test = new ManyClasses();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyClasses that = TestUtil.toObjects(json, null);

        assertEquals(that._classes_a.get(0), Character.class);

        assertSame(Boolean.class, that._booleanClass);
        assertSame(Boolean.class, that._BooleanClass);
        assertSame(Boolean.class, that._booleanClassO);
        assertSame(Boolean.class, that._BooleanClassO);
        assertSame(Boolean.class, that._booleanClassArray[0]);
        assertSame(Boolean.class, that._BooleanClassArray[0]);
        assertSame(Boolean.class, that._booleanClassArrayO[0]);
        assertSame(Boolean.class, that._BooleanClassArrayO[0]);

        assertSame(Character.class, that._charClass);
        assertSame(Character.class, that._CharacterClass);
        assertSame(Character.class, that._charClassO);
        assertSame(Character.class, that._CharacterClassO);
        assertSame(Character.class, that._charClassArray[0]);
        assertSame(Character.class, that._CharacterClassArray[0]);
        assertSame(Character.class, that._charClassArrayO[0]);
        assertSame(Character.class, that._CharacterClassArrayO[0]);
    }

    public static class ManyClasses implements Serializable
    {
        private ManyClasses()
        {
            _classes_a = new ArrayList<>();
            _classes_a.add(Character.class);
            _booleanClass = Boolean.class;
            _BooleanClass = Boolean.class;
            _booleanClassO = Boolean.class;
            _BooleanClassO = Boolean.class;
            _booleanClassArray = new Class[]{Boolean.class};
            _BooleanClassArray = new Class[]{Boolean.class};
            _booleanClassArrayO = new Object[]{Boolean.class};
            _BooleanClassArrayO = new Object[]{Boolean.class};

            _charClass = Character.class;
            _CharacterClass = Character.class;
            _charClassO = Character.class;
            _CharacterClassO = Character.class;
            _charClassArray = new Class[]{Character.class};
            _CharacterClassArray = new Class[]{Character.class};
            _charClassArrayO = new Object[]{Character.class};
            _CharacterClassArrayO = new Object[]{Character.class};
        }

        private final List<Class<?>> _classes_a;
        private final Class<?> _booleanClass;
        private final Class<?> _BooleanClass;
        private final Object _booleanClassO;
        private final Object _BooleanClassO;
        private final Class<?>[] _booleanClassArray;
        private final Class<?>[] _BooleanClassArray;
        private final Object[] _booleanClassArrayO;
        private final Object[] _BooleanClassArrayO;
        private final Class<?> _charClass;
        private final Class<?> _CharacterClass;
        private final Object _charClassO;
        private final Object _CharacterClassO;
        private final Class<?>[] _charClassArray;
        private final Class<?>[] _CharacterClassArray;
        private final Object[] _charClassArrayO;
        private final Object[] _CharacterClassArrayO;
    }
    private static class OneNestedClass
    {
        private Class<?> cls;
    }
}
