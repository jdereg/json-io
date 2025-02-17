package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
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
public class CustomWriterTest
{
    public static Person createTestPerson()
    {
        Person p = new Person();
        p.setFirstName("Michael");
        p.setLastName("Bolton");
        p.getPets().add(createPet("Eddie", "Terrier", 6));
        p.getPets().add(createPet("Bella", "Chi hua hua", 3));
        return p;
    }

    public static Pet createPet(String name, String type, int age)
    {
        Pet pet = new Pet();
        pet.setName(name);
        pet.setType(type);
        pet.setAge(age);
        return pet;
    }

    @Test
    public void testCustomWriter()
    {
        Person p = createTestPerson();

        Map<Class<?>, JsonReader.JsonClassReader> customReaders = new HashMap<>();
        customReaders.put(Person.class, new CustomPersonReader());

        WriteOptions writeOptions0 = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).lruSize(100).build();
        ReadOptions readOptions0 = new ReadOptionsBuilder().returnAsJsonObjects().replaceCustomReaderClasses(customReaders).replaceNotCustomReaderClasses(new ArrayList<>()).lruSize(1).build();
        String jsonCustom = TestUtil.toJson(p, writeOptions0);
        Map obj = TestUtil.toObjects(jsonCustom, readOptions0, null);
        assert "Michael".equals(obj.get("f"));
        assert "Bolton".equals(obj.get("l"));
        Object[] pets = (Object[]) obj.get("p");
        assert 2 == pets.length;
        Map ed = (Map) pets[0];
        assert "Eddie".equals(ed.get("n"));
        assert "Terrier".equals(ed.get("t"));
        assert 6L == (long)ed.get("a");

        Map bella = (Map) pets[1];
        assert "Bella".equals(bella.get("n"));
        assert "Chi hua hua".equals(bella.get("t"));
        assert 3L == (long) bella.get("a");

        ReadOptions readOptions = new ReadOptionsBuilder().addCustomReaderClass(Person.class, new CustomPersonReader()).build();
        Person personCustom = TestUtil.toObjects(jsonCustom, readOptions, null);

        assert personCustom.getFirstName().equals("Michael");
        assert personCustom.getLastName().equals("Bolton");
        List<Pet> petz = personCustom.getPets();
        assert "Eddie".equals(petz.get(0).getName());
        assert "Terrier".equals(petz.get(0).getType());
        assert 6 == petz.get(0).getAge();

        assert "Bella".equals(petz.get(1).getName());
        assert "Chi hua hua".equals(petz.get(1).getType());
        assert 3 == petz.get(1).getAge();

        WriteOptions writeOptions = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).addNotCustomWrittenClass(Person.class).build();
        String jsonOrig = TestUtil.toJson(p, writeOptions);
        assert !jsonCustom.equals(jsonOrig);
        assert jsonCustom.length() < jsonOrig.length();

        writeOptions = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).build();
        String jsonCustom2 = TestUtil.toJson(p, writeOptions);
        String jsonOrig2 = TestUtil.toJson(p);
        assert jsonCustom.equals(jsonCustom2);
        assert jsonOrig.equals(jsonOrig2);

        Map<Class<Person>, CustomPersonReader> customPersonReaderMap = new HashMap<>();
        customPersonReaderMap.put(Person.class, new CustomPersonReader());
        Person personOrig = TestUtil.toObjects(jsonOrig, new ReadOptionsBuilder()
                .replaceCustomReaderClasses(customPersonReaderMap)
                .replaceNotCustomReaderClasses(listOf(Person.class))
                .build(), null);
        assert personOrig.equals(personCustom);

        p = TestUtil.toObjects(jsonCustom, null);
        assert null == p.getFirstName();
    }

    @Test
    public void testCustomWriterException()
    {
        Person p = createTestPerson();
        try {
            TestUtil.toJson(p, new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new BadCustomPWriter()).build());
            fail();
        }
        catch (JsonIoException e) {
            assert e.getMessage().toLowerCase().contains("unable to write custom formatted object");
        }
    }

    @Test
    public void testCustomWriterAddField()
    {
        Person p = createTestPerson();
        String jsonCustom = TestUtil.toJson(p, new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriterAddField()).build());
        assert jsonCustom.contains("_version\":12");
        assert jsonCustom.contains("Michael");
    }

    @Test
    public void testCustomPersonWriterReaderinCollectionTypes()
    {
        Person p = createTestPerson();

        Map<Class<?>, JsonReader.JsonClassReader> customReaders = new HashMap<>();
        customReaders.put(Person.class, new CustomPersonReader());

        WriteOptions writeOptions = new WriteOptionsBuilder().build();
        ReadOptions readOptions = new ReadOptionsBuilder().build();

        // Object[] { person, person }  (same instance twice - 2nd instance if simply @ref to 1st)
        // Works - not using custom writer/reader
        Object people = new Object[]{p, p};
        String json = TestUtil.toJson(people, writeOptions);
        Object obj = TestUtil.toObjects(json, readOptions, null);
        assert DeepEquals.deepEquals(people, obj);
        assert ((Object[])people)[0] == ((Object[])people)[1];

        // Failed - (until fixed in ObjectResolver.readWithCustomReaderIfOneExists() near the bottom
        // JsonObject needs to be updated to point to the newly created actual instance
        writeOptions = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).build();
        readOptions = new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).replaceNotCustomReaderClasses(new ArrayList<>()).build();
        json = TestUtil.toJson(people, writeOptions);
        obj = TestUtil.toObjects(json, readOptions, null);
        assert DeepEquals.deepEquals(people, obj);
        assert ((Object[])people)[0] == ((Object[])people)[1];

        writeOptions = new WriteOptionsBuilder().build();
        readOptions = new ReadOptionsBuilder().build();

        // List of { person, person }  (same instance twice - 2nd instance if simply @ref to 1st)
        // Works - not using custom writer/reader
        people = new ArrayList<>();
        ((List<Person>)people).add(p);
        ((List<Person>)people).add(p);
        json = TestUtil.toJson(people, writeOptions);
        obj = TestUtil.toObjects(json, readOptions, null);
        assert DeepEquals.deepEquals(people, obj);
        assert ((List)people).get(0) == ((List) people).get(1);

        // Failed - (until fixed in ObjectResolver.readWithCustomReaderIfOneExists() near the bottom
        // JsonObject needs to be updated to point to the newly created actual instance
        writeOptions = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).build();
        readOptions = new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).replaceNotCustomReaderClasses(new ArrayList<>()).build();
        json = TestUtil.toJson(people, writeOptions);
        obj = TestUtil.toObjects(json, readOptions, null);
        assert DeepEquals.deepEquals(people, obj);
        assert ((List)people).get(0) == ((List) people).get(1);
    }

    @Test
    public void testCustomPersonWriterReaderForCollectionFields()
    {
        Person p = createTestPerson();

        Map<Class<?>, JsonReader.JsonClassReader> customReaders = new HashMap<>();
        customReaders.put(Person.class, new CustomPersonReader());

        WriteOptions writeOptions = new WriteOptionsBuilder().addCustomWrittenClass(Person.class, new CustomPersonWriter()).build();
        ReadOptions readOptions = new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).replaceNotCustomReaderClasses(new ArrayList<>()).build();
        
        People people = new People(new Object[]{p, p});
        String json = TestUtil.toJson(people, writeOptions);    // Massive @ref JSON
        people = TestUtil.toObjects(json, readOptions, null);
        p = people.listPeeps.get(0);
        assert people.listPeeps.get(1) == p;
        assert people.arrayPeeps[0] == p;
        assert people.arrayPeeps[1] == p;
        assert people.typeArrayPeeps[0] == p;
        assert people.typeArrayPeeps[1] == p;
     }

    public static class Pet
    {
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (!(o instanceof Pet))
            {
                return false;
            }

            Pet pet = (Pet) o;

            if (age != pet.getAge())
            {
                return false;
            }

            if (!name.equals(pet.getName()))
            {
                return false;
            }

            if (!type.equals(pet.getType()))
            {
                return false;
            }

            return true;
        }

        public int hashCode()
        {
            int result;
            result = age;
            result = 31 * result + type.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        public int getAge()
        {
            return age;
        }

        public void setAge(int age)
        {
            this.age = age;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        private int age;
        private String type;
        private String name;
    }

    public static class Person
    {
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (!(o instanceof Person))
            {
                return false;
            }
            
            Person person = (Person) o;

            if (!firstName.equals(person.getFirstName()))
            {
                return false;
            }

            if (!lastName.equals(person.getLastName()))
            {
                return false;
            }
            
            if (pets.size() != person.getPets().size())
            {
                return false;
            }

            int len = pets.size();
            for (int i = 0; i < len ; i++)
            {
                if (!pets.get(i).equals(person.getPets().get(i)))
                {
                    return false;
                }
            }

            return true;
        }

        public int hashCode()
        {
            int result;
            result = firstName.hashCode();
            result = 31 * result + lastName.hashCode();
            result = 31 * result + pets.hashCode();
            return result;
        }

        public String getFirstName()
        {
            return firstName;
        }

        public void setFirstName(String firstName)
        {
            this.firstName = firstName;
        }

        public String getLastName()
        {
            return lastName;
        }

        public void setLastName(String lastName)
        {
            this.lastName = lastName;
        }

        public List<Pet> getPets()
        {
            return pets;
        }

        public void setPets(List<Pet> pets)
        {
            this.pets = pets;
        }

        private String firstName;
        private String lastName;
        private List<Pet> pets = new ArrayList<>();
    }

    static class People
    {
        List<Person> listPeeps;
        Object[] arrayPeeps;
        Person[] typeArrayPeeps;

        People(Object[] peeps)
        {
            listPeeps = new ArrayList<>(peeps.length);
            arrayPeeps = new Object[peeps.length];
            typeArrayPeeps = new Person[peeps.length];

            for (int i=0; i < peeps.length; i++)
            {
                listPeeps.add((Person)peeps[i]);
                arrayPeeps[i] = peeps[i];
                typeArrayPeeps[i] = (Person) peeps[i];
            }
        }
    }

    public static class CustomPersonWriter implements JsonWriter.JsonClassWriter
    {
        @Override
        public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException
        {
            Person p = (Person) o;
            output.write("\"f\":\"");
            output.write(p.getFirstName());
            output.write("\",\"l\":\"");
            output.write(p.getLastName());
            output.write("\",\"p\":[");

            Iterator<Pet> i = p.getPets().iterator();
            while (i.hasNext())
            {
                Pet pet = i.next();
                output.write("{\"n\":\"");
                output.write(pet.getName());
                output.write("\",\"t\":\"");
                output.write(pet.getType());
                output.write("\",\"a\":");
                output.write("" + pet.getAge());
                output.write("}");
                if (i.hasNext())
                {
                    output.write(",");
                }
            }

            output.write("]");
        }
    }

    public static class CustomPersonWriterAddField implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, WriterContext context) throws IOException
        {
            output.write("\"_version\":12,");
            context.writeObject(o, false, true);
        }
    }

    public static class CustomPersonReader implements JsonReader.JsonClassReader
    {
        public Object read(Object jsonObj, Resolver resolver)
        {
            JsonObject map = (JsonObject) jsonObj;
            Person p = new Person();
            p.setFirstName((String)map.get("f"));
            p.setLastName((String)map.get("l"));
            p.setPets(new ArrayList<>());
            Object[] petz = (Object[]) map.get("p");
            for (Object pt : petz)
            {
                Map pet = (Map) pt;
                Pet petObj = new Pet();
                Long age = (Long)pet.get("a");
                petObj.setAge(age.intValue());
                petObj.setName((String)pet.get("n"));
                petObj.setType((String)pet.get("t"));
                p.getPets().add(petObj);
            }

            return p;
        }
    }

    public static class BadCustomPWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, WriterContext writerContext) throws IOException
        {
            throw new RuntimeException("Bad custom writer");
        }
    }
}