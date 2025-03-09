package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

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
public class NoTypeTest
{
    static class Person
    {
        String name;
        int age;
        double salary;
        int creditScore;
    }

    @Test
    void personTestToPerson()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/person.json");
        Person person = TestUtil.toObjects(json, Person.class);
        assert person.name.equals("Joe");
        assert person.age == 50;
        assert person.salary == 225000.0d;
        assert person.creditScore == 0;
    }

    @Test
    void personTestToPersonWithReadOptionJavaObjects()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/person.json");
        String msg = assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), Person.class); }).getMessage();
        assert msg.contains("isReturningJsonObjects");
    }

    @Test
    void personTestToMap()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/person.json");
        Map<String, Object> person = TestUtil.toObjects(json, Map.class);
        assert person.get("name").equals("Joe");
        assert (long)person.get("age") == 50L;  // Comes in as Long because all JSON integer values are long
        assert (double)person.get("salary") == 225000.0d;
        assert person.get("creditScore") == null;
    }

    @Test
    void personsTestWithPersonArrayRoot()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/persons.json");
        Person[] persons = TestUtil.toObjects(json, Person[].class);
        
        assert persons[0].name.equals("Joe");
        assert persons[0].age == 50;
        assert persons[0].salary == 225000.0d;
        assert persons[0].creditScore == 0;

        assert persons[1].name.equals("Jane");
        assert persons[1].age == 40;
        assert persons[1].salary == 314000.0d;
        assert persons[1].creditScore == 0;
    }

    @Test
    void personsTestWithPersonArrayRootAsNativeJsonObjects()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/persons.json");

        try {
            Object o = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), Person[].class);
            fail();
        } catch (JsonIoException e) {
            assert e.getMessage().contains("isReturningJsonObjects");
        }
    }

    @Test
    void personsTestWithNullRoot()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/persons.json");
        Object[] persons = TestUtil.toObjects(json, null);
        Map<String, Object> joe = (Map<String, Object>)persons[0];
        Map<String, Object> jane = (Map<String, Object>) persons[1];

        assert joe.get("name").equals("Joe");
        assert joe.get("age").equals(50L);
        assert joe.get("salary").equals(225000.0d);
        assert joe.get("creditScore") == null;

        assert jane.get("name").equals("Jane");
        assert jane.get("age").equals(40L);
        assert jane.get("salary").equals(314000.0d);
        assert jane.get("creditScore") == null;
    }

    @Test
    void personsTestWithMapRoot()
    {
        String json = ClassUtilities.loadResourceAsString("noTypes/persons.json");
        Exception e = assertThrows(JsonIoException.class, () -> { Map x = TestUtil.toObjects(json, Map.class); });
        assert e.getCause() instanceof IllegalArgumentException;
    }

    @Test
    void testStringArrayAtRoot() {
        String json = "[\"foo\", null, \"baz\"]";

        // String[]
        String[] strings = TestUtil.toObjects(json, String[].class);
        assert strings.length == 3;
        assert strings[0].equals("foo");
        assert strings[1] == null;
        assert strings[2].equals("baz");

        // Implied Object[]
        Object[] array = TestUtil.toObjects(json, null);
        assert array.length == 3;
        assert array[0].equals("foo");
        assert array[1] == null;
        assert array[2].equals("baz");

        // Explicit Object[]
        array = TestUtil.toObjects(json, Object[].class);
        assert array.length == 3;
        assert array[0].equals("foo");
        assert array[1] == null;
        assert array[2].equals("baz");
    }
    
    @Test
    void testSpecificRootsWithReturnAsJsonObjects() {
        // String[]
        String json = "[\"foo\", null, \"baz\"]";
        String[] strings = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), String[].class);
        assert strings.length == 3;
        assertEquals("foo", strings[0]);
        assertNull(strings[1]);
        assertEquals("baz", strings[2]);

        Object[] objects = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assert objects.length == 3;
        assertEquals("foo", objects[0]);
        assertNull(objects[1]);
        assertEquals("baz", objects[2]);

        objects = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), Object[].class);
        assert objects.length == 3;
        assertEquals("foo", objects[0]);
        assertNull(objects[1]);
        assertEquals("baz", objects[2]);

        // Number[]
        String json1 = "[16, null, 3.14159]";
        Number[] numbers = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), Number[].class);
        assert numbers.length == 3;
        assertEquals(16L, numbers[0]);
        assertNull(numbers[1]);
        assertEquals(3.14159, numbers[2]);

        objects = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assert objects.length == 3;
        assertEquals(16L, objects[0]);
        assertNull(objects[1]);
        assertEquals(3.14159, objects[2]);

        objects = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), Object[].class);
        assert objects.length == 3;
        assertEquals(16L, objects[0]);
        assertNull(objects[1]);
        assertEquals(3.14159, objects[2]);

        strings = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), String[].class);
        assert strings.length == 3;
        assertEquals("16", strings[0]);
        assertNull(strings[1]);
        assertEquals("3.14159", strings[2]);

        // error - cannot stuff Number[] with Strings
        final String json2 = "[\"foo\", null ,\"baz\"]";
        assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json2, new ReadOptionsBuilder().returnAsJsonObjects().build(), Number[].class); });

        // AtomicInteger
        String json3 = "75.1";
        AtomicInteger x = TestUtil.toObjects(json3, new ReadOptionsBuilder().returnAsJsonObjects().build(), AtomicInteger.class);
        assert x.get() == 75;   // Expected coercion into 'integer' space
    }

    @Test
    void testSpecificRootsWithReturnAsJavaObjects() {
        // String[]
        String json = "[\"foo\", null ,\"baz\"]";
        String[] strings = TestUtil.toObjects(json, null, String[].class);
        assert strings.length == 3;
        assertEquals("foo", strings[0]);
        assertNull(strings[1]);
        assertEquals("baz", strings[2]);

        Object[] objects = TestUtil.toObjects(json, null, null);
        assert objects.length == 3;
        assertEquals("foo", objects[0]);
        assertNull(objects[1]);
        assertEquals("baz", objects[2]);

        objects = TestUtil.toObjects(json, null, Object[].class);
        assert objects.length == 3;
        assertEquals("foo", objects[0]);
        assertNull(objects[1]);
        assertEquals("baz", objects[2]);

        // Number[]
        String json1 = "[16, null, 3.14159]";
        Number[] numbers = TestUtil.toObjects(json1, null, Number[].class);
        assert numbers.length == 3;
        assertEquals(16L, numbers[0]);
        assertNull(numbers[1]);
        assertEquals(3.14159, numbers[2]);

        objects = TestUtil.toObjects(json1, null, null);
        assert objects.length == 3;
        assertEquals(16L, objects[0]);
        assertNull(objects[1]);
        assertEquals(3.14159, objects[2]);

        objects = TestUtil.toObjects(json1, null, Object[].class);
        assert objects.length == 3;
        assertEquals(16L, objects[0]);
        assertNull(objects[1]);
        assertEquals(3.14159, objects[2]);

        strings = TestUtil.toObjects(json1, null, String[].class);
        assert strings.length == 3;
        assertEquals("16", strings[0]);
        assertNull(strings[1]);
        assertEquals("3.14159", strings[2]);

        // error - cannot stuff Number[] with Strings
        final String json2 = "[\"foo\", null ,\"baz\"]";
        assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json2, null, Number[].class); });

        // AtomicInteger
        String json3 = "75.1";
        AtomicInteger x = TestUtil.toObjects(json3, null, AtomicInteger.class);
        assert x.get() == 75;   // Expected coercion into 'integer' space
    }

    @Test
    void testStringArrayHeteroItems() {
        String json = "[\"foo\", null , 25, true, 3.14159]";

        // String[]
        String[] strings = TestUtil.toObjects(json, String[].class);
        assert strings.length == 5;
        assert strings[0].equals("foo");
        assert strings[1] == null;
        assert strings[2].equals("25"); // Converted during parsing to component type of array[]
        assert strings[3].equals("true"); // Converted during parsing to component type of array[]
        assert strings[4].equals("3.14159"); // Converted during parsing to component type of array[]

        // Implied Object[]
        Object[] array = TestUtil.toObjects(json, null);
        assert array.length == 5;
        assert array[0].equals("foo");
        assert array[1] == null;
        assert array[2].equals(25L);    // No conversion when Object[]
        assert array[3].equals(true);    // No conversion when Object[]
        assert array[4].equals(3.14159d);    // No conversion when Object[]

        // Explicit Object[]
        array = TestUtil.toObjects(json, Object[].class);
        assert array.length == 5;
        assert array[0].equals("foo");
        assert array[1] == null;
        assert array[2].equals(25L);    // No coversion when implied Object[]
        assert array[3].equals(true);    // No coversion when implied Object[]
        assert array[4].equals(3.14159d);    // No coversion when implied Object[]
    }

    @Test
    void testStringArrayHeteroItemsNoChance() {
        // String[] does not allow an array as an element
        assertThrows(JsonIoException.class, () -> {String[] strings = TestUtil.toObjects("[\"foo\", [null]]", String[].class); });

        String json = "[\"foo\", null]";

        // Explicit String[]
        String[] strings = TestUtil.toObjects(json, String[].class);
        assert strings.length == 2;
        assert strings[0].equals("foo");
        assert strings[1] == null;

        json = "[\"foo\", [null]]";

        // Implied Object[]
        Object[] array = TestUtil.toObjects(json, null);
        assert array.length == 2;
        assert array[0].equals("foo");
        assert DeepEquals.deepEquals(array[1], new Object[] {null});

        // Explicit Object[]
        array = TestUtil.toObjects(json, Object[].class);
        assert array.length == 2;
        assert array[0].equals("foo");
        assert DeepEquals.deepEquals(array[1], new Object[] {null});
    }

    @Test
    void testStringArrayHeteroItemsNoChance2() {
        String json = "[\"foo\", {}]";

        // String[]
        assertThrows(JsonIoException.class, () -> {String[] strings = TestUtil.toObjects(json, String[].class); });

        // Implied Object[]
        Object[] array = TestUtil.toObjects(json, null);
        assert array.length == 2;
        assert array[0].equals("foo");
        assert array[1] instanceof JsonObject;

        // Explicit Object[]
        array = TestUtil.toObjects(json, Object[].class);
        assert array.length == 2;
        assert array[0].equals("foo");
        assert array[1] instanceof JsonObject;
    }

    @Test
    void testNoType()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.clear();
        cal.set(-700, 5, 10);

        Junk j = new Junk();
        j.setStuff(new Object[]{1, 2L, BigInteger.valueOf(3), new BigDecimal("4"), cal.getTime(), "Hello", Junk.class});
        j.setName("Zeus");
        j.setThings(listOf(1, 2L, BigInteger.valueOf(3), new BigDecimal("4"), cal.getTime(), "Hello", Junk.class));
        j.getNamesToAge().put("Appollo", 2500L);
        j.getNamesToAge().put("Hercules", 2489);
        j.getNamesToAge().put("Poseidon", BigInteger.valueOf(2502));
        j.getNamesToAge().put("Aphrodite", "2499.0");
        j.getNamesToAge().put("Zeus", cal.getTime());

        String json = TestUtil.toJson(j);
        String json2 = TestUtil.toJson(j, new WriteOptionsBuilder().showTypeInfoNever().build());
        assert !json.equals(json2);

        String expectedJson = "{\"name\":\"Zeus\",\"things\":[1,2,\"3\",\"4\",-84243801600000,\"Hello\",\"com.cedarsoftware.io.NoTypeTest$Junk\"],\"namesToAge\":{\"Appollo\":2500,\"Hercules\":2489,\"Poseidon\":\"2502\",\"Aphrodite\":\"2499.0\",\"Zeus\":-84243801600000},\"stuff\":[1,2,\"3\",\"4\",-84243801600000,\"Hello\",\"com.cedarsoftware.io.NoTypeTest$Junk\"]}";

        assert JsonParser.parseString(json2).equals(JsonParser.parseString(expectedJson));
    }

    @Test
    void testItems()
    {
        String json = "{\"groups\":[\"one\",\"two\",\"three\"],\"search\":{\"datalist\":[]}}";

        Map map = TestUtil.toObjects(json, null);
        Object[] groups = (Object[]) map.get("groups");
        assert groups.length == 3;
        assert groups[0].equals("one");
        assert groups[1].equals("two");
        assert groups[2].equals("three");
        Map search = (Map) map.get("search");
        Object[] dataList = (Object[]) search.get("datalist");
        assert dataList.length == 0;

        map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        groups = (Object[]) (map.get("groups"));
        assert groups.length == 3;
        assert groups[0].equals("one");
        assert groups[1].equals("two");
        assert groups[2].equals("three");
        search = (Map) map.get("search");
        Object[] datalist = (Object[]) search.get("datalist");
        assert datalist.length == 0;
    }

    @Test
    void testCollections()
    {
        CollectionTest cols = new CollectionTest();
        cols.setFoos(listOf(1, 2, "4", 8));
        cols.setBars(new Object[]{1, 3, "5", 7});

        String json = TestUtil.toJson(cols, new WriteOptionsBuilder().showTypeInfoNever().build());
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        Object[] listFoos = (Object[]) map.get("foos");
        assert listFoos.length == 4;
        assert listFoos[0].equals(1L);
        assert listFoos[1].equals(2L);
        assert listFoos[2].equals("4");
        assert listFoos[3].equals(8L);

        Object[] listBars = (Object[]) map.get("bars");
        assert listBars.length == 4;
        assert listBars[0].equals(1L);
        assert listBars[1].equals(3L);
        assert listBars[2].equals("5");
        assert listBars[3].equals(7L);

        json = TestUtil.toJson(listOf(1, 2, 3, 4), new WriteOptionsBuilder().showTypeInfoNever().build());
        assert "[1,2,3,4]".equals(json);

        json = TestUtil.toJson(new Object[]{1, 2, 3, 4}, new WriteOptionsBuilder().showTypeInfoNever().build());
        assert "[1,2,3,4]".equals(json);
    }

    @Test
    void testObjectArray()
    {
        Object[] array = new Object[]{new Object[]{1L, 2L, 3L}, new Object[] {'a', 'b', 'c'}};
        String json = TestUtil.toJson(array);
        Object[] list = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assert list.length == 2;
        Object[] list0 = (Object[]) list[0];
        assert list0.length == 3;
        Object[] list1 = (Object[]) list[1];
        assert list1.length == 3;
        
        assert list0[0].equals(1L);
        assert list0[1].equals(2L);
        assert list0[2].equals(3L);

        assert list1[0] == Character.valueOf('a');
        assert list1[1] == Character.valueOf('b');
        assert list1[2] == Character.valueOf('c');
    }

    public static class Junk
    {
        public Object getName()
        {
            return name;
        }

        public void setName(Object name)
        {
            this.name = name;
        }

        public List<Object> getThings()
        {
            return things;
        }

        public void setThings(List things)
        {
            this.things = things;
        }

        public Map<Object, Object> getNamesToAge()
        {
            return namesToAge;
        }

        public void setNamesToAge(Map namesToAge)
        {
            this.namesToAge = namesToAge;
        }

        public Object[] getStuff()
        {
            return stuff;
        }

        public void setStuff(Object[] stuff)
        {
            this.stuff = stuff;
        }

        private Object name;
        private List things = new ArrayList<>();
        private Map namesToAge = new LinkedHashMap<>();
        private Object[] stuff;
    }

    public static class CollectionTest
    {
        public Collection getFoos()
        {
            return foos;
        }

        public void setFoos(Collection foos)
        {
            this.foos = foos;
        }

        public Object[] getBars()
        {
            return bars;
        }

        public void setBars(Object[] bars)
        {
            this.bars = bars;
        }

        private Collection foos;
        private Object[] bars;
    }
}
