package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class SetTest
{
    @Test
    public void testSet()
    {
        ManySets set = new ManySets();
        set.init();
        String json = TestUtil.toJson(set);

        ManySets testSet = TestUtil.toObjects(json, null);
        TestUtil.printLine("json = " + json);

        assertEquals(26, testSet._treeSet.size());
        assertEquals(26, testSet._hashSet.size());
        assertTrue(testSet._treeSet.containsAll(testSet._hashSet));
        assertTrue(testSet._hashSet.containsAll(testSet._treeSet));
        assertEquals("alpha", testSet._treeSet.iterator().next());
        assertTrue(testSet._enumSet.containsAll(EnumSet.allOf(ManySets.EnumValues.class)));
        assertTrue(testSet._emptyEnumSet.containsAll(EnumSet.allOf(ManySets.EmptyValues.class)));
        assertTrue(testSet._setOfEnums.containsAll(EnumSet.allOf(ManySets.EnumValues.class)));

        testSet._enumSet.remove(ManySets.EnumValues.E1);
        testSet._enumSet.remove(ManySets.EnumValues.E2);
        testSet._enumSet.remove(ManySets.EnumValues.E3);
        json = TestUtil.toJson(testSet);
        TestUtil.printLine(json);
        testSet = TestUtil.toObjects(json, null);
        testSet._enumSet.add(ManySets.EnumValues.E1);
    }

    public static class ManySets implements Serializable
    {
        private void init()
        {
            _hashSet = new HashSet();
            _hashSet.add("alpha");
            _hashSet.add("bravo");
            _hashSet.add("charlie");
            _hashSet.add("delta");
            _hashSet.add("echo");
            _hashSet.add("foxtrot");
            _hashSet.add("golf");
            _hashSet.add("hotel");
            _hashSet.add("indigo");
            _hashSet.add("juliet");
            _hashSet.add("kilo");
            _hashSet.add("lima");
            _hashSet.add("mike");
            _hashSet.add("november");
            _hashSet.add("oscar");
            _hashSet.add("papa");
            _hashSet.add("quebec");
            _hashSet.add("romeo");
            _hashSet.add("sierra");
            _hashSet.add("tango");
            _hashSet.add("uniform");
            _hashSet.add("victor");
            _hashSet.add("whiskey");
            _hashSet.add("xray");
            _hashSet.add("yankee");
            _hashSet.add("zulu");

            _treeSet = new TreeSet<>();
            _treeSet.addAll(_hashSet);

            _enumSet = EnumSet.allOf(EnumValues.class);
            _emptyEnumSet = EnumSet.allOf(EmptyValues.class);
            _setOfEnums = EnumSet.allOf(EnumValues.class);
        }

        protected ManySets()
        {
        }

        private Set _hashSet;
        private Set _treeSet;
        private EnumSet<EnumValues> _enumSet;
        private EnumSet<EmptyValues> _emptyEnumSet;
        private EnumSet<EnumValues> _setOfEnums;

        private static enum EnumValues
        {
            E1, E2, E3;
        }

        private static enum EmptyValues
        {
            ;
        }
    }
}
