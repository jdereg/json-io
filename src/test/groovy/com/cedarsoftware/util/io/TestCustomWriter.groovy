package com.cedarsoftware.util.io

import org.junit.Test

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
    }

    static class Person
    {
        String firstName
        String lastName
        List<Pet> pets = new ArrayList<>()
    }

    static class CustomPersonWriter implements JsonWriter.JsonClassWriterEx
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
        }
    }

    Person createTestPerson()
    {
        Person p = new Person()
        p.firstName = 'Michael'
        p.lastName = 'Bolton'
        p.pets.add(createPet('Eddie', 'Terrier', 6))
        p.pets.add(createPet('Bella', 'Chi hua hua', 3))
        return p
    }

    Pet createPet(String name, String type, int age)
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
        JsonWriter.addWriter(Person.class, new CustomPersonWriter())
        String json = JsonWriter.objectToJson(p)
        println json
        Map obj = JsonReader.jsonToMaps(json)
        assert 'Michael' == obj.f
        assert 'Bolton' == obj.l
        // Left off here
//        List<Pet> pets = obj.p
//        assert 2 == pets.size()
    }
}
