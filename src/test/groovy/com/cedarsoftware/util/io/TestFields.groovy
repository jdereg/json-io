package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
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
@CompileStatic
class TestFields
{
    public static Date _testDate = new Date()

    static class PainfulToSerialize
    {
        ClassLoader classLoader = TestFields.class.getClassLoader()
        String name
    }

    static class MorePainfulToSerialize extends PainfulToSerialize
    {
        int age
    }

    static class TestVanillaFields
    {
        Object name
        Object salary
        Comparable age
        Serializable alive
        Object garbage
    }

    public static class TestLocale implements Serializable
    {
        private Locale _loc;
    }

    /**
     * Test direct fields for all types, primitives, special handled fields
     * like Date, String, and Class, plus regular objects, and circular
     * references.
     */
    private static class ManyFields implements Serializable
    {
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

        private Class _class_a;
        private Class _class_b;
        private Class _class_c;
        private Class _class_d;
        private Class _class_e;
        private Class _class_f;
        private Class _class_g;
        private Class _class_h;
        private Class _class_i;
        private Class _class_j;

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

        // Cycle test
        private TestObject _cycleTest;

        // Ensure @type is dropped when Collection field type matches instance type
        // Normally, this is poor coding style, however, the @type field can be dropped
        // in these cases, making the JSON output smaller.
        private ArrayList _arrayList_empty;
        private ArrayList _arrayList_1;
        private List _arrayList_2;
        private List _arrayList_3;
        private List _arrayList_4;
        private ArrayList _arrayList_5;
        private List _arrayList_6;

        private HashMap _hashMap_empty;
        private HashMap _hashMap_1;
        private Map _hashMap_2;
        private Map _hashMap_3;
        private Map _hashMap_4;
        private HashMap _hashMap_5;
        private Map _hashMap_6;

        private String[] _stringArray_empty;
        private String[] _stringArray_1;
        private Object[] _stringArray_2;
        private Object[] _stringArray_3;
        private Object[] _stringArray_4;
        private String[] _stringArray_5;
        private Object[] _stringArray_6;

        private void init()
        {
            _boolean_a = true;
            _boolean_b = false;
            _boolean_c = new Boolean(true)
            _boolean_d = new Boolean(false)
            _boolean_e = null;

            _char_a = 'a'
            _char_b = '\t'
            _char_c = '\u0004'
            _char_d = new Character('a' as char)
            _char_e = new Character('\t' as char)
            _char_f = new Character('\u0002' as char)
            _char_g = null;

            _byte_a = -128;
            _byte_b = 127;
            _byte_c = new Byte((byte) -128)
            _byte_d = new Byte((byte) 127)
            _byte_e = null;

            _short_a = Short.MIN_VALUE;
            _short_b = Short.MAX_VALUE;
            _short_c = new Short(Short.MIN_VALUE)
            _short_d = new Short(Short.MAX_VALUE)
            _short_e = null;

            _int_a = Integer.MIN_VALUE;
            _int_b = Integer.MAX_VALUE;
            _int_c = new Integer(Integer.MIN_VALUE)
            _int_d = new Integer(Integer.MAX_VALUE)
            _int_e = null;

            _long_a = Long.MIN_VALUE;
            _long_b = Long.MAX_VALUE;
            _long_c = new Long(Long.MIN_VALUE)
            _long_d = new Long(Long.MAX_VALUE)
            _long_e = null;

            _float_a = Float.MIN_VALUE;
            _float_b = Float.MAX_VALUE;
            _float_c = new Float(Float.MIN_VALUE)
            _float_d = new Float(Float.MAX_VALUE)
            _float_e = null;

            _double_a = Double.MIN_VALUE;
            _double_b = Double.MAX_VALUE;
            _double_c = new Double(Double.MIN_VALUE)
            _double_d = new Double(Double.MAX_VALUE)
            _double_e = null;

            _string_a = "Hello"
            _string_b = ""
            _string_c = null;

            _date_a = _testDate;
            _date_b = new Date(0)
            _date_c = null;

            _class_a = boolean.class;
            _class_b = char.class;
            _class_c = byte.class;
            _class_d = short.class;
            _class_e = int.class;
            _class_f = long.class;
            _class_g = float.class;
            _class_h = double.class;
            _class_i = String.class;
            _class_j = null;

            _sb_a = new StringBuffer("holstein")
            _sb_b = new StringBuffer()
            _sb_c = null;
            _sb_d = new StringBuffer("viper")

            _sbld_a = new StringBuilder("holstein")
            _sbld_b = new StringBuilder()
            _sbld_c = null;
            _sbld_d = new StringBuilder("viper")

            _bigInt = new BigInteger("25")
            _bigIntO = new BigInteger("25")
            _bigDec = new BigDecimal("25.0")
            _bigDecO = new BigDecimal("25.0")

            TestObject a = new TestObject("A")
            TestObject b = new TestObject("B")
            TestObject c = new TestObject("C")
            a._other = b;
            b._other = c;
            c._other = a;
            _cycleTest = a;

            _arrayList_empty = new ArrayList()
            _arrayList_1 = new ArrayList()
            _arrayList_1.add("should be no id, no type")
            _arrayList_2 = new ArrayList()
            _arrayList_2.add("should have type, but no id")
            _arrayList_3 = new ArrayList()
            _arrayList_3.add("should have id and type")
            _arrayList_4 = _arrayList_3;
            _arrayList_5 = new ArrayList()
            _arrayList_5.add("should have id, but no type")
            _arrayList_6 = _arrayList_5;

            _hashMap_empty = new HashMap()
            _hashMap_1 = new HashMap()
            _hashMap_1.mapkey = "should have no id or type"
            _hashMap_2 = new HashMap()
            _hashMap_2.mapkey = "should have type, but no id"
            _hashMap_3 = new HashMap()
            _hashMap_3.mapkey = "should have id and type"
            _hashMap_4 = _hashMap_3;
            _hashMap_5 = new HashMap()
            _hashMap_5.mapkey = 'should have id, but no type'
            _hashMap_6 = _hashMap_5;

            _stringArray_empty = [] as String[];
            _stringArray_1 = ['should have no id, no type'] as String[]
            _stringArray_2 = ['should have type, but no id'] as String[]
            _stringArray_3 = ['should have id and type'] as String[]
            _stringArray_4 = _stringArray_3;
            _stringArray_5 = ['should have id, but not type'] as String[]
            _stringArray_6 = _stringArray_5;
        }
    }

    @Test
    void testFields()
    {
        ManyFields obj = new ManyFields()
        obj.init()
        String jsonOut = TestUtil.getJsonString(obj)
        TestUtil.printLine(jsonOut)

        ManyFields root = (ManyFields) TestUtil.readJsonObject(jsonOut)
        assertFields(root)
    }

    private static void assertFields(ManyFields root)
    {
        assertTrue(root._boolean_a)
        assertFalse(root._boolean_b)
        assertTrue(root._boolean_c.booleanValue())
        assertFalse(root._boolean_d.booleanValue())
        assertNull(root._boolean_e)

        assertTrue(root._char_a == 'a')
        assertTrue(root._char_b == '\t')
        assertTrue(root._char_c == '\u0004')
        assertTrue(root._char_d.equals(new Character('a' as char)))
        assertTrue(root._char_e.equals(new Character('\t' as char)))
        assertTrue(root._char_f.equals(new Character('\u0002' as char)))
        assertNull(root._char_g)

        assertTrue(root._byte_a == Byte.MIN_VALUE)
        assertTrue(root._byte_b == Byte.MAX_VALUE)
        assertTrue(root._byte_c.equals(new Byte(Byte.MIN_VALUE)))
        assertTrue(root._byte_d.equals(new Byte(Byte.MAX_VALUE)))
        assertNull(root._byte_e)

        assertTrue(root._short_a == Short.MIN_VALUE)
        assertTrue(root._short_b == Short.MAX_VALUE)
        assertTrue(root._short_c.equals(new Short(Short.MIN_VALUE)))
        assertTrue(root._short_d.equals(new Short(Short.MAX_VALUE)))
        assertNull(root._short_e)

        assertTrue(root._int_a == Integer.MIN_VALUE)
        assertTrue(root._int_b == Integer.MAX_VALUE)
        assertTrue(root._int_c.equals(new Integer(Integer.MIN_VALUE)))
        assertTrue(root._int_d.equals(new Integer(Integer.MAX_VALUE)))
        assertNull(root._int_e)

        assertTrue(root._long_a == Long.MIN_VALUE)
        assertTrue(root._long_b == Long.MAX_VALUE)
        assertTrue(root._long_c.equals(new Long(Long.MIN_VALUE)))
        assertTrue(root._long_d.equals(new Long(Long.MAX_VALUE)))
        assertNull(root._long_e)

        assertTrue(root._float_a == Float.MIN_VALUE)
        assertTrue(root._float_b == Float.MAX_VALUE)
        assertTrue(root._float_c.equals(new Float(Float.MIN_VALUE)))
        assertTrue(root._float_d.equals(new Float(Float.MAX_VALUE)))
        assertNull(root._float_e)

        assertTrue(root._double_a == Double.MIN_VALUE)
        assertTrue(root._double_b == Double.MAX_VALUE)
        assertTrue(root._double_c.equals(new Double(Double.MIN_VALUE)))
        assertTrue(root._double_d.equals(new Double(Double.MAX_VALUE)))
        assertNull(root._double_e)

        assertTrue(root._string_a.equals("Hello"))
        assertTrue(root._string_b.equals(""))
        assertNull(root._string_c)

        assertTrue(root._date_a.equals(_testDate))
        assertTrue(root._date_b.equals(new Date(0)))
        assertNull(root._date_c)

        assertTrue(root._class_a.equals(boolean.class))
        assertTrue(root._class_b.equals(char.class))
        assertTrue(root._class_c.equals(byte.class))
        assertTrue(root._class_d.equals(short.class))
        assertTrue(root._class_e.equals(int.class))
        assertTrue(root._class_f.equals(long.class))
        assertTrue(root._class_g.equals(float.class))
        assertTrue(root._class_h.equals(double.class))
        assertTrue(root._class_i.equals(String.class))
        assertNull(root._class_j)

        assertTrue("holstein".equals(root._sb_a.toString()))
        assertTrue(root._sb_b.toString().equals(""))
        assertNull(root._sb_c)
        assertTrue("viper".equals(root._sb_d.toString()))

        assertTrue("holstein".equals(root._sb_a.toString()))
        assertTrue(root._sb_b.toString().equals(""))
        assertNull(root._sb_c)
        assertTrue("viper".equals(root._sb_d.toString()))

        assertTrue(root._bigInt.equals(new BigInteger("25")))
        assertTrue(root._bigIntO.equals(new BigInteger("25")))
        assertTrue(root._bigDec.equals(new BigDecimal("25.0")))
        assertTrue(root._bigDec.equals(new BigDecimal("25.0")))

        assertTrue(root._cycleTest._name.equals("A"))
        assertTrue(root._cycleTest._other._name.equals("B"))
        assertTrue(root._cycleTest._other._other._name.equals("C"))
        assertTrue(root._cycleTest._other._other._other._name.equals("A"))
        assertTrue(root._cycleTest == root._cycleTest._other._other._other)

        assertTrue(root._arrayList_empty.isEmpty())
        assertTrue(root._arrayList_1.size() == 1)
        assertTrue(root._arrayList_2.size() == 1)
        assertTrue(root._arrayList_3.size() == 1)
        assertTrue(root._arrayList_4 == root._arrayList_3)
        assertTrue(root._arrayList_5.size() == 1)
        assertTrue(root._arrayList_6 == root._arrayList_5)

        assertTrue(root._hashMap_empty.isEmpty())
        assertTrue(root._hashMap_1.size() == 1)
        assertTrue(root._hashMap_2.size() == 1)
        assertTrue(root._hashMap_3.size() == 1)
        assertTrue(root._hashMap_4 == root._hashMap_3)
        assertTrue(root._hashMap_5.size() == 1)
        assertTrue(root._hashMap_6 == root._hashMap_5)

        assertTrue(root._stringArray_empty.length == 0)
        assertTrue(root._stringArray_1.length == 1)
        assertTrue(root._stringArray_2.length == 1)
        assertTrue(root._stringArray_3.length == 1)
        assertTrue(root._stringArray_4 == root._stringArray_3)
        assertTrue(root._stringArray_5.length == 1)
        assertTrue(root._stringArray_6 == root._stringArray_5)
    }

    @Test
    void testAlwaysShowType()
    {
        ManyFields tf = new ManyFields()
        tf.init()
        def json0 = JsonWriter.objectToJson(tf, [(JsonWriter.TYPE):true] as Map)
        def json1 = JsonWriter.objectToJson(tf)
        assertTrue(json0.length() > json1.length())
    }

    @Test
    void testReconstituteFields()
    {
        ManyFields testFields = new ManyFields()
        testFields.init()
        String json0 = TestUtil.getJsonString(testFields)
        TestUtil.printLine("json0=" + json0)
        Map testFields2 = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS):true] as Map)

        String json1 = TestUtil.getJsonString(testFields2)
        TestUtil.printLine("json1=" + json1)

        ManyFields testFields3 = (ManyFields) TestUtil.readJsonObject(json1)
        assertFields(testFields3)   // Re-written from Map of Maps and re-loaded correctly
        assertTrue(json0.equals(json1))
    }

    @Test
    void testAssignToObjectField()
    {
        Class clazz = TestVanillaFields.class
        String json = '{"@type":"' + clazz.name + '","name":"Nakamoto","salary":100.45,"age":48,"alive":true,"garbage":null}'
        TestVanillaFields vanilla = (TestVanillaFields) TestUtil.readJsonObject(json)
        assertEquals(vanilla.name, "Nakamoto")
        assertEquals(vanilla.salary as Double, 100.45d, 0.0001d)
        assertEquals(vanilla.age, 48L)
        assertEquals(vanilla.alive, true)
        assertEquals(vanilla.garbage, null)
    }

    @Test
    void testExternalFieldSpecifiedBadName()
    {
        Map<Class, List<String>> fieldSpecifiers = new HashMap<Class, List<String>>()
        List<String> fields = new ArrayList<String>()
        fields.add("mane")
        fieldSpecifiers.put(PainfulToSerialize.class, fields)

        PainfulToSerialize painful = new PainfulToSerialize()
        painful.name = "Android rocks"

        Map args = new HashMap()
        args.put(JsonWriter.FIELD_SPECIFIERS, fieldSpecifiers)

        assertThrows(Exception.class, {  JsonWriter.objectToJson(painful, args) })
    }

    @Test
    void testExternalFieldSpecifier()
    {
        Map<Class, List<String>> fieldSpecifiers = new HashMap<>()
        List<String> fields = new ArrayList<>()
        fields.add("name")
        fieldSpecifiers.put(PainfulToSerialize.class, fields)

        PainfulToSerialize painful = new PainfulToSerialize()
        painful.name = "Android rocks"

        Map args = new HashMap()
        args.put(JsonWriter.FIELD_SPECIFIERS, fieldSpecifiers)
        String json = JsonWriter.objectToJson(painful, args)
        Map check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 1)
        assertTrue(check.containsKey("name"))
    }

    @Test
    void testExternalFieldSpecifierInheritance()
    {
        Map<Class, List<String>> fieldSpecifiers = [(PainfulToSerialize.class): ['name']] as Map
        MorePainfulToSerialize painful = new MorePainfulToSerialize()
        painful.name = "Android rocks"
        painful.age = 50;

        def args = [(JsonWriter.FIELD_SPECIFIERS):fieldSpecifiers] as Map
        String json = JsonWriter.objectToJson(painful, args)
        Map check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 1)
        assertTrue(check.containsKey("name"))

        List<String> fields2 = new ArrayList<>()
        fields2.add("age")
        fields2.add("name")
        fieldSpecifiers.put(MorePainfulToSerialize.class, fields2)
        json = JsonWriter.objectToJson(painful, args)
        check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 2)
        assertTrue(check.containsKey("name"))
        assertTrue(check.containsKey("age"))
    }

    @Test
    void testLocaleAsField()
    {
        Locale locale = Locale.getDefault()
        TestLocale tl = new TestLocale()
        tl._loc = locale;
        String json = TestUtil.getJsonString(tl)
        TestUtil.printLine("json=" + json)

        tl = (TestLocale) TestUtil.readJsonObject(json)
        assertTrue(locale.equals(tl._loc))
    }
    
    @Test
    void testFieldBlackList()
    {
        Map<Class, List<String>> blackLists = [(PainfulToSerialize.class):['classLoader']] as Map
        PainfulToSerialize painful = new PainfulToSerialize()
        painful.name = "Android rocks"

        def args = [(JsonWriter.FIELD_NAME_BLACK_LIST):blackLists] as Map
        String json = JsonWriter.objectToJson(painful, args)
        Map check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 1)
        assertTrue(check.containsKey("name"))
    }

    @Test
    void testFieldBlackListInheritance()
    {
        Map<Class, List<String>> blackLists = [(PainfulToSerialize.class):['classLoader']] as Map
        MorePainfulToSerialize painful = new MorePainfulToSerialize()
        painful.name = "Android rocks"
        painful.age = 50;

        def args = [(JsonWriter.FIELD_NAME_BLACK_LIST):blackLists] as Map
        String json = JsonWriter.objectToJson(painful, args)
        Map check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 2)
        assertTrue(check.containsKey("name"))
        assertTrue(check.containsKey("age"))
    }

    @Test
    void testFieldBlackListPriorityToSpecifier()
    {
        Map<Class, List<String>> fieldSpecifiers = [(PainfulToSerialize.class):['name','classLoader']] as Map
        Map<Class, List<String>> blackLists = [(PainfulToSerialize.class):['classLoader']] as Map
        
        PainfulToSerialize painful = new PainfulToSerialize()
        painful.name = "Android rocks"

        Map args = new HashMap()
        args.put(JsonWriter.FIELD_SPECIFIERS, fieldSpecifiers)
        args.put(JsonWriter.FIELD_NAME_BLACK_LIST, blackLists)
        String json = JsonWriter.objectToJson(painful, args)
        Map check = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue(check.size() == 1)
        assertTrue(check.containsKey("name"))
    }

}
