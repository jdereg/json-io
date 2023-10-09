package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestCustomWriter
{
    static class Pet
    {
        int age
        String type
        String name

        boolean equals(o)
        {
            if (this.is(o))
            {
                return true
            }
            if (!(o instanceof Pet))
            {
                return false
            }

            Pet pet = (Pet) o

            if (age != pet.age)
            {
                return false
            }
            if (name != pet.name)
            {
                return false
            }
            if (type != pet.type)
            {
                return false
            }

            return true
        }

        int hashCode()
        {
            int result
            result = age
            result = 31 * result + type.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }
    }

    static class Person
    {
        String firstName
        String lastName
        List<Pet> pets = new ArrayList<>()

        boolean equals(o)
        {
            if (this.is(o))
            {
                return true
            }
            if (!(o instanceof Person))
            {
                return false
            }

            Person person = (Person) o

            if (firstName != person.firstName)
            {
                return false
            }
            if (lastName != person.lastName)
            {
                return false
            }

            if (pets.size() != person.pets.size())
            {
                return false
            }

            int len = pets.size()
            for (int i = 0; i < len; i++)
            {
                if (pets[i] != person.pets[i])
                {
                    return false
                }
            }

            return true
        }

        int hashCode()
        {
            int result
            result = firstName.hashCode()
            result = 31 * result + lastName.hashCode()
            result = 31 * result + pets.hashCode()
            return result
        }
    }

    static class CustomPersonWriter implements JsonWriter.JsonClassWriter
    {
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            Person p = (Person) o
            output.write('"f":"')
            output.write(p.getFirstName())
            output.write('","l":"')
            output.write(p.getLastName())
            output.write('","p":[')

            Iterator<Pet> i = p.getPets().iterator()
            while (i.hasNext())
            {
                Pet pet = i.next()
                output.write('{"n":"')
                output.write(pet.name)
                output.write('","t":"')
                output.write(pet.type)
                output.write('","a":')
                output.write(pet.age.toString())
                output.write('}')
                if (i.hasNext())
                {
                    output.write(',');
                }
            }
            output.write(']');

            assert getWriter(args) instanceof JsonWriter
        }
    }

    static class CustomPersonWriterAddField implements JsonWriter.JsonClassWriter
    {
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            JsonWriter writer = getWriter(args);
            output.write("\"_version\":12,");
            writer.writeObject(o, false, true);
        }
    }

    static class CustomPersonReader implements JsonReader.JsonClassReaderEx
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            JsonReader reader = JsonReader.JsonClassReaderEx.Support.getReader(args)
            assert reader instanceof JsonReader
            JsonObject map = (JsonObject) jOb
            Person p = new Person()
            p.firstName = map.f
            p.lastName = map.l
            p.pets = []
            Object[] petz = map.p
            for (Map pet : petz)
            {
                Pet petObj = new Pet()
                petObj.age = pet.a
                petObj.name = pet.n
                petObj.type = pet.t
                p.pets.add(petObj)
            }
            return p
        }
    }

    static class BadCustomPWriter implements JsonWriter.JsonClassWriter
    {
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            throw new RuntimeException('Bad custom writer')
        }
    }

    static Person createTestPerson()
    {
        Person p = new Person()
        p.firstName = 'Michael'
        p.lastName = 'Bolton'
        p.pets.add(createPet('Eddie', 'Terrier', 6))
        p.pets.add(createPet('Bella', 'Chi hua hua', 3))
        return p
    }

    static Pet createPet(String name, String type, int age)
    {
        Pet pet = new Pet()
        pet.name = name
        pet.type = type
        pet.age = age
        return pet
    }

    @Test
    void testCustomWriter()
    {
        Person p = createTestPerson()
        String jsonCustom = TestUtil.getJsonString(p, [(JsonWriter.CUSTOM_WRITER_MAP): [(Person.class): new CustomPersonWriter()]])
        Map obj = TestUtil.readJsonMap(jsonCustom, [(JsonReader.CUSTOM_READER_MAP): [(Person.class): new CustomPersonReader()], (JsonReader.NOT_CUSTOM_READER_MAP): []])
        assert 'Michael' == obj.f
        assert 'Bolton' == obj.l
        List pets = obj.p
        assert 2 == pets.size()
        Map ed = pets[0]
        assert 'Eddie' == ed.n
        assert 'Terrier' == ed.t
        assert 6 == ed.a

        Map bella = pets[1]
        assert 'Bella' == bella.n
        assert 'Chi hua hua' == bella.t
        assert 3 == bella.a

        Person personCustom = TestUtil.readJsonObject(jsonCustom,
                [
                    (JsonReader.CUSTOM_READER_MAP) : [(Person.class):new CustomPersonReader()]
                ])
        assert personCustom.firstName == 'Michael'
        assert personCustom.lastName == 'Bolton'
        List petz = personCustom.pets
        assert 'Eddie' == petz[0].name
        assert 'Terrier' == petz[0].type
        assert 6 == petz[0].age

        assert 'Bella' == petz[1].name
        assert 'Chi hua hua' == petz[1].type
        assert 3 == petz[1].age

        String jsonOrig = TestUtil.getJsonString(p, [(JsonWriter.CUSTOM_WRITER_MAP):[(Person.class):new CustomPersonWriter()],(JsonWriter.NOT_CUSTOM_WRITER_MAP):[Person.class]])
        assert jsonCustom != jsonOrig
        assert jsonCustom.length() < jsonOrig.length()

        String jsonCustom2 = TestUtil.getJsonString(p, [(JsonWriter.CUSTOM_WRITER_MAP):[(Person.class):new CustomPersonWriter()]])
        String jsonOrig2 = JsonWriter.objectToJson(p)
        assert jsonCustom == jsonCustom2
        assert jsonOrig == jsonOrig2

        Person personOrig = TestUtil.readJsonObject(jsonOrig,
                [
                        (JsonReader.CUSTOM_READER_MAP):[(Person.class):new CustomPersonReader()],
                        (JsonReader.NOT_CUSTOM_READER_MAP):[Person.class]
                 ])
        assert personOrig == personCustom

        p = JsonReader.jsonToJava(jsonCustom)
        assert null == p.firstName
    }

    @Test
    void testCustomWriterException()
    {
        Person p = createTestPerson()
        try
        {
            TestUtil.getJsonString(p, [(JsonWriter.CUSTOM_WRITER_MAP): [(Person.class): new BadCustomPWriter()]])
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('error writing object')
        }
    }

    @Test
    void testCustomWriterAddField()
    {
        Person p = createTestPerson()
        String jsonCustom = TestUtil.getJsonString(p, [(JsonWriter.CUSTOM_WRITER_MAP): [(Person.class): new CustomPersonWriterAddField()]])
        assert jsonCustom.contains("_version\":12");
        assert jsonCustom.contains("Michael");
    }
}
