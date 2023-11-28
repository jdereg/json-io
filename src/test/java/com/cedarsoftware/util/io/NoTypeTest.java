package com.cedarsoftware.util.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.cedarsoftware.util.ReturnType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        String json = TestUtil.fetchResource("noTypes/person.json");
        Person person = TestUtil.toObjects(json, Person.class);
        assert person.name.equals("Joe");
        assert person.age == 50;
        assert person.salary == 225000.0d;
        assert person.creditScore == 0;
    }

    @Test
    void personTestToMap()
    {
        String json = TestUtil.fetchResource("noTypes/person.json");
        Map<String, Object> person = TestUtil.toObjects(json, Map.class);
        assert person.get("name").equals("Joe");
        assert (long)person.get("age") == 50L;  // Comes in as Long because all JSON integer values are long
        assert (double)person.get("salary") == 225000.0d;
        assert person.get("creditScore") == null;
    }

    @Test
    @Disabled
    void personsTestToPersons()
    {
        String json = TestUtil.fetchResource("noTypes/persons.json");
        Person[] persons = TestUtil.toObjects(json, Person[].class);
        assert persons[0].name.equals("Joe");
        assert persons[0].age == 50;
        assert persons[0].salary == 225000.0d;
        assert persons[0].creditScore == 0;
        assert persons[0].name.equals("Jane");
        assert persons[0].age == 40;
        assert persons[0].salary == 314000.0d;
        assert persons[0].creditScore == 0;
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
        j.setThings(MetaUtils.listOf(1, 2L, BigInteger.valueOf(3), new BigDecimal("4"), cal.getTime(), "Hello", Junk.class));
        j.getNamesToAge().put("Appollo", 2500L);
        j.getNamesToAge().put("Hercules", 2489);
        j.getNamesToAge().put("Poseidon", BigInteger.valueOf(2502));
        j.getNamesToAge().put("Aphrodite", "2499.0");
        j.getNamesToAge().put("Zeus", cal.getTime());

        String json = TestUtil.toJson(j);
        String json2 = TestUtil.toJson(j, new WriteOptions().showTypeInfoNever());
        assert !json.equals(json2);
        assertEquals("{\"name\":\"Zeus\",\"things\":[1,2,\"3\",\"4\",-84243801600000,\"Hello\",\"com.cedarsoftware.util.io.NoTypeTest$Junk\"],\"namesToAge\":{\"Appollo\":2500,\"Hercules\":2489,\"Poseidon\":\"2502\",\"Aphrodite\":\"2499.0\",\"Zeus\":-84243801600000},\"stuff\":[1,2,\"3\",\"4\",-84243801600000,\"Hello\",\"com.cedarsoftware.util.io.NoTypeTest$Junk\"]}", json2);
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

        map = TestUtil.toObjects(json, new ReadOptions().returnType(ReturnType.JSON_VALUES), null);
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
        cols.setFoos(MetaUtils.listOf(1, 2, "4", 8));
        cols.setBars(new Object[]{1, 3, "5", 7});

        String json = TestUtil.toJson(cols, new WriteOptions().showTypeInfoNever());
        Map map = TestUtil.toObjects(json, new ReadOptions().returnType(ReturnType.JSON_VALUES), null);
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

        json = TestUtil.toJson(MetaUtils.listOf(1, 2, 3, 4), new WriteOptions().showTypeInfoNever());
        assert "[1,2,3,4]".equals(json);

        json = TestUtil.toJson(new Object[]{1, 2, 3, 4}, new WriteOptions().showTypeInfoNever());
        assert "[1,2,3,4]".equals(json);
    }

    @Test
    void testObjectArray()
    {
        Object[] array = new Object[]{new Object[]{1L, 2L, 3L}, new Object[] {'a', 'b', 'c'}};
        String json = TestUtil.toJson(array);
        Object[] list = TestUtil.toObjects(json, new ReadOptions().returnType(ReturnType.JSON_VALUES), null);
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
