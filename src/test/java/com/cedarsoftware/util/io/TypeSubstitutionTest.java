package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import java.util.*;

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
public class TypeSubstitutionTest
{
    @Test
    public void testBasicTypeSubAtRoot()
    {
        LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
        map.put("java.util.ArrayList", "al");
        Map<String, String> types = map;
        List list = MetaUtils.listOf("alpha", "bravo", "charlie");
        String json = TestUtil.toJson(list, new WriteOptionsBuilder().withCustomTypeNameMap(types).build());
        List test = TestUtil.toJava(json, new ReadOptionsBuilder().withCustomTypeNameMap(types).build());
        assert DeepEquals.deepEquals(list, test);
    }

    @Test
    public void testBasicTypeSubInFieldAndInnerClass()
    {
        Person p = new Person();
        p.setName("John");
        LinkedHashMap<String, String> map = new LinkedHashMap<>(2);
        map.put("Eddie", "Dog");
        map.put("Bella", "Dog");
        p.setPets(map);

        LinkedHashMap<String, String> map1 = new LinkedHashMap<>(2);
        map1.put("java.util.LinkedHashMap", "lmap");
        map1.put("com.cedarsoftware.util.io.TestTypeSubstitution$Person", "person");
        Map<String, String> types = map1;
        String json = TestUtil.toJson(p, new WriteOptionsBuilder().withCustomTypeNameMap(types).build());
        Person clone = TestUtil.toJava(json, new ReadOptionsBuilder().withCustomTypeNameMap(types).build());
        assert clone.getName().equals("John");
        assert DeepEquals.deepEquals(clone.getPets(), p.getPets());
    }

    public static class Person
    {
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Map<Object, Object> getPets()
        {
            return pets;
        }

        public void setPets(Map pets)
        {
            this.pets = pets;
        }

        private String name;
        private Map pets = new HashMap<>();
    }
}
