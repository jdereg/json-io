package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

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
class TestTransient
{
    static class Transient1
    {
        String fname;
        String lname;
        transient String fullname;

        void setFname(String f) { fname = f; buildFull() }
        void setLname(String l) { lname = l; buildFull() }
        void buildFull()        { fullname = fname + " " + lname; }
    }

    @Test
    void testSkipsTransient()
    {
        Transient1 person = new Transient1()
        person.fname = "John"
        person.lname = "DeRegnaucourt"

        String json = TestUtil.getJsonString(person)
        TestUtil.printLine("json = " + json)
        assertFalse(json.contains("fullname"))

        person = (Transient1) TestUtil.readJsonObject(json)
        assertTrue(person.fullname == null)
    }

    @Test
    void testForceTransientSerialize()
    {
        Transient1 person = new Transient1()
        person.fname = "John"
        person.lname = "DeRegnaucourt"
        person.buildFull()

        String json = JsonWriter.objectToJson(person, [FIELD_SPECIFIERS:[(Transient1.class) : ['fname', 'lname','fullname']]])
        assert json.contains("fullname")

        person = (Transient1) TestUtil.readJsonObject(json)
        assertTrue(person.fullname == 'John DeRegnaucourt')
    }

    static class Transient2
    {
        transient TestObject backup;
        TestObject main;
    }

    @Test
    void testTransient2()
    {
        Transient2 trans = new Transient2()
        trans.backup = new TestObject("Roswell")
        trans.main = trans.backup;

        String json = TestUtil.getJsonString(trans)
        TestUtil.printLine("json = " + json)
        assertFalse(json.contains("backup"))

        trans = (Transient2) TestUtil.readJsonObject(json)
        assertEquals(trans.main._name, "Roswell")
    }

}
