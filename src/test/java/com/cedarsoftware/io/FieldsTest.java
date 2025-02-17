package com.cedarsoftware.io;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.cedarsoftware.io.models.Human;
import com.cedarsoftware.io.models.OuterObject;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
public class FieldsTest
{
    @Test
    void testNestedPrivateClass() {
        OuterObject expected = OuterObject.of(9, 12, "Happy Holidays", "Some Other Message");
        String json = TestUtil.toJson(expected);
        TestUtil.printLine(json);
        OuterObject actual = TestUtil.toObjects(json, null);
        assertEquals(actual.getX(), expected.getX());
        assertEquals(actual.getY(), expected.getY());
        assertEquals(actual.getMessage1Holder().getMessage(), expected.getMessage1Holder().getMessage());
        assertEquals(actual.getMessage2Holder().getMessage(), expected.getMessage2Holder().getMessage());
    }

    @Test
    public void testFields()
    {
        ManyFields obj = new ManyFields();
        obj.init();
        String jsonOut = TestUtil.toJson(obj);
        TestUtil.printLine(jsonOut);

        ManyFields root = TestUtil.toObjects(jsonOut, null);
        assertFields(root);
    }

    @Test
    void testNestedChar() {
        NestedChar initial = new NestedChar();
        initial.ch = '\t';

        String jsonOut = TestUtil.toJson(initial);
        TestUtil.printLine(jsonOut);

        NestedChar actual = TestUtil.toObjects(jsonOut, null);
        assertEquals(initial.ch, actual.ch);
    }

    private static void assertFields(ManyFields root)
    {
        assertTrue(root._boolean_a);
        assertFalse(root._boolean_b);
        assertTrue(root._boolean_c);
        assertFalse(root._boolean_d);
        assertNull(root._boolean_e);

        assertEquals('a', root._char_a);
        assertEquals('\t', root._char_b);
        assertEquals('\u0004', root._char_c);
        assertEquals('a', (char) root._char_d);
        assertEquals('\t', (char) root._char_e);
        assertEquals('\u0002', (char) root._char_f);
        assertNull(root._char_g);

        assertEquals(Byte.MIN_VALUE, root._byte_a);
        assertEquals(Byte.MAX_VALUE, root._byte_b);
        assertEquals(Byte.MIN_VALUE, (byte) root._byte_c);
        assertEquals(Byte.MAX_VALUE, (byte) root._byte_d);
        assertNull(root._byte_e);

        assertEquals(Short.MIN_VALUE, root._short_a);
        assertEquals(Short.MAX_VALUE, root._short_b);
        assertEquals(Short.MIN_VALUE, (short) root._short_c);
        assertEquals(Short.MAX_VALUE, (short) root._short_d);
        assertNull(root._short_e);

        assertEquals(Integer.MIN_VALUE, root._int_a);
        assertEquals(Integer.MAX_VALUE, root._int_b);
        assertEquals(Integer.MIN_VALUE, (int) root._int_c);
        assertEquals(Integer.MAX_VALUE, (int) root._int_d);
        assertNull(root._int_e);

        assertEquals(Long.MIN_VALUE, root._long_a);
        assertEquals(Long.MAX_VALUE, root._long_b);
        assertEquals(Long.MIN_VALUE, (long) root._long_c);
        assertEquals(Long.MAX_VALUE, (long) root._long_d);
        assertNull(root._long_e);

        assertEquals(Float.MIN_VALUE, root._float_a);
        assertEquals(Float.MAX_VALUE, root._float_b);
        assertEquals(Float.MIN_VALUE, root._float_c);
        assertEquals(Float.MAX_VALUE, root._float_d);
        assertNull(root._float_e);

        assertEquals(Double.MIN_VALUE, root._double_a);
        assertEquals(Double.MAX_VALUE, root._double_b);
        assertEquals(Double.MIN_VALUE, root._double_c);
        assertEquals(Double.MAX_VALUE, root._double_d);
        assertNull(root._double_e);

        assertEquals("Hello", root._string_a);
        assertEquals("", root._string_b);
        assertNull(root._string_c);

        assertEquals(root._date_a, _testDate);
        assertEquals(root._date_b, new Date(0));
        assertNull(root._date_c);

        assertEquals(root._class_a, Boolean.class);
        assertEquals(root._class_b, Character.class);
        assertEquals(root._class_c, Byte.class);
        assertEquals(root._class_d, Short.class);
        assertEquals(root._class_e, Integer.class);
        assertEquals(root._class_f, Long.class);
        assertEquals(root._class_g, Float.class);
        assertEquals(root._class_h, Double.class);
        assertEquals(root._class_i, String.class);
        assertNull(root._class_j);

        assertEquals("holstein", root._sb_a.toString());
        assertEquals("", root._sb_b.toString());
        assertNull(root._sb_c);
        assertEquals("viper", root._sb_d.toString());

        assertEquals("holstein", root._sb_a.toString());
        assertEquals("", root._sb_b.toString());
        assertNull(root._sb_c);
        assertEquals("viper", root._sb_d.toString());

        assertEquals(root._bigInt, new BigInteger("25"));
        assertEquals(root._bigIntO, new BigInteger("25"));
        assertEquals(root._bigDec, new BigDecimal("25.0"));
        assertEquals(root._bigDec, new BigDecimal("25.0"));

        assertEquals("A", root._cycleTest._name);
        assertEquals("B", root._cycleTest._other._name);
        assertEquals("C", root._cycleTest._other._other._name);
        assertEquals("A", root._cycleTest._other._other._other._name);
        assertEquals(root._cycleTest, root._cycleTest._other._other._other);

        assertTrue(root._arrayList_empty.isEmpty());
        assertEquals(1, root._arrayList_1.size());
        assertEquals(1, root._arrayList_2.size());
        assertEquals(1, root._arrayList_3.size());
        assertTrue(DeepEquals.deepEquals(root._arrayList_4, root._arrayList_3));
        assertEquals(1, root._arrayList_5.size());
        assertTrue(DeepEquals.deepEquals(root._arrayList_6, root._arrayList_5));

        assertTrue(root._hashMap_empty.isEmpty());
        assertEquals(1, root._hashMap_1.size());
        assertEquals(1, root._hashMap_2.size());
        assertEquals(1, root._hashMap_3.size());
        assertTrue(DeepEquals.deepEquals(root._hashMap_4, root._hashMap_3));
        assertEquals(1, root._hashMap_5.size());
        assertTrue(DeepEquals.deepEquals(root._hashMap_6, root._hashMap_5));

        assertEquals(0, root._stringArray_empty.length);
        assertEquals(1, root._stringArray_1.length);
        assertEquals(1, root._stringArray_2.length);
        assertEquals(1, root._stringArray_3.length);
        assertEquals(root._stringArray_4, root._stringArray_3);
        assertEquals(1, root._stringArray_5.length);
        assertEquals(root._stringArray_6, root._stringArray_5);
    }

    @Test
    public void testAlwaysShowType()
    {
        ManyFields tf = new ManyFields();
        tf.init();
        String json0 = TestUtil.toJson(tf, new WriteOptionsBuilder().showTypeInfoAlways().build());
        String json1 = TestUtil.toJson(tf);
        assertTrue(json0.length() > json1.length());
    }

    @Test
    public void testReconstituteFields()
    {
        ManyFields testFields = new ManyFields();
        testFields.init();
        String json0 = TestUtil.toJson(testFields);
        Map testFields2 = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        String json1 = TestUtil.toJson(testFields2);
        TestUtil.printLine("json1=" + json1);

        ManyFields testFields3 = TestUtil.toObjects(json1, null);
        assertFields(testFields3);// Re-written from Map of Maps and re-loaded correctly
        assertEquals(json0, json1);
    }

    @Test
    public void testAssignToObjectField()
    {
        Class<TestVanillaFields> clazz = TestVanillaFields.class;
        String json = "{\"@type\":\"" + clazz.getName() + "\",\"name\":\"Nakamoto\",\"salary\":100.45,\"age\":48,\"alive\":true,\"garbage\":null}";
        TestVanillaFields vanilla = (TestVanillaFields) TestUtil.toObjects(json, null);
        assertEquals(vanilla.getName(), "Nakamoto");
        assertEquals(vanilla.salary, 100.45d);
        assertEquals(vanilla.getAge(), 48L);
        assertEquals(vanilla.getAlive(), true);
        assertNull(vanilla.getGarbage());
    }

    private static Stream<Arguments> includeFieldsFromClass_withInheritance() {
        return Stream.of(
                Arguments.of(Human.class, listOf("name"), 1),
                Arguments.of(Human.class, listOf("name", "Race.name", "age"), 3),
                Arguments.of(Human.class, listOf("name", "age", "strength"), 3)

        );
    }

    @SuppressWarnings("rawtypes")
    @ParameterizedTest
    @MethodSource("includeFieldsFromClass_withInheritance")
    void testIncludedFields_withInheritance(Class<?> c, List<String> strings, int expectedSize) {
        Human human = new Human();
        human.setAge(27);
        human.setWisdom(15);
        human.setIntelligence(17);
        human.setStrength(5);

        String json = TestUtil.toJson(human, new WriteOptionsBuilder()
                .addIncludedFields(c, strings).build());
        Map<String, Object> check = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        assertThat(check)
                .hasSize(expectedSize)
                .containsOnlyKeys(strings);
    }

    @Test
    void testIncludedFields_onlyIncludesFieldsFromClass_althoughTheyCanBeOnTheParent() {
        Map<Class<?>, Collection<String>> includedFields = mapOf(MorePainfulToSerialize.class, listOf("name"));

        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        WriteOptions writeOptions = new WriteOptionsBuilder().addIncludedFields(includedFields).build();
        String json = TestUtil.toJson(painful, writeOptions);
        Map check = TestUtil.toObjects(json, readOptions, null);
        assertEquals(1, check.size());
        assertTrue(check.containsKey("name"));
    }

    @Test
    void testIncludedFields_whenDefinedOnOurClass_definesInclusion() {
        Map<Class<?>, Collection<String>> includedFields = mapOf(MorePainfulToSerialize.class, listOf("name", "age"));
        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        WriteOptions writeOptions = new WriteOptionsBuilder().addIncludedFields(includedFields).build();
        String json = TestUtil.toJson(painful, writeOptions);
        Map check = TestUtil.toObjects(json, readOptions, null);
        assertEquals(2, check.size());
        assertTrue(check.containsKey("name"));
        assertTrue(check.containsKey("age"));
    }

    @Test
    void testIncludedFields_whenDefinedOnParentClass_doesNothingToSubClass() {
        Map<Class<?>, Collection<String>> includedFields = mapOf(PainfulToSerialize.class, listOf("name"));
        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        WriteOptions writeOptions = new WriteOptionsBuilder().addIncludedFields(includedFields).build();
        String json = TestUtil.toJson(painful, writeOptions);
        Map check = TestUtil.toObjects(json, readOptions, null);
        assertEquals(3, check.size());
        assertTrue(check.containsKey("name"));
    }

    @Test
    public void testExcludedFieldsInheritance()
    {
        Map<Class<?>, Collection<String>> excludedFields = new LinkedHashMap<>();
        excludedFields.put(PainfulToSerialize.class, listOf("classLoader"));
        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        WriteOptions writeOptions = new WriteOptionsBuilder().addExcludedFields(excludedFields).build();
        String json = TestUtil.toJson(painful, writeOptions);
        Map check = TestUtil.toObjects(json, readOptions, null);
        assertEquals(2, check.size());
        assertTrue(check.containsKey("age"));
        assertTrue(check.containsKey("name"));

    }

    @Test
    void testExcludedFields() {
        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();

        WriteOptions writeOptions = new WriteOptionsBuilder()
                .addExcludedField(PainfulToSerialize.class, "classLoader")
                .addExcludedField(MorePainfulToSerialize.class, "age")
                .build();
        String json = TestUtil.toJson(painful, writeOptions);
        Map check = TestUtil.toObjects(json, readOptions, null);
        assertEquals(1, check.size());
        assertTrue(check.containsKey("name"));
    }

    @Test
    public void testLocaleAsField()
    {
        Locale locale = Locale.getDefault();
        TestLocale tl = new TestLocale();
        tl._loc = locale;
        String json = TestUtil.toJson(tl);
        TestUtil.printLine("json=" + json);

        tl = TestUtil.toObjects(json, null);
        assertEquals(locale, tl._loc);
    }

    @Test
    public void testFieldBlackList()
    {
        PainfulToSerialize painful = new PainfulToSerialize();
        painful.setName("Android rocks");

        String json = TestUtil.toJson(painful, new WriteOptionsBuilder()
                .addExcludedFields(PainfulToSerialize.class, listOf("classLoader")).build());
        Map check = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(1, check.size());
        assertTrue(check.containsKey("name"));
    }

    @Test
    public void testFieldBlackListInheritance()
    {
        MorePainfulToSerialize painful = new MorePainfulToSerialize();
        painful.setName("Android rocks");
        painful.setAge(50);

        String json = TestUtil.toJson(painful, new WriteOptionsBuilder()
                .addExcludedFields(PainfulToSerialize.class, listOf("classLoader")).build());
        Map check = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(2, check.size());
        assertTrue(check.containsKey("name"));
        assertTrue(check.containsKey("age"));
    }

    @Test
    public void testFieldBlackListPriorityToSpecifier()
    {
        PainfulToSerialize painful = new PainfulToSerialize();
        painful.setName("Android rocks");

        String json = TestUtil.toJson(painful, new WriteOptionsBuilder()
                .addIncludedFields(PainfulToSerialize.class, listOf("name"))
                .addExcludedFields(PainfulToSerialize.class, listOf("name", "classLoader")).build());
        Map check = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(0, check.size());
    }
    @Test

    public void testFieldBlackListPriorityToSpecifier2()
    {
        PainfulToSerialize painful = new PainfulToSerialize();
        painful.setName("Android rocks");

        String json = TestUtil.toJson(painful, new WriteOptionsBuilder()
                .addIncludedFields(PainfulToSerialize.class, listOf("name", "classLoader"))
                .addExcludedFields(PainfulToSerialize.class, listOf("classLoader")).build());
        Map check = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(1, check.size());
        assertTrue(check.containsKey("name"));
    }

    public static Date _testDate = new Date();

    public static class PainfulToSerialize
    {
        public ClassLoader getClassLoader()
        {
            return classLoader;
        }

        public void setClassLoader(ClassLoader classLoader)
        {
            this.classLoader = classLoader;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        private ClassLoader classLoader = ClassUtilities.getClassLoader(FieldsTest.class);
        private String name;
    }

    public static class MorePainfulToSerialize extends PainfulToSerialize
    {
        public int getAge()
        {
            return age;
        }

        public void setAge(int age)
        {
            this.age = age;
        }

        private int age;
    }

    public static class TestVanillaFields
    {
        public Object getName()
        {
            return name;
        }

        public void setName(Object name)
        {
            this.name = name;
        }

        public Object getSalary()
        {
            return salary;
        }

        public void setSalary(Object salary)
        {
            this.salary = salary;
        }

        public Comparable getAge()
        {
            return age;
        }

        public void setAge(Comparable age)
        {
            this.age = age;
        }

        public Serializable getAlive()
        {
            return alive;
        }

        public void setAlive(Serializable alive)
        {
            this.alive = alive;
        }

        public Object getGarbage()
        {
            return garbage;
        }

        public void setGarbage(Object garbage)
        {
            this.garbage = garbage;
        }

        private Object name;
        private Object salary;
        private Comparable age;
        private Serializable alive;
        private Object garbage;
    }

    public static class TestLocale implements Serializable
    {
        private Locale _loc;
    }

    private static class NestedChar implements Serializable {
        private char ch;

        public char getCh() {
            return this.ch;
        }

        public void setCh(char ch) {
            this.ch = ch;
        }
    }

    /**
     * Test direct fields for all types, primitives, special handled fields
     * like Date, String, and Class, plus regular objects, and circular
     * references.
     */
    private static class ManyFields implements Serializable
    {
        private void init()
        {
            _boolean_a = true;
            _boolean_b = false;
            _boolean_c = Boolean.TRUE;
            _boolean_d = Boolean.FALSE;
            _boolean_e = null;

            _char_a = 'a';
            _char_b = '\t';
            _char_c = '\u0004';
            _char_d = 'a';
            _char_e = '\t';
            _char_f = '\u0002';
            _char_g = null;

            _byte_a = -128;
            _byte_b = 127;
            _byte_c = (byte) -128;
            _byte_d = (byte) 127;
            _byte_e = null;

            _short_a = Short.MIN_VALUE;
            _short_b = Short.MAX_VALUE;
            _short_c = Short.MIN_VALUE;
            _short_d = Short.MAX_VALUE;
            _short_e = null;

            _int_a = Integer.MIN_VALUE;
            _int_b = Integer.MAX_VALUE;
            _int_c = Integer.MIN_VALUE;
            _int_d = Integer.MAX_VALUE;
            _int_e = null;

            _long_a = Long.MIN_VALUE;
            _long_b = Long.MAX_VALUE;
            _long_c = Long.MIN_VALUE;
            _long_d = Long.MAX_VALUE;
            _long_e = null;

            _float_a = Float.MIN_VALUE;
            _float_b = Float.MAX_VALUE;
            _float_c = Float.MIN_VALUE;
            _float_d = Float.MAX_VALUE;
            _float_e = null;

            _double_a = Double.MIN_VALUE;
            _double_b = Double.MAX_VALUE;
            _double_c = Double.MIN_VALUE;
            _double_d = Double.MAX_VALUE;
            _double_e = null;

            _string_a = "Hello";
            _string_b = "";
            _string_c = null;

            _date_a = _testDate;
            _date_b = new Date(0);
            _date_c = null;

            _class_a = Boolean.class;
            _class_b = Character.class;
            _class_c = Byte.class;
            _class_d = Short.class;
            _class_e = Integer.class;
            _class_f = Long.class;
            _class_g = Float.class;
            _class_h = Double.class;
            _class_i = String.class;
            _class_j = null;

            _sb_a = new StringBuffer("holstein");
            _sb_b = new StringBuffer();
            _sb_c = null;
            _sb_d = new StringBuffer("viper");

            _sbld_a = new StringBuilder("holstein");
            _sbld_b = new StringBuilder();
            _sbld_c = null;
            _sbld_d = new StringBuilder("viper");

            _bigInt = new BigInteger("25");
            _bigIntO = new BigInteger("25");
            _bigDec = new BigDecimal("25.0");
            _bigDecO = new BigDecimal("25.0");

            TestObject a = new TestObject("A");
            TestObject b = new TestObject("B");
            TestObject c = new TestObject("C");
            a._other = b;
            b._other = c;
            c._other = a;
            _cycleTest = a;

            _arrayList_empty = new ArrayList<>();
            _arrayList_1 = new ArrayList<>();
            _arrayList_1.add("should be no id, no type");
            _arrayList_2 = new ArrayList<>();
            _arrayList_2.add("should have type, but no id");
            _arrayList_3 = new ArrayList<>();
            _arrayList_3.add("should have id and type");
            _arrayList_4 = _arrayList_3;
            _arrayList_5 = new ArrayList<>();
            _arrayList_5.add("should have id, but no type");
            _arrayList_6 = _arrayList_5;

            _hashMap_empty = new HashMap<>();
            _hashMap_1 = new HashMap<>();
            _hashMap_1.put("mapkey", "should have no id or type");
            _hashMap_2 = new HashMap<>();
            _hashMap_2.put("mapkey", "should have type, but no id");
            _hashMap_3 = new HashMap<>();
            _hashMap_3.put("mapkey", "should have id and type");
            _hashMap_4 = _hashMap_3;
            _hashMap_5 = new HashMap<>();
            _hashMap_5.put("mapkey", "should have id, but no type");
            _hashMap_6 = _hashMap_5;

            _stringArray_empty = new String[0];
            _stringArray_1 = new String[]{"should have no id, no type"};
            _stringArray_2 = new String[]{"should have type, but no id"};
            _stringArray_3 = new String[]{"should have id and type"};
            _stringArray_4 = _stringArray_3;
            _stringArray_5 = new String[]{"should have id, but not type"};
            _stringArray_6 = _stringArray_5;
        }

        private boolean _boolean_a;
        private boolean _boolean_b;
        private Boolean _boolean_c;
        private Boolean _boolean_d;
        private Boolean _boolean_e;
        private char _char_a;
        private char _char_b;
        private char _char_c;
        private Character _char_d;
        private Character _char_e;
        private Character _char_f;
        private Character _char_g;
        private byte _byte_a;
        private byte _byte_b;
        private Byte _byte_c;
        private Byte _byte_d;
        private Byte _byte_e;
        private short _short_a;
        private short _short_b;
        private Short _short_c;
        private Short _short_d;
        private Short _short_e;
        private int _int_a;
        private int _int_b;
        private Integer _int_c;
        private Integer _int_d;
        private Integer _int_e;
        private long _long_a;
        private long _long_b;
        private Long _long_c;
        private Long _long_d;
        private Long _long_e;
        private float _float_a;
        private float _float_b;
        private Float _float_c;
        private Float _float_d;
        private Float _float_e;
        private double _double_a;
        private double _double_b;
        private Double _double_c;
        private Double _double_d;
        private Double _double_e;
        private String _string_a;
        private String _string_b;
        private String _string_c;
        private Date _date_a;
        private Date _date_b;
        private Date _date_c;
        private Class<?> _class_a;
        private Class<?> _class_b;
        private Class<?> _class_c;
        private Class<?> _class_d;
        private Class<?> _class_e;
        private Class<?> _class_f;
        private Class<?> _class_g;
        private Class<?> _class_h;
        private Class<?> _class_i;
        private Class<?> _class_j;
        private StringBuffer _sb_a;
        private StringBuffer _sb_b;
        private StringBuffer _sb_c;
        private Object _sb_d;
        private StringBuilder _sbld_a;
        private StringBuilder _sbld_b;
        private StringBuilder _sbld_c;
        private Object _sbld_d;
        private BigInteger _bigInt;
        private Object _bigIntO;
        private BigDecimal _bigDec;
        private Object _bigDecO;
        private TestObject _cycleTest;
        private List<?> _arrayList_empty;
        private List<String> _arrayList_1;
        private List<String> _arrayList_2;
        private List<String> _arrayList_3;
        private List<String> _arrayList_4;
        private List<String> _arrayList_5;
        private List<String> _arrayList_6;
        private Map _hashMap_empty;
        private Map _hashMap_1;
        private Map _hashMap_2;
        private Map _hashMap_3;
        private Map _hashMap_4;
        private Map _hashMap_5;
        private Map _hashMap_6;
        private String[] _stringArray_empty;
        private String[] _stringArray_1;
        private Object[] _stringArray_2;
        private Object[] _stringArray_3;
        private Object[] _stringArray_4;
        private String[] _stringArray_5;
        private Object[] _stringArray_6;
    }

    public static class P1
    {
        public int x,y;
    }

    public static class P2 extends P1
    {
        public String x, y, z;
    }

    @Test
    void testFieldNameShadowing()
    {
        P2 p2 = new P2();
        p2.x = "1";
        p2.y = "2";
        p2.z = "3";

        ((P1)p2).x = 4;
        ((P1)p2).y = 5;

        String json = TestUtil.toJson(p2);
        assert json.contains("P1.x");
        assert json.contains("P1.y");
        P2 p2copy = TestUtil.toObjects(json, null);
        assert p2copy.x.equals("1");
        assert p2copy.y.equals("2");
        assert p2copy.z.equals("3");
        assert ((P1)p2copy).x == 4;
        assert ((P1)p2copy).y == 5;
    }

    @Test
    public void testFieldNameNotShadowed()
    {
        P1 p1 = new P1();
        p1.x = 10;
        p1.y = 20;

        String json = TestUtil.toJson(p1);
        assert !json.contains("P1.x");
        assert !json.contains("P1.y");
        P1 p1copy = TestUtil.toObjects(json, null);
        assert p1copy.x == 10;
        assert p1copy.y == 20;
    }
}
