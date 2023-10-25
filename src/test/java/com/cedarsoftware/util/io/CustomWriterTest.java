package com.cedarsoftware.util.io;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        Map<String, Object> args = new HashMap<>();

        Map<Class<Person>, CustomPersonWriter> customWriters = new HashMap<>();
        customWriters.put(Person.class, new CustomPersonWriter());

        Map<Class<Person>, CustomPersonReader> customReaders = new HashMap<>();
        customReaders.put(Person.class, new CustomPersonReader());

        args.put(JsonWriter.CUSTOM_WRITER_MAP, customWriters);
        args.put(JsonReader.CUSTOM_READER_MAP, customReaders);
        args.put(JsonReader.NOT_CUSTOM_READER_MAP, new ArrayList<>());
        String jsonCustom = TestUtil.toJson(p, args);
        Map obj = TestUtil.toMap(jsonCustom, args);
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

        Map<String, Object> readOptions = new ReadOptionsBuilder().withCustomReader(Person.class, new CustomPersonReader()).build();
        Person personCustom = TestUtil.toJava(jsonCustom, readOptions);

        assert personCustom.getFirstName().equals("Michael");
        assert personCustom.getLastName().equals("Bolton");
        List<Pet> petz = personCustom.getPets();
        assert "Eddie".equals(petz.get(0).getName());
        assert "Terrier".equals(petz.get(0).getType());
        assert 6 == petz.get(0).getAge();

        assert "Bella".equals(petz.get(1).getName());
        assert "Chi hua hua".equals(petz.get(1).getType());
        assert 3 == petz.get(1).getAge();

        Map writeOptions = new WriteOptionsBuilder().withCustomWriter(Person.class, new CustomPersonWriter()).withNoCustomizationFor(Person.class).build();
        String jsonOrig = TestUtil.toJson(p, writeOptions);
        assert !jsonCustom.equals(jsonOrig);
        assert jsonCustom.length() < jsonOrig.length();

        writeOptions = new WriteOptionsBuilder().withCustomWriter(Person.class, new CustomPersonWriter()).build();
        String jsonCustom2 = TestUtil.toJson(p, writeOptions);
        String jsonOrig2 = TestUtil.toJson(p);
        assert jsonCustom.equals(jsonCustom2);
        assert jsonOrig.equals(jsonOrig2);

        args.clear();
        Map<Class<Person>, CustomPersonReader> customPersonReaderMap = new HashMap<>();
        customPersonReaderMap.put(Person.class, new CustomPersonReader());
        args.put(JsonReader.CUSTOM_READER_MAP, customPersonReaderMap);
        args.put(JsonReader.NOT_CUSTOM_READER_MAP, new ArrayList<>(List.of(Person.class)));
        Person personOrig = TestUtil.toJava(jsonOrig, args);
        assert personOrig.equals(personCustom);

        p = TestUtil.toJava(jsonCustom);
        assert null == p.getFirstName();
    }

    @Test
    public void testCustomWriterException()
    {
        Person p = createTestPerson();
        try
        {
            Map<String, Object> args = new HashMap<>();
            Map<Class<Person>, BadCustomPWriter> badCustomPWriterMap = new HashMap<>();
            badCustomPWriterMap.put(Person.class, new BadCustomPWriter());
            args.put(JsonWriter.CUSTOM_WRITER_MAP, badCustomPWriterMap);
            TestUtil.toJson(p, args);
            fail();
        }
        catch (JsonIoException e)
        {
            assert e.getMessage().toLowerCase().contains("error writing object");
        }
    }

    @Test
    public void testCustomWriterAddField()
    {
        Person p = createTestPerson();
        Map<String, Object> args = new HashMap<>();
        Map<Class<Person>, CustomPersonWriterAddField> customPersonWriterAddFieldMap = new HashMap<>();
        customPersonWriterAddFieldMap.put(Person.class, new CustomPersonWriterAddField());
        args.put(JsonWriter.CUSTOM_WRITER_MAP, customPersonWriterAddFieldMap);
        String jsonCustom = TestUtil.toJson(p, args);
        assert jsonCustom.contains("_version\":12");
        assert jsonCustom.contains("Michael");
    }

    public static class Pet
    {
        public boolean equals(Object o)
        {
            if (DefaultGroovyMethods.is(this, o))
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
            if (DefaultGroovyMethods.is(this, o))
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

    public static class CustomPersonWriter implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
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

            assert getWriter(args) instanceof JsonWriter;
        }

    }

    public static class CustomPersonWriterAddField implements JsonWriter.JsonClassWriter
    {
        public void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            JsonWriter writer = getWriter(args);
            output.write("\"_version\":12,");
            writer.writeObject(o, false, true);
        }
    }

    public static class CustomPersonReader implements JsonReader.JsonClassReader
    {
        public Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            JsonReader reader = JsonReader.JsonClassReaderEx.Support.getReader(args);
            assert reader != null;
            JsonObject map = (JsonObject) jOb;
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
        public void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            throw new RuntimeException("Bad custom writer");
        }

    }
}
