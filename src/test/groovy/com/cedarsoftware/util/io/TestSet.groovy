package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
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
class TestSet
{
    static class ManySets implements Serializable
    {
        private enum EnumValues { E1, E2, E3 }
        private enum EmptyValues {  }

        private Set _hashSet
        private Set _treeSet
        private EnumSet<EnumValues> _enumSet
        private EnumSet<EmptyValues> _emptyEnumSet
        private EnumSet<EnumValues> _setOfEnums

        private void init()
        {
            _hashSet = new HashSet()
            _hashSet.add("alpha")
            _hashSet.add("bravo")
            _hashSet.add("charlie")
            _hashSet.add("delta")
            _hashSet.add("echo")
            _hashSet.add("foxtrot")
            _hashSet.add("golf")
            _hashSet.add("hotel")
            _hashSet.add("indigo")
            _hashSet.add("juliet")
            _hashSet.add("kilo")
            _hashSet.add("lima")
            _hashSet.add("mike")
            _hashSet.add("november")
            _hashSet.add("oscar")
            _hashSet.add("papa")
            _hashSet.add("quebec")
            _hashSet.add("romeo")
            _hashSet.add("sierra")
            _hashSet.add("tango")
            _hashSet.add("uniform")
            _hashSet.add("victor")
            _hashSet.add("whiskey")
            _hashSet.add("xray")
            _hashSet.add("yankee")
            _hashSet.add("zulu")

            _treeSet = new TreeSet()
            _treeSet.addAll(_hashSet)

            _enumSet = EnumSet.allOf(EnumValues)
            _emptyEnumSet = EnumSet.allOf(EmptyValues)
            _setOfEnums = EnumSet.allOf(EnumValues)
        }

        protected ManySets()
        {
        }
    }

    @Test
    void testSet()
    {
        ManySets set = new ManySets()
        set.init()
        String json = TestUtil.getJsonString(set)

        ManySets testSet = (ManySets) TestUtil.readJsonObject(json)
        TestUtil.printLine("json = " + json)

        assertTrue(testSet._treeSet.size() == 26)
        assertTrue(testSet._hashSet.size() == 26)
        assertTrue(testSet._treeSet.containsAll(testSet._hashSet))
        assertTrue(testSet._hashSet.containsAll(testSet._treeSet))
        assertEquals("alpha", testSet._treeSet.iterator().next())
        assertTrue(testSet._enumSet.containsAll(EnumSet.allOf(ManySets.EnumValues)))
        assertTrue(testSet._emptyEnumSet.containsAll(EnumSet.allOf(ManySets.EmptyValues)))
        assertTrue(testSet._setOfEnums.containsAll(EnumSet.allOf(ManySets.EnumValues)))

        testSet._enumSet.remove(ManySets.EnumValues.E1)
        testSet._enumSet.remove(ManySets.EnumValues.E2)
        testSet._enumSet.remove(ManySets.EnumValues.E3)
        json = TestUtil.getJsonString(testSet)
        println json
        testSet = (ManySets) TestUtil.readJsonObject(json)
        // TODO: This line throws a ClassCastException - the Enum being added is not the same type as the one used to create the empty EnumSet
//        testSet._enumSet.add(ManySets.EnumValues.E1)
    }
}
