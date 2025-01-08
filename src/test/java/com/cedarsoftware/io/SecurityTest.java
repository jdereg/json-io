package com.cedarsoftware.io;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.ClassUtilities.getClassLoader;
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
        attemptToWriteDisallowedClass(getClassLoader(SecurityTest.class));
        Method method = ReflectionUtils.getMethod(SecurityTest.class, "testSecureWrites", (Class<?>[])null);
        assert method != null;
        attemptToWriteDisallowedClass(method);
        Collection<Field> fields = ReflectionUtils.getAllDeclaredFields(SecurityTest.class);
        attemptToWriteDisallowedClass(fields.toArray()[0]);
        Constructor<?> constructor = ReflectionUtils.getConstructor(SecurityTest.class, (Class<?>[])null);
        attemptToWriteDisallowedClass(constructor);
    }

    private void attemptToWriteDisallowedClass(Object obj)
    {
        // attempt to write as root
        assert obj != null;
        String json = TestUtil.toJson(obj);
        Object o = TestUtil.toObjects(json, null);
        assert o == null;

        // attempt to write inside Object[]
        Object[] arrayOfObj = new Object[] {obj};
        json = TestUtil.toJson(arrayOfObj);
        o = TestUtil.toObjects(json, null);
        Object[] array = (Object[]) o;
        assert array.length == 1;
        assert array[0] == null;

        // attempt to write inside List
        List listOfObj = listOf(obj);
        json = TestUtil.toJson(listOfObj);
        o = TestUtil.toObjects(json, null);
        List list = (List) o;
        assert list.size() == 1;
        assert list.get(0) == null;

        // attempt to write inside of Map on value side
        Map<String, Object> mapStringObj = new HashMap<>();
        mapStringObj.put("test", obj);
        json = TestUtil.toJson(mapStringObj);
        o = TestUtil.toObjects(json, null);
        Map map = (Map) o;
        assert map.size() == 1;
        Map.Entry entry = (Map.Entry)map.entrySet().iterator().next();
        assert entry.getKey().equals("test");
        assert entry.getValue() == null;

        // attempt to write inside of Map on value side of a Long keyed Map
        Map<Long, Object> mapLongObj = new HashMap<>();
        mapLongObj.put(17L, obj);
        json = TestUtil.toJson(mapLongObj);
        o = TestUtil.toObjects(json, null);
        map = (Map) o;
        assert map.size() == 1;
        entry = (Map.Entry)map.entrySet().iterator().next();
        assert DeepEquals.deepEquals(entry.getKey(), 17L);
        assert entry.getValue() == null;

        // attempt to write inside of Map on key side
        Map<Object, String> mapObjString = new HashMap<>();
        mapObjString.put(obj, "test");
        json = TestUtil.toJson(mapObjString);
        o = TestUtil.toObjects(json, null);
        map = (Map) o;
        assert map.size() == 1;
        entry = (Map.Entry)map.entrySet().iterator().next();
        assert entry.getKey() == null;
        assert entry.getValue().equals("test");

        // attempt to write as field on an class
        Fugazi fugazi = new Fugazi(obj);
        json = TestUtil.toJson(fugazi);
        o = TestUtil.toObjects(json, null);
        Fugazi fugazi2 = (Fugazi) o;
        assert fugazi2.desc.equals("fake");
        assert fugazi2.value == null;
    }

    @Test
    public void testSecureReads()
    {
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/processBuilder.json"));
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/process.json"));
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/classLoader.json"));
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/constructor.json"));
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/method.json"));
        verifyReadInstantiationSecurity(ClassUtilities.loadResourceAsString("security/field.json"));
    }

    private void verifyReadInstantiationSecurity(String json)
    {
        try
        {
            TestUtil.toObjects(json, null);
            fail();
        }
        catch (Exception e)
        {
            String msg = e.getMessage().toLowerCase();
            String msg2 = e.getCause() == null ? "no cause" : e.getCause().getMessage().toLowerCase();
            assert msg.contains("security exception") ||
                    msg2.contains("security")||
                    msg.contains("unable to instantiate") ||
                    msg.contains("unable to load class");
        }
    }

    @Test
    public void testInstantiateClassInstances()
    {
        //  {"@type":"class","value":"java.lang.String"}
        String json = TestUtil.toJson(String.class);
        Class c1 = TestUtil.toObjects(json, null);
        Class c2 = TestUtil.toObjects(json, null);
        assert c1 == c2;
        assert c1.equals(c2);
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
