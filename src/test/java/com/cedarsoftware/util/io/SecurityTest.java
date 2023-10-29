package com.cedarsoftware.util.io;

import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

public class SecurityTest
{
    public String testName = SecurityTest.class.getSimpleName();
    public SecurityTest() {}

    @Test
    public void testSecureWrites() throws Exception
    {
        ProcessBuilder builder = new ProcessBuilder("ipconfig");
        Process process = builder.start();
        attemptToWriteDisallowedClass(builder);
        attemptToWriteDisallowedClass(process);
        attemptToWriteDisallowedClass(SecurityTest.class.getClassLoader());
        Method method = ReflectionUtils.getMethod(SecurityTest.class, "testSecureWrites", (Class<?>[])null);
        assert method != null;
        attemptToWriteDisallowedClass(method);
        Collection<Field> fields = ReflectionUtils.getDeepDeclaredFields(SecurityTest.class);
        attemptToWriteDisallowedClass(fields.toArray()[0]);
        Constructor<?> constructor = ReflectionUtils.getConstructor(SecurityTest.class, (Class<?>[])null);
        attemptToWriteDisallowedClass(constructor);
    }

    private void attemptToWriteDisallowedClass(Object obj)
    {
        // attempt to write as root
        assert obj != null;
        String json = TestUtil.toJson(obj);
        Object o = TestUtil.toJava(json);
        assert o == null;

        // attempt to write inside Object[]
        Object[] arrayOfObj = new Object[] {obj};
        json = TestUtil.toJson(arrayOfObj);
        o = TestUtil.toJava(json);
        Object[] array = (Object[]) o;
        assert array.length == 1;
        assert array[0] == null;

        // attempt to write inside List
        List listOfObj = List.of(obj);
        json = TestUtil.toJson(listOfObj);
        o = TestUtil.toJava(json);
        List list = (List) o;
        assert list.size() == 1;
        assert list.get(0) == null;

        // attempt to write inside of Map on value side
        Map<String, Object> mapStringObj = new HashMap<>();
        mapStringObj.put("test", obj);
        json = TestUtil.toJson(mapStringObj);
        o = TestUtil.toJava(json);
        Map map = (Map) o;
        assert map.size() == 1;
        Map.Entry entry = (Map.Entry)map.entrySet().iterator().next();
        assert entry.getKey().equals("test");
        assert entry.getValue() == null;

        // attempt to write inside of Map on value side of a Long keyed Map
        Map<Long, Object> mapLongObj = new HashMap<>();
        mapLongObj.put(17L, obj);
        json = TestUtil.toJson(mapLongObj);
        o = TestUtil.toJava(json);
        map = (Map) o;
        assert map.size() == 1;
        entry = (Map.Entry)map.entrySet().iterator().next();
        assert DeepEquals.deepEquals(entry.getKey(), 17L);
        assert entry.getValue() == null;

        // attempt to write inside of Map on key side
        Map<Object, String> mapObjString = new HashMap<>();
        mapObjString.put(obj, "test");
        json = TestUtil.toJson(mapObjString);
        o = TestUtil.toJava(json);
        map = (Map) o;
        assert map.size() == 1;
        entry = (Map.Entry)map.entrySet().iterator().next();
        assert entry.getKey() == null;
        assert entry.getValue().equals("test");

        // attempt to write as field on an class
        Fugazi fugazi = new Fugazi(obj);
        json = TestUtil.toJson(fugazi);
        o = TestUtil.toJava(json);
        Fugazi fugazi2 = (Fugazi) o;
        assert fugazi2.desc.equals("fake");
        assert fugazi2.value == null;
    }

    @Test
    public void testSecureReads()
    {
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/processBuilder.json"));
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/process.json"));
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/classLoader.json"));
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/constructor.json"));
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/method.json"));
        verifyReadInstantiationSecurity(TestUtil.fetchResource("security/field.json"));
    }

    private void verifyReadInstantiationSecurity(String json)
    {
        try
        {
            TestUtil.toJava(json);
            fail();
        }
        catch (JsonIoException e)
        {
            assert e.getMessage().toLowerCase().contains("security");
            assert e.getMessage().toLowerCase().contains("instantiation");
        }
    }

    static class Fugazi
    {
        String desc = "fake";
        Object value;

        Fugazi(Object o)
        {
            value = o;
        }
    }
}
